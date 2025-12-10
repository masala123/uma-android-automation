package com.steve1316.uma_android_automation.components

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.opencv.core.Point

import com.steve1316.uma_android_automation.components.ComponentUtils
import com.steve1316.uma_android_automation.utils.CustomImageUtils

import com.steve1316.automation_library.data.SharedData

object Region {
    val topHalf: IntArray = intArrayOf(0, 0, SharedData.displayWidth, SharedData.displayHeight / 2)
    val topQuarter: IntArray = intArrayOf(0, 0, SharedData.displayWidth, SharedData.displayHeight / 4)
    val bottomHalf: IntArray = intArrayOf(0, SharedData.displayHeight / 2, SharedData.displayWidth, SharedData.displayHeight / 2)
    val bottomQuarter: IntArray = intArrayOf(0, SharedData.displayHeight / 4, SharedData.displayWidth, SharedData.displayHeight / 4)
    val middle: IntArray = intArrayOf(0, SharedData.displayHeight / 4, SharedData.displayWidth, SharedData.displayHeight / 2)
    val leftHalf: IntArray = intArrayOf(0, 0, SharedData.displayWidth / 2, SharedData.displayHeight)
    val rightHalf: IntArray = intArrayOf(SharedData.displayWidth / 2, 0, SharedData.displayWidth / 2, SharedData.displayHeight)
}

enum class TemplateComparisonMode {AND, OR}
data class Template(val path: String, val region: IntArray = intArrayOf(0, 0, 0, 0), val confidence: Double = 0.0) {
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
        if (confidence != other.confidence) return false
        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + region.contentHashCode()
        result = 31 * result + confidence.hashCode()
        return result
    }

    fun getBitmap(imageUtils: CustomImageUtils): Bitmap? {
        return imageUtils.getTemplateBitmap(path.substringAfterLast('/'), "images/" + path.substringBeforeLast('/'))
    }
}

interface BaseComponentInterface {
    val TAG: String

    fun check(imageUtils: CustomImageUtils, region: IntArray? = null, tries: Int = 1, confidence: Double? = null): Boolean
    fun find(imageUtils: CustomImageUtils, region: IntArray? = null, tries: Int = 1, confidence: Double? = null): Pair<Point?, Bitmap>
    fun findImageWithBitmap(imageUtils: CustomImageUtils, sourceBitmap: Bitmap, region: IntArray = intArrayOf(0, 0, 0, 0), confidence: Double? = null): Point?
    fun click(imageUtils: CustomImageUtils, region: IntArray? = null, tries: Int = 1, taps: Int = 1, confidence: Double? = null): Boolean
}

interface ComponentInterface: BaseComponentInterface {
    val template: Template
    
    override fun find(imageUtils: CustomImageUtils, region: IntArray?, tries: Int, confidence: Double?): Pair<Point?, Bitmap> {
        return imageUtils.findImage(template.path, region = region ?: template.region, tries = tries, confidence = confidence ?: template.confidence, suppressError = true)
    }

    override fun findImageWithBitmap(imageUtils: CustomImageUtils, sourceBitmap: Bitmap, region: IntArray, confidence: Double?): Point? {
        return imageUtils.findImageWithBitmap(template.path, sourceBitmap, region, customConfidence = confidence ?: template.confidence, suppressError = true)
    }

    fun findAll(imageUtils: CustomImageUtils, region: IntArray? = null, confidence: Double? = null): ArrayList<Point> {
        return imageUtils.findAll(template.path, region = region ?: template.region, confidence = (confidence ?: template.confidence) ?: 0.0)
    }

    override fun check(imageUtils: CustomImageUtils, region: IntArray?, tries: Int, confidence: Double?): Boolean {
        return find(imageUtils = imageUtils, region = region, tries = tries, confidence = confidence ?: template.confidence).first != null
    }

    override fun click(imageUtils: CustomImageUtils, region: IntArray?, tries: Int, taps: Int, confidence: Double?): Boolean {
        val point = find(imageUtils = imageUtils, region = region, tries = tries, confidence = confidence ?: template.confidence).first
        if (point == null) {
            return false
        }

        ComponentUtils.tap(point.x, point.y, template.path, taps=taps)
        return true
    }
}

interface ComplexComponentInterface: BaseComponentInterface {
    val templates: List<Template>

    override fun find(imageUtils: CustomImageUtils, region: IntArray?, tries: Int, confidence: Double?): Pair<Point?, Bitmap> {
        for (template in templates) {
            val result: Pair<Point?, Bitmap> = imageUtils.findImage(template.path, region = region ?: template.region, tries = tries, confidence = confidence ?: template.confidence, suppressError = true)
            if (result.first != null) {
                return result
            }
        }
        return Pair(null, imageUtils.getSourceBitmap())
    }

    override fun findImageWithBitmap(imageUtils: CustomImageUtils, sourceBitmap: Bitmap, region: IntArray, confidence: Double?): Point? {
        for (template in templates) {
            val result: Point? = imageUtils.findImageWithBitmap(template.path, sourceBitmap, region, customConfidence = confidence ?: template.confidence, suppressError = true)
            if (result != null) {
                return result
            }
        }
        return null
    }

    override fun check(imageUtils: CustomImageUtils, region: IntArray?, tries: Int, confidence: Double?): Boolean {
        return find(imageUtils = imageUtils, region = region, tries = tries).first != null
    }

    override fun click(imageUtils: CustomImageUtils, region: IntArray?, tries: Int, taps: Int, confidence: Double?): Boolean {
        var resultTemplate: Template? = null
        var resultPoint: Point? = null
        for (template in templates) {
            resultPoint = imageUtils.findImage(template.path, region = region ?: template.region, tries = tries, confidence = confidence ?: template.confidence, suppressError = true).first
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
    override fun find(imageUtils: CustomImageUtils, region: IntArray?, tries: Int, confidence: Double?): Pair<Point?, Bitmap> {
        for (template in templates) {
            val (point, bitmap) = imageUtils.findImage(
                template.path,
                region = region ?: template.region,
                tries = tries,
                confidence = confidence ?: template.confidence,
                suppressError = true,
            )
            if (point != null) {
                return Pair(point, bitmap)
            }
        }
        return Pair(null, createBitmap(1, 1))
    }

    override fun findImageWithBitmap(imageUtils: CustomImageUtils, sourceBitmap: Bitmap, region: IntArray, confidence: Double?): Point? {
        for (template in templates) {
            val result: Point? = imageUtils.findImageWithBitmap(template.path, sourceBitmap, region, customConfidence = confidence ?: template.confidence, suppressError = true)
            if (result != null) {
                return result
            }
        }
        return null
    }

    /** Finds image on screen and returns boolean whether it exists. */
    override fun check(imageUtils: CustomImageUtils, region: IntArray?, tries: Int, confidence: Double?): Boolean {
        return find(imageUtils = imageUtils, region = region, tries = tries, confidence = confidence).first != null
    }

    override fun click(imageUtils: CustomImageUtils, region: IntArray?, tries: Int, taps: Int, confidence: Double?): Boolean {
        val point = find(imageUtils = imageUtils, region = region, tries = tries, confidence = confidence).first
        if (point == null) {
            return false
        }

        ComponentUtils.tap(point.x, point.y, templates.first().path, taps=taps)
        return true
    }
}
