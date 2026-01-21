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
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.uma_android_automation.utils.SQLiteSettingsManager
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.automation_library.data.SharedData
import com.steve1316.uma_android_automation.bot.SkillDatabase
import com.steve1316.uma_android_automation.bot.SkillList

import com.steve1316.uma_android_automation.utils.types.BoundingBox
import com.steve1316.uma_android_automation.utils.types.SkillData
import com.steve1316.uma_android_automation.utils.types.SkillType
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.Aptitude

import com.steve1316.uma_android_automation.components.*

private const val USE_MOCKED_DATA: Boolean = true

class SkillPlan (private val game: Game) {
    val skillList: SkillList = SkillList(game)
    val skillDatabase: SkillDatabase = SkillDatabase(game)

    var skillsToBuy: List<SkillToBuy> = listOf()

    // Get user settings for skill plans.
    val enablePreFinalsSkillPlan = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsSkillPlan")
    val preFinalsSpendingStrategy = SettingsHelper.getStringSetting("skills", "preFinalsSpendingStrategy")
    val enablePreFinalsBuyInheritedSkills = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsBuyInheritedSkills")
    val enablePreFinalsBuyNegativeSkills = SettingsHelper.getBooleanSetting("skills", "enablePreFinalsBuyNegativeSkills")
    private val preFinalsSkillPlanJson = SettingsHelper.getStringSetting("skills", "preFinalsSkillPlan")
    
    val enableCareerCompleteSkillPlan = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteSkillPlan")
    val careerCompleteSpendingStrategy = SettingsHelper.getStringSetting("skills", "careerCompleteSpendingStrategy")
    val enableCareerCompleteBuyInheritedSkills = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteBuyInheritedSkills")
    val enableCareerCompleteBuyNegativeSkills = SettingsHelper.getBooleanSetting("skills", "enableCareerCompleteBuyNegativeSkills")
    private val careerCompleteSkillPlanJson = SettingsHelper.getStringSetting("skills", "careerCompleteSkillPlan")

    // Get other relevant user settings.
    private val userSelectedTrackDistanceOverrideString = SettingsHelper.getStringSetting("training", "preferredDistanceOverride")
    private val userSelectedRunningStyleString = SettingsHelper.getStringSetting("racing", "originalRaceStrategy")

    private val userPlannedPreFinalsSkills: List<String> = loadUserPlannedPreFinalsSkills()
    private val userPlannedCareerCompleteSkills: List<String> = loadUserPlannedCareerCompleteSkills()

    private val skillPlanSettingsPreFinals: SkillPlanSettings = SkillPlanSettings(
        enablePreFinalsSkillPlan,
        userPlannedPreFinalsSkills,
        preFinalsSpendingStrategy,
        enablePreFinalsBuyInheritedSkills,
        enablePreFinalsBuyNegativeSkills,
    )

    private val skillPlanSettingsCareerComplete: SkillPlanSettings = SkillPlanSettings(
        enableCareerCompleteSkillPlan,
        userPlannedCareerCompleteSkills,
        careerCompleteSpendingStrategy,
        enableCareerCompleteBuyInheritedSkills,
        enableCareerCompleteBuyNegativeSkills,
    )

    var skillPlanSettings: SkillPlanSettings = skillPlanSettingsPreFinals

    private var skillListEntries: MutableMap<String, SkillListEntry> = mutableMapOf()
    private var remainingSkillListEntries: MutableMap<String, SkillListEntry> = mutableMapOf()
    private var remainingSkillPoints: Int = 0

