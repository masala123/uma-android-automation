package com.steve1316.uma_android_automation.components

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.opencv.core.Point

import com.steve1316.uma_android_automation.components.ComponentUtils
import com.steve1316.uma_android_automation.utils.CustomImageUtils

enum class TemplateComparisonMode {AND, OR}
data class Template(val path: String, val region: IntArray = intArrayOf(0, 0, 0, 0)) {
    val dirname: String
        get() = path.substringBeforeLast('/')

    val basename: String
        get() = path.substringAfterLast('/')

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Template

        if (path != other.path) return false
        if (!region.contentEquals(other.region)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + region.contentHashCode()
        return result
    }
}

interface BaseComponentInterface {
    val TAG: String

    fun check(imageUtils: CustomImageUtils, tries: Int = 1): Boolean
    fun find(imageUtils: CustomImageUtils, tries: Int = 1): Pair<Point?, Bitmap>
    fun click(imageUtils: CustomImageUtils, tries: Int = 1, taps: Int = 1): Boolean
}

interface ComponentInterface: BaseComponentInterface {
    val template: Template
    
    override fun find(imageUtils: CustomImageUtils, tries: Int): Pair<Point?, Bitmap> {
        return imageUtils.findImage(template.path, region = template.region, tries = tries)
    }

    override fun check(imageUtils: CustomImageUtils, tries: Int): Boolean {
        return find(imageUtils = imageUtils, tries = tries).first != null
    }

    override fun click(imageUtils: CustomImageUtils, tries: Int, taps: Int): Boolean {
        val point = find(imageUtils = imageUtils, tries = tries).first
        if (point == null) {
            return false
        }

        ComponentUtils.tap(point.x, point.y, template.path, taps=taps)
        return true
    }
}

interface ComplexComponentInterface: BaseComponentInterface {
    val templates: List<Template>

    override fun find(imageUtils: CustomImageUtils, tries: Int): Pair<Point?, Bitmap> {
        for (template in templates) {
            val result: Pair<Point?, Bitmap> = imageUtils.findImage(template.path, region = template.region, tries = tries)
            if (result.first != null) {
                return result
            }
        }
        return Pair(null, imageUtils.getSourceBitmap())
    }

    override fun check(imageUtils: CustomImageUtils, tries: Int): Boolean {
        return find(imageUtils = imageUtils, tries = tries).first != null
    }

    override fun click(imageUtils: CustomImageUtils, tries: Int, taps: Int): Boolean {
        var resultTemplate: Template? = null
        var resultPoint: Point? = null
        for (template in templates) {
            resultPoint = imageUtils.findImage(template.path, region = template.region, tries = tries).first
            if (resultPoint != null) {
                resultTemplate = template
                break
            }
        }

        // Failed to find any of the templates on the screen.
        if (resultPoint == null || resultTemplate == null) {
            return false
        }

        ComponentUtils.tap(resultPoint.x, resultPoint.y, resultTemplate.path, taps = taps)
        return true
    }
}

// More complex buttons have multiple templates which could match them based
// on the button state. This interface handles this by finding buttons
// where one state is active at a time. Logical OR between templates, essentially.
interface MultiStateButtonInterface : ComplexComponentInterface {
    /** Finds image on screen and returns its location if it exists. */
    override fun find(imageUtils: CustomImageUtils, tries: Int): Pair<Point?, Bitmap> {
        for (template in templates) {
            val (point, bitmap) = imageUtils.findImage(template.path, region = template.region, tries = tries)
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

        ComponentUtils.tap(point.x, point.y, templates.first().path, taps=taps)
        return true
    }
}
