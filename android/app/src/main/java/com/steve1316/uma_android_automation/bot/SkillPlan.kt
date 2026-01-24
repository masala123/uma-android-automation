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
    private val TAG: String = "[${MainActivity.loggerTag}]SkillPlan"

    val skillDatabase: SkillDatabase = SkillDatabase(game)
    val skillList: SkillList = SkillList(game, skillDatabase)

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
        if (entry.bIsObtained || entry.bIsVirtual) {
            return
        }

        if (entry.name !in this.skillsToBuy) {
            return
        }

        // Determine if there are other in-place versions of this skill that
        // we need to buy.
        if (entry.bIsInPlace) {
            val namesToBuy: List<String> = listOf(entry.name) +
                entry.getUpgradeNames().filter { it in this.skillsToBuy }

            for (name in namesToBuy) {
                val result: SkillListEntry? = this.skillList.buySkill(name, point)
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

            if (entry.screenPrice <= remainingSkillPoints) {
                skillsToBuy.add(name)
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
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

            if (entry.screenPrice <= remainingSkillPoints) {
                skillsToBuy.add(name)
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
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
        val availableSkills: Map<String, SkillListEntry> = this.skillList.getAvailableWithVirtualUpgradeSkills()
        MessageLog.e("REMOVEME", "============= addUserPlannedSkills: AVAILABLE =============")
        for ((k, v) in availableSkills) {
            MessageLog.e("REMOVEME", "\t$k: ${v.price}")
        }

        for (name in skillPlanSettings.skillPlan) {
            val entry: SkillListEntry? = this.skillList.getEntry(name)
            if (entry == null) {
                MessageLog.e(TAG, "addUserPlannedSkills: Failed to find entry for \"$name\".")
                continue
            }

            // Handle exact matches.
            if (entry.bIsAvailable) {
                skillsToBuy.add(entry.name)
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
                // We're done. Go to the next planned skill.
                MessageLog.e("REMOVEME", "Exact match for \"$name\".")
                continue
            }

            
            // If no downgraded versions exist in our skill list,
            // skip this entry since we won't be able to buy it.
            val availableEntry: SkillListEntry? = entry.getFirstAvailableDowngrade()
            if (availableEntry == null) {
                MessageLog.e("REMOVEME", "Failed to find available downgrade for \"$name\".")
                continue
            }
            MessageLog.e("REMOVEME", "addUserPlannedSkills: ${availableEntry.name} -> $name")
            
            // Get all skill entries from the available entry to the one from
            // the skill plan (inclusive).
            val upgrades: List<SkillListEntry> = availableEntry.getUpgradesUntil(name)

            // Handle in-place upgrade skill chains.
            if (upgrades.all { it.bIsInPlace }) {
                MessageLog.e("REMOVEME", "addUserPlannedSkills: in-place upgrades: $upgrades")
                val totalPrice: Int = upgrades.sumOf { it.price }
                if (totalPrice <= remainingSkillPoints) {
                    upgrades.forEach { it.buy() }
                    skillsToBuy.addAll(upgrades.map { it.name })
                    remainingSkillPoints -= totalPrice
                }
                continue
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
        val userSelectedRunningStyle: RunningStyle? = RunningStyle.fromShortName(userSelectedRunningStyleString)
        val preferredRunningStyle: RunningStyle = userSelectedRunningStyle ?: game.trainee.runningStyle
        // Get user specified track distance.
        // If not specified, then we use the trainee's highest aptitude option.
        val userSelectedTrackDistance: TrackDistance? = TrackDistance.fromName(userSelectedTrackDistanceOverrideString)
        val preferredTrackDistance: TrackDistance = userSelectedTrackDistance ?: game.trainee.trackDistance

        
        MessageLog.e("REMOVEME", "=============== Aptitude Independent ===============")
        for ((name, entry) in this.skillList.getAptitudeIndependentSkills()) {
            MessageLog.e("REMOVEME", "\t${entry.name}")
        }
        MessageLog.e("REMOVEME", "====================================================")
        MessageLog.e("REMOVEME", "================== Running Style ===================")
        MessageLog.e("REMOVEME", "preferredRunningStyle=$preferredRunningStyle")
        for ((name, entry) in this.skillList.getRunningStyleSkills(preferredRunningStyle)) {
            MessageLog.e("REMOVEME", "\t${entry.name}")
        }
        MessageLog.e("REMOVEME", "====================================================")
        MessageLog.e("REMOVEME", "================== Track Distance ==================")
        MessageLog.e("REMOVEME", "preferredTrackDistance=$preferredTrackDistance")
        for ((name, entry) in this.skillList.getTrackDistanceSkills(preferredTrackDistance)) {
            MessageLog.e("REMOVEME", "\t${entry.name}")
        }
        MessageLog.e("REMOVEME", "====================================================")
        // Get only skills which match our aptitudes or user-specified styles or
        // are agnostic of style or track variables.
        val filteredSkills: Map<String, SkillListEntry> =
            this.skillList.getAptitudeIndependentSkills() +
            this.skillList.getRunningStyleSkills(preferredRunningStyle) +
            this.skillList.getTrackDistanceSkills(preferredTrackDistance)

        // Group the remaining entries by their communityTier. Higher values are better.
        // This can contain a NULL group for skills that are not in the tier list.
        val groupedByCommunityTier: Map<Int?, List<SkillListEntry>> = filteredSkills.values
            .groupBy { it.communityTier }
            .toSortedMap(compareByDescending { it })

        // Iterate from highest tier to lowest.
        for ((communityTier, group) in groupedByCommunityTier) {
            // Ignore the null entries for now.
            if (communityTier == null) {
                continue
            }

            // Sort the tier by its point ratio.
            val sortedByPointRatio: List<SkillListEntry> = group.sortedByDescending { it.evaluationPointRatio }
            for (entry in sortedByPointRatio) {
                // Don't add duplicate entries.
                if (entry.name in skillsToBuy) {
                    continue
                }

                // If this skill isnt an in-place upgrade and we have already
                // added its upgraded version to the list, then don't add it.
                if (!entry.bIsAvailable) {
                    continue
                }

                // If we can't afford this skill, continue to the next.
                if (entry.screenPrice > remainingSkillPoints) {
                    continue
                }

                skillsToBuy.add(entry.name)
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
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

        val maxIterations: Int = 5
        var i: Int = 0 
        var remainingSkills: Map<String, SkillListEntry> = this.skillList.getAvailableSkills()
        while (remainingSkills.any { it.value.screenPrice <= remainingSkillPoints}) {
            var sortedByPointRatio: List<SkillListEntry> = remainingSkills.values
                .sortedByDescending { it.evaluationPointRatio }
            for (entry in sortedByPointRatio) {
                // If we can't afford this skill, continue to the next.
                MessageLog.w("REMOVEME", "optimizeRank: ${entry.name}: price=${entry.price}(${entry.screenPrice}), remainingPoints=${remainingSkillPoints}")
                if (entry.screenPrice > remainingSkillPoints) {
                    continue
                }

                // If the entry is already in the list, don't add it again since
                // that would double up on the skill point spending estimate.
                if (skillsToBuy.any { it == entry.name }) {
                    continue
                }

                skillsToBuy.add(entry.name)
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
            }

            remainingSkills = this.skillList.getAvailableSkills()

            MessageLog.e("REMOVEME", "========== REMAINING SKILLS (iter $i) =========")
            for ((k, v) in remainingSkills) {
                MessageLog.e("REMOVEME", "\t$k: ${v.price}")
            }
            MessageLog.e("REMOVEME", "\n\tRemaining Skill Points: $remainingSkillPoints")
            MessageLog.e("REMOVEME", "===============================================")

            if (i++ > maxIterations) {
                break
            }
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
            val price: Int = entry.screenPrice
            MessageLog.d(TAG, "\t$name: ${entry.price}(${entry.screenPrice})")
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
            skillList.parseMockSkillListEntries()
        } else {
            skillList.parseSkillListEntries()
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
        skillList.parseSkillListEntries(::onSkillListEntryDetected)

        //return true
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