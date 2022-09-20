import java.io.File

class PrCommentBuilder {
    private var buffer = StringBuilder()

    fun print(isCI: Boolean) {
        var body = buffer.toString()
        if (isCI) {
            body = body.replace("%", "%25")
            body = body.replace("\n", "%0A")
            body = body.replace("\r", "%0D")
            println("::set-output name=commentBody::$body")
            println("::set-output name=commentTitle::$title")
        } else {
            File("out/comment.html").writeText(body)
        }
    }

    private val title get() = "## ${System.getenv("RESULT_NAME") ?: ""} Performance metrics :rocket:"

    fun addCurrentResult(result: ResultFile) = addResult(title, result)

    private fun addResult(header: String, r: ResultFile) {
        buffer.append(
            """
          $header
          <table>
            <tr>
              <th>&nbsp;</th>
              <th align="right">Plain</th>
              <th align="right">With Sentry</th>
              <th align="right">Diff</th>
            </tr>
            <tr>
              <th align="left">Startup time</th>
              <td align="right">${r.getDecimal("startup time", "0")} ms</td>
              <td align="right">${r.getDecimal("startup time", "1")} ms</td>
              <td align="right"><strong>${r.getDecimal("startup time", "diff")} ms</strong></td>
            </tr>
            <tr>
              <th align="left">Size</th>
              <td align="right">${r.getBytes("app size", "0")}</td>
              <td align="right">${r.getBytes("app size", "1")}</td>
              <td align="right"><strong>${r.getBytes("app size", "diff")}</strong></td>
            </tr>
          </table>
        """.trimIndent()
        )
        buffer.append("\n")
    }

    private fun addDetailsTable(
        header: String, items: List<Pair<String, ResultFile>>, getValue: (r: ResultFile, key: String) -> String
    ) {
        buffer.append(
            """
          <h3>$header</h3>
          <table>
            <tr>
              <th>Revision</th>
              <th align="right">Plain</th>
              <th align="right">With Sentry</th>
              <th align="right">Diff</th>
            </tr>
            """.trimIndent()
        )
        items.forEach {
            buffer.append(
                """
                <tr>
                  <th align="left">${it.first}</th>
                  <td align="right">${getValue(it.second, "0")}</td>
                  <td align="right">${getValue(it.second, "1")}</td>
                  <td align="right"><strong>${getValue(it.second, "diff")}</strong></td>
                </tr>
                """.trimIndent()
            )
        }
        buffer.append("</table>\n")
    }

    fun addAdditionalResultsSet(name: String, results: ResultsSet) {
        if (results.count() == 0) {
            return
        }

        val items = results.items().take(10)

        buffer.append("<details><summary><h3>$name</h3></summary>\n")
        addDetailsTable("Startup times", items) { r, key -> r.getDecimal("startup time", key) + " ms" }
        addDetailsTable("App size", items) { r, key -> r.getBytes("app size", key) }
        buffer.append("</details>")
        buffer.append("\n")
    }
}
