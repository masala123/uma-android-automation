package com.steve1316.uma_android_automation.bot

import android.util.Log
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.automation_library.data.SharedData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.intArrayOf

class Training(private val game: Game) {
	private val tag: String = "[${MainActivity.loggerTag}]Training"

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	data class TrainingOption(
		val name: String,
		val statGains: IntArray,
		val failureChance: Int,
		val relationshipBars: ArrayList<CustomImageUtils.BarFillResult>,
		val isRainbow: Boolean
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

			return true
		}

		override fun hashCode(): Int {
			var result = failureChance
			result = 31 * result + name.hashCode()
			result = 31 * result + statGains.contentHashCode()
			result = 31 * result + relationshipBars.hashCode()
			result = 31 * result + isRainbow.hashCode()
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
	private val focusOnSparkStatTarget: Boolean = SettingsHelper.getBooleanSetting("training", "focusOnSparkStatTarget")
	private val enableRainbowTrainingBonus: Boolean = SettingsHelper.getBooleanSetting("training", "enableRainbowTrainingBonus")
	private val preferredDistanceOverride: String = SettingsHelper.getStringSetting("training", "preferredDistanceOverride")
	private val enableRiskyTraining: Boolean = SettingsHelper.getBooleanSetting("training", "enableRiskyTraining")
	private val riskyTrainingMinStatGain: Int = SettingsHelper.getIntSetting("training", "riskyTrainingMinStatGain")
	private val riskyTrainingMaxFailureChance: Int = SettingsHelper.getIntSetting("training", "riskyTrainingMaxFailureChance")
	private val statTargetsByDistance: MutableMap<String, IntArray> = mutableMapOf(
		"Sprint" to intArrayOf(0, 0, 0, 0, 0),
		"Mile" to intArrayOf(0, 0, 0, 0, 0),
		"Medium" to intArrayOf(0, 0, 0, 0, 0),
		"Long" to intArrayOf(0, 0, 0, 0, 0)
	)
	var preferredDistance: String = ""
	var firstTrainingCheck = true
	private val currentStatCap = 1200

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// Public Training Methods
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
		game.printToLog("\n[TRAINING] Updating preferred distance...")
		
		// If manual override is set and not "Auto", use the manual value.
		if (preferredDistanceOverride != "Auto") {
			preferredDistance = preferredDistanceOverride
			game.printToLog("[TRAINING] Using manual override: $preferredDistance.")
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
		
		game.printToLog("[TRAINING] Determined preferred distance: $preferredDistance (Sprint: ${aptitudes.sprint}, Mile: ${aptitudes.mile}, Medium: ${aptitudes.medium}, Long: ${aptitudes.long})")
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
		game.printToLog("\n[TEST] Now beginning Single Training OCR test on the Training screen for the current training on display.")
		game.printToLog("[TEST] Note that this test is dependent on having the correct scale.")
		analyzeTrainings(test = true, singleTraining = true)
		printTrainingMap()
	}

	/**
	 * Handles the test to perform OCR on all 5 trainings on display for stat gains and failure chances.
	 */
	fun startComprehensiveTrainingOCRTest() {
		game.printToLog("\n[TEST] Now beginning Comprehensive Training OCR test on the Training screen for all 5 trainings on display.")
		game.printToLog("[TEST] Note that this test is dependent on having the correct scale.")
		analyzeTrainings(test = true)
		printTrainingMap()
	}

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	/**
	 * The entry point for handling Training.
	 */
	fun handleTraining() {
		game.printToLog("\n[TRAINING] Starting Training process...")

		// Enter the Training screen.
		if (game.findAndTapImage("training_option", region = game.imageUtils.regionBottomHalf)) {
			// Acquire the percentages and stat gains for each training.
			game.wait(0.5)
			analyzeTrainings()

			if (trainingMap.isEmpty()) {
				game.printToLog("[TRAINING] Backing out of Training and returning on the Main screen.")
				game.findAndTapImage("back", region = game.imageUtils.regionBottomHalf)
				game.wait(1.0)

				if (game.checkMainScreen()) {
					game.printToLog("[TRAINING] Will recover energy due to either failure chance was high enough to do so or no failure chances were detected via OCR.")
					game.recoverEnergy()
				} else {
					game.printToLog("[ERROR] Could not head back to the Main screen in order to recover energy.")
				}
			} else {
				// Now select the training option with the highest weight.
				executeTraining()

				firstTrainingCheck = false
			}

			game.racing.raceRepeatWarningCheck = false
			game.printToLog("\n[TRAINING] Training process completed.")
		} else {
			game.printToLog("[ERROR] Cannot start the Training process. Moving on...", isError = true)
		}
	}

	/**
	 * Analyze all 5 Trainings for their details including stat gains, relationship bars, etc.
	 *
	 * @param test Flag that forces the failure chance through even if it is not in the acceptable range for testing purposes.
	 * @param singleTraining Flag that forces only singular training analysis for the current training on the screen.
	 */
	private fun analyzeTrainings(test: Boolean = false, singleTraining: Boolean = false) {
		if (singleTraining) game.printToLog("\n[TRAINING] Now starting process to analyze the training on screen.")
		else game.printToLog("\n[TRAINING] Now starting process to analyze all 5 Trainings.")

		// Acquire the position of the speed stat text.
		val (speedStatTextLocation, _) = if (game.campaign == "Ao Haru") {
			game.imageUtils.findImage("aoharu_stat_speed", tries = 1, region = game.imageUtils.regionBottomHalf)
		} else {
			game.imageUtils.findImage("stat_speed", tries = 1, region = game.imageUtils.regionBottomHalf)
		}

		if (speedStatTextLocation != null) {
			// Perform a percentage check of Speed training to see if the bot has enough energy to do training. As a result, Speed training will be the one selected for the rest of the algorithm.
			if (!singleTraining && game.imageUtils.findImage("speed_training_header", tries = 1, region = game.imageUtils.regionTopHalf, suppressError = true).first == null) {
				game.findAndTapImage("training_speed", region = game.imageUtils.regionBottomHalf)
				game.wait(0.5)
			}

			val failureChance: Int = game.imageUtils.findTrainingFailureChance()
			if (failureChance == -1) {
				game.printToLog("[WARNING] Skipping training due to not being able to confirm whether or not the bot is at the Training screen.")
				return
			}

			if (test || failureChance <= maximumFailureChance) {
				if (!test) game.printToLog("[TRAINING] $failureChance% within acceptable range of ${maximumFailureChance}%. Proceeding to acquire all other percentages and total stat increases...")

				// Iterate through every training that is not blacklisted.
				for ((index, training) in trainings.withIndex()) {
					if (!test && blacklist.getOrElse(index) { "" } == training) {
						game.printToLog("[TRAINING] Skipping $training training due to being blacklisted.")
						continue
					}

					if (singleTraining) {
						val trainingHeader = "${training.lowercase()}_training_header"
						if (game.imageUtils.findImage(trainingHeader, tries = 1, region = game.imageUtils.regionTopHalf, suppressError = true).first == null) {
							// Keep iterating until the current training is found.
							continue
						}
						game.printToLog("[TRAINING] The $training training is currently selected on the screen.")
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
					}

					// Update the object in the training map.
					// Use CountDownLatch to run the 4 operations in parallel to cut down on processing time.
					val latch = CountDownLatch(4)

					// Variables to store results from parallel threads.
					var statGains: IntArray = intArrayOf()
					var failureChance: Int = -1
					var relationshipBars: ArrayList<CustomImageUtils.BarFillResult> = arrayListOf()
					var isRainbow = false

					// Get the Points and source Bitmap beforehand before starting the threads to make them safe for parallel processing.
					val (skillPointsLocation, sourceBitmap) = game.imageUtils.findImage("skill_points", tries = 1, region = game.imageUtils.regionMiddle)
					val (trainingSelectionLocation, _) = game.imageUtils.findImage("training_failure_chance", tries = 1, region = game.imageUtils.regionBottomHalf)

					// Thread 1: Determine stat gains.
					Thread {
						try {
							statGains = game.imageUtils.determineStatGainFromTraining(training, sourceBitmap, skillPointsLocation!!)
						} catch (e: Exception) {
							Log.e(tag, "[ERROR] Error in determineStatGainFromTraining: ${e.stackTraceToString()}")
							statGains = intArrayOf(0, 0, 0, 0, 0)
						} finally {
							latch.countDown()
						}
					}.start()

					// Thread 2: Find failure chance.
					Thread {
						try {
							failureChance = game.imageUtils.findTrainingFailureChance(sourceBitmap, trainingSelectionLocation!!)
						} catch (e: Exception) {
							game.printToLog("[ERROR] Error in findTrainingFailureChance: ${e.stackTraceToString()}", isError = true)
							failureChance = -1
						} finally {
							latch.countDown()
						}
					}.start()

					// Thread 3: Analyze relationship bars.
					Thread {
						try {
							relationshipBars = game.imageUtils.analyzeRelationshipBars(sourceBitmap)
						} catch (e: Exception) {
							Log.e(tag, "[ERROR] Error in analyzeRelationshipBars: ${e.stackTraceToString()}")
							relationshipBars = arrayListOf()
						} finally {
							latch.countDown()
						}
					}.start()

					// Thread 4: Detect rainbow training.
                    Thread {
                        try {
                            isRainbow = game.imageUtils.findImage("training_rainbow", tries = 2, confidence = 0.9, suppressError = true, region = game.imageUtils.regionBottomHalf).first != null
                        } catch (e: Exception) {
                            Log.e(tag, "[ERROR] Error in rainbow detection: ${e.stackTraceToString()}")
                            isRainbow = false
                        } finally {
                            latch.countDown()
                        }
                    }.start()
					try {
						latch.await(10, TimeUnit.SECONDS)
					} catch (_: InterruptedException) {
						Log.e(tag, "[ERROR] Parallel training analysis timed out")
					} finally {
						game.printToLog("[INFO] All 5 stat regions processed for $training training. Results: ${statGains.toList()}", tag = tag)
					}

					// Check if risky training logic should apply based on main stat gain.
					// The main stat gain for each training type corresponds to its index in the statGains array.
					val mainStatGain = statGains[index]
					val effectiveFailureChance = if (enableRiskyTraining && mainStatGain >= riskyTrainingMinStatGain) {
						riskyTrainingMaxFailureChance
					} else {
						maximumFailureChance
					}
					
					// Filter out trainings that exceed the effective failure chance threshold.
					if (!test && failureChance > effectiveFailureChance) {
						if (enableRiskyTraining && mainStatGain >= riskyTrainingMinStatGain) {
							game.printToLog("[TRAINING] Skipping $training training due to failure chance ($failureChance%) exceeding risky threshold (${riskyTrainingMaxFailureChance}%) despite high main stat gain of $mainStatGain.")
						} else {
							game.printToLog("[TRAINING] Skipping $training training due to failure chance ($failureChance%) exceeding threshold (${maximumFailureChance}%).")
						}
						continue
					}

					val newTraining = TrainingOption(
						name = training,
						statGains = statGains,
						failureChance = failureChance,
						relationshipBars = relationshipBars,
                        isRainbow = isRainbow
					)
					trainingMap.put(training, newTraining)
					if (singleTraining) {
						break
					}
				}

				if (singleTraining) {
					game.printToLog("[TRAINING] Process to analyze the singular Training complete.")
				} else {
					game.printToLog("[TRAINING] Process to analyze all 5 Trainings complete.")
				}
			} else {
				// Clear the Training map if the bot failed to have enough energy to conduct the training.
				game.printToLog("[TRAINING] $failureChance% is not within acceptable range of ${maximumFailureChance}%. Proceeding to recover energy.")
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
	 * - Uses ratio-based scoring via `scoreStatTraining()`
	 * - Scores based on completion percentage (currentStat / targetStat)
	 * - Trains stats furthest behind their target ratio
	 * - Priority order only breaks ties when completion percentages are similar
	 *
	 * The scoring system considers multiple factors:
	 * - **Ratio Completion:** How far each stat is toward its target percentage (primary driver)
	 * - **Priority Tiebreaker:** Only matters when stats have similar completion percentages
	 * - **Main Stat Bonus:** High gains on main stat get bonus (likely undetected rainbow)
	 * - **Rainbow Detection:** Heavily favored for overall ratio balance
	 * - **Late Game Stamina:** Ensures 600+ stamina in Year 3
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
			game.printToLog("\n[TRAINING] Starting process to score ${training.name} Training with a focus on building relationship bars.")

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

			game.printToLog("[TRAINING] ${training.name} Training has a score of ${game.decimalFormat.format(score)} with a focus on building relationship bars.")
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
					
					// Special case: Ensure Stamina is at least 600 in late game.
					val isLateGame = game.currentDate.year == 3
					val isStamina = stat == "Stamina"
					val staminaBelowMinimum = isStamina && currentStat < 600
					val lateGameStaminaBonus = if (isLateGame && staminaBelowMinimum) {
						game.printToLog("[TRAINING] Stamina of $currentStat is currently less than 600 so bringing its score higher for Senior Year.", tag = tag)
						2.0
					} else 1.0
					
					if (game.debugMode) {
						val bonusNote = if (isMainStat && statGain >= 30) " [HIGH MAIN STAT]" else ""
						val staminaNote = if (isLateGame && staminaBelowMinimum) " [LATE GAME MINIMUM]" else ""
						game.printToLog("[DEBUG] $stat: gain=$statGain, completion=${game.decimalFormat.format(completionPercent)}%, " +
							"ratioMult=${game.decimalFormat.format(ratioMultiplier)}, priorityMult=${game.decimalFormat.format(priorityMultiplier)}$bonusNote$staminaNote",
							tag = tag
						)
					} else {
						Log.d(tag, "[DEBUG] $stat: gain=$statGain, completion=${game.decimalFormat.format(completionPercent)}%, " +
							"ratioMult=$ratioMultiplier, priorityMult=$priorityMultiplier")
					}
					
					// Calculate final score for this stat.
					var statScore = statGain.toDouble()
					statScore *= ratioMultiplier
					statScore *= priorityMultiplier
					statScore *= mainStatBonus
					statScore *= lateGameStaminaBonus
					
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
                game.printToLog("[TRAINING] Skill hint(s) detected for ${training.name} Training.", tag = tag)
            }
			score += 10.0 * skillHintLocations.size

			return score.coerceIn(0.0, 100.0)
		}

		/**
		 * Performs comprehensive scoring of training options using ratio-based evaluation.
		 *
		 * This scoring system combines multiple components:
		 * - Stat efficiency: Ratio completion toward target distribution
		 * - Relationship building: Value of friendship bar progress
		 * - Context bonuses: Skill hints and situational bonuses
		 * - Rainbow multiplier: Mulitplies the score based on the existence of a rainbow training and whether rainbow training bonus is enabled.
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A score (0-100) representing overall training value.
		 */
		fun scoreStatTraining(training: TrainingOption): Double {
			if (training.name in blacklist) return 0.0

			// Don't score for stats that are maxed or would be maxed.
			if ((disableTrainingOnMaxedStat && currentStatsMap[training.name]!! >= currentStatCap) ||
				(currentStatsMap.getOrDefault(training.name, 0) + training.statGains[trainings.indexOf(training.name)] >= currentStatCap)) {
				return 0.0
			}

			game.printToLog("\n[TRAINING] Starting scoring for ${training.name} Training.")

			val target = statTargetsByDistance[preferredDistance] ?: intArrayOf(600, 600, 600, 300, 300)

			var totalScore = 0.0

			// 1. Stat Efficiency scoring
			val statScore = calculateStatEfficiencyScore(training, target)

			// 2. Friendship scoring
			val relationshipScore = calculateRelationshipScore(training)

			// 3. Misc-aware scoring
			val miscScore = calculateMiscScore(training)

			// Define scoring weights based on relationship bars presence.
			val statWeight = if (training.relationshipBars.isNotEmpty()) 0.4 else 0.6
			val relationshipWeight = if (training.relationshipBars.isNotEmpty()) 0.3 else 0.0
			val miscWeight = 0.4

			// Calculate weighted total score.
			totalScore += statScore * statWeight
			totalScore += relationshipScore * relationshipWeight
			totalScore += miscScore * miscWeight

			// 4. Rainbow training multiplier (Year 2+ only).
			// Rainbow is heavily favored because it improves overall ratio balance.
			val rainbowMultiplier = if (training.isRainbow && game.currentDate.year >= 2) {
				if (enableRainbowTrainingBonus) {
                    game.printToLog("[TRAINING] ${training.name} Training is detected as a rainbow training.", tag = tag)
					2.0
				} else {
                    game.printToLog("[TRAINING] ${training.name} Training is detected as a rainbow training, but rainbow training bonus is not enabled.", tag = tag)
					1.5
				}
			} else {
                game.printToLog("[TRAINING] ${training.name} Training is not detected as a rainbow training.", tag = tag)
				1.0
			}

			// Apply rainbow multiplier to total score.
			totalScore *= rainbowMultiplier

			game.printToLog(
				"[TRAINING] Scores | Current Stat: ${currentStatsMap[training.name]}, Target Stat: ${target[trainings.indexOf(training.name)]}, " +
					"Stat Efficiency: ${game.decimalFormat.format(statScore)}, Relationship: ${game.decimalFormat.format(relationshipScore)}, " +
					"Misc: ${game.decimalFormat.format(miscScore)}, Rainbow Multiplier: ${game.decimalFormat.format(rainbowMultiplier)}"
			)

			val finalScore = totalScore.coerceIn(0.0, 100.0)

			game.printToLog("[TRAINING] Enhanced final score for ${training.name} Training: ${game.decimalFormat.format(finalScore)}/100.0")

			return finalScore
		}

		/**
		 * Calculates raw training score without normalization.
		 *
		 * This function contains the same logic as scoreStatTraining but returns raw scores
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
                    game.printToLog("[TRAINING] ${training.name} Training is detected as a rainbow training. Adding multiplier to score.", tag = tag)
					2.0
				} else {
                    game.printToLog("[TRAINING] ${training.name} Training is detected as a rainbow training, but rainbow training bonus is not enabled.", tag = tag)
					1.5
				}
			} else {
				1.0
			}

			// Apply rainbow multiplier to total score.
			totalScore *= rainbowMultiplier

			return totalScore.coerceAtLeast(0.0)
		}

		// Decide which scoring function to use based on the current phase or year.
		// Junior Year will focus on building relationship bars.
		val best = if (game.currentDate.phase == "Pre-Debut" || game.currentDate.year == 1) {
			trainingMap.values.maxByOrNull { scoreFriendshipTraining(it) }
		} else {
			// For Year 2+, calculate all scores first, then normalize based on actual maximum.
			val trainingScores = trainingMap.values.map { training ->
				training to calculateRawTrainingScore(training)
			}.toMap()
			
			val maxScore = trainingScores.values.maxOrNull() ?: 0.0
			
			// Normalize scores to 0-100 scale based on actual maximum.
			val normalizedScores = trainingScores.mapValues { (_, score) ->
				if (maxScore > 0) (score / maxScore * 100.0).coerceIn(0.0, 100.0) else 0.0
			}
			
			// Log normalized scores for debugging.
			normalizedScores.forEach { (training, score) ->
				game.printToLog("[TRAINING] ${training.name}: ${game.decimalFormat.format(score)}/100")
			}
			
			trainingScores.keys.maxByOrNull { normalizedScores[it] ?: 0.0 }
		}

		return best?.name ?: (trainingMap.keys.firstOrNull { it !in blacklist } ?: "")
	}

	/**
	 * Execute the training with the highest stat weight.
	 */
	private fun executeTraining() {
		game.printToLog("\n********************")
		game.printToLog("[TRAINING] Now starting process to execute training...")
		val trainingSelected = recommendTraining()

		if (trainingSelected != "") {
			printTrainingMap()
			game.printToLog("[TRAINING] Executing the $trainingSelected Training.")
			game.findAndTapImage("training_${trainingSelected.lowercase()}", region = game.imageUtils.regionBottomHalf, taps = 3)
			game.printToLog("[TRAINING] Process to execute training completed.")
		} else {
			game.printToLog("[TRAINING] Conditions have not been met so training will not be done.")
		}

		game.printToLog("********************\n")

		// Now reset the Training map.
		trainingMap.clear()
	}

	/**
	 * Prints the training map object for informational purposes.
	 */
	private fun printTrainingMap() {
		game.printToLog("\n[INFO] Stat Gains by Training:")
		trainingMap.forEach { name, training ->
			game.printToLog("[TRAINING] $name Training stat gains: ${training.statGains.contentToString()}, failure chance: ${training.failureChance}%.")
		}
	}
}