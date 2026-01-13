package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import com.steve1316.uma_android_automation.bot.Training.Companion.scoreFriendshipTraining
import com.steve1316.uma_android_automation.bot.Training.Companion.scoreUnityCupTraining
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateStatEfficiencyScore
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateRelationshipScore
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateMiscScore
import com.steve1316.uma_android_automation.bot.Training.Companion.calculateRawTrainingScore
import com.steve1316.uma_android_automation.bot.Training.TrainingConfig
import com.steve1316.uma_android_automation.bot.Training.TrainingOption
import com.steve1316.uma_android_automation.utils.CustomImageUtils.BarFillResult
import java.util.stream.Stream

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

	// ============================================================================
	// calculateStatEfficiencyScore Tests
	// ============================================================================

	@Test
	@DisplayName("Stats furthest behind target get highest multiplier")
	fun testStatsBehindTargetGetHigherMultiplier() {
		val currentStats = mapOf(
			"Speed" to 300,
			"Stamina" to 600,
			"Power" to 300,
			"Guts" to 300,
			"Wit" to 300
		)

		val speedTraining = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(30, 0, 15, 0, 0)
		)
		val staminaTraining = createDefaultTrainingOption(
			name = "Stamina",
			statGains = intArrayOf(0, 45, 0, 20, 0)
		)

		val config = createDefaultConfig(
			trainingOptions = listOf(speedTraining, staminaTraining),
			currentStats = currentStats,
			preferredDistance = "Medium"
		)

		val speedScore = calculateStatEfficiencyScore(config, speedTraining)
		val staminaScore = calculateStatEfficiencyScore(config, staminaTraining)

		assertTrue(speedScore > staminaScore, "Speed should score higher than Stamina due to being more behind target and is higher in the stat priority list")
	}

	@Test
	@DisplayName("High main stat gains get bonus multiplier")
	fun testHighMainStatGainsGetBonus() {
		val currentStats = mapOf(
			"Speed" to 600,
			"Stamina" to 600,
			"Power" to 600,
			"Guts" to 600,
			"Wit" to 600
		)

		val highMainStatTraining = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(35, 0, 10, 0, 0)
		)
		val lowMainStatTraining = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(20, 0, 10, 0, 0)
		)

		val config = createDefaultConfig(
			trainingOptions = listOf(highMainStatTraining, lowMainStatTraining),
			currentStats = currentStats
		)

		val highScore = calculateStatEfficiencyScore(config, highMainStatTraining)
		val lowScore = calculateStatEfficiencyScore(config, lowMainStatTraining)

		val expectedRatio = 35.0 / 20.0
		val actualRatio = highScore / lowScore
		assertTrue(actualRatio > expectedRatio, "High main stat gains (30+) should get bonus beyond just stat gain difference")
	}

	@Test
	@DisplayName("Spark bonus applies for stats below 600 when enabled")
	fun testSparkBonusAppliesForLowStats() {
		val currentStats = mapOf(
			"Speed" to 400,
			"Stamina" to 400,
			"Power" to 400,
			"Guts" to 400,
			"Wit" to 400
		)

		val speedTraining = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(20, 0, 10, 0, 0)
		)

		val configWithSpark = createDefaultConfig(
			trainingOptions = listOf(speedTraining),
			currentStats = currentStats,
			focusOnSparkStatTarget = listOf("Speed")
		)
		val configWithoutSpark = createDefaultConfig(
			trainingOptions = listOf(speedTraining),
			currentStats = currentStats,
			focusOnSparkStatTarget = emptyList()
		)

		val sparkScore = calculateStatEfficiencyScore(configWithSpark, speedTraining)
		val noSparkScore = calculateStatEfficiencyScore(configWithoutSpark, speedTraining)

		assertTrue(sparkScore > noSparkScore, "Spark bonus should increase score for stats below 600")
	}

	@Test
	@DisplayName("Zero stat gains return zero score")
	fun testZeroStatGainsReturnZero() {
		val training = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(0, 0, 0, 0, 0)
		)

		val config = createDefaultConfig(trainingOptions = listOf(training))
		val score = calculateStatEfficiencyScore(config, training)

		assertEquals(0.0, score, "Training with no stat gains should return zero")
	}

	// ============================================================================
	// calculateRelationshipScore Tests
	// ============================================================================

	@Test
	@DisplayName("Diminishing returns apply as bars fill up")
	fun testDiminishingReturnsForFilledBars() {
		val lowFillBar = BarFillResult(fillPercent = 20.0, filledSegments = 1, dominantColor = "blue")
		val highFillBar = BarFillResult(fillPercent = 70.0, filledSegments = 3, dominantColor = "green")

		val lowFillTraining = createDefaultTrainingOption(
			relationshipBars = arrayListOf(lowFillBar)
		)
		val highFillTraining = createDefaultTrainingOption(
			relationshipBars = arrayListOf(highFillBar)
		)

		val config = createDefaultConfig(trainingOptions = listOf(lowFillTraining, highFillTraining))

		val lowFillScore = calculateRelationshipScore(config, lowFillTraining)
		val highFillScore = calculateRelationshipScore(config, highFillTraining)

		assertTrue(lowFillScore > highFillScore, "Lower fill bars should score higher due to diminishing returns")
	}

	// ============================================================================
	// calculateMiscScore Tests
	// ============================================================================

	@Test
	@DisplayName("Trainings with skill hints score higher than those without")
	fun testSkillHintsAdd10PointsEach() {
		val speedTraining = createDefaultTrainingOption(name = "Speed")
		val staminaTraining = createDefaultTrainingOption(name = "Stamina")

		// Speed has 2 skill hints, Stamina has 0.
		val config = createDefaultConfig(
			trainingOptions = listOf(speedTraining, staminaTraining),
			skillHintsPerLocation = listOf(2, 0, 0, 0, 0)
		)

		val speedScore = calculateMiscScore(config, speedTraining)
		val staminaScore = calculateMiscScore(config, staminaTraining)

		assertTrue(speedScore > staminaScore, "A training with skill hints should score higher than a training with no skill hints")
	}

	@Test
	@DisplayName("Prioritized skill hints return massive score")
	fun testPrioritizedSkillHintsReturnMassiveScore() {
		val training = createDefaultTrainingOption(name = "Speed")

		val configWithPriority = createDefaultConfig(
			trainingOptions = listOf(training),
			skillHintsPerLocation = listOf(1, 0, 0, 0, 0),
			enablePrioritizeSkillHints = true
		)
		val configWithoutPriority = createDefaultConfig(
			trainingOptions = listOf(training),
			skillHintsPerLocation = listOf(1, 0, 0, 0, 0),
			enablePrioritizeSkillHints = false
		)

		val priorityScore = calculateMiscScore(configWithPriority, training)
		val normalScore = calculateMiscScore(configWithoutPriority, training)

		assertTrue(priorityScore > normalScore, "Prioritized skill hints should return higher score than normal skill hints")
	}

	// ============================================================================
	// calculateRawTrainingScore Tests
	// ============================================================================

	@Test
	@DisplayName("Blacklisted training returns zero score")
	fun testBlacklistedTrainingReturnsZero() {
		val training = createDefaultTrainingOption(name = "Speed")

		val config = createDefaultConfig(
			trainingOptions = listOf(training),
			blacklist = listOf("Speed")
		)

		val score = calculateRawTrainingScore(config, training)

		assertEquals(0.0, score, "Blacklisted training should return zero score")
	}

	@Test
	@DisplayName("Training at stat cap returns zero score")
	fun testTrainingAtStatCapReturnsZero() {
		val currentStats = mapOf(
			"Speed" to 1200,
			"Stamina" to 400,
			"Power" to 400,
			"Guts" to 400,
			"Wit" to 400
		)

		val training = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(60, 0, 30, 0, 0)
		)

		val config = createDefaultConfig(
			trainingOptions = listOf(training),
			currentStats = currentStats,
			currentStatCap = 1200
		)

		val score = calculateRawTrainingScore(config, training)

		assertEquals(0.0, score, "Training that would exceed stat cap should return zero score")
	}

	@Test
	@DisplayName("Maxed stat with disableTrainingOnMaxedStat returns zero")
	fun testMaxedStatWithDisableSettingReturnsZero() {
		val currentStats = mapOf(
			"Speed" to 1999,
			"Stamina" to 400,
			"Power" to 400,
			"Guts" to 400,
			"Wit" to 400
		)

		val training = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(60, 0, 30, 0, 0)
		)

		val config = createDefaultConfig(
			trainingOptions = listOf(training),
			currentStats = currentStats,
			disableTrainingOnMaxedStat = true,
			currentStatCap = 1000
		)

		val score = calculateRawTrainingScore(config, training)

		assertEquals(0.0, score, "Training for would-be maxed stat should return zero when disableTrainingOnMaxedStat is true")
	}

	@Test
	@DisplayName("Rainbow training scores higher")
	fun testRainbowMultiplierInYear2Plus() {
		val rainbowTraining = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(30, 0, 15, 0, 0),
			isRainbow = true
		)
		val normalTraining = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(30, 0, 15, 0, 0),
			isRainbow = false
		)

		val config = createDefaultConfig(
			trainingOptions = listOf(rainbowTraining, normalTraining),
			currentDate = Game.Date(year = 2, phase = "Late", month = 12, turnNumber = 50),
			enableRainbowTrainingBonus = true
		)

		val rainbowScore = calculateRawTrainingScore(config, rainbowTraining)
		val normalScore = calculateRawTrainingScore(config, normalTraining)

		assertTrue(rainbowScore > normalScore, "Rainbow training should score higher")
	}

	@Test
	@DisplayName("Training with relationship bars uses different weights")
	fun testRelationshipBarsChangeWeightDistribution() {
		val bar = BarFillResult(fillPercent = 20.0, filledSegments = 2, dominantColor = "blue")
		val trainingWithBars = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(20, 0, 10, 0, 0),
			relationshipBars = arrayListOf(bar)
		)
		val trainingWithoutBars = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(20, 0, 10, 0, 0),
			relationshipBars = arrayListOf()
		)

		val config = createDefaultConfig(
			trainingOptions = listOf(trainingWithBars, trainingWithoutBars)
		)

		val withBarsScore = calculateRawTrainingScore(config, trainingWithBars)
		val withoutBarsScore = calculateRawTrainingScore(config, trainingWithoutBars)

		// Both should have positive scores, and the relationship bar contribution should affect total.
		assertTrue(withBarsScore > 0, "Training with bars should have positive score")
		assertTrue(withoutBarsScore > 0, "Training without bars should have positive score")
		// The training with bars gets relationship contribution.
		assertNotEquals(withBarsScore, withoutBarsScore, "Scores should differ based on relationship bars presence")
	}

	@Test
	@DisplayName("Rainbow bonus is reduced when enableRainbowTrainingBonus is false")
	fun testReducedRainbowBonusWhenDisabled() {
		val rainbowTraining = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(30, 0, 15, 0, 0),
			isRainbow = true
		)
		val normalTraining = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(30, 0, 15, 0, 0),
			isRainbow = false
		)

		val config = createDefaultConfig(
			trainingOptions = listOf(rainbowTraining, normalTraining),
			currentDate = Game.Date(year = 2, phase = "Late", month = 12, turnNumber = 50),
			enableRainbowTrainingBonus = false
		)

		val rainbowScore = calculateRawTrainingScore(config, rainbowTraining)
		val normalScore = calculateRawTrainingScore(config, normalTraining)

		assertTrue(rainbowScore > normalScore, "Rainbow training should still score higher when bonus is disabled")
	}

	// ============================================================================
	// scoreUnityCupTraining Tests
	// ============================================================================

	@Test
	@DisplayName("Spirit gauges ready to burst get highest priority")
	fun testSpiritGaugesReadyToBurstHighestPriority() {
		val trainingWithBurst = createDefaultTrainingOption(
			name = "Speed",
			numSpiritGaugesReadyToBurst = 1,
			numSpiritGaugesCanFill = 0
		)
		val trainingWithFill = createDefaultTrainingOption(
			name = "Stamina",
			numSpiritGaugesReadyToBurst = 0,
			numSpiritGaugesCanFill = 3
		)
		val trainingWithNoGauges = createDefaultTrainingOption(
			name = "Power",
			numSpiritGaugesReadyToBurst = 0,
			numSpiritGaugesCanFill = 0
		)

		val config = createDefaultConfig(
			trainingOptions = listOf(trainingWithBurst, trainingWithFill, trainingWithNoGauges),
			scenario = "Unity Cup"
		)

		val burstScore = scoreUnityCupTraining(config, trainingWithBurst)
		val fillScore = scoreUnityCupTraining(config, trainingWithFill)
		val noGaugeScore = scoreUnityCupTraining(config, trainingWithNoGauges)

		assertTrue(burstScore > fillScore, "Training with gauges ready to burst should score higher than training that can fill gauges")
		assertTrue(fillScore > noGaugeScore, "Training that can fill gauges should score higher than training with no gauges")
	}

	@Test
	@DisplayName("Speed and Wit get facility preference bonuses when spirit gauge bursting")
	fun testFacilityPreferenceBonusesForBursting() {
		// Zero out stat gains to isolate facility bonuses.
		val speedTraining = createDefaultTrainingOption(
			name = "Speed",
			statGains = intArrayOf(0, 0, 0, 0, 0),
			numSpiritGaugesReadyToBurst = 1
		)
		val witTraining = createDefaultTrainingOption(
			name = "Wit",
			statGains = intArrayOf(0, 0, 0, 0, 0),
			numSpiritGaugesReadyToBurst = 1
		)
		val gutsTraining = createDefaultTrainingOption(
			name = "Guts",
			statGains = intArrayOf(0, 0, 0, 0, 0),
			numSpiritGaugesReadyToBurst = 1,
			numSpiritGaugesCanFill = 0
		)

		val config = createDefaultConfig(
			trainingOptions = listOf(speedTraining, witTraining, gutsTraining),
			scenario = "Unity Cup"
		)

		val speedScore = scoreUnityCupTraining(config, speedTraining)
		val witScore = scoreUnityCupTraining(config, witTraining)
		val gutsScore = scoreUnityCupTraining(config, gutsTraining)

		// Speed and Wit should have same bonuses (both get +500 facility bonus).
		assertEquals(speedScore, witScore, 0.01, "Speed and Wit should have equal facility bonuses")
		// Guts should score lower since it doesn't have the facility bonus.
		assertTrue(speedScore > gutsScore, "Speed should score higher than Guts for facility preference")
	}

	@Test
	@DisplayName("Early game provides spirit gauge filling bonus")
	fun testEarlyGameGaugeFillingBonus() {
		val training = createDefaultTrainingOption(
			name = "Speed",
			numSpiritGaugesCanFill = 2
		)

		val earlyConfig = createDefaultConfig(
			trainingOptions = listOf(training),
			scenario = "Unity Cup",
			currentDate = Game.Date(year = 1, phase = "Early", month = 1, turnNumber = 1)
		)
		val lateConfig = createDefaultConfig(
			trainingOptions = listOf(training),
			scenario = "Unity Cup",
			currentDate = Game.Date(year = 2, phase = "Early", month = 6, turnNumber = 40)
		)

		val earlyScore = scoreUnityCupTraining(earlyConfig, training)
		val lateScore = scoreUnityCupTraining(lateConfig, training)

		assertTrue(earlyScore > lateScore, "Early game should provide bonus for spirit gauge filling")
	}

	@Test
	@DisplayName("Rainbow training provides bonus when spirit gauge bursting")
	fun testRainbowBonusWhenBursting() {
		val rainbowBurstTraining = createDefaultTrainingOption(
			name = "Speed",
			numSpiritGaugesReadyToBurst = 1,
			isRainbow = true
		)
		val normalBurstTraining = createDefaultTrainingOption(
			name = "Speed",
			numSpiritGaugesReadyToBurst = 1,
			isRainbow = false
		)

		val config = createDefaultConfig(
			trainingOptions = listOf(rainbowBurstTraining, normalBurstTraining),
			scenario = "Unity Cup"
		)

		val rainbowScore = scoreUnityCupTraining(config, rainbowBurstTraining)
		val normalScore = scoreUnityCupTraining(config, normalBurstTraining)

		assertTrue(rainbowScore > normalScore, "Rainbow training should score higher when spirit gauge bursting")
	}

	// ============================================================================
	// Training Example Cases (Parameterized)
	// ============================================================================

	/**
	 * Data class representing a training scenario test case.
	 */
	data class TrainingTestCase(
		val description: String,
		val currentStats: Map<String, Int>,
		val trainings: List<TrainingDef>,
		val preferredDistance: String,
		val date: Game.Date,
		val expectedTraining: String
	) {
		// Override toString() to only show the description in test names.
		override fun toString(): String = description
	}

	/**
	 * Simplified training definition for test cases.
	 */
	data class TrainingDef(
		val name: String,
		val statGains: IntArray,
		val relationshipBars: List<BarDef> = emptyList(),
		val numSpiritGaugesCanFill: Int = 0,
		val numSpiritGaugesReadyToBurst: Int = 0,
		val isRainbow: Boolean = false
	) {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false

			other as TrainingDef

			if (numSpiritGaugesCanFill != other.numSpiritGaugesCanFill) return false
			if (numSpiritGaugesReadyToBurst != other.numSpiritGaugesReadyToBurst) return false
			if (isRainbow != other.isRainbow) return false
			if (name != other.name) return false
			if (!statGains.contentEquals(other.statGains)) return false
			if (relationshipBars != other.relationshipBars) return false

			return true
		}

		override fun hashCode(): Int {
			var result = numSpiritGaugesCanFill
			result = 31 * result + numSpiritGaugesReadyToBurst
			result = 31 * result + isRainbow.hashCode()
			result = 31 * result + name.hashCode()
			result = 31 * result + statGains.contentHashCode()
			result = 31 * result + relationshipBars.hashCode()
			return result
		}
	}

	/**
	 * Simplified bar definition for test cases.
	 */
	data class BarDef(
		val fillPercent: Double = 50.0,
		val filledSegments: Int = 2,
		val color: String
	)

    // Note: Stat prioritization follows the default of [Speed, Stamina, Power, Wit, Guts].
	companion object {
		/**
		 * Provides Unity Cup test cases for parameterized testing.
		 * Add new test cases here - each one will automatically be tested.
		 */
		@JvmStatic
		fun unityCupTestCases(): Stream<TrainingTestCase> = Stream.of(
			TrainingTestCase(
				description = "Junior Year Early Dec - Guts with the only burstable gauge",
				currentStats = mapOf("Speed" to 358, "Stamina" to 217, "Power" to 258, "Guts" to 168, "Wit" to 168),
				trainings = listOf(
					TrainingDef("Speed", intArrayOf(15, 0, 6, 0, 0), listOf(BarDef(color = "green")), numSpiritGaugesCanFill = 1),
					TrainingDef("Stamina", intArrayOf(0, 8, 0, 4, 0)),
					TrainingDef("Power", intArrayOf(0, 4, 8, 0, 0)),
					TrainingDef("Guts", intArrayOf(11, 0, 10, 31, 0), listOf(BarDef(color = "green"), BarDef(color = "green"), BarDef(color = "green")), numSpiritGaugesCanFill = 1, numSpiritGaugesReadyToBurst = 1),
					TrainingDef("Wit", intArrayOf(4, 0, 0, 0, 17), numSpiritGaugesReadyToBurst = 1)
				),
				preferredDistance = "Medium",
				date = Game.Date(year = 1, phase = "Early", month = 12, turnNumber = 23),
				expectedTraining = "Guts"
			),
			TrainingTestCase(
				description = "Classic Year Early Aug - Stamina with more relationship bars and fillable gauge",
				currentStats = mapOf("Speed" to 453, "Stamina" to 372, "Power" to 483, "Guts" to 244, "Wit" to 214),
				trainings = listOf(
					TrainingDef("Speed", intArrayOf(22, 0, 10, 0, 0), listOf(BarDef(color = "green")), numSpiritGaugesCanFill = 1),
					TrainingDef("Stamina", intArrayOf(0, 25, 0, 13, 0), listOf(BarDef(color = "orange"), BarDef(color = "green"), BarDef(color = "green")), numSpiritGaugesCanFill = 1),
					TrainingDef("Power", intArrayOf(0, 15, 23, 0, 0), listOf(BarDef(color = "orange")), numSpiritGaugesCanFill = 1, isRainbow = true),
					TrainingDef("Guts", intArrayOf(5, 0, 5, 15, 0)),
					TrainingDef("Wit", intArrayOf(5, 0, 0, 0, 12))
				),
				preferredDistance = "Medium",
				date = Game.Date(year = 2, phase = "Early", month = 8, turnNumber = 39),
				expectedTraining = "Stamina"
			),
			TrainingTestCase(
				description = "Senior Year Early Jul - Speed with high main stat gain, rainbow bonus and fillable gauges",
				currentStats = mapOf("Speed" to 834, "Stamina" to 588, "Power" to 724, "Guts" to 335, "Wit" to 283),
				trainings = listOf(
					TrainingDef("Speed", intArrayOf(33, 0, 13, 0, 0), listOf(BarDef(color = "orange")), numSpiritGaugesCanFill = 2, isRainbow = true),
					TrainingDef("Stamina", intArrayOf(0, 47, 0, 22, 0), listOf(BarDef(color = "orange")), numSpiritGaugesReadyToBurst = 1),
					TrainingDef("Power", intArrayOf(0, 8, 14, 0, 0), numSpiritGaugesCanFill = 1),
					TrainingDef("Guts", intArrayOf(12, 0, 9, 35, 0), numSpiritGaugesReadyToBurst = 1),
					TrainingDef("Wit", intArrayOf(6, 0, 0, 0, 13))
				),
				preferredDistance = "Medium",
				date = Game.Date(year = 3, phase = "Early", month = 7, turnNumber = 53),
				expectedTraining = "Speed"
			)
		)

		/**
		 * Provides URA Finale test cases for parameterized testing.
		 * Add new test cases here - each one will automatically be tested.
		 */
		@JvmStatic
		fun uraFinaleTestCases(): Stream<TrainingTestCase> = Stream.of(
			TrainingTestCase(
				description = "URA Finale Qualifier - Speed with high main stat gain and rainbow bonus",
				currentStats = mapOf("Speed" to 1042, "Stamina" to 615, "Power" to 841, "Guts" to 362, "Wit" to 315),
				trainings = listOf(
					TrainingDef("Speed", intArrayOf(31, 0, 15, 0, 0), isRainbow = true),
					TrainingDef("Stamina", intArrayOf(0, 15, 0, 6, 0)),
					TrainingDef("Power", intArrayOf(0, 7, 15, 0, 0)),
					TrainingDef("Guts", intArrayOf(6, 0, 4, 16, 0)),
					TrainingDef("Wit", intArrayOf(5, 0, 0, 0, 15))
				),
				preferredDistance = "Medium",
				date = Game.Date(year = 3, phase = "Late", month = 12, turnNumber = 73),
				expectedTraining = "Speed"
			),
			TrainingTestCase(
				description = "Classic Year Early Aug - Speed with high main stat gain and rainbow bonus",
				currentStats = mapOf("Speed" to 537, "Stamina" to 386, "Power" to 388, "Guts" to 228, "Wit" to 255),
				trainings = listOf(
					TrainingDef("Speed", intArrayOf(29, 0, 12, 0, 0), listOf(BarDef(color = "orange")), isRainbow = true),
					TrainingDef("Stamina", intArrayOf(0, 25, 0, 10, 0), listOf(BarDef(color = "orange"))),
					TrainingDef("Power", intArrayOf(0, 8, 12, 0, 0)),
					TrainingDef("Guts", intArrayOf(7, 0, 7, 15, 0), listOf(BarDef(color = "green"))),
					TrainingDef("Wit", intArrayOf(6, 0, 0, 0, 14))
				),
				preferredDistance = "Medium",
				date = Game.Date(year = 2, phase = "Early", month = 8, turnNumber = 39),
				expectedTraining = "Speed"
			),
			TrainingTestCase(
				description = "Junior Year Pre-Debut - Power with the most relationship bars",
				currentStats = mapOf("Speed" to 136, "Stamina" to 189, "Power" to 160, "Guts" to 76, "Wit" to 135),
				trainings = listOf(
					TrainingDef("Speed", intArrayOf(10, 0, 4, 0, 0), listOf(BarDef(color = "blue"), BarDef(color = "blue"))),
					TrainingDef("Stamina", intArrayOf(0, 8, 0, 3, 0)),
					TrainingDef("Power", intArrayOf(0, 8, 12, 0, 0), listOf(BarDef(color = "blue"), BarDef(color = "blue"), BarDef(color = "blue"))),
					TrainingDef("Guts", intArrayOf(3, 0, 3, 6, 0)),
					TrainingDef("Wit", intArrayOf(3, 0, 0, 0, 9))
				),
				preferredDistance = "Medium",
				date = Game.Date(year = 1, phase = "Pre-Debut", month = 2, turnNumber = 2),
				expectedTraining = "Power"
			)
		)
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("unityCupTestCases")
	@DisplayName("Unity Cup Training Selection")
	fun testUnityCupTrainingSelection(testCase: TrainingTestCase) {
		// Convert TrainingDef to TrainingOption.
		val trainingOptions = testCase.trainings.map { def ->
			createDefaultTrainingOption(
				name = def.name,
				statGains = def.statGains,
				relationshipBars = ArrayList(def.relationshipBars.map { bar ->
					BarFillResult(bar.fillPercent, bar.filledSegments, bar.color)
				}),
				numSpiritGaugesCanFill = def.numSpiritGaugesCanFill,
				numSpiritGaugesReadyToBurst = def.numSpiritGaugesReadyToBurst,
				isRainbow = def.isRainbow
			)
		}

		val config = createDefaultConfig(
			trainingOptions = trainingOptions,
			currentStats = testCase.currentStats,
			preferredDistance = testCase.preferredDistance,
			currentDate = testCase.date,
			scenario = "Unity Cup"
		)

		// Score all trainings using Unity Cup scoring.
		val scores = if (testCase.date.year < 3) {
			trainingOptions.associateWith { scoreUnityCupTraining(config, it) }
		} else {
			trainingOptions.associateWith { calculateRawTrainingScore(config, it) }
		}
		val bestTraining = scores.maxByOrNull { it.value }?.key

		assertEquals(testCase.expectedTraining, bestTraining?.name, testCase.description)
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("uraFinaleTestCases")
	@DisplayName("URA Finale Training Selection")
	fun testURAFinaleTrainingSelection(testCase: TrainingTestCase) {
		// Convert TrainingDef to TrainingOption.
		val trainingOptions = testCase.trainings.map { def ->
			createDefaultTrainingOption(
				name = def.name,
				statGains = def.statGains,
				relationshipBars = ArrayList(def.relationshipBars.map { bar ->
					BarFillResult(bar.fillPercent, bar.filledSegments, bar.color)
				}),
				isRainbow = def.isRainbow
			)
		}

		val config = createDefaultConfig(
			trainingOptions = trainingOptions,
			currentStats = testCase.currentStats,
			preferredDistance = testCase.preferredDistance,
			currentDate = testCase.date,
			scenario = "URA Finale"
		)

		// Use friendship training scoring for Junior Year, otherwise use standard scoring.
		val scores = if (testCase.date.year == 1) {
			trainingOptions.associateWith { scoreFriendshipTraining(it) }
		} else {
			trainingOptions.associateWith { calculateRawTrainingScore(config, it) }
		}
		val bestTraining = scores.maxByOrNull { it.value }?.key

		assertEquals(testCase.expectedTraining, bestTraining?.name, testCase.description)
	}
}
