/** Defines radio button components. */

package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.Template
import com.steve1316.uma_android_automation.components.Region

object RadioCareerQuickShortenAllEvents : ComponentInterface {
    override val TAG: String = "RadioCareerQuickShortenAllEvents"
   override val template = Template("components/radio/radio_career_quick_shorten_all_events", region = Region.middle)
}

object RadioPortrait : ComponentInterface {
    override val TAG: String = "RadioPortrait"
   override val template = Template("components/radio/radio_portrait", region = Region.middle)
}

object RadioLandscape : ComponentInterface {
    override val TAG: String = "RadioLandscape"
   override val template = Template("components/radio/radio_landscape", region = Region.middle)
}

object RadioVoiceOff : ComponentInterface {
    override val TAG: String = "RadioVoiceOff"
   override val template = Template("components/radio/radio_voice_off", region = Region.middle)
}