    companion object {
        private val TAG: String = "[${MainActivity.loggerTag}]SkillPlan"

        enum class SpendingStrategy {
            DEFAULT,
            OPTIMIZE_SKILLS,
            OPTIMIZE_RANK;

            companion object {
                private val nameMap = entries.associateBy { it.name }
                private val ordinalMap = entries.associateBy { it.ordinal }

                fun fromName(value: String): SpendingStrategy? = nameMap[value.uppercase()]
                fun fromOrdinal(ordinal: Int): SpendingStrategy? = ordinalMap[ordinal]
            }
        }

        // Store each skill plan in a data class to make it easier to manage the settings.
        data class SkillPlanSettings(
            val enabled: Boolean,
            val skillPlan: List<String>,
            val spendingStrategy: SpendingStrategy,
            val buyInheritedSkills: Boolean,
            val buyNegativeSkills: Boolean,
        ) {
            constructor(
                enabled: Boolean,
                skillPlan: List<String>,
                spendingStrategy: String,
                buyInheritedSkills: Boolean,
                buyNegativeSkills: Boolean,
            ) : this(
                enabled,
                skillPlan,
                SpendingStrategy.fromName(spendingStrategy) ?: SpendingStrategy.DEFAULT,
                buyInheritedSkills,
                buyNegativeSkills,
            )
        }

        data class SkillToBuy(
            val name: String,
            var numUpgrades: Int = 0,
            var totalPrice: Int = 0,
        )

        data class SkillsToBuyCommonReturnData (
            val remainingSkillListEntries: Map<String, SkillListEntry>,
            val remainingSkillPoints: Int,
            val skillsToBuy: List<SkillToBuy>,
            val earlyExit: Boolean = false,
        )
    }

    private fun loadUserPlannedPreFinalsSkills(): List<String> {
        if (!enablePreFinalsSkillPlan) {
            MessageLog.i(TAG, "[SKILLS] Pre-Finals skill plan is disabled, returning empty planned skills list...")
            return emptyList()
        }

        return try {
            if (preFinalsSkillPlanJson.isEmpty() || preFinalsSkillPlanJson == "[]") {
                MessageLog.i(TAG, "[SKILLS] User-selected pre-finals skill plan is empty. Returning empty planned skills list...")
                return emptyList()
            }

            val jsonArray = JSONArray(preFinalsSkillPlanJson)
            val plannedSkills = mutableListOf<String>()

            for (i in 0 until jsonArray.length()) {
                val skillObj = jsonArray.getJSONObject(i)
                plannedSkills.add(skillObj.getString("name"))
            }

            MessageLog.i(TAG, "[SKILLS] Successfully loaded ${plannedSkills.size} user-selected pre-finals planned skills from settings.")
            plannedSkills
        } catch (e: Exception) {
            MessageLog.e(TAG, "[SKILLS] Failed to parse user-selected pre-finals skill plan JSON: ${e.message}. Returning empty list...")
            emptyList()
        }
    }

    private fun loadUserPlannedCareerCompleteSkills(): List<String> {
        if (!enableCareerCompleteSkillPlan) {
            MessageLog.i(TAG, "[SKILLS] Career complete skill plan is disabled, returning empty planned skills list...")
            return emptyList()
        }

        return try {
            if (careerCompleteSkillPlanJson.isEmpty() || careerCompleteSkillPlanJson == "[]") {
                MessageLog.i(TAG, "[SKILLS] User-selected career complete skill plan is empty. Returning empty planned skills list...")
                return emptyList()
            }

            val jsonArray = JSONArray(careerCompleteSkillPlanJson)
            val plannedSkills = mutableListOf<String>()

            for (i in 0 until jsonArray.length()) {
                val skillObj = jsonArray.getJSONObject(i)
                plannedSkills.add(skillObj.getString("name"))
            }

            MessageLog.i(TAG, "[SKILLS] Successfully loaded ${plannedSkills.size} user-selected career complete planned skills from settings.")
            plannedSkills
        } catch (e: Exception) {
            MessageLog.e(TAG, "[SKILLS] Failed to parse user-selected career complete skill plan JSON: ${e.message}. Returning empty list...")
            emptyList()
        }
    }

