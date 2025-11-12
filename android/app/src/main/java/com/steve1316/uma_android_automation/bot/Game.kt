package com.steve1316.uma_android_automation.bot

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.campaigns.AoHaru
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.automation_library.utils.ImageUtils.ScaleConfidenceResult
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.MyAccessibilityService
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.uma_android_automation.utils.GameDateParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.opencv.core.Point
import java.text.DecimalFormat
import kotlin.intArrayOf

/**
 * Main driver for bot activity and navigation.
 */
class Game(val myContext: Context) {
	private val TAG: String = "[${MainActivity.loggerTag}]Game"
	var notificationMessage: String = ""

	val imageUtils: CustomImageUtils = CustomImageUtils(myContext, this)
	val gestureUtils: MyAccessibilityService = MyAccessibilityService.getInstance()
	val gameDateParser: GameDateParser = GameDateParser()

	val decimalFormat = DecimalFormat("#.##")

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// SQLite Settings
	val campaign: String = SettingsHelper.getStringSetting("general", "scenario")
	val debugMode: Boolean = SettingsHelper.getBooleanSetting("debug", "enableDebugMode")

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	val training: Training = Training(this)
	val racing: Racing = Racing(this)
	val trainingEvent: TrainingEvent = TrainingEvent(this)

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// Stops
	val enableSkillPointCheck: Boolean = SettingsHelper.getBooleanSetting("general", "enableSkillPointCheck")
	val skillPointsRequired: Int = SettingsHelper.getIntSetting("general", "skillPointCheck")
	private val enablePopupCheck: Boolean = SettingsHelper.getBooleanSetting("general", "enablePopupCheck")

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// Misc
    var currentDate: Date = Date(1, "Early", 1, 1)
	var aptitudes: Aptitudes = Aptitudes(
		track = Track("B", "B"),
		distance = Distance("B", "B", "B", "B"),
		style = Style("B", "B", "B", "B")
	)
	private var inheritancesDone = 0
    private var needToUpdateAptitudes: Boolean = true

	data class Date(
		val year: Int,
		val phase: String,
		val month: Int,
		val turnNumber: Int
	)

	data class Track(
		val turf: String,
		val dirt: String
	)

	data class Distance(
		val sprint: String,
		val mile: String,
		val medium: String,
		val long: String
	)

	data class Style(
		val front: String,
		val pace: String,
		val late: String,
		val end: String
	)

	data class Aptitudes(
		val track: Track,
		val distance: Distance,
		val style: Style
	)

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// Helper functions for bot interaction.

	/**
	 * Wait the specified seconds to account for ping or loading.
	 * It also checks for interruption every 100ms to allow faster interruption and checks if the game is still in the middle of loading.
	 *
	 * @param seconds Number of seconds to pause execution.
	 * @param skipWaitingForLoading If true, then it will skip the loading check. Defaults to false.
	 */
	fun wait(seconds: Double, skipWaitingForLoading: Boolean = false) {
		val totalMillis = (seconds * 1000).toLong()
		// Check for interruption every 100ms.
		val checkInterval = 100L

		var remainingMillis = totalMillis
		while (remainingMillis > 0) {
			if (!BotService.isRunning) {
				throw InterruptedException()
			}

			val sleepTime = minOf(checkInterval, remainingMillis)
			runBlocking {
				delay(sleepTime)
			}
			remainingMillis -= sleepTime
		}

		if (!skipWaitingForLoading) {
			// Check if the game is still loading as well.
			waitForLoading()
		}
	}

	/**
	 * Wait for the game to finish loading.
	 */
	fun waitForLoading() {
		while (checkLoading()) {
			// Avoid an infinite loop by setting the flag to true.
			wait(0.5, skipWaitingForLoading = true)
		}
	}

