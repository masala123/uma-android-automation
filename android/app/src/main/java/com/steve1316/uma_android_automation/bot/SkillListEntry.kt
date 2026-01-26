package com.steve1316.uma_android_automation.bot

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

import org.opencv.core.Point

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.automation_library.utils.MessageLog

import com.steve1316.uma_android_automation.utils.DoublyLinkedList
import com.steve1316.uma_android_automation.utils.DoublyLinkedListNode

import com.steve1316.uma_android_automation.components.ButtonSkillUp

import com.steve1316.uma_android_automation.utils.types.BoundingBox
import com.steve1316.uma_android_automation.utils.types.SkillData
import com.steve1316.uma_android_automation.utils.types.SkillType
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.Aptitude

class SkillListEntry(
    private val game: Game,
    val skillData: SkillData,
    var bIsObtained: Boolean = false,
    var bIsVirtual: Boolean = false,
    // Pointers for linked-list style navigation.
    var prev: SkillListEntry? = null,
    var next: SkillListEntry? = null,
) {
    private val TAG: String = "[${MainActivity.loggerTag}]SkillListEntry"

    private val EVALUATION_POINT_APTITUDE_RATIO_MAP: Map<Aptitude, Double> = mapOf(
        Aptitude.S to 1.1,
        Aptitude.A to 1.1,
        Aptitude.B to 0.9,
        Aptitude.C to 0.9,
        Aptitude.D to 0.8,
        Aptitude.E to 0.8,
        Aptitude.F to 0.8,
        Aptitude.G to 0.7,
    )

    private val DISCOUNT_VALUES: List<Double> = listOf(
        0.0,
        0.1,
        0.2,
        0.3,
        0.35,
        0.4,
    )

    val name: String = skillData.name

    // Skills can't be cheaper than their max discount.
    private val minScreenPrice: Int = (skillData.cost * (1.0 - DISCOUNT_VALUES.last())).roundToInt()
    // For skills that aren't in-place upgradable, their screen price also includes any
    // previous unpurchased versions prices. The skillData.cost that we scraped does not
    // account for this and is only the price of the skill if its upgrades have all
    // been purchased. Double this cost is a safe margin of error since no lower
    // versions are more expensive than their upgraded variant.
    private val maxScreenPrice: Int = if (skillData.bIsInPlace) skillData.cost else skillData.cost * 2
    // This is the price tag shown on the screen for the skill.
    // NOTE: We need to make sure to call updateScreenPrice() after fully
    // setting up the entire upgrade chain linked list.
    var screenPrice: Int = maxScreenPrice
    // Copy of the screen price that is only ever modified in `updateScreenPrice`.
    // We use this so that we have a baseline for what the screen price was
    // when we originally read it with OCR.
    // This is necessary for some calculations since `screenPrice` is modified
    // in multiple functions.
    private var originalScreenPrice: Int = screenPrice

    val price: Int
        get() = calculatePrice()

    // The adjusted price without a discount applied.
    val rawPrice: Int
        get() = ceil(price.toDouble() / (1.0 - discount)).toInt()

    val discount: Double
        get() = calculateDiscount()

    val evaluationPoints: Int
        get() = calculateEvaluationPoints()
    
    val evaluationPointRatio: Double
        get() = calculateEvaluationPointRatio()

    val bIsAvailable: Boolean
        get() = !bIsObtained && !bIsVirtual
    
    // Wrappers around commonly used SkillData values
    // so we don't have to directly reference skillData.
    val bIsGold: Boolean
        get() = skillData.bIsGold
    val bIsUnique: Boolean
        get() = skillData.bIsUnique
    val bIsInheritedUnique: Boolean
        get() = skillData.bIsInheritedUnique
    val bIsNegative: Boolean
        get() = skillData.bIsNegative
    val bIsInPlace: Boolean
        get() = skillData.bIsInPlace
    val skillType: SkillType
        get() = skillData.type
    val runningStyle: RunningStyle?
        get() = skillData.runningStyle
    val trackDistance: TrackDistance?
        get() = skillData.trackDistance
    val communityTier: Int?
        get() = skillData.communityTier
    val baseCost: Int
        get() = skillData.cost

    init {
        // Update linked list pointers if they were passed in.
        val prev: SkillListEntry? = prev
        if (prev != null) {
            prev.next = this
        }

        val next: SkillListEntry? = next
        if (next != null) {
            next.prev = this
        }
    }

    /** Rounds a discount value to the nearest predetermined valid value.
     *
     * Until hint level 3, discount value increases by 10%.
     * After this point, value only increases by 5% up to a max of 40%.
     *
     * @return The rounded discount value.
     */
    private fun Double.roundDiscount(): Double {
        return DISCOUNT_VALUES.minBy { abs(it - this) }
    }

    private fun calculateDiscount(): Double {
        if (screenPrice <= 0) {
            return 0.0
        }

        val res: Double = 1.0 - (price.toDouble() / skillData.cost.toDouble())
        return res.roundDiscount()
    }

    private fun calculatePrice(): Int {
        val prev: SkillListEntry? = prev
        if (prev == null) {
            return screenPrice
        }

        // For skills that have downgrades as separate entries in the skill list,
        // we need to subtract the downgrade's price from our screenPrice.
        // We need to calculate this adjusted value in order for
        // `calculateDiscount()` to be accurate.
        // This is because the skills in this type of upgrade chain can have
        // different hint levels and thus different discount values.
        if (!bIsInPlace && !prev.bIsObtained && !prev.bIsVirtual) {
            val adjustedPrice: Int = (screenPrice - prev.price).coerceIn(0, 500)
            return adjustedPrice
        }

        return screenPrice
    }

    private fun getRunningStyleAptitudeEvaluationModifier(): Double? {
        val runningStyle: RunningStyle = runningStyle ?: return null
        val aptitude: Aptitude = game.trainee.checkRunningStyleAptitude(runningStyle)
        return EVALUATION_POINT_APTITUDE_RATIO_MAP[aptitude]
    }

    private fun getTrackDistanceAptitudeEvaluationModifier(): Double? {
        val trackDistance: TrackDistance = trackDistance ?: return null
        val aptitude: Aptitude = game.trainee.checkTrackDistanceAptitude(trackDistance)
        return EVALUATION_POINT_APTITUDE_RATIO_MAP[aptitude]
    }

    private fun calculateEvaluationPoints(): Int {
        var res: Int = skillData.evalPt

        val prev: SkillListEntry? = prev
        if (prev != null && prev.bIsAvailable) {
            res += prev.evaluationPoints
        }

        val modifier: Double = getRunningStyleAptitudeEvaluationModifier() ?:
            getTrackDistanceAptitudeEvaluationModifier() ?:
            1.0

        return (res * modifier).roundToInt()
    }

    private fun calculateEvaluationPointRatio(): Double {
        return evaluationPoints.toDouble() / screenPrice.toDouble()
    }

    private fun clampValueForScreenPrice(value: Int): Int {
        if (value in minScreenPrice..maxScreenPrice) {
            return value
        }

        val prev: SkillListEntry? = prev
        if (bIsInPlace || prev == null) {
            return skillData.cost
        }

        return if (prev.bIsObtained) skillData.cost else skillData.cost * 2
    }

    /** Manually set the screen price.
     *
     * This also updates the `originalScreenPrice` which is not modified
     * anywhere else.
     */
    fun updateScreenPrice(value: Int) {
        val clampedValue: Int = clampValueForScreenPrice(value)
        screenPrice = clampedValue
        originalScreenPrice = clampedValue

        // Need to manually push changes to other in-place versions.
        if (bIsInPlace) {
            val next: SkillListEntry = next ?: return
            val nextBaseCost: Int = next.baseCost ?: return
            next.updateScreenPrice(((1.0 - discount) * nextBaseCost.toDouble()).roundToInt())
        }
    }

    fun onDowngradePurchased(entry: SkillListEntry) {
        if (bIsInPlace) {
            // Purchasing an in-place upgrade skill will make the next upgrade
            // available in the skill list. This entry is no longer virtual.
            bIsVirtual = false
            val prev: SkillListEntry? = prev
            if (prev != null) {
                screenPrice = (skillData.cost.toDouble() * (1.0 - prev.discount)).roundToInt()
            }
        } else {
            // Otherwise if the two skills are separate entries in the skill list,
            // then we need to subtract the purchased skill's price from our
            // screen price since that is how it is displayed in game.

            // We always want to calculate from the original screen price.
            // Otherwise, we may end up compounding this subtraction in
            // successive calls to this function.
            screenPrice = (originalScreenPrice - entry.price).coerceIn(0, 500)

            // Propagate forward.
            val next: SkillListEntry? = next
            if (next != null) {
                next.onDowngradePurchased(this)
            }
        }
    }

    fun onUpgradePurchased(entry: SkillListEntry) {
        // Whenever a higher version of a skill is purchased,
        // all lower versions are automatically purchased.
        bIsObtained = true

        // For in-place version chains, purchasing a higher version
        // replaces the older version in the list. Make sure we reflect this
        // change by updating replaced entries to be virtual since they no
        // longer exist in the skill list.
        if (bIsInPlace) {
            bIsVirtual = true
        }

        // Propagate backward.
        val prev: SkillListEntry? = prev
        if (prev != null) {
            prev.onUpgradePurchased(this)
        }
    }

    fun buy(skillUpLocation: Point? = null) {
        if (skillUpLocation != null) {
            game.tap(
                skillUpLocation.x,
                skillUpLocation.y,
                ButtonSkillUp.template.path,
            )
        }

        bIsObtained = true

        // Propagate changes backward.
        val prev: SkillListEntry? = prev
        if (prev != null) {
            prev.onUpgradePurchased(this)
        }

        // Propagate changes forward.
        val next: SkillListEntry? = next
        if (next != null) {
            next.onDowngradePurchased(this)
        }
    }

    fun getFirstAvailableDowngrade(): SkillListEntry? {
        var entry: SkillListEntry? = prev
        while (entry != null) {
            if (entry.bIsAvailable) {
                return entry
            }
            entry = entry.prev
        }

        return null
    }

    fun getDowngrades(): List<SkillListEntry> {
        val result: MutableList<SkillListEntry> = mutableListOf()
        var entry: SkillListEntry? = this
        while (entry != null) {
            // Don't add ourself to the results.
            if (entry != this) {
                result.add(entry)
            }
            entry = entry.prev
        }
        // Reverse so list is in lowest to highest version order.
        return result.reversed().toList()
    }

    fun getDowngradeNames(): List<String> {
        return getDowngrades().map { it.name }
    }

    fun getDowngradesUntil(lastName: String): List<SkillListEntry> {
        val result: MutableList<SkillListEntry> = mutableListOf(this)

        if (name == lastName) {
            return result.toList()
        }

        val downgrades: List<SkillListEntry> = getDowngrades()
        var foundMatch: Boolean = false
        for (entry in downgrades.reversed()) {
            result.add(entry)
            if (entry.name == lastName) {
                return result.toList()
            }
        }

        return emptyList()
    }

    fun getDowngradeNamesUntil(lastName: String): List<String> {
        return getDowngradesUntil(lastName).map { it.name }
    }

    fun getUpgrades(): List<SkillListEntry> {
        val result: MutableList<SkillListEntry> = mutableListOf()
        var entry: SkillListEntry? = this
        while (entry != null) {
            // Don't add ourself to the results.
            if (entry != this) {
                result.add(entry)
            }
            entry = entry.next
        }
        return result.toList()
    }

    fun getUpgradeNames(): List<String> {
        return getUpgrades().map { it.name }
    }

    fun getUpgradesUntil(lastName: String): List<SkillListEntry> {
        val result: MutableList<SkillListEntry> = mutableListOf(this)

        if (name == lastName) {
            return result.toList()
        }

        val upgrades: List<SkillListEntry> = getUpgrades()
        var foundMatch: Boolean = false
        for (entry in upgrades) {
            result.add(entry)
            if (entry.name == lastName) {
                return result.toList()
            }
        }

        return emptyList()
    }

    fun getUpgradeNamesUntil(lastName: String): List<String> {
        return getUpgradesUntil(lastName).map { it.name }
    }

    fun getVersions(): List<SkillListEntry> {
        return getDowngrades() + this + getUpgrades()
    }

    fun getVersionNames(): List<String> {
        return getVersions().map { it.name }
    }

    /** Returns a (slightly) user friendly string of this class's key properties. */
    override fun toString(): String {
        val evaluationPointRatioString: String = "%.2f".format(evaluationPointRatio)
        return "{" +
            "name: \"${name}\", " +
            "virtual: $bIsVirtual, " +
            "obtained: $bIsObtained, " +
            "price: $price ($screenPrice), " +
            "discount: ${(discount * 100).roundToInt()}%, " +
            "evalPt: $evaluationPoints ($evaluationPointRatioString / pt)" +
            "}"
    }
}
