package org.cindustry.transpiler

import org.cindustry.exceptions.TokenException
import org.cindustry.parser.*
import org.cindustry.transpiler.instructions.Instruction
import org.cindustry.transpiler.instructions.InstructionManager
import org.cindustry.transpiler.instructions.InstructionsOptimizer
import org.cindustry.transpiler.instructions.JumpInstruction
import java.io.File
import java.util.*

class Transpiler(private val fileToken: FileToken, private val directory: File) : InstructionManager{
    companion object{
        const val AFTER_BLOCK_END = "blockStart"
        const val AFTER_LOOP_END = "afterLoopEnd"

        fun getParsed(file: File): FileToken{
            return Parser(Lexer(CharStream(file.readText(), file.path))).parse(file.name.replace(".cind", ""))
        }
    }

    private val mainStream: MutableList<Instruction> = ArrayList()
    private val variableStack: VariableStack = VariableStack()
    private val objectsRegistry: ObjectsRegistry = ObjectsRegistry()
    private val functionRegistry: FunctionRegistry = FunctionRegistry(this, objectsRegistry)

    private val jumpIdPending: MutableMap<BlockToken, MutableList<Pair<String, JumpInstruction>>> = IdentityHashMap()
    private val nextIdPending: MutableList<(Int) -> Unit> = LinkedList()
    private var counter = 0

    fun transpile(): String{
        val files = HashSet<FileToken>()
        files.add(fileToken)
        computeImports(files, fileToken.imports)
        files.remove(fileToken)

        mergeObjects(files)
        mergeGlobalVariables(files)
        mergeFunctions(files)

        fileToken.globalVariables.forEach(::transpileInitialization)
        fileToken.enums.forEach { objectsRegistry.addEnum(it, VariableStack.GLOBAL_SCOPE) }

        registerFunctions(fileToken.functions.filter { it.name.word != "main" })

        val mainIndex = mainStream.size
        val main: FunctionDeclarationToken = fileToken.functions.find { it.name.word == "main" }
                ?: throw TokenException(TokenException.COMPILATION, "No main function")

        variableStack.add(main.codeBlock, main)
        main.codeBlock.statements.forEach(this::transpileExecutableToken)

        // If there are global variables,
        // then cycling of the main function is replaced by artificial jump rather than natural processor cycling,
        // so that global variables won't change after each cycle
        if (mainIndex != 0)
            writeJumpInstruction(JumpInstruction.createAlways(mainStream[mainIndex].id, nextId()))

        val firstId = mainStream[mainIndex].id
        // Allows jumps for if statements to transpile properly if there is no following instruction
        nextIdPending.forEach {
            // Redirects jump to the start, as if the program ended after jump firing
            it.invoke(firstId)
        }

        InstructionsOptimizer.optimize(mainStream)

        return mainStream.joinToString("\n") { it.getCode(::getInstructionIndex) }
    }

    // TODO those conflicts can be easily avoided wih proper imports but not for now
    private fun mergeObjects(files: HashSet<FileToken>) {
        for (file in files) {
            val conflictCandidate = objectsRegistry.enums.find { it.name in file.enums.map { i -> i.name.word } }
            if (conflictCandidate != null)
                throw TokenException(TokenException.IMPORT, "Enum '${conflictCandidate.name}' from '${conflictCandidate.from}' conflicts with '${conflictCandidate.name}' at '${file.name}'")

            file.enums.forEach { objectsRegistry.addEnum(it, file.name) }
        }
    }

    private fun mergeGlobalVariables(files: HashSet<FileToken>) {
        for (file in files){
            val packageName = file.name

            val renamingMap: MutableMap<String, String> = HashMap()

            for (variable in file.globalVariables){
                if (variable.value is BuildingToken)
                    throw TokenException(TokenException.IMPORT, "Importing modules that use buildings is prohibited: $packageName", variable)

                val oldName = variable.name.word
                variable.name.word = "__${packageName}_$oldName"
                renamingMap[oldName] = variable.name.word

                fileToken.globalVariables.add(variable)
            }

            executeDeep(file, ::nextChildToken){
                if (it is VariableToken && it.name.word in renamingMap)
                    it.name.word = renamingMap[it.name.word]!!
            }
        }
    }

