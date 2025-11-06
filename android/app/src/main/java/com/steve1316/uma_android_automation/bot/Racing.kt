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
    private val racingPlanJson = SettingsHelper.getStringSetting("racing", "racingPlan")

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
            MessageLog.i(tag, "[RACE] Racing plan is disabled, returning empty planned races list.")
            return emptyList()
        }
        
        return try {
            if (game.debugMode) MessageLog.i(tag, "[RACE] Raw user-selected racing plan JSON: \"$racingPlanJson\".")
            
            if (racingPlanJson.isEmpty() || racingPlanJson == "[]") {
                MessageLog.i(tag, "[RACE] User-selected racing plan is empty, returning empty list.")
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
            
            MessageLog.i(tag, "[RACE] Successfully loaded ${plannedRaces.size} user-selected planned races from settings.")
            plannedRaces
        } catch (e: Exception) {
            MessageLog.e(tag, "Failed to parse user-selected racing plan JSON: ${e.message}. Returning empty list.")
            emptyList()
        }
    }

    /**
     * Loads the complete race database from saved settings, including all race metadata such as
     * names, grades, distances, and turn numbers.
     *
     * @return A map of race names to their [RaceData] or an empty map if racing plan data is missing or invalid.
     */
    private fun getRacePlanData(): Map<String, RaceData> {
        return try {
            val racingPlanDataJson = SettingsHelper.getStringSetting("racing", "racingPlanData")
            if (game.debugMode) MessageLog.i(tag, "[RACE] Raw racing plan data JSON length: ${racingPlanDataJson.length}.")
            
            if (racingPlanDataJson.isEmpty()) {
                MessageLog.i(tag, "[RACE] Racing plan data is empty, returning empty map.")
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
            
            MessageLog.i(tag, "[RACE] Successfully loaded ${raceDataMap.size} race entries from racing plan data.")
            raceDataMap
        } catch (e: Exception) {
            MessageLog.e(tag, "Failed to parse racing plan data JSON: ${e.message}. Returning empty map.")
            emptyMap()
        }
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
        val priority: Int
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Handles the test to detect the currently displayed races on the Race List screen.
     */
    fun startRaceListDetectionTest() {
        MessageLog.i(tag, "\n[TEST] Now beginning detection test on the Race List screen for the currently displayed races.")
        if (game.imageUtils.findImage("race_status").first == null) {
            MessageLog.i(tag, "[TEST] Bot is not on the Race List screen. Ending the test.")
            return
        }

        // Detect the current date first.
        game.updateDate()

        // Check for all double star predictions.
        val doublePredictionLocations = game.imageUtils.findAll("race_extra_double_prediction")
        MessageLog.i(tag, "[TEST] Found ${doublePredictionLocations.size} races with double predictions.")
        
        doublePredictionLocations.forEachIndexed { index, location ->
            val raceName = game.imageUtils.extractRaceName(location)
            MessageLog.i(tag, "[TEST] Race #${index + 1} - Detected name: \"$raceName\".")
            
            // Query database for race details.
            val raceData = getRaceByTurnAndName(game.currentDate.turnNumber, raceName)
            
            if (raceData != null) {
                MessageLog.i(tag, "[TEST] Race #${index + 1} - Match found:")
                MessageLog.i(tag, "[TEST]     Name: ${raceData.name}")
                MessageLog.i(tag, "[TEST]     Grade: ${raceData.grade}")
                MessageLog.i(tag, "[TEST]     Fans: ${raceData.fans}")
                MessageLog.i(tag, "[TEST]     Formatted: ${raceData.nameFormatted}")
            } else {
                MessageLog.i(tag, "[TEST] Race #${index + 1} - No match found for turn ${game.currentDate.turnNumber}")
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////

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
            MessageLog.e(tag, "Database not available for race lookup.")
            return null
        }

        return try {
            MessageLog.i(tag, "[RACE] Looking up race for turn $turnNumber with detected name: \"$detectedName\".")
            
            // Do exact matching based on the info gathered.
            val exactMatch = findExactMatch(settingsManager, turnNumber, detectedName)
            if (exactMatch != null) {
                MessageLog.i(tag, "[RACE] Found exact match: \"${exactMatch.name}\" AKA \"${exactMatch.nameFormatted}\".")
                settingsManager.close()
                return exactMatch
            }
            
            // Otherwise, do fuzzy matching to find the most similar match using Jaro-Winkler.
            val fuzzyMatch = findFuzzyMatch(settingsManager, turnNumber, detectedName)
            if (fuzzyMatch != null) {
                MessageLog.i(tag, "[RACE] Found fuzzy match: \"${fuzzyMatch.name}\" AKA \"${fuzzyMatch.nameFormatted}\".")
                settingsManager.close()
                return fuzzyMatch
            }
            
            MessageLog.i(tag, "[RACE] No match found for turn $turnNumber with name \"$detectedName\".")
            settingsManager.close()
            null
        } catch (e: Exception) {
            MessageLog.e(tag, "Error looking up race: ${e.message}.")
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
                RACES_COLUMN_DISTANCE_TYPE,
                RACES_COLUMN_TURN_NUMBER
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
                distanceType = cursor.getString(5),
                turnNumber = cursor.getInt(6)
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
                RACES_COLUMN_DISTANCE_TYPE,
                RACES_COLUMN_TURN_NUMBER
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
                    distanceType = cursor.getString(5),
                    turnNumber = cursor.getInt(6)
                )
                if (game.debugMode) MessageLog.d(tag, "Fuzzy match candidate: \"${bestMatch.name}\" AKA \"$nameFormatted\" with similarity ${game.decimalFormat.format(similarity)}.")
                else Log.d(tag, "Fuzzy match candidate: \"${bestMatch.name}\" AKA \"$nameFormatted\" with similarity ${game.decimalFormat.format(similarity)}.")
            }
        } while (cursor.moveToNext())

        cursor.close()
        
        if (bestMatch != null) {
            MessageLog.i(tag, "[RACE] Best fuzzy match: \"${bestMatch.name}\" AKA \"${bestMatch.nameFormatted}\" with similarity ${game.decimalFormat.format(bestScore)}.")
        }
        
        return bestMatch
    }

    /**
     * Check if there are fan or trophy requirements that need to be satisfied.
     */
    fun checkForRacingRequirements() {
        // Check for fan requirement on the main screen.
        val sourceBitmap = game.imageUtils.getSourceBitmap()
        val needsFanRequirement = game.imageUtils.findImageWithBitmap("race_fans_criteria", sourceBitmap, region = game.imageUtils.regionTopHalf) != null
        if (needsFanRequirement) {
            hasFanRequirement = true
            MessageLog.i(tag, "[RACE] Fan requirement criteria detected on main screen. Forcing racing to fulfill requirement.")
        } else {
            // Check for trophy requirement on the main screen.
            val needsTrophyRequirement = game.imageUtils.findImageWithBitmap("race_trophies_criteria", sourceBitmap, region = game.imageUtils.regionTopHalf) != null
            if (needsTrophyRequirement) {
                hasTrophyRequirement = true
                MessageLog.i(tag, "[RACE] Trophy requirement criteria detected on main screen. Forcing racing to fulfill requirement.")
            }
        }
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
        if (game.debugMode) MessageLog.d(
            tag,
            """
            Scoring ${race.name}:
            Fans        = ${race.fans} (${game.decimalFormat.format(fansScore)})
            Grade       = ${race.grade} (${game.decimalFormat.format(gradeScore)})
            Terrain     = ${race.terrain} ($terrainAptitude)
            Distance    = ${race.distanceType} ($distanceAptitude)
            Aptitude    = ${game.decimalFormat.format(aptitudeBonus)}
            Final       = ${game.decimalFormat.format(finalScore)}
            """.trimIndent(),
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
            MessageLog.e(tag, "Database not available for look-ahead race lookup.")
            return emptyList()
        }

        return try {
            val database = settingsManager.getDatabase()
            if (database == null) {
                MessageLog.e(tag, "Database is null for look-ahead race lookup.")
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
            
            MessageLog.i(tag, "[RACE] Found ${races.size} races in look-ahead window (turns $currentTurn to $endTurn).")
            races
        } catch (e: Exception) {
            MessageLog.e(tag, "Error getting look-ahead races: ${e.message}")
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
            MessageLog.e(tag, "Database not available for G1 race check.")
            return false
        }

        return try {
            val database = settingsManager.getDatabase()
            if (database == null) {
                MessageLog.e(tag, "Database is null for G1 race check.")
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
            MessageLog.e(tag, "Error checking for G1 races: ${e.message}")
            settingsManager.close()
            false
        }
    }

    /**
     * Filters the given list of races according to the user's Racing Plan settings.
     *
     * The filtering criteria are loaded from the Racing Plan configuration and include:
     * - **Minimum fans threshold:** Races must have at least this number of fans.
     * - **Preferred terrain:** Only races matching the specified terrain (or "Any") are included.
     * - **Preferred grades:** Races must match one of the preferred grade values.
     *
     * @param races The list of [RaceData] entries to filter.
     * @param bypassMinFans If true, bypasses the minimum fans threshold check (useful for trophy requirement).
     * @return A list of [RaceData] objects that satisfy all Racing Plan filter criteria.
     */
    fun filterRacesBySettings(races: List<RaceData>, bypassMinFans: Boolean = false): List<RaceData> {
        // Parse preferred grades from JSON array string.
        MessageLog.i(tag, "[RACE] Raw preferred grades string: \"$preferredGradesString\".")
        val preferredGrades = try {
            // Parse as JSON array.
            val jsonArray = JSONArray(preferredGradesString)
            val parsed = (0 until jsonArray.length()).map { jsonArray.getString(it) }
            MessageLog.i(tag, "[RACE] Parsed as JSON array: $parsed.")
            parsed
        } catch (e: Exception) {
            MessageLog.i(tag, "[RACE] Error parsing preferred grades: ${e.message}, using fallback.")
            val parsed = preferredGradesString.split(",").map { it.trim() }
            MessageLog.i(tag, "[RACE] Fallback parsing result: $parsed")
            parsed
        }

        if (game.debugMode) MessageLog.d(tag, "Filter criteria: Min fans: $minFansThreshold, terrain: $preferredTerrain, grades: $preferredGrades")
        else Log.d(tag, "Filter criteria: Min fans: $minFansThreshold, terrain: $preferredTerrain, grades: $preferredGrades")
        
        val filteredRaces = races.filter { race ->
            val meetsFansThreshold = bypassMinFans || race.fans >= minFansThreshold
            val meetsTerrainPreference = preferredTerrain == "Any" || race.terrain == preferredTerrain
            val meetsGradePreference = preferredGrades.isEmpty() || preferredGrades.contains(race.grade)
            
            val passes = meetsFansThreshold && meetsTerrainPreference && meetsGradePreference

            // If the race did not pass any of the filters, print the reason why.
            if (!passes) {
                val reasons = mutableListOf<String>()
                if (!meetsFansThreshold) reasons.add("fans ${race.fans} < $minFansThreshold")
                if (!meetsTerrainPreference) reasons.add("terrain ${race.terrain} != $preferredTerrain")
                if (!meetsGradePreference) reasons.add("grade ${race.grade} not in $preferredGrades")
                if (game.debugMode) MessageLog.d(tag, "✗ Filtered out ${race.name}: ${reasons.joinToString(", ")}")
                else Log.d(tag, "✗ Filtered out ${race.name}: ${reasons.joinToString(", ")}")
            } else {
                if (game.debugMode) MessageLog.d(tag, "✓ Passed filter: ${race.name} (fans: ${race.fans}, terrain: ${race.terrain}, grade: ${race.grade})")
                else Log.d(tag, "✓ Passed filter: ${race.name} (fans: ${race.fans}, terrain: ${race.terrain}, grade: ${race.grade})")
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
    private fun isPlannedRaceEligible(plannedRace: PlannedRace, racePlanData: Map<String, RaceData>, dayNumber: Int, currentTurnNumber: Int): Boolean {
        // Find the race in the plan data.
        val raceData = racePlanData[plannedRace.raceName]
        if (raceData == null) {
            MessageLog.e(tag, "Planned race \"${plannedRace.raceName}\" not found in race plan data.")
            return false
        }
        
        val raceTurnNumber = raceData.turnNumber
        val turnDistance = raceTurnNumber - currentTurnNumber
        
        // Check if race is within look-ahead window.
        if (turnDistance < 0) {
            return false
        } else if (turnDistance > lookAheadDays) {
            if (game.debugMode) {
                MessageLog.d(tag, "Planned race \"${plannedRace.raceName}\" is too far ahead of the look-ahead window (distance $turnDistance > lookAheadDays $lookAheadDays).")
            } else {
                Log.d(tag, "Planned race \"${plannedRace.raceName}\" is too far ahead of the look-ahead window (distance $turnDistance > lookAheadDays $lookAheadDays).")
            }
            return false
        }
        
        // For Classic Year, check if it's an eligible racing day using the settings for the standard racing logic.
        if (game.currentDate.year == 2) {
            if (!isEligibleRacingDay(dayNumber)) {
                MessageLog.i(tag, "[RACE] Planned race \"${plannedRace.raceName}\" is not on an eligible racing day (day $dayNumber, interval $daysToRunExtraRaces).")
                return false
            }
        }
        
        MessageLog.i(tag, "[RACE] Planned race \"${plannedRace.raceName}\" is eligible for racing.")
        return true
    }

    /**
     * Checks if a given day number is eligible for racing based on the configured interval.
     *
     * @param dayNumber The day number to check.
     * @return True if the day falls on the racing interval (dayNumber % daysToRunExtraRaces == 0).
     */
    private fun isEligibleRacingDay(dayNumber: Int): Boolean {
        return dayNumber % daysToRunExtraRaces == 0
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
        MessageLog.i(tag, "[RACE] Finding best race in window from ${filteredUpcomingRaces.size} races after filters...")
        
        if (filteredUpcomingRaces.isEmpty()) {
            MessageLog.i(tag, "[RACE] No races provided after filters, cannot find best race.")
            return null
        }

        // For each upcoming race, calculate their score.
        val scoredRaces = filteredUpcomingRaces.map { calculateRaceScore(it) }
        val sortedScoredRaces = scoredRaces.sortedByDescending { it.score }
        MessageLog.i(tag, "[RACE] Scored all races (sorted by score descending):")
        sortedScoredRaces.forEach { scoredRace ->
            if (game.debugMode) MessageLog.d(
                tag,
                "    ${scoredRace.raceData.name}: score=${game.decimalFormat.format(scoredRace.score)}, " +
                "fans=${scoredRace.raceData.fans}(${game.decimalFormat.format(scoredRace.fansScore)}), " +
                "grade=${scoredRace.raceData.grade}(${game.decimalFormat.format(scoredRace.gradeScore)}), " +
                "aptitude=${game.decimalFormat.format(scoredRace.aptitudeBonus)}",
            )
        }
        
        val bestRace = sortedScoredRaces.maxByOrNull { it.score }
        
        if (bestRace != null) {
            MessageLog.i(tag, "[RACE] Best race in window: ${bestRace.raceData.name} (score: ${game.decimalFormat.format(bestRace.score)})")
            MessageLog.i(tag, "[RACE]     Fans: ${bestRace.raceData.fans} (${game.decimalFormat.format(bestRace.fansScore)}), Grade: ${bestRace.raceData.grade} (${game.decimalFormat.format(bestRace.gradeScore)}), Aptitude: ${game.decimalFormat.format(bestRace.aptitudeBonus)}")
        } else {
            MessageLog.i(tag, "[RACE] Failed to determine best race from scored races.")
        }
        
        return bestRace
    }

    /**
     * Calculates opportunity cost to determine whether the bot should race immediately or wait for a better opportunity.
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
    fun calculateOpportunityCost(currentRaces: List<RaceData>, lookAheadDays: Int): Boolean {
        MessageLog.i(tag, "[RACE] Evaluating whether to race now using Opportunity Cost logic...")
        if (currentRaces.isEmpty()) {
            MessageLog.i(tag, "[RACE] No current races available, cannot race now.")
            return false
        }
        
        // Score current races.
        MessageLog.i(tag, "[RACE] Scoring ${currentRaces.size} current races (sorted by score descending):")
        val currentScoredRaces = currentRaces.map { calculateRaceScore(it) }
        val sortedScoredRaces = currentScoredRaces.sortedByDescending { it.score }
        sortedScoredRaces.forEach { scoredRace ->
            MessageLog.i(tag, "[RACE]     Current race: ${scoredRace.raceData.name} (score: ${game.decimalFormat.format(scoredRace.score)})")
        }
        val bestCurrentRace = sortedScoredRaces.maxByOrNull { it.score }
        
        if (bestCurrentRace == null) {
            MessageLog.i(tag, "[RACE] Failed to score current races, cannot race now.")
            return false
        }
        
        MessageLog.i(tag, "[RACE] Best current race: ${bestCurrentRace.raceData.name} (score: ${game.decimalFormat.format(bestCurrentRace.score)})")
        
        // Get and score upcoming races.
        MessageLog.i(tag, "[RACE] Looking ahead $lookAheadDays days for upcoming races...")
        val upcomingRaces = getLookAheadRaces(game.currentDate.turnNumber + 1, lookAheadDays)
        MessageLog.i(tag, "[RACE] Found ${upcomingRaces.size} upcoming races in database.")
        
        val filteredUpcomingRaces = filterRacesBySettings(upcomingRaces)
        MessageLog.i(tag, "[RACE] After filtering: ${filteredUpcomingRaces.size} upcoming races remain.")
        
        val bestUpcomingRace = findBestRaceInWindow(filteredUpcomingRaces)
        
        if (bestUpcomingRace == null) {
            MessageLog.i(tag, "[RACE] No suitable upcoming races found, racing now with best current option.")
            return true
        }
        
        MessageLog.i(tag, "[RACE] Best upcoming race: ${bestUpcomingRace.raceData.name} (score: ${game.decimalFormat.format(bestUpcomingRace.score)}).")
        
        // Opportunity Cost logic.
        val minimumQualityThreshold = 70.0  // Don't race anything scoring below this.
        val timeDecayFactor = 0.90          // Future races are worth this percentage of their score.
        val improvementThreshold = 25.0     // Only wait if improvement is greater than this.

        // Apply time decay to upcoming race score.
        val discountedUpcomingScore = bestUpcomingRace.score * timeDecayFactor
        
        // Calculate opportunity cost: How much better is waiting?
        val improvementFromWaiting = discountedUpcomingScore - bestCurrentRace.score
        
        // Decision criteria.
        val isGoodEnough = bestCurrentRace.score >= minimumQualityThreshold
        val notWorthWaiting = improvementFromWaiting < improvementThreshold
        val shouldRace = isGoodEnough && notWorthWaiting
        
        MessageLog.i(tag, "[RACE] Opportunity Cost Analysis:")
        MessageLog.i(tag, "[RACE]     Current score: ${game.decimalFormat.format(bestCurrentRace.score)}")
        MessageLog.i(tag, "[RACE]     Upcoming score (raw): ${game.decimalFormat.format(bestUpcomingRace.score)}")
        MessageLog.i(tag, "[RACE]     Upcoming score (discounted by ${game.decimalFormat.format((1 - timeDecayFactor) * 100)}%): ${game.decimalFormat.format(discountedUpcomingScore)}")
        MessageLog.i(tag, "[RACE]     Improvement from waiting: ${game.decimalFormat.format(improvementFromWaiting)}")
        MessageLog.i(tag, "[RACE]     Quality check (≥${minimumQualityThreshold}): ${if (isGoodEnough) "PASS" else "FAIL"}")
        MessageLog.i(tag, "[RACE]     Worth waiting check (<${improvementThreshold}): ${if (notWorthWaiting) "PASS" else "FAIL"}")
        MessageLog.i(tag, "[RACE]     Decision: ${if (shouldRace) "RACE NOW" else "WAIT FOR BETTER OPPORTUNITY"}")

        // Print the reasoning for the decision.
        if (shouldRace) {
            MessageLog.i(tag, "[RACE] Reasoning: Current race is good enough (${game.decimalFormat.format(bestCurrentRace.score)} ≥ ${minimumQualityThreshold}) and waiting only gives ${game.decimalFormat.format(improvementFromWaiting)} more points (less than ${improvementThreshold}).")
            // Race now - clear the next race day tracker.
            nextSmartRaceDay = null
        } else {
            val reason = if (!isGoodEnough) {
                "Current race quality too low (${game.decimalFormat.format(bestCurrentRace.score)} < ${minimumQualityThreshold})."
            } else {
                "Worth waiting for better opportunity (+${game.decimalFormat.format(improvementFromWaiting)} points > ${improvementThreshold})."
            }
            MessageLog.i(tag, "[RACE] Reasoning: $reason")
            // Wait for better opportunity - store the turn number to race on.
            val bestUpcomingRaceData = upcomingRaces.find { it.name == bestUpcomingRace.raceData.name }
            nextSmartRaceDay = bestUpcomingRaceData?.turnNumber
            MessageLog.i(tag, "[RACE] Setting next smart race day to turn ${nextSmartRaceDay}.")
        }
        
        return shouldRace
    }

    /**
     * Determines if racing is worthwhile based on turn number and opportunity cost analysis for smart racing.
     * 
     * This function queries the race database to check if races exist at the current turn
     * and uses opportunity cost logic to determine if racing is better than waiting.
     * 
     * @param currentTurnNumber The current turn number in the game.
     * @return True if we should race based on turn analysis, false otherwise.
     */
    private fun shouldRaceSmartCheck(currentTurnNumber: Int): Boolean {
        return try {
            MessageLog.i(tag, "[RACE] Checking eligibility for racing at turn $currentTurnNumber...")
            
            // First, check if there are any races available at the current turn.
            val currentTurnRaces = getLookAheadRaces(currentTurnNumber, 0)
            if (currentTurnRaces.isEmpty()) {
                MessageLog.i(tag, "[RACE] No races available at turn $currentTurnNumber.")
                return false
            }
            
            MessageLog.i(tag, "[RACE] Found ${currentTurnRaces.size} race(s) at turn $currentTurnNumber.")
            
            // Query upcoming races in the look-ahead window for opportunity cost analysis.
            val upcomingRaces = getLookAheadRaces(currentTurnNumber + 1, lookAheadDays)
            MessageLog.i(tag, "[RACE] Found ${upcomingRaces.size} upcoming races in look-ahead window.")
            
            // Apply filters to both current and upcoming races.
            val filteredCurrentRaces = filterRacesBySettings(currentTurnRaces)
            val filteredUpcomingRaces = filterRacesBySettings(upcomingRaces)
            
            MessageLog.i(tag, "[RACE] After filtering: ${filteredCurrentRaces.size} current races, ${filteredUpcomingRaces.size} upcoming races.")
            
            // If no filtered current races exist, we shouldn't race.
            if (filteredCurrentRaces.isEmpty()) {
                MessageLog.i(tag, "[RACE] No current races match the filter criteria. Skipping racing.")
                return false
            }
            
            // If there are no upcoming races to compare against, race now if we have acceptable races.
            if (filteredUpcomingRaces.isEmpty()) {
                MessageLog.i(tag, "[RACE] No upcoming races to compare against. Racing now with available races.")
                return true
            }
            
            // Use opportunity cost logic to determine if we should race now or wait.
            val shouldRace = calculateOpportunityCost(filteredCurrentRaces, lookAheadDays)
            
            shouldRace
        } catch (e: Exception) {
            MessageLog.e(tag, "Error in turn-based racing check: ${e.message}. Falling back to screen-based checks.")
            true  // Return true to fall back to screen checks.
        }
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
        MessageLog.i(tag, "[RACE] Using Smart Racing Plan logic...")

        // Updates the current date and aptitudes for accurate scoring.
        game.updateDate()
        game.updateAptitudes()

        // Load user planned races and race plan data.
        val userPlannedRaces = getUserPlannedRaces()
        val racePlanData = getRacePlanData()
        MessageLog.i(tag, "[RACE] Loaded ${userPlannedRaces.size} user-selected races and ${racePlanData.size} race entries.")

        // Detects all double-star race predictions on screen.
        val doublePredictionLocations = game.imageUtils.findAll("race_extra_double_prediction")
        MessageLog.i(tag, "[RACE] Found ${doublePredictionLocations.size} double-star prediction locations.")
        if (doublePredictionLocations.isEmpty()) {
            MessageLog.i(tag, "[RACE] No double-star predictions found. Canceling racing process.")
            return false
        }

        // Extracts race names from the screen and matches them with the in-game database.
        MessageLog.i(tag, "[RACE] Extracting race names and matching with database...")
        val currentRaces = doublePredictionLocations.mapNotNull { location ->
            val raceName = game.imageUtils.extractRaceName(location)
            val raceData = getRaceByTurnAndName(game.currentDate.turnNumber, raceName)
            if (raceData != null) {
                MessageLog.i(tag, "[RACE] ✓ Matched in database: ${raceData.name} (Grade: ${raceData.grade}, Fans: ${raceData.fans}, Terrain: ${raceData.terrain}).")
                raceData
            } else {
                MessageLog.i(tag, "[RACE] ✗ No match found in database for \"$raceName\".")
                null
            }
        }

        if (currentRaces.isEmpty()) {
            MessageLog.i(tag, "[RACE] No races matched in database. Canceling racing process.")
            return false
        }
        MessageLog.i(tag, "[RACE] Successfully matched ${currentRaces.size} races in database.")

        // If trophy requirement is active, filter to only G1 races.
        // Trophy requirement is independent of racing plan and farming fans settings.
        val racesForSelection = if (hasTrophyRequirement) {
            val g1Races = currentRaces.filter { it.grade == "G1" }
            if (g1Races.isEmpty()) {
                // No G1 races available. Cancel since trophy requirement specifically needs G1 races.
                MessageLog.i(tag, "[RACE] Trophy requirement active but no G1 races available. Canceling racing process (independent of racing plan/farming fans).")
                return false
            } else {
                MessageLog.i(tag, "[RACE] Trophy requirement active. Filtering to ${g1Races.size} G1 races: ${g1Races.map { it.name }}.")
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
        MessageLog.i(tag, "[RACE] Found ${plannedRaces.size} user-selected races on screen: ${plannedRaces.map { it.name }}.")
        MessageLog.i(tag, "[RACE] Found ${regularRaces.size} regular races on screen: ${regularRaces.map { it.name }}.")

        // Filter both lists by user Racing Plan settings.
        // If trophy requirement is active, bypass min fan filtering but still apply other filters.
        val filteredPlannedRaces = if (hasTrophyRequirement) {
            MessageLog.i(tag, "[RACE] Trophy requirement active. Bypassing min fan threshold for G1 races.")
            filterRacesBySettings(plannedRaces, bypassMinFans = true)
        } else {
            filterRacesBySettings(plannedRaces)
        }
        val filteredRegularRaces = if (hasTrophyRequirement) {
            filterRacesBySettings(regularRaces, bypassMinFans = true)
        } else {
            filterRacesBySettings(regularRaces)
        }
        MessageLog.i(tag, "[RACE] After filtering: ${filteredPlannedRaces.size} planned races and ${filteredRegularRaces.size} regular races remain.")

        // Combine all filtered races for Opportunity Cost analysis.
        val allFilteredRaces = filteredPlannedRaces + filteredRegularRaces
        if (allFilteredRaces.isEmpty()) {
            MessageLog.i(tag, "[RACE] No races match current settings after filtering. Canceling racing process.")
            return false
        }

        // Evaluate whether the bot should race now using Opportunity Cost logic.
        // If trophy requirement is active, bypass opportunity cost to prioritize clearing the requirement.
        if (hasTrophyRequirement) {
            MessageLog.i(tag, "[RACE] Bypassing opportunity cost analysis to prioritize G1 race due to trophy requirement.")
        } else if (!calculateOpportunityCost(allFilteredRaces, lookAheadDays)) {
            MessageLog.i(tag, "[RACE] Smart racing suggests waiting for better opportunities. Canceling racing process.")
            return false
        }

        // Decide which races to score based on availability.
        val racesToScore = if (filteredPlannedRaces.isNotEmpty()) {
            // Prefer planned races, but include regular races for comparison.
            MessageLog.i(tag, "[RACE] Prioritizing ${filteredPlannedRaces.size} planned races with ${filteredRegularRaces.size} regular races for comparison.")
            filteredPlannedRaces + filteredRegularRaces
        } else {
            // No planned races available, use regular races only.
            MessageLog.i(tag, "[RACE] No planned races available, using ${filteredRegularRaces.size} regular races only.")
            filteredRegularRaces
        }

        // Score all eligible races with bonus for planned races.
        val scoredRaces = racesToScore.map { race ->
            val baseScore = calculateRaceScore(race)
            if (plannedRaces.contains(race)) {
                // Add a bonus for planned races.
                val bonusScore = baseScore.copy(score = baseScore.score + 50.0)
                MessageLog.i(tag, "[RACE] Planned race \"${race.name}\" gets a bonus: ${game.decimalFormat.format(baseScore.score)} -> ${game.decimalFormat.format(bonusScore.score)}.")
                bonusScore
            } else {
                baseScore
            }
        }

        // Sort by score and find the best race.
        val sortedScoredRaces = scoredRaces.sortedByDescending { it.score }
        val bestRace = sortedScoredRaces.first()

        MessageLog.i(tag, "[RACE] Best race selected: ${bestRace.raceData.name} (score: ${game.decimalFormat.format(bestRace.score)}).")
        if (plannedRaces.contains(bestRace.raceData)) {
            MessageLog.i(tag, "[RACE] Selected race is from user's planned races list.")
        } else {
            MessageLog.i(tag, "[RACE] Selected race is from regular available races.")
        }

        // Locates the best race on screen and selects it.
        MessageLog.i(tag, "[RACE] Looking for target race \"${bestRace.raceData.name}\" on screen...")
        val targetRaceLocation = doublePredictionLocations.find { location ->
            val raceName = game.imageUtils.extractRaceName(location)
            val raceData = getRaceByTurnAndName(game.currentDate.turnNumber, raceName)
            val matches = raceData?.name == bestRace.raceData.name
            if (matches) MessageLog.i(tag, "[RACE] ✓ Found target race at location (${location.x}, ${location.y}).")
            matches
        } ?: run {
            MessageLog.i(tag, "[RACE] Could not find target race \"${bestRace.raceData.name}\" on screen. Canceling racing process.")
            return false
        }

        MessageLog.i(tag, "[RACE] Selecting smart racing choice: ${bestRace.raceData.name} (score: ${game.decimalFormat.format(bestRace.score)}).")
        game.tap(targetRaceLocation.x, targetRaceLocation.y, "race_extra_double_prediction", ignoreWaiting = true)

        return true
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Determines if extra race is eligible to be run based on various eligibility criteria.
     *
     * This function consolidates all eligibility checking logic including:
     * - Force racing checks
     * - Planned race eligibility (for years 1-2)
     * - Opportunity cost analysis (via smart racing check)
     * - Screen restrictions (locked, summer, UMA finals)
     * - Day eligibility checks (optimal day, interval day, standard)
     *
     * @return True if extra race is eligible, false otherwise.
     */
    fun isExtraRaceEligible(): Boolean {
        val dayNumber = game.imageUtils.determineDayForExtraRace()
        MessageLog.i(tag, "[RACE] Current remaining number of days before the next mandatory race: $dayNumber.")

        // If the setting to force racing extra races is enabled, always return true.
        if (enableForceRacing) return true

        // For years 1-2, check if planned races are eligible before proceeding.
        if (game.currentDate.year == 2 && enableRacingPlan) {
            val userPlannedRaces = getUserPlannedRaces()
            if (userPlannedRaces.isNotEmpty()) {
                val racePlanData = getRacePlanData()
                if (racePlanData.isNotEmpty()) {
                    val currentTurnNumber = game.currentDate.turnNumber
                    
                    // Check each planned race for eligibility.
                    val eligiblePlannedRaces = userPlannedRaces.filter { plannedRace ->
                        isPlannedRaceEligible(plannedRace, racePlanData, dayNumber, currentTurnNumber)
                    }
                    
                    if (eligiblePlannedRaces.isEmpty()) {
                        MessageLog.i(tag, "[RACE] No user-selected races are eligible at turn $currentTurnNumber.")
                        return false
                    }
                    
                    MessageLog.i(tag, "[RACE] Found ${eligiblePlannedRaces.size} eligible user-selected races: ${eligiblePlannedRaces.map { it.raceName }}.")
                } else {
                    MessageLog.i(tag, "[RACE] No race plan data available for eligibility checking.")
                    return false
                }
            } else {
                MessageLog.i(tag, "[RACE] No user-selected races configured.")
                return false
            }
        }

        // If fan or trophy requirement is detected, bypass smart racing logic to force racing.
        // Both requirements are independent of racing plan and farming fans settings.
        if (hasFanRequirement) {
            MessageLog.i(tag, "[RACE] Fan requirement detected. Bypassing smart racing logic to fulfill requirement.")
        } else if (hasTrophyRequirement) {
            // Trophy requirement: Check if G1 races exist at current turn before proceeding.
            // If no G1 races are available, still allow regular racing if it's a regular race day or smart racing day.
            if (!hasG1RacesAtTurn(game.currentDate.turnNumber)) {
                // Check if regular racing is allowed (farming fans enabled and it's a regular race day, or it's a smart racing day).
                val isRegularRacingDay = enableFarmingFans && isEligibleRacingDay(dayNumber)
                val isSmartRacingDay = enableRacingPlan && enableFarmingFans && nextSmartRaceDay == dayNumber
                
                if (isRegularRacingDay || isSmartRacingDay) {
                    MessageLog.i(tag, "[RACE] Trophy requirement detected but no G1 races at turn ${game.currentDate.turnNumber}. Allowing regular racing on eligible day.")
                } else {
                    MessageLog.i(tag, "[RACE] Trophy requirement detected but no G1 races available at turn ${game.currentDate.turnNumber} and not a regular/smart racing day. Skipping racing.")
                    return false
                }
            } else {
                MessageLog.i(tag, "[RACE] Trophy requirement detected. G1 races available at turn ${game.currentDate.turnNumber}. Proceeding to racing screen.")
            }
        } else if (enableRacingPlan && enableFarmingFans) {
            // Smart racing: Check turn-based eligibility before screen checks.
            // Only run opportunity cost analysis with smartRacingCheckInterval.
            val isCheckInterval = game.currentDate.turnNumber % smartRacingCheckInterval == 0
            
            if (isCheckInterval) {
                MessageLog.i(tag, "[RACE] Running opportunity cost analysis at turn ${game.currentDate.turnNumber} (smartRacingCheckInterval: every $smartRacingCheckInterval turns)...")
                
                val shouldRaceFromTurnCheck = shouldRaceSmartCheck(game.currentDate.turnNumber)
                if (!shouldRaceFromTurnCheck) {
                    MessageLog.i(tag, "[RACE] No suitable races at turn ${game.currentDate.turnNumber} based on opportunity cost analysis.")
                    return false
                }
                
                MessageLog.i(tag, "[RACE] Opportunity cost analysis completed, proceeding with screen checks...")
            } else {
                MessageLog.i(tag, "[RACE] Skipping opportunity cost analysis (turn ${game.currentDate.turnNumber} does not match smartRacingCheckInterval). Using cached optimal race day.")
            }
        }

        // Check for common restrictions that apply to both smart and standard racing.
        val sourceBitmap = game.imageUtils.getSourceBitmap()
        val isUmaFinalsLocked = game.imageUtils.findImageWithBitmap("race_select_extra_locked_uma_finals", sourceBitmap, region = game.imageUtils.regionBottomHalf) != null
        val isLocked = game.imageUtils.findImageWithBitmap("race_select_extra_locked", sourceBitmap, region = game.imageUtils.regionBottomHalf) != null
        val isSummer = game.imageUtils.findImageWithBitmap("recover_energy_summer", sourceBitmap, region = game.imageUtils.regionBottomHalf) != null
        
        if (isUmaFinalsLocked) {
            MessageLog.i(tag, "[RACE] It is UMA Finals right now so there will be no extra races. Stopping extra race check.")
            return false
        } else if (isLocked) {
            MessageLog.i(tag, "[RACE] Extra Races button is currently locked. Stopping extra race check.")
            return false
        } else if (isSummer) {
            MessageLog.i(tag, "[RACE] It is currently Summer right now. Stopping extra race check.")
            return false
        }

        // For smart racing, if we got here, the turn-based check passed, so we should race.
        // For standard racing, use the interval check.
        // Both fan and trophy requirements are independent of racing plan and farming fans settings.
        if (hasFanRequirement) {
            MessageLog.i(tag, "[RACE] Fan requirement detected. Allowing racing on any eligible day (independent of racing plan/farming fans).")
            return !raceRepeatWarningCheck
        } else if (hasTrophyRequirement) {
            // Trophy requirement: G1 race availability was already checked above via database query.
            // If no G1 races were found, regular racing eligibility was also checked.
            return !raceRepeatWarningCheck
        } else if (enableRacingPlan && enableFarmingFans) {
            // Check if current day matches the optimal race day or falls on the interval.
            val isOptimalDay = nextSmartRaceDay == dayNumber
            val isIntervalDay = isEligibleRacingDay(dayNumber)
            
            if (isOptimalDay) {
                MessageLog.i(tag, "[RACE] Current day ($dayNumber) matches optimal race day.")
                return !raceRepeatWarningCheck
            } else if (isIntervalDay) {
                MessageLog.i(tag, "[RACE] Current day ($dayNumber) falls on racing interval ($daysToRunExtraRaces).")
                return !raceRepeatWarningCheck
            } else {
                MessageLog.i(tag, "[RACE] Current day ($dayNumber) is not optimal (next: $nextSmartRaceDay, interval: $daysToRunExtraRaces).")
                return false
            }
        }

        // Standard racing logic.
        return enableFarmingFans && isEligibleRacingDay(dayNumber) && !raceRepeatWarningCheck
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
        MessageLog.i(tag, "[RACE] Using traditional racing logic for extra races...")

        // 1. Detects double-star races on screen.
        val doublePredictionLocations = game.imageUtils.findAll("race_extra_double_prediction")
        val maxCount = doublePredictionLocations.size
        if (maxCount == 0) {
            MessageLog.w(tag, "No extra races found on screen. Canceling racing process.")
            return false
        }

        // 2. If only one race has double predictions, check if it's G1 when trophy requirement is active.
        if (maxCount == 1) {
            if (hasTrophyRequirement) {
                game.updateDate()
                val raceName = game.imageUtils.extractRaceName(doublePredictionLocations[0])
                val raceData = getRaceByTurnAndName(game.currentDate.turnNumber, raceName)
                if (raceData?.grade == "G1") {
                    MessageLog.i(tag, "[RACE] Only one race with double predictions and it's G1. Selecting it.")
                    game.tap(doublePredictionLocations[0].x, doublePredictionLocations[0].y, "race_extra_double_prediction", ignoreWaiting = true)
                    return true
                } else {
                    // Not G1. Trophy requirement specifically needs G1 races, so cancel.
                    // Trophy requirement is independent of racing plan and farming fans settings.
                    MessageLog.i(tag, "[RACE] Trophy requirement active but only non-G1 race available. Canceling racing process (independent of racing plan/farming fans).")
                    return false
                }
            } else {
                MessageLog.i(tag, "[RACE] Only one race with double predictions. Selecting it.")
                game.tap(doublePredictionLocations[0].x, doublePredictionLocations[0].y, "race_extra_double_prediction", ignoreWaiting = true)
                return true
            }
        }

        // 3. Otherwise, iterates through each extra race to determine fan gain and double prediction status.
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
                val raceData = getRaceByTurnAndName(game.currentDate.turnNumber, raceName)
                if (raceData?.grade == "G1") index else null
            }
            
            if (g1Indices.isEmpty()) {
                // No G1 races available. Cancel since trophy requirement specifically needs G1 races.
                // Trophy requirement is independent of racing plan and farming fans settings.
                MessageLog.i(tag, "[RACE] Trophy requirement active but no G1 races available. Canceling racing process (independent of racing plan/farming fans).")
                return false
            } else {
                MessageLog.i(tag, "[RACE] Trophy requirement active. Filtering to ${g1Indices.size} G1 races.")
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
        MessageLog.i(tag, "[RACE] Number of fans detected for each extra race are: ${filteredRaces.joinToString(", ") { it.fans.toString() }}")

        // 4. Evaluates which race to select based on maximum fans and double prediction priority (if force racing is enabled).
        val index = if (!enableForceRacing) {
            filteredRaces.indexOfFirst { it.fans == maxFans }
        } else {
            filteredRaces.indexOfFirst { it.hasDoublePredictions }.takeIf { it != -1 } ?: filteredRaces.indexOfFirst { it.fans == maxFans }
        }

        // 5. Selects the determined race on screen.
        MessageLog.i(tag, "[RACE] Selecting extra race at option #${index + 1}.")
        val target = filteredLocations[index]
        game.tap(target.x - game.imageUtils.relWidth((100 * 1.36).toInt()), target.y - game.imageUtils.relHeight(70), "race_extra_selection", ignoreWaiting = true)

        return true
    }

    /**
     * The entry point for handling mandatory or extra races.
     *
     * @return True if the mandatory/extra race was completed successfully. Otherwise false.
     */
    fun handleRaceEvents(): Boolean {
        MessageLog.i(tag, "\n********************")
        MessageLog.i(tag, "[RACE] Starting Racing process on ${game.printFormattedDate()}.")
        if (encounteredRacingPopup) {
            // Dismiss the insufficient fans popup here and head to the Race Selection screen.
            game.findAndTapImage("race_confirm", tries = 1, region = game.imageUtils.regionBottomHalf)
            encounteredRacingPopup = false
            game.wait(1.0)
            
            // Now check if there is a racing requirement.
            checkForRacingRequirements()
        }

        // If there are no races available, cancel the racing process.
        if (game.imageUtils.findImage("race_none_available", tries = 1, region = game.imageUtils.regionMiddle, suppressError = true).first != null) {
            MessageLog.i(tag, "[RACE] There are no races to compete in. Canceling the racing process and doing something else.")
            MessageLog.i(tag, "********************")
            return false
        }

        skipRacing = false

        // First, check if there is a mandatory or a extra race available. If so, head into the Race Selection screen.
        // Note: If there is a mandatory race, the bot would be on the Home screen.
        // Otherwise, it would have found itself at the Race Selection screen already (by way of the insufficient fans popup).
        if (game.findAndTapImage("race_select_mandatory", tries = 1, region = game.imageUtils.regionBottomHalf)) {
            MessageLog.i(tag, "[RACE] Starting process for handling a mandatory race.")

            if (enableStopOnMandatoryRace) {
                MessageLog.i(tag, "********************")
                detectedMandatoryRaceCheck = true
                return false
            }

            // If there is a popup warning about racing too many times, confirm the popup to continue as this is a mandatory race.
            game.findAndTapImage("ok", tries = 1, region = game.imageUtils.regionMiddle, suppressError = true)
            game.wait(1.0)

            // There is a mandatory race. Now confirm the selection and the resultant popup and then wait for the game to load.
            game.wait(2.0)
            MessageLog.i(tag, "[RACE] Confirming the mandatory race selection.")
            game.findAndTapImage("race_confirm", tries = 3, region = game.imageUtils.regionBottomHalf)
            game.wait(1.0)
            MessageLog.i(tag, "[RACE] Confirming any popup from the mandatory race selection.")
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

            MessageLog.i(tag, "[RACE] Racing process for Mandatory Race is completed.")
            MessageLog.i(tag, "********************")
            return true
        } else if (game.currentDate.phase != "Pre-Debut" && game.findAndTapImage("race_select_extra", tries = 1, region = game.imageUtils.regionBottomHalf)) {
            MessageLog.i(tag, "[RACE] Starting process for handling a extra race.")

            // If there is a popup warning about repeating races 3+ times, stop the process and do something else other than racing.
            if (game.imageUtils.findImage("race_repeat_warning").first != null) {
                if (!enableForceRacing) {
                    raceRepeatWarningCheck = true
                    MessageLog.i(tag, "[RACE] Closing popup warning of doing more than 3+ races and setting flag to prevent racing for now. Canceling the racing process and doing something else.")
                    game.findAndTapImage("cancel", region = game.imageUtils.regionBottomHalf)
                    MessageLog.i(tag, "********************")
                    return false
                } else {
                    game.findAndTapImage("ok", tries = 1, region = game.imageUtils.regionMiddle)
                    game.wait(1.0)
                }
            }

            // There is a extra race.
            val statusLocation = game.imageUtils.findImage("race_status").first
            if (statusLocation == null) {
                MessageLog.e(tag, "Unable to determine existence of list of extra races. Canceling the racing process and doing something else.")
                MessageLog.i(tag, "********************")
                return false
            }

            val maxCount = game.imageUtils.findAll("race_selection_fans", region = game.imageUtils.regionBottomHalf).size
            if (maxCount == 0) {
                MessageLog.w(tag, "Was unable to find any extra races to select. Canceling the racing process and doing something else.")
                MessageLog.i(tag, "********************")
                return false
            } else {
                MessageLog.i(tag, "[RACE] There are $maxCount extra race options currently on screen.")
            }

            if (hasFanRequirement) MessageLog.i(tag, "[RACE] Fan requirement criteria detected. This race must be completed to meet the requirement.")
            if (hasTrophyRequirement) MessageLog.i(tag, "[RACE] Trophy requirement criteria detected. Only G1 races will be selected to meet the requirement.")

            // Determine whether to use smart racing with user-selected races or standard racing.
            val useSmartRacing = if (hasFanRequirement) {
                // If fan requirement is needed, force standard racing to ensure the race proceeds.
                false
            } else if (hasTrophyRequirement) {
                // Trophy requirement can use smart racing as it filters to G1 races internally.
                // Use smart racing for all years except Year 1 (Junior Year).
                game.currentDate.year != 1
            } else if (game.currentDate.year == 3) {
                // Year 3 (Senior Year): Use smart racing if conditions are met.
                enableFarmingFans && !enableForceRacing && enableRacingPlan
            } else {
                // Year 2 (Classic Year): Use smart racing if conditions are met.
                // The planned race eligibility check is now handled inside isExtraRaceEligible().
                // Year 1 (Junior Year) will use the standard racing logic.
                game.currentDate.year == 2 && enableRacingPlan
            }

            val success = if (useSmartRacing) {
                if (game.currentDate.year == 3) {
                    MessageLog.i(tag, "[RACE] Using smart racing for Senior Year.")
                } else {
                    MessageLog.i(tag, "[RACE] Using smart racing with user-selected races for Year ${game.currentDate.year}.")
                }
                handleSmartRacing()
            } else {
                // Use the standard racing logic.
                // If needed, print the reason(s) to why the smart racing logic was not started.
                if (enableRacingPlan && !hasFanRequirement && !hasTrophyRequirement) {
                    MessageLog.i(tag, "[RACE] Smart racing conditions not met due to current settings, using traditional racing logic...")
                    MessageLog.i(tag, "[RACE] Reason: One or more conditions failed:")
                    if (game.currentDate.year == 3) {
                        if (!enableFarmingFans) MessageLog.i(tag, "[RACE]   - enableFarmingFans is false")
                        if (enableForceRacing) MessageLog.i(tag, "[RACE]   - enableForceRacing is true")
                    } else if (game.currentDate.year == 1) {
                        MessageLog.i(tag, "[RACE]   - It is currently the Junior Year.")
                    } else {
                        MessageLog.i(tag, "[RACE]   - No eligible user-selected races found for Year ${game.currentDate.year}")
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

            MessageLog.i(tag, "[RACE] Racing process for Extra Race is completed.")
            MessageLog.i(tag, "********************")
            return true
        }

        MessageLog.i(tag, "********************")
        return false
    }

    /**
     * The entry point for handling standalone races if the user started the bot on the Racing screen.
     */
    fun handleStandaloneRace() {
        MessageLog.i(tag, "\n********************")
        MessageLog.i(tag, "[RACE] Starting Standalone Racing process...")

        // Skip the race if possible, otherwise run it manually.
        val resultCheck: Boolean = if (game.imageUtils.findImage("race_skip_locked", tries = 5, region = game.imageUtils.regionBottomHalf).first == null) {
            skipRace()
        } else {
            manualRace()
        }

        finishRace(resultCheck)

        MessageLog.i(tag, "[RACE] Racing process for Standalone Race is completed.")
        MessageLog.i(tag, "********************")
    }

    /**
     * Skips the current race to get to the results screen.
     *
     * @return True if the bot completed the race with retry attempts remaining. Otherwise false.
     */
    private fun skipRace(): Boolean {
        while (raceRetries >= 0) {
            MessageLog.i(tag, "[RACE] Skipping race...")

            // Press the skip button and then wait for your result of the race to show.
            if (game.findAndTapImage("race_skip", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                MessageLog.i(tag, "[RACE] Race was able to be skipped.")
            }
            game.wait(2.0)

            // Now tap on the screen to get past the Race Result screen.
            game.tap(350.0, 450.0, "ok", taps = 3)

            // Check if the race needed to be retried.
            if (game.imageUtils.findImage("race_retry", tries = 5, region = game.imageUtils.regionBottomHalf, suppressError = true).first != null) {
                if (disableRaceRetries) {
                    MessageLog.i(tag, "\n[END] Stopping the bot due to failing a mandatory race.")
                    MessageLog.i(tag, "********************")
                    game.notificationMessage = "Stopping the bot due to failing a mandatory race."
                    throw IllegalStateException()
                }
                game.findAndTapImage("race_retry", tries = 1, region = game.imageUtils.regionBottomHalf, suppressError = true)
                MessageLog.i(tag, "[RACE] The skipped race failed and needs to be run again. Attempting to retry...")
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
            MessageLog.i(tag, "[RACE] Skipping manual race...")

            // Press the manual button.
            if (game.findAndTapImage("race_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                MessageLog.i(tag, "[RACE] Started the manual race.")
            }
            game.wait(2.0)

            // Confirm the Race Playback popup if it appears.
            if (game.findAndTapImage("ok", tries = 1, region = game.imageUtils.regionMiddle, suppressError = true)) {
                MessageLog.i(tag, "[RACE] Confirmed the Race Playback popup.")
                game.wait(5.0)
            }

            game.waitForLoading()

            // Now press the confirm button to get past the list of participants.
            if (game.findAndTapImage("race_confirm", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                MessageLog.i(tag, "[RACE] Dismissed the list of participants.")
            }
            game.waitForLoading()
            game.wait(1.0)
            game.waitForLoading()
            game.wait(1.0)

            // Skip the part where it reveals the name of the race.
            if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                MessageLog.i(tag, "[RACE] Skipped the name reveal of the race.")
            }
            // Skip the walkthrough of the starting gate.
            if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                MessageLog.i(tag, "[RACE] Skipped the walkthrough of the starting gate.")
            }
            game.wait(3.0)
            // Skip the start of the race.
            if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                MessageLog.i(tag, "[RACE] Skipped the start of the race.")
            }
            // Skip the lead up to the finish line.
            if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                MessageLog.i(tag, "[RACE] Skipped the lead up to the finish line.")
            }
            game.wait(2.0)
            // Skip the result screen.
            if (game.findAndTapImage("race_skip_manual", tries = 30, region = game.imageUtils.regionBottomHalf)) {
                MessageLog.i(tag, "[RACE] Skipped the results screen.")
            }
            game.wait(2.0)

            game.waitForLoading()
            game.wait(1.0)

            // Check if the race needed to be retried.
            if (game.imageUtils.findImage("race_retry", tries = 5, region = game.imageUtils.regionBottomHalf, suppressError = true).first != null) {
                if (disableRaceRetries) {
                    MessageLog.i(tag, "\n[END] Stopping the bot due to failing a mandatory race.")
                    MessageLog.i(tag, "********************")
                    game.notificationMessage = "Stopping the bot due to failing a mandatory race."
                    throw IllegalStateException()
                }
                game.findAndTapImage("race_retry", tries = 1, region = game.imageUtils.regionBottomHalf, suppressError = true)
                MessageLog.i(tag, "[RACE] Manual race failed and needs to be run again. Attempting to retry...")
                game.wait(5.0)
                raceRetries--
            } else {
                // Check if a Trophy was acquired.
                if (game.findAndTapImage("race_accept_trophy", tries = 5, region = game.imageUtils.regionBottomHalf)) {
                    MessageLog.i(tag, "[RACE] Closing popup to claim trophy...")
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
        MessageLog.i(tag, "\n[RACE] Now performing cleanup and finishing the race.")
        if (!resultCheck) {
            game.notificationMessage = "Bot has run out of retry attempts for racing. Stopping the bot now..."
            throw IllegalStateException()
        }

        // Bot will be at the screen where it shows the final positions of all participants.
        // Press the confirm button and wait to see the triangle of fans.
        MessageLog.i(tag, "[RACE] Now attempting to confirm the final positions of all participants and number of gained fans")
        if (game.findAndTapImage("next", tries = 30, region = game.imageUtils.regionBottomHalf)) {
            game.wait(0.5)

            // Now tap on the screen to get to the next screen.
            game.tap(350.0, 750.0, "ok", taps = 3)

            // Now press the end button to finish the race.
            game.findAndTapImage("race_end", tries = 30, region = game.imageUtils.regionBottomHalf)

            if (!isExtra) {
                MessageLog.i(tag, "[RACE] Seeing if a Training Goal popup will appear.")
                // Wait until the popup showing the completion of a Training Goal appears and confirm it.
                // There will be dialog before it so the delay should be longer.
                game.wait(5.0)
                if (game.findAndTapImage("next", tries = 10, region = game.imageUtils.regionBottomHalf)) {
                    game.wait(2.0)

                    // Now confirm the completion of a Training Goal popup.
                    MessageLog.i(tag, "[RACE] There was a Training Goal popup. Confirming it now.")
                    game.findAndTapImage("next", tries = 10, region = game.imageUtils.regionBottomHalf)
                }
            } else if (game.findAndTapImage("next", tries = 10, region = game.imageUtils.regionBottomHalf)) {
                // Same as above but without the longer delay.
                game.wait(2.0)
                game.findAndTapImage("race_end", tries = 10, region = game.imageUtils.regionBottomHalf)
            }

            firstTimeRacing = false
            hasFanRequirement = false  // Reset fan requirement flag after race completion.
            hasTrophyRequirement = false  // Reset trophy requirement flag after race completion.
        } else {
            MessageLog.e(tag, "Cannot start the cleanup process for finishing the race. Moving on...")
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
        } else if (!firstTimeRacing && !hasAppliedStrategyOverride && game.currentDate.year != 1) {
            return
        }

        val currentYear = game.currentDate.year
        MessageLog.i(tag, "[RACE] Handling race strategy override for Year $currentYear.")

        // Check if we're on the racing screen by looking for the Change Strategy button.
        if (!game.findAndTapImage("race_change_strategy", tries = 1, region = game.imageUtils.regionBottomHalf)) {
            MessageLog.i(tag, "[RACE] Change Strategy button not found. Skipping strategy override.")
            return
        }

        // Wait for the strategy selection popup to appear.
        game.wait(2.0)

        // Find the confirm button to use as reference point for strategy coordinates.
        val confirmLocation = game.imageUtils.findImage("confirm", region = game.imageUtils.regionBottomHalf).first
        if (confirmLocation == null) {
            MessageLog.e(tag, "Could not find confirm button for strategy selection. Skipping strategy override.")
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
                    MessageLog.i(tag, "[RACE] Detected original race strategy: $originalStrategy")
                }

                // Apply the user's selected strategy.
                MessageLog.i(tag, "[RACE] Applying user-selected strategy: $juniorYearRaceStrategy")

                if (modifyRacingStrategy(baseX, baseY, juniorYearRaceStrategy)) {
                    hasAppliedStrategyOverride = true
                    MessageLog.i(tag, "[RACE] Successfully applied strategy override for Junior Year.")
                } else {
                    MessageLog.e(tag, "Failed to apply strategy override.")
                }
            }
        } else {
            // Year 2+: Apply the detected original strategy if available, otherwise use user-selected strategy.
            val strategyToApply = if (detectedOriginalStrategy != null) {
                detectedOriginalStrategy!!
            } else {
                userSelectedOriginalStrategy
            }
            
            MessageLog.i(tag, "[RACE] Applying original race strategy: $strategyToApply")
            
            if (modifyRacingStrategy(baseX, baseY, strategyToApply)) {
                hasAppliedStrategyOverride = false
                MessageLog.i(tag, "[RACE] Successfully applied original strategy. Strategy override disabled for rest of run.")
            } else {
                MessageLog.e(tag, "Failed to apply original strategy.")
            }
        }

        // Click confirm to apply the strategy change.
        if (game.findAndTapImage("confirm", tries = 3, region = game.imageUtils.regionBottomHalf)) {
            game.wait(2.0)
            MessageLog.i(tag, "[RACE] Strategy change confirmed.")
        } else {
            MessageLog.e(tag, "Failed to confirm strategy change.")
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
            MessageLog.e(tag, "Unknown strategy: $strategy")
            return false
        }

        val targetX = (baseX + offset.first).toDouble()
        val targetY = (baseY + offset.second).toDouble()

        MessageLog.i(tag, "[RACE] Clicking strategy button at ($targetX, $targetY) for strategy: $strategy")
        
        return game.gestureUtils.tap(targetX, targetY)
    }
}