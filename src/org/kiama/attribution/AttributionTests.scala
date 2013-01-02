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
package attribution

import org.kiama.util.Tests

/**
 * Tests of basic attribution.
 */
class AttributionTests extends Tests {

    import Attribution._
    import scala.collection.GenSeq

    abstract class Tree extends Attributable
    case class Pair (left : Tree, right : Tree) extends Tree
    case class Leaf (value : Int) extends Tree
    case class Unused (b : Boolean) extends Tree
    case class EitherTree (e : Either[Pair,Leaf]) extends Tree
    case class ListTree (l : List[Tree]) extends Tree
    case class SetTree (s : Set[Tree]) extends Tree
    case class GenSeqTree (v : GenSeq[Tree]) extends Tree
    case class MapTree (m : Map[Tree,Tree]) extends Tree
    case class PairTree (p : (Tree,Tree)) extends Tree
    case class TripleTree (p : (Tree,Tree,Tree)) extends Tree
    case class QuadTree (p : (Tree,Tree,Tree,Tree)) extends Tree

    val s = Pair (Leaf (3), Pair (Leaf (1), Leaf (10)))
    val t = Pair (Leaf (3), Pair (Leaf (1), Leaf (10)))
    val u = Pair (Leaf (1), Leaf (2))

    var count = 0

    lazy val maximumDef : Tree => Int =
        {
            case Pair (l,r) => count = count + 1; (l->maximum).max (r->maximum)
            case Leaf (v)   => v
        }

    lazy val maximum =
        attr (maximumDef)

    lazy val cattrDef : Tree => Attributable => Int =
        {
            case Pair (l, r) => {
                case Pair (l, r) => 0
                case Leaf (v)    => 1
                case _           => 2
            }
            case Leaf (v) => {
                case Pair (l, r) => 3
                case Leaf (v)    => 4
                case _           => 5
            }
            case _ => {
                case _ => 6
            }
        }

    lazy val pattrDef : String => Attributable => Int =
        {
            case "hello" => {
                case Pair (l, r) => count = count + 1; 0
                case Leaf (v)    => 1
                case _           => 2
            }
            case "goodbye" => {
                case _ => 3
            }
        }

    before {
        count = 0
        maximum.reset ()
    }

    test ("cached attributes are only evaluated once") {
        expectResult (false, "hasBeenComputedAt") (maximum.hasBeenComputedAt (t))
        expectResult (10, "first value") (t->maximum)
        expectResult (true, "hasBeenComputedAt") (maximum.hasBeenComputedAt (t))
        expectResult (10, "second value") (t->maximum)
        expectResult (true, "hasBeenComputedAt") (maximum.hasBeenComputedAt (t))
        expectResult (2, "evaluation count") (count)
    }

    test ("constant attributes are only evaluated once") {
        lazy val answer : Tree => Int =
            constant { count = count + 1; 42 }

        expectResult (42, "first value") (t->answer)
        expectResult (42, "second value") (t->answer)
        expectResult (1, "evaluation count") (count)
    }

    test ("cached attributes are re-evaluated after a reset") {
        expectResult (10, "first value") (t->maximum)
        expectResult (10, "first value") (t->maximum)
        expectResult (2, "evaluation count") (count)
        maximum.reset ()
        expectResult (10, "second value") (t->maximum)
        expectResult (4, "evaluation count") (count)
    }

    test ("cached attributes are distinct for nodes that are equal") {
        expectResult (10, "first value") (t->maximum)
        expectResult (10, "second value") (s->maximum)
        expectResult (4, "evaluation count") (count)
    }

    test ("cached attributes can be reset") {
        expectResult (10, "first value") (t->maximum)
        resetMemo
        expectResult (10, "second value") (t->maximum)
        expectResult (4, "evaluation count") (count)
    }