    private fun mergeFunctions(files: HashSet<FileToken>) {
        for (file in files) {
            val packageName = file.name

            for (function in file.functions) {
                val conflictCandidate = functionRegistry.getFunctionData(function.name.word, function.parameters
                    .map { objectsRegistry.findType(it.type.word) })
                if (conflictCandidate != null)
                    //TODO make imports as package private OR force to use module name before functions
                    throw TokenException(TokenException.IMPORT, "Function '${conflictCandidate.name}' from '${conflictCandidate.packageName}'" +
                            " conflicts with imported function '${function.name}' from '$packageName'")

                functionRegistry.add(function, packageName)
            }
        }
    }

    private fun computeImports(set: HashSet<FileToken>, imports: List<ImportToken>) {
        val files = ArrayList<FileToken>()
        for (import in imports) {
            val fileName = (import.path.last() as? WordToken)?.word ?: throw TokenException(TokenException.IMPORT, "Invalid import statement", import)
            if (set.any { it.name == fileName }) return

            if (fileName in functionRegistry.standardModules && import.path.size == 1) {
                functionRegistry.use(fileName)
            } else {
                val path = import.path.joinToString { if (it is WordToken) it.word else "/" } + ".cind"
                val file = File(directory, path)

                if (!file.exists() || !file.isFile)
                    throw TokenException(TokenException.IMPORT, "Module '$fileName' wasn't found at '${file.absoluteFile}'", import)

                files.add(getParsed(file))
            }
        }
        set.addAll(files)

        if (files.isNotEmpty())
            computeImports(set, files.flatMap { it.imports })
    }

    private fun registerFunctions(functions: List<FunctionDeclarationToken>) {
        functions.forEach(functionRegistry::add)
    }

    private fun getInstructionIndex(id: Int): Int {
        for (i in 0..<mainStream.size)
            if (mainStream[i].id == id)
                return i

        throw IllegalArgumentException()
    }

    private fun transpileExecutableToken(token: ExecutableToken) {
        when (token) {
            is InitializationToken -> transpileInitialization(token)
            is ExpressionToken -> transpileExpression(token)
            is WhileToken -> transpileWhile(token)
            is ForToken -> transpileFor(token)
            is IfToken -> transpileIf(token)
            is ReturnToken -> transpileReturn(token)
            else -> throw IllegalArgumentException()
        }
    }

    private fun transpileReturn(token: ReturnToken) {
        if (token.type.word == "return"){
            val scope = variableStack.blockStack.findLast { it.parentToken is FunctionDeclarationToken }!!
            val function = scope.parentToken as FunctionDeclarationToken

            if (function.name.word != "main") {
                if (token.value != null){
                    val result = transpileExpressionWithReference(token.value!!)
                    variableStack.returnStack[result] = scope
                }

                val jump = JumpInstruction.createAlways(-1, nextId())
                writeJumpInstruction(jump)

                jumpIdPending[function]!!.add(Pair( AFTER_BLOCK_END, jump))
            } else
                writeInstruction("end")
        } else {
            val loop = variableStack.blockStack.findLast { it.parentToken is ForToken || it.parentToken is WhileToken }
                ?.parentToken
            if (loop == null)
                throw TokenException(TokenException.SYNTAX, "There is no loop to ${token.type.word}", token)

            val jump = JumpInstruction.createAlways(-1, nextId())
            writeJumpInstruction(jump)

            jumpIdPending[loop]!!.add(Pair(if(token.type.word == "break") AFTER_LOOP_END else AFTER_BLOCK_END, jump))
        }
    }

    // TODO optimize if always true/false (useful for standard functions that use enums)
    private fun transpileIf(token: IfToken) {
        val conditionToken = OperationOptimizer.optimize(
                OperationToken(OperatorToken("!"), OperationToken.EmptySide(), token.condition))

        //TODO fix if ORIGINAL condition is 'true' (treat as always true if statement)
        val condition = transpileExpressionWithReference(conditionToken, DependedValue.createJump()).value
        val conditionJump =
            JumpInstruction(if (condition == "1") "always" else condition, -1, nextId())
        writeJumpInstruction(conditionJump)

        variableStack.add(token.doBlock, token)
        token.doBlock.statements.forEach(this::transpileExecutableToken)
        variableStack.remove(token.doBlock)

        awaitNextId { conditionJump.jumpToId = it }

        if (token.elseBlock != null){
            val ifEndedJump = JumpInstruction("always", -1, nextId())
            writeJumpInstruction(ifEndedJump)

            awaitNextId { conditionJump.jumpToId = it }

            variableStack.add(token.elseBlock!!, token)
            token.elseBlock!!.statements.forEach(this::transpileExecutableToken)
            variableStack.remove(token.elseBlock!!)

            awaitNextId { ifEndedJump.jumpToId = it }
        }
    }

