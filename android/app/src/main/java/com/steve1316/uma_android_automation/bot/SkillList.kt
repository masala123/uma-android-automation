package com.steve1316.uma_android_automation.bot

import android.util.Log
import android.graphics.Bitmap
import org.opencv.core.Point
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Collections
import kotlinx.coroutines.*
import kotlin.math.ceil
import kotlin.math.roundToInt

import net.ricecode.similarity.JaroWinklerStrategy
import net.ricecode.similarity.StringSimilarityServiceImpl

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.automation_library.data.SharedData

import com.steve1316.uma_android_automation.utils.types.BoundingBox
import com.steve1316.uma_android_automation.utils.types.SkillData
import com.steve1316.uma_android_automation.utils.types.SkillType
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.Aptitude

import com.steve1316.uma_android_automation.components.ComponentInterface
import com.steve1316.uma_android_automation.components.IconScrollListTopLeft
import com.steve1316.uma_android_automation.components.IconScrollListBottomRight
import com.steve1316.uma_android_automation.components.IconSkillTitleDoubleCircle
import com.steve1316.uma_android_automation.components.IconSkillTitleCircle
import com.steve1316.uma_android_automation.components.IconSkillTitleX
import com.steve1316.uma_android_automation.components.IconObtainedPill
import com.steve1316.uma_android_automation.components.ButtonSkillListFullStats
import com.steve1316.uma_android_automation.components.ButtonSkillUp
import com.steve1316.uma_android_automation.components.ButtonLog
import com.steve1316.uma_android_automation.components.LabelSkillListScreenSkillPoints


/**
 * @property entries Mapping of SkillListEntry objects.
 * @property skillPoints The current remaining skill points.
 * Defaults to NULL if not yet detected.
 */
class SkillList (private val game: Game) {
    private val TAG: String = "[${MainActivity.loggerTag}]SkillList"

    var entries: Map<String, SkillListEntry> = mapOf()
    var skillPoints: Int = 0
        private set

    /** Stops overscrolling of the skill list by clicking on screen.
     *
     * When scrolling the skill list, upon releasing the swipe gesture,
     * the list will continue scrolling a bit. For OCR to work properly,
     * we need the list to remain stationary ASAP.
     *
     * To prevent this overscrolling behavior, we simply click at a safe
     * location in the list in order to immediately stop the list's
     * scrolling animation.
     *
     * This location is randomized to help avoid bot detection
     * (if they even have any bot detection at all...)
     *
     * @param bbox The bounding box for the skill list.
     */
    private fun stopScrolling(bbox: BoundingBox) {
        // Define the bounding region for the tap.
        // 15px buffer around the edge to ensure we actually click in a clickable
        // region within the skill list.
        // We don't want the x-value to be further right than half way across the entry
        // in order to avoid clicking the skill up/down buttons.
        // We can use the full height (within buffer region) to click.
        val x0: Int = game.imageUtils.relX(bbox.x.toDouble(), 15)
        val x1: Int = game.imageUtils.relX(bbox.x.toDouble(), ((bbox.w - 15) / 2).toInt())
        val y0: Int = game.imageUtils.relY(bbox.y.toDouble(), 15)
        val y1: Int = game.imageUtils.relY(bbox.y.toDouble(), (bbox.h - 15).toInt())

        // Now select a random point within this region to click.
        val x: Double = (x0..x1).random().toDouble()
        val y: Double = (y0..y1).random().toDouble()

        // Tap to prevent overscrolling. This location shouldn't select any skills.
        game.tap(x, y, taps = 1, ignoreWaiting = true)
        // Small delay to allow list to stabilize and for click animation
        // to disappear before we try reading it.
        game.wait(0.2, skipWaitingForLoading = true)
    }

    /** Scrolls to the top of the skill list.
     *
     * @param bbox The skill list's bounding region.
     */
    private fun scrollToTop(bbox: BoundingBox) {
        // Scroll to top of list.
        game.gestureUtils.swipe(
            (bbox.x + (bbox.w / 2)).toFloat(),
            (bbox.y + (bbox.h / 2)).toFloat(),
            (bbox.x + (bbox.w / 2)).toFloat(),
            // high value here ensures we go all the way to top of list
            (bbox.y + (bbox.h * 100)).toFloat(),
        )
        // Small delay for list to stabilize.
        game.wait(1.0, skipWaitingForLoading = true)
    }

    /** Scrolls down a few entries in the skill list.
     *
     * @param bbox The skill list's bounding region.
     */
    private fun scrollDown(bbox: BoundingBox) {
        // Scroll down approx 2 entries in list.
        game.gestureUtils.swipe(
            (bbox.x + (bbox.w / 2)).toFloat(),
            (bbox.y + (bbox.h / 2)).toFloat(),
            (bbox.x + (bbox.w / 2)).toFloat(),
            // If we scrolled entire height of list, we'd possibly miss some
            // entries. So we just go a bit shy of the full height.
            (bbox.y - (bbox.h / 3)).toFloat(),
            duration=500,
        )
        stopScrolling(bbox)
    }

    /** Scrolls up a few entries in the skill list.
     *
     * @param bbox The skill list's bounding region.
     */
    private fun scrollUp(bbox: BoundingBox) {
        // Scroll down approx 2 entries in list.
        game.gestureUtils.swipe(
            (bbox.x + (bbox.w / 2)).toFloat(),
            (bbox.y + (bbox.h / 2)).toFloat(),
            (bbox.x + (bbox.w / 2)).toFloat(),
            // If we scrolled entire height of list, we'd possibly miss some
            // entries. So we just go a bit shy of the full height.
            (bbox.y + (bbox.h / 3)).toFloat(),
            duration=500,
        )
        stopScrolling(bbox)
    }

