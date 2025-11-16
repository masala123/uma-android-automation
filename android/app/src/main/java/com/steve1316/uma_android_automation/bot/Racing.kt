package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.uma_android_automation.utils.CustomImageUtils.RaceDetails
import com.steve1316.uma_android_automation.utils.SQLiteSettingsManager
import com.steve1316.automation_library.utils.MessageLog
import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.core.Point
import android.util.Log

import com.steve1316.uma_android_automation.utils.types.Aptitude
import com.steve1316.uma_android_automation.utils.types.TrackSurface
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.RaceGrade

import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.DialogInterface
import com.steve1316.uma_android_automation.components.ButtonChangeRunningStyle
import com.steve1316.uma_android_automation.components.ButtonRaceStrategyFront
import com.steve1316.uma_android_automation.components.ButtonRaceStrategyPace
import com.steve1316.uma_android_automation.components.ButtonRaceStrategyLate
import com.steve1316.uma_android_automation.components.ButtonRaceStrategyEnd

class Racing (private val game: Game) {
    private val TAG: String = "[${MainActivity.loggerTag}]Racing"

    private val enableFarmingFans = SettingsHelper.getBooleanSetting("racing", "enableFarmingFans")
    private val daysToRunExtraRaces: Int = SettingsHelper.getIntSetting("racing", "daysToRunExtraRaces")
    private val disableRaceRetries: Boolean = SettingsHelper.getBooleanSetting("racing", "disableRaceRetries")
    val enableForceRacing = SettingsHelper.getBooleanSetting("racing", "enableForceRacing")

    private val enableRacingPlan = SettingsHelper.getBooleanSetting("racing", "enableRacingPlan")
    private val lookAheadDays = SettingsHelper.getIntSetting("racing", "lookAheadDays")
    private val smartRacingCheckInterval = SettingsHelper.getIntSetting("racing", "smartRacingCheckInterval")
    private val minFansThreshold = SettingsHelper.getIntSetting("racing", "minFansThreshold")
    private val preferredTrackSurfaceString = SettingsHelper.getStringSetting("racing", "preferredTrackSurface")
    private val preferredGradesString = SettingsHelper.getStringSetting("racing", "preferredGrades")
    private val racingPlanJson = SettingsHelper.getStringSetting("racing", "racingPlan")
    private val minimumQualityThreshold = SettingsHelper.getDoubleSetting("racing", "minimumQualityThreshold")
    private val timeDecayFactor = SettingsHelper.getDoubleSetting("racing", "timeDecayFactor")
    private val improvementThreshold = SettingsHelper.getDoubleSetting("racing", "improvementThreshold")

    private var raceRetries = 3
    var raceRepeatWarningCheck = false
    var encounteredRacingPopup = false
    var skipRacing = false
    var firstTimeRacing = true
    var hasFanRequirement = false  // Indicates that a fan requirement has been detected on the main screen.
    var hasTrophyRequirement = false  // Indicates that a trophy requirement has been detected on the main screen.
    private var nextSmartRaceDay: Int? = null  // Tracks the specific day to race based on opportunity cost analysis.

    private val enableStopOnMandatoryRace: Boolean = SettingsHelper.getBooleanSetting("racing", "enableStopOnMandatoryRaces")
    var detectedMandatoryRaceCheck = false

    // Race strategy override settings.
    private val enableRaceStrategyOverride = SettingsHelper.getBooleanSetting("racing", "enableRaceStrategyOverride")
    private val juniorYearRaceStrategy = SettingsHelper.getStringSetting("racing", "juniorYearRaceStrategy")
    private val userSelectedOriginalStrategy = SettingsHelper.getStringSetting("racing", "originalRaceStrategy")
    private var detectedOriginalStrategy: String? = null
    private var hasAppliedStrategyOverride = false

    // Cached race plan data loaded once per class instance.
    private val raceData: Map<String, RaceData> = loadRaceData()
    private val userPlannedRaces: List<PlannedRace> = loadUserPlannedRaces()

    companion object {
        private const val TABLE_RACES = "races"
        private const val RACES_COLUMN_NAME = "name"
        private const val RACES_COLUMN_GRADE = "grade"
        private const val RACES_COLUMN_FANS = "fans"
        private const val RACES_COLUMN_TURN_NUMBER = "turnNumber"
        private const val RACES_COLUMN_NAME_FORMATTED = "nameFormatted"
        private const val RACES_COLUMN_TRACK_SURFACE = "trackSurface"
        private const val RACES_COLUMN_TRACK_DISTANCE = "trackDistance"
        private const val SIMILARITY_THRESHOLD = 0.7
    }

    data class RaceData(
        val name: String,
        val grade: RaceGrade,
        val fans: Int,
        val nameFormatted: String,
        val trackSurface: TrackSurface,
        val trackDistance: TrackDistance,
        val turnNumber: Int
    ) {
        constructor(
            name: String,
            grade: String,
            fans: Int,
            nameFormatted: String,
            trackSurface: String,
            trackDistance: String,
            turnNumber: Int,
        ) : this(
            name,
            RaceGrade.fromName(grade)!!,
            fans,
            nameFormatted,
            TrackSurface.fromName(trackSurface)!!,
            TrackDistance.fromName(trackDistance)!!,
            turnNumber,
        )
    }

    data class ScoredRace(
        val raceData: RaceData,
        val score: Double,
        val fansScore: Double,
        val gradeScore: Double,
        val aptitudeBonus: Double
    )

    data class PlannedRace(
        val raceName: String,
        val date: String,
        val priority: Int
    )

    fun handleDialogs() {
        val dialog: DialogInterface? = DialogUtils.getDialog(imageUtils = game.imageUtils)
        if (dialog == null) {
            return
        }

        when (dialog.name) {
            "strategy" -> {
                if (!game.trainee.bHasUpdatedAptitudes) {
                    game.trainee.bTemporaryRunningStyleAptitudesUpdated = updateRaceScreenRunningStyleAptitudes()
                }

                var runningStyle: RunningStyle? = null
                val runningStyleString: String = getRunningStyleOption().uppercase()
                when (runningStyleString) {
                    // Do not select a strategy. Use what is already selected.
                    "DEFAULT" -> {
                        MessageLog.i(TAG, "[DIALOG] strategy:: Using the default running style.")
                        dialog.ok(imageUtils = game.imageUtils)
                        game.trainee.bHasSetRunningStyle = true
                        return
                    }
                    // Auto-select the optimal running style based on trainee aptitudes.
                    "AUTO" -> {
                        MessageLog.i(TAG, "[DIALOG] strategy:: Auto-selecting the trainee's optimal running style.")
                        runningStyle = game.trainee.runningStyle
                    }
                    else -> {
                        MessageLog.i(TAG, "[DIALOG] strategy:: Using user-specified running style: $runningStyleString")
                        runningStyle = RunningStyle.fromShortName(runningStyleString)
                    }
                }

                when (runningStyle) {
                    RunningStyle.FRONT_RUNNER -> ButtonRaceStrategyFront.click(imageUtils = game.imageUtils)
                    RunningStyle.PACE_CHASER -> ButtonRaceStrategyPace.click(imageUtils = game.imageUtils)
                    RunningStyle.LATE_SURGER -> ButtonRaceStrategyLate.click(imageUtils = game.imageUtils)
                    RunningStyle.END_CLOSER -> ButtonRaceStrategyEnd.click(imageUtils = game.imageUtils)
                    null -> {
                        // This indicates programmer error.
                        MessageLog.e(TAG, "[DIALOG] strategy:: Invalid running style: $runningStyle")
                        dialog.close(imageUtils = game.imageUtils)
                        game.trainee.bHasSetRunningStyle = false
                        return
                    }
                }

                game.trainee.bHasSetRunningStyle = true
                dialog.ok(imageUtils = game.imageUtils)
            }
        }
    }

    /**
     * Retrieves the user's planned races from saved settings.
     *
     * @return A list of [PlannedRace] entries defined by the user, or an empty list if none exist.
     */
    private fun loadUserPlannedRaces(): List<PlannedRace> {
        if (!enableRacingPlan) {
            MessageLog.i(TAG, "[RACE] Racing plan is disabled, returning empty planned races list.")
            return emptyList()
        }

        return try {
            if (racingPlanJson.isEmpty() || racingPlanJson == "[]") {
                MessageLog.i(TAG, "[RACE] User-selected racing plan is empty, returning empty list.")
                return emptyList()
            }

            val jsonArray = JSONArray(racingPlanJson)
            val plannedRaces = mutableListOf<PlannedRace>()

            for (i in 0 until jsonArray.length()) {
                val raceObj = jsonArray.getJSONObject(i)
                val plannedRace = PlannedRace(
                    raceName = raceObj.getString("raceName"),
                    date = raceObj.getString("date"),
                    priority = raceObj.optInt("priority", 0)
                )
                plannedRaces.add(plannedRace)
            }

            MessageLog.i(TAG, "[RACE] Successfully loaded ${plannedRaces.size} user-selected planned races from settings.")
            plannedRaces
        } catch (e: Exception) {
            MessageLog.e(TAG, "Failed to parse user-selected racing plan JSON: ${e.message}. Returning empty list.")
            emptyList()
        }
    }

