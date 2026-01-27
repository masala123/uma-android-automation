package com.steve1316.uma_android_automation.bot

import android.graphics.Bitmap
import org.opencv.core.Point
import org.json.JSONArray
import org.json.JSONObject

import com.steve1316.automation_library.utils.SettingsHelper
import com.steve1316.automation_library.utils.MessageLog

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.SkillList

import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.TrackSurface
import com.steve1316.uma_android_automation.utils.types.Aptitude

private const val USE_MOCK_DATA: Boolean = false
private const val MOCK_SKILL_POINTS: Int = 1495

class SkillPlan (private val game: Game) {
    private val TAG: String = "[${MainActivity.loggerTag}]SkillPlan"

    // Get user settings for skill plans.
    val skillSettingRunningStyleString = SettingsHelper.getStringSetting("skills", "preferredRunningStyle")
    val skillSettingTrackDistanceString = SettingsHelper.getStringSetting("skills", "preferredTrackDistance")
    val skillSettingTrackSurfaceString = SettingsHelper.getStringSetting("skills", "preferredTrackSurface")

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

    // Other settings.
    private val trainingSettingTrackDistanceString = SettingsHelper.getStringSetting("training", "preferredDistanceOverride")
    private val racingSettingRunningStyleString = SettingsHelper.getStringSetting("racing", "originalRaceStrategy")

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

    /** Data class used to store all skill plan settings for easier access. */
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

    private fun getNegativeSkills(
        skillPlanSettings: SkillPlanSettings,
        skillList: SkillList,
        skillsToBuy: List<String>,
        availableSkillPoints: Int,
    ): Map<String, Int> {
        if (!skillPlanSettings.buyNegativeSkills) {
            return emptyMap()
        }

        val result: MutableMap<String, Int> = mutableMapOf()
        var remainingSkillPoints: Int = availableSkillPoints

        val entries: Map<String, SkillListEntry> = skillList.getNegativeSkills()
        for ((name, entry) in entries) {
            // Don't add any duplicate entries.
            if (name in skillsToBuy) {
                continue
            }

            if (entry.screenPrice <= remainingSkillPoints) {
                result[name] = entry.screenPrice
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
            }
        }

        return result.toMap()
    }

    private fun getInheritedUniqueSkills(
        skillPlanSettings: SkillPlanSettings,
        skillList: SkillList,
        skillsToBuy: List<String>,
        availableSkillPoints: Int,
    ): Map<String, Int> {
        if (!skillPlanSettings.buyInheritedSkills) {
            return emptyMap()
        }

        val result: MutableMap<String, Int> = mutableMapOf()
        var remainingSkillPoints: Int = availableSkillPoints

        val entries: Map<String, SkillListEntry> = skillList.getInheritedUniqueSkills()
        for ((name, entry) in entries) {
            if (name in skillsToBuy || name in result) {
                continue
            }

            if (entry.screenPrice <= remainingSkillPoints) {
                result[name] = entry.screenPrice
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
            }
        }

        return result.toMap()
    }

