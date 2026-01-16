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
import com.steve1316.uma_android_automation.utils.types.SkillListEntry
import com.steve1316.uma_android_automation.utils.types.SkillType
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.Aptitude

import com.steve1316.uma_android_automation.components.*


class Skills (private val game: Game) {
    private val TAG: String = "[${MainActivity.loggerTag}]Skills"

    // Get user settings for skill plans.
    val enablePreFinalsSkillPlan = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsSkillPlan")
    val enablePreFinalsSpendAll = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsSpendAll")
    val enablePreFinalsOptimizeRank = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsOptimizeRank")
    val enablePreFinalsBuyInheritedSkills = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsBuyInheritedSkills")
    val enablePreFinalsBuyNegativeSkills = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsBuyNegativeSkills")
    val enablePreFinalsIgnoreGoldSkills = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsIgnoreGoldSkills")
    private val preFinalsSkillPlanJson = SettingsHelper.getStringSetting("skills", "preFinalsSkillPlan")
    
    val enableCareerCompleteSkillPlan = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteSkillPlan")
    val enableCareerCompleteSpendAll = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteSpendAll")
    val enableCareerCompleteOptimizeRank = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteOptimizeRank")
    val enableCareerCompleteBuyInheritedSkills = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteBuyInheritedSkills")
    val enableCareerCompleteBuyNegativeSkills = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteBuyNegativeSkills")
    val enableCareerCompleteIgnoreGoldSkills = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteIgnoreGoldSkills")
    private val careerCompleteSkillPlanJson = SettingsHelper.getStringSetting("skills", "careerCompleteSkillPlan")

    // Get other relevant user settings.
    private val userSelectedTrackDistancesString = SettingsHelper.getStringSetting("racing", "preferredDistances")
    // Parse preferred distances.
    private val userSelectedTrackDistances: List<TrackDistance> = try {
        // Parse as JSON array.
        val jsonArray = JSONArray(userSelectedTrackDistancesString)
        val parsed = (0 until jsonArray.length()).mapNotNull {
            TrackDistance.fromName(jsonArray.getString(it).uppercase())
        }
        MessageLog.i(TAG, "[SKILLS] Parsed preferred distances as JSON array: $parsed.")
        parsed
    } catch (e: Exception) {
        MessageLog.i(TAG, "[SKILLS] Error parsing preferred distances: ${e.message}, using fallback.")
        val parsed = userSelectedTrackDistancesString.split(",").mapNotNull {
            TrackDistance.fromName(it.trim().uppercase())
        }
        MessageLog.i(TAG, "[SKILLS] Fallback parsing result: $parsed")
        parsed
    }

    private val userSelectedRunningStyleString = SettingsHelper.getStringSetting("racing", "originalRaceStrategy")
    

    // Cached skill plan data loaded once per class instance.
    private val skillData: Map<String, SkillData> = loadSkillData()
    private val userPlannedPreFinalsSkills: List<String> = loadUserPlannedPreFinalsSkills()
    private val userPlannedCareerCompleteSkills: List<String> = loadUserPlannedCareerCompleteSkills()

    // Store each skill plan in a data class to make it easier to manage the settings.

    data class SkillPlanSettings(
        val enabled: Boolean,
        val skillPlan: List<String>,
        val spendAll: Boolean,
        val optimizeRank: Boolean,
        val buyInheritedSkills: Boolean,
        val buyNegativeSkills: Boolean,
        val ignoreGoldSkills: Boolean,
    )

    private val skillPlanSettingsPreFinals = SkillPlanSettings(
        enablePreFinalsSkillPlan,
        userPlannedPreFinalsSkills,
        enablePreFinalsSpendAll,
        enablePreFinalsOptimizeRank,
        enablePreFinalsBuyInheritedSkills,
        enablePreFinalsBuyNegativeSkills,
        enablePreFinalsIgnoreGoldSkills,
    )

