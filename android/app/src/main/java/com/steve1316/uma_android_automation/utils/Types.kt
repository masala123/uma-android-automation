/** This file contains various custom data types used throughout the app.
 *
 * This just allows us to keep these custom types in a central location.
 * Of course, if a custom type is only used in a single class then keep it in a
 * companion object in that class. Anything else that could be used elsewhere
 * should go in here.
 *
 * If a custom type gets too complex (such as GameDate) then it should be moved
 * to its own file.
 */

package com.steve1316.uma_android_automation.utils.types

/** These are the different tiers defined in game and awarded based on fan count. */
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
        fun fromShortName(value: String): RunningStyle? = entries.find { value.uppercase() == it.shortName }
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
        fun fromShortName(value: String): DateMonth? = entries.find { value.uppercase() == it.shortName }
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
        fun fromLongName(value: String): DateYear? = entries.find { value.uppercase() == it.longName }
    }
}

// DATA CLASSES

/** A simple class used to define a bounding box on the screen.
 *
 * @param x The bounding region's bottom left corner's X-coordinate.
 * @param y The bounding region's bottom left corner's Y-coordinate.
 * @param w The bounding region's width.
 * @param h The bounding region's height.
 *
 * @property cx The bounding region's center X-coordinate.
 * @property cy The bounding region's center Y-coordinate.
 * @property center A pair containing the bounding region's center coordinates.
 */
data class BoundingBox(val x: Int, val y: Int, val w: Int, val h: Int) {
    val cx: Int
        get() = (x + (w / 2)).toInt()
    
    val cy: Int
        get() = (y + (h / 2)).toInt()

    val center: Pair<Int, Int>
        get() = Pair(cx, cy)

    override fun toString(): String {
        return "x=$x, y=$y, w=$w, h=$h"
    }

    /** Converts the parameters to an integer array.
     *
     * Mostly used for backward compatibility.
     * Does not include any of the center coordinates.
     *
     * @return An array containing x, y, w, h values.
     */
    fun toIntArray(): IntArray {
        return intArrayOf(x, y, w, h)
    }
}

enum class SkillType {
    GREEN,
    BLUE,
    YELLOW,
    RED;

    companion object {
        private val nameMap = entries.associateBy { it.name }
        private val ordinalMap = entries.associateBy { it.ordinal }

        fun fromName(value: String): SkillType? = nameMap[value.uppercase()]
        fun fromOrdinal(ordinal: Int): SkillType? = ordinalMap[ordinal]
        fun fromIconId(iconId: Int): SkillType? {
            val digits: String = iconId.toString()
            return when {
                digits.take(1) == "1" -> GREEN
                // BLUE and YELLOW types both start with "20" so we filter
                // out the BLUE types first since there are way fewer of them.
                digits.take(4) == "2002" -> BLUE
                digits.take(4) == "2003" -> BLUE
                digits.take(4) == "2011" -> BLUE
                // The rest of the skills starting with "2" are yellow
                digits.take(1) == "2" -> YELLOW
                digits.take(1) == "3" -> RED
                // At the moment the Runaway skill starts with "40". Unsure why
                // since it is a green skill.
                iconId == 40012 -> GREEN
                else -> null
            }
        }
    }
}

data class SkillData(
    val id: Int,
    val name: String,
    val description: String,
    val iconId: Int,
    val cost: Int?,
    val rarity: Int,
    val versions: List<Int>,
    val upgrade: Int?,
    val downgrade: Int?,
) {
    val type: SkillType = SkillType.fromIconId(iconId)!!
    // Some skills are for specific running styles or track distances.
    // This information is appeneded to the end of the description
    // string inside parentheses.
    // We extract this and store it in a nullable property.
    val style: RunningStyle?
        get() = RunningStyle.entries.find { description.contains("(${it.name.replace('_', ' ')})", ignoreCase = true) }

    val distance: TrackDistance?
        get() = TrackDistance.entries.find { description.contains("($it)", ignoreCase = true) }

    val bIsGold: Boolean = iconId % 10 == 2
    val bIsUnique: Boolean = iconId % 10 == 3
    val bIsNegative: Boolean = iconId % 10 == 4

    constructor(
        id: Int,
        name: String,
        description: String,
        iconId: Int,
        cost: Int?,
        rarity: Int,
        versions: String,
        upgrade: Int?,
        downgrade: Int?,
    ) : this(
        id,
        name,
        description,
        iconId,
        cost,
        rarity,
        versions
            .replace("[", "")
            .replace("]", "")
            .split(",")
            .filter { it.isNotEmpty() }
            .map { it.trim().toInt() }
            .filterNotNull(),
        upgrade,
        downgrade,
    )
}

data class SkillListEntry(
    val skillData: SkillData,
    val price: Int,
    val discount: Int,
    val bIsObtained: Boolean,
) {
    val name: String
        get() = skillData.name

    // If there is a direct upgrade/downgrade version of this entry in the skill list,
    // then these variables can be set to form a pseudo linked list.
    var upgrade: SkillListEntry? = null
    var downgrade: SkillListEntry? = null

    override fun toString(): String {
        return "name=$name, price=$price, discount=$discount, bIsObtained=$bIsObtained"
    }
}
