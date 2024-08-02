package org.cindustry.transpiler

import org.cindustry.parser.*
import org.cindustry.transpiler.instructions.Instruction
import org.cindustry.transpiler.instructions.InstructionManager
import org.cindustry.transpiler.instructions.JumpInstruction
import java.util.*

class Transpiler(private val fileToken: FileToken) : InstructionManager{
    companion object{
        const val AFTER_BLOCK_END = "blockStart"
        const val AFTER_LOOP_END = "afterLoopEnd"
    }

    private val mainStream: MutableList<Instruction> = ArrayList()
    private val variableStack: VariableStack = VariableStack()
    private val functionRegistry: FunctionRegistry = FunctionRegistry(this)

    private val jumpIdPending: MutableMap<BlockToken, MutableList<Pair<String, JumpInstruction>>> = IdentityHashMap()
    private val nextIdPending: MutableList<(Int) -> Unit> = LinkedList()
    private var counter = 0

    fun transpile(): String{
        fileToken.globalVariables.forEach(::transpileInitialization)

        val mainIndex = mainStream.size
        val main: FunctionDeclarationToken = fileToken.functions.find { it.name.word == "main" }
                ?: throw TranspileException("No main function")

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

        return mainStream.joinToString("\n") { it.getCode(::getInstructionIndex) }
    }

