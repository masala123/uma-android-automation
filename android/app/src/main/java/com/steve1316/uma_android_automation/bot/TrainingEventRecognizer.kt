package com.steve1316.uma_android_automation.bot

import android.util.Log
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.uma_android_automation.utils.SettingsHelper
import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl
import org.json.JSONObject

/**
 * Recognizes training events by performing OCR on event titles and matching them against
 * known character and support card event data using string similarity algorithms.
 */
class TrainingEventRecognizer(private val game: Game, private val imageUtils: CustomImageUtils) {
	private val tag: String = "[${MainActivity.loggerTag}]TrainingEventRecognizer"
	
	private var result = ""
	private var confidence = 0.0
	private var category = ""
	private var eventTitle = ""
	private var supportCardTitle = ""
	private var eventOptionRewards: ArrayList<String> = arrayListOf()
	
	private var character = ""
	
	// Get character event data from settings.
	private val characterEventData: JSONObject? = try {
		val characterDataString = SettingsHelper.getStringSetting("trainingEvent", "characterEventData")
		if (characterDataString.isNotEmpty()) {
			val jsonObject = JSONObject(characterDataString)
			game.printToLog("[TRAINING_EVENT_RECOGNIZER] Character event data length: ${jsonObject.length()}.", tag = tag)
			jsonObject
		} else {
			null
		}
	} catch (_: Exception) {
		null
	}
	
	// Get support event data from settings.
	private val supportEventData: JSONObject? = try {
		val supportDataString = SettingsHelper.getStringSetting("trainingEvent", "supportEventData")
		if (supportDataString.isNotEmpty()) {
			val jsonObject = JSONObject(supportDataString)
			game.printToLog("[TRAINING_EVENT_RECOGNIZER] Support event data length: ${jsonObject.length()}.", tag = tag)
			jsonObject
		} else {
			null
		}
	} catch (_: Exception) {
		null
	}
	
	private val supportCards: List<String> = try {
		if (supportEventData != null) {
			supportEventData.keys().asSequence().toList()
		} else {
			emptyList()
		}
	} catch (_: Exception) {
		emptyList()
	}
	private val hideComparisonResults: Boolean = SettingsHelper.getBooleanSetting("debug", "enableHideOCRComparisonResults")
	private val selectAllCharacters: Boolean = SettingsHelper.getBooleanSetting("trainingEvent", "selectAllCharacters")
	private val selectAllSupportCards: Boolean = SettingsHelper.getBooleanSetting("trainingEvent", "selectAllSupportCards")
	private val minimumConfidence = SettingsHelper.getIntSetting("ocr", "ocrConfidence").toDouble() / 100.0
	private val threshold = SettingsHelper.getIntSetting("ocr", "ocrThreshold").toDouble()
	private val enableAutomaticRetry = SettingsHelper.getBooleanSetting("ocr", "enableAutomaticOCRRetry")
	
