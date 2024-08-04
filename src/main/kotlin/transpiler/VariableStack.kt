package org.cindustry.transpiler

import org.cindustry.parser.BlockToken
import org.cindustry.parser.CodeBlockToken
import java.util.*

class VariableStack {
    val stack: LinkedList<VariableData> = LinkedList()
    val blockStack: LinkedList<Scope> = LinkedList()

    init {
        blockStack.add(Scope(null, null, 0, 0))  // File scope
    }

    fun add(block: CodeBlockToken, parent: BlockToken){
        val id = blockStack.last().id
        blockStack.add(Scope(block, parent, id + 1, 0))
    }

    fun requestBufferVariable(): String {
        val scope = blockStack.last()
        return "_buffer${scope.id}_${scope.bufferCount++}"
    }

    fun remove(block: CodeBlockToken) {
        val removed = blockStack.removeLast()

        if (removed.block != block)
            throw IllegalStateException()  // TODO Change to InternalParserException

        stack.removeIf { it.codeBlock == removed.block }
    }

    data class VariableData(val name: String, val type: String, val codeBlock: CodeBlockToken?, var initialized: Boolean, var constant: Boolean){
        fun getTyped(ignoreInitialization: Boolean = false): TypedExpression{
            if (!ignoreInitialization && !initialized)
                throw TranspileException("Variable '$name' is not initialized")

            return TypedExpression(name, Types.valueOf(type.uppercase()), false)
        }
    }
}

data class Scope(val block: CodeBlockToken?, val parentToken: BlockToken?, val id: Int, var bufferCount: Int)

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
data class TypedExpression(val value: String, val type: Types, val complete: Boolean, var addAfter: Array<String>? = null, var used: Boolean = false){
    fun compatible(other: TypedExpression): Boolean{
        return type.compatible(other.type)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TypedExpression

        if (value != other.value) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}