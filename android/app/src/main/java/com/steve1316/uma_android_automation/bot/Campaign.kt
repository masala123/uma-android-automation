package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.utils.SettingsHelper
import com.steve1316.automation_library.utils.MessageLog

/**
 * Base campaign class that contains all shared logic for campaign automation.
 * Campaign-specific logic should be implemented in subclasses by overriding the appropriate methods.
 * By default, URA Finale is handled by this base class.
 */
open class Campaign(val game: Game) {
	protected open val TAG: String = "[${MainActivity.Companion.loggerTag}]Normal"

	val mustRestBeforeSummer: Boolean = SettingsHelper.getBooleanSetting("training", "mustRestBeforeSummer")

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
        game.bNeedToCheckFans = true
		return game.racing.handleRaceEvents()
	}

	/**
	 * Campaign-specific checks for special screens or conditions.
	 */
	open fun checkCampaignSpecificConditions(): Boolean {
		return false
	}

	/**
	 * Main automation loop that handles all shared logic.
	 */
	fun start() {
		while (true) {
			////////////////////////////////////////////////
			// Most bot operations start at the Main screen.
			if (game.checkMainScreen()) {
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
						if (mustRestBeforeSummer && (game.currentDate.year == 2 || game.currentDate.year == 3) && game.currentDate.month == 6 && game.currentDate.phase == "Late") {
							MessageLog.i(TAG, "Forcing rest during June ${game.currentDate.phase} in Year ${game.currentDate.year} in preparation for Summer Training.")
							game.recoverEnergy()
							game.racing.skipRacing = false
						} else if (game.checkInjury() && !game.checkFinals()) {
							game.findAndTapImage("ok", region = game.imageUtils.regionMiddle)
							game.wait(3.0)
							game.racing.skipRacing = false
						} else if (game.recoverMood() && !game.checkFinals()) {
							game.racing.skipRacing = false
						} else if (game.currentDate.turnNumber >= 16 && !game.racing.checkEligibilityToStartExtraRacingProcess()) {
							MessageLog.i(TAG, "[INFO] Training due to it not being an extra race day.")
							game.training.handleTraining()
							game.racing.skipRacing = false
						} else {
							MessageLog.i(TAG, "[INFO] Bot has no injuries, mood is sufficient and extra races can be run today. Setting the needToRace flag to true.")
							needToRace = true
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