package com.steve1316.uma_android_automation.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.ImageUtils
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Game
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.lang.Integer.max
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import com.steve1316.automation_library.data.SharedData
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.sqrt
import kotlin.text.replace


/**
 * Utility functions for image processing via CV like OpenCV.
 */
class CustomImageUtils(context: Context, private val game: Game) : ImageUtils(context) {
	private val TAG: String = "[${MainActivity.loggerTag}]CustomImageUtils"

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// SQLite Settings
	private val threshold: Int = SettingsHelper.getIntSetting("ocr", "ocrThreshold")
	override var debugMode: Boolean = SettingsHelper.getBooleanSetting("debug", "enableDebugMode")
	override var confidence: Double = SettingsHelper.getStringSetting("debug", "templateMatchConfidence").toDouble()
	override var customScale: Double = SettingsHelper.getStringSetting("debug", "templateMatchCustomScale").toDouble()

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	data class RaceDetails (
		val fans: Int,
		val hasDoublePredictions: Boolean
	)

	data class StatGainRowConfig(
		val startX: Int,
		val startY: Int,
		val width: Int,
		val height: Int,
		val rowName: String
	)

	data class BarFillResult(
		val fillPercent: Double,
		val filledSegments: Int,
		val dominantColor: String
	)

	data class SpiritGaugeResult(
		val numGaugesCanFill: Int,
		val numGaugesReadyToBurst: Int
	)

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	init {
		initTesseract("eng.traineddata")
		SharedData.templateSubfolderPathName = "images/"
	}

    public override fun getSourceBitmap(): Bitmap {
        return super.getSourceBitmap()
    }

	/**
	 * Find all occurrences of the specified image in the images folder using a provided source bitmap. Useful for parallel processing to avoid exceeding the maxImages buffer.
	 *
	 * @param templateName File name of the template image.
	 * @param sourceBitmap The source bitmap to search in.
	 * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
	 * @param customConfidence Override the default confidence threshold for template matching. Defaults to 0.0 which uses the default confidence.
	 * @return An ArrayList of Point objects containing all the occurrences of the specified image or null if not found.
	 */
	private fun findAllWithBitmap(templateName: String, sourceBitmap: Bitmap, region: IntArray = intArrayOf(0, 0, 0, 0), customConfidence: Double = 0.0): ArrayList<Point> {
		var templateBitmap: Bitmap?
		context.assets?.open("images/$templateName.png").use { inputStream ->
			templateBitmap = BitmapFactory.decodeStream(inputStream)
		}

		if (templateBitmap != null) {
			val matchLocations = matchAll(sourceBitmap, templateBitmap, region = region, customConfidence = customConfidence)
			
			// Sort the match locations by ascending x and y coordinates.
			matchLocations.sortBy { it.x }
			matchLocations.sortBy { it.y }

			if (debugMode) {
				MessageLog.d(TAG, "Found match locations for $templateName: $matchLocations.")
			} else {
				Log.d(TAG, "[DEBUG] Found match locations for $templateName: $matchLocations.")
			}

			return matchLocations
		}
		
		return arrayListOf()
	}

	/**
	 * Find a single occurrence of the specified image in the images folder using a provided source bitmap. Useful for parallel processing to avoid exceeding the maxImages buffer.
	 *
	 * @param templateName File name of the template image.
	 * @param sourceBitmap The source bitmap to search in.
	 * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
	 * @param customConfidence Set a custom confidence for the template matching. Defaults to 0.0 which will use the confidence set in the app.
     * @param suppressError Whether to suppress error logging if image is not found. Defaults to false.
	 * @return A Point object containing the location of the first occurrence, or null if not found.
	 */
	fun findImageWithBitmap(templateName: String, sourceBitmap: Bitmap, region: IntArray = intArrayOf(0, 0, 0, 0), customConfidence: Double = 0.0, suppressError: Boolean = false): Point? {
		var templateBitmap: Bitmap?
		context.assets?.open("images/$templateName.png").use { inputStream ->
			templateBitmap = BitmapFactory.decodeStream(inputStream)
		}

		if (templateBitmap != null) {
            val matchLocation = match(sourceBitmap, templateBitmap, templateName, region = region, customConfidence = customConfidence).second
            if (matchLocation == null && !suppressError) {
                if (debugMode) MessageLog.d(TAG, "Could not find $templateName in the provided source bitmap.")
                else Log.d(TAG, "[DEBUG] Could not find $templateName in the provided source bitmap.")
            }
            return matchLocation
		}
		return null
	}

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////

	/**
	 * Perform OCR text detection on the training event title using Tesseract along with some image manipulation via thresholding to make the cropped screenshot black and white using OpenCV.
	 *
	 * @param increment Increments the threshold by this value. Defaults to 0.0.
	 * @return The detected event title in the cropped region.
	 */
	fun findEventTitle(increment: Double = 0.0): String {
		val (sourceBitmap, templateBitmap) = getBitmaps("shift")

		// Acquire the location of the energy text image.
		val (_, energyTemplateBitmap) = getBitmaps("energy")
		val (_, matchLocation) = match(sourceBitmap, energyTemplateBitmap!!, "energy")
		if (matchLocation == null) {
			MessageLog.w(TAG, "Could not proceed with OCR text detection due to not being able to find the energy template on the source image.")
			return "empty!"
		}

		// Use the match location acquired from finding the energy text image and acquire the (x, y) coordinates of the event title container right below the location of the energy text image.
		val newX: Int
		val newY: Int
		var croppedBitmap: Bitmap? = if (isTablet) {
			newX = max(0, matchLocation.x.toInt() - relWidth(250))
			newY = max(0, matchLocation.y.toInt() + relHeight(154))
			createSafeBitmap(sourceBitmap, newX, newY, relWidth(746), relHeight(85), "findEventTitle tablet crop")
		} else {
			newX = max(0, matchLocation.x.toInt() - relWidth(125))
			newY = max(0, matchLocation.y.toInt() + relHeight(116))
			createSafeBitmap(sourceBitmap, newX, newY, relWidth(645), relHeight(65), "findEventTitle phone crop")
		}
		if (croppedBitmap == null) {
			MessageLog.e(TAG, "Failed to create cropped bitmap for text detection")
			return "empty!"
		}

		val tempImage = Mat()
		Utils.bitmapToMat(croppedBitmap, tempImage)
		if (debugMode) Imgcodecs.imwrite("$matchFilePath/debugEventTitleText.png", tempImage)

		// Now see if it is necessary to shift the cropped region over by 70 pixels or not to account for certain events.
		val (shiftMatch, _) = match(croppedBitmap, templateBitmap!!, "shift")
		croppedBitmap = if (shiftMatch) {
			Log.d(TAG, "Shifting the region over by 70 pixels!")
			createSafeBitmap(sourceBitmap, relX(newX.toDouble(), 70), newY, 645 - 70, 65, "findEventTitle shifted crop") ?: croppedBitmap
		} else {
			Log.d(TAG, "Do not need to shift.")
			croppedBitmap
		}

		// Make the cropped screenshot grayscale.
		val cvImage = Mat()
		Utils.bitmapToMat(croppedBitmap, cvImage)
		Imgproc.cvtColor(cvImage, cvImage, Imgproc.COLOR_BGR2GRAY)

		// Save the cropped image before converting it to black and white in order to troubleshoot issues related to differing device sizes and cropping.
		if (debugMode) Imgcodecs.imwrite("$matchFilePath/debugEventTitleText_afterCrop.png", cvImage)

		// Thresh the grayscale cropped image to make it black and white.
		val bwImage = Mat()
		Imgproc.threshold(cvImage, bwImage, threshold.toDouble() + increment, 255.0, Imgproc.THRESH_BINARY)
		if (debugMode) Imgcodecs.imwrite("$matchFilePath/debugEventTitleText_afterThreshold.png", bwImage)

		// Convert the Mat directly to Bitmap and then pass it to the text reader.
		val resultBitmap = createBitmap(bwImage.cols(), bwImage.rows())
		Utils.matToBitmap(bwImage, resultBitmap)
		tessBaseAPI.setImage(resultBitmap)

		var result = "empty!"
		try {
			// Finally, detect text on the cropped region.
			result = tessBaseAPI.utF8Text
			MessageLog.i(TAG, "Detected text with Tesseract: $result")
		} catch (e: Exception) {
			MessageLog.e(TAG, "Cannot perform OCR: ${e.stackTraceToString()}")
		}

		tessBaseAPI.clear()
		tempImage.release()
		cvImage.release()
		bwImage.release()

		return result
	}

