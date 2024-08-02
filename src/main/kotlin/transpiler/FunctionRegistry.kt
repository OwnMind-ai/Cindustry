package org.cindustry.transpiler

import org.cindustry.parser.FunctionDeclarationToken
import org.cindustry.transpiler.instructions.InstructionManager
import java.util.*

class FunctionRegistry(instructionManager: InstructionManager){
    companion object {
        const val STANDARD_PACKAGE = "__STANDARD"
    }

    private val registry: MutableList<FunctionData> = ArrayList()

    init {
        registry.addAll(createStandardRegistry(instructionManager))
    }
}

data class FunctionData(
    val name: String,
    val parameterStructure: List<Types>,
    val returnType: Types,
    val packageName: String,
    val token: FunctionDeclarationToken? = null,
    val transpilationFunction: ((Collection<TypedExpression>) -> TypedExpression?)? = null
){
    init {
        if (token == null && transpilationFunction == null) throw IllegalStateException()
    }

    fun isCallable(parameters: List<Types>): Boolean{
        return parameters.size == parameterStructure.size && parameters.withIndex()
            .all { it.value.compatible(parameterStructure[it.index]) }
    }
}

private fun createStandardRegistry(instructionManager: InstructionManager): List<FunctionData> {
    val standard = LinkedList<FunctionData>()

    standard.add(FunctionData("print", listOf(Types.ANY), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) {
        null
    })

    return standard
}
