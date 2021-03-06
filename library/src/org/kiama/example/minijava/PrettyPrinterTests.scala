/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2013-2015 Anthony M Sloane, Macquarie University.
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

import org.kiama.util.Tests

/**
 * Tests that check that the code generator produces the expected byte code.
 */
class PrettyPrinterTests extends PrettyPrinter with org.kiama.util.PrettyPrinterTests {

    import MiniJavaTree._

    val mul = IdnDef ("Mul")
    val five = IntExp (5)
    val eight = IntExp (8)
    val starexp = StarExp (five, eight)
    val println = Println (starexp)
    val mainclass = MainClass (mul, println)
    val nil = Nil
    val program = Program (mainclass, nil)

    test ("a simple MiniJava program pretty-prints with the correct positions using any") {

        // Program (
        //     MainClass (IdnDef ("Mul"), Println (StarExp (IntExp (5), IntExp (8)))),
        //     Nil)

        assertPositions (
            Map (
                program -> Range (0, 95),
                mainclass -> Range (14, 85),
                mul -> Range (25, 40),
                println -> Range (41, 84),
                starexp -> Range (50, 83),
                five -> Range (59, 70),
                eight -> Range (71, 82),
                nil -> Range (90, 94),
                5 -> Range (67, 69),
                8 -> Range (79, 81),
                "Mul" -> Range (33, 39)
            )
        ) (pretty (any (program)))

    }

    test ("a simple MiniJava program pretty-prints with the correct positions using MiniJava pretty-printer") {

        // class Mul {
        //     public static void main () {
        //         System.out.println (5 * 8);
        //     }
        // }

        assertPositions (
            Map (
                program -> Range (0, 91),
                mainclass -> Range (0, 91),
                mul -> Range (6, 10),
                println -> Range (53, 81),
                starexp -> Range (73, 79),
                five -> Range (73, 75),
                eight -> Range (77, 79)
            )
        ) (pretty (toDoc (program)))

    }

}

