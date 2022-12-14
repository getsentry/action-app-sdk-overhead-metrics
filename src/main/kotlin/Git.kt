import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class Git {
    companion object {
        private val env = System.getenv()
        val branch: String by lazy {
            if (!env["GITHUB_HEAD_REF"].isNullOrEmpty()) {
                env["GITHUB_HEAD_REF"]!!
            } else if (env["GITHUB_REF"]?.startsWith("refs/heads/") == true) {
                env["GITHUB_REF"]!!.removePrefix("refs/heads/")
            } else {
                executeCommand("git branch --show-current")
            }
        }

        val baseBranch by lazy {
            val baseRef = env["GITHUB_BASE_REF"]
            if (baseRef.isNullOrEmpty()) defaultBranch else baseRef
        }

        val repository by lazy {
            var repo = env["GITHUB_REPOSITORY"]
            if (repo.isNullOrEmpty()) {
                repo = executeCommand("git remote get-url origin")
                repo = repo!!.removePrefix("git@").removeSuffix(".git")
                repo!!.replace(':', '/')
            } else {
                "github.com/$repo"
            }
        }

        private val workingDirectory =
            if (System.getenv().containsKey("CI")) File(System.getenv("GITHUB_WORKSPACE")!!) else null

        private val defaultBranch by lazy {
            Regex("HEAD branch: *(.*)").find(executeCommand("git remote show origin"))!!.groupValues[1]
        }

        val hash by lazy {
            var gitHash = executeCommand("git rev-parse HEAD")
            if (exec("git diff --quiet HEAD").waitFor() != 0) {
                gitHash += "+dirty"
            }
            gitHash
        }

        private fun exec(command: String) = Runtime.getRuntime().exec(command, null, workingDirectory)

        private fun executeCommand(command: String): String {
            val process = exec(command)
            val stdOut = BufferedReader(InputStreamReader(process.inputStream))
            val stdErr = BufferedReader(InputStreamReader(process.errorStream))
            if (process.waitFor() == 0) {
                return stdOut.readText().trim()
            } else {
                throw Exception("Command '$command' exited with code ${process.waitFor()}\n${stdErr.readText().trim()}")
            }
        }
    }
}
