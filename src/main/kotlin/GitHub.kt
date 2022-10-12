import java.io.FileOutputStream

class GitHub {
    companion object {
        private val outputFile = System.getenv("GITHUB_OUTPUT")

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
    }
}
