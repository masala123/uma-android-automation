package com.steve1316.uma_android_automation.bot

import android.util.Log
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.MAX_STAT_VALUE
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.uma_android_automation.utils.types.StatName
import com.steve1316.uma_android_automation.utils.types.Aptitude
import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.TrackSurface
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.Mood
import com.steve1316.uma_android_automation.utils.types.DateYear
import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.ButtonTrainingSpeed
import com.steve1316.uma_android_automation.components.ButtonTrainingStamina
import com.steve1316.uma_android_automation.components.ButtonTrainingPower
import com.steve1316.uma_android_automation.components.ButtonTrainingGuts
import com.steve1316.uma_android_automation.components.ButtonTrainingWit
import com.steve1316.uma_android_automation.components.IconTrainingHeaderSpeed
import com.steve1316.uma_android_automation.components.IconTrainingHeaderStamina
import com.steve1316.uma_android_automation.components.IconTrainingHeaderPower
import com.steve1316.uma_android_automation.components.IconTrainingHeaderGuts
import com.steve1316.uma_android_automation.components.IconTrainingHeaderWit
import com.steve1316.uma_android_automation.components.LabelTrainingFailureChance
import com.steve1316.uma_android_automation.components.LabelStatTableHeaderSkillPoints
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.intArrayOf
import kotlin.math.pow
import org.opencv.core.Point

class Training(private val game: Game) {
	private val TAG: String = "[${MainActivity.loggerTag}]Training"

	/**
	 * Class to store analysis results for a training during parallel processing.
	 * Uses mutable properties so threads can update them.
	 */
	data class TrainingAnalysisResult(
		val name: StatName,
		val logMessages: ConcurrentLinkedQueue<String>,
		val latch: CountDownLatch,
		val startTime: Long
	) {
		var statGains: Map<StatName, Int> = mapOf()
		var statGainRowValues: Map<StatName, List<Int>> = emptyMap()
		var failureChance: Int = -1
		var relationshipBars: ArrayList<CustomImageUtils.BarFillResult> = arrayListOf()
		var numRainbow: Int = 0
		var numSpiritGaugesCanFill: Int = 0
		var numSpiritGaugesReadyToBurst: Int = 0
	}

	data class TrainingOption(
		val name: StatName,
		val statGains: Map<StatName, Int>,
		val failureChance: Int,
		val relationshipBars: ArrayList<CustomImageUtils.BarFillResult>,
		val numRainbow: Int,
		val numSpiritGaugesCanFill: Int = 0,
		val numSpiritGaugesReadyToBurst: Int = 0
	) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false

			other as TrainingOption

			if (failureChance != other.failureChance) return false
			if (name != other.name) return false
			if (!statGains.equals(other.statGains)) return false
			if (relationshipBars != other.relationshipBars) return false
			if (numRainbow != other.numRainbow) return false
			if (numSpiritGaugesCanFill != other.numSpiritGaugesCanFill) return false
			if (numSpiritGaugesReadyToBurst != other.numSpiritGaugesReadyToBurst) return false

