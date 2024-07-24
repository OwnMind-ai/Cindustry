package org.cindustry.transpiler

import org.cindustry.parser.CodeBlockToken
import java.util.*

class VariableStack {
    val stack: LinkedList<VariableData> = LinkedList()
    val blockStack: LinkedList<Scope> = LinkedList()

    fun add(block: CodeBlockToken){
        val id = blockStack.lastOrNull()?.id ?: 0
        blockStack.add(Scope(block, id, 0))
    }

    fun requestBufferVariable(): String {
        val scope = blockStack.last()
        return "_buffer${scope.id}_${scope.bufferCount++}"
    }

    data class VariableData(val name: String, val type: String, val codeBlock: CodeBlockToken){
        fun getTyped(): TypedExpression{
            return TypedExpression(name, Types.valueOf(type.uppercase()), false)
        }
    }
}

enum class Types{
    NUMBER, VOID, STRING, BUILDING, ANY, BOOL
}

data class Scope(val block: CodeBlockToken, val id: Int, var bufferCount: Int)

data class TypedExpression(val value: String, val type: Types, val complete: Boolean){
    fun compatible(other: TypedExpression): Boolean{
        return other.type != Types.VOID && this.type != Types.VOID &&
                (other.type == Types.ANY || this.type == Types.ANY || other.type == this.type)
    }
}