    private val skillPlanSettingsCareerComplete = SkillPlanSettings(
        enableCareerCompleteSkillPlan,
        userPlannedCareerCompleteSkills,
        enableCareerCompleteSpendAll,
        enableCareerCompleteOptimizeRank,
        enableCareerCompleteBuyInheritedSkills,
        enableCareerCompleteBuyNegativeSkills,
        enableCareerCompleteIgnoreGoldSkills,
    )

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
        private const val SKILLS_COLUMN_VERSIONS = "versions"
        private const val SKILLS_COLUMN_UPGRADE = "upgrade"
        private const val SKILLS_COLUMN_DOWNGRADE = "downgrade"
        private const val SIMILARITY_THRESHOLD = 0.7
    }

    data class SkillToBuy(
        val name: String,
        val numUpgrades: Int = 1,
    )

    /**
     * Detects and handles any dialog popups.
     *
     * To prevent the bot moving too fast, we add a 500ms delay to the
     * exit of this function whenever we close the dialog.
     * This gives the dialog time to close since there is a very short
     * animation that plays when a dialog closes.
     *
     * @return A pair of a boolean and a nullable DialogInterface.
     * The boolean is true when a dialog has been handled by this function.
     * The DialogInterface is the detected dialog, or NULL if no dialogs were found.
     */
    fun handleDialogs(): Pair<Boolean, DialogInterface?> {
        val dialog: DialogInterface? = DialogUtils.getDialog(imageUtils = game.imageUtils)
        if (dialog == null) {
            return Pair(false, null)
        }

        when (dialog.name) {
            "skill_list_confirmation" -> dialog.ok(game.imageUtils)
            "skills_learned" -> dialog.close(game.imageUtils)
            "umamusume_details" -> {
                val prevTrackSurface = game.trainee.trackSurface
                val prevTrackDistance = game.trainee.trackDistance
                val prevRunningStyle = game.trainee.runningStyle
                game.trainee.updateAptitudes(imageUtils = game.imageUtils)
                game.trainee.bTemporaryRunningStyleAptitudesUpdated = false

                if (game.trainee.runningStyle != prevRunningStyle) {
                    // Reset this flag since our preferred running style has changed.
                    game.trainee.bHasSetRunningStyle = false
                }

                dialog.close(imageUtils = game.imageUtils)
            }
            else -> {
                return Pair(false, dialog)
            }
        }

        game.wait(0.5, skipWaitingForLoading = true)
        return Pair(true, dialog)
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
                    versions = exactCursor.getString(8),
                    upgrade = if (exactCursor.isNull(9)) null else exactCursor.getInt(9),
                    downgrade = if (exactCursor.isNull(10)) null else exactCursor.getInt(10),
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
                        versions = fuzzyCursor.getString(8),
                        upgrade = if (fuzzyCursor.isNull(9)) null else fuzzyCursor.getInt(9),
                        downgrade = if (fuzzyCursor.isNull(10)) null else fuzzyCursor.getInt(10),
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

    fun analyzeSkillListEntryThreadSafe(bitmap: Bitmap, bIsObtained: Boolean, debugString: String = ""): SkillListEntry? {
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
                MessageLog.d(TAG, "Extracted text: \"$detectedText\"")
                return detectedText
            } catch (e: Exception) {
                MessageLog.e(TAG, "Exception during text extraction: ${e.message}")
                return ""
            }
        }

        var skillPrice: Int? = null
        var skillDiscount: Int? = null
        var skillData: SkillData? = null
        val logMessages = ConcurrentLinkedQueue<String>()

        // TITLE
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
            return null
        }

        if (game.debugMode) {
            game.imageUtils.saveBitmap(croppedTitle, filename = "bboxTitle_$debugString")
        }

        var skillName: String = extractText(croppedTitle).lowercase()
        if (skillName == "") {
            Log.e(TAG, "[SKILLS] Failed to extract skill name.")
            return null
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
            return null
        }
        skillData = tmpSkillData

        // PRICE
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
            return null
        }

        if (game.debugMode) {
            game.imageUtils.saveBitmap(croppedPrice, filename = "bboxPrice_$debugString")
        }

        if (!bIsObtained) {
            val price: Int? = extractText(croppedPrice).replace("[^0-9]".toRegex(), "").toIntOrNull()
            if (price == null) {
                Log.e(TAG, "[SKILLS] Failed to extract skill price.")
                return null
            }
            skillPrice = price ?: -1
        }

        if (skillData == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to infer skillData.")
            return null
        }

        if (skillPrice == null && !bIsObtained) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to detect skillPrice.")
            return null
        }

        return SkillListEntry(
            skillData = skillData,
            price = skillPrice ?: -1,
            discount = skillDiscount ?: -1,
            bIsObtained = bIsObtained,
        )
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
        var skillDiscount: Int? = null
        var skillData: SkillData? = null
        val logMessages = ConcurrentLinkedQueue<String>()

        // TITLE
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

        // PRICE
        Thread {
            try {
                // Early exit thread if skill is already obtained.
                if (bIsObtained) {
                    return@Thread
                }

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

        // HINT LEVEL
        /*
        Thread {
            try {
                val bbox = BoundingBox(
                    x = (bitmap.width * 0.7711).toInt(),
                    y = (bitmap.height * 0.1645).toInt(),
                    w = (bitmap.width * 0.15056).toInt(),
                    h = (bitmap.height * 0.12987).toInt(),
                )
                val cropped = game.imageUtils.createSafeBitmap(
                    bitmap,
                    bbox.x,
                    bbox.y,
                    bbox.w,
                    bbox.h,
                    "bboxDiscount_$debugString",
                )

                if (cropped == null) {
                    Log.e(TAG, "[SKILLS] analyzeSkillListEntry: createSafeBitmap for bboxDiscount returned NULL.")
                    return@Thread
                }

                if (game.debugMode) {
                    game.imageUtils.saveBitmap(cropped, filename = "bboxDiscount_$debugString")
                }

                val text: String = extractText(cropped)
                // If we don't have any discount, then set to 0. This way we know
                // that detection succeeded but there is no discount available.
                if (!text.contains("OFF", ignoreCase = true)) {
                    skillDiscount = 0
                    return@Thread
                }

                val discount: Int? = text.replace("[^0-9]".toRegex(), "").toIntOrNull()
                if (discount == null) {
                    Log.e(TAG, "[SKILLS] Failed to extract skill discount.")
                    return@Thread
                }
                skillDiscount = discount
            } catch (e: Exception) {
                Log.e(TAG, "[ERROR] Error processing skill discount: ${e.stackTraceToString()}")
            } finally {
                latch.countDown()
            }
        }.start()
        */

        try {
            latch.await(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Log.e(TAG, "[ERROR] Parallel skill analysis timed out.")
        }

        if (skillData == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to infer skillData.")
            return null
        }

        if (skillPrice == null && !bIsObtained) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to detect skillPrice.")
            return null
        }

        /*
        if (skillDiscount == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to detect skillDiscount.")
            return null
        }
        */

        return SkillListEntry(
            skillData = skillData,
            price = skillPrice ?: -1,
            discount = skillDiscount ?: -1,
            bIsObtained = bIsObtained,
        )
    }

    fun analyzeSkillList(
        skillPlanSettings: SkillPlanSettings,
        bitmap: Bitmap? = null,
        bboxSkillList: BoundingBox? = null,
        debugString: String = "",
        skillsToBuy: List<SkillToBuy>? = null,
        threadSafe: Boolean = false,
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

        val skillUpLocs: List<Pair<String, Point>> = ButtonSkillUp.findAllWithBitmap(
            imageUtils = game.imageUtils,
            sourceBitmap = bitmap,
            region = bboxSkillUpRegion.toIntArray(),
        ).map { point -> Pair("skillUp", point) }

        val obtainedPillLocs: List<Pair<String, Point>> = IconObtainedPill.findAllWithBitmap(
            imageUtils = game.imageUtils,
            sourceBitmap = bitmap,
            region = bboxObtainedPillRegion.toIntArray(),
        ).map { point -> Pair("obtained", point) }

        val points = skillUpLocs.plus(obtainedPillLocs).sortedBy { it.second.y }
        val skills: MutableMap<String, SkillListEntry> = mutableMapOf()

        var i: Int = 0
        for ((pointType, point) in points) {
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

            val skillListEntry = if (threadSafe) {
                analyzeSkillListEntryThreadSafe(
                    bitmap = croppedSkillBox,
                    bIsObtained = pointType == "obtained",
                    debugString = "$i",
                )
            } else {
                analyzeSkillListEntry(
                    bitmap = croppedSkillBox,
                    bIsObtained = pointType == "obtained",
                    debugString = "$i",
                )
            }
            if (skillListEntry == null) {
                continue
            }
            if (skillListEntry.name == "") {
                MessageLog.e(TAG, "[SKILLS] analyzeSkillList: SkillListEntry name is NULL.")
                continue
            }

            // Don't need to buy skill if it's already obtained.
            if (!skillListEntry.bIsObtained) {
                // Purchase skill if necessary by clicking the skill up button.
                if (skillPlanSettings.buyInheritedSkills && skillListEntry.skillData.bIsUnique) {
                    game.tap(point.x, point.y, ButtonSkillUp.template.path)
                } else if (skillPlanSettings.buyNegativeSkills && skillListEntry.skillData.bIsNegative) {
                    game.tap(point.x, point.y, ButtonSkillUp.template.path)
                } else if (
                    skillsToBuy != null &&
                    !(skillPlanSettings.ignoreGoldSkills && skillListEntry.skillData.bIsGold)
                ) {
                    val skillToBuy: SkillToBuy? = skillsToBuy.find { it.name == skillListEntry.name }
                    if (skillToBuy != null) {
                        game.tap(point.x, point.y, ButtonSkillUp.template.path, taps = skillToBuy.numUpgrades)
                    }
                }
            }

            skills[skillListEntry.name] = skillListEntry
            i++
        }

        return skills.toMap()
    }

    private fun scrollDown(bbox: BoundingBox) {
        // Scroll down approx 2 entries in list.
        game.gestureUtils.swipe(
            (bbox.x + (bbox.w / 2)).toFloat(),
            (bbox.y + (bbox.h / 2)).toFloat(),
            (bbox.x + (bbox.w / 2)).toFloat(),
            // If we scrolled entire height of list, we'd possibly miss some
            // entries. So we just go a bit shy of the full height.
            (bbox.y - (bbox.h / 3)).toFloat(),
            duration=500,
        )
        // Tap to prevent overscrolling. This location shouldn't select any skills.
        game.tap(
            game.imageUtils.relX(bbox.x.toDouble(), 15).toDouble(),
            game.imageUtils.relY(bbox.y.toDouble(), 15).toDouble(),
            taps = 2,
            ignoreWaiting = true,
        )
        // Small delay to allow list to stabilize before we try reading it.
        game.wait(0.1, skipWaitingForLoading = true)
    }

    private fun scrollToTop(bbox: BoundingBox) {
        // Scroll to top of list.
        game.gestureUtils.swipe(
            (bbox.x + (bbox.w / 2)).toFloat(),
            (bbox.y + (bbox.h / 2)).toFloat(),
            (bbox.x + (bbox.w / 2)).toFloat(),
            // high value here ensures we go all the way to top of list
            (bbox.y + (bbox.h * 100)).toFloat(),
        )
        // Small delay for list to stabilize.
        game.wait(1.0, skipWaitingForLoading = true)
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

    fun processSkillList(
        skillPlanSettings: SkillPlanSettings,
        skillsToBuy: List<SkillToBuy>? = null,
    ): Map<String, SkillListEntry>? {
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
        val maxTimeMs: Long = 60000
        var prevScrollBarBitmap: Bitmap? = null
        val skills: MutableMap<String, SkillListEntry> = mutableMapOf()

        while (System.currentTimeMillis() - startTime < maxTimeMs) {
            bitmap = game.imageUtils.getSourceBitmap()

            // SCROLLBAR CHANGE DETECTION LOGIC
            val scrollBarBitmap: Bitmap? = game.imageUtils.createSafeBitmap(
                bitmap,
                bboxScrollBarSingleColumn,
                "skill list scrollbar single column bitmap",
            )
            if (scrollBarBitmap == null) {
                MessageLog.e(TAG, "[SKILLS] Failed to createSafeBitmap for scrollbar.")
                return null
            }

            // If the scrollbar hasn't changed after scrolling,
            // that means we've reached the end of the list.
            if (prevScrollBarBitmap != null && scrollBarBitmap.sameAs(prevScrollBarBitmap)) {
                break
            }

            prevScrollBarBitmap = scrollBarBitmap

            val tmpSkills: Map<String, SkillListEntry>? = analyzeSkillList(
                bitmap = bitmap,
                bboxSkillList = bboxSkillList,
                skillPlanSettings = skillPlanSettings,
                skillsToBuy = skillsToBuy,
            )
            if (tmpSkills != null) {
                // Another exit condition.
                // If there are no new entries after scrolling,
                // then we're at the bottom of the list.
                if (skills.keys.containsAll(tmpSkills.keys)) {
                    break
                }
                skills.putAll(tmpSkills)
            }


            scrollDown(bboxSkillList)
            game.wait(0.5, skipWaitingForLoading = true)
        }

        // Now we need to update the upgrade/downgrade properties of each entry.
        for ((name, entry) in skills) {
            if (entry.bIsObtained) {
                continue
            }
            entry.upgrade = getDirectUpgrade(entry, skills)
            entry.downgrade = getDirectDowngrade(entry, skills)
        }

        return skills.toMap()
    }

    /** Gets the direct upgrade to a SkillListEntry in the Skill List.
     * 
     * @param entry The SkillListEntry to check.
     * @param skills A mapping of all skill names to SkillListEntries in the skill list.
     *
     * @return The SkillListEntry that is a direct upgrade to the passed skill entry.
     * If no direct upgrades exist in the skill list, then NULL is returned.
     */
    private fun getDirectUpgrade(entry: SkillListEntry, skills: Map<String, SkillListEntry>): SkillListEntry? {
        if (entry.skillData.upgrade == null) {
            return null
        }

        val name: String? = skillIdToName[entry.skillData.upgrade]
        if (name == null) {
            return null
        }

        return skills[name]
    }

    /** Gets the direct downgrade to a SkillListEntry in the Skill List.
     * 
     * @param entry The SkillListEntry to check.
     * @param skills A mapping of all skill names to SkillListEntries in the skill list.
     *
     * @return The SkillListEntry that is a direct downgrade to the passed skill entry.
     * If no direct downgrades exist in the skill list, then NULL is returned.
     */
    private fun getDirectDowngrade(entry: SkillListEntry, skills: Map<String, SkillListEntry>): SkillListEntry? {
        if (entry.skillData.downgrade == null) {
            return null
        }

        val name: String? = skillIdToName[entry.skillData.downgrade]
        if (name == null) {
            return null
        }

        return skills[name]
    }

    private fun getOtherVersionsInSkillList(
        skills: Map<String, SkillListEntry>,
        entry: SkillListEntry,
    ): Pair<List<SkillListEntry>, List<SkillListEntry>> {
        // Follow the chain of downgrade IDs that exist in our skill list
        // for this skill. This will tell us all of the previous versions
        // of this skill that have not been purchased yet.
        // This will only matter for skills which are not in-place upgrades
        // (in-place like Wet Conditions ○ and double ◎ as opposed to
        // something like Swinging Maestro and Corner Recovery ○)
        val downgradeVersions: MutableList<SkillListEntry> = mutableListOf()
        var tmpEntry: SkillListEntry? = entry
        while (tmpEntry?.skillData?.downgrade != null) {
            val downgradeId: Int = tmpEntry.skillData.downgrade
            val downgradeName: String? = skillIdToName[downgradeId]
            if (downgradeName == null) {
                break
            }
            val downgradeEntry: SkillListEntry? = skills[downgradeName]
            // We don't care about the downgrade if it doesn't exist in our skill list.
            if (downgradeEntry == null) {
                break
            }

            downgradeVersions.add(downgradeEntry)
            tmpEntry = downgradeEntry
        }

        // Repeat this process but with upgrades instead of downgrades.
        val upgradeVersions: MutableList<SkillListEntry> = mutableListOf()
        tmpEntry = entry
        while (tmpEntry?.skillData?.upgrade != null) {
            val upgradeId: Int = tmpEntry.skillData.upgrade
            val upgradeName: String? = skillIdToName[upgradeId]
            if (upgradeName == null) {
                break
            }
            val upgradeEntry: SkillListEntry? = skills[upgradeName]
            // We don't care about the upgrade if it doesn't exist in our skill list.
            if (upgradeEntry == null) {
                break
            }

            upgradeVersions.add(upgradeEntry)
            tmpEntry = upgradeEntry
        }

        return Pair(downgradeVersions.toList(), upgradeVersions.toList())
    }

    private fun validateSkillPrices(skills: Map<String, SkillListEntry>): Map<String, SkillListEntry>? {
        throw NotImplementedError("Skills::validateSkillPrices: Not implemented!")

        val result: MutableMap<String, SkillListEntry> = mutableMapOf()

        for ((name, entry) in skills) {
            val basePrice: Int? = entry.skillData.cost
            if (basePrice == null) {
                MessageLog.w(TAG, "[SKILLS] Invalid cost in database for ${entry.skillData.name}: NULL.")
                continue
            }

            val (upgrades, downgrades) = getOtherVersionsInSkillList(skills, entry)
            
        }

        // straightaways, corners, and green entries have in-place upgrades.

        // Attempt to validate the price and discount.
        /*
        val basePrice: Int? = skillData.cost
        if (basePrice == null) {
            MessageLog.w(TAG, "[SKILLS] Invalid cost in database for ${skillData.name}: NULL.")
            return null
        }

        if (skillDiscount == null) {
            // If there is no discount, then the detected price should
            // match what we have as the base cost in the database.
            // If not, then set the price to reflect the base cost.
            if (skillData.cost != null && skillPrice != skillData.cost) {
                skillPrice = skillData.cost
            }
        } else {
            if (skillData.cost)
            val expectedPrice: Int = skillData.cost
        }
        if (skillDiscount < 0)

        if (skillPrice > 500 || skillDiscount > 50) {

        }
        */
    }

    private fun calculateAdjustedPointRatio(skillListEntry: SkillListEntry): Double {
            val ratioModifierMap: Map<Aptitude, Double> = mapOf(
                Aptitude.S to 1.1,
                Aptitude.A to 1.1,
                Aptitude.B to 0.9,
                Aptitude.C to 0.9,
                Aptitude.D to 0.8,
                Aptitude.E to 0.8,
                Aptitude.F to 0.8,
                Aptitude.G to 0.7,
            )
            val skillRunningStyle: RunningStyle? = skillListEntry.skillData.style
            val skillTrackDistance: TrackDistance? = skillListEntry.skillData.distance
            val evalPt: Int = if (skillRunningStyle != null) {
                val aptitude: Aptitude = game.trainee.checkRunningStyleAptitude(skillRunningStyle)
                val ratioModifier: Double = ratioModifierMap[aptitude] ?: 1.0
                (skillListEntry.skillData.evalPt * ratioModifier).roundToInt()
            } else if (skillTrackDistance != null) {
                val aptitude: Aptitude = game.trainee.checkTrackDistanceAptitude(skillTrackDistance)
                val ratioModifier: Double = ratioModifierMap[aptitude] ?: 1.0
                (skillListEntry.skillData.evalPt * ratioModifier).roundToInt()
            } else {
                skillListEntry.skillData.evalPt
            }

            return (evalPt.toDouble() / skillListEntry.price.toDouble()).toDouble()
        }

    private fun getSkillsToBuyOptimizeRankStrategy(
        skills: Map<String, SkillListEntry>,
        skillPoints: Int,
        skillPlanSettings: SkillPlanSettings,
    ): List<SkillToBuy> {
        var remainingSkills: MutableMap<String, SkillListEntry> = skills.toMutableMap()
        var remainingSkillPoints: Int = skillPoints

        val skillsToBuy: MutableList<SkillToBuy> = mutableListOf()

        // Add negative skills
        for ((name, entry) in remainingSkills) {
            if (entry.skillData.bIsNegative && entry.price <= remainingSkillPoints) {
                skillsToBuy.add(SkillToBuy(name))
                remainingSkillPoints -= entry.price
            }
        }

        // Add user planned skills

        // If two different versions of one skill are in the skill list AND in the
        // skill plan, we want to buy the highest level version of that skill.
        // For example, if Corner Recovery O and Swinging Maestro are both in the skill
        // plan, and both entries are in the skill list, then we want to buy Swinging Maestro.
        // However, if we do not have enough points for Swinging Maestro, then attempt to
        // buy Corner Recovery O instead.
        for (name in skillPlanSettings.skillPlan) {
            var entry: SkillListEntry? = remainingSkills[name]
            if (entry == null) {
                continue
            }

            // Skip if we can't afford this entry.
            if (entry.price > remainingSkillPoints) {
                continue
            }

            // Get the highest level of this skill that we can afford.
            // We are essentially walking up the linked list of upgrades.
            while (entry != null) {
                val upgrade: SkillListEntry? = entry.upgrade
                if (upgrade == null) {
                    break
                }
                if (upgrade.price > remainingSkillPoints) {
                    break
                }
                if (entry != null){
                    remainingSkills.remove(entry.skillData.name)
                }
                entry = upgrade
            }

            skillsToBuy.add(SkillToBuy(entry.skillData.name))
            remainingSkillPoints -= entry.price
        }
        remainingSkills -= skillsToBuy.map { it.name }

        // Now we need to handle skills in our plan which do not exist in the skill
        // list BUT will exist if we upgrade a skill far enough. For example, if the
        // user adds Firm Conditions ◎ to the skill plan but we only have
        // Firm Conditions × in the skill list, then we'd need to purchase
        // Firm Conditions ×, then Firm Conditions ○, and only then will we be able
        // to purchase Firm Conditions ◎. This of course means we need to calculate
        // the total price of the skill in our plan by adding the previous two skills
        // prices.
        val skillPlanIds: List<Int> = skillPlanSettings.skillPlan.mapNotNull { skillNameToId[it] }
        for ((name, entry) in remainingSkills) {
            // Only certain types of skills can have in-place upgrades:
            // Negative skills
            // Green skills
            // Distance-based straightaway/corner skills
            //
            // We only want inplace upgrades, so just skip all others.
            if (
                entry.skillData.type != SkillType.GREEN &&
                !entry.skillData.bIsNegative &&
                !entry.skillData.name.dropLast(2).endsWith("straightaways", ignoreCase = true) &&
                !entry.skillData.name.dropLast(2).endsWith("corners", ignoreCase = true)
            ) {
                continue
            }

            val upgradeNames: MutableList<String> = mutableListOf()
            var upgradeId: Int? = entry.skillData.upgrade
            var totalPrice: Int = entry.price
            if (entry.skillData.cost == null) {
                continue
            }
            // The discount amount doesn't change when upgrading a skill.
            val entryDiscount: Double = (entry.price).toDouble() / (entry.skillData.cost).toDouble()
            while (upgradeId != null) {
                val upgradeName: String? = skillIdToName[upgradeId]
                if (upgradeName == null) {
                    break
                }
                
                val upgradeSkillData: SkillData? = skillData[upgradeName]
                if (upgradeSkillData == null) {
                    break
                }

                val upgradeCost: Int? = upgradeSkillData.cost
                if (upgradeCost == null) {
                    break
                }
                totalPrice += ceil(upgradeCost * entryDiscount).toInt()

                upgradeNames.add(upgradeSkillData.name)
                upgradeId = upgradeSkillData.upgrade

                // If this upgrade version is in our skill plan, then we stop here
                // and set it to be purchased. The numUpgrades is how many times
                // we click the "+" button.
                if (upgradeName in skillPlanSettings.skillPlan && totalPrice <= remainingSkillPoints) {
                    skillsToBuy.add(SkillToBuy(name, numUpgrades = upgradeNames.size + 1))
                    remainingSkillPoints -= totalPrice
                    break
                }
            }
        }
        remainingSkills -= skillsToBuy.map { it.name }

        // Early exit if we aren't spending all our points.
        if (!skillPlanSettings.spendAll) {
            return skillsToBuy.toList().distinctBy { it.name }
        }

        var filteredSkills: Map<String, SkillListEntry> = remainingSkills.filterKeys { it !in skillsToBuy.map { skillToBuy -> skillToBuy.name } }
        val sortedByPointRatio: List<SkillListEntry> = filteredSkills.values
            .sortedByDescending { calculateAdjustedPointRatio(it) }

        for (entry in sortedByPointRatio) {
            val ptRatioString: String = "%.2f".format(calculateAdjustedPointRatio(entry))
            MessageLog.d(TAG, "${entry.skillData.name}: ${entry.price}pt (${ptRatioString} rating/pt)")
            // If we can't afford this skill, continue to the next.
            if (remainingSkillPoints < entry.price) {
                continue
            }

            // Skip any gold skills if user specified to not purchase them.
            if (entry.skillData.bIsGold && skillPlanSettings.ignoreGoldSkills) {
                continue
            }

            skillsToBuy.add(SkillToBuy(entry.skillData.name))
            remainingSkillPoints -= entry.price
        }

        remainingSkills -= skillsToBuy.map { it.name }

        return skillsToBuy.toList().distinctBy { it.name }
    }

    private fun getSkillsToBuyDefaultStrategy(
        skills: Map<String, SkillListEntry>,
        skillPoints: Int,
        skillPlanSettings: SkillPlanSettings,
    ): List<SkillToBuy> {
        var remainingSkills: MutableMap<String, SkillListEntry> = skills.toMutableMap()
        var remainingSkillPoints: Int = skillPoints

        val skillsToBuy: MutableList<SkillToBuy> = mutableListOf()

        // Create a priority list for skills.
        //
        // Negative skills always have highest priority.
        // Next is user planned skills.
        // Then unique skills (inherited)
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
        for ((name, entry) in remainingSkills) {
            if (entry.skillData.bIsNegative && entry.price <= remainingSkillPoints) {
                skillsToBuy.add(SkillToBuy(name))
                remainingSkillPoints -= entry.price
            }
        }
        // Remove these entries from our available skills.
        remainingSkills -= skillsToBuy.map { it.name }


        // Add user planned skills

        // If two different versions of one skill are in the skill list AND in the
        // skill plan, we want to buy the highest level version of that skill.
        // For example, if Corner Recovery O and Swinging Maestro are both in the skill
        // plan, and both entries are in the skill list, then we want to buy Swinging Maestro.
        // However, if we do not have enough points for Swinging Maestro, then attempt to
        // buy Corner Recovery O instead.
        for (name in skillPlanSettings.skillPlan) {
            var entry: SkillListEntry? = remainingSkills[name]
            if (entry == null) {
                continue
            }

            // Skip if we can't afford this entry.
            if (entry.price > remainingSkillPoints) {
                continue
            }

            // Get the highest level of this skill that we can afford.
            // We are essentially walking up the linked list of upgrades.
            while (entry != null) {
                val upgrade: SkillListEntry? = entry.upgrade
                if (upgrade == null) {
                    break
                }
                if (upgrade.price > remainingSkillPoints) {
                    break
                }
                if (entry != null){
                    remainingSkills.remove(entry.skillData.name)
                }
                entry = upgrade
            }

            skillsToBuy.add(SkillToBuy(entry.skillData.name))
            remainingSkillPoints -= entry.price
        }
        remainingSkills -= skillsToBuy.map { it.name }

        // Now we need to handle skills in our plan which do not exist in the skill
        // list BUT will exist if we upgrade a skill far enough. For example, if the
        // user adds Firm Conditions ◎ to the skill plan but we only have
        // Firm Conditions × in the skill list, then we'd need to purchase
        // Firm Conditions ×, then Firm Conditions ○, and only then will we be able
        // to purchase Firm Conditions ◎. This of course means we need to calculate
        // the total price of the skill in our plan by adding the previous two skills
        // prices.
        val skillPlanIds: List<Int> = skillPlanSettings.skillPlan.mapNotNull { skillNameToId[it] }
        for ((name, entry) in remainingSkills) {
            // Only certain types of skills can have in-place upgrades:
            // Negative skills
            // Green skills
            // Distance-based straightaway/corner skills
            //
            // We only want inplace upgrades, so just skip all others.
            if (
                entry.skillData.type != SkillType.GREEN &&
                !entry.skillData.bIsNegative &&
                !entry.skillData.name.dropLast(2).endsWith("straightaways", ignoreCase = true) &&
                !entry.skillData.name.dropLast(2).endsWith("corners", ignoreCase = true)
            ) {
                continue
            }

            val upgradeNames: MutableList<String> = mutableListOf()
            var upgradeId: Int? = entry.skillData.upgrade
            var totalPrice: Int = entry.price
            if (entry.skillData.cost == null) {
                continue
            }
            // The discount amount doesn't change when upgrading a skill.
            val entryDiscount: Double = (entry.price).toDouble() / (entry.skillData.cost).toDouble()
            while (upgradeId != null) {
                val upgradeName: String? = skillIdToName[upgradeId]
                if (upgradeName == null) {
                    break
                }
                
                val upgradeSkillData: SkillData? = skillData[upgradeName]
                if (upgradeSkillData == null) {
                    break
                }

                val upgradeCost: Int? = upgradeSkillData.cost
                if (upgradeCost == null) {
                    break
                }
                totalPrice += ceil(upgradeCost * entryDiscount).toInt()

                upgradeNames.add(upgradeSkillData.name)
                upgradeId = upgradeSkillData.upgrade

                // If this upgrade version is in our skill plan, then we stop here
                // and set it to be purchased. The numUpgrades is how many times
                // we click the "+" button.
                if (upgradeName in skillPlanSettings.skillPlan && totalPrice <= remainingSkillPoints) {
                    skillsToBuy.add(SkillToBuy(name, numUpgrades = upgradeNames.size + 1))
                    remainingSkillPoints -= totalPrice
                    break
                }
            }
        }
        remainingSkills -= skillsToBuy.map { it.name }

        // Early exit if we aren't spending all our points.
        if (!skillPlanSettings.spendAll) {
            return skillsToBuy.toList().distinctBy { it.name }
        }

        // Add unique (inherited) skills
        for ((name, entry) in remainingSkills) {
            if (entry.skillData.rarity > 2 && entry.price <= remainingSkillPoints) {
                skillsToBuy.add(SkillToBuy(name))
                remainingSkillPoints -= entry.price
            }
        }
        remainingSkills -= skillsToBuy.map { it.name }

        // Split skills by type
        val preferredRunningStyle: RunningStyle? = when (userSelectedRunningStyleString.uppercase()) {
            "DEFAULT", "AUTO" -> game.trainee.runningStyle
            else -> RunningStyle.fromName(userSelectedRunningStyleString)
        }

        val preferredTrackDistances: List<TrackDistance> = if (userSelectedTrackDistances.isEmpty()) {
            listOf<TrackDistance>(game.trainee.trackDistance)
        } else {
            userSelectedTrackDistances
        }

        var filteredSkills: Map<String, SkillListEntry> = remainingSkills.filterKeys { it !in skillsToBuy.map { skillToBuy -> skillToBuy.name } }
        // Now filter by trainee aptitudes.
        // If the user specified a track distance and/or a running style, then those
        // will be used. Otherwise, the trainee's highest aptitude track distance and
        // running style will be used.
        filteredSkills = filteredSkills.filterValues {
            it.skillData.style == preferredRunningStyle ||
            it.skillData.distance in preferredTrackDistances
        }
        val groupedByType: Map<SkillType, List<SkillListEntry>> = filteredSkills.values.groupBy { it.skillData.type }

        val skillTypePriority: List<SkillType> = listOf(
            SkillType.YELLOW,
            SkillType.BLUE,
            SkillType.GREEN,
            SkillType.RED,
        )

        for (skillType in skillTypePriority) {
            val group: List<SkillListEntry>? = groupedByType[skillType]
            if (group == null) {
                continue
            }
            // Split into two maps where the first has no aptitude-dependent skills
            // and the second only has aptitude-dependent skills.
            val (a, tmpB) = group.partition {
                it.skillData.style == null &&
                it.skillData.distance == null
            }
            // Filter the "b" list to only include items that match the trainee's preferred style/distance.
            val b: List<SkillListEntry> = tmpB.filter {
                it.skillData.style == game.trainee.runningStyle ||
                it.skillData.distance == game.trainee.trackDistance
            }

            // Add the skills that arent aptitude-dependent.
            // Prioritize skills with better evaluation point ratio.
            for (entry in a.sortedByDescending { calculateAdjustedPointRatio(it) }) {
                if (remainingSkillPoints < entry.price) {
                    break
                }
                if (entry.skillData.bIsGold && skillPlanSettings.ignoreGoldSkills) {
                    continue
                }
                skillsToBuy.add(SkillToBuy(entry.skillData.name))
                remainingSkillPoints -= entry.price
            }

            // Add the aptitude-dependent skills.
            // Prioritize skills with better evaluation point ratio.
            for (entry in b.sortedByDescending { calculateAdjustedPointRatio(it) }) {
                if (remainingSkillPoints < entry.price) {
                    break
                }
                if (entry.skillData.bIsGold && skillPlanSettings.ignoreGoldSkills) {
                    continue
                }
                skillsToBuy.add(SkillToBuy(entry.skillData.name))
                remainingSkillPoints -= entry.price
            }
        }
        remainingSkills -= skillsToBuy.map { it.name }

        return skillsToBuy.toList().distinctBy { it.name }
    }

    private fun getSkillsToBuy(
        skills: Map<String, SkillListEntry>,
        skillPoints: Int,
        skillPlanSettings: SkillPlanSettings,
    ): List<SkillToBuy> {
        MessageLog.d(TAG, "[SKILLS] Beginning process of calculating skills to purchase...")

        if(!skillPlanSettings.enabled) {
            MessageLog.i(TAG, "[SKILLS] Skill plan is disabled. No skills will be purchased.")
            return emptyList()
        }

        if (
            skillPlanSettings.skillPlan.isEmpty() &&
            !skillPlanSettings.spendAll &&
            !skillPlanSettings.buyInheritedSkills &&
            !skillPlanSettings.buyNegativeSkills
        ) {
            MessageLog.w(TAG, "[SKILLS] Skill Plan is empty and no options to purchase any skills are enabled. Aborting...")
            return emptyList()
        }

        MessageLog.d(TAG, "[SKILLS] User-specified Skill Plan:")
        for (name in skillPlanSettings.skillPlan) {
            MessageLog.d(TAG, "    $name")
        }

        // Remove any skills that we've already obtained from the map.
        val filteredSkills: Map<String, SkillListEntry> = skills.filterValues { !it.bIsObtained }

        val skillsToBuy: List<SkillToBuy> = if (skillPlanSettings.optimizeRank) {
            getSkillsToBuyOptimizeRankStrategy(
                filteredSkills,
                skillPoints,
                skillPlanSettings,
            )
        } else {
            getSkillsToBuyDefaultStrategy(
                filteredSkills,
                skillPoints,
                skillPlanSettings,
            )
        }

        if (skillsToBuy.isEmpty()) {
            MessageLog.w(TAG, "[SKILLS] List of skills to buy is empty. Aborting...")
            return emptyList()
        }

        MessageLog.d(TAG, "[SKILLS] Skills to Buy:")
        for (skillToBuy in skillsToBuy) {
            MessageLog.d(TAG, "    ${skillToBuy.name}: ${skills[skillToBuy.name]?.price} (numUpgrades=${skillToBuy.numUpgrades})")
        }
        MessageLog.d(TAG, "=======================================")

        return skillsToBuy
    }

    fun handleSkillList(): Boolean {
        // Verify that we are at the skill list screen.
        if (
            !ButtonSkillListFullStats.check(game.imageUtils) ||
            !LabelSkillListScreenSkillPoints.check(game.imageUtils)
        ) {
            MessageLog.e(TAG, "[SKILLS] Not at skill list screen. Aborting...")
            return false
        }

        val bIsCareerComplete: Boolean = !ButtonLog.check(game.imageUtils)
        val skillPlanSettings: SkillPlanSettings = if (bIsCareerComplete) skillPlanSettingsCareerComplete else skillPlanSettingsPreFinals
        if (
            skillPlanSettings.skillPlan.isEmpty() &&
            !skillPlanSettings.spendAll &&
            !skillPlanSettings.buyInheritedSkills &&
            !skillPlanSettings.buyNegativeSkills
        ) {
            MessageLog.w(TAG, "[SKILLS] Skill Plan is empty and no options to purchase any skills are enabled. Aborting...")
            return true
        }

        // Purchasing skills depends on us knowing our aptitudes.
        // If we haven't acquired them yet, then we need to force check them.
        // This can happen if we start the bot at the skill list screen.
        if (!game.trainee.bHasUpdatedAptitudes) {
            ButtonSkillListFullStats.click(game.imageUtils)
            game.wait(0.5, skipWaitingForLoading = true)
            handleDialogs()
        }

        val skillPoints: Int? = getSkillPoints()
        if (skillPoints == null) {
            MessageLog.e(TAG, "[SKILLS] handleSkillList: Failed to determine skill points. Aborting...")
            return false
        }

        if (skillPoints < 30) {
            MessageLog.i(TAG, "[SKILLS] Skill Points < 30. Cannot afford any skills. Aborting...")
            return true
        }

        val skills: Map<String, SkillListEntry>? = processSkillList(
            skillPlanSettings = skillPlanSettings,
        )
        if (skills == null) {
            MessageLog.e(TAG, "[SKILLS] handleSkillList: Failed to detect skills.")
            return false
        }

        MessageLog.d(TAG, "[SKILLS] Detected skills:")
        for ((name, skillListEntry) in skills) {
            MessageLog.d(TAG, "    $name: ${skillListEntry.price}")
        }

        val skillsToBuy: List<SkillToBuy> = getSkillsToBuy(
            skillPlanSettings = skillPlanSettings,
            skills = skills,
            skillPoints = skillPoints,
        )

        if (skillsToBuy.isNotEmpty()) {
            processSkillList(
                skillPlanSettings = skillPlanSettings,
                skillsToBuy = skillsToBuy,
            )
        }

        //ButtonConfirm.click(game.imageUtils)
        game.wait(0.5, skipWaitingForLoading = true)
        // Two dialogs will appear if we purchase any skills.
        // First is the purchase confirmation.
        handleDialogs()
        // Second is the Skills Learned dialog.
        handleDialogs()

        return true
    }
}