	/**
	 * Find the success percentage chance on the currently selected stat. Parameters are optional to allow for thread-safe operations.
	 *
	 * @param sourceBitmap Bitmap of the source image separately taken. Defaults to null.
	 * @param trainingSelectionLocation Point location of the template image separately taken. Defaults to null.
	 *
	 * @return Integer representing the percentage.
	 */
	fun findTrainingFailureChance(sourceBitmap: Bitmap? = null, trainingSelectionLocation: Point? = null): Int {
		// Crop the source screenshot to hold the success percentage only.
		val (trainingSelectionLocation, sourceBitmap) = if (sourceBitmap == null && trainingSelectionLocation == null) {
			findImage("training_failure_chance")
		} else {
			Pair(trainingSelectionLocation, sourceBitmap)
		}

		if (trainingSelectionLocation == null) {
			return -1
		}

		// Determine crop region based on device type.
		val (offsetX, offsetY, width, height) = if (isTablet) {
			listOf(-65, 23, relWidth(130), relHeight(50))
		} else {
			listOf(-45, 15, relWidth(100), relHeight(37))
		}

		// Perform OCR with 2x scaling and no thresholding.
		val detectedText = performOCROnRegion(
			sourceBitmap!!,
			relX(trainingSelectionLocation.x, offsetX),
			relY(trainingSelectionLocation.y, offsetY),
			width,
			height,
			useThreshold = false,
			useGrayscale = true,
			scale = 2.0,
			ocrEngine = "mlkit",
			debugName = "TrainingFailureChance"
		)

		// Parse the result.
		val result = try {
			val cleanedResult = detectedText.replace("%", "").replace(Regex("[^0-9]"), "").trim()
			cleanedResult.toInt()
		} catch (_: NumberFormatException) {
			MessageLog.e(TAG, "Could not convert \"$detectedText\" to integer for training failure chance.")
			-1
		}

		if (debugMode) {
			MessageLog.d(TAG, "Failure chance detected to be at $result%.")
		} else {
			Log.d(TAG, "Failure chance detected to be at $result%.")
		}

		return result
	}

	/**
	 * Determines the turn number of the "X turn(s) left" text at the top left corner of the screen.
	 *
	 * @return The remaining turn number.
	 */
	fun determineTurnsRemainingBeforeNextGoal(): Int {
		val (energyTextLocation, sourceBitmap) = findImage("energy", tries = 1, region = regionTopHalf)

		if (energyTextLocation != null) {
			// Determine crop region based on campaign and device type.
			val (offsetX, offsetY, width, height) = if (game.campaign == "Unity Cup") {
				if (isTablet) {
					listOf(-(260 * 1.32).toInt(), -(140 * 1.32).toInt(), relWidth(135), relHeight(100))
				} else {
					listOf(-260, -137, relWidth(100), relHeight(80))
				}
			} else {
				if (isTablet) {
					listOf(-(246 * 1.32).toInt(), -(96 * 1.32).toInt(), relWidth(175), relHeight(116))
				} else {
					listOf(-246, -100, relWidth(140), relHeight(95))
				}
			}

			// Perform OCR with 2x scaling.
			val detectedText = performOCROnRegion(
				sourceBitmap,
				relX(energyTextLocation.x, offsetX),
				relY(energyTextLocation.y, offsetY),
				width,
				height,
				useThreshold = true,
				useGrayscale = true,
				scale = 2.0,
				ocrEngine = "mlkit",
				debugName = "DayForExtraRace"
			)

			// Parse the result.
			val result = try {
				val cleanedResult = detectedText.replace(Regex("[^0-9]"), "")
				MessageLog.i(TAG, "Detected day for extra racing: $detectedText")
				cleanedResult.toInt()
			} catch (_: NumberFormatException) {
				MessageLog.e(TAG, "Could not convert \"$detectedText\" to integer for the turns remaining.")
				-1
			}

			return result
		}

		return -1
	}

	/**
	 * Extract the race name from the extra race selection screen using OCR.
	 *
	 * @param extraRaceLocation Point object of the extra race's location.
	 * @return The race name as detected by OCR, or empty string if not found.
	 */
	fun extractRaceName(extraRaceLocation: Point): String {
		try {
			val detectedText = performOCRFromReference(
				referencePoint = extraRaceLocation,
				offsetX = -455,
				offsetY = -105,
				width = relWidth(585),
				height = relHeight(45),
				useThreshold = true,
				useGrayscale = true,
				scale = 2.0,
				ocrEngine = "mlkit",
				debugName = "extractRaceName"
			)
			
			MessageLog.i(TAG, "Extracted race name: \"$detectedText\"")
			return detectedText
		} catch (e: Exception) {
			MessageLog.e(TAG, "Exception during race name extraction: ${e.message}")
			return ""
		}
	}

	/**
	 * Determine the amount of fans that the extra race will give only if it matches the double star prediction.
	 *
	 * @param extraRaceLocation Point object of the extra race's location.
	 * @param sourceBitmap Bitmap of the source screenshot.
	 * @param doubleStarPredictionBitmap Bitmap of the double star prediction template image.
	 * @param forceRacing Flag to allow the extra race to forcibly pass double star prediction check. Defaults to false.
	 * @return Number of fans to be gained from the extra race or -1 if not found as an object.
	 */
	fun determineExtraRaceFans(extraRaceLocation: Point, sourceBitmap: Bitmap, doubleStarPredictionBitmap: Bitmap, forceRacing: Boolean = false): RaceDetails {
		// Crop the source screenshot to show only the fan amount and the predictions.
		val croppedBitmap = if (isTablet) {
			createSafeBitmap(sourceBitmap, relX(extraRaceLocation.x, -(173 * 1.34).toInt()), relY(extraRaceLocation.y, -(106 * 1.34).toInt()), relWidth(220), relHeight(125), "determineExtraRaceFans prediction tablet")
		} else {
			createSafeBitmap(sourceBitmap, relX(extraRaceLocation.x, -173), relY(extraRaceLocation.y, -106), relWidth(163), relHeight(96), "determineExtraRaceFans prediction phone")
		}
		if (croppedBitmap == null) {
			MessageLog.e(TAG, "Failed to create cropped bitmap for extra race prediction detection.")
			return RaceDetails(-1, false)
		}

		val cvImage = Mat()
		Utils.bitmapToMat(croppedBitmap, cvImage)
		if (debugMode) Imgcodecs.imwrite("$matchFilePath/debugExtraRacePrediction.png", cvImage)

		// Determine if the extra race has double star prediction.
		val (predictionCheck, _) = match(croppedBitmap, doubleStarPredictionBitmap, "race_extra_double_prediction")

		return if (forceRacing || predictionCheck) {
			if (debugMode && !forceRacing) MessageLog.d(TAG, "This race has double predictions. Now checking how many fans this race gives.")
			else if (debugMode) MessageLog.d(TAG, "Check for double predictions was skipped due to the force racing flag being enabled. Now checking how many fans this race gives.")

			// Crop the source screenshot to show only the fans.
			val croppedBitmap2 = if (isTablet) {
				createSafeBitmap(sourceBitmap, relX(extraRaceLocation.x, -(625 * 1.40).toInt()), relY(extraRaceLocation.y, -(75 * 1.34).toInt()), relWidth(320), relHeight(45), "determineExtraRaceFans fans tablet")
			} else {
				createSafeBitmap(sourceBitmap, relX(extraRaceLocation.x, -625), relY(extraRaceLocation.y, -75), relWidth(250), relHeight(35), "determineExtraRaceFans fans phone")
			}
			if (croppedBitmap2 == null) {
				MessageLog.e(TAG, "Failed to create cropped bitmap for extra race fans detection.")
				return RaceDetails(-1, predictionCheck)
			}

			// Make the cropped screenshot grayscale.
			Utils.bitmapToMat(croppedBitmap2, cvImage)
			Imgproc.cvtColor(cvImage, cvImage, Imgproc.COLOR_BGR2GRAY)
			if (debugMode) Imgcodecs.imwrite("$matchFilePath/debugExtraRaceFans_afterCrop.png", cvImage)

			// Convert the Mat directly to Bitmap and then pass it to the text reader.
			var resultBitmap = createBitmap(cvImage.cols(), cvImage.rows())
			Utils.matToBitmap(cvImage, resultBitmap)

			// Thresh the grayscale cropped image to make it black and white.
			val bwImage = Mat()
			Imgproc.threshold(cvImage, bwImage, threshold.toDouble(), 255.0, Imgproc.THRESH_BINARY)
			if (debugMode) Imgcodecs.imwrite("$matchFilePath/debugExtraRaceFans_afterThreshold.png", bwImage)

			resultBitmap = createBitmap(bwImage.cols(), bwImage.rows())
			Utils.matToBitmap(bwImage, resultBitmap)
			tessDigitsBaseAPI.setImage(resultBitmap)

			var result = "empty!"
			try {
				// Finally, detect text on the cropped region.
				result = tessDigitsBaseAPI.utF8Text
			} catch (e: Exception) {
				MessageLog.e(TAG, "Cannot perform OCR with Tesseract: ${e.stackTraceToString()}")
			}

			tessDigitsBaseAPI.clear()
			cvImage.release()
			bwImage.release()

			// Format the string to be converted to an integer.
			MessageLog.i(TAG, "Detected number of fans from Tesseract before formatting: $result")
			result = result
				.replace(",", "")
				.replace(".", "")
				.replace("+", "")
				.replace("-", "")
				.replace(">", "")
				.replace("<", "")
				.replace("(", "")
				.replace("人", "")
				.replace("ォ", "")
				.replace("fans", "").trim()

			try {
				Log.d(TAG, "Converting $result to integer for fans")
				val cleanedResult = result.replace(Regex("[^0-9]"), "")
				RaceDetails(cleanedResult.toInt(), predictionCheck)
			} catch (_: NumberFormatException) {
				RaceDetails(-1, predictionCheck)
			}
		} else {
			Log.d(TAG, "This race has no double prediction.")
			return RaceDetails(-1, false)
		}
	}