    /**
     * Detects and handles any dialog popups.
     *
     * To prevent the bot moving too fast, we add a 500ms delay to the
     * exit of this function whenever we close the dialog.
     * This gives the dialog time to close since there is a very short
     * animation that plays when a dialog closes.
     *
     * @return A pair of a boolean and a nullable DialogInterface.
     * The boolean is true when a dialog has been handled by this function.
     * The DialogInterface is the detected dialog, or NULL if no dialogs were found.
     */
    fun handleDialogs(): Pair<Boolean, DialogInterface?> {
        val dialog: DialogInterface? = DialogUtils.getDialog(imageUtils = game.imageUtils)
        if (dialog == null) {
            return Pair(false, null)
        }

        when (dialog.name) {
            "skill_list_confirmation" -> dialog.ok(game.imageUtils)
            "skills_learned" -> dialog.close(game.imageUtils)
            "umamusume_details" -> {
                val prevTrackSurface = game.trainee.trackSurface
                val prevTrackDistance = game.trainee.trackDistance
                val prevRunningStyle = game.trainee.runningStyle
                game.trainee.updateAptitudes(imageUtils = game.imageUtils)
                game.trainee.bTemporaryRunningStyleAptitudesUpdated = false

                if (game.trainee.runningStyle != prevRunningStyle) {
                    // Reset this flag since our preferred running style has changed.
                    game.trainee.bHasSetRunningStyle = false
                }

                dialog.close(imageUtils = game.imageUtils)
            }
            else -> {
                return Pair(false, dialog)
            }
        }

        game.wait(0.5, skipWaitingForLoading = true)
        return Pair(true, dialog)
    }

    private fun onSkillListEntryDetected(entry: SkillListEntry, point: Point) {
        // Don't need to buy skill if it's already obtained.
        if (!entry.bIsObtained) {
            // Purchase skill if necessary by clicking the skill up button.
            if (skillPlanSettings.buyInheritedSkills && entry.skillData.bIsUnique) {
                game.tap(point.x, point.y, ButtonSkillUp.template.path)
            } else if (skillPlanSettings.buyNegativeSkills && entry.skillData.bIsNegative) {
                game.tap(point.x, point.y, ButtonSkillUp.template.path)
            } else if (skillsToBuy != null) {
                val skillToBuy: SkillToBuy? = skillsToBuy.find { it.name == entry.name }
                if (skillToBuy != null) {
                    game.tap(point.x, point.y, ButtonSkillUp.template.path, taps = skillToBuy.numUpgrades)
                }
            }
        }
    }

    private fun calculateAdjustedPointRatio(skillListEntry: SkillListEntry): Double {
        val ratioModifierMap: Map<Aptitude, Double> = mapOf(
            Aptitude.S to 1.1,
            Aptitude.A to 1.1,
            Aptitude.B to 0.9,
            Aptitude.C to 0.9,
            Aptitude.D to 0.8,
            Aptitude.E to 0.8,
            Aptitude.F to 0.8,
            Aptitude.G to 0.7,
        )
        val skillRunningStyle: RunningStyle? = skillListEntry.skillData.runningStyle
        val skillTrackDistance: TrackDistance? = skillListEntry.skillData.trackDistance
        val evalPt: Int = if (skillRunningStyle != null) {
            val aptitude: Aptitude = game.trainee.checkRunningStyleAptitude(skillRunningStyle)
            val ratioModifier: Double = ratioModifierMap[aptitude] ?: 1.0
            (skillListEntry.skillData.evalPt * ratioModifier).roundToInt()
        } else if (skillTrackDistance != null) {
            val aptitude: Aptitude = game.trainee.checkTrackDistanceAptitude(skillTrackDistance)
            val ratioModifier: Double = ratioModifierMap[aptitude] ?: 1.0
            (skillListEntry.skillData.evalPt * ratioModifier).roundToInt()
        } else {
            skillListEntry.skillData.evalPt
        }

        return (evalPt.toDouble() / skillListEntry.price.toDouble()).toDouble()
    }