    private fun transpileFor(token: ForToken) {
        val condition = if (token.condition != null) token.condition!! else BooleanToken(true)
        val whileToken = WhileToken(condition, token.doBlock, false).loadData(token)
        transpileWhile(whileToken, token.initialization, token.after)
    }

    private fun transpileWhile(token: WhileToken, beforeBlock: ExecutableToken? = null, afterBlock: ExecutableToken? = null) {
        variableStack.add(token.doBlock, token)
        if (beforeBlock != null)
            transpileExecutableToken(beforeBlock)

        var jumpToConditionInstruction: JumpInstruction? = null
        var blockEndPointer: Int? = null
        var startId: Int? = null

        if (!token.isDoWhile) {
            jumpToConditionInstruction = JumpInstruction.createAlways(-1, nextId())
            writeJumpInstruction(jumpToConditionInstruction)
        }

        awaitNextId { startId = it }

        jumpIdPending[token] = ArrayList()

        token.doBlock.statements.forEach(this::transpileExecutableToken)

        if (afterBlock != null){
            awaitNextId { blockEndPointer = it }
            this.transpileExecutableToken(afterBlock)
        }

        var conditionId: Int? = null
        awaitNextId { conditionId = it }

        if (startId == null) throw TokenException(TokenException.SYNTAX, "Illegal while block", token)

        val condition = transpileExpressionWithReference(token.condition, DependedValue.createJump()).value
        variableStack.remove(token.doBlock)

        val conditionJump =
            JumpInstruction(if (condition == "true") "always" else condition, startId, nextId())
        writeJumpInstruction(conditionJump)

        jumpToConditionInstruction?.jumpToId = conditionId ?: throw TokenException(TokenException.SYNTAX,"Illegal while block", token)
        if (blockEndPointer == null) blockEndPointer = conditionId

        jumpIdPending.remove(token)!!.forEach { it.second.jumpToId = when(it.first){
            AFTER_LOOP_END -> mainStream.size  // FIX
            AFTER_BLOCK_END -> blockEndPointer!!
            else -> throw IllegalArgumentException()
        } }
    }

    private fun awaitNextId(setter: (Int) -> Unit){
        nextIdPending.add(setter)
    }

    private fun transpileExpression(token: ExpressionToken) {
        if (token is NumberToken || token is StringToken || token is BooleanToken || token is FieldAccessToken) return

        when (token) {
            is OperationToken -> transpileOperationStatement(token)
            is CallToken -> transpileCallToken(token)
            else -> throw IllegalArgumentException()
        }
    }

    private fun transpileCallToken(
        token: CallToken, dependedVariable: DependedValue = DependedValue(null)
    ): TypedExpression? {
        if (variableStack.blockStack.filter { it.parentToken != null && it.parentToken is FunctionDeclarationToken }
                .any { (it.parentToken as FunctionDeclarationToken).name == token.name })
            throw TokenException(TokenException.SYNTAX, "Recursions are not allowed", token)

        val parameters = token.parameters.map { transpileExpressionWithReference(it) }

        val function = functionRegistry.getFunctionData(token.name.word, parameters.map { it.type })
                       ?: throw TokenException(TokenException.NAME,"Function '${token.name.word}(${parameters.joinToString(",") { it.type.name.lowercase() }})' is not defined", token.name)

        val result = if (function.transpilationFunction != null){
            function.transpilationFunction.invoke(parameters.toTypedArray(), dependedVariable)
        } else {
            variableStack.add(function.token!!.codeBlock, function.token)

            // TODO optimize for constants
            parameters.forEachIndexed { i, param ->
                val parameter = function.token.parameters[i]
                val initialization = InitializationToken(parameter.type, parameter.name, param.toToken(), parameter.const ?: false)
                transpileInitialization(initialization)
            }

            jumpIdPending[function.token] = ArrayList()
            function.token.codeBlock.statements.forEach(::transpileExecutableToken)

            variableStack.remove(function.token.codeBlock)

            jumpIdPending.remove(function.token)!!.forEach {  when(it.first){
                AFTER_BLOCK_END -> awaitNextId { i -> it.second.jumpToId = i }
                else -> throw IllegalArgumentException()
            } }

            if (function.returnType == Type.VOID)
                null
            else{
                val result = variableStack.returnStack.filterValues { it.parentToken == function.token }.keys.firstOrNull()
                    ?: throw TokenException(TokenException.SYNTAX, "Function '${function.name}' must return a value", token)
                variableStack.returnStack.remove(result)
                result
            }
        }

        if (dependedVariable.variable != null)
            checkType(dependedVariable.variable.type, function.returnType, token)

        return result
    }

