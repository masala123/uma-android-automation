package com.steve1316.uma_android_automation.bot

import android.util.Log

import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SQLiteSettingsManager
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.TextUtils


class Skills (private val game: Game) {
    private val TAG: String = "[${MainActivity.loggerTag}]Racing"

    companion object {
        private const val TABLE_SKILLS = "skills"
        private const val SKILLS_COLUMN_SKILL_ID = "skillId"
        private const val SKILLS_COLUMN_ENGLISH_NAME = "englishName"
        private const val SKILLS_COLUMN_ENGLISH_DESCRIPTION = "englishDescription"
        private const val SIMILARITY_THRESHOLD = 0.7
    }

    data class SkillData(
        val id: Int,
        val name: String,
        val description: String,
    ) {
        override fun toString(): String {
            return "id=$id, name=$name, description=$description"
        }
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
                settingsManager.close()
                return null
            }

            val cursor = database.rawQuery("SELECT $SKILLS_COLUMN_ENGLISH_NAME FROM $TABLE_SKILLS", null)
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(SKILLS_COLUMN_ENGLISH_NAME)
                if (columnIndex != -1) {
                    do {
                        names.add(cursor.getString(columnIndex))
                    } while (cursor.moveToNext())
                }
            }
            cursor.close()
            settingsManager.close()
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] Error looking up skill: ${e.message}")
            settingsManager.close()
        }

        val match: String? = TextUtils.matchStringInList(name, names.toList())
        if (match == null) {
            return null
        }
        MessageLog.e("REMOVEME", "MATCH: $match")
        return lookupSkillInDatabase(match)
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
                settingsManager.close()
                return null
            }

            val exactCursor = database.query(
                TABLE_SKILLS,
                arrayOf(
                    SKILLS_COLUMN_SKILL_ID,
                    SKILLS_COLUMN_ENGLISH_NAME,
                    SKILLS_COLUMN_ENGLISH_DESCRIPTION,
                ),
                "$SKILLS_COLUMN_ENGLISH_NAME = ?",
                arrayOf(name),
                null, null, null,
            )

            if (exactCursor.moveToFirst()) {
                val skill = SkillData(
                    id = exactCursor.getInt(0),
                    name = exactCursor.getString(1),
                    description = exactCursor.getString(2),
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
                    SKILLS_COLUMN_ENGLISH_NAME,
                    SKILLS_COLUMN_ENGLISH_DESCRIPTION,
                ),
                "$SKILLS_COLUMN_ENGLISH_NAME = ?",
                arrayOf(name),
                null, null, null,
            )

            if (!fuzzyCursor.moveToFirst()) {
                fuzzyCursor.close()
                settingsManager.close()
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
                    )
                    if (game.debugMode) {
                        MessageLog.d(TAG, "[DEBUG] Fuzzy match candidate: \"${bestMatch.name}\" AKA \"$tmpName\" with similarity ${game.decimalFormat.format(similarity)}.")
                    } else {
                        Log.d(TAG, "[DEBUG] Fuzzy match candidate: \"${bestMatch.name}\" AKA \"$tmpName\" with similarity ${game.decimalFormat.format(similarity)}.")
                    }
                }
            } while (fuzzyCursor.moveToNext())

            fuzzyCursor.close()
            settingsManager.close()

            if (bestMatch != null) {
                MessageLog.i(TAG, "[SKILLS] Found fuzzy match: \"${bestMatch.name}\" with similarity ${game.decimalFormat.format(bestScore)}.")
                return bestMatch
            }

            MessageLog.i(TAG, "[SKILLS] No match found for skill with name \"$name\".")
            null
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] Error looking up skill: ${e.message}")
            settingsManager.close()
            null
        }
    }

    fun checkSkill(name: String): Boolean {
        return lookupSkillInDatabase(name) != null
    }
}