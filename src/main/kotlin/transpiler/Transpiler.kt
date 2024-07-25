package org.cindustry.transpiler

import org.cindustry.parser.*

class Transpiler(private val fileToken: FileToken) {

    private val mainStream: MutableList<Instruction> = ArrayList()
    private val variableStack: VariableStack = VariableStack()
    private var counter = 0

    fun transpile(): String{
        val main: FunctionDeclarationToken = fileToken.functions.find { it.name.word == "main" }
                ?: throw TranspileException("No main function")

        variableStack.add(main.codeBlock)
        main.codeBlock.statements.forEach(this::transpileExecutableToken)

        return mainStream.joinToString("\n") { it.getCode(::getInstructionIndex) }
    }

    private fun getInstructionIndex(id: Int): Int {
        for (i in 0..mainStream.size)
            if (mainStream[i].id == id)
                return i

        throw IllegalArgumentException()
    }

    private fun transpileExecutableToken(token: ExecutableToken) {
        if (token is InitializationToken)
            transpileInitialization(token)
        else if (token is ExpressionToken)
            transpileExpression(token)
    }

    private fun transpileExpression(token: ExpressionToken) {
        if (token is NumberToken && token is StringToken) return

        if (token is OperationToken){
            // We don't care if it is ++x or x++, because we don't use return value
            val increment = token.operator.operator in listOf("++", "--")
            val assigmentComposition = token.operator.operator != "=" && token.operator.operator in OperatorToken.ASSIGMENT_OPERATION
            if ((token.operator.operator !in OperatorToken.ASSIGMENT_OPERATION || token.left !is VariableToken) && !increment) return

            // TypedToken for buffer operations
            val variableToken = (if (token.left is VariableToken || token.left is TypedToken) token.left else token.right)
            val variable = if (variableToken is VariableToken) variableToken.name.word else (variableToken as TypedToken).value.value
            val variableTyped = if (variableToken is VariableToken) getVariable(variable).getTyped() else (variableToken as TypedToken).value

            val value = if (increment) TypedExpression("1", Types.NUMBER, false)
                        else transpileExpressionWithReference(token.right, if (assigmentComposition) null else variableTyped)

            if (variableToken is VariableToken)
                checkVariable(variable) { checkType(it.getTyped(), value) }

            if (value.complete){
                writeInstruction(value)
            } else if (token.operator.operator == "=") {
                writeInstruction("set ${(token.left as VariableToken).name.word} #", value)
            } else {
                if (!value.compatible(TypedExpression("", Types.NUMBER, false)))
                    throw TranspileException("Unable to perform string concatenation in this version")

                writeInstruction("op ${getOperationName(token.operator.operator)} $variable $variable #", value)
            }
        } else
            throw IllegalArgumentException()
    }

    private fun transpileExpressionWithReference(value: ExpressionToken, dependedVariable: TypedExpression? = null): TypedExpression {
        if (value is TypedToken) return value.value
        if (value is NumberToken) return TypedExpression(value.number, Types.NUMBER, false)
        if (value is StringToken) return TypedExpression("\"${value.content}\"", Types.STRING, false)
        if (value is VariableToken && value.name.word == "null") return TypedExpression("null", Types.ANY, false)

        if (value is VariableToken) {
            var variable: TypedExpression? = null
            checkVariable(value.name.word) { variable = it.getTyped() }
            return variable!!
        }

        if (value is OperationToken){
            return this.transpileOperationChain(value,
                createBuffer(value),  //TODO destroy
                dependedVariable)
        }

        throw IllegalArgumentException()
    }

    private fun createBuffer(value: OperationToken) =
        TypedExpression(variableStack.requestBufferVariable(), getReturnType(value), false)

