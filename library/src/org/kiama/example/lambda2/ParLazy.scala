/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2009-2014 Anthony M Sloane, Macquarie University.
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
package example.lambda2

/**
 * Lazy evaluation of lambda calculus with parallel term-level substitution
 * and arithmetic operations.
 */
trait ParLazy extends Par {

    import LambdaTree._
    import org.kiama.rewriting.Rewriter._
    import org.kiama.rewriting.Strategy
    import scala.collection.immutable.Seq

    /**
     * Lift an expression to be evaluated to a substitution.
     */
    lazy val letLift =
        rule[Exp] {
            case e => Letp (Seq (), e)
        }

    /**
     * Drop the bindings.
     */
    lazy val letDrop =
        rule[Exp] {
            case Letp (_, e) => e
        }

    /**
     * Substitute a variable and maintain the bindings.
     */
    override lazy val subsVar =
        rulefs {
            case Letp (ds, Var (x)) =>
                option (lookupb (x, ds)) <* rule[Exp] { case e => Letp (ds, e) }
        }

    /**
     * Apply substitutions lazily in an application, maintaining the
     * environment.
     */
    def letAppL (eval : => Strategy) : Strategy =
        rulefs {
            case Letp (ds1, App (e1, e2)) =>
                option (eval (Letp (ds1, e1))) <* rule[Letp] {
                    case Letp (ds2, e3) =>
                        Letp (ds2, App (e3, e2))
                }
        }

    /**
     * Apply substitutions strictly in an operator evaluation, maintaining the
     * environment.
     */
    def letOpn (eval : => Strategy) : Strategy =
        rulefs {
            case Letp (ds1, Opn (e1, op, e2)) =>
                option (eval (Letp (ds1, e1))) <* rulefs {
                    case Letp (ds2, e3) =>
                        option (eval (Letp (ds2, e2))) <* rule[Letp] {
                            case Letp (ds3, e4) =>
                                Letp (ds3, Opn (e3, op, e4))
                        }
                }
        }

    /**
     * Rename all variables in a parallel binding expression to fresh vars.
     * Assumes that the names are unique to start with.
     */
    def rename : Strategy = {
        val env = scala.collection.mutable.HashMap[Idn,Idn] ()
        val newname =
            rule[Idn] {
                case i => env.getOrElseUpdate (i, freshVar ())
            }
        val chgname =
            rule[Idn] {
                case i => env.getOrElse (i, i)
            }
        lazy val r : Strategy =
            attempt (Var (chgname) + App (r, r) + Lam (newname, id, r) +
                Opn (r, id, r) + Letp (map (r), r) + Bind (newname, r))
        r
    }

    /**
     * Rename variables bound in an inner let (corresponds to heap allocation
     * for these values).
     */
    lazy val letLetRen =
        rulefs {
            case Letp (ds1, Letp (ds2, e1)) =>
                option (rename (Letp (ds2, e1))) <* rule[Letp] {
                    case Letp (ds3, e2) =>
                        val ds4 = ds3 ++ ds1
                        Letp (ds4, e2)
                }
        }

}
