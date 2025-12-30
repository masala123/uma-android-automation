package com.steve1316.uma_android_automation.bot

import android.util.Log
import android.graphics.Bitmap
import org.opencv.core.Point
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
import com.steve1316.uma_android_automation.utils.types.SkillListEntry
import com.steve1316.uma_android_automation.utils.types.SkillType

import com.steve1316.uma_android_automation.components.*

class Skills (private val game: Game) {
    private val TAG: String = "[${MainActivity.loggerTag}]Skills"

    private val enablePreFinalsSkillPlan = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsSkillPlan")
    private val enablePreFinalsSpendAll = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsSpendAll")
    private val enablePreFinalsMaximizeRank = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsMaximizeRank")
    private val preFinalsSkillPlanJson = SettingsHelper.getStringSetting("skills", "preFinalsSkillPlan")
    private val enableCareerCompleteSkillPlan = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteSkillPlan")
    private val enableCareerCompleteSpendAll = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteSpendAll")
    private val enableCareerCompleteMaximizeRank = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteMaximizeRank")
    private val careerCompleteSkillPlanJson = SettingsHelper.getStringSetting("skills", "careerCompleteSkillPlan")

    // Cached skill plan data loaded once per class instance.
    private val skillData: Map<String, SkillData> = loadSkillData()
    private val userPlannedPreFinalsSkills: List<String> = loadUserPlannedPreFinalsSkills()
    private val userPlannedCareerCompleteSkills: List<String> = loadUserPlannedCareerCompleteSkills()

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
                    rarity = skillObj.getInt(SKILLS_COLUMN_RARITY),
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

    fun getSkillsToSkillIds(): Map<String, Int>? {
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
        return null
    }

    private fun loadUserPlannedPreFinalsSkills(): List<String> {
        if (!enablePreFinalsSkillPlan) {
            MessageLog.i(TAG, "[SKILLS] Pre-Finals skill plan is disabled, returning empty planned skills list...")
            return emptyList()
        }

        return try {
            if (preFinalsSkillPlanJson.isEmpty() || preFinalsSkillPlanJson == "[]") {
                MessageLog.i(TAG, "[SKILLS] User-selected pre-finals skill plan is empty. Returning empty planned skills list...")
                return emptyList()
            }

            val jsonArray = JSONArray(preFinalsSkillPlanJson)
            val plannedSkills = mutableListOf<String>()

            for (i in 0 until jsonArray.length()) {
                val skillObj = jsonArray.getJSONObject(i)
                plannedSkills.add(skillObj.getString("name"))
            }

            MessageLog.i(TAG, "[SKILLS] Successfully loaded ${plannedSkills.size} user-selected pre-finals planned skills from settings.")
            plannedSkills
        } catch (e: Exception) {
            MessageLog.e(TAG, "[SKILLS] Failed to parse user-selected pre-finals skill plan JSON: ${e.message}. Returning empty list...")
            emptyList()
        }
    }

    private fun loadUserPlannedCareerCompleteSkills(): List<String> {
        if (!enableCareerCompleteSkillPlan) {
            MessageLog.i(TAG, "[SKILLS] Career complete skill plan is disabled, returning empty planned skills list...")
            return emptyList()
        }

        return try {
            if (careerCompleteSkillPlanJson.isEmpty() || careerCompleteSkillPlanJson == "[]") {
                MessageLog.i(TAG, "[SKILLS] User-selected career complete skill plan is empty. Returning empty planned skills list...")
                return emptyList()
            }

            val jsonArray = JSONArray(careerCompleteSkillPlanJson)
            val plannedSkills = mutableListOf<String>()

            for (i in 0 until jsonArray.length()) {
                val skillObj = jsonArray.getJSONObject(i)
                plannedSkills.add(skillObj.getString("name"))
            }

            MessageLog.i(TAG, "[SKILLS] Successfully loaded ${plannedSkills.size} user-selected career complete planned skills from settings.")
            plannedSkills
        } catch (e: Exception) {
            MessageLog.e(TAG, "[SKILLS] Failed to parse user-selected career complete skill plan JSON: ${e.message}. Returning empty list...")
            emptyList()
        }
    }

