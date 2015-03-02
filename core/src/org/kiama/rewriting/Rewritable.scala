/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2011-2015 Anthony M Sloane, Macquarie University.
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
package rewriting

/**
 * Types that implement this interface can be rewritten using the methods
 * of Kiama's Rewriter library.  Implementing this interface is not
 * necessary if the type implements Product (which automatically includes
 * all case class instances and case objects).
 */
trait Rewritable {

    import scala.collection.immutable.Seq

    /**
     * Return the number of components that this value has.  Should be
     * greater than or equal to zero.
     */
    def arity : Int

    /**
     * Return a finite sequence containing the components of this value.
     */
    def deconstruct : Seq[Any]

    /**
     * Return a new value constructed from the given original value and
     * the given a finite sequence of the new components. In most cases,
     * the new value should be of the same type as the original with the
     * new components, but this is not required.  This method should throw an
     * IllegalArgumentException if any of the components are not
     * appropriate (e.g., there is the wrong number of them or they are
     * of the wrong type).
     */
    def reconstruct (components : Seq[Any]) : Any

    /**
     * Helper function that can be called by implementations of reconstruct
     * to report an error.  desc is a description of the structure that
     * was being constructed, argtypes is a string describing the expected
     * types of the arguments and args is the argument sequence that was
     * provided.  An IllegalArgumentException containing a description
     * of the problem is thrown.
     */
    protected def illegalArgs (desc : String, argtypes : String, args : Seq[Any]) =
        throw (new IllegalArgumentException (s"making $desc: expecting $argtypes, got ${args.mkString (", ")}"))

}
