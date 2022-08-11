import io.ktor.util.*
import java.lang.Long.signum
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

class ByteUtils {
    companion object {
        private val sizeChars = arrayOf('K', 'M', 'G', 'T', 'P', 'E')
        private val parseRegex = Regex("^([0-9]+(?:\\.[0-9]+)?) *([a-zA-Z]+)?$")

        fun human(bytes: Long): String {
            val absValue = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else abs(bytes)
            if (absValue < 1024) {
                return "$absValue B"
            }
            var i = 40
            var c = 0
            var value = absValue
            while (i >= 0 && absValue > 0xfffccccccccccccL shr i) {
                value = value shr 10
                c++
                i -= 10
            }
            value *= signum(bytes).toLong()
            return String.format("%.2f %ciB", value / 1024.0, sizeChars[c])
        }

        fun parse(humanReadable: String): Long {
            val match = parseRegex.find(humanReadable)
                ?: throw IllegalArgumentException("Couldn't parse human-readable binary size: '$humanReadable' - doesn't match RegEx '$parseRegex'")

            val groups = match.groupValues
            var value = groups[1].toDouble()
            if (groups.size > 2) {
                val unit = groups[2].toUpperCasePreservingASCIIRules()
                if (unit[0] != 'B') {
                    val index = sizeChars.indexOf(unit[0])
                    if (index < 0) {
                        throw IllegalArgumentException("Couldn't parse human-readable binary size: '$humanReadable' - unknown unit")
                    }
                    val coef = if (unit.length > 1 && unit[1] == 'I') 1024 else 1000
                    value *= coef.toDouble().pow(index + 1.0)
                }
            }
            return value.roundToLong()
        }
    }
}
