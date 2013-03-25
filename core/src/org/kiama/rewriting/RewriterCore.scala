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
 * Core implementation of strategy-based rewriting. Implement and construct
 * basic strategies and rewrite rules.
 */
trait RewriterCore {

    import org.kiama.util.Emitter
    import scala.collection.generic.CanBuildFrom
    import scala.collection.mutable.Builder
    import scala.collection.mutable.WeakHashMap
    import scala.language.higherKinds

    /**
     * The type of terms that can be rewritten.  Any type of value is acceptable
     * but generic traversals will only work on some specific types.  See the
     * documentation of the specific generic traversals (e.g., `all` or `some`)
     * for a detailed description.
     */
    type Term = Any

    /**
     * Term-rewriting strategies. A strategy is a function that takes a term
     * as input and either succeeds producing a new term (`Some`), or fails
     * (`None`).
     */
    abstract class Strategy extends (Term => Option[Term]) {

        /**
         * Alias this strategy as `p` to make it easier to refer to in the
         * combinator definitions.
         */
        p =>

        /**
         * Apply this strategy to a term, producing either a transformed term
         * wrapped in `Some`, or `None`, representing a rewriting failure.
         */
        def apply (r : Term) : Option[Term]

        /**
         * Sequential composition. Construct a strategy that first applies
         * this strategy. If it succeeds, then apply `q` to the new subject
         * term. Otherwise fail.
         */
        def <* (q : => Strategy) : Strategy =
            new Strategy {
                def apply (t1 : Term) : Option[Term] =
                    p (t1) match {
                        case Some (t2) => q (t2)
                        case None      => None
                    }
            }

        /**
         * Deterministic choice.  Construct a strategy that first applies
         * this strategy. If it succeeds, succeed with the resulting term.
         * Otherwise, apply `q` to the original subject term.
         */
        def <+ (q : => Strategy) : Strategy =
            new Strategy {
                def apply (t1 : Term) : Option[Term] =
                    p (t1) match {
                        case Some (t2) => Some (t2)
                        case None      => q (t1)
                    }
            }

        /**
         * Non-deterministic choice. Normally, construct a strategy that
         * first applies either this strategy or the given strategy. If it
         * succeeds, succeed with the resulting term. Otherwise, apply `q`.
         * Currently implemented as deterministic choice, but this behaviour
         * should not be relied upon.
         * When used as the argument to the `<` conditional choice
         * combinator, `+` just serves to hold the two strategies that are
         * chosen between by the conditional choice.
         */
        def + (q : => Strategy) : PlusStrategy =
            new PlusStrategy (p, q)

        /**
         * Conditional choice: `c < l + r`. Construct a strategy that first
         * applies this strategy (`c`). If `c` succeeds, the strategy applies
         * `l` to the resulting term, otherwise it applies `r` to the original
         * subject term.
         */
        def < (lr : => PlusStrategy) : Strategy =
            new Strategy {
                def apply (t1 : Term) : Option[Term] =
                    p (t1) match {
                        case Some (t2) => lr.lhs (t2)
                        case None      => lr.rhs (t1)
                    }
            }

    }

    /**
     * Helper class to contain commonality of choice in non-deterministic
     * choice operator and then-else part of a conditional choice. Only
     * returned by the non-deterministic choice operator.
     */
    class PlusStrategy (p : => Strategy, q : => Strategy) extends Strategy {
        val lhs = p
        val rhs = q
        def apply (t : Term) : Option[Term] =
            (p <+ q) (t)
    }

