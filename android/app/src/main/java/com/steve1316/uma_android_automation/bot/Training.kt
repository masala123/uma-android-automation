package com.steve1316.uma_android_automation.bot

import android.util.Log
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.MessageLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.intArrayOf

class Training(private val game: Game) {
	private val TAG: String = "[${MainActivity.loggerTag}]Training"

	/**
	 * Class to store analysis results for a training during parallel processing.
	 * Uses mutable properties so threads can update them.
	 */
	private class TrainingAnalysisResult(
		val training: String,
		val index: Int,
		val logMessages: ConcurrentLinkedQueue<String>,
		val latch: CountDownLatch,
		val startTime: Long
	) {
		var statGains: IntArray = intArrayOf()
		var statGainRowValues: Map<Int, List<Int>> = emptyMap()
		var failureChance: Int = -1
		var relationshipBars: ArrayList<CustomImageUtils.BarFillResult> = arrayListOf()
		var isRainbow: Boolean = false
		var numSpiritGaugesCanFill: Int = 0
		var numSpiritGaugesReadyToBurst: Int = 0
	}

	data class TrainingOption(
		val name: String,
		val statGains: IntArray,
		val failureChance: Int,
		val relationshipBars: ArrayList<CustomImageUtils.BarFillResult>,
		val isRainbow: Boolean,
		val numSpiritGaugesCanFill: Int = 0,
		val numSpiritGaugesReadyToBurst: Int = 0
	) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false

			other as TrainingOption

			if (failureChance != other.failureChance) return false
			if (name != other.name) return false
			if (!statGains.contentEquals(other.statGains)) return false
			if (relationshipBars != other.relationshipBars) return false
			if (isRainbow != other.isRainbow) return false
			if (numSpiritGaugesCanFill != other.numSpiritGaugesCanFill) return false
			if (numSpiritGaugesReadyToBurst != other.numSpiritGaugesReadyToBurst) return false

