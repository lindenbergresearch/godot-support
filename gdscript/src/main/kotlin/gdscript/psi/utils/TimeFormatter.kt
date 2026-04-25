package gdscript.psi.utils

import java.util.*

/**
 * Supported time units with conversion factor from nanoseconds.
 */
enum class TimeUnit(val suffix: String, val nanosFactor: Double) {
    NANOSECONDS("ns", 1.0),
    MICROSECONDS("µs", 1_000.0),
    MILLISECONDS("ms", 1_000_000.0),
    SECONDS("s", 1_000_000_000.0)
}


/**
 * Formats a duration given in nanoseconds into a human-readable string.
 *
 * @param nanos      Duration in nanoseconds
 * @param unit       Target time unit
 * @param decimals   Number of decimal places
 * @param totalWidth Optional total width of the resulting string (including unit)
 * @param padChar    Padding character (' ' or '0')
 *
 * @return Formatted duration string
 */
fun formatDuration(
    nanos: Long,
    unit: TimeUnit,
    decimals: Int = 2,
    totalWidth: Int? = null,
    padChar: Char = ' '
): String {

    require(decimals >= 0) { "decimals must be >= 0" }
    require(padChar == ' ' || padChar == '0') { "padChar must be ' ' or '0'" }

    val value = nanos.toDouble() / unit.nanosFactor

    val numberFormat = "%.${decimals}f"
    val formattedNumber = String.format(Locale.US, numberFormat, value)

    val result = formattedNumber + unit.suffix

    if (totalWidth == null || result.length >= totalWidth) {
        return result
    }

    val padding = padChar.toString().repeat(totalWidth - result.length)
    return padding + result
}

fun Long.toTimeString(width: Int = 9, unit: TimeUnit = TimeUnit.MILLISECONDS, dec: Int = 3): String {
    return formatDuration(this, unit, dec, width)
}