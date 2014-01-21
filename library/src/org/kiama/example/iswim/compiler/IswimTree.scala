/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2010-2014 Dominic R B Verity, Macquarie University.
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
package example.iswim.compiler

/**
 * Abstract syntax for a variant of ISWIM.
 */
object IswimTree {

    import org.kiama.util.TreeNode
    import scala.collection.immutable.Seq

    /**
     * In essence, all program clauses in ISWIM are expressions.
     * In this variant, however, we treat top level let expressions
     * as statements.
     */
    abstract class Iswim extends TreeNode
    sealed abstract class Expr extends Iswim
    sealed abstract class Stmt extends Iswim

    /**
     * A complete ISWIM program
     */
    case class IswimProg(bds : Seq[Stmt]) extends Iswim

    /**
     * A simple statement which simply computes a value
     */
    case class ExprStmt(e : Expr) extends Stmt

    /**
     * Variable Identifiers
     */
    case class Variable(s : String) extends Expr

    /**
     * Integer Expressions
     */
    case class NumVal(i : Int) extends Expr
    case class Negate(e : Expr) extends Expr
    case class Plus(l : Expr, r : Expr) extends Expr
    case class Minus(l : Expr, r : Expr) extends Expr
    case class Times(l : Expr, r : Expr) extends Expr
    case class Divide(l : Expr, r : Expr) extends Expr
    case class Remainder(l : Expr, r : Expr) extends Expr

    /**
     * Integer Comparisons
     */
    case class Equal(l : Expr, r : Expr) extends Expr
    case class NotEqual(l : Expr, r : Expr) extends Expr
    case class Less(l : Expr, r : Expr) extends Expr
    case class LessEq(l : Expr, r : Expr) extends Expr
    case class Greater(l : Expr, r : Expr) extends Expr
    case class GreaterEq(l : Expr, r : Expr) extends Expr

    /**
     * Boolean Expressions
     */
    case class BoolVal(b : Boolean) extends Expr
    case class Not(e : Expr) extends Expr
    case class And(l : Expr, r : Expr) extends Expr
    case class Or(l : Expr, r : Expr) extends Expr

    /**
     * String literals.
     */
    case class StringVal(s : String) extends Expr

    /**
     * Binding Constructs
     */
    case class Binding(v : Variable, e : Expr) extends Iswim
    case class Let(bind : Seq[Binding], body : Expr) extends Expr
    case class LetRec(bind : Seq[Binding], body : Expr) extends Expr

    case class LetStmt(bind : Seq[Binding]) extends Stmt
    case class LetRecStmt(bind : Seq[Binding]) extends Stmt

    /**
     * Import statement (not used at the moment)
     */
    case class Import(fn : String) extends Stmt

    /**
     * Function Definition and Application
     */
    case class Lambda(par : Variable, body : Expr) extends Expr
    case class Return(res : Expr) extends Expr
    case class Apply(f : Expr, e : Expr) extends Expr

    /**
     * Conditionals and Loops
     */
    case class If(e : Expr, thn : Expr, els : Expr) extends Expr
    case class While(ctrl : Expr, body : Expr) extends Expr

    /**
     * Blocks
     */
    case class Block(es : Seq[Expr]) extends Expr

    /**
     * Records / Tuples
     */
    case class Empty() extends Expr
    case class Tuple(fields : Seq[Expr]) extends Expr
    case class Pattern(ns : Seq[Variable]) extends Iswim
    case class MatchClause(p : Pattern, e : Expr) extends Iswim
    case class Match(ctrl : Expr, clauses : Seq[MatchClause]) extends Expr

    /**
     * Continuations
     */
    case class CallCC(e : Expr) extends Expr
    case class ThrowTo(e : Expr, c : Expr) extends Expr

    /**
     * References
     */
    case class MkRef(e : Expr) extends Expr
    case class Val(e : Expr) extends Expr
    case class Assign(r : Expr, e : Expr) extends Expr

    /**
     * In my original Haskell implementation of ISWIM the following were
     * implemented as builtin primitives:
     *
     * message (string)     print a text message to the terminal
     * write (expr)         write a text representation of a value to the terminal
     * read ()              read an integer from the terminal
     * numfields (expr)     return the number of fields in a record
     *
     * In this implementation, we instead intoduce a mechanism for declaring the names
     * of values which we assume will be constructed and pre-loaded into the environment
     * by the the startup pre-amble.
     */
    case class Primitives(nms : Seq[Variable]) extends Stmt
}
