/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2008-2011 Anthony M Sloane, Macquarie University.
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

import org.kiama.example.imperative.Generator
import org.kiama.util.Tests
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.Checkers

/**
 * Rewriting tests.
 */
@RunWith(classOf[JUnitRunner])
class RewriterTests extends Tests with Checkers with Generator {

    import org.kiama.example.imperative.AST._
    import org.kiama.rewriting.Rewriter.{fail => rwfail, test => rwtest, _}
    
    test ("basic arithmetic evaluation") {
        val eval =
            rule {
                case Add (Num (i), Num (j)) => Num (i + j)
                case Sub (Num (i), Num (j)) => Num (i - j)
                case Mul (Num (i), Num (j)) => Num (i * j)
                case Div (Num (i), Num (0)) => Num (0)  // Hack
                case Div (Num (i), Num (j)) => Num (i / j)
                case Var (_)                => Num (3)  // Hack
            }
        check ((t : Exp) => everywherebu (eval) (t) == Some (Num (t.value)))
        check ((t : Exp) => reduce (eval) (t) == Some (Num (t.value)))
    }
    
    test ("issubterm: a term is a subterm of itself") {
        check ((t : Stmt) => same (Some (t), issubterm (t, t)))
        check ((t : Exp) => same (Some (t), issubterm (t, t)))
    }
    
    test ("issubterm: random descendants are subterms") {
        val random = new scala.util.Random
    
        /**
         * Pick a random Term child of t, returning t if there are no
         * children or there are children but none of them are Terms.
         */
        def pickchild (t : Product) : Term = {
            def isterm (c : Any) : Boolean = {
                c match {
                    case t : Term => true
                    case _        => false
                }
            }
            val children = for (i <- 0 until t.productArity) yield t.productElement (i)
            val childterms = children.filter (isterm)
            if (childterms.length == 0)
                // No term children, just use t itself
                t
            else {
                val termnum = random.nextInt (childterms.length)
                childterms (termnum).asInstanceOf[Term]
            }
        }
    
        /**
         * Pick a random descendant of t (including possibly t).
         */
        def pickdesc (t : Term) : Term =
            t match {
                case p : Product =>
                    if (random.nextBoolean) {
                        pickchild (p)
                    } else {
                        val child = pickchild (p)
                        if (child == t)
                            t
                        else
                            pickdesc (child)
                    }
                case _ =>
                    t
            }
    
        check ((t : Stmt) => same (Some (t), issubterm (pickdesc (t), t)))
        check ((t : Exp) => same (Some (t), issubterm (pickdesc (t), t)))
    }
    
    {
        val t = Add (Num (1), Num (2))
    
        test ("issubterm: selected subterms - fail") {
            expect (None) (issubterm (Num (42), t))
        }
        
        test ("issubterm: selected subterms - succeed sub") {
            expectsame (Some (t)) (issubterm (Num (1), t))
        }
        
        test ("issubterm: selected subterms - succeed self") {
            expectsame (Some (t)) (issubterm (t, t))
        }
    
        test ("issubterm: selected proper subterms - fail") {
            expect (None) (ispropersubterm (Num (42), t))
        }
        
        test ("issubterm: selected proper subterms - succeed sub") {
            expectsame (Some (t)) (ispropersubterm (Num (1), t))
        }
        
        test ("issubterm: selected proper subterms - fail self") {
            expect (None) (ispropersubterm (t, t))
        }
    
        test ("issuperterm: selected superterms - fail") {
            expect (None) (issuperterm (t, Num (42)))
        }
    
        test ("issuperterm: selected superterms - succeed sub") {
            expectsame (Some (t)) (issuperterm (t, Num (1)))
        }
    
        test ("issuperterm: selected superterms - succeed self") {
            expectsame (Some (t)) (issuperterm (t, t))
        }
    
        test ("issuperterm: selected proper superterms - fail") {
            expect (None) (ispropersuperterm (t, Num (42)))
        }
        
        test ("issuperterm: selected proper superterms - succeed sub") {
            expectsame (Some (t)) (ispropersuperterm (t, Num (1)))
        }
        
        test ("issuperterm: selected proper superterms - fail self") {
            expect (None) (ispropersuperterm (t, t))
        }
    }
    
    test ("strategies that have no effect: identity") {
        check ((t : Stmt) => same (Some (t), id (t)))
        check ((t : Exp) => same (Some (t), id (t)))
    }
    
    test ("strategies that have no effect: some terms to themselves") {
        val noopstmt = everywherebu (rule { case Asgn (v, e) => Asgn (v, e) })
        check ((t : Stmt) => Some (t) == noopstmt (t))
        check ((t : Exp) => Some (t) == noopstmt (t))
    
        val noopexp = everywherebu (rule { case Num (i) => Num (i) })
        check ((t : Stmt) => Some (t) == noopexp (t))
        check ((t : Exp) => Some (t) == noopexp (t))
    }
    
    test ("strategies that fail immediately") {
        check ((t : Stmt) => rwfail (t) == None)
        check ((t : Exp) => rwfail (t) == None)
    }
    
    test ("where: failure") {
        check ((t : Exp) => where (rwfail) (t) == None)
    }
    
    test ("where: identity") {
        check ((t : Exp) => same (Some (t), where (id) (t)))
    }
    
    test ("where restores the original term after succcess") {
        val r = rule { case Num (i) => Num (i + 1) }
        val s = where (r)
        val t = Num (1)
        expectsame (Some (t)) (s (t))
    }
    
    test ("test: failure") {
        check ((t : Exp) => rwtest (rwfail) (t) == None)
    }
    
    test ("test: identity") {
        check ((t : Exp) => same (Some (t), rwtest (id) (t)))
    }
     
    test ("test restores the original term after succcess") {
        val r = rule { case Num (i) => Num (i + 1) }
        val s = rwtest (r)
        val t = Num (1)
        expectsame (Some (t)) (s (t))
    }
       
    test ("leaf detection") {
        check ((t : Exp) =>
            same (if (t.productArity == 0) Some (t) else None, isleaf (t)))
    }
    
    test ("innernode detection") {
        check ((t : Exp) =>
            same (if (t.productArity == 0) None else Some (t), isinnernode (t)))
    }
    
    test ("terms as strategies") {
        check ((t : Stmt, u : Exp) => same (Some (t), t (u)))
        check ((t : Exp, u : Exp) => same (Some (t), t (u)))
        check ((t : Stmt, u : Stmt) => same (Some (t), t (u)))
        check ((t : Exp, u : Stmt) => same (Some (t), t (u)))
    }
    
    test ("term combinator") {
        check ((t : Stmt) => (term (t)) (t) == Some (t))
        check ((t : Exp) => (term (t)) (t) == Some (t))
    
        val t = Add (Num (1), Num (2))
        expect (None) (term (Num (1)) (t))
        expect (None) (term (Num (42)) (t))
    }
    
    {
        val e1 = Mul (Num (2), Num (3))
        val e2 = Add (Num (2), Num (3))
    
        test ("conditional choice operator: identity") {
            expect (Some (Num (1))) ((id < (Num (1) : Strategy) + Num (2)) (e1))
        }
    
        test ("conditional choice operator: failure") {
            expect (Some (Num (2))) ((rwfail < (Num (1) : Strategy) + Num (2)) (e1))
        }
    
        test ("conditional choice operator: condition for just success or failure") {
            val ismulbytwo = rule { case t @ Mul (Num (2), _) => t }
            val multoadd = rule { case Mul (Num (2), x) => Add (x, x) }
            val error : Strategy = Num (99)
            val trans1 = ismulbytwo < multoadd + error
            expect (Some (Add (Num (3), Num (3)))) ((trans1) (e1))
            expect (Some (Num (99))) ((trans1) (e2))
        }
    
        test ("conditional choice operator: condition that transforms object") {
            val mulbytwotoadd = rule { case t @ Mul (Num (2), x) => Add (x, x) }
            val add = rule { case Add (_, _) => Num (42) }
            val trans2 = mulbytwotoadd < add + id
            expect (Some (Num (42))) ((trans2) (e1))
            expect (Some (Add (Num (2), Num (3)))) ((trans2) (e2))
        }
    }
    
