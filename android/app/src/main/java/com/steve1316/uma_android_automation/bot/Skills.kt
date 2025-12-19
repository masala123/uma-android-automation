package com.steve1316.uma_android_automation.bot

import android.util.Log

import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SQLiteSettingsManager
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.TextUtils


class Skills (private val game: Game) {
    private val TAG: String = "[${MainActivity.loggerTag}]Skills"

    companion object {
        private const val TABLE_SKILLS = "skills"
        private const val SKILLS_COLUMN_SKILL_ID = "skill_id"
        private const val SKILLS_COLUMN_NAME_EN = "name_en"
        private const val SKILLS_COLUMN_DESC_EN = "desc_en"
        private const val SKILLS_COLUMN_ICON_ID = "icon_id"
        private const val SKILLS_COLUMN_COST = "cost"
        private const val SKILLS_COLUMN_RARITY = "rarity"
        private const val SKILLS_COLUMN_VERSIONS = "versions"
        private const val SKILLS_COLUMN_UPGRADE = "upgrade"
        private const val SKILLS_COLUMN_DOWNGRADE = "downgrade"
        private const val SIMILARITY_THRESHOLD = 0.7
    }

    data class SkillData(
        val id: Int,
        val name: String,
        val description: String,
        val iconId: Int,
        val cost: Int?,
        val rarity: Int,
        val versions: List<Int>,
        val upgrade: Int?,
        val downgrade: Int?,
    ) {
        constructor(
            id: Int,
            name: String,
            description: String,
            iconId: Int,
            cost: Int?,
            rarity: Int,
            versions: String,
            upgrade: Int?,
            downgrade: Int?,
        ) : this(
            id,
            name,
            description,
            iconId,
            cost,
            rarity,
            versions.split(",").filter { it.isNotEmpty() }.map { it.trim().toInt() }.filterNotNull(),
            upgrade,
            downgrade,
        )
    }

    fun getAllSkillsMap(): Map<String, Int>? {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for skill lookup.")
            return null
        }
        try {
            val res: MutableMap<String, Int> = mutableMapOf()
            val database = settingsManager.getDatabase()
            if (database == null) {
                return null
            }

            val cursor = database.rawQuery(
                "SELECT $SKILLS_COLUMN_SKILL_ID, $SKILLS_COLUMN_NAME_EN FROM $TABLE_SKILLS", null)
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
        return null
    }

    fun getSkill(name: String): SkillData? {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for skill lookup.")
            return null
        }

        val names: MutableList<String> = mutableListOf()

        try {
            MessageLog.i(TAG, "[SKILLS] Looking up skill: $name")

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
        MessageLog.e("REMOVEME", "MATCH: $match")
        return lookupSkillInDatabase(match)
    }

    fun getSkillById(id: Int): SkillData? {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for skill lookup.")
            return null
        }

        return try {
            MessageLog.i(TAG, "[SKILLS] Looking up skill by ID: $id")
            val database = settingsManager.getDatabase()
            if (database == null) {
                return null
            }

            val exactCursor = database.query(
                TABLE_SKILLS,
                arrayOf(
                    SKILLS_COLUMN_SKILL_ID,
                    SKILLS_COLUMN_NAME_EN,
                    SKILLS_COLUMN_DESC_EN,
                    SKILLS_COLUMN_ICON_ID,
                    SKILLS_COLUMN_COST,
                    SKILLS_COLUMN_RARITY,
                    SKILLS_COLUMN_VERSIONS,
                    SKILLS_COLUMN_UPGRADE,
                    SKILLS_COLUMN_DOWNGRADE,
                ),
                "$SKILLS_COLUMN_SKILL_ID = ?",
                arrayOf(id.toString()),
                null, null, null,
            )

            if (exactCursor.moveToFirst()) {
                val skill = SkillData(
                    id = exactCursor.getInt(0),
                    name = exactCursor.getString(1),
                    description = exactCursor.getString(2),
                    iconId = exactCursor.getInt(3),
                    cost = if (exactCursor.isNull(4)) null else exactCursor.getInt(4),
                    rarity = exactCursor.getInt(5),
                    versions = exactCursor.getString(6),
                    upgrade = if (exactCursor.isNull(7)) null else exactCursor.getInt(7),
                    downgrade = if (exactCursor.isNull(8)) null else exactCursor.getInt(8),
                )
                exactCursor.close()
                MessageLog.i(TAG, "[SKILLS] Found exact match: \"${skill.id}\" => \"${skill.name}\"")
                return skill
            }
            exactCursor.close()
            return null
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] Error looking up skill by ID: ${e.message}")
            null
        } finally {
            settingsManager.close()
        }
    }

    fun lookupSkillInDatabase(name: String): SkillData? {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for skill lookup.")
            return null
        }

        return try {
            MessageLog.i(TAG, "[SKILLS] Looking up skill: $name")

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
                    SKILLS_COLUMN_RARITY,
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
                    rarity = exactCursor.getInt(5),
                    versions = exactCursor.getString(6),
                    upgrade = if (exactCursor.isNull(7)) null else exactCursor.getInt(7),
                    downgrade = if (exactCursor.isNull(8)) null else exactCursor.getInt(8),
                )
                exactCursor.close()
                settingsManager.close()
                MessageLog.i(TAG, "[SKILLS] Found exact match: \"${skill.name}\"")
                return skill
            }
            exactCursor.close()

            val fuzzyCursor = database.query(
                TABLE_SKILLS,
                arrayOf(
                    SKILLS_COLUMN_SKILL_ID,
                    SKILLS_COLUMN_NAME_EN,
                    SKILLS_COLUMN_DESC_EN,
                    SKILLS_COLUMN_ICON_ID,
                    SKILLS_COLUMN_COST,
                    SKILLS_COLUMN_RARITY,
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
                        rarity = fuzzyCursor.getInt(5),
                        versions = fuzzyCursor.getString(6),
                        upgrade = if (fuzzyCursor.isNull(7)) null else fuzzyCursor.getInt(7),
                        downgrade = if (fuzzyCursor.isNull(8)) null else fuzzyCursor.getInt(8),
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