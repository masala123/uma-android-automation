package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SettingsHelper
import org.opencv.core.Point

class TrainingEvent(private val game: Game) {
    private val tag: String = "[${MainActivity.loggerTag}]TrainingEvent"

    private val trainingEventRecognizer: TrainingEventRecognizer = TrainingEventRecognizer(game, game.imageUtils)

    val enablePrioritizeEnergyOptions: Boolean = SettingsHelper.getBooleanSetting("trainingEvent", "enablePrioritizeEnergyOptions")
    val acupunctureOption: String = SettingsHelper.getStringSetting("trainingEvent", "acupunctureOption")

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    // Functions to handle Training Events with the help of the TrainingEventRecognizer class.

    /**
     * Start text detection to determine what Training Event it is and the event rewards for each option.
     * It will then select the best option according to the user's preferences. By default, it will choose the first option.
     */
    fun handleTrainingEvent() {
        game.printToLog("\n[TRAINING_EVENT] Starting Training Event process...", tag = tag)

        val (eventRewards, confidence, eventTitle) = trainingEventRecognizer.start()

        val regex = Regex("[a-zA-Z]+")
        var optionSelected = 0

        // Double check if the bot is at the Main screen or not.
        if (game.checkMainScreen()) {
            return
        }

        if (eventRewards.isNotEmpty() && eventRewards[0] != "") {
            // Check if this is the Acupuncture event and handle it specially.
            if (eventTitle.contains("Acupuncture") || eventTitle.contains("Just an Acupuncturist")) {
                game.printToLog("[TRAINING_EVENT] Detected Acupuncture event: $eventTitle", tag = tag)
                
                // Parse the option number from the setting (e.g., "Option 5: Energy +10" -> 5),
                val optionMatch = Regex("Option (\\d+)").find(acupunctureOption)
                optionSelected = if (optionMatch != null) {
                    // Convert to 0-based index.
                    val optionNumber = optionMatch.groupValues[1].toInt()
                    game.printToLog("[TRAINING_EVENT] Using Acupuncture setting: $acupunctureOption (Option $optionNumber)", tag = tag)
                    optionNumber - 1
                } else {
                    // Default to Option 5.
                    game.printToLog("[WARNING] Could not parse option number from acupuncture setting: $acupunctureOption. Using default option 5.", tag = tag)
                    4
                }
                
                // Ensure the selected option is within bounds.
                if (optionSelected >= eventRewards.size) {
                    game.printToLog("[WARNING] Selected acupuncture option $optionSelected is out of bounds. Using last option.", tag = tag)
                    optionSelected = eventRewards.size - 1
                }
                
                game.printToLog("[TRAINING_EVENT] Acupuncture event will select option ${optionSelected + 1}: \"${eventRewards[optionSelected]}\"", tag = tag)
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
                            game.printToLog("[TRAINING-EVENT Adding weight for option#${optionSelected + 1} of $moodWeight for ${if (moodWeight > 0) "positive" else "negative"} mood gain.", tag = tag)
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
                var max: Int? = selectionWeight.maxOrNull()
                if (max == null) {
                    max = 0
                    optionSelected = 0
                } else {
                    optionSelected = selectionWeight.indexOf(max)
                }

                // Print the selection weights.
                game.printToLog("[TRAINING_EVENT] Selection weights for each option:", tag = tag)
                selectionWeight.forEachIndexed { index, weight ->
                    game.printToLog("Option ${index + 1}: $weight", tag = tag)
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

            game.printToLog(resultString, tag = tag)
        } else {
            game.printToLog("[TRAINING_EVENT] First option will be selected since OCR failed to detect anything.", tag = tag)
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
            
            // Check if this was an Acupuncture event and handle confirmation.
            if (eventRewards.isNotEmpty() && eventRewards[0] != "" && (eventTitle.contains("Acupuncture") || eventTitle.contains("Just an Acupuncturist"))) {
                game.printToLog("[TRAINING_EVENT] Acupuncture event selected, waiting for confirmation dialog...", tag = tag)
                
                // Wait a moment for the confirmation dialog to appear.
                game.wait(1.0)
                
                // Look for confirmation options and select the first one (Yes).
                val confirmationLocations: ArrayList<Point> = game.imageUtils.findAll("training_event_active")
                if (confirmationLocations.isNotEmpty()) {
                    val confirmLocation = confirmationLocations[0]
                    game.tap(confirmLocation.x + game.imageUtils.relWidth(100), confirmLocation.y, "training_event_active")
                    game.printToLog("[TRAINING_EVENT] Acupuncture event confirmed.", tag = tag)
                } else {
                    game.printToLog("[WARNING] Could not find confirmation options for Acupuncture event.", tag = tag)
                }
            }
        }

        game.printToLog("[TRAINING_EVENT] Process to handle detected Training Event completed.", tag = tag)
    }
}