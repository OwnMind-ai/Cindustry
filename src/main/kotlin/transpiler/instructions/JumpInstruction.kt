package org.cindustry.transpiler.instructions

class JumpInstruction(private var condition: String, var jumpToId: Int, val instructionId: Int) : Instruction("", instructionId){
    override fun getCode(lineProvider: (Int) -> Int): String {
        return "jump ${lineProvider.invoke(jumpToId)} $condition"
    }

    companion object{
        fun createAlways(jumpToId: Int, instructionId: Int): JumpInstruction {
            return JumpInstruction("always", jumpToId, instructionId)
        }
    }
}