    /**
     * Loads the complete race database from saved settings, including all race metadata such as names, grades, distances, and turn numbers.
     *
     * @return A map of race names to their [RaceData] or an empty map if racing plan data is missing or invalid.
     */
    private fun loadRaceData(): Map<String, RaceData> {
        return try {
            val racingPlanDataJson = SettingsHelper.getStringSetting("racing", "racingPlanData")
            if (racingPlanDataJson.isEmpty()) {
                MessageLog.i(TAG, "[RACE] Racing plan data is empty, returning empty map.")
                return emptyMap()
            }

            val jsonObject = JSONObject(racingPlanDataJson)
            val raceDataMap = mutableMapOf<String, RaceData>()

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val raceObj = jsonObject.getJSONObject(key)

                val raceData = RaceData(
                    name = raceObj.getString("name"),
                    grade = raceObj.getString("grade"),
                    trackSurface = raceObj.getString("trackSurface"),
                    trackDistance = raceObj.getString("trackDistance"),
                    fans = raceObj.getInt("fans"),
                    turnNumber = raceObj.getInt("turnNumber"),
                    nameFormatted = raceObj.getString("nameFormatted")
                )

                raceDataMap[raceData.name] = raceData
            }

            MessageLog.i(TAG, "[RACE] Successfully loaded ${raceDataMap.size} race entries from racing plan data.")
            raceDataMap
        } catch (e: Exception) {
            MessageLog.e(TAG, "Failed to parse racing plan data JSON: ${e.message}. Returning empty map.")
            emptyMap()
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Handles the test to detect the currently displayed races on the Race List screen.
     */
    fun startRaceListDetectionTest() {
        MessageLog.i(TAG, "\n[TEST] Now beginning detection test on the Race List screen for the currently displayed races.")
        if (game.imageUtils.findImage("race_status").first == null) {
            MessageLog.i(TAG, "[TEST] Bot is not on the Race List screen. Ending the test.")
            return
        }

        // Detect the current date first.
        game.updateDate()

        // Check for all double star predictions.
        val doublePredictionLocations = game.imageUtils.findAll("race_extra_double_prediction")
        MessageLog.i(TAG, "[TEST] Found ${doublePredictionLocations.size} races with double predictions.")
        
        doublePredictionLocations.forEachIndexed { index, location ->
            val raceName = game.imageUtils.extractRaceName(location)
            MessageLog.i(TAG, "[TEST] Race #${index + 1} - Detected name: \"$raceName\".")
            
            // Query database for race details.
            val raceData = lookupRaceInDatabase(game.currentDate.turnNumber, raceName)
            
            if (raceData != null) {
                MessageLog.i(TAG, "[TEST] Race #${index + 1} - Match found:")
                MessageLog.i(TAG, "[TEST]     Name: ${raceData.name}")
                MessageLog.i(TAG, "[TEST]     Grade: ${raceData.grade}")
                MessageLog.i(TAG, "[TEST]     Fans: ${raceData.fans}")
                MessageLog.i(TAG, "[TEST]     Formatted: ${raceData.nameFormatted}")
            } else {
                MessageLog.i(TAG, "[TEST] Race #${index + 1} - No match found for turn ${game.currentDate.turnNumber}")
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // Entry Points
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * The entry point for handling mandatory or extra races.
     *
     * @return True if the mandatory/extra race was completed successfully. Otherwise false.
     */
    fun handleRaceEvents(): Boolean {
        MessageLog.i(TAG, "\n********************")
        MessageLog.i(TAG, "[RACE] Starting Racing process on ${game.printFormattedDate()}.")
        if (encounteredRacingPopup) {
            // Dismiss the insufficient fans popup here and head to the Race Selection screen.
            game.findAndTapImage("race_confirm", tries = 1, region = game.imageUtils.regionBottomHalf)
            encounteredRacingPopup = false
            game.wait(1.0)
            
            // Now check if there is a racing requirement.
            checkRacingRequirements()
        }

        // If there are no races available, cancel the racing process.
        if (game.imageUtils.findImage("race_none_available", tries = 1, region = game.imageUtils.regionMiddle, suppressError = true).first != null) {
            MessageLog.i(TAG, "[RACE] There are no races to compete in. Canceling the racing process and doing something else.")
            MessageLog.i(TAG, "********************")
            return false
        }

        skipRacing = false

        // First, check if there is a mandatory or a extra race available. If so, head into the Race Selection screen.
        // Note: If there is a mandatory race, the bot would be on the Home screen.
        // Otherwise, it would have found itself at the Race Selection screen already (by way of the insufficient fans popup).
        if (game.findAndTapImage("race_select_mandatory", tries = 1, region = game.imageUtils.regionBottomHalf)) {
            return handleMandatoryRace()
        } else if (game.currentDate.phase != "Pre-Debut" && game.findAndTapImage("race_select_extra", tries = 1, region = game.imageUtils.regionBottomHalf)) {
            return handleExtraRace()
        }

        MessageLog.i(TAG, "********************")
        return false
    }

    /**
     * The entry point for handling standalone races if the user started the bot on the Racing screen.
     */
    fun handleStandaloneRace() {
        MessageLog.i(TAG, "\n********************")
        MessageLog.i(TAG, "[RACE] Starting Standalone Racing process...")

        // Skip the race if possible, otherwise run it manually.
        val resultCheck = runRaceWithRetries()
        finalizeRaceResults(resultCheck)

        MessageLog.i(TAG, "[RACE] Racing process for Standalone Race is completed.")
        MessageLog.i(TAG, "********************")
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // Mandatory and Extra Racing Processes
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Handles mandatory race processing.
     * 
     * @return True if the mandatory race was completed successfully, false otherwise.
     */
    private fun handleMandatoryRace(): Boolean {
        MessageLog.i(TAG, "[RACE] Starting process for handling a mandatory race.")

        if (enableStopOnMandatoryRace) {
            MessageLog.i(TAG, "********************")
            detectedMandatoryRaceCheck = true
            return false
        }

        // If there is a popup warning about racing too many times, confirm the popup to continue as this is a mandatory race.
        game.findAndTapImage("ok", tries = 1, region = game.imageUtils.regionMiddle, suppressError = true)
        game.wait(1.0)

        // There is a mandatory race. Now confirm the selection and the resultant popup and then wait for the game to load.
        game.wait(2.0)
        MessageLog.i(TAG, "[RACE] Confirming the mandatory race selection.")
        game.findAndTapImage("race_confirm", tries = 3, region = game.imageUtils.regionBottomHalf)
        game.wait(1.0)
        MessageLog.i(TAG, "[RACE] Confirming any popup from the mandatory race selection.")
        game.findAndTapImage("race_confirm", tries = 3, region = game.imageUtils.regionBottomHalf)
        game.wait(2.0)

        game.waitForLoading()

        // Handle race strategy override if enabled.
        selectRaceStrategy()

        // Skip the race if possible, otherwise run it manually.
        val resultCheck = runRaceWithRetries()
        finalizeRaceResults(resultCheck)

        MessageLog.i(TAG, "[RACE] Racing process for Mandatory Race is completed.")
        MessageLog.i(TAG, "********************")
        return true
    }

    /**
     * Handles extra race processing.
     * 
     * @return True if the extra race was completed successfully, false otherwise.
     */
    private fun handleExtraRace(): Boolean {
        MessageLog.i(TAG, "[RACE] Starting process for handling a extra race.")

        // If there is a popup warning about repeating races 3+ times, stop the process and do something else other than racing.
        if (game.imageUtils.findImage("race_repeat_warning").first != null) {
            if (!enableForceRacing) {
                raceRepeatWarningCheck = true
                MessageLog.i(TAG, "[RACE] Closing popup warning of doing more than 3+ races and setting flag to prevent racing for now. Canceling the racing process and doing something else.")
                game.findAndTapImage("cancel", region = game.imageUtils.regionBottomHalf)
                MessageLog.i(TAG, "********************")
                return false
            } else {
                game.findAndTapImage("ok", tries = 1, region = game.imageUtils.regionMiddle)
                game.wait(1.0)
            }
        }

        // There is a extra race.
        val statusLocation = game.imageUtils.findImage("race_status").first
        if (statusLocation == null) {
            MessageLog.e(TAG, "[ERROR] Unable to determine existence of list of extra races. Canceling the racing process and doing something else.")
            MessageLog.i(TAG, "********************")
            return false
        }

        val maxCount = game.imageUtils.findAll("race_selection_fans", region = game.imageUtils.regionBottomHalf).size
        if (maxCount == 0) {
            // If there is a fan/trophy requirement but no races available, reset the flags and proceed with training to advance the day.
            if (hasFanRequirement || hasTrophyRequirement) {
                MessageLog.i(TAG, "[RACE] Fan/trophy requirement detected but no extra races available. Clearing requirement flags and proceeding with training to advance the day.")
                hasFanRequirement = false
                hasTrophyRequirement = false
            } else {
                MessageLog.e(TAG, "[ERROR] Was unable to find any extra races to select. Canceling the racing process and doing something else.")
            }
            MessageLog.i(TAG, "********************")
            return false
        } else {
            MessageLog.i(TAG, "[RACE] There are $maxCount extra race options currently on screen.")
        }

        if (hasFanRequirement) MessageLog.i(TAG, "[RACE] Fan requirement criteria detected. This race must be completed to meet the requirement.")
        if (hasTrophyRequirement) MessageLog.i(TAG, "[RACE] Trophy requirement criteria detected. Only G1 races will be selected to meet the requirement.")

        // Determine whether to use smart racing with user-selected races or standard racing.
        val useSmartRacing = if (hasFanRequirement) {
            // If fan requirement is needed, force standard racing to ensure the race proceeds.
            false
        } else if (hasTrophyRequirement) {
            // Trophy requirement can use smart racing as it filters to G1 races internally.
            // Use smart racing for all years except Year 1 (Junior Year).
            game.currentDate.year != 1
        } else if (enableRacingPlan && game.currentDate.year != 1) {
            // Year 2 and 3: Use smart racing if conditions are met.
            enableFarmingFans && !enableForceRacing
        } else {
            false
        }

        val success = if (useSmartRacing && game.currentDate.year != 1) {
            // Use the smart racing logic.
            MessageLog.i(TAG, "[RACE] Using smart racing for Year ${game.currentDate.year}.")
            processSmartRacing()
        } else {
            // Use the standard racing logic.
            // If needed, print the reason(s) to why the smart racing logic was not started.
            if (enableRacingPlan && !hasFanRequirement && !hasTrophyRequirement) {
                MessageLog.i(TAG, "[RACE] Smart racing conditions not met due to current settings, using traditional racing logic...")
                MessageLog.i(TAG, "[RACE] Reason: One or more conditions failed:")
                if (game.currentDate.year != 1) {
                    if (!enableFarmingFans) MessageLog.i(TAG, "[RACE]   - enableFarmingFans is false")
                    if (enableForceRacing) MessageLog.i(TAG, "[RACE]   - enableForceRacing is true")
                } else {
                    MessageLog.i(TAG, "[RACE]   - It is currently the Junior Year.")
                }
            }

            processStandardRacing()
        }

        if (!success) return false

        // Confirm the selection and the resultant popup and then wait for the game to load.
        game.findAndTapImage("race_confirm", tries = 30, region = game.imageUtils.regionBottomHalf)
        game.findAndTapImage("race_confirm", tries = 10, region = game.imageUtils.regionBottomHalf)
        game.wait(2.0)

        // Handle race strategy override if enabled.
        selectRaceStrategy()

        // Skip the race if possible, otherwise run it manually.
        val resultCheck = runRaceWithRetries()
        finalizeRaceResults(resultCheck, isExtra = true)

        // Clear the next smart race day tracker since we just completed a race.
        nextSmartRaceDay = null

        MessageLog.i(TAG, "[RACE] Racing process for Extra Race is completed.")
        MessageLog.i(TAG, "********************")
        return true
    }

    /**
     * Handles extra races using Smart Racing logic.
     *
     * @return True if a race was successfully selected and ready to run; false if the process was canceled.
     */
    private fun processSmartRacing(): Boolean {
        MessageLog.i(TAG, "[RACE] Using Smart Racing Plan logic...")

        // Updates the current date and aptitudes for accurate scoring.
        game.updateDate()

        // Use cached user planned races and race plan data.
        MessageLog.i(TAG, "[RACE] Loaded ${userPlannedRaces.size} user-selected races and ${raceData.size} race entries.")

        // Detects all double-star race predictions on screen.
        val doublePredictionLocations = game.imageUtils.findAll("race_extra_double_prediction")
        MessageLog.i(TAG, "[RACE] Found ${doublePredictionLocations.size} double-star prediction locations.")
        if (doublePredictionLocations.isEmpty()) {
            MessageLog.i(TAG, "[RACE] No double-star predictions found. Canceling racing process.")
            return false
        }

        // Extracts race names from the screen and matches them with the in-game database.
        MessageLog.i(TAG, "[RACE] Extracting race names and matching with database...")
        val currentRaces = doublePredictionLocations.mapNotNull { location ->
            val raceName = game.imageUtils.extractRaceName(location)
            val raceData = lookupRaceInDatabase(game.currentDate.turnNumber, raceName)
            if (raceData != null) {
                MessageLog.i(TAG, "[RACE] ✓ Matched in database: ${raceData.name} (Grade: ${raceData.grade}, Fans: ${raceData.fans}, Track Surface: ${raceData.trackSurface}).")
                raceData
            } else {
                MessageLog.i(TAG, "[RACE] ✗ No match found in database for \"$raceName\".")
                null
            }
        }

        if (currentRaces.isEmpty()) {
            MessageLog.i(TAG, "[RACE] No races matched in database. Canceling racing process.")
            return false
        }
        MessageLog.i(TAG, "[RACE] Successfully matched ${currentRaces.size} races in database.")

        // If trophy requirement is active, filter to only G1 races.
        // Trophy requirement is independent of racing plan and farming fans settings.
        val racesForSelection = if (hasTrophyRequirement) {
            val g1Races = currentRaces.filter { it.grade == RaceGrade.G1 }
            if (g1Races.isEmpty()) {
                // No G1 races available. Cancel since trophy requirement specifically needs G1 races.
                MessageLog.i(TAG, "[RACE] Trophy requirement active but no G1 races available. Canceling racing process (independent of racing plan/farming fans).")
                return false
            } else {
                MessageLog.i(TAG, "[RACE] Trophy requirement active. Filtering to ${g1Races.size} G1 races: ${g1Races.map { it.name }}.")
                g1Races
            }
        } else {
            currentRaces
        }

        // Separate matched races into planned vs unplanned.
        val (plannedRaces, regularRaces) = racesForSelection.partition { race ->
            userPlannedRaces.any { it.raceName == race.name }
        }

        // Log which races are user-selected vs regular.
        MessageLog.i(TAG, "[RACE] Found ${plannedRaces.size} user-selected races on screen: ${plannedRaces.map { it.name }}.")
        MessageLog.i(TAG, "[RACE] Found ${regularRaces.size} regular races on screen: ${regularRaces.map { it.name }}.")

        // Filter both lists by user Racing Plan settings.
        // If trophy requirement is active, bypass min fan filtering but still apply other filters.
        val filteredPlannedRaces = if (hasTrophyRequirement) {
            MessageLog.i(TAG, "[RACE] Trophy requirement active. Bypassing min fan threshold for G1 races.")
            filterRacesByCriteria(plannedRaces, bypassMinFans = true)
        } else {
            filterRacesByCriteria(plannedRaces)
        }
        val filteredRegularRaces = if (hasTrophyRequirement) {
            filterRacesByCriteria(regularRaces, bypassMinFans = true)
        } else {
            filterRacesByCriteria(regularRaces)
        }
        MessageLog.i(TAG, "[RACE] After filtering: ${filteredPlannedRaces.size} planned races and ${filteredRegularRaces.size} regular races remain.")

        // Combine all filtered races for Opportunity Cost analysis.
        val allFilteredRaces = filteredPlannedRaces + filteredRegularRaces
        if (allFilteredRaces.isEmpty()) {
            MessageLog.i(TAG, "[RACE] No races match current settings after filtering. Canceling racing process.")
            return false
        }

        // Evaluate whether the bot should race now using Opportunity Cost logic.
        // If trophy requirement is active, bypass opportunity cost to prioritize clearing the requirement.
        if (hasTrophyRequirement) {
            MessageLog.i(TAG, "[RACE] Bypassing opportunity cost analysis to prioritize G1 race due to trophy requirement.")
        } else if (!evaluateOpportunityCost(allFilteredRaces, lookAheadDays)) {
            MessageLog.i(TAG, "[RACE] Smart racing suggests waiting for better opportunities. Canceling racing process.")
            return false
        }

        // Decide which races to score based on availability.
        val racesToScore = if (filteredPlannedRaces.isNotEmpty()) {
            // Prefer planned races, but include regular races for comparison.
            MessageLog.i(TAG, "[RACE] Prioritizing ${filteredPlannedRaces.size} planned races with ${filteredRegularRaces.size} regular races for comparison.")
            filteredPlannedRaces + filteredRegularRaces
        } else {
            // No planned races available, use regular races only.
            MessageLog.i(TAG, "[RACE] No planned races available, using ${filteredRegularRaces.size} regular races only.")
            filteredRegularRaces
        }

        // Score all eligible races with bonus for planned races.
        val scoredRaces = racesToScore.map { race ->
            val baseScore = scoreRace(race)
            if (plannedRaces.contains(race)) {
                // Add a bonus for planned races.
                val bonusScore = baseScore.copy(score = baseScore.score + 50.0)
                MessageLog.i(TAG, "[RACE] Planned race \"${race.name}\" gets a bonus: ${game.decimalFormat.format(baseScore.score)} -> ${game.decimalFormat.format(bonusScore.score)}.")
                bonusScore
            } else {
                baseScore
            }
        }

        // Sort by score and find the best race.
        val sortedScoredRaces = scoredRaces.sortedByDescending { it.score }
        val bestRace = sortedScoredRaces.first()

        MessageLog.i(TAG, "[RACE] Best race selected: ${bestRace.raceData.name} (score: ${game.decimalFormat.format(bestRace.score)}).")
        if (plannedRaces.contains(bestRace.raceData)) {
            MessageLog.i(TAG, "[RACE] Selected race is from user's planned races list.")
        } else {
            MessageLog.i(TAG, "[RACE] Selected race is from regular available races.")
        }

        // Locates the best race on screen and selects it.
        MessageLog.i(TAG, "[RACE] Looking for target race \"${bestRace.raceData.name}\" on screen...")
        val targetRaceLocation = doublePredictionLocations.find { location ->
            val raceName = game.imageUtils.extractRaceName(location)
            val raceData = lookupRaceInDatabase(game.currentDate.turnNumber, raceName)
            val matches = raceData?.name == bestRace.raceData.name
            if (matches) MessageLog.i(TAG, "[RACE] ✓ Found target race at location (${location.x}, ${location.y}).")
            matches
        } ?: run {
            MessageLog.i(TAG, "[RACE] Could not find target race \"${bestRace.raceData.name}\" on screen. Canceling racing process.")
            return false
        }

        MessageLog.i(TAG, "[RACE] Selecting smart racing choice: ${bestRace.raceData.name} (score: ${game.decimalFormat.format(bestRace.score)}).")
        game.tap(targetRaceLocation.x, targetRaceLocation.y, "race_extra_double_prediction", ignoreWaiting = true)

        return true
    }

    /**
     * Handles extra races using the standard or traditional racing logic.
     *
     * @return True if a race was successfully selected; false if the process was canceled.
     */
    private fun processStandardRacing(): Boolean {
        MessageLog.i(TAG, "[RACE] Using traditional racing logic for extra races...")

        // Detects double-star races on screen.
        val doublePredictionLocations = game.imageUtils.findAll("race_extra_double_prediction")
        val maxCount = doublePredictionLocations.size
        if (maxCount == 0) {
            MessageLog.w(TAG, "No extra races found on screen. Canceling racing process.")
            return false
        }

        // If only one race has double predictions, check if it's G1 when trophy requirement is active.
        if (maxCount == 1) {
            if (hasTrophyRequirement) {
                game.updateDate()
                val raceName = game.imageUtils.extractRaceName(doublePredictionLocations[0])
                val raceData = lookupRaceInDatabase(game.currentDate.turnNumber, raceName)
                if (raceData?.grade == RaceGrade.G1) {
                    MessageLog.i(TAG, "[RACE] Only one race with double predictions and it's G1. Selecting it.")
                    game.tap(doublePredictionLocations[0].x, doublePredictionLocations[0].y, "race_extra_double_prediction", ignoreWaiting = true)
                    return true
                } else {
                    // Not G1. Trophy requirement specifically needs G1 races, so cancel.
                    MessageLog.i(TAG, "[RACE] Trophy requirement active but only non-G1 race available. Canceling racing process...")
                    return false
                }
            } else {
                MessageLog.i(TAG, "[RACE] Only one race with double predictions. Selecting it.")
                game.tap(doublePredictionLocations[0].x, doublePredictionLocations[0].y, "race_extra_double_prediction", ignoreWaiting = true)
                return true
            }
        }

        // Otherwise, iterates through each extra race to determine fan gain and double prediction status.
        val (sourceBitmap, templateBitmap) = game.imageUtils.getBitmaps("race_extra_double_prediction")
        val listOfRaces = ArrayList<RaceDetails>()
        val extraRaceLocations = ArrayList<Point>()
        val raceNamesList = ArrayList<String>()

        for (count in 0 until maxCount) {
            val selectedExtraRace = game.imageUtils.findImage("race_extra_selection", region = game.imageUtils.regionBottomHalf).first ?: break
            extraRaceLocations.add(selectedExtraRace)

            // Extract race name for G1 filtering if trophy requirement is active.
            if (hasTrophyRequirement && count < doublePredictionLocations.size) {
                val raceName = game.imageUtils.extractRaceName(doublePredictionLocations[count])
                raceNamesList.add(raceName)
            }

            val raceDetails = game.imageUtils.determineExtraRaceFans(selectedExtraRace, sourceBitmap, templateBitmap!!, forceRacing = enableForceRacing)
            listOfRaces.add(raceDetails)

            if (count + 1 < maxCount) {
                val nextX = if (game.imageUtils.isTablet) {
                    game.imageUtils.relX(selectedExtraRace.x, (-100 * 1.36).toInt())
                } else {
                    game.imageUtils.relX(selectedExtraRace.x, -100)
                }

                val nextY = if (game.imageUtils.isTablet) {
                    game.imageUtils.relY(selectedExtraRace.y, (150 * 1.50).toInt())
                } else {
                    game.imageUtils.relY(selectedExtraRace.y, 150)
                }

                game.tap(nextX.toDouble(), nextY.toDouble(), "race_extra_selection", ignoreWaiting = true)
            }

            game.wait(0.5)
        }

        // If trophy requirement is active, filter to only G1 races.
        val (filteredRaces, filteredLocations, _) = if (hasTrophyRequirement) {
            game.updateDate()
            val g1Indices = raceNamesList.mapIndexedNotNull { index, raceName ->
                val raceData = lookupRaceInDatabase(game.currentDate.turnNumber, raceName)
                if (raceData?.grade == RaceGrade.G1) index else null
            }

            if (g1Indices.isEmpty()) {
                // No G1 races available. Cancel since trophy requirement specifically needs G1 races.
                // Trophy requirement is independent of racing plan and farming fans settings.
                MessageLog.i(TAG, "[RACE] Trophy requirement active but no G1 races available. Canceling racing process (independent of racing plan/farming fans).")
                return false
            } else {
                MessageLog.i(TAG, "[RACE] Trophy requirement active. Filtering to ${g1Indices.size} G1 races.")
                val filtered = g1Indices.map { listOfRaces[it] }
                val filteredLocations = g1Indices.map { extraRaceLocations[it] }
                val filteredNames = g1Indices.map { raceNamesList[it] }
                Triple(filtered, filteredLocations, filteredNames)
            }
        } else {
            Triple(listOfRaces, extraRaceLocations, raceNamesList)
        }

        // Determine max fans and select the appropriate race.
        val maxFans = filteredRaces.maxOfOrNull { it.fans } ?: -1
        if (maxFans == -1) return false
        MessageLog.i(TAG, "[RACE] Number of fans detected for each extra race are: ${filteredRaces.joinToString(", ") { it.fans.toString() }}")

        // Evaluates which race to select based on maximum fans and double prediction priority (if force racing is enabled).
        val index = if (!enableForceRacing) {
            filteredRaces.indexOfFirst { it.fans == maxFans }
        } else {
            filteredRaces.indexOfFirst { it.hasDoublePredictions }.takeIf { it != -1 } ?: filteredRaces.indexOfFirst { it.fans == maxFans }
        }

        // Selects the determined race on screen.
        MessageLog.i(TAG, "[RACE] Selecting extra race at option #${index + 1}.")
        val target = filteredLocations[index]
        game.tap(target.x - game.imageUtils.relWidth((100 * 1.36).toInt()), target.y - game.imageUtils.relHeight(70), "race_extra_selection", ignoreWaiting = true)

        return true
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // Helper Functions
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Check if there are fan or trophy requirements that need to be satisfied.
     */
    fun checkRacingRequirements() {
        // Check for fan requirement on the main screen.
        val sourceBitmap = game.imageUtils.getSourceBitmap()
        val needsFanRequirement = game.imageUtils.findImageWithBitmap("race_fans_criteria", sourceBitmap, region = game.imageUtils.regionTopHalf) != null
        if (needsFanRequirement) {
            hasFanRequirement = true
            MessageLog.i(TAG, "[RACE] Fan requirement criteria detected on main screen. Forcing racing to fulfill requirement.")
        } else {
            // Check for trophy requirement on the main screen.
            val needsTrophyRequirement = game.imageUtils.findImageWithBitmap("race_trophies_criteria", sourceBitmap, region = game.imageUtils.regionTopHalf) != null
            if (needsTrophyRequirement) {
                hasTrophyRequirement = true
                MessageLog.i(TAG, "[RACE] Trophy requirement criteria detected on main screen. Forcing racing to fulfill requirement.")
            }
        }
    }

    /**
     * Determines if the extra racing process should be started now or later.
     *
     * @return True if the current date is okay to start the extra racing process and false otherwise.
     */
    fun checkEligibilityToStartExtraRacingProcess(): Boolean {
        MessageLog.i(TAG, "\n[RACE] Now determining eligibility to start the extra racing process...")
        val turnsRemaining = game.imageUtils.determineTurnsRemainingBeforeNextGoal()
        MessageLog.i(TAG, "[RACE] Current remaining number of days before the next mandatory race: $turnsRemaining.")

        // If the setting to force racing extra races is enabled, always return true.
        if (enableForceRacing) return true

        // Check for common restrictions that apply to both smart and standard racing via screen checks.
        val sourceBitmap = game.imageUtils.getSourceBitmap()
        if (game.imageUtils.findImageWithBitmap("race_select_extra_locked_uma_finals", sourceBitmap, region = game.imageUtils.regionBottomHalf) != null) {
            MessageLog.i(TAG, "[RACE] It is UMA Finals right now so there will be no extra races. Stopping extra race check.")
            return false
        } else if (game.imageUtils.findImageWithBitmap("race_select_extra_locked", sourceBitmap, region = game.imageUtils.regionBottomHalf) != null) {
            MessageLog.i(TAG, "[RACE] Extra Races button is currently locked. Stopping extra race check.")
            return false
        } else if (game.imageUtils.findImageWithBitmap("recover_energy_summer", sourceBitmap, region = game.imageUtils.regionBottomHalf) != null) {
            MessageLog.i(TAG, "[RACE] It is currently Summer right now. Stopping extra race check.")
            return false
        }

        // For Classic and Senior Year, check if planned races are coming up in the look-ahead window and are eligible for racing.
        if (enableFarmingFans && enableRacingPlan && game.currentDate.year != 1) {
            // Handle the user-selected planned races here.
            if (userPlannedRaces.isNotEmpty()) {
                val currentTurnNumber = game.currentDate.turnNumber

                // Check each planned race for eligibility.
                val eligiblePlannedRaces = userPlannedRaces.filter { plannedRace ->
                    val raceDetails = raceData[plannedRace.raceName]
                    if (raceDetails == null) {
                        MessageLog.e(TAG, "[ERROR] Planned race \"${plannedRace.raceName}\" not found in race data.")
                        false
                    } else {
                        val turnDistance = raceDetails.turnNumber - currentTurnNumber

                        // Check if race is within look-ahead window.
                        if (turnDistance < 0 || turnDistance > lookAheadDays) {
                            if (turnDistance > lookAheadDays) {
                                if (game.debugMode) {
                                    MessageLog.d(TAG, "[DEBUG] Planned race \"${plannedRace.raceName}\" is too far ahead of the look-ahead window (distance $turnDistance > lookAheadDays $lookAheadDays).")
                                } else {
                                    Log.d(TAG, "[DEBUG] Planned race \"${plannedRace.raceName}\" is too far ahead of the look-ahead window (distance $turnDistance > lookAheadDays $lookAheadDays).")
                                }
                            }
                            false
                        } else {
                            // For Classic Year, check if it's an eligible racing day.
                            if (game.currentDate.year == 2) {
                                val isEligible = turnsRemaining % daysToRunExtraRaces == 0
                                if (!isEligible) {
                                    MessageLog.i(TAG, "[RACE] Planned race \"${plannedRace.raceName}\" is not on an eligible racing day (day $turnsRemaining, interval $daysToRunExtraRaces).")
                                }
                                isEligible
                            } else {
                                true
                            }
                        }
                    }
                }

                if (eligiblePlannedRaces.isEmpty()) {
                    MessageLog.i(TAG, "[RACE] No user-selected races are eligible at turn $currentTurnNumber. Continuing with other checks.")
                } else {
                    MessageLog.i(TAG, "[RACE] Found ${eligiblePlannedRaces.size} eligible user-selected races: ${eligiblePlannedRaces.map { it.raceName }}.")
                }
            } else {
                MessageLog.i(TAG, "[RACE] No user-selected races configured. Continuing with other checks.")
            }
        }

        // If fan or trophy requirement is detected, bypass smart racing logic to force racing.
        // Both requirements are independent of racing plan and farming fans settings.
        if (hasFanRequirement) {
            MessageLog.i(TAG, "[RACE] Fan requirement detected. Bypassing smart racing logic to fulfill requirement.")
            return !raceRepeatWarningCheck
        } else if (hasTrophyRequirement) {
            // Check if G1 races exist at current turn before proceeding.
            // If no G1 races are available, it will still allow regular racing if it's a regular race day or smart racing day.
            if (!hasG1RacesAtTurn(game.currentDate.turnNumber)) {
                val isRegularRacingDay = enableFarmingFans && (turnsRemaining % daysToRunExtraRaces == 0)
                val isSmartRacingDay = enableRacingPlan && enableFarmingFans && nextSmartRaceDay == turnsRemaining

                if (isRegularRacingDay || isSmartRacingDay) {
                    MessageLog.i(TAG, "[RACE] Trophy requirement detected but no G1 races at turn ${game.currentDate.turnNumber}. Allowing regular racing on eligible day.")
                } else {
                    MessageLog.i(TAG, "[RACE] Trophy requirement detected but no G1 races available at turn ${game.currentDate.turnNumber} and not a regular/smart racing day. Skipping racing.")
                    return false
                }
            } else {
                MessageLog.i(TAG, "[RACE] Trophy requirement detected. G1 races available at turn ${game.currentDate.turnNumber}. Proceeding to racing screen.")
            }

            return !raceRepeatWarningCheck
        } else if (enableRacingPlan && enableFarmingFans) {
            // Smart racing: Check turn-based eligibility before screen checks.
            // Only run opportunity cost analysis with smartRacingCheckInterval.
            val isCheckInterval = game.currentDate.turnNumber % smartRacingCheckInterval == 0

            if (isCheckInterval) {
                MessageLog.i(TAG, "[RACE] Running opportunity cost analysis at turn ${game.currentDate.turnNumber} (smartRacingCheckInterval: every $smartRacingCheckInterval turns)...")

                // Check if there are any races available at the current turn.
                val currentTurnRaces = queryRacesFromDatabase(game.currentDate.turnNumber, 0)
                if (currentTurnRaces.isEmpty()) {
                    MessageLog.i(TAG, "[RACE] No races available at turn ${game.currentDate.turnNumber}.")
                    return false
                }

                MessageLog.i(TAG, "[RACE] Found ${currentTurnRaces.size} race(s) at the current turn ${game.currentDate.turnNumber}.")

                // Query upcoming races in the look-ahead window for opportunity cost analysis.
                val upcomingRaces = queryRacesFromDatabase(game.currentDate.turnNumber + 1, lookAheadDays)
                MessageLog.i(TAG, "[RACE] Found ${upcomingRaces.size} upcoming races in look-ahead window.")

                // Apply filters to both current and upcoming races.
                val filteredCurrentRaces = filterRacesByCriteria(currentTurnRaces)
                val filteredUpcomingRaces = filterRacesByCriteria(upcomingRaces)

                MessageLog.i(TAG, "[RACE] After filtering: ${filteredCurrentRaces.size} current races, ${filteredUpcomingRaces.size} upcoming races.")

                // If no filtered current races exist, we shouldn't race.
                if (filteredCurrentRaces.isEmpty()) {
                    MessageLog.i(TAG, "[RACE] No current races match the filter criteria. Skipping racing.")
                    return false
                }

                // If there are no upcoming races to compare against, race now if we have acceptable races.
                if (filteredUpcomingRaces.isEmpty()) {
                    MessageLog.i(TAG, "[RACE] No upcoming races to compare against. Racing now with available races.")
                    nextSmartRaceDay = turnsRemaining
                } else {
                    // Use opportunity cost logic to determine if we should race now or wait.
                    val shouldRace = evaluateOpportunityCost(filteredCurrentRaces, lookAheadDays)
                    if (!shouldRace) {
                        MessageLog.i(TAG, "[RACE] No suitable races at turn ${game.currentDate.turnNumber} based on opportunity cost analysis.")
                        return false
                    }

                    // Opportunity cost analysis determined we should race now, so set the optimal race day to the current day.
                    nextSmartRaceDay = turnsRemaining
                }

                MessageLog.i(TAG, "[RACE] Opportunity cost analysis completed, proceeding with screen checks...")
            } else {
                MessageLog.i(TAG, "[RACE] Skipping opportunity cost analysis (turn ${game.currentDate.turnNumber} does not match smartRacingCheckInterval). Using cached optimal race day.")
            }

            // Check if current day matches the optimal race day or falls on the interval.
            val isOptimalDay = nextSmartRaceDay == turnsRemaining
            val isIntervalDay = turnsRemaining % daysToRunExtraRaces == 0

            if (isOptimalDay) {
                MessageLog.i(TAG, "[RACE] Current day ($turnsRemaining) matches optimal race day.")
                return !raceRepeatWarningCheck
            } else if (isIntervalDay) {
                MessageLog.i(TAG, "[RACE] Current day ($turnsRemaining) falls on racing interval ($daysToRunExtraRaces).")
                return !raceRepeatWarningCheck
            } else {
                MessageLog.i(TAG, "[RACE] Current day ($turnsRemaining) is not optimal (next: $nextSmartRaceDay, interval: $daysToRunExtraRaces).")
                return false
            }
        }

        // Conditionally start the standard racing process.
        return enableFarmingFans && (turnsRemaining % daysToRunExtraRaces == 0) && !raceRepeatWarningCheck
    }

    fun getRunningStyleOption(): String {
        val currentYear = game.currentDate.year
        return if (currentYear == 1) juniorYearRaceStrategy else userSelectedOriginalStrategy
    }

    fun updateRaceScreenRunningStyleAptitudes(): Boolean {
        val bitmap = game.imageUtils.getSourceBitmap()
        var text: String = game.imageUtils.performOCROnRegion(
            bitmap,
            125,
            1140,
            825,
            45,
            useThreshold=false,
            useGrayscale=false,
            debugName="updateRaceScreenRunningStyleAptitudes",
        )
        if (text == "") {
            MessageLog.w(TAG, "performOCROnRegion did not detect any text.")
            return false
        }
        text = text.replace("[^A-Za-z]".toRegex(), "").lowercase()
        val substrings = listOf("end", "late", "pace", "front")
        val parts = text.split(*substrings.toTypedArray()).filter { it.isNotBlank() }
        if (parts.size != 4) {
            MessageLog.w(TAG, "performOCROnRegion returned a malformed string: $text")
            return false
        }
        val styleMap: Map<String, String> = substrings.zip(parts).toMap()
        for ((styleString, aptitudeString) in styleMap) {
            val style: RunningStyle? = RunningStyle.fromShortName(styleString)
            if (style == null) {
                MessageLog.w(TAG, "performOCROnRegion returned invalid running style: $styleString")
                return false
            }
            val aptitude: Aptitude? = Aptitude.fromName(aptitudeString)
            if (aptitude == null) {
                MessageLog.w(TAG, "performOCROnRegion returned invalid aptitude for running style: $style -> $aptitudeString")
                return false
            }
            game.trainee.setRunningStyleAptitude(style, aptitude)
        }

        MessageLog.d(TAG, "Set temporary running style aptitudes: ${game.trainee.runningStyleAptitudes}")
        return true
    }

    /**
     * Handles race strategy override for Junior Year races.
     *
     * During Junior Year: Applies the user-selected strategy and stores the original.
     * After Junior Year: Restores the original strategy and disables the feature.
     */
    private fun selectRaceStrategy() {
        if (
            !game.trainee.bHasUpdatedAptitudes &&
            !game.trainee.bTemporaryRunningStyleAptitudesUpdated
        ) {
            // If trainee aptitudes are unknown, this means we probably started the bot
            // at the race screen. We need to open the race strategy dialog and
            // read the aptitudes in from there.
            ButtonChangeRunningStyle.click(imageUtils = game.imageUtils)
            handleDialogs()
        } else if (!game.trainee.bHasSetRunningStyle) {
            // If we haven't set the trainee's running style yet, open the dialog.
            ButtonChangeRunningStyle.click(imageUtils = game.imageUtils)
            handleDialogs()
        }
    }

    /**
     * Executes the race with retry logic.
     *
     * @return True if the bot completed the race with retry attempts remaining. Otherwise false.
     */
    fun runRaceWithRetries(): Boolean {
        val canSkip = game.imageUtils.findImage("race_skip_locked", tries = 5, region = game.imageUtils.regionBottomHalf).first == null
        
        while (raceRetries >= 0) {
            if (canSkip) {
                MessageLog.i(TAG, "[RACE] Skipping race...")

                // Press the skip button and then wait for your result of the race to show.
                if (game.findAndTapImage("race_skip", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Race was able to be skipped.")
                }
                game.wait(2.0)

                // Now tap on the screen to get past the Race Result screen.
                game.tap(350.0, 450.0, "ok", taps = 3)

                // Check if the race needed to be retried.
                if (game.imageUtils.findImage("race_retry", tries = 5, region = game.imageUtils.regionBottomHalf, suppressError = true).first != null) {
                    if (disableRaceRetries) {
                        MessageLog.i(TAG, "\n[END] Stopping the bot due to failing a mandatory race.")
                        MessageLog.i(TAG, "********************")
                        game.notificationMessage = "Stopping the bot due to failing a mandatory race."
                        throw IllegalStateException()
                    }
                    game.findAndTapImage("race_retry", tries = 1, region = game.imageUtils.regionBottomHalf, suppressError = true)
                    MessageLog.i(TAG, "[RACE] The skipped race failed and needs to be run again. Attempting to retry...")
                    game.wait(3.0)
                    raceRetries--
                } else {
                    return true
                }
            } else {
                MessageLog.i(TAG, "[RACE] Skipping manual race...")

                // Press the manual button.
                if (game.findAndTapImage("race_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Started the manual race.")
                }
                game.wait(2.0)

                // Confirm the Race Playback popup if it appears.
                if (game.findAndTapImage("ok", tries = 1, region = game.imageUtils.regionMiddle, suppressError = true)) {
                    MessageLog.i(TAG, "[RACE] Confirmed the Race Playback popup.")
                    game.wait(5.0)
                }

                game.waitForLoading()

                // Now press the confirm button to get past the list of participants.
                if (game.findAndTapImage("race_confirm", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Dismissed the list of participants.")
                }
                game.waitForLoading()
                game.wait(1.0)
                game.waitForLoading()
                game.wait(1.0)

                // Skip the part where it reveals the name of the race.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped the name reveal of the race.")
                }
                // Skip the walkthrough of the starting gate.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped the walkthrough of the starting gate.")
                }
                game.wait(3.0)
                // Skip the start of the race.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped the start of the race.")
                }
                // Skip the lead up to the finish line.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped the lead up to the finish line.")
                }
                game.wait(2.0)
                // Skip crossing the finish line.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped crossing the finish line.")
                }
                game.wait(2.0)
                // Skip the result screen.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped the results screen.")
                }
                game.wait(2.0)

                game.waitForLoading()
                game.wait(1.0)

                // Check if the race needed to be retried.
                if (game.imageUtils.findImage("race_retry", tries = 5, region = game.imageUtils.regionBottomHalf, suppressError = true).first != null) {
                    if (disableRaceRetries) {
                        MessageLog.i(TAG, "\n[END] Stopping the bot due to failing a mandatory race.")
                        MessageLog.i(TAG, "********************")
                        game.notificationMessage = "Stopping the bot due to failing a mandatory race."
                        throw IllegalStateException()
                    }
                    game.findAndTapImage("race_retry", tries = 1, region = game.imageUtils.regionBottomHalf, suppressError = true)
                    MessageLog.i(TAG, "[RACE] Manual race failed and needs to be run again. Attempting to retry...")
                    game.wait(5.0)
                    raceRetries--
                } else {
                    // Check if a Trophy was acquired.
                    if (game.findAndTapImage("close", tries = 5, region = game.imageUtils.regionBottomHalf)) {
                        MessageLog.i(TAG, "[RACE] Closing popup to claim trophy...")
                    }

                    return true
                }
            }
        }

        return false
    }

    /**
     * Finishes up and confirms the results of the race and its success.
     *
     * @param resultCheck Flag to see if the race was completed successfully. Throws an IllegalStateException if it did not.
     * @param isExtra Flag to determine the following actions to finish up this mandatory or extra race.
     */
    fun finalizeRaceResults(resultCheck: Boolean, isExtra: Boolean = false) {
        MessageLog.e(TAG, "finalizeRaceResults")
        MessageLog.i(TAG, "\n[RACE] Now performing cleanup and finishing the race.")
        if (!resultCheck) {
            game.notificationMessage = "Bot has run out of retry attempts for racing. Stopping the bot now..."
            throw IllegalStateException()
        }

        // Bot will be at the screen where it shows the final positions of all participants.
        // Press the confirm button and wait to see the triangle of fans.
        MessageLog.i(TAG, "[RACE] Now attempting to confirm the final positions of all participants and number of gained fans")
        if (game.findAndTapImage("next", tries = 30, region = game.imageUtils.regionBottomHalf)) {
            game.wait(0.5)

            // Now tap on the screen to get to the next screen.
            game.tap(350.0, 750.0, "ok", taps = 3)

            // Now press the end button to finish the race.
            game.findAndTapImage("race_end", tries = 30, region = game.imageUtils.regionBottomHalf)

            if (!isExtra) {
                MessageLog.i(TAG, "[RACE] Seeing if a Training Goal popup will appear.")
                // Wait until the popup showing the completion of a Training Goal appears and confirm it.
                // There will be dialog before it so the delay should be longer.
                game.wait(5.0)
                if (game.findAndTapImage("next", tries = 10, region = game.imageUtils.regionBottomHalf)) {
                    game.wait(2.0)

                    // Now confirm the completion of a Training Goal popup.
                    if (game.scenario != "Unity Cup") {
                        MessageLog.i(TAG, "[RACE] There was a Training Goal popup. Confirming it now.")
                        game.findAndTapImage("next", tries = 10, region = game.imageUtils.regionBottomHalf)
                    }
                }
            } else if (game.findAndTapImage("next", tries = 10, region = game.imageUtils.regionBottomHalf)) {
                // Same as above but without the longer delay.
                game.wait(2.0)
                game.findAndTapImage("race_end", tries = 10, region = game.imageUtils.regionBottomHalf)
            }

            firstTimeRacing = false
            hasFanRequirement = false
            hasTrophyRequirement = false
        } else {
            MessageLog.e(TAG, "Cannot start the cleanup process for finishing the race. Moving on...")
        }
    }

    /**
     * Race database lookup using exact and/or fuzzy matching.
     * 
     * @param turnNumber The current turn number to match against.
     * @param detectedName The race name detected by OCR.
     * @return A [RaceData] object if a match is found, null otherwise.
     */
    private fun lookupRaceInDatabase(turnNumber: Int, detectedName: String): RaceData? {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for race lookup.")
            return null
        }

        return try {
            MessageLog.i(TAG, "[RACE] Looking up race for turn $turnNumber with detected name: \"$detectedName\".")
            
            val database = settingsManager.getDatabase()
            if (database == null) {
                settingsManager.close()
                return null
            }
            
            // Do exact matching based on the info gathered.
            val exactCursor = database.query(
                TABLE_RACES,
                arrayOf(
                    RACES_COLUMN_NAME,
                    RACES_COLUMN_GRADE,
                    RACES_COLUMN_FANS,
                    RACES_COLUMN_NAME_FORMATTED,
                    RACES_COLUMN_TRACK_SURFACE,
                    RACES_COLUMN_TRACK_DISTANCE,
                    RACES_COLUMN_TURN_NUMBER
                ),
                "$RACES_COLUMN_TURN_NUMBER = ? AND $RACES_COLUMN_NAME_FORMATTED = ?",
                arrayOf(turnNumber.toString(), detectedName),
                null, null, null
            )

            if (exactCursor.moveToFirst()) {
                val race = RaceData(
                    name = exactCursor.getString(0),
                    grade = exactCursor.getString(1),
                    fans = exactCursor.getInt(2),
                    nameFormatted = exactCursor.getString(3),
                    trackSurface = exactCursor.getString(4),
                    trackDistance = exactCursor.getString(5),
                    turnNumber = exactCursor.getInt(6)
                )
                exactCursor.close()
                settingsManager.close()
                MessageLog.i(TAG, "[RACE] Found exact match: \"${race.name}\" AKA \"${race.nameFormatted}\".")
                return race
            }
            exactCursor.close()
            
            // Otherwise, do fuzzy matching to find the most similar match using Jaro-Winkler.
            val fuzzyCursor = database.query(
                TABLE_RACES,
                arrayOf(
                    RACES_COLUMN_NAME,
                    RACES_COLUMN_GRADE,
                    RACES_COLUMN_FANS,
                    RACES_COLUMN_NAME_FORMATTED,
                    RACES_COLUMN_TRACK_SURFACE,
                    RACES_COLUMN_TRACK_DISTANCE,
                    RACES_COLUMN_TURN_NUMBER
                ),
                "$RACES_COLUMN_TURN_NUMBER = ?",
                arrayOf(turnNumber.toString()),
                null, null, null
            )

            if (!fuzzyCursor.moveToFirst()) {
                fuzzyCursor.close()
                settingsManager.close()
                MessageLog.i(TAG, "[RACE] No match found for turn $turnNumber with name \"$detectedName\".")
                return null
            }

            val similarityService = StringSimilarityServiceImpl(JaroWinklerStrategy())
            var bestMatch: RaceData? = null
            var bestScore = 0.0

            do {
                val nameFormatted = fuzzyCursor.getString(3)
                val similarity = similarityService.score(detectedName, nameFormatted)
                
                if (similarity > bestScore && similarity >= SIMILARITY_THRESHOLD) {
                    bestScore = similarity
                    bestMatch = RaceData(
                        name = fuzzyCursor.getString(0),
                        grade = fuzzyCursor.getString(1),
                        fans = fuzzyCursor.getInt(2),
                        nameFormatted = nameFormatted,
                        trackSurface = fuzzyCursor.getString(4),
                        trackDistance = fuzzyCursor.getString(5),
                        turnNumber = fuzzyCursor.getInt(6)
                    )
                    if (game.debugMode) MessageLog.d(TAG, "[DEBUG] Fuzzy match candidate: \"${bestMatch.name}\" AKA \"$nameFormatted\" with similarity ${game.decimalFormat.format(similarity)}.")
                    else Log.d(TAG, "[DEBUG] Fuzzy match candidate: \"${bestMatch.name}\" AKA \"$nameFormatted\" with similarity ${game.decimalFormat.format(similarity)}.")
                }
            } while (fuzzyCursor.moveToNext())

            fuzzyCursor.close()
            settingsManager.close()
            
            if (bestMatch != null) {
                MessageLog.i(TAG, "[RACE] Found fuzzy match: \"${bestMatch.name}\" AKA \"${bestMatch.nameFormatted}\" with similarity ${game.decimalFormat.format(bestScore)}.")
                return bestMatch
            }
            
            MessageLog.i(TAG, "[RACE] No match found for turn $turnNumber with name \"$detectedName\".")
            null
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] Error looking up race: ${e.message}.")
            settingsManager.close()
            null
        }
    }

