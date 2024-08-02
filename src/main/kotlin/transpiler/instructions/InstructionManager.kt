package org.cindustry.transpiler.instructions

import org.cindustry.transpiler.TypedExpression
import org.cindustry.transpiler.Types

interface InstructionManager {
    fun nextId(): Int

    fun writeJumpInstruction(instruction: JumpInstruction)
    fun writeInstruction(expression: TypedExpression)
    fun writeInstruction(code: String, vararg elements: TypedExpression)

    fun createBuffer(type: Types): TypedExpression
}