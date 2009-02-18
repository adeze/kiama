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

package kiama.example.oberon0.machine

import RISCISA._
import kiama.machine.Machine

class RISC (code : Code) extends Machine ("RISC") {

    /**
     * Debug flag. Set this to true in sub-classes or objects to obtain
     * tracing information during execution of the machine.
     */
    override def debug = false

    /**
     * Words are 32-bits long.
     */
    type Word = Int
    
    /**
     * A named register.
     */
    case class Reg (name : String) extends State[Int] (name)

    /**
     * Integer register file addressed by 0-15.
     */
    lazy val R = Array.fromFunction (i => Reg ("R" + i.toString)) (16)
    
    /**
     * The program counter is register 15.
     */
    lazy val PC = R (15)
    
    /**
     * Byte addressed store of words.
     */
    val Mem = State[Map[Int,Word]] ("Mem")
    
    /**
     * Condition code: zero.
     */
    val Z = State[Boolean] ("Z")

    /**
     * Condition code: less than.
     */
    val N = State[Boolean] ("N")

    /**
     * Halt flag.  Undefined until the machine is to stop executing.
     */
    val halt = State[String] ("halt")

    /**
     * Initialise the machine.
     */
    def init {
        Mem.update (Map ())
        PC.update (0)
        R (0).update(0)		// BEN MOD
        Z.update (false)
        N.update (false)
        halt.undefine
    }

    /**
     * The main rule of this machine.
     */
    def main =
        if (halt isUndefined)
            execute (code (PC))

    /**
     * Execute a single instruction.
     */
    def execute (instr : Instr) = {
        if (debug)
            println (name + " exec: " + instr)
        arithmetic (instr)
        memory (instr)
        control (instr)
        inputoutput (instr)
    }
    
    /**
     * Execute arithmetic instructions.
     */
    def arithmetic (instr : Instr) = 
        instr match {
            case MOV (a, b, c)   => R (a) := R (c) << b
            case MOVI (a, b, im) => R (a) := im << b
            case MVN (a, b, c)   => R (a) := - (R (c) << b)
            case MVNI (a, b, im) => R (a) := - (im << b)
            case ADD (a, b, c)   => R (a) := R (b) + (R (c) : Int)
            case ADDI (a, b, im) => R (a) := R (b) + im
            case SUB (a, b, c)   => R (a) := R (b) - (R (c) : Int)
            case SUBI (a, b, im) => R (a) := R (b) - im
            case MUL (a, b, c)   => R (a) := R (b) * (R (c) : Int)
            case MULI (a, b, im) => R (a) := R (b) * im
            case DIV (a, b, c)   => R (a) := R (b) / (R (c) : Int)
            case DIVI (a, b, im) => R (a) := R (b) / im
            case MOD (a, b, c)   => R (a) := R (b) % (R (c) : Int)
            case MODI (a, b, im) => R (a) := R (b) % im
            case CMP (b, c)      => Z := R (b).value == R (c).value             // BEN MOD
                                    N := R (b).value < (R (c).value : Int)      // BEN MOD
            case CMPI (b, im)    => Z := R (b).value == im		// BEN MOD
                                    N := R (b).value < im		// BEN MOD
            case CHKI (a, im)    => if ((R (a) < 0) || (R (a) >= im))
                                        R (a) := 0
            case _ => ()		// BEN MOD
        }

    /**
     * Execute memory instructions.
     */
    def memory (instr : Instr) =
        instr match {
            case LDW (a, b, im) => R (a) := Mem ((R (b) + im) / 4)
            case LDB (a, b, im) => halt := "LDB not implemented"
            case POP (a, b, im) => R (a) := Mem (R (b) / 4)
                                   R (b) := R (b) + im
            case STW (a, b, im) => Mem := Mem + (((R (b) + im) / 4, R (a)))
            case STB (a, b, im) => halt := "STB not implemented"
            case PSH (a, b, im) => Mem := Mem + (((R (b) - im) / 4, R (a)))
                                   R (b) := R (b) - im
            case _ => ()		// BEN MOD
        }

    /**
     * Execute control instructions, including default control step.
     */
    def control (instr : Instr) =
        instr match {
            case b : BEQ if (Z.value) => PC := PC + b.disp
            case b : BNE if (!Z.value) => PC := PC + b.disp
            case b : BLT if (N.value) => PC := PC + b.disp
            case b : BGE if (!N.value) => PC := PC + b.disp
            case b : BLE if (Z.value || N.value) => PC := PC + b.disp
            case b : BGT if (!Z.value && !N.value) => PC := PC + b.disp
            case b : BR => PC := PC + b.disp
            case b : BSR => PC := PC + b.disp
                               R (14) := PC
            case RET (c) => PC := R (c)
                            if (R (c).value == 0) halt := "Halt"
            case _  => PC := PC + 1
        }

    /**
     * Execute input/output instructions.
     */
    def inputoutput (instr : Instr) = 
        instr match {
            case RD (a)  => R (a) := readInt
            case WRD (c) => print (R (c))
            case WRH (c) => print (R (c) toHexString)
            case WRL     => println
            case _ => ()	// BEN MOD
        }    

}

object MachineTest {
    def main (args: Array[String]) {
        val mycode = List (
            ADDI (1, 0, 1),
            ADDI (2, 0, 0),
            STW (1, 2, 0),
            CMPI (1, 5),
            BGT (4),
            WRH (1),
            ADDI (1, 1, 1),
            BR (-4),
            RET (0)
        )
        val mymachine = new RISC (mycode)
        mymachine.init
        mymachine.steps
    }
}
