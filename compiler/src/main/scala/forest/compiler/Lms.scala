package forest.compiler

import forest.ast._
import scalax.file.Path

/**
 * Lms backend
 */
class Lms {

  def generate(document: Document, name: String, targetDirectory: Path): String = {
    """|def %s(%s): Rep[scala.xml.Node] = {
       |  %s
       |}
       |""".stripMargin.format(
             name,
             (for ((name, kind) <- document.parameters) yield "%s: Rep[%s]".format(name, kind)).mkString(", "),
             q(document.tree)
           )
  }

  implicit val quoteNode: Quote[Node] = new Quote[Node] {
    override def quote(node: Node) = node match {
      case Tag(name, children, attrs, _) => {
        "el(%s%s)(%s)".format(q(name), q(attrs)(quoteAttrs), q(children)(quoteNodes))
      }
      case Text(content) => "txt(%s)".format(content.map(c => q(c)).mkString(" + "))
      case If(cond, thenPart, elsePart) => {
        "if (%s) %s else %s".format(q(cond), q(thenPart), q(elsePart))
      }
      case For(it, seq, body) => "%s.flatMap[scala.xml.Node]{ %s => %s }".format(q(seq), it, q(body))
      case Call(callee, args) => "%s(%s)".format(callee, args.map(arg => q(arg)).mkString(", "))
    }
  }

  implicit val quoteTextContent: Quote[TextContent] = new Quote[TextContent] {
    override def quote(txt: TextContent) = txt match {
      case RawText(txt) => q(txt)
      case e: Expr => quoteExpr.quote(e)
    }
  }

  implicit val quoteExpr: Quote[Expr] = new Quote[Expr] {
    override def quote(expr: Expr) = expr match {
      case Data(path) => path
      case InlineIf(cond, thenPart, elsePart) => "if (%s) %s else %s".format(q(cond), q(thenPart), q(elsePart))
      case Literal(value) => q(value)
    }
  }

  object quoteAttrs extends Quote[Map[String, List[TextContent]]] {
    override def quote(attrs: Map[String, List[TextContent]]) = {
      if (attrs.isEmpty) {
        ""
      } else {
        ", %s"
            .format((for ((k, vs) <- attrs) yield "(%s, (%s))"
                .format(q(k), (for (v <- vs) yield q(v)).mkString(" + "))).mkString(", "))
      }
    }
  }

  object quoteNodes extends Quote[List[Node]] {
    override def quote(nodes: List[Node]) = {
      def format(nodes: List[Node], isOpen: Boolean): String = nodes match {
        case Nil            => if (isOpen) ")" else "List[scala.xml.Node]()"
        case (f: For) :: ns => (if (isOpen) ") ++ " else "") + q(f) + " ++ " + format(ns, false) // TODO remove this crap code
        case n :: ns        => (if (isOpen) ", " else "List(") + q(n) + format(ns, true)
      }
      format(nodes, false)
    }
  }

  def q[A : Quote](a: A): String = implicitly[Quote[A]].quote(a)

}

object Lms extends Lms