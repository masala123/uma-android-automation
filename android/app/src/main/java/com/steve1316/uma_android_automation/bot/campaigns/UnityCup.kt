package com.steve1316.uma_android_automation.bot.campaigns

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Campaign
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.components.ButtonRace
import org.opencv.core.Point

class UnityCup(game: Game) : Campaign(game) {
    override val TAG: String = "[${MainActivity.loggerTag}]UnityCup"
	private var tutorialDisabled = false
	private var aoHaruRaceFirstTime: Boolean = true

	override fun handleTrainingEvent() {
        MessageLog.i(TAG, "\n[UNITY_CUP] Running handleTrainingEvent() for Unity Cup.")
        if (!tutorialDisabled) {
            tutorialDisabled = if (game.imageUtils.findImage("unitycup_tutorial_header", tries = 1, region = game.imageUtils.regionTopHalf).first != null) {
                // If the tutorial is detected, select the second option to close it.
                MessageLog.i(TAG, "\n[UNITY_CUP] Detected tutorial for Unity Cup. Closing it now...")
                val trainingOptionLocations: ArrayList<Point> = game.imageUtils.findAll("training_event_active")
                game.gestureUtils.tap(trainingOptionLocations[1].x, trainingOptionLocations[1].y, "training_event_active")
                true
            } else {
                MessageLog.i(TAG, "\n[UNITY_CUP] Tutorial must have already been dismissed.")
                super.handleTrainingEvent()
                true
            }
        } else {
            super.handleTrainingEvent()
        }
	}

	override fun handleRaceEvents(): Boolean {
        MessageLog.i(TAG, "\n[UNITY_CUP] Running handleRaceEvents() for Unity Cup.")
		if (game.imageUtils.findImage("unitycup_race", tries = 1, region = game.imageUtils.regionBottomHalf).first != null) {
            // Otherwise, handle the Unity Cup race.
			handleRaceEventsUnityCup()
			return true
		} else if (
            game.imageUtils.findImage("unitycup_race_skip_results", tries = 1, region = game.imageUtils.regionBottomHalf).first != null &&
            game.imageUtils.findImage("race_skip_locked", tries = 1, region = game.imageUtils.regionBottomHalf).first == null
        ) {
            game.findAndTapImage("unitycup_race_skip_results", region = game.imageUtils.regionBottomHalf)
            return true
        } else if (
            game.imageUtils.findImage("unitycup_race_manual", tries = 1, region = game.imageUtils.regionBottomHalf).first != null &&
            game.imageUtils.findImage("race_skip_locked", tries = 1, region = game.imageUtils.regionBottomHalf).first != null
        ) {
            game.findAndTapImage("unitycup_race_manual", region = game.imageUtils.regionBottomHalf)
            return true
        } else if (ButtonRace.click(imageUtils = game.imageUtils)) {
            return true
        }

		// Fall back to the regular race handling logic.
		return super.handleRaceEvents()
	}

	override fun checkCampaignSpecificConditions(): Boolean {
        // Check for the Unity Cup popup to set the initial team.
        if (aoHaruRaceFirstTime && game.imageUtils.findImage("unitycup_set_initial_team_header", tries = 1).first != null) {
            MessageLog.i(TAG, "\n[UNITY_CUP] Dismissed the popup to set the initial team.")
			game.findAndTapImage("close")
			handleRaceEventsUnityCup()
			return true
        } else if (
            game.imageUtils.findImage("unitycup_race_skip_results", tries = 1, region = game.imageUtils.regionBottomHalf).first != null &&
            game.imageUtils.findImage("race_skip_locked", tries = 1, region = game.imageUtils.regionBottomHalf).first == null
        ) {
            game.findAndTapImage("unitycup_race_skip_results", region = game.imageUtils.regionBottomHalf)
            return true
        } else if (
            game.imageUtils.findImage("unitycup_race_manual", tries = 1, region = game.imageUtils.regionBottomHalf).first != null &&
            game.imageUtils.findImage("race_skip_locked", tries = 1, region = game.imageUtils.regionBottomHalf).first != null
        ) {
            game.findAndTapImage("unitycup_race_manual", region = game.imageUtils.regionBottomHalf)
            return true
        } else if (ButtonRace.click(imageUtils = game.imageUtils)) {
            return true
        }
		return false
	}
	
