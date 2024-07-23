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
        val TYPES = listOf("number", "string", "bool", "building", "any", "void")  //TODO add int and float
    }

    fun assertTypeKeyword() {
        assert { TYPES.contains(word) }
    }

    fun assertNotKeyword() {
        assert{ !TYPES.contains(word) }
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
        val ASSIGMENT_OPERATION: List<String> = listOf("=", "+=", "-=", "*=", "/=")
        val LOGIC_OPERATION: List<String> = listOf("||", "|", "&&", "&", ">", "<", "==", "===", ">=", "<=")
    }

    fun getPriority(): Int{
        return when(operator){
            "=", "+=", "-=", "*=", "/=" -> 1
            "||", "|" -> 3
            "&&", "&" -> 4
            "<", ">", "<=", ">=", "==", "!=", "===" -> 7
            "+", "-" -> 10
            "*", "/", "%", ">>", "<<" -> 20
            else -> Int.MIN_VALUE
        }
    }

    fun assertUnary() {
        assert{ listOf("-", "+", "@", "!", "++", "--").contains(operator) }
    }
}

data class NumberToken(
    var number: String
) : ExpressionToken

data class BooleanToken(
    var value: Boolean
) : ExpressionToken

// PARSER LEVEL

interface ExecutableToken : Token
interface ExpressionToken : ExecutableToken
interface BlockToken

data class CodeBlockToken(
    var statements: List<ExecutableToken>
) : Token

data class InitializationToken(
    var type: WordToken,
    var name: WordToken,
    var value: ExpressionToken
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

data class CallToken(
    var name: WordToken,
    var parameters: List<ExpressionToken>
) : ExpressionToken

data class VariableToken(
    var name: WordToken
) : ExpressionToken

data class IfToken(
    var condition: ExpressionToken,
    var doBlock: CodeBlockToken,
    var elseBlock: CodeBlockToken?
) : ExecutableToken, BlockToken

data class WhileToken(
    var condition: ExpressionToken,
    var doBlock: CodeBlockToken
) : ExecutableToken, BlockToken

data class ForToken(
    var initialization: ExecutableToken,
    var condition: ExpressionToken,
    var after: ExecutableToken,
    var doBlock: CodeBlockToken
) : ExecutableToken, BlockToken

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
    var name: WordToken
) : Token

data class FunctionDeclarationToken(
    var name: WordToken,
    var returnType: WordToken,
    var parameters: List<ParameterToken>,
    var codeBlock: CodeBlockToken
) : Token

data class FileToken(
    var functions: List<FunctionDeclarationToken>
) : Token {
    override fun toString(): String {
        return "FILE($functions)"
    }
}