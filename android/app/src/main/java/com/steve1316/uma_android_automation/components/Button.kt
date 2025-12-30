package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.Template
import com.steve1316.uma_android_automation.components.Region

object ButtonAgenda : ComponentInterface {
    override val TAG: String = "ButtonAgenda"
    override val template = Template("components/button/agenda")
}

object ButtonAutoSelect : ComponentInterface {
    override val TAG: String = "ButtonAutoSelect"
    override val template = Template("components/button/auto_select")
}

object ButtonBack : ComponentInterface {
    override val TAG: String = "ButtonBack"
    override val template = Template("components/button/back", region = Region.bottomHalf)
}

object ButtonBackGreen : ComponentInterface {
    override val TAG: String = "ButtonBackGreen"
    override val template = Template("components/button/back_green", region = Region.bottomHalf)
}

object ButtonBeginShowdown : ComponentInterface {
    override val TAG: String = "ButtonBeginShowdown"
    override val template = Template("components/button/begin_showdown")
}

object ButtonBorrowSupportCard : ComponentInterface {
    override val TAG: String = "ButtonBorrowSupportCard"
    override val template = Template("components/button/borrow_support_card")
}

object ButtonBurger : ComponentInterface {
    override val TAG: String = "ButtonBurger"
    override val template = Template("components/button/burger", region = Region.bottomHalf)
}

object ButtonCancel : ComponentInterface {
    override val TAG: String = "ButtonCancel"
    override val template = Template("components/button/cancel", region = Region.bottomHalf)
}

object ButtonChangeRunningStyle : ComponentInterface {
    override val TAG: String = "ButtonChangeRunningStyle"
    override val template = Template("components/button/change")
}

object ButtonClose : ComponentInterface {
    override val TAG: String = "ButtonClose"
    override val template = Template("components/button/close", region = Region.bottomHalf)
}

object ButtonCollectAll : ComponentInterface {
    override val TAG: String = "ButtonCollectAll"
    override val template = Template("components/button/collect_all", region = Region.bottomHalf)
}


object ButtonConfirm : ComponentInterface {
    override val TAG: String = "ButtonConfirm"
    override val template = Template("components/button/confirm", region = Region.bottomHalf)
}

object ButtonConfirmExclamation : ComponentInterface {
    override val TAG: String = "ButtonConfirmExclamation"
    override val template = Template("components/button/confirm_exclamation", region = Region.bottomHalf)
}


object ButtonDailyRaces : ComponentInterface {
    override val TAG: String = "ButtonDailyRaces"
    override val template = Template("components/button/daily_races")
}

object ButtonDailyRacesDisabled : ComponentInterface {
    override val TAG: String = "ButtonDailyRacesDisabled"
    override val template = Template("components/button/daily_races_disabled")
}

object ButtonDailyRacesJupiterCup : ComponentInterface {
    override val TAG: String = "ButtonDailyRacesJupiterCup"
    override val template = Template("components/button/daily_races_jupiter_cup_logo")
}

object ButtonDailyRacesMoonlightSho : ComponentInterface {
    override val TAG: String = "ButtonDailyRacesMoonlightSho"
    override val template = Template("components/button/daily_races_moonlight_sho_logo")
}

object ButtonEditTeam : ComponentInterface {
    override val TAG: String = "ButtonEditTeam"
    override val template = Template("components/button/edit_team")
}

object ButtonFollow : ComponentInterface {
    override val TAG: String = "ButtonFollow"
    override val template = Template("components/button/follow")
}

object ButtonFinish : ComponentInterface {
    override val TAG: String = "ButtonFinish"
    override val template = Template("components/button/finish")
}

object ButtonGiveUp : ComponentInterface {
    override val TAG: String = "ButtonGiveUp"
    override val template = Template("components/button/give_up")
}

object ButtonToHome : ComponentInterface {
    override val TAG: String = "ButtonToHome"
    override val template = Template("components/button/to_home")
}

object ButtonHomeSpecialMissions : ComponentInterface {
    override val TAG: String = "ButtonHomeSpecialMissions"
    override val template = Template("components/button/home_special_missions")
}

object ButtonHomePresents : ComponentInterface {
    override val TAG: String = "ButtonHomePresents"
    override val template = Template("components/button/home_presents")
}

object ButtonSpecialMissionsTabDaily : ComponentInterface {
    override val TAG: String = "ButtonSpecialMissionsTabDaily"
    override val template = Template("components/button/special_missions_tab_daily")
}

