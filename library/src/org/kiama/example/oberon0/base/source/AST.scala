/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2011-2013 Anthony M Sloane, Macquarie University.
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
package example.oberon0
package base.source

import org.kiama.attribution.Attributable
import org.kiama.util.Positioned
import scala.collection.immutable.Seq

/**
 * Root type of all source abstract syntax tree nodes.
 */
abstract class SourceASTNode extends Attributable with Positioned

/**
 * Non-terminal type for declarations.
 */
abstract class Declaration extends SourceASTNode

/**
 * Module declarations.
 */
case class ModuleDecl (idndef : IdnDef, block : Block, idnuse : IdnUse) extends SourceASTNode

/**
 * Non-terminal type for statements.
 */
abstract class Statement extends SourceASTNode

/**
 * Block of declarations and statements.
 */
case class Block (decls : Seq[Declaration], stmts: Seq[Statement]) extends Statement

/**
 * Empty statements.
 */
case class EmptyStmt () extends Statement

/**
 * Common interface for all identifier occurrences.
 */
abstract class Identifier extends SourceASTNode {
    def ident : String
}

/**
 * Defining occurrences of identifiers
 */
case class IdnDef (ident : String) extends Identifier

/**
 * Applied occurrences (uses) of identifiers.
 */
case class IdnUse (ident : String) extends Identifier
