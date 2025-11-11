package com.steve1316.uma_android_automation.components

import android.graphics.Bitmap
import org.opencv.core.Point

import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.ImageUtils
//import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.uma_android_automation.components.ComponentInterface

/* Example usage:

import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.DialogInterface

fun handleDialogs() {
    val dialog: DialogInterface? = DialogUtils.getDialog(imageUtils=game.imageUtils)
    if (dialog == null) {
        game.printToLog("\n[DIALOG] No dialog found.", tag = tag)
        return
    }

    when (dialog.name) {
        "open_soon" -> {
            dialog.close(imageUtils=game.imageUtils)
            game.notificationMessage = "open_soon"
            game.printToLog("\n[DIALOG] Open Soon!")
        }
        "continue_career" -> {
            dialog.close(imageUtils=game.imageUtils)
            //ButtonClose.click(imageUtils=game.imageUtils)
            game.printToLog("\n[DIALOG] Continue Career")
        }
        else -> {
            game.printToLog("\n[DIALOG] ${dialog.name}")
            game.notificationMessage = "${dialog.name}"
            dialog.close(imageUtils=game.imageUtils)
        }
    }
}
*/

object DialogUtils {
    private val titleGradientTemplates = listOf<String>(
        "components/dialog/dialog_title_gradient_0",
        "components/dialog/dialog_title_gradient_1",
    )

    /** Checks if any dialog is on screen.
    *
    * @param imageUtils The CustomImageUtils instance used to find the dialog.
    * @param tries The number of times to attempt to find the image.
    *
    * @return Whether a dialog was detected.
    */
    fun check(imageUtils: CustomImageUtils, tries: Int = 1): Boolean {
        var loc: Point? = null
        for (template in titleGradientTemplates) {
            loc = imageUtils.findImage(template, tries = tries).first
            if (loc != null) {
                break
            }
        }
        return loc != null
    }

    /** Gets the title bar text of any dialog current on screen.
    *
    * @param imageUtils The CustomImageUtils instance used to find the dialog.
    * @param titleLocation Optional location of the title bar gradient.
    * @param tries The number of times to attempt to find the image.
    *
    * @return The text of the dialog's title bar if one was found, else NULL.
    */
    fun getTitle(imageUtils: CustomImageUtils, titleLocation: Point? = null, tries: Int = 1): String? {
        // If the title location isn't passed, try to find it.
        val titleLocation: Point? = if (titleLocation == null) {
            var loc: Point? = null
            for (template in titleGradientTemplates) {
                loc = imageUtils.findImage(template, tries = tries).first
                if (loc != null) {
                    break
                }
            }
            loc
        } else {
            titleLocation
        }

        // If titleLocation is still null, then just return.
        if (titleLocation == null) {
            return null
        }

        val sourceBitmap = imageUtils.getSourceBitmap()
        var templateBitmap: Bitmap? = null
        for (template in titleGradientTemplates) {
            templateBitmap = imageUtils.getBitmaps(template).second
            if (templateBitmap != null) {
                break
            }
        }

        // None of our templates could be loaded.
        if (templateBitmap == null) {
            return null
        }

        // Get top left coordinates of the title.
        val x = titleLocation.x - (templateBitmap.width / 2.0)
        val y = titleLocation.y - (templateBitmap.height / 2.0)

        val result: String = imageUtils.performOCROnRegion(
            sourceBitmap,
            imageUtils.relX(x, 0),
            imageUtils.relY(y, 0),
            imageUtils.relWidth((SharedData.displayWidth - (x * 2)).toInt()),
            imageUtils.relHeight(templateBitmap.height),
            useThreshold=true,
            useGrayscale=true,
            scaleUp=1,
            ocrEngine="mlkit",
            debugName="dialogTitle",
        )

        if (result == "") {
            return null
        }

        return result
    }

