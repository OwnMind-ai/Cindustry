package org.cindustry.parser

import java.util.*

class Parser(private val lexer: Lexer) {
    fun parse(): FileToken{
        val globalVariables = ArrayList<InitializationToken>()
        val functions = ArrayList<FunctionDeclarationToken>()
        while (!lexer.ended())
            parseOnFileLevel(globalVariables, functions)

        return FileToken(globalVariables, functions)
    }

    private fun parseOnFileLevel(globalVariables: ArrayList<InitializationToken>, functions: ArrayList<FunctionDeclarationToken>) {
        if ((lexer.peek() is WordToken) && (lexer.peek() as WordToken).word == "use") {
            lexer.next()
            lexer.strictNext<OperatorToken>().assert { (it as OperatorToken).operator == "@" }
            val name = lexer.strictNext<WordToken>()
            name.assertNotKeyword()

            lexer.strictNext<PunctuationToken>().assert { (it as PunctuationToken).character == ";" }

            globalVariables.add(InitializationToken(WordToken("building"), name, BuildingToken(name.word)))
        } else if ((lexer.peek() is WordToken) && (lexer.peek() as WordToken).word == "global") {
            lexer.next()
            globalVariables.add(parseInitialization())
            lexer.strictNext<PunctuationToken>().assert { (it as PunctuationToken).character == ";" }
        } else {
            functions.add(parseFunction())
        }
    }

    private fun parseFunction(): FunctionDeclarationToken {
        val returnType = lexer.strictNext<WordToken>()
        val name = lexer.strictNext<WordToken>()
        val parameters = parseParameters()
        val code = parseCodeBlock()

        evaluateParametersMutability(parameters, code)

        return FunctionDeclarationToken(name, returnType, parameters, code)
    }

    private fun parseBody(): CodeBlockToken{
        return if ((lexer.peek() is PunctuationToken) && (lexer.peek() as PunctuationToken).character == "{")
            parseCodeBlock()
        else {
            val line = parseExpression()

            if (line !is BlockToken)
                lexer.strictNext<PunctuationToken>().assert { (it as PunctuationToken).character == ";" }

            CodeBlockToken(mutableListOf(line))
        }
    }

    private fun parseCodeBlock(): CodeBlockToken {
        val token = CodeBlockToken(
            delimiter("{", ";", "}", this::parseExpression,
                separatorIgnorePredicate = { it is BlockToken && (it !is WhileToken || !it.isDoWhile) })
        )

        if (token.statements.withIndex().any { it.value is ReturnToken && it.index < token.statements.size - 1})
            throw ParserException("Unreachable statement")

        return token
    }

    private fun parseExpression(): ExecutableToken {
        val token = buildExpressionTree(tryParseExpression(), 0)
        return if (token is OperationToken) OperationOptimizer.optimize(token) else token
    }

    private fun buildExpressionTree(token: ExecutableToken, currentPriority: Int): ExecutableToken {
        val next = lexer.peek()

        if (token is ExpressionToken && next is OperatorToken) {
            if ((next.operator == "++" || next.operator == "--") && token is AssignableToken)
                return buildExpressionTree(OperationToken(lexer.strictNext(), token, OperationToken.EmptySide()), 0)

            if (next.getPriority() >= currentPriority) {
                val operator = lexer.strictNext<OperatorToken>()

                val right = buildExpressionTree(tryParseExpression(), operator.getPriority())
                if (right !is ExpressionToken)
                    throw ParserException("Invalid token, expression was expected")

                return buildExpressionTree(OperationToken(operator, token, right), currentPriority)
            }
        } else if (token is ExpressionToken && (next as? PunctuationToken)?.character == "."){
            lexer.strictNext<PunctuationToken>()
            val field = lexer.strictNext<WordToken>()
            field.assertNotKeyword()

            if (token !is VariableToken && token !is CallToken && token !is FieldAccessToken && token !is BuildingToken)
                throw ParserException("Unable to access field '${field.word}'")

            return buildExpressionTree(FieldAccessToken(token, field), 0)
        } else if (token is ExpressionToken && (lexer.peek() as? PunctuationToken)?.character == "[")
            return buildExpressionTree(parseArrayAccess(token), 0)

        return token
    }

