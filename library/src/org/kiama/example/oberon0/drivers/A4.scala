/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2011-2014 Anthony M Sloane, Macquarie University.
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
package example.oberon0
package drivers

trait A4Phases extends L4.SyntaxAnalyser
        with L4.source.SourcePrettyPrinter
        with L4.c.CPrettyPrinter
        with base.TranslatingDriver {

    phases =>

    import base.source.SourceTree.SourceTree

    def artefact : String = "A4"
    def langlevel : Int = 4
    def tasklevel : Int = 6

    def buildAnalyser (atree : SourceTree) : L0.TypeAnalyser =
        new L4.NameAnalyser with L4.TypeAnalyser {
            val tree = atree
        }

    def buildTransformer (atree : SourceTree) : base.Transformer =
        new L2.Lifter with L2.Desugarer {
            val tree = atree
            def buildAnalyser (atree : SourceTree) : L0.TypeAnalyser =
                phases.buildAnalyser (atree)
        }

    def buildTranslator (atree : SourceTree) : base.Translator =
        new L4.CCodeGenerator {
            val tree = atree
            def buildAnalyser (atree : SourceTree) : L0.TypeAnalyser =
                phases.buildAnalyser (atree)
        }

}

object A4 extends A4Phases
