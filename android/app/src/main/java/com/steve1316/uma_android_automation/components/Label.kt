package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.Template
import com.steve1316.uma_android_automation.components.Region

object LabelStatDistance : ComponentInterface {
    override val TAG: String = "LabelStatDistance"
    override val template = Template("components/label/stat_distance", region = Region.topHalf)
}

object LabelStatTrackSurface : ComponentInterface {
    override val TAG: String = "LabelStatTrackSurface"
    override val template = Template("components/label/stat_track_surface", region = Region.topHalf)
}

object LabelStatStyle : ComponentInterface {
    override val TAG: String = "LabelStatStyle"
    override val template = Template("components/label/stat_style", region = Region.topHalf)
}

object LabelUmamusumeClassFans : ComponentInterface {
    override val TAG: String = "LabelUmamusumeClassFans"
    override val template = Template("components/label/umamusume_class_fans", region = Region.middle)
}

object LabelStatTableHeaderSkillPoints : ComponentInterface {
    override val TAG: String = "LabelStatTableHeaderSkillPoints"
    override val template = Template("components/label/stat_table_header_skill_points", region = Region.bottomHalf)
}

object LabelTrainingFailureChance : ComponentInterface {
    override val TAG: String = "LabelTrainingFailureChance"
    override val template = Template("components/label/training_failure_chance", region = Region.bottomHalf)
}

object LabelWinToBecomeRank : ComponentInterface {
    override val TAG: String = "LabelWinToBecomeRank"
    override val template = Template("components/label/win_to_become_rank")
}

object LabelUnityCupOpponentSelectionLaurel : ComponentInterface {
    override val TAG: String = "LabelUnityCupOpponentSelectionLaurel"
    override val template = Template("components/label/unitycup_opponent_selection_laurel", region = Region.leftHalf)
}

object LabelEnergy : ComponentInterface {
    override val TAG: String = "LabelEnergy"
    override val template = Template("components/label/energy")
}

object LabelEnergyBarLeftPart : ComponentInterface {
    override val TAG: String = "LabelEnergyBarLeftPart"
    override val template = Template("components/label/energy_bar_left_part")
}

object LabelEnergyBarRightPart : ComponentInterface {
    override val TAG: String = "LabelEnergyBarRightPart"
    override val template = Template("components/label/energy_bar_right_part_0")
}

object LabelEnergyBarExtendedRightPart : ComponentInterface {
    override val TAG: String = "LabelEnergyBarExtendedRightPart"
    override val template = Template("components/label/energy_bar_right_part_1")
}