object ButtonSpecialMissionsTabMain : ComponentInterface {
    override val TAG: String = "ButtonSpecialMissionsTabMain"
    override val template = Template("components/button/special_missions_tab_main")
}

object ButtonSpecialMissionsTabTitles : ComponentInterface {
    override val TAG: String = "ButtonSpecialMissionsTabTitles"
    override val template = Template("components/button/special_missions_tab_titles")
}

object ButtonSpecialMissionsTabSpecial : ComponentInterface {
    override val TAG: String = "ButtonSpecialMissionsTabSpecial"
    override val template = Template("components/button/special_missions_tab_special")
}

object ButtonLegendRace : ComponentInterface {
    override val TAG: String = "ButtonLegendRace"
    override val template = Template("components/button/legend_race")
}

object ButtonLegendRaceDisabled : ComponentInterface {
    override val TAG: String = "ButtonLegendRaceDisabled"
    override val template = Template("components/button/legend_race_disabled")
}

object ButtonRaceHardInactive : ComponentInterface {
    override val TAG: String = "ButtonRaceHardInactive"
    override val template = Template("components/button/race_hard_inactive")
}

object ButtonRaceHardActive : ComponentInterface {
    override val TAG: String = "ButtonRaceHardActive"
    override val template = Template("components/button/race_hard_active")
}

object ButtonLegendRaceHomeSpecialMissions : ComponentInterface {
    override val TAG: String = "ButtonLegendRaceHomeSpecialMissions"
    override val template = Template("components/button/legend_race_special_missions")
}

object ButtonLog : ComponentInterface {
    override val TAG: String = "ButtonLog"
    override val template = Template("components/button/log", region = Region.bottomHalf)
}

object ButtonNext : ComponentInterface {
    override val TAG: String = "ButtonNext"
    override val template = Template("components/button/next", region = Region.bottomHalf)
}

object ButtonNextWithImage : ComponentInterface {
    override val TAG: String = "ButtonNextWithImage"
    override val template = Template("components/button/next_with_image", region = Region.bottomHalf)
}

object ButtonNextRaceEnd : ComponentInterface {
    override val TAG: String = "ButtonNextRaceEnd"
    override val template = Template("components/button/next_race_end", region = Region.bottomHalf)
}

object ButtonNo : ComponentInterface {
    override val TAG: String = "ButtonNo"
    override val template = Template("components/button/no", region = Region.bottomHalf)
}

object ButtonOk : ComponentInterface {
    override val TAG: String = "ButtonOk"
    override val template = Template("components/button/ok", region = Region.bottomHalf)
}

object ButtonOptions : ComponentInterface {
    override val TAG: String = "ButtonOptions"
    override val template = Template("components/button/options", region = Region.bottomHalf)
}

object ButtonLearn : ComponentInterface {
    override val TAG: String = "ButtonLearn"
    override val template = Template("components/button/learn")
}

object ButtonReset : ComponentInterface {
    override val TAG: String = "ButtonReset"
    override val template = Template("components/button/reset", region = Region.bottomHalf)
}

object ButtonRace : ComponentInterface {
    override val TAG: String = "ButtonRace"
    override val template = Template("components/button/race", region = Region.bottomHalf)
}

object ButtonRaceAgain : ComponentInterface {
    override val TAG: String = "ButtonRaceAgain"
    override val template = Template("components/button/race_again", region = Region.bottomHalf)
}

object ButtonRaceDetails : ComponentInterface {
    override val TAG: String = "ButtonRaceDetails"
    override val template = Template("components/button/race_details", region = Region.bottomHalf)
}

object ButtonRaceEvents : ComponentInterface {
    override val TAG: String = "ButtonRaceEvents"
    override val template = Template("components/button/race_events")
}

object ButtonRaceExclamation : ComponentInterface {
    override val TAG: String = "ButtonRaceExclamation"
    override val template = Template("components/button/race_exclamation", region = Region.bottomHalf)
}

object ButtonRaceExclamationShiftedUp : ComponentInterface {
    override val TAG: String = "ButtonRaceExclamationShiftedUp"
    override val template = Template("components/button/race_exclamation_shifted_up", region = Region.middle)
}

object ButtonRaceManual : ComponentInterface {
    override val TAG: String = "ButtonRaceManual"
    override val template = Template("components/button/race_manual", region = Region.bottomHalf)
}

