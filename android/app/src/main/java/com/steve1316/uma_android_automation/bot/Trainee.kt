package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import org.opencv.core.Point
import kotlin.enums.enumEntries
import kotlin.math.abs

import com.steve1316.automation_library.utils.MessageLog

import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.uma_android_automation.utils.types.StatName
import com.steve1316.uma_android_automation.utils.types.Aptitude
import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.TrackSurface
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.Mood
import com.steve1316.uma_android_automation.utils.types.FanCountClass
import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.IconMoodGreat
import com.steve1316.uma_android_automation.components.IconMoodGood
import com.steve1316.uma_android_automation.components.IconMoodNormal
import com.steve1316.uma_android_automation.components.IconMoodBad
import com.steve1316.uma_android_automation.components.IconMoodAwful
import com.steve1316.uma_android_automation.components.LabelStatDistance
import com.steve1316.uma_android_automation.components.LabelStatTrackSurface
import com.steve1316.uma_android_automation.components.LabelStatStyle

class Trainee {
    companion object {
        const val TAG: String = "Trainee"

        data class Stats(
            var speed: Int = -1,
            var stamina: Int = -1,
            var power: Int = -1,
            var guts: Int = -1,
            var wit: Int = -1,
        ) {
            fun setStat(statName: StatName, value: Int) {
                when (statName) {
                    StatName.SPEED -> speed = value
                    StatName.STAMINA -> stamina = value
                    StatName.POWER -> power = value
                    StatName.GUTS -> guts = value
                    StatName.WIT -> wit = value
                }
            }

            override fun toString(): String {
                return "Spd=$speed, Sta=$stamina, Pow=$power, Gut=$guts, Wit=$wit"
            }

            fun toIntArray(): IntArray {
                return intArrayOf(speed, stamina, power, guts, wit)
            }

            fun asMap(): Map<StatName, Int> {
                return mapOf(
                    StatName.SPEED to speed,
                    StatName.STAMINA to stamina,
                    StatName.POWER to power,
                    StatName.GUTS to guts,
                    StatName.WIT to wit,
                )
            }
        }
    }

    private val preferredDistanceOverride: String = SettingsHelper.getStringSetting("training", "preferredDistanceOverride")
    private val statTargetsByDistance = mutableMapOf<TrackDistance, Stats>()

    val stats: Stats = Stats()
    val trackSurfaceAptitudes: MutableMap<TrackSurface, Aptitude> = mutableMapOf(
        TrackSurface.TURF to Aptitude.G,
        TrackSurface.DIRT to Aptitude.G,
    )
    val trackDistanceAptitudes: MutableMap<TrackDistance, Aptitude> = mutableMapOf(
        TrackDistance.SPRINT to Aptitude.G,
        TrackDistance.MILE to Aptitude.G,
        TrackDistance.MEDIUM to Aptitude.G,
        TrackDistance.LONG to Aptitude.G,
    )
    val runningStyleAptitudes: MutableMap<RunningStyle, Aptitude> = mutableMapOf(
        RunningStyle.FRONT_RUNNER to Aptitude.G,
        RunningStyle.PACE_CHASER to Aptitude.G,
        RunningStyle.LATE_SURGER to Aptitude.G,
        RunningStyle.END_CLOSER to Aptitude.G,
    )
    var skillPoints: Int = 120
    var fans: Int = 1
    var mood: Mood = Mood.NORMAL

    var bHasUpdatedAptitudes: Boolean = false
    var bHasUpdatedStats: Boolean = false
    var bHasUpdatedSkillPoints: Boolean = false
    var bTemporaryRunningStyleAptitudesUpdated: Boolean = false
    var bHasSetRunningStyle: Boolean = false

    var fanCountClass: FanCountClass = FanCountClass.DEBUT

    val bIsInitialized: Boolean
        get() = bHasUpdatedAptitudes && bHasUpdatedStats && bHasUpdatedSkillPoints

    /** Calculates the highest weighted key based on aptitude for the passed Enum
    *
    *   See trackDistance getter for example.
    */
    inline fun <reified T : Enum<T>> getMaxAptitude(
        aptitudeMap: MutableMap<T, Aptitude>,
        defaultMaxKey: T,
    ): T {
        var maxKey = defaultMaxKey
        var maxVal: Aptitude = Aptitude.G

        for ((key, aptitude) in aptitudeMap) {
            if (aptitude > maxVal) {
                // If aptitude is higher, pick it.
                maxKey = key
                maxVal = aptitude
            } else if (aptitude == maxVal && key < maxKey) {
                // If aptitudes are the same, we want to pick the higher
                // priority key.
                maxKey = key
                maxVal = aptitude
            }
        }

        return maxKey
    }