    private fun getSkillsToBuyCommon(
        skillListEntries: Map<String, SkillListEntry>,
        skillPoints: Int,
        skillPlanSettings: SkillPlanSettings,
    ): SkillsToBuyCommonReturnData {
        var remainingSkillListEntries: MutableMap<String, SkillListEntry> = skillListEntries.toMutableMap()
        var remainingSkillPoints: Int = skillPoints
        var skillsToBuy: MutableList<SkillToBuy> = mutableListOf()

        // Add negative skills
        for ((name, entry) in remainingSkillListEntries) {
            if (entry.skillData.bIsNegative && entry.price <= remainingSkillPoints) {
                skillsToBuy.add(SkillToBuy(name, totalPrice = entry.price))
                remainingSkillPoints -= entry.price
            }
        }

        // Add user planned skills

        // If two different versions of one skill are in the skill list AND in the
        // skill plan, we want to buy the highest level version of that skill.
        // For example, if Corner Recovery O and Swinging Maestro are both in the skill
        // plan, and both entries are in the skill list, then we want to buy Swinging Maestro.
        // However, if we do not have enough points for Swinging Maestro, then attempt to
        // buy Corner Recovery O instead.

        val remainingRealSkills: Map<String, SkillListEntry> = remainingSkillListEntries
            .filterValues { !it.bIsVirtual }

        for (name in skillPlanSettings.skillPlan) {
            var entry: SkillListEntry? = remainingSkillListEntries[name]
            if (entry == null) {
                continue
            }

            val upgrades: List<SkillListEntry> = entry.getUpgrades()
            // If any higher versions of this skill are already in the skillsToBuy
            // list then we dont want to try adding this one.
            val skillNames: List<String> = skillsToBuy.map { it.name }
            if (upgrades.any { it.name in skillNames }) {
                continue
            }

            val downgrades: MutableList<SkillListEntry> = entry.getDowngrades().toMutableList()
            val currSkillDowngradeIndex: Int = downgrades.indexOfFirst {
                val tmp: SkillListEntry? = remainingSkillListEntries[it.name]
                tmp != null && !tmp.bIsVirtual
            }
            if (currSkillDowngradeIndex == -1) {
                continue
            }
            val currSkill: SkillListEntry = downgrades[currSkillDowngradeIndex]
            // Trim list so that we only have a list from the skill in the list to the
            // current skillToBuy's location in the list.
            downgrades.subList(0, currSkillDowngradeIndex).clear()
            MessageLog.e("REMOVEME", "downgrades sublist for $name: $downgrades")
            // Check that we will be able to fully purchase this skill and
            // any required upgrades to unlock it.
            var i: Int = 0
            var totPrice: Int = 0
            val skillsToRemove: MutableList<String> = mutableListOf()
            for (other in downgrades) {
                if (totPrice + other.price > remainingSkillPoints) {
                    break
                }
                entry = other
                totPrice += entry.price
                if (i > 0) {
                    skillsToRemove.add(entry.skillData.name)
                }
                i++
            }

            if (totPrice <= remainingSkillPoints) {
                if (skillsToBuy.any { it.name == currSkill.name && it.numUpgrades > i}) {
                    // There is a better upgrade that we already plan on buying.
                    // Skip the current entry.
                    continue
                }

                // Now remove any duplicate entries since they are worse than our
                // current entry.
                val tmpSkillsToRemove: List<SkillToBuy> = skillsToBuy.filter { it.name == currSkill.name }
                // Need to add the total price back to our remaining skill points
                // since we're not buying this one anymore.
                for (skillToRemove in tmpSkillsToRemove) {
                    remainingSkillPoints += skillToRemove.totalPrice
                }
                skillsToBuy.removeAll { it.name == currSkill.name }

                // Finally, add our current entry to be purchased.
                skillsToBuy.add(SkillToBuy(currSkill.name, i, totPrice))
                remainingSkillPoints -= totPrice
                MessageLog.e("REMOVEME", "$name totPrice=$totPrice")
                MessageLog.e("REMOVEME", "skillsToRemove = $skillsToRemove")
                remainingSkillListEntries -= skillsToRemove
                skillsToBuy.removeAll { it.name != currSkill.name && it.name in skillsToRemove }
            }
        }
        remainingSkillListEntries -= skillsToBuy.map { it.name }

        MessageLog.e("REMOVEME", "[SKILLS] After parsing upgrades/downgrades: $skillsToBuy")

        // Now we need to handle skills in our plan which do not exist in the skill
        // list BUT will exist if we upgrade a skill far enough. For example, if the
        // user adds Firm Conditions ◎ to the skill plan but we only have
        // Firm Conditions × in the skill list, then we'd need to purchase
        // Firm Conditions ×, then Firm Conditions ○, and only then will we be able
        // to purchase Firm Conditions ◎. This of course means we need to calculate
        // the total price of the skill in our plan by adding the previous two skills
        // prices.
        val skillPlanIds: List<Int> = skillPlanSettings.skillPlan.mapNotNull { skillDatabase.getSkillId(it) }
        for ((name, entry) in remainingSkillListEntries) {
            // Only certain types of skills can have in-place upgrades:
            // Negative skills
            // Green skills
            // Distance-based straightaway/corner skills
            //
            // We only want inplace upgrades, so just skip all others.
            if (
                entry.skillData.type != SkillType.GREEN &&
                !entry.skillData.bIsNegative &&
                !entry.skillData.name.dropLast(2).endsWith("straightaways", ignoreCase = true) &&
                !entry.skillData.name.dropLast(2).endsWith("corners", ignoreCase = true)
            ) {
                continue
            }

            val upgradeNames: MutableList<String> = mutableListOf()
            var upgradeId: Int? = entry.skillData.upgrade
            var totalPrice: Int = entry.price
            if (entry.skillData.cost == null) {
                continue
            }

            while (upgradeId != null) {
                val upgradeName: String? = skillDatabase.getSkillName(upgradeId)
                if (upgradeName == null) {
                    break
                }

                val upgradeSkillData: SkillData? = skillDatabase.getSkillData(upgradeName)
                if (upgradeSkillData == null) {
                    break
                }

                val upgradeCost: Int? = upgradeSkillData.cost
                if (upgradeCost == null) {
                    break
                }
                // The discount value doesn't change for in-place upgrades.
                // REMOVEME fix this entry. we calculate this elsewhere.
                totalPrice += ceil(upgradeCost * entry.discount).toInt()
                MessageLog.e("REMOVEME", "$name -> ${upgradeSkillData.name} ($upgradeCost)")
                upgradeNames.add(upgradeSkillData.name)
                upgradeId = upgradeSkillData.upgrade

                // If this upgrade version is in our skill plan, then we stop here
                // and set it to be purchased. The numUpgrades is how many extra times
                // we click the "+" button after purchasing the base skill.
                if (upgradeName in skillPlanSettings.skillPlan && totalPrice <= remainingSkillPoints) {
                    skillsToBuy.add(SkillToBuy(name, numUpgrades = upgradeNames.size, totalPrice = totalPrice))
                    remainingSkillPoints -= totalPrice
                    break
                }
            }
        }
        remainingSkillListEntries -= skillsToBuy.map { it.name }

        return SkillsToBuyCommonReturnData(
            remainingSkillListEntries = remainingSkillListEntries.toMap(),
            remainingSkillPoints = remainingSkillPoints,
            skillsToBuy = skillsToBuy.toList().distinctBy { it.name },
            // Early exit if we aren't spending all our points.
            earlyExit = skillPlanSettings.spendingStrategy == SpendingStrategy.DEFAULT,
        )
    }

