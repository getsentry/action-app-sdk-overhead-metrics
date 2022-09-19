import java.nio.file.Path
import kotlin.io.path.*

/// Wraps a directory containing multiple (N-*-result.properties) files.
/// The files are numbered from the most recently added one, to the oldest one.
class ResultsSet(val directory: Path) {
    companion object {
        private const val delimiter = "-"
    }

    fun add(file: Path, info: String?) {
        println("Preparing to add $file to $directory")
        if (directory.notExists()) {
            directory.createDirectories()
        } else {
            // rename all existing files, increasing the prefix
            val files = directory.listDirectoryEntries().filter { it.isRegularFile() }.sortedByDescending {
                it.name.split(delimiter).first().toInt()
            }
            files.forEach {
                val parts = it.name.split(delimiter).toMutableList()
                parts[0] = (parts[0].toInt() + 1).toString()
                val newPath = it.resolveSibling(parts.joinToString(delimiter))
                println("Renaming $it to $newPath")
                it.moveTo(newPath)
            }
        }
        val newName = "1$delimiter" + (if (info == null) "" else "$info$delimiter") + "result.properties"
        println("Adding $file to $directory as $newName")
        file.copyTo(directory.resolve(newName))
    }
}