			return true
		}

		override fun hashCode(): Int {
			var result = failureChance
			result = 31 * result + name.hashCode()
			result = 31 * result + statGains.contentHashCode()
			result = 31 * result + relationshipBars.hashCode()
			result = 31 * result + isRainbow.hashCode()
			result = 31 * result + numSpiritGaugesCanFill
			result = 31 * result + numSpiritGaugesReadyToBurst
			return result
		}
	}

	private val trainings: List<String> = listOf("Speed", "Stamina", "Power", "Guts", "Wit")
	private val trainingMap: MutableMap<String, TrainingOption> = mutableMapOf()
	var currentStatsMap: MutableMap<String, Int> = mutableMapOf(
		"Speed" to 0,
		"Stamina" to 0,
		"Power" to 0,
		"Guts" to 0,
		"Wit" to 0
	)
	private val blacklist: List<String> = SettingsHelper.getStringArraySetting("training", "trainingBlacklist")
	private val statPrioritizationRaw = SettingsHelper.getStringArraySetting("training", "statPrioritization")
	val statPrioritization: List<String> = if (!statPrioritizationRaw.isEmpty()) {
		statPrioritizationRaw
	} else {
		listOf("Speed", "Stamina", "Power", "Wit", "Guts")
	}
	private val maximumFailureChance: Int = SettingsHelper.getIntSetting("training", "maximumFailureChance")
	private val disableTrainingOnMaxedStat: Boolean = SettingsHelper.getBooleanSetting("training", "disableTrainingOnMaxedStat")
	private val focusOnSparkStatTarget: List<String> = SettingsHelper.getStringArraySetting("training", "focusOnSparkStatTarget")
	private val enableRainbowTrainingBonus: Boolean = SettingsHelper.getBooleanSetting("training", "enableRainbowTrainingBonus")
	private val preferredDistanceOverride: String = SettingsHelper.getStringSetting("training", "preferredDistanceOverride")
	private val enableRiskyTraining: Boolean = SettingsHelper.getBooleanSetting("training", "enableRiskyTraining")
	private val riskyTrainingMinStatGain: Int = SettingsHelper.getIntSetting("training", "riskyTrainingMinStatGain")
	private val riskyTrainingMaxFailureChance: Int = SettingsHelper.getIntSetting("training", "riskyTrainingMaxFailureChance")
	private val trainWitDuringFinale: Boolean = SettingsHelper.getBooleanSetting("training", "trainWitDuringFinale")
	private val manualStatCap: Int = SettingsHelper.getIntSetting("training", "manualStatCap")
	private val statTargetsByDistance: MutableMap<String, IntArray> = mutableMapOf(
		"Sprint" to intArrayOf(0, 0, 0, 0, 0),
		"Mile" to intArrayOf(0, 0, 0, 0, 0),
		"Medium" to intArrayOf(0, 0, 0, 0, 0),
		"Long" to intArrayOf(0, 0, 0, 0, 0)
	)
	var preferredDistance: String = ""
	var firstTrainingCheck = true
	private val currentStatCap: Int
		get() = if (disableTrainingOnMaxedStat) manualStatCap else 1200

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	/**
	 * Updates the preferred distance based on character aptitudes or manual override setting.
	 * 
	 * Priority order for automatic determination:
	 * 1. Check all distances for S aptitude first (Sprint → Mile → Medium → Long)
	 * 2. If no S aptitude found, check for A aptitude in same order
	 * 3. If no S or A aptitude found, default to "Medium"
	 */
	fun updatePreferredDistance() {
		MessageLog.i(TAG, "\n[TRAINING] Updating preferred distance...")
		
		// If manual override is set and not "Auto", use the manual value.
		if (preferredDistanceOverride != "Auto") {
			preferredDistance = preferredDistanceOverride
			MessageLog.i(TAG, "[TRAINING] Using manual override: $preferredDistance.")
			return
		}
		
		// Automatic determination based on aptitudes.
		val aptitudes = game.aptitudes.distance
		
		// First, check all distances for S aptitude.
		if (aptitudes.sprint == "S") {
			preferredDistance = "Sprint"
		} else if (aptitudes.mile == "S") {
			preferredDistance = "Mile"
		} else if (aptitudes.medium == "S") {
			preferredDistance = "Medium"
		} else if (aptitudes.long == "S") {
			preferredDistance = "Long"
		}
		// Then check for A aptitude if no S found.
		else if (aptitudes.sprint == "A") {
			preferredDistance = "Sprint"
		} else if (aptitudes.mile == "A") {
			preferredDistance = "Mile"
		} else if (aptitudes.medium == "A") {
			preferredDistance = "Medium"
		} else if (aptitudes.long == "A") {
			preferredDistance = "Long"
		}
		// Default fallback if no S or A aptitude found.
		else {
			preferredDistance = "Medium"
		}
		
		MessageLog.i(TAG, "[TRAINING] Determined preferred distance: $preferredDistance (Sprint: ${aptitudes.sprint}, Mile: ${aptitudes.mile}, Medium: ${aptitudes.medium}, Long: ${aptitudes.long})")
	}

	/**
	 * Sets up stat targets for different race distances by reading values from SQLite settings. These targets are used to determine training priorities based on the expected race distance.
	 */
	fun setStatTargetsByDistances() {
		val sprintSpeedTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingSprintStatTarget_speedStatTarget")
		val sprintStaminaTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingSprintStatTarget_staminaStatTarget")
		val sprintPowerTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingSprintStatTarget_powerStatTarget")
		val sprintGutsTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingSprintStatTarget_gutsStatTarget")
		val sprintWitTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingSprintStatTarget_witStatTarget")

		val mileSpeedTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingMileStatTarget_speedStatTarget")
		val mileStaminaTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingMileStatTarget_staminaStatTarget")
		val milePowerTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingMileStatTarget_powerStatTarget")
		val mileGutsTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingMileStatTarget_gutsStatTarget")
		val mileWitTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingMileStatTarget_witStatTarget")

		val mediumSpeedTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingMediumStatTarget_speedStatTarget")
		val mediumStaminaTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingMediumStatTarget_staminaStatTarget")
		val mediumPowerTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingMediumStatTarget_powerStatTarget")
		val mediumGutsTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingMediumStatTarget_gutsStatTarget")
		val mediumWitTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingMediumStatTarget_witStatTarget")

		val longSpeedTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingLongStatTarget_speedStatTarget")
		val longStaminaTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingLongStatTarget_staminaStatTarget")
		val longPowerTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingLongStatTarget_powerStatTarget")
		val longGutsTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingLongStatTarget_gutsStatTarget")
		val longWitTarget = SettingsHelper.getIntSetting("trainingStatTarget", "trainingLongStatTarget_witStatTarget")

		// Set the stat targets for each distance type.
		// Order: Speed, Stamina, Power, Guts, Wit
		statTargetsByDistance["Sprint"] = intArrayOf(sprintSpeedTarget, sprintStaminaTarget, sprintPowerTarget, sprintGutsTarget, sprintWitTarget)
		statTargetsByDistance["Mile"] = intArrayOf(mileSpeedTarget, mileStaminaTarget, milePowerTarget, mileGutsTarget, mileWitTarget)
		statTargetsByDistance["Medium"] = intArrayOf(mediumSpeedTarget, mediumStaminaTarget, mediumPowerTarget, mediumGutsTarget, mediumWitTarget)
		statTargetsByDistance["Long"] = intArrayOf(longSpeedTarget, longStaminaTarget, longPowerTarget, longGutsTarget, longWitTarget)
	}

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
		MessageLog.i(TAG, "[TRAINING] Starting Training process on ${game.printFormattedDate()}.")
        val startTime = System.currentTimeMillis()

		// Enter the Training screen.
		if (game.findAndTapImage("training_option", region = game.imageUtils.regionBottomHalf)) {
			// Acquire the percentages and stat gains for each training.
			game.wait(0.5)
			analyzeTrainings()

			if (trainingMap.isEmpty()) {
				// Check if we should force Wit training during the Finale instead of recovering energy.
				if (trainWitDuringFinale && game.currentDate.turnNumber in 73..75) {
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
	private fun analyzeTrainings(test: Boolean = false, singleTraining: Boolean = false) {
		if (singleTraining) MessageLog.i(TAG, "\n[TRAINING] Now starting process to analyze the training on screen.")
		else MessageLog.i(TAG, "\n[TRAINING] Now starting process to analyze all 5 Trainings.")

		// Acquire the position of the speed stat text.
		val (speedStatTextLocation, _) = game.imageUtils.findImage("stat_speed", tries = 1, region = game.imageUtils.regionBottomHalf)

		if (speedStatTextLocation != null) {
			// Perform a percentage check of Speed training to see if the bot has enough energy to do training. As a result, Speed training will be the one selected for the rest of the algorithm.
			if (!singleTraining && game.imageUtils.findImage("speed_training_header", tries = 1, region = game.imageUtils.regionTopHalf, suppressError = true).first == null) {
				game.findAndTapImage("training_speed", region = game.imageUtils.regionBottomHalf)
				game.wait(0.5)
			}

			val failureChance: Int = game.imageUtils.findTrainingFailureChance()
			if (failureChance == -1) {
				MessageLog.w(TAG, "Skipping training due to not being able to confirm whether or not the bot is at the Training screen.")
				return
			}

			// Check if failure chance is acceptable: either within regular threshold or within risky threshold (if enabled).
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

				// List to store all training analysis results for parallel processing.
				val analysisResults = mutableListOf<TrainingAnalysisResult>()

				// Iterate through every training that is not blacklisted.
				for ((index, training) in trainings.withIndex()) {
					if (!test && blacklist.getOrElse(index) { "" } == training) {
						MessageLog.i(TAG, "[TRAINING] Skipping $training training due to being blacklisted.")
						continue
					}

					if (singleTraining) {
						if (game.imageUtils.findImage("${training.lowercase()}_training_header", tries = 1, region = game.imageUtils.regionTopHalf, suppressError = true).first == null) {
							// Keep iterating until the current training is found.
							continue
						}
						MessageLog.i(TAG, "[TRAINING] The $training training is currently selected on the screen.")
					}

					// Select the Training to make it active except Speed Training since that is already selected at the start.
					val newX: Double = when (training) {
						"Stamina" -> {
							280.0
						}
						"Power" -> {
							402.0
						}
						"Guts" -> {
							591.0
						}
						"Wit" -> {
							779.0
						}
						else -> {
							0.0
						}
					}

					if (newX != 0.0 && !singleTraining) {
						if (game.imageUtils.isTablet) {
							if (training == "Stamina") {
								game.tap(
									speedStatTextLocation.x + game.imageUtils.relWidth((newX * 1.05).toInt()),
									speedStatTextLocation.y + game.imageUtils.relHeight((319 * 1.50).toInt()),
									"training_option_circular",
									ignoreWaiting = true
								)
							} else {
								game.tap(
									speedStatTextLocation.x + game.imageUtils.relWidth((newX * 1.36).toInt()),
									speedStatTextLocation.y + game.imageUtils.relHeight((319 * 1.50).toInt()),
									"training_option_circular",
									ignoreWaiting = true
								)
							}
						} else {
							game.tap(
								speedStatTextLocation.x + game.imageUtils.relWidth(newX.toInt()),
								speedStatTextLocation.y + game.imageUtils.relHeight(319),
								"training_option_circular",
								ignoreWaiting = true
							)
						}
						
						// Wait briefly for UI to update after tapping training button.
						game.wait(0.2)
					}

					// Get the Points and source Bitmap beforehand before starting the threads to make them safe for parallel processing.
                    val sourceBitmap = game.imageUtils.getSourceBitmap()
                    val skillPointsLocation = game.imageUtils.findImageWithBitmap("skill_points", sourceBitmap, region = game.imageUtils.regionMiddle)
                    val trainingSelectionLocation = game.imageUtils.findImageWithBitmap("training_failure_chance", sourceBitmap, region = game.imageUtils.regionBottomHalf)

					// Record start time for elapsed time measurement.
					val startTime = System.currentTimeMillis()

					// Unified approach: always use result object and start threads the same way.
					// Use CountDownLatch to run the operations in parallel to cut down on processing time.
					// Note: For parallel processing, Spirit Explosion Gauge is handled synchronously for Unity Cup, so latch count is 4.
					// For singleTraining, Spirit Explosion Gauge runs in a thread for Unity Cup, so latch count is 5.
					val latch = CountDownLatch(if (singleTraining && game.scenario == "Unity Cup") 5 else 4)

					// Create log message buffer for this training.
					val logMessages = ConcurrentLinkedQueue<String>()

					// Create result object to store analysis state.
					val result = TrainingAnalysisResult(
						training = training,
						index = index,
						logMessages = logMessages,
						latch = latch,
						startTime = startTime
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
                        Log.d(TAG, "Total time to analyze Spirit Explosion Gauge for $training: ${System.currentTimeMillis() - startTimeSpiritGauge}ms")
					}

					// Check if bot is still running before starting parallel threads.
					if (BotService.isRunning) {
                        // Thread 1: Determine stat gains.
                        Thread {
                            val startTimeStatGains = System.currentTimeMillis()
                            try {
                                val statGainResult = game.imageUtils.determineStatGainFromTraining(training, sourceBitmap, skillPointsLocation!!)
                                result.statGains = statGainResult.statGains
                                result.statGainRowValues = statGainResult.rowValuesMap
                            } catch (e: Exception) {
                                Log.e(TAG, "[ERROR] Error in determineStatGainFromTraining: ${e.stackTraceToString()}")
                                result.statGains = intArrayOf(0, 0, 0, 0, 0)
                                result.statGainRowValues = emptyMap()
                            } finally {
                                latch.countDown()
                                val elapsedTime = System.currentTimeMillis() - startTimeStatGains
                                Log.d(TAG, "Total time to determine stat gains for $training: ${elapsedTime}ms")
                                if (!singleTraining) {
                                    logMessages.offer("[TRAINING] [$training] Stat gains analysis completed in ${elapsedTime}ms")
                                }
                            }
                        }.start()

                        // Thread 2: Find failure chance.
                        Thread {
                            val startTimeFailureChance = System.currentTimeMillis()
                            try {
                                result.failureChance = game.imageUtils.findTrainingFailureChance(sourceBitmap, trainingSelectionLocation!!)
                            } catch (e: Exception) {
                                MessageLog.e(TAG, "Error in findTrainingFailureChance: ${e.stackTraceToString()}")
                                result.failureChance = -1
                            } finally {
                                latch.countDown()
                                val elapsedTime = System.currentTimeMillis() - startTimeFailureChance
                                Log.d(TAG, "Total time to determine failure chance for $training: ${elapsedTime}ms")
                                if (!singleTraining) {
                                    logMessages.offer("[TRAINING] [$training] Failure chance analysis completed in ${elapsedTime}ms")
                                }
                            }
                        }.start()

                        // Thread 3: Analyze relationship bars.
                        Thread {
                            val startTimeRelationshipBars = System.currentTimeMillis()
                            try {
                                result.relationshipBars = game.imageUtils.analyzeRelationshipBars(sourceBitmap)
                            } catch (e: Exception) {
                                Log.e(TAG, "[ERROR] Error in analyzeRelationshipBars: ${e.stackTraceToString()}")
                                result.relationshipBars = arrayListOf()
                            } finally {
                                latch.countDown()
                                val elapsedTime = System.currentTimeMillis() - startTimeRelationshipBars
                                Log.d(TAG, "Total time to analyze relationship bars for $training: ${elapsedTime}ms")
                                if (!singleTraining) {
                                    logMessages.offer("[TRAINING] [$training] Relationship bars analysis completed in ${elapsedTime}ms")
                                }
                            }
                        }.start()

                        // Thread 4: Detect rainbow training.
                        Thread {
                            val startTimeRainbow = System.currentTimeMillis()
                            try {
                                result.isRainbow = game.imageUtils.findImageWithBitmap("training_rainbow", sourceBitmap, region = game.imageUtils.regionBottomHalf, suppressError = true) != null
                            } catch (e: Exception) {
                                Log.e(TAG, "[ERROR] Error in rainbow detection: ${e.stackTraceToString()}")
                                result.isRainbow = false
                            } finally {
                                latch.countDown()
                                val elapsedTime = System.currentTimeMillis() - startTimeRainbow
                                Log.d(TAG, "Total time to detect rainbow for $training: ${elapsedTime}ms")
                                if (!singleTraining) {
                                    logMessages.offer("[TRAINING] [$training] Rainbow detection completed in ${elapsedTime}ms")
                                }
                            }
                        }.start()

                        // Thread 5: Analyze Spirit Explosion Gauges (Unity Cup only, singleTraining mode only).
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
                                    Log.d(TAG, "Total time to analyze Spirit Explosion Gauge for $training: ${System.currentTimeMillis() - startTimeSpiritGauge}ms")
                                }
                            }.start()
                        }
                    }

					// Branch on singleTraining vs parallel processing.
					if (singleTraining) {
						// For singleTraining, wait here and process immediately.
						try {
							latch.await(3, TimeUnit.SECONDS)
						} catch (e: InterruptedException) {
							Log.e(TAG, "[ERROR] Parallel training analysis timed out")
							Thread.currentThread().interrupt()
						} finally {
							val elapsedTime = System.currentTimeMillis() - startTime
							Log.d(TAG, "Total time for $training training analysis: ${elapsedTime}ms")
							// Log stat gain results sequentially after threads complete to ensure correct order.
							logStatGainResults(training, result.statGains, result.statGainRowValues)
							MessageLog.i(TAG, "All 5 stat regions processed for $training training. Results: ${result.statGains.toList()}")
						}

						// Check if risky training logic should apply based on main stat gain.
						val mainStatGain = result.statGains[result.index]
						val effectiveFailureChance = if (enableRiskyTraining && mainStatGain >= riskyTrainingMinStatGain) {
							riskyTrainingMaxFailureChance
						} else {
							maximumFailureChance
						}
						
						// Filter out trainings that exceed the effective failure chance threshold.
						if (!test && result.failureChance > effectiveFailureChance) {
							if (enableRiskyTraining && mainStatGain >= riskyTrainingMinStatGain) {
								MessageLog.i(TAG, "[TRAINING] Skipping $training training due to failure chance (${result.failureChance}%) exceeding risky threshold (${riskyTrainingMaxFailureChance}%) despite high main stat gain of $mainStatGain.")
							} else {
								MessageLog.i(TAG, "[TRAINING] Skipping $training training due to failure chance (${result.failureChance}%) exceeding threshold (${maximumFailureChance}%).")
							}
							continue
						}

						val newTraining = TrainingOption(
							name = result.training,
							statGains = result.statGains,
							failureChance = result.failureChance,
							relationshipBars = result.relationshipBars,
							isRainbow = result.isRainbow,
							numSpiritGaugesCanFill = result.numSpiritGaugesCanFill,
							numSpiritGaugesReadyToBurst = result.numSpiritGaugesReadyToBurst
						)
						trainingMap[result.training] = newTraining
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
								Log.e(TAG, "[ERROR] Parallel training analysis timed out for ${result.training}")
								Thread.currentThread().interrupt()
							} finally {
								// Only log and process if bot is still running.
								if (BotService.isRunning) {
									val elapsedTime = System.currentTimeMillis() - result.startTime
									Log.d(TAG, "Total time for ${result.training} training analysis: ${elapsedTime}ms")
									result.logMessages.offer("[TRAINING] [${result.training}] All analysis threads completed. Total time: ${elapsedTime}ms")
									result.logMessages.offer("[TRAINING] [${result.training}] All 5 stat regions processed. Results: ${result.statGains.toList()}")
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
					
					for (result in analysisResults) {
						// Output buffered log messages for this training.
						while (result.logMessages.isNotEmpty()) {
							MessageLog.i(TAG, result.logMessages.poll())
						}

						// Log stat gain results sequentially after threads complete to ensure correct order.
						logStatGainResults(result.training, result.statGains, result.statGainRowValues)

						// Check if risky training logic should apply based on main stat gain.
						val mainStatGain = result.statGains[result.index]
						val effectiveFailureChance = if (enableRiskyTraining && mainStatGain >= riskyTrainingMinStatGain) {
							riskyTrainingMaxFailureChance
						} else {
							maximumFailureChance
						}
						
						// Filter out trainings that exceed the effective failure chance threshold.
						if (!test && result.failureChance > effectiveFailureChance) {
							if (enableRiskyTraining && mainStatGain >= riskyTrainingMinStatGain) {
								MessageLog.i(TAG, "[TRAINING] Skipping ${result.training} training due to failure chance (${result.failureChance}%) exceeding risky threshold (${riskyTrainingMaxFailureChance}%) despite high main stat gain of $mainStatGain.")
							} else {
								MessageLog.i(TAG, "[TRAINING] Skipping ${result.training} training due to failure chance (${result.failureChance}%) exceeding threshold (${maximumFailureChance}%).")
							}
							continue
						}

						val newTraining = TrainingOption(
							name = result.training,
							statGains = result.statGains,
							failureChance = result.failureChance,
							relationshipBars = result.relationshipBars,
							isRainbow = result.isRainbow,
							numSpiritGaugesCanFill = result.numSpiritGaugesCanFill,
							numSpiritGaugesReadyToBurst = result.numSpiritGaugesReadyToBurst
						)
						trainingMap[result.training] = newTraining
					}
				}

				if (singleTraining) {
					MessageLog.i(TAG, "[TRAINING] Process to analyze the singular Training complete.")
				} else {
					MessageLog.i(TAG, "[TRAINING] Process to analyze all 5 Trainings complete.")
				}
			} else {
				// Clear the Training map if the bot failed to have enough energy to conduct the training.
				MessageLog.i(TAG, "[TRAINING] $failureChance% is not within acceptable range of ${maximumFailureChance}%. Proceeding to recover energy.")
				trainingMap.clear()
			}
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
	 * @return The name of the recommended training option, or empty string if no suitable option found.
	 */
	private fun recommendTraining(): String {
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

			// 1. Highest Priority: Trainings with Spirit Explosion Gauges ready to burst.
            var score = 0.0
			if (training.numSpiritGaugesReadyToBurst > 0) {
				// Score increases with number of gauges ready to burst.
				score += 1000.0 + (training.numSpiritGaugesReadyToBurst * 1000.0)
				MessageLog.i(TAG, "[TRAINING] ${training.name} Training has ${training.numSpiritGaugesReadyToBurst} Spirit Explosion Gauge(s) ready to burst. Highest priority.")
				
				// Facility preference bonuses for bursting.
				when (training.name) {
					"Speed" -> score += 500.0 // Best for increased speed stat gains.
					"Wit" -> score += 500.0 // Best for energy recovery and slightly increased speed stat gain.
					"Stamina", "Power" -> {
						// Can be bursted if lacking stats.
						val statIndex = trainings.indexOf(training.name)
						val currentStat = currentStatsMap.getOrDefault(training.name, 0)
						val target = statTargetsByDistance[preferredDistance] ?: intArrayOf(600, 600, 600, 300, 300)
						val targetStat = target.getOrElse(statIndex) { 600 }
						if (currentStat < targetStat * 0.8) {
							score += 300.0
						}
					}
					"Guts" -> {
						// Guts is not ideal, but can be worth it if building up gauges to max them out for bursting.
						if (training.numSpiritGaugesCanFill >= 2) {
							score += 200.0 // Building up multiple gauges to allow for bursting.
						} else {
							score -= 100.0 // Not ideal without building up multiple gauges.
						}
					}
				}

				// Bonus for rainbow training while bursting.
				if (training.isRainbow) {
					score += 200.0
					MessageLog.i(TAG, "[TRAINING] Adding some score for ${training.name} Training for being a rainbow training.")
				}
			}

			// 2. Second Priority: Trainings that can fill Spirit Explosion Gauges (not at 100% yet).
			if (training.numSpiritGaugesCanFill > 0) {
				// Score increases with number of gauges that can be filled.
				// Each gauge fills by 25% per training execution.
				score += 1000.0 + (training.numSpiritGaugesCanFill * 200.0)
				MessageLog.i(TAG, "[TRAINING] ${training.name} Training can fill ${training.numSpiritGaugesCanFill} Spirit Explosion Gauge(s).")

				// Early game: If gauges can be filled for deprioritized stat trainings, ignore stat prioritization.
				val isEarlyGame = game.currentDate.year < 2
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
			val statIndex = trainings.indexOf(training.name)
			val statGain = training.statGains.getOrElse(statIndex) { 0 }
			score += statGain.toDouble() * 0.1
			MessageLog.i(TAG, "[TRAINING] ${training.name} Training stat gain contribution: ${statGain}.")

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
		fun calculateStatEfficiencyScore(training: TrainingOption, target: IntArray): Double {
			var score = 0.0
			
			for ((index, stat) in trainings.withIndex()) {
				val currentStat = currentStatsMap.getOrDefault(stat, 0)
				val targetStat = target.getOrElse(index) { 0 }
				val statGain = training.statGains.getOrElse(index) { 0 }
				
				if (statGain > 0 && targetStat > 0) {
					val priorityIndex = statPrioritization.indexOf(stat)
					
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
					val highestPriorityStat = statPrioritization.firstOrNull() ?: stat
					val highestPriorityIndex = trainings.indexOf(highestPriorityStat)
					val highestPriorityCompletion = if (highestPriorityIndex != -1) {
						val hpCurrent = currentStatsMap.getOrDefault(highestPriorityStat, 0)
						val hpTarget = target.getOrElse(highestPriorityIndex) { 1 }
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
					val isMainStat = training.name == stat
					val mainStatBonus = if (isMainStat && statGain >= 30) {
						2.0
					} else {
						1.0
					}
					
                    // Spark bonus: Prioritize training sessions for 3* sparks for selected stats below 600 if the setting is enabled.
                    val isSparkStat = stat in focusOnSparkStatTarget
                    val canTriggerSpark = currentStat < 600
                    val sparkBonus = if (isSparkStat && canTriggerSpark) {
                        MessageLog.i(TAG, "[TRAINING] $stat is at $currentStat (< 600). Prioritizing this training for potential spark event to get above 600.")
                        2.5
                    } else {
                        1.0
                    }
                    
                    if (game.debugMode) {
                        val bonusNote = if (isMainStat && statGain >= 30) " [HIGH MAIN STAT]" else ""
                        val sparkNote = if (isSparkStat && canTriggerSpark) " [SPARK PRIORITY]" else ""
						MessageLog.d(
                            TAG,
                            "$stat: gain=$statGain, completion=${game.decimalFormat.format(completionPercent)}%, " +
							"ratioMult=${game.decimalFormat.format(ratioMultiplier)}, priorityMult=${game.decimalFormat.format(priorityMultiplier)}$bonusNote$sparkNote",
						)
					} else {
						Log.d(TAG, "$stat: gain=$statGain, completion=${game.decimalFormat.format(completionPercent)}%, ratioMult=$ratioMultiplier, priorityMult=$priorityMultiplier")
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
					val earlyGameBonus = if (game.currentDate.year == 1 || game.currentDate.phase == "Pre-Debut") 1.3 else 1.0

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

			// Don't score for stats that are maxed or would be maxed.
			if ((disableTrainingOnMaxedStat && currentStatsMap[training.name]!! >= currentStatCap) ||
				(currentStatsMap.getOrDefault(training.name, 0) + training.statGains[trainings.indexOf(training.name)] >= currentStatCap)) {
				return 0.0
			}

			val target = statTargetsByDistance[preferredDistance] ?: intArrayOf(600, 600, 600, 300, 300)

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
			val rainbowMultiplier = if (training.isRainbow && game.currentDate.year >= 2) {
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
		val best = if (game.scenario == "Unity Cup" && game.currentDate.year < 3) {
            // Unity Cup (Year < 3): Use Spirit Explosion Gauge priority system.
			trainingMap.values.maxByOrNull { scoreUnityCupTraining(it) }
		} else if (game.currentDate.phase == "Pre-Debut" || game.currentDate.year == 1) {
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

		return best?.name ?: (trainingMap.keys.firstOrNull { it !in blacklist } ?: "")
	}

	/**
	 * Execute the training with the highest stat weight.
	 */
	private fun executeTraining() {
		MessageLog.i(TAG, "[TRAINING] Now starting process to execute training...")
		val trainingSelected = recommendTraining()

		if (trainingSelected != "") {
			printTrainingMap()
			MessageLog.i(TAG, "[TRAINING] Executing the $trainingSelected Training.")
			game.findAndTapImage("training_${trainingSelected.lowercase()}", region = game.imageUtils.regionBottomHalf, taps = 3)
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
			MessageLog.i(TAG, "$name Training stat gains: ${training.statGains.contentToString()}, failure chance: ${training.failureChance}%, rainbow: ${training.isRainbow}.")
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
	private fun logStatGainResults(trainingName: String, statGains: IntArray, rowValuesMap: Map<Int, List<Int>>) {
		val statNames = listOf("Speed", "Stamina", "Power", "Guts", "Wit")
		// Define a mapping of training types to their stat indices.
		val trainingToStatIndices = mapOf(
			"Speed" to listOf(0, 2),
			"Stamina" to listOf(1, 3),
			"Power" to listOf(1, 2),
			"Guts" to listOf(0, 2, 3),
			"Wit" to listOf(0, 4)
		)

		// Log all stat results sequentially in order (Speed, Stamina, Power, Guts, Wit) for consistent logging.
		statNames.forEachIndexed { index, statName ->
			val validIndices = trainingToStatIndices[trainingName] ?: emptyList()
			if (index in validIndices && statGains[index] >= 0) {
				val rowValues = rowValuesMap[index]
				if (rowValues != null) {
					// Unity Cup case: log with row values.
					MessageLog.d(TAG, "[INFO] $statName final constructed values from $trainingName training: $rowValues, sum: ${statGains[index]}.")
				} else {
					// Single row case: log simple value.
					MessageLog.d(TAG, "[INFO] $statName final constructed value from $trainingName training: ${statGains[index]}.")
				}
			}
		}
	}
}