object ButtonRaceRecommendationsCenterStage : ComponentInterface {
    override val TAG: String = "ButtonRaceRecommendationsCenterStage"
    override val template = Template("components/button/race_recommendations_center_stage")
}

object ButtonRaceRecommendationsPathToFame : ComponentInterface {
    override val TAG: String = "ButtonRaceRecommendationsPathToFame"
    override val template = Template("components/button/race_recommendations_path_to_fame")
}

object ButtonRaceRecommendationsForgeYourOwnPath : ComponentInterface {
    override val TAG: String = "ButtonRaceRecommendationsForgeYourOwnPath"
    override val template = Template("components/button/race_recommendations_forge_your_own_path")
}

object ButtonRaceResults : ComponentInterface {
    override val TAG: String = "ButtonRaceResults"
    override val template = Template("components/button/race_results")
}

object ButtonRestore : ComponentInterface {
    override val TAG: String = "ButtonRestore"
    override val template = Template("components/button/restore")
}

object ButtonRetry : ComponentInterface {
    override val TAG: String = "ButtonRetry"
    override val template = Template("components/button/retry")
}

object ButtonResume : ComponentInterface {
    override val TAG: String = "ButtonResume"
    override val template = Template("components/button/resume")
}

object ButtonSave : ComponentInterface {
    override val TAG: String = "ButtonSave"
    override val template = Template("components/button/save", region = Region.bottomHalf)
}

object ButtonSaveSchedule : ComponentInterface {
    override val TAG: String = "ButtonSaveSchedule"
    override val template = Template("components/button/save_schedule", region = Region.bottomHalf)
}

object ButtonSaveAndExit : ComponentInterface {
    override val TAG: String = "ButtonSaveAndExit"
    override val template = Template("components/button/save_and_exit", region = Region.bottomHalf)
}

object ButtonSeeResults : ComponentInterface {
    override val TAG: String = "ButtonSeeResults"
    override val template = Template("components/button/see_results", region = Region.bottomHalf)
}

object ButtonSelectOpponent : ComponentInterface {
    override val TAG: String = "ButtonSelectOpponent"
    override val template = Template("components/button/select_opponent", region = Region.bottomHalf)
}

object ButtonSelectLegacy : ComponentInterface {
    override val TAG: String = "ButtonSelectLegacy"
    override val template = Template("components/button/select_legacy")
}

object ButtonShop : ComponentInterface {
    override val TAG: String = "ButtonShop"
    override val template = Template("components/button/shop")
}

object ButtonSkip : ComponentInterface {
    override val TAG: String = "ButtonSkip"
    override val template = Template("components/button/skip", region = Region.bottomHalf)
}

object ButtonSkills : ComponentInterface {
    override val TAG: String = "ButtonSkills"
    override val template = Template("components/button/skills", region = Region.bottomHalf)
}

object ButtonStartCareer : ComponentInterface {
    override val TAG: String = "ButtonStartCareer"
    override val template = Template("components/button/start_career", region = Region.bottomHalf)
}

object ButtonStartCareerOffset : ComponentInterface {
    override val TAG: String = "ButtonStartCareerOffset"
    override val template = Template("components/button/start_career_offset", region = Region.bottomHalf)
}

object ButtonTeamRace : ComponentInterface {
    override val TAG: String = "ButtonTeamRace"
    override val template = Template("components/button/team_race")
}

object ButtonTeamTrials : ComponentInterface {
    override val TAG: String = "ButtonTeamTrials"
    override val template = Template("components/button/team_trials")
}

object ButtonTitleScreen : ComponentInterface {
    override val TAG: String = "ButtonTitleScreen"
    override val template = Template("components/button/title_screen")
}

object ButtonTryAgain : ComponentInterface {
    override val TAG: String = "ButtonTryAgain"
    override val template = Template("components/button/try_again", region = Region.bottomHalf)
}

object ButtonViewResults : ComponentInterface {
    override val TAG: String = "ButtonViewResults"
    override val template = Template("components/button/view_results", region = Region.bottomHalf)
}

object ButtonWatchConcert : ComponentInterface {
    override val TAG: String = "ButtonWatchConcert"
    override val template = Template("components/button/watch_concert", region = Region.bottomHalf)
}

object ButtonRaceStrategyFront : ComponentInterface {
    override val TAG: String = "ButtonRaceStrategyFront"
    override val template = Template("components/button/strategy_front_select", region = Region.middle)
}

