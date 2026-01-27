package com.steve1316.uma_android_automation.bot

import android.util.Log
import android.graphics.Bitmap
import org.opencv.core.Point
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.automation_library.data.SharedData

import com.steve1316.uma_android_automation.bot.SkillDatabase

import com.steve1316.uma_android_automation.utils.types.BoundingBox
import com.steve1316.uma_android_automation.utils.types.TrackDistance
import com.steve1316.uma_android_automation.utils.types.TrackSurface
import com.steve1316.uma_android_automation.utils.types.RunningStyle
import com.steve1316.uma_android_automation.utils.types.SkillData

import com.steve1316.uma_android_automation.components.*

/** A callback that fires whenever we detect an entry in the skill list.
 *
 * @param skillList A reference to the SkillList instance which fired this callback.
 * @param entry: The SkillListEntry instance which was detected.
 * @param skillUpButtonLocation The screen location of the SkillUpButton for this entry.
 */
typealias OnEntryDetectedCallback = (
    skillList: SkillList,
    entry: SkillListEntry,
    skillUpButtonLocation: Point,
) -> Unit

/**
 * @property skillDatabase A SkillDatabase instance used for querying skill info.
 * @property entries Mapping of SkillListEntry objects.
 * @property skillPoints The current remaining skill points.
 * Defaults to NULL if not yet detected.
 * The value is set in the `detectSkillPoints` function.
 */
class SkillList (private val game: Game) {
    private val TAG: String = "[${MainActivity.loggerTag}]SkillList"

    private val skillDatabase: SkillDatabase = SkillDatabase(game)
    private var entries: Map<String, SkillListEntry> = generateSkillListEntries()
    var skillPoints: Int = 0
        private set

    /**
     * Detects and handles any dialog popups.
     *
     * To prevent the bot moving too fast, we add a 500ms delay to the
     * exit of this function whenever we close the dialog.
     * This gives the dialog time to close since there is a very short
     * animation that plays when a dialog closes.
     *
     * @return A pair of a boolean and a nullable DialogInterface.
     * The boolean is true when a dialog has been handled by this function.
     * The DialogInterface is the detected dialog, or NULL if no dialogs were found.
     */
    private fun handleDialogs(): Pair<Boolean, DialogInterface?> {
        val dialog: DialogInterface? = DialogUtils.getDialog(game.imageUtils)
        if (dialog == null) {
            return Pair(false, null)
        }

        when (dialog.name) {
            "skill_list_confirmation" -> {
                dialog.ok(game.imageUtils)
                // This dialog takes longer to close than others.
                // Add an extra delay to make sure we don't skip anything.
                game.wait(1.0)
            }
            "skill_list_confirm_exit" -> dialog.ok(game.imageUtils)
            "skills_learned" -> dialog.close(game.imageUtils)
            "umamusume_details" -> {
                game.trainee.updateAptitudes(game.imageUtils)
                dialog.close(game.imageUtils)
            }
            else -> {
                return Pair(false, dialog)
            }
        }

        game.wait(0.5, skipWaitingForLoading = true)
        return Pair(true, dialog)
    }

    /** Creates a mapping of skill names to SkillListEntry objects.
     *
     * This function uses the skill database to populate our `entries` object.
     * While building the `entries` object, we also make sure to preserve the
     * structure of skill upgrade chains by linking them together when
     * instantiating the SkillListEntry object (`prev` parameter).
     *
     * @return A mapping of skill names to SkillListEntry objects.
     */
    private fun generateSkillListEntries(): Map<String, SkillListEntry> {
        // Get list of unique upgrade chains.
        val upgradeChains: List<List<String>> = skillDatabase.skillUpgradeChains
            .values.toList()
            .toSet()
            .toList()

        val result: MutableMap<String, SkillListEntry> = mutableMapOf()
        
        for (chain in upgradeChains) {
            var prevEntry: SkillListEntry? = null
            for (name in chain) {
                if (name in result) {
                    continue
                }
                val skillData: SkillData? = skillDatabase.getSkillData(name)
                if (skillData == null) {
                    MessageLog.e(TAG, "Failed to get skill data for \"$name\".")
                    continue
                }
                // Because we're building this mapping before we've scanned
                // the skill list, all entries are virtual.
                val entry = SkillListEntry(game, skillData, bIsVirtual = true, prev = prevEntry)

                // Now add to our mapping.
                result[name] = entry

                prevEntry = entry
            }
        }
        return result
    }

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
            (bbox.y + (bbox.h * 1000)).toFloat(),
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