    test ("uncached attributes are evaluated each time") {
        import UncachedAttribution._

        lazy val maximum : Tree => Int =
            attr {
                case Pair (l,r) => count = count + 1; (l->maximum).max (r->maximum)
                case Leaf (v)   => v
            }

        expectResult (10, "first value") (t->maximum)
        expectResult (10, "second value") (t->maximum)
        expectResult (4, "evaluation count") (count)
    }

    test ("cached child attributes work") {
        lazy val cattr =
            childAttr (cattrDef)

        val f = Leaf (4)
        val e = Leaf (3)
        val d = Leaf (2)
        val c = Leaf (1)
        val b = Pair (d, e)
        val a = Pair (b, c)
        initTree (a)

        expectResult (0, "cached childAttr Pair Pair") (cattr (b))
        expectResult (2, "cached childAttr Pair top") (cattr (a))
        expectResult (3, "cached childAttr Leaf Pair") (cattr (c))
        expectResult (5, "cached childAttr Leaf top") (cattr (f))
    }

    test ("uncached child attributes work") {
        import UncachedAttribution._

        lazy val cattr : Tree => Int =
            childAttr (cattrDef)

        val f = Leaf (4)
        val e = Leaf (3)
        val d = Leaf (2)
        val c = Leaf (1)
        val b = Pair (d, e)
        val a = Pair (b, c)
        initTree (a)

        expectResult (0, "uncached childAttr Pair Pair") (cattr (b))
        expectResult (2, "uncached childAttr Pair top") (cattr (a))
        expectResult (3, "uncached childAttr Leaf Pair") (cattr (c))
        expectResult (5, "uncached childAttr Leaf top") (cattr (f))
    }

    test ("cached parameterised attributes work") {
        lazy val pattr =
            paramAttr (pattrDef)

        expectResult (0, "cached paramAttr Pair hello") (
            pattr ("hello") (Pair (Leaf (1), Leaf (2)))
        )
        expectResult (3, "cached paramAttr Pair goodbye") (
            pattr ("goodbye") (Pair (Leaf (1), Leaf (2)))
        )
        expectResult (1, "cached paramAttr Leaf hello") (pattr ("hello") (Leaf (1)))
        expectResult (3, "cached paramAttr Leaf goodbye") (pattr ("goodbye") (Leaf (1)))
    }

    test ("cached parameterised attributes are re-evaluated after reset") {
        count = 0

        lazy val pattr =
            paramAttr (pattrDef)

        expectResult (false, "hasBeenComputedAt") (pattr.hasBeenComputedAt ("hello", u))
        expectResult (0, "cached paramAttr Pair hello") (pattr ("hello") (u))
        expectResult (true, "hasBeenComputedAt") (pattr.hasBeenComputedAt ("hello", u))
        expectResult (0, "cached paramAttr Pair hello") (pattr ("hello") (u))
        expectResult (1, "evaluation count") (count)
        expectResult (true, "hasBeenComputedAt") (pattr.hasBeenComputedAt ("hello", u))
        pattr.reset ()
        expectResult (false, "hasBeenComputedAt") (pattr.hasBeenComputedAt ("hello", u))
        expectResult (0, "cached paramAttr Pair hello") (pattr ("hello") (u))
        expectResult (2, "evaluation count") (count)
        expectResult (true, "hasBeenComputedAt") (pattr.hasBeenComputedAt ("hello", u))
    }

    test ("uncached parameterised attributes work") {
        import UncachedAttribution._

        lazy val pattr =
            paramAttr (pattrDef)

        expectResult (0, "uncached paramAttr Pair hello") (
            pattr ("hello") (Pair (Leaf (1), Leaf (2)))
        )
        expectResult (3, "uncached paramAttr Pair goodbye") (
            pattr ("goodbye") (Pair (Leaf (1), Leaf (2)))
        )
        expectResult (1, "uncached paramAttr Leaf hello") (pattr ("hello") (Leaf (1)))
        expectResult (3, "uncached paramAttr Leaf goodbye") (pattr ("goodbye") (Leaf (1)))
    }

