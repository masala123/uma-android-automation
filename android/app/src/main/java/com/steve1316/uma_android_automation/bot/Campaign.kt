package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.BotService

import com.steve1316.uma_android_automation.utils.types.DateYear
import com.steve1316.uma_android_automation.utils.types.DateMonth
import com.steve1316.uma_android_automation.utils.types.DatePhase
import com.steve1316.uma_android_automation.utils.types.FanCountClass
import com.steve1316.uma_android_automation.utils.types.BoundingBox

import com.steve1316.uma_android_automation.components.*

import android.graphics.Bitmap
import org.opencv.core.Point
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Base campaign class that contains all shared logic for campaign automation.
 * Campaign-specific logic should be implemented in subclasses by overriding the appropriate methods.
 * By default, URA Finale is handled by this base class.
 */
open class Campaign(val game: Game) {
	protected open val TAG: String = "[${MainActivity.Companion.loggerTag}]Normal"

    private val mustRestBeforeSummer: Boolean = SettingsHelper.getBooleanSetting("training", "mustRestBeforeSummer")
    private val enableFarmingFans: Boolean = SettingsHelper.getBooleanSetting("racing", "enableFarmingFans")

    // Should always check fan count at bot start unless in pre-debut.
    protected var bNeedToCheckFans: Boolean = true
    // Flag used to prevent us from attempting to check fans multiple times in a day.
    // This helps us avoid infinite loops.
    protected var bHasTriedCheckingFansToday: Boolean = false