    test ("strategies can return another strategy") {
        // Test expressions
        val e1 = Mul (Num (2), Num (5))
        val e2 = Add (Num (4), Num (5))
    
        // Single step passing
        val twotothree = rule { case Num (2) => Num (3) }
        val pass = rulefs { case Num (2) => twotothree }
        val passtd = everywhere (pass)
        expect (Some (Mul (Num (3), (Num (5))))) ((passtd) (e1))
        expect (Some (Add (Num (4), (Num (5))))) ((passtd) (e2))
    }
    
    {
        val e = Mul (Num (1), Add (Sub (Var ("hello"), Num (2)), Var ("harold")))
        val ee = Mul (Num (1), Add (Sub (Var ("hello"), Num (2)), Var ("harold")))
        
        test ("a bottomup traversal applying identity returns the same term") {
            expectsame (Some (e)) ((bottomup (id)) (e))
        }
    
        test ("a bottomup traversal applying identity doesn't returns term with same value") {
            expectnotsame (Some (ee)) ((bottomup (id)) (e))
        }
        
        test ("counting all terms using count") {
            val countall = count { case _ => 1 }
            expect (11) (countall (e))
        }
    
        test ("counting all terms using queryf") {
            var count = 0
            val countall = everywhere (queryf (_ => count = count + 1))
            expectsame (Some (e)) (countall (e))
            expect (11) (count)
        }
        
        test ("counting all terms using a para") {
            val countfold = 
                para[Int] {
                    case (t, cs) => 1 + cs.sum
                }
            expect (11) (countfold (e))
        }

        test ("counting all Num terms twice") {
            val countnum = count { case Num (_) => 2 }
            expect (4) (countnum (e))
        }

        test ("counting all Div terms") {
            val countdiv = count { case Div (_, _) => 1 }
            expect (0) (countdiv (e))
        }

        test ("counting all binary operator terms, with Muls twice") {
            val countbin = count {
                case Add (_, _) => 1
                case Sub (_, _) => 1
                case Mul (_, _) => 2
                case Div (_, _) => 1
            }
            expect (4) (countbin (e))
        }
        
        {
            val r = Mul (Num (2), Add (Sub (Var ("hello"), Num (3)), Var ("harold")))
            val s = Mul (Num (2), Add (Sub (Var ("hello"), Num (2)), Var ("harold")))

            val double = rule { case d : Double => d + 1 }

            test ("rewriting leaf types: increment doubles - all, topdown") {
                expect (Some (r)) ((alltd (double)) (e))
            }
        
            test ("rewriting leaf types: increment doubles - all, bottomup, same") {
                expectsame (Some (e)) ((allbu (double)) (e))
            }
        
            test ("rewriting leaf types: increment doubles - all, bottomup, not same") {
                expectnotsame (Some (ee)) ((allbu (double)) (e))
            }
        
            test ("rewriting leaf types: increment doubles - some, topdown") {
                expect (Some (r)) ((sometd (double)) (e))
            }

            test ("rewriting leaf types: increment doubles - some, bottomup") {
                expect (Some (r)) ((somebu (double)) (e))
            }

            test ("rewriting leaf types: increment doubles - one, topdown") {
                expect (Some (s)) ((oncetd (double)) (e))
            }

            test ("rewriting leaf types: increment doubles - one, bottomup") {
                expect (Some (s)) ((oncebu (double)) (e))
            }
        }
    
        {
            val r = Mul (Num (1), Add (Sub (Var ("olleh"), Num (2)), Var ("dlorah")))
            val s = Mul (Num (1), Add (Sub (Var ("olleh"), Num (2)), Var ("harold")))
            
            val rev = rule { case s : String => s.reverse }
            
            test ("rewriting leaf types: reverse identifiers - all, topdown") {
                expect (Some (r)) ((alltd (rev)) (e))
            }
            
            test ("rewriting leaf types: reverse identifiers - all, bottomup, same") {
                expectsame (Some (e)) ((allbu (rev)) (e))
            }
            
            test ("rewriting leaf types: reverse identifiers - all, bottomup, not same") {
                expectnotsame (Some (ee)) ((allbu (rev)) (e))
            }
            
            test ("rewriting leaf types: reverse identifiers - some, topdown") {
                expect (Some (r)) ((sometd (rev)) (e))
            }
            
            test ("rewriting leaf types: reverse identifiers - some, bottomup") {
                expect (Some (r)) ((somebu (rev)) (e))
            }
            
            test ("rewriting leaf types: reverse identifiers - one, topdown") {
                expect (Some (s)) ((oncetd (rev)) (e))
            }
            
            test ("rewriting leaf types: reverse identifiers - one, bottomup") {
                expect (Some (s)) ((oncebu (rev)) (e))
            }
        }
            
        {
            val r = Mul (Num (2), Add (Sub (Var ("olleh"), Num (2)), Var ("dlorah")))
            val s = Mul (Num (2), Add (Sub (Var ("hello"), Num (2)), Var ("harold")))
           
            val evendoubleincrev =
                rule {
                    case i : Double if i < 2 => i + 1
                    case s : String => s.reverse
                }

            test ("rewriting leaf types: increment even doubles and reverse idn - all, topdown") {
                expect (Some (r)) ((alltd (evendoubleincrev)) (e))
            }
        
            test ("rewriting leaf types: increment even doubles and reverse idn - all, bottomup, same") {
                expectsame (Some (e)) ((allbu (evendoubleincrev)) (e))
            }
        
            test ("rewriting leaf types: increment even doubles and reverse idn - all, bottomup, not same") {
                expectnotsame (Some (ee)) ((allbu (evendoubleincrev)) (e))
            }
        
            test ("rewriting leaf types: increment even doubles and reverse idn - some, topdown") {
                expect (Some (r)) ((sometd (evendoubleincrev)) (e))
            }

            test ("rewriting leaf types: increment even doubles and reverse idn - some, bottomup") {
                expect (Some (r)) ((somebu (evendoubleincrev)) (e))
            }

            test ("rewriting leaf types: increment even doubles and reverse idn - one, topdown") {
                expect (Some (s)) ((oncetd (evendoubleincrev)) (e))
            }

            test ("rewriting leaf types: increment even doubles and reverse idn - one, bottomup") {
                expect (Some (s)) ((oncebu (evendoubleincrev)) (e))
            }
        }
    }
    
    test ("rewrite to increment an integer") {
        val inc = rule { case i : Int => i + 1 }
        expect (Some (4)) ((inc) (3))
    }
    
    test ("rewrite to a constant value") {
        val const = rulef (_ => 88)
        expect (Some (88)) ((const) (3))
    }
    
    test ("rewrite failing to increment an integer with a double increment") {
        val inc = rule { case d : Double => d + 1 }
        expect (None) ((inc) (3))
    }
    
