package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.automation_library.utils.MessageLog

import com.steve1316.uma_android_automation.utils.types.DateYear
import com.steve1316.uma_android_automation.utils.types.DateMonth
import com.steve1316.uma_android_automation.utils.types.DatePhase

import com.steve1316.uma_android_automation.components.*

import android.graphics.Bitmap
import org.opencv.core.Point

/**
 * Base campaign class that contains all shared logic for campaign automation.
 * Campaign-specific logic should be implemented in subclasses by overriding the appropriate methods.
 * By default, URA Finale is handled by this base class.
 */
open class Campaign(val game: Game) {
	protected open val TAG: String = "[${MainActivity.Companion.loggerTag}]Normal"

    private val mustRestBeforeSummer: Boolean = SettingsHelper.getBooleanSetting("training", "mustRestBeforeSummer")
    private val enableFarmingFans: Boolean = SettingsHelper.getBooleanSetting("racing", "enableFarmingFans")

    protected var bHasSetQuickMode: Boolean = false

    /**
     * Detects and handles any dialog popups.
     *
     * @return A pair of a boolean and a nullable DialogInterface.
     * The boolean is true when a dialog has been handled by this function.
     * The DialogInterface is the detected dialog, or NULL if no dialogs were found.
     */
    open fun handleDialogs(): Pair<Boolean, DialogInterface?> {
        game.wait(0.1)
        val dialog: DialogInterface? = DialogUtils.getDialog(imageUtils = game.imageUtils)
        if (dialog == null) {
            return Pair(false, null)
        }

        MessageLog.d(TAG, "[DIALOG] ${dialog.name}")

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
            "consecutive_race_warning" -> {
                dialog.ok(imageUtils = game.imageUtils)
            }
            "epithets" -> dialog.close(imageUtils = game.imageUtils)
            "fans" -> dialog.close(imageUtils = game.imageUtils)
            "featured_cards" -> dialog.close(imageUtils = game.imageUtils)
            "give_up" -> dialog.close(imageUtils = game.imageUtils)
            "goal_not_reached" -> {
                // We are handling the logic for when to race on our own.
                // Thus we just close this warning.
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
                val optionLocations: ArrayList<Point> = IconHorseshoe.findAll(
                    imageUtils = game.imageUtils,
                    region = intArrayOf(160, 770, 70, 460),
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
                bHasSetQuickMode = true
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
                game.wait(5.0)
            }
            "umamusume_class" -> {
                val bitmap: Bitmap = game.imageUtils.getSourceBitmap()
                val templateBitmap: Bitmap? = game.imageUtils.getBitmaps(LabelUmamusumeClassFans.template.path).second
                if (templateBitmap == null) {
                    MessageLog.e(TAG, "[DIALOG] umamusume_class: Could not get template bitmap for LabelUmamusumeClassFans: ${LabelUmamusumeClassFans.template.path}.")
                    dialog.close(imageUtils = game.imageUtils)
                    game.wait(0.1)
                    return Pair(true, dialog)
                }
                val point: Point? = LabelUmamusumeClassFans.find(imageUtils = game.imageUtils).first
                if (point == null) {
                    MessageLog.w(TAG, "[DIALOG] umamusume_class: Could not find LabelUmamusumeClassFans.")
                    dialog.close(imageUtils = game.imageUtils)
                    game.wait(0.1)
                    return Pair(true, dialog)
                }

                // Add a small 8px buffer to vertical component.
                val x = (point.x + (templateBitmap.width / 2)).toInt()
                val y = (point.y - (templateBitmap.height / 2) - 4).toInt()
                val w = 300
                val h = templateBitmap.height + 4

                val croppedBitmap = game.imageUtils.createSafeBitmap(
                    bitmap,
                    x,
                    y,
                    w,
                    h,
                    "dialog::umamusume_class: Cropped bitmap.",
                )
                if (croppedBitmap == null) {
                    MessageLog.e(TAG, "[DIALOG] umamusume_class: Failed to crop bitmap.")
                    dialog.close(imageUtils = game.imageUtils)
                    game.wait(0.1)
                    return Pair(true, dialog)
                }
                val fans = game.imageUtils.getUmamusumeClassDialogFanCount(croppedBitmap)
                if (fans != null) {
                    game.trainee.fans = fans
                    game.bNeedToCheckFans = false
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

        game.wait(0.1)
        return Pair(true, dialog)
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
        game.bNeedToCheckFans = bDidRace
		return bDidRace
	}

	/**
	 * Campaign-specific checks for special screens or conditions.
	 */
	open fun checkCampaignSpecificConditions(): Boolean {
		return false
	}

    /**
     * Clicks the career Quick Mode button and selects the fastest option.
     */
    fun handleCareerQuickMode() {
        if (!bHasSetQuickMode) {
            // Click the Quick Mode button regardless of its state
            // so that we can verify the correct setting.
            if (
                ButtonCareerQuick.click(imageUtils = game.imageUtils) ||
                ButtonCareerQuickEnabled.click(imageUtils = game.imageUtils)
            ) {
                game.wait(0.1)
                handleDialogs()
                game.wait(0.1)
            }
        }
    }

    /**
     * Clicks the career skip button so that it is at its fastest speed (2x).
     */
    fun handleCareerSkipButton() {
        if (!ButtonCareerSkip2.check(imageUtils = game.imageUtils)) {
            // Set the `skip` button to 2x.
            ButtonCareerSkipOff.click(imageUtils = game.imageUtils, taps = 2)
            ButtonCareerSkip1.click(imageUtils = game.imageUtils, taps = 1)
        }
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

        MessageLog.i(TAG, "Testing multi-state buttons...")
        when {
            ButtonCareerSkip1.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${ButtonCareerSkip1.template.path}")
                numPass++
            }
            ButtonCareerSkip2.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${ButtonCareerSkip2.template.path}")
                numPass++
            }
            ButtonCareerSkipOff.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${ButtonCareerSkipOff.template.path}")
                numPass++
            }
            else -> {
                MessageLog.e(TAG, "[FAIL] Could not detect any Career Skip buttons.")
                numFail++
            }
        }
        when {
            ButtonCareerQuick.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${ButtonCareerQuick.template.path}")
                numPass++
            }
            ButtonCareerQuickEnabled.check(game.imageUtils) -> {
                MessageLog.i(TAG, "[PASS] ${ButtonCareerQuickEnabled.template.path}")
                numPass++
            }
            else -> {
                MessageLog.e(TAG, "[FAIL] Could not detect any Career Quick Mode buttons.")
                numFail++
            }
        }