	/**
	 * Find and tap the specified image.
	 *
	 * @param imageName Name of the button image file in the /assets/images/ folder.
     * @param sourceBitmap The source bitmap to find the image on. This is optional and defaults to null which will fetch its own source bitmap.
	 * @param tries Number of tries to find the specified button. Defaults to 3.
	 * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
	 * @param taps Specify the number of taps on the specified image. Defaults to 1.
	 * @param suppressError Whether or not to suppress saving error messages to the log in failing to find the button. Defaults to false.
	 * @return True if the button was found and clicked. False otherwise.
	 */
	fun findAndTapImage(imageName: String, sourceBitmap: Bitmap? = null, tries: Int = 3, region: IntArray = intArrayOf(0, 0, 0, 0), taps: Int = 1, suppressError: Boolean = false): Boolean {
		if (debugMode) {
			MessageLog.d(TAG, "Now attempting to find and click the \"$imageName\" button.")
		}

		val tempLocation: Point? = if (sourceBitmap == null) {
            imageUtils.findImage(imageName, tries = tries, region = region, suppressError = suppressError).first
        } else {
            imageUtils.findImageWithBitmap(imageName, sourceBitmap, region = region, suppressError = suppressError)
        }

		return if (tempLocation != null) {
			Log.d(TAG, "Found and going to tap: $imageName")
			tap(tempLocation.x, tempLocation.y, imageName, taps = taps)
			true
		} else {
			false
		}
	}

	/**
	 * Performs a tap on the screen at the coordinates and then will wait until the game processes the server request and gets a response back.
	 *
	 * @param x The x-coordinate.
	 * @param y The y-coordinate.
	 * @param imageName The template image name to use for tap location randomization.
	 * @param taps The number of taps.
	 * @param ignoreWaiting Flag to ignore checking if the game is busy loading.
	 */
	fun tap(x: Double, y: Double, imageName: String, taps: Int = 1, ignoreWaiting: Boolean = false) {
		// Perform the tap.
		gestureUtils.tap(x, y, imageName, taps = taps)

		if (!ignoreWaiting) {
			// Now check if the game is waiting for a server response from the tap and wait if necessary.
			wait(0.20)
			waitForLoading()
		}
	}