    /** Confirms skill purchases and backs out of the Skill List screen. */
    fun confirmAndExit() {
        ButtonConfirm.click(game.imageUtils)
        game.wait(0.5, skipWaitingForLoading = true)
        // Two dialogs will appear if we purchase any skills.
        // First is the purchase confirmation.
        handleDialogs()
        // Second is the Skills Learned dialog.
        handleDialogs()
        // Return to previous screen.
        ButtonBack.click(game.imageUtils)
    }

    /** Aborts spending skill points and backs out of the Skill List screen. */
    fun cancelAndExit() {
        // Reset skills to prevent popup.
        ButtonReset.click(game.imageUtils)
        ButtonBack.click(game.imageUtils)
        game.wait(0.5, skipWaitingForLoading = true)
        // As a failsafe, handle dialogs to catch the dialog for
        // aborting spending skill points.
        handleDialogs()
    }

    /** Opens the stats dialog and parses it.
     *
     * This allows our dialog handler to update aptitudes for the trainee.
     * This is useful for when we start the bot at the skills list or at
     * the end of a career when aptitudes are unknown.
     *
     * Evaluating skills relies on these aptitudes being known since many
     * skills are dependent on things such as running style or track distance.
     */
    fun checkStats() {
        ButtonSkillListFullStats.click(game.imageUtils)
        game.wait(0.5, skipWaitingForLoading = true)
        handleDialogs()
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

        // Most negative skills have "Remove" in front of their skill
        // name in the title. The actual skill itself in the database does
        // not have this prefix. We need to get rid of this prefix as it
        // causes fuzzy matching to fail.
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
        var skillName: String? = null

        // TITLE
        Thread {
            try {
                // Extract the skill name from the bitmap.
                val tmpSkillName: String? = getSkillListEntryTitle(bitmap, debugString)
                if (tmpSkillName == null) {
                    Log.e(TAG, "[SKILLS] getSkillListEntryTitle() returned NULL.")
                    return@Thread
                }
                skillName = skillDatabase.checkSkillName(tmpSkillName, fuzzySearch = true)
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
            Log.e(TAG, "[ERROR] analyzeSkillListEntry: Parallel analysis timed out.")
        }

        if (skillName == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to parse skillName.")
            return null
        }

        if (skillPrice == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillListEntry: Failed to detect skillPrice.")
            return null
        }

        val entry: SkillListEntry? = entries[skillName]
        if (entry == null) {
            MessageLog.e(TAG, "analyzeSkillListEntry: Failed to find \"$skillName\" in entries.")
            return null
        }

        // Update the entry with our new data.
        entry.bIsObtained = bIsObtained
        entry.bIsVirtual = false
        // Very important we call this otherwise prices wont be accurate.
        entry.updateScreenPrice(skillPrice)

        return entry
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
    fun analyzeSkillListEntryThreadSafe(
        bitmap: Bitmap,
        bIsObtained: Boolean,
        debugString: String = "",
    ): SkillListEntry? {
        // Extract the skill name from the bitmap.
        var skillName: String? = getSkillListEntryTitle(bitmap, debugString)
        if (skillName == null) {
            MessageLog.e(TAG, "analyzeSkillListEntryThreadSafe: getSkillListEntryTitle() returned NULL.")
            return null
        }
        skillName = skillDatabase.checkSkillName(skillName, fuzzySearch = true)


        // If the skill is already obtained, don't bother trying to get the price.
        val skillPrice: Int? = if (bIsObtained) 0 else getSkillListEntryPrice(bitmap, debugString)
        if (skillPrice == null) {
            MessageLog.e(TAG, "analyzeSkillListEntryThreadSafe: getSkillListEntryPrice() returned NULL.")
            return null
        }

        val entry: SkillListEntry? = entries[skillName]
        if (entry == null) {
            MessageLog.e(TAG, "analyzeSkillListEntryThreadSafe: Failed to find \"$skillName\" in entries.")
            return null
        }

        // Update the entry with our new data.
        entry.bIsObtained = bIsObtained
        entry.bIsVirtual = false
        // Very important we call this otherwise prices wont be accurate.
        entry.updateScreenPrice(skillPrice)

        return entry
    }

    /** Parses the skill list entries currently visible on screen.
     *
     * @param bitmap The bitmap to use when analyzing the skill list.
     * If not specified, a screenshot will be taken and used instead.
     * @param bboxSkillList The bounding region for the skill list in the bitmap.
     * If not specified, this region will be detected automatically.
     * @param debugString A string used in logging and saved bitmap filenames.
     * @param onEntry A callback function that is called for each
     * SkillListEntry that we detect. This can be useful if we want to perform
     * an operation immediately upon detecting an entry.
     *
     * @return A mapping of skill names to SkillListEntry objects.
     */
    fun processSkillList(
        bitmap: Bitmap? = null,
        bboxSkillList: BoundingBox? = null,
        debugString: String = "",
        onEntry: OnEntryDetectedCallback? = null,
    ): List<String> {
        val bitmap = bitmap ?: game.imageUtils.getSourceBitmap()

        val bboxSkillList: BoundingBox? = bboxSkillList ?: getSkillListBoundingRegion(bitmap, debugString)
        if (bboxSkillList == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListBoundingRegion() returned NULL.")
            return emptyList()
        }

        val bboxSkillListEntries = getSkillListEntriesBoundingRegion(bitmap, bboxSkillList, debugString)
        if (bboxSkillListEntries == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListEntriesBoundingRegion() returned NULL.")
            return emptyList()
        }

        val bboxSkillUpRegion = getSkillListSkillUpBoundingRegion(bitmap, bboxSkillListEntries, debugString)
        if (bboxSkillUpRegion == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListSkillUpBoundingRegion() returned NULL.")
            return emptyList()
        }

        val bboxObtainedPillRegion = getSkillListObtainedPillBoundingRegion(bitmap, bboxSkillListEntries, debugString)
        if (bboxObtainedPillRegion == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListObtainedPillBoundingRegion() returned NULL.")
            return emptyList()
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
        val entryNames: MutableList<String> = mutableListOf()

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
                return emptyList()
            }
            if (game.debugMode) {
                game.imageUtils.saveBitmap(croppedSkillBox, filename = "bboxSkillBox_$index")
            }

            val bIsObtained: Boolean = pointType == "obtained"
            val entry: SkillListEntry? = analyzeSkillListEntry(croppedSkillBox, bIsObtained, "$index")
            if (entry == null) {
                MessageLog.e(TAG, "[SKILLS] analyzeSkillList: ($index) SkillListEntry is NULL.")
                continue
            }

            if (onEntry != null) {
                onEntry(this, entry, point)
            }

            entryNames.add(entry.name)
        }

        return entryNames.toList()
    }

    /** Parses the skill list entries currently visible on screen in a thread-safe manner.
     *
     * @param bitmap The bitmap to use when analyzing the skill list.
     * @param bboxSkillList The bounding region for the skill list in the bitmap.
     * @param debugString A string to append to logging messages and any
     * potential saved bitmaps.
     * @param onEntry A callback function that is called for each
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
        onEntry: OnEntryDetectedCallback? = null,
    ): List<String> {
        val bboxSkillListEntries = getSkillListEntriesBoundingRegion(bitmap, bboxSkillList, debugString)
        if (bboxSkillListEntries == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListEntriesBoundingRegion() returned NULL.")
            return emptyList()
        }

        val bboxSkillUpRegion = getSkillListSkillUpBoundingRegion(bitmap, bboxSkillListEntries, debugString)
        if (bboxSkillUpRegion == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListSkillUpBoundingRegion() returned NULL.")
            return emptyList()
        }

        val bboxObtainedPillRegion = getSkillListObtainedPillBoundingRegion(bitmap, bboxSkillListEntries, debugString)
        if (bboxObtainedPillRegion == null) {
            MessageLog.e(TAG, "[SKILLS] analyzeSkillList: getSkillListObtainedPillBoundingRegion() returned NULL.")
            return emptyList()
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
        val entryNames: MutableList<String> = mutableListOf()

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
                return emptyList()
            }
            if (game.debugMode) {
                game.imageUtils.saveBitmap(croppedSkillBox, filename = "bboxSkillBox_$index")
            }

            val bIsObtained: Boolean = pointType == "obtained"
            val entry: SkillListEntry? = analyzeSkillListEntryThreadSafe(croppedSkillBox, bIsObtained, "$index")
            if (entry == null) {
                MessageLog.e(TAG, "[SKILLS] analyzeSkillList: ($index) SkillListEntry is NULL.")
                continue
            }

            if (onEntry != null) {
                onEntry(this, entry, point)
            }

            entryNames.add(entry.name)
        }

        return entryNames.toList()
    }

    /** Scrolls through the entire skill list and extracts info from entries.
     *
     * The entries are stored in the class state as they are read.
     *
     * @param onEntry A callback function that is called for each
     * SkillListEntry that we detect. This can be useful if we want to perform
     * an operation immediately upon detecting an entry.
     */
    fun iterateOverSkillList(
        onEntry: OnEntryDetectedCallback? = null,
    ) {
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
            if (prevScrollBarBitmap != null && scrollBarBitmap.sameAs(prevScrollBarBitmap)) {
                break
            }

            prevScrollBarBitmap = scrollBarBitmap

            val processedNames: List<String> = processSkillList(
                bitmap = bitmap,
                bboxSkillList = bboxSkillList,
                debugString = "iterateOverSkillList",
                onEntry = onEntry,
            )
            // Another exit condition. If there are no new entries after
            // scrolling, then we're at the bottom of the list.
            if (prevNames == processedNames.toSet()) {
                break
            }
            prevNames = processedNames.toSet()

            scrollDown(bboxSkillList)
            // Slight delay to allow screen to settle before next loop.
            game.wait(0.5, skipWaitingForLoading = true)
        }
    }

