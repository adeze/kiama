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
package example.picojava

trait PredefinedTypes {

    self : NameResolution =>

    import PicoJavaTree._
    import org.kiama.attribution.Attribution._
    import scala.collection.immutable.Seq

    /*
     * A list of declarations of primitive types.
     *
     * syn lazy List Program.getPredefinedTypeList() {
     *    return new List().
     *        add(new UnknownDecl("$unknown")).
     *        add(new PrimitiveDecl("boolean"));
     * }
     */
    val getPredefinedTypeList : Program => Seq[TypeDecl] =
        constant {
            Seq (UnknownDecl ("$unknown"),
                 PrimitiveDecl ("boolean"),
                 PrimitiveDecl ("int"))
        }

    /**
     * Make the boolean type available.
     *
     * syn lazy PrimitiveDecl Program.booleanType() = (PrimitiveDecl) localLookup("boolean");
     * inh PrimitiveDecl BooleanLiteral.booleanType();
     * inh PrimitiveDecl WhileStmt.booleanType();
     * inh PrimitiveDecl Decl.booleanType();
     * eq Program.getBlock().booleanType() = booleanType();
     * eq Program.getPredefinedType().booleanType() = booleanType();
     */
    val booleanType : PicoJavaNode => PrimitiveDecl =
        attr {
            case p : Program =>
                localLookup ("boolean") (p).asInstanceOf[PrimitiveDecl]
            // FIXME don't have NTA case, needed?
            case tree.parent (p) =>
                booleanType (p)
        }

}
