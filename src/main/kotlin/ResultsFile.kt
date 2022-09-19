import org.apache.commons.configuration2.FileBasedConfiguration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.notExists

class ResultsFile(val path: Path = Path.of(latestResultsFilePath)) {
    companion object {
        const val latestResultsFilePath = "out/latest-result.properties"
    }

    fun set(scope: String, name: String, value: Any) = contents.setProperty(fullKey(scope, name), value)

    fun get(scope: String, name: String): String = contents.getString(fullKey(scope, name), "")

    private fun fullKey(scope: String, name: String) = "$scope.$name"

    private val contents: FileBasedConfiguration get() = builder.configuration

    val builder: FileBasedConfigurationBuilder<FileBasedConfiguration> by lazy {
        if (path.notExists()) {
            path.parent.createDirectories()
            path.createFile()
        }
        FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration::class.java).configure(
            Parameters().properties().setFileName(path.toString())
        )
    }
}
