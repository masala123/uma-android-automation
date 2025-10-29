package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.uma_android_automation.utils.CustomImageUtils.RaceDetails
import com.steve1316.uma_android_automation.utils.SQLiteSettingsManager
import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.core.Point
import android.util.Log

class Racing (private val game: Game) {
    private val tag: String = "[${MainActivity.loggerTag}]Racing"

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
    private val racingPlanJson = SettingsHelper.getStringSetting("racing", "racingPlan")

    private var raceRetries = 3
    var raceRepeatWarningCheck = false
    var encounteredRacingPopup = false
    var skipRacing = false
    var firstTimeSmartRacingSetup = true
    var firstTimeRacing = true
    var hasFanRequirement = false  // Indicates that a fan requirement has been detected on the main screen.
    private var nextSmartRaceDay: Int? = null  // Tracks the specific day to race based on opportunity cost analysis.

    private val enableStopOnMandatoryRace: Boolean = SettingsHelper.getBooleanSetting("racing", "enableStopOnMandatoryRaces")
    var detectedMandatoryRaceCheck = false

    // Race strategy override settings.
    private val enableRaceStrategyOverride = SettingsHelper.getBooleanSetting("racing", "enableRaceStrategyOverride")
    private val juniorYearRaceStrategy = SettingsHelper.getStringSetting("racing", "juniorYearRaceStrategy")
    private val userSelectedOriginalStrategy = SettingsHelper.getStringSetting("racing", "originalRaceStrategy")
    private var detectedOriginalStrategy: String? = null
    private var hasAppliedStrategyOverride = false

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

    /**
     * Retrieves the user's planned races from saved settings.
     *
     * @return A list of [PlannedRace] entries defined by the user, or an empty list if none exist.
     */
    private fun getUserPlannedRaces(): List<PlannedRace> {
        if (!enableRacingPlan) {
            game.printToLog("[RACE] Racing plan is disabled, returning empty planned races list.", tag = tag)
            return emptyList()
        }
        
        return try {
            if (game.debugMode) game.printToLog("[RACE] Raw user-selected racing plan JSON: \"$racingPlanJson\".", tag = tag)
            
            if (racingPlanJson.isEmpty() || racingPlanJson == "[]") {
                game.printToLog("[RACE] User-selected racing plan is empty, returning empty list.", tag = tag)
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
            
            game.printToLog("[RACE] Successfully loaded ${plannedRaces.size} user-selected planned races from settings.", tag = tag)
            plannedRaces
        } catch (e: Exception) {
            game.printToLog("[ERROR] Failed to parse user-selected racing plan JSON: ${e.message}. Returning empty list.", tag = tag, isError = true)
            emptyList()
        }
    }

    /**
     * Loads the complete race database from saved settings, including all race metadata such as
     * names, grades, distances, and turn numbers.
     *
     * @return A map of race names to their [FullRaceData] or an empty map if racing plan data is missing or invalid.
     */
    private fun getRacePlanData(): Map<String, FullRaceData> {
        return try {
            val racingPlanDataJson = SettingsHelper.getStringSetting("racing", "racingPlanData")
            if (game.debugMode) game.printToLog("[RACE] Raw racing plan data JSON length: ${racingPlanDataJson.length}.", tag = tag)
            
            if (racingPlanDataJson.isEmpty()) {
                game.printToLog("[RACE] Racing plan data is empty, returning empty map.", tag = tag)
                return emptyMap()
            }
            
            val jsonObject = JSONObject(racingPlanDataJson)
            val raceDataMap = mutableMapOf<String, FullRaceData>()
            
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val raceObj = jsonObject.getJSONObject(key)
                
                val fullRaceData = FullRaceData(
                    name = raceObj.getString("name"),
                    grade = raceObj.getString("grade"),
                    terrain = raceObj.getString("terrain"),
                    distanceType = raceObj.getString("distanceType"),
                    fans = raceObj.getInt("fans"),
                    turnNumber = raceObj.getInt("turnNumber"),
                    nameFormatted = raceObj.getString("nameFormatted")
                )
                
                raceDataMap[fullRaceData.name] = fullRaceData
            }
            
            game.printToLog("[RACE] Successfully loaded ${raceDataMap.size} race entries from racing plan data.", tag = tag)
            raceDataMap
        } catch (e: Exception) {
            game.printToLog("[ERROR] Failed to parse racing plan data JSON: ${e.message}. Returning empty map.", tag = tag, isError = true)
            emptyMap()
        }
    }

    data class RaceData(
        val name: String,
        val grade: String,
        val fans: Int,
        val nameFormatted: String,
        val terrain: String,
        val distanceType: String
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
        val priority: Int
    )

    data class FullRaceData(
        val name: String,
        val grade: String,
        val terrain: String,
        val distanceType: String,
        val fans: Int,
        val turnNumber: Int,
        val nameFormatted: String
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Handles the test to detect the currently displayed races on the Race List screen.
     */
    fun startRaceListDetectionTest() {
        game.printToLog("\n[TEST] Now beginning detection test on the Race List screen for the currently displayed races.", tag = tag)
        if (game.imageUtils.findImage("race_status").first == null) {
            game.printToLog("[TEST] Bot is not on the Race List screen. Ending the test.")
            return
        }

        // Detect the current date first.
        game.updateDate()

        // Check for all double star predictions.
        val doublePredictionLocations = game.imageUtils.findAll("race_extra_double_prediction")
        game.printToLog("[TEST] Found ${doublePredictionLocations.size} races with double predictions.", tag = tag)
        
        doublePredictionLocations.forEachIndexed { index, location ->
            val raceName = game.imageUtils.extractRaceName(location)
            game.printToLog("[TEST] Race #${index + 1} - Detected name: \"$raceName\".", tag = tag)
            
            // Query database for race details.
            val raceData = getRaceByTurnAndName(game.currentDate.turnNumber, raceName)
            
            if (raceData != null) {
                game.printToLog("[TEST] Race #${index + 1} - Match found:", tag = tag)
                game.printToLog("[TEST]     Name: ${raceData.name}", tag = tag)
                game.printToLog("[TEST]     Grade: ${raceData.grade}", tag = tag)
                game.printToLog("[TEST]     Fans: ${raceData.fans}", tag = tag)
                game.printToLog("[TEST]     Formatted: ${raceData.nameFormatted}", tag = tag)
            } else {
                game.printToLog("[TEST] Race #${index + 1} - No match found for turn ${game.currentDate.turnNumber}", tag = tag)
            }
        }
    }

    /**
     * Get race data by turn number and detected name using exact and/or fuzzy matching.
     * 
     * @param turnNumber The current turn number to match against.
     * @param detectedName The race name detected by OCR.
     * @return A [RaceData] object if a match is found, null otherwise.
     */
    fun getRaceByTurnAndName(turnNumber: Int, detectedName: String): RaceData? {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            game.printToLog("[ERROR] Database not available for race lookup.", tag = tag, isError = true)
            return null
        }

        return try {
            game.printToLog("[RACE] Looking up race for turn $turnNumber with detected name: \"$detectedName\".", tag = tag)
            
            // Do exact matching based on the info gathered.
            val exactMatch = findExactMatch(settingsManager, turnNumber, detectedName)
            if (exactMatch != null) {
                game.printToLog("[RACE] Found exact match: \"${exactMatch.name}\" AKA \"${exactMatch.nameFormatted}\".", tag = tag)
                settingsManager.close()
                return exactMatch
            }
            
            // Otherwise, do fuzzy matching to find the most similar match using Jaro-Winkler.
            val fuzzyMatch = findFuzzyMatch(settingsManager, turnNumber, detectedName)
            if (fuzzyMatch != null) {
                game.printToLog("[RACE] Found fuzzy match: \"${fuzzyMatch.name}\" AKA \"${fuzzyMatch.nameFormatted}\".", tag = tag)
                settingsManager.close()
                return fuzzyMatch
            }
            
            game.printToLog("[RACE] No match found for turn $turnNumber with name \"$detectedName\".", tag = tag)
            settingsManager.close()
            null
        } catch (e: Exception) {
            game.printToLog("[ERROR] Error looking up race: ${e.message}.", tag = tag, isError = true)
            settingsManager.close()
            null
        }
    }

    /**
     * Queries the race database for an entry matching the specified turn number and formatted name.
     *
     * @param settingsManager The settings manager providing access to the race database.
     * @param turnNumber The turn number used to filter the race records.
     * @param detectedName The exact formatted race name to match against.
     * @return A [RaceData] object if an exact match is found, or null if no matching race exists.
     */
    private fun findExactMatch(settingsManager: SQLiteSettingsManager, turnNumber: Int, detectedName: String): RaceData? {
        val database = settingsManager.getDatabase()
        if (database == null) return null

        val cursor = database.query(
            TABLE_RACES,
            arrayOf(
                RACES_COLUMN_NAME,
                RACES_COLUMN_GRADE,
                RACES_COLUMN_FANS,
                RACES_COLUMN_NAME_FORMATTED,
                RACES_COLUMN_TERRAIN,
                RACES_COLUMN_DISTANCE_TYPE
            ),
            "$RACES_COLUMN_TURN_NUMBER = ? AND $RACES_COLUMN_NAME_FORMATTED = ?",
            arrayOf(turnNumber.toString(), detectedName),
            null, null, null
        )

        return if (cursor.moveToFirst()) {
            val race = RaceData(
                name = cursor.getString(0),
                grade = cursor.getString(1),
                fans = cursor.getInt(2),
                nameFormatted = cursor.getString(3),
                terrain = cursor.getString(4),
                distanceType = cursor.getString(5)
            )
            cursor.close()
            race
        } else {
            cursor.close()
            null
        }
    }

