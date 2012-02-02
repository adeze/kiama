/**
 * JSON parser.
 *
 * This file is part of Kiama.
 *
 * Copyright (C) 2011 Anthony M Sloane, Macquarie University.
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
package example.json

/**
 * Module containing parsers for the JSON language.
 */
trait SyntaxAnalysis extends org.kiama.util.ParserUtilities {

    import JSONTree._

    lazy val parser : PackratParser[JValue] =
        phrase (jvalue)

    lazy val jvalue : PackratParser[JValue] =
        jobject | jarray | jstring | jnumber | jtrue | jfalse | jnull

    lazy val jobject : PackratParser[JObject] =
        "{" ~> repsep (jpair, ",") <~ "}" ^^ {
            case l => JObject (l)
        }

    lazy val jpair : PackratParser[(JName,JValue)] =
        string ~ (":" ~> jvalue) ^^ {
            case s ~ v => (JName (s), v)
        }

    lazy val jarray : PackratParser[JArray] =
        "[" ~> repsep (jvalue, ",") <~ "]" ^^ {
            case l => JArray (Vector (l : _*))
        }

    lazy val jstring : PackratParser[JString] =
        string ^^ JString

    lazy val string : PackratParser[String] =
        regex ("\"[^\"]*\"".r) ^^ {
            case s => s.substring (1, s.length - 1)
        }

    // FIXME
    // lazy val string : PackratParser[String] =
    //     regex (""""([^"\\]|\\(["\\/bfnrt]|u[0-9a-fA-F]{4}))*"""".r) ^^ {
    //         case s => s.substring (1, s.length - 1)
    //     }

    lazy val jnumber : PackratParser[JNumber] =
        regex ("""-?(0|[1-9]\d*)(\.\d+)?([eE][-+]?\d+)?""".r) ^^ {
            case s => JNumber (s.toDouble)
        }

    lazy val jtrue : PackratParser[JTrue] =
        "true" ^^^ JTrue ()

    lazy val jfalse : PackratParser[JFalse] =
        "false" ^^^ JFalse ()

    lazy val jnull : PackratParser[JNull] =
        "null" ^^^ JNull ()

}