    val trackSurface: TrackSurface
        get() = getMaxAptitude<TrackSurface>(
            aptitudeMap=trackSurfaceAptitudes,
            defaultMaxKey=TrackSurface.TURF,
        )

    val trackDistance: TrackDistance
        get() = TrackDistance.fromName(preferredDistanceOverride) ?: getMaxAptitude<TrackDistance>(
            aptitudeMap=trackDistanceAptitudes,
            defaultMaxKey=TrackDistance.MEDIUM,
        )
    
    val runningStyle: RunningStyle
        get() = getMaxAptitude<RunningStyle>(
            aptitudeMap=runningStyleAptitudes,
            defaultMaxKey=RunningStyle.FRONT_RUNNER,
        )

    init {
        setStatTargetsByDistances()
    }

    fun getStat(statName: StatName): Int {
        return when (statName) {
            StatName.SPEED -> stats.speed
            StatName.STAMINA -> stats.stamina
            StatName.POWER -> stats.power
            StatName.GUTS -> stats.guts
            StatName.WIT -> stats.wit
        }
    }

    fun getStatTargetsByDistance(distance: TrackDistance? = null): Map<StatName, Int> {
        // If distance is NULL, we want to use the calculated preferred track distance.
        val distance: TrackDistance = distance ?: trackDistance

        // Return a default set of stat targets if the distance does not exist in the mapping.
        if (distance !in statTargetsByDistance) {
            return mapOf(
                StatName.SPEED to 600,
                StatName.STAMINA to 600,
                StatName.POWER to 600,
                StatName.GUTS to 300,
                StatName.WIT to 300,
            )
        }

        return statTargetsByDistance[distance]!!.asMap()
    }

    fun setTraineeStats(
        speed: Int? = null,
        stamina: Int? = null,
        power: Int? = null,
        guts: Int? = null,
        wit: Int? = null,
    ) {
        if (speed != null) {
            stats.speed = speed
        }
        if (stamina != null) {
            stats.stamina = stamina
        }
        if (power != null) {
            stats.power = power
        }
        if (guts != null) {
            stats.guts = guts
        }
        if (wit != null) {
            stats.wit = wit
        }
    }

    fun setRunningStyleAptitude(runningStyle: RunningStyle, aptitude: Aptitude) {
        runningStyleAptitudes[runningStyle] = aptitude
    }

    /** Returns the offset integer aptitude for the track surface.
    * G=-5,F=-4,E=-3,D=-2,C=-1,B=0,A=1,S=2
    * Values offset by 5 for easier calculations later on.
    */
    fun checkTrackSurfaceAptitude(trackSurface: TrackSurface): Aptitude {
        return trackSurfaceAptitudes[trackSurface] ?: Aptitude.G
    }

    /** Returns the offset integer aptitude for the track distance.
    * G=-5,F=-4,E=-3,D=-2,C=-1,B=0,A=1,S=2
    * Values offset by 5 for easier calculations later on.
    */
    fun checkTrackDistanceAptitude(trackDistance: TrackDistance): Aptitude {
        return trackDistanceAptitudes[trackDistance] ?: Aptitude.G
    }

    /** Returns the offset integer aptitude for the running style.
    * G=-5,F=-4,E=-3,D=-2,C=-1,B=0,A=1,S=2
    * Values offset by 5 for easier calculations later on.
    */
    fun checkRunningStyleAptitude(runningStyle: RunningStyle): Aptitude {
        return runningStyleAptitudes[runningStyle] ?: Aptitude.G
    }

    inline fun <reified T : Enum<T>> findAptitudesInBitmap(
        imageUtils: CustomImageUtils,
        label: ComponentInterface,
    ): Map<T, Aptitude>? {
        val result = mutableMapOf<T, Aptitude>()

        val bitmap: Bitmap = imageUtils.getSourceBitmap()
        val point: Point? = label.find(imageUtils = imageUtils).first
        if (point == null) {
            MessageLog.e(TAG, "findAptitudesInBitmap<${T::class.simpleName}>:: point is NULL.")
            return null
        }

        enumEntries<T>().forEachIndexed { index, option ->
            val croppedBitmap: Bitmap? = imageUtils.createSafeBitmap(
                bitmap,
                imageUtils.relX(point.x, 108 + (index * 190)),
                imageUtils.relY(point.y, -25),
                176,
                52,
                "Trainee::findAptitudesInBitmap<${T::class.simpleName}>:: crop bitmap.",
            )
            if (croppedBitmap == null) {
                MessageLog.e(TAG, "findAptitudesInBitmap<${T::class.simpleName}>:: Failed to create cropped bitmap: ${option}.")
                return@forEachIndexed
            }
            for (aptitude in Aptitude.entries) {
                if (imageUtils.findImageWithBitmap("stat_aptitude_${aptitude.name}", croppedBitmap) != null) {
                    result[option] = aptitude
                    break
                }
            }
        }

        return result.toMap()
    }