    private fun getInstructionIndex(id: Int): Int {
        for (i in 0..mainStream.size)
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
            val function = variableStack.blockStack.findLast { it.parentToken is FunctionDeclarationToken }!!
                    .parentToken as FunctionDeclarationToken

            if (function.name.word != "main") TODO()

            writeInstruction("end")
        } else {
            val loop = variableStack.blockStack.findLast { it.parentToken is ForToken || it.parentToken is WhileToken }
                ?.parentToken
            if (loop == null)
                throw  TranspileException("There is no loop to ${token.type.word}")

            val jump = JumpInstruction.createAlways(-1, nextId())
            writeJumpInstruction(jump)

            jumpIdPending[loop]!!.add(Pair(if(token.type.word == "break") AFTER_LOOP_END else AFTER_BLOCK_END, jump))
        }
    }

    private fun transpileIf(token: IfToken) {
        // TODO change to something like token.condition.negate(), idk
        val conditionToken = OperationOptimizer.optimize(
                OperationToken(OperatorToken("!"), OperationToken.EmptySide(), token.condition))

        val condition = transpileExpressionWithReference(conditionToken, DependedValue.createJump()).value
        val conditionJump =
            JumpInstruction(if (condition == "true") "always" else condition, -1, nextId())
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
        if (token.initialization != null)
            transpileExecutableToken(token.initialization!!)

        val condition = if (token.condition != null) token.condition!! else BooleanToken(true)
        val whileToken = WhileToken(condition, token.doBlock, false)
        transpileWhile(whileToken, token.after)
    }

    private fun transpileWhile(token: WhileToken, afterBlock: ExecutableToken? = null) {
        var jumpToConditionInstruction: JumpInstruction? = null
        var blockEndPointer: Int? = null
        var startId: Int? = null

        if (!token.isDoWhile) {
            jumpToConditionInstruction = JumpInstruction.createAlways(-1, nextId())
            writeJumpInstruction(jumpToConditionInstruction)
        }

        awaitNextId { startId = it }

        jumpIdPending[token] = ArrayList()

        variableStack.add(token.doBlock, token)
        token.doBlock.statements.forEach(this::transpileExecutableToken)
        variableStack.remove(token.doBlock)

        if (afterBlock != null){
            awaitNextId { blockEndPointer = it }
            this.transpileExecutableToken(afterBlock)
        }

        var conditionId: Int? = null
        awaitNextId { conditionId = it }

        if (startId == null) throw TranspileException("Illegal while block")

        val condition = transpileExpressionWithReference(token.condition, DependedValue.createJump()).value
        val conditionJump =
            JumpInstruction(if (condition == "true") "always" else condition, startId!!, nextId())
        writeJumpInstruction(conditionJump)

        jumpToConditionInstruction?.jumpToId = conditionId ?: throw TranspileException("Illegal while block")
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
        val parameters = token.parameters.map { transpileExpressionWithReference(it) }

        val function = functionRegistry.getFunctionData(token.name.word, parameters.map { it.type })
                       ?: throw TranspileException("Function '${token.name.word}' is not defined")

        val result = if (function.transpilationFunction != null){
            function.transpilationFunction.invoke(parameters.toTypedArray(), dependedVariable)
        } else {
            TODO()
        }

        if (dependedVariable.variable != null)
            checkType(dependedVariable.variable.type, function.returnType)

        return result
    }

    private fun transpileOperationStatement(token: OperationToken) {
        // We don't care if it is ++x or x++, because we don't use return value
        val increment = token.operator.operator in listOf("++", "--")
        val assigmentComposition =
            token.operator.operator != "=" && token.operator.operator in OperatorToken.ASSIGMENT_OPERATION
        if ((token.operator.operator !in OperatorToken.ASSIGMENT_OPERATION ||
                    (token.left !is NamedToken && token.left !is FieldAccessToken)) && !increment
        )
            throw TranspileException("Invalid expression")

        if (token.left is FieldAccessToken) {
            val field = token.left as FieldAccessToken

            val from = transpileExpressionWithReference(field.from)
            if (from.complete || from.type != Types.BUILDING) throw TranspileException("Invalid field access")

            val fieldData = BuildingFields.getField(field.field.word)!!
            if (!fieldData.mutable)
                throw TranspileException("Property '${field.field.word}' is immutable")

            val value = if (token.operator.operator != "=") {
                transpileExpressionWithReference(
                    OperationToken(
                        token.operator.primary(), field, if (increment) NumberToken("1") else token.left
                    )
                )
            } else {
                if (increment) TypedExpression("1", Types.NUMBER, false)
                else transpileExpressionWithReference(token.right)
            }

            checkType(value.type, fieldData.resultType)

            if (value.used) return

            writeInstruction("control ${field.field.word} ${from.value} ${value.value}")
            return
        }

        // TypedToken for buffer operations
        val variable = (if (token.left is NamedToken) token.left else token.right) as NamedToken
        val variableTyped = variable.getTyped(token.operator.operator == "=")

        val value = if (increment) TypedExpression("1", Types.NUMBER, false)
        else transpileExpressionWithReference(
            token.right,
            DependedValue(if (assigmentComposition) null else variableTyped)
        )

        if (variable is VariableToken)
            checkVariable(variable.getName()) { checkType(it.getTyped(token.operator.operator == "="), value) }

        getVariable(variable.getName()).initialized = true
        if (value.used) return

        if (value.complete) {
            writeInstruction(value)
        } else if (token.operator.operator == "=") {
            writeInstruction("set ${variable.getName()} #", value)
        } else {
            if (!value.compatible(TypedExpression("", Types.NUMBER, false)))
                throw TranspileException("Unable to perform string concatenation in this version")

            writeInstruction(
                "op ${getOperationName(token.operator.operator)} ${variable.getName()} ${variable.getName()} #",
                value
            )
        }
    }

    private fun transpileExpressionWithReference(value: ExpressionToken, dependedVariable: DependedValue = DependedValue(null)): TypedExpression {
        if (value is TypedToken) return value.value
        if (value is OperationToken.EmptySide) return TypedExpression("EMPTY", Types.ANY, false)
        if (value is NumberToken) return TypedExpression(value.number, Types.NUMBER, false)
        if (value is BooleanToken) return TypedExpression(if (value.value) "1" else "0", Types.BOOL, false)
        if (value is StringToken) return TypedExpression("\"${value.content}\"", Types.STRING, false)
        if (value is VariableToken && value.name.word == "null") return TypedExpression("null", Types.ANY, false)

        if (value is CallToken)
            return transpileCallToken(value, dependedVariable) ?: throw TranspileException("Function '${value.name.word}' doesn't return any value")

        if (value is BuildingToken) {
            checkVariable(value.name){ checkType(it.getTyped().type, Types.BUILDING) }
            return TypedExpression(value.name, Types.BUILDING, false)
        }

        if (value is VariableToken) {
            var variable: TypedExpression? = null
            checkVariable(value.name.word) { variable = it.getTyped() }
            return variable!!
        }

        if (value is FieldAccessToken)
            return this.transpileFieldValue(value)

        if (value is OperationToken)
            return this.transpileOperationChain(value, createBuffer(getReturnType(value))  /*TODO destroy*/, dependedVariable)

        throw IllegalArgumentException()
    }

    private fun transpileFieldValue(token: FieldAccessToken): TypedExpression {
        val from = transpileExpressionWithReference(token.from)
        if(from.complete || from.type != Types.BUILDING) throw TranspileException("Invalid field access")

        val field = BuildingFields.getField(token.field.word) ?: throw TranspileException("Unknown building property")
        val buffer = createBuffer(field.resultType)

        writeInstruction("sensor ${buffer.value} ${from.value} @${field.actualName}")

        return buffer
    }

    override fun createBuffer(type: Types) =
        TypedExpression(variableStack.requestBufferVariable(), type, false)

    private fun transpileOperationChain(value: OperationToken, buffer: TypedExpression, dependedVariable: DependedValue, shortAssigmentPossible: Boolean = true): TypedExpression {
        if (value.isFlat()){
            val incremental = value.operator.operator in listOf("++", "--")

            if (value.operator.operator in OperatorToken.ASSIGMENT_OPERATION || incremental && value.right is VariableToken){
                val variable = (if (incremental) value.right else value.left) as NamedToken
                val valueToken = if (incremental) NumberToken("1") else value.right

                if (shortAssigmentPossible) {
                    transpileExpression(value)
                    return variable.getTyped()
                } else {
                    //TODO add warning maybe?
                    // If user actually invoked this (shortAssigmentPossible is false) inefficient black magic,
                    // then they definitely wrote some shit-code.
                    // By the way, there are A LOT of room of improvement (specifically in this code block),
                    // however it is really rare case,
                    // so why bother, considering how toy this language is
                    val operationBuffer = TypedExpression(variableStack.requestBufferVariable(), variable.getTyped().type, false)
                    val valueTokenResult = this.transpileExpressionWithReference(valueToken)

                    writeInstruction("op ${getOperationName(value.operator.operator)} ${operationBuffer.value} ${variable.getName()} #", valueTokenResult)
                    writeInstruction("set ${variable.getName()} ${operationBuffer.value}")

                    return operationBuffer
                }
            } else if (incremental) {
                val variableToken = getVariable((value.left as VariableToken).name.word).getTyped()
                if (shortAssigmentPossible) {
                    variableToken.addAfter = "op ${getOperationName(value.operator.operator)} ${variableToken.value} ${variableToken.value} 1"
                    return variableToken
                } else {
                    val operationBuffer = TypedExpression(variableStack.requestBufferVariable(), variableToken.type, false)
                    writeInstruction("set ${operationBuffer.value} ${variableToken.value}")
                    writeInstruction("op ${getOperationName(value.operator.operator)} ${variableToken.value} ${variableToken.value} 1")

                    return operationBuffer
                }
            } else if (value.operator.operator == "!"){
                return transpileFlatOperation(
                    TypedExpression("1", Types.BOOL, false),
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
        checkType(left, right)

        if (dependedVariable.jump) {
            if (getReturnType(value) !in listOf(Types.BOOL, Types.ANY))
                throw TranspileException("Invalid condition type ${getReturnType(value).name}")

            // Although it is not really a complete expression, it is assumed tht it always comes with jump instruction
            return TypedExpression("${getOperationName(operation)} ${left.value} ${right.value}", Types.BOOL, true)
        } else if (dependedVariable.variable != null) {
            checkType(dependedVariable.variable, TypedExpression("", getReturnType(value), false))

            return TypedExpression(
                "op ${getOperationName(operation)} ${dependedVariable.variable.value} ${left.value} ${right.value}",
                getReturnType(OperationToken(OperatorToken(operation), left.toToken(), right.toToken())),
                true,
                left.addAfter ?: right.addAfter
            )
        } else {
            val result = transpileOperationChain(OperationToken(value.operator, left.toToken(), right.toToken()),
                    buffer, DependedValue(buffer))
            if (result.complete) {
                writeInstruction(result)
                return buffer
            } else {   // For '=' operator
                return result
            }
        }
    }

    private fun checkType(first: TypedExpression, second: TypedExpression) {
        checkType(first.type, second.type)
    }

    private fun checkType(first: Types, second: Types) {
        if (!first.compatible(second))
            throw TranspileException("Type '${first.name.lowercase()}' is not compatible with type '${second.name.lowercase()}'")
    }

    private fun checkVariable(name: String, ifFound: (VariableStack.VariableData) -> Unit = { }) {
        ifFound(getVariable(name))

    }

    private fun getVariable(name: String) =
        variableStack.stack.find { it.name == name } ?: throw TranspileException("Variable '$name' is not defined")

    private fun transpileInitialization(token: InitializationToken) {
        if(variableStack.stack.any{ it.name == token.name.word })
            throw TranspileException("Variable '${token.name.word}' already exists in this scope")

        variableStack.stack.add(VariableStack.VariableData(token.name.word, token.type.word,
            variableStack.blockStack.lastOrNull()?.block, token.value != null))

        if (token.value != null && token.value !is BuildingToken) {
            val value = transpileExpressionWithReference(token.value!!, DependedValue(getVariable(token.name.word).getTyped()))

            checkType(variableStack.stack.last().getTyped(), value)

            if (value.used) return
            if (value.complete)
                writeInstruction(value)
            else
                writeInstruction("set ${token.name.word} #", value)
        }
    }

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

    private fun getReturnType(token: OperationToken): Types {
        return when(token.operator.operator){
            "=" -> (token.left as? VariableToken)?.name?.word?.let { getVariable(it).getTyped().type } ?: Types.ANY
            "@" -> Types.BUILDING
            "+", "-", "*", "/", "%", "+=", "-=", "*=", "/=", "++", "--", ">>", "<<" -> Types.NUMBER
            ">", "<", ">=", "<=", "==", "===", "!=", "!", "&&", "||", "&", "|" -> Types.BOOL
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

        if (expression.addAfter != null)
            mainStream.add(Instruction(expression.addAfter!!, nextId()))
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
            if (element.addAfter != null)
                mainStream.add(Instruction(element.addAfter!!, nextId()))
    }

    data class TypedToken(val value: TypedExpression) : ExpressionToken, NamedToken {
        override fun getName(): String {
            return value.value
        }
    }

    private fun NamedToken.getTyped(ignoreInitialization: Boolean = false): TypedExpression{
        return if (this is TypedToken) this.value
               else getVariable(this.getName()).getTyped(ignoreInitialization)
    }

    private fun TypedExpression.toToken(): TypedToken {
        return TypedToken(this)
    }
}