    /** Detect and return a DialogInterface on screen.
    *
    * @param imageUtils The CustomImageUtils instance used to find the dialog.
    * @param tries The number of times to attempt to find the image.
    *
    * @return The DialogInterface if one was found, else NULL.
    */
    fun getDialog(imageUtils: CustomImageUtils, tries: Int = 1): DialogInterface? {
        var loc: Point? = null
        for (template in titleGradientTemplates) {
            loc = imageUtils.findImage(template, tries = tries).first
            if (loc != null) {
                break
            }
        }
        if (loc == null) {
            return null
        }

        val title: String = getTitle(imageUtils = imageUtils, titleLocation=loc) ?: return null

        //val match = TextUtils.matchStringInList(title, DialogObjects.map.keys.toList())
        return if (DialogObjects.map.keys.toList().contains(title)) {
            DialogObjects.map.get(title)
        } else {
            null
        }
    }
}

interface DialogInterface {
    val TAG: String
    val name: String
    val title: String
    val buttons: List<ComponentInterface>
    // The close button is just which ever button is used primarily to close the dialog
    // If not specified, the first button in Buttons will be used.
    val closeButton: ComponentInterface?
    // The OK button is typically used in a dialog with two primary buttons
    // and it closes the dialog while accepting the dialog.
    // If not specified, no default is selected unlike the closeButton.
    // This is because some dialogs may have a close button and a checkbox,
    // but no OK button.
    // If there is only one button in the dialog, then okButton will be set to that.
    val okButton: ComponentInterface?

    fun close(imageUtils: CustomImageUtils, tries: Int = 1): Boolean {
        if (closeButton == null) {
            return buttons.getOrNull(0)?.click(imageUtils = imageUtils, tries = tries) ?: false
        }
        return closeButton?.click(imageUtils = imageUtils, tries = tries) ?: false
    }

    fun ok(imageUtils: CustomImageUtils, tries: Int = 1): Boolean {
        if (okButton == null) {
            return if (buttons.size == 1) {
                close(imageUtils = imageUtils, tries = tries)
            } else {
                false
            }
        }
        return okButton?.click(imageUtils = imageUtils, tries = tries) ?: false
    }
}

// Simple object used to store a list of all dialog objects.
// This is used to easily iterate over all dialogs.
object DialogObjects {
    val items: List<DialogInterface> = listOf<DialogInterface>(
        DialogAutoSelect,                   // Career Selection
        DialogBorrowCard,                   // Career Selection
        DialogBorrowCardConfirmation,       // Career Selection
        DialogCareer,                       // Career
        DialogCareerComplete,               // Career
        DialogCareerOptions,                // Career
        DialogCompleteCareer,               // Career (yes this is different from above...)
        DialogConcertSkipConfirmation,      // Career
        DialogConfirmAutoSelect,            // Career Selection
        DialogConfirmExchange,              // Main Screen
        DialogConfirmRestoreRP,             // Team Trials
        DialogConnectionError,              // Anywhere
        DialogConsecutiveRaceWarning,       // Career
        DialogContinueCareer,               // Main Screen
        DialogDailySale,                    // Team Trials, Special Events, Daily Races
        DialogDateChanged,                  // Anywhere
        DialogDisplaySettings,              // Anywhere
        DialogEpithet,                      // Career End
        DialogExternalLink,                 // Main Screen
        DialogFinalConfirmation,            // Career Selection
        DialogFollowTrainer,                // Career
        DialogInfirmary,                    // Career
        DialogInsufficientFans,             // Career
        DialogItemsSelected,                // Team Trials, Special Events, Daily Races
        DialogMenu,                         // Career
        DialogNotices,                      // Main Screen
        DialogOpenSoon,                     // Shop (only when clicking inactive daily sales button)
        DialogPresents,                     // Main Screen (i think?)
        DialogPurchaseDailyRaceTicket,      // Daily Races
        DialogQuickModeSettings,            // Career
        DialogRaceDetails,                  // Daily Races, Special Events, and Career
        DialogRacePlayback,                 // Career
        DialogRaceRecommendations,          // Career
        DialogRecreation,                   // Career
        DialogRest,                         // Career
        DialogRestAndRecreation,            // Career
        DialogRewardsCollected,             // Main Screen, Special Events
        DialogSessionError,                 // Anywhere
        DialogSkillListConfirmation,        // Career
        DialogSkillsLearned,                // Career
        DialogSongAcquired,                 // Career
        DialogSpecialMissions,              // Main Screen, Special Events
        DialogStrategy,                     // Race Screen
        DialogStoryUnlocked,                // Main Screen, end of career
        DialogTrophyWon,                    // Career
        DialogTryAgain,                     // Career
        DialogUmamusumeClass,               // Career
        DialogUmamusumeDetails,             // Career
        DialogUnmetRequirements,
        DialogViewStory,                    // Main Screen, end of career
    )