	/**
	 * Attempt to find the most similar string from data compared to the string returned by OCR.
	 */
	private fun findMostSimilarString() {
		if (!hideComparisonResults) {
			game.printToLog("\n[TRAINING_EVENT_RECOGNIZER] Now starting process to find most similar string to: $result\n", tag = tag)
		} else {
			game.printToLog("\n[TRAINING_EVENT_RECOGNIZER] Now starting process to find most similar string to: $result", tag = tag)
		}
		
		// Remove any detected whitespaces.
		result = result.replace(" ", "")
		
		// Use the Jaro Winkler algorithm to compare similarities the OCR detected string and the rest of the strings inside the data classes.
		val service = StringSimilarityServiceImpl(JaroWinklerStrategy())
		
		// Attempt to find the most similar string inside the character event data.
		if (characterEventData != null) {
			if (selectAllCharacters) {
				// Check all characters in the event data.
				characterEventData.keys().forEach { characterKey ->
					val characterEvents = characterEventData.getJSONObject(characterKey)
					characterEvents.keys().forEach { eventName ->
						val eventOptionsArray = characterEvents.getJSONArray(eventName)
						val eventOptions = ArrayList<String>()
						for (i in 0 until eventOptionsArray.length()) {
							eventOptions.add(eventOptionsArray.getString(i))
						}
						
						val score = service.score(result, eventName)
						if (!hideComparisonResults) {
							game.printToLog("[CHARA] $characterKey \"${result}\" vs. \"${eventName}\" confidence: $score", tag = tag)
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
			} else {
				// Check only the specific character if it exists in the event data.
				if (character.isNotEmpty() && characterEventData.has(character)) {
					val characterEvents = characterEventData.getJSONObject(character)
					characterEvents.keys().forEach { eventName ->
						val eventOptionsArray = characterEvents.getJSONArray(eventName)
						val eventOptions = ArrayList<String>()
						for (i in 0 until eventOptionsArray.length()) {
							eventOptions.add(eventOptionsArray.getString(i))
						}
						
						val score = service.score(result, eventName)
						if (!hideComparisonResults) {
							game.printToLog("[CHARA] $character \"${result}\" vs. \"${eventName}\" confidence: $score", tag = tag)
						}
						
						if (score >= confidence) {
							confidence = score
							eventTitle = eventName
							eventOptionRewards = eventOptions
							category = "character"
						}
					}
				}
			}
		}
		
		// Finally, do the same with the user-selected Support Cards.
		if (supportEventData != null) {
			if (!selectAllSupportCards) {
				supportCards.forEach { supportCardName ->
					if (supportEventData.has(supportCardName)) {
						val supportEvents = supportEventData.getJSONObject(supportCardName)
						supportEvents.keys().forEach { eventName ->
							val eventOptionsArray = supportEvents.getJSONArray(eventName)
							val eventOptions = ArrayList<String>()
							for (i in 0 until eventOptionsArray.length()) {
								eventOptions.add(eventOptionsArray.getString(i))
							}
							
							val score = service.score(result, eventName)
							if (!hideComparisonResults) {
								game.printToLog("[SUPPORT] $supportCardName \"${result}\" vs. \"${eventName}\" confidence: $score", tag = tag)
							}
							
							if (score >= confidence) {
								confidence = score
								eventTitle = eventName
								supportCardTitle = supportCardName
								eventOptionRewards = eventOptions
								category = "support"
							}
						}
					}
				}
			} else {
				// Check all support cards in the event data.
				supportEventData.keys().forEach { supportName ->
					val supportEvents = supportEventData.getJSONObject(supportName)
					supportEvents.keys().forEach { eventName ->
						val eventOptionsArray = supportEvents.getJSONArray(eventName)
						val eventOptions = ArrayList<String>()
						for (i in 0 until eventOptionsArray.length()) {
							eventOptions.add(eventOptionsArray.getString(i))
						}
						
						val score = service.score(result, eventName)
						if (!hideComparisonResults) {
							game.printToLog("[SUPPORT] $supportName \"${result}\" vs. \"${eventName}\" confidence: $score", tag = tag)
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
		}
		
		if (!hideComparisonResults) {
			game.printToLog("\n[TRAINING_EVENT_RECOGNIZER] Finished process to find similar string.", tag = tag)
		} else {
			game.printToLog("[TRAINING_EVENT_RECOGNIZER] Finished process to find similar string.", tag = tag)
		}
		game.printToLog("[TRAINING_EVENT_RECOGNIZER] Event data fetched for \"${eventTitle}\".")
	}

	/**
	 * Starts the training event recognition process by performing OCR on the event title
	 * and matching it against known event data.
	 *
	 * @return A triple containing the event option rewards, confidence score, and event title.
	 */
	fun start(): Triple<ArrayList<String>, Double, String> {
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
						if (!hideComparisonResults) {
							game.printToLog("\n[RESULT] Character $character Event Name = $eventTitle with confidence = $confidence", tag = tag)
						}
					}
					"character-shared" -> {
						if (!hideComparisonResults) {
							game.printToLog("\n[RESULT] Character Shared Event Name = $eventTitle with confidence = $confidence", tag = tag)
						}
					}
					"support" -> {
						if (!hideComparisonResults) {
							game.printToLog("\n[RESULT] Support $supportCardTitle Event Name = $eventTitle with confidence = $confidence", tag = tag)
						}
					}
				}
				
				if (enableAutomaticRetry && !hideComparisonResults) {
					game.printToLog("\n[RESULT] Threshold incremented by $increment", tag = tag)
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
		Log.d(tag, "Total Runtime for recognizing training event: ${endTime - startTime}ms")
		
		return Triple(eventOptionRewards, confidence, eventTitle)
	}
}