    {
        val incall = alltd (rule { case i : Int => i + 1 })
        val incfirst = oncetd (rule { case i : Int => i + 1 })
        val incodd = sometd (rule { case i : Int if i % 2 == 1 => i + 1 })
    
        test ("rewrite list: increment all numbers - non-empty") {
            expect (Some (List (2, 3, 4))) ((incall) (List (1, 2, 3)))
        }
        
        test ("rewrite list: increment all numbers - empty") {
            expect (Some (Nil)) ((incall) (Nil))
        }
        
        test ("rewrite list: increment first number - non-empty") {
            expect (Some (List (2, 2, 3))) ((incfirst) (List (1, 2, 3)))
        }
        
        test ("rewrite list: increment first number - empty") {
            expect (None) ((incfirst) (Nil))
        }
        
        test ("rewrite list: increment odd numbers - succeed") {
            expect (Some (List (2, 2, 4))) ((incodd) (List (1, 2, 3)))
        }
        
        test ("rewrite list: increment odd numbers - fail") {
            expect (None) ((incodd) (List (2, 4, 6)))
        }
        
        val l = List (List (1, 2), List (3), List (4, 5, 6))
    
        test ("rewrite list: nested increment all numbers") {
            expect (Some (List (List (2, 3), List (4), List (5, 6, 7)))) ((incall) (l))
        }
        
        test ("rewrite list: nested increment first number") {
            expect (Some (List (List (2, 2), List (3), List (4, 5, 6)))) ((incfirst) (l))
        }
        
        test ("rewrite list: nested increment odd numbers - succeed") {
            expect (Some (List (List (2, 2), List (4), List (4, 6, 6)))) ((incodd) (l))
        }
        
        test ("rewrite list: nested increment odd numbers - fail") {
            expect (None) ((incodd) (List (List (2, 2), List (4), List (4, 6, 6))))
        }
    }

    test ("same comparison of equal references yields true") {
        case class Num (i : Int) 
        val r = Num (42)
        expect (true) (same (r, r))
    }
    

    test ("same comparison of unequalt references yields false") {
        case class Num (i : Int) 
        val r1 = Num (42)
        val r2 = Num (42)
        expect (false) (same (r1, r2))
    }

    test ("same comparison of equal non-references yields true") {
        expect (true) (same (42, 42))
    }
    

    test ("same comparison of unequalt non-references yields false") {
        expect (false) (same (42, 43))
    }

    /**
     * The kind of comparison that is expected to be true for a test.  Equal
     * means use ==.  Same means the result must be the same reference or, if
     * the values are not references, use ==.  NotSame is the opposite of Same.
     */
    abstract class Expecting
    case object Equal extends Expecting
    case object Same extends Expecting
    case object NotSame extends Expecting
    
    def travtest (basemsg : String, testmsg : String, trav : (=> Strategy) => Strategy,
                  rewl : Strategy, term : Term, result : Option[Term],
                  expecting : Expecting = Equal) = {
        val msg = basemsg + " - " + testmsg + ", " + expecting
        test (msg) {
            expecting match {
                case Equal   => expect (result) (trav (rewl) (term))
                case Same    => expectsame (result) (trav (rewl) (term))
                case NotSame => expectnotsame (result) (trav (rewl) (term))
            }
        }
    }
    
    {
        val l = List (Sub (Num (2), Var ("one")), Add (Num (4), Num (5)), Var ("two"))
        val ll = List (Sub (Num (2), Var ("one")), Add (Num (4), Num (5)), Var ("two"))
        val r = List (Sub (Num (0), Var ("one")), Add (Num (0), Num (0)), Var ("two"))
        val s = List (Sub (Num (0), Var ("one")), Add (Num (4), Num (5)), Var ("two"))
        
        val strat = rule { case _ : Double => 0 }
        val basemsg = "rewrite list: doubles to zero in non-primitive list"
    
        travtest (basemsg, "all, topdown", alltd, strat, l, Some (r))
        travtest (basemsg, "all, bottomup", allbu, strat, l, Some (l), Same)
        travtest (basemsg, "all, bottomup", allbu, strat, l, Some (ll), NotSame)
        travtest (basemsg, "some, topdown", sometd, strat, l, Some (r))
        travtest (basemsg, "some, bottomup", somebu, strat, l, Some (r))
        travtest (basemsg, "one, topdown", oncetd, strat, l, Some (s))
        travtest (basemsg, "one, bottomup", oncebu, strat, l, Some (s))
    }
    
    {
        val v = Set (1, 5, 8, 9)
        val vv = Set (1, 5, 8, 9)
        
        val strat = rule { case i : Int => i }
        val basemsg = "rewrite set: no change"
    
        travtest (basemsg, "all, topdown", alltd, strat, v, Some (v), Same)
        travtest (basemsg, "all, bottomup", allbu, strat, v, Some (v), Same)
        travtest (basemsg, "some, topdown", sometd, strat, v, Some (v), Same)
        travtest (basemsg, "some, bottomup", somebu, strat, v, Some (v), Same)
        travtest (basemsg, "one, topdown", oncetd, strat, v, Some (v), Same)
        travtest (basemsg, "one, bottomup", oncebu, strat, v, Some (v), Same)
    
        travtest (basemsg, "all, topdown", alltd, strat, v, Some (vv), NotSame)
        travtest (basemsg, "all, bottomup", allbu, strat, v, Some (vv), NotSame)
        travtest (basemsg, "some, topdown", sometd, strat, v, Some (vv), NotSame)
        travtest (basemsg, "some, bottomup", somebu, strat, v, Some (vv), NotSame)
        travtest (basemsg, "one, topdown", oncetd, strat, v, Some (vv), NotSame)
        travtest (basemsg, "one, bottomup", oncebu, strat, v, Some (vv), NotSame)
    }
    
    {
        val r = Set (1, 5, 8, 9)
        val rr = Set (1, 5, 8, 9)
        val s = Set (2, 10, 16, 18)
        val t = Set (2, 5, 8, 9)
    
        val strat = rule { case i : Int => i * 2 }
        val basemsg = "rewrite set: double value"
    
        travtest (basemsg, "all, topdown", alltd, strat, r, Some (s))
        travtest (basemsg, "all, bottomup", allbu, strat, r, Some (r), Same)
        travtest (basemsg, "all, bottomup", allbu, strat, r, Some (rr), NotSame)
        travtest (basemsg, "some, topdown", sometd, strat, r, Some (s))
        travtest (basemsg, "some, bottomup", somebu, strat, r, Some (s))
        travtest (basemsg, "one, topdown", oncetd, strat, r, Some (t))
        travtest (basemsg, "one, bottomup", oncebu, strat, r, Some (t))
    }
    
    {
        val m = Map ("one" -> 1, "two" -> 2, "three" -> 3)
        val mm = Map ("one" -> 1, "two" -> 2, "three" -> 3)
        
        val strat = rule { case s : String => s }
        val basemsg = "rewrite map: no change"
    
        travtest (basemsg, "all, topdown", alltd, strat, m, Some (m), Same)
        travtest (basemsg, "all, bottomup", allbu, strat, m, Some (m), Same)
        travtest (basemsg, "some, topdown", sometd, strat, m, Some (m), Same)
        travtest (basemsg, "some, bottomup", somebu, strat, m, Some (m), Same)
        travtest (basemsg, "one, topdown", oncetd, strat, m, Some (m), Same)
        travtest (basemsg, "one, bottomup", oncebu, strat, m, Some (m), Same)
    
        travtest (basemsg, "all, topdown", alltd, strat, m, Some (mm), NotSame)
        travtest (basemsg, "all, bottomup", allbu, strat, m, Some (mm), NotSame)
        travtest (basemsg, "some, topdown", sometd, strat, m, Some (mm), NotSame)
        travtest (basemsg,"some, bottomup", somebu, strat, m, Some (mm), NotSame)
        travtest (basemsg, "one, topdown", oncetd, strat, m, Some (mm), NotSame)
        travtest (basemsg, "one, bottomup", oncebu, strat, m, Some (mm), NotSame)
    }
    
