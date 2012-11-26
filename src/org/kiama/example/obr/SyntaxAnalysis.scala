/**
 * Obr language parser.
 *
 * This file is part of Kiama.
 *
 * Copyright (C) 2009-2012 Anthony M Sloane, Macquarie University.
 * Copyright (C) 2010-2012 Dominic Verity, Macquarie University.
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
package example.obr

import org.kiama.util.PositionedParserUtilities

/**
 * Module containing parsers for the Obr language.
 */
class SyntaxAnalysis extends PositionedParserUtilities {

    import ObrTree._
    import org.kiama.util.Positioned
    import scala.collection.immutable.HashSet

    override val whiteSpace = """(\s|\(\*(?:.|[\n\r])*?\*\))+""".r

    val reservedWords = HashSet(
          "PROGRAM", "INTEGER", "BEGIN", "END", "ELSE", "CONST", "VAR"
        , "BOOLEAN", "ARRAY", "OF", "RECORD", "EXIT", "RETURN", "IF", "THEN"
        , "LOOP", "WHILE", "DO", "FOR", "TO", "OR", "MOD", "AND", "TRUE"
        , "FALSE", "EXCEPTION", "RAISE", "TRY", "CATCH"
        )

    case class Pos(s : String) extends Positioned {
        override def toString : String = s
    }

    def withPos (op : Parser[String]) : Parser[Pos] = op ^^ Pos

    lazy val parser : Parser[ObrInt] =
        phrase (program)

    lazy val program : Parser[ObrInt] =
        ("PROGRAM" ~> ident) ~
        ("(" ~> rep1sep (parameterdecl, ";") <~ ")" <~ ":" <~ "INTEGER" <~ ";") ~
        declarations ~
        ("BEGIN" ~> statementseq <~ "END") ~
        (ident <~ ".") ^^
            { case i1 ~ ps ~ ds ~ ss ~ i2 =>
                  ObrInt (i1, ps ++ ds, ss, i2) }

    lazy val parameterdecl : Parser[Declaration] =
        ident <~ ":" <~ "INTEGER" ^^ IntParam

    lazy val declarations : Parser[List[Declaration]] =
        constantdecls ~ variabledecls ^^ { case cs ~ vs => cs ++ vs }

    lazy val constantdecls : Parser[List[Declaration]] =
        opt ("CONST" ~> rep1 (constantdecl <~ ";")) ^^
            {
                case None     => Nil
                case Some (l) => l
            }

    lazy val variabledecls : Parser[List[Declaration]] =
        opt ("VAR" ~> rep1 (variabledecl <~ ";")) ^^
            {
                case None     => Nil
                case Some (l) => l
            }

    lazy val constantdecl : Parser[Declaration] =
        ident ~ ("=" ~> signed) ^^ IntConst |
        ident <~ ":" <~ "EXCEPTION" ^^ ExnConst

    lazy val variabledecl : Parser[Declaration] =
        ident <~ ":" <~ "BOOLEAN" ^^ BoolVar |
        ident <~ ":" <~ "INTEGER" ^^ IntVar |
        ident ~ (":" ~> "ARRAY" ~> integer <~ "OF" <~ "INTEGER") ^^ ArrayVar |
        ident ~ (":" ~> "RECORD" ~> (fielddecl+) <~ "END") ^^ RecordVar |
        // Extra clause to handle parsing the declaration of an enumeration variable
        ident ~ (":" ~> "(" ~> rep1sep (withPos (ident), ",") <~ ")") ^^
            { case i ~ cs => EnumVar (i, cs map { case p @ Pos (s) => EnumConst (s) setPos p }) }

    lazy val fielddecl : Parser[Identifier] =
        ident <~ ":" <~ "INTEGER" <~ ";"

    lazy val statementseq : Parser[List[Statement]] =
        statement*

    lazy val statement : Parser[Statement] =
        lvalue ~ withPos (":=") ~ (expression <~ ";") ^^
            { case l ~ p ~ e => AssignStmt (l, e) setPos p } |
        conditional |
        iteration |
        trycatch |
        "EXIT" ~ ";" ^^ (_ => ExitStmt ()) |
        "RETURN" ~> expression <~ ";" ^^ ReturnStmt |
        "RAISE" ~> ident <~ ";" ^^ RaiseStmt

