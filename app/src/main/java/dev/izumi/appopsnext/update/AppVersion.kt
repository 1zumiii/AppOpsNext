package dev.izumi.appopsnext.update

/**
 * Compares release names like `1.3.3` or `v1.3.10`.
 *
 * Numeric segments are compared as numbers, so 1.3.10 sorts after 1.3.9, which
 * a plain string comparison would get backwards. Anything that is not a plain
 * number is treated as older than the same version without it, so `1.4.0-rc1`
 * does not present itself as newer than `1.4.0`.
 */
object AppVersion {
    fun isNewer(candidate: String, current: String): Boolean =
        compare(candidate, current) > 0

    internal fun compare(left: String, right: String): Int {
        val leftParts = segments(left)
        val rightParts = segments(right)
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val a = leftParts.getOrNull(index)
            val b = rightParts.getOrNull(index)
            val result = compareSegment(a, b)
            if (result != 0) return result
        }
        return 0
    }

    private fun compareSegment(left: Segment?, right: Segment?): Int = when {
        left == null && right == null -> 0
        // A missing segment means a shorter version, which is the release.
        left == null -> if (right!!.isNumeric) -1 else 1
        right == null -> if (left.isNumeric) 1 else -1
        left.isNumeric && right.isNumeric -> left.number.compareTo(right.number)
        left.isNumeric -> 1
        right.isNumeric -> -1
        else -> left.text.compareTo(right.text)
    }

    private fun segments(value: String): List<Segment> =
        value
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .split('.', '-', '+', '_')
            .filter(String::isNotEmpty)
            .map { part ->
                val number = part.toLongOrNull()
                if (number != null) Segment(number, part, true)
                else Segment(0, part, false)
            }

    private data class Segment(
        val number: Long,
        val text: String,
        val isNumeric: Boolean,
    )
}
