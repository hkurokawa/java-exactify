import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Paths

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    val reader = InputStreamReader(System.`in`)
    val writer = OutputStreamWriter(System.out)
    Transformer().transform(reader, writer)
    writer.flush()
  } else {
    val transformer = Transformer()
    val writer = OutputStreamWriter(System.out)
    args.forEach { arg ->
      val reader = Files.newBufferedReader(Paths.get(arg))
      reader.use { r ->
        transformer.transform(r, writer)
      }
    }
    writer.flush()
  }
}
