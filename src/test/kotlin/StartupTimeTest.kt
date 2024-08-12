import com.google.common.collect.ImmutableMap
import com.google.common.math.Quantiles
import com.google.common.math.Stats
import io.appium.java_client.AppiumDriver
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.ios.IOSDriver
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class StartupTimeTest : TestBase() {
    data class Options(
        val runs: Int, val diffMax: Int?, val diffMin: Int = 1, val stdDevMax: Int = 50, val retries: Int = 5
    )

    private val options get() = baseOptions.startupTimeTest!!

    companion object {
        const val sleepTimeMs: Long = 300

        // See https://en.wikipedia.org/wiki/Interquartile_range#Outliers for details
        private fun filterOutliers(list: List<Long>): List<Long> {
            // sort array (as numbers)
            val sorted = list.sorted()

            val q1 = Quantiles.percentiles().index(25).compute(sorted)
            val q3 = Quantiles.percentiles().index(75).compute(sorted)
            val iqr = q3 - q1

            return list.filter { it.toDouble() in (q1 - 1.5 * iqr)..(q3 + 1.5 * iqr) }
        }
    }

    @Test
    fun `startup time`() {
        Assumptions.assumeTrue(baseOptions.startupTimeTest != null)

        val measuredTimes = collectStartupTimes()
        val filteredTimes = mutableListOf<List<Long>>()

        for (j in apps.indices) {
            val app = apps[j]

            val times1 = measuredTimes[j]
            val stats1 = Stats.of(times1)
            printf(
                "$logAppPrefix launch times (original) | mean: %3.2f ms | stddev: %3.2f | %d values: $times1",
                app.name,
                stats1.mean(),
                stats1.populationStandardDeviation(),
                times1.size
            )

            val times2 = filterOutliers(measuredTimes[j])
            val stats2 = Stats.of(times2)
            printf(
                "$logAppPrefix launch times (filtered) | mean: %3.2f ms | stddev: %3.2f | %d values: $times2",
                app.name,
                stats2.mean(),
                stats2.populationStandardDeviation(),
                times2.size
            )

            if (options.stdDevMax > 0) {
                stats2.populationStandardDeviation().shouldBeLessThan(options.stdDevMax.toDouble())
            }
            filteredTimes.add(times2)
            writeOutput(j.toString(), stats2.mean())
        }

        if (apps.size == 2) {
            val means = filteredTimes.map { Stats.meanOf(it) }
            val diff = means[1] - means[0]
            printf(
                "$logAppPrefix takes approximately %3f ms %s time to start than app %s",
                apps[1].name,
                abs(diff),
                if (diff >= 0) "more" else "less",
                apps[0].name
            )

            // fail if the slowdown is not within the expected range
            diff.shouldBeGreaterThan(options.diffMin.toDouble())
            if (options.diffMax != null) {
                diff.shouldBeLessThan(options.diffMax!!.toDouble())
            }
            writeOutput("diff", diff)
        }
    }

    private fun collectStartupTimes(): List<List<Long>> {
        val measuredTimes = mutableListOf<List<Long>>()

        withDriver { driver ->
            val appStartCounter = AtomicInteger(0) // needed for iOS time collection
            for (appIndex in apps.indices) {
                val app = apps[appIndex]

                for (retry in 1..options.retries) {
                    // sleep before the first test to improve the first run time
                    Thread.sleep(1_000)

                    // clear logcat before test runs
                    if (baseOptions.platform == TestOptions.Platform.Android) {
                        driver.manage().logs().get("logcat")
                    }

                    val appTimes = collectAppStartupTimes(app, driver, appStartCounter)
                    printf(
                        "$logAppPrefix collected %d/%d startup times (try %d/%d)",
                        app.name,
                        appTimes.size,
                        options.runs,
                        retry,
                        options.retries
                    )
                    appTimes.size.shouldBe(options.runs)

                    if (options.stdDevMax > 0) {
                        val stdDev = Stats.of(filterOutliers(appTimes)).populationStandardDeviation()
                        if (stdDev > options.stdDevMax.toDouble()) {
                            printf(
                                "$logAppPrefix stddev on the filtered startup-time list is too high: %.2f, expected %.2f at most.",
                                app.name,
                                stdDev,
                                options.stdDevMax.toDouble()
                            )
                            if (retry < options.retries) {
                                printf(
                                    "$logAppPrefix retrying startup time collection: %d/%d",
                                    app.name,
                                    retry + 1,
                                    options.retries
                                )
                                continue
                            }
                        }
                    }

                    measuredTimes.add(appTimes)
                    break
                }
            }
        }

        return measuredTimes
    }

    private fun collectAppStartupTimes(app: AppInfo, driver: AppiumDriver, counter: AtomicInteger): MutableList<Long> {
        val appTimes = mutableListOf<Long>()
        for (i in 1..options.runs) {
            printf("$logAppPrefix measuring startup times: %d/%d", app.name, i, options.runs)

            // kill the app and sleep before running the next iteration
            val startupTime = when (baseOptions.platform) {
                TestOptions.Platform.Android -> {
                    val androidDriver = (driver as AndroidDriver)
                    printf("%s", "${app.name} is installed: ${driver.isAppInstalled(app.name)}")

                    try {
                        val result = androidDriver.executeScript("mobile: startActivity",
                            ImmutableMap.of("intent", "${app.name}/.${app.activity!!}", "wait", true)).toString()
                        val error = Regex("Error: (.*)").find(result)?.groupValues
                        if (error != null) {
                            throw Exception(error[0])
                        }
                    } catch (e: Exception) {
                        // in case the app can't be launched or crashes on startup, print logcat output
                        val logs = driver.manage().logs().get("logcat").all.joinToString("\n")
                        printf("%s", logs)
                        throw(e)
                    }

                    androidDriver.terminateApp(app.name).shouldBe(true)

                    val logEntries = driver.manage().logs().get("logcat")
                    val regex = Regex("Displayed ${app.name}/\\.${app.activity}: \\+(?:([0-9]+)s)?([0-9]+)ms")
                    val times = logEntries.mapNotNull {
                        val groups = regex.find(it.message)?.groupValues
                        if (groups == null) {
                            null
                        } else {
                            printf("$logAppPrefix logcat entry processed: %s", app.name, it.message)
                            val seconds = if (groups[1].isEmpty()) 0 else groups[1].toLong()
                            seconds * 1000 + groups[2].toLong()
                        }
                    }
                    times.shouldHaveSize(1)
                    times.first()
                }

                TestOptions.Platform.IOS -> {
                    val iosDriver = (driver as IOSDriver)
                    iosDriver.activateApp(app.name)
                    // Note: with Appium 9 we can no longer filter by actual command name, see https://github.com/appium/java-client/issues/2219
                    val times = driver.events.commands
                        .filter { it.name == "execute" }
                        .map { it.endTimestamp - it.startTimestamp }
                    times.shouldHaveSize(counter.incrementAndGet())
                    iosDriver.terminateApp(app.name)
                    counter.incrementAndGet()
                    times.last()
                }
            }
            appTimes.add(startupTime)

            // sleep before the next test run
            Thread.sleep(sleepTimeMs)
        }
        return appTimes
    }
}