    private fun getSkillsToBuyDefaultStrategy(
        skillListEntries: Map<String, SkillListEntry>,
        skillPoints: Int,
        skillPlanSettings: SkillPlanSettings,
    ): List<SkillToBuy> {
        val commonResultData: SkillsToBuyCommonReturnData = getSkillsToBuyCommon(
            skillListEntries,
            skillPoints,
            skillPlanSettings,
        )

        return commonResultData.skillsToBuy.distinctBy { it.name }
    }

    private fun getSkillsToBuyOptimizeSkillsStrategy(
        skillListEntries: Map<String, SkillListEntry>,
        skillPoints: Int,
        skillPlanSettings: SkillPlanSettings,
    ): List<SkillToBuy> {
        val commonResultData: SkillsToBuyCommonReturnData = getSkillsToBuyCommon(
            skillListEntries,
            skillPoints,
            skillPlanSettings,
        )

        if (commonResultData.earlyExit) {
            return commonResultData.skillsToBuy
        }

        var remainingSkillListEntries: MutableMap<String, SkillListEntry> = commonResultData.remainingSkillListEntries.toMutableMap()
        var remainingSkillPoints: Int = commonResultData.remainingSkillPoints
        val skillsToBuy: MutableList<SkillToBuy> = commonResultData.skillsToBuy.toMutableList()

        // Get user specified running style.
        // If not specified, then we use the trainee's highest aptitude option.
        val userSelectedRunningStyle: RunningStyle? = RunningStyle.fromName(userSelectedRunningStyleString)
        val preferredRunningStyle: RunningStyle = userSelectedRunningStyle ?: game.trainee.runningStyle
        
        // Get user specified track distance.
        // If not specified, then we use the trainee's highest aptitude option.
        val userSelectedTrackDistance: TrackDistance? = TrackDistance.fromName(userSelectedTrackDistanceOverrideString)
        val preferredTrackDistance: TrackDistance = userSelectedTrackDistance ?: game.trainee.trackDistance

        // Now filter by trainee aptitudes.
        // If the user specified a track distance and/or a running style, then those
        // will be used. Otherwise, the trainee's highest aptitude track distance and
        // running style will be used.
        remainingSkillListEntries = remainingSkillListEntries.filterValues {
            it.skillData.runningStyle == preferredRunningStyle ||
            it.skillData.trackDistance == preferredTrackDistance
        }.toMutableMap()

        // Group the remaining entries by their communityTier. Higher values are better.
        // This can contain a NULL group for skills that are not in the tier list.
        val groupedByCommunityTier: Map<Int?, List<SkillListEntry>> = remainingSkillListEntries.values
            .groupBy { it.skillData.communityTier }
            .toSortedMap(compareByDescending { it })

        // Iterate from highest tier to lowest.
        for ((communityTier, group) in groupedByCommunityTier) {
            // Ignore the null entries for now.
            if (communityTier == null) {
                continue
            }

            val sortedByPointRatio: List<SkillListEntry> = group.sortedByDescending { calculateAdjustedPointRatio(it) }
            for (entry in sortedByPointRatio) {
                val ptRatioString: String = "%.2f".format(calculateAdjustedPointRatio(entry))
                MessageLog.d(TAG, "${entry.skillData.name}: ${entry.price}pt (${ptRatioString} rating/pt)")
                // If we can't afford this skill, continue to the next.
                if (remainingSkillPoints < entry.price) {
                    continue
                }

                skillsToBuy.add(SkillToBuy(entry.skillData.name, totalPrice = entry.price))
                remainingSkillPoints -= entry.price
            }
            remainingSkillListEntries -= skillsToBuy.map { it.name }
        }

        return skillsToBuy.toList().distinctBy { it.name }
    }