    /**
     * Make a strategy from a function `f`. The function return value
     * determines whether the strategy succeeds or fails.
     */
    def strategyf (f : Term => Option[Term]) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] =
                f (t)
        }

    /**
     * Make a strategy from a partial function `f`. If the function is
     * defined at the current term, then the function return value
     * when applied to the current term determines whether the strategy
     * succeeds or fails. If the function is not defined at the current
     * term, the strategy fails.
     */
    def strategy (f : Term ==> Option[Term]) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] = {
                if (f isDefinedAt t)
                    f (t)
                else
                    None
            }
        }

    /**
     * Define a rewrite rule using a function `f` that returns a term.
     * The rule always succeeds with the return value of the function.
     */
    def rulef (f : Term => Term) : Strategy =
        strategyf (t => Some (f (t)))

    /**
     * Define a rewrite rule using a partial function `f`. If the function is
     * defined at the current term, then the strategy succeeds with the return
     * value of the function applied to the current term. Otherwise the
     * strategy fails.
     */
    def rule (f : Term ==> Term) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] = {
                if (f isDefinedAt t)
                    Some (f (t))
                else
                    None
            }
        }

    /**
     * Define a rewrite rule using a function `f` that returns a strategy.  The
     * rule applies the function to the subject term to get a strategy which
     * is then applied again to the subject term. In other words, the function
     * is only used for side-effects such as pattern matching.  The whole thing
     * also fails if `f` is not defined at the term in the first place.
     */
    def rulefs (f : Term ==> Strategy) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] = {
                if (f isDefinedAt t)
                    (f (t)) (t)
                else
                    None
            }
        }

    /**
     * Construct a strategy that always succeeds, changing the subject term to
     * the given term `t`.
     */
    def build (t : => Term) : Strategy =
        rulef (_ => t)

    /**
     * Construct a strategy from an option value `o`. The strategy succeeds
     * or fails depending on whether `o` is a Some or None, respectively.
     * If `o` is a `Some`, then the subject term is changed to the term that
     * is wrapped by the `Some`.
     */
    def option (o : => Option[Term]) : Strategy =
        strategyf (_ => o)

    /**
     * Define a term query by a function `f`. The query always succeeds with
     * no effect on the subject term but applies the given (possibly partial)
     * function `f` to the subject term.  In other words, the strategy runs
     * `f` for its side-effects.
     */
    def queryf[T] (f : Term => T) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] = {
                f (t)
                Some (t)
            }
        }

    /**
     * Define a term query by a partial function `f`. The query always succeeds
     * with no effect on the subject term but applies the given partial function
     * `f` to the subject term.  In other words, the strategy runs `f` for its
     * side-effects.
     */
    def query[T] (f : Term ==> T) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] = {
                if (f isDefinedAt t)
                    f (t)
                Some (t)
            }
        }

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
     * A strategy that always succeeds with the subject term unchanged (i.e.,
     * this is the identity strategy) with the side-effect that the subject
     * term is printed to the given emitter, prefixed by the string `s`.  The
     * emitter defaults to one that writes to standard output.
     */
    def debug (msg : String, emitter : Emitter = new Emitter) : Strategy =
        strategyf (t => { emitter.emitln (msg + t); Some (t) })

    /**
     * Create a logging strategy based on a strategy `s`. The returned strategy
     * succeeds or fails exactly as `s` does, but also prints the provided message,
     * the subject term, the success or failure status, and on success, the result
     * term, to the provided emitter (default: standard output).
     */
    def log[T] (s : => Strategy, msg : String, emitter : Emitter = new Emitter) : Strategy =
        new Strategy {
            def apply (t1 : Term) : Option[Term] = {
                emitter.emit (msg + t1)
                val r = s (t1)
                r match {
                    case Some (t2) =>
                        emitter.emitln (" succeeded with " + t2)
                    case None =>
                        emitter.emitln (" failed")
                }
                r
            }
        }

    /**
     * Create a logging strategy based on a strategy `s`.  The returned strategy
     * succeeds or fails exactly as `s` does, but if `s` fails, also prints the
     * provided message and the subject term to the provided emitter (default:
     * standard output).
     */
    def logfail[T] (s : => Strategy, msg : String, emitter : Emitter = new Emitter) : Strategy =
        new Strategy {
            def apply (t1 : Term) : Option[Term] = {
                val r = s (t1)
                r match {
                    case Some (t2) =>
                        // Do nothing
                    case None =>
                        emitter.emitln (msg + t1 + " failed")
                }
                r
            }
        }

    /**
     * Construct a strategy that succeeds only if the subject term matches
     * the given term `t`.
     */
    def term (t : Term) : Strategy =
        rule {
            case `t` => t
        }

    /**
     * Generic term deconstruction.
     */
    object Term {

        /**
         * Generic term deconstruction. An extractor that decomposes `Product`
         * or `Rewritable` values into the value itself and a sequence of its
         * children.  Terms that are not `Product` or `Rewritable` are not
         * decomposable (i.e., the list of children will be empty).
         */
        def unapply (t : Any) : Option[(Any,Seq[Any])] = {
            t match {
                case r : Rewritable =>
                    Some ((r, r.deconstruct))
                case p : Product =>
                    val cs = for (i <- 0 until p.productArity) yield p.productElement (i)
                    Some ((p, cs))
                case _ =>
                    Some ((t, Nil))
            }
        }

    }

    /**
     * Perform a paramorphism over a value. This is a fold in which the
     * recursive step may refer to the recursive component of the value
     * and the results of folding over the children.  When the function `f`
     * is called, the first parameter is the value and the second is a
     * sequence of the values that `f` has returned for the children.  his
     * will work on any value, but will only decompose values that are
     * supported by the `Term` generic term deconstruction.  This operation
     * is similar to that used in the Uniplate library.
     */
    def para[T] (f : (Any, Seq[T]) => T) : Any => T = {
        case Term (t, ts) => f (t, ts.map (para (f)))
    }

    /**
     * Cache of constructors for product duplication.
     */
    protected val constrcache =
        new WeakHashMap[java.lang.Class[_], java.lang.reflect.Constructor[_]]

    /**
     * General product duplication function.  Returns a product that applies
     * the same constructor as the product `t`, but with the given children
     * instead of `t`'s children.  Fails if a constructor cannot be found,
     * there are the wrong number of new children, or if one of the new
     * children is not of the appropriate type.
     */
    protected def dup[T <: Product] (t : T, children : Array[AnyRef]) : T = {
        val clazz = t.getClass
        val ctor = constrcache.getOrElseUpdate (clazz, (clazz.getConstructors())(0))
        try {
            ctor.newInstance (children : _*).asInstanceOf[T]
        } catch {
            case e : IllegalArgumentException =>
                sys.error ("dup illegal arguments: " + ctor + " (" +
                           children.deep.mkString (",") + "), expects " +
                           ctor.getParameterTypes.length)
        }
    }

    /**
     * Make an arbitrary value `c` into a term child, checking that it worked
     * properly. Object references will be returned unchanged; other values
     * will be boxed.
     */
    protected def makechild (c : Any) : AnyRef =
        c.asInstanceOf[AnyRef]

    /**
     * Traversal to a single child.  Construct a strategy that applies `s` to
     * the ''ith'' child of the subject term (counting from one).  If `s` succeeds on
     * the ''ith'' child producing `t`, then succeed, forming a new term that is the
     * same as the original term except that the ''ith'' child is now `t`.  If `s` fails
     * on the ''ith'' child or the subject term does not have an ''ith'' child, then fail.
     * `child(i, s)` is equivalent to Stratego's `i(s)` operator.  If `s` succeeds on
     * the ''ith'' child producing the same term (by `eq` for references and by `==` for
     * other values), then the overall strategy returns the subject term.
     * This operation works for instances of `Product` or finite `Seq` values.
     */
    def child (i : Int, s : Strategy) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] =
                t match {
                    case p : Product => childProduct (p)
                    case t : Seq[_]  => childSeq (t.asInstanceOf[Seq[Term]])
                    case _           => None
                }

            private def childProduct (p : Product) : Option[Term] = {
                val numchildren = p.productArity
                if ((i < 1) || (i > numchildren)) {
                    None
                } else {
                    val ct = p.productElement (i-1)
                    s (ct) match {
                        case Some (ti) if (same (ct, ti)) =>
                            Some (p)
                        case Some (ti) =>
                            val newchildren = new Array[AnyRef](numchildren)
                            var j = 0
                            while (j < numchildren) {
                                newchildren (j) = makechild (p.productElement (j))
                                j = j + 1
                            }
                            newchildren (i-1) = makechild (ti)
                            val ret = dup (p, newchildren)
                            Some (ret)
                        case None =>
                            None
                    }
                }
            }

            private def childSeq[CC[U] <: Seq[U]] (t : CC[Term])
                            (implicit cbf : CanBuildFrom[CC[Term], Term, CC[Term]])
                                : Option[CC[Term]] = {
                val numchildren = t.size
                if ((i < 1) || (i > numchildren)) {
                    None
                } else {
                    val ct = t (i - 1)
                    s (ct) match {
                        case Some (ti) if (same (ct, ti)) =>
                            Some (t)
                        case Some (ti) =>
                            val b = cbf (t)
                            b.sizeHint (t.size)
                            var j = 0
                            while (j < i - 1) {
                                b += t (j)
                                j = j + 1
                            }
                            b += ti
                            j = j + 1
                            while (j < numchildren) {
                                b += t (j)
                                j = j + 1
                            }
                            Some (b.result)
                        case None =>
                            None
                    }
                }
            }
        }

    /**
     * Compare two arbitrary values. If they are both references, use
     * reference equality, otherwise throw an error since we should be
     * able to cast anything to reference.
     */
    protected def same (v1 : Any, v2 : Any) : Boolean =
        if (v1 == null)
            v2 == null
        else if (v2 == null)
            false
        else
            (v1, v2) match {
                case (r1 : AnyRef, r2: AnyRef) =>
                    r1 eq r2
                case _ =>
                    sys.error ("Rewriter.same: comparison of non-AnyRefs " + v1 + " and " +
                               v2 + ", should not be reached")
            }

    /**
     * Traversal to all children.  Construct a strategy that applies `s` to all
     * term children of the subject term.  If `s` succeeds on all of the children,
     * then succeed, forming a new term from the constructor
     * of the original term and the result of `s` for each child.  If `s` fails on any
     * child, fail. If there are no children, succeed.  If `s` succeeds on all
     * children producing the same terms (by `eq` for references and by `==` for
     * other values), then the overall strategy returns the subject term.
     * This operation works on finite `Rewritable`, `Product`, `Map` and `Traversable`
     * values, checked for in that order.
     * Children of a `Rewritable` (resp. Product, collection) value are processed
     * in the order returned by the value's deconstruct (resp. `productElement`,
     * `foreach`) method.
     */
    def all (s : => Strategy) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] =
                t match {
                    case r : Rewritable     => allRewritable (r)
                    case p : Product        => allProduct (p)
                    case m : Map[_,_]       => allMap (m.asInstanceOf[Map[Term,Term]])
                    case t : Traversable[_] => allTraversable (t.asInstanceOf[Traversable[Term]])
                    case _                  => Some (t)
                }

            private def allProduct (p : Product) : Option[Term] = {
                val numchildren = p.productArity
                if (numchildren == 0)
                    Some (p)
                else {
                    val newchildren = new Array[AnyRef](numchildren)
                    var changed = false
                    var i = 0
                    while (i < numchildren) {
                        val ct = p.productElement (i)
                        s (ct) match {
                            case Some (ti) =>
                                newchildren (i) = makechild (ti)
                                if (!same (ct, ti))
                                    changed = true
                            case None =>
                                return None
                        }
                        i = i + 1
                    }
                    if (changed) {
                        val ret = dup (p, newchildren)
                        Some (ret)
                    } else
                        Some (p)
                }
            }

            private def allRewritable (r : Rewritable) : Option[Term] = {
                val numchildren = r.arity
                if (numchildren == 0)
                    Some (r)
                else {
                    val children = r.deconstruct
                    val newchildren = new Array[Any](numchildren)
                    var changed = false
                    var i = 0
                    while (i < numchildren) {
                        val ct = children (i)
                        s (ct) match {
                            case Some (ti) =>
                                newchildren (i) = makechild (ti)
                                if (!same (ct, ti))
                                    changed = true
                            case None =>
                                return None
                        }
                        i = i + 1
                    }
                    if (changed) {
                        val ret = r.reconstruct (newchildren)
                        Some (ret)
                    } else
                        Some (r)
                }
            }

            private def allTraversable[CC[_] <: Traversable[Term]] (t : CC[Term])
                            (implicit cbf : CanBuildFrom[CC[Term], Term, CC[Term]])
                                : Option[CC[Term]] =
                if (t.size == 0)
                    Some (t)
                else {
                    val b = cbf (t)
                    b.sizeHint (t.size)
                    var changed = false
                    for (ct <- t)
                        s (ct) match {
                            case Some (ti) =>
                                b += ti
                                if (!same (ct, ti))
                                    changed = true
                            case None =>
                                return None
                        }
                    if (changed)
                        Some (b.result)
                    else
                        Some (t)
                }

            private def allMap[CC[V,W] <: Map[V,W]] (t : CC[Term,Term])
                            (implicit cbf : CanBuildFrom[CC[Term,Term], (Term, Term), CC[Term,Term]])
                                : Option[CC[Term,Term]] =
                if (t.size == 0)
                    Some (t)
                else {
                    val b = cbf (t)
                    b.sizeHint (t.size)
                    var changed = false
                    for (ct <- t)
                        s (ct) match {
                            case Some (ti @ (tix,tiy)) =>
                                b += ti
                                if (!same (ct, ti))
                                    changed = true
                            case _ =>
                                return None
                        }
                    if (changed)
                        Some (b.result)
                    else
                        Some (t)
                }
        }

    /**
     * Traversal to one child.  Construct a strategy that applies `s` to the term
     * children of the subject term.  Assume that `c` is the
     * first child on which s succeeds.  Then stop applying `s` to the children and
     * succeed, forming a new term from the constructor of the original term and
     * the original children, except that `c` is replaced by the result of applying
     * `s` to `c`.  In the event that the strategy fails on all children, then fail.
     * If there are no children, fail.  If `s` succeeds on the one child producing
     * the same term (by `eq` for references and by `==` for other values), then
     * the overall strategy returns the subject term.
     * This operation works on instances of finite `Rewritable`, `Product`, `Map`
     * and `Traversable` values, checked for in that order.
     * Children of a `Rewritable` (resp. `Product`, collection) value are processed
     * in the order returned by the value's `deconstruct` (resp. `productElement`,
     * `foreach`) method.
     */
    def one (s : => Strategy) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] =
                t match {
                    case r : Rewritable     => oneRewritable (r)
                    case p : Product        => oneProduct (p)
                    case m : Map[_,_]       => oneMap (m.asInstanceOf[Map[Term,Term]])
                    case t : Traversable[_] => oneTraversable (t.asInstanceOf[Traversable[Term]])
                    case _                  => None
                }

            private def oneProduct (p : Product) : Option[Term] = {
                val numchildren = p.productArity
                var i = 0
                while (i < numchildren) {
                    val ct = p.productElement (i)
                    s (ct) match {
                        case Some (ti) if (same (ct, ti)) =>
                            return Some (p)
                        case Some (ti) =>
                            val newchildren = new Array[AnyRef] (numchildren)
                            var j = 0
                            while (j < i) {
                                newchildren (j) = makechild (p.productElement (j))
                                j = j + 1
                            }
                            newchildren (i) = makechild (ti)
                            j = j + 1
                            while (j < numchildren) {
                                newchildren (j) = makechild (p.productElement (j))
                                j = j + 1
                            }
                            val ret = dup (p, newchildren)
                            return Some (ret)
                        case None =>
                            // Do nothing
                    }
                    i = i + 1
                }
                None
            }

            private def oneRewritable (r : Rewritable) : Option[Term] = {
                val numchildren = r.arity
                val children = r.deconstruct
                var i = 0
                while (i < numchildren) {
                    val ct = children (i)
                    s (ct) match {
                        case Some (ti) if (same (ct, ti)) =>
                            return Some (r)
                        case Some (ti) =>
                            val newchildren = new Array[Any] (numchildren)
                            var j = 0
                            while (j < i) {
                                newchildren (j) = makechild (children (j))
                                j = j + 1
                            }
                            newchildren (i) = makechild (ti)
                            j = j + 1
                            while (j < numchildren) {
                                newchildren (j) = makechild (children (j))
                                j = j + 1
                            }
                            val ret = r.reconstruct (newchildren)
                            return Some (ret)
                        case None =>
                            // Do nothing
                    }
                    i = i + 1
                }
                None
            }

            private def oneTraversable[CC[U] <: Traversable[U]] (t : CC[Term])
                            (implicit cbf : CanBuildFrom[CC[Term], Term, CC[Term]])
                                : Option[CC[Term]] = {
                val b = cbf (t)
                b.sizeHint (t.size)
                var add = true
                for (ct <- t)
                    if (add)
                        s (ct) match {
                            case Some (ti) if same (ct, ti) =>
                                return Some (t)
                            case Some (ti) =>
                                b += ti
                                add = false
                            case None =>
                                b += ct
                        }
                    else
                        b += ct
                if (add)
                    None
                else
                    Some (b.result)
            }

            private def oneMap[CC[V,W] <: Map[V,W]] (t : CC[Term,Term])
                            (implicit cbf : CanBuildFrom[CC[Term,Term], (Term, Term), CC[Term,Term]])
                                : Option[CC[Term,Term]] = {
                val b = cbf (t)
                b.sizeHint (t.size)
                var add = true
                for (ct <- t)
                    if (add)
                        s (ct) match {
                            case Some (ti @ (tix,tiy)) if (same (ct, ti)) =>
                                return Some (t)
                            case Some (ti @ (tix, tiy)) =>
                                b += ti
                                add = false
                            case None =>
                                b += ct
                        }
                    else
                        b += ct
                if (add)
                    None
                else
                    Some (b.result)
            }

        }

    /**
     * Traversal to as many children as possible, but at least one.  Construct a
     * strategy that applies `s` to the term children of the subject term.
     * If `s` succeeds on any of the children, then succeed,
     * forming a new term from the constructor of the original term and the result
     * of `s` for each succeeding child, with other children unchanged.  In the event
     * that the strategy fails on all children, then fail. If there are no
     * children, fail.  If `s` succeeds on children producing the same terms (by `eq`
     * for references and by `==` for other values), then the overall strategy
     * returns the subject term.
     * This operation works on instances of finite `Rewritable`, `Product`, `Map` and
     * `Traversable` values, checked for in that order.
     * Children of a `Rewritable` (resp. `Product`, collection) value are processed
     * in the order returned by the value's `deconstruct` (resp. `productElement`,
     * `foreach`) method.
     */
    def some (s : => Strategy) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] =
                t match {
                    case r : Rewritable     => someRewritable (r)
                    case p : Product        => someProduct (p)
                    case m : Map[_,_]       => someMap (m.asInstanceOf[Map[Term,Term]])
                    case t : Traversable[_] => someTraversable (t.asInstanceOf[Traversable[Term]])
                    case _                  => None
                }

            private def someProduct (p : Product) : Option[Term] = {
                val numchildren = p.productArity
                if (numchildren == 0)
                    None
                else {
                    val newchildren = new Array[AnyRef](numchildren)
                    var success = false
                    var changed = false
                    var i = 0
                    while (i < numchildren) {
                        val ct = p.productElement (i)
                        s (ct) match {
                            case Some (ti) =>
                                newchildren (i) = makechild (ti)
                                if (!same (ct, ti))
                                    changed = true
                                success = true
                            case None =>
                                newchildren (i) = makechild (ct)
                        }
                        i = i + 1
                    }
                    if (success)
                        if (changed) {
                            val ret = dup (p, newchildren)
                            Some (ret)
                        } else
                            Some (p)
                    else
                        None
                }
            }

            private def someRewritable (r : Rewritable) : Option[Term] = {
                val numchildren = r.arity
                if (numchildren == 0)
                    None
                else {
                    val children = r.deconstruct
                    val newchildren = new Array[Any](numchildren)
                    var success = false
                    var changed = false
                    var i = 0
                    while (i < numchildren) {
                        val ct = children (i)
                        s (ct) match {
                            case Some (ti) =>
                                newchildren (i) = makechild (ti)
                                if (!same (ct, ti))
                                    changed = true
                                success = true
                            case None =>
                                newchildren (i) = makechild (ct)
                        }
                        i = i + 1
                    }
                    if (success)
                        if (changed) {
                            val ret = r.reconstruct (newchildren)
                            Some (ret)
                        } else
                            Some (r)
                    else
                        None
                }
            }

            private def someTraversable[CC[U] <: Traversable[U]] (t : CC[Term])
                            (implicit cbf : CanBuildFrom[CC[Term], Term, CC[Term]])
                                : Option[CC[Term]] =
                if (t.size == 0)
                    None
                else {
                    val b = cbf (t)
                    b.sizeHint (t.size)
                    var success = false
                    var changed = false
                    for (ct <- t)
                        s (ct) match {
                            case Some (ti) =>
                                b += ti
                                if (!same (ct, ti))
                                    changed = true
                                success = true
                            case None =>
                                b += ct
                        }
                    if (success)
                        if (changed)
                            Some (b.result)
                        else
                            Some (t)
                    else
                        None
                }

            private def someMap[CC[V,W] <: Map[V,W]] (t : CC[Term,Term])
                            (implicit cbf : CanBuildFrom[CC[Term,Term], (Term, Term), CC[Term,Term]])
                                : Option[CC[Term,Term]] =
                if (t.size == 0)
                    None
                else {
                    val b = cbf (t)
                    b.sizeHint (t.size)
                    var success = false
                    var changed = false
                    for (ct <- t)
                        s (ct) match {
                            case Some (ti @ (tix, tiy)) =>
                                b += ti
                                if (!same (ct, ti))
                                    changed = true
                                success = true
                            case _ =>
                                b += ct
                        }
                    if (success)
                        if (changed)
                            Some (b.result)
                        else
                            Some (t)
                    else
                        None
                }
        }

    /**
     * Make a strategy that applies the elements of ss pairwise to the
     * children of the subject term, returning a new term if all of the
     * strategies succeed, otherwise failing.  The constructor of the new
     * term is the same as that of the original term and the children
     * are the results of the strategies.  If the length of `ss` is not
     * the same as the number of children, then `congruence(ss)` fails.
     * If the argument strategies succeed on children producing the same
     * terms (by `eq` for references and by `==` for other values), then the
     * overall strategy returns the subject term.
     * This operation works on instances of `Product` values.
     */
    def congruence (ss : Strategy*) : Strategy =
        new Strategy {
            def apply (t : Term) : Option[Term] =
                t match {
                    case p : Product        => congruenceProduct (p, ss : _*)
                    case _                  => Some (t)
                }

            private def congruenceProduct (p : Product, ss : Strategy*) : Option[Term] = {
               val numchildren = p.productArity
               if (numchildren == ss.length) {
                   val newchildren = new Array[AnyRef](numchildren)
                   var changed = false
                   var i = 0
                   while (i < numchildren) {
                       val ct = p.productElement (i)
                       (ss (i)) (ct) match {
                           case Some (ti) =>
                               newchildren (i) = makechild (ti)
                               if (!same (ct, ti))
                                   changed = true
                           case None =>
                               return None
                       }
                       i = i + 1
                   }
                   if (changed) {
                       val ret = dup (p, newchildren)
                       Some (ret)
                   } else
                       Some (p)
               } else
                   None
            }
        }

    /**
     * Return a strategy that behaves as `s` does, but memoises its arguments and
     * results.  In other words, if `memo(s)` is called on a term `t` twice, the
     * second time will return the same result as the first, without having to
     * invoke `s`.  For best results, it is important that `s` should have no side
     * effects.
     */
    def memo (s : => Strategy) : Strategy =
        new Strategy {
            private val cache =
                new scala.collection.mutable.HashMap[Term,Option[Term]]
            def apply (t : Term) : Option[Term] =
                cache.getOrElseUpdate (t, s (t))
        }

}
