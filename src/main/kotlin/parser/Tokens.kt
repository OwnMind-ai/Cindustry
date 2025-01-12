package org.cindustry.parser

import org.cindustry.exceptions.ParserException
import java.util.*

abstract class Token{
    var lineNumber: Int? = null
    var columnNumber: Int? = null
    var line: String? = null
    var tokenLength: Int? = null
    var file: String? = null

    open fun computeErrorData(){}
}

fun <T : Token> T.assert(message: String = "Invalid token", predicate: (T) -> Boolean){
    if (!predicate.invoke(this))
        throw ParserException(message, this)
}

fun <T : Token> T.loadData(stream: CharStream): T{
    file = stream.fileName
    line = stream.getLine()
    columnNumber = stream.columnNumber
    lineNumber = stream.lineNumber
    return this
}

fun <T : Token> T.loadData(other: Token): T{
    file = other.file
    line = other.line
    columnNumber = other.columnNumber
    lineNumber = other.lineNumber
    tokenLength = other.tokenLength
    return this
}

fun <T : Token> T.lineNumber(lineNumber: Int): T{
    this.lineNumber = lineNumber
    return this
}

fun <T : Token> T.columnNumber(columnNumber: Int): T{
    this.columnNumber = columnNumber
    return this
}

fun <T : Token> T.line(line: String): T{
    this.line = line
    return this
}

fun <T : Token> T.tokenLength(length: Int): T{
    this.tokenLength = length
    return this
}

fun <T : Token> T.file(name: String): T{
    this.file = name
    return this
}

// Used for exceptions
internal class DummyToken() : Token() {
    constructor(stream: CharStream) : this() {
        loadData(stream)
    }
}

// LEXER LEVEL

data class WordToken(
    var word: String
) : Token() {
    companion object{
        val TYPES = listOf("number", "string", "content", "bool", "building", "any", "void")
        val KEYWORDS = listOf("use", "if", "while", "for", "return", "break", "continue", "global", "const", "as", "import")
    }

    fun assertNotKeyword() {
        assert("Symbol must not be a keyword") { !TYPES.contains(word) && !KEYWORDS.contains(word) }
    }
}

data class StringToken(
    var content: String
) : ExpressionToken()

data class PunctuationToken(
    var character: String
) : Token()

data class OperatorToken(
    var operator: String
) : Token() {
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
            else -> Int.MIN_VALUE
        }
    }

    fun assertUnary() {
        assert{ listOf("-", "+", "!", "++", "--").contains(operator) }
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
) : ExpressionToken()

data class BooleanToken(
    var value: Boolean
) : ExpressionToken()

data class BuildingToken(
    var name: String
) : ExpressionToken()

// PARSER LEVEL

abstract class ExecutableToken : Token()
abstract class ExpressionToken : ExecutableToken()
interface BlockToken{
    fun getAllExecutableTokens(): List<ExecutableToken>
}

interface AssignableToken

data class CodeBlockToken(
    var statements: List<ExecutableToken>
) : Token()

data class InitializationToken(
    var type: WordToken,
    var name: WordToken,
    var value: ExpressionToken?,
    var const: Boolean = false
) : ExecutableToken() {
    override fun computeErrorData() {
        name.computeErrorData()
        file(name.file!!).lineNumber(name.lineNumber!!).line(name.line!!)
    }
}

data class OperationToken(
    var operator: OperatorToken,
    var left: ExpressionToken,
    var right: ExpressionToken,
) : ExpressionToken() {
    class EmptySide : ExpressionToken() {
        override fun toString(): String {
            return "EMPTY SIDE"
        }
    }

    fun isFlat(): Boolean{
        return left !is OperationToken && left !is CallToken && right !is OperationToken && right !is CallToken
    }

    override fun computeErrorData() {
        right.computeErrorData()
        operator.computeErrorData()
        left.computeErrorData()

        if (right is EmptySide)
            loadData(left).tokenLength(operator.tokenLength!! + operator.columnNumber!! - left.columnNumber!!)
        else if(left is EmptySide)
            loadData(operator).tokenLength(right.tokenLength!! + right.columnNumber!! - operator.columnNumber!!)
        else
            loadData(left).tokenLength(right.tokenLength!! + right.columnNumber!! - left.columnNumber!!)
    }
}

data class ArrayAccessToken(
    var array: ExpressionToken,
    var index: ExpressionToken
) : ExpressionToken(), AssignableToken{
    override fun computeErrorData() {
        array.computeErrorData()
        index.computeErrorData()
        loadData(array).tokenLength(index.tokenLength!! + index.columnNumber!! - array.columnNumber!!)
    }
}

