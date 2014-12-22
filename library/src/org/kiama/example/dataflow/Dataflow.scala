/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2008-2014 Anthony M Sloane, Macquarie University.
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
package example.dataflow

import DataflowTree._
import org.kiama.attribution.Attribution

class Dataflow (val tree : DataflowTree) extends Attribution {

    // Control flow

    /**
     * Control flow successor relation.
     */
    val succ : Stm => Set[Stm] =
        dynAttr {
            case If (_, s1, s2) =>
                Set (s1, s2)
            case t @ While (_, s) =>
                following (t) + s
            case Return (_) =>
                Set ()
            case Block (s :: _) =>
                Set (s)
            case s =>
                following (s)
        }

    /**
     * Control flow default successor relation.
     */
    val following : Stm => Set[Stm] =
        dynAttr {
            case tree.parent (t : If) =>
                following (t)
            case tree.parent (t : While) =>
                Set (t)
            case tree.parent.pair (tree.next (n), _ : Block) =>
                Set (n)
            case tree.parent (b : Block) =>
                following (b)
            case _ =>
                Set ()
        }

    // Variable use and definition

    /**
     * Variable uses.
     */
    val uses : Stm => Set[Var] =
        dynAttr {
            case If (v, _, _)  => Set (v)
            case While (v, _)  => Set (v)
            case Assign (_, v) => Set (v)
            case Return (v)    => Set (v)
            case _             => Set ()
        }

    /**
     * Variable definitions.
     */
    val defines : Stm => Set[Var] =
        dynAttr {
            case Assign (v, _) => Set (v)
            case _             => Set ()
        }

    // Variable liveness

    /**
     * Variables "live" into a statement.
     */
    val in : Stm => Set[Var] =
        circular (Set[Var]()) (
            // Optimisation to not include vars used to calculate v
            // if v is not live in the following.
            // case s @ Assign (v, _) if ! (out (s) contains v) =>
            //    out (s)
            s => uses (s) ++ (out (s) -- defines (s))
        )

    /**
     * Variables "live" out of a statement.
     */
    val out : Stm => Set[Var] =
        circular (Set[Var]()) (
            s => succ (s) flatMap (in)
        )

}
