package io.github.arglax.wuwalab.util

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Formats an Argstrite (or any large in-app currency) count for display.
 *
 * Below 100,000 the exact number is shown (with thousands separators via
 * ["%,d"]) since that range is still easy to read and precision matters at
 * shop-price scale. From 100,000 up, it switches to a suffixed short form -
 * K (thousand), M (million), B (billion), T (trillion) - with up to one
 * decimal place, dropping a trailing ".0" so "250K" reads cleaner than
 * "250.0K". This only ever affects DISPLAY; the real Int balance stored and
 * spent is never touched.
 *
 * Examples: 999 -> "999", 12345 -> "12,345", 250000 -> "250K",
 * 1500000 -> "1.5M", 100000000 -> "100M".
 */
fun formatArgstrites(amount: Int): String {
    val suffixThreshold = 100_000
    val n = abs(amount.toLong())
    if (n < suffixThreshold) return "%,d".format(amount)

    val suffixes = listOf("", "K", "M", "B", "T")
    // Which suffix tier n falls into: 0 = units, 1 = thousands, 2 = millions...
    val tier = (floor(log10(n.toDouble())) / 3).toInt().coerceIn(0, suffixes.size - 1)
    if (tier == 0) return "%,d".format(amount)

    val scaled = n / 10.0.pow(tier * 3)
    val rounded = (scaled * 10).toInt() / 10.0
    val text = if (rounded == rounded.toInt().toDouble()) {
        rounded.toInt().toString()
    } else {
        "%.1f".format(rounded)
    }
    val sign = if (amount < 0) "-" else ""
    return sign + text + suffixes[tier]
}