    private fun updateTrackSurfaceAptitudes(imageUtils: CustomImageUtils) {
        val aptitudes = findAptitudesInBitmap<TrackSurface>(
            imageUtils = imageUtils,
            label = LabelStatTrackSurface,
        )

        if (aptitudes == null) {
            return
        }

        for ((key, value) in aptitudes) {
            trackSurfaceAptitudes[key] = value
        }
    }

    private fun updateTrackDistanceAptitudes(imageUtils: CustomImageUtils) {
        val aptitudes = findAptitudesInBitmap<TrackDistance>(
            imageUtils = imageUtils,
            label = LabelStatDistance,
        )

        if (aptitudes == null) {
            return
        }

        for ((key, value) in aptitudes) {
            trackDistanceAptitudes[key] = value
        }
    }

    private fun updateRunningStyleAptitudes(imageUtils: CustomImageUtils) {
        val aptitudes = findAptitudesInBitmap<RunningStyle>(
            imageUtils = imageUtils,
            label = LabelStatStyle,
        )

        if (aptitudes == null) {
            return
        }

        for ((key, value) in aptitudes) {
            runningStyleAptitudes[key] = value
        }
    }

    /** Updates all aptitudes for trainee.
    *
    *   Requires the Umamusume Details dialog to be opened.
    */
    fun updateAptitudes(imageUtils: CustomImageUtils) {
        updateTrackSurfaceAptitudes(imageUtils = imageUtils)
        updateTrackDistanceAptitudes(imageUtils = imageUtils)
        updateRunningStyleAptitudes(imageUtils = imageUtils)

        bHasUpdatedAptitudes = true
    }

    fun updateSkillPoints(imageUtils: CustomImageUtils) {
        val res = imageUtils.determineSkillPoints()
        if (res != -1) {
            skillPoints = res
        }

        bHasUpdatedSkillPoints = skillPoints != -1
    }

    fun updateStats(imageUtils: CustomImageUtils) {
        val statMapping: Map<StatName, Int> = imageUtils.determineStatValues()

        // It is possible that we misread a stat value. We want to make sure we
        // don't update our stats if they change too wildly from the previous values.
        for ((statName, newValue) in statMapping) {
            val oldValue = getStat(statName)
            val diff = abs(newValue - oldValue)
            // If our previous stat value is <= 0, that means we havent set it yet.
            if (oldValue <= 0 || diff < 150) {
                stats.setStat(statName, newValue)
                bHasUpdatedStats = true
            } else {
                MessageLog.w(TAG, "New $statName stat value has changed too much since last update: old=$oldValue, new=$newValue")
            }
        }
    }

    fun checkMood(imageUtils: CustomImageUtils): Mood? {
        return when {
            IconMoodAwful.check(imageUtils = imageUtils) -> Mood.AWFUL
            IconMoodBad.check(imageUtils = imageUtils) -> Mood.BAD
            IconMoodNormal.check(imageUtils = imageUtils) -> Mood.NORMAL
            IconMoodGood.check(imageUtils = imageUtils) -> Mood.GOOD
            IconMoodGreat.check(imageUtils = imageUtils) -> Mood.GREAT
            else -> null
        }
    }

    fun updateMood(imageUtils: CustomImageUtils) {
        // If checkMood returns NULL, then make no change to the mood state.
        mood = checkMood(imageUtils) ?: mood
    }

    /**
	 * Sets up stat targets for different race distances by reading values from SQLite settings. These targets are used to determine training priorities based on the expected race distance.
	 */
	fun setStatTargetsByDistances() {
		for (trackDistance in TrackDistance.entries) {
            val newStats = Stats()
            for (statName in StatName.entries) {
                val statNameString = statName.name.lowercase()
                val trackDistanceString = trackDistance.name.lowercase().replaceFirstChar { it.uppercase() }
                val target: Int = SettingsHelper.getIntSetting(
                    "trainingStatTarget",
                    "training${trackDistanceString}StatTarget_${statNameString}StatTarget",
                )
                newStats.setStat(statName, target)
            }
            statTargetsByDistance[trackDistance] = newStats
        }
	}

    fun getAptitudesString(): String {
        return "TrackSurface: $trackSurface\nTrackDistance: $trackDistance\nRunningStyle: $runningStyle"
    }

    fun getStatsString(): String {
        return stats.toString()
    }

    override fun toString(): String {
        val aptitudesString = getAptitudesString()
        val statsString = getStatsString()
        return "Aptitudes: $aptitudesString" +
            "\nStats: $statsString" +
            "\nSkill Points: $skillPoints" +
            "\nMood: $mood" +
            "\nFans: $fans" +
            "\nFanCountClass: $fanCountClass"
    }
}
