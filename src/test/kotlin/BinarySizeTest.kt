import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class BinarySizeTest : TestBase() {
    data class Options(val diffMin: String?, val diffMax: String?)

    private val options get() = baseOptions.binarySizeTest!!

    @Test
    fun `app size`() {
        assumeTrue(baseOptions.binarySizeTest != null)

        val sizes = apps.map { Files.size(it.file) }.toList()
        for (j in apps.indices) {
            printf("$logAppPrefix size is %s", apps[j].name, ByteUtils.human(sizes[j]))
            writeOutput(j.toString(), sizes[j])
        }

        if (apps.size == 2) {
            val diff = sizes[1] - sizes[0]
            printf(
                "$logAppPrefix is %s %s than app %s",
                apps[1].name,
                ByteUtils.human(diff),
                if (diff >= 0) "larger" else "smaller",
                apps[0].name
            )

            // fail if the added size is not within the expected range
            if (options.diffMin != null) {
                diff.shouldBeGreaterThan(ByteUtils.parse(options.diffMin!!))
            }
            if (options.diffMax != null) {
                diff.shouldBeLessThan(ByteUtils.parse(options.diffMax!!))
            }
            writeOutput("diff", diff)
        }
    }
}
