package com.steve1316.uma_android_automation.components

import com.steve1316.automation_library.utils.MyAccessibilityService

import com.steve1316.uma_android_automation.utils.CustomImageUtils

import org.opencv.core.Point

object ComponentUtils {
    /**
    * Performs a tap on the screen at the coordinates and then will wait until the game processes the server request and gets a response back.
    *
    * @param x The x-coordinate.
    * @param y The y-coordinate.
    * @param imageName The template image name to use for tap location randomization.
    * @param taps The number of taps.
    */
    fun tap(x: Double, y: Double, imageName: String, taps: Int = 1) {
        // Perform the tap.
        MyAccessibilityService.getInstance().tap(x, y, imageName, taps=taps)
    }

    /**
    * Find and tap the specified image.
    *
    * @param imageUtils the CustomImageUtils instance used to find the image.
    * @param imageName Name of the button image file in the /assets/images/ folder.
    * @param tries Number of tries to find the specified button. Defaults to 3.
    * @param region Specify the region consisting of (x, y, width, height) of the source screenshot to template match. Defaults to (0, 0, 0, 0) which is equivalent to searching the full image.
    * @param taps Specify the number of taps on the specified image. Defaults to 1.
    * @param suppressError Whether or not to suppress saving error messages to the log in failing to find the button. Defaults to false.
    * @return True if the button was found and clicked. False otherwise.
    */
	fun findAndTapImage(imageUtils: CustomImageUtils, imageName: String, tries: Int = 3, region: IntArray = intArrayOf(0, 0, 0, 0), taps: Int = 1, suppressError: Boolean = false, loggingTag: String = "ComponentUtils"): Boolean {
		val tempLocation: Point? = imageUtils.findImage(imageName, tries = tries, region = region, suppressError = suppressError).first

		return if (tempLocation != null) {
			tap(tempLocation.x, tempLocation.y, imageName, taps=taps)
			true
		} else {
			false
		}
	}
}