    /**
     * Calculates a composite race score based on fan count, race grade, and aptitude performance.
     *
     * The score is derived from three weighted factors:
     * - **Fans:** Normalized to a 0–100 scale.
     * - **Grade:** Weighted to a map of values based on grade.
     * - **Aptitude:** Adds a bonus if both track surface and distance aptitudes are A or S.
     *
     * The final score is the average of these three components.
     *
     * @param race The [RaceData] instance to evaluate.
     * @return A [ScoredRace] object containing the final score and individual factor breakdowns.
     */
    private fun scoreRace(race: RaceData): ScoredRace {
        // Normalize fans to 0-100 scale (assuming max fans is 30000).
        val fansScore = (race.fans.toDouble() / 30000.0) * 100.0
        
        // Grade scoring: G1 = 75, G2 = 50, G3 = 25.
        val gradeScore = when (race.grade) {
            RaceGrade.G1 -> 75.0
            RaceGrade.G2 -> 50.0
            RaceGrade.G3 -> 25.0
            else -> 0.0
        }
        
        // Get the trainee's aptitude for this race's track surface/distance.
        val trackSurfaceAptitude: Aptitude = game.trainee.checkTrackSurfaceAptitude(race.trackSurface)
        val trackDistanceAptitude: Aptitude = game.trainee.checkTrackDistanceAptitude(race.trackDistance)
        
        // Aptitude bonus: 100 if both track surface and distance match A/S, else 0.
        val trackSurfaceMatch: Boolean = trackSurfaceAptitude >= Aptitude.A
        val trackDistanceMatch: Boolean = trackDistanceAptitude >= Aptitude.A

        val aptitudeBonus = if (trackSurfaceMatch && trackDistanceMatch) 100.0 else 0.0
        
        // Calculate final score with equal weights.
        val finalScore = (fansScore + gradeScore + aptitudeBonus) / 3.0
        
        // Log detailed scoring breakdown for debugging.
        if (game.debugMode) MessageLog.d(
            TAG,
            """
            [DEBUG] Scoring ${race.name}:
            Fans            = ${race.fans} (${game.decimalFormat.format(fansScore)})
            Grade           = ${race.grade} (${game.decimalFormat.format(gradeScore)})
            Track Surface   = ${race.trackSurface} ($trackSurfaceAptitude)
            Track Distance  = ${race.trackDistance} ($trackDistanceAptitude)
            Aptitude        = ${game.decimalFormat.format(aptitudeBonus)}
            Final           = ${game.decimalFormat.format(finalScore)}
            """.trimIndent()
        )
        
        return ScoredRace(
            raceData = race,
            score = finalScore,
            fansScore = fansScore,
            gradeScore = gradeScore,
            aptitudeBonus = aptitudeBonus
        )
    }