    private fun getUserPlannedSkills(
        skillPlanSettings: SkillPlanSettings,
        skillList: SkillList,
        skillsToBuy: List<String>,
        availableSkillPoints: Int,
    ): Map<String, Int> {
        if (skillPlanSettings.skillPlan.isEmpty()) {
            return emptyMap()
        }

        val result: MutableMap<String, Int> = mutableMapOf()
        var remainingSkillPoints: Int = availableSkillPoints

        // If two different versions of one skill are in the skill list AND in the
        // skill plan, we want to buy the highest level version of that skill that we can afford.
        // For example, if Corner Recovery O and Swinging Maestro are both in the skill
        // plan, and both entries are in the skill list, then we want to buy Swinging Maestro.
        // However, if we do not have enough points for Swinging Maestro, then attempt to
        // buy Corner Recovery O instead.
        for (name in skillPlanSettings.skillPlan) {
            // Don't add duplicate entries.
            if (name in skillsToBuy || name in result) {
                continue
            }

            val entry: SkillListEntry? = skillList.getEntry(name)
            if (entry == null) {
                MessageLog.e(TAG, "addUserPlannedSkills: Failed to find entry for \"$name\".")
                continue
            }

            // Handle exact matches.
            if (entry.bIsAvailable) {
                result[name] = entry.screenPrice
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
                continue
            }

            // If there are no exact matches, then our only hope of buying
            // this skill is if it is in an in-place upgrade chain.
            // This is because getting a skill hint for an in-place chain skill
            // means that you can upgrade to any higher versions of it.
            // However if it isn't an in-place chain, then you cannot unlock the
            // upgraded version simply by buying a lower version. The higher
            // version requires a separate skill hint to unlock.

            // If no downgraded versions exist in our skill list, skip this
            // entry since we won't be able to buy it.
            val availableEntry: SkillListEntry? = entry.getFirstAvailableDowngrade()
            if (availableEntry == null) {
                continue
            }

            // Otherwise if there IS a downgraded verison in the skill list, then
            // we need to purchase it for the planned skill to become available.

            // Get all skill entries from the available entry to the one from
            // the skill plan (inclusive).
            val upgrades: List<SkillListEntry> = availableEntry.getUpgradesUntil(name)

            // Handle in-place upgrade skill chains.
            if (upgrades.all { it.bIsInPlace }) {
                // Only add entries which we haven't already added.
                val unacquired: List<SkillListEntry> = upgrades
                    .filter {it.name !in skillsToBuy && it.name !in result }
            
                val totalPrice: Int = unacquired.sumOf { it.price }
                if (totalPrice <= remainingSkillPoints) {
                    unacquired.forEach { it.buy() }
                    // TODO: Does this need to be screenPrice instead of price?
                    val toAdd: Map<String, Int> = unacquired.associate { it.name to it.price }
                    result.putAll(toAdd)
                    remainingSkillPoints -= totalPrice
                }
                continue
            }
        }

        return result.toMap()
    }

    private fun getSkillsToBuyCommon(
        skillPlanSettings: SkillPlanSettings,
        skillList: SkillList,
        skillsToBuy: List<String>,
        availableSkillPoints: Int,
    ): Map<String, Int> {
        val result: MutableMap<String, Int> = mutableMapOf()

        result += getNegativeSkills(
            skillPlanSettings = skillPlanSettings,
            skillList = skillList,
            skillsToBuy = skillsToBuy + result.keys.toList(),
            availableSkillPoints = availableSkillPoints - result.values.sum(),
        )

        result += getInheritedUniqueSkills(
            skillPlanSettings = skillPlanSettings,
            skillList = skillList,
            skillsToBuy = skillsToBuy + result.keys.toList(),
            availableSkillPoints = availableSkillPoints - result.values.sum(),
        )

        result += getUserPlannedSkills(
            skillPlanSettings = skillPlanSettings,
            skillList = skillList,
            skillsToBuy = skillsToBuy + result.keys.toList(),
            availableSkillPoints = availableSkillPoints - result.values.sum(),
        )

        return result.toMap()
    }

    /**
     *
     * NOTE: Currently doesn't do anything but it's here since we have a strategy
     * option for DEFAULT so it'd be weird to not have a function ready for it.
     */
    private fun getSkillsToBuyDefaultStrategy(
        skillPlanSettings: SkillPlanSettings,
        skillList: SkillList,
        skillsToBuy: List<String>,
        availableSkillPoints: Int,
    ): Map<String, Int> {
        val result: MutableMap<String, Int> = mutableMapOf()

        return result.toMap()
    }