    /** Gets the bounding region for the skill list on the screen.
     *
     * @param bitmap Optional bitmap used for detecting skill list bounding region.
     * If not specified, a screenshot will be taken and used instead.
     * NOTE: This parameter must be specified in thread-safe contexts.
     *
     * @return On success, the bounding region. On failure, NULL.
     */
    fun getSkillListBoundingRegion(
        bitmap: Bitmap? = null,
        debugString: String = "",
    ): BoundingBox? {
        val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()

        val listTopLeftBitmap: Bitmap? = IconScrollListTopLeft.template.getBitmap(game.imageUtils)
        if (listTopLeftBitmap == null) {
            MessageLog.e(TAG, "[SKILLS] Failed to load IconScrollListTopLeft bitmap.")
            return null
        }

        val listBottomRightBitmap: Bitmap? = IconScrollListBottomRight.template.getBitmap(game.imageUtils)
        if (listBottomRightBitmap == null) {
            MessageLog.e(TAG, "[SKILLS] Failed to load IconScrollListBottomRight bitmap.")
            return null
        }

        val listTopLeft: Point? = IconScrollListTopLeft.findImageWithBitmap(game.imageUtils, bitmap)
        if (listTopLeft == null) {
            MessageLog.e(TAG, "[SKILLS] Failed to find top left corner of race list.")
            return null
        }
        val listBottomRight: Point? = IconScrollListBottomRight.findImageWithBitmap(game.imageUtils, bitmap)
        if (listBottomRight == null) {
            MessageLog.e(TAG, "[SKILLS] Failed to find bottom right corner of race list.")
            return null
        }
        val x0 = (listTopLeft.x - (listTopLeftBitmap.width / 2)).toInt()
        val y0 = (listTopLeft.y - (listTopLeftBitmap.height / 2)).toInt()
        val x1 = (listBottomRight.x + (listBottomRightBitmap.width / 2)).toInt()
        val y1 = (listBottomRight.y + (listBottomRightBitmap.height / 2)).toInt()
        val bbox = BoundingBox(
            x = x0,
            y = y0,
            w = x1 - x0,
            h = y1 - y0,
        )

        if (game.debugMode) {
            game.imageUtils.saveBitmap(bitmap, "getSkillListBoundingRegion_$debugString", bbox)
        }

        return bbox
    }

    /** Gets the bounding region for all skill list entries in the skill list.
     *
     * @param bitmap Optional bitmap used for debugging.
     * @param bboxSkillListEntries The bounding region of the skill list on screen.
     * If not specified, then we get the skill list's bounding region automatically.
     * NOTE: If this function needs to be thread safe, then the `bitmap` parameter
     * must be specified if this parameter is NULL.
     *
     * @return On success, the bounding region. On failure, NULL.
     */
    fun getSkillListEntriesBoundingRegion(
        bitmap: Bitmap? = null,
        bboxSkillList: BoundingBox? = null,
        debugString: String = ""
    ): BoundingBox? {
        val bitmap: Bitmap? = bitmap ?: game.imageUtils.getSourceBitmap()
        val bboxSkillList: BoundingBox? = bboxSkillList ?: getSkillListBoundingRegion(bitmap)
        if (bboxSkillList == null) {
            MessageLog.e(TAG, "getSKillListEntriesBoundingRegion: bboxSkillList is NULL.")
            return null
        }

        // Further crop the skill list region. This creates a window where we
        // will search for skill list entries. This prevents us from detecting
        // entries which are scrolled partially outside of the list and are cut off.
        val bbox = BoundingBox(
            x = bboxSkillList.x,
            y = bboxSkillList.y + ((SharedData.displayHeight * 0.12) / 2).toInt(),
            w = bboxSkillList.w,
            h = bboxSkillList.h - (SharedData.displayHeight * 0.12).toInt(),
        )
        if (game.debugMode) {
            game.imageUtils.saveBitmap(bitmap, "getSkillListEntriesBoundingRegion_$debugString", bbox)
        }

        return bbox
    }

    /** Gets the bounding region for all Skill Up (+) buttons in the skill list.
     *
     * @param bitmap Optional bitmap used for debugging.
     * @param bboxSkillListEntries The bounding region of all skill list entries on screen.
     *
     * @return On success, the bounding region. On failure, NULL.
     */
    private fun getSkillListSkillUpBoundingRegion(
        bitmap: Bitmap? = null,
        bboxSkillListEntries: BoundingBox,
        debugString: String = ""
    ): BoundingBox? {
        // Smaller region used to detect SkillUp buttons in the list.
        val bbox = BoundingBox(
            x = game.imageUtils.relX((bboxSkillListEntries.x + bboxSkillListEntries.w).toDouble(), -125),
            y = bboxSkillListEntries.y,
            w = game.imageUtils.relWidth(70),
            h = bboxSkillListEntries.h,
        )
        if (game.debugMode) {
            val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()
            game.imageUtils.saveBitmap(bitmap, "skillUpRegion_$debugString", bbox)
        }

        return bbox
    }

    /** Gets the bounding region for all Obtained Pill icons in the skill list.
     *
     * @param bitmap Optional bitmap used for debugging.
     * @param bboxSkillListEntries The bounding region of all skill list entries on screen.
     *
     * @return On success, the bounding region. On failure, NULL.
     */
    private fun getSkillListObtainedPillBoundingRegion(
        bitmap: Bitmap? = null,
        bboxSkillListEntries: BoundingBox,
        debugString: String = "",
    ): BoundingBox? {
        val bbox = BoundingBox(
            x = game.imageUtils.relX((bboxSkillListEntries.x + bboxSkillListEntries.w).toDouble(), -260),
            y = bboxSkillListEntries.y,
            w = game.imageUtils.relWidth(140),
            h = bboxSkillListEntries.h,
        )
        if (game.debugMode) {
            val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()
            game.imageUtils.saveBitmap(bitmap, "obtainedPillRegion_$debugString", bbox)
        }

        return bbox
    }

    /** Gets the bounding region the scroll bar on screen.
     *
     * @param bitmap Optional bitmap used for debugging.
     * @param bboxSkillList The bounding region of the skill list on the screen.
     *
     * @return On success, the bounding region. On failure, NULL.
     */
    private fun getSkillListScrollBarBoundingRegion(
        bitmap: Bitmap? = null,
        bboxSkillList: BoundingBox,
        debugString: String = "",
    ): BoundingBox? {
        val bboxScrollBar = BoundingBox(
            x = game.imageUtils.relX((bboxSkillList.x + bboxSkillList.w).toDouble(), -22),
            y = bboxSkillList.y,
            w = 10,
            h = bboxSkillList.h,
        )

        // The center column of pixels in the scrollbar.
        // This allows us to perform faster analysis on the scrollbar.
        val bboxScrollBarSingleColumn = BoundingBox(
            x = bboxScrollBar.x + (bboxScrollBar.w / 2),
            y = bboxScrollBar.y,
            w = 1,
            h = bboxScrollBar.h,
        )

        if (game.debugMode) {
            val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()
            game.imageUtils.saveBitmap(bitmap, "bboxScrollBar_$debugString", bboxScrollBar)
            game.imageUtils.saveBitmap(bitmap, "bboxScrollBarSingleColumn_$debugString", bboxScrollBarSingleColumn)
        }

        return bboxScrollBarSingleColumn
    }

