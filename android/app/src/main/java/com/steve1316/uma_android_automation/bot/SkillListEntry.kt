package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.automation_library.utils.MessageLog

import com.steve1316.uma_android_automation.utils.types.BoundingBox
import com.steve1316.uma_android_automation.utils.types.SkillData

/**
 *
 * @param game Reference to the Game instance.
 * @param skillData This entry's skill data.
 * @param price The current price of this entry as it is shown on screen.
 * @param bIsObtained Optional flag specifying whether this entry has been purchased.
 * This property can be updated later if needed.
 * @param bIsVirtual Optional flag specifying whether the entry is a virtual entry,
 * meaning it does not currently exist in the skill list but WILL exist
 * when all previous versions have been purchased.
 * This only applies to skills with in-place upgrades. (See `SkillData.bIsInPlace`)
 *
 * @property name Shortcut for `skillData.name`.
 * @property discount The calculated discount multiplier for this entry's price.
 * We calculate this using the current price and the default `skillData.cost` property.
 * @property basePrice The price of this skill, adjusted to reflect the standalone
 * cost of this skill if all previous versions of this skill have been purchased.
 * This value is recalculated in `updateBasePrice()`.
 * @property combinedEvalPt The combined evalPt values of all downgrades of this skill.
 * See `updateEvalPt()` for more info.
 */
class SkillListEntry (
    private val game: Game,
    val skillData: SkillData,
    val price: Int = -1,
    var bIsObtained: Boolean = false,
    var bIsVirtual: Boolean = false,
) {
    private val TAG: String = "[${MainActivity.loggerTag}]SkillListEntry"

    val name: String = skillData.name
    
    var combinedDowngradeEvalPt: Int = skillData.evalPt
    var combinedDowngradePrices: Int = 0
    val basePrice: Int
        get() = price - combinedDowngradePrices
    val discount: Double
        get() = if (skillData.cost == null || basePrice <= 0) 0.0 else basePrice.toDouble() / (skillData.cost).toDouble()
    val bHasSeparateDowngrade: Boolean
        get() = price != basePrice
    

    /** Updates the `basePrice` to offset this skill's previous versions prices.
     *
     * This also fixes the discount value since `discount` uses the `basePrice`
     * variable for its calculation.
     *
     * @param combinedDowngradeValues The sum of all downgrade versions of this skill.
     */
    fun updateBasePrice(combinedDowngradePrices: Int) {
        MessageLog.e("REMOVEME", "updateBasePrice: old=${this.combinedDowngradePrices}, new=${combinedDowngradePrices}")
        this.combinedDowngradePrices = combinedDowngradePrices
    }

    /** Updates the `combinedEvalPt` to include downgrade entry evalPt values.
     *
     * The skillData.evalPt for a skill assumes that all required downgrades
     * have been purchased (as if this skill is a standalone skill).
     * By adding the downgrade skills' evalPt to this one, we get a better
     * representation of this skill's worth.
     * 
     * @param combinedDowngradeValues The sum of all downgrade version's evalPt value.
     */
    fun updateEvalPt(combinedDowngradeEvalPt: Int) {
        MessageLog.e("REMOVEME", "updateEvalPt: old=${this.combinedDowngradeEvalPt}, new=${skillData.evalPt + combinedDowngradeEvalPt}")
        this.combinedDowngradeEvalPt = skillData.evalPt + combinedDowngradeEvalPt
    }

    /** Returns a (slightly) user friendly string of this class's key properties. */
    override fun toString(): String {
        return "{name: $name, price: $price, discount: $discount, bIsObtained: $bIsObtained, bIsVirtual: $bIsVirtual}"
    }
}