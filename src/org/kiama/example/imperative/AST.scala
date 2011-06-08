/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2008-2011 Anthony M Sloane, Macquarie University.
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
package example.imperative

/**
 * A simple imperative language abstract syntax designed for testing.
 */
object AST {
    
    import org.kiama.attribution.Attributable
    import org.kiama.rewriting.Rewriter.{Strategy, rulefs, congruence}

    /**
     * Identifiers are represented as strings.
     */
    type Idn = String

    /**
     * Superclass of all imperative language tree node types.  The Product
     * supertype is used here to enable generic access to the children of 
     * an ImperativeNode; this capability is only used in the Kiama tests
     * and is not usually needed for normal use of the library.
     */
    trait ImperativeNode extends Attributable with Cloneable {
        override def clone () = super.clone ().asInstanceOf[ImperativeNode]
    }

    /**
     * Expressions.
     */
    abstract class Exp extends ImperativeNode {

        /**
         * The numeric value of the expression.
         */
        def value : Double

        /**
         * The set of all variable references in the expression.
         */
        def vars : Set[Idn] = Set ()

        /**
         * The number of divisions by the constant zero in the expression.
         */
        def divsbyzero : Int = 0

        /**
         * The depth of the expression, i.e., the number of levels from the
         * root to the leaf values.
         */
        def depth : Int = 0

        /**
         * The number of additions of integer constants in the expression.
         */
        def intadds : Int = 0
    }

    /**
     * Numeric expressions.
     */
    case class Num (d : Double) extends Exp {
        override def value = d
        override def depth = 2
    }

    /**
     * Variable expressions.
     */
    case class Var (s : Idn) extends Exp {
        // Hack to make tests more interesting
        override def value = 3
        override def vars = Set (s)
        override def depth = 2
        override def toString = "Var(\"" + s + "\")"
    }

    /**
     * Unary negation expressions.
     */
    case class Neg (e : Exp) extends Exp {
        override def value = - e.value
        override def vars = e.vars
        override def divsbyzero = e.divsbyzero
        override def depth = 1 + e.depth
        override def intadds = e.intadds
    }

    /**
     * Binary expressions.
     */
    abstract class Binary (l : Exp, r : Exp) extends Exp {
        override def vars = l.vars ++ r.vars
        override def divsbyzero = l.divsbyzero + r.divsbyzero
        override def depth = 1 + (l.depth).max (r.depth)
        override def intadds = l.intadds + r.intadds
    }

    /**
     * Addition expressions.
     */
    case class Add (l : Exp, r : Exp) extends Binary (l, r) {
        override def value = l.value + r.value
        override def intadds =
            (l, r) match {
                case (Num (_), Num (_)) => 1
                case _                  => super.intadds
            }
    }

    /**
     * Subtraction expressions.
     */
    case class Sub (l : Exp, r : Exp) extends Binary (l, r) {
        override def value = l.value - r.value
    }

    /**
     * Multiplication expressions.
     */
    case class Mul (l : Exp, r : Exp) extends Binary (l, r) {
        override def value = l.value * r.value
    }

    /**
     * Division expressions.
     */
    case class Div (l : Exp, r : Exp) extends Binary (l, r) {
        // Hack: no errors, so return zero for divide by zero
        override def value = if (r.value == 0) 0 else l.value / r.value
        override def divsbyzero =
            l.divsbyzero + (r match {
                                case Num (0) => 1
                                case _       => r.divsbyzero
                            })
    }

    /**
     * Statements.
     */
    abstract class Stmt extends ImperativeNode {

        /**
         * The set of all variable references in the statement.
         */
        def vars : Set[Idn] = Set ()

    }

    /**
     * Empty statements.
     */
    case class Null () extends Stmt

    /**
     * Statement sequences.
     */
    case class Seqn (ss : Seq[Stmt]) extends Stmt {
        override def vars = Set (ss flatMap (_ vars) : _*)
    }

    /**
     * Assignment statements.
     */
    case class Asgn (v : Var, e : Exp) extends Stmt {
        override def vars = Set (v.s)
    }

    /**
     * While loops.
     */
    case class While (e : Exp, b : Stmt) extends Stmt {
        override def vars = e.vars ++ b.vars
    }

    // Congruences

    def Num (s1 : => Strategy) : Strategy =
        rulefs {
            case _ : Num =>
                congruence (s1)
        }

    def Var (s1 : => Strategy) : Strategy =
        rulefs {
            case _ : Var =>
                congruence (s1)
        }

    def Neg (s1 : => Strategy) : Strategy =
        rulefs {
            case _ : Var =>
                congruence (s1)
        }

    def Add (s1 : => Strategy, s2 : => Strategy) : Strategy =
        rulefs {
            case _ : Add =>
                congruence (s1, s2)
        }

    def Sub (s1 : => Strategy, s2 : => Strategy) : Strategy =
        rulefs {
            case _ : Sub =>
                congruence (s1, s2)
        }

    def Mul (s1 : => Strategy, s2 : => Strategy) : Strategy =
        rulefs {
            case _ : Mul =>
                congruence (s1, s2)
        }

    def Div (s1 : => Strategy, s2 : => Strategy) : Strategy =
        rulefs {
            case _ : Div =>
                congruence (s1, s2)
        }

    def Seqn (s1 : => Strategy) : Strategy =
        rulefs {
            case _ : Seqn =>
                congruence (s1)
        }

    def Asgn (s1 : => Strategy, s2 : => Strategy) : Strategy =
        rulefs {
            case _ : Asgn =>
                congruence (s1, s2)
        }

    def While (s1 : => Strategy, s2 : => Strategy) : Strategy =
        rulefs {
            case _ : While =>
                congruence (s1, s2)
        }

}
