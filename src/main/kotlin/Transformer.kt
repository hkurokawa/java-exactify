import com.github.javaparser.JavaParser
import com.github.javaparser.ParseProblemException
import com.github.javaparser.ParseStart
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.Providers
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.UnaryExpr
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.github.javaparser.resolution.types.ResolvedPrimitiveType
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver
import java.io.Reader
import java.io.Writer

class Transformer(classLoader: ClassLoader = ClassLoader.getSystemClassLoader().parent) {
  private val parser: JavaParser

  init {
    val symbolSolver = JavaSymbolSolver(
        ClassLoaderTypeSolver(
            classLoader))
    val config = ParserConfiguration()
    config.setSymbolResolver(symbolSolver)
    parser = JavaParser(config)
  }

  /**
   * This method transform the given source code so that it does not occur an unexpected
   * arithmetic overflow.
   *
   * The method reads the Java source code from [reader] and then writes out the transformed code
   * to [writer] where all the arithmetic operations which could cause an overflow are replaced with
   * `xxxExact` method in [Math] class, such as [Math.addExact] or [Math.subtractExact].
   *
   * Example:
   * ```
   *   public class Main {
   *     public static void main(String[] args) {
   *        int a = 1;
   *        a++;
   *        a += 2;
   *        int b = a - 3;
   *     }
   *   }
   * ```
   * would be transformed to the below code.
   * ```
   *   public class Main {
   *     public static void main(String[] args) {
   *        int a = 1;
   *        a = Math.incrementExact(a);
   *        a = Math.addExact(a, 2);
   *        int b = Math.subtractExact(a, 3);
   *     }
   *   }
   * ```
   *
   * Note the method throws an [ParseProblemException] if the given code is not a valid Java code.
   *
   * @param[reader] the reader to read the source code from to transform
   * @param[writer] the writer to write the transformed code to
   */
  fun transform(reader: Reader, writer: Writer) {
    val cu = parse(reader)
    do {
      val visitor = OverflowableVisitor()
      cu.accept(visitor, Unit)
    } while (visitor.replaced)

    writer.write(cu.toString())
  }

  private fun parse(reader: Reader): CompilationUnit {
    val result = parser.parse(ParseStart.COMPILATION_UNIT,
        Providers.provider(reader))
    return if (result.isSuccessful) {
      result.result.get()
    } else {
      throw ParseProblemException(result.problems)
    }
  }
}

class OverflowableVisitor : VoidVisitorAdapter<Unit>() {
  private val mathClass = NameExpr(Math::class.java.simpleName)
  var replaced = false

  override fun visit(expr: BinaryExpr, arg: Unit) {
    super.visit(expr, arg)
    with(expr) {
      if (left.isOverflowable() && right.isOverflowable() && operator.isOverflowable()) {
        expr.replace(
            MethodCallExpr(mathClass, operator.toMathMethod(),
                NodeList(left, right)))
        replaced = true
      }
    }
  }

  override fun visit(expr: UnaryExpr, arg: Unit) {
    super.visit(expr, arg)
    expr.operator.toMathMethod()?.let {
      val rightHand = MethodCallExpr(mathClass, it,
          NodeList(expr.expression))
      expr.replace(AssignExpr(expr.expression, rightHand,
          AssignExpr.Operator.ASSIGN))
      replaced = true
    }
  }

  override fun visit(expr: AssignExpr, arg: Unit) {
    super.visit(expr, arg)
    with(expr) {
      if (target.isOverflowable() && value.isOverflowable() && operator.isOverflowable()) {
        val rightHand =
            MethodCallExpr(mathClass, operator.toMathMethod(),
                NodeList(target, value))
        expr.replace(AssignExpr(target, rightHand,
            AssignExpr.Operator.ASSIGN))
        replaced = true
      }
    }
  }

  private fun BinaryExpr.Operator.toMathMethod() = when (this) {
    BinaryExpr.Operator.PLUS -> Math::addExact.name
    BinaryExpr.Operator.MINUS -> Math::subtractExact.name
    BinaryExpr.Operator.MULTIPLY -> Math::multiplyExact.name
    else -> throw IllegalStateException("Unexpected BinaryExpr Operator: $this")
  }

  private fun UnaryExpr.Operator.toMathMethod() = when (this) {
    UnaryExpr.Operator.PREFIX_INCREMENT, UnaryExpr.Operator.POSTFIX_INCREMENT -> Math::incrementExact.name
    UnaryExpr.Operator.PREFIX_DECREMENT, UnaryExpr.Operator.POSTFIX_DECREMENT -> Math::decrementExact.name
    UnaryExpr.Operator.MINUS -> Math::negateExact.name
    else -> null
  }

  private fun AssignExpr.Operator.toMathMethod() = when (this) {
    AssignExpr.Operator.PLUS -> Math::addExact.name
    AssignExpr.Operator.MINUS -> Math::subtractExact.name
    AssignExpr.Operator.MULTIPLY -> Math::multiplyExact.name
    else -> throw IllegalStateException("Unexpected AssignExpr Operator: $this")
  }
}

private fun Expression.isOverflowable() = with(calculateResolvedType()) {
  isPrimitive && asPrimitive().isOverflowable()
}

private fun ResolvedPrimitiveType.isOverflowable() = when (this) {
  ResolvedPrimitiveType.INT, ResolvedPrimitiveType.LONG -> true
  else -> false
}

private fun BinaryExpr.Operator.isOverflowable() = when (this) {
  BinaryExpr.Operator.PLUS, BinaryExpr.Operator.MINUS, BinaryExpr.Operator.MULTIPLY -> true
  else -> false
}

private fun AssignExpr.Operator.isOverflowable() = when (this) {
  AssignExpr.Operator.PLUS, AssignExpr.Operator.MINUS, AssignExpr.Operator.MULTIPLY -> true
  else -> false
}