    val map: Map<String, DialogInterface> = items.associateBy { it.title }
}

object DialogAutoSelect : DialogInterface {
    override val TAG: String = "DialogAutoSelect"
    override val name: String = "auto_select"
    override val title: String = "Auto-Select"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
        Checkbox,
    )
}

object DialogBorrowCard : DialogInterface {
    override val TAG: String = "DialogBorrowCard"
    override val name: String = "borrow_card"
    override val title: String = "Borrow Card"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogBorrowCardConfirmation : DialogInterface {
    override val TAG: String = "DialogBorrowCardConfirmation"
    override val name: String = "borrow_card_confirmation"
    override val title: String = "Confirmation"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
        ButtonOk,
    )
}

object DialogCareer : DialogInterface {
    override val TAG: String = "DialogCareer"
    override val name: String = "career"
    override val title: String = "Career"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogCareerComplete : DialogInterface {
    override val TAG: String = "DialogCareerComplete"
    override val name: String = "career_complete"
    override val title: String = "Career Complete"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonEditTeam
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonToHome,
        ButtonEditTeam,
    )
}

object DialogCompleteCareer : DialogInterface {
    override val TAG: String = "DialogCompleteCareer"
    override val name: String = "complete_career"
    override val title: String = "Complete Career"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonFinish
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonFinish,
    )
}

object DialogConcertSkipConfirmation : DialogInterface {
    override val TAG: String = "DialogConcertSkipConfirmation"
    override val name: String = "concert_skip_confirmation"
    override val title: String = "Confirmation"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
        Checkbox,
    )
}

object DialogConfirmAutoSelect : DialogInterface {
    override val TAG: String = "DialogConfirmAutoSelect"
    override val name: String = "confirm_auto_select"
    override val title: String = "Confirm Auto-Select"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
        Checkbox,
    )
}

object DialogConfirmExchange : DialogInterface {
    override val TAG: String = "DialogConfirmExchange"
    override val name: String = "confirm_exchange"
    override val title: String = "Confirm Exchange"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogConnectionError : DialogInterface {
    override val TAG: String = "DialogConnectionError"
    override val name: String = "connection_error"
    override val title: String = "Connection Error"
    override val closeButton = null
    override val okButton = ButtonRetry
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonTitleScreen,
        ButtonRetry,
    )

    // This dialog is unique in that there are two versions of it.
    // The dialog can have either a single button ("Title Screen") or
    // two buttons ("Title Screen" and "Retry").
    override fun ok(imageUtils: CustomImageUtils, tries: Int): Boolean {
        if (ButtonRetry.click(imageUtils = imageUtils, tries = tries)) {
            return true
        }
        
        return ButtonTitleScreen.click(imageUtils = imageUtils, tries = tries)
    }
}

object DialogConsecutiveRaceWarning : DialogInterface {
    override val TAG: String = "DialogConsecutiveRaceWarning"
    override val name: String = "consecutive_race_warning"
    override val title: String = "Warning"
    override val closeButton = null
    override val okButton = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
    )
}

object DialogContinueCareer : DialogInterface {
    override val TAG: String = "DialogContinueCareer"
    override val name: String = "continue_career"
    override val title: String = "Continue Career"
    override val closeButton = null
    override val okButton = ButtonResume
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonResume,
    )
}

object DialogConfirmRestoreRP : DialogInterface {
    override val TAG: String = "DialogConfirmRestoreRP"
    override val name: String = "confirm_restore_rp"
    override val title: String = "Confirm"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonRestore
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonNo,
        ButtonRestore,
    )
}

object DialogDailySale : DialogInterface {
    override val TAG: String = "DialogDailySale"
    override val name: String = "daily_sale"
    override val title: String = "Daily Sale"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonShop
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonShop,
    )
}

object DialogDateChanged : DialogInterface {
    override val TAG: String = "DialogDateChanged"
    override val name: String = "date_changed"
    override val title: String = "Date Changed"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonOk,
    )
}

