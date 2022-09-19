import org.kohsuke.github.GHWorkflow
import org.kohsuke.github.GHWorkflowRun
import org.kohsuke.github.GitHubBuilder
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.system.exitProcess

fun main() = ResultProcessor().main()

class ResultProcessor {
    private val env = System.getenv()
    private val isCI = env.containsKey("CI")
    private val artifactName =
        "app-sdk-metrics-results" + (if (env["RESULT_NAME"].isNullOrEmpty()) "" else "-${env["RESULT_NAME"]}")

    /// Previous results of the runs on the same branch.
    private val previousResultsDir = Path.of("out/results")

    /// Results from the git PR target branch or the repo base branch.
    private val baselineResultsDir = Path.of("out/baseline-results")

    fun main() {
        val latestResults = ResultsFile()
        if (!latestResults.path.exists()) {
            println("Can't process latest results - the file doesn't exist: ${latestResults.path}")
            exitProcess(1)
        }

        downloadResults()

        val previousResultsSet = ResultsSet(previousResultsDir)
        previousResultsSet.add(latestResults.path, info = Git.hash)

        val resultMarkdown = PrCommentMarkdown(latestResults)

        if (isCI) {
            println("::echo::on")
            println("::set-output name=artifactName::$artifactName")
            println("::set-output name=artifactPath::${previousResultsDir}")
            resultMarkdown.printGitHubActionsOutputs()
            println("::echo::off")
        } else {
            println(resultMarkdown.build())
        }
    }

    private fun downloadResults() {
        if (!env.containsKey("GITHUB_TOKEN")) {
            return
        }

        val github = GitHubBuilder().withOAuthToken(env["GITHUB_TOKEN"]!!).build()
        val repo = github.getRepository(env["GITHUB_REPOSITORY"]!!)
        val workflow = repo.getWorkflow(Path.of(env["GITHUB_WORKFLOW"]!!).name)
        downloadResultsFor(workflow, Git.baseBranch)
        if (Git.baseBranch != Git.branch) {
            downloadResultsFor(workflow, Git.baseBranch)
        }
    }

    private fun downloadResultsFor(workflow: GHWorkflow, branch: String) {
        println("Trying to download previous results for branch $branch")

        val run = workflow.listRuns()
            .firstOrNull { it.headBranch == branch && it.conclusion == GHWorkflowRun.Conclusion.SUCCESS }
        if (run == null) {
            println("Couldn't find any successful run workflow ${workflow.name}")
            return
        }

        val artifact = run.listArtifacts().firstOrNull { it.name == artifactName }
        if (artifact == null) {
            println("Couldn't find any artifact matching $artifactName")
            return
        }

        // TODO
    }
}
