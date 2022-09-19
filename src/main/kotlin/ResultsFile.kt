import org.apache.commons.configuration2.FileBasedConfiguration
import org.apache.commons.configuration2.PropertiesConfiguration
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder
import org.apache.commons.configuration2.builder.fluent.Parameters
import java.nio.file.Path
import kotlin.io.path.*

class ResultsFile {
    var path: Path = Path.of(latestResultsFilePath)
        private set

    companion object {
        const val latestResultsFilePath = "out/latest-result.properties"
    }

    fun exists() = path.exists()

    val contents: FileBasedConfiguration get() = builder.configuration

    val builder: FileBasedConfigurationBuilder<FileBasedConfiguration> by lazy {
        if (path.notExists()) {
            path.parent.createDirectories()
            path.createFile()
        }
        FileBasedConfigurationBuilder<FileBasedConfiguration>(PropertiesConfiguration::class.java).configure(
            Parameters().properties().setFileName(path.pathString)
        )
    }
}
