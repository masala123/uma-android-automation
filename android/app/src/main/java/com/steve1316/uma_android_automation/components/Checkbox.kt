package com.steve1316.uma_android_automation.components

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.Template

object Checkbox : ComponentInterface {
    override val TAG: String = "Checkbox"
    override val template = Template("components/checkbox/checkbox")
}

object CheckboxDoNotShowAgain : ComponentInterface {
    override val TAG: String = "CheckboxDoNotShowAgain"
    override val template = Template("components/checkbox/checkbox_do_not_show_again")
}