    // Flag for only checking for maiden races once per day.
    var bHasCheckedForMaidenRaceToday: Boolean = false

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
    open fun handleDialogs(): Pair<Boolean, DialogInterface?> {
        val dialog: DialogInterface? = DialogUtils.getDialog(imageUtils = game.imageUtils)
        if (dialog == null) {
            return Pair(false, null)
        }

        when (dialog.name) {
            "agenda_details" -> dialog.close(imageUtils = game.imageUtils)
            "bonus_umamusume_details" -> dialog.close(imageUtils = game.imageUtils)
            "career" -> dialog.close(imageUtils = game.imageUtils)
            "career_event_details" -> dialog.close(imageUtils = game.imageUtils)
            "career_profile" -> dialog.close(imageUtils = game.imageUtils)
            "choices" -> dialog.close(imageUtils = game.imageUtils)
            "concert_skip_confirmation" -> {
                // Click the checkbox to prevent this popup in the future.
                Checkbox.click(imageUtils = game.imageUtils)
                dialog.ok(imageUtils = game.imageUtils)
            }
            "epithets" -> dialog.close(imageUtils = game.imageUtils)
            "fans" -> dialog.close(imageUtils = game.imageUtils)
            "featured_cards" -> dialog.close(imageUtils = game.imageUtils)
            "give_up" -> dialog.close(imageUtils = game.imageUtils)
            "goal_not_reached" -> {
                // We are handling the logic for when to race on our own.
                // Thus we just close this warning.
                game.racing.encounteredRacingPopup = true
                dialog.close(imageUtils = game.imageUtils)
            }
            "goals" -> dialog.close(imageUtils = game.imageUtils)
            "infirmary" -> {
                Checkbox.click(imageUtils = game.imageUtils)
                dialog.ok(imageUtils = game.imageUtils)
            }
            "insufficient_fans" -> {
                // We are handling the logic for when to race on our own.
                // Thus we just close this warning.
                game.racing.encounteredRacingPopup = true
                dialog.close(imageUtils = game.imageUtils)
            }
            "log" -> dialog.close(imageUtils = game.imageUtils)
            "menu" -> dialog.close(imageUtils = game.imageUtils)
            "mood_effect" -> dialog.close(imageUtils = game.imageUtils)
            "my_agendas" -> dialog.close(imageUtils = game.imageUtils)
            "options" -> dialog.close(imageUtils = game.imageUtils)
            "perks" -> dialog.close(imageUtils = game.imageUtils)
            "placing" -> dialog.close(imageUtils = game.imageUtils)
            "purchase_alarm_clock" -> {
                throw InterruptedException("Ran out of alarm clocks. Stopping bot...")
            }
            "quick_mode_settings" -> {
                val bbox = BoundingBox(
                    x = game.imageUtils.relX(0.0, 160),
                    y = game.imageUtils.relY(0.0, 770),
                    w = game.imageUtils.relWidth(70),
                    h = game.imageUtils.relHeight(460),
                )
                val optionLocations: ArrayList<Point> = IconHorseshoe.findAll(
                    imageUtils = game.imageUtils,
                    region = bbox.toIntArray(),
                    confidence = 0.0,
                )
                if (optionLocations.size == 4) {
                    MessageLog.d(TAG, "[DIALOG] quick_mode_settings: Using findAll method.")
                    val loc: Point = optionLocations[1]
                    game.tap(loc.x, loc.y, IconHorseshoe.template.path)
                } else {
                    MessageLog.d(TAG, "[DIALOG] quick_mode_settings: Using image OCR method.")
                    // Fallback to image detection.
                    RadioCareerQuickShortenAllEvents.click(imageUtils = game.imageUtils)
                }
                
                dialog.ok(imageUtils = game.imageUtils)
            }
            "race_details" -> {
                dialog.ok(imageUtils = game.imageUtils)
            }
            "race_playback" -> {
                // Select portrait mode to prevent game from switching to landscape.
                RadioPortrait.click(imageUtils = game.imageUtils)
                // Click the checkbox to prevent this popup in the future.
                Checkbox.click(imageUtils = game.imageUtils)
                dialog.ok(imageUtils = game.imageUtils)
            }
            "race_recommendations" -> {
                ButtonRaceRecommendationsCenterStage.click(imageUtils = game.imageUtils)
                Checkbox.click(imageUtils = game.imageUtils)
                dialog.ok(imageUtils = game.imageUtils)
            }
            "recreation" -> {
                Checkbox.click(imageUtils = game.imageUtils)
                dialog.ok(imageUtils = game.imageUtils)
            }
            "rest" -> {
                Checkbox.click(imageUtils = game.imageUtils)
                dialog.ok(imageUtils = game.imageUtils)
            }
            "rest_and_recreation" -> {
                // Does not have a checkbox unlike the other rest/rec/etc.
                // TODO: Go through menu to set this option.
                dialog.ok(imageUtils = game.imageUtils)
            }
            "scheduled_races" -> dialog.close(imageUtils = game.imageUtils)
            "schedule_settings" -> dialog.close(imageUtils = game.imageUtils)
            "skill_details" -> dialog.close(imageUtils = game.imageUtils)
            "song_acquired" -> dialog.close(imageUtils = game.imageUtils)
            "spark_details" -> dialog.close(imageUtils = game.imageUtils)
            "sparks" -> dialog.close(imageUtils = game.imageUtils)
            "team_info" -> dialog.close(imageUtils = game.imageUtils)
            "trophy_won" -> dialog.close(imageUtils = game.imageUtils)
            "try_again" -> {
                dialog.ok(imageUtils = game.imageUtils)
            }
            "umamusume_class" -> {
                val bitmap: Bitmap = game.imageUtils.getSourceBitmap()
                val templateBitmap: Bitmap? = game.imageUtils.getBitmaps(LabelUmamusumeClassFans.template.path).second
                if (templateBitmap == null) {
                    MessageLog.e(TAG, "[DIALOG] umamusume_class: Could not get template bitmap for LabelUmamusumeClassFans: ${LabelUmamusumeClassFans.template.path}.")
                    dialog.close(imageUtils = game.imageUtils)
                    game.wait(0.5, skipWaitingForLoading = true)
                    return Pair(true, dialog)
                }
                val point: Point? = LabelUmamusumeClassFans.find(imageUtils = game.imageUtils).first
                if (point == null) {
                    MessageLog.w(TAG, "[DIALOG] umamusume_class: Could not find LabelUmamusumeClassFans.")
                    dialog.close(imageUtils = game.imageUtils)
                    game.wait(0.5, skipWaitingForLoading = true)
                    return Pair(true, dialog)
                }

                // Add a small 8px buffer to vertical component.
                val bbox = BoundingBox(
                    x = game.imageUtils.relX(0.0, (point.x + (templateBitmap.width / 2)).toInt()),
                    y = game.imageUtils.relY(0.0, (point.y - (templateBitmap.height / 2) - 4).toInt()),
                    w = game.imageUtils.relWidth(300),
                    h = game.imageUtils.relHeight(templateBitmap.height + 4),
                )

                val croppedBitmap = game.imageUtils.createSafeBitmap(
                    bitmap,
                    bbox.x,
                    bbox.y,
                    bbox.w,
                    bbox.h,
                    "dialog::umamusume_class: Cropped bitmap.",
                )
                if (croppedBitmap == null) {
                    MessageLog.e(TAG, "[DIALOG] umamusume_class: Failed to crop bitmap.")
                    dialog.close(imageUtils = game.imageUtils)
                    game.wait(0.5, skipWaitingForLoading = true)
                    return Pair(true, dialog)
                }
                val fans = game.imageUtils.getUmamusumeClassDialogFanCount(croppedBitmap)
                if (fans != null) {
                    game.trainee.fans = fans
                    bNeedToCheckFans = false
                    MessageLog.d(TAG, "[DIALOG] umamusume_class: Updated fan count: ${game.trainee.fans}")
                } else {
                    MessageLog.w(TAG, "[DIALOG] umamusume_class: getUmamusumeClassDialogFanCount returned NULL.")
                }
                
                dialog.close(imageUtils = game.imageUtils)
            }
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
            "unity_cup_available" -> dialog.close(imageUtils = game.imageUtils)
            "unmet_requirements" -> dialog.close(imageUtils = game.imageUtils)
            else -> {
                return Pair(false, dialog)
            }
        }

        game.wait(0.5, skipWaitingForLoading = true)
        return Pair(true, dialog)
    }