    {
        val m = Map ("one" -> 1, "two" -> 2, "three" -> 3)
        val mm = Map ("one" -> 1, "two" -> 2, "three" -> 3)
        val r = Map ("eno" -> 1, "owt" -> 2, "eerht" -> 3)
        val s = Map ("eno" -> 1, "two" -> 2, "three" -> 3)
    
        val strat = rule { case s : String => s.reverse }
        val basemsg = "rewrite map: reverse keys"
        
        travtest (basemsg, "all, topdown", alltd, strat, m, Some (r))
        travtest (basemsg, "all, bottomup", allbu, strat, m, Some (m), Same)
        travtest (basemsg, "all, bottomup", allbu, strat, m, Some (mm), NotSame)
        travtest (basemsg, "some, topdown", sometd, strat, m, Some (r))
        travtest (basemsg, "some, bottomup", somebu, strat, m, Some (r))
        travtest (basemsg, "one, topdown", oncetd, strat, m, Some (s))
        travtest (basemsg, "one, bottomup", oncebu, strat, m, Some (s))
    }
    
    {
        val m = Map ("one" -> 1, "two" -> 2, "three" -> 3)
        val mm = Map ("one" -> 1, "two" -> 2, "three" -> 3)
        val r = Map ("one" -> 2, "two" -> 3, "three" -> 4)
        val s = Map ("one" -> 2, "two" -> 2, "three" -> 3)
    
        val strat = rule { case i : Int => i + 1 }
        val basemsg = "rewrite map: increment values"
        
        travtest (basemsg, "all, topdown", alltd, strat, m, Some (r))
        travtest (basemsg, "all, bottomup", allbu, strat, m, Some (m), Same)
        travtest (basemsg, "all, bottomup", allbu, strat, m, Some (mm), NotSame)
        travtest (basemsg, "some, topdown", sometd, strat, m, Some (r))
        travtest (basemsg, "some, bottomup", somebu, strat, m, Some (r))
        travtest (basemsg, "one, topdown", oncetd, strat, m, Some (s))
        travtest (basemsg, "one, bottomup", oncebu, strat, m, Some (s))
    }
    
    {
        val m = Map ("one" -> 1, "two" -> 2, "three" -> 3)
        val mm = Map ("one" -> 1, "two" -> 2, "three" -> 3)
        val r = Map ("eno" -> 2, "owt" -> 3, "eerht" -> 4)
        val s = Map ("eno" -> 1, "two" -> 2, "three" -> 3)
        
        val basemsg = "rewrite map: reverse keys and increment values"
        val strat = rule {
                        case s : String => s.reverse
                        case i : Int    => i + 1
                    }
        
        travtest (basemsg, "all, topdown", alltd, strat, m, Some (r))
        travtest (basemsg, "all, bottomup", allbu, strat, m, Some (m), Same)
        travtest (basemsg, "all, bottomup", allbu, strat, m, Some (mm), NotSame)
        travtest (basemsg, "some, topdown", sometd, strat, m, Some (r))
        travtest (basemsg, "some, bottomup", somebu, strat, m, Some (r))
        travtest (basemsg, "one, topdown", oncetd, strat, m, Some (s))
        travtest (basemsg, "one, bottomup", oncebu, strat, m, Some (s))
    }
    
    {
        val m = Map (1 -> 2, 3 -> 4, 5 -> 6)
        val mm = Map (1 -> 2, 3 -> 4, 5 -> 6)
        val r = Map (2 -> 4, 4 -> 8, 6 -> 12)
        val s = Map (2 -> 4, 3 -> 4, 5 -> 6)
        
        val basemsg = "rewrite map: increment key and double value"
        val strat = rule { case (k : Int, v : Int) => (k + 1, v * 2) }
        
        travtest (basemsg, "all, topdown", alltd, strat, m, Some (r))
        travtest (basemsg, "all, bottomup", allbu, strat, m, Some (m), Same)
        travtest (basemsg, "all, bottomup", allbu, strat, m, Some (mm), NotSame)
        travtest (basemsg, "some, topdown", sometd, strat, m, Some (r))
        travtest (basemsg, "some, bottomup", somebu, strat, m, Some (r))
        travtest (basemsg, "one, topdown", oncetd, strat, m, Some (s))
        travtest (basemsg, "one, bottomup", oncebu, strat, m, Some (s))
    }
    
    {
        // Maps from sets to their sizes, on init size is always zero
        val m1 = Map (Set (1, 3) -> 0, Set (2, 4, 6) -> 0)
        val m2 = Map (Set (12, 16) -> 0, Set (23) -> 0)
    
        // List of the maps
        val l = List (m1, m2)
        val ll = List (Map (Set (1, 3) -> 0, Set (2, 4, 6) -> 0),
                       Map (Set (12, 16) -> 0, Set (23) -> 0))
    
        {
            val r = List (Map (Set (2, 4) -> 1, Set (3, 5, 7) -> 1),
                          Map (Set (13, 17) -> 1, Set (24) -> 1))
            val s = List (Map (Set (2, 3) -> 0, Set (2, 4, 6) -> 0),
                          Map (Set (12, 16) -> 0, Set (23) -> 0))
            
            val basemsg = "rewrite set: heterogeneous collection: inc integers"
            val strat = rule { case i : Int => i + 1 }
            
            travtest (basemsg, "all, topdown", alltd, strat, l, Some (r))
            travtest (basemsg, "all, bottomup", allbu, strat, l, Some (l), Same)
            travtest (basemsg, "all, bottomup", allbu, strat, l, Some (ll), NotSame)
            travtest (basemsg, "some, topdown", sometd, strat, l, Some (r))
            travtest (basemsg, "some, bottomup", somebu, strat, l, Some (r))
            travtest (basemsg, "one, topdown", oncetd, strat, l, Some (s))
            travtest (basemsg, "one, bottomup", oncebu, strat, l, Some (s))
        }
    
        {
            val r = List (Map (Set (1, 3) -> 2, Set (2, 4, 6) -> 3),
                          Map (Set (12, 16) -> 2, Set (23) -> 1))
            val s = List (Map (Set (1, 3) -> 2, Set (2, 4, 6) -> 0),
                          Map (Set (12, 16) -> 0, Set (23) -> 0))
            
            val basemsg = "rewrite set: heterogeneous collection: set to size"
            val strat = rule { case (s : Set[_], _) => (s, s.size) }
            
            travtest (basemsg, "all, topdown", alltd, strat, l, Some (r))
            travtest (basemsg, "all, bottomup", allbu, strat, l, Some (l), Same)
            travtest (basemsg, "all, bottomup", allbu, strat, l, Some (ll), NotSame)
            travtest (basemsg, "some, topdown", sometd, strat, l, Some (r))
            travtest (basemsg, "some, bottomup", somebu, strat, l, Some (r))
            travtest (basemsg, "one, topdown", oncetd, strat, l, Some (s))
            travtest (basemsg, "one, bottomup", oncebu, strat, l, Some (s))
        }
    }
    
    {
        val l = Add (Num (1), Num (2))
        val r = Add (Num (3), Num (4))
        val t = Sub (l, r)

        val incnum = rule { case Num (i) => Num (i + 1) }
        val inczerothchild = child (0, incnum)
        val incfirstchild = child (1, incnum)
        val incsecondchild = child (2, incnum)
        val incthirdchild = child (3, incnum)
        val incallsecondchild = alltd (incsecondchild)
    
        test ("rewrite by child index: inc zeroth child - fail") {
            expect (None) (inczerothchild (Add (Num (2), Num (3))))
        }
    
        test ("rewrite by child index: inc first child - fail") {
            expect (None) (incfirstchild (Num (2)))
        }
    
        test ("rewrite by child index: inc first child - succeed, one child, one level") {
            expect (Some (Neg (Num (3)))) (incfirstchild (Neg (Num (2))))
        }
    
        test ("rewrite by child index: inc first child - succeed, two children, one level") {
            expect (Some (Add (Num (3), Num (3)))) (incfirstchild (Add (Num (2), Num (3))))
        }
    
        test ("rewrite by child index: inc second child - fail") {
            expect (None) (incsecondchild (Num (2)))
        }
    
        test ("rewrite by child index: inc second child - succeed, one level") {
            expect (Some (Add (Num (2), Num (4)))) (incsecondchild (Add (Num (2), Num (3))))
        }
    
        test ("rewrite by child index: inc third child - fail, one level") {
            expect (None) (incthirdchild (Add (Num (2), Num (3))))
        }
    
        test ("rewrite by child index: inc second child - succeed, multi-level") {
            expect (Some (Sub (Add (Num (2), Num (4)), Mul (Num (4), Num (6))))) (
                incallsecondchild (Sub (Add (Num (2), Num (3)), Mul (Num (4), Num (5))))
            )
        }
    }
    
