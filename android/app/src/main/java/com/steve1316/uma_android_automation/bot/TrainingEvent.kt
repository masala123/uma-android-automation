package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.automation_library.utils.MessageLog
import org.opencv.core.Point
import org.json.JSONObject

class TrainingEvent(private val game: Game) {
    private val TAG: String = "[${MainActivity.loggerTag}]TrainingEvent"

    private val trainingEventRecognizer: TrainingEventRecognizer = TrainingEventRecognizer(game, game.imageUtils)

    private val enablePrioritizeEnergyOptions: Boolean = SettingsHelper.getBooleanSetting("trainingEvent", "enablePrioritizeEnergyOptions")
    
    private val positiveStatuses = listOf("Charming", "Fast Learner", "Practice Practice")
    private val negativeStatuses = listOf("Practice Poor", "Migraine", "Night Owl", "Slow Metabolism", "Slacker")

    // Load special event overrides from settings.
    private val specialEventOverrides: Map<String, EventOverride> = try {
        val overridesString = SettingsHelper.getStringSetting("trainingEvent", "specialEventOverrides")
        if (overridesString.isNotEmpty()) {
            val jsonObject = JSONObject(overridesString)
            val overridesMap = mutableMapOf<String, EventOverride>()
            jsonObject.keys().forEach { eventName ->
                val eventData = jsonObject.getJSONObject(eventName)
                overridesMap[eventName] = EventOverride(
                    selectedOption = eventData.getString("selectedOption"),
                    requiresConfirmation = eventData.getBoolean("requiresConfirmation")
                )
            }
            overridesMap
        } else {
            emptyMap()
        }
    } catch (e: Exception) {
        MessageLog.w(TAG, "Could not parse special event overrides: ${e.message}")
        emptyMap()
    }
    
    // Load character event overrides from settings.
    private val characterEventOverrides: Map<String, Int> = try {
        val overridesString = SettingsHelper.getStringSetting("trainingEvent", "characterEventOverrides")
        if (overridesString.isNotEmpty()) {
            val jsonObject = JSONObject(overridesString)
            val overridesMap = mutableMapOf<String, Int>()
            jsonObject.keys().forEach { eventKey ->
                overridesMap[eventKey] = jsonObject.getInt(eventKey)
            }
            overridesMap
        } else {
            emptyMap()
        }
    } catch (e: Exception) {
        game.printToLog("[WARNING] Could not parse character event overrides: ${e.message}", tag = tag)
        emptyMap()
    }
    
    // Load support event overrides from settings.
    private val supportEventOverrides: Map<String, Int> = try {
        val overridesString = SettingsHelper.getStringSetting("trainingEvent", "supportEventOverrides")
        if (overridesString.isNotEmpty()) {
            val jsonObject = JSONObject(overridesString)
            val overridesMap = mutableMapOf<String, Int>()
            jsonObject.keys().forEach { eventKey ->
                overridesMap[eventKey] = jsonObject.getInt(eventKey)
            }
            overridesMap
        } else {
            emptyMap()
        }
    } catch (e: Exception) {
        game.printToLog("[WARNING] Could not parse support event overrides: ${e.message}", tag = tag)
        emptyMap()
    }
    
    data class EventOverride(val selectedOption: String, val requiresConfirmation: Boolean)

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // Functions to handle Training Events with the help of the TrainingEventRecognizer class.

    /**
     * Check if the given event title matches any special event overrides.
     *
     * @param eventTitle The detected event title from OCR
     * @return Pair of (optionIndex, requiresConfirmation) if match found, null otherwise
     */
    private fun checkSpecialEventOverride(eventTitle: String): Pair<Int, Boolean>? {
        for ((eventName, patterns) in trainingEventRecognizer.eventPatterns) {
            val override = specialEventOverrides[eventName]
            if (override != null) {
                // Check if any pattern matches the event title.
                val matches = patterns.any { pattern -> eventTitle.contains(pattern) }
                if (matches) {
                    MessageLog.i(TAG, "[TRAINING_EVENT] Detected special event: $eventName")

                    // Parse the option number from the setting (e.g., "Option 5: Energy +10" -> 5)
                    val optionMatch = Regex("Option (\\d+)").find(override.selectedOption)
                    val optionIndex = if (optionMatch != null) {
                        val optionNumber = optionMatch.groupValues[1].toInt()
                        MessageLog.i(TAG, "[TRAINING_EVENT] Using setting: ${override.selectedOption} (Option $optionNumber)")
                        optionNumber - 1
                    } else {
                        MessageLog.w(TAG, "Could not parse option number from setting: ${override.selectedOption}. Using option 1 by default.")
                        0
                    }

                    return Pair(optionIndex, override.requiresConfirmation)
                }
            }
        }

        return null
    }

