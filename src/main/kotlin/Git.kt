import java.io.BufferedReader
import java.io.InputStreamReader

class Git {
    companion object {
        val branch by lazy { executeCommand("git branch --show-current") }
        val baseBranch by lazy { System.getenv("GITHUB_BASE_REF") ?: defaultBranch }
        private val defaultBranch by lazy {
            executeCommand("git symbolic-ref refs/remotes/origin/HEAD").split("/").last()
        }
        val hash by lazy {
            var gitHash = executeCommand("git rev-parse HEAD")
            if (Runtime.getRuntime().exec("git diff --quiet HEAD").waitFor() != 0) {
                gitHash += "+dirty"
            }
            gitHash
        }
    }
}

fun executeCommand(command: String): String {
    val process = Runtime.getRuntime().exec(command)
    val reader = BufferedReader(InputStreamReader(process.inputStream))
    if (process.waitFor() == 0) {
        return reader.readText().trim()
    } else {
        throw Exception("Command '$command' exited with code ${process.waitFor()}")
    }
}