object DialogDisplaySettings : DialogInterface {
    override val TAG: String = "DialogDisplaySettings"
    override val name: String = "display_settings"
    override val title: String = "Display Settings"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
    )
}

object DialogEpithet : DialogInterface {
    override val TAG: String = "DialogEpithet"
    override val name: String = "epithet"
    override val title: String = "Epithet"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonConfirmExclamation,
        Checkbox,
    )
}

object DialogExternalLink : DialogInterface {
    override val TAG: String = "DialogExternalLink"
    override val name: String = "external_link"
    override val title: String = "External Link"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
    )
}

object DialogFinalConfirmation : DialogInterface {
    override val TAG: String = "DialogFinalConfirmation"
    override val name: String = "final_confirmation"
    override val title: String = "Final Confirmation"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonStartCareer
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonStartCareer,
    )
}

object DialogFollowTrainer : DialogInterface {
    override val TAG: String = "DialogFollowTrainer"
    override val name: String = "follow_trainer"
    override val title: String = "Follow Trainer"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonFollow
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonFollow,
    )
}

object DialogInfirmary : DialogInterface {
    override val TAG: String = "DialogInfirmary"
    override val name: String = "infirmary"
    override val title: String = "Infirmary"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
        Checkbox,
    )
}

object DialogInsufficientFans : DialogInterface {
    override val TAG: String = "DialogInsufficientFans"
    override val name: String = "insufficient_fans"
    override val title: String = "Insufficient Fans"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonRace
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonRace,
    )
}

object DialogItemsSelected : DialogInterface {
    override val TAG: String = "DialogItemsSelected"
    override val name: String = "items_selected"
    override val title: String = "Items Selected"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonRaceExclamationShiftedUp
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonRaceExclamationShiftedUp,
    )
}

object DialogMenu : DialogInterface {
    override val TAG: String = "DialogMenu"
    override val name: String = "menu"
    override val title: String = "Menu"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
        ButtonOptions,
        ButtonSaveAndExit,
        ButtonGiveUp,
    )
}

object DialogNotices : DialogInterface {
    override val TAG: String = "DialogNotices"
    override val name: String = "notices"
    override val title: String = "Notices"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogOpenSoon : DialogInterface {
    override val TAG: String = "DialogOpenSoon"
    override val name: String = "open_soon"
    override val title: String = "Open Soon!"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogCareerOptions : DialogInterface {
    override val TAG: String = "DialogCareerOptions"
    override val name: String = "options"
    override val title: String = "Options"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonSave
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonSave,
    )
}

object DialogPresents : DialogInterface {
    override val TAG: String = "DialogPresents"
    override val name: String = "presents"
    override val title: String = "Presents"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonCollectAll
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
        ButtonCollectAll,
    )
}

object DialogPurchaseDailyRaceTicket : DialogInterface {
    override val TAG: String = "DialogPurchaseDailyRaceTicket"
    override val name: String = "purchase_daily_race_ticket"
    override val title: String = "Purchase Daily Race Ticket"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
    )
}

object DialogQuickModeSettings : DialogInterface {
    override val TAG: String = "DialogQuickModeSettings"
    override val name: String = "quick_mode_settings"
    override val title: String = "Quick Mode Settings"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonConfirm
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonConfirm,
        RadioCareerQuickShortenAllEvents,
    )
}

object DialogRaceDetails : DialogInterface {
    override val TAG: String = "DialogRaceDetails"
    override val name: String = "race_details"
    override val title: String = "Race Details"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonRace,
        ButtonRaceExclamation,
    )

    // This dialog is unique in that there are two versions of it.
    // The normal race details dialog has a "Race!" button whereas
    // the career version just has a "Race" button.
    override fun ok(imageUtils: CustomImageUtils, tries: Int): Boolean {
        if (ButtonRaceExclamation.click(imageUtils = imageUtils, tries = tries)) {
            return true
        }
        
        return ButtonRace.click(imageUtils = imageUtils, tries = tries)
    }
}

object DialogRacePlayback : DialogInterface {
    override val TAG: String = "DialogRacePlayback"
    override val name: String = "race_playback"
    override val title: String = "Race Playback"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
        Checkbox,
        RadioLandscape,
        RadioPortrait,
    )
}

