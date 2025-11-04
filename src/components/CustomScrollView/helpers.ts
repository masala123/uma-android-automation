import { ViewStyle } from "react-native"

/**
 * ---------------------------------------------------------------------------
 * Utility Functions for CustomScrollView.
 * ---------------------------------------------------------------------------
 *
 * This module contains math and layout utilities used by CustomScrollView
 * and its internal scroll indicator. These helpers are *pure functions*,
 * meaning they have no side effects and can be easily tested or reused.
 *
 * The goal is to isolate all layout calculations so that the main component
 * can focus purely on rendering and animation.
 * ---------------------------------------------------------------------------
 */

/**
 * Clamps a numeric value between a minimum and maximum boundary.
 *
 * @example
 * clamp(120, 0, 100) → 100
 * clamp(-10, 0, 100) → 0
 * clamp(50, 0, 100) → 50
 *
 * @param value The number to be clamped.
 * @param min The minimum allowed value.
 * @param max The maximum allowed value.
 * @returns The clamped value.
 */
export const clamp = (value: number, min: number, max: number): number => {
    return Math.max(min, Math.min(value, max))
}

/**
 * Calculates the orthogonal (cross-axis) style position for the indicator.
 *
 * This determines where the scrollbar should be anchored relative to
 * its parent container — either top/bottom for horizontal scrolls,
 * or left/right for vertical scrolls.
 *
 * If a numeric value is provided instead of a keyword, it is treated as
 * a percentage offset of the available cross-axis space.
 *
 * @example
 * // Example: horizontal scroll with indicator at bottom.
 * getIndicatorPositionStyle(true, "bottom", 100, 6)
 * → { bottom: 0 }
 *
 * // Example: vertical scroll with indicator 20% from left.
 * getIndicatorPositionStyle(false, 20, 200, 8)
 * → { left: 32 } // 20% * 200 − 8/2 = 32
 *
 * @param horizontal Whether the scroll is horizontal (true) or vertical (false).
 * @param position One of:
 *   - "top" | "bottom" (for horizontal).
 *   - "left" | "right" (for vertical).
 *   - A number (percentage position of the indicator).
 * @param crossAxisSize The total size of the container along the orthogonal axis
 *   (e.g., height if horizontal, width if vertical).
 * @param indicatorThickness The thickness (height or width) of the scrollbar indicator.
 * @returns A React Native ViewStyle object positioning the indicator correctly.
 * @throws Will throw an error if `position` is invalid for the given orientation.
 */
export const getIndicatorPositionStyle = (horizontal: boolean, position: string | number, crossAxisSize: number, indicatorThickness: number): ViewStyle => {
    // Horizontal scroll case: indicator is placed along top/bottom edges.
    if (horizontal) {
        if (typeof position === "string" && ["top", "bottom"].includes(position)) {
            // Fixed placement (flush to top or bottom).
            return { [position]: 0 }
        } else if (typeof position === "number") {
            // Numeric placement (percentage offset from top).
            const offset = clamp((position / 100) * crossAxisSize - indicatorThickness / 2, 0, crossAxisSize - indicatorThickness)
            return { top: offset }
        } else {
            throw Error('Invalid "position" for horizontal mode. Must be "top", "bottom", or a number (percentage).')
        }
    }

    // Vertical scroll case: indicator is placed along left/right edges.
    else {
        if (typeof position === "string" && ["left", "right"].includes(position)) {
            // Fixed placement (flush to left or right).
            return { [position]: 0 }
        } else if (typeof position === "number") {
            // Numeric placement (percentage offset from left).
            const offset = clamp((position / 100) * crossAxisSize - indicatorThickness / 2, 0, crossAxisSize - indicatorThickness)
            return { left: offset }
        } else {
            throw Error('Invalid "position" for vertical mode. Must be "left", "right", or a number (percentage).')
        }
    }
}
