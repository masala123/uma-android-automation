package com.steve1316.uma_android_automation.bot

import android.util.Log
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.uma_android_automation.utils.SQLiteSettingsManager
import com.steve1316.automation_library.utils.MessageLog
import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl
import org.json.JSONObject

/**
 * Recognizes training events by performing OCR on event titles and matching them against
 * known character and support card event data using string similarity algorithms.
 */
class TrainingEventRecognizer(private val game: Game, private val imageUtils: CustomImageUtils) {
	private val TAG: String = "[${MainActivity.loggerTag}]TrainingEventRecognizer"
	
	private var result = ""
	private var confidence = 0.0
	private var category = ""
	private var eventTitle = ""
	private var supportCardTitle = ""
	private var eventOptionRewards: ArrayList<String> = arrayListOf()
	
	private var character = ""
	
	// Define event matching patterns to filter false positives during detection.
	val eventPatterns = mapOf(
		"New Year's Resolutions" to listOf("New Year's Resolutions", "Resolutions"),
		"New Year's Shrine Visit" to listOf("New Year's Shrine Visit", "Shrine Visit"),
		"Victory!" to listOf("Victory!"),
		"Solid Showing" to listOf("Solid Showing"),
		"Defeat" to listOf("Defeat"),
		"Get Well Soon!" to listOf("Get Well Soon"),
		"Don't Overdo It!" to listOf("Don't Overdo It"),
		"Extra Training" to listOf("Extra Training"),
		"Acupuncture (Just an Acupuncturist, No Worries! ☆)" to listOf("Acupuncture", "Just an Acupuncturist"),
		"Etsuko's Exhaustive Coverage" to listOf("Etsuko", "Exhaustive Coverage")
	)
	
	// The full character event data should be stored in SQLite and will be loaded here.
	private val characterEventData: JSONObject? = try {
		val settingsManager = SQLiteSettingsManager(game.myContext)
		if (!settingsManager.isAvailable()) {
			settingsManager.initialize()
		}
		val characterDataString = settingsManager.loadSetting("trainingEvent", "characterEventData")
		if (characterDataString != null && characterDataString.isNotEmpty()) {
			val jsonObject = JSONObject(characterDataString)
			if (game.debugMode) MessageLog.d(TAG, "Character event data length: ${jsonObject.length()}.")
			jsonObject
		} else {
			null
		}
	} catch (e: Exception) {
		if (game.debugMode) MessageLog.d(TAG, "[DEBUG] Failed to load character event data from SQLite: ${e.message}")
		null
	}
	
	// The full support event data should be stored in SQLite and will be loaded here.
	private val supportEventData: JSONObject? = try {
		val settingsManager = SQLiteSettingsManager(game.myContext)
		if (!settingsManager.isAvailable()) {
			settingsManager.initialize()
		}
		val supportDataString = settingsManager.loadSetting("trainingEvent", "supportEventData")
		if (supportDataString != null && supportDataString.isNotEmpty()) {
			val jsonObject = JSONObject(supportDataString)
			if (game.debugMode) MessageLog.d(TAG, "Support event data length: ${jsonObject.length()}.")
			jsonObject
		} else {
			null
		}
	} catch (e: Exception) {
		if (game.debugMode) MessageLog.d(TAG, "[DEBUG] Failed to load support event data from SQLite: ${e.message}")
		null
	}
	
	private val hideComparisonResults: Boolean = SettingsHelper.getBooleanSetting("debug", "enableHideOCRComparisonResults")
	private val minimumConfidence = SettingsHelper.getIntSetting("ocr", "ocrConfidence").toDouble() / 100.0
	private val threshold = SettingsHelper.getIntSetting("ocr", "ocrThreshold").toDouble()
	private val enableAutomaticRetry = SettingsHelper.getBooleanSetting("ocr", "enableAutomaticOCRRetry")

