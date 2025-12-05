package com.steve1316.uma_android_automation.components

import android.graphics.Bitmap
import org.opencv.core.Point

import com.steve1316.automation_library.data.SharedData
import com.steve1316.automation_library.utils.ImageUtils
import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.uma_android_automation.utils.CustomImageUtils
import com.steve1316.uma_android_automation.components.ComponentInterface

/* Example usage:

import com.steve1316.uma_android_automation.components.DialogUtils
import com.steve1316.uma_android_automation.components.DialogInterface

fun handleDialogs() {
    val dialog: DialogInterface? = DialogUtils.getDialog(imageUtils=game.imageUtils)
    if (dialog == null) {
        MessageLog.i(TAG, "\n[DIALOG] No dialog found.")
        return
    }

    when (dialog.name) {
        "open_soon" -> {
            dialog.close(imageUtils=game.imageUtils)
            game.notificationMessage = "open_soon"
            MessageLog.i(TAG, "\n[DIALOG] Open Soon!")
        }
        "continue_career" -> {
            dialog.close(imageUtils=game.imageUtils)
            //ButtonClose.click(imageUtils=game.imageUtils)
            MessageLog.i(TAG, "\n[DIALOG] Continue Career")
        }
        else -> {
            MessageLog.i(TAG, "\n[DIALOG] ${dialog.name}")
            game.notificationMessage = "${dialog.name}"
            dialog.close(imageUtils=game.imageUtils)
        }
    }
}
*/

