package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.Template

object RadioCareerQuickShortenAllEvents : ComponentInterface {
    override val TAG: String = "RadioCareerQuickShortenAllEvents"
   override val template = Template("components/radio/radio_career_quick_shorten_all_events")
}

object RadioPortrait : ComponentInterface {
    override val TAG: String = "RadioPortrait"
   override val template = Template("components/radio/radio_portrait")
}

object RadioLandscape : ComponentInterface {
    override val TAG: String = "RadioLandscape"
   override val template = Template("components/radio/radio_landscape")
}

object RadioVoiceOff : ComponentInterface {
    override val TAG: String = "RadioVoiceOff"
   override val template = Template("components/radio/radio_voice_off")
}