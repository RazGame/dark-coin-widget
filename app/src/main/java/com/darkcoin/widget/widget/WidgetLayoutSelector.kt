package com.darkcoin.widget.widget

/** Bounds are dp, including the XML padding and 48dp controls, with extra room for large fonts. */
object WidgetLayoutSelector {
    enum class Layout { DASHBOARD, STRIP, TALL, COMPACT, SHORT, SCAN_ONLY }
    data class Spec(val layout: Layout, val width: Int, val height: Int)
    fun compactHeight(fontScale: Float) = (136 * fontScale.coerceAtLeast(1f)).toInt()
    fun dashboardHeight(fontScale: Float) = (200 * fontScale.coerceAtLeast(1f)).toInt()
    fun tallHeight(fontScale: Float) = (280 * fontScale.coerceAtLeast(1f)).toInt()

    fun sizes(fontScale: Float) = listOf(
        Spec(Layout.SCAN_ONLY, 64, 64),
        Spec(Layout.SHORT, 140, 64),
        Spec(Layout.COMPACT, 140, compactHeight(fontScale)),
        Spec(Layout.TALL, 140, tallHeight(fontScale)),
        Spec(Layout.STRIP, (280 * fontScale.coerceAtLeast(1f)).toInt(), 70),
        Spec(Layout.DASHBOARD, (300 * fontScale.coerceAtLeast(1f)).toInt(), dashboardHeight(fontScale)),
        // A tall, wide widget needs a portrait scan card, not two elongated dashboard columns.
        Spec(Layout.TALL, (300 * fontScale.coerceAtLeast(1f)).toInt(), (320 * fontScale.coerceAtLeast(1f)).toInt())
    )

    // RemoteViews(Map) also chooses the fitting layout with the largest area.
    fun select(width: Int, height: Int, fontScale: Float = 1f): Layout = sizes(fontScale)
        .filter { it.width <= width && it.height <= height }
        .maxByOrNull { it.width * it.height }?.layout ?: Layout.SCAN_ONLY
}
