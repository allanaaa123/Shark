package com.example.sharkoverlay

/** Window alpha, not sprite alpha, determines Android 12+ untrusted-touch blocking.
 * All pets must share one window so overlapping animals do not stack opacity.
 * See WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.
 */
object OverlayTouchPolicy {
    fun windowAlpha(apiLevel: Int, systemMaximum: Float = 0.8f): Float {
        if (apiLevel < 31) return 1f
        val limit = if (systemMaximum.isFinite()) systemMaximum.coerceIn(0f, 0.8f) else 0.8f
        return (limit - 0.01f).coerceAtLeast(0f)
    }
}