    private fun transpileOperationStatement(token: OperationToken) {
        // We don't care if it is ++x or x++, because we don't use return value
        val increment = token.operator.operator in listOf("++", "--")
        val assigmentComposition =
            token.operator.operator != "=" && token.operator.operator in OperatorToken.ASSIGMENT_OPERATION
        if ((token.operator.operator !in OperatorToken.ASSIGMENT_OPERATION || (token.left !is AssignableToken)) && !increment)
            throw TokenException(TokenException.SYNTAX, "Invalid expression", token)

        if (token.left is ArrayAccessToken) { //TODO doesn't work for '++arr[index]'
            if (token.operator.operator == "="){
                val array = token.left as ArrayAccessToken
                val arrayTyped = transpileExpressionWithReference(array.array)
                val indexTyped = transpileExpressionWithReference(array.index)

                transpileCallToken(CallToken(WordToken("write"), listOf(token.right, arrayTyped.toToken(), indexTyped.toToken())))
            } else {
                val result = transpileExpressionWithReference(token)
                if (result.addAfter != null)
                    result.addAfter?.forEach {
                        mainStream.add(Instruction(it, nextId()))
                    }

            }

            return
        }

        if (token.left is FieldAccessToken) {  //TODO doesn't work for '++obj.field'
            val field = token.left as FieldAccessToken

            val from = transpileExpressionWithReference(field.from)
            if (from.complete) throw TokenException(TokenException.SYNTAX, "Invalid field access", field.from)
            if (from.type == Type.ENUM)
                throw TokenException(TokenException.FIELD, "Enum values are immutable", field)
            else if (from.type != Type.BUILDING)
                throw TokenException(TokenException.FIELD, "Invalid field access", field)

            val fieldData = BuildingFields.getField(field.field.word)
                ?: throw TokenException(TokenException.NAME, "Undefined building property '${field.field.word}'", field.field)

            if (!fieldData.mutable)
                throw TokenException(TokenException.FIELD, "Property '${field.field.word}' is immutable", field.field)

            val value = if (token.operator.operator != "=") {
                transpileExpressionWithReference(
                    OperationToken(
                        token.operator.primary(), field, if (increment) NumberToken("1") else token.left
                    )
                )
            } else {
                if (increment) TypedExpression("1", Type.NUMBER, false)
                else transpileExpressionWithReference(token.right)
            }

            checkType(value.type, fieldData.resultType, field)

            if (value.used) return

            writeInstruction("control ${field.field.word} ${from.value} ${value.value}")
            return
        }

        // TypedToken for buffer operations
        val variable = (if (token.left is NamedToken) token.left else token.right) as NamedToken
        val variableTyped = variable.getTyped(token.operator.operator == "=")

        val value = if (increment) TypedExpression("1", Type.NUMBER, false)
        else transpileExpressionWithReference(
            token.right,
            DependedValue(if (assigmentComposition) null else variableTyped)
        )

        if (variable is VariableToken)
            checkVariable(variable.getName(), variable) {
                checkType(it.getTyped(token.operator.operator == "="), value, token)
                if (it.constant)
                    throw TokenException(TokenException.FIELD, "Variable '${variable.getName()}' is constant", token)
            }

        getVariable(variableTyped.value, variable as? VariableToken ?: token).initialized = true
        if (value.used) return

        if (value.complete) {
            writeInstruction(value)
        } else if (token.operator.operator == "=") {
            writeInstruction("set ${variableTyped.value} #", value)
        } else {
            if (!value.compatible(TypedExpression("", Type.NUMBER, false)))
                throw TokenException(TokenException.OPERATION, "String concatenations are prohibited in this version", token)

            writeInstruction(
                "op ${getOperationName(token.operator.operator)} ${variableTyped.value} ${variableTyped.value} #",
                value
            )
        }
    }

