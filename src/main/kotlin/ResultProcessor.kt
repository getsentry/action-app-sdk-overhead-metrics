import org.kohsuke.github.GHWorkflow
import org.kohsuke.github.GHWorkflowRun
import org.kohsuke.github.GitHubBuilder
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
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

        if (isCI) {
            println("::echo::on")
            println("::set-output name=artifactName::$artifactName")
            println("::set-output name=artifactPath::${previousResultsDir}")
        }

        val baselineResults = ResultsSet(baselineResultsDir)
        val resultMarkdown = PrCommentMarkdown(latestResults, baselineResults)
        resultMarkdown.print(isCI)

        if (isCI) {
            println("::echo::off")
        }

        // Copy the latest test run results to the archived result dir.
        val previousResults = ResultsSet(previousResultsDir)
        previousResults.add(latestResults.path, info = Git.hash)
    }

    private fun downloadResults() {
        baselineResultsDir.createDirectories()
        previousResultsDir.createDirectories()

        if (!env.containsKey("GITHUB_TOKEN")) {
            return
        }

        val github = GitHubBuilder().withOAuthToken(env["GITHUB_TOKEN"]!!).build()
        val repo = github.getRepository(env["GITHUB_REPOSITORY"]!!)
        val workflow = repo.getWorkflow(Path.of(env["GITHUB_WORKFLOW"]!!).name)
        downloadResultsFor(workflow, Git.baseBranch, baselineResultsDir)
        if (Git.baseBranch != Git.branch) {
            downloadResultsFor(workflow, Git.branch, previousResultsDir)
        }
    }

    private fun downloadResultsFor(workflow: GHWorkflow, branch: String, targetDir: Path) {
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

        println("Downloading artifact ${artifact.archiveDownloadUrl} and extracting to $targetDir")
        artifact.download {
            val zipStream = ZipInputStream(it)
            var entry: ZipEntry?
            // while there are entries I process them
            while (true) {
                entry = zipStream.nextEntry
                if (entry == null) {
                    break
                }
                if (entry.isDirectory) {
                    Path.of(entry.name).createDirectories()
                } else {
                    println("Extracting ${entry.name}")
                    val outFile = FileOutputStream(targetDir.resolve(entry.name).toFile())
                    while (zipStream.available() > 0) {
                        val c = zipStream.read()
                        if (c > 0) {
                            outFile.write(c)
                        } else {
                            break
                        }
                    }
                    outFile.close()
                }
            }
        }
    }
}
