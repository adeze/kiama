/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2010-2014 Dominic R B Verity, Anthony Sloane, Macquarie University.
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
package example.iswim.tests

/*
 * Tests of the ISWIM parser combinators.
 */

import org.kiama.util.RegexParserTests
import org.kiama.example.iswim.compiler._

class ParserTests extends RegexParserTests with Parser {

    import Syntax._

    test("parse a 0-tuple") {
        assertParseOk ("()", expr,
            Empty ())
    }

    test("parse a 2-tuple of manifest boolean values") {
        assertParseOk ("(true,true)", expr,
            Tuple(List(BoolVal(true),BoolVal(true))))
    }

    test("parse a 4-tuple of manifest boolean values") {
        assertParseOk ("(true,false,false,true)", expr,
            Tuple(List(BoolVal(true),BoolVal(false),
                        BoolVal(false),BoolVal(true))))
    }

    test("parse nested tuples of manifest boolean values") {
        assertParseOk ("(true,(false,false),true)", expr,
            Tuple(List(
                BoolVal(true),
                Tuple(List(BoolVal(false),BoolVal(false))),
                BoolVal(true))))
    }

    test("parse error: tuple missing open bracket") {
        assertParseError ("true,false)", expr, 1, 5,
            """string matching regex `\z' expected but `,' found""")
    }

    test("parse error: tuple missing close bracket") {
        assertParseError ("(true,()", expr, 1, 9,
            """operator ")" expected""")
    }

    test("parse a tuple of literal integers and boolean values") {
        assertParseOk ("(1,true,3,-5)", expr,
            Tuple(List(NumVal(1),BoolVal(true),
                        NumVal(3),Negate(NumVal(5)))))
    }

    test("parse a simple arithmetic expression (1)") {
        assertParseOk ("1 + 2 * 3 + 4 * (5- 4 + 7 % -2) / 6", expr,
            Plus(Plus(NumVal(1),Times(NumVal(2),NumVal(3))),
                 Divide(Times(NumVal(4),Plus(Minus(NumVal(5),NumVal(4)),
                                             Remainder(NumVal(7),Negate(NumVal(2))))),
                        NumVal(6))))
    }

    test("parse a simple arithmetic expression (2)") {
        assertParseOk ("1+2*3+4*(dom-4+7%-2)/6", expr,
            Plus(Plus(NumVal(1),Times(NumVal(2),NumVal(3))),
                 Divide(Times(NumVal(4),
                              Plus(Minus(Variable("dom"),NumVal(4)),
                                   Remainder(NumVal(7),Negate(NumVal(2))))),
                        NumVal(6))))
    }

    test("attempt to parse a keyword as a variable name") {
        assertParseError ("callcc", variable, 1, 7,
            """keyword "callcc" found where variable name expected""")
    }

    test("parse a variable name whose prefix is a keyword") {
        assertParseOk ("elseifvar", variable, Variable ("elseifvar"))
    }

    test("attempted parse of non-matching keyword") {
        assertParseError ("else", keyword("if"), 1, 5, """keyword "if" expected""")
    }

    test("attempted parse of matching keyword from front of variable name") {
        assertParseError ("elseifvar", keyword("else"), 1, 10, """keyword "else" expected""")
    }

    test("parse a match clause") {
        assertParseOk ("(dom,sal) -> sal * dom + 1", matchclause,
            MatchClause(Pattern(List(Variable("dom"),Variable("sal"))),
                Plus(Times(Variable("sal"),Variable("dom")),NumVal(1))))
    }

    test("attempt to parse a match clause with a bad pattern") {
        assertParseError ("(1,x) -> 1", matchclause, 1, 2, "variable name expected")
    }

    test("attempt to parse a letrec which binds a non-lambda expression") {
        assertParseError ("letrec x = fun(y) (x y) and z = x in (x 10)", expr, 1, 35,
            """keyword "fun" expected""")
    }

    test("parse a match expression") {
        assertParseOk ("""
(dom 10) match {
    ()      -> 0;
    x       -> x;
    (x,y,z) -> x * y + z
}
""",
            expr,
            Match(Apply(Variable("dom"),NumVal(10)),
                  List(MatchClause(Pattern(List()),NumVal(0)),
                       MatchClause(Pattern(List(Variable("x"))),Variable("x")),
                       MatchClause(Pattern(List(Variable("x"), Variable("y"), Variable("z"))),
                                   Plus(Times(Variable("x"),Variable("y")),Variable("z"))))))
    }