    private fun transpileExpressionWithReference(value: ExpressionToken, dependedVariable: DependedValue = DependedValue(null)): TypedExpression {
        if (value is TypedToken) return value.value
        if (value is OperationToken.EmptySide) return TypedExpression("EMPTY", Type.ANY, false)
        if (value is NumberToken) return TypedExpression(value.number, Type.NUMBER, false)
        if (value is BooleanToken) return TypedExpression(if (value.value) "1" else "0", Type.BOOL, false)
        if (value is StringToken) return TypedExpression("\"${value.content}\"", Type.STRING, false)
        if (value is VariableToken && value.name.word == "null") return TypedExpression("null", Type.ANY, false)

        if(value is ArrayAccessToken)  //TODO actual arrays
            return transpileCallToken(
                CallToken(WordToken("read"), listOf(value.array, value.index)), dependedVariable
            ,)!!

        if (value is CallToken)
            return transpileCallToken(value, dependedVariable)
                ?: throw TokenException(TokenException.CALL, "Function '${value.name.word}' doesn't return any value", value)

        if (value is BuildingToken) {
            checkType(getVariable(value.name, value).getTyped().type, Type.BUILDING, value)
            return TypedExpression(value.name, Type.BUILDING, false)
        }

        if (value is VariableToken) {
            var variable: TypedExpression? = null

            objectsRegistry.enums.find { it.name == value.name.word }?.let {
                return TypedExpression(it.name, Type.ENUM, false)
            }

            checkVariable(value.name.word, value) { variable = it.getTyped() }
            return variable!!
        }

        if (value is FieldAccessToken)
            return this.transpileFieldValue(value)

        if (value is OperationToken) {
            if (listOf(value.left, value.right).any { it is ArrayAccessToken } && value.operator.operator in OperatorToken.ASSIGMENT_INCREMENT_OPERATION){
                if (value.operator.operator in listOf("++", "--") && value.left is OperationToken.EmptySide){
                    val array = value.right as ArrayAccessToken
                    val buffer = createBuffer(Type.NUMBER)
                    this.transpileCallToken(CallToken(WordToken("read"), listOf(array.array, array.index)), DependedValue(buffer))

                    this.transpileExpression(OperationToken(value.operator, OperationToken.EmptySide(), buffer.toToken()))

                    this.transpileCallToken(CallToken(WordToken("write"), listOf(buffer.toToken(), array.array, array.index)))
                    return buffer
                } else if (value.left is ArrayAccessToken) {
                    val array = value.left as ArrayAccessToken
                    val arrayTyped = transpileExpressionWithReference(array.array)
                    val indexTyped = transpileExpressionWithReference(array.index)

                    val buffer = createBuffer(Type.ANY)
                    this.transpileCallToken(CallToken(WordToken("read"), listOf(array.array, array.index)), DependedValue(buffer))

                    val right = if (value.operator.operator in listOf("++", "--")) TypedExpression("1", Type.NUMBER, false)
                        else transpileExpressionWithReference(value.right)

                    if (value.operator.operator == "="){
                        buffer.addAfter = arrayOf(
                            "set ${buffer.value} ${right.value}",
                            "write ${buffer.value} ${arrayTyped.value} ${indexTyped.value}"
                        )
                    } else {
                        buffer.addAfter = arrayOf(
                            "op ${getOperationName(value.operator.primary().operator)} ${buffer.value} ${buffer.value} ${right.value}",
                            "write ${buffer.value} ${arrayTyped.value} ${indexTyped.value}"
                        )
                    }

                    buffer.used = false
                    return buffer
                }
            }

            return this.transpileOperationChain(value, createBuffer(getReturnType(value)), dependedVariable)  //TODO destroy buffer
        }

        throw IllegalArgumentException()
    }

