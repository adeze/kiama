/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2012-2013 Anthony M Sloane, Macquarie University.
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
 * Parser to use for semantic tests. Separated from `SemanticTests` since
 * we only need the program parser and we don't want to bring all of the
 * parsers into scope in the tests.
 */
object SemanticTestParser extends SyntaxAnalysis

/**
 * Tests that check that the parser works correctly.  I.e., it accepts correct
 * input and produces the appropriate trees, and it rejects illegal input.
 */
class SemanticTests extends Tests {

    import MiniJavaTree._
    import org.kiama.attribution.Attribution.initTree
    import org.kiama.util.{Message, Messaging}
    import scala.collection.immutable.Seq
    import SemanticTestParser.{Error, parser, parseAll, Success, Failure}

    // Tests of definition uniqueness (Rule 1)

    test ("two declarations of same class is an error") {
        semanticTest ("""
            |class Main { public static void main () { System.out.println (0); } }
            |class Main { }
            """.stripMargin,
            (0, Message (2, 7, "Main is declared more than once")),
            (1, Message (3, 7, "Main is declared more than once")))
    }

    test ("two declarations of same name in same class is an error") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    int mult;
            |    int mult;
            |}
            """.stripMargin,
           (0, Message (4, 9, "mult is declared more than once")),
           (1, Message (5, 9, "mult is declared more than once")))
    }

    test ("two declarations of same name in different scopes is ok") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    int notmult;
            |    public int m () {
            |        int notmult;
            |        return 0;
            |    }
            |}
            """.stripMargin)
    }

    // Test of applied occurence matching defining occurrence (Rule 2)

    test ("use of a name that is not declared is an error") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    public int m () {
            |        notdecl = 1;
            |        return 0;
            |    }
            |}
            """.stripMargin,
            (0, Message (5, 9, "notdecl is not declared")))
    }

    test ("use of a name that is declared in wrong scope is an error") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    public int m1 () {
            |        int notdecl;
            |        return 0;
            |    }
            |    public int m2 () {
            |        notdecl = 1;
            |        return 0;
            |    }
            |}
            """.stripMargin,
            (0, Message (9, 9, "notdecl is not declared")))
    }

    // Test type of integer expression (Rule 4)

    test ("an integer expression has integer type") {
        val exp = IntExp (42)
        val analysis = semanticTest (embedExpression (exp))
        assertResult (IntType ()) (analysis.tipe (exp))
    }

    // Test type of boolean expressions (Rule 5)

    test ("a true expression has Boolean type") {
        val exp = TrueExp ()
        val analysis = semanticTest (embedExpression (exp, BooleanType ()))
        assertResult (BooleanType ()) (analysis.tipe (exp))
    }

    test ("a false expression has Boolean type") {
        val exp = FalseExp ()
        val analysis = semanticTest (embedExpression (exp, BooleanType ()))
        assertResult (BooleanType ()) (analysis.tipe (exp))
    }

    // Test use of method names in expressions (rule 6)

    test ("a method name cannot be used in an expression") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    int v;
            |    public int m () {
            |        return m;
            |    }
            |}
            """.stripMargin,
            (0, Message (6, 16, "can't refer to methods directly")))
    }

    // Test type of condition in if and while statements (Rule 7)

    test ("the condition of an if statement can have Boolean type") {
        val exp = IntExp (0) // dummy
        val cond = TrueExp ()
        val stmts = Seq (If (cond, Block (Nil), Block (Nil)))
        semanticTest (embedExpression (exp, IntType (), Nil, stmts))
    }

    test ("the condition of an if statement cannot have integer type") {
        val exp = IntExp (0) // dummy
        val cond = IntExp (42)
        val stmts = Seq (If (cond, Block (Nil), Block (Nil)))
        semanticTest (
            embedExpression (exp, IntType (), Nil, stmts),
            (0, Message (0, 0, "type error: expected boolean got int")))
    }

    test ("the condition of a while statement can have Boolean type") {
        val exp = IntExp (0) // dummy
        val cond = TrueExp ()
        val stmts = Seq (While (cond, Block (Nil)))
        semanticTest (embedExpression (exp, IntType (), Nil, stmts))
    }

    test ("the condition of a while statement cannot have integer type") {
        val exp = IntExp (0) // dummy
        val cond = IntExp (42)
        val stmts = Seq (While (cond, Block (Nil)))
        semanticTest (
            embedExpression (exp, IntType (), Nil, stmts),
            (0, Message (0, 0, "type error: expected boolean got int")))
    }

    // Test type of expression in println statement can be of any type (Rule 8)

    test ("the expression in a println statement can be of Boolean type") {
        val exp = IntExp (0) // dummy
        val exp1 = TrueExp ()
        val stmts = Seq (Println (exp1))
        semanticTest (embedExpression (exp, IntType (), Nil, stmts))
    }

    test ("the expression in a println statement can be of integer type") {
        val exp = IntExp (0) // dummy
        val exp1 = IntExp (42)
        val stmts = Seq (Println (exp1))
        semanticTest (embedExpression (exp, IntType (), Nil, stmts))
    }

    test ("the expression in a println statement can be of integer array type") {
        val exp = IntExp (0) // dummy
        val exp1 = NewArrayExp (IntExp (42))
        val stmts = Seq (Println (exp1))
        semanticTest (embedExpression (exp, IntType (), Nil, stmts))
    }

    test ("the expression in a println statement can be of reference type") {
        val exp = IntExp (0) // dummy
        val exp1 = NewExp (IdnUse ("Test"))
        val stmts = Seq (Println (exp1))
        semanticTest (embedExpression (exp, IntType (), Nil, stmts))
    }

    // Test that assignment RHSes have compatible types with LHS (Rule 9)

    test ("an integer expression is assignment compatible with an integer var") {
        val exp = IntExp (0) // dummy
        val exp1 = IntExp (42)
        val vars = Seq (Var (IntType (), IdnDef ("v")))
        val stmts = Seq (VarAssign (IdnUse ("v"), exp1))
        semanticTest (embedExpression (exp, IntType (), vars, stmts))
    }

    test ("a Boolean expression is not assignment compatible with an integer var") {
        val exp = IntExp (0) // dummy
        val exp1 = TrueExp ()
        val vars = Seq (Var (IntType (), IdnDef ("v")))
        val stmts = Seq (VarAssign (IdnUse ("v"), exp1))
        semanticTest (
            embedExpression (exp, IntType (), vars, stmts),
            (0, Message (0, 0, "type error: expected int got boolean")))
    }

    test ("a Boolean expression is assignment compatible with a Boolean var") {
        val exp = IntExp (0) // dummy
        val exp1 = TrueExp ()
        val vars = Seq (Var (BooleanType (), IdnDef ("v")))
        val stmts = Seq (VarAssign (IdnUse ("v"), exp1))
        semanticTest (embedExpression (exp, IntType (), vars, stmts))
    }

    test ("an integer expression is not assignment compatible with a Boolean var") {
        val exp = IntExp (0) // dummy
        val exp1 = IntExp (42)
        val vars = Seq (Var (BooleanType (), IdnDef ("v")))
        val stmts = Seq (VarAssign (IdnUse ("v"), exp1))
        semanticTest (
            embedExpression (exp, IntType (), vars, stmts),
            (0, Message (0, 0, "type error: expected boolean got int")))
    }

    test ("an integer array expression is assignment compatible with an integer array var") {
        val exp = IntExp (0) // dummy
        val exp1 = NewArrayExp (IntExp (42))
        val vars = Seq (Var (IntArrayType (), IdnDef ("v")))
        val stmts = Seq (VarAssign (IdnUse ("v"), exp1))
        semanticTest (embedExpression (exp, IntType (), vars, stmts))
    }

    test ("an integer expression is not assignment compatible with an integer array var") {
        val exp = IntExp (0) // dummy
        val exp1 = IntExp (42)
        val vars = Seq (Var (IntArrayType (), IdnDef ("v")))
        val stmts = Seq (VarAssign (IdnUse ("v"), exp1))
        semanticTest (
            embedExpression (exp, IntType (), vars, stmts),
            (0, Message (0, 0, "type error: expected int[] got int")))
    }

    // Test types in array assignments (Rule 10)

    test ("integer expressions are ok in an integer array assignment") {
        val exp = IntExp (0) // dummy
        val exp1 = IntExp (42)
        val exp2 = IntExp (99)
        val vars = Seq (Var (IntArrayType (), IdnDef ("v")))
        val stmts = Seq (ArrayAssign (IdnUse ("v"), exp1, exp2))
        semanticTest (embedExpression (exp, IntType (), vars, stmts))
    }

    test ("Boolean expressions are not ok in an integer array assignment") {
        val exp = IntExp (0) // dummy
        val exp1 = TrueExp ()
        val exp2 = FalseExp ()
        val vars = Seq (Var (IntArrayType (), IdnDef ("v")))
        val stmts = Seq (ArrayAssign (IdnUse ("v"), exp1, exp2))
        semanticTest (
            embedExpression (exp, IntType (), vars, stmts),
            (0, Message (0, 0, "type error: expected int got boolean")),
            (1, Message (0, 0, "type error: expected int got boolean")))
    }

    // Test type of plus expressions (Rule 11)

    test ("the children of a plus expression are allowed to be integers") {
        val exp = PlusExp (IntExp (42), IntExp (99))
        semanticTest (embedExpression (exp))
    }

    test ("the children of a plus expression must be integers and its type is integer") {
        val exp = PlusExp (TrueExp (), FalseExp ())
        val analysis =
            semanticTest (
                embedExpression (exp),
                (0, Message (0, 0, "type error: expected int got boolean")),
                (1, Message (0, 0, "type error: expected int got boolean")))
        assertResult (IntType ()) (analysis.tipe (exp))
    }

    // Test type of and expressions (Rule 12)

    test ("the children of an and expression are allowed to be Booleans") {
        val exp = AndExp (TrueExp (), FalseExp ())
        semanticTest (embedExpression (exp, BooleanType ()))
    }

    test ("the children of an and expression must be Booelans and its type is Boolean") {
        val exp = AndExp (IntExp (42), IntExp (99))
        val analysis =
            semanticTest (
                embedExpression (exp, BooleanType ()),
                (0, Message (0, 0, "type error: expected boolean got int")),
                (1, Message (0, 0, "type error: expected boolean got int")))
        assertResult (BooleanType ()) (analysis.tipe (exp))
    }

    // Test type of plus expressions (Rule 13)

    test ("the child of a not expression is allowed to be Boolean") {
        val exp = NotExp (TrueExp ())
        semanticTest (embedExpression (exp, BooleanType ()))
    }

    test ("the child of a not expression must be Boolean and its type is Boolean") {
        val exp = NotExp (IntExp (42))
        val analysis =
            semanticTest (
                embedExpression (exp, BooleanType ()),
                (0, Message (0, 0, "type error: expected boolean got int")))
        assertResult (BooleanType ()) (analysis.tipe (exp))
    }

    // Test type of less-than expressions (Rule 14)

    test ("the children of a less-than expression are allowed to be integers") {
        val exp = LessExp (IntExp (42), IntExp (99))
        semanticTest (embedExpression (exp, BooleanType ()))
    }

    test ("the children of a less-than expression must be integers and its type is Boolean") {
        val exp = LessExp (TrueExp (), FalseExp ())
        val analysis =
            semanticTest (
                embedExpression (exp, BooleanType ()),
                (0, Message (0, 0, "type error: expected int got boolean")),
                (1, Message (0, 0, "type error: expected int got boolean")))
        assertResult (BooleanType ()) (analysis.tipe (exp))
    }

    // Test type of length expressions (Rule 15)

    test ("the child of a length expression is allowed to be an integer array") {
        val exp = LengthExp (NewArrayExp (IntExp (42)))
        semanticTest (embedExpression (exp))
    }

    test ("the child of a length expression must be an integer array and its type is integer") {
        val exp = LengthExp (IntExp (42))
        val analysis =
            semanticTest (
                embedExpression (exp),
                (0, Message (0, 0, "type error: expected int[] got int")))
        assertResult (IntType ()) (analysis.tipe (exp))
    }

    // Test method call expressions (rule 3, 16)

    test ("a non-method cannot be called") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    int v;
            |    public int m () {
            |        return this.v ();
            |    }
            |}
            """.stripMargin,
            (0, Message (6, 21, "illegal call to non-method")))
    }

    test ("a superclass method can be called") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Super {
            |    public int m (int v) {
            |        return v + 1;
            |    }
            |}
            |class Test extends Super {
            |    int v;
            |    public int n () {
            |        return this.m (99);
            |    }
            |}
            """.stripMargin)
    }

    test ("the type of a method call expression is the method return type (1)") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    int v;
            |    public int m () {
            |        return 42;
            |    }
            |    public int n () {
            |        v = this.m ();
            |        return 0;
            |    }
            |}
            """.stripMargin)
    }

    test ("the type of a method call expression is the method return type (2)") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    int v;
            |    public boolean m () {
            |        return true;
            |    }
            |    public int n () {
            |        v = this.m ();
            |        return 0;
            |    }
            |}
            """.stripMargin,
            (0, Message (9, 13, "type error: expected int got boolean")))
    }

    test ("the numbers of arguments in a call can match the declaration") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    public int m (int a, int b) {
            |        return 33;
            |    }
            |    public int n () {
            |        return this.m (42, 99);
            |    }
            |}
            """.stripMargin)
    }

    test ("the numbers of arguments in a call must match the declaration") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    public int m (int a, int b) {
            |        return 33;
            |    }
            |    public int n () {
            |        return this.m (42);
            |    }
            |}
            """.stripMargin,
            (0, Message (8, 21, "wrong number of arguments, got 1 but expected 2")))
    }

    test ("the types of arguments in a call must match the declaration") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    public int m (boolean a, int[] b) {
            |        return 33;
            |    }
            |    public int n () {
            |        return this.m (42, 99);
            |    }
            |}
            """.stripMargin,
            (0, Message (8, 24, "type error: expected boolean got int")),
            (1, Message (8, 28, "type error: expected int[] got int")))
    }

    test ("forward references to methods work") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    int v;
            |    public int n () {
            |        v = this.m ();
            |        return 0;
            |    }
            |    public int m () {
            |        return 42;
            |    }
            |}
            """.stripMargin)
    }

    // Test the type of "this" (rule 17)

    test ("the type of this is the current class") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    public Test m () {
            |        return this;
            |    }
            |}
            """.stripMargin)
    }

    // Test the types in new integer array expressions (rule 18)

    test ("The type of a new array expression is an integer array") {
        val exp = NewArrayExp (IntExp (42))
        val analysis = semanticTest (embedExpression (exp, IntArrayType ()))
        assertResult (IntArrayType ()) (analysis.tipe (exp))
    }

    test ("The type of the parameter in a new integer array expression must be an integer") {
        val exp = NewArrayExp (TrueExp ())
        semanticTest (
            embedExpression (exp, IntArrayType ()),
            (0, Message (0, 0, "type error: expected int got boolean")))
    }

    // Test the use of names in new expressions (rule 19)

    test ("The name used in a new expression must refer to a class") {
        val exp = NewExp (IdnUse ("v"))
        val vars = Seq (Var (IntType (), IdnDef ("v")))
        semanticTest (
            embedExpression (exp, IntType (), vars),
            (0, Message (0, 0, "illegal instance creation of non-class type")))
    }

    test ("The type of a new expression is a reference to the created class") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    public Test m () {
            |        return (new Test ());
            |    }
            |}
            """.stripMargin)
    }

    // Test the return type of a method (rule 20)

    test ("The return expression of a method can return the appropriate type") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    public int m () {
            |        return 42;
            |    }
            |}
            """.stripMargin)
    }

    test ("The return expression of a method cannot return an inappropriate type") {
        semanticTest ("""
            |class Dummy { public static void main () { System.out.println (0); } }
            |class Test {
            |    public int m () {
            |        return true;
            |    }
            |}
            """.stripMargin,
            (0, Message (5, 16, "type error: expected int got boolean")))
    }

    /**
     * Parse some test input as a program and, if the parse succeeds with
     * no input left, return the program tree. If the parse fails, fail
     * the test.
     */
    def parseProgram (str : String) : Program =
        parseAll (parser, str) match {
            case Success (r, in) =>
                if (!in.atEnd) fail (s"input remaining at ${in.pos}")
                r
            case f : Error =>
                fail (s"parse error: $f")
            case f : Failure =>
                fail (s"parse failure: $f")
        }

    /**
     * Parse some test input as a program, run the semantic analyser
     * over the resulting tree (if the parse succeeds) and check that
     * the expected messages are produced. Returns the analysis object
     * so that more tests can be performed by caller.
     */
    def semanticTest (str : String, messages : (Int, Message)*) : SemanticAnalysis =
        runSemanticChecks (parseProgram (str), messages : _*)

    /**
     * Run the semantic analyser over a given tree and check that the
     * expected messages are produced. Returns the analysis object
     * so that more tests can be performed by caller.
     */
    def semanticTest (prog : Program, messages : (Int, Message)*) : SemanticAnalysis =
        runSemanticChecks (prog, messages : _*)

    /**
     * Run the semantic checks on the given program.
     */
    def runSemanticChecks (prog : Program, messages : (Int, Message)*) : SemanticAnalysis = {
        initTree (prog)
        val messaging = new Messaging
        val analysis = new SemanticAnalysis (messaging)
        analysis.check (prog)
        assertMessages (messaging, messages : _*)
        analysis
    }

    /**
     * Construct a program by inserting the given expression into a return
     * statement of a method. The idea is that you construct the expression
     * outside and insert it into the program for checking. The optional
     * `retType`, `vars` and `stmts` arguments can be used to inject a return
     * type, variable declarations or statements into the method as well. The
     * return type defaults to integer and the variable and statement lists
     * to empty.
     */
    def embedExpression (exp : Expression,
                         retType : Type = IntType (),
                         vars : Seq[Var] = Nil,
                         stmts : Seq[Statement] = Nil) =
        Program (MainClass (IdnDef ("Dummy"), Println (IntExp (0))),
            Seq(
                Class (IdnDef ("Test"), None,
                    ClassBody (
                        Nil,
                        Seq (
                            Method (IdnDef ("m"),
                                MethodBody (
                                    retType,
                                    Nil,
                                    vars,
                                    stmts,
                                    exp)))))))

}