    /** Extracts all text from a bitmap.
     *
     * @param bitmap The bitmap to extract text from.
     *
     * @return The extracted text as a string.
     * An empty string is returned if nothing is detected.
     */
    private fun extractText(bitmap: Bitmap): String {
        try {
            val detectedText = game.imageUtils.performOCROnRegion(
                bitmap,
                0,
                0,
                bitmap.width - 1,
                bitmap.height - 1,
                useThreshold = false,
                useGrayscale = true,
                scale = 2.0,
                ocrEngine = "mlKit",
                debugName = "analyzeSkillListEntry::extractText"
            )
            MessageLog.d(TAG, "Extracted text: \"$detectedText\"")
            return detectedText
        } catch (e: Exception) {
            MessageLog.e(TAG, "Exception during text extraction: ${e.message}")
            return ""
        }
    }

    /** Extracts the skill points from a bitmap.
     *
     * @param bitmap Optional bitmap used to detect the skill points.
     * If not specified, a screenshot is taken and used instead.
     *
     * @return On success, the skill points as an integer. On failure, NULL.
     */
    fun detectSkillPoints(bitmap: Bitmap? = null): Int? {
        val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()

        val templateBitmap: Bitmap? = LabelSkillListScreenSkillPoints.template.getBitmap(game.imageUtils)
        if (templateBitmap == null) {
            MessageLog.e(TAG, "Failed to load template bitmap for LabelSkillListScreenSkillPoints.")
            return null
        }

        val point: Point? = LabelSkillListScreenSkillPoints.findImageWithBitmap(game.imageUtils, bitmap)
        if (point == null) {
            MessageLog.e(TAG, "Failed to find LabelSkillListScreenSkillPoints.")
            return null
        }

        val bbox = BoundingBox(
            x = (point.x + templateBitmap.width).toInt(),
            y = (point.y - templateBitmap.height).toInt(),
            w = (templateBitmap.width * 1.5).toInt(),
            h = (templateBitmap.height * 2).toInt(),
        )

        val skillPointsBitmap: Bitmap? = game.imageUtils.createSafeBitmap(bitmap, bbox, "skillPointsBitmap")
        if (skillPointsBitmap == null) {
            MessageLog.e(TAG, "[SKILLS] detectSkillPoints: Failed to createSafeBitmap for skill points.")
            return null
        }

        val skillPointsString: String = extractText(skillPointsBitmap)
        val tmpSkillPoints: Int? = skillPointsString
            .replace("[^0-9]".toRegex(), "")
            .toIntOrNull()
        if (tmpSkillPoints != null) {
            skillPoints = tmpSkillPoints
        }
        return skillPoints
    }

    /** Extracts the title (skill name) from a cropped skill list entry bitmap.
     *
     * @param bitmap A bitmap of a single cropped skill list entry.
     *
     * @return On success, the title string. On failure, NULL.
     */
    fun getSkillListEntryTitle(bitmap: Bitmap? = null, debugString: String = ""): String? {
        val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()

        val bbox = BoundingBox(
            x = (bitmap.width * 0.142).toInt(),
            y = 0,
            w = (bitmap.width * 0.57).toInt(),
            h = (bitmap.height * 0.338).toInt(),
        )
        val croppedTitle = game.imageUtils.createSafeBitmap(bitmap, bbox, "bboxTitle_$debugString")
        if (croppedTitle == null) {
            Log.e(TAG, "[SKILLS] getSkillListEntryTitle: createSafeBitmap for croppedTitle returned NULL.")
            return null
        }
        if (game.debugMode) {
            game.imageUtils.saveBitmap(croppedTitle, filename = "bboxTitle_$debugString")
        }

        var skillName: String = extractText(croppedTitle)
        if (skillName == "") {
            Log.e(TAG, "[SKILLS] getSkillListEntryTitle: Failed to extract skill name.")
            return null
        }

        // Check if the skill has a special char (◎, ○, ×) at the end.
        val componentsToCheck: List<ComponentInterface> = listOf(
            IconSkillTitleDoubleCircle,
            IconSkillTitleCircle,
            IconSkillTitleX,
        )
        var match: ComponentInterface? = null
        for (component in componentsToCheck) {
            val point: Point? = component.findImageWithBitmap(game.imageUtils, croppedTitle)
            if (point != null) {
                match = component
                break
            }
        }

        // Get the appropriate char to append to the search string.
        // Typically the extracted title will end with "O" or "x"
        // but we can't just replace that character since some titles
        // actually end in those letters. So we just append this to the title
        // since it shouldn't cause fuzzy matching to fail.
        val iconChar: String = when (match) {
            IconSkillTitleDoubleCircle -> "◎"
            IconSkillTitleCircle -> "○"
            IconSkillTitleX -> "×"
            else -> ""
        }
        skillName += iconChar

        // Negative skills have "Remove" in front of their skill name in the title.
        // The actual skill itself in the database does not have this prefix.
        // Get rid of this prefix as it causes fuzzy matching to fail.
        skillName = if (skillName.startsWith("remove", ignoreCase = true)) {
            skillName.drop("remove".length)
        } else {
            skillName
        }

        return skillName
    }
    
    /** Extracts the price from a cropped skill list entry bitmap.
     *
     * @param bitmap A bitmap of a single cropped skill list entry.
     *
     * @return On success, the price as an integer. On failure, NULL.
     */
    fun getSkillListEntryPrice(bitmap: Bitmap? = null, debugString: String = ""): Int? {
        val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()

        val bbox = BoundingBox(
            x = (bitmap.width * 0.7935).toInt(),
            y = (bitmap.height * 0.372).toInt(),
            w = (bitmap.width * 0.1068).toInt(),
            h = (bitmap.height * 0.251).toInt(),
        )
        val croppedPrice = game.imageUtils.createSafeBitmap(bitmap, bbox, "bboxPrice_$debugString")
        if (croppedPrice == null) {
            Log.e(TAG, "[SKILLS] getSkillListEntryPrice: createSafeBitmap for croppedPrice returned NULL.")
            return null
        }

        if (game.debugMode) {
            game.imageUtils.saveBitmap(croppedPrice, filename = "bboxPrice_$debugString")
        }

        val price: Int? = extractText(croppedPrice).replace("[^0-9]".toRegex(), "").toIntOrNull()
        if (price == null) {
            Log.e(TAG, "[SKILLS] getSkillListEntryPrice: Failed to extract skill price.")
            return null
        }

        return price
    }

