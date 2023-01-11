import org.kohsuke.github.*
import java.io.File
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
        private val repo: GHRepository? by lazy {
            if (Git.repository.startsWith("github.com/")) {
                github?.getRepository(Git.repository.removePrefix("github.com/"))
            } else {
                null
            }
        }
        private val workflow: GHWorkflow? by lazy {
            if (env.containsKey("GITHUB_WORKFLOW")) {
                repo?.listWorkflows()?.single {
                    // GITHUB_WORKFLOW: The name of the workflow. For example, My test workflow. If the workflow file doesn't
                    // specify a name, the value of this variable is the full path of the workflow file in the repository.
                    listOf(it.name, it.path).contains(env["GITHUB_WORKFLOW"]!!)
                }
            } else {
                null
            }
        }
        private val pullRequest: GHPullRequest? by lazy {
            // This is only set if a branch or tag is available for the event type.
            // The ref given is fully-formed, meaning that
            // * for branches the format is refs/heads/<branch_name>,
            // * for pull requests it is refs/pull/<pr_number>/merge,
            // * and for tags it is refs/tags/<tag_name>.
            // For example, refs/heads/feature-branch-1.
            val ref = env["GITHUB_REF"]
            if (repo == null) {
                null
            } else if (ref?.startsWith("refs/pull/") == true) {
                val prNumber = ref.split('/')[2].toInt()
                println("Fetching GitHub PR by number: $prNumber")
                repo!!.getPullRequest(prNumber)
            } else {
                repo!!.queryPullRequests().base(Git.baseBranch).head(Git.branch).list().firstOrNull()
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

            if (artifact.isExpired) {
                println("Couldn't download artifact ${artifact.name} - it has expired on ${artifact.expiresAt}")
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

        fun addOrUpdateComment(commentBuilder: PrCommentBuilder) {
            if (pullRequest == null) {
                val file = File("out/comment.html")
                println("No PR available (not running in CI?): writing built comment to ${file.absolutePath}")
                file.writeText(commentBuilder.body)
            } else {
                val comments = pullRequest!!.comments
                // Trying to fetch `github!!.myself` throws (in CI only): Exception in thread "main" org.kohsuke.github.HttpException:
                //   {"message":"Resource not accessible by integration","documentation_url":"https://docs.github.com/rest/reference/users#get-the-authenticated-user"}
                // Let's make this conditional on some env variable that's unlikely to be set.
                // Do not use "CI" because that's commonly set during local development and testing.
                val author = if(env.containsKey("GITHUB_ACTION")) "github-actions[bot]" else github!!.myself.login
                val comment = comments.firstOrNull {
                    it.user.login.equals(author) &&
                            it.body.startsWith(commentBuilder.title, ignoreCase = true)
                }
                if (comment != null) {
                    println("Updating PR comment ${comment.htmlUrl} body")
                    comment.update(commentBuilder.body)
                } else {
                    println("Adding new PR comment to ${pullRequest!!.htmlUrl}")
                    pullRequest!!.comment(commentBuilder.body)
                }
            }
        }
    }
}