    private fun getSkillsToBuyOptimizeRankStrategy(
        skillListEntries: Map<String, SkillListEntry>,
        skillPoints: Int,
        skillPlanSettings: SkillPlanSettings,
    ): List<SkillToBuy> {
        val commonResultData: SkillsToBuyCommonReturnData = getSkillsToBuyCommon(
            skillListEntries,
            skillPoints,
            skillPlanSettings,
        )

        if (commonResultData.earlyExit) {
            return commonResultData.skillsToBuy
        }

        var remainingSkillListEntries: MutableMap<String, SkillListEntry> = commonResultData.remainingSkillListEntries.toMutableMap()
        var remainingSkillPoints: Int = commonResultData.remainingSkillPoints
        val skillsToBuy: MutableList<SkillToBuy> = commonResultData.skillsToBuy.toMutableList()

        val sortedByPointRatio: List<SkillListEntry> = remainingSkillListEntries.values
            .sortedByDescending { calculateAdjustedPointRatio(it) }

        for (entry in sortedByPointRatio) {
            val ptRatioString: String = "%.2f".format(calculateAdjustedPointRatio(entry))
            MessageLog.d(TAG, "${entry.skillData.name}: ${entry.price}pt (${ptRatioString} rating/pt)")
            // If we can't afford this skill, continue to the next.
            if (remainingSkillPoints < entry.price) {
                continue
            }

            skillsToBuy.add(SkillToBuy(entry.skillData.name, totalPrice = entry.price))
            remainingSkillPoints -= entry.price
        }

        remainingSkillListEntries -= skillsToBuy.map { it.name }
        return skillsToBuy.toList().distinctBy { it.name }
    }

