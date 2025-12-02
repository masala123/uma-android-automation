package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.Template

object IconMoodGreat : ComponentInterface {
    override val TAG: String = "IconMoodGreat"
    override val template = Template("components/icon/mood_great")
}

object IconMoodGood : ComponentInterface {
    override val TAG: String = "IconMoodGood"
    override val template = Template("components/icon/mood_good")
}

object IconMoodNormal : ComponentInterface {
    override val TAG: String = "IconMoodNormal"
    override val template = Template("components/icon/mood_normal")
}

object IconMoodBad : ComponentInterface {
    override val TAG: String = "IconMoodBad"
    override val template = Template("components/icon/mood_bad")
}

object IconMoodAwful : ComponentInterface {
    override val TAG: String = "IconMoodAwful"
    override val template = Template("components/icon/mood_awful")
}

object IconTrainingHeaderSpeed : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderSpeed"
    override val template = Template("components/icon/training_header_speed")
}

object IconTrainingHeaderStamina : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderStamina"
    override val template = Template("components/icon/training_header_stamina")
}

object IconTrainingHeaderPower : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderPower"
    override val template = Template("components/icon/training_header_power")
}

object IconTrainingHeaderGuts : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderGuts"
    override val template = Template("components/icon/training_header_guts")
}

object IconTrainingHeaderWit : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderWit"
    override val template = Template("components/icon/training_header_wit")
}

object IconHorseshoe : ComponentInterface {
    override val TAG: String = "IconHorseshoe"
    override val template = Template("components/icon/horseshoe")
}
