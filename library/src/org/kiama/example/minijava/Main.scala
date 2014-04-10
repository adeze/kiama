/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2012-2014 Anthony M Sloane, Macquarie University.
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
package example.minijava

import MiniJavaTree.Program
import org.kiama.util.Compiler

/**
 * Conduct syntax analysis on the MiniJava program in the file given as the
 * first command-line argument and print out the result source program tree.
 */
trait Driver extends SyntaxAnalyser with Compiler[Program] {

    import CodeGenerator.generate
    import PrettyPrinter.{pretty, pretty_any}
    import Translator.translate
    import org.kiama.output.PrettyPrinter
    import org.kiama.util.Config
    import org.kiama.util.Messaging.report

    /**
     * Whether this is a test run or not. Test runs generate all of their
     * code using a single emitter so it can be easily compared to what
     * we expect. A normal run sends the code for each class to a separate
     * file so that they can be compiled by Jasmin.
     */
    def isTest = true

    /**
     * Process the source tree by analysing it to check for semantic
     * errors. If any messages are produced, print them. If all is ok,
     * translate the program and generate code for the translation.
     */
    override def process (filename : String, ast : Program, config : Config) {

        super.process (filename, ast, config)

        // Pretty print the abstract syntax tree
        // emitter.emitln (pretty_any (ast))

        // Perform the semantic checks
        val analyser = new SemanticAnalyser
        val messages = analyser.errors (ast)

        // Report any messages that were produced
        if (messages.length > 0) {

            report (messages, config.error)

        } else {

            // Translate the source tree to JVM
            val targettree = translate (ast, filename, analyser)

            // Pretty print the target tree
            // config.output.emitln (pretty_any (targettree))

            // Output code for the target tree
            targettree.map (generate (isTest, _, config.output))

        }

    }

    /**
     * Pretty printer to use to print minijava ASTs.
     */
    override def prettyprint (ast : Program) : String =
        pretty (ast)

}

/**
 * Main program for MiniJava compiler.
 */
object Main extends Driver {
    override def isTest = false
}
