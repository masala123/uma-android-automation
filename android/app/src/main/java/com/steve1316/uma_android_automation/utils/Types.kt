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
