/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2011-2012 Anthony M Sloane, Macquarie University.
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
package util

import scala.util.parsing.combinator.PackratParsers
import scala.util.parsing.combinator.RegexParsers

/**
 * Useful utilities for defining regular expression packrat parsers.
 */
trait ParserUtilities extends RegexParsers with PackratParsers {
    
    import scala.util.matching.Regex

    /**
     * A parser that matches any element, failing if the end of input
     * is reached.
     */
    def any : PackratParser[Char] =
        Parser { in =>
            if (in.atEnd)
                Failure ("any character expected but end of source found", in)
            else
                elem ("any character", _ => true) (in)           
        }
    
    /**
     * Return an error after skipping white space.
     */
    override def err (msg : String) : Parser[Nothing] =
        "" ~> super.err (msg)

    /**
     * Return a failure after skipping white space.
     */
    override def failure (msg : String) : Parser[Nothing] =
        "" ~> super.failure (msg)

    /**
     * Construct a parser that always succeeds and returns value `v`.  See
     * also the `success` combinator in the Scala library that does something 
     * similar but always returns the same value each time since the parameter
     * is not passed by name.
     */
    def result[T] (v : => T) : Parser[T] =
        Parser { in => Success (v, in) }

    /**
     * Wrap a parser `p` that produces a value of type `T` to produce a
     * parser returning values of type `U`.  Whitespace is skipped (if
     * we are skipping white space) before `p` is applied, so that we
     * have access to the first non-whitespace position.
     *  
     * The function `f` is responsible for converting the `T` value into
     * either a `U` value or a string that indicates what went wrong.
     * In the latter case, the resulting parser will error at the
     * original position with the message, ignoring any other errors
     * at that position.  Failures or errors of `p` will be lifted to
     * the returned type.
     */
    def wrap[T,U] (p : => Parser[T], f : T => Either[U,String]) : Parser[U] =
        Parser { in =>
            val offset = in.offset
            val start = handleWhiteSpace (in.source, offset)
            val newin = in.drop (start - offset)
            p (newin) match {
                case Success (t, out) =>
                    f (t) match {
                        case Left (u)    =>
                            Success (u, out)
                        case Right (msg) =>
                            Error (msg, newin)
                    }
                case Failure (msg, out) =>
                    Failure (msg, out)
                case Error (msg, out) =>
                    Error (msg, out)
            }
        }
        
    /**
     * Convenience conversion to lift parsers that return 2-tilde-tuples to parsers
     * that return regular 2-tuples. 
     */
    implicit def parseResultToTuple2[A,B] (p : Parser[A ~ B]) : PackratParser[(A,B)] =
        p ^^ { case a ~ b => (a,b) }

    /**
     * Convenience conversion to lift parsers that return 3-tilde-tuples to parsers
     * that return regular 3-tuples. 
     */
    implicit def parseResultToTuple3[A,B,C] (p : Parser[A ~ B ~ C]) : PackratParser[(A,B,C)] =
        p ^^ { case a ~ b ~ c => (a,b,c) }

    /**
     * Convenience conversion to lift parsers that return 4-tilde-tuples to parsers
     * that return regular 4-tuples. 
     */
    implicit def parseResultToTuple4[A,B,C,D] (p : Parser[A ~ B ~ C ~ D]) : PackratParser[(A,B,C,D)] =
        p ^^ { case a ~ b ~ c ~ d => (a,b,c,d) }

    /**
     * Convenience conversion to lift parsers that return 5-tilde-tuples to parsers
     * that return regular 5-tuples. 
     */
    implicit def parseResultToTuple5[A,B,C,D,E] (p : Parser[A ~ B ~ C ~ D ~ E]) : PackratParser[(A,B,C,D,E)] =
        p ^^ { case a ~ b ~ c ~ d ~ e => (a,b,c,d,e) }

    /**
     * Convenience conversion to lift parsers that return 6-tilde-tuples to parsers
     * that return regular 6-tuples. 
     */
    implicit def parseResultToTuple6[A,B,C,D,E,F] (p : Parser[A ~ B ~ C ~ D ~ E ~ F]) : PackratParser[(A,B,C,D,E,F)] =
        p ^^ { case a ~ b ~ c ~ d ~ e ~ f => (a,b,c,d,e,f) }

    /**
     * Convenience conversion to allow arity two functions to be used directly in
     * tree construction actions.
     */
    implicit def constToTupleFunction2[A,B,R] (r : (A,B) => R) : 
                     (A ~ B) => R = {
        case a ~ b =>
            r (a, b)
    }

    /**
     * Convenience conversion to allow arity three functions to be used directly in
     * tree construction actions.
     */
    implicit def constToTupleFunction3[A,B,C,R] (r : (A,B,C) => R) : 
                     (A ~ B ~ C) => R = {
        case a ~ b ~ c =>
            r (a, b, c)
    }

