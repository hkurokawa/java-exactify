import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

fun main(args: Array<String>) {
  val transformer = Transformer()

  if (args.isEmpty()) {
    val reader = InputStreamReader(System.`in`)
    val writer = OutputStreamWriter(System.out)
    transformer.transform(reader, writer)
    writer.flush()
  } else {
    var n = args.size
    val toReplace = args[0] == "-r"
    if (toReplace) n--
    val files = args.takeLast(n).map { arg -> Paths.get(arg) }

    if (toReplace) {
      transformer.replace(files)
    } else {
      transformer.print(files)
    }
  }
}

private fun Transformer.print(files: List<Path>) {
  val writer = OutputStreamWriter(System.out)
  files.forEach { transform(it, writer) }
  writer.flush()
}

private fun Transformer.replace(files: List<Path>) {
  useTempDir { dir ->
    files.forEach { file ->
      val tempFile = Files.createTempFile(dir, "", ".java")

      Files.newBufferedWriter(tempFile).use { writer ->
        transform(file, writer)
      }

      Files.copy(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
    }
  }
}

private fun useTempDir(process: (dir: Path) -> Unit) {
  val tempDir = Files.createTempDirectory("java-exactify-")

  try {
    process(tempDir)
  } finally {
    Files.walkFileTree(tempDir, object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
        super.visitFile(file, attrs)
        Files.delete(file)
        return FileVisitResult.CONTINUE
      }
    })
  }
}

private fun Transformer.transform(file: Path, writer: Writer) {
  try {
    Files.newBufferedReader(file).use { reader ->
      transform(reader, writer)
    }
  } catch (e: Exception) {
    System.err.println("Failed to transform $file: $e")
    throw e
  }
}