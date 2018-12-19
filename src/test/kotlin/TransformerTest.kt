import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.io.StringWriter

internal class TransformerTest {
  @Test
  fun testIntInt() {
    test(
        """
      System.out.println(1 + 2);
    """.trimIndent(),
        """
      System.out.println(Math.addExact(1, 2));
    """.trimIndent())
  }

  @Test
  fun testIntLong() {
    test(
        """
      System.out.println(1 + 2L);
    """.trimIndent(),
        """
      System.out.println(Math.addExact(1, 2L));
    """.trimIndent())
  }

  @Test
  fun testIntVariable() {
    test(
        """
      int a = 1;
      System.out.println(a + 2);
    """.trimIndent(),
        """
      int a = 1;
      System.out.println(Math.addExact(a, 2));
    """.trimIndent())
  }

  @Test
  fun testLongVariable() {
    test(
        """
      int a = 1L;
      System.out.println(a + 2);
    """.trimIndent(),
        """
      int a = 1L;
      System.out.println(Math.addExact(a, 2));
    """.trimIndent())
  }

  @Test
  fun testNested() {
    test(
        """
      System.out.println(1 + 2 + 3 + 4);
    """.trimIndent(),
        """
      System.out.println(Math.addExact(Math.addExact(Math.addExact(1, 2), 3), 4));
    """.trimIndent())
  }

  @Test
  fun testIncrement() {
    test(
        """
      int a = 1;
      a++;
    """.trimIndent(),
        """
      int a = 1;
      a = Math.incrementExact(a);
    """.trimIndent())
  }

  @Test
  fun testAddAssign() {
    test(
        """
      int a = 1;
      a += 2;
    """.trimIndent(),
        """
      int a = 1;
      a = Math.addExact(a, 2);
    """.trimIndent())
  }

  @Test
  fun testAssignBin() {
    test(
        """
          int a;
          a = 1 + 2;
        """.trimIndent(),
        """
          int a;
          a = Math.addExact(1, 2);
        """.trimIndent()
    )
  }

  private fun test(input: String, expected: String) {
    val transformer = Transformer()
    val writer = StringWriter()
    transformer.transform(StringReader(template(input)), writer)
    assertEquals(template(expected), writer.toString())
  }
}

private fun template(value: String) = """
class Test {

    public static void main(String[] args) {
        ${value.replace("\n", "\n        ")}
    }
}

""".trimIndent()