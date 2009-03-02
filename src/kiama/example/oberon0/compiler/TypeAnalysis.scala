/*
 * This file is part of Kiama.
 *
 * Copyright (C) 2009 Anthony M Sloane, Macquarie University.
 *
 * Contributed by Ben Mockler.
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

package kiama.example.oberon0.compiler

object TypeAnalysis {

    import kiama.attribution.Attribution._
    import AST._
    import NameAnalysis._
    import ValueAnalysis._
    import ConstantAnalysis._

    // *** Attribute 'objType':  The associated Type object
    val objType : Attributable ==> Type =
        attr {
            // Named types
            case NamedType (id) => id->objType

            // Other types
            case t : Type => t

            // Declarations
            case ConstDecl (_, exp)   => exp->objType
            case VarDecl (_, tp)      => tp->objType
            case RefVarDecl (_, tp)   => tp->objType
            case TypeDecl (_, tp)     => tp->objType
            case FieldDecl (_, tp)    => tp->objType
            case ModuleDecl (_, _, _, _, tp)  => tp
            case ProcDecl (_, _, _, _, _, tp) => tp
            case BuiltInProcDecl (_, _, tp)	  => tp

            // Expressions
            case id : Ident => id->decl->objType

            case il : IntegerLiteral => IntegerType

            case FieldDesig (_, id) => id->objType

            case ArrayDesig (left, _) if (left->objType).isInstanceOf[ArrayType] =>
                left->objType match {case ArrayType (_, tp) => tp->objType}

            case Not (e) if e->objType == BooleanType => BooleanType

            case ue : UnaryNumExp if (ue.getExp)->objType == IntegerType => IntegerType

            case Div (l, r) if (l->objType == IntegerType && r->objType == IntegerType) => {
                if (r->isConstant && r->intValue == 0)
                    InvalidType
                else
                    IntegerType
            }

            case be : BinaryNumExp if ((be.getLeft)->objType == IntegerType
                                       && (be.getRight)->objType == IntegerType) => IntegerType

            case And (l, r) if (l->objType == BooleanType && r->objType == BooleanType) => BooleanType

            case Or (l, r) if (l->objType == BooleanType && r->objType == BooleanType) => BooleanType

            case Equal (l, r) if (l->objType == BooleanType && r->objType == BooleanType) => BooleanType
            case Equal (l, r) if (l->objType == IntegerType && r->objType == IntegerType) => BooleanType

            case NotEqual (l, r) if (l->objType == BooleanType && r->objType == BooleanType) => BooleanType
            case NotEqual (l, r) if (l->objType == IntegerType && r->objType == IntegerType) => BooleanType

            case LessThan (l, r) if (l->objType == IntegerType && r->objType == IntegerType) => BooleanType

            case LessThanOrEqual (l, r) if (l->objType == IntegerType && r->objType == IntegerType) => BooleanType

            case GreaterThan (l, r) if (l->objType == IntegerType && r->objType == IntegerType) => BooleanType

            case GreaterThanOrEqual (l, r) if (l->objType == IntegerType && r->objType == IntegerType) => BooleanType

            // Assignment statements
            case Assignment (desig, exp) => {
                if (desig->objType == exp->objType)
                    desig->objType
                else
                    InvalidType
            }

            // Other statements
            case s : Statement => StatementType

            case _ => InvalidType
        }

    // *** Attribute 'isAssignable':  Whether an object can store a value
    val isAssignable : Exp ==> Boolean = 
        attr {
            case id : Ident => true
            case fd : FieldDesig => true
            case ad : ArrayDesig => true
            case _ => false
        }
    
    // *** Attribute 'validProcArgs': Verify that number and type of formal params equals
    // number and type of actual params
    val procArgErrors : ProcedureCall ==> List[String] =
        attr {
            case ProcedureCall (des, aps) => {
                des->objType match {
                    case ProcType (fps) => {
                        // Check number of actual params
                        if (fps.size == aps.size)
                            checkParams (fps, aps)
                        else
                            List("Incorrect number of arguments in call to " + des)  
                    }
                    case _ => Nil
                }
            }
        }

    // Check individual procedure call parameters
    def checkParams (fps : List[Declaration], aps : List[Exp]) : List[String] = {
        if (fps.isEmpty)
            Nil
        else {
            var errs : List[String] = Nil
            val fp = fps.head
            val ap = aps.head

            // Check for type mismatch
            if (fp->objType != aps.head->objType)
                errs = (ap + " is not of type " + (fp->objType)) :: errs

            // Check for by-ref formal param with non-assignable actual param
            if (fp.isInstanceOf[RefVarDecl] && !(ap->isAssignable)) {
                errs = (ap + " must be a variable, record field or array element") :: errs
            }

            errs = checkParams (fps.tail, aps.tail) ::: errs
            errs
        }
    }

    // *** Attribute 'byteSize':  Memory size associated with types and declarations
    val byteSize : Attributable ==> Int = 
        attr {
            case dec : Declaration => dec->objType->byteSize

		    case nt : NamedType => nt->objType->byteSize

            case at @ ArrayType (sz, tp) if (sz->objType == IntegerType && sz->isConstant) =>
		        (sz->intValue) * (tp->objType->byteSize)

		    case RecordType (flds) => {
		        var sz : Int = 0
		        for (fld <- flds)
                    sz += fld->byteSize
                sz
		    }

		    case IntegerType => 4
		    case BooleanType => 4
		    case _ => -1
        }

    // *** Attribute 'level':  Nesting depth of the declaration
    val level : Attributable ==> Int = 
        attr {
            case md : ModuleDecl => 0

		    case pd : ProcDecl => (pd.parent)->level + 1

		    case x @ _ => (x.parent)->level
        }
}
