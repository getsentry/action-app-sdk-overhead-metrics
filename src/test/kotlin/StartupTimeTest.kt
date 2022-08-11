import com.google.common.math.Quantiles
import com.google.common.math.Stats
import io.appium.java_client.android.Activity
import io.appium.java_client.android.AndroidDriver
import io.appium.java_client.ios.IOSDriver
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import kotlin.math.abs

@Suppress("UnstableApiUsage")
class StartupTimeTest : TestBase() {
    data class Options(val runs: Int, val diffMax: Int?, val diffMin: Int = 1, val stdDevMax: Int = 50)

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
                app.name, stats1.mean(), stats1.populationStandardDeviation(), times1.size
            )

            val times2 = filterOutliers(measuredTimes[j])
            val stats2 = Stats.of(times2)
            printf(
                "$logAppPrefix launch times (filtered) | mean: %3.2f ms | stddev: %3.2f | %d values: $times2",
                app.name, stats2.mean(), stats2.populationStandardDeviation(), times2.size
            )

            if (options.stdDevMax > 0) {
                stats2.populationStandardDeviation().shouldBeLessThan(options.stdDevMax.toDouble())
            }
            filteredTimes.add(times2)
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

            if (TestOptions.isCI) {
                println(String.format("::set-output name=StartTimeApp1::%.2f", means[0]))
                println(String.format("::set-output name=StartTimeApp2::%.2f", means[1]))
                println(String.format("::set-output name=StartTimeDiff::%.2f", diff))
            }
        }
    }

    private fun collectStartupTimes(): List<List<Long>> {
        val measuredTimes = mutableListOf<List<Long>>()

        withDriver { driver ->
            val runs = options.runs
            for (j in apps.indices) {
                val app = apps[j]

                // sleep before the first test to improve the first run time
                Thread.sleep(1_000)

                // clear logcat before test runs
                if (baseOptions.platform == TestOptions.Platform.Android) {
                    driver.manage().logs().get("logcat")
                }

                for (i in 1..runs) {
                    printf("$logAppPrefix collecting startup times: %d/%d", app.name, i, runs)

                    // kill the app and sleep before running the next iteration
                    when (baseOptions.platform) {
                        TestOptions.Platform.Android -> {
                            val androidDriver = (driver as AndroidDriver)
                            // Note: there's also .activateApp() which should be OS independent, but doesn't seem to wait for the activity to start
                            androidDriver.startActivity(Activity(app.name, app.activity))
                            androidDriver.terminateApp(app.name)
                        }

                        TestOptions.Platform.IOS -> {
                            val iosDriver = (driver as IOSDriver)
                            iosDriver.activateApp(app.name)
                            iosDriver.terminateApp(app.name)
                        }
                    }

                    // sleep before the next test run
                    Thread.sleep(sleepTimeMs)
                }

                val appTimes = when (baseOptions.platform) {
                    TestOptions.Platform.Android -> {
                        // Originally we used a code that loaded a list of executed Appium commands and used the time
                        // that the 'startActivity' command took. It seems like this time includes some overhead of the
                        // Appium controller because the times were about 900 ms, while the time reported in logcat
                        // was `ActivityManager: Displayed io.../.MainActivity: +276ms` or `... +1s40ms`
                        //   val times = driver.events.commands.filter { it.name == "startActivity" } .map { it.endTimestamp - it.startTimestamp }
                        //   val offset = j * runs
                        //   times.subList(offset, offset + runs)
                        val logEntries = driver.manage().logs().get("logcat")
                        val regex = Regex("Displayed ${app.name}/\\.${app.activity}: \\+(?:([0-9]+)s)?([0-9]+)ms")
                        logEntries.mapNotNull {
                            val groups = regex.find(it.message)?.groupValues
                            if (groups == null) {
                                null
                            } else {
                                val seconds = if (groups[1].isEmpty()) 0 else groups[1].toLong()
                                seconds * 1000 + groups[2].toLong()
                            }
                        }
                    }

                    TestOptions.Platform.IOS -> {
                        val times = driver.events.commands.filter { it.name == "activateApp" }
                            .map { it.endTimestamp - it.startTimestamp }
                        val offset = j * runs
                        times.subList(offset, offset + runs)
                    }
                }

                appTimes.size.shouldBe(runs)
                measuredTimes.add(appTimes)
            }
        }

        return measuredTimes
    }
}