    test ("circularities are detected for cached attributes") {
        lazy val direct : Tree => Int =
            attr (t => t->direct)
        lazy val indirect : Tree => Int =
            attr ("indirect") (t => t->indirect2)
        lazy val indirect2 : Tree => Int =
            attr (t => t->indirect)

        val t = Pair (Leaf (3), Pair (Leaf (1), Leaf (10)))

        val i1 = intercept[IllegalStateException] {
                    t->direct
                }
        expectResult ("Cycle detected in attribute evaluation at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i1.getMessage)

        val i2 = intercept[IllegalStateException] {
                     t->indirect
                 }
        expectResult ("Cycle detected in attribute evaluation 'indirect' at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i2.getMessage)

        val i3 = intercept[IllegalStateException] {
                     t->indirect2
                 }
        expectResult ("Cycle detected in attribute evaluation at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i3.getMessage)
    }

    test ("circularities are detected for uncached attributes") {
        import UncachedAttribution._

        lazy val direct : Tree => Int =
            attr ("direct") (t => t->direct)
        lazy val indirect : Tree => Int =
            attr (t => t->indirect2)
        lazy val indirect2 : Tree => Int =
            attr ("indirect2") (t => t->indirect)

        val t = Pair (Leaf (3), Pair (Leaf (1), Leaf (10)))

        val i1 = intercept[IllegalStateException] {
                    t->direct
                }
        expectResult ("Cycle detected in attribute evaluation 'direct' at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i1.getMessage)

        val i2 = intercept[IllegalStateException] {
                     t->indirect
                 }
        expectResult ("Cycle detected in attribute evaluation at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i2.getMessage)

        val i3 = intercept[IllegalStateException] {
                     t->indirect2
                 }
        expectResult ("Cycle detected in attribute evaluation 'indirect2' at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i3.getMessage)
    }

    test ("circularities are detected for parameterised attributes") {
        lazy val direct : Int => Tree => Int =
            paramAttr ("direct") (i => (t => t->direct (i)))
        lazy val indirect : Int => Tree => Int =
            paramAttr (i => (t => t->indirect2 (i)))
        lazy val indirect2 : Int => Tree => Int =
            paramAttr ("indirect2") (i => (t => t->indirect (i)))

        val t = Pair (Leaf (3), Pair (Leaf (1), Leaf (10)))

        val i1 = intercept[IllegalStateException] {
                    t->direct (1)
                }
        expectResult ("Cycle detected in attribute evaluation 'direct (1)' at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i1.getMessage)

        val i2 = intercept[IllegalStateException] {
                     t->indirect (8)
                 }
        expectResult ("Cycle detected in attribute evaluation at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i2.getMessage)

        val i3 = intercept[IllegalStateException] {
                     t->indirect2 (9)
                 }
        expectResult ("Cycle detected in attribute evaluation 'indirect2 (9)' at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i3.getMessage)
    }

    test ("parameterised attribute keys compare correctly") {

        object Base extends AttributionBase {
            val n = Leaf (1)
            val k1 = new ParamAttributeKey ("hello", n)
            val k2 = new ParamAttributeKey ("hello", n)
            val k3 = new ParamAttributeKey ("hello", Leaf (1))
            val k4 = new ParamAttributeKey ("goodbye", n)
            val k5 = new ParamAttributeKey ("goodbye", Leaf (1))
            val k6 = new ParamAttributeKey ("hello", null)
            val k7 = new ParamAttributeKey ("hello", null)
            val k8 = new ParamAttributeKey ("goodbye", null)
            expectResult (false) (n equals k1)
            expectResult (false) (k1 equals n)
            expectResult (true) (k1 equals k2)
            expectResult (true) (k2 equals k1)
            expectResult (false) (k1 equals k3)
            expectResult (false) (k3 equals k1)
            expectResult (false) (k1 equals k4)
            expectResult (false) (k4 equals k1)
            expectResult (false) (k1 equals k5)
            expectResult (false) (k5 equals k1)
            expectResult (false) (k1 equals k6)
            expectResult (false) (k6 equals k1)
            expectResult (false) (k1 equals k7)
            expectResult (false) (k7 equals k1)
            expectResult (false) (k1 equals k8)
            expectResult (false) (k8 equals k1)
            expectResult (true) (k6 equals k7)
            expectResult (true) (k7 equals k6)
            expectResult (false) (k6 equals k8)
            expectResult (false) (k8 equals k6)
        }

        Base

    }

    test ("a normal child's properties are set correctly") {
        val c1 = Leaf (3)
        val c2 = Leaf (1)
        val c3 = Leaf (10)
        val c4 = Pair (c2, c3)
        val t = Pair (c1, c4)
        for (i <- 1 to 2) {

            initTree (t)

            val tchildren = t.children.toArray
            expectResult (2) (tchildren.length)
            expectsame (c1) (tchildren (0))
            expectsame (c4) (tchildren (1))
            expectsame (c1) (t.firstChild)
            expectResult (true) (t.hasChildren)
            expectResult (-1) (t.index)
            expectResult (true) (t.isFirst)
            expectResult (true) (t.isLast)
            expectResult (true) (t.isRoot)
            expectsame (c4) (t.lastChild)
            expectResult (null) (t.next)
            expectResult (null) (t.parent)
            expectResult (null) (t.prev)

            val c1children = c1.children.toArray
            expectResult (0) (c1children.length)
            expectResult (false) (c1.hasChildren)
            expectResult (0) (c1.index)
            expectResult (true) (c1.isFirst)
            expectResult (false) (c1.isLast)
            expectResult (false) (c1.isRoot)
            expectsame (c4) (c1.next)
            expectsame (t) (c1.parent)
            expectResult (null) (c1.prev)

            val c2children = c2.children.toArray
            expectResult (0) (c2children.length)
            expectResult (false) (c2.hasChildren)
            expectResult (0) (c2.index)
            expectResult (true) (c2.isFirst)
            expectResult (false) (c2.isLast)
            expectResult (false) (c2.isRoot)
            expectsame (c3) (c2.next)
            expectsame (c4) (c2.parent)
            expectResult (null) (c2.prev)

            val c3children = c3.children.toArray
            expectResult (0) (c3children.length)
            expectResult (false) (c3.hasChildren)
            expectResult (1) (c3.index)
            expectResult (false) (c3.isFirst)
            expectResult (true) (c3.isLast)
            expectResult (false) (c3.isRoot)
            expectResult (null) (c3.next)
            expectsame (c4) (c3.parent)
            expectsame (c2) (c3.prev)

            val c4children = c4.children.toArray
            expectResult (2) (c4children.length)
            expectsame (c2) (c4children (0))
            expectsame (c3) (c4children (1))
            expectsame (c2) (c4.firstChild)
            expectResult (true) (c4.hasChildren)
            expectResult (1) (c4.index)
            expectResult (false) (c4.isFirst)
            expectResult (true) (c4.isLast)
            expectResult (false) (c4.isRoot)
            expectsame (c3) (c4.lastChild)
            expectResult (null) (c4.next)
            expectsame (t) (c4.parent)
            expectsame (c1) (c4.prev)

        }
    }

    test ("an either child's parent property is set correctly") {
        val c1 = Leaf (3)
        val c2 = Leaf (1)
        val c3 = Pair (c1, c2)
        val t1 = EitherTree (Left (c3))
        val c4 = Leaf (6)
        val t2 = EitherTree (Right (c4))
        initTree (t1)
        expectsame (null) (t1.parent)
        expectsame (t1) (c3.parent)
        expectsame (c3) (c1.parent)
        expectsame (c3) (c2.parent)
        initTree (t2)
        expectsame (null) (t2.parent)
        expectsame (t2) (c4.parent)
    }

    test ("a list child's parent property is set correctly") {
        val c1 = Leaf (3)
        val c2 = Leaf (1)
        val c3 = Leaf (10)
        val c4 = ListTree (List (c2, c3))
        val t = Pair (c1, c4)
        initTree (t)
        expectsame (null) (t.parent)
        expectsame (t) (c1.parent)
        expectsame (t) (c4.parent)
        expectsame (c4) (c2.parent)
        expectsame (c4) (c3.parent)
    }

    test ("a set child's parent property is set correctly") {
        val c1 = Leaf (3)
        val c2 = Leaf (1)
        val c3 = Leaf (10)
        val c4 = SetTree (Set (c2, c3))
        val t = Pair (c1, c4)
        initTree (t)
        expectsame (null) (t.parent)
        expectsame (t) (c1.parent)
        expectsame (t) (c4.parent)
        expectsame (c4) (c2.parent)
        expectsame (c4) (c3.parent)
    }

    test ("a sequential vector child's parent property is set correctly") {
        val c1 = Leaf (3)
        val c2 = Leaf (1)
        val c3 = Leaf (10)
        val c4 = GenSeqTree (Vector (c2, c3))
        val t = Pair (c1, c4)
        initTree (t)
        expectsame (null) (t.parent)
        expectsame (t) (c1.parent)
        expectsame (t) (c4.parent)
        expectsame (c4) (c2.parent)
        expectsame (c4) (c3.parent)
    }

    test ("a parallel vector child's parent property is set correctly") {
        val c1 = Leaf (3)
        val c2 = Leaf (1)
        val c3 = Leaf (10)
        val c4 = GenSeqTree (Vector (c2, c3).par)
        val t = Pair (c1, c4)
        initTree (t)
        expectsame (null) (t.parent)
        expectsame (t) (c1.parent)
        expectsame (t) (c4.parent)
        expectsame (c4) (c2.parent)
        expectsame (c4) (c3.parent)
    }

    test ("a map's tuple parent properties are set correctly") {
        val c1 = Leaf (3)
        val c2 = Leaf (1)
        val c3 = Leaf (10)
        val c4 = Leaf (11)
        val c5 = Leaf (12)
        val c6 = MapTree (Map (c4 -> c5))
        val t = MapTree (Map (c1 -> c2, c3 -> c6))
        initTree (t)
        expectsame (null) (t.parent)
        expectsame (t) (c1.parent)
        expectsame (t) (c2.parent)
        expectsame (t) (c3.parent)
        expectsame (t) (c6.parent)
        expectsame (c6) (c4.parent)
        expectsame (c6) (c5.parent)
    }

    test ("a pair's component parent properties are set correctly") {
        val c1 = Leaf (3)
        val c2 = Leaf (1)
        val c3 = Leaf (10)
        val c4 = PairTree (c2, c3)
        val t = PairTree (c1, c4)
        initTree (t)
        expectsame (null) (t.parent)
        expectsame (t) (c1.parent)
        expectsame (t) (c4.parent)
        expectsame (c4) (c2.parent)
        expectsame (c4) (c3.parent)
    }

    test ("a triple's component parent properties are set correctly") {
        val c1 = Leaf (3)
        val c2 = Leaf (1)
        val c3 = Leaf (10)
        val c4 = Leaf (11)
        val c5 = TripleTree (c2, c3, c4)
        val t = PairTree (c5, c1)
        initTree (t)
        expectsame (null) (t.parent)
        expectsame (t) (c1.parent)
        expectsame (t) (c5.parent)
        expectsame (c5) (c2.parent)
        expectsame (c5) (c4.parent)
        expectsame (c5) (c4.parent)
    }

    test ("a quad's component parent properties are set correctly") {
        val c1 = Leaf (3)
        val c2 = Leaf (1)
        val c3 = Leaf (10)
        val c4 = Leaf (11)
        val c5 = Leaf (12)
        val c6 = QuadTree (c2, c3, c4, c5)
        val t = PairTree (c1, c6)
        initTree (t)
        expectsame (null) (t.parent)
        expectsame (t) (c1.parent)
        expectsame (t) (c6.parent)
        expectsame (c6) (c2.parent)
        expectsame (c6) (c3.parent)
        expectsame (c6) (c4.parent)
        expectsame (c6) (c5.parent)
    }

    test ("a chain that is only defined at the root returns the root value") {
        import Decorators.{Chain, chain}
        val t = Pair (Leaf (3), Pair (Leaf (1), Leaf (10)))
        initTree (t)
        def rootupd (in : Tree => Int) : Tree ==> Int = {
            case n if n.isRoot => 42
        }
        val rootchain = chain (rootupd)
        expectResult (42) (t->(rootchain.in))
        expectResult (42) (t->(rootchain.out))
    }

    test ("a chain with no updates throws appropriate exceptions") {
        import Decorators.{Chain, chain}
        val t = Pair (Leaf (3), Pair (Leaf (1), Leaf (10)))
        initTree (t)

        // A chain with only identiy update functions
        val idchain = chain[Tree,Int] ()
        val i1 = intercept[RuntimeException] {
                    t->(idchain.in)
                }
        expectResult ("chain root of tree reached at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i1.getMessage)
        val i2 = intercept[RuntimeException] {
                    t->(idchain.out)
                }
        expectResult ("chain root of tree reached at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i2.getMessage)

        // A chain with refusing-all-in update function. This exercises a
        // different path in the 'in' attribute to the previous checks.
        def refuse (in : Tree => Int) : Tree ==> Int =
            new (Tree ==> Int) {
                def apply (t : Tree) : Int = in (t) // Never used
                def isDefinedAt (t : Tree) : Boolean = false
            }
        val refchain = chain (refuse)
        val i3 = intercept[RuntimeException] {
                    t->(refchain.in)
                }
        expectResult ("chain root of tree reached at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i3.getMessage)
        val i4 = intercept[RuntimeException] {
                    t->(refchain.out)
                }
        expectResult ("chain root of tree reached at Pair(Leaf(3),Pair(Leaf(1),Leaf(10)))") (i4.getMessage)

    }

    test ("deep cloning a term with sharing gives an equal but not eq term") {
        import Attributable.deepclone
        import org.kiama.example.imperative.AST._

        val c = Add (Num (1), Num (2))
        val d = Add (Num (1), Num (2))
        val e = Add (Num (3), Num (4))
        val t = Add (Mul (c,
                          Sub (c,
                               d)),
                     Add (Add (e,
                               Num (5)),
                          e))
        val u = Add (Mul (Add (Num (1), Num (2)),
                          Sub (Add (Num (1), Num (2)),
                               d)),
                     Add (Add (Add (Num (3), Num (4)),
                               Num (5)),
                          Add (Num (3), Num (4))))

        initTree (t)
        val ct = deepclone (t)

        // Must get the right answer (==)
        expectResult (u) (ct)

        // Must not get the original term (eq)
        expectnotsame (t) (ct)

        // Make sure that the parents proerpties are set correctly
        // (for the top level)
        def isTree (ast : Attributable) : Boolean =
            ast.children.forall (c => (c.parent eq ast) && isTree (c))
        assert (isTree (ct.asInstanceOf[Attributable]),
                "deep cloned tree has invalid parent properties")

        // Check the terms at the positions of the two c occurrences
        // against each other, since they are eq to start but should
        // not be after
        val mul = ct.asInstanceOf[Add].l.asInstanceOf[Mul]
        val c1 = mul.l
        val mulsub = mul.r.asInstanceOf[Sub]
        val c2 = mulsub.l
        expectnotsame (c1) (c2)

        // Check the terms at the positions of the two c ocurrences
        // against the one at the position of the d occurrence (which
        // is == but not eq to the two original c's)
        val d1 = mulsub.r
        expectnotsame (c1) (d1)
        expectnotsame (c2) (d1)
    }

}