object ButtonRaceStrategyPace : ComponentInterface {
    override val TAG: String = "ButtonRaceStrategyPace"
    override val template = Template("components/button/strategy_pace_select", region = Region.middle)
}

object ButtonRaceStrategyLate : ComponentInterface {
    override val TAG: String = "ButtonRaceStrategyLate"
    override val template = Template("components/button/strategy_late_select", region = Region.middle)
}

object ButtonRaceStrategyEnd : ComponentInterface {
    override val TAG: String = "ButtonRaceStrategyEnd"
    override val template = Template("components/button/strategy_end_select", region = Region.middle)
}

// More complex buttons

object ButtonMenuBarHomeSelected : ComponentInterface {
    override val TAG: String = "ButtonMenuBarHomeSelected"
    override val template = Template("components/button/menu_bar_home_selected")
}

object ButtonMenuBarHomeUnselected : ComponentInterface {
    override val TAG: String = "ButtonMenuBarHomeUnselected"
    override val template = Template("components/button/menu_bar_home_unselected")
}

object ButtonMenuBarHome : MultiStateButtonInterface {
    override val TAG: String = "ButtonMenuBarHome"
    override val templates: List<Template> = listOf(
        Template("components/button/menu_bar_home_unselected"),
        Template("components/button/menu_bar_home_selected"),
    )
}

object ButtonMenuBarRaceSelected : ComponentInterface {
    override val TAG: String = "ButtonMenuBarRaceSelected"
    override val template = Template("components/button/menu_bar_race_selected")
}

object ButtonMenuBarRaceUnselected : ComponentInterface {
    override val TAG: String = "ButtonMenuBarRaceUnselected"
    override val template = Template("components/button/menu_bar_race_unselected")
}

object ButtonMenuBarRace : MultiStateButtonInterface {
    override val TAG: String = "ButtonMenuBarRace"
    override val templates: List<Template> = listOf(
        Template("components/button/menu_bar_race_unselected"),
        Template("components/button/menu_bar_race_selected"),
    )
}

object ButtonCompleteCareer : ComponentInterface {
    override val TAG: String = "ButtonCompleteCareer"
    override val template = Template("components/button/complete_career", region = Region.bottomHalf)
}

object ButtonCareerEndSkills : ComponentInterface {
    override val TAG: String = "ButtonCareerEndSkills"
    override val template = Template("components/button/career_end_skills")
}

object ButtonCraneGame : ComponentInterface {
    override val TAG: String = "ButtonCraneGame"
    override val template = Template("components/button/crane_game", region = Region.bottomHalf)
}

object ButtonCraneGameOk : ComponentInterface {
    override val TAG: String = "ButtonCraneGameOk"
    override val template = Template("components/button/crane_game_ok", region = Region.bottomHalf)
}

object ButtonInheritance : ComponentInterface {
    override val TAG: String = "ButtonInheritance"
    override val template = Template("components/button/inheritance", region = Region.bottomHalf)
}

object ButtonPredictions : ComponentInterface {
    override val TAG: String = "ButtonPredictions"
    override val template = Template("components/button/predictions", region = Region.bottomHalf)
}

object ButtonRunners : ComponentInterface {
    override val TAG: String = "ButtonRunners"
    override val template = Template("components/button/runners", region = Region.middle)
}

object ButtonRaceSelectExtra : ComponentInterface {
    override val TAG: String = "ButtonRaceSelectExtra"
    override val template = Template("components/button/race_select_extra", region = Region.bottomHalf)
}

object ButtonRaceSelectExtraLocked : ComponentInterface {
    override val TAG: String = "ButtonRaceSelectExtraLocked"
    override val template = Template("components/button/race_select_extra_locked", region = Region.bottomHalf)
}

object ButtonRaceSelectExtraLockedUraFinals : ComponentInterface {
    override val TAG: String = "ButtonRaceSelectExtraLockedUraFinals"
    override val template = Template("components/button/race_select_extra_locked_ura_finals", region = Region.bottomHalf)
}

object ButtonUnityCupRace : ComponentInterface {
    override val TAG: String = "ButtonUnityCupRace"
    override val template = Template("components/button/unitycup_race", region = Region.bottomHalf)
}

object ButtonUnityCupRaceFinal : ComponentInterface {
    override val TAG: String = "ButtonUnityCupRaceFinal"
    override val template = Template("components/button/unitycup_race_final", region = Region.bottomHalf)
}