data class FieldAccessToken(
    var from: ExpressionToken,
    var field: WordToken
) : ExpressionToken(), AssignableToken{
    override fun computeErrorData() {
        field.computeErrorData()
        from.computeErrorData()
        loadData(from).tokenLength(field.tokenLength!! + field.columnNumber!! - from.columnNumber!!)
    }
}

data class CallToken(
    var name: WordToken,
    var parameters: List<ExpressionToken>
) : ExpressionToken(){
    override fun computeErrorData() {
        name.computeErrorData()
        loadData(name).tokenLength(name.tokenLength!! + 2)
        parameters.lastOrNull()?.let {
            it.computeErrorData()
            tokenLength(it.columnNumber!! - name.columnNumber!! + it.tokenLength!! + 1)
        }
    }
}

interface NamedToken : AssignableToken{
    fun getName(): String
}

data class VariableToken(
    var name: WordToken
) : ExpressionToken(), NamedToken {
    override fun getName(): String {
        return name.word
    }

    override fun computeErrorData() {
        name.computeErrorData()
        loadData(name)
    }
}

data class IfToken(
    var condition: ExpressionToken,
    var doBlock: CodeBlockToken,
    var elseBlock: CodeBlockToken?
) : ExecutableToken(), BlockToken {
    override fun getAllExecutableTokens(): List<ExecutableToken> {
        return doBlock.statements + (elseBlock?.statements ?: listOf())
    }
}

data class WhileToken(
    var condition: ExpressionToken,
    var doBlock: CodeBlockToken,
    var isDoWhile: Boolean
) : ExecutableToken(), BlockToken {
    override fun getAllExecutableTokens(): List<ExecutableToken> {
        return doBlock.statements + condition
    }
}

data class ForToken(
    var initialization: ExecutableToken?,
    var condition: ExpressionToken?,
    var after: ExecutableToken?,
    var doBlock: CodeBlockToken
) : ExecutableToken(), BlockToken{
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
) : ExecutableToken() {
    companion object{
        const val RETURN = "return"
        const val BREAK = "break"
        const val CONTINUE = "continue"
    }

    override fun computeErrorData() {
        type.computeErrorData()
        loadData(type)
        if (value != null) {
            value?.computeErrorData()
            tokenLength(value?.tokenLength!! + value?.columnNumber!! - type.columnNumber!!)
        }
    }
}

data class ParameterToken(
    var type: WordToken,
    var name: WordToken,
    var const: Boolean? = null
) : Token() {
    override fun computeErrorData() {
        type.computeErrorData()
        name.computeErrorData()
        loadData(type).tokenLength(name.tokenLength!! + name.columnNumber!! - type.columnNumber!!)
    }
}

data class FunctionDeclarationToken(
    var name: WordToken,
    var returnType: WordToken,
    var parameters: List<ParameterToken>,
    var codeBlock: CodeBlockToken
) : Token(), BlockToken {
    override fun getAllExecutableTokens(): List<ExecutableToken> {
        return codeBlock.statements
    }
}

data class FileToken(
    var name: String,
    var imports: MutableList<ImportToken>,
    var globalVariables: MutableList<InitializationToken>,
    var enums: MutableList<EnumToken>,
    var functions: MutableList<FunctionDeclarationToken>
) : Token() {
    override fun toString(): String {
        return "FILE($functions)"
    }
}

data class ImportToken (
    var path: List<Token>
) : Token() {
    override fun computeErrorData() {
        path.first().computeErrorData()
        file(path.first().file!!).lineNumber(path.first().lineNumber!!).line(path.first().line!!)
    }
}

data class EnumToken (
    var name: WordToken,
    var values: List<WordToken>
) : Token() {
    override fun computeErrorData() {
        name.computeErrorData()
        file(name.file!!).lineNumber(name.lineNumber!!).line(name.line!!)
    }
}

fun nextChildToken(t: Token) = when (t) {
    is OperationToken -> listOf(t.left, t.right)
    is CallToken -> t.parameters
    is ArrayAccessToken -> listOf(t.array, t.index)
    is FieldAccessToken -> listOf(t.from)
    is BlockToken -> t.getAllExecutableTokens()
    is CodeBlockToken -> t.statements
    is InitializationToken -> if (t.value != null) listOf(t.value!!) else listOf()
    is ReturnToken -> if (t.value != null) listOf(t.value!!) else listOf()
    is FileToken -> t.functions
    else -> listOf()
}

fun <T> executeDeep(token: T, next: (T) -> List<T>, execute: (T) -> Unit){
    execute.invoke(token)

    val list = next.invoke(token).filter(Objects::nonNull)
    if (list.isEmpty()) return

    list.forEach { executeDeep(it, next, execute) }
}
