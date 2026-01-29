/** Defines label components.
 *
 * These are non-clickable regions of text on screen.
 */

package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.Template
import com.steve1316.uma_android_automation.components.Region

object LabelStatDistance : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelStatDistance"
    override val template = Template("components/label/stat_distance", region = Region.topHalf)
}

object LabelStatTrackSurface : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelStatTrackSurface"
    override val template = Template("components/label/stat_track_surface", region = Region.topHalf)
}

object LabelStatStyle : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelStatStyle"
    override val template = Template("components/label/stat_style", region = Region.topHalf)
}

object LabelUmamusumeClassFans : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelUmamusumeClassFans"
    override val template = Template("components/label/umamusume_class_fans", region = Region.middle)
}

object LabelStatTableHeaderSkillPoints : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelStatTableHeaderSkillPoints"
    override val template = Template("components/label/stat_table_header_skill_points", region = Region.bottomHalf)
}

object LabelTrainingFailureChance : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelTrainingFailureChance"
    override val template = Template("components/label/training_failure_chance", region = Region.bottomHalf)
}

object LabelWinToBecomeRank : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelWinToBecomeRank"
    override val template = Template("components/label/win_to_become_rank")
}

object LabelUnityCupOpponentSelectionLaurel : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelUnityCupOpponentSelectionLaurel"
    override val template = Template("components/label/unitycup_opponent_selection_laurel", region = Region.leftHalf)
}

object LabelEnergy : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelEnergy"
    override val template = Template("components/label/energy")
}

object LabelEnergyBarLeftPart : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelEnergyBarLeftPart"
    override val template = Template("components/label/energy_bar_left_part")
}

object LabelEnergyBarRightPart : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelEnergyBarRightPart"
    override val template = Template("components/label/energy_bar_right_part_0")
}

object LabelEnergyBarExtendedRightPart : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelEnergyBarExtendedRightPart"
    override val template = Template("components/label/energy_bar_right_part_1")
}

object LabelSkillListScreenSkillPoints : ComponentInterface {
    override val TAG: String = "LabelSkillListScreenSkillPoints"
    override val template = Template("components/label/skill_list_screen_skill_points", region = Region.topHalf)
}

object LabelScheduledRace : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelScheduledRace"
    override val template = Template("components/label/scheduled_race", region = Region.bottomHalf)
}


object LabelTrainingCannotPerform : ComponentInterface {
    override val TAG: String = "[${MainActivity.loggerTag}]LabelTrainingCannotPerform"
    override val template = Template("components/label/training_cannot_perform", region = Region.middle)
}
