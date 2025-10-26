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
		val relationshipBars: ArrayList<CustomImageUtils.BarFillResult>
	) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false

			other as TrainingOption

			if (failureChance != other.failureChance) return false
			if (name != other.name) return false
			if (!statGains.contentEquals(other.statGains)) return false
			if (relationshipBars != other.relationshipBars) return false

			return true
		}

		override fun hashCode(): Int {
			var result = failureChance
			result = 31 * result + name.hashCode()
			result = 31 * result + statGains.contentHashCode()
			result = 31 * result + relationshipBars.hashCode()
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
	private val preferredDistanceOverride: String = SettingsHelper.getStringSetting("training", "preferredDistanceOverride")
	private val statTargetsByDistance: MutableMap<String, IntArray> = mutableMapOf(
		"Sprint" to intArrayOf(0, 0, 0, 0, 0),
		"Mile" to intArrayOf(0, 0, 0, 0, 0),
		"Medium" to intArrayOf(0, 0, 0, 0, 0),
		"Long" to intArrayOf(0, 0, 0, 0, 0)
	)
	var preferredDistance: String = ""
	var firstTrainingCheck = true
	private val currentStatCap = 1200
	private val historicalTrainingCounts: MutableMap<String, Int> = mutableMapOf()

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
					// Use CountDownLatch to run the 3 operations in parallel to cut down on processing time.
					val latch = CountDownLatch(3)

					// Variables to store results from parallel threads.
					var statGains: IntArray = intArrayOf()
					var failureChance: Int = -1
					var relationshipBars: ArrayList<CustomImageUtils.BarFillResult> = arrayListOf()

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

					// Wait for all threads to complete.
					try {
						latch.await(10, TimeUnit.SECONDS)
					} catch (_: InterruptedException) {
						Log.e(tag, "[ERROR] Parallel training analysis timed out")
					} finally {
						game.printToLog("[INFO] All 5 stat regions processed for $training training. Results: ${statGains.toList()}", tag = tag)
					}

					val newTraining = TrainingOption(
						name = training,
						statGains = statGains,
						failureChance = failureChance,
						relationshipBars = relationshipBars
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
	 * Recommends the best training option based on current game state and strategic priorities.
	 *
	 * This function implements a sophisticated training recommendation system that adapts to different
	 * phases of the game. It uses different scoring algorithms depending on the current game year:
	 *
	 * **Early Game (Pre-Debut/Year 1):**
	 * - Focuses on relationship building using `scoreFriendshipTraining()`
	 * - Prioritizes training options that build friendship bars, especially blue bars
	 * - Ignores stat gains in favor of relationship development
	 *
	 * **Mid/Late Game (Year 2+):**
	 * - Uses comprehensive scoring via `scoreStatTrainingEnhanced()`
	 * - Combines stat efficiency (60-70%), relationship building (10%), and context bonuses (30%)
	 * - Adapts weighting based on whether relationship bars are present
	 *
	 * The scoring system considers multiple factors:
	 * - **Stat Efficiency:** How well training helps achieve target stats for the preferred race distance
	 * - **Relationship Building:** Value of friendship bar progress with diminishing returns
	 * - **Context Bonuses:** Phase-specific bonuses and stat gain thresholds
	 * - **Blacklist Compliance:** Excludes blacklisted training options
	 * - **Stat Cap Respect:** Avoids training that would exceed stat caps when enabled
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
		 * Calculates the efficiency score for stat gains based on target achievement and priority weights.
		 *
		 * This function evaluates how well a training option helps achieve stat targets by considering:
		 * - The gap between current stats and target stats
		 * - Priority weights that vary by game year (higher priority in later years)
		 * - Efficiency bonuses for closing gaps vs diminishing returns for overage
		 * - Spark stat target focus when enabled (Speed, Stamina, Power to 600+)
		 * - Enhanced priority weighting for top 3 stats to prevent target completion from overriding large gains
		 *
		 * @param training The training option to evaluate.
		 * @param target Array of target stat values for the preferred race distance.
		 *
		 * @return A normalized score (0-100) representing stat efficiency.
		 */
		fun calculateStatEfficiencyScore(training: TrainingOption, target: IntArray): Double {
			var score = 100.0

			for ((index, stat) in trainings.withIndex()) {
				val currentStat = currentStatsMap.getOrDefault(stat, 0)
				val targetStat = target.getOrElse(index) { 0 }
				val statGain = training.statGains.getOrElse(index) { 0 }
				val remaining = targetStat - currentStat

				if (statGain > 0) {
					// Priority weight based on the current state of the game.
					val priorityIndex = statPrioritization.indexOf(stat)
					val priorityWeight = if (priorityIndex != -1) {
						// Enhanced priority weighting for top 3 stats
						val top3Bonus = when (priorityIndex) {
							0 -> 2.0
							1 -> 1.5
							2 -> 1.1
							else -> 1.0
						}

						val baseWeight = when {
							game.currentDate.year == 1 || game.currentDate.phase == "Pre-Debut" -> 1.0 + (0.1 * (statPrioritization.size - priorityIndex)) / statPrioritization.size
							game.currentDate.year == 2 -> 1.0 + (0.3 * (statPrioritization.size - priorityIndex)) / statPrioritization.size
							game.currentDate.year == 3 -> 1.0 + (0.5 * (statPrioritization.size - priorityIndex)) / statPrioritization.size
							else -> 1.0
						}

						baseWeight * top3Bonus
					} else {
						0.5 // Lower weight for non-prioritized stats.
					}

					Log.d(tag, "[DEBUG] Priority Weight: $priorityWeight")

					// Calculate efficiency based on remaining gap between the current stat and the target.
					var efficiency = if (remaining > 0) {
						// Stat is below target, but reduce the bonus when very close to the target.
						Log.d(tag, "[DEBUG] Giving bonus for remaining efficiency.")
						val gapRatio = remaining.toDouble() / targetStat
						val targetBonus = when {
							gapRatio > 0.1 -> 1.5
							gapRatio > 0.05 -> 1.25
							else -> 1.1
						}
						targetBonus + (statGain.toDouble() / remaining).coerceAtMost(1.0)
					} else {
						// Stat is above target, give a diminishing bonus based on how much over.
						Log.d(tag, "[DEBUG] Stat is above target so giving diminishing bonus.")
						val overageRatio = (statGain.toDouble() / (-remaining + statGain))
						1.0 + overageRatio
					}

					Log.d(tag, "[DEBUG] Efficiency: $efficiency")

					// Apply Spark stat target focus when enabled.
					if (focusOnSparkStatTarget) {
						val sparkTarget = 600
						val sparkRemaining = sparkTarget - currentStat

						// Check if this is a Spark stat (Speed, Stamina, Power) and it's below 600.
						if ((stat == "Speed" || stat == "Stamina" || stat == "Power") && sparkRemaining > 0) {
							// Boost efficiency for Spark stats that are below 600.
							val sparkEfficiency = 2.0 + (statGain.toDouble() / sparkRemaining).coerceAtMost(1.0)
							// Use the higher of the two efficiencies (original target vs spark target).
							efficiency = maxOf(efficiency, sparkEfficiency)
						}
					}

					score += statGain * 2
					score += (statGain * 2) * (efficiency * priorityWeight)
					Log.d(tag, "[DEBUG] Score: $score")
				}
			}

			return score.coerceAtMost(1000.0)
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
		 * Calculates context-aware bonuses and penalties based on game phase and training properties.
		 *
		 * Applies various bonuses including:
		 * - Phase-specific bonuses (relationship focus in early game, stat efficiency in later years)
		 * - Stat gain thresholds that provide additional bonuses
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A context score between 0-200 representing situational bonuses.
		 */
		fun calculateContextScore(training: TrainingOption): Double {
			// Start with neutral score.
			var score = 100.0

			// Bonuses for each game phase.
			when {
				game.currentDate.year == 1 || game.currentDate.phase == "Pre-Debut" -> {
					// Prefer relationship building and balanced stat gains.
					if (training.relationshipBars.isNotEmpty()) score += 50.0
					if (training.statGains.sum() > 15) score += 50.0
				}
				game.currentDate.year == 2 -> {
					// Focus on stat efficiency.
					score += 50.0
					if (training.statGains.sum() > 20) score += 100.0
				}
				game.currentDate.year == 3 -> {
					// Prioritize target achievement
					score += 100.0
					if (training.statGains.sum() > 40) score += 200.0
				}
			}

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
			score += 100.0 * skillHintLocations.size

			return score.coerceIn(0.0, 1000.0)
		}

		/**
		 * Performs comprehensive scoring of training options using multiple weighted factors.
		 *
		 * This scoring system combines three main components:
		 * - Stat efficiency (60-70% weight): How well the training helps achieve stat targets
		 * - Relationship building (10% weight): Value of friendship bar progress
		 * - Context bonuses (30% weight): Phase-specific bonuses, etc.
		 *
		 * The weighting changes based on whether relationship bars are present:
		 * - With relationship bars: 60% stat, 10% relationship, 30% context
		 * - Without relationship bars: 70% stat, 0% relationship, 30% context
		 *
		 * @param training The training option to evaluate.
		 *
		 * @return A normalized score (1-1000) representing overall training value.
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
			var maxPossibleScore = 0.0

			// 1. Stat Efficiency scoring
			val statScore = calculateStatEfficiencyScore(training, target)

			// 2. Friendship scoring
			val relationshipScore = calculateRelationshipScore(training)

			// 3. Context-aware scoring
			val contextScore = calculateContextScore(training)

			if (training.relationshipBars.isNotEmpty()) {
				totalScore += statScore * 0.6
				maxPossibleScore += 100.0 * 0.6

				totalScore += relationshipScore * 0.1
				maxPossibleScore += 100.0 * 0.1

				totalScore += contextScore * 0.3
				maxPossibleScore += 100.0 * 0.3
			} else {
				totalScore += statScore * 0.7
				maxPossibleScore += 100.0 * 0.7

				totalScore += contextScore * 0.3
				maxPossibleScore += 100.0 * 0.3
			}

			game.printToLog(
				"[TRAINING] Scores | Current Stat: ${currentStatsMap[training.name]}, Target Stat: ${target[trainings.indexOf(training.name)]}, " +
					"Stat Efficiency: ${game.decimalFormat.format(statScore)}, Relationship: ${game.decimalFormat.format(relationshipScore)}, " +
					"Context: ${game.decimalFormat.format(contextScore)}"
			)

			// Normalize the score.
			val normalizedScore = (totalScore / maxPossibleScore * 100.0).coerceIn(1.0, 1000.0)

			game.printToLog("[TRAINING] Enhanced final score for ${training.name} Training: ${game.decimalFormat.format(normalizedScore)}/1000.0")

			return normalizedScore
		}

		// Decide which scoring function to use based on the current phase or year.
		// Junior Year will focus on building relationship bars.
		val best = if (game.currentDate.phase == "Pre-Debut" || game.currentDate.year == 1) {
			trainingMap.values.maxByOrNull { scoreFriendshipTraining(it) }
		} else trainingMap.values.maxByOrNull { scoreStatTraining(it) }

		return if (best != null) {
			historicalTrainingCounts.put(best.name, historicalTrainingCounts.getOrDefault(best.name, 0) + 1)
			best.name
		} else {
			trainingMap.keys.firstOrNull { it !in blacklist } ?: ""
		}
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