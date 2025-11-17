package com.steve1316.uma_android_automation.utils.types

// Classes based on fan count.
enum class FanCountClass {
    DEBUT,
    MAIDEN,
    BEGINNER,
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM,
    STAR,
    TOP_STAR,
    LEGEND;

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): FanCountClass? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): FanCountClass? = ordinalMap[ordinal]
    }
}

enum class StatName {
    SPEED,
    STAMINA,
    POWER,
    GUTS,
    WIT;

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): StatName? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): StatName? = ordinalMap[ordinal]
    }
}

enum class Aptitude {
    G,
    F,
    E,
    D,
    C,
    B,
    A,
    S;

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): Aptitude? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): Aptitude? = ordinalMap[ordinal]
    }
}

enum class RunningStyle(val shortName: String) {
    FRONT_RUNNER("FRONT"),
    PACE_CHASER("PACE"),
    LATE_SURGER("LATE"),
    END_CLOSER("END");

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): RunningStyle? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): RunningStyle? = ordinalMap[ordinal]
        fun fromShortName(value: String): RunningStyle? = entries.find { value == it.shortName }
    }
}

enum class TrackSurface {
    TURF,
    DIRT;

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): TrackSurface? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): TrackSurface? = ordinalMap[ordinal]
    }
}

enum class TrackDistance {
    SPRINT,
    MILE,
    MEDIUM,
    LONG;

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): TrackDistance? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): TrackDistance? = ordinalMap[ordinal]
    }
}

enum class Mood {
    AWFUL,
    BAD,
    NORMAL,
    GOOD,
    GREAT;

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): Mood? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): Mood? = ordinalMap[ordinal]
    }
}

enum class RaceGrade {
    DEBUT,
    MAIDEN,
    PRE_OP,
    OP,
    G3,
    G2,
    G1,
    EX;

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): RaceGrade? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): RaceGrade? = ordinalMap[ordinal]
    }
}

enum class DatePhase {
    EARLY,
    LATE;

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): DatePhase? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): DatePhase? = ordinalMap[ordinal]
    }
}

enum class DateMonth(val shortName: String) {
    JANUARY("JAN"),
    FEBRUARY("FEB"),
    MARCH("MAR"),
    APRIL("APR"),
    MAY("MAY"),
    JUNE("JUN"),
    JULY("JUL"),
    AUGUST("AUG"),
    SEPTEMBER("SEP"),
    OCTOBER("OCT"),
    NOVEMBER("NOV"),
    DECEMBER("DEC");

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): DateMonth? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): DateMonth? = ordinalMap[ordinal]
        fun fromShortName(value: String): DateMonth? = entries.find { value == it.shortName }
    }
}

enum class DateYear(val longName: String) {
    JUNIOR("JUNIOR YEAR"),
    CLASSIC("CLASSIC YEAR"),
    SENIOR("SENIOR YEAR");

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): DateYear? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): DateYear? = ordinalMap[ordinal]
        fun fromLongName(value: String): DateYear? = entries.find { value == it.longName }
    }
}

// DATA CLASSES

data class BoundingBox(val x: Int, val y: Int, val w: Int, val h: Int) {
    fun asString(): String {
        return "x=$x, y=$y, w=$w, h=$h"
    }

    fun asIntArray(): IntArray {
        return intArrayOf(x, y, w, h)
    }
}