    fun getSkillData(name: String): SkillData? {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for skill lookup.")
            return null
        }

        val names: MutableList<String> = mutableListOf()

        try {
            MessageLog.i(TAG, "[SKILLS] getSkillData: Looking up skill: $name")

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

            MessageLog.d(TAG, "[SKILLS] No exact match for skill \"$name\". Attempting fuzzy search...")

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

    // ==== SKILL LIST HANDLING FUNCTIONS ====

    fun getSkillListBoundingRegion(bitmap: Bitmap? = null): BoundingBox? {
        val bitmap = bitmap ?: game.imageUtils.getSourceBitmap()

        val listTopLeftBitmap: Bitmap? = IconScrollListTopLeft.template.getBitmap(game.imageUtils)
        if (listTopLeftBitmap == null) {
            MessageLog.e(TAG, "[SKILLS] Failed to load IconScrollListTopLeft bitmap.")
            return null
        }

        val listBottomRightBitmap: Bitmap? = IconScrollListBottomRight.template.getBitmap(game.imageUtils)
        if (listBottomRightBitmap == null) {
            MessageLog.e(TAG, "[SKILLS] Failed to load IconScrollListBottomRight bitmap.")
            return null
        }

        val listTopLeft: Point? = IconScrollListTopLeft.find(game.imageUtils).first
        if (listTopLeft == null) {
            MessageLog.e(TAG, "[SKILLS] Failed to find top left corner of race list.")
            return null
        }
        val listBottomRight: Point? = IconScrollListBottomRight.find(game.imageUtils).first
        if (listBottomRight == null) {
            MessageLog.e(TAG, "[SKILLS] Failed to find bottom right corner of race list.")
            return null
        }
        val x0 = (listTopLeft.x - (listTopLeftBitmap.width / 2)).toInt()
        val y0 = (listTopLeft.y - (listTopLeftBitmap.height / 2)).toInt()
        val x1 = (listBottomRight.x + (listBottomRightBitmap.width / 2)).toInt()
        val y1 = (listBottomRight.y + (listBottomRightBitmap.height / 2)).toInt()
        val bbox = BoundingBox(
            x = x0,
            y = y0,
            w = x1 - x0,
            h = y1 - y0,
        )

        if (game.debugMode) {
            game.imageUtils.saveBitmap(bitmap, "getSkillListBoundingRegion", bbox)
        }

        return bbox
    }
    
    fun analyzeSkillListEntry(bitmap: Bitmap, bIsObtained: Boolean, debugString: String = ""): SkillListEntry? {
        fun extractText(bitmap: Bitmap): String {
            try {
                val detectedText = game.imageUtils.performOCROnRegion(
                    bitmap,
                    0,
                    0,
                    bitmap.width - 1,
                    bitmap.height - 1,
                    useThreshold = false,
                    useGrayscale = true,
                    scale = 2.0,
                    ocrEngine = "mlKit",
                    debugName = "analyzeSkillListEntry::extractText"
                )
                MessageLog.i(TAG, "Extracted text: \"$detectedText\"")
                return detectedText
            } catch (e: Exception) {
                MessageLog.e(TAG, "Exception during text extraction: ${e.message}")
                return ""
            }
        }

        val latch = CountDownLatch(2)
        var skillPrice: Int? = null
        var skillData: SkillData? = null
        val logMessages = ConcurrentLinkedQueue<String>()

        Thread {
            try {
                val bboxTitle = BoundingBox(
                    x = (bitmap.width * 0.142).toInt(),
                    y = 0,
                    w = (bitmap.width * 0.57).toInt(),
                    h = (bitmap.height * 0.338).toInt(),
                )
                val croppedTitle = game.imageUtils.createSafeBitmap(
                    bitmap,
                    bboxTitle.x,
                    bboxTitle.y,
                    bboxTitle.w,
                    bboxTitle.h,
                    "bboxTitle_$debugString",
                )

                if (croppedTitle == null) {
                    Log.e(TAG, "[SKILLS] analyzeSkillListEntry: createSafeBitmap for croppedTitle returned NULL.")
                    return@Thread
                }

                if (game.debugMode) {
                    game.imageUtils.saveBitmap(croppedTitle, filename = "bboxTitle_$debugString")
                }

                var skillName: String = extractText(croppedTitle).lowercase()
                if (skillName == "") {
                    Log.e(TAG, "[SKILLS] Failed to extract skill name.")
                    return@Thread
                }

                // Check if the skill has a special char (◎, ○, ×) at the end.
                val componentsToCheck: List<ComponentInterface> = listOf(
                    IconSkillTitleDoubleCircle,
                    IconSkillTitleCircle,
                    IconSkillTitleX,
                )
                var match: ComponentInterface? = null
                for (component in componentsToCheck) {
                    val loc: Point? = game.imageUtils.findImageWithBitmap(
                        component.template.path,
                        croppedTitle,
                        suppressError = true,
                    )
                    if (loc != null) {
                        match = component
                        break
                    }
                }

                // Get the appropriate char to append to the search string.
                // Typically the extracted title will end with "O" or "x"
                // but we can't just replace that character since some titles
                // actually end in those letters. So we just append this to the title
                // since it shouldn't cause fuzzy matching to fail.
                val iconChar: String = when (match) {
                    IconSkillTitleDoubleCircle -> "◎"
                    IconSkillTitleCircle -> "○"
                    IconSkillTitleX -> "×"
                    else -> ""
                }
                skillName += iconChar

                // Negative skills have "Remove" in front of their skill name in the title.
                // The actual skill itself in the database does not have this prefix.
                // Get rid of this prefix as it causes fuzzy matching to fail.
                skillName = skillName.removePrefix("remove")

                // Now lookup the name in the database and update it.
                val tmpSkillData: SkillData? = getSkillData(skillName)
                if (tmpSkillData == null) {
                    Log.e(TAG, "[SKILLS] lookupSkillInDatabase(\"${skillName}\") returned NULL.")
                    return@Thread
                }
                skillData = tmpSkillData
            } catch (e: Exception) {
                Log.e(TAG, "[ERROR] Error processing skill name: ${e.stackTraceToString()}")
            } finally {
                latch.countDown()
            }
        }.start()

        Thread {
            try {
                val bboxPrice = BoundingBox(
                    x = (bitmap.width * 0.7935).toInt(),
                    y = (bitmap.height * 0.372).toInt(),
                    w = (bitmap.width * 0.1068).toInt(),
                    h = (bitmap.height * 0.251).toInt(),
                )
                val croppedPrice = game.imageUtils.createSafeBitmap(
                    bitmap,
                    bboxPrice.x,
                    bboxPrice.y,
                    bboxPrice.w,
                    bboxPrice.h,
                    "bboxPrice_$debugString",
                )

                if (croppedPrice == null) {
                    Log.e(TAG, "[SKILLS] analyzeSkillListEntry: createSafeBitmap for croppedPrice returned NULL.")
                    return@Thread
                }

                if (game.debugMode) {
                    game.imageUtils.saveBitmap(croppedPrice, filename = "bboxPrice_$debugString")
                }

                val price: Int? = extractText(croppedPrice).replace("[^0-9]".toRegex(), "").toIntOrNull()
                if (price == null) {
                    Log.e(TAG, "[SKILLS] Failed to extract skill price.")
                    return@Thread
                }
                skillPrice = price ?: -1
            } catch (e: Exception) {
                Log.e(TAG, "[ERROR] Error processing skill price: ${e.stackTraceToString()}")
            } finally {
                latch.countDown()
            }
        }.start()

        try {
            latch.await(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Log.e(TAG, "[ERROR] Parallel skill analysis timed out.")
        }

        if (skillData == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to infer skillData.")
            return null
        }

        if (skillPrice == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to detect skillPrice.")
            return null
        }

        return SkillListEntry(
            skillData = skillData,
            price = skillPrice,
            bIsObtained = bIsObtained,
        )
    }

    fun analyzeSkillList(
        bitmap: Bitmap? = null,
        bboxSkillList: BoundingBox? = null,
        debugString: String = "",
        skillsToBuy: List<String>? = null,
    ): Map<String, SkillListEntry>? {
        val bitmap = bitmap ?: game.imageUtils.getSourceBitmap()
        val bboxSkillList: BoundingBox? = if (bboxSkillList != null) {
            bboxSkillList
        } else {
            getSkillListBoundingRegion(bitmap)
        }

        if (bboxSkillList == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListBoundingRegion() returned NULL.")
            return null
        }

        // Further crop the skill list region. This creates a window where we
        // will search for skill list entries. This prevents us from detecting
        // entries which are scrolled partially outside of the list and are cut off.
        val bboxSkillEntryRegion = BoundingBox(
            x = bboxSkillList.x,
            y = bboxSkillList.y + ((SharedData.displayHeight * 0.12) / 2).toInt(),
            w = bboxSkillList.w,
            h = bboxSkillList.h - (SharedData.displayHeight * 0.12).toInt(),
        )
        if (game.debugMode) {
            game.imageUtils.saveBitmap(
                bitmap = bitmap,
                filename = "skillEntryRegion_$debugString",
                bbox = bboxSkillEntryRegion,
            )
        }

        // Smaller region used to detect SkillUp buttons in the list.
        val bboxSkillUpRegion = BoundingBox(
            x = game.imageUtils.relX((bboxSkillEntryRegion.x + bboxSkillEntryRegion.w).toDouble(), -125),
            y = bboxSkillEntryRegion.y,
            w = game.imageUtils.relWidth(70),
            h = bboxSkillEntryRegion.h,
        )
        if (game.debugMode) {
            game.imageUtils.saveBitmap(
                bitmap = bitmap,
                filename = "skillUpRegion_$debugString",
                bbox = bboxSkillUpRegion,
            )
        }

        val bboxObtainedPillRegion = BoundingBox(
            x = game.imageUtils.relX((bboxSkillEntryRegion.x + bboxSkillEntryRegion.w).toDouble(), -260),
            y = bboxSkillEntryRegion.y,
            w = game.imageUtils.relWidth(140),
            h = bboxSkillEntryRegion.h,
        )
        if (game.debugMode) {
            game.imageUtils.saveBitmap(
                bitmap = bitmap,
                filename = "obtainedPillRegion_$debugString",
                bbox = bboxObtainedPillRegion,
            )
        }

        val skillUpLocs: List<Pair<String, Point>> = ButtonSkillUp.findAll(
            imageUtils = game.imageUtils,
            region = bboxSkillUpRegion.toIntArray(),
        ).map { point -> Pair("skillUp", point) }

        val obtainedPillLocs: List<Pair<String, Point>> = IconObtainedPill.findAll(
            imageUtils = game.imageUtils,
            region = bboxObtainedPillRegion.toIntArray(),
        ).map { point -> Pair("obtained", point) }

        val points = skillUpLocs.plus(obtainedPillLocs).sortedBy { it.second.y }

        val skills: MutableMap<String, SkillListEntry> = mutableMapOf()

        var i: Int = 0
        for ((pointType, point) in points) {
            if (pointType == "obtained") {
                continue
            }
            // Calculate the bounding box for the skill info.
            // x/y positions differ for the SkillUp and ObtainedPill images.
            val bboxSkillBox = if (pointType == "obtained") {
                BoundingBox(
                    x = (point.x - (SharedData.displayWidth * 0.77)).toInt(),
                    y = (point.y - (SharedData.displayHeight * 0.0599)).toInt(),
                    w = (SharedData.displayWidth * 0.91).toInt(),
                    h = (SharedData.displayHeight * 0.12).toInt(),
                )
            } else {
                BoundingBox(
                    x = (point.x - (SharedData.displayWidth * 0.86)).toInt(),
                    y = (point.y - (SharedData.displayHeight * 0.0583)).toInt(),
                    w = (SharedData.displayWidth * 0.91).toInt(),
                    h = (SharedData.displayHeight * 0.12).toInt(),
                )
            }

            val croppedSkillBox = game.imageUtils.createSafeBitmap(
                bitmap,
                bboxSkillBox.x,
                bboxSkillBox.y,
                bboxSkillBox.w,
                bboxSkillBox.h,
                "bboxSkillBox_$i",
            )

            if (croppedSkillBox == null) {
                MessageLog.e(TAG, "[SKILLS] analyzeSkillList: createSafeBitmap for skillBoxBitmap returned NULL.")
                return null
            }

            if (game.debugMode) {
                game.imageUtils.saveBitmap(croppedSkillBox, filename = "bboxSkillBox_$i")
            }

            val skillListEntry = analyzeSkillListEntry(croppedSkillBox, pointType == "obtained", "$i")
            if (skillListEntry == null) {
                continue
            }
            if (skillListEntry.name == "") {
                MessageLog.e(TAG, "[SKILLS] analyzeSkillList: SkillListEntry name is NULL.")
                continue
            }

            // If this skill is in the skillsToBuy list, click the skill up button.
            if (skillsToBuy != null) {
                if (skillListEntry.name in skillsToBuy) {
                    game.tap(point.x, point.y, ButtonSkillUp.template.path)
                }
            }

            skills[skillListEntry.name] = skillListEntry
            i++
        }

        return skills.toMap()
    }

    private fun scrollDown(bbox: BoundingBox) {
        // Scroll down approx. 2 entries in list.
        game.gestureUtils.swipe(
            (bbox.x + (bbox.w / 2)).toFloat(),
            (bbox.y + (bbox.h / 2)).toFloat(),
            (bbox.x + (bbox.w / 2)).toFloat(),
            ((bbox.y + (bbox.h / 3)) - bbox.h).toFloat(),
            duration=500,
        )
        game.wait(0.1, skipWaitingForLoading = true)
        // Tap to prevent overscrolling. This location shouldn't select any skills.
        game.tap(
            game.imageUtils.relX(bbox.x.toDouble(), 15).toDouble(),
            game.imageUtils.relY(bbox.y.toDouble(), 15).toDouble(),
            ignoreWaiting = true,
        )
        game.wait(0.5, skipWaitingForLoading = true)
    }

    private fun scrollToTop(bbox: BoundingBox) {
        // Scroll to top of list.
        game.gestureUtils.swipe(
            (bbox.x + (bbox.w / 2)).toFloat(),
            (bbox.y + (bbox.h / 2)).toFloat(),
            (bbox.x + (bbox.w / 2)).toFloat(),
            // high value here ensures we go all the way to top of list
            (bbox.y + (bbox.h * 10)).toFloat(),
        )
        // Small delay for list to stabilize.
        game.wait(0.5, skipWaitingForLoading = true)
    }

    fun getSkillPoints(): Int? {
        var bitmap = game.imageUtils.getSourceBitmap()

        val templateBitmap: Bitmap? = LabelSkillListScreenSkillPoints.template.getBitmap(game.imageUtils)
        if (templateBitmap == null) {
            MessageLog.e(TAG, "Failed to load template bitmap for LabelSkillListScreenSkillPoints.")
            return null
        }

        val loc: Point? = LabelSkillListScreenSkillPoints.find(game.imageUtils).first
        if (loc == null) {
            MessageLog.e(TAG, "Failed to find LabelSkillListScreenSkillPoints.")
            return null
        }

        val bbox = BoundingBox(
            x = (loc.x + templateBitmap.width).toInt(),
            y = (loc.y - templateBitmap.height).toInt(),
            w = (templateBitmap.width * 1.5).toInt(),
            h = (templateBitmap.height * 2).toInt(),
        )

        val skillPointsBitmap: Bitmap? = game.imageUtils.createSafeBitmap(
            bitmap,
            bbox,
            "skill list skill points",
        )
        if (skillPointsBitmap == null) {
            MessageLog.e(TAG, "[SKILLS] Failed to createSafeBitmap for skill points.")
            return null
        }

        val skillPoints: Int? = try {
            val detectedText = game.imageUtils.performOCROnRegion(
                skillPointsBitmap,
                0,
                0,
                skillPointsBitmap.width - 1,
                skillPointsBitmap.height - 1,
                useThreshold = false,
                useGrayscale = true,
                scale = 2.0,
                ocrEngine = "mlKit",
                debugName = "getSkillPoints::extractText"
            )
            MessageLog.i(TAG, "Extracted text: \"$detectedText\"")
            return detectedText.replace("[^0-9]".toRegex(), "").toIntOrNull()
        } catch (e: Exception) {
            MessageLog.e(TAG, "Exception during text extraction: ${e.message}")
            return null
        }

        MessageLog.i(TAG, "Detected skill points: $skillPoints")
        return skillPoints
    }

    fun processSkillList(skillsToBuy: List<String>? = null): Map<String, SkillListEntry>? {
        // List of skills in the order that they are shown in the game.
        // We use this later to purchase items from top to bottom.
        var skillList = mutableListOf<String>()

        var bitmap = game.imageUtils.getSourceBitmap()
        val bboxSkillList: BoundingBox? = getSkillListBoundingRegion(bitmap)
        if (bboxSkillList == null) {
            MessageLog.e(TAG, "[SKILLS] detectSkills: getSkillListBoundingRegion() returned NULL.")
            return null
        }

        val bboxScrollBar = BoundingBox(
            x = game.imageUtils.relX((bboxSkillList.x + bboxSkillList.w).toDouble(), -22),
            y = bboxSkillList.y,
            w = 10,
            h = bboxSkillList.h,
        )

        // The center column of pixels in the scrollbar.
        val bboxScrollBarSingleColumn = BoundingBox(
            x = bboxScrollBar.x + (bboxScrollBar.w / 2),
            y = bboxScrollBar.y,
            w = 1,
            h = bboxScrollBar.h,
        )

        // Scroll to top before we do anything else.
        scrollToTop(bboxSkillList)
        
        // Used as a break flag for the loop.
        // When the last item we add is the same as this variable,
        // we know that we are at the last element in the list.
        var lastTitle: String? = null
        // Max time limit for the while loop to search for skills.
        val startTime: Long = System.currentTimeMillis()
        val maxTimeMs: Long = 30000
        var prevScrollBarBitmap: Bitmap? = null
        val skills: MutableMap<String, SkillListEntry> = mutableMapOf()

        while (System.currentTimeMillis() - startTime < maxTimeMs) {
            bitmap = game.imageUtils.getSourceBitmap()

            // SCROLLBAR CHANGE DETECTION LOGIC
            val scrollBarBitmap: Bitmap? = game.imageUtils.createSafeBitmap(
                bitmap,
                bboxScrollBarSingleColumn,
                "skill list scrollbar right half bitmap",
            )
            if (scrollBarBitmap == null) {
                MessageLog.e(TAG, "[SKILLS] Failed to createSafeBitmap for scrollbar.")
                return null
            }

            // If after scrolling the scrollbar hasn't changed, that means
            // we've reached the end of the list.
            if (prevScrollBarBitmap != null && scrollBarBitmap.sameAs(prevScrollBarBitmap)) {
                break
            }

            prevScrollBarBitmap = scrollBarBitmap

            val tmpSkills: Map<String, SkillListEntry>? = analyzeSkillList(
                bitmap,
                bboxSkillList,
                skillsToBuy = skillsToBuy,
            )
            if (tmpSkills != null) {
                skills.putAll(tmpSkills)
            }

            scrollDown(bboxSkillList)
        }

        return skills.toMap()
    }

    private fun getSkillsToBuy(skills: Map<String, SkillListEntry>, skillPoints: Int): List<String> {
        var remainingSkillPoints: Int = skillPoints

        val userSkillPlan: List<String> = if (game.currentDate.day <= 72) userPlannedPreFinalsSkills else userPlannedCareerCompleteSkills

        val skillsToBuy: MutableList<String> = mutableListOf()

        // Create a priority list for skills.
        //
        // Negative skills always have highest priority.
        // Next is user planned skills.
        // Then yellow skills.
        // Then blue skills.
        // Then green skills.
        // Finally red skills.
        //
        // For each skill type, we prioritize generic skills that don't depend
        // on any aptitudes, then after exhausting those options, we attempt to
        // purchase aptitude based skills.
        //
        // After exhausting every option, and if the user enabled the setting
        // to spend all points, then we will just buy every skill from cheapest
        // to most expensive in the following order yellow->blue->green->red.
        // This will optimize the final rank of the trainee.

        // Add negative skills
        for ((name, entry) in skills) {
            if (entry.skillData.bIsNegative && entry.price <= remainingSkillPoints) {
                skillsToBuy.add(name)
                remainingSkillPoints -= entry.price
            }
        }

        // Add user planned skills
        for ((name, entry) in skills) {
            if (name in userSkillPlan && entry.price <= remainingSkillPoints) {
                skillsToBuy.add(name)
                remainingSkillPoints -= entry.price
            }
        }

        // Split skills by type
        val filteredSkills: Map<String, SkillListEntry> = skills.filterKeys { it !in skillsToBuy }
        val groupedByType: Map<SkillType, List<SkillListEntry>> = filteredSkills.values.groupBy { it.skillData.type }

        fun partitionGroupedSkills(
            group: List<SkillListEntry>?,
        ): Pair<List<SkillListEntry>, List<SkillListEntry>> {
            if (group == null) {
                return Pair(emptyList(), emptyList())
            }
            val (a, b) = group.partition { it.skillData.style == null && it.skillData.distance == null }
            // Filter the "b" list to only include items that match the trainee's preferred style/distance.
            val filteredB = b.filter { it.skillData.style == game.trainee.runningStyle || it.skillData.distance == game.trainee.trackDistance }
            return Pair(a, filteredB)
        }

        // Split each skill type into general skills and skills that rely on an aptitude.
        val (yellow1, yellow2) = partitionGroupedSkills(groupedByType[SkillType.YELLOW])
        val (blue1, blue2) = partitionGroupedSkills(groupedByType[SkillType.BLUE])
        val (green1, green2) = partitionGroupedSkills(groupedByType[SkillType.GREEN])
        val (red1, red2) = partitionGroupedSkills(groupedByType[SkillType.RED])

        // Custom priority for skill type and aptitude-based skills.
        val groupedAndPartitionedSkills: List<List<SkillListEntry>> = listOf(
            yellow1,
            yellow2,
            blue1,
            blue2,
            green1,
            green2,
            red1,
            red2,
        )

        for (skillListEntries in groupedAndPartitionedSkills) {
            // Sort by price (lowest first)
            val priceMap: Map<String, Int> = skillListEntries.associateBy { it.skillData.name }.mapValues { it.value.price } 
            val sortedPrices: List<Pair<String, Int>> = priceMap.entries.sortedBy { it.value }.map { it.toPair() }

            // Now add as many skills as we can afford.
            for ((name, price) in sortedPrices) {
                if (remainingSkillPoints < price) {
                    break
                }
                skillsToBuy.add(name)
                remainingSkillPoints -= price
            }
        }

        MessageLog.d(TAG, "[SKILLS] Remaining skill points: $remainingSkillPoints")
        for (name in skillsToBuy) {
            MessageLog.d(TAG, "    $name: ${skills[name]?.price}")
        }
        return skillsToBuy.toList()
    }

    fun handleSkillList(): Boolean {
        val skillPoints: Int? = getSkillPoints()
        if (skillPoints == null) {
            MessageLog.e(TAG, "[SKILLS] handleSkillList: Failed to determine skill points. Aborting...")
            return false
        }

        val skills: Map<String, SkillListEntry>? = processSkillList()
        if (skills == null) {
            MessageLog.e(TAG, "[SKILLS] handleSkillList: Failed to detect skills.")
            return false
        }

        // TODO: Add user configuration for forcing skill purchases.

        val skillsToBuy: List<String> = getSkillsToBuy(skills, skillPoints)

        processSkillList(skillsToBuy)

        return true
    }
}