    /** Gets all entries in the skill list.
     *
     * @param onEntry A callback function that is called for each
     * SkillListEntry that we detect. This can be useful if we want to perform
     * an operation immediately upon detecting an entry.
     *
     * @return A mapping of skill names to SkillListEntry objects.
     */
    fun parseSkillListEntries(
        bUseMockData: Boolean = false,
        onEntry: OnEntryDetectedCallback? = null,
    ): Map<String, SkillListEntry> {
        if (bUseMockData) {
            return parseMockSkillListEntries()
        }

        iterateOverSkillList() { _, entry: SkillListEntry, point: Point ->
            // Bubble the event up.
            if (onEntry != null) {
                onEntry(this, entry, point)
            }
        }

        return entries
    }

    /** Gets skill list entries using mocked data.
     *
     * NOTE: This is just useful for debugging purposes.
     * The data was taken from a real run so these values are all valid.
     *
     * @return A mapping of skill names to SkillListEntry objects.
     */
    fun parseMockSkillListEntries(): Map<String, SkillListEntry> {
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

        // Fix skill names in case any have been typed incorrectly.
        val fixedSkills: MutableMap<String, Int> = mutableMapOf()
        for ((name, price) in mockSkills) {
            val fixedName: String? = skillDatabase.checkSkillName(name, fuzzySearch = true)
            if (fixedName == null) {
                MessageLog.e(TAG, "parseMockSkillListEntries: \"$name\" not in database.")
                return emptyMap()
            }
            // In case of error, we don't want to give back partial data so
            // we just immediately return an empty list.
            val entry: SkillListEntry? = entries[fixedName]
            if (entry == null) {
                MessageLog.e(TAG, "parseMockSkillListEntries: \"$name\" not in entries.")
                return emptyMap()
            }
            fixedSkills[fixedName] = price
        }

        val result: MutableMap<String, SkillListEntry> = mutableMapOf()
        for ((name, price) in fixedSkills) {
            val entry: SkillListEntry? = entries[name]
            if (entry == null) {
                MessageLog.e(TAG, "parseMockSkillListEntries: \"$name\" not in entries.")
                return emptyMap()
            }
            // Manually update entries with data.
            entry.bIsObtained = price <= 0
            entry.bIsVirtual = false
            // Very important we call this otherwise prices wont be accurate.
            entry.updateScreenPrice(price)
            result[name] = entry
        }

        return result.toMap()
    }

