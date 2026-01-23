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

import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.automation_library.data.SharedData

import com.steve1316.uma_android_automation.MainActivity
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

    private val purchasedSkills: MutableList<SkillListEntry> = mutableListOf()
    private var skillsToBuy: List<String> = listOf()

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
        if (entry.bIsObtained) {
            return
        }

        if (entry.name !in this.skillsToBuy) {
            return
        }

        // Determine if there are other in-place versions of this skill that
        // we need to buy.
        if (entry.skillData.bIsInPlace) {
            val upgradeNames: MutableList<String> = this.skillDatabase.getUpgrades(entry.name).toMutableList()
            upgradeNames.retainAll { it in this.skillsToBuy }
            for (upgradeName in upgradeNames) {
                val result: SkillListEntry? = this.skillList.buySkill(upgradeName, point)
                if (result != null) {
                    MessageLog.i(TAG, "Buying \"${result.name}\" for ${result.price} pts")
                }
            }
        } else {
            val result: SkillListEntry? = this.skillList.buySkill(entry.name, point)
            if (result != null) {
                MessageLog.i(TAG, "Buying \"${result.name}\" for ${result.price} pts")
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
            (skillListEntry.combinedDowngradeEvalPt * ratioModifier).roundToInt()
        } else if (skillTrackDistance != null) {
            val aptitude: Aptitude = game.trainee.checkTrackDistanceAptitude(skillTrackDistance)
            val ratioModifier: Double = ratioModifierMap[aptitude] ?: 1.0
            (skillListEntry.combinedDowngradeEvalPt * ratioModifier).roundToInt()
        } else {
            skillListEntry.combinedDowngradeEvalPt
        }

        return (evalPt.toDouble() / skillListEntry.basePrice.toDouble()).toDouble()
    }

    private fun addNegativeSkills(skillPlanSettings: SkillPlanSettings) {
        if (!skillPlanSettings.buyNegativeSkills) {
            return
        }

        val skillsToBuy: MutableList<String> = this.skillsToBuy.toMutableList()
        var remainingSkillPoints: Int = this.remainingSkillPoints

        val entries: Map<String, SkillListEntry> = this.skillList.getNegativeSkills()
        for ((name, entry) in entries) {
            if (name in skillsToBuy) {
                continue
            }

            if (entry.price <= remainingSkillPoints) {
                skillsToBuy.add(name)
                remainingSkillPoints -= if (entry.bHasSeparateDowngrade) entry.basePrice else entry.price
                this.skillList.markEntryObtained(name)
            }
        }

        this.skillsToBuy = skillsToBuy.toList()
        this.remainingSkillPoints = remainingSkillPoints
    }

    private fun addInheritedUniqueSkills(skillPlanSettings: SkillPlanSettings) {
        if (!skillPlanSettings.buyInheritedSkills) {
            return
        }

        val skillsToBuy: MutableList<String> = this.skillsToBuy.toMutableList()
        var remainingSkillPoints: Int = this.remainingSkillPoints

        val entries: Map<String, SkillListEntry> = this.skillList.getInheritedUniqueSkills()
        for ((name, entry) in entries) {
            if (name in skillsToBuy) {
                continue
            }

            if (entry.price <= remainingSkillPoints) {
                skillsToBuy.add(name)
                remainingSkillPoints -= if (entry.bHasSeparateDowngrade) entry.basePrice else entry.price
                this.skillList.markEntryObtained(name)
            }
        }

        this.skillsToBuy = skillsToBuy.toList()
        this.remainingSkillPoints = remainingSkillPoints
    }

    private fun addUserPlannedSkills(skillPlanSettings: SkillPlanSettings) {
        if (skillPlanSettings.skillPlan.isEmpty()) {
            return
        }

        val skillsToBuy: MutableList<String> = this.skillsToBuy.toMutableList()
        var remainingSkillPoints: Int = this.remainingSkillPoints

        // If two different versions of one skill are in the skill list AND in the
        // skill plan, we want to buy the highest level version of that skill that we can afford.
        // For example, if Corner Recovery O and Swinging Maestro are both in the skill
        // plan, and both entries are in the skill list, then we want to buy Swinging Maestro.
        // However, if we do not have enough points for Swinging Maestro, then attempt to
        // buy Corner Recovery O instead.
        val availableSkills: Map<String, SkillListEntry> = this.skillList.getAvailableSkills()
        MessageLog.e("REMOVEME", "============= addUserPlannedSkills: AVAILABLE =============")
        for ((k, v) in availableSkills) {
            MessageLog.e("REMOVEME", "\t$k: ${v.basePrice}")
        }

        for (name in skillPlanSettings.skillPlan) {
            // Do not add this skill if there is already a higher upgraded
            // version in the list of skillsToBuy.
            val upgrades: List<String> = this.skillDatabase.getUpgrades(name)
            if (upgrades.any { it in skillsToBuy }) {
                MessageLog.w("REMOVEME", "Higher version of \"$name\" exists: $upgrades")
                continue
            }

            val downgrades: List<String> = this.skillDatabase.getDowngrades(name)
            if (downgrades.any { it in skillsToBuy }) {
                MessageLog.e("REMOVEME", "updating price and eval for \"$name\".")
                this.skillList.updateSkillListEntryEvaluationPoints(name)
                this.skillList.updateSkillListEntryBasePrice(name)
            }


            // Handle exact matches.
            if (name in availableSkills) {
                skillsToBuy.add(name)
                val entry: SkillListEntry = availableSkills[name] ?: continue
                remainingSkillPoints -= if (entry.bHasSeparateDowngrade) entry.basePrice else entry.price
                this.skillList.markEntryObtained(name)
                // We're done. Go to the next planned skill.
                MessageLog.e("REMOVEME", "Exact match for \"$name\".")
                continue
            }

            // Handle skills with multiple versions in the list.
            
            // If no downgraded versions exist in our skill list, skip this entry since
            // we won't be able to buy it.
            val existingSkillName: String? = downgrades.find { it in availableSkills }
            MessageLog.e("REMOVEME", "skillPlan ($name) -> downgrades = $downgrades, existingSkillName=$existingSkillName")
            if (existingSkillName == null) {
                continue
            }

            // Get list of all prior versions that we'll have to buy in order to
            // buy the user-specified skill.
            val versions: List<String> = skillDatabase.getVersionRange(existingSkillName, name)
            MessageLog.e("REMOVEME", "skillPlan ($name) -> versions=$versions")
            var totalPrice: Int = 0
            var bCanAfford: Boolean = true
            val tmpSkillsToBuy: MutableList<String> = mutableListOf()
            for (version in versions) {
                // If we already added this entry to the list of skills to buy,
                // then just skip it and check the next version.
                if (version in skillsToBuy) {
                    continue
                }

                val entry: SkillListEntry? = this.skillList.getEntry(version)
                if (entry == null) {
                    break
                }

                // If we can't afford this skill, then bail out.
                if (totalPrice + entry.price > remainingSkillPoints) {
                    bCanAfford = false
                    break
                }

                totalPrice += if (entry.bHasSeparateDowngrade) entry.basePrice else entry.price
                tmpSkillsToBuy.add(version)
            }
            // Only add to skillsToBuy if we can afford ALL upgrades.
            if (bCanAfford) {
                skillsToBuy.addAll(tmpSkillsToBuy)
                remainingSkillPoints -= totalPrice
                for (tmpSkillToBuy in tmpSkillsToBuy) {
                    this.skillList.markEntryObtained(tmpSkillToBuy)
                }
            }
        }

        this.skillsToBuy = skillsToBuy.toList()
        this.remainingSkillPoints = remainingSkillPoints
    }

    private fun addSkillsToBuyCommon(skillPlanSettings: SkillPlanSettings) {
        addNegativeSkills(skillPlanSettings)
        MessageLog.e("REMOVEME", "[SKILLS] skillsToBuy after addNegativeSkills: $skillsToBuy")
        addInheritedUniqueSkills(skillPlanSettings)
        MessageLog.e("REMOVEME", "[SKILLS] skillsToBuy after addInheritedUniqueSkills: $skillsToBuy")
        addUserPlannedSkills(skillPlanSettings)
        MessageLog.e("REMOVEME", "[SKILLS] skillsToBuy after addUserPlannedSkills: $skillsToBuy")
    }

    private fun addSkillsToBuyDefaultStrategy(skillPlanSettings: SkillPlanSettings) {
        if (skillPlanSettings.spendingStrategy != SpendingStrategy.DEFAULT) {
            return
        }
        // For now this function is very simple and does no extra work.
        // But we may want to add functionality to the default strategy later.
        addSkillsToBuyCommon(skillPlanSettings)
    }

    private fun addSkillsToBuyOptimizeSkillsStrategy(skillPlanSettings: SkillPlanSettings) {
        if (skillPlanSettings.spendingStrategy != SpendingStrategy.OPTIMIZE_SKILLS) {
            return
        }

        // Must come before the temporary local variables.
        addSkillsToBuyCommon(skillPlanSettings)

        // Temporarily store class state as local variables.
        val skillsToBuy: MutableList<String> = this.skillsToBuy.toMutableList()
        var remainingSkillPoints: Int = this.remainingSkillPoints

        // Get user specified running style.
        // If not specified, then we use the trainee's highest aptitude option.
        val userSelectedRunningStyle: RunningStyle? = RunningStyle.fromName(userSelectedRunningStyleString)
        val preferredRunningStyle: RunningStyle = userSelectedRunningStyle ?: game.trainee.runningStyle
        
        // Get user specified track distance.
        // If not specified, then we use the trainee's highest aptitude option.
        val userSelectedTrackDistance: TrackDistance? = TrackDistance.fromName(userSelectedTrackDistanceOverrideString)
        val preferredTrackDistance: TrackDistance = userSelectedTrackDistance ?: game.trainee.trackDistance

        // Get only skills which match our aptitudes or user-specified styles or
        // are agnostic of style or track variables.
        val filteredSkills: Map<String, SkillListEntry> =
            this.skillList.getAptitudeIndependentSkills() +
            this.skillList.getRunningStyleSkills(preferredRunningStyle) +
            this.skillList.getTrackDistanceSkills(preferredTrackDistance)

        // Group the remaining entries by their communityTier. Higher values are better.
        // This can contain a NULL group for skills that are not in the tier list.
        val groupedByCommunityTier: Map<Int?, List<SkillListEntry>> = filteredSkills.values
            .groupBy { it.skillData.communityTier }
            .toSortedMap(compareByDescending { it })

        // Iterate from highest tier to lowest.
        for ((communityTier, group) in groupedByCommunityTier) {
            // Ignore the null entries for now.
            if (communityTier == null) {
                continue
            }

            // Sort the tier by its point ratio.
            val sortedByPointRatio: List<SkillListEntry> = group.sortedByDescending { calculateAdjustedPointRatio(it) }
            for (entry in sortedByPointRatio) {
                // Don't add duplicate entries.
                if (entry.name in skillsToBuy) {
                    continue
                }

                // If this skill isnt an in-place upgrade and we have already
                // added its upgraded version to the list, then don't add it.
                if (
                    !entry.skillData.bIsInPlace &&
                    entry.name in this.skillDatabase.getUpgrades(entry.name)
                ) {
                    continue
                }

                val ptRatioString: String = "%.2f".format(calculateAdjustedPointRatio(entry))
                MessageLog.d(TAG, "${entry.skillData.name}: ${entry.price}pt (${ptRatioString} rating/pt)")
                // If we can't afford this skill, continue to the next.
                if (entry.price > remainingSkillPoints) {
                    continue
                }

                skillsToBuy.add(entry.name)
                remainingSkillPoints -= if (entry.bHasSeparateDowngrade) entry.basePrice else entry.price
                this.skillList.markEntryObtained(entry.name)
            }
        }

        // Update the class properties.
        this.skillsToBuy = skillsToBuy.toList()
        this.remainingSkillPoints = remainingSkillPoints
    }

    private fun addSkillsToBuyOptimizeRankStrategy(skillPlanSettings: SkillPlanSettings) {
        if (skillPlanSettings.spendingStrategy != SpendingStrategy.OPTIMIZE_RANK) {
            return
        }

        // Must come before the temporary local variables.
        addSkillsToBuyCommon(skillPlanSettings)

        // Temporarily store class state as local variables.
        val skillsToBuy: MutableList<String> = this.skillsToBuy.toMutableList()
        var remainingSkillPoints: Int = this.remainingSkillPoints

        val remainingSkills: MutableMap<String, SkillListEntry> = this.skillList.getAvailableSkills().toMutableMap()

        var sortedByPointRatio: List<SkillListEntry> = remainingSkills.values
            .sortedByDescending { calculateAdjustedPointRatio(it) }

        while (sortedByPointRatio.any { it.price <= remainingSkillPoints}) {
            for (entry in sortedByPointRatio) {
                val ptRatioString: String = "%.2f".format(calculateAdjustedPointRatio(entry))
                MessageLog.d(TAG, "${entry.skillData.name}: ${entry.price}pt (${ptRatioString} rating/pt)")
                // If we can't afford this skill, continue to the next.
                if (entry.price > remainingSkillPoints) {
                    continue
                }

                // If the entry is already in the list, don't add it again since
                // that would double up on the skill point spending estimate.
                if (skillsToBuy.any { it == entry.name }) {
                    continue
                }

                skillsToBuy.add(entry.name)
                remainingSkillPoints -= if (entry.bHasSeparateDowngrade) entry.basePrice else entry.price
                remainingSkills.remove(entry.name)
                this.skillList.markEntryObtained(entry.name)
            }
            MessageLog.e("REMOVEME", "ITERATION DONE. ${remainingSkillPoints} points remaining.")
            sortedByPointRatio = remainingSkills.values
                .sortedByDescending { calculateAdjustedPointRatio(it) }
        }

        // Update the class properties.
        this.skillsToBuy = skillsToBuy.toList()
        this.remainingSkillPoints = remainingSkillPoints
    }

    private fun addSkillsToBuy(skillPlanSettings: SkillPlanSettings) {
        MessageLog.d(TAG, "[SKILLS] Beginning process of calculating skills to purchase...")

        if(!skillPlanSettings.enabled) {
            MessageLog.i(TAG, "[SKILLS] Skill plan is disabled. No skills will be purchased.")
            return
        }

        MessageLog.d(TAG, "[SKILLS] User-specified Skill Plan:")
        for (name in skillPlanSettings.skillPlan) {
            MessageLog.d(TAG, "[SKILLS]\t$name")
        }

        when (skillPlanSettings.spendingStrategy) {
            SpendingStrategy.DEFAULT -> addSkillsToBuyDefaultStrategy(skillPlanSettings)
            SpendingStrategy.OPTIMIZE_SKILLS -> addSkillsToBuyOptimizeSkillsStrategy(skillPlanSettings)
            SpendingStrategy.OPTIMIZE_RANK -> addSkillsToBuyOptimizeRankStrategy(skillPlanSettings)
        }

        MessageLog.d(TAG, "============== Skills To Buy ==============")
        var totalPrice: Int = 0
        for (name in this.skillsToBuy) {
            val entry: SkillListEntry? = this.skillList.getEntry(name)
            if (entry == null) {
                MessageLog.w(TAG, "\t$name: NULL")
                continue
            }
            val price: Int = if (entry.bHasSeparateDowngrade) entry.basePrice else entry.price
            MessageLog.d(TAG, "\t$name: $price")
            totalPrice += price
        }
        MessageLog.d(TAG, "\n\tTOTAL: $totalPrice / ${this.skillList.skillPoints} pts")
        MessageLog.d(TAG, "===========================================")
    }

    fun start(): Boolean {
        val bitmap: Bitmap = game.imageUtils.getSourceBitmap()

        MessageLog.e("REMOVEME", "SkillPlan::start()")

        // Verify that we are at the skill list screen.
        val bIsCareerComplete: Boolean = skillList.checkCareerCompleteSkillListScreen(bitmap)
        if (!bIsCareerComplete && !skillList.checkSkillListScreen(bitmap)) {
            MessageLog.e(TAG, "[SKILLS] Not at skill list screen. Aborting...")
            return false
        }

        skillPlanSettings = if (bIsCareerComplete) skillPlanSettingsCareerComplete else skillPlanSettingsPreFinals
        MessageLog.e("REMOVEME", "skillPlanSettings:")
        MessageLog.e("REMOVEME", "bIsCareerComplete=$bIsCareerComplete")
        MessageLog.e("REMOVEME", "\t${skillPlanSettings.skillPlan}")
        MessageLog.e("REMOVEME", "\t${skillPlanSettings.spendingStrategy}")
        MessageLog.e("REMOVEME", "\t${skillPlanSettings.buyInheritedSkills}")
        MessageLog.e("REMOVEME", "\t${skillPlanSettings.buyNegativeSkills}")

        // If no options are enabled for purchasing skills, then we should
        // exit early to avoid having to scan the whole skill list.
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

        this.remainingSkillPoints = skillList.detectSkillPoints(bitmap) ?: 0
        // The cheapest skills are all 70 points and with discounts
        // can be as low as 42 points. If anything is less than this, then
        // we should update this.
        if (this.remainingSkillPoints < 42) {
            MessageLog.i(TAG, "[SKILLS] Skill Points < 42. Cannot afford any skills. Aborting...")
            return true
        }
        
        // Gather all skill list entry data.
        if (USE_MOCKED_DATA) {
            skillList.getMockSkillListEntries()
        } else {
            skillList.getSkillListEntries()
        }

        if (skillList.entries.isEmpty()) {
            MessageLog.e(TAG, "[SKILLS] Failed to detect skills.")
            return false
        }

        skillList.printSkillListEntries(verbose = true)

        // Calculate list of skills to purchase.
        addSkillsToBuy(skillPlanSettings)

        if (this.skillsToBuy.isEmpty()) {
            // Return to previous screen.
            ButtonBack.click(game.imageUtils)
            return true
        }

        // Go back through skill list and purchase skills.
        skillList.getSkillListEntries(::onSkillListEntryDetected)

        return true
        ButtonConfirm.click(game.imageUtils)
        game.wait(0.5, skipWaitingForLoading = true)
        // Two dialogs will appear if we purchase any skills.
        // First is the purchase confirmation.
        handleDialogs()
        // Second is the Skills Learned dialog.
        handleDialogs()
        // Return to previous screen.
        ButtonBack.click(game.imageUtils)

        return true
    }
}