    /**
     * Check if the given character event matches any character event overrides.
     *
     * @param characterName The detected character name
     * @param eventTitle The detected event title from OCR
     * @return The option index (0-based) if override found, null otherwise
     */
    private fun checkCharacterEventOverride(characterName: String, eventTitle: String): Int? {
        if (characterName.isEmpty()) return null
        
        val eventKey = "$characterName|$eventTitle"
        val override = characterEventOverrides[eventKey]
        if (override != null) {
            game.printToLog("[TRAINING_EVENT] Detected character event override: $eventKey -> Option ${override + 1}", tag = tag)
            return override
        }
        
        return null
    }

    /**
     * Check if the given support event matches any support event overrides.
     *
     * @param supportName The detected support name
     * @param eventTitle The detected event title from OCR
     * @return The option index (0-based) if override found, null otherwise
     */
    private fun checkSupportEventOverride(supportName: String, eventTitle: String): Int? {
        if (supportName.isEmpty()) return null
        
        val eventKey = "$supportName|$eventTitle"
        val override = supportEventOverrides[eventKey]
        if (override != null) {
            game.printToLog("[TRAINING_EVENT] Detected support event override: $eventKey -> Option ${override + 1}", tag = tag)
            return override
        }
        
        return null
    }

    /**
     * Start text detection to determine what Training Event it is and the event rewards for each option.
     * It will then select the best option according to the user's preferences. By default, it will choose the first option.
     */
    fun handleTrainingEvent() {
        MessageLog.i(TAG, "\n********************")
        MessageLog.i(TAG, "[TRAINING_EVENT] Starting Training Event process on ${game.printFormattedDate()}.")

        // Double check if the bot is at the Main screen or not.
        if (game.checkMainScreen()) {
            MessageLog.i(TAG, "[TRAINING_EVENT] Bot is at the Main Screen. Ending the Training Event process.")
            MessageLog.i(TAG, "********************")
            return
        }

        val (eventRewards, confidence, eventTitle, characterOrSupportName) = trainingEventRecognizer.start()

        val regex = Regex("[a-zA-Z]+")
        var optionSelected = 0

        if (eventRewards.isNotEmpty() && eventRewards[0] != "") {
            // Check for special event overrides first.
            val specialEventResult = checkSpecialEventOverride(eventTitle)
            if (specialEventResult != null) {
                val (selectedOptionIndex, requiresConfirmation) = specialEventResult
                optionSelected = selectedOptionIndex
                
                // Ensure the selected option is within bounds.
                if (optionSelected >= eventRewards.size) {
                    MessageLog.w(TAG, "Selected special event option $optionSelected is out of bounds. Using last option.")
                    optionSelected = eventRewards.size - 1
                }
                
                MessageLog.i(TAG, "[TRAINING_EVENT] Special event override applied: option ${optionSelected + 1}: \"${eventRewards[optionSelected]}\"")
            } else {
                // Check for character or support event overrides.
                val characterOverride = checkCharacterEventOverride(characterOrSupportName, eventTitle)
                val supportOverride = checkSupportEventOverride(characterOrSupportName, eventTitle)
                
                if (characterOverride != null) {
                    optionSelected = characterOverride
                    
                    // Ensure the selected option is within bounds.
                    if (optionSelected >= eventRewards.size) {
                        game.printToLog("[WARNING] Selected character event option $optionSelected is out of bounds. Using last option.", tag = tag)
                        optionSelected = eventRewards.size - 1
                    }
                    
                    game.printToLog("[TRAINING_EVENT] Character event override applied: option ${optionSelected + 1}: \"${eventRewards[optionSelected]}\"", tag = tag)
                } else if (supportOverride != null) {
                    optionSelected = supportOverride
                    
                    // Ensure the selected option is within bounds.
                    if (optionSelected >= eventRewards.size) {
                        game.printToLog("[WARNING] Selected support event option $optionSelected is out of bounds. Using last option.", tag = tag)
                        optionSelected = eventRewards.size - 1
                    }
                    
                    game.printToLog("[TRAINING_EVENT] Support event override applied: option ${optionSelected + 1}: \"${eventRewards[optionSelected]}\"", tag = tag)
                } else {
                    // Initialize the List for normal event processing.
                    val selectionWeight = List(eventRewards.size) { 0 }.toMutableList()

                    // Sum up the stat gains with additional weight applied to stats that are prioritized.
                    eventRewards.forEach { reward ->
                        val formattedReward: List<String> = reward.split("\n")

                        formattedReward.forEach { line ->
                            val formattedLine: String = regex
                                .replace(line, "")
                                .replace("(", "")
                                .replace(")", "")
                                .trim()
                                .lowercase()

                            game.printToLog("[TRAINING_EVENT] Original line is \"$line\".", tag = tag)
                            game.printToLog("[TRAINING_EVENT] Formatted line is \"$formattedLine\".", tag = tag)

                            var priorityStatCheck = false
                            if (line.lowercase().contains("energy")) {
                                val finalEnergyValue = try {
                                    val energyValue = if (formattedLine.contains("/")) {
                                        val splits = formattedLine.split("/")
                                        var sum = 0
                                        for (split in splits) {
                                            sum += try {
                                                split.trim().toInt()
                                            } catch (_: NumberFormatException) {
                                                game.printToLog("[WARNING] Could not convert $formattedLine to a number for energy with a forward slash.", tag = tag)
                                                20
                                            }
                                        }
                                        sum
                                    } else {
                                        formattedLine.toInt()
                                    }

                                    if (enablePrioritizeEnergyOptions) {
                                        energyValue * 100
                                    } else {
                                        energyValue * 3
                                    }
                                } catch (_: NumberFormatException) {
                                    game.printToLog("[WARNING] Could not convert $formattedLine to a number for energy.", tag = tag)
                                    20
                                }
                                game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of $finalEnergyValue for energy.", tag = tag)
                                selectionWeight[optionSelected] += finalEnergyValue
                            } else if (line.lowercase().contains("mood")) {
                                val moodWeight = if (formattedLine.contains("-")) -50 else 50
                                game.printToLog("[TRAINING-EVENT] Adding weight for option#${optionSelected + 1} of $moodWeight for ${if (moodWeight > 0) "positive" else "negative"} mood gain.", tag = tag)
                                selectionWeight[optionSelected] += moodWeight
                            } else if (line.lowercase().contains("bond")) {
                                game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of 20 for bond.", tag = tag)
                                selectionWeight[optionSelected] += 20
                            } else if (line.lowercase().contains("event chain ended")) {
                                game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of -50 for event chain ending.", tag = tag)
                                selectionWeight[optionSelected] += -50
                            } else if (line.lowercase().contains("(random)")) {
                                game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of -10 for random reward.", tag = tag)
                                selectionWeight[optionSelected] += -10
                            } else if (line.lowercase().contains("randomly")) {
                                game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of 50 for random options.", tag = tag)
                                selectionWeight[optionSelected] += 50
                            } else if (line.lowercase().contains("hint")) {
                                game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of 25 for skill hint(s).", tag = tag)
                                selectionWeight[optionSelected] += 25
                            } else if (positiveStatuses.any { status -> line.contains(status) }) {
                                game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of 25 for positive status effect.", tag = tag)
                                selectionWeight[optionSelected] += 25
                            } else if (negativeStatuses.any { status -> line.contains(status) }) {
                                game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of -25 for negative status effect.", tag = tag)
                                selectionWeight[optionSelected] += -25
                            } else if (line.lowercase().contains("skill")) {
                                val finalSkillPoints = if (formattedLine.contains("/")) {
                                    val splits = formattedLine.split("/")
                                    var sum = 0
                                    for (split in splits) {
                                        sum += try {
                                            split.trim().toInt()
                                        } catch (_: NumberFormatException) {
                                            game.printToLog("[WARNING] Could not convert $formattedLine to a number for skill points with a forward slash.", tag = tag)
                                            10
                                        }
                                    }
                                    sum
                                } else {
                                    formattedLine.toInt()
                                }
                                game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of $finalSkillPoints for skill points.", tag = tag)
                                selectionWeight[optionSelected] += finalSkillPoints
                            } else {
                                // Apply inflated weights to the prioritized stats based on their order.
                                game.training.statPrioritization.forEachIndexed { index, stat ->
                                    if (line.contains(stat)) {
                                        // Calculate weight bonus based on position (higher priority = higher bonus).
                                        val priorityBonus = when (index) {
                                            0 -> 50
                                            1 -> 40
                                            2 -> 30
                                            3 -> 20
                                            else -> 10
                                        }

                                        val finalStatValue = try {
                                            priorityStatCheck = true
                                            if (formattedLine.contains("/")) {
                                                val splits = formattedLine.split("/")
                                                var sum = 0
                                                for (split in splits) {
                                                    sum += try {
                                                        split.trim().toInt()
                                                    } catch (_: NumberFormatException) {
                                                        game.printToLog("[WARNING] Could not convert $formattedLine to a number for a priority stat with a forward slash.", tag = tag)
                                                        10
                                                    }
                                                }
                                                sum + priorityBonus
                                            } else {
                                                formattedLine.toInt() + priorityBonus
                                            }
                                        } catch (_: NumberFormatException) {
                                            game.printToLog("[WARNING] Could not convert $formattedLine to a number for a priority stat.", tag = tag)
                                            priorityStatCheck = false
                                            10
                                        }
                                        game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of $finalStatValue for prioritized stat.", tag = tag)
                                        selectionWeight[optionSelected] += finalStatValue
                                    }
                                }

                                // Apply normal weights to the rest of the stats.
                                if (!priorityStatCheck) {
                                    val finalStatValue = try {
                                        if (formattedLine.contains("/")) {
                                            val splits = formattedLine.split("/")
                                            var sum = 0
                                            for (split in splits) {
                                                sum += try {
                                                    split.trim().toInt()
                                                } catch (_: NumberFormatException) {
                                                    game.printToLog("[WARNING] Could not convert $formattedLine to a number for non-prioritized stat with a forward slash.", tag = tag)
                                                    10
                                                }
                                            }
                                            sum
                                        } else {
                                            formattedLine.toInt()
                                        }
                                    } catch (_: NumberFormatException) {
                                        game.printToLog("[WARNING] Could not convert $formattedLine to a number for non-prioritized stat.", tag = tag)
                                        10
                                    }
                                    game.printToLog("[TRAINING_EVENT] Adding weight for option #${optionSelected + 1} of $finalStatValue for non-prioritized stat.", tag = tag)
                                    selectionWeight[optionSelected] += finalStatValue
                                }
                            }

                            game.printToLog("[TRAINING_EVENT] Final weight for option #${optionSelected + 1} is: ${selectionWeight[optionSelected]}.", tag = tag)
                        }

                        optionSelected++
                    }

                    // Select the best option that aligns with the stat prioritization made in the Training options.
                    val max: Int? = selectionWeight.maxOrNull()
                    optionSelected = if (max == null) {
                        0
                    } else {
                        selectionWeight.indexOf(max)
                    }

                    // Print the selection weights.
                    game.printToLog("[TRAINING_EVENT] Selection weights for each option:", tag = tag)
                    selectionWeight.forEachIndexed { index, weight ->
                        game.printToLog("Option ${index + 1}: $weight", tag = tag)
                    }
                }
            }

            // Format the string to display each option's rewards.
            var eventRewardsString = ""
            var optionNumber = 1
            eventRewards.forEach { reward ->
                eventRewardsString += "Option $optionNumber: \"$reward\"\n"
                optionNumber += 1
            }

            val minimumConfidence = SettingsHelper.getStringSetting("debug", "templateMatchConfidence").toDouble()
            val resultString = if (confidence >= minimumConfidence) {
                "[TRAINING_EVENT] For this Training Event consisting of:\n$eventRewardsString\nThe bot will select Option ${optionSelected + 1}: \"${eventRewards[optionSelected]}\"."
            } else {
                "[TRAINING_EVENT] Since the confidence was less than the set minimum, first option will be selected."
            }

            MessageLog.i(TAG, resultString)
        } else {
            MessageLog.w(TAG, "First option will be selected since OCR failed to match the event title.")
            optionSelected = 0
        }

        val trainingOptionLocations: ArrayList<Point> = game.imageUtils.findAll("training_event_active")
        val selectedLocation: Point? = if (trainingOptionLocations.isNotEmpty()) {
            // Account for the situation where it could go out of bounds if the detected event options is incorrect and gives too many results.
            try {
                trainingOptionLocations[optionSelected]
            } catch (_: IndexOutOfBoundsException) {
                // Default to the first option.
                trainingOptionLocations[0]
            }
        } else {
            game.imageUtils.findImage("training_event_active", tries = 5, region = game.imageUtils.regionMiddle).first
        }

        if (selectedLocation != null) {
            game.tap(selectedLocation.x + game.imageUtils.relWidth(100), selectedLocation.y, "training_event_active")
            
            // Check if this special event requires confirmation.
            val specialEventResult = checkSpecialEventOverride(eventTitle)
            if (specialEventResult != null) {
                val (_, requiresConfirmation) = specialEventResult
                if (requiresConfirmation) {
                    MessageLog.i(TAG, "[TRAINING_EVENT] Special event requires confirmation, waiting for dialog...")
                    
                    // Wait a moment for the confirmation dialog to appear.
                    game.wait(1.0)
                    
                    // Look for confirmation options and select the first one (Yes).
                    val confirmationLocations: ArrayList<Point> = game.imageUtils.findAll("training_event_active")
                    if (confirmationLocations.isNotEmpty()) {
                        val confirmLocation = confirmationLocations[0]
                        game.tap(confirmLocation.x + game.imageUtils.relWidth(100), confirmLocation.y, "training_event_active")
                        MessageLog.i(TAG, "[TRAINING_EVENT] Special event confirmed.")
                    } else {
                        MessageLog.w(TAG, "Could not find confirmation options for special event.")
                    }
                }
            }
        }

        MessageLog.i(TAG, "[TRAINING_EVENT] Process to handle detected Training Event completed.")
        MessageLog.i(TAG, "********************")
    }
}