    private fun getSkillsToBuy(
        skillPlanSettings: SkillPlanSettings,
        skillPoints: Int? = null,
        skillListEntries: Map<String, SkillListEntry>? = null,
    ): List<SkillToBuy> {
        MessageLog.d(TAG, "[SKILLS] Beginning process of calculating skills to purchase...")

        val skillListEntries: Map<String, SkillListEntry> = skillListEntries ?: skillList.entries
        val skillPoints: Int? = skillPoints ?: skillList.getSkillPoints()
        if (skillPoints == null) {
            MessageLog.w(TAG, "[SKILLS] getSkillsToBuy: skillList.getSkillPoints returned NULL.")
            return emptyList()
        }

        if(!skillPlanSettings.enabled) {
            MessageLog.i(TAG, "[SKILLS] Skill plan is disabled. No skills will be purchased.")
            return emptyList()
        }

        if (
            skillPlanSettings.skillPlan.isEmpty() &&
            skillPlanSettings.spendingStrategy == SpendingStrategy.DEFAULT &&
            !skillPlanSettings.buyInheritedSkills &&
            !skillPlanSettings.buyNegativeSkills
        ) {
            MessageLog.w(TAG, "[SKILLS] Skill Plan is empty and no options to purchase any skills are enabled. Aborting...")
            return emptyList()
        }

        MessageLog.d(TAG, "[SKILLS] User-specified Skill Plan:")
        for (name in skillPlanSettings.skillPlan) {
            MessageLog.d(TAG, "[SKILLS]\t$name")
        }

        // Remove any skills that we've already obtained from the map.
        val filteredSkills: Map<String, SkillListEntry> = skillList.getAvailableEntries(skillListEntries)

        val result: List<SkillToBuy> = when (skillPlanSettings.spendingStrategy) {
            SpendingStrategy.DEFAULT -> getSkillsToBuyDefaultStrategy(
                filteredSkills,
                skillPoints,
                skillPlanSettings,
            )
            SpendingStrategy.OPTIMIZE_SKILLS -> getSkillsToBuyOptimizeSkillsStrategy(
                filteredSkills,
                skillPoints,
                skillPlanSettings,
            )
            SpendingStrategy.OPTIMIZE_RANK -> getSkillsToBuyOptimizeRankStrategy(
                filteredSkills,
                skillPoints,
                skillPlanSettings,
            )
        }

        if (result.isEmpty()) {
            MessageLog.w(TAG, "[SKILLS] List of skills to buy is empty. Aborting...")
            return emptyList()
        }

        MessageLog.d(TAG, "=======================================")
        MessageLog.d(TAG, "[SKILLS] Skills to Buy:")
        for (skillToBuy in result) {
            val upgradeString: String = if (skillToBuy.numUpgrades > 0) " +${skillToBuy.numUpgrades} upgrade(s)" else ""
            val price: Int = skillListEntries[skillToBuy.name]?.price ?: -1
            MessageLog.d(TAG, "[SKILLS]\t${skillToBuy.name}${upgradeString} for ${skillToBuy.totalPrice}pts (${price}ea)")
        }
        MessageLog.d(TAG, "=======================================")

        skillsToBuy = result
        return skillsToBuy
    }