			return true
		}

		override fun hashCode(): Int {
			var result = failureChance
			result = 31 * result + name.hashCode()
			result = 31 * result + statGains.entries.hashCode()
			result = 31 * result + relationshipBars.hashCode()
			result = 31 * result + numRainbow
			result = 31 * result + numSpiritGaugesCanFill
			result = 31 * result + numSpiritGaugesReadyToBurst
			return result
		}
	}

	val trainingMap: MutableMap<StatName, TrainingOption> = mutableMapOf()
	private val blacklist: List<StatName?> = SettingsHelper.getStringArraySetting("training", "trainingBlacklist").map { StatName.fromName(it) }
	private val statPrioritizationRaw: List<StatName> = SettingsHelper.getStringArraySetting("training", "statPrioritization").map { StatName.fromName(it)!! }
	val statPrioritization: List<StatName> = if (!statPrioritizationRaw.isEmpty()) {
		statPrioritizationRaw
	} else {
		StatName.entries
	}
	private val maximumFailureChance: Int = SettingsHelper.getIntSetting("training", "maximumFailureChance")
	private val disableTrainingOnMaxedStat: Boolean = SettingsHelper.getBooleanSetting("training", "disableTrainingOnMaxedStat")
	private val focusOnSparkStatTarget: List<StatName> = SettingsHelper.getStringArraySetting("training", "focusOnSparkStatTarget").map { StatName.fromName(it)!! }
	private val enableRainbowTrainingBonus: Boolean = SettingsHelper.getBooleanSetting("training", "enableRainbowTrainingBonus")
	private val enableRiskyTraining: Boolean = SettingsHelper.getBooleanSetting("training", "enableRiskyTraining")
	private val riskyTrainingMinStatGain: Int = SettingsHelper.getIntSetting("training", "riskyTrainingMinStatGain")
	private val riskyTrainingMaxFailureChance: Int = SettingsHelper.getIntSetting("training", "riskyTrainingMaxFailureChance")
	private val trainWitDuringFinale: Boolean = SettingsHelper.getBooleanSetting("training", "trainWitDuringFinale")
	private val manualStatCap: Int = SettingsHelper.getIntSetting("training", "manualStatCap")
	var firstTrainingCheck = true
	private val currentStatCap: Int
		get() = if (disableTrainingOnMaxedStat) manualStatCap else 1200

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	/**
	 * Handles the test to perform OCR on the current training on display for stat gains and failure chance.
	 */
	fun startSingleTrainingOCRTest() {
		MessageLog.i(TAG, "\n[TEST] Now beginning Single Training OCR test on the Training screen for the current training on display.")
		MessageLog.i(TAG, "[TEST] Note that this test is dependent on having the correct scale.")
		analyzeTrainings(test = true, singleTraining = true)
		printTrainingMap()
	}

	/**
	 * Handles the test to perform OCR on all 5 trainings on display for stat gains and failure chances.
	 */
	fun startComprehensiveTrainingOCRTest() {
		MessageLog.i(TAG, "\n[TEST] Now beginning Comprehensive Training OCR test on the Training screen for all 5 trainings on display.")
		MessageLog.i(TAG, "[TEST] Note that this test is dependent on having the correct scale.")
		analyzeTrainings(test = true)
		printTrainingMap()
	}

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	/**
	 * The entry point for handling Training.
	 */
	fun handleTraining() {
		MessageLog.i(TAG, "\n********************")
		MessageLog.i(TAG, "[TRAINING] Starting Training process on ${game.currentDate}.")
        val startTime = System.currentTimeMillis()

		// Enter the Training screen.
		if (game.findAndTapImage("training_option", region = game.imageUtils.regionBottomHalf)) {
			// Acquire the percentages and stat gains for each training.
			game.wait(0.5)
			analyzeTrainings()

			if (trainingMap.isEmpty()) {
				// Check if we should force Wit training during the Finale instead of recovering energy.
				if (trainWitDuringFinale && game.currentDate.day > 72) {
					MessageLog.i(TAG, "[TRAINING] There is not enough energy for training to be done but the setting to train Wit during the Finale is enabled. Forcing Wit training...")
					// Directly attempt to tap Wit training.
					if (game.findAndTapImage("training_wit", region = game.imageUtils.regionBottomHalf, taps = 3)) {
						MessageLog.i(TAG, "[TRAINING] Successfully forced Wit training during the Finale instead of recovering energy.")
						firstTrainingCheck = false
					} else {
						MessageLog.w(TAG, "[WARNING] Could not find Wit training button. Falling back to recovering energy...")
						game.findAndTapImage("back", region = game.imageUtils.regionBottomHalf)
						game.wait(1.0)
						if (game.checkMainScreen()) {
							game.recoverEnergy()
						} else {
							MessageLog.w(TAG, "[WARNING] Could not head back to the Main screen in order to recover energy.")
						}
					}
				} else {
					MessageLog.i(TAG, "[TRAINING] Backing out of Training and returning on the Main screen.")
					game.findAndTapImage("back", region = game.imageUtils.regionBottomHalf)
					game.wait(1.0)

					if (game.checkMainScreen()) {
						MessageLog.i(TAG, "[TRAINING] Will recover energy due to either failure chance was high enough to do so or no failure chances were detected via OCR.")
						game.recoverEnergy()
					} else {
						MessageLog.w(TAG, "[WARNING] Could not head back to the Main screen in order to recover energy.")
					}
				}
			} else {
				// Now select the training option with the highest weight.
				executeTraining()
				firstTrainingCheck = false
			}

			game.racing.raceRepeatWarningCheck = false
			MessageLog.i(TAG, "[TRAINING] Training process completed. Total time: ${System.currentTimeMillis() - startTime}ms")
		} else {
			MessageLog.e(TAG, "Cannot start the Training process. Moving on...")
		}
		MessageLog.i(TAG, "********************")
	}

	/**
	 * Analyze all 5 Trainings for their details including stat gains, relationship bars, etc.
	 *
	 * @param test Flag that forces the failure chance through even if it is not in the acceptable range for testing purposes.
	 * @param singleTraining Flag that forces only singular training analysis for the current training on the screen.
	 */
	fun analyzeTrainings(test: Boolean = false, singleTraining: Boolean = false) {
		if (singleTraining) {
            MessageLog.i(TAG, "\n[TRAINING] Now starting process to analyze the training on screen.")
        } else {
            MessageLog.i(TAG, "\n[TRAINING] Now starting process to analyze all 5 Trainings.")
        }

        val trainingButtons: Map<StatName, ComponentInterface> = mapOf(
            StatName.SPEED to ButtonTrainingSpeed,
            StatName.STAMINA to ButtonTrainingStamina,
            StatName.POWER to ButtonTrainingPower,
            StatName.GUTS to ButtonTrainingGuts,
            StatName.WIT to ButtonTrainingWit,
        )

        val iconTrainingHeaders: Map<StatName, ComponentInterface> = mapOf(
            StatName.SPEED to IconTrainingHeaderSpeed,
            StatName.STAMINA to IconTrainingHeaderStamina,
            StatName.POWER to IconTrainingHeaderPower,
            StatName.GUTS to IconTrainingHeaderGuts,
            StatName.WIT to IconTrainingHeaderWit,
        )

        // If not doing single training and speed training isn't active, make it active.
        if (!singleTraining && !IconTrainingHeaderSpeed.check(imageUtils = game.imageUtils)) {
            ButtonTrainingSpeed.click(imageUtils = game.imageUtils)
        }

        // List to store all training analysis results for parallel processing.
        val analysisResults = mutableListOf<TrainingAnalysisResult>()

        // Check if failure chance is acceptable: either within regular threshold or within risky threshold (if enabled).
        // This acts as an early exit from training analysis to speed up training.
        val failureChance: Int = game.imageUtils.findTrainingFailureChance()
        if (failureChance == -1) {
            MessageLog.w(TAG, "Skipping training due to not being able to confirm whether or not the bot is at the Training screen.")
            return
        }
        val isWithinRegularThreshold = failureChance <= maximumFailureChance
        val isWithinRiskyThreshold = enableRiskyTraining && failureChance <= riskyTrainingMaxFailureChance
        if (test || isWithinRegularThreshold || isWithinRiskyThreshold) {
            if (!test) {
                if (isWithinRegularThreshold) {
                    MessageLog.i(TAG, "[TRAINING] $failureChance% within acceptable range of ${maximumFailureChance}%. Proceeding to acquire all other percentages and total stat increases...")
                } else if (isWithinRiskyThreshold) {
                    MessageLog.i(TAG, "[TRAINING] $failureChance% exceeds regular threshold (${maximumFailureChance}%) but is within risky training threshold (${riskyTrainingMaxFailureChance}%). Proceeding to acquire all other percentages and total stat increases...")
                }
            }
        }
        
        // Now analyze each stat.
        for (statName in StatName.entries) {
            if (!test && statName in blacklist) {
                MessageLog.i(TAG, "[TRAINING] Skipping $statName training due to being blacklisted.")
                continue
            }

            // Keep iterating until the current training is found.
            if (singleTraining) {
                val iconTrainingHeader = iconTrainingHeaders[statName]!!
                if (!iconTrainingHeader.check(imageUtils = game.imageUtils)) {
                    continue
                }
                MessageLog.i(TAG, "[TRAINING] The $statName training is currently selected on the screen.")
            }


            // Only click the button if we arent doing single training.
            if (!singleTraining) {
                if (!trainingButtons[statName]!!.click(imageUtils = game.imageUtils)) {
                    MessageLog.e(TAG, "[TRAINING] Failed to click training button for $statName. Aborting training...")
                    return
                }
            }

            // Slight delay for UI to update after clicking button.
            game.wait(0.2)

            // Get bitmaps and locations before starting threads to make them safe for parallel processing.
            val sourceBitmap = game.imageUtils.getSourceBitmap()
            val skillPointsLocation = LabelStatTableHeaderSkillPoints.find(imageUtils = game.imageUtils).first
            val failureChanceLocation = LabelTrainingFailureChance.find(imageUtils = game.imageUtils).first

            // Record start time for elapsed time measurement.
            val startTime = System.currentTimeMillis()

            // Unified approach: always use result object and start threads the same way.
            // Use CountDownLatch to run the operations in parallel to cut down on processing time.
            // Note: For parallel processing, Spirit Explosion Gauge is handled synchronously for Unity Cup, so latch count is 4.
            // For singleTraining, Spirit Explosion Gauge runs in a thread for Unity Cup, so latch count is 5.
            val latch = CountDownLatch(if (singleTraining && game.scenario == "Unity Cup") 4 else 3)

            // Create log message buffer for this training.
            val logMessages = ConcurrentLinkedQueue<String>()

            // Create result object to store analysis state.
            val result = TrainingAnalysisResult(
                name = statName,
                logMessages = logMessages,
                latch = latch,
                startTime = startTime,
            )

            // For Unity Cup in parallel mode, run Spirit Explosion Gauge analysis synchronously before moving to next training.
            // This ensures if retry is needed, it can take a new screenshot while still on the correct training.
            // For singleTraining mode, handle it in a thread like the other analyses.
            if (game.scenario == "Unity Cup" && BotService.isRunning && !singleTraining) {
                val startTimeSpiritGauge = System.currentTimeMillis()
                val gaugeResult = game.imageUtils.analyzeSpiritExplosionGauges(sourceBitmap)
                if (gaugeResult != null) {
                    result.numSpiritGaugesCanFill = gaugeResult.numGaugesCanFill
                    result.numSpiritGaugesReadyToBurst = gaugeResult.numGaugesReadyToBurst
                } else {
                    result.numSpiritGaugesCanFill = 0
                    result.numSpiritGaugesReadyToBurst = 0
                }
                Log.d(TAG, "Total time to analyze Spirit Explosion Gauge for $statName: ${System.currentTimeMillis() - startTimeSpiritGauge}ms")
            }

            // Check if bot is still running before starting parallel threads.
            if (!BotService.isRunning) {
                MessageLog.i(TAG, "Bot stopped before training analysis could complete.")
                return
            }

            // Thread 1: Determine stat gains.
            Thread {
                val startTimeStatGains = System.currentTimeMillis()
                try {
                    val statGainResult = game.imageUtils.determineStatGainFromTraining(statName, sourceBitmap, skillPointsLocation!!)
                    result.statGains = statGainResult.statGains
                    result.statGainRowValues = statGainResult.rowValuesMap
                } catch (e: Exception) {
                    Log.e(TAG, "[ERROR] Error in determineStatGainFromTraining: ${e.stackTraceToString()}")
                    result.statGains = StatName.values().associateWith { 0 }.toMap()
                    result.statGainRowValues = emptyMap()
                } finally {
                    latch.countDown()
                    val elapsedTime = System.currentTimeMillis() - startTimeStatGains
                    Log.d(TAG, "Total time to determine stat gains for $statName: ${elapsedTime}ms")
                    if (!singleTraining) {
                        logMessages.offer("[TRAINING] [$statName] Stat gains analysis completed in ${elapsedTime}ms")
                    }
                }
            }.start()

            // Thread 2: Find failure chance.
            Thread {
                val startTimeFailureChance = System.currentTimeMillis()
                try {
                    result.failureChance = game.imageUtils.findTrainingFailureChance(sourceBitmap, failureChanceLocation!!)
                } catch (e: Exception) {
                    MessageLog.e(TAG, "Error in findTrainingFailureChance: ${e.stackTraceToString()}")
                    result.failureChance = -1
                } finally {
                    latch.countDown()
                    val elapsedTime = System.currentTimeMillis() - startTimeFailureChance
                    Log.d(TAG, "Total time to determine failure chance for $statName: ${elapsedTime}ms")
                    if (!singleTraining) {
                        logMessages.offer("[TRAINING] [$statName] Failure chance analysis completed in ${elapsedTime}ms")
                    }
                }
            }.start()

            // Thread 3: Analyze relationship bars.
            Thread {
                val startTimeRelationshipBars = System.currentTimeMillis()
                try {
                    result.relationshipBars = game.imageUtils.analyzeRelationshipBars(sourceBitmap, statName)
                    result.numRainbow = result.relationshipBars.count { barFillResult ->
                        barFillResult.isRainbow
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[ERROR] Error in analyzeRelationshipBars: ${e.stackTraceToString()}")
                    result.relationshipBars = arrayListOf()
                } finally {
                    latch.countDown()
                    val elapsedTime = System.currentTimeMillis() - startTimeRelationshipBars
                    Log.d(TAG, "Total time to analyze relationship bars for $statName: ${elapsedTime}ms")
                    if (!singleTraining) {
                        logMessages.offer("[TRAINING] [$statName] Relationship bars analysis completed in ${elapsedTime}ms")
                    }
                }
            }.start()

            // Thread 4: Analyze Spirit Explosion Gauges (Unity Cup only, singleTraining mode only).
            if (game.scenario == "Unity Cup" && singleTraining) {
                Thread {
                    val startTimeSpiritGauge = System.currentTimeMillis()
                    try {
                        val gaugeResult = game.imageUtils.analyzeSpiritExplosionGauges(sourceBitmap)
                        if (gaugeResult != null) {
                            result.numSpiritGaugesCanFill = gaugeResult.numGaugesCanFill
                            result.numSpiritGaugesReadyToBurst = gaugeResult.numGaugesReadyToBurst
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "[ERROR] Error in Spirit Explosion Gauge analysis: ${e.stackTraceToString()}")
                        result.numSpiritGaugesCanFill = 0
                        result.numSpiritGaugesReadyToBurst = 0
                    } finally {
                        latch.countDown()
                        Log.d(TAG, "Total time to analyze Spirit Explosion Gauge for $statName: ${System.currentTimeMillis() - startTimeSpiritGauge}ms")
                    }
                }.start()
            }

            // Branch on singleTraining vs parallel processing.
            if (singleTraining) {
                // For singleTraining, wait here and process immediately.
                try {
                    latch.await(3, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    Log.e(TAG, "[ERROR] Parallel training analysis timed out")
                } finally {
                    val elapsedTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Total time for $statName training analysis: ${elapsedTime}ms")
                    // Log stat gain results sequentially after threads complete to ensure correct order.
					logStatGainResults(statName, result.statGains, result.statGainRowValues)
                    MessageLog.i(TAG, "All 5 stat regions processed for $statName training. Results: ${result.statGains.toString()}")
                }

                // Check if risky training logic should apply based on main stat gain.
                val mainStatGain = result.statGains[result.name] ?: 0
                val effectiveFailureChance = if (enableRiskyTraining && mainStatGain >= riskyTrainingMinStatGain) {
                    riskyTrainingMaxFailureChance
                } else {
                    maximumFailureChance
                }

                // Filter out trainings that exceed the effective failure chance threshold.
                if (!test && result.failureChance > effectiveFailureChance) {
                    if (enableRiskyTraining && mainStatGain >= riskyTrainingMinStatGain) {
                        MessageLog.i(TAG, "[TRAINING] Skipping $statName training due to failure chance (${result.failureChance}%) exceeding risky threshold (${riskyTrainingMaxFailureChance}%) despite high main stat gain of $mainStatGain.")
                    } else {
                        MessageLog.i(TAG, "[TRAINING] Skipping $statName training due to failure chance (${result.failureChance}%) exceeding threshold (${maximumFailureChance}%).")
                    }
                    continue
                }

                val newTraining = TrainingOption(
                    name = result.name,
                    statGains = result.statGains,
                    failureChance = result.failureChance,
                    relationshipBars = result.relationshipBars,
                    numRainbow = result.numRainbow,
                    numSpiritGaugesCanFill = result.numSpiritGaugesCanFill,
                    numSpiritGaugesReadyToBurst = result.numSpiritGaugesReadyToBurst
                )
                trainingMap[result.name] = newTraining
                break
            } else {
                // For parallel processing, store result for later processing.
                analysisResults.add(result)
            }
        }

        // For parallel processing, wait for all analyses and process results.
        if (!singleTraining && analysisResults.isNotEmpty()) {
            // Wait for all analysis threads to complete in parallel with 10s timeout.
            val waitThreads = analysisResults.map { result ->
                Thread {
                    try {
                        // Check if bot is still running before waiting.
                        if (!BotService.isRunning) {
                            return@Thread
                        }
                        result.latch.await(10, TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "[ERROR] Parallel training analysis timed out for ${result.name}")
                        Thread.currentThread().interrupt()
                    } finally {
                        // Only log and process if bot is still running.
                        if (BotService.isRunning) {
                            val elapsedTime = System.currentTimeMillis() - result.startTime
                            Log.d(TAG, "Total time for ${result.name} training analysis: ${elapsedTime}ms")
                            result.logMessages.offer("[TRAINING] [${result.name}] All analysis threads completed. Total time: ${elapsedTime}ms")
                            result.logMessages.offer("[TRAINING] [${result.name}] All 5 stat regions processed. Results: ${result.statGains.toString()}")
                        }
                    }
                }
            }

            // Start all wait threads concurrently.
            waitThreads.forEach { it.start() }
            // Join all wait threads to ensure completion.
            if (BotService.isRunning) {
                waitThreads.forEach { it.join() }
            } else {
                return
            }

            // Process results and output logs in training order.
            for (result in analysisResults) {
                // Output buffered log messages for this training.
                while (result.logMessages.isNotEmpty()) {
                    MessageLog.i(TAG, result.logMessages.poll())
                }

                // Log stat gain results sequentially after threads complete to ensure correct order.
			    logStatGainResults(result.name, result.statGains, result.statGainRowValues)

                // Check if risky training logic should apply based on main stat gain.
                val mainStatGain: Int = result.statGains[result.name] ?: 0
                val effectiveFailureChance = if (enableRiskyTraining && mainStatGain >= riskyTrainingMinStatGain) {
                    riskyTrainingMaxFailureChance
                } else {
                    maximumFailureChance
                }
                
                // Filter out trainings that exceed the effective failure chance threshold.
                if (!test && result.failureChance > effectiveFailureChance) {
                    if (enableRiskyTraining && mainStatGain >= riskyTrainingMinStatGain) {
                        MessageLog.i(TAG, "[TRAINING] Skipping ${result.name} training due to failure chance (${result.failureChance}%) exceeding risky threshold (${riskyTrainingMaxFailureChance}%) despite high main stat gain of $mainStatGain.")
                    } else {
                        MessageLog.i(TAG, "[TRAINING] Skipping ${result.name} training due to failure chance (${result.failureChance}%) exceeding threshold (${maximumFailureChance}%).")
                    }
                    continue
                }

                val newTraining = TrainingOption(
                    name = result.name,
                    statGains = result.statGains,
                    failureChance = result.failureChance,
                    relationshipBars = result.relationshipBars,
                    numRainbow = result.numRainbow,
                    numSpiritGaugesCanFill = result.numSpiritGaugesCanFill,
                    numSpiritGaugesReadyToBurst = result.numSpiritGaugesReadyToBurst
                )
                trainingMap[result.name] = newTraining
            }
        }

        if (singleTraining) {
            MessageLog.i(TAG, "[TRAINING] Process to analyze the singular Training complete.")
        } else {
            MessageLog.i(TAG, "[TRAINING] Process to analyze all 5 Trainings complete.")
        }
	}

	/**
	 * Recommends the best training option based on ratio completion toward stat targets.
	 *
	 * This function implements a ratio-based training recommendation system that treats
	 * stat targets as desired distribution rather than sequential goals.
	 *
	 * **Early Game (Pre-Debut/Year 1):**
	 * - Focuses on relationship building using `scoreFriendshipTraining()`
	 * - Prioritizes training options that build friendship bars, especially blue bars
	 * - Ignores stat gains in favor of relationship development
	 *
	 * **Mid/Late Game (Year 2+):**
	 * - Uses ratio-based scoring via `calculateRawTrainingScore()`
	 * - Scores based on completion percentage (currentStat / targetStat)
	 * - Trains stats furthest behind their target ratio
	 * - Priority order only breaks ties when completion percentages are similar
	 *
	 * The scoring system considers multiple factors:
	 * - **Ratio Completion:** How far each stat is toward its target percentage (primary driver)
	 * - **Priority Tiebreaker:** Only matters when stats have similar completion percentages
	 * - **Main Stat Bonus:** High gains on main stat get bonus (likely undetected rainbow)
	 * - **Rainbow Detection:** Heavily favored for overall ratio balance
	 *
	 * @return The name of the recommended training option, or NULL if no suitable option found.
	 */
	private fun recommendTraining(): StatName? {
		/**
		 * Scores the currently selected training option during Junior Year based on friendship bar progress.
		 *
		 * This algorithm prefers training options with the least relationship progress (especially blue bars).
		 * It ignores stat gains unless all else is equal.
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A score representing relationship-building value.
		 */
		fun scoreFriendshipTraining(training: TrainingOption): Double {
			// Ignore the blacklist in favor of making sure we build up the relationship bars as fast as possible.
			MessageLog.i(TAG, "\n[TRAINING] Starting process to score ${training.name} Training with a focus on building relationship bars.")

			val barResults = training.relationshipBars
			if (barResults.isEmpty()) return Double.NEGATIVE_INFINITY

			var score = 0.0
			for (bar in barResults) {
				val contribution = when (bar.dominantColor) {
					"orange" -> 0.0
					"green" -> 1.0
					"blue" -> 2.5
					else -> 0.0
				}
				score += contribution
			}

			MessageLog.i(TAG, "[TRAINING] ${training.name} Training has a score of ${game.decimalFormat.format(score)} with a focus on building relationship bars.")
			return score
		}

		/**
		 * Scores training options for Unity Cup based on Spirit Explosion Gauge priority system.
		 *
		 * Priority order:
		 * 1. Highest Priority: Trainings with Spirit Explosion Gauges ready to burst.
		 * 2. Second Priority: Trainings that can fill Spirit Explosion Gauges (not at 100% yet).
		 * 3. Third Priority: Trainings that fill relationship bars.
		 * 4. Lowest Priority: Stat prioritization (only if no gauge/relationship opportunities).
		 *
		 * Additional considerations:
		 * - If gauges can be filled for deprioritized stat trainings, ignore stat prioritization (early game).
		 * - Sometimes worth doing training with no relationship bar gains if building up several bursts.
		 * - Ideally doing unity training at the same time as triggering regular rainbow trainings.
		 * - Good facilities to burst: Speed (increased speed stat gains), Wit (energy recovery + speed stat gain).
		 * - Stamina and Power can be bursted if lacking stats.
		 * - Guts is not ideal but can be worth it if building up several other bursts.
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A score representing Unity Cup training value.
		 */
		fun scoreUnityCupTraining(training: TrainingOption): Double {
			MessageLog.i(TAG, "\n[TRAINING] Starting process to score ${training.name} Training for Unity Cup with Spirit Explosion Gauge priority.")

            val target: Map<StatName, Int> = game.trainee.getStatTargetsByDistance()
            val currentStat = game.trainee.getStat(training.name)
            val targetStat = target[training.name] ?: 600

			// 1. Highest Priority: Trainings with Spirit Explosion Gauges ready to burst.
            var score = 0.0
			if (training.numSpiritGaugesReadyToBurst > 0) {
				// Score increases with number of gauges ready to burst.
				score += 1000.0 + (training.numSpiritGaugesReadyToBurst * 1000.0)
				MessageLog.i(TAG, "[TRAINING] ${training.name} Training has ${training.numSpiritGaugesReadyToBurst} Spirit Explosion Gauge(s) ready to burst. Highest priority.")

				// Facility preference bonuses for bursting.
				when (training.name) {
					StatName.SPEED -> score += 500.0 // Best for increased speed stat gains.
					StatName.WIT -> score += 500.0 // Best for energy recovery and slightly increased speed stat gain.
					StatName.STAMINA, StatName.POWER -> {
						// Can be bursted if lacking stats.
						if (currentStat < targetStat * 0.8) {
							score += 300.0
						}
					}
					StatName.GUTS -> {
						// Guts is not ideal, but can be worth it if building up gauges to max them out for bursting.
						if (training.numSpiritGaugesCanFill >= 2) {
							score += 200.0 // Building up multiple gauges to allow for bursting.
						} else {
							score -= 100.0 // Not ideal without building up multiple gauges.
						}
					}
				}

				// Bonus for rainbow training while bursting.
                var rainbowBonusScore = 0.0
                for (i in 1 until training.numRainbow + 1) {
                    rainbowBonusScore += 400 * (0.5).pow(i)
                }
				if (rainbowBonusScore > 0) {
					MessageLog.i(TAG, "[TRAINING] Adding bonus score for ${training.numRainbow} rainbow trainings: $rainbowBonusScore")
                    score += rainbowBonusScore
				}
			}

			// 2. Second Priority: Trainings that can fill Spirit Explosion Gauges (not at 100% yet).
			if (training.numSpiritGaugesCanFill > 0) {
				// Score increases with number of gauges that can be filled.
				// Each gauge fills by 25% per training execution.
				score += 1000.0 + (training.numSpiritGaugesCanFill * 200.0)
				MessageLog.i(TAG, "[TRAINING] ${training.name} Training can fill ${training.numSpiritGaugesCanFill} Spirit Explosion Gauge(s).")

				// Early game: If gauges can be filled for deprioritized stat trainings, ignore stat prioritization.
				val isEarlyGame = game.currentDate.year == DateYear.JUNIOR
				if (isEarlyGame) {
					score += 500.0
					MessageLog.i(TAG, "[TRAINING] Early game: Prioritizing gauge filling over stat prioritization.")
				}
			}

			// 3. Third Priority: Trainings that fill relationship bars.
			if (training.relationshipBars.isNotEmpty()) {
				var relationshipScore = 0.0
				for (bar in training.relationshipBars) {
					val contribution = when (bar.dominantColor) {
						"orange" -> 0.0
						"green" -> 1.0
						"blue" -> 2.5
						else -> 0.0
					}
					relationshipScore += contribution
				}
				score += 100.0 + (relationshipScore * 20.0)
				MessageLog.i(TAG, "[TRAINING] ${training.name} Training fills relationship bars. Score: ${game.decimalFormat.format(relationshipScore)}.")
			}

			// 4. Lowest Priority: Stat prioritization.
			val statGain = training.statGains[training.name] ?: 0
			score += statGain.toDouble() * 0.1
			MessageLog.i(TAG, "[TRAINING] ${training.name} Training stat gain contribution: $statGain.")

			// Sometimes worth doing training with no relationship bar gains if building up several bursts.
			if (training.relationshipBars.isEmpty() && training.numSpiritGaugesCanFill > 0) {
				val otherBurstsBuilding = trainingMap.values.sumOf { it.numSpiritGaugesCanFill } - training.numSpiritGaugesCanFill
				if (otherBurstsBuilding >= 2) {
					score += 300.0 // Building up several bursts is worth it.
					Log.d(TAG, "[DEBUG] ${training.name} Training has no relationship bars but is building up ${training.numSpiritGaugesCanFill} gauge(s) along with $otherBurstsBuilding other gauges being built.")
				}
			}

			MessageLog.i(TAG, "[TRAINING] ${training.name} Training has a Unity Cup score of ${game.decimalFormat.format(score)}.")
			return score
		}

		/**
		 * Calculates stat efficiency based on ratio completion toward targets.
		 *
		 * This function treats stat targets as desired ratios rather than sequential goals.
		 * It scores training based on how well it balances the overall stat distribution.
		 *
		 * Key principles:
		 * - Stats furthest behind their target ratio get highest priority
		 * - Completion percentage = currentStat / targetStat
		 * - Priority order only breaks ties when completion percentages are similar
		 * - High main stat gains receive bonus (likely undetected rainbow)
		 *
		 * @param training The training option to evaluate.
		 * @param target Array of target stat values representing desired ratio.
		 *
		 * @return Raw score representing stat efficiency (will be normalized later).
		 */
		fun calculateStatEfficiencyScore(training: TrainingOption, target: Map<StatName, Int>): Double {
			var score = 0.0
			
			for (statName in StatName.entries) {
				val currentStat = game.trainee.getStat(statName)
				val targetStat = target[statName] ?: 0
				val statGain = training.statGains[statName] ?: 0
				
				if (statGain > 0 && targetStat > 0) {
					val priorityIndex = statPrioritization.indexOf(statName)
					
					// Calculate completion percentage (how far along this stat is toward its target).
					val completionPercent = (currentStat.toDouble() / targetStat) * 100.0
					
					// Ratio-based multiplier: Stats furthest behind get highest priority.
					val ratioMultiplier = when {
						completionPercent < 30.0 -> 5.0   // Severely behind.
						completionPercent < 50.0 -> 4.0   // Significantly behind.
						completionPercent < 70.0 -> 3.0   // Moderately behind.
						completionPercent < 90.0 -> 2.0   // Slightly behind.
						completionPercent < 110.0 -> 1.0  // At target.
						completionPercent < 130.0 -> 0.5  // Slightly over.
						else -> 0.3                       // Well over.
					}
					
					// Priority-based tiebreaker (only applies when completion is similar).
					// Find the completion percentage of the highest priority stat for comparison.
					val highestPriorityStat: StatName? = statPrioritization.firstOrNull()
					val highestPriorityCompletion = if (highestPriorityStat != null) {
						val hpCurrent = game.trainee.getStat(highestPriorityStat)
						val hpTarget = target[highestPriorityStat] ?: 1
						(hpCurrent.toDouble() / hpTarget) * 100.0
					} else {
						completionPercent
					}
					
					// Only apply priority bonus if this stat's completion is within 10% of highest priority stat.
					val priorityMultiplier = if (priorityIndex != -1 && kotlin.math.abs(completionPercent - highestPriorityCompletion) <= 10.0) {
						1.0 + (0.1 * (statPrioritization.size - priorityIndex))
					} else {
						1.0
					}
					
					// Main stat gain bonus: If training improves its MAIN stat by a large amount, it is most likely an undetected rainbow.
					val isMainStat = training.name == statName
					val mainStatBonus = if (isMainStat && statGain >= 30) {
						2.0
					} else {
						1.0
					}
					
					val isLateGame = game.currentDate.year == DateYear.SENIOR
                    // Spark bonus: Prioritize training sessions for 3* sparks for selected stats below 600 if the setting is enabled.
                    val isSparkStat = statName in focusOnSparkStatTarget
                    val canTriggerSpark = currentStat < 600
                    val sparkBonus = if (isSparkStat && canTriggerSpark) {
                        MessageLog.i(TAG, "[TRAINING] $statName is at $currentStat (< 600). Prioritizing this training for potential spark event to get above 600.")
                        2.5
                    } else {
                        1.0
                    }
                    
                    if (game.debugMode) {
                        val bonusNote = if (isMainStat && statGain >= 30) " [HIGH MAIN STAT]" else ""
                        val sparkNote = if (isSparkStat && canTriggerSpark) " [SPARK PRIORITY]" else ""
						MessageLog.d(
                            TAG,
                            "$statName: gain=$statGain, completion=${game.decimalFormat.format(completionPercent)}%, " +
							"ratioMult=${game.decimalFormat.format(ratioMultiplier)}, priorityMult=${game.decimalFormat.format(priorityMultiplier)}$bonusNote$sparkNote",
						)
					} else {
						Log.d(TAG, "$statName: gain=$statGain, completion=${game.decimalFormat.format(completionPercent)}%, ratioMult=$ratioMultiplier, priorityMult=$priorityMultiplier")
					}
					
					// Calculate final score for this stat.
					var statScore = statGain.toDouble()
					statScore *= ratioMultiplier
					statScore *= priorityMultiplier
					statScore *= mainStatBonus
					statScore *= sparkBonus
					
					score += statScore
				}
			}
			
			return score
		}

		/**
		 * Calculates relationship building score with diminishing returns.
		 *
		 * Evaluates the value of relationship bars based on their color and fill level:
		 * - Blue bars: 2.5 points (highest priority)
		 * - Green bars: 1.0 points (medium priority)
		 * - Orange bars: 0.0 points (no value)
		 *
		 * Applies diminishing returns as bars fill up and early game bonuses for relationship building.
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A normalized score (0-100) representing relationship building value.
		 */
		fun calculateRelationshipScore(training: TrainingOption): Double {
			if (training.relationshipBars.isEmpty()) return 0.0

			var score = 0.0
			var maxScore = 0.0

			for (bar in training.relationshipBars) {
				val baseValue = when (bar.dominantColor) {
					"orange" -> 0.0
					"green" -> 1.0
					"blue" -> 2.5
					else -> 0.0
				}

				if (baseValue > 0) {
					// Apply diminishing returns for relationship building.
					val fillLevel = bar.fillPercent / 100.0
					val diminishingFactor = 1.0 - (fillLevel * 0.5) // Less valuable as bars fill up.

					// Early game bonus for relationship building.
					val earlyGameBonus = if (game.currentDate.year == DateYear.JUNIOR || game.currentDate.bIsPreDebut) 1.3 else 1.0

					val contribution = baseValue * diminishingFactor * earlyGameBonus
					score += contribution
					maxScore += 2.5 * 1.3
				}
			}

			return if (maxScore > 0) (score / maxScore * 100.0) else 0.0
		}

		/**
		 * Calculates miscellaneous bonuses and penalties based on training properties.
		 *
		 * Applies bonuses for skill hints that provide additional value to training sessions.
		 * Removed complex phase bonuses to avoid conflicts with target-based scoring.
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A misc score between 0-100 representing situational bonuses.
		 */
		fun calculateMiscScore(training: TrainingOption): Double {
			// Start with neutral score.
			var score = 50.0

			// Bonuses for skill hints.
            val skillHintLocations = game.imageUtils.findAll(
                "stat_skill_hint",
				region = intArrayOf(
					SharedData.displayWidth - (SharedData.displayWidth / 3),
					0,
					(SharedData.displayWidth / 3),
					SharedData.displayHeight - (SharedData.displayHeight / 3)
				)
			)
            if (skillHintLocations.isNotEmpty()) {
                MessageLog.i(TAG, "[TRAINING] Skill hint(s) detected for ${training.name} Training.")
            }
			score += 10.0 * skillHintLocations.size

            return score.coerceIn(0.0, 100.0)
        }

	    /**
		 * Calculates raw training score without normalization.
		 *
		 * This function calculates raw training scores
		 * that will be normalized based on the actual maximum score in the current session.
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return Raw score representing overall training value.
		 */
		fun calculateRawTrainingScore(training: TrainingOption): Double {
			if (training.name in blacklist) return 0.0

            val currentStat = game.trainee.getStat(training.name)

			// Don't score for stats that are maxed or would be maxed.
			if ((disableTrainingOnMaxedStat && currentStat >= MAX_STAT_VALUE) ||
				(currentStat + (training.statGains[training.name] ?: 0) >= MAX_STAT_VALUE)) {
				return 0.0
			}

            val target: Map<StatName, Int> = game.trainee.getStatTargetsByDistance()

			var totalScore = 0.0

			// 1. Stat Efficiency scoring
			val statScore = calculateStatEfficiencyScore(training, target)

			// 2. Friendship scoring
			val relationshipScore = calculateRelationshipScore(training)

			// 3. Misc-aware scoring
			val miscScore = calculateMiscScore(training)

			// Define scoring weights based on relationship bars presence.
			val statWeight = if (training.relationshipBars.isNotEmpty()) 0.6 else 0.7
			val relationshipWeight = if (training.relationshipBars.isNotEmpty()) 0.1 else 0.0
			val miscWeight = 0.3

			// Calculate weighted total score.
			totalScore += statScore * statWeight
			totalScore += relationshipScore * relationshipWeight
			totalScore += miscScore * miscWeight

			// 4. Rainbow training multiplier (Year 2+ only).
			// Rainbow is heavily favored because it improves overall ratio balance.
			val rainbowMultiplier = if (training.numRainbow > 0 && game.currentDate.year > DateYear.JUNIOR) {
				if (enableRainbowTrainingBonus) {
                    MessageLog.i(TAG, "[TRAINING] ${training.name} Training is detected as a rainbow training. Adding multiplier to score.")
					2.0
				} else {
                    MessageLog.i(TAG, "[TRAINING] ${training.name} Training is detected as a rainbow training, but rainbow training bonus is not enabled.")
					1.5
				}
			} else {
				1.0
			}

			// Apply rainbow multiplier to total score.
			totalScore *= rainbowMultiplier

			return totalScore.coerceAtLeast(0.0)
		}

		// Decide which scoring function to use based on campaign, phase, or year.
		val best = if (game.scenario == "Unity Cup" && game.currentDate.year < DateYear.SENIOR) {
            // Unity Cup (Year < 3): Use Spirit Explosion Gauge priority system.
			trainingMap.values.maxByOrNull { scoreUnityCupTraining(it) }
		} else if (game.currentDate.bIsPreDebut || game.currentDate.year == DateYear.JUNIOR) {
            // Junior Year: Focus on building relationship bars.
			trainingMap.values.maxByOrNull { scoreFriendshipTraining(it) }
		} else {
			// For Year 2+, calculate all scores first, then normalize based on actual maximum.
			val trainingScores = trainingMap.values.associateWith { training -> calculateRawTrainingScore(training) }

            val maxScore = trainingScores.values.maxOrNull() ?: 0.0
			
			// Normalize scores to 0-100 scale based on actual maximum.
			val normalizedScores = trainingScores.mapValues { (_, score) ->
				if (maxScore > 0) (score / maxScore * 100.0).coerceIn(0.0, 100.0) else 0.0
			}
			
			// Log normalized scores for debugging.
			normalizedScores.forEach { (training, score) ->
				MessageLog.i(TAG, "[TRAINING] ${training.name}: ${game.decimalFormat.format(score)}/100")
			}
			
			trainingScores.keys.maxByOrNull { normalizedScores[it] ?: 0.0 }
		}

		return best?.name ?: (trainingMap.keys.firstOrNull { it !in blacklist } ?: null)
	}

	/**
	 * Execute the training with the highest stat weight.
	 */
	private fun executeTraining() {
		MessageLog.i(TAG, "[TRAINING] Now starting process to execute training...")
		val trainingSelected = recommendTraining()

		if (trainingSelected != null) {
			printTrainingMap()
			MessageLog.i(TAG, "[TRAINING] Executing the $trainingSelected Training.")
			game.findAndTapImage("training_${trainingSelected.name.lowercase()}", region = game.imageUtils.regionBottomHalf, taps = 3)
            game.wait(1.0)

            // Dismiss any popup warning about a scheduled race.
            game.findAndTapImage("ok", tries = 1, region = game.imageUtils.regionMiddle, suppressError = true)

			MessageLog.i(TAG, "[TRAINING] Process to execute training completed.")
		} else {
			MessageLog.i(TAG, "[TRAINING] Conditions have not been met so training will not be done.")
		}

		// Now reset the Training map.
		trainingMap.clear()
	}

	/**
	 * Prints the training map object for informational purposes.
	 */
	private fun printTrainingMap() {
		MessageLog.i(TAG, "\nStat Gains by Training:")
		trainingMap.forEach { name, training ->
			MessageLog.i(TAG, "$name Training stat gains: ${training.statGains}, failure chance: ${training.failureChance}%, rainbow: ${training.numRainbow}.")
		}
	}

    /**
	 * Logs stat gain results sequentially to ensure correct order.
	 * This is called after threads complete to avoid out-of-order messages.
	 *
	 * @param trainingName Name of the training type (Speed, Stamina, Power, Guts, Wit).
	 * @param statGains Array of 5 stat gains.
	 * @param rowValuesMap Map of stat index to row values for Unity Cup cases.
	 */
	private fun logStatGainResults(trainingName: StatName, statGains: Map<StatName, Int>, rowValuesMap: Map<StatName, List<Int>>) {
		// Define a mapping of training types to their stat indices.
        val trainingStatMap = mapOf(
			StatName.SPEED to listOf(StatName.SPEED, StatName.POWER),
			StatName.STAMINA to listOf(StatName.STAMINA, StatName.GUTS),
			StatName.POWER to listOf(StatName.STAMINA, StatName.POWER),
			StatName.GUTS to listOf(StatName.SPEED, StatName.POWER, StatName.GUTS),
			StatName.WIT to listOf(StatName.SPEED, StatName.WIT),
		)

		// Iterate over the StatName enum so we always print stats in the same order.
        for (statName in StatName.entries) {
            val appliedStatNames = trainingStatMap[trainingName] ?: emptyList()
            if (statName in appliedStatNames && statGains.getOrDefault(statName, -1) >= 0) {
				val rowValues = rowValuesMap[statName]
				if (rowValues != null) {
					// Unity Cup case: log with row values.
					MessageLog.d(TAG, "[INFO] $statName final constructed values from $trainingName training: $rowValues, sum: ${statGains[statName]}.")
				} else {
					// Single row case: log simple value.
					MessageLog.d(TAG, "[INFO] $statName final constructed value from $trainingName training: ${statGains[statName]}.")
				}
			}
		}
	}
}