    private fun tryParseExpression(): ExecutableToken {
        val current = lexer.peek()

        if (current is OperatorToken){
            current.assertUnary()

            lexer.next()
            val right = if(current.operator in listOf("@", "++", "--")){
                val peeked = lexer.peek()
                if (peeked !is WordToken) throw ParserException("Invalid operator use")

                val result = parseWordToken(peeked)
                if (result !is VariableToken) throw ParserException("Invalid operator use")

                if (current.operator == "@")
                    return BuildingToken(result.getName())
                else
                    result
            } else
                parseExpression()

            if (listOf("@", "++", "--").contains(current.operator) && right !is VariableToken)
                throw ParserException("Invalid operator use")

            if (right !is ExpressionToken)
                throw ParserException("Invalid token, expression was expected")

            return OperationToken(current, if(current.operator in listOf("-", "+")) NumberToken("0") else OperationToken.EmptySide(), right)
        }

        if (current is WordToken) {
            return parseWordToken(current)
        }

        if (current is PunctuationToken && current.character == "("){
            lexer.next()
            val expression = parseExpression()
            lexer.strictNext<PunctuationToken>().assert { (it as PunctuationToken).character == ")" }

            return expression
        }

        if (current is ExecutableToken)
            return lexer.next() as ExecutableToken

        throw ParserException("Unexpected token")
    }

    private fun parseWordToken(current: WordToken): ExecutableToken {
        if (WordToken.TYPES.contains(current.word) || current.word == "const")
            return this.parseInitialization()

        if (current.word == "true" || current.word == "false")
            return BooleanToken(lexer.strictNext<WordToken>().word == "true")

        if (current.word in listOf(ReturnToken.RETURN, ReturnToken.BREAK, ReturnToken.CONTINUE)) {
            return ReturnToken(
                lexer.strictNext(),
                if (lexer.peek() is PunctuationToken) {
                    null
                } else {
                    val expression = parseExpression()
                    if (expression !is ExpressionToken)
                        throw ParserException("Invalid token, expression was expected")

                    expression
                }
            )
        }

        val token = lexer.strictNext<WordToken>()

        if (token.word == "for") return parseFor()
        if (token.word == "while") return parseWhile()
        if (token.word == "do") return parseDoWhile()
        if (token.word == "if") return parseIf()

        if (lexer.peek() is PunctuationToken && (lexer.peek() as PunctuationToken).character == "(")
            return this.parseCall(token)

        return VariableToken(token)
    }

    private fun parseArrayAccess(token: ExpressionToken): ArrayAccessToken {
        lexer.strictNext<PunctuationToken>().assert { (it as PunctuationToken).character == "[" }
        val index = this.parseExpression() as? ExpressionToken ?: throw ParserException("Invalid array index")
        lexer.strictNext<PunctuationToken>().assert { (it as PunctuationToken).character == "]" }

        return ArrayAccessToken(token, index)
    }

    private fun parseIf(): IfToken {
        lexer.strictNext<PunctuationToken>().assert { it is PunctuationToken && it.character == "(" }
        val condition = parseExpression()
        lexer.strictNext<PunctuationToken>().assert { it is PunctuationToken && it.character == ")" }

        val doBlock = parseBody()
        var elseBlock: CodeBlockToken? = null

        if (lexer.peek() is WordToken && (lexer.peek() as WordToken).word == "else") {
            lexer.next()
            elseBlock = parseBody()
        }

        if (condition !is ExpressionToken)
            throw ParserException("Invalid token, expression was expected")

        return IfToken(condition, doBlock, elseBlock)
    }

    private fun parseWhile(): WhileToken {
        lexer.strictNext<PunctuationToken>().assert { it is PunctuationToken && it.character == "(" }
        val condition = parseExpression()
        lexer.strictNext<PunctuationToken>().assert { it is PunctuationToken && it.character == ")" }

        if (condition !is ExpressionToken)
            throw ParserException("Invalid token, expression was expected")

        return WhileToken(condition, parseBody(), false)
    }

    private fun parseDoWhile(): WhileToken {
        val doBlock = parseBody()

        lexer.strictNext<WordToken>().assert { it is WordToken && it.word == "while" }
        lexer.strictNext<PunctuationToken>().assert { it is PunctuationToken && it.character == "(" }
        val condition = parseExpression()
        lexer.strictNext<PunctuationToken>().assert { it is PunctuationToken && it.character == ")" }

        if (condition !is ExpressionToken)
            throw ParserException("Invalid token, expression was expected")

        return WhileToken(condition, doBlock, true)
    }

