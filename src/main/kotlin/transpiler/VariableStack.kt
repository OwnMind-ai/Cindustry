package org.cindustry.transpiler

import org.cindustry.parser.CodeBlockToken
import java.util.LinkedList

class VariableStack {
    val stack: LinkedList<VariableData> = LinkedList()
    val blockStack: LinkedList<CodeBlockToken> = LinkedList()

    data class VariableData(val name: String, val type: String, val codeBlock: CodeBlockToken){
        fun getTyped(): TypedExpression{
            return TypedExpression(name, Types.valueOf(type.uppercase()), false)
        }
    }
}

enum class Types{
    NUMBER, VOID, STRING, BUILDING, ANY, BOOL
}

data class TypedExpression(val value: String, val type: Types, val complete: Boolean){
    fun compatible(other: TypedExpression): Boolean{
        return other.type != Types.VOID && this.type != Types.VOID &&
                (other.type == Types.ANY || this.type == Types.ANY || other.type == this.type)

    }
}