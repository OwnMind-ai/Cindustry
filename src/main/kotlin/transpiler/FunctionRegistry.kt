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
    val transpilationFunction: ((Array<TypedExpression>, Transpiler.DependedValue) -> TypedExpression?)? = null
){
    init {
        if (token == null && transpilationFunction == null) throw IllegalStateException()
    }

    fun isCallable(parameters: List<Types>): Boolean{
        return parameters.size == parameterStructure.size && parameters.withIndex()
            .all { it.value.compatible(parameterStructure[it.index]) }
    }
}

private fun addMathFunction(instructionManager: InstructionManager, registry: MutableList<FunctionData>, name: String, paramsNumber: Int = 2, actualName: String = name){
    registry.add(FunctionData(name, List(paramsNumber) { Types.NUMBER }, Types.NUMBER, FunctionRegistry.STANDARD_PACKAGE) { params, dependedVariable ->
        val variable = if (dependedVariable.variable != null){
            dependedVariable.variable.used = true
            dependedVariable.variable
        } else
            instructionManager.createBuffer(Types.NUMBER)

        instructionManager.writeInstruction("op $actualName # # #", variable, params[0],
            if (paramsNumber == 2) params[1] else TypedExpression("0", Types.NUMBER, false))
        variable
    })
}

private fun createStandardRegistry(instructionManager: InstructionManager): List<FunctionData> {
    val standard = LinkedList<FunctionData>()

    // MATH FUNCTIONS
    //TODO Move to separate 'math' package

    listOf("pow", "max", "min", "angle", "angleDiff", "noise")
        .forEach { addMathFunction(instructionManager, standard, it) }
    listOf("abs", "log", "log10", "floor", "ceil", "sqrt", "rand", "sin", "cos", "tan", "asin", "acos", "atan")
        .forEach { addMathFunction(instructionManager, standard, it, paramsNumber = 1) }

    addMathFunction(instructionManager, standard, "intDiv", 2, "idiv")
    addMathFunction(instructionManager, standard, "vectorLen", 2, "len")
    addMathFunction(instructionManager, standard, "flip", 1, "not")

    // MINDUSTRY FUNCTIONS
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
    { params, dependedVariable ->
        if (dependedVariable.variable != null) {
            instructionManager.writeInstruction("read # # #", dependedVariable.variable, *params)
            dependedVariable.variable.used = true
            dependedVariable.variable
        } else {
            val buffer = instructionManager.createBuffer(Types.ANY)
            instructionManager.writeInstruction("read # # #", buffer, *params)
            buffer
        }
    })

    standard.add(FunctionData("write", listOf(Types.ANY, Types.BUILDING, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("write # # #", *params)
        null
    })

    standard.add(FunctionData("clearDisplay", listOf(), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { _, _ ->
        instructionManager.writeInstruction("draw clear 0 0 0 0 0 0")
        null
    })

    standard.add(FunctionData("clearDisplay", listOf(Types.NUMBER, Types.NUMBER, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw clear # # # 0 0 0", *params)
        null
    })

    standard.add(FunctionData("setColor", listOf(Types.NUMBER, Types.NUMBER, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw color # # # 255 0 0", *params)
        null
    })

    standard.add(FunctionData("setColor", listOf(Types.STRING), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        if (params[0].value.first() != '"' || params[0].value.last() != '"')   // TODO add type and specific syntax for these
            throw TranspileException("For function 'color', that first parameter must be a string, not a variable or expression")

        val value = params[0].value.replace("\"", "")
        if (value.length != 6 || value.any { !it.isDigit() && !CharRange('a', 'f').contains(it.lowercaseChar()) })
            throw TranspileException("Invalid color code")

        instructionManager.writeInstruction("draw col %${value} 0 0 255 0 0", *params)
        null
    })

    standard.add(FunctionData("setColor", listOf(Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw color # # # # 0 0", *params)
        null
    })

    standard.add(FunctionData("setStroke", listOf(Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw stroke # 0 0 0 0 0", *params)
        null
    })

    standard.add(FunctionData("drawLine", listOf(Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw line # # # # 0 0", *params)
        null
    })

    standard.add(FunctionData("drawRect", listOf(Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw rect # # # # 0 0", *params)
        null
    })

    standard.add(FunctionData("lineRect", listOf(Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw lineRect # # # # 0 0", *params)
        null
    })

    standard.add(FunctionData("poly", listOf(Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw poly # # # # # 0", *params)
        null
    })

    standard.add(FunctionData("linePole", listOf(Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw linePoly # # # # # 0", *params)
        null
    })

    standard.add(FunctionData("triangle", listOf(Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw triangle # # # # # #", *params)
        null
    })

    standard.add(FunctionData("image", listOf(Types.NUMBER, Types.NUMBER, Types.STRING, Types.NUMBER, Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        if (params[0].value.first() != '"' || params[0].value.last() != '"') {   // TODO add type and specific syntax for these
            instructionManager.writeInstruction("draw image # # @${params[0].value.replace("\"", "")} # # 0", params[0], params[1], params[3], params[4])
        } else {
            instructionManager.writeInstruction("draw image # # # # # 0", *params)
        }
        null
    })

    standard.add(FunctionData("drawFlush", listOf(Types.BUILDING), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("drawflush #", *params)
        null
    })

    standard.add(FunctionData("getLink", listOf(Types.NUMBER), Types.BUILDING, FunctionRegistry.STANDARD_PACKAGE) { params, dependedVariable ->
        if (dependedVariable.variable != null) {
            instructionManager.writeInstruction("getlink # #", dependedVariable.variable, *params)
            dependedVariable.variable.used = true
            dependedVariable.variable
        } else {
            val buffer = instructionManager.createBuffer(Types.ANY)
            instructionManager.writeInstruction("getlink # #", buffer, *params)
            buffer
        }
    })

    standard.add(FunctionData("wait", listOf(Types.NUMBER), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("wait #", *params)
        null
    })

    standard.add(FunctionData("stop", listOf(), Types.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("stop", *params)
        null
    })

    return standard
}
