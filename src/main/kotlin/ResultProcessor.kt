import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.system.exitProcess

val isCI = System.getenv().containsKey("CI")

fun main() {
    val latestResults = ResultsFile()
    if (!latestResults.path.exists()) {
        println("Can't process latest results - the file doesn't exist: ${latestResults.path}")
        exitProcess(1)
    }

    val previousResultsSet = ResultsSet(Path.of("out/previous"))
    previousResultsSet.add(latestResults.path, info = Git.hash)

    val resultMarkdown = PrCommentMarkdown(latestResults)
    if (isCI) {
        resultMarkdown.printGitHubActionsOutputs()
    } else {
        println(resultMarkdown.build())
    }
}
