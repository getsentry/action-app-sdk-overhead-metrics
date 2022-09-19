class PrCommentMarkdown(private val result: ResultsFile, private val baselineResults: ResultsSet) {
    fun print(isCI: Boolean) {
        var body = build()
        if (isCI) {
            body = body.replace("%", "%25")
            body = body.replace("\n", "%0A")
            body = body.replace("\r", "%0D")
            println("::set-output name=commentBody::$body")
            println("::set-output name=commentTitle::$title")
        } else {
            println(body)
        }
    }

    private val title get() = "## ${System.getenv("RESULT_NAME") ?: ""} Performance metrics :rocket:"

    fun build(): String {
        return """
          $title
          |                   |                                         Plain  |                                   With Sentry  |                                                 Diff  |
          |-------------------|----------------------------------------------: |----------------------------------------------: |-----------------------------------------------------: |
          | Startup time (ms) | ${result.get("startup time", "0")} | ${result.get("startup time", "1")} | **${result.get("startup time", "diff")}** |
          | Size (bytes)      | ${result.get("app size", "0")}     | ${result.get("app size", "1")}     | **${result.get("app size", "diff")}**     |
        """.trimIndent()
    }
}