package org.cindustry.parser

interface Token{
    fun assert(message: String = "Invalid token", predicate: (Token) -> Boolean){
        if (!predicate.invoke(this))
            throw ParserException(message)
    }
}

// LEXER LEVEL

data class WordToken(
    var word: String
) : Token {
    companion object{
        val TYPES = listOf("number", "string", "content", "bool", "building", "any", "void")
        val KEYWORDS = listOf("use", "if", "while", "for", "return", "break", "continue", "global", "const", "as")
    }

    fun assertTypeKeyword() {
        assert { TYPES.contains(word) }
    }

    fun assertNotKeyword() {
        assert{ !TYPES.contains(word) && !KEYWORDS.contains(word) }
    }
}

data class StringToken(
    var content: String
) : ExpressionToken

data class PunctuationToken(
    var character: String
) : Token

data class OperatorToken(
    var operator: String
) : Token {
    companion object{
        val ASSIGMENT_OPERATION: List<String> = listOf("=", "+=", "-=", "*=", "/=", "%=")
        val ASSIGMENT_INCREMENT_OPERATION: MutableList<String> = mutableListOf("++", "--")
        val LOGIC_OPERATION: List<String> = listOf("||", "|", "&&", "&", ">", "<", "==", "===", ">=", "<=")

        init {
            ASSIGMENT_INCREMENT_OPERATION.addAll(ASSIGMENT_OPERATION)
        }
    }

    fun getPriority(): Int{
        return when(operator){
            "=", "+=", "-=", "*=", "/=", "%=" -> 1
            "||", "|" -> 3
            "&&", "&" -> 4
            "<", ">", "<=", ">=", "==", "!=", "===" -> 7
            "+", "-" -> 10
            "*", "/", "%", ">>", "<<" -> 20
            "++", "--" -> 30
            "@" -> 50
            else -> Int.MIN_VALUE
        }
    }

    fun assertUnary() {
        assert{ listOf("-", "+", "@", "!", "++", "--").contains(operator) }
    }

    fun primary(): OperatorToken {
        return OperatorToken(when(operator){
            "+=", "-=", "*=", "/=", "%=" -> operator.replace("=", "")
            "++" -> "+"
            "--" -> "-"
            else -> operator
        })
    }
}

data class NumberToken(
    var number: String
) : ExpressionToken

data class BooleanToken(
    var value: Boolean
) : ExpressionToken

data class BuildingToken(
    var name: String
) : ExpressionToken

// PARSER LEVEL

interface ExecutableToken : Token
interface ExpressionToken : ExecutableToken
interface BlockToken{
    fun getAllExecutableTokens(): List<ExecutableToken>
}

interface AssignableToken

data class CodeBlockToken(
    var statements: List<ExecutableToken>
) : Token

data class InitializationToken(
    var type: WordToken,
    var name: WordToken,
    var value: ExpressionToken?
) : ExecutableToken

data class OperationToken(
    var operator: OperatorToken,
    var left: ExpressionToken,
    var right: ExpressionToken,
) : ExpressionToken {
    class EmptySide : ExpressionToken {
        override fun toString(): String {
            return "EMPTY SIDE"
        }
    }

    fun isFlat(): Boolean{
        return left !is OperationToken && left !is CallToken && right !is OperationToken && right !is CallToken
    }
}

data class ArrayAccessToken(
    var array: ExpressionToken,
    var index: ExpressionToken
) : ExpressionToken, AssignableToken

data class FieldAccessToken(
    var from: ExpressionToken,
    var field: WordToken
) : ExpressionToken, AssignableToken

data class CallToken(
    var name: WordToken,
    var parameters: List<ExpressionToken>
) : ExpressionToken

interface NamedToken : AssignableToken{
    fun getName(): String
}

data class VariableToken(
    var name: WordToken
) : ExpressionToken, NamedToken {
    override fun getName(): String {
        return name.word
    }
}

data class IfToken(
    var condition: ExpressionToken,
    var doBlock: CodeBlockToken,
    var elseBlock: CodeBlockToken?
) : ExecutableToken, BlockToken {
    override fun getAllExecutableTokens(): List<ExecutableToken> {
        return doBlock.statements + (elseBlock?.statements ?: listOf())
    }
}

data class WhileToken(
    var condition: ExpressionToken,
    var doBlock: CodeBlockToken,
    var isDoWhile: Boolean
) : ExecutableToken, BlockToken {
    override fun getAllExecutableTokens(): List<ExecutableToken> {
        return doBlock.statements + condition
    }
}

data class ForToken(
    var initialization: ExecutableToken?,
    var condition: ExpressionToken?,
    var after: ExecutableToken?,
    var doBlock: CodeBlockToken
) : ExecutableToken, BlockToken{
    override fun getAllExecutableTokens(): List<ExecutableToken> {
        val result = ArrayList(doBlock.statements)
        if (initialization != null) result.add(initialization)
        if (condition != null) result.add(condition)
        if (after != null) result.add(after)

        return result
    }

}

data class ReturnToken(
    var type: WordToken,
    var value: ExpressionToken?
) : ExecutableToken {
    companion object{
        const val RETURN = "return"
        const val BREAK = "break"
        const val CONTINUE = "continue"
    }
}

data class ParameterToken(
    var type: WordToken,
    var name: WordToken,
    var const: Boolean? = null
) : Token

data class FunctionDeclarationToken(
    var name: WordToken,
    var returnType: WordToken,
    var parameters: List<ParameterToken>,
    var codeBlock: CodeBlockToken
) : Token, BlockToken {
    override fun getAllExecutableTokens(): List<ExecutableToken> {
        return codeBlock.statements
    }
}

data class FileToken(
    var globalVariables: List<InitializationToken>,
    var functions: List<FunctionDeclarationToken>
) : Token {
    override fun toString(): String {
        return "FILE($functions)"
    }
}