    private fun transpileOperationChain(value: OperationToken, buffer: TypedExpression, dependedVariable: TypedExpression?, shortAssigmentPossible: Boolean = true): TypedExpression {
        if (value.isFlat()){
            val incremental = value.operator.operator in listOf("++", "--")

            if (value.operator.operator in OperatorToken.ASSIGMENT_OPERATION || incremental && value.right is VariableToken){
                val variableToken = if (incremental) value.right as VariableToken else value.left as VariableToken
                val valueToken = if (incremental) NumberToken("1") else value.right

                if (shortAssigmentPossible) {
                    transpileExpression(value)
                    return getVariable(variableToken.name.word).getTyped()
                } else {
                    //TODO add warning maybe?
                    // If user actually invoked this (shortAssigmentPossible is false) inefficient black magic,
                    // then they definitely wrote some shit-code.
                    // By the way, there are A LOT of room of improvement (specifically in this code block),
                    // however it is really rare case,
                    // so why bother considering how toy this language is
                    val operationBuffer = TypedExpression(variableStack.requestBufferVariable(), getVariable(variableToken.name.word).getTyped().type, false)
                    val valueTokenResult = this.transpileExpressionWithReference(valueToken)

                    writeInstruction("op ${getOperationName(value.operator.operator)} ${operationBuffer.value} ${variableToken.name.word} #", valueTokenResult)

//                    operationBuffer.addAfter = "set ${variableToken.name.word} ${operationBuffer.value}"
                    writeInstruction("set ${variableToken.name.word} ${operationBuffer.value}")

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
            }

            val operation = getOperationName(value.operator.operator)
            val left = transpileExpressionWithReference(value.left)
            val right = transpileExpressionWithReference(value.right)

            return transpileFlatOperation(left, right, dependedVariable, operation, value, buffer)
        } else {
            val propagateLeft = value.left is OperationToken
            val shortAssigment = isShortAssigmentPossible(value.left, value.right)

            val left: TypedExpression = if (!propagateLeft) transpileExpressionWithReference(value.left)
                else transpileOperationChain(value.left as OperationToken, buffer, null, shortAssigment)

            val right: TypedExpression = if (value.right !is OperationToken) transpileExpressionWithReference(value.right)
                else transpileOperationChain(
                    value.right as OperationToken,
                    if (propagateLeft) createBuffer(value.right as OperationToken) else buffer,
                    null, shortAssigment
                )

            return transpileFlatOperation(
                left, right, dependedVariable,
                getOperationName(value.operator.operator),
                OperationToken(value.operator, left.toToken(), right.toToken()),
                buffer
            )
        }
    }

    private fun isShortAssigmentPossible(left: ExpressionToken, right: ExpressionToken, finish: Boolean = false): Boolean {
        val leftGetter: (OperationToken) -> VariableToken = { (if (it.left is VariableToken) it.left else it.right) as VariableToken }
        val rightGetter: (ExpressionToken) -> VariableToken? = {
            if (it is VariableToken) it else
                if (it is OperationToken && it.operator.operator in OperatorToken.ASSIGMENT_INCREMENT_OPERATION) leftGetter(it)
                else null
        }
        return !(left is OperationToken && left.operator.operator in OperatorToken.ASSIGMENT_INCREMENT_OPERATION
                && leftGetter.invoke(left) == rightGetter.invoke(right))
                && !(!finish && isShortAssigmentPossible(right, left, true))
    }

    private fun transpileFlatOperation(left: TypedExpression, right: TypedExpression, dependedVariable: TypedExpression?,
        operation: String, value: OperationToken, buffer: TypedExpression
    ): TypedExpression {
        checkType(left, right)

        if (dependedVariable != null) {
            checkType(dependedVariable, left)

            //TODO some operators return bool (do same for buffer)
            return TypedExpression(
                "op ${getOperationName(operation)} ${dependedVariable.value} ${left.value} ${right.value}",
                left.type,
                true,
                left.addAfter ?: right.addAfter
            )
        } else {
            val result = transpileOperationChain(value, buffer, buffer)
            writeInstruction(result)

            return buffer
        }
    }

    private fun checkType(first: TypedExpression, second: TypedExpression) {
        if (!first.compatible(second))
            throw TranspileException("Type '${first.type.name.lowercase()}' is not compatible with type '${second.type.name.lowercase()}'")
    }

    private fun checkVariable(name: String, ifFound: (VariableStack.VariableData) -> Unit = { }) {
        ifFound(getVariable(name))

    }

    private fun getVariable(name: String) =
        variableStack.stack.find { it.name == name } ?: throw TranspileException("Variable '$name' is not defined")

    private fun transpileInitialization(token: InitializationToken) {
        variableStack.stack.add(VariableStack.VariableData(token.name.word, token.type.word, variableStack.blockStack.last().block))
        val value = transpileExpressionWithReference(token.value, getVariable(token.name.word).getTyped())

        checkType(variableStack.stack.last().getTyped(), value)

        if (value.complete)
            writeInstruction(value)
        else
            writeInstruction("set ${token.name.word} #", value)
    }

    private fun nextId(): Int{
        return counter++
    }

    private fun getOperationName(value: String): String{
        return when(value){
            "+", "+=", "++" -> "add"
            "-", "-=", "--" -> "sub"
            "*", "*=" -> "mul"
            "/", "/=" -> "div"
            "&&" -> "and"
            "||", "|" -> "or"
            "!=" -> "notEqual"
            "!" -> throw UnsupportedOperationException()

            else -> value
        }
    }

    private fun getReturnType(token: OperationToken): Types{
        return when(token.operator.operator){
            "=" -> (token.left as? VariableToken)?.name?.word?.let { getVariable(it).getTyped().type } ?: Types.ANY
            "@" -> Types.BUILDING
            "+", "-", "*", "/", "%", "+=", "-=", "*=", "/=", "++", "--", ">>", "<<" -> Types.NUMBER
            ">", "<", ">=", "<=", "==", "===", "!", "&&", "||", "&", "|" -> Types.BOOL
            else -> throw IllegalArgumentException()
        }
    }

    open class Instruction(
        private var code: String,
        val id: Int
    ) {
        open fun getCode(lineProvider: (Int) -> Int): String{
            return code
        }
    }

    data class JumpInstruction(var condition: String, val instructionId: Int) : Instruction("", instructionId){
        override fun getCode(lineProvider: (Int) -> Int): String {
            return "jump ${lineProvider.invoke(instructionId)} $condition"
        }
    }

    private fun writeInstruction(expression: TypedExpression){
        mainStream.add(Instruction(expression.value, nextId()))

        if (expression.addAfter != null)
            mainStream.add(Instruction(expression.addAfter!!, nextId()))
    }

    // Use # to indicate
    private fun writeInstruction(code: String, vararg elements: TypedExpression){
        val parts = code.split("#").toMutableList()

        if (elements.isNotEmpty())
            parts.forEachIndexed { i, _ ->
                if (i != parts.size - 1)
                    parts[i] = parts[i] + elements[i].value
            }

        mainStream.add(Instruction(parts.joinToString(""), nextId()))

        for (element in elements) {
            if (element.addAfter != null)
                mainStream.add(Instruction(element.addAfter!!, nextId()))
        }
    }

    data class TypedToken(val value: TypedExpression) : ExpressionToken

    private fun TypedExpression.toToken(): TypedToken {
        return TypedToken(this)
    }
}

