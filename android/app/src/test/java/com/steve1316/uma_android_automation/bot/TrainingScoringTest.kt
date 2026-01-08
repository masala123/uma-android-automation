package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import com.steve1316.uma_android_automation.bot.Training.Companion.scoreFriendshipTraining
import com.steve1316.uma_android_automation.bot.Training.Companion.scoreUnityCupTraining
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateStatEfficiencyScore
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateRelationshipScore
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateMiscScore
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateRawTrainingScore
import com.steve1316.uma_android_automation.bot.Training.TrainingConfig
import com.steve1316.uma_android_automation.bot.Training.TrainingOption
import com.steve1316.uma_android_automation.utils.CustomImageUtils.BarFillResult

/**
 * Unit tests for the Training scoring functions.
 *
 * These tests verify the correctness of the scoring algorithms used to determine
 * the best training option based on various game state configurations.
 */
@DisplayName("Training Scoring Tests")
class TrainingScoringTest {

	/**
	 * Returns the stat targets IntArray for the given distance.
	 * Order: Speed, Stamina, Power, Guts, Wit
	 *
	 * @param distance The distance string: "Sprint", "Mile", "Medium", or "Long".
	 *
	 * @return IntArray of stat targets for that distance.
	 */
	private fun getStatTargetsForDistance(distance: String): IntArray {
		return when (distance) {
			"Sprint" -> intArrayOf(900, 300, 600, 300, 300)
			"Mile" -> intArrayOf(900, 300, 600, 300, 300)
			"Medium" -> intArrayOf(800, 450, 550, 300, 300)
			"Long" -> intArrayOf(700, 600, 450, 300, 300)
			else -> intArrayOf(600, 600, 600, 300, 300)
		}
	}

	// Helper function to create a default TrainingOption for testing.
	private fun createDefaultTrainingOption(
		name: String = "Speed",
		statGains: IntArray = intArrayOf(15, 0, 5, 0, 0),
		failureChance: Int = 5,
		relationshipBars: ArrayList<BarFillResult> = arrayListOf(),
		isRainbow: Boolean = false,
		numSpiritGaugesCanFill: Int = 0,
		numSpiritGaugesReadyToBurst: Int = 0
	): TrainingOption {
		return TrainingOption(
			name = name,
			statGains = statGains,
			failureChance = failureChance,
			relationshipBars = relationshipBars,
			isRainbow = isRainbow,
			numSpiritGaugesCanFill = numSpiritGaugesCanFill,
			numSpiritGaugesReadyToBurst = numSpiritGaugesReadyToBurst
		)
	}

	// Helper function to create a default TrainingConfig for testing.
	private fun createDefaultConfig(
		trainingOptions: List<TrainingOption> = listOf(createDefaultTrainingOption()),
		currentStats: Map<String, Int> = mapOf(
			"Speed" to 120,
			"Stamina" to 120,
			"Power" to 120,
			"Guts" to 120,
			"Wit" to 120
		),
		statPrioritization: List<String> = listOf("Speed", "Stamina", "Power", "Wit", "Guts"),
		preferredDistance: String = "Medium",
		currentDate: Game.Date = Game.Date(year = 1, phase = "Early", month = 1, turnNumber = 1),
		scenario: String = "URA Finale",
		enableRainbowTrainingBonus: Boolean = true,
		focusOnSparkStatTarget: List<String> = emptyList(),
		blacklist: List<String> = emptyList(),
		disableTrainingOnMaxedStat: Boolean = false,
		currentStatCap: Int = 1200,
		skillHintsPerLocation: List<Int> = listOf(0, 0, 0, 0, 0),
		enablePrioritizeSkillHints: Boolean = false
	): TrainingConfig {
		return TrainingConfig(
			currentStats = currentStats,
			statPrioritization = statPrioritization,
			statTargets = getStatTargetsForDistance(preferredDistance),
			currentDate = currentDate,
			scenario = scenario,
			enableRainbowTrainingBonus = enableRainbowTrainingBonus,
			focusOnSparkStatTarget = focusOnSparkStatTarget,
			blacklist = blacklist,
			disableTrainingOnMaxedStat = disableTrainingOnMaxedStat,
			currentStatCap = currentStatCap,
			trainingOptions = trainingOptions,
			skillHintsPerLocation = skillHintsPerLocation,
			enablePrioritizeSkillHints = enablePrioritizeSkillHints
		)
	}

