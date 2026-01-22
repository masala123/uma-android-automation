package com.steve1316.uma_android_automation.bot

import android.util.Log
import android.graphics.Bitmap
import org.opencv.core.Point
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Collections
import kotlinx.coroutines.*
import kotlin.math.ceil
import kotlin.math.roundToInt

import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.automation_library.data.SharedData
import com.steve1316.uma_android_automation.bot.SkillList

import com.steve1316.uma_android_automation.utils.types.BoundingBox
import com.steve1316.uma_android_automation.utils.types.SkillData
import com.steve1316.uma_android_automation.utils.types.SkillType
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.Aptitude

/**
 *
 * @param game Reference to the Game instance.
 * @param skillData This entry's skill data.
 * @param price The current price of this entry.
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
 * @property uprgrade The direct upgrade to this skill. This is an entry in the list
 * and can even be a virtual skill.
 * @property downgrade The direct downgrade to this skill. This is an entry in the list
 * and can even be a virtual skill.
 * @property upgrades Ordered list of entries which are upgrades to this skill.
 * Lowest level skills first.
 * @property downgrades Ordered list of entries which are downgrades to this skill.
 * Lowest level skills first.
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
    val discount: Double = if (skillData.cost == null || price <= 0) 0.0 else price.toDouble() / (skillData.cost).toDouble()

    // If there is a direct upgrade/downgrade version of this entry in the skill list,
    // then these variables can be set to form a pseudo linked list.
    var upgrade: SkillListEntry? = getDirectUpgrade()
    var downgrade: SkillListEntry? = getDirectDowngrade()

    /** Returns a (slightly) user friendly string of this class's key properties. */
    override fun toString(): String {
        return "{name: $name, price: $price, discount: $discount, bIsObtained: $bIsObtained, bIsVirtual: $bIsVirtual}"
    }

    /** Recursively traverses the chain of upgrades or downgrades for a skill.
     *
     * Acts like a binary tree traversal where we only walk a single branch.
     *
     * @param bWalkUpgrades Whether to walk upgrades instead of downgrades.
     * Defaults to downgrades (false).
     * @param entry The current root SkillListEntry.
     * @param res The recursive result.
     *
     * @return The list of upgrade or downgrade SkillListEntry objects.
     */
    private fun walk(
        bWalkUpgrades: Boolean = false,
        entry: SkillListEntry? = null,
        res: MutableList<SkillListEntry> = mutableListOf(),
    ): List<SkillListEntry> {
        // Base cases. If there is no upgrade for this skill, start returning.
        if (entry == null) {
            return res
        }

        res.add(entry)

        val tmpId: Int? = if (bWalkUpgrades) {
            entry.skillData.upgrade
        } else {
            entry.skillData.downgrade
        }
        if (tmpId == null) {
            return res
        }

        val tmpName: String? = game.skillPlan.skillDatabase.getSkillName(tmpId)
        if (tmpName == null) {
            return res
        }

        val tmpSkillData: SkillData? = game.skillPlan.skillDatabase.getSkillData(tmpName)
        if (tmpSkillData == null) {
            MessageLog.w(TAG, "SkillListEntry.walk: Failed to get skill data for \"$tmpName\"")
            return res
        }

        val tmpSkillListEntry: SkillListEntry = SkillListEntry(
            game = game,
            skillData = tmpSkillData,
        )!!

        return walk(
            bWalkUpgrades = bWalkUpgrades,
            entry = tmpSkillListEntry,
            res = res,
        )
    }

    /** Gets the direct upgrade to a SkillListEntry in the Skill List.
     *
     * @return The SkillListEntry that is a direct upgrade to the skill entry.
     * If no direct upgrades exist in the skill list, then NULL is returned.
     */
    private fun getDirectUpgrade(): SkillListEntry? {
        if (skillData.upgrade == null) {
            return null
        }

        val upgradeName: String? = game.skillPlan.skillDatabase.getSkillName(skillData.upgrade)
        if (upgradeName == null) {
            return null
        }

        return game.skillPlan.skillList.entries[upgradeName]
    }

    /** Gets the direct downgrade to a SkillListEntry in the Skill List.
     *
     * @return The SkillListEntry that is a direct downgrade to the skill entry.
     * If no direct downgrades exist in the skill list, then NULL is returned.
     */
    private fun getDirectDowngrade(): SkillListEntry? {
        if (skillData.downgrade == null) {
            return null
        }

        val name: String? = game.skillPlan.skillDatabase.getSkillName(skillData.downgrade)
        if (name == null) {
            return null
        }

        return game.skillPlan.skillList.entries[name]
    }

    /** Returns an ordered list of this entry's upgrades.
     *
     * @return Ordered list of SkillListEntry objects.
     */
    fun getUpgrades(): List<SkillListEntry> {
        if (skillData.upgrade == null) {
            return emptyList()
        }

        return walk(bWalkUpgrades = true, entry = this)
            .map {
                val basePrice: Int = it.skillData.cost ?: 0
                SkillListEntry(
                    game = game,
                    skillData = it.skillData,
                    price = ceil(basePrice * discount).toInt(),
                    bIsObtained = it.bIsObtained,
                    bIsVirtual = it.name !in game.skillPlan.skillList.entries,
                )
            }
            .drop(1)
    }

    /** Returns an ordered list of this entry's downgrades.
     *
     * @return Ordered list of SkillListEntry objects.
     */
    fun getDowngrades(): List<SkillListEntry> {
        if (skillData.downgrade == null) {
            return emptyList()
        }

        return walk(bWalkUpgrades = false, entry = this)
            .map {
                val basePrice: Int = it.skillData.cost ?: 0
                SkillListEntry(
                    game = game,
                    skillData = it.skillData,
                    price = ceil(basePrice * discount).toInt(),
                    bIsObtained = it.bIsObtained,
                    bIsVirtual = it.name !in game.skillPlan.skillList.entries,
                )
            }
            // Don't include this entry instance in the results.
            .drop(1)
            // We want these in order from lowest level to highest.
            .reversed()
            // We want to remove any virtual entries from downgrades list
            // since they are impossible to obtain.
            .filter { !it.bIsVirtual }
    }
}