	/**
	 * Prints the current date as a formatted string.
	 *
	 * @return Formatted date string.
	 */
	fun printFormattedDate(): String {
		// Handle Finals dates (turns 73, 74, 75).
		val finalsLabel = when (currentDate.turnNumber) {
			73 -> "Finale Qualifier"
			74 -> "Finale Semifinal"
			75 -> "Finale Finals"
			else -> null
		}
		if (finalsLabel != null) {
			return "$finalsLabel / Turn Number ${currentDate.turnNumber}"
		}

		val formattedYear = when (currentDate.year) {
			1 -> "Junior Year"
			2 -> "Classic Year"
			3 -> "Senior Year"
            else -> "Null Year"
		}
		val formattedMonth = when (currentDate.month) {
			1 -> "Jan"
			2 -> "Feb"
			3 -> "Mar"
			4 -> "Apr"
			5 -> "May"
			6 -> "Jun"
			7 -> "Jul"
			8 -> "Aug"
			9 -> "Sep"
			10 -> "Oct"
			11 -> "Nov"
			12 -> "Dec"
            else -> "Null Month"
		}
		return "$formattedYear ${currentDate.phase} $formattedMonth / Turn Number ${currentDate.turnNumber}"
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
    // Helper functions to test behavior and results of various workflows.

	/**
	 * Handles the test to perform template matching to determine what the best scale will be for the device.
	 */
	fun startTemplateMatchingTest() {
		MessageLog.i(TAG, "\n[TEST] Now beginning basic template match test on the Home screen.")
		MessageLog.i(TAG, "[TEST] Template match confidence setting will be overridden for the test.\n")
		var results = mutableMapOf<String, MutableList<ScaleConfidenceResult>>(
			"energy" to mutableListOf(),
			"tazuna" to mutableListOf(),
			"skill_points" to mutableListOf()
		)
		results = imageUtils.startTemplateMatchingTest(results)
		MessageLog.i(TAG, "\n[TEST] Basic template match test complete.")

		// Print all scale/confidence combinations that worked for each template.
		for ((templateName, scaleConfidenceResults) in results) {
			if (scaleConfidenceResults.isNotEmpty()) {
				MessageLog.i(TAG, "[TEST] All working scale/confidence combinations for $templateName:")
				for (result in scaleConfidenceResults) {
					MessageLog.i(TAG, "[TEST]	Scale: ${result.scale}, Confidence: ${result.confidence}")
				}
			} else {
				MessageLog.w(TAG, "No working scale/confidence combinations found for $templateName")
			}
		}

		// Then print the median scales and confidences.
		val medianScales = mutableListOf<Double>()
		val medianConfidences = mutableListOf<Double>()
		for ((templateName, scaleConfidenceResults) in results) {
			if (scaleConfidenceResults.isNotEmpty()) {
				val sortedScales = scaleConfidenceResults.map { it.scale }.sorted()
				val sortedConfidences = scaleConfidenceResults.map { it.confidence }.sorted()
				val medianScale = sortedScales[sortedScales.size / 2]
				val medianConfidence = sortedConfidences[sortedConfidences.size / 2]
				medianScales.add(medianScale)
				medianConfidences.add(medianConfidence)
				MessageLog.i(TAG, "[TEST] Median scale for $templateName: $medianScale")
				MessageLog.i(TAG, "[TEST] Median confidence for $templateName: $medianConfidence")
			}
		}

		if (medianScales.isNotEmpty()) {
			MessageLog.i(TAG, "\n[TEST] The following are the recommended scales to set: $medianScales.")
			MessageLog.i(TAG, "[TEST] The following are the recommended confidences to set: $medianConfidences.")
		} else {
			MessageLog.e(TAG, "\nNo median scale/confidence can be found.")
		}
	}

	/**
	 * Handles the test to perform OCR on the current date and elapsed turn number.
	 */
	fun startDateOCRTest() {
		MessageLog.i(TAG, "\n[TEST] Now beginning the Date OCR test on the Main screen.")
		MessageLog.i(TAG, "[TEST] Note that this test is dependent on having the correct scale.")
        val finalsLocation = imageUtils.findImage("race_select_extra_locked_uma_finals", tries = 1, suppressError = true, region = imageUtils.regionBottomHalf).first
        updateDate(isFinals = (finalsLocation != null))
	}

	fun startAptitudesDetectionTest() {
		MessageLog.i(TAG, "\n[TEST] Now beginning the Aptitudes Detection test on the Main screen.")
		MessageLog.i(TAG, "[TEST] Note that this test is dependent on having the correct scale.")
		updateAptitudes()
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// Helper functions to check what screen the bot is at.

	/**
	 * Checks if the bot is at the Main screen or the screen with available options to undertake.
	 * This will also make sure that the Main screen does not contain the option to select a race.
	 *
	 * @return True if the bot is at the Main screen. Otherwise false.
	 */
	fun checkMainScreen(): Boolean {
		// Current date should be printed here to section off the tasks undertaken for this date.
		MessageLog.i(TAG, "\nChecking if the bot is sitting at the Main screen.")
        val sourceBitmap = imageUtils.getSourceBitmap()
		return if (imageUtils.findImageWithBitmap("tazuna", sourceBitmap, region = imageUtils.regionTopHalf, suppressError = true) != null &&
			imageUtils.findImageWithBitmap("race_select_mandatory", sourceBitmap, region = imageUtils.regionBottomHalf, suppressError = true) == null) {
			MessageLog.i(TAG, "Bot is at the Main screen.")

			// Perform updates here if necessary.
            val finalsLocation = imageUtils.findImageWithBitmap("race_select_extra_locked_uma_finals", sourceBitmap, suppressError = true, region = imageUtils.regionBottomHalf)
            updateDate(isFinals = (finalsLocation != null))
            if (needToUpdateAptitudes) updateAptitudes()
			true
		} else if (!enablePopupCheck && imageUtils.findImageWithBitmap("cancel", sourceBitmap, region = imageUtils.regionBottomHalf) != null &&
			imageUtils.findImageWithBitmap("race_confirm", sourceBitmap, region = imageUtils.regionBottomHalf) != null) {
			// This popup is most likely the insufficient fans popup. Force an extra race to catch up on the required fans.
			MessageLog.i(TAG, "There is a possible insufficient fans or maiden race popup.")
			racing.encounteredRacingPopup = true
			racing.skipRacing = false
			true
		} else {
			MessageLog.i(TAG, "Bot is not at the Main screen.")
			false
		}
	}

	/**
	 * Checks if the bot is at the Training Event screen with an active event with options to select on screen.
	 *
	 * @return True if the bot is at the Training Event screen. Otherwise false.
	 */
	fun checkTrainingEventScreen(): Boolean {
		MessageLog.i(TAG, "\nChecking if the bot is sitting on the Training Event screen.")
		return if (imageUtils.findImage("training_event_active", tries = 1, region = imageUtils.regionMiddle).first != null) {
			MessageLog.i(TAG, "Bot is at the Training Event screen.")
			true
		} else {
			MessageLog.i(TAG, "Bot is not at the Training Event screen.")
			false
		}
	}

	/**
	 * Checks if the bot is at the preparation screen with a mandatory race needing to be completed.
	 *
	 * @return True if the bot is at the Main screen with a mandatory race. Otherwise false.
	 */
	fun checkMandatoryRacePrepScreen(): Boolean {
		MessageLog.i(TAG, "\nChecking if the bot is sitting on the Race Preparation screen for a mandatory race.")
        val sourceBitmap = imageUtils.getSourceBitmap()
		return if (imageUtils.findImageWithBitmap("race_select_mandatory", sourceBitmap, region = imageUtils.regionBottomHalf) != null) {
			MessageLog.i(TAG, "Bot is at the preparation screen with a mandatory race ready to be completed.")
			true
		} else if (imageUtils.findImageWithBitmap("race_select_mandatory_goal", sourceBitmap, region = imageUtils.regionMiddle) != null) {
			// Most likely the user started the bot here so a delay will need to be placed to allow the start banner of the Service to disappear.
			wait(2.0)
			MessageLog.i(TAG, "Bot is at the Race Selection screen with a mandatory race needing to be selected.")
			// Walk back to the preparation screen.
			findAndTapImage("back", tries = 1, region = imageUtils.regionBottomHalf)
			wait(1.0)
			true
		} else {
			MessageLog.i(TAG, "Bot is not at the Race Preparation screen for a mandatory race.")
			false
		}
	}

	/**
	 * Checks if the bot is at the Racing screen waiting to be skipped or done manually.
	 *
	 * @return True if the bot is at the Racing screen. Otherwise, false.
	 */
	fun checkRacingScreen(): Boolean {
		MessageLog.i(TAG, "\nChecking if the bot is sitting on the Racing screen.")
		return if (imageUtils.findImage("race_change_strategy", tries = 1, region = imageUtils.regionBottomHalf).first != null) {
			MessageLog.i(TAG, "Bot is at the Racing screen waiting to be skipped or done manually.")
			true
		} else {
			MessageLog.i(TAG, "Bot is not at the Racing screen.")
			false
		}
	}

	/**
	 * Checks if the bot is at the Ending screen detailing the overall results of the run.
	 *
	 * @return True if the bot is at the Ending screen. Otherwise false.
	 */
	fun checkEndScreen(): Boolean {
		MessageLog.i(TAG, "\nChecking if the bot is sitting on the End screen.")
		return if (imageUtils.findImage("complete_career", tries = 1, region = imageUtils.regionBottomHalf).first != null) {
			true
		} else {
			MessageLog.i(TAG, "Bot is not at the End screen and can keep going.")
			false
		}
	}

	/**
	 * Checks if the bot is currently at Finals.
	 *
	 * @return True if the bot is at Finals. Otherwise false.
	 */
	fun checkFinals(): Boolean {
		MessageLog.i(TAG, "\nChecking if the bot is at the Finals.")
		val finalsLocation = imageUtils.findImage("race_select_extra_locked_uma_finals", tries = 1, suppressError = true, region = imageUtils.regionBottomHalf).first
		return if (finalsLocation != null) {
			MessageLog.i(TAG, "It is currently the Finals.")
			updateDate(isFinals = true)
			true
		} else {
			MessageLog.i(TAG, "It is not the Finals yet.")
			false
		}
	}

	/**
	 * Checks if the bot has a injury.
	 *
	 * @return True if the bot has a injury. Otherwise false.
	 */
	fun checkInjury(): Boolean {
		MessageLog.i(TAG, "\n[INJURY] Checking if there is an injury that needs healing on ${printFormattedDate()}.")
        val sourceBitmap = imageUtils.getSourceBitmap()
		val recoverInjuryLocation = imageUtils.findImageWithBitmap("recover_injury", sourceBitmap, region = imageUtils.regionBottomHalf)
		return if (recoverInjuryLocation != null && imageUtils.checkColorAtCoordinates(
				recoverInjuryLocation.x.toInt(),
				recoverInjuryLocation.y.toInt() + 15,
				intArrayOf(151, 105, 243),
				10
			)) {
			if (findAndTapImage("recover_injury", sourceBitmap, tries = 1, region = imageUtils.regionBottomHalf)) {
				wait(0.3)
				if (imageUtils.findImage("recover_injury_header", tries = 1, region = imageUtils.regionMiddle).first != null) {
					MessageLog.i(TAG, "[INJURY] Injury detected and attempted to heal.")
					true
				} else {
					false
				}
			} else {
				MessageLog.w(TAG, "Injury detected but attempt to rest failed.")
				false
			}
		} else {
			MessageLog.i(TAG, "[INJURY] No injury detected.")
			false
		}
	}

	/**
	 * Checks if the bot is at a "Now Loading..." screen or if the game is awaiting for a server response. This may cause significant delays in normal bot processes.
	 *
	 * @return True if the game is still loading or is awaiting for a server response. Otherwise, false.
	 */
	fun checkLoading(): Boolean {
		MessageLog.i(TAG, "[LOADING] Now checking if the game is still loading...")
        val sourceBitmap = imageUtils.getSourceBitmap()
		return if (imageUtils.findImageWithBitmap("connecting", sourceBitmap, region = imageUtils.regionTopHalf, suppressError = true) != null) {
			MessageLog.i(TAG, "[LOADING] Detected that the game is awaiting a response from the server from the \"Connecting\" text at the top of the screen. Waiting...")
			true
		} else if (imageUtils.findImageWithBitmap("now_loading", sourceBitmap, region = imageUtils.regionBottomHalf, suppressError = true) != null) {
			MessageLog.i(TAG, "[LOADING] Detected that the game is still loading from the \"Now Loading\" text at the bottom of the screen. Waiting...")
			true
		} else {
			false
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////
	// Helper functions to update game states.

	fun updateAptitudes() {
		MessageLog.i(TAG, "\n[STATS] Updating aptitudes for the current character.")
		if (findAndTapImage("main_status", tries = 1, region = imageUtils.regionMiddle)) {
			aptitudes = imageUtils.determineAptitudes(aptitudes)
			findAndTapImage("race_accept_trophy", tries = 1, region = imageUtils.regionBottomHalf)
			MessageLog.i(
                TAG,
                """
                [Aptitudes]
                Track: Turf=${aptitudes.track.turf}, Dirt=${aptitudes.track.dirt}
                Distance: Sprint=${aptitudes.distance.sprint}, Mile=${aptitudes.distance.mile}, Medium=${aptitudes.distance.medium}, Long=${aptitudes.distance.long}
                Style: Front=${aptitudes.style.front}, Pace=${aptitudes.style.pace}, Late=${aptitudes.style.late}, End=${aptitudes.style.end}
                """.trimIndent(),
			)
			
			// Update preferred distance based on new aptitudes.
			training.updatePreferredDistance()
            needToUpdateAptitudes = false
		}
	}

	/**
	 * Updates the current stat value mapping by reading the character's current stats from the Main screen.
	 */
	fun updateStatValueMapping() {
		MessageLog.i(TAG, "\n[STATS] Updating stat value mapping.")
		training.currentStatsMap = imageUtils.determineStatValues(training.currentStatsMap)
		// Print the updated stat value mapping here.
		training.currentStatsMap.forEach { it ->
			MessageLog.i(TAG, "[STATS] ${it.key}: ${it.value}")
		}
	}

	/**
	 * Updates the stored date in memory by keeping track of the current year, phase, month and current turn number.
	 *
	 * @param isFinals If true, checks for Finals date images instead of parsing a date string. Defaults to false.
	 */
	fun updateDate(isFinals: Boolean = false) {
		MessageLog.i(TAG, "\n[DATE] Updating the current date.")
		if (isFinals) {
			// During Finals, check for Finals-specific date images.
			// The Finals occur at turns 73, 74, and 75.
			// Date will be kept at Senior Year Late Dec, only the turn number will be updated.
            val sourceBitmap = imageUtils.getSourceBitmap()
			val turnNumber = when {
				imageUtils.findImageWithBitmap("date_final_qualifier", sourceBitmap, suppressError = true, region = imageUtils.regionTopHalf, customConfidence = 0.9) != null -> {
					MessageLog.i(TAG, "[DATE] Detected Finals Qualifier (Turn 73).")
					73
				}
				imageUtils.findImageWithBitmap("date_final_semifinal", sourceBitmap, suppressError = true, region = imageUtils.regionTopHalf, customConfidence = 0.9) != null -> {
					MessageLog.i(TAG, "[DATE] Detected Finals Semifinal (Turn 74).")
					74
				}
				imageUtils.findImageWithBitmap("date_final_finals", sourceBitmap, suppressError = true, region = imageUtils.regionTopHalf, customConfidence = 0.9) != null -> {
					MessageLog.i(TAG, "[DATE] Detected Finals Finals (Turn 75).")
					75
				}
				else -> {
					MessageLog.w(TAG, "Could not determine Finals date. Defaulting to turn 73.", isError = true)
					73
				}
			}
			// Keep the date at Senior Year Late Dec and only update the turn number.
			currentDate = Date(3, "Late", 12, turnNumber)
		} else {
			val dateString = imageUtils.determineDayString()
			currentDate = gameDateParser.parseDateString(dateString, imageUtils, this)
		}
		MessageLog.i(TAG, "[DATE] It is currently ${printFormattedDate()}.")
	}

	/**
	 * Handles the Inheritance event if detected on the screen.
	 *
	 * @return True if the Inheritance event happened and was accepted. Otherwise false.
	 */
	fun handleInheritanceEvent(): Boolean {
		return if (inheritancesDone < 2) {
			if (findAndTapImage("inheritance", tries = 1, region = imageUtils.regionBottomHalf)) {
				MessageLog.i(TAG, "\nClaimed an inheritance on ${printFormattedDate()}.")
				inheritancesDone++
                needToUpdateAptitudes = true
				true
			} else {
				false
			}
		} else {
			false
		}
	}

	/**
	 * Attempt to recover energy.
	 *
	 * @return True if the bot successfully recovered energy. Otherwise false.
	 */
    fun recoverEnergy(): Boolean {
		MessageLog.i(TAG, "\n[ENERGY] Now starting attempt to recover energy on ${printFormattedDate()}.")
        val sourceBitmap = imageUtils.getSourceBitmap()
		return when {
			findAndTapImage("recover_energy", sourceBitmap, tries = 1, region = imageUtils.regionBottomHalf) -> {
				findAndTapImage("ok")
				MessageLog.i(TAG, "[ENERGY] Successfully recovered energy.")
				racing.raceRepeatWarningCheck = false
				true
			}
			findAndTapImage("recover_energy_summer", sourceBitmap, tries = 1, region = imageUtils.regionBottomHalf) -> {
				findAndTapImage("ok")
				MessageLog.i(TAG, "[ENERGY] Successfully recovered energy for the Summer.")
				racing.raceRepeatWarningCheck = false
				true
			}
			else -> {
				MessageLog.i(TAG, "[ENERGY] Failed to recover energy. Moving on...")
				false
			}
		}
	}

	/**
	 * Attempt to recover mood to always maintain at least Above Normal mood.
	 *
	 * @return True if the bot successfully recovered mood. Otherwise false.
	 */
	fun recoverMood(): Boolean {
		MessageLog.i(TAG, "\n[MOOD] Detecting current mood on ${printFormattedDate()}.")

		// Detect what Mood the bot is at.
        val sourceBitmap = imageUtils.getSourceBitmap()
		val currentMood: String = when {
			imageUtils.findImageWithBitmap("mood_normal", sourceBitmap, region = imageUtils.regionTopHalf, suppressError = true) != null -> {
				"Normal"
			}
			imageUtils.findImageWithBitmap("mood_good", sourceBitmap, region = imageUtils.regionTopHalf, suppressError = true) != null -> {
				"Good"
			}
			imageUtils.findImageWithBitmap("mood_great", sourceBitmap, region = imageUtils.regionTopHalf, suppressError = true) != null -> {
				"Great"
			}
			else -> {
				"Bad/Awful"
			}
		}

		MessageLog.i(TAG, "[MOOD] Detected mood to be $currentMood.")

		// Only recover mood if its below Good mood and its not Summer.
		return if (training.firstTrainingCheck && currentMood == "Normal" && imageUtils.findImageWithBitmap("recover_energy_summer", sourceBitmap, region = imageUtils.regionBottomHalf, suppressError = true) == null) {
			MessageLog.i(TAG, "[MOOD] Current mood is Normal. Not recovering mood due to firstTrainingCheck flag being active. Will need to complete a training first before being allowed to recover mood.")
			false
		} else if ((currentMood == "Bad/Awful" || currentMood == "Normal") && imageUtils.findImageWithBitmap("recover_energy_summer", sourceBitmap, region = imageUtils.regionBottomHalf, suppressError = true) == null) {
			MessageLog.i(TAG, "[MOOD] Current mood is not good. Recovering mood now.")
			if (!findAndTapImage("recover_mood", sourceBitmap, tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)) {
				findAndTapImage("recover_energy_summer", sourceBitmap, tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)
			}

			// Do the date if it is unlocked.
			if (findAndTapImage("recover_mood_date", tries = 1, region = imageUtils.regionMiddle, suppressError = true)) {
				wait(1.0)
			}

			findAndTapImage("ok", region = imageUtils.regionMiddle, suppressError = true)
			racing.raceRepeatWarningCheck = false
			true
		} else {
			MessageLog.i(TAG, "[MOOD] Current mood is good enough or its the Summer event. Moving on...")
			false
		}
	}


	/**
	 * Perform misc checks to potentially fix instances where the bot is stuck.
	 *
	 * @return True if the checks passed. Otherwise false if the bot encountered a warning popup and needs to exit.
	 */
	fun performMiscChecks(): Boolean {
		MessageLog.i(TAG, "\n[MISC] Beginning check for misc cases...")

        val sourceBitmap = imageUtils.getSourceBitmap()

		if (enablePopupCheck && imageUtils.findImageWithBitmap("cancel", sourceBitmap, region = imageUtils.regionBottomHalf) != null &&
			imageUtils.findImageWithBitmap("recover_mood_date", sourceBitmap, region = imageUtils.regionMiddle) == null) {
			MessageLog.i(TAG, "\n[END] Bot may have encountered a warning popup. Exiting now...")
			notificationMessage = "Bot may have encountered a warning popup"
			return false
		} else if (findAndTapImage("next", sourceBitmap, tries = 1, region = imageUtils.regionBottomHalf)) {
			// Now confirm the completion of a Training Goal popup.
			MessageLog.i(TAG, "[MISC] Popup detected that needs to be dismissed with the \"Next\" button.")
			wait(2.0)
			findAndTapImage("next", tries = 1, region = imageUtils.regionBottomHalf)
			wait(1.0)
		} else if (imageUtils.findImageWithBitmap("crane_game", sourceBitmap, region = imageUtils.regionBottomHalf) != null) {
			// Stop when the bot has reached the Crane Game Event.
			MessageLog.i(TAG, "\n[END] Bot will stop due to the detection of the Crane Game Event. Please complete it and restart the bot.")
			notificationMessage = "Bot will stop due to the detection of the Crane Game Event. Please complete it and restart the bot."
			return false
		} else if (findAndTapImage("race_retry", sourceBitmap, tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)) {
			MessageLog.i(TAG, "[MISC] There is a race retry popup.")
			wait(5.0)
		} else if (findAndTapImage("race_accept_trophy", sourceBitmap, tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)) {
			printToLog("[MISC] There is a possible popup to accept a trophy.")
			racing.finalizeRaceResults(true, isExtra = true)
		} else if (findAndTapImage("race_end", sourceBitmap, tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)) {
			MessageLog.i(TAG, "[MISC] Ended a leftover race.")
		} else if (imageUtils.findImageWithBitmap("connection_error", sourceBitmap, region = imageUtils.regionMiddle, suppressError = true) != null) {
			MessageLog.i(TAG, "\n[END] Bot will stop due to detecting a connection error.")
			notificationMessage = "Bot will stop due to detecting a connection error."
			return false
		} else if (imageUtils.findImageWithBitmap("race_not_enough_fans", sourceBitmap, region = imageUtils.regionMiddle, suppressError = true) != null) {
			MessageLog.i(TAG, "[MISC] There was a popup about insufficient fans.")
			racing.encounteredRacingPopup = true
			findAndTapImage("cancel", region = imageUtils.regionBottomHalf)
		} else if (findAndTapImage("back", tries = 1, region = imageUtils.regionBottomHalf, suppressError = true)) {
			MessageLog.i(TAG, "[MISC] Navigating back a screen since all the other misc checks have been completed.")
			wait(1.0)
		} else if (!BotService.isRunning) {
			MessageLog.i(TAG, "\n[END] BotService is not running. Exiting now...")
			throw InterruptedException()
		} else {
			MessageLog.i(TAG, "[MISC] Did not detect any popups or the Crane Game on the screen. Moving on...")
		}

		return true
	}

	/**
	 * Bot will begin automation here.
	 *
	 * @return True if all automation goals have been met. False otherwise.
	 */
	fun start(): Boolean {
		// Print current app settings at the start of the run.
		try {
			val formattedSettingsString = SettingsHelper.getStringSetting("misc", "formattedSettingsString")
			MessageLog.i(TAG, "\n[SETTINGS] Current Bot Configuration:")
			MessageLog.i(TAG, "=====================================")
			formattedSettingsString.split("\n").forEach { line ->
				if (line.isNotEmpty()) {
					MessageLog.i(TAG, line)
				}
			}
			MessageLog.i(TAG, "=====================================\n")
		} catch (e: Exception) {
			MessageLog.w(TAG, "Failed to load formatted settings from SQLite: ${e.message}")
			MessageLog.i(TAG, "Using fallback settings display...")
			// Fallback to basic settings display if formatted string is not available.
			MessageLog.i(TAG, "Campaign: $campaign")
			MessageLog.i(TAG, "Debug Mode: $debugMode")
		}

		// Print device and version information.
		MessageLog.i(TAG, "Device Information: ${SharedData.displayWidth}x${SharedData.displayHeight}, DPI ${SharedData.displayDPI}")
		if (SharedData.displayWidth != 1080) MessageLog.w(TAG, "⚠️ Bot performance will be severely degraded since display width is not 1080p unless an appropriate scale is set for your device.")
		if (debugMode) MessageLog.w(TAG, "⚠️ Debug Mode is enabled. All bot operations will be significantly slower as a result.")
		if (SettingsHelper.getStringSetting("debug", "templateMatchCustomScale").toDouble() != 1.0) MessageLog.i(TAG, "Manual scale has been set to ${SettingsHelper.getStringSetting("debug", "templateMatchCustomScale").toDouble()}")
		MessageLog.w(TAG, "⚠️ Note that certain Android notification styles (like banners) are big enough that they cover the area that contains the Mood which will interfere with mood recovery logic in the beginning.")
		val packageInfo = myContext.packageManager.getPackageInfo(myContext.packageName, 0)
		MessageLog.i(TAG, "Bot version: ${packageInfo.versionName} (${packageInfo.versionCode})\n\n")

		val startTime: Long = System.currentTimeMillis()

		// Start debug tests here if enabled. Otherwise, proceed with regular bot operations.
		if (SettingsHelper.getBooleanSetting("debug", "debugMode_startTemplateMatchingTest")) {
			startTemplateMatchingTest()
		} else if (SettingsHelper.getBooleanSetting("debug", "debugMode_startSingleTrainingOCRTest")) {
			training.startSingleTrainingOCRTest()
		} else if (SettingsHelper.getBooleanSetting("debug", "debugMode_startComprehensiveTrainingOCRTest")) {
			training.startComprehensiveTrainingOCRTest()
		} else if (SettingsHelper.getBooleanSetting("debug", "debugMode_startDateOCRTest")) {
			startDateOCRTest()
		} else if (SettingsHelper.getBooleanSetting("debug", "debugMode_startRaceListDetectionTest")) {
			racing.startRaceListDetectionTest()
		} else if (SettingsHelper.getBooleanSetting("debug", "debugMode_startAptitudesDetectionTest")) {
			startAptitudesDetectionTest()
		} else {
			// Update the stat targets by distances and the preferred distance for training.
			training.setStatTargetsByDistances()
			training.updatePreferredDistance()

			wait(5.0)

			if (campaign == "Ao Haru") {
				val aoHaruCampaign = AoHaru(this)
				aoHaruCampaign.start()
			} else {
				val uraFinaleCampaign = Campaign(this)
				uraFinaleCampaign.start()
			}
		}

		val endTime: Long = System.currentTimeMillis()
		Log.d(TAG, "Total Runtime: ${endTime - startTime}ms")

		return true
	}
}