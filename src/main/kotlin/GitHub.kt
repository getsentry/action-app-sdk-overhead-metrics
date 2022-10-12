import org.kohsuke.github.GHWorkflow
import org.kohsuke.github.GHWorkflowRun
import org.kohsuke.github.GitHubBuilder
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories

class GitHub {
    companion object {
        private val env = System.getenv()
        private val outputFile = env["GITHUB_OUTPUT"]
        private val token = env["GITHUB_TOKEN"]
        private val github =
            if (token.isNullOrEmpty()) null else GitHubBuilder().withOAuthToken(token).build()
        private val repo = github?.getRepository(env["GITHUB_REPOSITORY"]!!)
        private val workflow: GHWorkflow? by lazy {
            repo?.listWorkflows()?.single {
                // GITHUB_WORKFLOW: The name of the workflow. For example, My test workflow. If the workflow file doesn't
                // specify a name, the value of this variable is the full path of the workflow file in the repository.
                listOf(it.name, it.path).contains(env["GITHUB_WORKFLOW"]!!)
            }
        }

        fun writeOutput(name: String, value: Any) {
            if (!outputFile.isNullOrEmpty()) {
                FileOutputStream(outputFile, true).bufferedWriter().use { writer ->
                    writer.write(name)
                    writer.write('='.code)
                    writer.write(value.toString())
                    writer.write('\n'.code)
                }
            }
            println("Output $name=$value")
        }

        fun downloadPreviousArtifact(branch: String, targetDir: Path, artifactName: String) {
            targetDir.createDirectories()

            if (workflow == null) {
                println("Skipping previous artifact '$artifactName' download for branch '$branch' - not running in CI")
                return
            }
            println("Trying to download previous artifact '$artifactName' for branch '$branch'")

            val run = workflow!!.listRuns()
                .firstOrNull { it.headBranch == branch && it.conclusion == GHWorkflowRun.Conclusion.SUCCESS }
            if (run == null) {
                println("Couldn't find any successful run workflow ${workflow!!.name}")
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
}
