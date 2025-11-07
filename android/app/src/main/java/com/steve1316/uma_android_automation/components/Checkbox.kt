package com.steve1316.uma_android_automation.utils.components

import com.steve1316.uma_android_automation.utils.components.ComponentInterface
import com.steve1316.uma_android_automation.utils.components.Template

object Checkbox : ComponentInterface {
    override val TAG: String = "Checkbox"
    override val templates: List<Template> = listOf<Template>(
        Template("components/checkbox/checkbox")
    )
}

object CheckboxDoNotShowAgain : ComponentInterface {
    override val TAG: String = "CheckboxDoNotShowAgain"
    override val templates: List<Template> = listOf<Template>(
        Template("components/checkbox/checkbox_do_not_show_again")
    )
}