    fun checkAptitudes() {
        MessageLog.d(TAG, "Checking aptitudes...")
        // We update the trainee aptitudes by checking the stats dialog.
        // So in here we just open the dialog then the dialog handler
        // will take care of the rest.
        ButtonHomeFullStats.click(imageUtils = game.imageUtils)
        game.wait(1.0, skipWaitingForLoading = true)
    }

    fun checkFans() {
        MessageLog.d(TAG, "Checking fans...")
        // Detect the new fan count by clicking the fans info button.
        // This opens the "Umamusume Class" dialog.
        // We process this dialog in the dialog handler.
        // This button is in a different position for Unity/URA scenarios.
        // The Unity scenario has an info button just like the ButtonHomeFansInfo
        // button so they are easily mistaken by OCR, thus we just tap the location manually.
        if (game.scenario == "Unity Cup") {
            ButtonHomeFansInfo.click(game.imageUtils, region = game.imageUtils.regionBottomHalf, tries = 10)
        } else {
            ButtonHomeFansInfo.click(game.imageUtils, region = game.imageUtils.regionTopHalf, tries = 10)
        }

        bHasTriedCheckingFansToday = true
    }

    fun getFanCountClass(bitmap: Bitmap? = null): FanCountClass? {
        val (bitmap, templateBitmap) = game.imageUtils.getBitmaps(ButtonHomeFansInfo.template.path)
        if (templateBitmap == null) {
            MessageLog.e(TAG, "getFanCountClass: Could not get template bitmap for ButtonHomeFansInfo: ${ButtonHomeFansInfo.template.path}.")
            return null
        }
        val point: Point? = ButtonHomeFansInfo.find(imageUtils = game.imageUtils).first
        if (point == null) {
            MessageLog.w(TAG, "getFanCountClass: Could not find ButtonHomeFansInfo.")
            return null
        }

        val bbox = BoundingBox(
            x = game.imageUtils.relX(0.0, (point.x - (templateBitmap.width / 2)).toInt() - 180),
            // Add a small buffer to vertical component.
            y = game.imageUtils.relY(0.0, (point.y - 16).toInt()),
            w = game.imageUtils.relWidth(180),
            // 32px minimum for google ML kit.
            h = game.imageUtils.relHeight(32),
        )

        val text: String = game.imageUtils.performOCROnRegion(
            bitmap,
            bbox.x,
            bbox.y,
            bbox.w,
            bbox.h,
			useThreshold = false,
            useGrayscale = true,
            scale = 1.0,
            ocrEngine = "tesseract",
            debugName = "getFanCountClass",
        )
        val fanCountClass: FanCountClass? = FanCountClass.fromName(text.replace(" ", "_"))
        if (fanCountClass == null) {
            MessageLog.w(TAG, "getFanCountClass:: Failed to match text to a FanCountClass: $text")
        }
        return fanCountClass
    }

	/**
	 * Campaign-specific training event handling.
	 */
	open fun handleTrainingEvent() {
		game.trainingEvent.handleTrainingEvent()
	}

	/**
	 * Campaign-specific race event handling.
	 */
	open fun handleRaceEvents(): Boolean {
        val bDidRace: Boolean = game.racing.handleRaceEvents()
        bNeedToCheckFans = bDidRace

		return bDidRace
	}