object DialogUtils {
    private val titleGradientTemplates: List<String> = listOf(
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
            loc = imageUtils.findImage(template, tries = tries, suppressError = true).first
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
    fun getTitle(imageUtils: CustomImageUtils, tries: Int = 1): String? {
        var templateBitmap: Bitmap? = null
        var titleLocation: Point? = null
        for (template in titleGradientTemplates) {
            titleLocation = imageUtils.findImage(template, tries = tries, suppressError = true).first
            if (titleLocation != null) {
                templateBitmap = imageUtils.getTemplateBitmap(template.substringAfterLast('/'), "images/" + template.substringBeforeLast('/'))
                break
            }
        }

        // If titleLocation is null, then just return.
        if (titleLocation == null) {
            return null
        }

        // If we failed to find the template bitmap, we can't do any calcs.
        if (templateBitmap == null) {
            return null
        }

        val sourceBitmap = imageUtils.getSourceBitmap()

        // Get top left coordinates of the title.
        val x = titleLocation.x - (templateBitmap.width / 2.0)
        val y = titleLocation.y - (templateBitmap.height / 2.0)

        val _x = imageUtils.relX(x, 0)
        val _y = imageUtils.relY(y, 0)
        val _w = imageUtils.relWidth((SharedData.displayWidth - (x * 2)).toInt())
        val _h = imageUtils.relHeight(templateBitmap.height)

        val result: String = imageUtils.performOCROnRegion(
            sourceBitmap,
            imageUtils.relX(x, 0),
            imageUtils.relY(y, 0),
            imageUtils.relWidth((SharedData.displayWidth - (x * 2)).toInt()),
            imageUtils.relHeight(templateBitmap.height),
            useThreshold=true,
            useGrayscale=true,
            scale=1.0,
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
        val title: String = getTitle(imageUtils = imageUtils) ?: return null

        val match: String? = TextUtils.matchStringInList(title, DialogObjects.map.keys.toList())
        return if (match != null) {
            DialogObjects.map[match]
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
    val items: List<DialogInterface> = listOf(
        DialogAgendaDetails,                // Career
        DialogAutoFill,                     // Career (Unity Cup)
        DialogAutoSelect,                   // Career Selection
        DialogAllRewardsEarned,             // Career (event only)
        DialogBonusUmamusumeDetails,        // Career -> Career Profile dialog
        DialogBorrowCard,                   // Career Selection
        DialogBorrowCardConfirmation,       // Career Selection
        DialogCareer,                       // Career
        DialogCareerComplete,               // Career
        DialogCareerEventDetails,           // Card details
        DialogCareerProfile,                // Career
        DialogChoices,                      // Career (training event effects)
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
        DialogEpithets,                     // Career DialogMenu -> Epithets button
        DialogExternalLink,                 // Main Screen
        DialogFans,                         // Career DialogGoals
        DialogFeaturedCards,                // Career
        DialogFinalConfirmation,            // Career Selection
        DialogFollowTrainer,                // Career
        DialogGiveUp,                       // Career
        DialogGoalNotReached,               // Career
        DialogGoals,                        // Career
        DialogHelpAndGlossary,              // Anywhere (from options dialog)
        DialogInfirmary,                    // Career
        DialogInsufficientFans,             // Career
        DialogItemsSelected,                // Team Trials, Special Events, Daily Races
        DialogLog,                          // Career
        DialogMenu,                         // Career
        DialogMoodEffect,                   // Career
        DialogMyAgendas,                    // Career
        DialogNotices,                      // Main Screen
        DialogOpenSoon,                     // Shop (only when clicking inactive daily sales button)
        DialogOptions,                      // Anywhere
        DialogPerks,                        // Career -> Career Profile dialog
        DialogPlacing,                      // Career -> DialogTryAgain
        DialogPresents,                     // Main Screen (i think?)
        DialogPurchaseAlarmClock,           // Career
        DialogPurchaseDailyRaceTicket,      // Daily Races
        DialogQuickModeSettings,            // Career
        DialogRaceDetails,                  // Daily Races, Special Events, and Career
        DialogRacePlayback,                 // Career
        DialogRaceRecommendations,          // Career
        DialogRecreation,                   // Career
        DialogRequestFulfilled,             // Transfer Requests
        DialogRest,                         // Career
        DialogRestAndRecreation,            // Career
        DialogRewardsCollected,             // Main Screen, Special Events
        DialogRunners,                      // Career -> Race screens
        DialogScheduledRaces,               // Career
        DialogScheduleSettings,             // Career
        DialogSessionError,                 // Anywhere
        DialogSkillDetails,                 // Anywhere
        DialogSkillListConfirmation,        // Career
        DialogSkillsLearned,                // Career
        DialogSongAcquired,                 // Career
        DialogSparkDetails,                 // Career (legacy uma details)
        DialogSparks,                       // Career -> Career Profile dialog
        DialogSpecialMissions,              // Main Screen, Special Events
        DialogStrategy,                     // Race Screen
        DialogStoryUnlocked,                // Main Screen, end of career
        DialogTeamInfo,                     // Career (Unity Cup)
        DialogTrophyWon,                    // Career
        DialogTryAgain,                     // Career
        DialogUmamusumeClass,               // Career
        DialogUmamusumeDetails,             // Career
        DialogUnityCupAvailable,            // Career (Unity Cup)
        DialogUnityCupConfirmation,         // Career (Unity Cup)
        DialogUnlockRequirements,           // Race Screen
        DialogUnmetRequirements,
        DialogViewStory,                    // Main Screen, end of career
    )

    val map: Map<String, DialogInterface> = items.associateBy { it.title }
}

// =========================
//      DIALOG OBJECTS
// =========================

object DialogAgendaDetails : DialogInterface {
    override val TAG: String = "DialogAgendaDetails"
    override val name: String = "agenda_details"
    override val title: String = "Agenda Details"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogAutoFill : DialogInterface {
    override val TAG: String = "DialogAutoFill"
    override val name: String = "auto_fill"
    override val title: String = "Auto-Fill"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonEditTeam
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
        ButtonEditTeam,
    )
}

object DialogAutoSelect : DialogInterface {
    override val TAG: String = "DialogAutoSelect"
    override val name: String = "auto_select"
    override val title: String = "Auto-Select"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonOk,
        Checkbox,
    )
}

object DialogAllRewardsEarned : DialogInterface {
    override val TAG: String = "DialogAllRewardsEarned"
    override val name: String = "all_rewards_earned"
    override val title: String = "ALL REWARDS EARNED!"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogBonusUmamusumeDetails : DialogInterface {
    override val TAG: String = "DialogBonusUmamusumeDetails"
    override val name: String = "bonus_umamusume_details"
    override val title: String = "Bonus Umamusume Details"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogBorrowCard : DialogInterface {
    override val TAG: String = "DialogBorrowCard"
    override val name: String = "borrow_card"
    override val title: String = "Borrow Card"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogBorrowCardConfirmation : DialogInterface {
    override val TAG: String = "DialogBorrowCardConfirmation"
    override val name: String = "borrow_card_confirmation"
    override val title: String = "Confirmation"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogCareerComplete : DialogInterface {
    override val TAG: String = "DialogCareerComplete"
    override val name: String = "career_complete"
    override val title: String = "Career Complete"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonEditTeam
    override val buttons: List<ComponentInterface> = listOf(
        ButtonToHome,
        ButtonClose,
        ButtonEditTeam,
    )

    // This dialog is unique in that there are two versions of it.
    // The dialog's close button can be one of two different buttons:
    // "To Home" and "Close"
    override fun close(imageUtils: CustomImageUtils, tries: Int): Boolean {
        if (ButtonToHome.click(imageUtils = imageUtils, tries = tries)) {
            return true
        }
        
        return ButtonClose.click(imageUtils = imageUtils, tries = tries)
    }
}

object DialogChoices : DialogInterface {
    override val TAG: String = "DialogChoices"
    override val name: String = "choices"
    override val title: String = "Choices"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogCompleteCareer : DialogInterface {
    override val TAG: String = "DialogCompleteCareer"
    override val name: String = "complete_career"
    override val title: String = "Complete Career"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonFinish
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogConnectionError : DialogInterface {
    override val TAG: String = "DialogConnectionError"
    override val name: String = "connection_error"
    override val title: String = "Connection Error"
    override val closeButton = null
    override val okButton = ButtonRetry
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonOk,
    )
}

object DialogDisplaySettings : DialogInterface {
    override val TAG: String = "DialogDisplaySettings"
    override val name: String = "display_settings"
    override val title: String = "Display Settings"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonConfirmExclamation,
        Checkbox,
    )
}

// This is the dialog opened from the Epithets button in DialogMenu.
object DialogEpithets : DialogInterface {
    override val TAG: String = "DialogEpithets"
    override val name: String = "epithets"
    override val title: String = "Epithets"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogExternalLink : DialogInterface {
    override val TAG: String = "DialogExternalLink"
    override val name: String = "external_link"
    override val title: String = "External Link"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonOk,
    )
}

object DialogFans : DialogInterface {
    override val TAG: String = "DialogFans"
    override val name: String = "fans"
    override val title: String = "Fans"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogFeaturedCards : DialogInterface {
    override val TAG: String = "DialogFeaturedCards"
    override val name: String = "featured_cards"
    override val title: String = "Featured Cards"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogFinalConfirmation : DialogInterface {
    override val TAG: String = "DialogFinalConfirmation"
    override val name: String = "final_confirmation"
    override val title: String = "Final Confirmation"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonStartCareer
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonFollow,
    )
}

object DialogGiveUp : DialogInterface {
    override val TAG: String = "DialogGiveUp"
    override val name: String = "give_up"
    override val title: String = "Give Up"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonGiveUp
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonGiveUp,
    )
}

object DialogGoalNotReached : DialogInterface {
    override val TAG: String = "DialogGoalNotReached"
    override val name: String = "goal_not_reached"
    override val title: String = "Goal Not Reached"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonRace
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonRace,
    )
}

object DialogGoals : DialogInterface {
    override val TAG: String = "DialogGoals"
    override val name: String = "goals"
    override val title: String = "Goals"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogHelpAndGlossary : DialogInterface {
    override val TAG: String = "DialogHelpAndGlossary"
    override val name: String = "help_and_glossary"
    override val title: String = "Help & Glossary"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogInfirmary : DialogInterface {
    override val TAG: String = "DialogInfirmary"
    override val name: String = "infirmary"
    override val title: String = "Infirmary"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonRaceExclamationShiftedUp,
    )
}

object DialogLog : DialogInterface {
    override val TAG: String = "DialogLog"
    override val name: String = "log"
    override val title: String = "Log"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogMenu : DialogInterface {
    override val TAG: String = "DialogMenu"
    override val name: String = "menu"
    override val title: String = "Menu"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
        ButtonOptions,
        ButtonSaveAndExit,
        ButtonGiveUp,
    )
}

object DialogMoodEffect : DialogInterface {
    override val TAG: String = "DialogMoodEffect"
    override val name: String = "mood_effect"
    override val title: String = "Mood Effect"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogMyAgendas : DialogInterface {
    override val TAG: String = "DialogMyAgendas"
    override val name: String = "my_agendas"
    override val title: String = "My Agendas"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogNotices : DialogInterface {
    override val TAG: String = "DialogNotices"
    override val name: String = "notices"
    override val title: String = "Notices"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogOpenSoon : DialogInterface {
    override val TAG: String = "DialogOpenSoon"
    override val name: String = "open_soon"
    override val title: String = "Open Soon!"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogCareerEventDetails : DialogInterface {
    override val TAG: String = "DialogCareerEventDetails"
    override val name: String = "career_event_details"
    override val title: String = "Career Event Details"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogCareerProfile : DialogInterface {
    override val TAG: String = "DialogCareerProfile"
    override val name: String = "career_profile"
    override val title: String = "Career Profile"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogOptions : DialogInterface {
    override val TAG: String = "DialogOptions"
    override val name: String = "options"
    override val title: String = "Options"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonSave
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonSave,
    )
}

object DialogPerks : DialogInterface {
    override val TAG: String = "DialogPerks"
    override val name: String = "perks"
    override val title: String = "Perks"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogPlacing : DialogInterface {
    override val TAG: String = "DialogPlacing"
    override val name: String = "placing"
    override val title: String = "Placing"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogPresents : DialogInterface {
    override val TAG: String = "DialogPresents"
    override val name: String = "presents"
    override val title: String = "Presents"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonCollectAll
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
        ButtonCollectAll,
    )
}

object DialogPurchaseAlarmClock : DialogInterface {
    override val TAG: String = "DialogPurchaseAlarmClock"
    override val name: String = "purchase_alarm_clock"
    override val title: String = "Purchase Alarm Clock"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonOk,
    )
}

object DialogPurchaseDailyRaceTicket : DialogInterface {
    override val TAG: String = "DialogPurchaseDailyRaceTicket"
    override val name: String = "purchase_daily_race_ticket"
    override val title: String = "Purchase Daily Race Ticket"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonRace,
        ButtonRaceExclamation,
    )

    // This dialog is unique in that there are three variants of it.
    // The normal race details dialog has a "Race!" button whereas
    // the career version just has a "Race" button. There is also
    // an informational version that only has a "Close" button.
    override fun ok(imageUtils: CustomImageUtils, tries: Int): Boolean {
        if (ButtonRaceExclamation.click(imageUtils = imageUtils, tries = tries)) {
            return true
        }
        
        if (ButtonRace.click(imageUtils = imageUtils, tries = tries)) {
            return true
        }

        return ButtonClose.click(imageUtils = imageUtils, tries = tries)
    }
}

object DialogRacePlayback : DialogInterface {
    override val TAG: String = "DialogRacePlayback"
    override val name: String = "race_playback"
    override val title: String = "Race Playback"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonOk,
        Checkbox,
    )
}

object DialogRequestFulfilled : DialogInterface {
    override val TAG: String = "DialogRequestFulfilled"
    override val name: String = "request_fulfilled"
    override val title: String = "REQUEST FULFILLED"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogRest : DialogInterface {
    override val TAG: String = "DialogRest"
    override val name: String = "rest"
    override val title: String = "Rest"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonOk
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogRunners : DialogInterface {
    override val TAG: String = "DialogRunners"
    override val name: String = "runners"
    override val title: String = "Runners"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogScheduledRaces : DialogInterface {
    override val TAG: String = "DialogScheduledRaces"
    override val name: String = "scheduled_races"
    override val title: String = "Scheduled Races"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogScheduleSettings : DialogInterface {
    override val TAG: String = "DialogScheduleSettings"
    override val name: String = "schedule_settings"
    override val title: String = "Schedule Settings"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonSaveSchedule
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonSaveSchedule,
    )
}

object DialogSessionError : DialogInterface {
    override val TAG: String = "DialogSessionError"
    override val name: String = "session_error"
    override val title: String = "Session Error"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonTitleScreen,
    )
}

object DialogSkillDetails : DialogInterface {
    override val TAG: String = "DialogSkillDetails"
    override val name: String = "skill_details"
    override val title: String = "Skill Details"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogSkillListConfirmation : DialogInterface {
    override val TAG: String = "DialogSkillListConfirmation"
    override val name: String = "skill_list_confirmation"
    override val title: String = "Confirmation"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonLearn
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogSongAcquired : DialogInterface {
    override val TAG: String = "DialogSongAcquired"
    override val name: String = "song_acquired"
    override val title: String = "Song Acquired"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogSparkDetails : DialogInterface {
    override val TAG: String = "DialogSparkDetails"
    override val name: String = "spark_details"
    override val title: String = "Spark Details"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogSparks : DialogInterface {
    override val TAG: String = "DialogSparks"
    override val name: String = "sparks"
    override val title: String = "Sparks"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogSpecialMissions : DialogInterface {
    override val TAG: String = "DialogSpecialMissions"
    override val name: String = "special_missions"
    override val title: String = "Special Missions"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonCollectAll
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonToHome,
    )
}

object DialogTeamInfo : DialogInterface {
    override val TAG: String = "DialogTeamInfo"
    override val name: String = "team_info"
    override val title: String = "Team Info"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonEditTeam
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
        ButtonEditTeam,
    )
}

object DialogTrophyWon : DialogInterface {
    override val TAG: String = "DialogTrophyWon"
    override val name: String = "trophy_won"
    override val title: String = "TROPHY WON!"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogTryAgain : DialogInterface {
    override val TAG: String = "DialogTryAgain"
    override val name: String = "try_again"
    override val title: String = "Try Again"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonTryAgain
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonTryAgain,
    )
}

object DialogUmamusumeClass : DialogInterface {
    override val TAG: String = "DialogUmamusumeClass"
    override val name: String = "umamusume_class"
    override val title: String = "Umamusume Class"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogUmamusumeDetails : DialogInterface {
    override val TAG: String = "DialogUmamusumeDetails"
    override val name: String = "umamusume_details"
    override val title: String = "Umamusume Details"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogUnityCupAvailable : DialogInterface {
    override val TAG: String = "DialogUnityCupAvailable"
    override val name: String = "unity_cup_available"
    override val title: String = "Unity Cup Available"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogUnityCupConfirmation : DialogInterface {
    override val TAG: String = "DialogUnityCupConfirmation"
    override val name: String = "unity_cup_confirmation"
    override val title: String = "Confirmation"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonBeginShowdown
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonBeginShowdown,
    )
}

object DialogUnlockRequirements : DialogInterface {
    override val TAG: String = "DialogUnlockRequirements"
    override val name: String = "unlock_requirements"
    override val title: String = "Unlock Requirements"
    override val closeButton = null
    override val okButton = null
    override val buttons: List<ComponentInterface> = listOf(
        ButtonClose,
    )
}

object DialogUnmetRequirements : DialogInterface {
    override val TAG: String = "DialogUnmetRequirements"
    override val name: String = "unmet_requirements"
    override val title: String = "Unmet Requirements"
    override val closeButton = null
    override val okButton: ComponentInterface = ButtonRace
    override val buttons: List<ComponentInterface> = listOf(
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
    override val buttons: List<ComponentInterface> = listOf(
        ButtonCancel,
        ButtonOk,
        RadioLandscape,
        RadioPortrait,
        RadioVoiceOff,
    )
}
