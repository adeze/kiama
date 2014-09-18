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

/*
 * This file is derived from a JastAdd implementation of PicoJava, created
 * in the Department of Computer Science at Lund University.  See the
 * following web site for details:
 *
 * http://jastadd.cs.lth.se/examples/PicoJava/index.shtml
 */

package org.kiama
package example.picojava.tests

import org.kiama.example.picojava.SyntaxAnalyser
import org.kiama.util.RegexParserTests

class ErrorTests extends RegexParserTests with SyntaxAnalyser {

    import org.kiama.example.picojava.ErrorCheck
    import org.kiama.example.picojava.PicoJavaTree.PicoJavaTree
    import org.kiama.util.Message
    import org.kiama.util.Positions.positionAt

    /**
     * Parse the illegal program and make sure that the errors and their
     * positions are as expected.
     */
    test ("semantic errors are correctly reported") {
        val text = """
{
  class A extends B{
    boolean a;
    a = b;
    A refA;
    a = refA;
  }
  class B extends A {
  }
  class C {
  }
  class D {
  }
  C refC;
  D refD;
  refC = refD;
}
""";
        assertParseCheck (text, program) {
            ast =>
                val tree = new PicoJavaTree (ast)
                val analyser = new ErrorCheck (tree)
                val messages = analyser.errors
                assertResult ("Unknown identifier b") (messages.get (0))
                assertResult ("Can not assign a variable of type boolean to a value of type A") (messages.get (1))
                assertResult ("Cyclic inheritance chain for class A") (messages.get (2))
                assertResult ("Cyclic inheritance chain for class B") (messages.get (3))
                assertResult ("Can not assign a variable of type C to a value of type D") (messages.get (4))
        }
    }

}