        MessageLog.i(TAG, "---- startMainScreenOCRTest END: PASS=$numPass, FAIL=$numFail ----")
    }

	/**
	 * Main automation loop that handles all shared logic.
	 */
	fun start() {
		while (true) {
            // First thing we should always check is if there are any dialogs.
            try {
                if (handleDialogs().first) {
                    continue
                }
                if (game.handleDialogs().first) {
                    continue
                }
            } catch (e: InterruptedException) {
                MessageLog.e(TAG, "Dialog handler triggered bot to stop: ${e.message}")
                break
            }
			////////////////////////////////////////////////
			// Most bot operations start at the Main screen.
			if (game.checkMainScreen()) {
				// Perform scenario validation check.
				if (!game.validateScenario()) {
					MessageLog.i(TAG, "\n[END] Stopping bot due to scenario validation failure.")
					break
				}

                handleCareerQuickMode()
                handleCareerSkipButton()

				// Check if bot should stop before the finals.
				if (game.checkFinalsStop()) {
					MessageLog.i(TAG, "\n[END] Stopping bot before the finals.")
					break
				}

				var needToRace = false
				if (!game.racing.encounteredRacingPopup) {
                    // Check if there are fan or trophy requirements that need to be met with racing.
					game.racing.checkRacingRequirements()

					// If the required skill points has been reached, stop the bot.
					if (game.enableSkillPointCheck && game.imageUtils.determineSkillPoints() >= game.skillPointsRequired) {
						MessageLog.i(TAG, "\n[END] Bot has acquired the set amount of skill points. Exiting now...")
						game.notificationMessage = "Bot has acquired the set amount of skill points."
						break
					}

					// If force racing is enabled, skip all other activities and go straight to racing
					if (game.racing.enableForceRacing) {
						MessageLog.i(TAG, "Force racing enabled - skipping all other activities and going straight to racing.")
						needToRace = true
					} else {
						// Check if we need to rest before Summer Training (June Early/Late in Classic/Senior Year).
						if (mustRestBeforeSummer &&
                            (   game.currentDate.year == DateYear.CLASSIC ||
                                game.currentDate.year == DateYear.SENIOR
                            ) &&
                            game.currentDate.month == DateMonth.JUNE &&
                            game.currentDate.phase == DatePhase.LATE
                        ) {
							MessageLog.i(TAG, "Forcing rest during ${game.currentDate} in preparation for Summer Training.")
							game.recoverEnergy()
							game.racing.skipRacing = false
						} else if (game.checkInjury() && !game.checkFinals()) {
							game.findAndTapImage("ok", region = game.imageUtils.regionMiddle)
							game.wait(3.0)
							game.racing.skipRacing = false
						} else if (game.recoverMood() && !game.checkFinals()) {
							game.racing.skipRacing = false
						} else if (game.currentDate.day >= 16 && game.racing.checkEligibilityToStartExtraRacingProcess()) {
							MessageLog.i(TAG, "[INFO] Bot has no injuries, mood is sufficient and extra races can be run today. Setting the needToRace flag to true.")
							needToRace = true
                        } else {
                            MessageLog.i(TAG, "[INFO] Training due to it not being an extra race day.")
                            game.training.handleTraining()
                            game.racing.skipRacing = false
                        }
					}
				}

                if (game.racing.encounteredRacingPopup || needToRace) {
                    MessageLog.i(TAG, "[INFO] All checks are cleared for racing.")
                    if (!handleRaceEvents()) {
                        if (game.racing.detectedMandatoryRaceCheck) {
                            MessageLog.i(TAG, "\n[END] Stopping bot due to detection of Mandatory Race.")
                            game.notificationMessage = "Stopping bot due to detection of Mandatory Race."
                            break
                        }
                        game.findAndTapImage("back", tries = 1, region = game.imageUtils.regionBottomHalf)
                        game.racing.skipRacing = !game.racing.enableForceRacing
                        game.wait(1.0)
                        game.training.handleTraining()
                    }
                }
			} else if (game.checkTrainingEventScreen()) {
				// If the bot is at the Training Event screen, that means there are selectable options for rewards.
				handleTrainingEvent()
				game.racing.skipRacing = false
			} else if (game.handleInheritanceEvent()) {
				// If the bot is at the Inheritance screen, then accept the inheritance.
				game.racing.skipRacing = false
			} else if (game.checkMandatoryRacePrepScreen()) {
				// If the bot is at the Main screen with the button to select a race visible, that means the bot needs to handle a mandatory race.
				if (!handleRaceEvents() && game.racing.detectedMandatoryRaceCheck) {
					MessageLog.i(TAG, "\n[END] Stopping bot due to detection of Mandatory Race.")
					game.notificationMessage = "Stopping bot due to detection of Mandatory Race."
					break
				}
			} else if (game.checkRacingScreen()) {
				// If the bot is already at the Racing screen, then complete this standalone race.
				game.racing.handleStandaloneRace()
				game.racing.skipRacing = false
			} else if (game.checkEndScreen()) {
				// Stop when the bot has reached the screen where it details the overall result of the run.
				MessageLog.i(TAG, "\n[END] Bot has reached the end of the run. Exiting now...")
				game.notificationMessage = "Bot has reached the end of the run"
				break
			} else if (checkCampaignSpecificConditions()) {
				MessageLog.i(TAG, "Campaign-specific checks complete.")
				game.racing.skipRacing = false
				continue
			} else {
				MessageLog.i(TAG, "Did not detect the bot being at the following screens: Main, Training Event, Inheritance, Mandatory Race Preparation, Racing and Career End.")
			}

			// Various miscellaneous checks
			if (!game.performMiscChecks()) {
				break
			}
		}
	}
}