    fun start(): Boolean {
        val bitmap: Bitmap = game.imageUtils.getSourceBitmap()

        // Verify that we are at the skill list screen.
        val bIsCareerComplete: Boolean = skillList.checkCareerCompleteSkillListScreen(bitmap)
        if (!bIsCareerComplete && !skillList.checkSkillListScreen(bitmap)) {
            MessageLog.e(TAG, "[SKILLS] Not at skill list screen. Aborting...")
            return false
        }

        skillPlanSettings = if (bIsCareerComplete) skillPlanSettingsCareerComplete else skillPlanSettingsPreFinals
        if (
            skillPlanSettings.skillPlan.isEmpty() &&
            skillPlanSettings.spendingStrategy == SpendingStrategy.DEFAULT &&
            !skillPlanSettings.buyInheritedSkills &&
            !skillPlanSettings.buyNegativeSkills
        ) {
            MessageLog.w(TAG, "[SKILLS] Skill Plan is empty and no options to purchase any skills are enabled. Aborting...")
            return true
        }

        // Purchasing skills depends on us knowing our aptitudes.
        // If we haven't acquired them yet, then we need to force check them.
        // This can happen if we start the bot at the skill list screen.
        if (!game.trainee.bHasUpdatedAptitudes) {
            ButtonSkillListFullStats.click(game.imageUtils, sourceBitmap = bitmap)
            game.wait(0.5, skipWaitingForLoading = true)
            handleDialogs()
        }

        val skillPoints: Int? = skillList.getSkillPoints(bitmap)
        if (skillPoints == null) {
            MessageLog.w(TAG, "[SKILLS] skillList.getSkillPoints returned NULL.")
            return false
        }

        if (skillPoints < 30) {
            MessageLog.i(TAG, "[SKILLS] Skill Points < 30. Cannot afford any skills. Aborting...")
            return true
        }
        
        if (USE_MOCKED_DATA) {
            skillList.getMockSkillListEntries()
        } else {
            skillList.getSkillListEntries() { entry: SkillListEntry, point: Point ->
                MessageLog.e("REMOVEME", "getSkillListEntries Callback: ${entry.name}")
            }
        }

        if (skillList.entries == null) {
            MessageLog.e(TAG, "[SKILLS] Failed to detect skills.")
            return false
        }

        skillList.printSkillListEntries(verbose = true)

        val skillsToBuy: List<SkillToBuy> = getSkillsToBuy(skillPlanSettings, skillPoints)

        if (skillsToBuy.isNotEmpty() && !USE_MOCKED_DATA) {
            skillList.getSkillListEntries() { entry: SkillListEntry, point: Point ->
                MessageLog.e("REMOVEME", "CALLBACK: ${entry.name}")
            }
        }

        return true
        //ButtonConfirm.click(game.imageUtils)
        game.wait(0.5, skipWaitingForLoading = true)
        // Two dialogs will appear if we purchase any skills.
        // First is the purchase confirmation.
        handleDialogs()
        // Second is the Skills Learned dialog.
        handleDialogs()

        return true
    }
}