data class GameDate(
    val year: DateYear,
    val month: DateMonth,
    val phase: DatePhase,
    val bIsFinaleSeason: Boolean,
) {
    private const val TAG: String = "GameDate"
    // Calendar starting at Jan 1 of Junior year.
    // Each year has 24 turns since each month is two phases.
    val day: Int = if (bIsFinaleSeason) {
        50
    } else {
        ((year.ordinal * (DateMonth.entries.size * 2)) + (((month.ordinal + 1) * 2) + phase.ordinal)) - 1
    }

    val bIsPreDebut: Boolean = day < 12

    constructor(
        year: String = "junior",
        month: String = "january",
        phase: String = "early",
        bIsFinaleSeason: Boolean = false,
    ) : this(
        DateYear.fromName(year)!!,// ?: DateYear.JUNIOR,
        DateMonth.fromName(month)!!,// ?: DateMonth.JANUARY,
        DatePhase.fromName(phase)!!,// ?: DatePhase.EARLY,
        bIsFinaleSeason,
    )

    companion object {
        const val TAG: String = "GameDate"
        fun fromDay(d: Int): GameDate? {
            // Day starts at 1, not 0, so we need to decrement.
            val d = d - 1
            val y = d.floorDiv(24)
            val m = (d % 24).floorDiv(2)
            val p = d % 2
            val year: DateYear? = DateYear.fromOrdinal(y)
            val month: DateMonth? = DateMonth.fromOrdinal(m)
            val phase: DatePhase? = DatePhase.fromOrdinal(p)
            if (year == null) {
                MessageLog.w(TAG, "fromDay:: Invalid year: $y")
                return null
            }
            if (month == null) {
                MessageLog.w(TAG, "fromDay:: Invalid month: $m")
                return null
            }
            if (phase == null) {
                MessageLog.w(TAG, "fromDay:: Invalid phase: $p")
                return null
            }
            return GameDate(year.name, month.name, phase.name)
        }

        fun fromDateString(s: String? = null, turnsLeft: Int? = null, imageUtils: CustomImageUtils? = null): GameDate? {
            val s: String? = s ?: imageUtils?.determineDayString()
            if (s == null || s == "") {
                MessageLog.d(TAG, "fromDateString:: Passed string is NULL or empty.")
                return null
            }

            if (s.lowercase().contains("debut")) {
                val turnsLeft: Int? = turnsLeft ?: imageUtils?.determineTurnsRemainingBeforeNextGoal()
                if (turnsLeft == null) {
                    MessageLog.e(TAG, "fromDateString:: In pre-debut but received turnsLeft=NULL. Cannot calculate date without turnsLeft.")
                    return null
                }

                if (turnsLeft <= 0) {
                    MessageLog.d(TAG, "fromDateString:: Debut race day.")
                    return GameDate(DateYear.JUNIOR.name, DateMonth.JUNE.name, DatePhase.LATE.name)
                }

                // Calculate the day number in pre-debut.
                val turnsInPreDebut = 12
                val currTurn = (turnsInPreDebut - turnsLeft).coerceIn(0, 12)
                return fromDay(currTurn)
            }

            if (s.lowercase().contains("finale")) {
                MessageLog.d(TAG, "fromDateString:: Finale season.")
                return GameDate(
                    DateYear.SENIOR.name,
                    DateMonth.DECEMBER.name,
                    DatePhase.LATE.name,
                    bIsFinaleSeason=true,
                )
            }

            val years: List<String> = DateYear.entries.map { it.name }
            val months: List<String> = DateMonth.entries.map { it.shortName }
            val phases: List<String> = DatePhase.entries.map { it.name }

            // Split the input string by whitespace.
            val parts = s.trim().split(" ")
            if (parts.size < 3) {
                MessageLog.w(TAG, "fromDateString:: Invalid date string format: $s")
                return null
            }
    
            // Extract the parts with safe indexing using default values.
            val yearPart: String = parts.getOrNull(0) ?: DateYear.SENIOR.name
            val phasePart: String = parts.getOrNull(2) ?: DatePhase.EARLY.name
            val monthPart: String = parts.getOrNull(3) ?: DateMonth.JANUARY.shortName

            // Find the best match for year using Jaro Winkler if not found in mapping.
            val year: String? = TextDetection.matchStringInList(yearPart, years)
            if (year == null) {
                MessageLog.w(TAG, "fromDateString:: Invalid date format. Could not detect YEAR from $yearPart.")
                return null
            }

            var month: String? = TextDetection.matchStringInList(monthPart, months)
            if (month == null) {
                MessageLog.w(TAG, "fromDateString:: Invalid date format. Could not detect MONTH from $monthPart.")
                return null
            }

            val phase: String? = TextDetection.matchStringInList(phasePart, phases)
            if (phase == null) {
                MessageLog.w(TAG, "fromDateString:: Invalid date format. Could not detect PHASE from $phasePart.")
                return null
            }

            month = DateMonth.fromShortName(month)?.name ?: DateMonth.JANUARY.name

            val result = GameDate(year, month, phase)
            MessageLog.d(TAG, "fromDateString:: Detected ${result.asString()}")
            return result
        }
    }

    fun isSummer(dayToCheck: Int? = null): Boolean {
        val dayToCheck = dayToCheck ?: day

        // Check Classic year summer
        if (
            dayToCheck >= GameDate("classic", "july", "early").day &&
            dayToCheck <= GameDate("classic", "august", "late").day
        ) {
            return true
        }

        // Check Senior year summer
        if (
            dayToCheck >= GameDate("senior", "july", "early").day &&
            dayToCheck <= GameDate("senior", "august", "late").day
        ) {
            return true
        }

        return false
    }

    fun asString(): String {
        if (bIsFinaleSeason) {
            return "Finale Season (ex)"
        }

        return "${year}-${month}-${phase} (${day})"
    }
}