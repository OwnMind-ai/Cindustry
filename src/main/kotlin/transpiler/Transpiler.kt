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

            val variable = ((if (token.left is VariableToken) token.left else token.right) as VariableToken).name.word
            val value = if (increment) TypedExpression("1", Types.NUMBER, false)
                        else transpileExpressionWithReference(token.right,
                            if (assigmentComposition) null else getVariable(variable).getTyped())

            checkVariable(variable) { checkType(it.getTyped(), value) }

            if (value.complete){
                mainStream.add(Instruction(value.value, nextId()))
            } else if (token.operator.operator == "=") {
                mainStream.add(Instruction("set ${(token.left as VariableToken).name.word} $value", nextId()))
            } else {
                if (!value.compatible(TypedExpression("", Types.NUMBER, false)))
                    throw TranspileException("Unable to perform string concatenation in this version")

                mainStream.add(Instruction("op ${getOperationName(token.operator.operator)} $variable $variable ${value.value}", nextId()))
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

    private fun transpileOperationChain(value: OperationToken, buffer: TypedExpression, dependedVariable: TypedExpression?): TypedExpression {
        if (value.isFlat()){
            if (value.operator.operator in OperatorToken.ASSIGMENT_OPERATION) throw IllegalArgumentException()  //TODO

            val operation = getOperationName(value.operator.operator)
            val left = transpileExpressionWithReference(value.left)
            val right = transpileExpressionWithReference(value.right)

            return transpileFlatOperation(left, right, dependedVariable, operation, value, buffer)
        } else {
            val propagateLeft = value.left is OperationToken

            val left: TypedExpression = if (!propagateLeft) transpileExpressionWithReference(value.left)
                else transpileOperationChain(value.left as OperationToken, buffer, dependedVariable)

            val right: TypedExpression = if (value.right !is OperationToken) transpileExpressionWithReference(value.right)
                else transpileOperationChain(value.right as OperationToken, if(propagateLeft) createBuffer(value.right as OperationToken) else buffer, dependedVariable)

            return transpileFlatOperation(
                left, right, dependedVariable,
                getOperationName(value.operator.operator),
                OperationToken(value.operator, left.toToken(), right.toToken()),
                buffer
            )
        }
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
                true
            )
        } else {
            val result = transpileOperationChain(value, buffer, buffer)
            mainStream.add(Instruction(result.value, nextId()))

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
            mainStream.add(Instruction(value.value, nextId()))
        else
            mainStream.add(Instruction("set ${token.name.word} ${value.value}", nextId()))
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

    data class TypedToken(val value: TypedExpression) : ExpressionToken

    private fun TypedExpression.toToken(): TypedToken {
        return TypedToken(this)
    }
}

