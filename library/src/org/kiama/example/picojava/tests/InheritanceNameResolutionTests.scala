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

import org.kiama.util.Tests

class InheritanceNameResolutionTests extends Tests {

    import org.kiama.attribution.Attribution.initTree
    import org.kiama.example.picojava.NameResolution._
    import org.kiama.example.picojava.PicoJavaTree._
    import scala.collection.immutable.Seq

    // For the actual program text, see InheritanceNameResolutionTests.pj

    val declAa  = VarDecl (Use ("int"), "a")
    val aInAA   = Use ("a")
    val declAAb = VarDecl (Use ("int"), "b")
    val bInAA   = Use ("b")
    val AinB    = Use ("A")
    val aInB    = Use ("a")
    val declBc  = VarDecl (Use ("int"), "c")
    val cInB    = Use ("c")
    val AAinBB  = Use ("AA")
    val aInBB   = Use ("a")
    val declAAe = VarDecl (Use ("int"), "e")
    val eInBB   = Use ("e")
    val fInBB   = Use ("f")
    val declBf  = VarDecl (Use ("int"), "f")

    val declAA = ClassDecl ("AA", None, Block(
                     Seq (declAAb,
                          VarDecl (Use ("int"), "d"),
                          declAAe,
                          AssignStmt (aInAA, bInAA))))

    val declA = ClassDecl ("A", None, Block(
                    Seq (declAa,
                         VarDecl (Use ("int"), "b"),
                         VarDecl (Use ("int"), "c"),
                         declAA)))

    val ast =
        Program (Block (
            Seq (declA,
                 ClassDecl ("B", Some (AinB), Block (
                     Seq (declBc,
                          VarDecl (Use ("int"), "e"),
                          declBf,
                          AssignStmt (aInB, cInB),
                          ClassDecl ("BB", Some (AAinBB), Block (
                              Seq (VarDecl (Use ("int"), "d"),
                                   AssignStmt (aInBB, Use ("d")),
                                   AssignStmt (eInBB, fInBB))))))))))
    initTree (ast)

    test ("members are resolved in nested classes") {
        assertResult (declAa) (aInAA->decl)
    }

    test ("nested members shadow outer members") {
        assertResult (declAAb) (bInAA->decl)
    }

    test ("class names are resolved in extends clauses") {
        assertResult (declA) (AinB->decl)
    }

    test ("inherited members are resolved") {
        assertResult (declAa) (aInB->decl)
    }

    test ("local members hide inherited ones") {
        assertResult (declBc) (cInB->decl)
    }

    test ("inherited inner classes are resolved") {
        assertResult (declAA) (AAinBB->decl)
    }

    test ("inner references to members of outer class are resolved") {
        assertResult (declBf) (fInBB->decl)
    }

    test ("inner references to inherited members of outer class are resolved") {
        assertResult (declAa) (aInBB->decl)
    }

    test ("inherited members shadow outer occurrences of the same name") {
        assertResult (declAAe) (eInBB->decl)
    }

}
