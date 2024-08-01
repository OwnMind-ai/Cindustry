package org.cindustry.transpiler.instructions

import org.cindustry.transpiler.TypedExpression

interface InstructionManager {
    fun nextId(): Int

    fun writeJumpInstruction(instruction: JumpInstruction)
    fun writeInstruction(expression: TypedExpression)
    fun writeInstruction(code: String, vararg elements: TypedExpression)
}