    /**
     * Attempts to find the best fuzzy match for a race entry based on the given formatted name.
     *
     * This function queries all races for the specified turn number, then compares each race’s
     * `nameFormatted` value to the provided [detectedName] using Jaro–Winkler string similarity.
     * The race with the highest similarity score above the defined [SIMILARITY_THRESHOLD] is returned.
     *
     * @param settingsManager The settings manager providing access to the race database.
     * @param turnNumber The turn number used to filter the race records.
     * @param detectedName The name to compare against existing formatted race names.
     * @return A [RaceData] object representing the best fuzzy match, or null if no similar race is found.
     */
    private fun findFuzzyMatch(settingsManager: SQLiteSettingsManager, turnNumber: Int, detectedName: String): RaceData? {
        val database = settingsManager.getDatabase()
        if (database == null) return null

        val cursor = database.query(
            TABLE_RACES,
            arrayOf(
                RACES_COLUMN_NAME,
                RACES_COLUMN_GRADE,
                RACES_COLUMN_FANS,
                RACES_COLUMN_NAME_FORMATTED,
                RACES_COLUMN_TERRAIN,
                RACES_COLUMN_DISTANCE_TYPE
            ),
            "$RACES_COLUMN_TURN_NUMBER = ?",
            arrayOf(turnNumber.toString()),
            null, null, null
        )

        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }

        val similarityService = StringSimilarityServiceImpl(JaroWinklerStrategy())
        var bestMatch: RaceData? = null
        var bestScore = 0.0

        do {
            val nameFormatted = cursor.getString(3)
            val similarity = similarityService.score(detectedName, nameFormatted)
            
            if (similarity > bestScore && similarity >= SIMILARITY_THRESHOLD) {
                bestScore = similarity
                bestMatch = RaceData(
                    name = cursor.getString(0),
                    grade = cursor.getString(1),
                    fans = cursor.getInt(2),
                    nameFormatted = nameFormatted,
                    terrain = cursor.getString(4),
                    distanceType = cursor.getString(5)
                )
                if (game.debugMode) game.printToLog("[DEBUG] Fuzzy match candidate: \"${bestMatch.name}\" AKA \"$nameFormatted\" with similarity ${game.decimalFormat.format(similarity)}.", tag = tag)
                else Log.d(tag, "[DEBUG] Fuzzy match candidate: \"${bestMatch.name}\" AKA \"$nameFormatted\" with similarity ${game.decimalFormat.format(similarity)}.")
            }
        } while (cursor.moveToNext())

        cursor.close()
        
        if (bestMatch != null) {
            game.printToLog("[RACE] Best fuzzy match: \"${bestMatch.name}\" AKA \"${bestMatch.nameFormatted}\" with similarity ${game.decimalFormat.format(bestScore)}.", tag = tag)
        }
        
        return bestMatch
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // Smart Racing Plan Functionality

    /**
     * Maps the distance/terrain type string to the corresponding aptitude field.
     * 
     * @param aptitudeType Either the distance type from race data ("Sprint", "Mile", "Medium", "Long") or the terrain ("Turf", "Dirt").
     * @return The corresponding aptitude value from the character's aptitudes.
     */
    private fun mapToAptitude(aptitudeType: String): String {
        return when (aptitudeType) {
            "Sprint" -> game.aptitudes.distance.sprint
            "Mile" -> game.aptitudes.distance.mile
            "Medium" -> game.aptitudes.distance.medium
            "Long" -> game.aptitudes.distance.long
            "Turf" -> game.aptitudes.track.turf
            "Dirt" -> game.aptitudes.track.dirt
            else -> "X"
        }
    }