    {
        // The type used here should be a Seq that is not implemented using case classes
        // (or other Products)
        import scala.collection.mutable.LinkedList
        
        val incint = rule { case i : Int => i + 1 }
        val inczerothchild = child (0, incint)
        val incfirstchild = child (1, incint)
        val incsecondchild = child (2, incint)
        val incallsecondchild = alltd (incsecondchild)
    
        val l1 = LinkedList ()
        val l2 = LinkedList (1)
        val l3 = LinkedList (1, 2, 3, 4)
        
        test ("rewrite linkedlist by child index: inc zeroth child - fail, empty") {
            expect (None) (inczerothchild (l1))
        }
    
        test ("rewrite linkedlist by child index: inc first child - fail, empty") {
            expect (None) (incfirstchild (l1))
        }
    
        test ("rewrite linkedlist by child index: inc first child - succeed, singleton") {
            expect (Some (LinkedList (2))) (incfirstchild (l2))
        }        
                
        test ("rewrite linkedlist by child index: inc second child - fail, singleton") {
            expect (None) (incsecondchild (l2))
        }        
        
        test ("rewrite linkedlist by child index: inc zeroth child - fail, multiple") {
            expect (None) (inczerothchild (l3))
        }        
        
        test ("rewrite linkedlist by child index: inc first child - succeed, multiple") {
            expect (Some (LinkedList (2, 2, 3, 4))) (incfirstchild (l3))
        }        
                
        test ("rewrite linkedlist by child index: inc second child - succeed, one level") {
            expect (Some (LinkedList (1, 3, 3, 4))) (incsecondchild (l3))
        }        
    
        test ("rewrite linkedlist by child index: inc second child - succeed, multi-level") {
            expect (Some (LinkedList (LinkedList (1), LinkedList (3, 5, 5), LinkedList (6, 8)))) (
                incallsecondchild (LinkedList (LinkedList (1), LinkedList (3, 4, 5), LinkedList (6, 7)))
            )
        }
    }
    
    {
        // { i = 10; count = 0; while (i) { count = count + 1; i = 1 + i; } }
        val p = 
            Seqn (List (
                Asgn (Var ("i"), Num (10)),
                Asgn (Var ("count"), Num (0)),
                While (Var ("i"),
                    Seqn (List (
                        Asgn (Var ("count"), Add (Var ("count"), Num (1))),
                        Asgn (Var ("i"), Add (Num (1), Var ("i"))))))))

        // { i = 0; count = 0; while (i) { count = bob + 1; i = 0 + i; } }
        val q = 
            Seqn (List (
                Asgn (Var ("i"), Num (0)),
                Asgn (Var ("count"), Num (0)),
                While (Var ("i"),
                    Seqn (List (
                        Asgn (Var ("count"), Add (Var ("bob"), Num (1))),
                        Asgn (Var ("i"), Add (Num (0), Var ("i"))))))))
    
        val incint = rule { case i : Int => i + 1 }
        val clearlist = rule { case _ => Nil }
        val zeronumsbreakadds =
            alltd (Num (rule { case _ => 0}) +
                   Add (rule { case Var (_) => Var ("bob")}, id))
    
        test ("rewrite by congruence: top-level wrong congruence") {
            expect (None) (Num (incint) (p))
        }
        
        test ("rewrite by congruence: top-level correct congruence") {
            expect (Some (Seqn (Nil))) (Seqn (clearlist) (p))
        }
        
        test ("rewrite by congruence: multi-level") {
            expect (Some (q)) (zeronumsbreakadds (p))
        }
    }
    
    test ("debug strategy produces the expected message and result") {
        import org.kiama.util.StringEmitter
        val e = new StringEmitter
        val s = debug ("hello there: ", e)
        val t = Asgn (Var ("i"), Add (Num (1), Var ("i")))
        expectsame (Some (t)) (s (t))
        expect ("hello there: " + t + "\n") (e.result)
    }

    test ("log strategy produces the expected message and result on success") {
        import org.kiama.util.StringEmitter
        val e = new StringEmitter
        var r = rule { case Asgn (l, r) => Asgn (l, Num (42)) }
        val s = log (r, "test log ", e)
        val t = Asgn (Var ("i"), Add (Num (1), Var ("i")))
        val u = Asgn (Var ("i"), Num (42))
        expect (Some (u)) (s (t))
        expect ("test log " + t + " succeeded with " + u + "\n") (e.result)
    }

    test ("log strategy produces the expected message and result on failure") {
        import org.kiama.util.StringEmitter
        val e = new StringEmitter
        var r = rule { case Asgn (l, r) => Asgn (l, Num (42)) }
        val s = log (r, "test log ", e)
        val t = Add (Num (1), Var ("i"))
        expect (None) (s (t))
        expect ("test log " + t + " failed\n") (e.result)
    }

    test ("logfail strategy produces no message but the right result on success") {
        import org.kiama.util.StringEmitter
        val e = new StringEmitter
        var r = rule { case Asgn (l, r) => Asgn (l, Num (42)) }
        val s = logfail (r, "test log ", e)
        val t = Asgn (Var ("i"), Add (Num (1), Var ("i")))
        val u = Asgn (Var ("i"), Num (42))
        expect (Some (u)) (s (t))
        expect ("") (e.result)
    }

    test ("logfail strategy produces the expected message and result on failure") {
        import org.kiama.util.StringEmitter
        val e = new StringEmitter
        var r = rule { case Asgn (l, r) => Asgn (l, Num (42)) }
        val s = logfail (r, "test log ", e)
        val t = Add (Num (1), Var ("i"))
        expect (None) (s (t))
        expect ("test log " + t + " failed\n") (e.result)
    }

    test ("rewrite returns the original term when the strategy fails") {
        val t = Asgn (Var ("i"), Add (Num (1), Var ("i")))
        expectsame (Some (t)) (Some (rewrite (rwfail) (t)))
    }
    
    test ("rewrite returns the strategy result when the strategy succeeds") {
        val t = Asgn (Var ("i"), Add (Num (1), Var ("i")))
        val s = everywhere (rule { case Var (_) => Var ("hello") })
        expect (s (t)) (Some (rewrite (s) (t)))
    }
    
    test ("a memo strategy returns the previous result") {
        val t = Asgn (Var ("i"), Add (Num (1), Var ("i")))
        var count = 0
        val s = memo (everywhere (rule {
                    case Var (_) => count = count + 1;
                                    Var ("i" + count)
                }))
        val r = Some (Asgn (Var ("i1"), Add (Num (1), Var ("i2"))))
        expect (r) (s (t))
        expect (r) (s (t))
    }
    
    test ("an illegal dup throws an appropriate exception") {
        val t = Asgn (Var ("i"), Add (Num (1), Var ("i")))
        val s = everywhere (rule { case Var (_) => 42 })
        val i = intercept[RuntimeException] { s (t) }
        expect ("dup illegal arguments: public org.kiama.example.imperative.AST$Add(org.kiama.example.imperative.AST$Exp,org.kiama.example.imperative.AST$Exp) (Num(1.0),42), expects 2") (i.getMessage)
    }
    
