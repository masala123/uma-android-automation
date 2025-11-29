package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.Template

object IconMoodGreat : ComponentInterface {
    override val TAG: String = "MoodGreat"
    override val templates: List<Template> = listOf<Template>(
        Template("components/icon/mood_great")
    )
}

object IconMoodGood : ComponentInterface {
    override val TAG: String = "MoodGood"
    override val templates: List<Template> = listOf<Template>(
        Template("components/icon/mood_good")
    )
}

object IconMoodNormal : ComponentInterface {
    override val TAG: String = "MoodNormal"
    override val templates: List<Template> = listOf<Template>(
        Template("components/icon/mood_normal")
    )
}

object IconMoodBad : ComponentInterface {
    override val TAG: String = "MoodBad"
    override val templates: List<Template> = listOf<Template>(
        Template("components/icon/mood_bad")
    )
}

object IconMoodAwful : ComponentInterface {
    override val TAG: String = "MoodAwful"
    override val templates: List<Template> = listOf<Template>(
        Template("components/icon/mood_awful")
    )
}

object IconTrainingHeaderSpeed : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderSpeed"
    override val templates: List<Template> = listOf<Template>(
        Template("components/icon/training_header_speed")
    )
}

object IconTrainingHeaderStamina : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderStamina"
    override val templates: List<Template> = listOf<Template>(
        Template("components/icon/training_header_stamina")
    )
}

object IconTrainingHeaderPower : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderPower"
    override val templates: List<Template> = listOf<Template>(
        Template("components/icon/training_header_power")
    )
}

object IconTrainingHeaderGuts : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderGuts"
    override val templates: List<Template> = listOf<Template>(
        Template("components/icon/training_header_guts")
    )
}

object IconTrainingHeaderWit : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderWit"
    override val templates: List<Template> = listOf<Template>(
        Template("components/icon/training_header_wit")
    )
}
