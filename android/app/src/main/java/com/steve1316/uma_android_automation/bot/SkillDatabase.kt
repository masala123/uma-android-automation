package com.steve1316.uma_android_automation.bot

import android.util.Log
import android.graphics.Bitmap
import org.opencv.core.Point
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Collections
import kotlinx.coroutines.*
import kotlin.math.ceil
import kotlin.math.roundToInt

import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.uma_android_automation.utils.SQLiteSettingsManager
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.automation_library.data.SharedData

import com.steve1316.uma_android_automation.utils.types.BoundingBox
import com.steve1316.uma_android_automation.utils.types.SkillData
import com.steve1316.uma_android_automation.utils.types.SkillType
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.Aptitude

import com.steve1316.uma_android_automation.components.*


class SkillDatabase (private val game: Game) {
    private val TAG: String = "[${MainActivity.loggerTag}]SkillDatabase"

    // Cached skill plan data loaded once per class instance.
    private val skillData: Map<String, SkillData> = loadSkillData()

    private val skillNameToId: Map<String, Int> = getSkillsToSkillIds()
    // Invert the mapping.
    private val skillIdToName: Map<Int, String> = skillNameToId.entries.associate { (k, v) -> v to k }

    companion object {
        private const val TABLE_SKILLS = "skills"
        private const val SKILLS_COLUMN_SKILL_ID = "skill_id"
        private const val SKILLS_COLUMN_NAME_EN = "name_en"
        private const val SKILLS_COLUMN_DESC_EN = "desc_en"
        private const val SKILLS_COLUMN_ICON_ID = "icon_id"
        private const val SKILLS_COLUMN_COST = "cost"
        private const val SKILLS_COLUMN_EVAL_PT = "eval_pt"
        private const val SKILLS_COLUMN_PT_RATIO = "pt_ratio"
        private const val SKILLS_COLUMN_RARITY = "rarity"
        private const val SKILLS_COLUMN_COMMUNITY_TIER = "community_tier"
        private const val SKILLS_COLUMN_VERSIONS = "versions"
        private const val SKILLS_COLUMN_UPGRADE = "upgrade"
        private const val SKILLS_COLUMN_DOWNGRADE = "downgrade"
        private const val SIMILARITY_THRESHOLD = 0.7
    }

