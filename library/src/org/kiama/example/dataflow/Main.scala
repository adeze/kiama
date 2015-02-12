/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2010-2015 Anthony M Sloane, Macquarie University.
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
import org.kiama.util.Compiler

/**
 * Parse a simple imperative language program, calculate its dataflow
 * relations and use them to remove dead assignments.
 */
class Driver extends SyntaxAnalyser with Compiler[Stm] {

    import org.kiama.output.PrettyPrinterTypes.{emptyDocument, Document}
    import org.kiama.util.Config

    /**
     * Process the tree by optimising it, then print optimised tree.
     */
    def process (filename : String, ast : Stm, config : Config) {
        val tree = new DataflowTree (ast)
        val optimiser = new Optimiser (tree)
        val optast = optimiser.run (ast)
        config.output.emitln (optast)
    }

    def format (ast : Stm) : Document =
        emptyDocument

}

/**
 * Dataflow language implementation main program.
 */
object Main extends Driver
