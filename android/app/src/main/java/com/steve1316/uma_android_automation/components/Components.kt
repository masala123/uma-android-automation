package com.steve1316.uma_android_automation.components

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.opencv.core.Point

import com.steve1316.uma_android_automation.components.ComponentUtils
import com.steve1316.uma_android_automation.utils.CustomImageUtils

enum class TemplateComparisonMode {AND, OR}
data class Template(val name: String, val region: IntArray = intArrayOf(0, 0, 0, 0))

interface BaseComponentInterface {
    val TAG: String

    val templates: List<Template>

    fun check(imageUtils: CustomImageUtils, tries: Int = 1): Boolean
    fun find(imageUtils: CustomImageUtils, tries: Int = 1): Pair<Point?, Bitmap>
}

interface ComponentInterface: BaseComponentInterface {
    override fun find(imageUtils: CustomImageUtils, tries: Int): Pair<Point?, Bitmap> {
        val template = templates.first()
        return imageUtils.findImage(template.name, region = template.region, tries = tries)
    }

    override fun check(imageUtils: CustomImageUtils, tries: Int): Boolean {
        return find(imageUtils = imageUtils, tries = tries).first != null
    }

    fun click(imageUtils: CustomImageUtils, tries: Int = 1, taps: Int = 1): Boolean {
        val point = find(imageUtils = imageUtils, tries = tries).first
        if (point == null) {
            return false
        }

        ComponentUtils.tap(point.x, point.y, templates.first().name, taps=taps)
        return true
    }
}

// More complex buttons have multiple templates which could match them based
// on the button state. This interface handles this by finding buttons
// where one state is active at a time. Logical OR between templates, essentially.
interface MultiStateButtonInterface : ComponentInterface {
    /** Finds image on screen and returns its location if it exists. */
    override fun find(imageUtils: CustomImageUtils, tries: Int): Pair<Point?, Bitmap> {
        for (template in templates) {
            val (point, bitmap) = imageUtils.findImage(template.name, region = template.region, tries = tries)
            if (point != null) {
                return Pair(point, bitmap)
            }
        }
        return Pair(null, createBitmap(1, 1))
    }

    /** Finds image on screen and returns boolean whether it exists. */
    override fun check(imageUtils: CustomImageUtils, tries: Int): Boolean {
        return find(imageUtils = imageUtils, tries = tries).first != null
    }

    override fun click(imageUtils: CustomImageUtils, tries: Int, taps: Int): Boolean {
        val point = find(imageUtils = imageUtils, tries = tries).first
        if (point == null) {
            return false
        }

        ComponentUtils.tap(point.x, point.y, templates.first().name, taps=taps)
        return true
    }
}