    test("parse a sequence of function applications") {
        assertParseOk ("dom 10 (sal + flo * 10) herb (10,30)", expr,
            Apply(Apply(Apply(Apply(Variable("dom"),
                                    NumVal(10)),
                              Plus(Variable("sal"),
                                   Times(Variable("flo"),NumVal(10)))),
                        Variable("herb")),
                  Tuple(List(NumVal(10),NumVal(30)))))
    }

    test("parse a code block") {
        assertParseOk ("{ 10 ; 15 * dom }", expr,
            Block(List(NumVal(10),
                       Times(NumVal(15),Variable("dom")))))
    }

    test("parse application of a function to a code block") {
        assertParseOk ("domFun10 { 10 ; test }", expr,
            Apply(Variable("domFun10"),
                  Block(List(NumVal(10),Variable("test")))))
    }

    test("parse if ... else if ... else ... expression") {
        assertParseOk ("""
if (inp == 20)
    test * 10 + 5
else if (inp <= 40)
    (test + 5) * hello
else if (inp >= 200)
    test % 4
else
    10
        """,
            expr,
            If(
                Equal(Variable("inp"),NumVal(20))
            ,   Plus(Times(Variable("test"),NumVal(10)),NumVal(5))
            ,   If (
                    LessEq(Variable("inp"),NumVal(40))
                ,   Times(Plus(Variable("test"),NumVal(5)),Variable("hello"))
                ,   If (
                        GreaterEq(Variable("inp"),NumVal(200))
                    ,   Remainder(Variable("test"),NumVal(4))
                    ,   NumVal(10)
                    )
                )
            )
        )
    }

    test("parse a while expression") {
        assertParseOk ("""
{
    i := 0;
    while (i <= 20)
        i := i + 1
}
        """,
            expr,
            Block(List(Assign(Variable("i"),NumVal(0)),
                       While(LessEq(Variable("i"),NumVal(20)),
                             Assign(Variable("i"),Plus(Variable("i"),NumVal(1)))))))
    }

    test("parse a callcc expression") {
        assertParseOk ("10 + callcc dom", expr,
            Plus(NumVal(10),CallCC(Variable("dom"))))
    }

    test("parse throw...to expression") {
        assertParseOk ("10 + throw v to c * 20", expr,
            Plus(NumVal(10),Times(ThrowTo(Variable("v"),
                Variable("c")),NumVal(20))))
    }

    test("parse some other builtins") {
        assertParseOk ("{ r := mkref 100; 10 + val r * 20; write dom }", expr,
            Block(List(Assign(Variable("r"),MkRef(NumVal(100))),
                       Plus(NumVal(10),Times(Val(Variable("r")),NumVal(20))),
                       Apply(Variable("write"),Variable("dom")))))
    }

    test("parse a lambda expression") {
        assertParseOk ("fun(x) { write 10; return (x+1) }", expr,
            Lambda(Variable("x"),Block(List(Apply(Variable("write"),NumVal(10)),
                                            Return(Plus(Variable("x"),NumVal(1)))))))
    }

    test("parse a simple correct let expression") {
        assertParseOk ("let a = 1 and b = 2 in a * b", expr,
            Let(List(Binding(Variable("a"),NumVal(1)),
                     Binding(Variable("b"),NumVal(2))),Times(Variable("a"),Variable("b"))))
    }

    test("parse a correct letrec expression") {
        assertParseOk ("""
letrec  plusone = fun(n) { n + 1 }
and     factorial = fun(n) { if (n == 0) 1 else n * factorial (n-1) }
in      factorial(plusone 5)
        """,
            expr,
            LetRec(List(
                Binding(Variable("plusone"),
                        Lambda(Variable("n"),
                               Block(List(Plus(Variable("n"),NumVal(1)))))),
                Binding(Variable("factorial"),
                        Lambda(Variable("n"), Block(List(
                            If(Equal(Variable("n"),NumVal(0)),NumVal(1),
                               Times(Variable("n"),Apply(Variable("factorial"),
                                                          Minus(Variable("n"),NumVal(1)))))))))),
                Apply(Variable("factorial"),Apply(Variable("plusone"),NumVal(5)))))
    }

    test("attempted parse of a letrec with a binding whose rhs is not a lambda clause") {
        assertParseError ("letrec a = fun(n) n + 1 and b = 22 in (a b)", expr, 1, 33,
            """keyword "fun" expected""")
    }

    test("parse an expression containing a string literal") {
        assertParseOk ("""{ write "hello\n"; write "there!\n" }""", expr,
            Block(List(Apply(Variable("write"),StringVal("hello\\n")),
                       Apply(Variable("write"),StringVal("there!\\n")))))
    }