    private fun transpileFieldValue(token: FieldAccessToken): TypedExpression {
        val from = transpileExpressionWithReference(token.from)
        if (from.complete) throw TokenException(TokenException.FIELD, "Invalid field access", token)

        if (from.type == Type.ENUM){
            val enum = objectsRegistry.enums.find { it.name == from.value } ?: throw TokenException(TokenException.NAME, "No enum '${from.value}' found", token.field)
            if (token.field.word !in enum.values)
                throw TokenException(TokenException.NAME, "There is no enum entry '${enum.name}.${token.field.word}'", token.field)

            return TypedExpression("\"${enum.name}.${token.field.word}\"", Type.enum(enum.name), false)
        }

        if (from.type != Type.BUILDING) throw TokenException(TokenException.FIELD, "Invalid field access", token)

        val field = BuildingFields.getField(token.field.word) ?: throw TokenException(TokenException.NAME, "Unknown building property", token.field)
        val buffer = createBuffer(field.resultType)

        writeInstruction("sensor ${buffer.value} ${from.value} @${field.actualName}")

        return buffer
    }

    override fun createBuffer(type: Type) =
        TypedExpression(variableStack.requestBufferVariable(), type, false)

    private fun transpileOperationChain(value: OperationToken, buffer: TypedExpression, dependedVariable: DependedValue, shortAssigmentPossible: Boolean = true): TypedExpression {
        if (value.isFlat()){
            val incremental = value.operator.operator in listOf("++", "--")

            if (value.operator.operator in OperatorToken.ASSIGMENT_OPERATION || incremental && value.right is VariableToken){
                val variable = (if (incremental) value.right else value.left) as NamedToken
                val valueToken = if (incremental) NumberToken("1") else value.right

                val typed = variable.getTyped()
                if (shortAssigmentPossible) {
                    transpileExpression(value)
                    return typed
                } else {
                    //TODO add warning maybe?
                    // If user actually invoked this (shortAssigmentPossible is false) inefficient black magic,
                    // then they definitely wrote some shit-code.
                    // By the way, there are A LOT of room of improvement (specifically in this code block),
                    // however it is really rare case,
                    // so why bother, considering how toy this language is
                    val operationBuffer = TypedExpression(variableStack.requestBufferVariable(), typed.type, false)
                    val valueTokenResult = this.transpileExpressionWithReference(valueToken)

                    writeInstruction("op ${getOperationName(value.operator.operator)} ${operationBuffer.value} ${typed.value} #", valueTokenResult)
                    writeInstruction("set ${typed.value} ${operationBuffer.value}")

                    return operationBuffer
                }
            } else if (incremental) {
                val variableTyped = (value.left as VariableToken).getTyped(false)
                if (shortAssigmentPossible) {
                    variableTyped.addAfter = arrayOf("op ${getOperationName(value.operator.operator)} ${variableTyped.value} ${variableTyped.value} 1")
                    return variableTyped
                } else {
                    val operationBuffer = TypedExpression(variableStack.requestBufferVariable(), variableTyped.type, false)
                    writeInstruction("set ${operationBuffer.value} ${variableTyped.value}")
                    writeInstruction("op ${getOperationName(value.operator.operator)} ${variableTyped.value} ${variableTyped.value} 1")

                    return operationBuffer
                }
            } else if (value.operator.operator == "!"){
                return transpileFlatOperation(
                    TypedExpression("1", Type.BOOL, false),
                    transpileExpressionWithReference(value.right),
                    dependedVariable, value.operator.operator, value, buffer
                )
            }

            val operation = value.operator.operator
            val left = transpileExpressionWithReference(value.left)
            val right = transpileExpressionWithReference(value.right)

            return transpileFlatOperation(left, right, dependedVariable, operation, value, buffer)
        } else {
            val propagateLeft = value.left is OperationToken
            val shortAssigment = isShortAssigmentPossible(value.left, value.right)
            val prepareBufferFunction: (TypedExpression, OperationToken) -> TypedExpression = { b, o ->
                if (!b.compatible(TypedExpression("", getReturnType(o), false)))
                    createBuffer(getReturnType(o))
                else
                    b
            }

            val left: TypedExpression = if (!propagateLeft) transpileExpressionWithReference(value.left)
                else transpileOperationChain(
                value.left as OperationToken,
                prepareBufferFunction(buffer, value.left as OperationToken),
                DependedValue(null), shortAssigment
            )

            val right: TypedExpression = if (value.right !is OperationToken) transpileExpressionWithReference(value.right)
                else transpileOperationChain(
                    value.right as OperationToken,
                    if (propagateLeft) createBuffer(getReturnType(value.right as OperationToken))
                        else prepareBufferFunction.invoke(buffer, value.right as OperationToken),
                    DependedValue(null), shortAssigment
                )

            val operationToken = OperationToken(value.operator, left.toToken(), right.toToken())
            return when (value.operator.operator) {
                in OperatorToken.ASSIGMENT_INCREMENT_OPERATION -> {
                    transpileOperationChain(operationToken, createBuffer(getReturnType(operationToken)), DependedValue(left))
                }
                "!" -> {
                    transpileOperationChain(operationToken, buffer, dependedVariable)
                }
                else -> {
                    transpileFlatOperation(
                        left, right, dependedVariable, value.operator.operator,
                        operationToken,
                        buffer
                    )
                }
            }
        }
    }

