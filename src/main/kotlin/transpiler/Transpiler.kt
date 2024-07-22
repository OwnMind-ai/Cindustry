package org.cindustry.transpiler

import org.cindustry.parser.*

class Transpiler(private val fileToken: FileToken) {

    private val mainStream: MutableList<Instruction> = ArrayList()
    private val variableStack: VariableStack = VariableStack()
    private var counter = 0

    fun transpile(): String{
        val main: FunctionDeclarationToken = fileToken.functions.find { it.name.word == "main" }
                ?: throw TranspileException("No main function")

        variableStack.blockStack.add(main.codeBlock)
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
            if ((token.operator.operator !in OperatorToken.ASSIGMENT_OPERATION || token.left !is VariableToken) && !increment) return

            val variable: VariableToken = (if (token.left is VariableToken) token.left else token.right) as VariableToken
            val value = if (increment) TypedExpression("1", Types.NUMBER) else transpileExpressionWithReference(token.right)

            checkVariable(variable.name.word) { checkType(it.getTyped(), value) }

            if (token.operator.operator == "=") {
                mainStream.add(Instruction("set ${(token.left as VariableToken).name.word} $value", nextId()))
            } else {
                if (!value.compatible(TypedExpression("", Types.NUMBER)))
                    throw TranspileException("Unable to perform string concatenation in this version")

                mainStream.add(Instruction("op ${getOperationName(token.operator.operator)} ${variable.name.word} ${variable.name.word} ${value.value}", nextId()))
            }
        } else
            throw IllegalArgumentException()
    }

    private fun checkType(first: TypedExpression, second: TypedExpression) {
        if (!first.compatible(second))
            throw TranspileException("Type '${first.type.name.lowercase()}' is not compatible with type '${second.type.name.lowercase()}'")
    }

    private fun checkVariable(name: String, ifFound: (VariableStack.VariableData) -> Unit = { }) {
        ifFound(variableStack.stack.find { it.name == name } ?: throw TranspileException("Variable '$name' is not defined"))

    }

    private fun transpileInitialization(token: InitializationToken) {
        val value = transpileExpressionWithReference(token.value)
        variableStack.stack.add(VariableStack.VariableData(token.name.word, token.type.word, variableStack.blockStack.last()))

        checkType(variableStack.stack.last().getTyped(), value)

        mainStream.add(Instruction("set ${token.name.word} ${value.value}", nextId()))
    }

    private fun transpileExpressionWithReference(value: ExpressionToken): TypedExpression {
        if (value is NumberToken) return TypedExpression(value.number, Types.NUMBER)
        if (value is StringToken) return TypedExpression("\"${value.content}\"", Types.STRING)
        if (value is VariableToken && value.name.word == "null") return TypedExpression("null", Types.ANY)

        throw IllegalArgumentException()
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

            else -> throw IllegalArgumentException(value)
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
}