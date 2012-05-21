/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2012 Anthony M Sloane, Macquarie University.
 *
 * Kiama is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Kiama is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Kiama.  (See files COPYING and COPYING.LESSER.)  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.kiama

/**
 * Simple lambda calculus implementation to illustrate Kiama's support for
 * nominal rewriting. This implementation is closely based on the example
 * used in Scrap your Nameplate, James Cheney, ICFP 2005.
 */
package example.lambda3

import org.kiama.util.ParsingREPL
import org.kiama.util.PositionedParserUtilities

/**
 * A simple lambda calculus using abstracted name binding.
 * The basic term syntax is augmented with query commands for the REPL.
 */
object AST {

    import Evaluator.cbn_eval
    import org.kiama.rewriting.NominalAST.{Bind, Name}
    import org.kiama.rewriting.NominalRewriter.{alphaequiv, fresh, fv,
        subst, swap, Trans}
    import org.kiama.util.Positioned

    /**
     * Lambda calculus expressions.
     */
    abstract class Exp extends Positioned

    /**
     * Numeric expression.
     */
    case class Num (i : Int) extends Exp {
        override def toString () = i.toString
    }

    /**
     * Variable expression.
     */
    case class Var (x : Name) extends Exp {
        override def toString () = x.toString
    }

    /**
     * Application of l to r.
     */
    case class App (e1 : Exp, e2 : Exp) extends Exp {
        override def toString () = "(" + e1 + " " + e2 + ")"
    }

    /**
     * Lambda expression containing an abstracted binding.
     */
    case class Lam (b : Bind) extends Exp {
        override def toString () = "(\\" + b.name + " . " + b.term + ")"
    }

    /**
     * A query that can be entered from the REPL and returns a value of
     * type T when executed. These values are not in the term language but
     * are used to represent user commands.
     */
    abstract class Query {
        type T
        def execute () : T
    }

    /**
     * A query that determines the alpha equivalence of two expressions.
     */
    case class EquivQuery (e1 : Exp, e2 : Exp) extends Query {
        type T = Boolean
        def execute () = alphaequiv (e1, e2)
    }

    /**
     * A query that computes the value of an expression.
     */
    case class EvalQuery (e : Exp) extends Query {
        type T = Exp
        def execute () = cbn_eval (e)
    }

    /**
     * A query that determines the free names in an expression.
     */
    case class FreeNamesQuery (e : Exp) extends Query {
        type T = Set[Name]
        def execute () = fv (e)
    }
    
    /**
     * A query that determines whether a name is not free in an expression.
     */
    case class FreshQuery (n : Name, e : Exp) extends Query {
        type T = Boolean
        def execute () = fresh (n) (e)
    }

    /**
     * A query that substitutes an expression `e1` for name `n` in another
     * expression `e2`.
     */
    case class SubstQuery (n : Name, e1 : Exp, e2 : Exp) extends Query {
        type T = Exp
        def execute () = subst (n, e1) (e2)
    }

    /**
     * A query that swaps two names in an expression.
     */
    case class SwapQuery (tr : Trans, e : Exp) extends Query {
        type T = Exp
        def execute () = swap (tr) (e)
    }

}

/**
 * Parser for simple lambda calculus plus REPL queries.
 */
 trait Parser extends PositionedParserUtilities {

    import AST._
    import org.kiama.rewriting.NominalAST.{Bind, Name}
    import org.kiama.rewriting.NominalRewriter.Trans

    lazy val start =
        phrase (query)

    lazy val query : PackratParser[Query] =
        exp ~ ("===" ~> exp) ^^ EquivQuery |
        ("fv" ~> exp) ^^ FreeNamesQuery |
        name ~ ("#" ~> exp) ^^ FreshQuery |
        ("[" ~> name) ~ ("-> " ~> exp <~ "]") ~ exp ^^ SubstQuery |
        trans ~ exp ^^ SwapQuery |
        exp ^^ EvalQuery

    lazy val trans : PackratParser[Trans] =
        "(" ~> name ~ ("<->" ~> name) <~ ")"

    lazy val exp : PackratParser[Exp] =
        exp ~ factor ^^ App |
        ("\\" ~> name) ~ ("." ~> exp) ^^ {
            case n ~ e => Lam (Bind (n, e))
        } |
        factor |
        failure ("expression expected")

    lazy val factor : PackratParser[Exp] =
        integer | variable | "(" ~> exp <~ ")"

    lazy val integer =
        "[0-9]+".r ^^ (s => Num (s.toInt))

    lazy val variable =
        name ^^ Var

    lazy val name =
        "[a-zA-Z]+".r ~ regexnows ("[0-9]*".r) ^^ {
            case base ~ index =>
                Name (base, if (index isEmpty) None else Some (index.toInt))
        }

}

/**
 * Evaluation methods for simple lambda calculus.
 */
object Evaluator {

    import AST._
    import org.kiama.rewriting.NominalAST.Bind
    import org.kiama.rewriting.NominalRewriter.subst

    /**
     * Call-by-name evaluation.
     */
    def cbn_eval (e : Exp) : Exp =
        e match {
            case App (t1, t2) =>
                val w = cbn_eval (t1)
                w match {
                    case Lam (Bind (a, u : Exp)) =>
                        val v = subst (a, t2) (u)
                        cbn_eval (v)
                    case _ =>
                        App (w, t2)
                }
            case _ =>
                e
        }

}

/**
 * A read-execute-print loop for lambda calculus queries.
 */
object Lambda extends ParsingREPL[AST.Query] with Parser {

    override def setup (args : Array[String]) : Boolean = {
        println
        println ("Enter lambda calculus queries:")
        println
        println (" e               evaluate e")
        println (" (n1 <-> n2) e   swap n1 and n2 in e")
        println (" n # e           is n fresh in e?")
        println (" fv e            free variables of e")
        println (" e1 === e2       is e1 alpha equivalent to e2?")
        println (" [n -> e1] e2    substitute e1 for n in e2")
        println
        println ("where n = name, e = expression")
        println
        true
    }

    override def prompt () = "query> "

    def process (q : AST.Query) {
        println (q.execute ())
    }

}