	@Test
	@DisplayName("Speed rainbow training should be selected despite high current stat")
	fun testSpeedRainbowTrainingSelectedWithHighStats() {
		// Current stats with Speed already at 1100.
		val currentStats = mapOf(
			"Speed" to 1100,
			"Stamina" to 700,
			"Power" to 800,
			"Guts" to 400,
			"Wit" to 300
		)

		val speedTraining = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(60, 0, 30, 0, 0),
			isRainbow = true
		)
		val staminaTraining = createDefaultTrainingOption(
			name = "Stamina",
			statGains = intArrayOf(0, 15, 0, 7, 0),
			isRainbow = false
		)
		val powerTraining = createDefaultTrainingOption(
			name = "Power",
			statGains = intArrayOf(0, 25, 45, 0, 0),
			isRainbow = true
		)
		val gutsTraining = createDefaultTrainingOption(
			name = "Guts",
			statGains = intArrayOf(0, 5, 0, 10, 0),
			isRainbow = false
		)
		val witTraining = createDefaultTrainingOption(
			name = "Wit",
			statGains = intArrayOf(5, 0, 0, 0, 10),
			isRainbow = false
		)

		val trainingOptions = listOf(speedTraining, staminaTraining, powerTraining, gutsTraining, witTraining)

		val config = createDefaultConfig(
			trainingOptions = trainingOptions,
			currentStats = currentStats,
			preferredDistance = "Medium",
			currentDate = Game.Date(year = 2, phase = "Early", month = 6, turnNumber = 40),
			enableRainbowTrainingBonus = true
		)

		// Speed training should have the highest score due to rainbow bonus.
		val scores = trainingOptions.associateWith { calculateRawTrainingScore(config, it) }
		val bestTraining = scores.maxByOrNull { it.value }?.key
		assertEquals("Speed", bestTraining?.name, "Speed rainbow training should be selected despite high current stat")
		assertTrue(scores[speedTraining]!! > 0, "Speed training score should be positive")
	}

    // ============================================================================
	// scoreFriendshipTraining Tests
	// ============================================================================

	@Test
	@DisplayName("Blue and green bars are prioritized with priority order blue > green > orange")
	fun testBarColorPriority() {
		// Blue bar should contribute most, green next, orange nothing.
		val blueBar = BarFillResult(fillPercent = 50.0, filledSegments = 2, dominantColor = "blue")
		val greenBar = BarFillResult(fillPercent = 50.0, filledSegments = 2, dominantColor = "green")
		val orangeBar = BarFillResult(fillPercent = 50.0, filledSegments = 2, dominantColor = "orange")

		val trainingWithBlue = createDefaultTrainingOption(
			relationshipBars = arrayListOf(blueBar)
		)
		val trainingWithGreen = createDefaultTrainingOption(
			relationshipBars = arrayListOf(greenBar)
		)
		val trainingWithOrange = createDefaultTrainingOption(
			relationshipBars = arrayListOf(orangeBar)
		)

		val blueScore = scoreFriendshipTraining(trainingWithBlue)
		val greenScore = scoreFriendshipTraining(trainingWithGreen)
		val orangeScore = scoreFriendshipTraining(trainingWithOrange)

		// Verify priority order: blue > green > orange.
        assertTrue(blueScore > greenScore, "Blue friendship bar should score higher than green")
		assertTrue(greenScore > orangeScore, "Green friendship bar should score higher than orange")
        assertTrue(blueScore > orangeScore, "Blue friendship bar should score higher than orange")
	}

	@Test
	@DisplayName("No bars returns negative infinity")
	fun testNoBarsReturnsNegativeInfinity() {
		val trainingWithNoBars = createDefaultTrainingOption(
			relationshipBars = arrayListOf()
		)

		val score = scoreFriendshipTraining(trainingWithNoBars)

		assertEquals(Double.NEGATIVE_INFINITY, score, "Empty relationship bars should return negative infinity")
	}

	@Test
	@DisplayName("Only orange bars returns zero score")
	fun testOnlyOrangeBarsReturnsZero() {
		val orangeBar1 = BarFillResult(fillPercent = 85.0, filledSegments = 3, dominantColor = "orange")
		val orangeBar2 = BarFillResult(fillPercent = 95.0, filledSegments = 3, dominantColor = "orange")
		val orangeBar3 = BarFillResult(fillPercent = 100.0, filledSegments = 4, dominantColor = "orange")

		val trainingWithOnlyOrange = createDefaultTrainingOption(
			relationshipBars = arrayListOf(orangeBar1, orangeBar2, orangeBar3)
		)

		val score = scoreFriendshipTraining(trainingWithOnlyOrange)

		assertEquals(0.0, score, "A zero score should be given with only orange bars for the training")
	}
}
