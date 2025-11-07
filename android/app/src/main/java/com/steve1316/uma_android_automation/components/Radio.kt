package com.steve1316.uma_android_automation.utils.components

import com.steve1316.uma_android_automation.utils.components.ComponentInterface
import com.steve1316.uma_android_automation.utils.components.Template

object RadioCareerQuickShortenAllEvents : ComponentInterface {
    override val TAG: String = "RadioCareerQuickShortenAllEvents"
    override val templates: List<Template> = listOf<Template>(
        Template("components/radio/radio_career_quick_shorten_all_events")
    )
}

object RadioPortrait : ComponentInterface {
    override val TAG: String = "RadioPortrait"
    override val templates: List<Template> = listOf<Template>(
        Template("components/radio/radio_portrait")
    )
}

object RadioLandscape : ComponentInterface {
    override val TAG: String = "RadioLandscape"
    override val templates: List<Template> = listOf<Template>(
        Template("components/radio/radio_landscape")
    )
}

object RadioVoiceOff : ComponentInterface {
    override val TAG: String = "RadioVoiceOff"
    override val templates: List<Template> = listOf<Template>(
        Template("components/radio/dialog_view_story_radio_voice_off")
    )
}