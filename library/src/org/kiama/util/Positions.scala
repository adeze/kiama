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
package util

/**
 * Record of source positions that correspond to program elements.
 */
object Positions extends Memoiser {

    import java.lang.CharSequence
    import scala.util.parsing.input.{NoPosition, OffsetPosition, Position}

    /**
     * Map between a value and a source code position.
     */
    class PositionMap extends IdMemoised[Any,Position]

    /**
     * Map between value and starting position.
     */
    private val startMap = new PositionMap

    /**
     * Map between value and whitespace starting position.
     */
    private val startWhiteMap = new PositionMap

    /**
     * Map between value and finishing position.
     */
    private val finishMap = new PositionMap

    /**
     * Get the start position of `t`. If it doesn't have one, return
     * `NoPosition`.
     */
    def getStart[T] (t : T) : Position =
        startMap.getWithDefault (t, NoPosition)

    /**
     * Get the start whitespace position of `t`. If it doesn't have
     * one, return `NoPosition`.
     */
    def getStartWhite[T] (t : T) : Position =
        startWhiteMap.getWithDefault (t, NoPosition)

    /**
     * Get the finish position of `t`. If it doesn't have one, return
     * `NoPosition`.
     */
    def getFinish[T] (t : T) : Position =
        finishMap.getWithDefault (t, NoPosition)

    /**
     * Set the start position of `t` to `p` if it has not already been set.
     */
    def setStart[T] (t : T, p : Position) {
        startMap.putIfNotPresent (t, p)
    }

    /**
     * Set the start whitespace position of `t` to `p` if it has not
     * already been set.
     */
    def setStartWhite[T] (t : T, p : Position) {
        startWhiteMap.putIfNotPresent (t, p)
    }

    /**
     * Set the `finish` position of `t` to `p` if it has not already been set.
     */
    def setFinish[T] (t : T, p : Position) {
        finishMap.putIfNotPresent (t, p)
    }

    /**
     * Set the start and finish positions of `t` to the positions of `a`
     * if it has them. Return `t`.
     */
    def dupPos[T] (a : Any, t : T) : T = {
        startMap.dup (a, t, NoPosition)
        startWhiteMap.dup (a, t, NoPosition)
        finishMap.dup (a, t, NoPosition)
        t
    }

    /**
     * Return a position value with starting position given by line `l`,
     * column `c` and empty line contents. This method is most useful
     * for testing low-level messages and is not normally be needed in
     * application code.
     */
    def positionAt (l : Int, c : Int) : Position =
        new Position {
            val line = l
            val column = c
            val lineContents = ""
        }

    /**
     * Get the source text associated with the subsequence of a source
     * between given starting and finishing offset positions. The two
     * positions are assumed to reference the same source.
     */
    def subSource (s : OffsetPosition, f : OffsetPosition) : CharSequence =
        s.source.subSequence (s.offset, f.offset)

}

/**
 * An extension of `ParserUtilities` that has support for automatically
 * tracking start and finish positions for tree nodes.
 */
trait PositionedParserUtilities extends ParserUtilities {

    import Positions.{getFinish, getStart, getStartWhite,
        setFinish, setStart, setStartWhite, subSource}
    import scala.util.parsing.input.OffsetPosition

    /**
     * A marker of a position. Used as a placeholder when there are no
     * other suitable values associated with the position.
     */
    class Marker

    /**
     * Mark a string parser so that its value is discarded but a marker is
     * returned instead. That return value can then be used to set the
     * position of another value. We can't use the string value itself
     * since we are not guaranteed to have reference equality on strings.
     */
    def mark[T] (p : Parser[String]) : Parser[Marker] =
        p ^^ (_ => new Marker)

    /**
     * Run a parse function on some input and set the position of the
     * resulting value.
     */
    def parseAndPosition[T] (f : Input => ParseResult[T], in : Input) : ParseResult[T] =
        f (in) match {
            case res @ Success (t, in1) =>
                val startoffset = handleWhiteSpace (in)
                val newin = in.drop (startoffset - in.offset)
                setStart (t, newin.pos)
                setStartWhite (t, in.pos)
                setFinish (t, in1.pos)
                res
            case res =>
                res
        }

    /**
     * Make a new parser that processes input according to `f`, setting
     * the position of the value produced. If the parser is ignoring whitespace
     * then the start position will be the first non-white character that
     * was accepted.
     */
    override def Parser[T] (f : Input => ParseResult[T]) : Parser[T] =
        new Parser[T] {
            def apply (in : Input) : ParseResult[T] =
                parseAndPosition (f, in)
        }

    /**
     * As for `Parser` but for the non-backtracking version `OnceParser`.
     */
    override def OnceParser[T](f : Input => ParseResult[T]) : Parser[T] with OnceParser[T] =
       new Parser[T] with OnceParser[T] {
            def apply (in : Input) : ParseResult[T] =
                parseAndPosition (f, in)
       }

    /**
     * If `t` has start and finish positions implemented by offset positions
     * from the Scala parser combinator library, return the source text
     * associated `t`. Otherwise, return `None`. If `includeWhiteSpace` is
     * true (the default) then the starting position includes any preceding
     * white space characters; otherwise it starts at the first non-whitespace
     * character.
     */
    def textOf[T] (t : T, includeWhiteSpace : Boolean = true) : Option[String] = {
        val start = if (includeWhiteSpace) getStartWhite (t) else getStart (t)
        (start, getFinish (t)) match {
            case (s : OffsetPosition, f : OffsetPosition) =>
                Some (subSource (s, f).toString)
            case _ =>
                None
        }
    }

}

/**
 * Combination of positioned parsing utilities and whitespace handling with a
 * parser.
 */
trait WhitespacePositionedParserUtilities extends WhitespaceParser with PositionedParserUtilities {

    /**
     * Version of `handleWhiteSpace` that accepts an `Input` value and
     * skips over whitespace defined by the parser. Used with
     * `PositionedParserUtilities` so that correct positions are used.
     */
    override def handleWhiteSpace (in : Input) : Int =
        parseWhitespace (in).next.offset

}