	/**
	 * Handles the Unity Cup race event.
	 */
	private fun handleRaceEventsUnityCup() {
		MessageLog.i(TAG, "\n[UNITY_CUP] Starting process for handling the Unity Cup racing process.")
		aoHaruRaceFirstTime = false
        var unityCupFinals = false
		
		// Head to the next screen with the 3 opponents.
		game.findAndTapImage("unitycup_race")
		game.wait(3.0)
		
		if (game.findAndTapImage("unitycup_final_race")) {
			MessageLog.i(TAG, "\n[UNITY_CUP] Final race detected with Team Zenith.")
			game.findAndTapImage("unitycup_race_begin_showdown")
            unityCupFinals = true
		} else {
			// Analyze which opponent has more than 2 double predictions and select the first one it finds going from top to bottom.
            // First get the locations of all 3 opponents.
			val opponents = game.imageUtils.findAll("unitycup_opponent_option")
            var opponentSelected = false
            for (i in opponents.indices) {
                val opponent = opponents[i]
                game.gestureUtils.tap(opponent.x, opponent.y, "unitycup_opponent_option")
                game.findAndTapImage("unitycup_select_opponent")
                game.wait(1.0)

                val doubleCircles = game.imageUtils.findAll("race_prediction_double_circle", region = game.imageUtils.regionMiddle)
                if (doubleCircles.size >= 3) {
                    MessageLog.i(TAG, "[UNITY_CUP] Race #${i + 1} has sufficient double circle predictions. Selecting it now...")
                    game.findAndTapImage("unitycup_race_begin_showdown")
                    opponentSelected = true
                    break
                } else {
                    MessageLog.i(TAG, "[UNITY_CUP] Race #${i + 1} only had ${doubleCircles.size} double predictions and falls short. Skipping this opponent.")
                    game.findAndTapImage("cancel")
                    game.wait(1.0)
                }
            }

            // If a opponent has not been selected yet, default to the 2nd opponent.
            if (!opponentSelected) {
                MessageLog.w(TAG, "[UNITY_CUP] Could not determine any opponent with sufficient double circle predictions. Selecting the 2nd opponent as a fallback.")
                game.gestureUtils.tap(opponents[1].x, opponents[1].y, "unitycup_opponent_option")
                game.findAndTapImage("unitycup_select_opponent")
                game.wait(1.0)
                game.findAndTapImage("unitycup_race_begin_showdown")
            }
		}
		
		game.wait(6.0)
		
		// Now skip the overall race results or manually run the final race with Team Zenith if the skip is not available.
        if (unityCupFinals) {
            if (game.imageUtils.findImage("race_skip_locked", tries = 1, region = game.imageUtils.regionBottomHalf).first != null) {
                // Manually run the race.
                game.racing.runRaceWithRetries()
                // After the race is manually ran, skip past the popup showing the final positions of the racers.
                game.findAndTapImage("next", tries = 20, region = game.imageUtils.regionBottomHalf)
            } else {
                game.findAndTapImage("unitycup_race_skip_results", tries = 20, region = game.imageUtils.regionBottomHalf)
                game.wait(2.0)
                game.findAndTapImage("race_skip_manual", tries = 20, region = game.imageUtils.regionBottomHalf)
            }
        } else {
            game.findAndTapImage("unitycup_race_skip_results", tries = 20, region = game.imageUtils.regionBottomHalf)
            game.wait(2.0)
            game.findAndTapImage("race_skip_manual", tries = 20, region = game.imageUtils.regionBottomHalf)
        }

        // On the Race Results screen, skip past the final results of all 5 races against this opponent.
		game.racing.finalizeRaceResults(true)
	}
}