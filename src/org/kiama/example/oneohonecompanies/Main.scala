/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2010-2012 Anthony M Sloane, Macquarie University.
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
package example.oneohonecompanies

import org.kiama.util.StdoutEmitter

object Main extends StdoutEmitter {

    import SampleCompany.company

    def main (args : Array[String]) {
        emitter.emitln (Total.total (company))
        emitter.emitln (Total.total (Cut.cut (company)))
        emitter.emitln (Depth.depth (company))
        emitter.emitln (Precedence.precedence (company))
    }

}