    private fun isShortAssigmentPossible(left: ExpressionToken, right: ExpressionToken, finish: Boolean = false): Boolean {
        val operations = OperatorToken.ASSIGMENT_INCREMENT_OPERATION.toMutableList()
        operations.remove("=")

        val leftGetter: (OperationToken) -> VariableToken = { (if (it.left is VariableToken) it.left else it.right) as VariableToken }
        val rightGetter: (ExpressionToken) -> VariableToken? = {
            if (it is VariableToken) it else
                if (it is OperationToken && it.operator.operator in operations) leftGetter(it)
                else null
        }
        return !(left is OperationToken && left.operator.operator in operations
                && leftGetter.invoke(left) == rightGetter.invoke(right))
                && !(!finish && !isShortAssigmentPossible(right, left, true))
    }

    private fun transpileFlatOperation(left: TypedExpression, right: TypedExpression, dependedVariable: DependedValue,
        operation: String, value: OperationToken, buffer: TypedExpression
    ): TypedExpression {
        checkType(left, right, value)

        if (dependedVariable.jump) {
            if (getReturnType(value) !in listOf(Type.BOOL, Type.ANY))
                throw TokenException(TokenException.TYPE, "Invalid condition type ${getReturnType(value).name}", value)

            // Although it is not really a complete expression, it is assumed that it always comes with jump instruction
            return TypedExpression("${getOperationName(operation)} ${left.value} ${right.value}", Type.BOOL, true)
        } else if (dependedVariable.variable != null) {
            checkType(dependedVariable.variable, TypedExpression("", getReturnType(value), false), value)

            return TypedExpression(
                "op ${getOperationName(operation)} ${dependedVariable.variable.value} ${left.value} ${right.value}",
                getReturnType(OperationToken(OperatorToken(operation), left.toToken(), right.toToken())),
                true,
                left.addAfter ?: right.addAfter
            )
        } else {
            val result = transpileOperationChain(OperationToken(value.operator, left.toToken(), right.toToken()).loadData(value),
                    buffer, DependedValue(buffer))
            if (result.complete) {
                writeInstruction(result)
                return buffer
            } else {   // For '=' operator
                return result
            }
        }
    }

    private fun checkType(first: TypedExpression, second: TypedExpression, token: Token) {
        checkType(first.type, second.type, token)
    }

    private fun checkType(first: Type, second: Type, token: Token) {
        if (!first.compatible(second))
            throw TokenException(TokenException.TYPE, "Type '${first.name.lowercase()}' is not compatible with type '${second.name.lowercase()}'", token)
    }

    private fun checkVariable(name: String, token: Token, ifFound: (VariableData) -> Unit = { }) {
        ifFound(getVariable(if (name.startsWith("_")) name
            else VariableData.variableName(getFunctionScope(), name), token))
    }

    private fun getVariable(name: String, token: Token?): VariableData {
        val functionScope = getFunctionScope()
        return variableStack.stack.filter { it.scope.functionScope in listOf(functionScope, VariableStack.GLOBAL_SCOPE) }.find { it.name() == name}
            ?: throw TokenException(TokenException.NAME, "Variable '$name' is not defined in this scope", token)
    }