	/**
	 * Determine the number of skill points.
	 *
	 * @return Number of skill points or -1 if not found.
	 */
	fun determineSkillPoints(): Int {
		val (skillPointLocation, sourceBitmap) = findImage("skill_points", tries = 1)

		return if (skillPointLocation != null) {
			// Determine crop region based on device type.
			val (offsetX, offsetY, width, height) = if (isTablet) {
				listOf(-75, 45, relWidth(150), relHeight(70))
			} else {
				listOf(-70, 28, relWidth(135), relHeight(70))
			}

			// Perform OCR with thresholding.
			val detectedText = performOCROnRegion(
				sourceBitmap,
				relX(skillPointLocation.x, offsetX),
				relY(skillPointLocation.y, offsetY),
				width,
				height,
				useThreshold = true,
				useGrayscale = true,
				scale = 1.0,
				ocrEngine = "mlkit",
				debugName = "SkillPoints"
			)

			// Parse the result.
			MessageLog.i(TAG, "Detected number of skill points before formatting: $detectedText")
			try {
				Log.d(TAG, "Converting $detectedText to integer for skill points")
				val cleanedResult = detectedText.replace(Regex("[^0-9]"), "")
				cleanedResult.toInt()
			} catch (_: NumberFormatException) {
				-1
			}
		} else {
			MessageLog.e(TAG, "Could not start the process of detecting skill points.")
			-1
		}
	}

	/**
	 * Analyze the relationship bars on the Training screen for the currently selected training. Parameter is optional to allow for thread-safe operations.
	 *
	 * @param sourceBitmap Bitmap of the source image separately taken. Defaults to null.
	 *
	 * @return A list of the results for each relationship bar.
	 */
	fun analyzeRelationshipBars(sourceBitmap: Bitmap? = null): ArrayList<BarFillResult> {
		val customRegion = intArrayOf(displayWidth - (displayWidth / 3), 0, (displayWidth / 3), displayHeight - (displayHeight / 3))

		// Take a single screenshot first to avoid buffer overflow.
		val sourceBitmap = sourceBitmap ?: getSourceBitmap()

		var allStatBlocks = mutableListOf<Point>()

		val latch = CountDownLatch(6)

		// Create arrays to store results from each thread.
		val speedBlocks = arrayListOf<Point>()
		val staminaBlocks = arrayListOf<Point>()
		val powerBlocks = arrayListOf<Point>()
		val gutsBlocks = arrayListOf<Point>()
		val witBlocks = arrayListOf<Point>()
		val friendshipBlocks = arrayListOf<Point>()

		// Start parallel threads for each findAll call, passing the same source bitmap.
		Thread {
			speedBlocks.addAll(findAllWithBitmap("stat_speed_block", sourceBitmap, region = customRegion))
			latch.countDown()
		}.start()

		Thread {
			staminaBlocks.addAll(findAllWithBitmap("stat_stamina_block", sourceBitmap, region = customRegion))
			latch.countDown()
		}.start()

		Thread {
			powerBlocks.addAll(findAllWithBitmap("stat_power_block", sourceBitmap, region = customRegion))
			latch.countDown()
		}.start()

		Thread {
			gutsBlocks.addAll(findAllWithBitmap("stat_guts_block", sourceBitmap, region = customRegion))
			latch.countDown()
		}.start()

		Thread {
			witBlocks.addAll(findAllWithBitmap("stat_wit_block", sourceBitmap, region = customRegion))
			latch.countDown()
		}.start()

		Thread {
			friendshipBlocks.addAll(findAllWithBitmap("stat_friendship_block", sourceBitmap, region = customRegion))
			latch.countDown()
		}.start()

		// Wait for all threads to complete.
		try {
			latch.await(10, TimeUnit.SECONDS)
		} catch (_: InterruptedException) {
			MessageLog.e(TAG, "Parallel findAll operations timed out.")
		}

		// Combine all results.
		allStatBlocks.addAll(speedBlocks)
		allStatBlocks.addAll(staminaBlocks)
		allStatBlocks.addAll(powerBlocks)
		allStatBlocks.addAll(gutsBlocks)
		allStatBlocks.addAll(witBlocks)
		allStatBlocks.addAll(friendshipBlocks)

		// Filter out duplicates based on exact coordinate matches.
		allStatBlocks = allStatBlocks.distinctBy { "${it.x},${it.y}" }.toMutableList()

		// Sort the combined stat blocks by ascending y-coordinate.
		allStatBlocks.sortBy { it.y }

		// Define HSV color ranges.
		val blueLower = Scalar(10.0, 150.0, 150.0)
		val blueUpper = Scalar(25.0, 255.0, 255.0)
		val greenLower = Scalar(40.0, 150.0, 150.0)
		val greenUpper = Scalar(80.0, 255.0, 255.0)
		val orangeLower = Scalar(100.0, 150.0, 150.0)
		val orangeUpper = Scalar(130.0, 255.0, 255.0)

		val (_, maxedTemplateBitmap) = getBitmaps("stat_maxed")
		val results = arrayListOf<BarFillResult>()

		for ((index, statBlock) in allStatBlocks.withIndex()) {
			if (debugMode) MessageLog.d(TAG, "Processing stat block #${index + 1} at position: (${statBlock.x}, ${statBlock.y})")

			val croppedBitmap = createSafeBitmap(sourceBitmap, relX(statBlock.x, -9), relY(statBlock.y, 107), 111, 13, "analyzeRelationshipBars stat block ${index + 1}")
			if (croppedBitmap == null) {
				MessageLog.e(TAG, "Failed to create cropped bitmap for stat block #${index + 1}.")
				continue
			}

			val (isMaxed, _) = match(croppedBitmap, maxedTemplateBitmap!!, "stat_maxed")
			if (isMaxed) {
				// Skip if the relationship bar is already maxed.
				if (debugMode) MessageLog.d(TAG, "Relationship bar #${index + 1} is full.")
				results.add(BarFillResult(100.0, 5, "orange"))
				continue
			}

			val barMat = Mat()
			Utils.bitmapToMat(croppedBitmap, barMat)

			// Convert to RGB and then to HSV for better color detection.
			val rgbMat = Mat()
			Imgproc.cvtColor(barMat, rgbMat, Imgproc.COLOR_BGR2RGB)
			if (debugMode) Imgcodecs.imwrite("$matchFilePath/debug_relationshipBar${index + 1}AfterRGB.png", rgbMat)
			val hsvMat = Mat()
			Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV)

			val blueMask = Mat()
			val greenMask = Mat()
			val orangeMask = Mat()

			// Count the pixels for each color.
			Core.inRange(hsvMat, blueLower, blueUpper, blueMask)
			Core.inRange(hsvMat, greenLower, greenUpper, greenMask)
			Core.inRange(hsvMat, orangeLower, orangeUpper, orangeMask)
			val bluePixels = Core.countNonZero(blueMask)
			val greenPixels = Core.countNonZero(greenMask)
			val orangePixels = Core.countNonZero(orangeMask)

			// Sum the colored pixels.
			val totalColoredPixels = bluePixels + greenPixels + orangePixels
			val totalPixels = barMat.rows() * barMat.cols()

			// Estimate the fill percentage based on the total colored pixels.
			val fillPercent = if (totalPixels > 0) {
				(totalColoredPixels.toDouble() / totalPixels.toDouble()) * 100.0
			} else 0.0

			// Estimate the filled segments (each segment is about 20% of the whole bar).
			val filledSegments = (fillPercent / 20).coerceAtMost(5.0).toInt()

			val dominantColor = when {
				orangePixels > greenPixels && orangePixels > bluePixels -> "orange"
				greenPixels > bluePixels -> "green"
				bluePixels > 0 -> "blue"
				else -> "none"
			}

			blueMask.release()
			greenMask.release()
			orangeMask.release()
			hsvMat.release()
			barMat.release()

			if (debugMode) MessageLog.d(TAG, "Relationship bar #${index + 1} is ${decimalFormat.format(fillPercent)}% filled with $filledSegments filled segments and the dominant color is $dominantColor")
			results.add(BarFillResult(fillPercent, filledSegments, dominantColor))
		}