    private fun parseFor(): ForToken {
        val header = delimiter("(", ";", ")", {
            if (lexer.peek() is PunctuationToken)
                null
            else
                parseExpression()
        })
        if (header.size !in 2..3) throw ParserException("Invalid for statement")

        if (header[1] != null && header[1] !is ExpressionToken)
            throw ParserException("Invalid token, expression was expected")

        return ForToken(header[0], header[1] as ExpressionToken?, header.getOrNull(2), parseBody())
    }

    private fun parseCall(token: WordToken): ExecutableToken {
        val parameters = delimiter("(", ",", ")", ::parseExpression)

        if (parameters.any { it !is ExpressionToken })
            throw ParserException("Invalid token, expression was expected")

        return CallToken(token, parameters.map { it as ExpressionToken })
    }

    private fun parseInitialization(): InitializationToken {
        val const = (lexer.peek() as? WordToken)?.word == "const"
        if(const) lexer.next()

        val type = lexer.strictNext<WordToken>()
        val name = lexer.strictNext<WordToken>()
        var value: ExecutableToken? = null
        name.assertNotKeyword()

        if (lexer.peek() is OperatorToken && (lexer.peek() as OperatorToken).operator == "=") {
            lexer.next()
            value = this.parseExpression()
            if (value !is ExpressionToken)
                throw ParserException("Invalid token, expression was expected")
        }

        return InitializationToken(type, name, value as ExpressionToken?, const)
    }

    private fun parseParameters(): List<ParameterToken> {
        return delimiter("(", ",", ")", this::parseParameter)
    }

    private fun parseParameter(): ParameterToken {
        val const = (lexer.peek() as? WordToken)?.word == "const"
        if(const) lexer.next()

        val type = lexer.strictNext<WordToken>()
        type.assertTypeKeyword()

        val name = lexer.strictNext<WordToken>()

        return ParameterToken(type, name, if(const) true else null)
    }

    private fun evaluateParametersMutability(parameters: List<ParameterToken>, code: CodeBlockToken) {
        code.statements.forEach { s ->
            executeDeep(s, ::nextChildToken) {
                if (it is OperationToken && it.operator.operator in OperatorToken.ASSIGMENT_INCREMENT_OPERATION){
                    val param = if (it.left is VariableToken)
                            parameters.find { p -> p.name.word == (it.left as VariableToken).getName() }
                        else if (it.right is VariableToken)
                            parameters.find { p -> p.name.word == (it.right as VariableToken).getName() }
                        else
                            return@executeDeep

                    if (param?.const == true)
                        throw ParserException("Constant modified")

                    param?.const = false
                }
            }
        }

        parameters.filter { it.const == null }.forEach{ it.const = true }
    }

    private fun nextChildToken(t: Token) = when (t) {
        is OperationToken -> listOf(t.left, t.right)
        is CallToken -> t.parameters
        is ArrayAccessToken -> listOf(t.array, t.index)
        is FieldAccessToken -> listOf(t.from)
        is BlockToken -> t.getAllExecutableTokens()
        is CodeBlockToken -> t.statements
        is InitializationToken -> if (t.value != null) listOf(t.value!!) else listOf()
        is ReturnToken -> if (t.value != null) listOf(t.value!!) else listOf()
        else -> listOf()
    }

    private fun <T> executeDeep(token: T, next: (T) -> List<T>, execute: (T) -> Unit){
        execute.invoke(token)

        val list = next.invoke(token).filter(Objects::nonNull)
        if (list.isEmpty()) return

        list.forEach { executeDeep(it, next, execute) }
    }

    private fun <T: Token?> delimiter(start: String, separator: String, end: String, parser: () -> T, separatorIgnorePredicate: (T) -> Boolean = { false }): List<T>{
        val result: MutableList<T> = ArrayList()
        lexer.strictNext<PunctuationToken>().assert { it is PunctuationToken && it.character == start }

        var endSkipped = false
        while (lexer.peek() !is PunctuationToken || (lexer.peek() as PunctuationToken).character != end){
            val element = parser.invoke()
            result.add(element)

            if (separatorIgnorePredicate.invoke(element)) {
                if (lexer.peek() is PunctuationToken && (lexer.peek() as PunctuationToken).character == end) break
            } else {
                val next = lexer.strictNext<PunctuationToken>()
                next.assert { it is PunctuationToken && (it.character == separator || it.character == end) }

                if (next.character == end) {
                    endSkipped = true
                    break
                }
            }
        }

        if (!endSkipped && lexer.peek() is PunctuationToken && (lexer.peek() as PunctuationToken).character == end)
            lexer.next()

        return result
    }
}