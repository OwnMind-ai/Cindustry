package org.cindustry.transpiler

import org.cindustry.parser.FunctionDeclarationToken
import org.cindustry.transpiler.instructions.InstructionManager
import java.util.*

class FunctionRegistry(instructionManager: InstructionManager, private val objectsRegistry: ObjectsRegistry){
    companion object {
        const val STANDARD_PACKAGE = "__STANDARD"
    }

    private val registry: MutableList<FunctionData> = ArrayList()

    val standardModules: MutableMap<String, List<FunctionData>> = HashMap()

    init {
        registry.addAll(createStandardRegistry(instructionManager))
        standardModules["math"] = createMathModule(instructionManager)
        standardModules["draw"] = createDrawModule(instructionManager)
    }

    fun add(function: FunctionDeclarationToken, packageName: String = "main"){
        registry.add(FunctionData(
            function.name.word,
            function.parameters.map { objectsRegistry.findType(it.type.word) },
            objectsRegistry.findType(function.returnType.word), // TODO FIX
            packageName,
            function
        ))
    }

    fun use(name: String){
        registry.addAll(standardModules[name] ?: throw TranspileException("Undefined standard module '$name'"))
    }

    fun getFunctionData(name: String, parameters: List<Type>): FunctionData? {
        return registry.find { it.name == name && it.isCallable(parameters) }
    }
}

data class FunctionData(
    val name: String,
    val parameterStructure: List<Type>,
    val returnType: Type,
    val packageName: String,
    val token: FunctionDeclarationToken? = null,
    val transpilationFunction: ((Array<TypedExpression>, Transpiler.DependedValue) -> TypedExpression?)? = null
){
    init {
        if (token == null && transpilationFunction == null) throw IllegalStateException()
    }

    fun isCallable(parameters: List<Type>): Boolean{
        return parameters.size == parameterStructure.size && parameters.withIndex()
            .all { it.value.compatible(parameterStructure[it.index]) }
    }
}


