package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.Template

object LabelStatDistance : ComponentInterface {
    override val TAG: String = "LabelStatDistance"
    override val template = Template("components/label/stat_distance")
}

object LabelStatTrackSurface : ComponentInterface {
    override val TAG: String = "LabelStatTrackSurface"
    override val template = Template("components/label/stat_track_surface")
}

object LabelStatStyle : ComponentInterface {
    override val TAG: String = "LabelStatStyle"
    override val template = Template("components/label/stat_style")
}

object LabelUmamusumeClassFans : ComponentInterface {
    override val TAG: String = "LabelUmamusumeClassFans"
    override val template = Template("components/label/umamusume_class_fans")
}

object LabelStatTableHeaderSkillPoints : ComponentInterface {
    override val TAG: String = "LabelStatTableHeaderSkillPoints"
    override val template = Template("components/label/stat_table_header_skill_points")
}

object LabelTrainingFailureChance : ComponentInterface {
    override val TAG: String = "LabelTrainingFailureChance"
    override val template = Template("components/label/training_failure_chance")
}