object ButtonUnityCupSeeAllRaceResults : ComponentInterface {
    override val TAG: String = "ButtonUnityCupSeeAllRaceResults"
    override val template = Template("components/button/unitycup_see_all_race_results", region = Region.bottomHalf)
}

object ButtonUnityCupTeam : ComponentInterface {
    override val TAG: String = "ButtonUnityCupTeam"
    override val template = Template("components/button/unitycup_team", region = Region.bottomHalf)
}

object ButtonUnityCupWatchMainRace : ComponentInterface {
    override val TAG: String = "ButtonUnityCupWatchMainRace"
    override val template = Template("components/button/unitycup_watch_main_race", region = Region.bottomHalf)
}

object ButtonRest : ComponentInterface {
    override val TAG: String = "ButtonRest"
    override val template = Template("components/button/rest", region = Region.bottomHalf)
}

object ButtonRestAndRecreation : ComponentInterface {
    override val TAG: String = "ButtonRestAndRecreation"
    override val template = Template("components/button/rest_and_recreation", region = Region.bottomHalf)
}

object ButtonInfirmary : ComponentInterface {
    override val TAG: String = "ButtonInfirmary"
    override val template = Template("components/button/infirmary", region = Region.bottomHalf)
}

object ButtonRecreation : ComponentInterface {
    override val TAG: String = "ButtonRecreation"
    override val template = Template("components/button/recreation", region = Region.bottomHalf)
}

object ButtonViewResultsLocked : ComponentInterface {
    override val TAG: String = "ButtonViewResultsLocked"
    override val template = Template("components/button/view_results_locked", region = Region.bottomHalf, confidence = 0.9)
}

object ButtonEndCareer : ComponentInterface {
    override val TAG: String = "ButtonEndCareer"
    override val template = Template("components/button/end_career", region = Region.bottomHalf)
}

object ButtonRaceListFullStats : ComponentInterface {
    override val TAG: String = "ButtonRaceListFullStats"
    override val template = Template("components/button/race_list_full_stats", region = Region.middle)
}

object ButtonSkillListFullStats : ComponentInterface {
    override val TAG: String = "ButtonSkillListFullStats"
    override val template = Template("components/button/skill_list_full_stats", region = Region.topHalf)
}

object ButtonHomeFullStats : ComponentInterface {
    override val TAG: String = "ButtonHomeFullStats"
    override val template = Template("components/button/home_full_stats", region = Region.middle)
}

object ButtonTrainingSpeed : ComponentInterface {
    override val TAG: String = "ButtonTrainingSpeed"
    override val template = Template("components/button/training_speed", region = Region.bottomHalf)
}

object ButtonTrainingStamina : ComponentInterface {
    override val TAG: String = "ButtonTrainingStamina"
    override val template = Template("components/button/training_stamina", region = Region.bottomHalf)
}

object ButtonTrainingPower : ComponentInterface {
    override val TAG: String = "ButtonTrainingPower"
    override val template = Template("components/button/training_power", region = Region.bottomHalf)
}

object ButtonTrainingGuts : ComponentInterface {
    override val TAG: String = "ButtonTrainingGuts"
    override val template = Template("components/button/training_guts", region = Region.bottomHalf)
}

object ButtonTrainingWit : ComponentInterface {
    override val TAG: String = "ButtonTrainingWit"
    override val template = Template("components/button/training_wit", region = Region.bottomHalf)
}

object ButtonTraining : ComponentInterface {
    override val TAG: String = "ButtonTraining"
    override val template = Template("components/button/training", region = Region.bottomHalf)
}

object ButtonRaces : ComponentInterface {
    override val TAG: String = "ButtonRaces"
    override val template = Template("components/button/races", region = Region.bottomHalf)
}

object ButtonHomeFansInfo : ComponentInterface {
    override val TAG: String = "ButtonHomeFansInfo"
    override val template = Template("components/button/home_fans_info", region = Region.leftHalf)
}

object ButtonSkillUp : ComponentInterface {
    override val TAG: String = "ButtonSkillUp"
    override val template = Template("components/button/skill_up", region = Region.rightHalf)
}

object ButtonSkillDown : ComponentInterface {
    override val TAG: String = "ButtonSkillDown"
    override val template = Template("components/button/skill_down", region = Region.rightHalf)
}