    /**
     * Calculates a bonus value based on the race’s aptitude ratings for terrain and distance.
     *
     * This function checks whether both the terrain and distance aptitudes of the given race
     * are rated as "A" or "S". If both conditions are met, a bonus of 100.0 is returned;
     * otherwise, the result is 0.0.
     *
     * @param race The [RaceData] instance whose aptitudes are evaluated.
     * @return The bonus value based on whether the conditions are met.
     */
    private fun getAptitudeMatchBonus(race: RaceData): Double {
        val terrainAptitude = mapToAptitude(race.terrain)
        val distanceAptitude = mapToAptitude(race.distanceType)
        
        val terrainMatch = terrainAptitude == "A" || terrainAptitude == "S"
        val distanceMatch = distanceAptitude == "A" || distanceAptitude == "S"
        
        return if (terrainMatch && distanceMatch) 100.0 else 0.0
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
    fun calculateRaceScore(race: RaceData): ScoredRace {
        // Normalize fans to 0-100 scale (assuming max fans is 30000).
        val fansScore = (race.fans.toDouble() / 30000.0) * 100.0
        
        // Grade scoring: G1 = 75, G2 = 50, G3 = 25.
        val gradeScore = when (race.grade) {
            "G1" -> 75.0
            "G2" -> 50.0
            "G3" -> 25.0
            else -> 0.0
        }
        
        // Aptitude bonus: 100 if both terrain and distance match A/S, else 0.
        val aptitudeBonus = getAptitudeMatchBonus(race)
        
        // Calculate final score with equal weights.
        val finalScore = (fansScore + gradeScore + aptitudeBonus) / 3.0
        
        // Log detailed scoring breakdown for debugging.
        val terrainAptitude = mapToAptitude(race.terrain)
        val distanceAptitude = mapToAptitude(race.distanceType)
        if (game.debugMode) game.printToLog(
            """
            [DEBUG] Scoring ${race.name}:
            Fans        = ${race.fans} (${game.decimalFormat.format(fansScore)})
            Grade       = ${race.grade} (${game.decimalFormat.format(gradeScore)})
            Terrain     = ${race.terrain} ($terrainAptitude)
            Distance    = ${race.distanceType} ($distanceAptitude)
            Aptitude    = ${game.decimalFormat.format(aptitudeBonus)}
            Final       = ${game.decimalFormat.format(finalScore)}
            """.trimIndent(),
            tag = tag
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
     * Retrieves all races scheduled within a specified look-ahead window from the database with turn numbers.
     *
     * This function queries races whose turn numbers fall between [currentTurn] and
     * [currentTurn] + [lookAheadDays], inclusive. It returns the corresponding [FullRaceData]
     * entries sorted in ascending order by turn number.
     *
     * @param currentTurn The current turn number used as the starting point.
     * @param lookAheadDays The number of days (turns) to look ahead for upcoming races.
     * @return A list of [FullRaceData] objects representing all races within the look-ahead window.
     */
    fun getLookAheadRacesWithTurnNumbers(currentTurn: Int, lookAheadDays: Int): List<FullRaceData> {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            game.printToLog("[ERROR] Database not available for look-ahead race lookup.", tag = tag, isError = true)
            return emptyList()
        }

        return try {
            val database = settingsManager.getDatabase()
            if (database == null) {
                game.printToLog("[ERROR] Database is null for look-ahead race lookup.", tag = tag, isError = true)
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

            val races = mutableListOf<FullRaceData>()
            if (cursor.moveToFirst()) {
                do {
                    val race = FullRaceData(
                        name = cursor.getString(0),
                        grade = cursor.getString(1),
                        terrain = cursor.getString(4),
                        distanceType = cursor.getString(5),
                        fans = cursor.getInt(2),
                        turnNumber = cursor.getInt(6),
                        nameFormatted = cursor.getString(3)
                    )
                    races.add(race)
                } while (cursor.moveToNext())
            }
            cursor.close()
            settingsManager.close()
            
            game.printToLog("[RACE] Found ${races.size} races in look-ahead window (turns $currentTurn to $endTurn).", tag = tag)
            races
        } catch (e: Exception) {
            game.printToLog("[ERROR] Error getting look-ahead races: ${e.message}", tag = tag, isError = true)
            settingsManager.close()
            emptyList()
        }
    }

    /**
     * Retrieves all races scheduled within a specified look-ahead window from the database.
     *
     * This function queries races whose turn numbers fall between [currentTurn] and
     * [currentTurn] + [lookAheadDays], inclusive. It returns the corresponding [RaceData]
     * entries sorted in ascending order by turn number.
     *
     * @param currentTurn The current turn number used as the starting point.
     * @param lookAheadDays The number of days (turns) to look ahead for upcoming races.
     * @return A list of [RaceData] objects representing all races within the look-ahead window.
     */
    fun getLookAheadRaces(currentTurn: Int, lookAheadDays: Int): List<RaceData> {
        val settingsManager = SQLiteSettingsManager(game.myContext)
        if (!settingsManager.initialize()) {
            game.printToLog("[ERROR] Database not available for look-ahead race lookup.", tag = tag, isError = true)
            return emptyList()
        }

        return try {
            val database = settingsManager.getDatabase()
            if (database == null) {
                game.printToLog("[ERROR] Database is null for look-ahead race lookup.", tag = tag, isError = true)
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
                    RACES_COLUMN_DISTANCE_TYPE
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
                        distanceType = cursor.getString(5)
                    )
                    races.add(race)
                } while (cursor.moveToNext())
            }
            cursor.close()
            settingsManager.close()
            
            game.printToLog("[RACE] Found ${races.size} races in look-ahead window (turns $currentTurn to $endTurn).", tag = tag)
            races
        } catch (e: Exception) {
            game.printToLog("[ERROR] Error getting look-ahead races: ${e.message}", tag = tag, isError = true)
            settingsManager.close()
            emptyList()
        }
    }

    /**
     * Filters the given list of races according to the user’s Racing Plan settings.
     *
     * The filtering criteria are loaded from the Racing Plan configuration and include:
     * - **Minimum fans threshold:** Races must have at least this number of fans.
     * - **Preferred terrain:** Only races matching the specified terrain (or "Any") are included.
     * - **Preferred grades:** Races must match one of the preferred grade values.
     *
     * @param races The list of [RaceData] entries to filter.
     * @return A list of [RaceData] objects that satisfy all Racing Plan filter criteria.
     */
    fun filterRacesBySettings(races: List<RaceData>): List<RaceData> {
        // Parse preferred grades from JSON array string.
        game.printToLog("[RACE] Raw preferred grades string: \"$preferredGradesString\".", tag = tag)
        val preferredGrades = try {
            // Parse as JSON array.
            val jsonArray = JSONArray(preferredGradesString)
            val parsed = (0 until jsonArray.length()).map { jsonArray.getString(it) }
            game.printToLog("[RACE] Parsed as JSON array: $parsed.", tag = tag)
            parsed
        } catch (e: Exception) {
            game.printToLog("[RACE] Error parsing preferred grades: ${e.message}, using fallback.", tag = tag)
            val parsed = preferredGradesString.split(",").map { it.trim() }
            game.printToLog("[RACE] Fallback parsing result: $parsed", tag = tag)
            parsed
        }

        if (game.debugMode) game.printToLog("[DEBUG] Filter criteria: Min fans: $minFansThreshold, terrain: $preferredTerrain, grades: $preferredGrades", tag = tag)
        else Log.d(tag, "[DEBUG] Filter criteria: Min fans: $minFansThreshold, terrain: $preferredTerrain, grades: $preferredGrades")
        
        val filteredRaces = races.filter { race ->
            val meetsFansThreshold = race.fans >= minFansThreshold
            val meetsTerrainPreference = preferredTerrain == "Any" || race.terrain == preferredTerrain
            val meetsGradePreference = preferredGrades.isEmpty() || preferredGrades.contains(race.grade)
            
            val passes = meetsFansThreshold && meetsTerrainPreference && meetsGradePreference

            // If the race did not pass any of the filters, print the reason why.
            if (!passes) {
                val reasons = mutableListOf<String>()
                if (!meetsFansThreshold) reasons.add("fans ${race.fans} < $minFansThreshold")
                if (!meetsTerrainPreference) reasons.add("terrain ${race.terrain} != $preferredTerrain")
                if (!meetsGradePreference) reasons.add("grade ${race.grade} not in $preferredGrades")
                if (game.debugMode) game.printToLog("[DEBUG] ✗ Filtered out ${race.name}: ${reasons.joinToString(", ")}", tag = tag)
                else Log.d(tag, "[DEBUG] ✗ Filtered out ${race.name}: ${reasons.joinToString(", ")}")
            } else {
                if (game.debugMode) game.printToLog("[DEBUG] ✓ Passed filter: ${race.name} (fans: ${race.fans}, terrain: ${race.terrain}, grade: ${race.grade})", tag = tag)
                else Log.d(tag, "[DEBUG] ✓ Passed filter: ${race.name} (fans: ${race.fans}, terrain: ${race.terrain}, grade: ${race.grade})")
            }
            
            passes
        }
        
        return filteredRaces
    }

    /**
     * Determines if a planned race should be considered based on current turn and race availability.
     * 
     * For Year 3 (Senior Year): Always check screen for availability (existing smart racing flow)
     * For Years 1-2: Calculate turn distance and check eligibility:
     *   - Must be within lookAheadDays range
     *   - Must pass standard racing checks (not in summer, not locked, etc.)
     *   - Use daysToRunExtraRaces to determine if it's an eligible racing day
     * 
     * @param plannedRace The user-selected race to evaluate.
     * @param racePlanData Full race database containing turn numbers.
     * @param dayNumber The current day number in the game.
     * @param currentTurnNumber The current turn in the game.
     * @return True if the race should be considered for racing.
     */
    private fun isPlannedRaceEligible(plannedRace: PlannedRace, racePlanData: Map<String, FullRaceData>, dayNumber: Int, currentTurnNumber: Int): Boolean {
        // Find the race in the plan data.
        val fullRaceData = racePlanData[plannedRace.raceName]
        if (fullRaceData == null) {
            game.printToLog("[ERROR] Planned race \"${plannedRace.raceName}\" not found in race plan data.", tag = tag, isError = true)
            return false
        }
        
        val raceTurnNumber = fullRaceData.turnNumber
        val turnDistance = raceTurnNumber - currentTurnNumber
        
        // Check if race is within look-ahead window.
        if (turnDistance < 0) {
            return false
        } else if (turnDistance > lookAheadDays) {
            if (game.debugMode) {
                game.printToLog("[DEBUG] Planned race \"${plannedRace.raceName}\" is too far ahead of the look-ahead window (distance $turnDistance > lookAheadDays $lookAheadDays).", tag = tag)
            } else {
                Log.d(tag, "[DEBUG] Planned race \"${plannedRace.raceName}\" is too far ahead of the look-ahead window (distance $turnDistance > lookAheadDays $lookAheadDays).")
            }
            return false
        }
        
        // For Classic Year, check if it's an eligible racing day using the settings for the standard racing logic.
        if (game.currentDate.year == 2) {
            if (!(dayNumber % daysToRunExtraRaces == 0)) {
                game.printToLog("[RACE] Planned race \"${plannedRace.raceName}\" is not on an eligible racing day (day $dayNumber, interval $daysToRunExtraRaces).", tag = tag)
                return false
            }
        }
        
        game.printToLog("[RACE] Planned race \"${plannedRace.raceName}\" is eligible for racing.", tag = tag)
        return true
    }

    /**
     * Determines the optimal race to participate in within the upcoming window by scoring all candidates.
     *
     * Each race in [filteredUpcomingRaces] is evaluated using [calculateRaceScore], which considers
     * fans, grade, and aptitude performance. The race with the highest overall score is returned.
     *
     * @param filteredUpcomingRaces The list of [RaceData] entries that passed prior filters.
     * @return The [ScoredRace] with the highest score, or null if the list is empty.
     */
    fun findBestRaceInWindow(filteredUpcomingRaces: List<RaceData>): ScoredRace? {
        game.printToLog("[RACE] Finding best race in window from ${filteredUpcomingRaces.size} races after filters...", tag = tag)
        
        if (filteredUpcomingRaces.isEmpty()) {
            game.printToLog("[RACE] No races provided after filters, cannot find best race.", tag = tag)
            return null
        }

        // For each upcoming race, calculate their score.
        val scoredRaces = filteredUpcomingRaces.map { calculateRaceScore(it) }
        val sortedScoredRaces = scoredRaces.sortedByDescending { it.score }
        game.printToLog("[RACE] Scored all races (sorted by score descending):", tag = tag)
        sortedScoredRaces.forEach { scoredRace ->
            if (game.debugMode) game.printToLog("[DEBUG]    ${scoredRace.raceData.name}: score=${game.decimalFormat.format(scoredRace.score)}, " +
                    "fans=${scoredRace.raceData.fans}(${game.decimalFormat.format(scoredRace.fansScore)}), " +
                    "grade=${scoredRace.raceData.grade}(${game.decimalFormat.format(scoredRace.gradeScore)}), " +
                    "aptitude=${game.decimalFormat.format(scoredRace.aptitudeBonus)}",
                tag = tag
            )
        }
        
        val bestRace = sortedScoredRaces.maxByOrNull { it.score }
        
        if (bestRace != null) {
            game.printToLog("[RACE] Best race in window: ${bestRace.raceData.name} (score: ${game.decimalFormat.format(bestRace.score)})", tag = tag)
            game.printToLog("[RACE]     Fans: ${bestRace.raceData.fans} (${game.decimalFormat.format(bestRace.fansScore)}), Grade: ${bestRace.raceData.grade} (${game.decimalFormat.format(bestRace.gradeScore)}), Aptitude: ${game.decimalFormat.format(bestRace.aptitudeBonus)}", tag = tag)
        } else {
            game.printToLog("[RACE] Failed to determine best race from scored races.", tag = tag)
        }
        
        return bestRace
    }

    /**
     * Determines whether the bot should race immediately or wait for a better opportunity using
     * Opportunity Cost analysis.
     *
     * The decision is based on comparing the best currently available races with upcoming races
     * within the specified look-ahead window. Each race is scored using [calculateRaceScore],
     * taking into account fans, grade, and aptitude. The function applies a time decay factor to
     * upcoming races and evaluates whether the expected improvement from waiting exceeds a
     * predefined threshold.
     *
     * Decision logic:
     * 1. If no current races are available, the bot cannot race.
     * 2. Scores current races and identifies the best option.
     * 3. Looks ahead [lookAheadDays] turns to find and filter upcoming races, then scores them.
     * 4. Applies time decay and calculates the potential improvement from waiting.
     * 5. Compares improvement against thresholds to decide whether to race now or wait.
     *
     * @param currentRaces List of currently available [RaceData] races.
     * @param lookAheadDays Number of turns/days to consider for upcoming races.
     * @return True if the bot should race now, false if it is better to wait for a future race.
     */
    fun shouldRaceNow(currentRaces: List<RaceData>, lookAheadDays: Int): Boolean {
        game.printToLog("[RACE] Evaluating whether to race now using Opportunity Cost logic...", tag = tag)
        if (currentRaces.isEmpty()) {
            game.printToLog("[RACE] No current races available, cannot race now.", tag = tag)
            return false
        }
        
        // Score current races.
        game.printToLog("[RACE] Scoring ${currentRaces.size} current races (sorted by score descending):", tag = tag)
        val currentScoredRaces = currentRaces.map { calculateRaceScore(it) }
        val sortedScoredRaces = currentScoredRaces.sortedByDescending { it.score }
        sortedScoredRaces.forEach { scoredRace ->
            game.printToLog("[RACE]     Current race: ${scoredRace.raceData.name} (score: ${game.decimalFormat.format(scoredRace.score)})", tag = tag)
        }
        val bestCurrentRace = sortedScoredRaces.maxByOrNull { it.score }
        
        if (bestCurrentRace == null) {
            game.printToLog("[RACE] Failed to score current races, cannot race now.", tag = tag)
            return false
        }
        
        game.printToLog("[RACE] Best current race: ${bestCurrentRace.raceData.name} (score: ${game.decimalFormat.format(bestCurrentRace.score)})", tag = tag)
        
        // Get and score upcoming races.
        game.printToLog("[RACE] Looking ahead $lookAheadDays days for upcoming races...", tag = tag)
        val upcomingRacesWithTurnNumbers = getLookAheadRacesWithTurnNumbers(game.currentDate.turnNumber + 1, lookAheadDays)
        game.printToLog("[RACE] Found ${upcomingRacesWithTurnNumbers.size} upcoming races in database.", tag = tag)
        
        // Convert FullRaceData to RaceData for filtering and scoring.
        val upcomingRaces = upcomingRacesWithTurnNumbers.map { fullRaceData ->
            RaceData(
                name = fullRaceData.name,
                grade = fullRaceData.grade,
                fans = fullRaceData.fans,
                nameFormatted = fullRaceData.nameFormatted,
                terrain = fullRaceData.terrain,
                distanceType = fullRaceData.distanceType
            )
        }
        
        val filteredUpcomingRaces = filterRacesBySettings(upcomingRaces)
        game.printToLog("[RACE] After filtering: ${filteredUpcomingRaces.size} upcoming races remain.", tag = tag)
        
        val bestUpcomingRace = findBestRaceInWindow(filteredUpcomingRaces)
        
        if (bestUpcomingRace == null) {
            game.printToLog("[RACE] No suitable upcoming races found, racing now with best current option.", tag = tag)
            return true
        }
        
        game.printToLog("[RACE] Best upcoming race: ${bestUpcomingRace.raceData.name} (score: ${game.decimalFormat.format(bestUpcomingRace.score)}).", tag = tag)
        
        // Opportunity Cost logic.
        val minimumQualityThreshold = 70.0  // Don't race anything scoring below this.
        val timeDecayFactor = 0.90  // Future races are worth this percentage of their score.
        val improvementThreshold = 25.0  // Only wait if improvement is greater than this.

        // Apply time decay to upcoming race score.
        val discountedUpcomingScore = bestUpcomingRace.score * timeDecayFactor
        
        // Calculate opportunity cost: How much better is waiting?
        val improvementFromWaiting = discountedUpcomingScore - bestCurrentRace.score
        
        // Decision criteria.
        val isGoodEnough = bestCurrentRace.score >= minimumQualityThreshold
        val notWorthWaiting = improvementFromWaiting < improvementThreshold
        val shouldRace = isGoodEnough && notWorthWaiting
        
        game.printToLog("[RACE] Opportunity Cost Analysis:", tag = tag)
        game.printToLog("[RACE]     Current score: ${game.decimalFormat.format(bestCurrentRace.score)}", tag = tag)
        game.printToLog("[RACE]     Upcoming score (raw): ${game.decimalFormat.format(bestUpcomingRace.score)}", tag = tag)
        game.printToLog("[RACE]     Upcoming score (discounted by ${game.decimalFormat.format((1 - timeDecayFactor) * 100)}%): ${game.decimalFormat.format(discountedUpcomingScore)}", tag = tag)
        game.printToLog("[RACE]     Improvement from waiting: ${game.decimalFormat.format(improvementFromWaiting)}", tag = tag)
        game.printToLog("[RACE]     Quality check (≥${minimumQualityThreshold}): ${if (isGoodEnough) "PASS" else "FAIL"}", tag = tag)
        game.printToLog("[RACE]     Worth waiting check (<${improvementThreshold}): ${if (notWorthWaiting) "PASS" else "FAIL"}", tag = tag)
        game.printToLog("[RACE]     Decision: ${if (shouldRace) "RACE NOW" else "WAIT FOR BETTER OPPORTUNITY"}", tag = tag)

        // Print the reasoning for the decision.
        if (shouldRace) {
            game.printToLog("[RACE] Reasoning: Current race is good enough (${game.decimalFormat.format(bestCurrentRace.score)} ≥ ${minimumQualityThreshold}) and waiting only gives ${game.decimalFormat.format(improvementFromWaiting)} more points (less than ${improvementThreshold}).", tag = tag)
            // Race now - clear the next race day tracker.
            nextSmartRaceDay = null
        } else {
            val reason = if (!isGoodEnough) {
                "Current race quality too low (${game.decimalFormat.format(bestCurrentRace.score)} < ${minimumQualityThreshold})."
            } else {
                "Worth waiting for better opportunity (+${game.decimalFormat.format(improvementFromWaiting)} points > ${improvementThreshold})."
            }
            game.printToLog("[RACE] Reasoning: $reason", tag = tag)
            // Wait for better opportunity - store the turn number to race on.
            // Find the corresponding FullRaceData to get the turn number.
            val bestUpcomingFullRace = upcomingRacesWithTurnNumbers.find { it.name == bestUpcomingRace.raceData.name }
            nextSmartRaceDay = bestUpcomingFullRace?.turnNumber
            game.printToLog("[RACE] Setting next smart race day to turn ${nextSmartRaceDay}.", tag = tag)
        }
        
        return shouldRace
    }

    /**
     * Determines if racing is worthwhile based on turn number and opportunity cost analysis.
     * 
     * This function queries the race database to check if races exist at the current turn
     * and uses opportunity cost logic to determine if racing is better than waiting.
     * 
     * @param currentTurnNumber The current turn number in the game.
     * @param dayNumber The current day number for extra races.
     * @return True if we should race based on turn analysis, false otherwise.
     */
    private fun shouldRaceBasedOnTurnNumber(currentTurnNumber: Int, dayNumber: Int): Boolean {
        return try {
            game.printToLog("[RACE] Checking eligibility for racing at turn $currentTurnNumber...", tag = tag)
            
            // First, check if there are any races available at the current turn.
            val currentTurnRaces = getLookAheadRacesWithTurnNumbers(currentTurnNumber, 0)
            if (currentTurnRaces.isEmpty()) {
                game.printToLog("[RACE] No races available at turn $currentTurnNumber.", tag = tag)
                return false
            }
            
            game.printToLog("[RACE] Found ${currentTurnRaces.size} race(s) at turn $currentTurnNumber.", tag = tag)
            
            // Convert FullRaceData to RaceData for filtering.
            val currentTurnRaceData = currentTurnRaces.map { fullRaceData ->
                RaceData(
                    name = fullRaceData.name,
                    grade = fullRaceData.grade,
                    fans = fullRaceData.fans,
                    nameFormatted = fullRaceData.nameFormatted,
                    terrain = fullRaceData.terrain,
                    distanceType = fullRaceData.distanceType
                )
            }
            
            // Query upcoming races in the look-ahead window for opportunity cost analysis.
            val upcomingRacesWithTurnNumbers = getLookAheadRacesWithTurnNumbers(currentTurnNumber + 1, lookAheadDays)
            game.printToLog("[RACE] Found ${upcomingRacesWithTurnNumbers.size} upcoming races in look-ahead window.", tag = tag)
            
            // Convert upcoming races from FullRaceData to RaceData.
            val upcomingRaces = upcomingRacesWithTurnNumbers.map { fullRaceData ->
                RaceData(
                    name = fullRaceData.name,
                    grade = fullRaceData.grade,
                    fans = fullRaceData.fans,
                    nameFormatted = fullRaceData.nameFormatted,
                    terrain = fullRaceData.terrain,
                    distanceType = fullRaceData.distanceType
                )
            }
            
            // Apply filters to both current and upcoming races.
            val filteredCurrentRaces = filterRacesBySettings(currentTurnRaceData)
            val filteredUpcomingRaces = filterRacesBySettings(upcomingRaces)
            
            game.printToLog("[RACE] After filtering: ${filteredCurrentRaces.size} current races, ${filteredUpcomingRaces.size} upcoming races.", tag = tag)
            
            // If no filtered current races exist, we shouldn't race.
            if (filteredCurrentRaces.isEmpty()) {
                game.printToLog("[RACE] No current races match the filter criteria. Skipping racing.", tag = tag)
                return false
            }
            
            // If there are no upcoming races to compare against, race now if we have acceptable races.
            if (filteredUpcomingRaces.isEmpty()) {
                game.printToLog("[RACE] No upcoming races to compare against. Racing now with available races.", tag = tag)
                return true
            }
            
            // Use opportunity cost logic to determine if we should race now or wait.
            val shouldRace = shouldRaceNow(filteredCurrentRaces, lookAheadDays)
            
            shouldRace
        } catch (e: Exception) {
            game.printToLog("[ERROR] Error in turn-based racing check: ${e.message}. Falling back to screen-based checks.", tag = tag, isError = true)
            true  // Return true to fall back to screen checks.
        }
    }

    /**
     * Handles user-selected races for the Classic Year.
     * Checks if any planned races are within range and eligible for racing.
     * 
     * @return True if the bot should attempt to race, false to skip racing
     */
    private fun checkPlannedRacesBeforeSeniorYear(): Boolean {
        val userPlannedRaces = getUserPlannedRaces()
        if (userPlannedRaces.isEmpty()) {
            game.printToLog("[RACE] No user-selected races configured.", tag = tag)
            return false
        }
        
        val racePlanData = getRacePlanData()
        if (racePlanData.isEmpty()) {
            game.printToLog("[RACE] No race plan data available for eligibility checking.", tag = tag)
            return false
        }

        val dayNumber = game.imageUtils.determineDayForExtraRace()
        val currentTurnNumber = game.currentDate.turnNumber
        
        // Check each planned race for eligibility.
        val eligiblePlannedRaces = userPlannedRaces.filter { plannedRace ->
            isPlannedRaceEligible(plannedRace, racePlanData, dayNumber, currentTurnNumber)
        }
        
        if (eligiblePlannedRaces.isEmpty()) {
            game.printToLog("[RACE] No user-selected races are eligible at turn $currentTurnNumber.", tag = tag)
            return false
        }
        
        game.printToLog("[RACE] Found ${eligiblePlannedRaces.size} eligible user-selected races: ${eligiblePlannedRaces.map { it.raceName }}.", tag = tag)
        return true
    }

    /**
     * Handles extra races using Smart Racing logic for Senior Year (Year 3).
     *
     * Updates game data, identifies and evaluates available races, and prioritizes planned ones
     * with a scoring bonus. If no valid race is found, the process is canceled.
     *
     * @return True if a race was successfully selected and ready to run; false if the process was canceled.
     */
    private fun handleSmartRacing(): Boolean {
        game.printToLog("[RACE] Using Smart Racing Plan logic...", tag = tag)

        // Updates the current date and aptitudes for accurate scoring.
        game.updateDate()
        game.updateAptitudes()

        // Load user planned races and race plan data.
        val userPlannedRaces = getUserPlannedRaces()
        val racePlanData = getRacePlanData()
        game.printToLog("[RACE] Loaded ${userPlannedRaces.size} user-selected races and ${racePlanData.size} race entries.", tag = tag)

        // Detects all double-star race predictions on screen.
        val doublePredictionLocations = game.imageUtils.findAll("race_extra_double_prediction")
        game.printToLog("[RACE] Found ${doublePredictionLocations.size} double-star prediction locations.", tag = tag)
        if (doublePredictionLocations.isEmpty()) {
            game.printToLog("[RACE] No double-star predictions found. Canceling racing process.", tag = tag)
            return false
        }

        // Extracts race names from the screen and matches them with the in-game database.
        game.printToLog("[RACE] Extracting race names and matching with database...", tag = tag)
        val currentRaces = doublePredictionLocations.mapNotNull { location ->
            val raceName = game.imageUtils.extractRaceName(location)
            val raceData = getRaceByTurnAndName(game.currentDate.turnNumber, raceName)
            if (raceData != null) {
                game.printToLog("[RACE] ✓ Matched in database: ${raceData.name} (Grade: ${raceData.grade}, Fans: ${raceData.fans}, Terrain: ${raceData.terrain}).", tag = tag)
                raceData
            } else {
                game.printToLog("[RACE] ✗ No match found in database for \"$raceName\".", tag = tag)
                null
            }
        }

        if (currentRaces.isEmpty()) {
            game.printToLog("[RACE] No races matched in database. Canceling racing process.", tag = tag)
            return false
        }
        game.printToLog("[RACE] Successfully matched ${currentRaces.size} races in database.", tag = tag)

        // Separate matched races into planned vs unplanned.
        val (plannedRaces, regularRaces) = currentRaces.partition { race ->
            userPlannedRaces.any { it.raceName == race.name }
        }

        // Log which races are user-selected vs regular.
        game.printToLog("[RACE] Found ${plannedRaces.size} user-selected races on screen: ${plannedRaces.map { it.name }}.", tag = tag)
        game.printToLog("[RACE] Found ${regularRaces.size} regular races on screen: ${regularRaces.map { it.name }}.", tag = tag)

        // Filter both lists by user Racing Plan settings.
        val filteredPlannedRaces = filterRacesBySettings(plannedRaces)
        val filteredRegularRaces = filterRacesBySettings(regularRaces)
        game.printToLog("[RACE] After filtering: ${filteredPlannedRaces.size} planned races and ${filteredRegularRaces.size} regular races remain.", tag = tag)

        // Combine all filtered races for Opportunity Cost analysis.
        val allFilteredRaces = filteredPlannedRaces + filteredRegularRaces
        if (allFilteredRaces.isEmpty()) {
            game.printToLog("[RACE] No races match current settings after filtering. Canceling racing process.", tag = tag)
            return false
        }

        // Evaluate whether the bot should race now using Opportunity Cost logic.
        if (!shouldRaceNow(allFilteredRaces, lookAheadDays)) {
            game.printToLog("[RACE] Smart racing suggests waiting for better opportunities. Canceling racing process.", tag = tag)
            return false
        }

        // Decide which races to score based on availability.
        val racesToScore = if (filteredPlannedRaces.isNotEmpty()) {
            // Prefer planned races, but include regular races for comparison.
            game.printToLog("[RACE] Prioritizing ${filteredPlannedRaces.size} planned races with ${filteredRegularRaces.size} regular races for comparison.", tag = tag)
            filteredPlannedRaces + filteredRegularRaces
        } else {
            // No planned races available, use regular races only.
            game.printToLog("[RACE] No planned races available, using ${filteredRegularRaces.size} regular races only.", tag = tag)
            filteredRegularRaces
        }

        // Score all eligible races with bonus for planned races.
        val scoredRaces = racesToScore.map { race ->
            val baseScore = calculateRaceScore(race)
            if (plannedRaces.contains(race)) {
                // Add a bonus for planned races.
                val bonusScore = baseScore.copy(score = baseScore.score + 50.0)
                game.printToLog("[RACE] Planned race \"${race.name}\" gets a bonus: ${game.decimalFormat.format(baseScore.score)} -> ${game.decimalFormat.format(bonusScore.score)}.", tag = tag)
                bonusScore
            } else {
                baseScore
            }
        }

        // Sort by score and find the best race.
        val sortedScoredRaces = scoredRaces.sortedByDescending { it.score }
        val bestRace = sortedScoredRaces.first()

        game.printToLog("[RACE] Best race selected: ${bestRace.raceData.name} (score: ${game.decimalFormat.format(bestRace.score)}).", tag = tag)
        if (plannedRaces.contains(bestRace.raceData)) {
            game.printToLog("[RACE] Selected race is from user's planned races list.", tag = tag)
        } else {
            game.printToLog("[RACE] Selected race is from regular available races.", tag = tag)
        }

        // Locates the best race on screen and selects it.
        game.printToLog("[RACE] Looking for target race \"${bestRace.raceData.name}\" on screen...", tag = tag)
        val targetRaceLocation = doublePredictionLocations.find { location ->
            val raceName = game.imageUtils.extractRaceName(location)
            val raceData = getRaceByTurnAndName(game.currentDate.turnNumber, raceName)
            val matches = raceData?.name == bestRace.raceData.name
            if (matches) game.printToLog("[RACE] ✓ Found target race at location (${location.x}, ${location.y}).", tag = tag)
            matches
        } ?: run {
            game.printToLog("[RACE] Could not find target race \"${bestRace.raceData.name}\" on screen. Canceling racing process.", tag = tag)
            return false
        }

        game.printToLog("[RACE] Selecting smart racing choice: ${bestRace.raceData.name} (score: ${game.decimalFormat.format(bestRace.score)}).", tag = tag)
        game.tap(targetRaceLocation.x, targetRaceLocation.y, "race_extra_double_prediction", ignoreWaiting = true)

        return true
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Checks if the day number is odd to be eligible to run an extra race, excluding Summer where extra racing is not allowed.
     *
     * @return True if the day number is odd. Otherwise false.
     */
    fun checkExtraRaceAvailability(): Boolean {
        val dayNumber = game.imageUtils.determineDayForExtraRace()
        game.printToLog("\n[INFO] Current remaining number of days before the next mandatory race: $dayNumber.", tag = tag)

        // If the setting to force racing extra races is enabled, always return true.
        if (enableForceRacing) return true

        // If fan requirement is detected, bypass smart racing logic to force racing.
        if (hasFanRequirement) {
            game.printToLog("[RACE] Fan requirement detected. Bypassing smart racing logic to fulfill requirement.", tag = tag)
        } else if (enableRacingPlan && enableFarmingFans) {
            // Smart racing: Check turn-based eligibility before screen checks.
            game.printToLog("[RACE] Smart racing enabled, checking eligibility based on turn number...", tag = tag)
            
            val shouldRaceFromTurnCheck = shouldRaceBasedOnTurnNumber(game.currentDate.turnNumber, dayNumber)
            if (!shouldRaceFromTurnCheck) {
                game.printToLog("[RACE] No suitable races at turn ${game.currentDate.turnNumber} based on opportunity cost analysis.", tag = tag)
                return false
            }
            
            game.printToLog("[RACE] Turn-based analysis suggests racing is worthwhile, proceeding with screen checks...", tag = tag)
        }

        // Check for common restrictions that apply to both smart and standard racing.
        val isUmaFinalsLocked = game.imageUtils.findImage("race_select_extra_locked_uma_finals", tries = 1, region = game.imageUtils.regionBottomHalf).first != null
        val isLocked = game.imageUtils.findImage("race_select_extra_locked", tries = 1, region = game.imageUtils.regionBottomHalf).first != null
        val isSummer = game.imageUtils.findImage("recover_energy_summer", tries = 1, region = game.imageUtils.regionBottomHalf).first != null
        
        if (isUmaFinalsLocked) {
            game.printToLog("[RACE] It is UMA Finals right now so there will be no extra races. Stopping extra race check.", tag = tag)
            return false
        } else if (isLocked) {
            game.printToLog("[RACE] Extra Races button is currently locked. Stopping extra race check.", tag = tag)
            return false
        } else if (isSummer) {
            game.printToLog("[RACE] It is currently Summer right now. Stopping extra race check.", tag = tag)
            return false
        }

        // For smart racing, if we got here, the turn-based check passed, so we should race.
        // For standard racing, use the interval check.
        // If fan requirement exists, always allow racing.
        if (hasFanRequirement) {
            game.printToLog("[RACE] Fan requirement detected. Allowing racing on any eligible day.", tag = tag)
            return !raceRepeatWarningCheck
        } else if (enableRacingPlan && enableFarmingFans) {
            // Check if current day matches the optimal race day or falls on the interval.
            val isOptimalDay = nextSmartRaceDay == dayNumber
            val isIntervalDay = dayNumber % daysToRunExtraRaces == 0
            
            if (isOptimalDay) {
                game.printToLog("[RACE] Current day ($dayNumber) matches optimal race day.", tag = tag)
                return !raceRepeatWarningCheck
            } else if (isIntervalDay) {
                game.printToLog("[RACE] Current day ($dayNumber) falls on racing interval ($daysToRunExtraRaces).", tag = tag)
                return !raceRepeatWarningCheck
            } else {
                game.printToLog("[RACE] Current day ($dayNumber) is not optimal (next: $nextSmartRaceDay, interval: $daysToRunExtraRaces).", tag = tag)
                return false
            }
        }

        // Standard racing logic.
        return enableFarmingFans && dayNumber % daysToRunExtraRaces == 0 && !raceRepeatWarningCheck
    }

    /**
     * Handles extra races using the standard or traditional racing logic.
     *
     * This function performs the following steps:
     * 1. Detects double-star races on screen.
     * 2. If only one race has double predictions, selects it immediately.
     * 3. Otherwise, iterates through each extra race to determine fan gain and double prediction status.
     * 4. Evaluates which race to select based on maximum fans and double prediction priority (if force racing is enabled).
     * 5. Selects the determined race on screen.
     *
     * @return True if a race was successfully selected; false if the process was canceled.
     */
    private fun handleStandardRacing(): Boolean {
        game.printToLog("[RACE] Using traditional racing logic for extra races...", tag = tag)

        // 1. Detects double-star races on screen.
        val doublePredictionLocations = game.imageUtils.findAll("race_extra_double_prediction")
        val maxCount = doublePredictionLocations.size
        if (maxCount == 0) {
            game.printToLog("[WARNING] No extra races found on screen. Canceling racing process.", tag = tag)
            return false
        }

        // 2. If only one race has double predictions, selects it immediately.
        if (doublePredictionLocations.size == 1) {
            game.printToLog("[RACE] Only one race with double predictions. Selecting it.", tag = tag)
            game.tap(doublePredictionLocations[0].x, doublePredictionLocations[0].y, "race_extra_double_prediction", ignoreWaiting = true)
            return true
        }

        // 3. Otherwise, iterates through each extra race to determine fan gain and double prediction status.
        val (sourceBitmap, templateBitmap) = game.imageUtils.getBitmaps("race_extra_double_prediction")
        val listOfRaces = ArrayList<RaceDetails>()
        val extraRaceLocations = ArrayList<Point>()

        for (count in 0 until maxCount) {
            val selectedExtraRace = game.imageUtils.findImage("race_extra_selection", region = game.imageUtils.regionBottomHalf).first ?: break
            extraRaceLocations.add(selectedExtraRace)

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

        // Determine max fans and select the appropriate race.
        val maxFans = listOfRaces.maxOfOrNull { it.fans } ?: -1
        if (maxFans == -1) return false
        game.printToLog("[RACE] Number of fans detected for each extra race are: ${listOfRaces.joinToString(", ") { it.fans.toString() }}", tag = tag)

        // 4. Evaluates which race to select based on maximum fans and double prediction priority (if force racing is enabled).
        val index = if (!enableForceRacing) {
            listOfRaces.indexOfFirst { it.fans == maxFans }
        } else {
            listOfRaces.indexOfFirst { it.hasDoublePredictions }.takeIf { it != -1 } ?: listOfRaces.indexOfFirst { it.fans == maxFans }
        }

        // 5. Selects the determined race on screen.
        game.printToLog("[RACE] Selecting extra race at option #${index + 1}.", tag = tag)
        val target = extraRaceLocations[index]
        game.tap(target.x - game.imageUtils.relWidth((100 * 1.36).toInt()), target.y - game.imageUtils.relHeight(70), "race_extra_selection", ignoreWaiting = true)

        return true
    }

    /**
     * The entry point for handling mandatory or extra races.
     *
     * @return True if the mandatory/extra race was completed successfully. Otherwise false.
     */
    fun handleRaceEvents(): Boolean {
        game.printToLog("\n[RACE] Starting Racing process...", tag = tag)
        if (encounteredRacingPopup) {
            // Dismiss the insufficient fans popup here and head to the Race Selection screen.
            game.findAndTapImage("race_confirm", tries = 1, region = game.imageUtils.regionBottomHalf)
            encounteredRacingPopup = false
            game.wait(1.0)
        }

        // If there are no races available, cancel the racing process.
        if (game.imageUtils.findImage("race_none_available", tries = 1, region = game.imageUtils.regionMiddle, suppressError = true).first != null) {
            game.printToLog("[RACE] There are no races to compete in. Canceling the racing process and doing something else.", tag = tag)
            return false
        }

        skipRacing = false

        // First, check if there is a mandatory or a extra race available. If so, head into the Race Selection screen.
        // Note: If there is a mandatory race, the bot would be on the Home screen.
        // Otherwise, it would have found itself at the Race Selection screen already (by way of the insufficient fans popup).
        if (game.findAndTapImage("race_select_mandatory", tries = 1, region = game.imageUtils.regionBottomHalf)) {
            game.printToLog("\n[RACE] Starting process for handling a mandatory race.", tag = tag)

            if (enableStopOnMandatoryRace) {
                detectedMandatoryRaceCheck = true
                return false
            }

            // If there is a popup warning about racing too many times, confirm the popup to continue as this is a mandatory race.
            game.findAndTapImage("ok", tries = 1, region = game.imageUtils.regionMiddle, suppressError = true)
            game.wait(1.0)

            // There is a mandatory race. Now confirm the selection and the resultant popup and then wait for the game to load.
            game.wait(2.0)
            game.printToLog("[RACE] Confirming the mandatory race selection.", tag = tag)
            game.findAndTapImage("race_confirm", tries = 3, region = game.imageUtils.regionBottomHalf)
            game.wait(1.0)
            game.printToLog("[RACE] Confirming any popup from the mandatory race selection.", tag = tag)
            game.findAndTapImage("race_confirm", tries = 3, region = game.imageUtils.regionBottomHalf)
            game.wait(2.0)

            game.waitForLoading()

            // Handle race strategy override if enabled.
            handleRaceStrategyOverride()

            // Skip the race if possible, otherwise run it manually.
            val resultCheck: Boolean = if (game.imageUtils.findImage("race_skip_locked", tries = 5, region = game.imageUtils.regionBottomHalf).first == null) {
                skipRace()
            } else {
                manualRace()
            }

            finishRace(resultCheck)

            game.printToLog("[RACE] Racing process for Mandatory Race is completed.", tag = tag)
            return true
        } else if (game.currentDate.phase != "Pre-Debut" && game.findAndTapImage("race_select_extra", tries = 1, region = game.imageUtils.regionBottomHalf)) {
            game.printToLog("\n[RACE] Starting process for handling a extra race.", tag = tag)

            // If there is a popup warning about repeating races 3+ times, stop the process and do something else other than racing.
            if (game.imageUtils.findImage("race_repeat_warning").first != null) {
                if (!enableForceRacing) {
                    raceRepeatWarningCheck = true
                    game.printToLog("\n[RACE] Closing popup warning of doing more than 3+ races and setting flag to prevent racing for now. Canceling the racing process and doing something else.", tag = tag)
                    game.findAndTapImage("cancel", region = game.imageUtils.regionBottomHalf)
                    return false
                } else {
                    game.findAndTapImage("ok", tries = 1, region = game.imageUtils.regionMiddle)
                    game.wait(1.0)
                }
            }

            // There is a extra race.
            val statusLocation = game.imageUtils.findImage("race_status").first
            if (statusLocation == null) {
                game.printToLog("[ERROR] Unable to determine existence of list of extra races. Canceling the racing process and doing something else.", tag = tag, isError = true)
                return false
            }

            val maxCount = game.imageUtils.findAll("race_selection_fans", region = game.imageUtils.regionBottomHalf).size
            if (maxCount == 0) {
                game.printToLog("[WARNING] Was unable to find any extra races to select. Canceling the racing process and doing something else.", tag = tag, isError = true)
                return false
            } else {
                game.printToLog("[RACE] There are $maxCount extra race options currently on screen.", tag = tag)
            }

            if (hasFanRequirement) game.printToLog("[RACE] Fan requirement criteria detected. This race must be completed to meet the fan requirement.", tag = tag)

            // Determine whether to use smart racing with user-selected races or standard racing.
            val useSmartRacing = if (hasFanRequirement) {
                // If fan requirement is needed, force standard racing to ensure the race proceeds.
                false
            } else if (game.currentDate.year == 3) {
                // Year 3 (Senior Year): Use smart racing if conditions are met.
                enableFarmingFans && !enableForceRacing && enableRacingPlan
            } else {
                // Year 2 (Classic Year): Check if user-selected races are eligible.
                // Year 1 (Junior Year) will use the standard racing logic.
                game.currentDate.year == 2 && enableRacingPlan && checkPlannedRacesBeforeSeniorYear()
            }

            val success = if (useSmartRacing) {
                if (game.currentDate.year == 3) {
                    game.printToLog("[RACE] Using smart racing for Senior Year.", tag = tag)
                } else {
                    game.printToLog("[RACE] Using smart racing with user-selected races for Year ${game.currentDate.year}.", tag = tag)
                }
                handleSmartRacing()
            } else {
                // Use the standard racing logic.
                // If needed, print the reason(s) to why the smart racing logic was not started.
                if (enableRacingPlan && !hasFanRequirement) {
                    game.printToLog("[RACE] Smart racing conditions not met due to current settings, using traditional racing logic...", tag = tag)
                    game.printToLog("[RACE] Reason: One or more conditions failed:", tag = tag)
                    if (game.currentDate.year == 3) {
                        if (!enableFarmingFans) game.printToLog("[RACE]   - enableFarmingFans is false", tag = tag)
                        if (enableForceRacing) game.printToLog("[RACE]   - enableForceRacing is true", tag = tag)
                    } else if (game.currentDate.year == 1) {
                        game.printToLog("[RACE]   - It is currently the Junior Year.", tag = tag)
                    } else {
                        game.printToLog("[RACE]   - No eligible user-selected races found for Year ${game.currentDate.year}", tag = tag)
                    }
                }

                handleStandardRacing()
            }

            if (!success) return false

            // Confirm the selection and the resultant popup and then wait for the game to load.
            game.findAndTapImage("race_confirm", tries = 30, region = game.imageUtils.regionBottomHalf)
            game.findAndTapImage("race_confirm", tries = 10, region = game.imageUtils.regionBottomHalf)
            game.wait(2.0)

            // Handle race strategy override if enabled.
            handleRaceStrategyOverride()

            // Skip the race if possible, otherwise run it manually.
            val resultCheck: Boolean = if (game.imageUtils.findImage("race_skip_locked", tries = 5, region = game.imageUtils.regionBottomHalf).first == null) {
                skipRace()
            } else {
                manualRace()
            }

            finishRace(resultCheck, isExtra = true)

            // Clear the next smart race day tracker since we just completed a race.
            nextSmartRaceDay = null

            game.printToLog("[RACE] Racing process for Extra Race is completed.", tag = tag)
            return true
        }

        return false
    }

    /**
     * The entry point for handling standalone races if the user started the bot on the Racing screen.
     */
    fun handleStandaloneRace() {
        game.printToLog("\n[RACE] Starting Standalone Racing process...", tag = tag)

        // Skip the race if possible, otherwise run it manually.
        val resultCheck: Boolean = if (game.imageUtils.findImage("race_skip_locked", tries = 5, region = game.imageUtils.regionBottomHalf).first == null) {
            skipRace()
        } else {
            manualRace()
        }

        finishRace(resultCheck)

        game.printToLog("[RACE] Racing process for Standalone Race is completed.", tag = tag)
    }

    /**
     * Skips the current race to get to the results screen.
     *
     * @return True if the bot completed the race with retry attempts remaining. Otherwise false.
     */
    private fun skipRace(): Boolean {
        while (raceRetries >= 0) {
            game.printToLog("[RACE] Skipping race...", tag = tag)

            // Press the skip button and then wait for your result of the race to show.
            if (game.findAndTapImage("race_skip", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                game.printToLog("[RACE] Race was able to be skipped.", tag = tag)
            }
            game.wait(2.0)

            // Now tap on the screen to get past the Race Result screen.
            game.tap(350.0, 450.0, "ok", taps = 3)

            // Check if the race needed to be retried.
            if (game.imageUtils.findImage("race_retry", tries = 5, region = game.imageUtils.regionBottomHalf, suppressError = true).first != null) {
                if (disableRaceRetries) {
                    game.printToLog("\n[END] Stopping the bot due to failing a mandatory race.", tag = tag)
                    game.notificationMessage = "Stopping the bot due to failing a mandatory race."
                    throw IllegalStateException()
                }
                game.findAndTapImage("race_retry", tries = 1, region = game.imageUtils.regionBottomHalf, suppressError = true)
                game.printToLog("[RACE] The skipped race failed and needs to be run again. Attempting to retry...", tag = tag)
                game.wait(3.0)
                raceRetries--
            } else {
                return true
            }
        }

        return false
    }

    /**
     * Manually runs the current race to get to the results screen.
     *
     * @return True if the bot completed the race with retry attempts remaining. Otherwise false.
     */
    private fun manualRace(): Boolean {
        while (raceRetries >= 0) {
            game.printToLog("[RACE] Skipping manual race...", tag = tag)

            // Press the manual button.
            if (game.findAndTapImage("race_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                game.printToLog("[RACE] Started the manual race.", tag = tag)
            }
            game.wait(2.0)

            // Confirm the Race Playback popup if it appears.
            if (game.findAndTapImage("ok", tries = 1, region = game.imageUtils.regionMiddle, suppressError = true)) {
                game.printToLog("[RACE] Confirmed the Race Playback popup.", tag = tag)
                game.wait(5.0)
            }

            game.waitForLoading()

            // Now press the confirm button to get past the list of participants.
            if (game.findAndTapImage("race_confirm", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                game.printToLog("[RACE] Dismissed the list of participants.", tag = tag)
            }
            game.waitForLoading()
            game.wait(1.0)
            game.waitForLoading()
            game.wait(1.0)

            // Skip the part where it reveals the name of the race.
            if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                game.printToLog("[RACE] Skipped the name reveal of the race.", tag = tag)
            }
            // Skip the walkthrough of the starting gate.
            if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                game.printToLog("[RACE] Skipped the walkthrough of the starting gate.", tag = tag)
            }
            game.wait(3.0)
            // Skip the start of the race.
            if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                game.printToLog("[RACE] Skipped the start of the race.", tag = tag)
            }
            // Skip the lead up to the finish line.
            if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                game.printToLog("[RACE] Skipped the lead up to the finish line.", tag = tag)
            }
            game.wait(2.0)
            // Skip the result screen.
            if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                game.printToLog("[RACE] Skipped the results screen.", tag = tag)
            }
            game.wait(2.0)

            game.waitForLoading()
            game.wait(1.0)

            // Check if the race needed to be retried.
            if (game.imageUtils.findImage("race_retry", tries = 5, region = game.imageUtils.regionBottomHalf, suppressError = true).first != null) {
                if (disableRaceRetries) {
                    game.printToLog("\n[END] Stopping the bot due to failing a mandatory race.", tag = tag)
                    game.notificationMessage = "Stopping the bot due to failing a mandatory race."
                    throw IllegalStateException()
                }
                game.findAndTapImage("race_retry", tries = 1, region = game.imageUtils.regionBottomHalf, suppressError = true)
                game.printToLog("[RACE] Manual race failed and needs to be run again. Attempting to retry...", tag = tag)
                game.wait(5.0)
                raceRetries--
            } else {
                // Check if a Trophy was acquired.
                if (game.findAndTapImage("race_accept_trophy", tries = 5, region = game.imageUtils.regionBottomHalf)) {
                    game.printToLog("[RACE] Closing popup to claim trophy...", tag = tag)
                }

                return true
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
    fun finishRace(resultCheck: Boolean, isExtra: Boolean = false) {
        game.printToLog("\n[RACE] Now performing cleanup and finishing the race.", tag = tag)
        if (!resultCheck) {
            game.notificationMessage = "Bot has run out of retry attempts for racing. Stopping the bot now..."
            throw IllegalStateException()
        }

        // Bot will be at the screen where it shows the final positions of all participants.
        // Press the confirm button and wait to see the triangle of fans.
        game.printToLog("[RACE] Now attempting to confirm the final positions of all participants and number of gained fans", tag = tag)
        if (game.findAndTapImage("next", tries = 30, region = game.imageUtils.regionBottomHalf)) {
            game.wait(0.5)

            // Now tap on the screen to get to the next screen.
            game.tap(350.0, 750.0, "ok", taps = 3)

            // Now press the end button to finish the race.
            game.findAndTapImage("race_end", tries = 30, region = game.imageUtils.regionBottomHalf)

            if (!isExtra) {
                game.printToLog("[RACE] Seeing if a Training Goal popup will appear.", tag = tag)
                // Wait until the popup showing the completion of a Training Goal appears and confirm it.
                // There will be dialog before it so the delay should be longer.
                game.wait(5.0)
                if (game.findAndTapImage("next", tries = 10, region = game.imageUtils.regionBottomHalf)) {
                    game.wait(2.0)

                    // Now confirm the completion of a Training Goal popup.
                    game.printToLog("[RACE] There was a Training Goal popup. Confirming it now.", tag = tag)
                    game.findAndTapImage("next", tries = 10, region = game.imageUtils.regionBottomHalf)
                }
            } else if (game.findAndTapImage("next", tries = 10, region = game.imageUtils.regionBottomHalf)) {
                // Same as above but without the longer delay.
                game.wait(2.0)
                game.findAndTapImage("race_end", tries = 10, region = game.imageUtils.regionBottomHalf)
            }

            firstTimeRacing = false
            hasFanRequirement = false  // Reset fan requirement flag after race completion.
        } else {
            game.printToLog("[ERROR] Cannot start the cleanup process for finishing the race. Moving on...", tag = tag, isError = true)
        }
    }

    /**
     * Handles race strategy override for Junior Year races.
     *
     * During Junior Year: Applies the user-selected strategy and stores the original.
     * After Junior Year: Restores the original strategy and disables the feature.
     */
    private fun handleRaceStrategyOverride() {
        if (!enableRaceStrategyOverride) {
            return
        } else if (enableRaceStrategyOverride && !firstTimeRacing && !hasAppliedStrategyOverride && game.currentDate.year != 1) {
            return
        }

        val currentYear = game.currentDate.year
        game.printToLog("[RACE] Handling race strategy override for Year $currentYear.", tag = tag)

        // Check if we're on the racing screen by looking for the Change Strategy button.
        if (!game.findAndTapImage("race_change_strategy", tries = 1, region = game.imageUtils.regionBottomHalf)) {
            game.printToLog("[RACE] Change Strategy button not found. Skipping strategy override.", tag = tag)
            return
        }

        // Wait for the strategy selection popup to appear.
        game.wait(2.0)

        // Find the confirm button to use as reference point for strategy coordinates.
        val confirmLocation = game.imageUtils.findImage("confirm", region = game.imageUtils.regionBottomHalf).first
        if (confirmLocation == null) {
            game.printToLog("[ERROR] Could not find confirm button for strategy selection. Skipping strategy override.", tag = tag, isError = true)
            game.findAndTapImage("cancel", region = game.imageUtils.regionMiddle)
            return
        }

        val baseX = confirmLocation.x.toInt()
        val baseY = confirmLocation.y.toInt()

        if (currentYear == 1) {
            // Junior Year: Apply user's selected strategy and detect the original.
            if (!hasAppliedStrategyOverride) {
                // Detect and store the original strategy.
                val originalStrategy = detectOriginalStrategy()
                if (originalStrategy != null) {
                    detectedOriginalStrategy = originalStrategy
                    game.printToLog("[RACE] Detected original race strategy: $originalStrategy", tag = tag)
                }

                // Apply the user's selected strategy.
                game.printToLog("[RACE] Applying user-selected strategy: $juniorYearRaceStrategy", tag = tag)

                if (modifyRacingStrategy(baseX, baseY, juniorYearRaceStrategy)) {
                    hasAppliedStrategyOverride = true
                    game.printToLog("[RACE] Successfully applied strategy override for Junior Year.", tag = tag)
                } else {
                    game.printToLog("[ERROR] Failed to apply strategy override.", tag = tag, isError = true)
                }
            }
        } else {
            // Year 2+: Apply the detected original strategy if available, otherwise use user-selected strategy.
            val strategyToApply = if (detectedOriginalStrategy != null) {
                detectedOriginalStrategy!!
            } else {
                userSelectedOriginalStrategy
            }
            
            game.printToLog("[RACE] Applying original race strategy: $strategyToApply", tag = tag)
            
            if (modifyRacingStrategy(baseX, baseY, strategyToApply)) {
                hasAppliedStrategyOverride = false
                game.printToLog("[RACE] Successfully applied original strategy. Strategy override disabled for rest of run.", tag = tag)
            } else {
                game.printToLog("[ERROR] Failed to apply original strategy.", tag = tag, isError = true)
            }
        }

        // Click confirm to apply the strategy change.
        if (game.findAndTapImage("confirm", tries = 3, region = game.imageUtils.regionBottomHalf)) {
            game.wait(2.0)
            game.printToLog("[RACE] Strategy change confirmed.", tag = tag)
        } else {
            game.printToLog("[ERROR] Failed to confirm strategy change.", tag = tag, isError = true)
        }
    }

    /**
     * Detects the original race strategy by searching for strategy indicators.
     * 
     * @return The detected strategy name or null if not found.
     */
    private fun detectOriginalStrategy(): String? {
        val strategyImages = listOf(
            "race_strategy_end" to "End",
            "race_strategy_late" to "Late", 
            "race_strategy_pace" to "Pace",
            "race_strategy_front" to "Front"
        )

        for ((imageName, strategyName) in strategyImages) {
            if (game.imageUtils.findImage(imageName).first != null) {
                return strategyName
            }
        }

        return null
    }

    /**
     * Clicks on a specific strategy button using coordinate offsets from the confirm button.
     * 
     * @param baseX The X coordinate of the confirm button.
     * @param baseY The Y coordinate of the confirm button.
     * @param strategy The strategy to select ("Front", "Pace", "Late", "End").
     * @return True if the click was successful, false otherwise.
     */
    private fun modifyRacingStrategy(baseX: Int, baseY: Int, strategy: String): Boolean {
        val strategyOffsets = mapOf(
            "end" to Pair(-585, -210),
            "late" to Pair(-355, -210),
            "pace" to Pair(-125, -210),
            "front" to Pair(105, -210)
        )

        val offset = strategyOffsets[strategy.lowercase()]
        if (offset == null) {
            game.printToLog("[ERROR] Unknown strategy: $strategy", tag = tag, isError = true)
            return false
        }

        val targetX = (baseX + offset.first).toDouble()
        val targetY = (baseY + offset.second).toDouble()

        game.printToLog("[RACE] Clicking strategy button at ($targetX, $targetY) for strategy: $strategy", tag = tag)
        
        return game.gestureUtils.tap(targetX, targetY)
    }
}