    private fun loadSkillData(): Map<String, SkillData> {
        return try {
            val skillPlanDataJson = SettingsHelper.getStringSetting("skills", "skillPlanData")
            if (skillPlanDataJson.isEmpty()) {
                MessageLog.i(TAG, "[SKILLS] SKill plan data is empty, returning empty map.")
                return emptyMap()
            }

            val jsonObject = JSONObject(skillPlanDataJson)
            val skillDataMap = mutableMapOf<String, SkillData>()
            
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val skillObj = jsonObject.getJSONObject(key)

                val skillData = SkillData(
                    // The JSON key is "id". We want this, not the database's "skill_id"
                    id = skillObj.getInt("id"),
                    name = skillObj.getString(SKILLS_COLUMN_NAME_EN),
                    description = skillObj.getString(SKILLS_COLUMN_DESC_EN),
                    iconId = skillObj.getInt(SKILLS_COLUMN_ICON_ID),
                    cost = skillObj.optInt(SKILLS_COLUMN_COST),
                    evalPt = skillObj.getInt(SKILLS_COLUMN_EVAL_PT),
                    ptRatio = skillObj.getDouble(SKILLS_COLUMN_PT_RATIO),
                    rarity = skillObj.getInt(SKILLS_COLUMN_RARITY),
                    communityTier = skillObj.optInt(SKILLS_COLUMN_COMMUNITY_TIER),
                    versions = skillObj.getString(SKILLS_COLUMN_VERSIONS),
                    upgrade = skillObj.optInt(SKILLS_COLUMN_UPGRADE),
                    downgrade = skillObj.optInt(SKILLS_COLUMN_DOWNGRADE),
                )

                skillDataMap[skillData.name] = skillData
            }

            MessageLog.i(TAG, "[SKILLS] Successfully loaded ${skillDataMap.size} skill entries from skill plan data.")
            skillDataMap
        } catch (e: Exception) {
            MessageLog.e(TAG, "[SKILLS] Failed to parse skill plan data JSON: ${e.message}. Returning empty map.")
            emptyMap()
        }
    }

    fun getSkillsToSkillIds(): Map<String, Int> {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for skill lookup.")
            return emptyMap()
        }
        try {
            val res: MutableMap<String, Int> = mutableMapOf()
            val database = settingsManager.getDatabase()
            if (database == null) {
                return emptyMap()
            }

            val cursor = database.rawQuery("SELECT $SKILLS_COLUMN_SKILL_ID, $SKILLS_COLUMN_NAME_EN FROM $TABLE_SKILLS", null)
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(SKILLS_COLUMN_SKILL_ID)
                val nameIndex = cursor.getColumnIndex(SKILLS_COLUMN_NAME_EN)
                if (idIndex != -1 && nameIndex != -1) {
                    do {
                        res[cursor.getString(nameIndex)] = cursor.getInt(idIndex)
                    } while (cursor.moveToNext())
                }
            }
            cursor.close()
            return res.toMap()
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] getSkill: Error looking up skill: ${e.message}")
        } finally {
            settingsManager.close()
        }
        return emptyMap()
    }

    fun getSkillData(name: String): SkillData? {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for skill lookup.")
            return null
        }

        val names: MutableList<String> = mutableListOf()

        try {
            MessageLog.d(TAG, "[SKILLS] getSkillData: Looking up skill: $name")

            val database = settingsManager.getDatabase()
            if (database == null) {
                return null
            }

            val cursor = database.rawQuery("SELECT $SKILLS_COLUMN_NAME_EN FROM $TABLE_SKILLS", null)
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(SKILLS_COLUMN_NAME_EN)
                if (columnIndex != -1) {
                    do {
                        names.add(cursor.getString(columnIndex))
                    } while (cursor.moveToNext())
                }
            }
            cursor.close()
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] getSkill: Error looking up skill: ${e.message}")
        } finally {
            settingsManager.close()
        }

        val match: String? = TextUtils.matchStringInList(name, names.toList())
        if (match == null) {
            return null
        }
        return lookupSkillInDatabase(match)
    }

    fun getSkillName(skillId: Int): String? {
        return skillIdToName[skillId]
    }

    fun getSkillId(skillName: String): Int? {
        return skillNameToId[skillName]
    }

    fun getSkillNameFromDatabase(skillId: Int): String? {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for skill lookup.")
            return null
        }

        var name: String? = null

        try {
            MessageLog.d(TAG, "[SKILLS] getSkillName: Looking up skill by ID: $skillId")

            val database = settingsManager.getDatabase()
            if (database == null) {
                return null
            }

            val query: String = "SELECT $SKILLS_COLUMN_NAME_EN FROM $TABLE_SKILLS WHERE $SKILLS_COLUMN_SKILL_ID = ?"
            val cursor = database.rawQuery(query, arrayOf(skillId.toString()))
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(SKILLS_COLUMN_NAME_EN)
                if (columnIndex != -1) {
                    name = cursor.getString(columnIndex)
                }
            }
            cursor.close()
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] getSkill: Error looking up skill: ${e.message}")
        } finally {
            settingsManager.close()
        }

        return name
    }

    fun lookupSkillInDatabase(name: String): SkillData? {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for skill lookup.")
            return null
        }

        return try {
            MessageLog.i(TAG, "[SKILLS] lookupSkillInDatabase: Looking up skill: $name")

            val database = settingsManager.getDatabase()
            if (database == null) {
                return null
            }

            val tmp: String = name

            val exactCursor = database.query(
                TABLE_SKILLS,
                arrayOf(
                    SKILLS_COLUMN_SKILL_ID,
                    SKILLS_COLUMN_NAME_EN,
                    SKILLS_COLUMN_DESC_EN,
                    SKILLS_COLUMN_ICON_ID,
                    SKILLS_COLUMN_COST,
                    SKILLS_COLUMN_EVAL_PT,
                    SKILLS_COLUMN_PT_RATIO,
                    SKILLS_COLUMN_RARITY,
                    SKILLS_COLUMN_COMMUNITY_TIER,
                    SKILLS_COLUMN_VERSIONS,
                    SKILLS_COLUMN_UPGRADE,
                    SKILLS_COLUMN_DOWNGRADE,
                ),
                "$SKILLS_COLUMN_NAME_EN = ?",
                arrayOf(tmp),
                null, null, null,
            )

            if (exactCursor.moveToFirst()) {
                val skill = SkillData(
                    id = exactCursor.getInt(0),
                    name = exactCursor.getString(1),
                    description = exactCursor.getString(2),
                    iconId = exactCursor.getInt(3),
                    cost = if (exactCursor.isNull(4)) null else exactCursor.getInt(4),
                    evalPt = exactCursor.getInt(5),
                    ptRatio = exactCursor.getDouble(6),
                    rarity = exactCursor.getInt(7),
                    communityTier = if (exactCursor.isNull(8)) null else exactCursor.getInt(8),
                    versions = exactCursor.getString(9),
                    upgrade = if (exactCursor.isNull(10)) null else exactCursor.getInt(10),
                    downgrade = if (exactCursor.isNull(11)) null else exactCursor.getInt(11),
                )
                exactCursor.close()
                settingsManager.close()
                MessageLog.d(TAG, "[SKILLS] Found exact match: \"${skill.name}\"")
                return skill
            }
            exactCursor.close()

            MessageLog.d(TAG, "[SKILLS] No exact match for skill \"$name\". Attempting fuzzy search...")

            val fuzzyCursor = database.query(
                TABLE_SKILLS,
                arrayOf(
                    SKILLS_COLUMN_SKILL_ID,
                    SKILLS_COLUMN_NAME_EN,
                    SKILLS_COLUMN_DESC_EN,
                    SKILLS_COLUMN_ICON_ID,
                    SKILLS_COLUMN_COST,
                    SKILLS_COLUMN_EVAL_PT,
                    SKILLS_COLUMN_PT_RATIO,
                    SKILLS_COLUMN_RARITY,
                    SKILLS_COLUMN_COMMUNITY_TIER,
                    SKILLS_COLUMN_VERSIONS,
                    SKILLS_COLUMN_UPGRADE,
                    SKILLS_COLUMN_DOWNGRADE,
                ),
                "$SKILLS_COLUMN_NAME_EN = ?",
                arrayOf(name),
                null, null, null,
            )

            if (!fuzzyCursor.moveToFirst()) {
                fuzzyCursor.close()
                MessageLog.i(TAG, "[SKILLS] No match found for skill with name \"$name\"")
                return null
            }

            val similarityService = StringSimilarityServiceImpl(JaroWinklerStrategy())
            var bestMatch: SkillData? = null
            var bestScore = 0.0

            do {
                val tmpName = fuzzyCursor.getString(1)
                val similarity = similarityService.score(name, tmpName)
                if (similarity > bestScore && similarity >= SIMILARITY_THRESHOLD) {
                    bestScore = similarity
                    bestMatch = SkillData(
                        id = fuzzyCursor.getInt(0),
                        name = tmpName,
                        description = fuzzyCursor.getString(2),
                        iconId = fuzzyCursor.getInt(3),
                        cost = if (fuzzyCursor.isNull(4)) null else fuzzyCursor.getInt(4),
                        evalPt = fuzzyCursor.getInt(5),
                        ptRatio = fuzzyCursor.getDouble(6),
                        rarity = fuzzyCursor.getInt(7),
                        communityTier = if (fuzzyCursor.isNull(8)) null else fuzzyCursor.getInt(8),
                        versions = fuzzyCursor.getString(9),
                        upgrade = if (fuzzyCursor.isNull(10)) null else fuzzyCursor.getInt(10),
                        downgrade = if (fuzzyCursor.isNull(11)) null else fuzzyCursor.getInt(11),
                    )
                    if (game.debugMode) {
                        MessageLog.d(TAG, "[DEBUG] Fuzzy match candidate: \"${bestMatch.name}\" AKA \"$tmpName\" with similarity ${game.decimalFormat.format(similarity)}.")
                    } else {
                        Log.d(TAG, "[DEBUG] Fuzzy match candidate: \"${bestMatch.name}\" AKA \"$tmpName\" with similarity ${game.decimalFormat.format(similarity)}.")
                    }
                }
            } while (fuzzyCursor.moveToNext())

            fuzzyCursor.close()

            if (bestMatch != null) {
                MessageLog.i(TAG, "[SKILLS] Found fuzzy match: \"${bestMatch.name}\" with similarity ${game.decimalFormat.format(bestScore)}.")
                return bestMatch
            }

            MessageLog.i(TAG, "[SKILLS] No match found for skill with name \"$name\".")
            null
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] lookupSkillInDatabase: Error looking up skill: ${e.message}")
            null
        } finally {
            settingsManager.close()
        }
    }

    fun checkSkill(name: String): Boolean {
        return lookupSkillInDatabase(name) != null
    }
}