		return results
	}

	/**
	 * Analyze the Spirit Explosion Gauges for the currently selected Unity Cup training. Parameter is optional to allow for thread-safe operations.
	 *
	 * A training can have multiple gauges with varying fill rates. This function analyzes all gauges and returns:
	 * - Number of gauges that can be filled (not at 100% yet) by executing this training.
	 * - Number of gauges that are ready to burst.
	 *
	 * @param sourceBitmap Bitmap of the source image separately taken. Defaults to null.
	 *
	 * @return A SpiritGaugeResult for the currently selected training, or null if no gauges found.
	 */
	fun analyzeSpiritExplosionGauges(sourceBitmap: Bitmap? = null): SpiritGaugeResult? {
		val customRegion = intArrayOf(displayWidth - (displayWidth / 3), 0, (displayWidth / 3), displayHeight - (displayHeight / 3))

		// Take a single screenshot first to avoid buffer overflow.
		val sourceBitmap = sourceBitmap ?: getSourceBitmap()

		// Find all Spirit Training icons (there may be multiple for the currently selected training).
		val spiritTrainingIcons = findAllWithBitmap("unitycup_spirit_training", sourceBitmap, region = customRegion, customConfidence = 0.90)
		if (spiritTrainingIcons.isEmpty()) {
			return null
		}

		// Find all Spirit Explosion icons to determine burst readiness.
		val spiritExplosionIcons = findAllWithBitmap("unitycup_spirit_explosion", sourceBitmap, region = customRegion, customConfidence = 0.90)

		// Analyze all gauges for all spirit training icons to count how many can be filled.
		var numGaugesCanFill = 0
		for (iconLocation in spiritTrainingIcons) {
			// Gauge is located to the left of the icon. Analyze the gauge region.
			// The gauge is gray inside (same gray as relationship bars), no dividers.
			// We need to calculate the percentage fill: gray pixels vs other colors (white, blue, etc.).
			val gaugeStartX = relX(iconLocation.x, -80) // Gauge is to the left of icon, estimate width.
			val gaugeStartY = relY(iconLocation.y, -5) // Slight vertical adjustment.
			val gaugeWidth = relWidth(60)
			val gaugeHeight = relHeight(15)

			val gaugeBitmap = createSafeBitmap(sourceBitmap, gaugeStartX, gaugeStartY, gaugeWidth, gaugeHeight, "analyzeSpiritExplosionGauges")
			if (gaugeBitmap == null) {
				continue
			}

			val gaugeMat = Mat()
			Utils.bitmapToMat(gaugeBitmap, gaugeMat)

			// Convert to RGB and then to HSV for better color detection.
			val rgbMat = Mat()
			Imgproc.cvtColor(gaugeMat, rgbMat, Imgproc.COLOR_BGR2RGB)
			val hsvMat = Mat()
			Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV)

			// Define gray color range (same as relationship bars gray).
			// Gray typically has low saturation and medium value.
			val grayLower = Scalar(0.0, 0.0, 50.0)
			val grayUpper = Scalar(180.0, 50.0, 200.0)

			val grayMask = Mat()
			Core.inRange(hsvMat, grayLower, grayUpper, grayMask)
			val grayPixels = Core.countNonZero(grayMask)

			val totalPixels = gaugeMat.rows() * gaugeMat.cols()
			val fillPercent = if (totalPixels > 0) {
				(grayPixels.toDouble() / totalPixels.toDouble()) * 100.0
			} else {
				0.0
			}

			// Round to nearest threshold: 0%, 25%, 50%, 75%, 100%.
			val roundedFillPercent = when {
				fillPercent < 12.5 -> 0.0
				fillPercent < 37.5 -> 25.0
				fillPercent < 62.5 -> 50.0
				fillPercent < 87.5 -> 75.0
				else -> 100.0
			}

			// Count gauges that can be filled.
			if (roundedFillPercent < 100.0) {
				numGaugesCanFill++
			}

			if (debugMode) {
				Log.d(TAG, "[DEBUG] Spirit Explosion Gauge at (${iconLocation.x}, ${iconLocation.y}): ${decimalFormat.format(roundedFillPercent)}% filled")
			}

			grayMask.release()
			hsvMat.release()
			rgbMat.release()
			gaugeMat.release()
		}

		return SpiritGaugeResult(numGaugesCanFill, spiritExplosionIcons.size)
	}

	/**
	 * Determines the aptitudes of the current character based on the levels (S, A, B) on the Full Stats popup. The priority order of the aptitude levels is S > A > B.
	 *
	 * @return The aptitudes of the current character.
	 */
	fun determineAptitudes(currentAptitudes: Game.Aptitudes): Game.Aptitudes {
		val (_, statAptitudeSTemplate) = getBitmaps("stat_aptitude_S")
		val (_, statAptitudeATemplate) = getBitmaps("stat_aptitude_A")
		val (_, statAptitudeBTemplate) = getBitmaps("stat_aptitude_B")

		val aptitudes = mutableMapOf(
			"stat_track" to mutableMapOf("turf" to "", "dirt" to ""),
			"stat_distance" to mutableMapOf("sprint" to "", "mile" to "", "medium" to "", "long" to ""),
			"stat_style" to mutableMapOf("front" to "", "pace" to "", "late" to "", "end" to "")
		)

		for ((templateName, keys) in aptitudes) {
			val (aptitudeLocation, sourceBitmap) = findImage(templateName, tries = 1, region = regionMiddle)
			if (aptitudeLocation == null) {
				MessageLog.e(TAG, "Could not determine aptitude using $templateName. Keeping previous values.")
				continue
			}

			keys.keys.forEachIndexed { i, key ->
				// Only two aptitudes for Track: Turf and Dirt.
				if (templateName == "stat_track" && i > 1) return@forEachIndexed

				val croppedBitmap = createSafeBitmap(
					sourceBitmap,
					relX(aptitudeLocation.x, 108 + (i * 190)),
					relY(aptitudeLocation.y, -25),
					176,
					52,
					"determineAptitudes $templateName $key"
				)

				if (croppedBitmap == null) {
					MessageLog.e(TAG, "Failed to crop bitmap for $templateName $key.")
					return@forEachIndexed
				}

				// Determine level by priority: S > A > B.
				val level = when {
					match(croppedBitmap, statAptitudeSTemplate!!, "stat_aptitude_S").first -> "S"
					match(croppedBitmap, statAptitudeATemplate!!, "stat_aptitude_A").first -> "A"
					match(croppedBitmap, statAptitudeBTemplate!!, "stat_aptitude_B").first -> "B"
					else -> "X"
				}

				aptitudes[templateName]?.set(key, level)
			}
		}

		// Build updated Aptitudes object
		return Game.Aptitudes(
			track = Game.Track(
				turf = aptitudes["stat_track"]?.get("turf") ?: currentAptitudes.track.turf,
				dirt = aptitudes["stat_track"]?.get("dirt") ?: currentAptitudes.track.dirt
			),
			distance = Game.Distance(
				sprint = aptitudes["stat_distance"]?.get("sprint") ?: currentAptitudes.distance.sprint,
				mile = aptitudes["stat_distance"]?.get("mile") ?: currentAptitudes.distance.mile,
				medium = aptitudes["stat_distance"]?.get("medium") ?: currentAptitudes.distance.medium,
				long = aptitudes["stat_distance"]?.get("long") ?: currentAptitudes.distance.long
			),
			style = Game.Style(
				front = aptitudes["stat_style"]?.get("front") ?: currentAptitudes.style.front,
				pace = aptitudes["stat_style"]?.get("pace") ?: currentAptitudes.style.pace,
				late = aptitudes["stat_style"]?.get("late") ?: currentAptitudes.style.late,
				end = aptitudes["stat_style"]?.get("end") ?: currentAptitudes.style.end
			)
		)
	}

	/**
	 * Reads the 5 stat values on the Main screen.
	 *
	 * @return The mapping of all 5 stats names to their respective integer values.
	 */
	fun determineStatValues(statValueMapping: MutableMap<String, Int>): MutableMap<String, Int> {
		val (skillPointsLocation, sourceBitmap) = findImage("skill_points")

		if (skillPointsLocation != null) {
			// Process all stats at once using the mapping.
			statValueMapping.keys.forEachIndexed { index, statName ->
				// Each stat is evenly spaced at 170 pixel intervals starting at offset -862.
				val offsetX = -862 + (index * 170)

				// Perform OCR with no thresholding (stats are on solid background).
				val result = performOCROnRegion(
					sourceBitmap,
					relX(skillPointsLocation.x, offsetX),
					relY(skillPointsLocation.y, 25),
					relWidth(98),
					relHeight(42),
					useThreshold = false,
					useGrayscale = true,
					scale = 1.0,
					ocrEngine = "tesseract_digits",
					debugName = "${statName}StatValue"
				)

				// Parse the result.
				MessageLog.i(TAG, "Detected number of stats for $statName from Tesseract before formatting: $result")
				if (result.lowercase().contains("max") || result.lowercase().contains("ax")) {
					MessageLog.i(TAG, "$statName seems to be maxed out. Setting it to 1200.")
					statValueMapping[statName] = 1200
				} else {
					try {
						Log.d(TAG, "Converting $result to integer for $statName stat value")
						val cleanedResult = result.replace(Regex("[^0-9]"), "")
						statValueMapping[statName] = cleanedResult.toInt()
					} catch (_: NumberFormatException) {
						statValueMapping[statName] = -1
					}
				}
			}
		} else {
			MessageLog.e(TAG, "Could not start the process of detecting stat values.")
		}

		return statValueMapping
	}

	/**
	 * Performs OCR on the date region from either the Race List screen or the Main screen to extract the current date string.
	 *
	 * @return The detected date string from the game screen, or empty string if detection fails.
	 */
	fun determineDayString(): String {
		var result = ""
		val (raceStatusLocation, sourceBitmap) = findImage("race_status", tries = 1)
		if (raceStatusLocation != null) {
			// Perform OCR with thresholding (date text is on solid white background).
			MessageLog.i(TAG, "Detecting date from the Race List screen.")
			result = performOCROnRegion(
				sourceBitmap,
				relX(raceStatusLocation.x, -170),
				relY(raceStatusLocation.y, 105),
				relWidth(640),
				relHeight(70),
				useThreshold = true,
				useGrayscale = true,
				scale = 1.0,
				ocrEngine = "mlkit",
				debugName = "dateString"
			)
		} else {
			val (energyLocation, _) = findImage("energy")
            val offsetX = if (game.campaign == "Unity Cup") {
                -40
            } else {
                -268
            }

			if (energyLocation != null) {
				// Perform OCR with no thresholding (date text is on moving background).
				MessageLog.i(TAG, "Detecting date from the Main screen.")
				result = performOCROnRegion(
					sourceBitmap,
					relX(energyLocation.x, offsetX),
					relY(energyLocation.y, -180),
					relWidth(308),
					relHeight(35),
					useThreshold = false,
					useGrayscale = true,
					scale = 1.0,
					ocrEngine = "mlkit",
					debugName = "dateString"
				)
			}
		}

		if (result != "") {
			MessageLog.i(TAG, "Detected date: $result")
			
			if (debugMode) {
				MessageLog.d(TAG, "Date string detected to be at \"$result\".")
			} else {
				Log.d(TAG, "Date string detected to be at \"$result\".")
			}

			return result
		} else {
			MessageLog.e(TAG, "Could not start the process of detecting the date string.")
		}

		return ""
	}

	/**
	 * Determines the stat gain values from training. Parameters are optional to allow for thread-safe operations.
	 *
	 * This function uses template matching to find individual digits and the "+" symbol in the
	 * stat gain area of the training screen. It processes templates for digits 0-9 and the "+"
	 * symbol, then constructs the final integer value by analyzing the spatial arrangement
	 * of detected matches.
	 *
	 * @param trainingName Name of the currently selected training to determine which stats to read.
	 * @param sourceBitmap Bitmap of the source image separately taken. Defaults to null.
	 * @param skillPointsLocation Point location of the template image separately taken. Defaults to null.
	 *
	 * @return Array of 5 detected stat gain values as integers, or -1 for failed detections.
	 */
	fun determineStatGainFromTraining(trainingName: String, sourceBitmap: Bitmap? = null, skillPointsLocation: Point? = null): IntArray {
        // Scenario-specific checks.
		val isUnityCup = game.campaign == "Unity Cup"

		val templateSuffix = if (isUnityCup) "_mini" else ""
		val templates = listOf("+", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9").map { it + templateSuffix }
		val statNames = listOf("Speed", "Stamina", "Power", "Guts", "Wit")
		// Define a mapping of training types to their stat indices
		val trainingToStatIndices = mapOf(
			"Speed" to listOf(0, 2),
			"Stamina" to listOf(1, 3),
			"Power" to listOf(1, 2),
			"Guts" to listOf(0, 2, 3),
			"Wit" to listOf(0, 4)
		)

		val (skillPointsLocation, sourceBitmap) = if (sourceBitmap == null && skillPointsLocation == null) {
			findImage("skill_points")
		} else {
			Pair(skillPointsLocation, sourceBitmap)
		}

		val threadSafeResults = IntArray(5)

		if (skillPointsLocation != null) {
			// Pre-load all template bitmaps to avoid thread contention
			val templateBitmaps = mutableMapOf<String, Bitmap?>()
			for (templateName in templates) {
				context.assets?.open("images/$templateName.png").use { inputStream ->
					templateBitmaps[templateName] = BitmapFactory.decodeStream(inputStream)
				}
			}

			// Process all stats in parallel using threads.
			val statLatch = CountDownLatch(5)
			for (i in 0 until 5) {
				Thread {
					var sourceMat: Mat? = null
					var sourceGray: Mat? = null
					var workingMat: Mat? = null
					try {
						// Stop the Thread early if the selected Training would not offer stats for the stat to be checked.
						// Speed gives Speed and Power
						// Stamina gives Stamina and Guts
						// Power gives Stamina and Power
						// Guts gives Speed, Power and Guts
						// Wits gives Speed and Wits
						val validIndices = trainingToStatIndices[trainingName] ?: return@Thread
						if (i !in validIndices) return@Thread

						// Check if bot is still running before starting work.
						if (!BotService.isRunning) {
							return@Thread
						}

						val statName = statNames[i]
						val xOffset = i * 180 // All stats are evenly spaced at 180 pixel intervals.

						// Determine crop regions based on campaign.
						val firstRowStartX = relX(skillPointsLocation.x, -934 + xOffset)
						val firstRowStartY = if (isUnityCup) {
                            relY(skillPointsLocation.y, -65)
                        } else {
                            relY(skillPointsLocation.y, -103)
                        }
						
						var matchResults = mutableMapOf<String, MutableList<Point>>()
						templates.forEach { template ->
							matchResults[template] = mutableListOf()
						}

						// Declare croppedBitmap variable for debug visualization (used in URA Finale path).
						var croppedBitmap: Bitmap? = null

						// Build the row configurations based on the current scenario.
						val rows = if (isUnityCup) {
							// For Unity Cup, stats are in two rows on top of each other.
							val secondRowStartY = relY(firstRowStartY.toDouble(), -55)
							listOf(
								StatGainRowConfig(firstRowStartX, firstRowStartY, relWidth(150), relHeight(55), "row1"),
                                StatGainRowConfig(firstRowStartX, secondRowStartY, relWidth(150), relHeight(55), "row2")
							)
						} else {
							// Default: single row.
							listOf(
								StatGainRowConfig(firstRowStartX, firstRowStartY, relWidth(150), relHeight(82), "")
							)
						}

						// Track all Mat objects for cleanup.
						val matObjects = mutableListOf<Mat>()
						var processingFailed = false
						// Track row information and matches for debug visualization.
						data class RowDebugInfo(val bitmap: Bitmap, val config: StatGainRowConfig, val matches: MutableMap<String, MutableList<Point>>)
						val rowDebugInfo = mutableListOf<RowDebugInfo>()

						try {
							// Process each row.
							for (row in rows) {
								if (!BotService.isRunning) {
									processingFailed = true
									break
								}

								// Create bitmap for this row.
								val rowBitmap = createSafeBitmap(sourceBitmap!!, row.startX, row.startY, row.width, row.height, "determineStatGainFromTraining $statName ${row.rowName}".trim())
								if (rowBitmap == null) {
									Log.e(TAG, "[ERROR] Failed to create cropped bitmap for $statName stat gain detection from $trainingName training ${row.rowName}.")
									threadSafeResults[i] = 0
									statLatch.countDown()
									processingFailed = true
									return@Thread
								}

								// Set croppedBitmap for non-Unity Cup path (used in debug visualization).
								if (!isUnityCup) {
									croppedBitmap = rowBitmap
								}

								// Initialize row-specific matches for debug visualization.
								val rowMatches = mutableMapOf<String, MutableList<Point>>()
								templates.forEach { template ->
									rowMatches[template] = mutableListOf()
								}

								// Check again before expensive operations.
								if (!BotService.isRunning) {
									statLatch.countDown()
									processingFailed = true
									return@Thread
								}

								// Convert to Mat and then turn it to grayscale.
								val rowMat = Mat()
								Utils.bitmapToMat(rowBitmap, rowMat)
								matObjects.add(rowMat)

								val rowGray = Mat()
								Imgproc.cvtColor(rowMat, rowGray, Imgproc.COLOR_BGR2GRAY)
								matObjects.add(rowGray)

								val rowWorking = Mat()
								rowGray.copyTo(rowWorking)
								matObjects.add(rowWorking)

								// Check again before starting template processing loop.
								if (!BotService.isRunning) {
									threadSafeResults[i] = 0
									processingFailed = true
									return@Thread
								}

								// Process templates for this row.
								for (templateName in templates) {
									// Check before each template processing operation.
									if (!BotService.isRunning) {
										processingFailed = true
										break
									}
									val templateBitmap = templateBitmaps[templateName]
									if (templateBitmap != null) {
										val processedMatches = processStatGainTemplateWithTransparency(templateName, templateBitmap, rowWorking, mutableMapOf<String, MutableList<Point>>().apply {
											templates.forEach { t -> this[t] = mutableListOf() }
										})
										// Store original matches for this row (for debug visualization).
										processedMatches[templateName]?.forEach { point ->
											rowMatches[templateName]?.add(point)
										}
										// Adjust match points by row offset and add to main results.
										processedMatches[templateName]?.forEach { point ->
											val adjustedPoint = Point(point.x, point.y)
											matchResults[templateName]?.add(adjustedPoint)
										}
									} else {
										Log.e(TAG, "[ERROR] Could not load template \"$templateName\" to process stat gains for $trainingName training.")
									}
								}

								// Store row bitmap, config, and matches for debug visualization.
								rowDebugInfo.add(RowDebugInfo(rowBitmap, row, rowMatches))
							}
						} finally {
							// Clean up all Mat objects.
							matObjects.forEach { it.release() }
						}

						if (processingFailed) {
							return@Thread
						}

						// Analyze results and construct the final integer value for this region.
						val finalValue = constructIntegerFromMatches(matchResults)
						threadSafeResults[i] = finalValue
						Log.d(TAG, "[INFO] $statName region final constructed value from $trainingName training: $finalValue.")

						// Draw final visualization with all matches for this region.
						if (debugMode) {
							// Save separate debug images for each row.
							for ((rowIndex, rowInfo) in rowDebugInfo.withIndex()) {
								val resultMat = Mat()
								Utils.bitmapToMat(rowInfo.bitmap, resultMat)

								// Draw matches for this row using the stored row-specific matches.
								templates.forEachIndexed { _, templateName ->
									rowInfo.matches[templateName]?.forEach { point ->
										val templateBitmap = templateBitmaps[templateName]
										if (templateBitmap != null) {
											val templateWidth = templateBitmap.width
											val templateHeight = templateBitmap.height

											// Calculate the bounding box coordinates.
											val x1 = (point.x - templateWidth/2).toInt()
											val y1 = (point.y - templateHeight/2).toInt()
											val x2 = (point.x + templateWidth/2).toInt()
											val y2 = (point.y + templateHeight/2).toInt()

											// Draw the bounding box.
											Imgproc.rectangle(resultMat, Point(x1.toDouble(), y1.toDouble()), Point(x2.toDouble(), y2.toDouble()), Scalar(0.0, 0.0, 0.0), 2)

											// Add text label.
											Imgproc.putText(resultMat, templateName, point, Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, Scalar(0.0, 0.0, 0.0), 1)
										}
									}
								}

								// Generate filename with row identifier if multiple rows exist.
								val rowSuffix = if (rows.size > 1) {
									if (rowInfo.config.rowName.isNotEmpty()) {
										"_${rowInfo.config.rowName}"
									} else {
										"_row${rowIndex + 1}"
									}
								} else {
									""
								}
								Imgcodecs.imwrite("$matchFilePath/debug_${trainingName}TrainingStatGain_${statNames[i]}${rowSuffix}.png", resultMat)
								resultMat.release()
							}
						}
					} catch (e: Exception) {
						Log.e(TAG, "[ERROR] Error processing stat ${statNames[i]} for $trainingName training: ${e.stackTraceToString()}")
						threadSafeResults[i] = 0
					} finally {
						// Always clean up resources, even if interrupted.
						sourceMat?.release()
						sourceGray?.release()
						workingMat?.release()
						statLatch.countDown()
					}
				}.start()
			}

			// Wait for all threads to complete.
			try {
				statLatch.await(30, TimeUnit.SECONDS)
			} catch (_: InterruptedException) {
				MessageLog.e(TAG, "Stat processing timed out for $trainingName training.")
			}

			// Apply artificial boost to main stat gains if they appear lower than side-effect stats.
			val boostedResults = applyStatGainBoost(trainingName, threadSafeResults, statNames, trainingToStatIndices)
			return boostedResults
		} else {
			MessageLog.e(TAG, "Could not find the skill points location to start determining stat gains for $trainingName training.")
		}

		return threadSafeResults
	}

	/**
	 * Applies artificial boost to main stat gains when they appear lower than side-effect stats due to OCR failure.
	 * 
	 * @param trainingName Name of the training type (Speed, Stamina, Power, Guts, Wit).
	 * @param statGains Array of 5 stat gains.
	 * @param statNames List of stat names in order.
	 * @param trainingToStatIndices Mapping of training types to their affected stat indices.
	 * @return Array of stat gains with potential artificial boost applied to main stat.
	 */
	private fun applyStatGainBoost(trainingName: String, statGains: IntArray, statNames: List<String>, trainingToStatIndices: Map<String, List<Int>>): IntArray {
		val boostedResults = statGains.clone()
		
		// Define the main stat index for each training type.
		val mainStatIndex = when (trainingName) {
			"Speed" -> 0
			"Stamina" -> 1
			"Power" -> 2
			"Guts" -> 3
			"Wit" -> 4
			else -> return boostedResults
		}
		
		// Get the stat indices affected by this training type and filter out the main stat to get side-effects.
		val affectedIndices = trainingToStatIndices[trainingName] ?: return boostedResults
		val sideEffectIndices = affectedIndices.filter { it != mainStatIndex }
		
		val mainStatGain = boostedResults[mainStatIndex]
		val mainStatName = statNames[mainStatIndex]
		
		// Check if any side-effect stat has a higher gain than the main stat.
		val maxSideEffectGain = sideEffectIndices.maxOfOrNull { boostedResults[it] } ?: 0
		
		if (mainStatGain > 0 && maxSideEffectGain > mainStatGain) {
			// Set main stat to be 10 points higher than the highest side-effect stat.
			val originalGain = boostedResults[mainStatIndex]
			boostedResults[mainStatIndex] = maxSideEffectGain + 10
			Log.d(TAG,
				"[DEBUG] Artificially increased $mainStatName stat gain from $originalGain to ${boostedResults[mainStatIndex]} due to possible OCR failure. " +
				"Side-effect stats had higher gains: ${sideEffectIndices.joinToString(", ") { "${statNames[it]} = ${boostedResults[it]}" }}"
			)
		}

		// If the side-effect stat gains were zeroes, boost them to half of the main stat gain.
		val boostedMainStatGain = boostedResults[mainStatIndex]
		sideEffectIndices.forEach { idx ->
			if (boostedResults[idx] == 0 && boostedMainStatGain > 0) {
				boostedResults[idx] = boostedMainStatGain / 2
				Log.d(TAG, "[DEBUG] Artificially increased ${statNames[idx]} side-effect stat gain to ${boostedResults[idx]} because it was 0 due to possible OCR failure. " +
						"Based on half of boosted $mainStatName = $boostedMainStatGain."
				)
			}
		}
		
		return boostedResults
	}

	/**
	 * Processes a single template with transparency to find all valid matches in the working matrix through a multi-stage algorithm.
	 *
	 * The algorithm uses two validation criteria:
	 * - Pixel match ratio: Ensures sufficient pixel-level similarity.
	 * - Correlation coefficient: Validates statistical correlation between template and matched region.
	 *
	 * @param templateName Name of the template being processed (used for logging and debugging).
	 * @param templateBitmap Bitmap of the template image (must have 4-channel RGBA format with transparency).
	 * @param workingMat Working matrix to search in (grayscale source image).
	 * @param matchResults Map to store match results, organized by template name.
	 *
	 * @return The modified matchResults mapping containing all valid matches found for this template
	 */
	private fun processStatGainTemplateWithTransparency(templateName: String, templateBitmap: Bitmap, workingMat: Mat, matchResults: MutableMap<String, MutableList<Point>>): MutableMap<String, MutableList<Point>> {
		// These values have been tested for the best results against the dynamic background.
		val matchConfidence = 0.9
		val minPixelMatchRatio = 0.1
		val minPixelCorrelation = 0.85

		// Convert template to Mat and then to grayscale.
		val templateMat = Mat()
		val templateGray = Mat()
		Utils.bitmapToMat(templateBitmap, templateMat)
		Imgproc.cvtColor(templateMat, templateGray, Imgproc.COLOR_BGR2GRAY)

		// Check if template has an alpha channel (transparency).
		if (templateMat.channels() != 4) {
			Log.e(TAG, "[ERROR] Template \"$templateName\" is not transparent and is a requirement.")
			templateMat.release()
			templateGray.release()
			return matchResults
		}

		// Extract alpha channel for the alpha mask.
		val alphaChannels = ArrayList<Mat>()
		Core.split(templateMat, alphaChannels)
		val alphaMask = alphaChannels[3] // Alpha channel is the 4th channel.

		// Create binary mask for non-transparent pixels.
		val validPixels = Mat()
		Core.compare(alphaMask, Scalar(0.0), validPixels, Core.CMP_GT)

		// Check transparency ratio.
		val nonZeroPixels = Core.countNonZero(alphaMask)
		val totalPixels = alphaMask.rows() * alphaMask.cols()
		val transparencyRatio = nonZeroPixels.toDouble() / totalPixels
		if (transparencyRatio < 0.1) {
			Log.w(TAG, "[DEBUG] Template \"$templateName\" appears to be mostly transparent!")
			alphaChannels.forEach { it.release() }
			validPixels.release()
			alphaMask.release()
			templateMat.release()
			templateGray.release()
			return matchResults
		}

		////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////

		var continueSearching = true
		var searchMat = Mat()
		var xOffset = 0
		workingMat.copyTo(searchMat)

		try {
			while (continueSearching) {
				var failedPixelMatchRatio = false
				var failedPixelCorrelation = false

				// Template match with the alpha mask.
				val result = Mat()
				Imgproc.matchTemplate(searchMat, templateGray, result, Imgproc.TM_CCORR_NORMED, alphaMask)
				val mmr = Core.minMaxLoc(result)
				val matchVal = mmr.maxVal
				val matchLocation = mmr.maxLoc

				if (matchVal >= matchConfidence) {
					val x = matchLocation.x.toInt()
					val y = matchLocation.y.toInt()
					val h = templateGray.rows()
					val w = templateGray.cols()

					// Validate that the match location is within bounds.
					if (x >= 0 && y >= 0 && x + w <= searchMat.cols() && y + h <= searchMat.rows()) {
						// Extract the matched region from the source image.
						val matchedRegion = Mat(searchMat, Rect(x, y, w, h))

						// Create masked versions of the template and matched region using only non-transparent pixels.
						val templateValid = Mat()
						val regionValid = Mat()
						templateGray.copyTo(templateValid, validPixels)
						matchedRegion.copyTo(regionValid, validPixels)

						// For the first test, compare pixel-by-pixel equality between the matched region and template to calculate match ratio.
						val templateComparison = Mat()
						Core.compare(matchedRegion, templateGray, templateComparison, Core.CMP_EQ)
						val matchingPixels = Core.countNonZero(templateComparison)
						val pixelMatchRatio = matchingPixels.toDouble() / (w * h)
						if (pixelMatchRatio < minPixelMatchRatio) {
							failedPixelMatchRatio = true
						}

						// Extract pixel values into double arrays for correlation calculation.
						val templateValidMat = Mat()
						val regionValidMat = Mat()
						templateValid.convertTo(templateValidMat, CvType.CV_64F)
						regionValid.convertTo(regionValidMat, CvType.CV_64F)
						val templateArray = DoubleArray(templateValid.total().toInt())
						val regionArray = DoubleArray(regionValid.total().toInt())
						templateValidMat.get(0, 0, templateArray)
						regionValidMat.get(0, 0, regionArray)

						// For the second test, validate the match quality by performing correlation calculation.
						val pixelCorrelation = calculateCorrelation(templateArray, regionArray)
						if (pixelCorrelation < minPixelCorrelation) {
							failedPixelCorrelation = true
						}

						// If both tests passed, then the match is valid.
						if (!failedPixelMatchRatio && !failedPixelCorrelation) {
							val centerX = (x + xOffset) + (w / 2)
							val centerY = y + (h / 2)

							// Check for overlap with existing matches within 10 pixels on both axes.
							val hasOverlap = matchResults.values.flatten().any { existingPoint ->
								val existingX = existingPoint.x
								val existingY = existingPoint.y

								// Check if the new match overlaps with existing match within 10 pixels.
								val xOverlap = kotlin.math.abs(centerX - existingX) < 10
								val yOverlap = kotlin.math.abs(centerY - existingY) < 10

								xOverlap && yOverlap
							}

							if (!hasOverlap) {
								Log.d(TAG, "[DEBUG] Found valid match for template \"$templateName\" at ($centerX, $centerY).")
								matchResults[templateName]?.add(Point(centerX.toDouble(), centerY.toDouble()))
							}
						}

						// Draw a box to prevent re-detection in the next loop iteration.
						Imgproc.rectangle(searchMat, Point(x.toDouble(), y.toDouble()), Point((x + w).toDouble(), (y + h).toDouble()), Scalar(0.0, 0.0, 0.0), 10)

						templateComparison.release()
						matchedRegion.release()
						templateValid.release()
						regionValid.release()
						templateValidMat.release()
						regionValidMat.release()

						// Crop the Mat horizontally to exclude the supposed matched area.
						val cropX = x + w
						val remainingWidth = searchMat.cols() - cropX
						when {
							remainingWidth < templateGray.cols() -> {
								continueSearching = false
							}
							else -> {
								val newSearchMat = Mat(searchMat, Rect(cropX, 0, remainingWidth, searchMat.rows()))
								searchMat.release()
								searchMat = newSearchMat
								xOffset += cropX
							}
						}
					} else {
						// Stop searching when the source has been traversed.
						continueSearching = false
					}
				} else {
					// No match found above threshold, stop searching for this template.
					continueSearching = false
				}

				result.release()

				// Safety check to prevent infinite loops.
				if ((matchResults[templateName]?.size ?: 0) > 10) {
					continueSearching = false
				}
				if (!BotService.isRunning) {
					throw InterruptedException()
				}
			}
		} finally {
			// Always clean up resources, even if InterruptedException is thrown.
			searchMat.release()
			alphaChannels.forEach { it.release() }
			validPixels.release()
			alphaMask.release()
			templateMat.release()
			templateGray.release()
		}

		////////////////////////////////////////////////////////////////////
		////////////////////////////////////////////////////////////////////

		return matchResults
	}

	/**
	 * Constructs the final integer value from matched template locations of numbers by analyzing spatial arrangement.
	 *
	 * The function is designed for OCR-like scenarios where individual character templates
	 * are matched separately and need to be reconstructed into a complete number.
	 *
	 * If matchResults contains: {"+" -> [(10, 20)], "1" -> [(15, 20)], "2" -> [(20, 20)]}, it returns: 12 (from string "+12").
	 *
	 * @param matchResults Map of template names (e.g., "0", "1", "2", "+") to their match locations.
	 *
	 * @return The constructed integer value or -1 if it failed.
	 */
	private fun constructIntegerFromMatches(matchResults: Map<String, MutableList<Point>>): Int {
		// Collect all matches with their template names.
		val allMatches = mutableListOf<Pair<String, Point>>()
		matchResults.forEach { (templateName, points) ->
			points.forEach { point ->
				allMatches.add(Pair(templateName, point))
			}
		}

		if (allMatches.isEmpty()) {
			Log.d(TAG, "[WARNING] No matches found to construct integer value.")
			return 0
		}

		// Sort matches by x-coordinate (left to right).
		allMatches.sortBy { it.second.x }
		Log.d(TAG, "[DEBUG] Sorted matches: ${allMatches.map { "${it.first}@(${it.second.x}, ${it.second.y})" }}")

		// Construct the string representation by extracting the character part from template names (removing suffixes like "_mini").
		// Template names can be "+", "0"-"9" or "+_mini", "0_mini"-"9_mini", so we extract the first character.
		val constructedString = allMatches.joinToString("") { it.first[0].toString() }
		Log.d(TAG, "[DEBUG] Constructed string: \"$constructedString\".")

		// Extract the numeric part and convert to integer.
		return try {
            if (constructedString == "+") {
                Log.w(TAG, "[WARNING] Constructed string was just the plus sign. Setting the result to 0.")
                return 0
            }

			val numericPart = if (constructedString.startsWith("+") && constructedString.substring(1).isNotEmpty()) {
				constructedString.substring(1)
			} else {
				constructedString
			}

			val result = numericPart.toInt()
			Log.d(TAG, "[DEBUG] Successfully constructed integer value: $result from \"$constructedString\".")
			result
		} catch (e: NumberFormatException) {
			Log.e(TAG, "[ERROR] Could not convert \"$constructedString\" to integer for stat gain: ${e.stackTraceToString()}")
			0
		}
	}

	/**
	 * Calculates the Pearson correlation coefficient between two arrays of pixel values.
	 *
	 * The Pearson correlation coefficient measures the linear correlation between two variables,
	 * ranging from -1 (perfect negative correlation) to +1 (perfect positive correlation).
	 * A value of 0 indicates no linear correlation.
	 *
	 * @param array1 First array of pixel values from the template image.
	 * @param array2 Second array of pixel values from the matched region.
	 * @return Correlation coefficient between -1.0 and +1.0, or 0.0 if arrays are invalid
	 */
	private fun calculateCorrelation(array1: DoubleArray, array2: DoubleArray): Double {
		if (array1.size != array2.size || array1.isEmpty()) {
			return 0.0
		}

		val n = array1.size
		val sum1 = array1.sum()
		val sum2 = array2.sum()
		val sum1Sq = array1.sumOf { it * it }
		val sum2Sq = array2.sumOf { it * it }
		val pSum = array1.zip(array2).sumOf { it.first * it.second }

		// Calculate the numerator: n*Σ(xy) - Σx*Σy
		val num = pSum - (sum1 * sum2 / n)
		// Calculate the denominator: sqrt((n*Σx² - (Σx)²) * (n*Σy² - (Σy)²))
		val den = sqrt((sum1Sq - sum1 * sum1 / n) * (sum2Sq - sum2 * sum2 / n))

		// Return the correlation coefficient, handling division by zero.
		return if (den == 0.0) 0.0 else num / den
	}

	////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////
	// Helper functions for OCR operations.

	/**
	 * Performs OCR on a cropped region of a source bitmap with optional preprocessing.
	 * 
	 * @param sourceBitmap The source image to crop from.
	 * @param x The x-coordinate of the crop region.
	 * @param y The y-coordinate of the crop region.
	 * @param width The width of the crop region.
	 * @param height The height of the crop region.
	 * @param useThreshold Whether to apply binary thresholding. Defaults to true.
	 * @param useGrayscale Whether to convert to grayscale first. Defaults to true.
	 * @param scale Scale factor to apply to the processed image. Values > 1 scale up, values < 1 scale down. Defaults to 1.0 (no scaling).
	 * @param ocrEngine The OCR engine to use ("tesseract", "mlkit", or "tesseract_digits"). Defaults to "tesseract".
	 * @param debugName Optional name for debug image saving.
	 * 
	 * @return The detected text string or empty string if OCR fails.
	 */
	fun performOCROnRegion(
		sourceBitmap: Bitmap,
		x: Int,
		y: Int,
		width: Int,
		height: Int,
		useThreshold: Boolean = true,
		useGrayscale: Boolean = true,
		scale: Double = 1.0,
		ocrEngine: String = "tesseract",
		debugName: String = ""
	): String {
		// Perform OCR using findText() from ImageUtils.
		return findText(
			cropRegion = intArrayOf(x, y, width, height),
			grayscale = useGrayscale,
			thresh = useThreshold,
			threshold = threshold.toDouble(),
			thresholdMax = 255.0,
			scale = scale,
			sourceBitmap = sourceBitmap,
			detectDigitsOnly = ocrEngine == "tesseract_digits",
            debugName = debugName
		)
	}

	/**
	 * Performs OCR on a custom region using a reference point.
	 * 
	 * @param referencePoint The point to base the crop region on.
	 * @param offsetX Offset from reference point x-coordinate.
	 * @param offsetY Offset from reference point y-coordinate.
	 * @param width Width of the crop region.
	 * @param height Height of the crop region.
	 * @param useThreshold Whether to apply binary thresholding. Defaults to true.
	 * @param useGrayscale Whether to convert to grayscale first. Defaults to true.
	 * @param scale Scale factor to apply to the processed image. Values > 1 scale up, values < 1 scale down. Defaults to 1.0 (no scaling).
	 * @param ocrEngine The OCR engine to use. Defaults to "tesseract".
	 * @param debugName Optional name for debug image saving.
	 * 
	 * @return The detected text string or empty string if OCR fails.
	 */
	fun performOCRFromReference(
		referencePoint: Point,
		offsetX: Int,
		offsetY: Int,
		width: Int,
		height: Int,
		useThreshold: Boolean = true,
		useGrayscale: Boolean = true,
		scale: Double = 1.0,
		ocrEngine: String = "tesseract",
		debugName: String = ""
	): String {
		val sourceBitmap = getSourceBitmap()
		val finalX = relX(referencePoint.x, offsetX)
		val finalY = relY(referencePoint.y, offsetY)
		
		return performOCROnRegion(
			sourceBitmap,
			finalX,
			finalY,
			width,
			height,
			useThreshold,
			useGrayscale,
			scale,
			ocrEngine,
			debugName
		)
	}

    /**
    * Gets the filled percentage of the energy bar.
    *
    * @return If energy bar is detected, returns the filled percentage, else returns null.
    */
    fun analyzeEnergyBar(): Int? {
        val (sourceBitmap, templateBitmap) = getBitmaps("energy")
        if (templateBitmap == null) {
            MessageLog.e(TAG, "[ERROR] Failed to find template bitmap for \"energy\".")
            return null
        }
        val energyTextLocation = findImage("energy", tries = 1, region = regionTopHalf).first
        if (energyTextLocation == null) {
            MessageLog.e(TAG, "[ERROR] Failed to find the text location of the energy bar.")
            return null
        }

        // Get top right of energyText.
        var x: Int = (energyTextLocation.x + (templateBitmap.width / 2)).toInt()
        var y: Int = (energyTextLocation.y - (templateBitmap.height / 2)).toInt()
        var w: Int = 700
        var h: Int = 75

        // Crop just the energy bar in the image.
        // This crop extends to the right beyond the energy bar a bit
        // since the bar is able to grow.
        var croppedBitmap = createSafeBitmap(sourceBitmap, x, y, w, h, "analyzeEnergyBar:: Crop energy bar.")
        if (croppedBitmap == null) {
            MessageLog.e(TAG, "[ERROR] Failed to crop the bitmap of the energy bar.")
            return null
        }

        // Now find the left and right brackets of the energy bar
        // to refine our cropped region.

        val energyBarLeftPartTemplateBitmap: Bitmap? = getBitmaps("energy_bar_left_part").second
        if (energyBarLeftPartTemplateBitmap == null) {
            MessageLog.e(TAG, "[ERROR] Failed to find the template bitmap for the left part of the energy bar.")
            return null
        }

        val leftPartLocation: Point? = match(croppedBitmap, energyBarLeftPartTemplateBitmap, "energy_bar_left_part").second
        if (leftPartLocation == null) {
            MessageLog.e(TAG, "[ERROR] Failed to find the location of the left part of the energy bar.")
            return null
        }

        // The right side of the energy bar looks very different depending on whether
        // the max energy has been increased. Thus we need to look for one of two bitmaps.
        var energyBarRightPartTemplateBitmap: Bitmap? = getBitmaps("energy_bar_right_part_0").second
        var rightPartLocation: Point?
        if (energyBarRightPartTemplateBitmap == null) {
            energyBarRightPartTemplateBitmap = getBitmaps("energy_bar_right_part_1").second
            if (energyBarRightPartTemplateBitmap == null) {
                MessageLog.e(TAG, "[ERROR] Failed to find the template bitmap for the right part of the energy bar.")
                return null
            }
            rightPartLocation = match(croppedBitmap, energyBarRightPartTemplateBitmap, "energy_bar_right_part_1").second
        } else {
            rightPartLocation = match(croppedBitmap, energyBarRightPartTemplateBitmap, "energy_bar_right_part_0").second
        }

        if (rightPartLocation == null) {
            MessageLog.e(TAG, "[ERROR] Failed to find the location of the right part of the energy bar.")
            return null
        }

        // Crop the energy bar further to refine the cropped region so that
        // we can measure the length of the bar.
        // This crop is just a single pixel high line at the center of the
        // bounding region.
        val left: Int = (leftPartLocation.x + (energyBarLeftPartTemplateBitmap.width / 2)).toInt()
        val right: Int = (rightPartLocation.x - (energyBarRightPartTemplateBitmap.width / 2)).toInt()
        x = left
        y = (croppedBitmap.height / 2).toInt()
        w = (right - left).toInt()
        h = 1

        croppedBitmap = createSafeBitmap(croppedBitmap, x, y, w, h, "analyzeEnergyBar:: Refine cropped energy bar.")
        if (croppedBitmap == null) {
            MessageLog.e(TAG, "[ERROR] Failed to refine the cropped bitmap region of the energy bar.")
            return null
        }

        // HSV color range for gray portion of energy bar.
        val grayLower = Scalar(0.0, 0.0, 116.0)
        val grayUpper = Scalar(180.0, 255.0, 118.0)
        val colorLower = Scalar(5.0, 0.0, 120.0)
        val colorUpper = Scalar(180.0, 255.0, 255.0)

        // Convert the cropped region to HSV
        val barMat = Mat()
        Utils.bitmapToMat(croppedBitmap, barMat)
        val hsvMat = Mat()
        Imgproc.cvtColor(barMat, hsvMat, Imgproc.COLOR_BGR2HSV)

        // Create masks for the gray and color portions of the image.
        val grayMask = Mat()
        val colorMask = Mat()
        Core.inRange(hsvMat, grayLower, grayUpper, grayMask)
        Core.inRange(hsvMat, colorLower, colorUpper, colorMask)

        // Calculate ratio of color and gray pixels.
        val grayPixels = Core.countNonZero(grayMask)
        val colorPixels = Core.countNonZero(colorMask)
        val totalPixels = grayPixels + colorPixels

        var fillPercent: Double = 0.0
        if (totalPixels > 0) {
            fillPercent = (colorPixels.toDouble() / totalPixels.toDouble()) * 100.0
        }
        val result: Int = fillPercent.toInt().coerceIn(0, 100)

        barMat.release()
        hsvMat.release()
        grayMask.release()
        colorMask.release()

        Log.d(TAG, "[DEBUG] Results of energy bar analysis: Gray pixels=$grayPixels, Color pixels=$colorPixels, Energy=$result")
        return result
    }
}