	/**
	 * Campaign-specific checks for special screens or conditions.
	 */
	open fun checkCampaignSpecificConditions(): Boolean {
		return false
	}

	fun startAptitudesDetectionTest() {
		MessageLog.i(TAG, "\n[TEST] Now beginning the Aptitudes Detection test on the Main screen.")
		MessageLog.i(TAG, "[TEST] Note that this test is dependent on having the correct scale.")
        checkAptitudes()
        handleDialogs()
	}

    fun startTrainingScreenOCRTest() {
        MessageLog.i(TAG, "---- startTrainingScreenOCRTest START ----")

        var numPass: Int = 0
        var numFail: Int = 0

        // Simple components to test.
        val componentsToTest: List<ComponentInterface> = listOf(
            LabelEnergy,
            LabelStatTableHeaderSkillPoints,
            LabelTrainingFailureChance,
            ButtonTrainingSpeed,
            ButtonTrainingStamina,
            ButtonTrainingPower,
            ButtonTrainingGuts,
            ButtonTrainingWit,
            ButtonBack,
            ButtonLog,
            ButtonBurger,
        )
        for (componentToTest in componentsToTest) {
            if (componentToTest.check(game.imageUtils)) {
                MessageLog.i(TAG, "[PASS] ${componentToTest.template.path}")
                numPass++
            } else {
                MessageLog.e(TAG, "[FAIL] ${componentToTest.template.path}")
                numFail++
            }
        }

        when {
            IconTrainingHeaderSpeed.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${IconTrainingHeaderSpeed.template.path}")
                numPass++
            }
            IconTrainingHeaderStamina.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${IconTrainingHeaderStamina.template.path}")
                numPass++
            }
            IconTrainingHeaderPower.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${IconTrainingHeaderPower.template.path}")
                numPass++
            }
            IconTrainingHeaderGuts.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${IconTrainingHeaderGuts.template.path}")
                numPass++
            }
            IconTrainingHeaderWit.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${IconTrainingHeaderWit.template.path}")
                numPass++
            }
            else -> {
                MessageLog.e(TAG, "[FAIL] Could not detect any training header icons.")
                numFail++
            }
        }

        MessageLog.i(TAG, "---- startTrainingScreenOCRTest END: PASS=$numPass, FAIL=$numFail ----")
    }

    fun startMainScreenOCRTest() {
        MessageLog.i(TAG, "---- startMainScreenOCRTest START ----")

        var numPass: Int = 0
        var numFail: Int = 0

        // Simple components to test.
        val componentsToTest: List<ComponentInterface> = listOf(
            ButtonHomeFansInfo,
            IconTazuna,
            LabelEnergy,
            LabelStatTableHeaderSkillPoints,
            ButtonHomeFullStats,
            ButtonRest,
            ButtonTraining,
            ButtonSkills,
            ButtonInfirmary,
            ButtonRecreation,
            ButtonLog,
            ButtonBurger,
        )
        for (componentToTest in componentsToTest) {
            if (componentToTest.check(game.imageUtils)) {
                MessageLog.i(TAG, "[PASS] ${componentToTest.template.path}")
                numPass++
            } else {
                MessageLog.e(TAG, "[FAIL] ${componentToTest.template.path}")
                numFail++
            }
        }

        // More complex components to test that have multiple different states.

        if (ButtonRaceSelectExtra.check(game.imageUtils)) {
            MessageLog.i(TAG, "[PASS] ${ButtonRaceSelectExtra.template.path}")
            numPass++
        } else if (ButtonRaceSelectExtraLocked.check(game.imageUtils)) {
            MessageLog.i(TAG, "[PASS] ${ButtonRaceSelectExtraLocked.template.path}")
            numPass++
        } else {
            MessageLog.e(TAG, "[FAIL] ${ButtonRaceSelectExtra.template.path}, ${ButtonRaceSelectExtraLocked.template.path}")
            numFail++
        }

        MessageLog.i(TAG, "Testing mood components...")
        when {
            IconMoodGreat.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${IconMoodGreat.template.path}")
                numPass++
            }
            IconMoodGood.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${IconMoodGood.template.path}")
                numPass++
            }
            IconMoodNormal.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${IconMoodNormal.template.path}")
                numPass++
            }
            IconMoodBad.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${IconMoodBad.template.path}")
                numPass++
            }
            IconMoodAwful.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${IconMoodAwful.template.path}")
                numPass++
            }
            else -> {
                MessageLog.e(TAG, "[FAIL] Could not detect any mood icons.")
                numFail++
            }
        }

        MessageLog.i(TAG, "---- startMainScreenOCRTest END: PASS=$numPass, FAIL=$numFail ----")
    }

    fun handleMainScreen(): Boolean {
        if (!game.checkMainScreen()) {
            return false
        }

        // Operations to be done every time the date changes.
        if (game.updateDate()) {
            // Reset flags on date change.
            game.racing.encounteredRacingPopup = false
            game.racing.raceRepeatWarningCheck = false
            bHasTriedCheckingFansToday = false
            bHasCheckedForMaidenRaceToday = false

            // Update the fan count class every time we're at the main screen.
            val fanCountClass: FanCountClass? = getFanCountClass()
            if (fanCountClass != null) {
                game.trainee.fanCountClass = fanCountClass
            }
            // Update trainee information using parallel processing with shared screenshot.
            val sourceBitmap = game.imageUtils.getSourceBitmap()
            val skillPointsLocation = game.imageUtils.findImageWithBitmap("skill_points", sourceBitmap, suppressError = true)
            if (!BotService.isRunning) {
                return false
            }
            
            // Use CountDownLatch to run the operations in parallel
            // 1 racingRequirements + 5 stats + 1 skill points + 1 mood = 8 threads
            val latch = CountDownLatch(8)

            MessageLog.disableOutput = true
            
            // Threads 1-5: Update stats (one thread per stat, created inside updateStats).
            // Pass the external latch so updateStats can count down for each stat thread.
            game.trainee.updateStats(game.imageUtils, sourceBitmap, skillPointsLocation, latch)
            
            // Thread 6: Update skill points.
            Thread {
                try {
                    game.trainee.updateSkillPoints(game.imageUtils, sourceBitmap, skillPointsLocation)
                } catch (e: Exception) {
                    MessageLog.e(TAG, "Error in updateSkillPoints thread: ${e.stackTraceToString()}")
                } finally {
                    latch.countDown()
                }
            }.apply { isDaemon = true }.start()
            
            // Thread 7: Update mood.
            Thread {
                try {
                    game.trainee.updateMood(game.imageUtils, sourceBitmap)
                } catch (e: Exception) {
                    MessageLog.e(TAG, "Error in updateMood thread: ${e.stackTraceToString()}")
                } finally {
                    latch.countDown()
                }
            }.apply { isDaemon = true }.start()

            // Thread 8: Update racing requirements.
            Thread {
                try {
                    game.racing.checkRacingRequirements(sourceBitmap)
                } catch (e: Exception) {
                    MessageLog.e(TAG, "Error in checkRacingRequirements thread: ${e.stackTraceToString()}")
                } finally {
                    latch.countDown()
                }
            }.apply { isDaemon = true }.start()
            
            // Wait for all threads to complete.
            try {
                latch.await(10, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                MessageLog.e(TAG, "Datre change operations threads timed out.")
            } finally {
                MessageLog.disableOutput = false
            }

            MessageLog.i(TAG, "[TRAINEE] Skills Updated: ${game.trainee.getStatsString()}")
            MessageLog.i(TAG, "[TRAINEE] Mood Updated: ${game.trainee.mood}")
        }

        // If the required skill points has been reached, stop the bot.
        if (game.enableSkillPointCheck && game.trainee.skillPoints >= game.skillPointsRequired) {
            throw InterruptedException("Bot reached skill point check threshold. Stopping bot...")
        }

        // Since we're at the main screen, we don't need to worry about this
        // flag anymore since we will update our aptitudes here if needed.
        game.trainee.bTemporaryRunningStyleAptitudesUpdated = false

        if (!game.trainee.bHasUpdatedAptitudes) {
            checkAptitudes()
            MessageLog.i(TAG, "\n[TRAINEE] Current aptitudes:\n${game.trainee.getAptitudesString()}")
            return true
        }

        val bIsMandatoryRaceDay = IconRaceDayRibbon.check(imageUtils = game.imageUtils)
        var needToRace = bIsMandatoryRaceDay
        // We don't need to bother checking fans on a mandatory race day.
        if (
            !game.currentDate.bIsFinaleSeason &&
            !bIsMandatoryRaceDay &&
            bNeedToCheckFans &&
            !bHasTriedCheckingFansToday
        ) {
            checkFans()
            return true
        }

        // Check if bot should stop before the finals.
        if (game.checkFinalsStop()) {
            throw InterruptedException("Reached finals. Stopping bot...")
        }

        if (bIsMandatoryRaceDay) {
            needToRace = true
        } else if (!game.racing.encounteredRacingPopup) {
            if (game.racing.enableForceRacing) {
                // If force racing is enabled, skip all other activities and go straight to racing
                MessageLog.i(TAG, "Force racing enabled - skipping all other activities and going straight to racing.")
                needToRace = true
            } else if (
                !bHasCheckedForMaidenRaceToday &&
                !game.currentDate.bIsPreDebut &&
                !game.trainee.bHasCompletedMaidenRace
            ) {
                // Need to check for maiden race.
                MessageLog.i(TAG, "[INFO] Bot has not yet completed maiden race. Checking for valid maiden race...")
                needToRace = true
            } else if (
                mustRestBeforeSummer &&
                (   game.currentDate.year == DateYear.CLASSIC ||
                    game.currentDate.year == DateYear.SENIOR
                ) &&
                game.currentDate.month == DateMonth.JUNE &&
                game.currentDate.phase == DatePhase.LATE
            ) {
                // Check if we need to rest before Summer Training (June Early/Late in Classic/Senior Year).
                MessageLog.i(TAG, "Forcing rest during ${game.currentDate} in preparation for Summer Training.")
                game.recoverEnergy()
            } else if (game.checkInjury() && !game.checkFinals()) {
                game.findAndTapImage("ok", region = game.imageUtils.regionMiddle)
                game.wait(3.0)
            } else if (game.recoverMood() && !game.checkFinals()) {
            } else if (game.currentDate.day >= 16 && game.racing.checkEligibilityToStartExtraRacingProcess()) {
                MessageLog.i(TAG, "[INFO] Bot has no injuries, mood is sufficient and extra races can be run today. Setting the needToRace flag to true.")
                needToRace = true
            } else {
                MessageLog.i(TAG, "[INFO] Training due to it not being an extra race day.")
                game.training.handleTraining()
            }
        }

        if (game.racing.encounteredRacingPopup || needToRace) {
            MessageLog.i(TAG, "[INFO] All checks are cleared for racing.")
            if (!handleRaceEvents()) {
                if (game.racing.detectedMandatoryRaceCheck) {
                    throw InterruptedException("Mandatory race detected. Stopping bot...")
                }
                ButtonBack.click(imageUtils = game.imageUtils)
                game.wait(1.0)
                game.training.handleTraining()
            }
        }
        return true
    }

	/**
	 * Main automation loop that handles all shared logic.
	 */
	fun start() {
		while (true) {
            try {
                // We always check for dialogs first.
                if (handleDialogs().first) {
                    continue
                } else if (game.handleDialogs().first) {
                    continue
                } else if (handleMainScreen()) {
                    continue
                } else if (game.checkTrainingEventScreen()) {
                    // If the bot is at the Training Event screen, that means there are selectable options for rewards.
                    handleTrainingEvent()
                } else if (game.checkMandatoryRacePrepScreen()) {
                    // If the bot is at the Main screen with the button to select a race visible,
                    // that means the bot needs to handle a mandatory race.
                    if (!handleRaceEvents() && game.racing.detectedMandatoryRaceCheck) {
                        throw InterruptedException("Mandatory race detected. Stopping bot...")
                    }
                } else if (game.checkRacingScreen()) {
                    // If the bot is already at the Racing screen, then complete this standalone race.
                    game.racing.handleStandaloneRace()
                } else if (game.checkEndScreen()) {
                    // Stop when the bot has reached the screen where it details the overall result of the run.
                    throw InterruptedException("Bot had reached end of run. Stopping bot...")
                } else if (checkCampaignSpecificConditions()) {
                    MessageLog.i(TAG, "Campaign-specific checks complete.")
                } else if (game.handleInheritanceEvent()) {
                    // If the bot is at the Inheritance screen, then accept the inheritance.
                } else if (game.performMiscChecks()) {
                    MessageLog.d(TAG, "Misc checks complete.")
                } else {
                    MessageLog.v(TAG, "Did not detect the bot being at the following screens: Main, Training Event, Inheritance, Mandatory Race Preparation, Racing and Career End.")
                }
            } catch (e: InterruptedException) {
                game.notificationMessage = "Campaign main loop exiting: ${e.message}"
                MessageLog.e(TAG, "Campaign main loop exiting: ${e.message}")
                break
            }
		}
	}
}
