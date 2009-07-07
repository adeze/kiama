/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2009 Anthony M Sloane, Macquarie University.
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

package kiama.example.lambda2

/**
 * Eager evaluation of lambda calculus with term-level substitution and
 * arithmetic operations.
 */
trait EagerSubst extends ReduceSubst {
  
	import AST._

    /**
     * Eagerly evaluate within the expression then try to reduce the
     * expression itself, repeating until no change.
     */
    override lazy val evals : Strategy =
        attempt (traverse) <* attempt (lambda_es <* evals)
    
    /**
     * Recursively try to eagerly evaluate expressions in applications,
     * substitutions and operations, but not within lambdas.
     */
    lazy val traverse : Strategy =
        rule {

            // In an application we need to traverse to both sub-expressions.
            case App (e1, e2)       => App (eval (e1), eval (e2))

            // In a substitution we need to traverse to the bound expression
            // as well as the body expression.
            case Let (x, t, e1, e2) => Let (x, t, eval (e1), eval (e2))

            // In an operation we need to traverse to both sub-expressions.
            case Opn (op, e1, e2)   => Opn (op, eval (e1), eval (e2))

        }

}

class EagerSubstEvaluator extends EagerSubst