    lazy val conditional : Parser[IfStmt] =
        "IF" ~> expression ~ ("THEN" ~> statementseq) ~ optelseend ^^ IfStmt

    lazy val optelseend : Parser[List[Statement]] =
        "ELSE" ~> statementseq <~ "END" |
        "END" ^^^ Nil

    lazy val iteration : Parser[Statement] =
        "LOOP" ~> statementseq <~ "END" ^^ LoopStmt |
        "WHILE" ~> expression ~ ("DO" ~> statementseq <~ "END") ^^ WhileStmt |
        "FOR" ~> ident ~ (":=" ~> expression) ~ ("TO" ~> expression) ~
             ("DO" ~> statementseq <~ "END") ^^ ForStmt

    lazy val trycatch : Parser[TryStmt] =
        withPos ("TRY") ~ statementseq ~ ((catchclause*) <~ "END") ^^
            { case p ~ ss ~ cs =>
                val body = TryBody (ss) setPos p
                TryStmt (body, cs) }

    lazy val catchclause : Parser[Catch] =
        ("CATCH" ~> ident <~ "DO") ~ statementseq ^^ Catch

    lazy val expression : PackratParser[Expression] =
        expression ~ withPos ("=") ~ simplexp ^^
            { case e ~ p ~ s => EqualExp (e, s) setPos p } |
        expression ~ withPos ("#") ~ simplexp ^^
            { case e ~ p ~ s => NotEqualExp (e, s) setPos p } |
        expression ~ withPos ("<") ~ simplexp ^^
            { case e ~ p ~ s => LessExp (e, s) setPos p } |
        expression ~ withPos (">") ~ simplexp ^^
            { case e ~ p ~ s => GreaterExp (e, s) setPos p } |
        simplexp

    lazy val simplexp : PackratParser[Expression] =
        simplexp ~ withPos ("+") ~ term ^^
            { case s ~ p ~ t => PlusExp (s, t) setPos p } |
        simplexp ~ withPos ("-") ~ term ^^
            { case s ~ p ~ t => MinusExp (s, t) setPos p } |
        simplexp ~ withPos ("OR") ~ term ^^
            { case s ~ p ~ t => OrExp (s, t) setPos p } |
        term

    lazy val term : PackratParser[Expression] =
        term ~ withPos ("*") ~ factor ^^
            { case t ~ p ~ f => StarExp (t, f) setPos p } |
        term ~ withPos ("/") ~ factor ^^
            { case t ~ p ~ f => SlashExp (t, f) setPos p } |
        term ~ withPos ("MOD") ~ factor ^^
            { case t ~ p ~ f => ModExp (t, f) setPos p } |
        term ~ withPos ("AND") ~ factor ^^
            { case t ~ p ~ f => AndExp (t, f) setPos p } |
        factor

    lazy val factor : PackratParser[Expression] =
        "TRUE" ^^ (_ => BoolExp (true)) |
        "FALSE" ^^ (_ => BoolExp (false)) |
        lvalue |
        integer ^^ IntExp |
        "(" ~> expression <~ ")" |
        "~" ~> factor ^^ NotExp |
        "-" ~> factor ^^ NegExp

    lazy val lvalue : PackratParser[AssignNode] =
        ident ~ ("[" ~> expression <~ "]") ^^ IndexExp |
        ident ~ withPos (".") ~ ident ^^
            { case r ~ p ~ f => FieldExp (r, f) setPos p } |
        ident ^^ IdnExp

    lazy val integer : PackratParser[Int] =
        "[0-9]+".r ^^ (s => s.toInt)

    lazy val signed : PackratParser[Int] =
        "-?[0-9]+".r ^^ (s => s.toInt)

    lazy val ident : PackratParser[Identifier] =
        "[a-zA-Z][a-zA-Z0-9]*".r into (s => {
            if (reservedWords contains s)
                failure ("keyword \"" + s + "\" found where variable name expected")
            else
                success (s)
        })

}