object DialogRaceRecommendations : DialogInterface {
    override val TAG: String = "DialogRaceRecommendations"
    override val name: String = "race_recommendations"
    override val title: String = "Race Recommendations"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonConfirm
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonConfirm,
        ButtonRaceRecommendationsCenterStage,
        ButtonRaceRecommendationsPathToFame,
        ButtonRaceRecommendationsForgeYourOwnPath,
        Checkbox,
    )
}

object DialogRecreation : DialogInterface {
    override val TAG: String = "DialogRecreation"
    override val name: String = "recreation"
    override val title: String = "Recreation"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
        Checkbox,
    )
}

object DialogRest : DialogInterface {
    override val TAG: String = "DialogRest"
    override val name: String = "rest"
    override val title: String = "Rest"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
        Checkbox,
    )
}

object DialogRestAndRecreation : DialogInterface {
    // This one doesn't have a checkbox to not ask again for some reason.
    override val TAG: String = "DialogRestAndRecreation"
    override val name: String = "rest_and_recreation"
    override val title: String = "Rest & Recreation"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
    )
}

object DialogRewardsCollected : DialogInterface {
    override val TAG: String = "DialogRewardsCollected"
    override val name: String = "rewards_collected"
    override val title: String = "Rewards Collected"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogSessionError : DialogInterface {
    override val TAG: String = "DialogSessionError"
    override val name: String = "session_error"
    override val title: String = "Session Error"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonTitleScreen,
    )
}

object DialogSkillListConfirmation : DialogInterface {
    override val TAG: String = "DialogSkillListConfirmation"
    override val name: String = "skill_list_confirmation"
    override val title: String = "Confirmation"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonLearn
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonLearn,
    )
}

object DialogSkillsLearned : DialogInterface {
    override val TAG: String = "DialogSkillsLearned"
    override val name: String = "skills_learned"
    override val title: String = "Skills Learned"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogSongAcquired : DialogInterface {
    override val TAG: String = "DialogSongAcquired"
    override val name: String = "song_acquired"
    override val title: String = "Song Acquired"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogSpecialMissions : DialogInterface {
    override val TAG: String = "DialogSpecialMissions"
    override val name: String = "special_missions"
    override val title: String = "Special Missions"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonCollectAll
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonOk,
        ButtonCollectAll,
    )
}

object DialogStrategy : DialogInterface {
    override val TAG: String = "DialogStrategy"
    override val name: String = "strategy"
    override val title: String = "Strategy"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonConfirm
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonConfirm,
        ButtonRaceStrategyFront,
        ButtonRaceStrategyPace,
        ButtonRaceStrategyLate,
        ButtonRaceStrategyEnd,
    )
}

object DialogStoryUnlocked : DialogInterface {
    override val TAG: String = "DialogStoryUnlocked"
    override val name: String = "story_unlocked"
    override val title: String = "Story Unlocked"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonToHome,
    )
}

object DialogTrophyWon : DialogInterface {
    override val TAG: String = "DialogTrophyWon"
    override val name: String = "trophy_won"
    override val title: String = "TROPHY WON!"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogTryAgain : DialogInterface {
    override val TAG: String = "DialogTryAgain"
    override val name: String = "try_again"
    override val title: String = "Try Again"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonTryAgain
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonEndCareer,
        ButtonTryAgain,
    )
}

object DialogUmamusumeClass : DialogInterface {
    override val TAG: String = "DialogUmamusumeClass"
    override val name: String = "umamusume_class"
    override val title: String = "Umamusume Class"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogUmamusumeDetails : DialogInterface {
    override val TAG: String = "DialogUmamusumeDetails"
    override val name: String = "umamusume_details"
    override val title: String = "Umamusume Details"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonClose,
    )
}

object DialogUnmetRequirements : DialogInterface {
    override val TAG: String = "DialogUnmetRequirements"
    override val name: String = "unmet_requirements"
    override val title: String = "Unmet Requirements"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonRace
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonRace,
    )
}

object DialogViewStory : DialogInterface {
    override val TAG: String = "DialogViewStory"
    override val name: String = "view_story"
    override val title: String = "View Story"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf<ComponentInterface>(
        ButtonCancel,
        ButtonOk,
        RadioLandscape,
        RadioPortrait,
        RadioVoiceOff,
    )
}
