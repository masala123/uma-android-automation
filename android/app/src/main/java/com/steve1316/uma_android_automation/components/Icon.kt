package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.Template
import com.steve1316.uma_android_automation.components.Region

object IconMoodGreat : ComponentInterface {
    override val TAG: String = "IconMoodGreat"
    override val template = Template("components/icon/mood_great", region = Region.topHalf)
}

object IconMoodGood : ComponentInterface {
    override val TAG: String = "IconMoodGood"
    override val template = Template("components/icon/mood_good", region = Region.topHalf)
}

object IconMoodNormal : ComponentInterface {
    override val TAG: String = "IconMoodNormal"
    override val template = Template("components/icon/mood_normal", region = Region.topHalf)
}

object IconMoodBad : ComponentInterface {
    override val TAG: String = "IconMoodBad"
    override val template = Template("components/icon/mood_bad", region = Region.topHalf)
}

object IconMoodAwful : ComponentInterface {
    override val TAG: String = "IconMoodAwful"
    override val template = Template("components/icon/mood_awful", region = Region.topHalf)
}

object IconTrainingHeaderSpeed : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderSpeed"
    override val template = Template("components/icon/training_header_speed", region = Region.topHalf)
}

object IconTrainingHeaderStamina : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderStamina"
    override val template = Template("components/icon/training_header_stamina", region = Region.topHalf)
}

object IconTrainingHeaderPower : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderPower"
    override val template = Template("components/icon/training_header_power", region = Region.topHalf)
}

object IconTrainingHeaderGuts : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderGuts"
    override val template = Template("components/icon/training_header_guts", region = Region.topHalf)
}

object IconTrainingHeaderWit : ComponentInterface {
    override val TAG: String = "IconTrainingHeaderWit"
    override val template = Template("components/icon/training_header_wit", region = Region.topHalf)
}

object IconHorseshoe : ComponentInterface {
    override val TAG: String = "IconHorseshoe"
    override val template = Template("components/icon/horseshoe")
}

object IconDoubleCircle : ComponentInterface {
    override val TAG: String = "IconDoubleCircle"
    override val template = Template("components/icon/double_circle")
}

object IconUnityCupRaceEndLogo : ComponentInterface {
    override val TAG: String = "IconUnityCupRaceEndLogo"
    override val template = Template("components/icon/unity_cup_race_end_logo", region = Region.topHalf)
}

object IconTazuna : ComponentInterface {
    override val TAG: String = "IconTazuna"
    override val template = Template("components/icon/tazuna", region = Region.topHalf)
}
