package org.cindustry.transpiler.instructions

import java.util.*

object InstructionsOptimizer {
    fun optimize(instructions: MutableList<Instruction>){
        val deleteQueue = LinkedList<Pair<Instruction, Int>>()

        instructions.forEachIndexed { i, current ->
            val previous = instructions.getOrNull(i - 1)
            val next = instructions.getOrNull(i + 1)

            if (current is JumpInstruction){
                if (current.instructionId + 1 == current.jumpToId)
                    deleteQueue.add(Pair(current, next?.id ?: 0))
            } else if (current.code.startsWith("set") || current.code.startsWith("op")) {
                val currentVar = getAffectedVariable(current, next, deleteQueue) ?: return@forEachIndexed
                if (previous == null || (!previous.code.startsWith("set") && !previous.code.startsWith("op")))
                    return@forEachIndexed

                val previousVar = getAffectedVariable(previous, next, deleteQueue) ?: return@forEachIndexed

                if (currentVar == previousVar)
                    deleteQueue.add(Pair(previous, current.id))
            }
        }

        deleteQueue.forEach{
            val id = it.first.id
            instructions.forEach { i ->
                if (i is JumpInstruction && i.jumpToId == id)
                    i.jumpToId = it.second
            }

            instructions.remove(it.first)
        }
    }

    private fun getAffectedVariable(instruction: Instruction, next: Instruction?, deleteQueue:  LinkedList<Pair<Instruction, Int>>): String?{
        val parts = instruction.code.split(" ").toList()

        if (parts[0] == "set" && parts[1] == parts[2]) {
            deleteQueue.add(Pair(instruction, next?.id ?: 0))
            return null
        }

        val affectedVariable = if (parts[0] == "set") parts[1] else parts[2]
        if (parts[0] == "op" && affectedVariable in parts.subList(3, 5))
            return null

        return affectedVariable
    }
}