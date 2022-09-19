import java.nio.file.Path
import kotlin.system.exitProcess

fun main() {
    val latestResults = ResultsFile()
    if (!latestResults.exists()) {
        println("Can't process latest results - the file doesn't exist: ${latestResults.path}")
        exitProcess(1)
    }

    val previousResultsSet = ResultsSet(Path.of("out/previous"))
    previousResultsSet.add(latestResults.path, info = Git.hash)
}