    private fun getSkillsToBuyOptimizeSkillsStrategy(
        skillPlanSettings: SkillPlanSettings,
        skillList: SkillList,
        skillsToBuy: List<String>,
        availableSkillPoints: Int,
    ): Map<String, Int> {
        val result: MutableMap<String, Int> = mutableMapOf()
        var remainingSkillPoints: Int = availableSkillPoints

        // Get user specified running style.
        var preferredRunningStyle: RunningStyle? = when (skillSettingRunningStyleString.lowercase()) {
            "disabled" -> null
            "inherit" -> RunningStyle.fromShortName(racingSettingRunningStyleString) ?: game.trainee.runningStyle
            else -> RunningStyle.fromName(skillSettingRunningStyleString)
        }


        // Get user specified track distance.
        var preferredTrackDistance: TrackDistance? = when (skillSettingTrackDistanceString.lowercase()) {
            "disabled" -> null
            "inherit" -> TrackDistance.fromName(trainingSettingTrackDistanceString) ?: game.trainee.trackDistance
            else -> TrackDistance.fromName(skillSettingTrackDistanceString)
        }

        // Get user specified track surface.
        var preferredTrackSurface: TrackSurface? = when (skillSettingTrackSurfaceString.lowercase()) {
            "disabled" -> null
            else -> TrackSurface.fromName(skillSettingTrackSurfaceString)
        }

        MessageLog.d(TAG, "Using preferred running style: $preferredRunningStyle")
        MessageLog.d(TAG, "Using preferred track distance: $preferredTrackDistance")
        MessageLog.d(TAG, "Using preferred track surface: $preferredTrackSurface")

        // Get only skills which match our aptitudes or user-specified styles or
        // are agnostic of style or track variables.
        val filteredSkills: MutableMap<String, SkillListEntry> = mutableMapOf()

        filteredSkills.putAll(skillList.getAptitudeIndependentSkills(preferredRunningStyle))

        if (preferredRunningStyle != null) {
                filteredSkills.putAll(skillList.getRunningStyleSkills(preferredRunningStyle))
                filteredSkills.putAll(skillList.getInferredRunningStyleSkills(preferredRunningStyle))
        }
        if (preferredTrackDistance != null) {
            filteredSkills.putAll(skillList.getTrackDistanceSkills(preferredTrackDistance))
        }
        if (preferredTrackSurface != null) {
            filteredSkills.putAll(skillList.getTrackSurfaceSkills(preferredTrackSurface))
        }

        // Group the remaining entries by their communityTier. Higher values are better.
        // This can contain a NULL group for skills that are not in the tier list.
        val groupedByCommunityTier: Map<Int?, List<SkillListEntry>> = filteredSkills.values
            .groupBy { it.communityTier }
            .toSortedMap(compareByDescending { it })

        // Iterate from highest tier to lowest.
        for ((communityTier, group) in groupedByCommunityTier) {
            // Ignore the NULL entries since they aren't ranked.
            if (communityTier == null) {
                continue
            }

            MessageLog.v(TAG, "============ SKILL COMMUNITY TIER $communityTier =============")

            // Sort the tier by its point ratio.
            val sortedByPointRatio: List<SkillListEntry> = group.sortedByDescending { it.evaluationPointRatio }
            for (entry in sortedByPointRatio) {
                MessageLog.v(TAG, "\t${entry.name} -> price [real(shown)]: ${entry.price}(${entry.screenPrice}), rank(ratio): ${entry.evaluationPoints}(" + "%.2f".format(entry.evaluationPointRatio) + ")")
                // Don't add duplicate entries.
                if (entry.name in result || entry.name in skillsToBuy) {
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

                result[entry.name] = entry.screenPrice
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
            }
            MessageLog.v(TAG, "=================================================")
        }

        // We may still have skill points after buying all aptitude-based skills.
        // Spend remaining points to optimize rank.
        result += getSkillsToBuyOptimizeRankStrategy(
            skillPlanSettings = skillPlanSettings,
            skillList = skillList,
            skillsToBuy = skillsToBuy + result.keys.toList(),
            availableSkillPoints = remainingSkillPoints,
        )

        return result.toMap()
    }

    private fun getSkillsToBuyOptimizeRankStrategy(
        skillPlanSettings: SkillPlanSettings,
        skillList: SkillList,
        skillsToBuy: List<String>,
        availableSkillPoints: Int,
    ): Map<String, Int> {
        val result: MutableMap<String, Int> = mutableMapOf()
        var remainingSkillPoints: Int = availableSkillPoints

        // Infinite loop protection.
        val maxIterations: Int = 10
        var i: Int = 0 
        var remainingSkills: Map<String, SkillListEntry> = skillList.getAvailableSkills()
        while (remainingSkills.any { it.value.screenPrice <= remainingSkillPoints}) {
            var sortedByPointRatio: List<SkillListEntry> = remainingSkills.values
                .sortedByDescending { it.evaluationPointRatio }
            for (entry in sortedByPointRatio) {
                // Don't add duplicate entries.
                if (entry.name in result || entry.name in skillsToBuy) {
                    continue
                }

                // If we can't afford this skill, continue to the next.
                if (entry.screenPrice > remainingSkillPoints) {
                    continue
                }

                result[entry.name] = entry.screenPrice
                remainingSkillPoints -= entry.screenPrice
                entry.buy()
            }

            remainingSkills = skillList.getAvailableSkills()

            if (i++ > maxIterations) {
                break
            }
        }

        return result.toMap()
    }

    private fun getSkillsToBuy(
        skillPlanSettings: SkillPlanSettings,
        skillList: SkillList,
        availableSkillPoints: Int,
    ): Map<String, Int> {
        MessageLog.d(TAG, "[SKILLS] Beginning process of calculating skills to purchase...")

        if(!skillPlanSettings.enabled) {
            MessageLog.i(TAG, "[SKILLS] Skill plan is disabled. No skills will be purchased.")
            return emptyMap()
        }

        MessageLog.d(TAG, "======================= Skill Plan =======================")
        MessageLog.d(TAG, "Spending Strategy: ${skillPlanSettings.spendingStrategy}")
        MessageLog.d(TAG, "Buy Inherited Skills: ${skillPlanSettings.buyInheritedSkills}")
        MessageLog.d(TAG, "Buy Negative Skills: ${skillPlanSettings.buyNegativeSkills}")
        MessageLog.d(TAG, "User-Specified Skills:" + if (skillPlanSettings.skillPlan.isEmpty()) " None" else "")
        for (name in skillPlanSettings.skillPlan) {
            MessageLog.d(TAG, "\t$name")
        }
        MessageLog.d(TAG, "==========================================================")

        val result: MutableMap<String, Int> = mutableMapOf()

        // We always perform these common operations.
        // Other strategies only add on top of these results.
        result += getSkillsToBuyCommon(
            skillPlanSettings = skillPlanSettings,
            skillList = skillList,
            skillsToBuy = result.keys.toList(),
            availableSkillPoints = availableSkillPoints - result.values.sum(),
        )

        result += when (skillPlanSettings.spendingStrategy) {
            SpendingStrategy.DEFAULT -> getSkillsToBuyDefaultStrategy(
                skillPlanSettings = skillPlanSettings,
                skillList = skillList,
                skillsToBuy = result.keys.toList(),
                availableSkillPoints = availableSkillPoints - result.values.sum(),
            )
            SpendingStrategy.OPTIMIZE_SKILLS -> getSkillsToBuyOptimizeSkillsStrategy(
                skillPlanSettings = skillPlanSettings,
                skillList = skillList,
                skillsToBuy = result.keys.toList(),
                availableSkillPoints = availableSkillPoints - result.values.sum(),
            )
            SpendingStrategy.OPTIMIZE_RANK -> getSkillsToBuyOptimizeRankStrategy(
                skillPlanSettings = skillPlanSettings,
                skillList = skillList,
                skillsToBuy = result.keys.toList(),
                availableSkillPoints = availableSkillPoints - result.values.sum(),
            )
        }

        MessageLog.d(TAG, "============== Skills To Buy ==============")
        for ((name, price) in result) {
            MessageLog.d(TAG, "\t$name: $price")
        }
        MessageLog.d(TAG, "\n\tTOTAL: ${result.values.sum()} / ${if (USE_MOCK_DATA) MOCK_SKILL_POINTS else skillList.skillPoints} pts")
        MessageLog.d(TAG, "===========================================")

        return result.toMap()
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

    private fun onSkillListEntryDetected(
        entry: SkillListEntry,
        point: Point,
        skillsToBuy: List<String>,
        skillList: SkillList,
    ) {
        if (entry.bIsObtained || entry.bIsVirtual) {
            return
        }

        if (entry.name !in skillsToBuy) {
            return
        }

        // Determine if there are other in-place versions of this skill that
        // we need to buy.
        if (entry.bIsInPlace) {
            val namesToBuy: List<String> = listOf(entry.name) +
                entry.getUpgradeNames().filter { it in skillsToBuy }

            for (name in namesToBuy) {
                val result: SkillListEntry? = skillList.buySkill(name, point)
                if (result != null) {
                    MessageLog.i(TAG, "Buying \"${result.name}\" for ${result.price} pts")
                }
            }
        } else {
            val result: SkillListEntry? = skillList.buySkill(entry.name, point)
            if (result != null) {
                MessageLog.i(TAG, "Buying \"${result.name}\" for ${result.price} pts")
            }
        }
    }

    fun start(): Boolean {
        val bitmap: Bitmap = game.imageUtils.getSourceBitmap()

        val skillList: SkillList = SkillList(game)

        // Verify that we are at the skill list screen.
        val bIsCareerComplete: Boolean = skillList.checkCareerCompleteSkillListScreen(bitmap)
        if (!bIsCareerComplete && !skillList.checkSkillListScreen(bitmap)) {
            MessageLog.e(TAG, "[SKILLS] Not at skill list screen. Aborting...")
            return false
        }

        val skillPlanSettings: SkillPlanSettings = if (bIsCareerComplete) {
            skillPlanSettingsCareerComplete
        } else {
            skillPlanSettingsPreFinals
        }

        // If no options are enabled for purchasing skills, then we should
        // exit early to avoid having to scan the whole skill list.
        if (
            skillPlanSettings.skillPlan.isEmpty() &&
            skillPlanSettings.spendingStrategy == SpendingStrategy.DEFAULT &&
            !skillPlanSettings.buyInheritedSkills &&
            !skillPlanSettings.buyNegativeSkills
        ) {
            MessageLog.w(TAG, "[SKILLS] Skill Plan is empty and no options to purchase any skills are enabled. Aborting...")
            skillList.cancelAndExit()
            return true
        }

        // Purchasing skills depends on us knowing our aptitudes.
        // If we haven't acquired them yet, then we need to force check them.
        // This can happen if we start the bot at the skill list screen
        // or at the end of a career.
        if (!USE_MOCK_DATA && !game.trainee.bHasUpdatedAptitudes) {
            skillList.checkStats()
        }

        val skillPoints: Int = if (USE_MOCK_DATA) {
            MOCK_SKILL_POINTS
        } else {
            skillList.detectSkillPoints(bitmap) ?: 0
        }
        // The cheapest skills are all 70 points and with discounts
        // can be as low as 42 points. If anything is less than this, then
        // we should update this.
        if (skillPoints < 42) {
            MessageLog.i(TAG, "[SKILLS] Skill Points < 42. Cannot afford any skills. Aborting...")
            skillList.cancelAndExit()
            return true
        }
        
        // Gather all skill list entry data.
        skillList.parseSkillListEntries(bUseMockData = USE_MOCK_DATA)
        if (skillList.getAllSkills().isEmpty()) {
            MessageLog.e(TAG, "[SKILLS] Failed to detect skills.")
            skillList.cancelAndExit()
            return false
        }

        skillList.printSkillListEntries(verbose = true)

        // Calculate list of skills to purchase.
        val result: Map<String, Int> = getSkillsToBuy(
            skillPlanSettings = skillPlanSettings,
            skillList = skillList,
            availableSkillPoints = skillPoints,
        )

        // No skills to buy. Return to previous screen.
        if (result.isEmpty()) {
            skillList.cancelAndExit()
            return true
        }

        // Go back through skill list and purchase skills.
        skillList.parseSkillListEntries() { _, entry: SkillListEntry, point: Point ->
            onSkillListEntryDetected(
                entry = entry,
                point = point,
                skillsToBuy = result.keys.toList(),
                skillList = skillList,
            )
        }

        skillList.confirmAndExit()

        return true
    }
}