    /** Checks whether we are at a skill list screen.
     *
     * @param bitmap Optional bitmap to use when detecting whether we are at
     * the skill list screen. If not specified, a screenshot will be taken
     * and used instead.
     *
     * @return Whether we are at a skill list screen.
     */
    fun checkSkillListScreen(bitmap: Bitmap? = null): Boolean {
        val bitmap: Bitmap = bitmap ?: game.imageUtils.getSourceBitmap()
        if (
            ButtonSkillListFullStats.check(game.imageUtils, sourceBitmap = bitmap) &&
            LabelSkillListScreenSkillPoints.check(game.imageUtils, sourceBitmap = bitmap)
        ) {
            return true
        }

        // Try to handle any skill list specific dialogs that may be up.
        if (!handleDialogs().first) {
            return false
        }

        // Now we can check again if we're at the skill list.
        return (
            ButtonSkillListFullStats.check(game.imageUtils) &&
            LabelSkillListScreenSkillPoints.check(game.imageUtils)
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

    /** Prints all skill list entries to the MessageLog.
     *
     * @param skillListEntries Optional mapping to use when printing.
     * If not specified, then this class instance's `entries` map is used instead.
     * @param verbose Whether to print extra entry information.
     */
    fun printSkillListEntries(
        skillListEntries: Map<String, SkillListEntry>? = null,
        verbose: Boolean = false,
    ) {
        val skillListEntries: Map<String, SkillListEntry> = skillListEntries ?: getAvailableSkills()
        MessageLog.v(TAG, "================= Skill List Entries =================")
        for ((name, entry) in skillListEntries) {
            val entryString: String = if (verbose) {
                "${entry}"
            } else {
                val extraString: String = if (entry.bIsVirtual) " (virtual)" else ""
                "${entry.price}${extraString}"
            }
            MessageLog.v(TAG, "\t${name}: ${entryString}")
        }
        MessageLog.v(TAG, "======================================================")
    }

    /** Purchases a skill.
     *
     * @param name The name of the skill to purchase.
     * @param skillUpButtonLocation The screen location of the SkillUpButton.
     */
    fun buySkill(name: String, skillUpButtonLocation: Point): SkillListEntry? {
        val entry: SkillListEntry? = entries[name]
        if (entry == null) {
            MessageLog.w(TAG, "buySkill: \"$name\" not found.")
            return null
        }

        if (entry.screenPrice > skillPoints) {
            MessageLog.w(TAG, "buySkill: Not enough skill points (${skillPoints}pt) to buy \"$name\" (${entry.screenPrice}pt).")
            return null
        }

        entry.buy(skillUpButtonLocation)
        skillPoints -= entry.screenPrice

        return entry
    }

    /** Returns all skill list entries.
     *
     * NOTE: This returns ALL entries, including ones that do not exist
     * in the actual skill list.
     */
    fun getAllSkills(): Map<String, SkillListEntry> {
        return entries
    }

    /** Returns skills that actually exist in the skill list (not virtual). */
    fun getAvailableSkills(): Map<String, SkillListEntry> {
        return entries.filterValues { it.bIsAvailable }
    }

    /** Returns skills that do not exist in the skill list.  */
    fun getVirtualSkills(): Map<String, SkillListEntry> {
        return getUnobtainedSkills().filterValues { it.bIsVirtual }
    }

    /** Returns all skills that not been purchased.
     *
     * @param includeVirtual Whether to include virtual skills in the result.
     * By default, only available skills will be included.
     */
    fun getUnobtainedSkills(includeVirtual: Boolean = false): Map<String, SkillListEntry> {
        val src: Map<String, SkillListEntry> = if (includeVirtual) getAllSkills() else getAvailableSkills()
        return src.filterValues { !it.bIsObtained }
    }

    /** Returns all negative skills (purple skills).
     *
     * @param includeVirtual Whether to include virtual skills in the result.
     * By default, only available skills will be included.
     */
    fun getNegativeSkills(includeVirtual: Boolean = false): Map<String, SkillListEntry> {
        val src: Map<String, SkillListEntry> = if (includeVirtual) getAllSkills() else getAvailableSkills()
        return src.filterValues { it.bIsNegative }
    }

    /** Returns all inherited unique skills.
     *
     * @param includeVirtual Whether to include virtual skills in the result.
     * By default, only available skills will be included.
     */
    fun getInheritedUniqueSkills(includeVirtual: Boolean = false): Map<String, SkillListEntry> {
        val src: Map<String, SkillListEntry> = if (includeVirtual) getAllSkills() else getAvailableSkills()
        return src.filterValues { it.bIsInheritedUnique }
    }

    /** Returns all skills that are not affected by a trainee's aptitudes.
     *
     * These skills are dependent on a running style, track distance, or track surface.
     * For example, the skill `Front Runner Savvy ○` specifies in its description
     * that it is for "(Front Runner)".
     *
     * This function also filters by skills whose running style is inferred and not
     * explicitly stated. See `getInferredRunningStyleSkills` for more details.
     *
     * @param runningStyle The optional RunningStyle to use when filtering the
     * inferred running styles. If not specified, then skills with ANY
     * inferred running styles are included in the results.
     * @param includeVirtual Whether to include virtual skills in the result.
     * By default, only available skills will be included.
     */
    fun getAptitudeIndependentSkills(
        runningStyle: RunningStyle? = null,
        includeVirtual: Boolean = false,
    ): Map<String, SkillListEntry> {
        val src: Map<String, SkillListEntry> = if (includeVirtual) getAllSkills() else getAvailableSkills()
        val inferredRunningStyleSkills: Map<String, SkillListEntry> = getInferredRunningStyleSkills(runningStyle, includeVirtual)
        return src.filterValues {
            it.runningStyle == null &&
            it.trackDistance == null &&
            it.trackSurface == null &&
            it.name !in inferredRunningStyleSkills
        }
    }

    /** Returns all skills for a RunningStyle.
     *
     * @param runningStyle The optional RunningStyle to use when filtering.
     * If not specified, then skills with ANY running style will be returned.
     * @param includeVirtual Whether to include virtual skills in the result.
     * By default, only available skills will be included.
     */
    fun getRunningStyleSkills(
        runningStyle: RunningStyle? = null,
        includeVirtual: Boolean = false,
    ): Map<String, SkillListEntry> {
        val src: Map<String, SkillListEntry> = if (includeVirtual) getAllSkills() else getAvailableSkills()
        // If null, then we want to return all skills that have any running style.
        if (runningStyle == null) {
            return src.filterValues { it.runningStyle != null }
        }
        return src.filterValues { it.runningStyle == runningStyle }
    }

    /** Returns all skills for a TrackDistance.
     *
     * @param trackDistance The optional TrackDistance to use when filtering.
     * If not specified, then skills with ANY track distance will be returned.
     * @param includeVirtual Whether to include virtual skills in the result.
     * By default, only available skills will be included.
     */
    fun getTrackDistanceSkills(
        trackDistance: TrackDistance? = null,
        includeVirtual: Boolean = false,
    ): Map<String, SkillListEntry> {
        val src: Map<String, SkillListEntry> = if (includeVirtual) getAllSkills() else getAvailableSkills()
        // If null, then we want to return all skills that have any track distance.
        if (trackDistance == null) {
            return src.filterValues { it.trackDistance != null }
        }
        return src.filterValues { it.trackDistance == trackDistance }
    }

    /** Returns all skills for a TrackSurface.
     *
     * @param trackSurface The optional TrackSurface to use when filtering.
     * If not specified, then skills with ANY track surface will be returned.
     * @param includeVirtual Whether to include virtual skills in the result.
     * By default, only available skills will be included.
     */
    fun getTrackSurfaceSkills(
        trackSurface: TrackSurface? = null,
        includeVirtual: Boolean = false,
    ): Map<String, SkillListEntry> {
        val src: Map<String, SkillListEntry> = if (includeVirtual) getAllSkills() else getAvailableSkills()
        // If null, then we want to return all skills that have any track surface.
        if (trackSurface == null) {
            return src.filterValues { it.trackSurface != null }
        }
        return src.filterValues { it.trackSurface == trackSurface }
    }

    /** Returns all skills that have an inferred RunningStyle.
     *
     * Unlike some skills which can only activate if a specific RunningStyle
     * is selected, inferred running style skills are skills which can be
     * activated when using ANY RunningStyle but require the trainee to be
     * in a specific positioning during the race. We refer to these kinds of
     * skills as "inferred running style skills". ...or at least I do.
     *
     * For example, some skills only activate if the trainee is in the lead or
     * well-positioned. These skills are typically only useful for specific
     * running styles however they technically CAN be activated by any style
     * under hyper-specific circumstances. We don't want to rely on ultra rare cases
     * so we only include inferred styles that match the passed runningStyle.
     *
     * @param runningStyle The optional RunningStyle to use when filtering.
     * If not specified, then skills with ANY running style will be returned.
     * @param includeVirtual Whether to include virtual skills in the result.
     * By default, only available skills will be included.
     */
    fun getInferredRunningStyleSkills(
        runningStyle: RunningStyle? = null,
        includeVirtual: Boolean = false,
    ): Map<String, SkillListEntry> {
        val src: Map<String, SkillListEntry> = if (includeVirtual) getAllSkills() else getAvailableSkills()

        // Get normal running style skills so we can filter them out later.
        val runningStyleSkills: Map<String, SkillListEntry> = getRunningStyleSkills(runningStyle, includeVirtual)

        // If null, then we want to return all skills that have any inferred running style.
        if (runningStyle == null) {
            return src
                .filterValues { it.inferredRunningStyles.isNotEmpty() }
                .filterKeys { it !in runningStyleSkills }
        }
        return src
            .filterValues { runningStyle in it.inferredRunningStyles }
            .filterKeys { it !in runningStyleSkills }
    }

    /** Gets all available skills their virtual upgrades.
     *
     * This effectively only adds virtual skills that have an in-place
     * downgrade in the available skill results.
     */
    fun getAvailableSkillsWithVirtualUpgrades(): Map<String, SkillListEntry> {
        val result: Map<String, SkillListEntry> = getAvailableSkills().toMap()
        val entriesToAdd: MutableMap<String, SkillListEntry> = mutableMapOf()
        for ((name, entry) in result) {
            val upgrades: List<SkillListEntry> = entry.getUpgrades()
            for (upgrade in upgrades) {
                entriesToAdd[upgrade.name] = upgrade
            }
        }
        return result + entriesToAdd.toMap()
    }

    /** Returns a SkillListEntry for a given skill name.
     *
     * @param name The name to look up.
     *
     * @return If the `name` is found, its SkillListEntry is returned. Otherwise, NULL.
     */
    fun getEntry(name: String): SkillListEntry? {
        val result: SkillListEntry? = entries[name]
        if (result == null) {
            MessageLog.w(TAG, "getEntry: No entry found for \"$name\".")
        }
        return result
    }
}