    /**
     * Database queries for races.
     *
     * @param currentTurn The current turn number used as the starting point.
     * @param lookAheadDays The number of days (turns) to look ahead for upcoming races.
     * @return A list of [RaceData] objects representing all races within the look-ahead window.
     */
    private fun queryRacesFromDatabase(currentTurn: Int, lookAheadDays: Int): List<RaceData> {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for race lookup.")
            return emptyList()
        }

        return try {
            val database = settingsManager.getDatabase()
            if (database == null) {
                MessageLog.e(TAG, "[ERROR] Database is null for race lookup.")
                return emptyList()
            }

            val endTurn = currentTurn + lookAheadDays
            val cursor = database.query(
                TABLE_RACES,
                arrayOf(
                    RACES_COLUMN_NAME,
                    RACES_COLUMN_GRADE,
                    RACES_COLUMN_FANS,
                    RACES_COLUMN_NAME_FORMATTED,
                    RACES_COLUMN_TRACK_SURFACE,
                    RACES_COLUMN_TRACK_DISTANCE,
                    RACES_COLUMN_TURN_NUMBER
                ),
                "$RACES_COLUMN_TURN_NUMBER >= ? AND $RACES_COLUMN_TURN_NUMBER <= ?",
                arrayOf(currentTurn.toString(), endTurn.toString()),
                null, null, "$RACES_COLUMN_TURN_NUMBER ASC"
            )

            val races = mutableListOf<RaceData>()
            if (cursor.moveToFirst()) {
                do {
                    val race = RaceData(
                        name = cursor.getString(0),
                        grade = cursor.getString(1),
                        fans = cursor.getInt(2),
                        nameFormatted = cursor.getString(3),
                        trackSurface = cursor.getString(4),
                        trackDistance = cursor.getString(5),
                        turnNumber = cursor.getInt(6)
                    )
                    races.add(race)
                } while (cursor.moveToNext())
            }
            cursor.close()
            settingsManager.close()
            
            MessageLog.i(TAG, "[RACE] Found ${races.size} races in look-ahead window (turns $currentTurn to $endTurn).")
            races
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] Error getting races from database: ${e.message}")
            settingsManager.close()
            emptyList()
        }
    }

    /**
     * Checks if any G1 races exist at the specified turn number in the database.
     *
     * @param turnNumber The turn number to check for G1 races.
     * @return True if at least one G1 race exists at the specified turn, false otherwise.
     */
    private fun hasG1RacesAtTurn(turnNumber: Int): Boolean {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            MessageLog.e(TAG, "[ERROR] Database not available for G1 race check.")
            return false
        }

        return try {
            val database = settingsManager.getDatabase()
            if (database == null) {
                MessageLog.e(TAG, "[ERROR] Database is null for G1 race check.")
                return false
            }

            val cursor = database.query(
                TABLE_RACES,
                arrayOf(RACES_COLUMN_GRADE),
                "$RACES_COLUMN_TURN_NUMBER = ? AND $RACES_COLUMN_GRADE = ?",
                arrayOf(turnNumber.toString(), "G1"),
                null, null, null
            )

            val hasG1 = cursor.count > 0
            cursor.close()
            settingsManager.close()
            
            hasG1
        } catch (e: Exception) {
            MessageLog.e(TAG, "[ERROR] Error checking for G1 races: ${e.message}")
            settingsManager.close()
            false
        }
    }

    /**
     * Filters the given list of races according to the user's Racing Plan settings.
     *
     * @param races The list of [RaceData] entries to filter.
     * @param bypassMinFans If true, bypasses the minimum fans threshold check (useful for trophy requirement).
     * @return A list of [RaceData] objects that satisfy all Racing Plan filter criteria.
     */
    private fun filterRacesByCriteria(races: List<RaceData>, bypassMinFans: Boolean = false): List<RaceData> {
        // Parse preferred grades from JSON array string.
        MessageLog.i(TAG, "[RACE] Raw preferred grades string: \"$preferredGradesString\".")
        val preferredGrades = try {
            // Parse as JSON array.
            val jsonArray = JSONArray(preferredGradesString)
            val parsed = (0 until jsonArray.length()).map { jsonArray.getString(it) }
            MessageLog.i(TAG, "[RACE] Parsed as JSON array: $parsed.")
            parsed
        } catch (e: Exception) {
            MessageLog.i(TAG, "[RACE] Error parsing preferred grades: ${e.message}, using fallback.")
            val parsed = preferredGradesString.split(",").map { it.trim() }
            MessageLog.i(TAG, "[RACE] Fallback parsing result: $parsed")
            parsed
        }

        if (game.debugMode) MessageLog.d(TAG, "[DEBUG] Filter criteria: Min fans: $minFansThreshold, track surface: $preferredTrackSurfaceString, grades: $preferredGrades")
        else Log.d(TAG, "[DEBUG] Filter criteria: Min fans: $minFansThreshold, trackSurface: $preferredTrackSurfaceString, grades: $preferredGrades")
        
        val filteredRaces = races.filter { race ->
            val meetsFansThreshold = bypassMinFans || race.fans >= minFansThreshold
            val meetsTrackSurfacePreference = preferredTrackSurfaceString == "Any" || race.trackSurface == TrackSurface.fromName(preferredTrackSurfaceString)
            val meetsGradePreference = preferredGrades.isEmpty() || preferredGrades.contains(race.grade.name)
            
            val passes = meetsFansThreshold && meetsTrackSurfacePreference && meetsGradePreference

            // If the race did not pass any of the filters, print the reason why.
            if (!passes) {
                val reasons = mutableListOf<String>()
                if (!meetsFansThreshold) reasons.add("fans ${race.fans} < $minFansThreshold")
                if (!meetsTrackSurfacePreference) reasons.add("trackSurface ${race.trackSurface} != $preferredTrackSurfaceString")
                if (!meetsGradePreference) reasons.add("grade ${race.grade} not in $preferredGrades")
                if (game.debugMode) MessageLog.d(TAG, "[DEBUG] ✗ Filtered out ${race.name}: ${reasons.joinToString(", ")}")
                else Log.d(TAG, "[DEBUG] ✗ Filtered out ${race.name}: ${reasons.joinToString(", ")}")
            } else {
                if (game.debugMode) MessageLog.d(TAG, "[DEBUG] ✓ Passed filter: ${race.name} (fans: ${race.fans}, trackSurface: ${race.trackSurface}, grade: ${race.grade})")
                else Log.d(TAG, "[DEBUG] ✓ Passed filter: ${race.name} (fans: ${race.fans}, trackSurface: ${race.trackSurface}, grade: ${race.grade})")
            }
            
            passes
        }
        
        return filteredRaces
    }

    /**
     * Evaluates opportunity cost to determine whether the bot should race immediately or wait for a better opportunity.
     *
     * @param currentRaces List of currently available [RaceData] races.
     * @param lookAheadDays Number of turns/days to consider for upcoming races.
     * @return True if the bot should race now, false if it is better to wait for a future race.
     */
    private fun evaluateOpportunityCost(currentRaces: List<RaceData>, lookAheadDays: Int): Boolean {
        MessageLog.i(TAG, "[RACE] Evaluating whether to race now using Opportunity Cost logic...")
        if (currentRaces.isEmpty()) {
            MessageLog.i(TAG, "[RACE] No current races available, cannot race now.")
            return false
        }
        
        // Score current races.
        MessageLog.i(TAG, "[RACE] Scoring ${currentRaces.size} current races (sorted by score descending):")
        val currentScoredRaces = currentRaces.map { scoreRace(it) }
        val sortedScoredRaces = currentScoredRaces.sortedByDescending { it.score }
        sortedScoredRaces.forEach { scoredRace ->
            MessageLog.i(TAG, "[RACE]     Current race: ${scoredRace.raceData.name} (score: ${game.decimalFormat.format(scoredRace.score)})")
        }
        val bestCurrentRace = sortedScoredRaces.maxByOrNull { it.score }
        
        if (bestCurrentRace == null) {
            MessageLog.i(TAG, "[RACE] Failed to score current races, cannot race now.")
            return false
        }
        
        MessageLog.i(TAG, "[RACE] Best current race: ${bestCurrentRace.raceData.name} (score: ${game.decimalFormat.format(bestCurrentRace.score)})")
        
        // Get and score upcoming races.
        MessageLog.i(TAG, "[RACE] Looking ahead $lookAheadDays days for upcoming races...")
        val upcomingRaces = queryRacesFromDatabase(game.currentDate.turnNumber + 1, lookAheadDays)
        MessageLog.i(TAG, "[RACE] Found ${upcomingRaces.size} upcoming races in database.")
        
        val filteredUpcomingRaces = filterRacesByCriteria(upcomingRaces)
        MessageLog.i(TAG, "[RACE] After filtering: ${filteredUpcomingRaces.size} upcoming races remain.")
        
        if (filteredUpcomingRaces.isEmpty()) {
            MessageLog.i(TAG, "[RACE] No suitable upcoming races found, racing now with best current option.")
            return true
        }
        
        // Score all upcoming races and find the best one.
        val scoredUpcomingRaces = filteredUpcomingRaces.map { scoreRace(it) }
        val sortedUpcomingScoredRaces = scoredUpcomingRaces.sortedByDescending { it.score }
        val bestUpcomingRace = sortedUpcomingScoredRaces.maxByOrNull { it.score }
        
        if (bestUpcomingRace == null) {
            MessageLog.i(TAG, "[RACE] No suitable upcoming races found, racing now with best current option.")
            return true
        }
        
        MessageLog.i(TAG, "[RACE] Best upcoming race: ${bestUpcomingRace.raceData.name} (score: ${game.decimalFormat.format(bestUpcomingRace.score)}).")
        
        // Apply time decay to upcoming race score.
        val discountedUpcomingScore = bestUpcomingRace.score * timeDecayFactor
        
        // Calculate opportunity cost: How much better is waiting?
        val improvementFromWaiting = discountedUpcomingScore - bestCurrentRace.score
        
        // Decision criteria.
        val isGoodEnough = bestCurrentRace.score >= minimumQualityThreshold
        val notWorthWaiting = improvementFromWaiting < improvementThreshold
        val shouldRace = isGoodEnough && notWorthWaiting
        
        MessageLog.i(TAG, "[RACE] Opportunity Cost Analysis:")
        MessageLog.i(TAG, "[RACE]     Current score: ${game.decimalFormat.format(bestCurrentRace.score)}")
        MessageLog.i(TAG, "[RACE]     Upcoming score (raw): ${game.decimalFormat.format(bestUpcomingRace.score)}")
        MessageLog.i(TAG, "[RACE]     Upcoming score (discounted by ${game.decimalFormat.format((1 - timeDecayFactor) * 100)}%): ${game.decimalFormat.format(discountedUpcomingScore)}")
        MessageLog.i(TAG, "[RACE]     Improvement from waiting: ${game.decimalFormat.format(improvementFromWaiting)}")
        MessageLog.i(TAG, "[RACE]     Quality check (≥${minimumQualityThreshold}): ${if (isGoodEnough) "PASS" else "FAIL"}")
        MessageLog.i(TAG, "[RACE]     Worth waiting check (<${improvementThreshold}): ${if (notWorthWaiting) "PASS" else "FAIL"}")
        MessageLog.i(TAG, "[RACE]     Decision: ${if (shouldRace) "RACE NOW" else "WAIT FOR BETTER OPPORTUNITY"}")

        // Print the reasoning for the decision.
        if (shouldRace) {
            MessageLog.i(TAG, "[RACE] Reasoning: Current race is good enough (${game.decimalFormat.format(bestCurrentRace.score)} ≥ ${minimumQualityThreshold}) and waiting only gives ${game.decimalFormat.format(improvementFromWaiting)} more points (less than ${improvementThreshold}).")
            // Race now - clear the next race day tracker.
            nextSmartRaceDay = null
        } else {
            val reason = if (!isGoodEnough) {
                "Current race quality too low (${game.decimalFormat.format(bestCurrentRace.score)} < ${minimumQualityThreshold})."
            } else {
                "Worth waiting for better opportunity (+${game.decimalFormat.format(improvementFromWaiting)} points > ${improvementThreshold})."
            }
            MessageLog.i(TAG, "[RACE] Reasoning: $reason")
            // Wait for better opportunity - store the turn number to race on.
            val bestUpcomingRaceData = upcomingRaces.find { it.name == bestUpcomingRace.raceData.name }
            nextSmartRaceDay = bestUpcomingRaceData?.turnNumber
            MessageLog.i(TAG, "[RACE] Setting next smart race day to turn ${nextSmartRaceDay}.")
        }
        
        return shouldRace
    }
}
