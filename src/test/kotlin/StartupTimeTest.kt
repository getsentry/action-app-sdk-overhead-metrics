import com.google.common.math.Quantiles
import com.google.common.math.Stats
import io.appium.java_client.AppiumDriver
import io.appium.java_client.android.Activity
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
import kotlin.math.ceil

@Suppress("UnstableApiUsage")
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
            for (appIndex in apps.indices) {
                val app = apps[appIndex]
                val needsTimes = options.runs - ceil(options.runs * 0.1).toInt();

                for (retry in 1..options.retries) {
                    val appStartCounter = AtomicInteger(0) // needed for iOS time collection
                    if (retry > 1) {
                        printf(
                            "$logAppPrefix retrying startup time collection: %d/%d",
                            app.name,
                            retry,
                            options.retries
                        )
                    }

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

                    if (appTimes.size == 0 || appTimes.size < needsTimes) {
                        printf(
                            "$logAppPrefix not enough startup times collected: %d/%d. Expected at least %d",
                            app.name,
                            appTimes.size,
                            options.runs,
                            needsTimes
                        )
                        continue
                    }
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
                            continue
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
            when (baseOptions.platform) {
                TestOptions.Platform.Android -> {
                    val androidDriver = (driver as AndroidDriver)
                    printf("%s", "${app.name} is installed: ${driver.isAppInstalled(app.name)}")

                    // Note: there's also .activateApp() which should be OS independent, but doesn't seem to wait for the activity to start
                    try {
                        androidDriver.startActivity(Activity(app.name, app.activity))
                    } catch (e: Exception) {
                        // in case the app can't be launched or crashes on startup, print logcat output
                        val logs = driver.manage().logs().get("logcat").all.joinToString("\n")
                        printf("%s", logs)
                        throw(e)
                    }
                    androidDriver.terminateApp(app.name)

                    // Originally we used a code that loaded a list of executed Appium commands and used the time
                    // that the 'startActivity' command took. It seems like this time includes some overhead of the
                    // Appium controller because the times were about 900 ms, while the time reported in logcat
                    // was `ActivityManager: Displayed io.../.MainActivity: +276ms` or `... +1s40ms`
                    //   val times = driver.events.commands.filter { it.name == "startActivity" } .map { it.endTimestamp - it.startTimestamp }
                    //   val offset = j * runs
                    //   times.subList(offset, offset + runs)
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
                    if (times.size == 1) {
                        appTimes.add(times.first())
                    } else {
                        printf("Expected 1 startup time in logcat, matching Regex '%s', but found %d", regex.pattern, times.size)
                    }
                }

                TestOptions.Platform.IOS -> {
                    val iosDriver = (driver as IOSDriver)
                    iosDriver.activateApp(app.name)
                    iosDriver.terminateApp(app.name)

                    val times = driver.events.commands.filter { it.name == "activateApp" }
                        .map { it.endTimestamp - it.startTimestamp }

                    val count = counter.incrementAndGet();
                    if (times.size == count) {
                        appTimes.add(times.last())
                    } else {
                        printf("Expected %d activateApp events, but found %d", count, times.size)
                    }
                }
            }

            // sleep before the next test run
            Thread.sleep(sleepTimeMs)
        }
        return appTimes
    }
}
