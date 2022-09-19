import io.appium.java_client.AppiumDriver
import java.util.logging.Level


abstract class TestBase(
    protected val baseOptions: TestOptions = TestOptions.instance
) {
    protected val apps = baseOptions.apps
    protected val logAppPrefix = "App %-${apps.maxOfOrNull { it.name.length }}s"
    private val results = ResultsFile()

    @Suppress("NOTHING_TO_INLINE") // Inline ensures the logger prints the actual caller.
    protected inline fun printf(format: String, vararg args: Any?, logLevel: Level = Level.INFO) {
        baseOptions.logger.log(logLevel, String.format(format, *args))
    }

    protected fun writeOutput(name: String, value: Any) {
        val caller = Thread.currentThread().stackTrace[2]
        val fullName = String.format("%s.%s", caller.methodName, name)
        results.contents.setProperty(fullName, value)
        results.builder.save()
        printf("Output '%s' = '%s'", fullName, results.contents.getString(fullName))
    }

    protected fun withDriver(cb: (driver: AppiumDriver) -> Any) {
        val driver = baseOptions.createDriver()
        try {
            cb(driver)
        } finally {
            driver.quit()
        }
    }
}
