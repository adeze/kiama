/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2008 Anthony M Sloane, Macquarie University.
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

/**
 * This file is derived from a JastAdd implementation of PicoJava, created
 * in the Department of Computer Science at Lund University.  See the
 * following web site for details:
 * 
 * http://jastadd.cs.lth.se/examples/PicoJava/index.shtml
 */

package kiama.example.picojava

object ErrorCheck {
  
    import AbstractSyntax._
    import NameResolution._
    import PredefinedTypes._
    import TypeAnalysis._
    import kiama.attribution.Attribution._
    import scala.collection.mutable.{Buffer,ListBuffer}
  
    /**
     * All of the error messages for a program.
     * 
     * public Collection Program.errors() {
     *    Collection c = new ArrayList();
     *    collectErrors(c);
     *    return c;
     * }
     */
    val errors : Program => Seq[String] =
        {
            p => {
                val b = new ListBuffer[String] ()
                collectErrors (p) (b)
                b
            }
        }

    /**
     * Collect the errors for a node recursively.
     *
     * public void ASTNode.collectErrors(Collection c) {
     *    for(int i = 0; i < getNumChild(); i++)
     *        getChild(i).collectErrors(c);
     * }
     *
     * public void AssignStmt.collectErrors(Collection c) {
     *     super.collectErrors(c);
     *     if(!getValue().type().isSubtypeOf(getVariable().type()))
     *         error(c, "Can not assign a variable of type " + getVariable().type().getName() +
     *               " to a value of type " + getValue().type().getName());
     * }
     * 
     * public void ClassDecl.collectErrors(Collection c) {
     *    super.collectErrors(c);
     *    if(hasCycleOnSuperclassChain())
     *       error(c, "Cyclic inheritance chain for class " + getName());
     * }
     * 
     * public void WhileStmt.collectErrors(Collection c) {
     *     super.collectErrors(c);
     *     if(!getCondition().type().isSubtypeOf(booleanType()))
     *         error(c, "Condition must be a boolean expression");
     *     if(!getCondition().isValue())
     *         error(c, "Condition must be a value");
     * }
     * 
     * public void IdUse.collectErrors(Collection c) {
     *     super.collectErrors(c);
     *     if(decl().isUnknown() && (!isQualified() || !qualifier().type().isUnknown()))
     *         error(c, "Unknown identifier " + getName());
     * }
     */
    val collectErrors : Attributable => (Buffer[String] => Unit) = 
        attr {
            case t => c =>
                // Process the errors of the children of t
                for (child <- t.children)
                    collectErrors (child) (c)
                // Process the errors at t
                t match {
                    case a : AssignStmt =>
                        if (!isSubtypeOf (tipe (a.Value)) (tipe (a.Variable)))
                            record (a) (c, "Can not assign a variable of type " +
                                            tipe (a.Variable).Name +
                                            " to a value of type " +
                                            tipe (a.Value).Name)
                    case d : ClassDecl =>
                        if (hasCycleOnSuperclassChain (d))
                            record (d) (c, "Cyclic inheritance chain for class " +
                                            d.Name)
                    case s : WhileStmt =>
                        if (!isSubtypeOf (tipe (s.Condition)) (booleanType (s)))
                            record (s) (c, "Condition must be a boolean expression")
                        if (!isValue (s.Condition))
                            record (s) (c, "Condition must be a value")
                    case i : IdUse =>
                        if (isUnknown (decl (i)) &&
                            (!isQualified (i) || !isUnknown (tipe (qualifier (i)))))
                            record (i) (c, "Unknown identifier " + i.Name)
                    case _ =>
                }
        }
    
    /**
     * Record a new error in the collection.
     *
     * protected void ASTNode.error(Collection c, String s) {
     *    //c.add(getLine(getStart()) + ": " + s);
     *    c.add(s);
     * }
     */
    val record : Attributable => ((Buffer[String],String) => Unit) =
        a => (b,s) => b + ((a.pos) + ": " + s)

    /**
     * Is this entity qualified?
     * 
     * eq Program.getBlock().isQualified() = false;
     * eq Program.getPredefinedType(int i).isQualified() = false;
     * eq Dot.getIdUse().isQualified() = true;
     * inh boolean IdUse.isQualified();
     * inh boolean TypeDecl.isQualified();
     */
    val isQualified : IdUse => Boolean =
        {
            i => i.parent match {
                case Dot (_, _)  => true
                case _           => false
            }
        }
      
    /**
     * What is the qualifier?
     * 
     * eq Program.getBlock().qualifier() {
     *    throw new Error("Can not compute qualifier for non qualified names");
     * }
     * eq Program.getPredefinedType(int i).qualifier() {
     *    throw new Error("Can not compute qualifier for non qualified names");
     * }
     * eq Dot.getIdUse().qualifier() = getObjectReference();
     * inh Access IdUse.qualifier();
     * inh Access TypeDecl.qualifier();
     */
    val qualifier : IdUse => Access = 
        attr {
            i => i.parent match {
                case Dot (o, _)  => o
                case _           => error ("Can not compute qualifier for non qualified names") 
            }
        }

}
