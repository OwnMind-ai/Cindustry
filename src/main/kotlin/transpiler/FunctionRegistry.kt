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

    fun getFunctionData(name: String, parameters: List<Types>): FunctionData? {
        return registry.find { it.name == name && it.isCallable(parameters) }
    }
}

data class FunctionData(
    val name: String,
    val parameterStructure: List<Types>,
    val returnType: Types,
    val packageName: String,
    val token: FunctionDeclarationToken? = null,
    val transpilationFunction: ((List<TypedExpression>, Transpiler.DependedValue) -> TypedExpression?)? = null
){
    init {
        if (token == null && transpilationFunction == null) throw IllegalStateException()
    }

    fun isCallable(parameters: List<Types>): Boolean{
        return parameters.size == parameterStructure.size && parameters.withIndex()
            .all { it.value.compatible(parameterStructure[it.index]) }
    }
}

@Suppress("SpellCheckingInspection")
private fun createStandardRegistry(instructionManager: InstructionManager): List<FunctionData> {
    val standard = LinkedList<FunctionData>()

    standard.add(FunctionData("print", listOf(Types.ANY), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("print #", params[0])
        null
    })

    standard.add(FunctionData("printFlush", listOf(Types.BUILDING), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("printflush #", params[0])
        null
    })

    standard.add(FunctionData("print", listOf(Types.ANY, Types.BUILDING), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("print #", params[0])
        instructionManager.writeInstruction("printflush #", params[1])
        null
    })

    standard.add(FunctionData("read", listOf(Types.BUILDING, Types.NUMBER), Types.ANY, FunctionRegistry.STANDARD_PACKAGE)
    { params, dependedVarible ->
        if (dependedVarible.variable != null) {
            instructionManager.writeInstruction("read # # #", dependedVarible.variable, params[0], params[1])
            dependedVarible.variable
        } else {
            val buffer = instructionManager.createBuffer(Types.ANY)
            instructionManager.writeInstruction("read # # #", buffer, params[0], params[1])
            buffer
        }
    })

    return standard
}