    /**
     * Convenience conversion to allow arity four functions to be used directly in
     * tree construction actions.
     */
    implicit def constToTupleFunction4[A,B,C,D,R] (r : (A,B,C,D) => R) : 
                     (A ~ B ~ C ~ D) => R = {
        case a ~ b ~ c ~ d =>
            r (a, b, c, d)
    }

    /**
     * Convenience conversion to allow arity five functions to be used directly in
     * tree construction actions.
     */
    implicit def constToTupleFunction5[A,B,C,D,E,R] (r : (A,B,C,D,E) => R) : 
                     (A ~ B ~ C ~ D ~ E) => R = {
        case a ~ b ~ c ~ d ~ e =>
            r (a, b, c, d, e)
    }

    /**
     * Convenience conversion to allow arity six functions to be used directly in
     * tree construction actions.
     */
    implicit def constToTupleFunction6[A,B,C,D,E,F,R] (r : (A,B,C,D,E,F) => R) : 
                     (A ~ B ~ C ~ D ~ E ~ F) => R = {
        case a ~ b ~ c ~ d ~ e ~ f =>
            r (a, b, c, d, e, f)
    }
                     
    // Specialist parsers for common situations
    
    /**
     * Parse digit strings that are constrained to fit into an `Int` value.
     * If the digit string is too big, a parse error results.
     */
    lazy val constrainedInt : PackratParser[Int] =
        wrap (regex ("[0-9]+".r), stringToInt)

    /**
     * Convert the digit string `s` to an `Int` if it's in range, but return an
     * error message if it's too big.  
     */
    def stringToInt (s : String) : Either[Int,String] = {
        var value = 0
        for (d <- s) {
            val dv = d.toInt - '0'.toInt
            if (value > (Int.MaxValue - dv) / 10)
                return Right ("integer will not fit into 32 bits")
            value = value * 10 + dv
        }
        Left (value)
    }

}

/**
 * Support for defining the form of whitespace using a parser, rather than a 
 * regular expression.  This version is useful particularly in cases where
 * the form of comments requires more power than a regular expression can 
 * provide (e.g., for nested comments).  
 */
trait WhitespaceParser extends RegexParsers with PackratParsers {

    import scala.util.matching.Regex
    import scala.util.parsing.input.Positional

    /*
     * Turn off whitespace processing based on the whiteSpace regular
     * expression from `RegexParsers`.
     */
    override def skipWhitespace = false

    /**
     * A parser that recognises whitespace.  Normal whitespace handling is
     * turned off while this parser is applied, since we need to avoid an
     * infinite recursion if the form of whitespace is defined using
     * `literal` or `regex`. 
     */
    protected val whitespaceParser : PackratParser[Any]
    
    /**
     * Are we currently parsing whitespace?
     */
    protected var parsingWhitespace = false
    
    /**
     * If we are parsing whitespace already, fail if we are the end
     * of the input, otherwise succeed with no progress.  If we are
     * not already parsing whitespace, then apply the whitespace
     * parser, swallowing any errors from it unless they occur at 
     * the end of the input. In other words, an error not at the end
     * is treated as the absence of whitespace.
     */
    protected def parseWhitespace (in: Input) : ParseResult[Any] =
        if (parsingWhitespace) {
            if (in.atEnd)
                Failure ("end of source while parsing whitespace", in)
            else
                Success ("", in)
        } else {
            parsingWhitespace = true
            val result =
                whitespaceParser (in) match {
                    case f @ NoSuccess (_, next) =>
                        if (next.atEnd)
                            f
                        else
                            Success ("", in)
                    case s =>
                        s
                }
            parsingWhitespace = false
            result
        }
    
    /**
     * A parser that matches a literal string after skipping any 
     * whitespace that is parsed by `whitespaceParser`.
     */
    override implicit def literal (s : String) : Parser[String] =
        Parser { in =>
            parseWhitespace (in) match {
                case Success (_, next) =>
                    WhitespaceParser.super.literal (s) (next)
                case NoSuccess (msg, next) =>
                    Failure (msg, next)
            }
        }

    /**
     * A parser that matches a regex string after skipping any 
     * whitespace that is parsed by `whitespaceParser`.
     */
    override implicit def regex (r : Regex) : Parser[String] =
        Parser { in =>
            parseWhitespace (in) match {
                case Success (_, next) =>
                    val res = WhitespaceParser.super.regex (r) (next)
                    res
                case NoSuccess (msg, next) =>
                    Failure (msg, next)
            }
        }

    /**
     * As for positioned in `RegexParsers`, but uses `parseWhitespace`
     * to skip whitespace.
     */
    override def positioned[T <: Positional](p : => Parser[T]) : Parser[T] = {
        val pp = super.positioned(p)
        Parser { in =>
            parseWhitespace (in) match {
                case Success (_, next) =>
                    pp (next)
                case NoSuccess (msg, next) =>
                    Failure (msg, next)
            }
        }
    }    
    
}