private fun createStandardRegistry(instructionManager: InstructionManager): List<FunctionData> {
    val standard = LinkedList<FunctionData>()

    // MINDUSTRY FUNCTIONS
    standard.add(FunctionData("print", listOf(Type.ANY), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("print #", params[0])
        null
    })

    standard.add(FunctionData("printFlush", listOf(Type.BUILDING), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("printflush #", params[0])
        null
    })

    standard.add(FunctionData("print", listOf(Type.ANY, Type.BUILDING), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("print #", params[0])
        instructionManager.writeInstruction("printflush #", params[1])
        null
    })

    standard.add(FunctionData("read", listOf(Type.BUILDING, Type.NUMBER), Type.ANY, FunctionRegistry.STANDARD_PACKAGE)
    { params, dependedVariable ->
        if (dependedVariable.variable != null) {
            instructionManager.writeInstruction("read # # #", dependedVariable.variable, *params)
            dependedVariable.variable.used = true
            dependedVariable.variable
        } else {
            val buffer = instructionManager.createBuffer(Type.ANY)
            instructionManager.writeInstruction("read # # #", buffer, *params)
            buffer
        }
    })

    standard.add(FunctionData("write", listOf(Type.ANY, Type.BUILDING, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("write # # #", *params)
        null
    })

    standard.add(FunctionData("getLink", listOf(Type.NUMBER), Type.BUILDING, FunctionRegistry.STANDARD_PACKAGE) { params, dependedVariable ->
        if (dependedVariable.variable != null) {
            instructionManager.writeInstruction("getlink # #", dependedVariable.variable, *params)
            dependedVariable.variable.used = true
            dependedVariable.variable
        } else {
            val buffer = instructionManager.createBuffer(Type.ANY)
            instructionManager.writeInstruction("getlink # #", buffer, *params)
            buffer
        }
    })

    standard.add(FunctionData("wait", listOf(Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("wait #", *params)
        null
    })

    standard.add(FunctionData("stop", listOf(), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("stop", *params)
        null
    })

    return standard
}

private fun createDrawModule(instructionManager: InstructionManager): List<FunctionData> {
    val result = ArrayList<FunctionData>()

    result.add(FunctionData("clearDisplay", listOf(), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { _, _ ->
        instructionManager.writeInstruction("draw clear 0 0 0 0 0 0")
        null
    })

    result.add(FunctionData("clearDisplay", listOf(Type.NUMBER, Type.NUMBER, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw clear # # # 0 0 0", *params)
        null
    })

    result.add(FunctionData("setColor", listOf(Type.NUMBER, Type.NUMBER, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw color # # # 255 0 0", *params)
        null
    })

    result.add(FunctionData("setColor", listOf(Type.STRING), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        if (params[0].value.first() != '"' || params[0].value.last() != '"')   // TODO add type and specific syntax for these
            throw TranspileException("For function 'color', that first parameter must be a string, not a variable or expression")

        val value = params[0].value.replace("\"", "")
        if (value.length != 6 || value.any { !it.isDigit() && !CharRange('a', 'f').contains(it.lowercaseChar()) })
            throw TranspileException("Invalid color code")

        instructionManager.writeInstruction("draw col %${value} 0 0 255 0 0", *params)
        null
    })

    result.add(FunctionData("setColor", listOf(Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw color # # # # 0 0", *params)
        null
    })

    result.add(FunctionData("setStroke", listOf(Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw stroke # 0 0 0 0 0", *params)
        null
    })

    result.add(FunctionData("drawLine", listOf(Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw line # # # # 0 0", *params)
        null
    })

    result.add(FunctionData("drawRect", listOf(Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw rect # # # # 0 0", *params)
        null
    })

    result.add(FunctionData("lineRect", listOf(Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw lineRect # # # # 0 0", *params)
        null
    })

    result.add(FunctionData("poly", listOf(Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw poly # # # # # 0", *params)
        null
    })

    result.add(FunctionData("linePole", listOf(Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw linePoly # # # # # 0", *params)
        null
    })

    result.add(FunctionData("triangle", listOf(Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("draw triangle # # # # # #", *params)
        null
    })

    result.add(FunctionData("image", listOf(Type.NUMBER, Type.NUMBER, Type.STRING, Type.NUMBER, Type.NUMBER), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        if (params[0].value.first() != '"' || params[0].value.last() != '"') {   // TODO add type and specific syntax for these
            instructionManager.writeInstruction("draw image # # @${params[0].value.replace("\"", "")} # # 0", params[0], params[1], params[3], params[4])
        } else {
            instructionManager.writeInstruction("draw image # # # # # 0", *params)
        }
        null
    })

    result.add(FunctionData("drawFlush", listOf(Type.BUILDING), Type.VOID, FunctionRegistry.STANDARD_PACKAGE) { params, _ ->
        instructionManager.writeInstruction("drawflush #", *params)
        null
    })

    return result
}

private fun addMathFunction(instructionManager: InstructionManager, registry: MutableList<FunctionData>, name: String, paramsNumber: Int = 2, actualName: String = name){
    registry.add(FunctionData(name, List(paramsNumber) { Type.NUMBER }, Type.NUMBER, FunctionRegistry.STANDARD_PACKAGE) { params, dependedVariable ->
        val variable = if (dependedVariable.variable != null){
            dependedVariable.variable.used = true
            dependedVariable.variable
        } else
            instructionManager.createBuffer(Type.NUMBER)

        instructionManager.writeInstruction("op $actualName # # #", variable, params[0],
            if (paramsNumber == 2) params[1] else TypedExpression("0", Type.NUMBER, false))
        variable
    })
}

private fun createMathModule(instructionManager: InstructionManager): List<FunctionData> {
    val result = ArrayList<FunctionData>()

    listOf("pow", "max", "min", "angle", "angleDiff", "noise")
        .forEach { addMathFunction(instructionManager, result, it) }
    listOf("abs", "log", "log10", "floor", "ceil", "sqrt", "rand", "sin", "cos", "tan", "asin", "acos", "atan")
        .forEach { addMathFunction(instructionManager, result, it, paramsNumber = 1) }

    addMathFunction(instructionManager, result, "intDiv", 2, "idiv")
    addMathFunction(instructionManager, result, "vectorLen", 2, "len")
    addMathFunction(instructionManager, result, "flip", 1, "not")

    return result
}