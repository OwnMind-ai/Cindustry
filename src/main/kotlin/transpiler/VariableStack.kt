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

/*
'executeAfter' exists because of this case: "x = a++ * 4".
Since operation translation is recursive, we are unable to put incrementing operation right after returning 'a'.
There are two possible solutions:
1. Create a buffer with the value of 'a', increment 'a', return buffer (2 additional operations)
2. Return 'a', put incrementing instruction right after the use of a (1 additional operation)

The 'executeAfter' makes the second solution possible, and it is chosen by default.

However, in the case of "a++ * a++", and even "a++ * a" the second solution breaks,
since we are unable to put incrementing instruction between addition instruction.
In that case, we use the first solution.
Although it is much more expensive, this case is really rare.
*/
data class TypedExpression(val value: String, val type: Types, val complete: Boolean, var addAfter: String? = null){
    fun compatible(other: TypedExpression): Boolean{
        return other.type != Types.VOID && this.type != Types.VOID &&
                (other.type == Types.ANY || this.type == Types.ANY || other.type == this.type)
    }
}