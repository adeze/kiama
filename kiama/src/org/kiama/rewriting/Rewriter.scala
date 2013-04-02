/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2008-2013 Anthony M Sloane, Macquarie University.
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
 * Strategy-based term rewriting in the style of Stratego (http://strategoxt.org/).
 * The implementation here is partially based on the semantics given in "Program
 * Transformation with Scoped Dynamic Rewrite Rules", by Bravenboer, van Dam, Olmos
 * and Visser, Fundamenta Informaticae, 69, 2005. The library strategies are mostly
 * based on the Stratego library, but also on combinators found in the Scrap Your
 * Boilerplate and Uniplate libraries for Haskell.
 */
trait Rewriter extends RewriterCore {

    import scala.collection.generic.CanBuildFrom
    import scala.language.higherKinds

    /**
     * Rewrite a term.  Apply the strategy `s` to a term returning the result term
     * if `s` succeeds, otherwise return the original term.
     */
    def rewrite[T] (s : => Strategy) (t : T) : T = {
        s (t) match {
            case Some (t1) =>
                t1.asInstanceOf[T]
            case None =>
                t
        }
    }

    // Library combinators

    /**
     * A strategy that always fails.
     */
    val fail : Strategy =
        option (None)

    /**
     * A strategy that always succeeds with the subject term unchanged (i.e.,
     * this is the identity strategy).
     */
    val id : Strategy =
        strategyf (t => Some (t))

    /**
     * Collect query results in a traversable collection.  Run the function
     * `f` as a top-down left-to-right query on the subject term.  Accumulate
     * the values produced by the function in the collection and return the
     * final value of the list.
     */
    def collect[CC[U] <: Traversable[U],T] (f : Any ==> T)
            (implicit cbf : CanBuildFrom[CC[T],T,CC[T]]) : Any => CC[T] =
        (t : Any) => {
            val b = cbf ()
            val add = (v : T) => b += v
            (everywhere (query (f andThen add))) (t)
            b.result ()
        }

    /**
     * Collect query results in a list.  Run the function `f` as a top-down
     * left-to-right query on the subject term.  Accumulate the values
     * produced by the function in a list and return the final value of
     * the list.
     */
    def collectl[T] (f : Any ==> T) : Any => List[T] =
        collect[List,T] (f)

    /**
     * Collect query results in a set.  Run the function `f` as a top-down
     * left-to-right query on the subject term.  Accumulate the values
     * produced by the function in a set and return the final value of
     * the set.
     */
    def collects[T] (f : Any ==> T) : Any => Set[T] =
        collect[Set,T] (f)

    /**
     * Count function results.  Run the function `f` as a top-down query on
     * the subject term.  Sum the integer values returned by `f` from all
     * applications.
     */
    def count (f : Any ==> Int) : Any => Int =
        everything (0) (_ + _) (f)

    /**
     * Construct a strategy that applies `s` to each element of a list,
     * returning a new list of the results if all of the applications
     * succeed, otherwise fail.  If all of the applications succeed
     * without change, return the input list.
     */
    def map (s : => Strategy) : Strategy =
        rulefs {
            case Nil           => id
            case l @ (x :: xs) =>
                option (s (x)) <*
                    rulefs {
                        case y =>
                            option (map (s) (xs)) <*
                                rule {
                                    case ys : List[_] =>
                                        if (same (x, y) && same (xs, ys))
                                            l
                                        else
                                            y :: ys
                                }
                    }
        }

    /**
     * Construct a strategy that applies `s`, yielding the result of `s` if it
     * succeeds, otherwise leave the original subject term unchanged.  In
     * Stratego library this strategy is called `try`.
     */
    def attempt (s : => Strategy) : Strategy =
        s <+ id

    /**
     * Construct a strategy that applies `s` repeatedly until it fails.
     */
    def repeat (s : => Strategy) : Strategy =
        attempt (s <* repeat (s))

    /**
     * Construct a strategy that repeatedly applies `s` until it fails and
     * then terminates with application of `c`.
     */
    def repeat (s : => Strategy, c : => Strategy) : Strategy =
        (s <* repeat (s, c)) <+ c

    /**
     * Construct a strategy that applies `s` repeatedly exactly `n` times. If
     * `s` fails at some point during the n applications, the entire strategy
     * fails. The result of the strategy is that of the ''nth'' application of
     * `s`.
     */
    def repeat (s : => Strategy, n : Int) : Strategy =
        if (n == 0) id else s <* repeat (s, n - 1)

    /**
     * Construct a strategy that repeatedly applies `s` (at least once) and
     * terminates with application of `c`.
     */
    def repeat1 (s : => Strategy, c : => Strategy) : Strategy =
        s <* (repeat1 (s, c) <+ c)

    /**
     * Construct a strategy that repeatedly applies `s` (at least once).
     */
    def repeat1 (s : => Strategy) : Strategy =
        repeat1 (s, id)

    /**
     * Construct a strategy that repeatedly applies `s` until `c` succeeds.
     */
    def repeatuntil (s : => Strategy, c : => Strategy) : Strategy =
        s <* (c <+ repeatuntil (s, c))

    /**
     * Construct a strategy that while c succeeds applies `s`.  This operator
     * is called `while` in the Stratego library.
     */
    def loop (c : => Strategy, s : => Strategy) : Strategy =
        attempt (c <* s <* loop (c, s))

    /**
     * Construct a strategy that while `c` does not succeed applies `s`.  This
     * operator is called `while-not` in the Stratego library.
     */
    def loopnot (c : => Strategy, s : => Strategy) : Strategy =
        c <+ (s <* loopnot (c, s))

    /**
     * Construct a strategy that applies `s` at least once and then repeats `s`
     * while `c` succeeds.  This operator is called `do-while` in the Stratego
     * library.
     */
    def doloop (s : => Strategy, c : => Strategy) : Strategy =
       s <* loop (c, s)

    /**
     * Construct a strategy that repeats application of `s` while `c` fails, after
     * initialization with `i`.  This operator is called `for` in the Stratego
     * library.
     */
    def loopiter (i : => Strategy, c : => Strategy, s : => Strategy) : Strategy =
        i <* loopnot (c, s)

    /**
     * Construct a strategy that applies `s(i)` for each integer `i` from `low` to
     * `high` (inclusive).  This operator is called `for` in the Stratego library.
     */
    def loopiter (s : Int => Strategy, low : Int, high : Int) : Strategy =
        if (low <= high)
            s (low) <* loopiter (s, low + 1, high)
        else
            id

    /**
     * Construct a strategy that applies `s`, then fails if `s` succeeded or, if `s`
     * failed, succeeds with the subject term unchanged,  I.e., it tests if
     * `s` applies, but has no effect on the subject term.
     */
    def not (s : => Strategy) : Strategy =
        s < fail + id

    /**
     * Construct a strategy that tests whether strategy `s` succeeds,
     * restoring the original term on success.  This is similar
     * to Stratego's `where`, except that in this version any effects on
     * bindings are not visible outside `s`.
     */
    def where (s : => Strategy) : Strategy =
        strategyf (t => (s <* build (t)) (t))

    /**
     * Construct a strategy that tests whether strategy `s` succeeds,
     * restoring the original term on success.  A synonym for `where`.
     */
    def test (s : => Strategy) : Strategy =
        where (s)

    /**
     * Construct a strategy that applies `s` in breadth first order.
     */
    def breadthfirst (s : => Strategy) : Strategy =
        all (s) <* all (breadthfirst (s))

    /**
     * Construct a strategy that applies `s` in a top-down, prefix fashion
     * to the subject term.
     */
    def topdown (s : => Strategy) : Strategy =
        s <* all (topdown (s))

    /**
     * Construct a strategy that applies `s` in a top-down, prefix fashion
     * to the subject term but stops when the strategy produced by `stop`
     * succeeds. `stop` is given the whole strategy itself as its argument.
     */
    def topdownS (s : => Strategy, stop : (=> Strategy) => Strategy) : Strategy =
        s <* (stop (topdownS (s, stop)) <+ all (topdownS (s, stop)))

    /**
     * Construct a strategy that applies `s` in a bottom-up, postfix fashion
     * to the subject term.
     */
    def bottomup (s : => Strategy) : Strategy =
        all (bottomup (s)) <* s

    /**
     * Construct a strategy that applies `s` in a bottom-up, postfix fashion
     * to the subject term but stops when the strategy produced by `stop`
     * succeeds. `stop` is given the whole strategy itself as its argument.
     */
    def bottomupS (s : => Strategy, stop : (=> Strategy) => Strategy) : Strategy =
        (stop (bottomupS (s, stop)) <+ (all (bottomupS (s, stop))) <* s)

    /**
     * Construct a strategy that applies `s` in a combined top-down and
     * bottom-up fashion (i.e., both prefix and postfix) to the subject
     * term.
     */
    def downup (s : => Strategy) : Strategy =
        s <* all (downup (s)) <* s

    /**
     * Construct a strategy that applies `s1` in a top-down, prefix fashion
     * and `s2` in a bottom-up, postfix fashion to the subject term.
     */
    def downup (s1 : => Strategy, s2 : => Strategy) : Strategy =
        s1 <* all (downup (s1, s2)) <* s2

    /**
     * Construct a strategy that applies `s` in a combined top-down and
     * bottom-up fashion (i.e., both prefix and postfix) to the subject
     * but stops when the strategy produced by `stop` succeeds. `stop` is
     * given the whole strategy itself as its argument.
     */
    def downupS (s : => Strategy, stop : (=> Strategy) => Strategy) : Strategy =
        s <* (stop (downupS (s, stop)) <+ all (downupS (s, stop))) <* s

    /**
     * Construct a strategy that applies `s1` in a top-down, prefix fashion
     * and `s2` in a bottom-up, postfix fashion to the subject term but stops
     * when the strategy produced by `stop` succeeds. `stop` is given the whole
     * strategy itself as its argument.
     */
    def downupS (s1 : => Strategy, s2 : => Strategy, stop : (=> Strategy) => Strategy) : Strategy =
        s1 <* (stop (downupS (s1, s2, stop)) <+ all (downupS (s1, s2, stop))) <* s2

    /**
     * A unit for `topdownS`, `bottomupS` and `downupS`.  For example, `topdown(s)`
     * is equivalent to `topdownS(s, dontstop)`.
     */
    def dontstop (s : => Strategy) : Strategy =
        fail

    /**
     * Construct a strategy that applies `s` in a top-down fashion to one
     * subterm at each level, stopping as soon as it succeeds once (at
     * any level).
     */
    def oncetd (s : => Strategy) : Strategy =
        s <+ one (oncetd (s))

    /**
     * Construct a strategy that applies `s` in a bottom-up fashion to one
     * subterm at each level, stopping as soon as it succeeds once (at
     * any level).
     */
    def oncebu (s : => Strategy) : Strategy =
        one (oncebu (s)) <+ s

    /**
     * Construct a strategy that applies `s` in a top-down fashion to some
     * subterms at each level, stopping as soon as it succeeds once (at
     * any level).
     */
    def sometd (s : => Strategy) : Strategy =
        s <+ some (sometd (s))

    /**
     * Construct a strategy that applies `s` in a bottom-up fashion to some
     * subterms at each level, stopping as soon as it succeeds once (at
     * any level).
     */
    def somebu (s : => Strategy) : Strategy =
        some (somebu (s)) <+ s

    /**
     * Construct a strategy that applies `s` repeatedly in a top-down fashion
     * stopping each time as soon as it succeeds once (at any level). The
     * outermost fails when `s` fails to apply to any (sub-)term.
     */
    def outermost (s : => Strategy) : Strategy =
        repeat (oncetd (s))

    /**
     * Construct a strategy that applies `s` repeatedly to the innermost
     * (i.e., lowest and left-most) (sub-)term to which it applies.
     * Stop with the current term if `s` doesn't apply anywhere.
     */
    def innermost (s : => Strategy) : Strategy =
        bottomup (attempt (s <* innermost (s)))

    /**
     * An alternative version of `innermost`.
     */
    def innermost2 (s : => Strategy) : Strategy =
        repeat (oncebu (s))

    /**
     * Construct a strategy that applies `s` repeatedly to subterms
     * until it fails on all of them.
     */
    def reduce (s : => Strategy) : Strategy = {
        def x : Strategy = some (x) + s
        repeat (x)
    }

    /**
     * Construct a strategy that applies `s` in a top-down fashion, stopping
     * at a frontier where s succeeds.
     */
    def alltd (s : => Strategy) : Strategy =
        s <+ all (alltd (s))

    /**
     * Construct a strategy that applies `s` in a bottom-up fashion to all
     * subterms at each level, stopping at a frontier where s succeeds.
     */
    def allbu (s : => Strategy) : Strategy =
        all (allbu (s)) <+ s

    /**
     * Construct a strategy that applies `s1` in a top-down, prefix fashion
     * stopping at a frontier where `s1` succeeds.  `s2` is applied in a bottom-up,
     * postfix fashion to the result.
     */
    def alldownup2 (s1 : => Strategy, s2 : => Strategy) : Strategy =
        (s1 <+ all (alldownup2 (s1, s2))) <* s2

    /**
     * Construct a strategy that applies `s1` in a top-down, prefix fashion
     * stopping at a frontier where `s1` succeeds.  `s2` is applied in a bottom-up,
     * postfix fashion to the results of the recursive calls.
     */
    def alltdfold (s1 : => Strategy, s2 : => Strategy) : Strategy =
        s1 <+ (all (alltdfold (s1, s2)) <* s2)

    /**
     * Construct a strategy that applies `s` in a top-down, prefix fashion
     * stopping at a frontier where `s` succeeds on some children.  `s` is then
     * applied in a bottom-up, postfix fashion to the result.
     */
    def somedownup (s : => Strategy) : Strategy =
        (s <* all (somedownup (s)) <* (attempt (s))) <+ (some (somedownup (s)) <+ attempt (s))

    /**
     * Construct a strategy that applies `s` as many times as possible, but
     * at least once, in bottom up order.
     */
    def manybu (s : Strategy) : Strategy =
        some (manybu (s)) <* attempt (s) <+ s

    /**
     * Construct a strategy that applies `s` as many times as possible, but
     * at least once, in top down order.
     */
    def manytd (s : Strategy) : Strategy =
        s <* all (attempt (manytd (s))) <+ some (manytd (s))

    /**
     * Construct a strategy that tests whether the two sub-terms of a
     * pair of terms are equal.
     */
    val eq : Strategy =
       rule {
           case t @ (x, y) if x == y => t
       }

    /**
     * Construct a strategy that tests whether the two sub-terms of a
     * pair of terms are equal. Synonym for `eq`.
     */
    val equal : Strategy =
        eq

    /**
     * Construct a strategy that succeeds when applied to a pair `(x,y)`
     * if `x` is a sub-term of `y`.
     */
    val issubterm : Strategy =
        strategy {
            case (x : Any, y : Any) => where (oncetd (term (x))) (y)
        }

    /**
     * Construct a strategy that succeeds when applied to a pair `(x,y)`
     * if `x` is a sub-term of `y` but is not equal to `y`.
     */
    val ispropersubterm : Strategy =
        not (eq) <* issubterm

    /**
     * Construct a strategy that succeeds when applied to a pair `(x,y)`
     * if `x` is a superterm of `y`.
     */
    val issuperterm : Strategy =
        strategy {
            case (x, y) => issubterm (y, x)
        }

    /**
     * Construct a strategy that succeeds when applied to a pair `(x,y)`
     * if `x` is a super-term of `y` but is not equal to `y`.
     */
    val ispropersuperterm : Strategy =
        not (eq) <* issuperterm

    /**
     * Construct a strategy that succeeds if the current term has no
     * direct subterms.
     */
    val isleaf : Strategy =
      all (fail)

    /**
     * Construct a strategy that applies to all of the leaves of the
     * current term, using `isleaf` as the leaf predicate.
     */
    def leaves (s : => Strategy, isleaf : => Strategy) : Strategy =
        (isleaf <* s) <+ all (leaves (s, isleaf))

    /**
     * Construct a strategy that applies to all of the leaves of the
     * current term, using `isleaf` as the leaf predicate, skipping
     * subterms for which `skip` when applied to the result succeeds.
     */
    def leaves (s : => Strategy, isleaf : => Strategy, skip : Strategy => Strategy) : Strategy =
        (isleaf <* s) <+ skip (leaves (s, isleaf, skip)) <+ all (leaves (s, isleaf, skip))

    /**
     * Construct a strategy that succeeds if the current term has at
     * least one direct subterm.
     */
    val isinnernode : Strategy =
        one (id)

    /**
     * Construct a strategy that applies `s` at all terms in a bottom-up fashion
     * regardless of failure.  Terms for which the strategy fails are left
     * unchanged.
     */
    def everywherebu (s : => Strategy) : Strategy =
        bottomup (attempt (s))

    /**
     * Construct a strategy that applies `s` at all terms in a top-down fashion
     * regardless of failure.  Terms for which the strategy fails are left
     * unchanged.
     */
    def everywheretd (s : => Strategy) : Strategy =
        topdown (attempt (s))

    /**
     * Same as `everywheretd`.
     */
    def everywhere (s : => Strategy) : Strategy =
        everywheretd (s)

    /**
     * Apply the function at every term in `t` in a top-down, left-to-right order.
     * Collect the resulting `T` values by accumulating them using `f` with initial
     * left value `v`.  Return the final value of the accumulation.
     */
    def everything[T] (v : T) (f : (T, T) => T) (g : Any ==> T) (t : Any) : T =
        (collectl (g) (t)).foldLeft (v) (f)

    /**
     * Construct a strategy that applies `s`, then applies the restoring action
     * `rest` if `s` fails (and then fail). Otherwise, let the result of `s` stand.
     * Typically useful if `s` performs side effects that should be restored or
     * undone when `s` fails.
     */
    def restore (s : => Strategy, rest : => Strategy) : Strategy =
        s <+ (rest <* fail)

    /**
     * Construct a strategy that applies `s`, then applies the restoring action
     * `rest` regardless of the success or failure of `s`. The whole strategy
     * preserves the success or failure of `s`. Typically useful if `s` performs
     * side effects that should be restored always, e.g., when maintaining scope
     * information.
     */
    def restorealways (s : => Strategy, rest : => Strategy) : Strategy =
        s < rest + (rest <* fail)

    /**
     * Applies `s` followed by `f` whether `s` failed or not.
     * This operator is called `finally` in the Stratego library.
     */
    def lastly (s : => Strategy, f : => Strategy) : Strategy =
        s < where (f) + (where (f) <* fail)

    /**
     * `ior(s1, s2)` implements inclusive OR, that is, the
     * inclusive choice of `s1` and `s2`. It first tries `s1`. If
     * that fails it applies `s2` (just like `s1 <+ s2`). However,
     * when `s1` succeeds it also tries to apply `s2`.
     */
    def ior (s1 : => Strategy, s2 : => Strategy) : Strategy =
        (s1 <* attempt (s2)) <+ s2

    /**
     * `or(s1, s2)` is similar to `ior(s1, s2)`, but the application
     * of the strategies is only tested.
     */
    def or (s1 : => Strategy, s2 : => Strategy) : Strategy =
        where (s1) < attempt (test (s2)) + test (s2)

    /**
     * `and(s1, s2)` applies `s1` and `s2` to the current
     * term and succeeds if both succeed. `s2` will always
     * be applied, i.e., and is ''not'' a short-circuit
     * operator
     */
    def and (s1 : => Strategy, s2 : => Strategy) : Strategy =
        where (s1) < test (s2) + (test (s2) <* fail)

}

/**
 * Strategy-based term rewriting for arbitrary terms.
 */
object Rewriter extends Rewriter