    private fun transpileInitialization(token: InitializationToken) {
        val type = objectsRegistry.findType(token.type.word)
        val data = VariableData(
            token.name.word, type,
            variableStack.blockStack.last(), token.value != null, token.const)

        val functionScope = getFunctionScope()
        if(variableStack.stack.filter { it.scope.functionScope == functionScope }.any{ it.name() == data.name() })
            throw TokenException(TokenException.NAME, "Variable '${token.name.word}' already exists in this scope", token)

        variableStack.stack.add(data)

        if (token.value != null && token.value !is BuildingToken) {
            val value = transpileExpressionWithReference(token.value!!, DependedValue(getVariable(data.name(), token.value!!).getTyped()))

            checkType(variableStack.stack.last().getTyped(), value, token.value!!)

            if (value.used) return
            if (value.complete)
                writeInstruction(value)
            else
                writeInstruction("set ${data.name()} #", value)
        } else if (token.const)
            throw TokenException(TokenException.FIELD, "Constant variables must be initialized", token)
    }

    private fun getFunctionScope() = variableStack.blockStack.last().functionScope

    override fun nextId(): Int{
        return counter++
    }

    private fun getOperationName(value: String): String{
        return when(value){
            "+", "+=", "++" -> "add"
            "-", "-=", "--" -> "sub"
            "*", "*=" -> "mul"
            "/", "/=" -> "div"
            "%", "%=" -> "mod"
            "&&" -> "land"
            "&" -> "and"
            "||", "|" -> "or"
            "!=", "!" -> "notEqual"
            "==" -> "equal"
            "===" -> "strictEqual"
            ">" -> "greaterThan"
            ">=" -> "greaterThanEq"
            "<" -> "lessThan"
            "<=" -> "lessThanEq"

            else -> value
        }
    }

    private fun getReturnType(token: OperationToken): Type {
        return when(token.operator.operator){
            "=" -> (token.left as? VariableToken)?.name?.word?.let { getVariable(it, token).getTyped().type } ?: Type.ANY
            "+", "-", "*", "/", "%", "+=", "-=", "*=", "/=", "++", "--", ">>", "<<" -> Type.NUMBER
            ">", "<", ">=", "<=", "==", "===", "!=", "!", "&&", "||", "&", "|" -> Type.BOOL
            else -> throw IllegalArgumentException()
        }
    }

    data class DependedValue(val variable: TypedExpression?, val jump: Boolean = false){
        companion object{
            fun createJump(): DependedValue{
                return DependedValue(null, true)
            }
        }
    }

    override fun writeJumpInstruction(instruction: JumpInstruction){
        mainStream.add(instruction)

        nextIdPending.forEach { it.invoke(instruction.instructionId) }
        nextIdPending.clear()
    }

    override fun writeInstruction(expression: TypedExpression){
        if (expression.value == "EMPTY")
            throw IllegalArgumentException("Empty side used")

        val id = nextId()
        mainStream.add(Instruction(expression.value, id))

        nextIdPending.forEach { it.invoke(id) }
        nextIdPending.clear()

        expression.addAfter?.forEach {
            mainStream.add(Instruction(it, nextId()))
        }
    }

    // Use # to indicate
    override fun writeInstruction(code: String, vararg elements: TypedExpression){
        if (elements.any { it.value == "EMPTY" })
            throw IllegalArgumentException("Empty side used")

        val parts = code.split("#").toMutableList()

        if (elements.isNotEmpty())
            parts.forEachIndexed { i, _ ->
                if (i != parts.size - 1)
                    parts[i] = parts[i] + elements[i].value
            }

        val id = nextId()
        mainStream.add(Instruction(parts.joinToString(""), id))

        nextIdPending.forEach { it.invoke(id) }
        nextIdPending.clear()

        for (element in elements)
            element.addAfter?.forEach {
                mainStream.add(Instruction(it, nextId()))
            }
    }

    data class TypedToken(val value: TypedExpression) : ExpressionToken(), NamedToken {
        override fun getName(): String {
            return value.value
        }
    }

    private fun NamedToken.getTyped(ignoreInitialization: Boolean = false): TypedExpression{
        return when (this) {
            is TypedToken -> this.value
            is VariableToken -> getVariable(VariableData.variableName(getFunctionScope(), this.getName()), this).getTyped(ignoreInitialization)
            else -> getVariable(this.getName(), null).getTyped(ignoreInitialization)
        }
    }

    private fun TypedExpression.toToken(): TypedToken {
        return TypedToken(this)
    }
}