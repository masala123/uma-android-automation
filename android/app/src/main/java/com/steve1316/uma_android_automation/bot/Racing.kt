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
    private val preferredTerrain = SettingsHelper.getStringSetting("racing", "preferredTerrain")
    private val preferredGradesString = SettingsHelper.getStringSetting("racing", "preferredGrades")
    private val preferredDistancesString = SettingsHelper.getStringSetting("racing", "preferredDistances")
    private val racingPlanJson = SettingsHelper.getStringSetting("racing", "racingPlan")
    private val minimumQualityThreshold = SettingsHelper.getDoubleSetting("racing", "minimumQualityThreshold")
    private val timeDecayFactor = SettingsHelper.getDoubleSetting("racing", "timeDecayFactor")
    private val improvementThreshold = SettingsHelper.getDoubleSetting("racing", "improvementThreshold")
    private val enableMandatoryRacingPlan = SettingsHelper.getBooleanSetting("racing", "enableMandatoryRacingPlan")

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
        private const val RACES_COLUMN_TERRAIN = "terrain"
        private const val RACES_COLUMN_DISTANCE_TYPE = "distanceType"
        private const val SIMILARITY_THRESHOLD = 0.7
    }

    data class RaceData(
        val name: String,
        val grade: String,
        val fans: Int,
        val nameFormatted: String,
        val terrain: String,
        val distanceType: String,
        val turnNumber: Int
    )

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
        val priority: Int,
        val turnNumber: Int
    )

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
                    priority = raceObj.optInt("priority", 0),
                    turnNumber = raceObj.getInt("turnNumber")
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
                    terrain = raceObj.getString("terrain"),
                    distanceType = raceObj.getString("distanceType"),
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
            // Clear requirement flags since we cannot proceed with racing.
            hasFanRequirement = false
            hasTrophyRequirement = false
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

        // Clear requirement flags if no race selection buttons were found.
        hasFanRequirement = false
        hasTrophyRequirement = false
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

        // Check for mandatory racing plan mode before any screen detection.
        val (_, mandatoryExtraRaceData) = findMandatoryExtraRaceForCurrentTurn()
        if (mandatoryExtraRaceData != null) {
            // Check if aptitudes match (both terrain and distance must be B or greater) for double predictions.
            val aptitudesMatch = checkRaceAptitudeMatch(mandatoryExtraRaceData)
            if (!aptitudesMatch) {
                val terrainAptitude = when (mandatoryExtraRaceData.terrain) {
                    "Turf" -> game.aptitudes.track.turf
                    "Dirt" -> game.aptitudes.track.dirt
                    else -> "X"
                }
                val distanceAptitude = when (mandatoryExtraRaceData.distanceType) {
                    "Short" -> game.aptitudes.distance.sprint
                    "Mile" -> game.aptitudes.distance.mile
                    "Medium" -> game.aptitudes.distance.medium
                    "Long" -> game.aptitudes.distance.long
                    else -> "X"
                }
                MessageLog.i(TAG, "[RACE] Mandatory extra race \"${mandatoryExtraRaceData.name}\" aptitudes don't match requirements (Terrain: $terrainAptitude, Distance: $distanceAptitude). Both must be B or greater.")
                return false
            } else {
                MessageLog.i(TAG, "[RACE] Mandatory extra race \"${mandatoryExtraRaceData.name}\" aptitudes match requirements. Proceeding to navigate to the Extra Races screen.")
            }
        }

        // If there is a popup warning about repeating races 3+ times, stop the process and do something else other than racing.
        if (game.imageUtils.findImage("race_repeat_warning").first != null) {
            if (!enableForceRacing) {
                raceRepeatWarningCheck = true
                MessageLog.i(TAG, "[RACE] Closing popup warning of doing more than 3+ races and setting flag to prevent racing for now. Canceling the racing process and doing something else.")
                game.findAndTapImage("cancel", region = game.imageUtils.regionBottomHalf)
                // Clear requirement flags since we cannot proceed with racing.
                hasFanRequirement = false
                hasTrophyRequirement = false
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
            // Clear requirement flags since we cannot proceed with racing.
            hasFanRequirement = false
            hasTrophyRequirement = false
            MessageLog.i(TAG, "********************")
            return false
        }

        val maxCount = game.imageUtils.findAll("race_selection_fans", region = game.imageUtils.regionBottomHalf).size
        if (maxCount == 0) {
            // If there is a fan/trophy requirement but no races available, reset the flags and proceed with training to advance the day.
            if (hasFanRequirement || hasTrophyRequirement) {
                MessageLog.i(TAG, "[RACE] Fan/trophy requirement detected but no extra races available. Clearing requirement flags and proceeding with training to advance the day.")
            } else {
                MessageLog.e(TAG, "[ERROR] Was unable to find any extra races to select. Canceling the racing process and doing something else.")
            }
            // Always clear requirement flags when no races are available.
            hasFanRequirement = false
            hasTrophyRequirement = false
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
            processSmartRacing(mandatoryExtraRaceData)
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

        if (!success) {
            // Clear requirement flags if race selection failed.
            hasFanRequirement = false
            hasTrophyRequirement = false
            return false
        }

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
     * @param mandatoryExtraRaceData Race data for for the extra race that is mandatory to run. If provided, this race will be selected immediately if found on the screen.
     * @return True if a race was successfully selected and ready to run; false if the process was canceled.
     */
    private fun processSmartRacing(mandatoryExtraRaceData: RaceData? = null): Boolean {
        MessageLog.i(TAG, "[RACE] Using Smart Racing Plan logic...")

        // Updates the current date and aptitudes for accurate scoring.
        game.updateDate()
        game.updateAptitudes()

        // Use cached user planned races and race plan data.
        MessageLog.i(TAG, "[RACE] Loaded ${userPlannedRaces.size} user-selected races and ${raceData.size} race entries.")

        // Detects all double-star race predictions on screen.
        val doublePredictionLocations = game.imageUtils.findAll("race_extra_double_prediction")
        MessageLog.i(TAG, "[RACE] Found ${doublePredictionLocations.size} double-star prediction locations.")
        if (doublePredictionLocations.isEmpty()) {
            MessageLog.i(TAG, "[RACE] No double-star predictions found. Canceling racing process.")
            return false
        }

        // If mandatory extra race data is provided, immediately find and select it on screen.
        if (mandatoryExtraRaceData != null) {
            MessageLog.i(TAG, "[RACE] Mandatory mode for extra races enabled. Looking for planned race \"${mandatoryExtraRaceData.name}\" on screen for turn ${game.currentDate.turnNumber}.")

            // Find the mandatory race on screen by matching race name with detected races.
            val mandatoryExtraRaceLocation = doublePredictionLocations.find { location ->
                val raceName = game.imageUtils.extractRaceName(location)
                val detectedExtraRaceData = lookupRaceInDatabase(game.currentDate.turnNumber, raceName)
                detectedExtraRaceData?.name == mandatoryExtraRaceData.name
            }

            if (mandatoryExtraRaceLocation != null) {
                MessageLog.i(TAG, "[RACE] Mandatory extra race \"${mandatoryExtraRaceData.name}\" found on screen with double predictions. Selecting it immediately (skipping opportunity cost analysis).")
                game.tap(mandatoryExtraRaceLocation.x, mandatoryExtraRaceLocation.y, "race_extra_double_prediction", ignoreWaiting = true)
                return true
            } else {
                MessageLog.i(TAG, "[RACE] Mandatory extra race \"${mandatoryExtraRaceData.name}\" not found on screen. Canceling racing process.")
                return false
            }
        }

        // Extracts race names from the screen and matches them with the in-game database.
        MessageLog.i(TAG, "[RACE] Extracting race names and matching with database...")
        val currentRaces = doublePredictionLocations.mapNotNull { location ->
            val raceName = game.imageUtils.extractRaceName(location)
            val raceData = lookupRaceInDatabase(game.currentDate.turnNumber, raceName)
            if (raceData != null) {
                MessageLog.i(TAG, "[RACE] ✓ Matched in database: ${raceData.name} (Grade: ${raceData.grade}, Fans: ${raceData.fans}, Terrain: ${raceData.terrain}).")
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
            val g1Races = currentRaces.filter { it.grade == "G1" }
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
                if (raceData?.grade == "G1") {
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
                if (raceData?.grade == "G1") index else null
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
     * Checks if an aptitude value meets the minimum requirement (B or greater).
     *
     * @param aptitude The aptitude value to check (S, A, B, etc.).
     * @return True if the aptitude is S, A or B; false otherwise.
     */
    private fun hasMinimumAptitude(aptitude: String): Boolean {
        return aptitude == "S" || aptitude == "A" || aptitude == "B"
    }

    /**
     * Checks if the racer's aptitudes match the race requirements (both terrain and distance must be B or greater).
     *
     * @param raceData The race data to check aptitudes against.
     * @return True if both terrain and distance aptitudes are B or greater; false otherwise.
     */
    private fun checkRaceAptitudeMatch(raceData: RaceData): Boolean {
        // Get terrain aptitude.
        val terrainAptitude = when (raceData.terrain) {
            "Turf" -> game.aptitudes.track.turf
            "Dirt" -> game.aptitudes.track.dirt
            else -> "X"
        }

        // Get distance aptitude.
        val distanceAptitude = when (raceData.distanceType) {
            "Short" -> game.aptitudes.distance.sprint
            "Mile" -> game.aptitudes.distance.mile
            "Medium" -> game.aptitudes.distance.medium
            "Long" -> game.aptitudes.distance.long
            else -> "X"
        }

        // Both aptitudes must be B or greater for double predictions.
        val terrainMatch = hasMinimumAptitude(terrainAptitude)
        val distanceMatch = hasMinimumAptitude(distanceAptitude)

        return terrainMatch && distanceMatch
    }

    /**
     * Finds the mandatory planned race for the current turn number if mandatory mode is enabled.
     *
     * @return A Pair containing the PlannedRace and RaceData for the mandatory extra race if found, null otherwise.
     */
    private fun findMandatoryExtraRaceForCurrentTurn(): Pair<PlannedRace?, RaceData?> {
        if (!enableRacingPlan || !enableMandatoryRacingPlan) {
            return Pair(null, null)
        }

        val currentTurnNumber = game.currentDate.turnNumber

        // Find planned race matching current turn number.
        val matchingPlannedRace = userPlannedRaces.find { it.turnNumber == currentTurnNumber }
        if (matchingPlannedRace == null) {
            return Pair(null, null)
        }

        // Look up race data from raceData map.
        val raceData = this.raceData[matchingPlannedRace.raceName]
        if (raceData == null) {
            MessageLog.e(TAG, "[ERROR] Planned race \"${matchingPlannedRace.raceName}\" not found in race data.")
            return Pair(null, null)
        }

        return Pair(matchingPlannedRace, raceData)
    }

    /**
     * Check if there are fan or trophy requirements that need to be satisfied.
     */
    fun checkRacingRequirements() {
        // Check for fan requirement on the main screen.
        val sourceBitmap = game.imageUtils.getSourceBitmap()
        val needsFanRequirement = game.imageUtils.findImageWithBitmap("race_fans_criteria", sourceBitmap, region = game.imageUtils.regionTopHalf, customConfidence = 0.90) != null
        if (needsFanRequirement) {
            hasFanRequirement = true
            MessageLog.i(TAG, "[RACE] Fan requirement criteria detected on main screen. Forcing racing to fulfill requirement.")
        } else {
            // Clear the flag if requirement is no longer present.
            if (hasFanRequirement) {
                MessageLog.i(TAG, "[RACE] Fan requirement no longer detected on main screen. Clearing flag.")
                hasFanRequirement = false
            }
            
            // Check for trophy requirement on the main screen.
            val needsTrophyRequirement = game.imageUtils.findImageWithBitmap("race_trophies_criteria", sourceBitmap, region = game.imageUtils.regionTopHalf, customConfidence = 0.90) != null
            if (needsTrophyRequirement) {
                hasTrophyRequirement = true
                MessageLog.i(TAG, "[RACE] Trophy requirement criteria detected on main screen. Forcing racing to fulfill requirement.")
            } else {
                // Clear the flag if requirement is no longer present.
                if (hasTrophyRequirement) {
                    MessageLog.i(TAG, "[RACE] Trophy requirement no longer detected on main screen. Clearing flag.")
                    hasTrophyRequirement = false
                }
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
        if (game.checkFinals()) {
            MessageLog.i(TAG, "[RACE] It is UMA Finals right now so there will be no extra races. Stopping extra race check.")
            return false
        } else if (game.imageUtils.findImageWithBitmap("race_select_extra_locked", sourceBitmap, region = game.imageUtils.regionBottomHalf) != null) {
            MessageLog.i(TAG, "[RACE] Extra Races button is currently locked. Stopping extra race check.")
            return false
        } else if (game.imageUtils.findImageWithBitmap("recover_energy_summer", sourceBitmap, region = game.imageUtils.regionBottomHalf) != null) {
            MessageLog.i(TAG, "[RACE] It is currently Summer right now. Stopping extra race check.")
            return false
        }

        // Check for mandatory racing plan mode (before opportunity cost analysis and while still on the main screen).
        if (enableRacingPlan && enableMandatoryRacingPlan) {
            val currentTurnNumber = game.currentDate.turnNumber

            // Find planned race matching current turn number.
            val matchingPlannedRace = userPlannedRaces.find { it.turnNumber == currentTurnNumber }

            if (matchingPlannedRace != null) {
                MessageLog.i(TAG, "[RACE] Found planned race \"${matchingPlannedRace.raceName}\" for turn $currentTurnNumber and mandatory mode for extra races is enabled.")
                return !raceRepeatWarningCheck
            } else {
                MessageLog.i(TAG, "[RACE] No planned race matches current turn $currentTurnNumber and mandatory mode for extra races is enabled. Continuing with normal eligibility checks.")
            }
        } else if (enableFarmingFans && enableRacingPlan && game.currentDate.year != 1) {
            // For Classic and Senior Year, check if planned races are coming up in the look-ahead window and are eligible for racing.
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
                            if (game.currentDate.year == 2 && !enableRacingPlan) {
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
                // Skip interval check if Racing Plan is enabled.
                val isRegularRacingDay = enableFarmingFans && !enableRacingPlan && (turnsRemaining % daysToRunExtraRaces == 0)
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
        } else if (enableRacingPlan && !enableMandatoryRacingPlan && enableFarmingFans) {
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
            val isIntervalDay = !enableRacingPlan && (turnsRemaining % daysToRunExtraRaces == 0)

            if (isOptimalDay) {
                MessageLog.i(TAG, "[RACE] Current day ($turnsRemaining) matches optimal race day.")
                return !raceRepeatWarningCheck
            } else if (isIntervalDay) {
                MessageLog.i(TAG, "[RACE] Current day ($turnsRemaining) falls on racing interval ($daysToRunExtraRaces).")
                return !raceRepeatWarningCheck
            } else {
                if (enableRacingPlan) {
                    MessageLog.i(TAG, "[RACE] Current day ($turnsRemaining) is not optimal (next: $nextSmartRaceDay).")
                } else {
                    MessageLog.i(TAG, "[RACE] Current day ($turnsRemaining) is not optimal (next: $nextSmartRaceDay, interval: $daysToRunExtraRaces).")
                }
                return false
            }
        }

        // Conditionally start the standard racing process.
        // This fallback only applies when Racing Plan is disabled, so use interval-based logic.
        return enableFarmingFans && !enableRacingPlan && (turnsRemaining % daysToRunExtraRaces == 0) && !raceRepeatWarningCheck
    }

    /**
     * Handles race strategy override for Junior Year races.
     *
     * During Junior Year: Applies the user-selected strategy and stores the original.
     * After Junior Year: Restores the original strategy and disables the feature.
     */
    private fun selectRaceStrategy() {
        if (!enableRaceStrategyOverride) {
            return
        } else if ((!firstTimeRacing && !hasAppliedStrategyOverride && game.currentDate.year != 1) || game.checkFinals()) {
            return
        }

        val currentYear = game.currentDate.year
        MessageLog.i(TAG, "[RACE] Handling race strategy override for Year $currentYear.")

        // Check if we're on the racing screen by looking for the Change Strategy button.
        if (!game.findAndTapImage("race_change_strategy", tries = 1, region = game.imageUtils.regionBottomHalf)) {
            MessageLog.i(TAG, "[RACE] Change Strategy button not found. Skipping strategy override.")
            return
        }

        // Wait for the strategy selection popup to appear.
        game.wait(2.0)

        // Find the confirm button to use as reference point for strategy coordinates.
        val confirmLocation = game.imageUtils.findImage("confirm", region = game.imageUtils.regionBottomHalf).first
        if (confirmLocation == null) {
            MessageLog.e(TAG, "[ERROR] Could not find confirm button for strategy selection. Skipping strategy override.")
            game.findAndTapImage("cancel", region = game.imageUtils.regionMiddle)
            return
        }

        val baseX = confirmLocation.x.toInt()
        val baseY = confirmLocation.y.toInt()

        if (currentYear == 1) {
            // Junior Year: Apply user's selected strategy and detect the original.
            if (!hasAppliedStrategyOverride) {
                // Detect and store the original strategy.
                val strategyImages = listOf(
                    "race_strategy_end" to "End",
                    "race_strategy_late" to "Late", 
                    "race_strategy_pace" to "Pace",
                    "race_strategy_front" to "Front"
                )

                var originalStrategy: String? = null
                for ((imageName, strategyName) in strategyImages) {
                    if (game.imageUtils.findImage(imageName).first != null) {
                        originalStrategy = strategyName
                        break
                    }
                }

                if (originalStrategy != null) {
                    detectedOriginalStrategy = originalStrategy
                    MessageLog.i(TAG, "[RACE] Detected original race strategy: $originalStrategy")
                }

                // Apply the user's selected strategy.
                MessageLog.i(TAG, "[RACE] Applying user-selected strategy: $juniorYearRaceStrategy")

                val strategyOffsets = mapOf(
                    "end" to Pair(-585, -210),
                    "late" to Pair(-355, -210),
                    "pace" to Pair(-125, -210),
                    "front" to Pair(105, -210)
                )

                val offset = strategyOffsets[juniorYearRaceStrategy.lowercase()]
                if (offset != null) {
                    val targetX = (baseX + offset.first).toDouble()
                    val targetY = (baseY + offset.second).toDouble()
                    MessageLog.i(TAG, "[RACE] Clicking strategy button at ($targetX, $targetY) for strategy: $juniorYearRaceStrategy")
                    if (game.gestureUtils.tap(targetX, targetY)) {
                        hasAppliedStrategyOverride = true
                        MessageLog.i(TAG, "[RACE] Successfully applied strategy override for Junior Year.")
                    } else {
                        MessageLog.e(TAG, "[ERROR] Failed to apply strategy override.")
                    }
                } else {
                    MessageLog.e(TAG, "[ERROR] Unknown strategy: $juniorYearRaceStrategy")
                }
            }
        } else {
            // Year 2+: Apply the detected original strategy if available, otherwise use user-selected strategy.
            val strategyToApply = if (detectedOriginalStrategy != null) {
                detectedOriginalStrategy!!
            } else {
                userSelectedOriginalStrategy
            }
            
            MessageLog.i(TAG, "[RACE] Applying original race strategy: $strategyToApply")
            
            val strategyOffsets = mapOf(
                "end" to Pair(-585, -210),
                "late" to Pair(-355, -210),
                "pace" to Pair(-125, -210),
                "front" to Pair(105, -210)
            )

            val offset = strategyOffsets[strategyToApply.lowercase()]
            if (offset != null) {
                val targetX = (baseX + offset.first).toDouble()
                val targetY = (baseY + offset.second).toDouble()
                MessageLog.i(TAG, "[RACE] Clicking strategy button at ($targetX, $targetY) for strategy: $strategyToApply")
                if (game.gestureUtils.tap(targetX, targetY)) {
                    hasAppliedStrategyOverride = false
                    MessageLog.i(TAG, "[RACE] Successfully applied original strategy. Strategy override disabled for rest of run.")
                } else {
                    MessageLog.e(TAG, "[ERROR] Failed to apply original strategy.")
                }
            } else {
                MessageLog.e(TAG, "[ERROR] Unknown strategy: $strategyToApply")
            }
        }

        // Click confirm to apply the strategy change.
        if (game.findAndTapImage("confirm", tries = 3, region = game.imageUtils.regionBottomHalf)) {
            game.wait(2.0)
            MessageLog.i(TAG, "[RACE] Strategy change confirmed.")
        } else {
            MessageLog.e(TAG, "[ERROR] Failed to confirm strategy change.")
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
                game.wait(1.0)

                // Now tap on the screen to get past the Race Result screen.
                game.tap(350.0, 450.0, "ok", taps = 3)
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
                    MessageLog.i(TAG, "[RACE] The skipped race failed and needs to be run again. Attempting to retry...")
                    game.wait(3.0)
                    raceRetries--
                } else {
                    return true
                }
            } else {
                MessageLog.i(TAG, "[RACE] Unable to skip the race. Proceeding to handle the race manually...")

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
                game.wait(2.0)

                // Now press the confirm button to get past the list of participants.
                if (game.findAndTapImage("race_confirm", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Dismissed the list of participants.")
                }
                game.wait(1.0)
                // Skip the part where it reveals the name of the race.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped the name reveal of the race.")
                }
                game.wait(1.0)
                // Skip the walkthrough of the starting gate.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped the walkthrough of the starting gate.")
                }
                game.wait(1.0)
                // Skip the start of the race.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped the start of the race.")
                }
                game.wait(1.0)
                // Skip the lead up to the finish line.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped the lead up to the finish line.")
                }
                game.wait(1.0)
                // Skip crossing the finish line.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped crossing the finish line.")
                }
                game.wait(2.0)
                // Skip the result screen.
                if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(TAG, "[RACE] Skipped the results screen.")
                }

                // Now wait for the race result screen to appear which may come with the race retry popup if we failed the race.
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
                    if (game.findAndTapImage("close", tries = 5, region = game.imageUtils.regionBottomHalf, suppressError = true)) {
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
        MessageLog.i(TAG, "\n[RACE] Now performing cleanup and finishing the race.")
        if (!resultCheck) {
            game.notificationMessage = "Bot has run out of retry attempts for racing. Stopping the bot now..."
            throw IllegalStateException()
        }

        // Always reset flags after successful race completion, regardless of UI flow.
        firstTimeRacing = false
        hasFanRequirement = false
        hasTrophyRequirement = false

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
                    RACES_COLUMN_TERRAIN,
                    RACES_COLUMN_DISTANCE_TYPE,
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
                    terrain = exactCursor.getString(4),
                    distanceType = exactCursor.getString(5),
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
                    RACES_COLUMN_TERRAIN,
                    RACES_COLUMN_DISTANCE_TYPE,
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
                        terrain = fuzzyCursor.getString(4),
                        distanceType = fuzzyCursor.getString(5),
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
     * - **Aptitude:** Adds a bonus if both terrain and distance aptitudes are A or S.
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
            "G1" -> 75.0
            "G2" -> 50.0
            "G3" -> 25.0
            else -> 0.0
        }
        
        // Map distance/terrain types to their current aptitudes.
        val terrainAptitude = when (race.terrain) {
            "Turf" -> game.aptitudes.track.turf
            "Dirt" -> game.aptitudes.track.dirt
            else -> "X"
        }
        val distanceAptitude = when (race.distanceType) {
            "Short" -> game.aptitudes.distance.sprint
            "Mile" -> game.aptitudes.distance.mile
            "Medium" -> game.aptitudes.distance.medium
            "Long" -> game.aptitudes.distance.long
            else -> "X"
        }
        
        // Aptitude bonus: 100 if both terrain and distance match A/S, else 0.
        val terrainMatch = terrainAptitude == "A" || terrainAptitude == "S"
        val distanceMatch = distanceAptitude == "A" || distanceAptitude == "S"
        val aptitudeBonus = if (terrainMatch && distanceMatch) 100.0 else 0.0
        
        // Calculate final score with equal weights.
        val finalScore = (fansScore + gradeScore + aptitudeBonus) / 3.0
        
        // Log detailed scoring breakdown for debugging.
        if (game.debugMode) MessageLog.d(
            TAG,
            """
            [DEBUG] Scoring ${race.name}:
            Fans        = ${race.fans} (${game.decimalFormat.format(fansScore)})
            Grade       = ${race.grade} (${game.decimalFormat.format(gradeScore)})
            Terrain     = ${race.terrain} ($terrainAptitude)
            Distance    = ${race.distanceType} ($distanceAptitude)
            Aptitude    = ${game.decimalFormat.format(aptitudeBonus)}
            Final       = ${game.decimalFormat.format(finalScore)}
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
                    RACES_COLUMN_TERRAIN,
                    RACES_COLUMN_DISTANCE_TYPE,
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
                        terrain = cursor.getString(4),
                        distanceType = cursor.getString(5),
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
        // Parse preferred grades.
        val preferredGrades = try {
            // Parse as JSON array.
            val jsonArray = JSONArray(preferredGradesString)
            val parsed = (0 until jsonArray.length()).map { jsonArray.getString(it) }
            MessageLog.i(TAG, "[RACE] Parsed preferred grades as JSON array: $parsed.")
            parsed
        } catch (e: Exception) {
            MessageLog.i(TAG, "[RACE] Error parsing preferred grades: ${e.message}, using fallback.")
            val parsed = preferredGradesString.split(",").map { it.trim() }
            MessageLog.i(TAG, "[RACE] Fallback parsing result: $parsed")
            parsed
        }

        // Parse preferred distances.
        val preferredDistances = try {
            // Parse as JSON array.
            val jsonArray = JSONArray(preferredDistancesString)
            val parsed = (0 until jsonArray.length()).map { jsonArray.getString(it) }
            MessageLog.i(TAG, "[RACE] Parsed preferred distances as JSON array: $parsed.")
            parsed
        } catch (e: Exception) {
            MessageLog.i(TAG, "[RACE] Error parsing preferred distances: ${e.message}, using fallback.")
            val parsed = preferredDistancesString.split(",").map { it.trim() }
            MessageLog.i(TAG, "[RACE] Fallback parsing result: $parsed")
            parsed
        }

        if (game.debugMode) MessageLog.d(TAG, "[DEBUG] Filter criteria: Min fans: $minFansThreshold, terrain: $preferredTerrain, grades: $preferredGrades, distances: $preferredDistances")
        else Log.d(TAG, "[DEBUG] Filter criteria: Min fans: $minFansThreshold, terrain: $preferredTerrain, grades: $preferredGrades, distances: $preferredDistances")
        
        val filteredRaces = races.filter { race ->
            val meetsFansThreshold = bypassMinFans || race.fans >= minFansThreshold
            val meetsTerrainPreference = preferredTerrain == "Any" || race.terrain == preferredTerrain
            val meetsGradePreference = preferredGrades.isEmpty() || preferredGrades.contains(race.grade)
            val meetsDistancePreference = preferredDistances.isEmpty() || preferredDistances.contains(race.distanceType)
            
            val passes = meetsFansThreshold && meetsTerrainPreference && meetsGradePreference && meetsDistancePreference

            // If the race did not pass any of the filters, print the reason why.
            if (!passes) {
                val reasons = mutableListOf<String>()
                if (!meetsFansThreshold) reasons.add("fans ${race.fans} < $minFansThreshold")
                if (!meetsTerrainPreference) reasons.add("terrain ${race.terrain} != $preferredTerrain")
                if (!meetsGradePreference) reasons.add("grade ${race.grade} not in $preferredGrades")
                if (!meetsDistancePreference) reasons.add("distance ${race.distanceType} not in $preferredDistances")
                if (game.debugMode) MessageLog.d(TAG, "[DEBUG] ✗ Filtered out ${race.name}: ${reasons.joinToString(", ")}")
                else Log.d(TAG, "[DEBUG] ✗ Filtered out ${race.name}: ${reasons.joinToString(", ")}")
            } else {
                if (game.debugMode) MessageLog.d(TAG, "[DEBUG] ✓ Passed filter: ${race.name} (fans: ${race.fans}, terrain: ${race.terrain}, grade: ${race.grade}, distance: ${race.distanceType})")
                else Log.d(TAG, "[DEBUG] ✓ Passed filter: ${race.name} (fans: ${race.fans}, terrain: ${race.terrain}, grade: ${race.grade}, distance: ${race.distanceType})")
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
