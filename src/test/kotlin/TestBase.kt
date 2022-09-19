import io.appium.java_client.AppiumDriver
import org.apache.commons.configuration2.FileBasedConfiguration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import java.nio.file.Path
import java.util.logging.Level
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.notExists
import kotlin.io.path.pathString


abstract class TestBase(
    protected val baseOptions: TestOptions = TestOptions.instance
) {
    companion object {
        private const val resultsOutputFile = "out/latest-result.properties"

        private val resultsOutputBuilder by lazy {
            val filePath = Path.of(resultsOutputFile)
            if (filePath.notExists()) {
                filePath.parent.createDirectories()
                filePath.createFile()
            }
            FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration::class.java).configure(
                Parameters().properties().setFileName(filePath.pathString)
            )
        }
    }

    protected val apps = baseOptions.apps
    protected val logAppPrefix = "App %-${apps.maxOfOrNull { it.name.length }}s"

    @Suppress("NOTHING_TO_INLINE") // Inline ensures the logger prints the actual caller.
    protected inline fun printf(format: String, vararg args: Any?, logLevel: Level = Level.INFO) {
        baseOptions.logger.log(logLevel, String.format(format, *args))
    }

    protected fun writeOutput(name: String, value: Any) {
        val caller = Thread.currentThread().stackTrace[2]
        val fullName = String.format("%s.%s", caller.methodName, name)
        resultsOutputBuilder.configuration.setProperty(fullName, value)
        resultsOutputBuilder.save()
        printf("Output '%s' = '%s'", fullName, resultsOutputBuilder.configuration.getString(fullName))
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