    /** Extracts all useful information from a single entry in the skill list.
     *
     * @param bitmap A bitmap of a single cropped skill list entry.
     * @param bIsObtained Whether this entry has the Obtained pill icon.
     * If not obtained, then this entry will have the skill up button.
     * This is important because it determines how we detect the skill's price.
     * @param debugString A string used in logging and saved bitmap filenames.
     *
     * @return On success, a SkillListEntry with the extracted info. Otherwise, NULL.
     */
    fun analyzeSkillListEntry(
        bitmap: Bitmap,
        bIsObtained: Boolean,
        debugString: String = "",
    ): SkillListEntry? {
        val latch = CountDownLatch(2)
        var skillPrice: Int? = null
        var skillData: SkillData? = null

        // TITLE
        Thread {
            try {
                // Extract the skill name from the bitmap.
                val skillName: String? = getSkillListEntryTitle(bitmap, debugString)
                if (skillName == null) {
                    Log.e(TAG, "[SKILLS] getSkillListEntryTitle() returned NULL.")
                    return@Thread
                }

                // Now lookup the name in the database and update it.
                val tmpSkillData: SkillData? = game.skillPlan.skillDatabase.getSkillData(skillName)
                if (tmpSkillData == null) {
                    Log.e(TAG, "[SKILLS] getSkillData(\"${skillName}\") returned NULL.")
                    return@Thread
                }
                skillData = tmpSkillData
            } catch (e: Exception) {
                Log.e(TAG, "[ERROR] Error processing skill name: ${e.stackTraceToString()}")
            } finally {
                latch.countDown()
            }
        }.start()

        // PRICE
        Thread {
            try {
                // If the skill is already obtained, don't bother trying to get the price.
                val tmpSkillPrice: Int? = if (bIsObtained) 0 else getSkillListEntryPrice(bitmap, debugString)
                if (tmpSkillPrice == null) {
                    Log.e(TAG, "[SKILLS] getSkillListEntryPrice() returned NULL.")
                    return@Thread
                }
                skillPrice = tmpSkillPrice
            } catch (e: Exception) {
                Log.e(TAG, "[ERROR] Error processing skill price: ${e.stackTraceToString()}")
            } finally {
                latch.countDown()
            }
        }.start()

        try {
            latch.await(3, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Log.e(TAG, "[ERROR] Parallel skill analysis timed out.")
        }

        if (skillData == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to infer skillData.")
            return null
        }

        if (skillPrice == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to detect skillPrice.")
            return null
        }

        return SkillListEntry(
            game = game,
            skillData = skillData,
            price = skillPrice,
            bIsObtained = bIsObtained,
            bIsVirtual = false,
        )
    }

    /** Extracts all useful information from a single entry in the skill list in a thread safe manner.
     *
     * This version performs all operations synchronously so that we can
     * use it from inside another thread.
     *
     * @param bitmap A bitmap of a single cropped skill list entry.
     * @param bIsObtained Whether this entry has the Obtained pill icon.
     * If not obtained, then this entry will have the skill up button.
     * This is important because it determines how we detect the skill's price.
     * @param debugString A string used in logging and saved bitmap filenames.
     *
     * @return On success, a SkillListEntry with the extracted info. Otherwise, NULL.
     */
    fun analyzeSkillListEntryThreadSafe(bitmap: Bitmap, bIsObtained: Boolean, debugString: String = ""): SkillListEntry? {
        // Extract the skill name from the bitmap.
        val skillName: String? = getSkillListEntryTitle(bitmap, debugString)
        if (skillName == null) {
            MessageLog.e(TAG, "[SKILLS] getSkillListEntryTitle() returned NULL.")
            return null
        }

        // Now lookup the name in the database and update it.
        val skillData: SkillData? = game.skillPlan.skillDatabase.getSkillData(skillName)
        if (skillData == null) {
            MessageLog.e(TAG, "[SKILLS] getSkillData(\"${skillName}\") returned NULL.")
            return null
        }

        // If the skill is already obtained, don't bother trying to get the price.
        val skillPrice: Int? = if (bIsObtained) 0 else getSkillListEntryPrice(bitmap, debugString)
        if (skillPrice == null) {
            MessageLog.e(TAG, "[SKILLS] getSkillListEntryPrice() returned NULL.")
            return null
        }

        return SkillListEntry(
            game = game,
            skillData = skillData,
            price = skillPrice,
            bIsObtained = bIsObtained,
            bIsVirtual = false,
        )
    }

    /** Parses the skill list entries currently visible on screen.
     *
     * @param bitmap The bitmap to use when analyzing the skill list.
     * If not specified, a screenshot will be taken and used instead.
     * @param bboxSkillList The bounding region for the skill list in the bitmap.
     * If not specified, this region will be detected automatically.
     * @param debugString A string used in logging and saved bitmap filenames.
     * @param onSkillListEntryDetected A callback function that is called for each
     * SkillListEntry that we detect. This can be useful if we want to perform
     * an operation immediately upon detecting an entry.
     *
     * @return A mapping of skill names to SkillListEntry objects.
     */
    fun processSkillList(
        bitmap: Bitmap? = null,
        bboxSkillList: BoundingBox? = null,
        debugString: String = "",
        onSkillListEntryDetected: ((entry: SkillListEntry, point: Point) -> Unit)? = null,
    ): Map<String, SkillListEntry> {
        val bitmap = bitmap ?: game.imageUtils.getSourceBitmap()

        val bboxSkillList: BoundingBox? = bboxSkillList ?: getSkillListBoundingRegion(bitmap, debugString)
        if (bboxSkillList == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListBoundingRegion() returned NULL.")
            return emptyMap()
        }

        val bboxSkillListEntries = getSkillListEntriesBoundingRegion(bitmap, bboxSkillList, debugString)
        if (bboxSkillListEntries == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListEntriesBoundingRegion() returned NULL.")
            return emptyMap()
        }

        val bboxSkillUpRegion = getSkillListSkillUpBoundingRegion(bitmap, bboxSkillListEntries, debugString)
        if (bboxSkillUpRegion == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListSkillUpBoundingRegion() returned NULL.")
            return emptyMap()
        }

        val bboxObtainedPillRegion = getSkillListObtainedPillBoundingRegion(bitmap, bboxSkillListEntries, debugString)
        if (bboxObtainedPillRegion == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListObtainedPillBoundingRegion() returned NULL.")
            return emptyMap()
        }

        val skillUpLocs: List<Pair<String, Point>> = ButtonSkillUp.findAllWithBitmap(
            imageUtils = game.imageUtils,
            sourceBitmap = bitmap,
            region = bboxSkillUpRegion.toIntArray(),
        ).map { point -> Pair("skillUp", point) }

        val obtainedPillLocs: List<Pair<String, Point>> = IconObtainedPill.findAllWithBitmap(
            imageUtils = game.imageUtils,
            sourceBitmap = bitmap,
            region = bboxObtainedPillRegion.toIntArray(),
        ).map { point -> Pair("obtained", point) }

        // Combine the sets of locations and sort by their height on the screen.
        val points = skillUpLocs.plus(obtainedPillLocs).sortedBy { it.second.y }
        val skillListEntries: MutableMap<String, SkillListEntry> = mutableMapOf()

        var i: Int = 0
        for ((index, pair) in points.withIndex()) {
            val (pointType, point) = pair
            // Calculate the bounding box for the skill info relative to
            // the location of the detected SkillUp and ObtainedPill icons.
            // x/y positions differ for the SkillUp and ObtainedPill images.
            val bboxSkillBox = if (pointType == "obtained") {
                BoundingBox(
                    x = (point.x - (SharedData.displayWidth * 0.77)).toInt(),
                    y = (point.y - (SharedData.displayHeight * 0.0599)).toInt(),
                    w = (SharedData.displayWidth * 0.91).toInt(),
                    h = (SharedData.displayHeight * 0.12).toInt(),
                )
            } else {
                BoundingBox(
                    x = (point.x - (SharedData.displayWidth * 0.86)).toInt(),
                    y = (point.y - (SharedData.displayHeight * 0.0583)).toInt(),
                    w = (SharedData.displayWidth * 0.91).toInt(),
                    h = (SharedData.displayHeight * 0.12).toInt(),
                )
            }

            val croppedSkillBox = game.imageUtils.createSafeBitmap(bitmap, bboxSkillBox, "bboxSkillBox_$index")
            if (croppedSkillBox == null) {
                MessageLog.e(TAG, "[SKILLS] analyzeSkillList: createSafeBitmap for skillBoxBitmap returned NULL.")
                return emptyMap()
            }
            if (game.debugMode) {
                game.imageUtils.saveBitmap(croppedSkillBox, filename = "bboxSkillBox_$index")
            }

            val bIsObtained: Boolean = pointType == "obtained"
            val skillListEntry: SkillListEntry? = analyzeSkillListEntry(croppedSkillBox, bIsObtained, "$index")
            if (skillListEntry == null) {
                MessageLog.e(TAG, "[SKILLS] analyzeSkillList: ($index) SkillListEntry is NULL.")
                continue
            }

            if (onSkillListEntryDetected != null) {
                onSkillListEntryDetected(skillListEntry, point)
            }

            skillListEntries[skillListEntry.name] = skillListEntry
        }

        return skillListEntries.toMap()
    }

    /** Parses the skill list entries currently visible on screen in a thread-safe manner.
     *
     * @param bitmap The bitmap to use when analyzing the skill list.
     * @param bboxSkillList The bounding region for the skill list in the bitmap.
     * @param debugString A string to append to logging messages and any
     * potential saved bitmaps.
     * @param onSkillListEntryDetected A callback function that is called for each
     * SkillListEntry that we detect. This can be useful if we want to perform
     * an operation immediately upon detecting an entry.
     * NOTE: This function MUST be thread safe.
     *
     * @return A mapping of skill names to SkillListEntry objects.
     */
    fun processSkillListThreadSafe(
        bitmap: Bitmap,
        bboxSkillList: BoundingBox,
        debugString: String = "",
        onSkillListEntryDetected: ((entry: SkillListEntry, point: Point) -> Unit)? = null,
    ): Map<String, SkillListEntry> {
        val bboxSkillListEntries = getSkillListEntriesBoundingRegion(bitmap, bboxSkillList, debugString)
        if (bboxSkillListEntries == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListEntriesBoundingRegion() returned NULL.")
            return emptyMap()
        }

        val bboxSkillUpRegion = getSkillListSkillUpBoundingRegion(bitmap, bboxSkillListEntries, debugString)
        if (bboxSkillUpRegion == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListSkillUpBoundingRegion() returned NULL.")
            return emptyMap()
        }

        val bboxObtainedPillRegion = getSkillListObtainedPillBoundingRegion(bitmap, bboxSkillListEntries, debugString)
        if (bboxObtainedPillRegion == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListObtainedPillBoundingRegion() returned NULL.")
            return emptyMap()
        }

        val skillUpLocs: List<Pair<String, Point>> = ButtonSkillUp.findAllWithBitmap(
            imageUtils = game.imageUtils,
            sourceBitmap = bitmap,
            region = bboxSkillUpRegion.toIntArray(),
        ).map { point -> Pair("skillUp", point) }

        val obtainedPillLocs: List<Pair<String, Point>> = IconObtainedPill.findAllWithBitmap(
            imageUtils = game.imageUtils,
            sourceBitmap = bitmap,
            region = bboxObtainedPillRegion.toIntArray(),
        ).map { point -> Pair("obtained", point) }

        // Combine the sets of locations and sort by their height on the screen.
        val points = skillUpLocs.plus(obtainedPillLocs).sortedBy { it.second.y }
        val skillListEntries: MutableMap<String, SkillListEntry> = mutableMapOf()

        var i: Int = 0
        for ((index, pair) in points.withIndex()) {
            val (pointType, point) = pair
            // Calculate the bounding box for the skill info relative to
            // the location of the detected SkillUp and ObtainedPill icons.
            // x/y positions differ for the SkillUp and ObtainedPill images.
            val bboxSkillBox = if (pointType == "obtained") {
                BoundingBox(
                    x = (point.x - (SharedData.displayWidth * 0.77)).toInt(),
                    y = (point.y - (SharedData.displayHeight * 0.0599)).toInt(),
                    w = (SharedData.displayWidth * 0.91).toInt(),
                    h = (SharedData.displayHeight * 0.12).toInt(),
                )
            } else {
                BoundingBox(
                    x = (point.x - (SharedData.displayWidth * 0.86)).toInt(),
                    y = (point.y - (SharedData.displayHeight * 0.0583)).toInt(),
                    w = (SharedData.displayWidth * 0.91).toInt(),
                    h = (SharedData.displayHeight * 0.12).toInt(),
                )
            }

            val croppedSkillBox = game.imageUtils.createSafeBitmap(bitmap, bboxSkillBox, "bboxSkillBox_$index")
            if (croppedSkillBox == null) {
                MessageLog.e(TAG, "[SKILLS] analyzeSkillList: createSafeBitmap for skillBoxBitmap returned NULL.")
                return emptyMap()
            }
            if (game.debugMode) {
                game.imageUtils.saveBitmap(croppedSkillBox, filename = "bboxSkillBox_$index")
            }

            val bIsObtained: Boolean = pointType == "obtained"
            val skillListEntry: SkillListEntry? = analyzeSkillListEntryThreadSafe(croppedSkillBox, bIsObtained, "$index")
            if (skillListEntry == null) {
                MessageLog.e(TAG, "[SKILLS] analyzeSkillList: ($index) SkillListEntry is NULL.")
                continue
            }

            if (onSkillListEntryDetected != null) {
                onSkillListEntryDetected(skillListEntry, point)
            }

            skillListEntries[skillListEntry.name] = skillListEntry
        }

        return skillListEntries.toMap()
    }

    /** Gets a mapping of virtual skills (upgrades for existing skills in list).
     *
     * If an entry in the skill list can be upgraded in-place, then all of its
     * upgrades are considered "virtual" entries in the skill list. This is because
     * they do not require any additional skill hints for us to acquire, however
     * they are not currently present in the skill list.
     *
     * @param skillListEntries A mapping of skill list entries which we
     * use to calculate a set of virtual skills.
     */
    fun getVirtualSkillListEntries(
        skillListEntries: Map<String, SkillListEntry>? = null,
    ): Map<String, SkillListEntry> {
        val skillListEntries: Map<String, SkillListEntry> = skillListEntries ?: entries
        val result: MutableMap<String, SkillListEntry> = mutableMapOf()

        for ((name, entry) in skillListEntries) {
            // If entry has no upgrades then we can ignore it.
            val upgradeNames: List<String> = game.skillPlan.skillDatabase.getUpgrades(name)
            if (upgradeNames.isEmpty()) {
                continue
            }

            // We also ignore the entry if it does not have in-place upgrades.
            // Take Corner Recovery O for example. Purchasing it does not cause
            // Swinging Maestro to appear in the skill list since it isn't an
            // in-place upgrade. This is why we skip these.
            if (!entry.skillData.bIsInPlace) {
                continue
            }

            for (upgradeName in upgradeNames) {
                // Don't need to parse upgrade if it is already in our entries.
                if (upgradeName in skillListEntries) {
                    continue
                }

                val upgradeData: SkillData? = game.skillPlan.skillDatabase.getSkillData(upgradeName)
                if (upgradeData == null) {
                    continue
                }
                
                // The discount doesn't change for in-place upgrades, however
                // the base cost of each level of the skill does change.
                val upgradeBaseCost: Int = upgradeData.cost ?: 0
                val upgradePrice: Int = ceil(upgradeBaseCost * entry.discount).toInt()
                val upgradeSkillListEntry: SkillListEntry = SkillListEntry(
                    game = game,
                    skillData = upgradeData,
                    price = upgradePrice,
                    bIsObtained = false,
                    bIsVirtual = true,
                )
                result[upgradeName] = upgradeSkillListEntry
            }
        }

        return result.toMap()
    }

    /** Scrolls through the entire skill list and extracts info from entries.
     *
     * @param onSkillListEntryDetected A callback function that is called for each
     * SkillListEntry that we detect. This can be useful if we want to perform
     * an operation immediately upon detecting an entry.
     */
    fun iterateOverSkillList(onSkillListEntryDetected: ((entry: SkillListEntry, point: Point) -> Unit)? = null) {
        var bitmap = game.imageUtils.getSourceBitmap()
        val bboxSkillList: BoundingBox? = getSkillListBoundingRegion(bitmap)
        if (bboxSkillList == null) {
            MessageLog.e(TAG, "[SKILLS] iterateOverSkillList: getSkillListBoundingRegion() returned NULL.")
            return
        }

        val bboxScrollBar: BoundingBox? = getSkillListScrollBarBoundingRegion(bitmap, bboxSkillList)
        if (bboxScrollBar == null) {
            MessageLog.e(TAG, "[SKILLS] iterateOverSkillList: getSkillListScrollBarBoundingRegion() returned NULL.")
            return
        }

        // Scroll to top before we do anything else.
        scrollToTop(bboxSkillList)
        
        // Max time limit for the while loop to scroll through the list.
        val startTime: Long = System.currentTimeMillis()
        val maxTimeMs: Long = 60000
        var prevScrollBarBitmap: Bitmap? = null

        var prevNames: Set<String> = setOf()

        while (System.currentTimeMillis() - startTime < maxTimeMs) {
            bitmap = game.imageUtils.getSourceBitmap()

            // SCROLLBAR CHANGE DETECTION LOGIC
            val scrollBarBitmap: Bitmap? = game.imageUtils.createSafeBitmap(
                bitmap,
                bboxScrollBar,
                "bboxScrollBar",
            )
            if (scrollBarBitmap == null) {
                MessageLog.e(TAG, "[SKILLS] iterateOverSkillList: Failed to createSafeBitmap for scrollbar.")
                return
            }

            // If the scrollbar hasn't changed after scrolling,
            // that means we've reached the end of the list.
            /*
            if (prevScrollBarBitmap != null) {
                val similarity: Double = game.imageUtils.compareBitmapSSIM(scrollBarBitmap, prevScrollBarBitmap)
                if (similarity >= 0.95) {
                    break
                }
            }
            */
            ///*
            if (prevScrollBarBitmap != null && scrollBarBitmap.sameAs(prevScrollBarBitmap)) {
                break
            }
            //*/

            prevScrollBarBitmap = scrollBarBitmap

            val tmpSkillListEntries: Map<String, SkillListEntry>? = processSkillList(
                bitmap = bitmap,
                bboxSkillList = bboxSkillList,
                debugString = "iterateOverSkillList",
                onSkillListEntryDetected = onSkillListEntryDetected,
            )
            if (tmpSkillListEntries != null) {
                // Another exit condition.
                // If there are no new entries after scrolling,
                // then we're at the bottom of the list.
                if (prevNames == tmpSkillListEntries.keys.toSet()) {
                    break
                }
                prevNames = tmpSkillListEntries.keys.toSet()
            }

            scrollDown(bboxSkillList)
            // Slight delay to allow screen to settle before next loop.
            game.wait(0.5, skipWaitingForLoading = true)
        }
    }

    /** Gets all entries in the skill list.
     *
     * @param onSkillListEntryDetected A callback function that is called for each
     * SkillListEntry that we detect. This can be useful if we want to perform
     * an operation immediately upon detecting an entry.
     *
     * @return A mapping of skill names to SkillListEntry objects.
     */
    fun getSkillListEntries(
        onSkillListEntryDetected: ((entry: SkillListEntry, point: Point) -> Unit)? = null,
    ): Map<String, SkillListEntry>? {
        // List of skills in the order that they are shown in the game.
        // We use this later to purchase items from top to bottom.
        val skillListEntries: MutableMap<String, SkillListEntry> = mutableMapOf()

        iterateOverSkillList() { entry: SkillListEntry, point: Point ->
            skillListEntries[entry.name] = entry
            // Pass this data along to the callback parameter.
            if (onSkillListEntryDetected != null) {
                onSkillListEntryDetected(entry, point)
            }
        }

        // Now add in any virtual skills.
        val virtualSkillListEntries: Map<String, SkillListEntry> = getVirtualSkillListEntries(skillListEntries)
        skillListEntries.putAll(virtualSkillListEntries)

        // The following steps rely on the `entries` property to be set.
        entries = skillListEntries.toMap()

        // We need to go back through and correct the prices for skills that
        // have upgrades that are not in-place.
        for ((name, entry) in entries) {
            updateSkillListEntryEvaluationPoints(name)
            updateSkillListEntryBasePrice(name)
        }

        return entries
    }

    fun updateSkillListEntryEvaluationPoints(name: String) {
        val entry: SkillListEntry? = getEntry(name)
        if (entry == null) {
            return
        }

        if (entry.bIsObtained || entry.bIsVirtual || entry.skillData.bIsInPlace) {
            return
        }

        val downgradeNames: MutableList<String> = game.skillPlan.skillDatabase.getDowngrades(name).toMutableList()
        // Exclude the current entry from the downgrades list.
        // We don't want to include it in the combined value.
        downgradeNames.remove(name)
        if (downgradeNames.isEmpty()) {
            return
        }

        val downgradeEntries: MutableList<SkillListEntry> = downgradeNames.mapNotNull { this.entries[it] }.toMutableList()
        // Virtual and obtained entries should not be counted.
        downgradeEntries.removeAll { it.bIsObtained || it.bIsVirtual || it.skillData.bIsNegative }
        // Combine downgrade version evaluation points.
        val combinedDowngradeEvalPt: Int = downgradeEntries.sumOf { it.skillData.evalPt }
        entry.updateEvalPt(combinedDowngradeEvalPt)
    }

    fun updateSkillListEntryBasePrice(name: String) {
        val entry: SkillListEntry? = getEntry(name)
        if (entry == null) {
            return
        }

        if (entry.bIsObtained || entry.bIsVirtual || entry.skillData.bIsInPlace) {
            return
        }

        val downgradeNames: MutableList<String> = game.skillPlan.skillDatabase.getDowngrades(name).toMutableList()
        // Exclude the current entry from the downgrades list.
        // We don't want to include it price in the combined value.
        downgradeNames.remove(name)
        if (downgradeNames.isEmpty()) {
            return
        }

        val downgradeEntries: MutableList<SkillListEntry> = downgradeNames.mapNotNull { this.entries[it] }.toMutableList()
        MessageLog.e("REMOVEME", "downgradeEntries for $name before filter: $downgradeEntries")
        // Virtual and obtained entries should not be counted.
        downgradeEntries.removeAll { it.bIsVirtual }
        // Combine downgrade version prices.
        val combinedDowngradePrices: Int = downgradeEntries.sumOf { it.price }
        entry.updateBasePrice(combinedDowngradePrices)
    }

    /** Gets skill list entries using mocked data.
     *
     * @return A mapping of skill names to SkillListEntry objects.
     */
    fun getMockSkillListEntries(): Map<String, SkillListEntry>? {
        var skillListEntries: MutableMap<String, SkillListEntry> = mutableMapOf()

        val mockSkills: Map<String, Int> = mapOf(
            "Warning Shot!" to -1,
            "Triumphant Pulse" to 120,
            "Kyoto Racecourse ○" to 63,
            "Standard Distance ○" to 63,
            //"Standard Distance ×" to 35,
            "Summer Runner ○" to 81,
            "Cloudy Days ○" to 81,
            "Professor of Curvature" to 279,
            "Corner Adept ○" to 117,
            "Swinging Maestro" to 323,
            "Corner Recovery ○" to 170,
            "Straightaway Acceleration" to 119,
            "Calm in a Crowd" to 153,
            "Nimble Navigator" to 135,
            "Homestretch Haste" to 153,
            "Up-Tempo" to 104,
            "Steadfast" to 144,
            "Extra Tank" to 96,
            "Frenzied Pace Chasers" to 104,
            "Medium Straightaways ○" to 60,
            "Keeping the Lead" to 128,
            "Pressure" to 128,
            "Pace Chaser Corners ○" to 91,
            "Straight Descent" to 78,
            "Hydrate" to 144,
            "Late Surger Straightaways ○" to 84,
            "Fighter" to 84,
            "I Can See Right Through You" to 110,
            "Highlander" to 128,
            "Uma Stan" to 160,
            "Ignited Spirit SPD" to 180,
        )

        for ((skillName, skillPrice) in mockSkills) {
            val skillData: SkillData? = game.skillPlan.skillDatabase.getSkillData(skillName)
            if (skillData == null) {
                MessageLog.w(TAG, "[SKILLS] getMockSkillList: Failed to get skill data for \"$skillName\"")
                continue
            }
            val skillListEntry: SkillListEntry? = SkillListEntry(
                game = game,
                skillData = skillData,
                price = skillPrice,
                bIsObtained = skillPrice == -1,
            )

            if (skillListEntry == null) {
                MessageLog.e(TAG, "[SKILLS] getMockSkillList: skillListEntry is NULL for \"$skillName\".")
                continue
            }
            skillListEntries[skillName] = skillListEntry
        }

        // Now add in any virtual skills.
        val virtualSkillListEntries: Map<String, SkillListEntry> = getVirtualSkillListEntries(skillListEntries)
        skillListEntries.putAll(virtualSkillListEntries)

        entries = skillListEntries.toMap()
        return entries
    }

    /** Checks whether we are at any skill list screen.
     *
     * @param bitmap Optional bitmap to use when detecting whether we are at
     * the skill list screen. If not specified, a screenshot will be taken
     * and used instead.
     *
     * @return Whether we are at a skill list screen.
     */
    fun checkSkillListScreen(bitmap: Bitmap? = null): Boolean {
        val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()
        return (
            ButtonSkillListFullStats.check(game.imageUtils, sourceBitmap = bitmap) &&
            LabelSkillListScreenSkillPoints.check(game.imageUtils, sourceBitmap = bitmap)
        )
    }

    /** Checks whether we are at the career completion skill list screen.
     *
     * @param bitmap Optional bitmap to use when detecting whether we are at
     * the skill list screen. If not specified, a screenshot will be taken
     * and used instead.
     *
     * @return Whether we are at the career completion skill list screen.
     */
    fun checkCareerCompleteSkillListScreen(bitmap: Bitmap? = null): Boolean {
        val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()
        return (
            !ButtonLog.check(game.imageUtils, sourceBitmap = bitmap) &&
            checkSkillListScreen(bitmap)
        )
    }

    fun printSkillListEntries(
        skillListEntries: Map<String, SkillListEntry>? = null,
        verbose: Boolean = false,
    ) {
        val skillListEntries: Map<String, SkillListEntry> = skillListEntries ?: entries
        MessageLog.d(TAG, "============ Skill List Entries ============")
        for ((name, entry) in skillListEntries) {
            val entryString: String = if (verbose) {
                "${entry}"
            } else {
                val priceString: String = if (entry.price != entry.basePrice) "${entry.price}(${entry.basePrice})" else "${entry.price}"
                val extraString: String = if (entry.bIsVirtual) " (virtual)" else ""
                "${priceString}${extraString}"
            }
            MessageLog.d(TAG, "\t${name}: ${entryString}")
        }
        MessageLog.d(TAG, "============================================")
    }

    fun buySkill(name: String, skillUpButtonLocation: Point): SkillListEntry? {
        MessageLog.e("REMOVEME", "buySkill: $name")
        val entry: SkillListEntry? = entries[name]
        if (entry == null) {
            MessageLog.w(TAG, "buySkill: \"$name\" not found.")
            return null
        }

        if (entry.price > skillPoints) {
            MessageLog.w(TAG, "buySkill: Not enough skill points (${skillPoints}pt) to buy \"$name\" (${entry.price}pt).")
            return null
        }

        game.tap(
            skillUpButtonLocation.x,
            skillUpButtonLocation.y,
            ButtonSkillUp.template.path,
        )

        // If we just purchased the entry, then it can't be virtual
        // since it exists on screen.
        entry.bIsVirtual = false
        entry.bIsObtained = true
        skillPoints -= entry.price

        return entry
    }

    fun getAllSkills(): Map<String, SkillListEntry> {
        return entries
    }
    
    fun getUnobtainedSkills(): Map<String, SkillListEntry> {
        return entries.filterValues { !it.bIsObtained }
    }

    fun getAvailableSkills(): Map<String, SkillListEntry> {
        return getUnobtainedSkills().filterValues { !it.bIsVirtual }
    }
    
    fun getVirtualSkills(): Map<String, SkillListEntry> {
        return getUnobtainedSkills().filterValues { it.bIsVirtual }
    }

    fun getNegativeSkills(): Map<String, SkillListEntry> {
        return getUnobtainedSkills().filterValues { it.skillData.bIsNegative }
    }

    fun getInheritedUniqueSkills(): Map<String, SkillListEntry> {
        return getUnobtainedSkills().filterValues { it.skillData.bIsInheritedUnique }
    }

    fun getAptitudeIndependentSkills(): Map<String, SkillListEntry> {
        return getUnobtainedSkills().filterValues {
            it.skillData.runningStyle == null &&
            it.skillData.trackDistance == null
        }
    }

    fun getRunningStyleSkills(runningStyle: RunningStyle): Map<String, SkillListEntry> {
        return getUnobtainedSkills().filterValues { it.skillData.runningStyle == runningStyle }
    }

    fun getTrackDistanceSkills(trackDistance: TrackDistance): Map<String, SkillListEntry> {
        return getUnobtainedSkills().filterValues { it.skillData.trackDistance == trackDistance }
    }

    fun getEntry(name: String): SkillListEntry? {
        val result: SkillListEntry? = entries[name]
        if (result == null) {
            MessageLog.w(TAG, "getEntry: No entry found for \"$name\".")
        }
        return result
    }

    fun markEntryObtained(name: String): Boolean {
        val entry: SkillListEntry? = getEntry(name)
        if (entry == null) {
            MessageLog.w(TAG, "updateEntry: getEntry(\"$name\") returned NULL.")
            return false
        }

        // If entry is already obtained, we have nothing to do.
        if (entry.bIsObtained) {
            return true
        }
        MessageLog.e("REMOVEME", "markEntryObtained before: $name -> ${entries[name]?.bIsObtained}")
        entry.bIsObtained = true
        MessageLog.e("REMOVEME", "markEntryObtained after: $name -> ${entries[name]?.bIsObtained}")
        
        val upgradeNames: List<String> = game.skillPlan.skillDatabase.getUpgrades(name)
        for (upgradeName in upgradeNames) {
            updateSkillListEntryEvaluationPoints(upgradeName)
            updateSkillListEntryBasePrice(upgradeName)
        }
        return true
    }
}
