import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.system.exitProcess

fun main() = ResultProcessor().main()

class ResultProcessor {
    private val env = System.getenv()
    private val artifactName =
        "app-sdk-metrics-results" + (if (env["RESULT_NAME"].isNullOrEmpty()) "" else "-${env["RESULT_NAME"]}")

    /// Previous results of the runs on the same branch.
    private val previousResultsDir = Path.of("out/results")

    /// Results from the git PR target branch or the repo base branch.
    private val baselineResultsDir = Path.of("out/baseline-results")

    fun main() {
        val latestResults = ResultFile()
        if (!latestResults.path.exists()) {
            println("Can't process latest results - the file doesn't exist: ${latestResults.path}")
            exitProcess(1)
        }

        GitHub.downloadPreviousArtifact(Git.baseBranch, baselineResultsDir, artifactName)
        GitHub.downloadPreviousArtifact(Git.branch, previousResultsDir, artifactName)

        GitHub.writeOutput("artifactName", artifactName)
        GitHub.writeOutput("artifactPath", previousResultsDir.absolutePathString())

        val prComment = PrCommentBuilder()
        prComment.addCurrentResult(latestResults)
        if (Git.baseBranch != Git.branch) {
            prComment.addAdditionalResultsSet(
                "Baseline results on branch: ${Git.baseBranch}",
                ResultsSet(baselineResultsDir)
            )
        }
        prComment.addAdditionalResultsSet(
            "Previous results on branch: ${Git.branch}",
            ResultsSet(previousResultsDir)
        )

        GitHub.addOrUpdateComment(prComment)

        // Copy the latest test run results to the archived result dir.
        ResultsSet(previousResultsDir).add(latestResults.path, info = Git.hash)
    }
}