    test("check that the expression parser correctly handles comments") {
        assertParseOk ("""
/* put */ fun /* a */ (/*comment*/x/*wherever*/)/* you */ { /* like */ write
/* and */ 10 /* everything */; /* should */ return /* still */(/*parse*/x
/*just   */+/* perfectly */1/*dontcha*/)/* know */ } /* old bean */
        """,
            expr,
            Lambda(Variable("x"),Block(List(Apply(Variable("write"),NumVal(10)),
                                            Return(Plus(Variable("x"),NumVal(1)))))))
    }

    test("parse a simple, but complete, program") {
        assertParseOk ("""
        /*
         * Title:       Fibonacci fun
         * Description: A very simple imperative Fibonacci function with driver.
         * Copyright:   (C) 2010-2014 Dominic Verity, Macquarie University
         */

        // declare preloaded primitives
        primitives write, read, fields, type;

        // Imperative fibonacci function
        letrec fib = fun(n)
            let r1 = mkref 0
            and r2 = mkref 1
            and r3 = mkref (-1)
            in  letrec f = fun(m)
                    if (m == 0)
                        val r1
                    else {
                        r3 := val r1 + val r2;
                        r1 := val r2;
                        r2 := val r3;
                        f (m-1) }
                in f n;

        // Execute an example and print the result.
        {
            write (fib 200);
            write "\n"
        }
""",
            parser,
            IswimProg(List(
              Primitives(List(
                  Variable("write"),Variable("read"),
                  Variable("fields"),Variable("type")))
            , LetRecStmt(List(
                Binding(
                  Variable("fib")
                , Lambda(
                    Variable("n")
                  , Let(
                      List(
                        Binding(Variable("r1"),MkRef(NumVal(0)))
                      , Binding(Variable("r2"),MkRef(NumVal(1)))
                      , Binding(Variable("r3"),MkRef(Negate(NumVal(1)))))
                    , LetRec(
                        List(
                          Binding(
                            Variable("f")
                          , Lambda(
                              Variable("m")
                            , If(
                                Equal(Variable("m"),NumVal(0))
                              , Val(Variable("r1"))
                              , Block(List(
                                  Assign(
                                    Variable("r3")
                                  , Plus(Val(Variable("r1")),Val(Variable("r2"))))
                                , Assign(Variable("r1"),Val(Variable("r2")))
                                , Assign(Variable("r2"),Val(Variable("r3")))
                                , Apply(Variable("f"),Minus(Variable("m"),NumVal(1)))))))))
                      , Apply(Variable("f"),Variable("n"))))))))
            , ExprStmt(Block(List(
                Apply(Variable("write"),Apply(Variable("fib"),NumVal(200)))
              , Apply(Variable("write"),StringVal("\\n")))))
            )))
    }

    test("check that the parser can handle very long comments") {
        val input = """
/**
 * Title:     An implementation of try...catch exception
 *            handling using continuations
 * Author:    Dominic Verity
 * Copyright: Macquarie University (C) 2007-2014
 *
 * A try..catch block is implemented using 2 continuations:
 *        The first marks the exit point for normal exits.
 *        The second marks the entry point to the exception handler code.
 *
 * We might picture this arrangement as:
 *
 *  --- normal exit cont    --- error handler cont
 *   |                       |
 *   |                       |                    code in the try block
 *   |                       |
 *   |                       |         at the end of the try block throw to normal
 *   |                       V         exit continuation to avoid exectuing the exception
 *   |                      ___        handler after successful execution of try code.
 *   |
 *   |
 *   |                                         code of corresponding
 *   |                                         exception handler.
 *   V
 *  ---
 *
 * The SECD machine provides a primitive, called 'raise', which allows us
 * to raise an exception. This can be brought into scope in user code using the 'primitives'
 * key word. The same mechanism also allows us to bring the names of the exception
 * values associated with the various machine exceptions into scope. Having done that we can
 * write code to catch and handle different kinds of machine error.
 *
 * The SECD 'raise' primitive assumes that when it is called a variable named 'exceptionHandler'
 * will be in scope and that it will be bound to a continuation. All it actually does is to
 * throw the exception value given as its argument to this continuation. The default provided
 * by the machine on startup is that 'exceptionHandler' is bound to a continuation marking the
 * exit point of the running program.
 *
 * We can replace this simple "exit on error" mechanism with something a little more useful
 * simply by re-binding 'exceptionHandler' to a more useful continuation. In the code given here
 * we organise things so that 'exceptionHandler' is bound always bound to the continuation
 * which marks the entry point to the catch block of the closest enclosing try...catch construct.
 */
"""
        assertParseError (input, parser, 45, 1, """operator "{" expected""")
    }

}