    test ("repeat on failure succeeds") {
        val s = repeat (rwfail)
        val t = Num (10)
        expectsame (Some (t)) (s (t))
    }
    
    test ("repeat of non-failure works") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i + 1)
                }
        val s = repeat (r)
        expect (Some (Num (10))) (s (Num (1)))
    }
    
    test ("repeat with a final strategy on failure applies the final strategy") {
        val f = rule {
                    case Num (10) => Num (20)
                }
        val s = repeat (rwfail, f)
        expect (Some (Num (20))) (s (Num (10)))
    }
    
    test ("repeat with a final strategy works") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i + 1)
                }
        val f = rule {
                    case Num (10) => Num (20)
                }
        val s = repeat (r, f)
        expect (Some (Num (20))) (s (Num (1)))
    }
    
    test ("repeat with a final failure fails") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i + 1)
                }
        val s = repeat (r, rwfail)
        expect (None) (s (Num (1)))
    }
    
    test ("repeat1 on failure fails") {
        val s = repeat1 (rwfail)
        expect (None) (s (Num (10)))
    }
    
    test ("repeat1 of non-failure works") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i + 1)
                }
        val s = repeat1 (r)
        expect (Some (Num (10))) (s (Num (1)))
    }
    
    test ("repeat1 with a final strategy on failure doesn't apply the final strategy") {
        val f = rule {
                    case Num (10) => Num (20)
                }
        val s = repeat1 (rwfail, f)
        expect (None) (s (Num (10)))
    }
    
    test ("repeat1 with a final strategy works") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i + 1)
                }
        val f = rule {
                    case Num (10) => Num (20)
                }
        val s = repeat1 (r, f)
        expect (Some (Num (20))) (s (Num (1)))
    }
    
    test ("repeat1 with a final failure fails") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i + 1)
                }
        val s = repeat1 (r, rwfail)
        expect (None) (s (Num (1)))
    }
    
    test ("zero repeat of failure is identity") {
        val s = repeat (rwfail, 0)
        val t = Num (1)
        expectsame (Some (t)) (s (t))
    }
    
    test ("non-zero repeat of failure fails") {
        val s = repeat (rwfail, 4)
        expect (None) (s (Num (1)))
    }
    
    test ("zero repeat of non-failure is identity") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i + 1)
                }
        val s = repeat (r, 0)
        val t = Num (1)
        expect (Some (t)) (s (t))
    }
    
    test ("non-zero repeat of non-failure is repeated correct number of times") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i + 1)
                }
        val s = repeat (r, 4)
        expect (Some (Num (5))) (s (Num (1)))
    }
    
    test ("repeatuntil on failure fails") {
        val f = rule {
                    case Num (10) => Num (20)
                }
        val s = repeatuntil (rwfail, f)
        expect (None) (s (Num (1)))
    }
    
    test ("repeatuntil on non-failure works") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i + 1)
                }
        val f = rule {
                    case Num (10) => Num (20)
                }
        val s = repeatuntil (r, f)
        expect (Some (Num (20))) (s (Num (1)))
    }
    
    test ("loop on failure is identity") {
        val f = rule {
                    case Num (1) => Num (2)
                }
        val s = loop (rwfail, f)
		val t = Num (1)
        expectsame (Some (t)) (s (t))
    }
    
    test ("loop on non-failure with initially false condition is identity") {
        val r = rule {
                    case Num (i) if i > 10 => Num (i)
                }
        val f = rule {
                    case Num (1) => Num (2)
                }
        val s = loop (r, f)
		val t = Num (1)
        expectsame (Some (t)) (s (t))
    }
    
    test ("loop on failure with initially true condition is identity") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i)
                }
        val s = loop (r, rwfail)
        val t = Num (1)
        expectsame (Some (t)) (s (t))
    }
    
    test ("loop on non-failure with initially true condition works") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i)
                }
        val f = rule {
                    case Num (i) => Num (i + 1)
                }
        val s = loop (r, f)
        expect (Some (Num (10))) (s (Num (1)))
    }
    
    test ("loopnot on succeess is identity") {
        val f = rule {
                    case Num (1) => Num (2)
                }
        val s = loopnot (id, f)
		val t = Num (1)
        expectsame (Some (t)) (s (t))
    }
    
    test ("loopnot on non-failure with initially true condition is identity") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i)
                }
        val f = rule {
                    case Num (1) => Num (2)
                }
        val s = loopnot (r, f)
        val t = Num (1)
        expect (Some (t)) (s (t))
    }
    
    test ("loopnot on failure with initially false condition fails") {
        val r = rule {
                    case Num (i) if i >= 10 => Num (i + 1)
                }
        val s = loopnot (r, rwfail)
        expect (None) (s (Num (1)))
    }
    
    test ("loopnot on non-failure with initially false condition works") {
        val r = rule {
                    case Num (i) if i >= 10 => Num (i)
                }
        val f = rule {
                    case Num (i) => Num (i + 1)
                }
        val s = loopnot (r, f)
        expect (Some (Num (10))) (s (Num (1)))
    }
    
    test ("doloop on failure applies once") {
        val f = rule {
                    case Num (i) => Num (i + 1)
                }
        val s = doloop (f, rwfail)
        expect (Some (Num (2))) (s (Num (1)))
    }
    
    test ("doloop on non-failure with initially false condition applies once") {
        val r = rule {
                    case Num (i) => Num (i + 1)
                }
        val f = rule {
                    case Num (i) if i >= 10 => Num (i)
                }
        val s = doloop (r, f)
        expect (Some (Num (2))) (s (Num (1)))
    }
    
    test ("doloop on failure with initially true condition is failure") {
        val f = rule {
                    case Num (i) if i < 10 => Num (i)
                }
        val s = doloop (rwfail, f)
        expect (None) (s (Num (1)))
    }
    
    test ("doloop on non-failure with initially true condition works") {
        val r = rule {
                    case Num (i) => Num (i + 1)
                }
        val f = rule {
                    case Num (i) if i < 10 => Num (i)
                }
        val s = doloop (r, f)
        expect (Some (Num (10))) (s (Num (1)))
    }
    
    test ("loopiter with failure init fails") {
        val r = rule {
                    case Num (i) if i < 10 => Num (i)
                }
        val f = rule {
                    case Num (1) => Num (2)
                }
        val s = loopiter (rwfail, r, f)
        expect (None) (s (Num (1)))
    }
    
    test ("loopiter with succeeding init and initially true condition works") {
        val i = rule {
                    case Num (100) => Num (1)
                }
        val r = rule {
                    case Num (i) if i < 10 => Num (i)
                }
        val f = rule {
                    case Num (1) => Num (2)
                }
        val s = loopiter (i, r, f)
        expect (Some (Num (1))) (s (Num (100)))
    }
    
    test ("loopiter with succeeding init and initially false condition works") {
        val i = rule {
                    case Num (100) => Num (1)
                }
        val r = rule {
                    case Num (i) if i >= 10 => Num (i)
                }
        val f = rule {
                    case Num (i) => Num (i + 1)
                }
        val s = loopiter (i, r, f)
        expect (Some (Num (10))) (s (Num (100)))
    }
    
    test ("counting loopiter is identity if there is nothing to count") {
        val r = (i : Int) => 
                    rule {
                        case Num (j) => Num (i + j)
                    }
        val s = loopiter (r, 10, 1)
        val t = Num (1)
        expectsame (Some (t)) (s (t))
    }
    
    test ("counting loopiter counts correctly") {
        var count = 0
        val r = (i : Int) => 
                    rule {
                        case Num (j) => count = count + i
                                        Num (j + 1)
                    }
        val s = loopiter (r, 1, 10)
        expect (Some (Num (11))) (s (Num (1)))
        expect (55) (count)
    }
    
    test ("breadthfirst traverses in correct order") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        var l : List[Double] = Nil
        val r = rule {
                    case n @ Num (i) => l = l :+ i
                                        n
                    case n           => n
                }
        val s = breadthfirst (r)
        expectsame (Some (t)) (s (t))
        expect (List (3, 1, 2, 4, 5)) (l)
    }
    
    test ("leaves with a failing leaf detector succeeds but doesn't collect anything") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        var sum = 0.0
        val r = rule {
                    case Num (i) => sum = sum + i
                }
        val s = leaves (r, rwfail)
        expect (Some (t)) (s (t))
        expect (0) (sum)
    }
    
    test ("leaves with a non-failing leaf detector succeeds and collects correctly") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        var sum = 0.0
        val r = rule {
                    case Num (i) => sum = sum + i
                                    Num (i)
                }
        val l = rule {
                    case Num (i) if (i % 2 == 0) => Num (i)
                }
        val s = leaves (r, l)
        expect (Some (t)) (s (t))
        expect (6) (sum)
    }    
    
    test ("skipping leaves with a non-failing leaf detector succeeds and collects correctly") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        var sum = 0.0
        val r = rule {
                    case Num (i) => sum = sum + i
                                    Num (i)
                }
        val l = rule {
                    case Num (i) if (i % 2 == 1) => Num (i)
                }
        val x = (y : Strategy) =>
                    rule {
                        case n @ Sub (_, _) => n
                    }
        val s = leaves (r, l, x)
        expect (Some (t)) (s (t))
        expect (4) (sum)
    }    
    
    def innermosttest (imost : (=> Strategy) => Strategy) = {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Add (Var ("1.0"), Var ("2.0")), Var ("3.0")), Sub (Var ("4.0"), Var ("5.0")))
        var l : List[Double] = Nil
        val r = rule {
                    case Num (i) => l = l :+ i
                                    Var (i.toString)
                }
        val s = imost (r)
        expect (Some (u)) (s (t))
        expect (List (1, 2, 3, 4, 5)) (l)
    }
    
    test ("innermost visits the correct nodes in the correct order") {
        innermosttest (innermost)
    }
    
    test ("innermost2 visits the correct node") {
        innermosttest (innermost2)
    }
    
    test ("downup (one arg version) visits the correct frontier") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Num (3), Add (Num (1), Num (2))), Sub (Num (4), Num (5)))
        val d = rule {
                    case Add (l, r @ Num (3)) => Add (r, l)
                    case Sub (l, r)           => Sub (r, l)
                    case n                    => n
                }
        val s = downup (d)
        expect (Some (u)) (s (t))
    }
  
    test ("downup (two arg version) visits the correct frontier") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Num (3), Add (Num (1), Num (2))), Sub (Num (8), Num (9)))
        val d = rule {
                    case Add (l, r @ Num (3)) => Add (r, l)
                    case Sub (l, r)           => Sub (r, l)
                    case n                    => n
                }
        val e = rule {
                    case Sub (l, r)           => Sub (Num (8), Num (9))
                    case n                    => n
                }
        val s = downup (d, e)
        expect (Some (u)) (s (t))
    }
    
    test ("somedownup visits the correct frontier") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Add (Num (3), Num (4)), Num (3)), Sub (Num (2), Num (3)))
        val d = rule {
                    case Add (Num (l), Num (r)) => Add (Num (l + 1), Num (r + 1))
                    case Sub (Num (l), Num (r)) => Sub (Num (l - 1), Num (r - 1))
                    case n : Mul                => n
                }
        val s = somedownup (d)
        expect (Some (u)) (s (t))
    }
    
    test ("downupS (two arg version) visits the correct frontier") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Num (3), Add (Num (1), Num (2))), Sub (Num (4), Num (5)))
        val d = rule {
                    case Add (l, r @ Num (3)) => Add (r, l)
                    case Sub (l, r)           => Sub (r, l)
                    case n                    => n
                }
        def f (y : => Strategy) =
            rule {
                case n @ Add (_, Num (3)) => n
            }
        val s = downupS (d, f)
        expect (Some (u)) (s (t))
    }
    
    test ("downupS (three arg version) visits the correct frontier") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Num (3), Add (Num (1), Num (2))), Sub (Num (8), Num (9)))
        val d = rule {
                    case Add (l, r @ Num (3)) => Add (r, l)
                    case Sub (l, r)           => Sub (r, l)
                    case n                    => n
                }
        val e = rule {
                    case Sub (l, r)           => Sub (Num (8), Num (9))
                    case n                    => n
                }
        def f (y : => Strategy) =
            rule {
                case n @ Add (_, Num (3)) => n
            }
        val s = downupS (d, e, f)
        expect (Some (u)) (s (t))
    }
    
    test ("alldownup2 visits the correct frontier") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Mul (Num (3), Add (Num (1), Num (2))), Sub (Num (5), Num (4)))
        val d = rule {
                    case Add (l, r @ Num (3)) => Add (r, l)
                    case Sub (l, r)           => Sub (r, l)
                }
        val e = rule {
                    case Add (l, r) => Mul (l, r)
                    case n          => n
                }
        val s = alldownup2 (d, e)
        expect (Some (u)) (s (t))
    }
    
    test ("topdownS stops at the right spots") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Num (3), Add (Num (1), Num (2))), Sub (Num (5), Num (4)))
        val d = rule {
                    case Add (l, r) => Add (r, l)
                    case Sub (l, r) => Sub (r, l)
                    case n          => n
                }
        def f (y : => Strategy) =
            rule {
                case n @ Add (Num (3), _) => n
            }
        val s = topdownS (d, f)
        expect (Some (u)) (s (t))
    }
    
    test ("topdownS with no stopping doesn't stop") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Num (3), Add (Num (2), Num (1))), Sub (Num (5), Num (4)))
        val d = rule {
                    case Add (l, r) => Add (r, l)
                    case Sub (l, r) => Sub (r, l)
                    case n          => n
                }
        val s = topdownS (d, dontstop)
        expect (Some (u)) (s (t))
    }
    
    test ("bottomupS stops at the right spots") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Num (3), Add (Num (1), Num (2))), Sub (Num (5), Num (4)))
        val d = rule {
                    case Add (l, r) => Add (r, l)
                    case Sub (l, r) => Sub (r, l)
                    case n          => n
                }
        def f (y : => Strategy) =
            rule {
                case n @ Add (_, Num (3)) => n
            }
        val s = bottomupS (d, f)
        expect (Some (u)) (s (t))
    }
    
    test ("bottomupS with no stopping doesn't stop") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Num (3), Add (Num (2), Num (1))), Sub (Num (5), Num (4)))
        val d = rule {
                    case Add (l, r) => Add (r, l)
                    case Sub (l, r) => Sub (r, l)
                    case n          => n
                }
        val s = bottomupS (d, dontstop)
        expect (Some (u)) (s (t))
    }
    
    test ("manybu applies the strategy in the right order and right number of times") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Add (Num (12), Num (11)), Num (10)), Sub (Num (4), Num (5)))
        var count = 13
        val d = rule {
                    case Num (i) if (count > 10) => count = count - 1
                                                    Num (count)
                }
        val s = manybu (d)
        expect (Some (u)) (s (t))
    }
    
    test ("manytd applies the strategy in the right order and right number of times") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Num (11), Add (Num (2), Num (1))), Sub (Num (4), Num (5)))
        var count = 13
        val d = rule {
                    case Num (i) if (count > 10) =>
                        count = count - 1
                        Num (count)
                    case Add (l, r) if (count > 10) =>
                        count = count - 1
                        Add (r, l)
                }
        val s = manytd (d)
        expect (Some (u)) (s (t))
    }
    
    test ("alltdfold can be used to evaluate an expression") {
        // ((1 + 2) + 3) * (4 - 5) = -6
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val d = rule {
                    case n : Num => n
                }
        val e = rule {
                    case Add (Num (i), Num (j)) => Num (i + j)
                    case Sub (Num (i), Num (j)) => Num (i - j)
                    case Mul (Num (i), Num (j)) => Num (i * j)
                }
        val s = alltdfold (d, e)
        expect (Some (Num (-6))) (s (t))
    }
    
    test ("restore restores when the strategy fails") {
        val t = Add (Num (1), Num (2))
        var count = 0
        val d = rule {
                    case n => count = count + 1; n
                } <* rule {
                    case Num (i) => Num (i + 1)
                }
        val e = rule {
                    case n => count = count - 1; n
                }
        val s = restore (d, e)
        expect (None) (s (t))
        expect (0) (count)
    }
    
    test ("restore doesn't restore when the strategy succeeds") {
        val t = Add (Num (1), Num (2))
        var count = 0
        val d = rule {
                    case n => count = count + 1; n
                }
        val e = rule {
                    case n => count = count - 1; n
                }
        val s = restore (d, e)
        expectsame (Some (t)) (s (t))
        expect (1) (count)
    }
    
    test ("restorealways restores when the strategy fails") {
        val t = Add (Num (1), Num (2))
        var count = 0
        val d = rule {
                    case n => count = count + 1; n
                } <* rule {
                    case Num (i) => Num (i + 1)
                }
        val e = rule {
                    case n => count = count - 1; n
                }
        val s = restorealways (d, e)
        expect (None) (s (t))
        expect (0) (count)
    }
    
    test ("restorealways restores when the strategy succeeds") {
        val t = Add (Num (1), Num (2))
        var count = 0
        val d = rule {
                    case n => count = count + 1; n
                }
        val e = rule {
                    case n => count = count - 1; n
                }
        val s = restorealways (d, e)
        expectsame (Some (t)) (s (t))
        expect (0) (count)
    }
    
    test ("lastly applies the second strategy when the first strategy fails") {
        val t = Add (Num (1), Num (2))
        var count = 0
        val d = rule {
                    case n => count = count + 1; n
                } <* rule {
                    case Num (i) => Num (i + 1)
                }
        val e = rule {
                    case n => count = count - 1; n
                }
        val s = lastly (d, e)
        expect (None) (s (t))
        expect (0) (count)
    }
    
    test ("lastly applies the second strategy when the first strategy succeeds") {
        val t = Add (Num (1), Num (2))
        var count = 0
        val d = rule {
                    case n => count = count + 1; n
                }
        val e = rule {
                    case n => count = count - 1; n
                }
        val s = lastly (d, e)
        expectsame (Some (t)) (s (t))
        expect (0) (count)
    }
    
    test ("ior applies second strategy if first strategy fails") {
        val t = Add (Num (1), Num (2))
        val u = Add (Num (2), Num (1))
        val d = rule {
                    case Num (i) => Num (i + 1)
                }
        val e = rule {
                    case Add (l, r) => Add (r, l)
                }
        val s = ior (d, e)
        expect (Some (u)) (s (t))
    }
    
    test ("ior applies second strategy if first strategy succeeds") {
        val t = Add (Num (1), Num (2))
        val u = Add (Num (9), Num (8))
        val d = rule {
                    case Add (l, r) => Add (Num (8), Num (9))
                }
        val e = rule {
                    case Add (l, r) => Add (r, l)
                }
        val s = ior (d, e)
        expect (Some (u)) (s (t))
    }
    
    test ("or applies second strategy and restores term if first strategy fails") {
        val t = Add (Num (1), Num (2))
        val d = rule {
                    case Num (i) => Num (i + 1)
                }
        val e = rule {
                    case Add (l, r) => Add (r, l)
                }
        val s = or (d, e)
        expectsame (Some (t)) (s (t))
    }
    
    test ("or applies second strategy and restores term if first strategy succeeds") {
        val t = Add (Num (1), Num (2))
        val d = rule {
                    case Add (l, r) => Add (Num (8), Num (9))
                }
        val e = rule {
                    case Add (l, r) => Add (r, l)
                }
        val s = or (d, e)
        expectsame (Some (t)) (s (t))
    }
    
    test ("and fails if the first strategy fails") {
        val t = Add (Num (1), Num (2))
        val d = rule {
                    case Num (i) => Num (i + 1)
                }
        val e = rule {
                    case Add (l, r) => Add (r, l)
                }
        val s = and (d, e)
        expect (None) (s (t))
    }
    
    test ("and fails if the first strategy succeeds but the second strategy fails") {
        val t = Add (Num (1), Num (2))
        val d = rule {
                    case Add (l, r) => Add (r, l)
                }
        val e = rule {
                    case Num (i) => Num (i + 1)
                }
        val s = and (d, e)
        expect (None) (s (t))
    }
    
    test ("and succeeds and restores term if both strategies succeed") {
        val t = Add (Num (1), Num (2))
        val d = rule {
                    case Add (l, r) => Add (Num (8), Num (9))
                }
        val e = rule {
                    case Add (l, r) => Add (r, l)
                }
        val s = and (d, e)
        expectsame (Some (t)) (s (t))
    }
    
    def everywheretdtest (everys : (=> Strategy) => Strategy) = {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Add (Num (12), Num (13)), Num (14)), Sub (Num (16), Num (17)))
        var l : List[Double] = Nil
        var count = 9
        val r = rule {
                    case Num (i) => l = l :+ i
                                    Num (count)
                    case n       => count = count + 1
                                    n
                }
        val s = everys (r)
        expect (Some (u)) (s (t))
        expect (List (1, 2, 3, 4, 5)) (l)
    }
    
    test ("everywhere traverses in expected order") {
        everywheretdtest (everywhere)
    }
    
    test ("everywheretd traverses in expected order") {
        everywheretdtest (everywheretd)
    }
    
    test ("everywherebu traverses in expected order") {
        val t = Mul (Add (Add (Num (1), Num (2)), Num (3)), Sub (Num (4), Num (5)))
        val u = Mul (Add (Add (Num (10), Num (11)), Num (13)), Sub (Num (15), Num (16)))
        var l : List[Double] = Nil
        var count = 9
        val r = rule {
                    case Num (i) => l = l :+ i
                                    Num (count)
                    case n       => count = count + 1
                                    n
                }
        val s = everywherebu (r)
        expect (Some (u)) (s (t))
        expect (List (1, 2, 3, 4, 5)) (l)
    }

    test ("cloning a term with sharing gives an equal but not eq term") {
        import org.kiama.attribution.Attributable

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

        val clone = everywherebu (rule {
                        case n : ImperativeNode => 
                            if (n.hasChildren) {
                                n.setChildConnections
                                n
                            } else
                                n.clone ()
                    })
        val ct = clone (t)
        
        // Must get the right answer (==)
        expect (Some (u)) (ct)
        
        // Must not get the original term (eq)
        expectnotsame (Some (t)) (ct)
        
        // Make sure that the parents proerpties are set correctly
        // (for the top level)
        def isTree (ast : Attributable) : Boolean =
            ast.children.forall(c => (c.parent eq ast) && isTree(c))
        assert (isTree (ct.get.asInstanceOf[Attributable]),
                "cloned tree has invalid parent properties")
        
        // Check the terms at the positions of the two c occurrences
        // against each other, since they are eq to start but should
        // not be after
        val mul = ct.get.asInstanceOf[Add].l.asInstanceOf[Mul]
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