    /**
    * Data class to hold a quadruple of values.
    */
    data class Quadruple<out A, out B, out C, out D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Attempt to find the most similar string from data compared to the string returned by OCR.
	 */
	private fun findMostSimilarString() {
		MessageLog.i(TAG, "[TRAINING_EVENT_RECOGNIZER] Now starting process to find most similar string to: $result")
		
		// Check if this matches any special event patterns first to filter false positives.
		var matchedSpecialEvent: String? = null
		for ((eventName, patterns) in eventPatterns) {
			if (patterns.any { pattern -> result.contains(pattern) }) {
				matchedSpecialEvent = eventName
				break
			}
		}
		val isSpecialEvent = matchedSpecialEvent != null
		if (isSpecialEvent) {
			MessageLog.i(TAG, "[TRAINING_EVENT_RECOGNIZER] Detected special event pattern: $matchedSpecialEvent. Will restrict search to this event.")
			eventTitle = matchedSpecialEvent
		}
		
		// Remove any detected whitespaces.
		result = result.replace(" ", "")
		
		// Use the Jaro Winkler algorithm to compare similarities the OCR detected string and the rest of the strings inside the data classes.
		val service = StringSimilarityServiceImpl(JaroWinklerStrategy())
		
		// Attempt to find the most similar string inside the character event data.
		if (characterEventData != null) {
			characterEventData.keys().forEach { characterKey ->
				val characterEvents = characterEventData.getJSONObject(characterKey)
				characterEvents.keys().forEach { eventName ->
					// Skip if this is a special event and the event name doesn't match our detected pattern.
					if (isSpecialEvent && eventName != matchedSpecialEvent) {
						return@forEach
					}
					
					val eventOptionsArray = characterEvents.getJSONArray(eventName)
					val eventOptions = ArrayList<String>()
					for (i in 0 until eventOptionsArray.length()) {
						eventOptions.add(eventOptionsArray.getString(i))
					}
					
					val score = service.score(result, eventName)
					if (!hideComparisonResults) {
						MessageLog.i(TAG, "[CHARA] $characterKey \"${result}\" vs. \"${eventName}\" confidence: ${game.decimalFormat.format(score)}")
					}
					
					if (score >= confidence) {
						confidence = score
						eventTitle = eventName
						eventOptionRewards = eventOptions
						category = "character"
						character = characterKey
					}
				}
			}
		}
		
		// Finally, do the same with all Support Cards.
		if (supportEventData != null) {
			supportEventData.keys().forEach { supportName ->
				val supportEvents = supportEventData.getJSONObject(supportName)
				supportEvents.keys().forEach { eventName ->
					// Skip if this is a special event and the event name doesn't match our detected pattern.
					if (isSpecialEvent && eventName != matchedSpecialEvent) {
						return@forEach
					}
					
					val eventOptionsArray = supportEvents.getJSONArray(eventName)
					val eventOptions = ArrayList<String>()
					for (i in 0 until eventOptionsArray.length()) {
						eventOptions.add(eventOptionsArray.getString(i))
					}
					
					val score = service.score(result, eventName)
					if (!hideComparisonResults) {
						MessageLog.i(TAG, "[SUPPORT] $supportName \"${result}\" vs. \"${eventName}\" confidence: $score")
					}
					
					if (score >= confidence) {
						confidence = score
						eventTitle = eventName
						supportCardTitle = supportName
						eventOptionRewards = eventOptions
						category = "support"
					}
				}
			}
		}

		MessageLog.i(TAG, "${if (!hideComparisonResults) "\n" else ""}[TRAINING_EVENT_RECOGNIZER] Finished process to find similar string.")
		MessageLog.i(TAG, "[TRAINING_EVENT_RECOGNIZER] Event data fetched for \"${eventTitle}\".")
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Starts the training event recognition process by performing OCR on the event title
	 * and matching it against known event data.
	 *
	 * @return A quadruple containing the event option rewards, confidence score, event title, and character/support name.
	 */
	fun start(): Quadruple<ArrayList<String>, Double, String, String> {
		MessageLog.i(TAG, "\n********************")

		// Reset to default values.
		result = ""
		confidence = 0.0
		category = ""
		eventTitle = ""
		supportCardTitle = ""
		eventOptionRewards.clear()
		
		var increment = 0.0
		
		val startTime: Long = System.currentTimeMillis()
		while (true) {
			// Perform Tesseract OCR detection.
			if ((255.0 - threshold - increment) > 0.0) {
				result = imageUtils.findText(increment)
			} else {
				break
			}
			
			if (result.isNotEmpty() && result != "empty!") {
				// Now attempt to find the most similar string compared to the one from OCR.
				findMostSimilarString()
				
				when (category) {
					"character" -> {
						MessageLog.i(TAG, "\n[RESULT] Character $character Event Name = $eventTitle with confidence = $confidence")
					}
					"support" -> {
						MessageLog.i(TAG, "\n[RESULT] Support $supportCardTitle Event Name = $eventTitle with confidence = $confidence")
					}
				}
				
				if (enableAutomaticRetry && !hideComparisonResults) {
					MessageLog.i(TAG, "\n[RESULT] Threshold incremented by $increment")
				}
				
				if (confidence < minimumConfidence && enableAutomaticRetry) {
					increment += 5.0
				} else {
					break
				}
			} else {
				increment += 5.0
			}
		}
		
		val endTime: Long = System.currentTimeMillis()
		Log.d(TAG, "Total Runtime for recognizing training event: ${endTime - startTime}ms")
		MessageLog.i(TAG, "********************")
		
		val characterOrSupportName = when (category) {
			"character" -> character
			"support" -> supportCardTitle
			else -> ""
		}
		
		return Quadruple(eventOptionRewards, confidence, eventTitle, characterOrSupportName)
	}
}
