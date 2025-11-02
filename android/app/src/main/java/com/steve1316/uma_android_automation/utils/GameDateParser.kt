package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.Game
import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl

/**
 * Utility class for parsing game date strings and converting them to structured Game.Date objects.
 */
class GameDateParser {
	/**
	 * Compute the next day's date from the current date by advancing the turn number.
	 */
	private fun computeNextDayDate(currentDate: Game.Date): Game.Date {
		val nextTurn = (currentDate.turnNumber + 1).coerceAtMost(72)
		val year = ((nextTurn - 1) / 24) + 1
		val month = (((nextTurn - 1) % 24) / 2) + 1
		val phase = if (((nextTurn - 1) % 2) == 0) "Early" else "Late"
		return Game.Date(year, phase, month, nextTurn)
	}

	/**
	 * Parses a date string from the game and converts it to a structured Game.Date object.
	 * 
	 * This function handles two types of date formats: Pre-Debut and regular date strings.
	 * 
	 * For Pre-Debut dates, the function calculates the current turn based on remaining turns
	 * and determines the month within the Pre-Debut phase (which spans 12 turns).
	 * 
	 * For regular dates, the function parses the year (Junior/Classic/Senior), phase (Early/Late),
	 * and month (Jan-Dec) components. If exact matches aren't found in the predefined mappings,
	 * it uses Jaro Winkler similarity scoring to find the best match.
	 * 
	 * @param dateString The date string to parse (e.g., "Classic Year Early Jan" or "Pre-Debut").
	 * @param imageUtils CustomImageUtils instance for determining day number in Pre-Debut phase.
	 * @param game Game instance for logging.
	 * @param tag Optional tag for logging. Defaults to "GameDateParser".
	 *
	 * @return A Game.Date object containing the parsed year, phase, month, and calculated turn number.
	 */
	fun parseDateString(
		dateString: String,
		imageUtils: CustomImageUtils,
		game: Game,
		tag: String = "GameDateParser"
	): Game.Date {
		if (dateString == "") {
			game.printToLog("[ERROR] Received date string from OCR was empty. Defaulting to \"Senior Year Early Jan\" at turn number 49.", tag = tag)
			return Game.Date(3, "Early", 1, 49)
		} else if (dateString.lowercase().contains("debut")) {
			// Special handling for the Pre-Debut phase.
			val turnsRemaining = imageUtils.determineDayForExtraRace()

			// Pre-Debut ends on Early July (turn 13), so we calculate backwards.
			// This includes the Race day.
			val totalTurnsInPreDebut = 12
			val currentTurnInPreDebut = totalTurnsInPreDebut - turnsRemaining + 1

			val month = ((currentTurnInPreDebut - 1) / 2) + 1
			return Game.Date(1, "Pre-Debut", month, currentTurnInPreDebut)
		}

		// Example input is "Classic Year Early Jan".
		val years = mapOf(
			"Junior Year" to 1,
			"Classic Year" to 2,
			"Senior Year" to 3
		)
		val months = mapOf(
			"Jan" to 1,
			"Feb" to 2,
			"Mar" to 3,
			"Apr" to 4,
			"May" to 5,
			"Jun" to 6,
			"Jul" to 7,
			"Aug" to 8,
			"Sep" to 9,
			"Oct" to 10,
			"Nov" to 11,
			"Dec" to 12
		)

		// Split the input string by whitespace.
		val parts = dateString.trim().split(" ")
		if (parts.size < 3) {
			game.printToLog("[DATE-PARSER] Invalid date string format: $dateString", tag = tag)
			return Game.Date(3, "Early", 1, 49)
		}
 
		// Extract the parts with safe indexing using default values.
		val yearPart = parts.getOrNull(0)?.let { first -> 
			parts.getOrNull(1)?.let { second -> "$first $second" }
		} ?: "Senior Year"
		val phase = parts.getOrNull(2) ?: "Early"
		val monthPart = parts.getOrNull(3) ?: "Jan"

		// Find the best match for year using Jaro Winkler if not found in mapping.
		var year = years[yearPart]
		if (year == null) {
			val service = StringSimilarityServiceImpl(JaroWinklerStrategy())
			var bestYearScore = 0.0
			var bestYear = 3

			years.keys.forEach { yearKey ->
				val score = service.score(yearPart, yearKey)
				if (score > bestYearScore) {
					bestYearScore = score
					bestYear = years[yearKey]!!
				}
			}
			year = bestYear
			game.printToLog("[DATE-PARSER] Year not found in mapping, using best match: $yearPart -> $year", tag = tag)
		}

		// Find the best match for month using Jaro Winkler if not found in mapping.
		var month = months[monthPart]
		if (month == null) {
			val service = StringSimilarityServiceImpl(JaroWinklerStrategy())
			var bestMonthScore = 0.0
			var bestMonth = 1

			months.keys.forEach { monthKey ->
				val score = service.score(monthPart, monthKey)
				if (score > bestMonthScore) {
					bestMonthScore = score
					bestMonth = months[monthKey]!!
				}
			}
			month = bestMonth
			game.printToLog("[DATE-PARSER] Month not found in mapping, using best match: $monthPart -> $month", tag = tag)
		}

		// Calculate the turn number.
		// Each year has 24 turns (12 months x 2 phases each).
		// Each month has 2 turns (Early and Late).
		val turnNumber = ((year - 1) * 24) + ((month - 1) * 2) + (if (phase == "Early") 1 else 2)

		return Game.Date(year, phase, month, turnNumber)
	}
}

