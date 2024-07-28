package org.cindustry.parser

class Parser(private val lexer: Lexer) {
    fun parse(): FileToken{
        val functions = ArrayList<FunctionDeclarationToken>()
        while (!lexer.ended())
            functions.add(parseFunction())

        return FileToken(functions)
    }

    private fun parseFunction(): FunctionDeclarationToken {
        val returnType = lexer.strictNext<WordToken>()
        val name = lexer.strictNext<WordToken>()
        val parameters = parseParameters()
        val code = parseCodeBlock()

        return FunctionDeclarationToken(name, returnType, parameters, code)
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
            if ((next.operator == "++" || next.operator == "--") && token is VariableToken)
                return buildExpressionTree(OperationToken(lexer.strictNext(), token, OperationToken.EmptySide()), 0)

            if (next.getPriority() >= currentPriority) {
                val operator = lexer.strictNext<OperatorToken>()

                val right = buildExpressionTree(tryParseExpression(), operator.getPriority())
                if (right !is ExpressionToken)
                    throw ParserException("Invalid token, expression was expected")

                return buildExpressionTree(OperationToken(operator, token, right), currentPriority)
            }
        }

        return token
    }

    private fun tryParseExpression(): ExecutableToken {
        val current = lexer.peek()

        if (current is OperatorToken){
            current.assertUnary()

            lexer.next()
            val right = if(current.operator in listOf("++", "--")){
                val peeked = lexer.peek()
                if (peeked !is WordToken) throw ParserException("Invalid operator use")

                val result = parseWordToken(peeked)
                if (result !is VariableToken) throw ParserException("Invalid operator use")

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
        if (WordToken.TYPES.contains(current.word))
            return this.parseInitialization()

        if (current.word == "true" || current.word == "false")
            return BooleanToken(lexer.strictNext<WordToken>().word == "true")

        if (listOf(ReturnToken.RETURN, ReturnToken.BREAK, ReturnToken.CONTINUE).contains(current.word)) {
            return ReturnToken(
                lexer.strictNext(),
                if (lexer.peek() is PunctuationToken) {
                    null
                } else {
                    val expression = parseExpression()
                    if (expression !is ExpressionToken)
                        throw ParserException("Invalid token, expression was expected")

                    return expression
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

    private fun parseIf(): IfToken {
        lexer.strictNext<PunctuationToken>().assert { it is PunctuationToken && it.character == "(" }
        val condition = parseExpression()
        lexer.strictNext<PunctuationToken>().assert { it is PunctuationToken && it.character == ")" }

        val doBlock = parseCodeBlock()
        var elseBlock: CodeBlockToken? = null

        if (lexer.peek() is WordToken && (lexer.peek() as WordToken).word == "else") {
            lexer.next()
            elseBlock = parseCodeBlock()
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

        return WhileToken(condition, parseCodeBlock(), false)
    }

    private fun parseDoWhile(): WhileToken {
        val doBlock = parseCodeBlock()

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

        return ForToken(header[0], header[1] as ExpressionToken?, header.getOrNull(2), parseCodeBlock())
    }

    private fun parseCall(token: WordToken): ExecutableToken {
        val parameters = delimiter("(", ",", ")", ::parseExpression)

        if (parameters.any { it !is ExpressionToken })
            throw ParserException("Invalid token, expression was expected")

        return CallToken(token, parameters.map { it as ExpressionToken })
    }

    private fun parseInitialization(): InitializationToken {
        val type = lexer.strictNext<WordToken>()
        val name = lexer.strictNext<WordToken>()
        name.assertNotKeyword()

        lexer.strictNext<OperatorToken>().assert { it is OperatorToken && it.operator == "=" }

        val value = this.parseExpression()
        if (value !is ExpressionToken)
            throw ParserException("Invalid token, expression was expected")

        return InitializationToken(type, name, value)
    }

    private fun parseParameters(): List<ParameterToken> {
        return delimiter("(", ",", ")", this::parseParameter)
    }

    private fun parseParameter(): ParameterToken {
        val type = lexer.strictNext<WordToken>()
        type.assertTypeKeyword()

        val name = lexer.strictNext<WordToken>()

        return ParameterToken(type, name)
    }

    private fun <T: Token?> delimiter(start: String, separator: String, end: String, parser: () -> T, separatorIgnorePredicate: (T) -> Boolean = { false }): List<T>{
        val result: MutableList<T> = ArrayList()
        lexer.strictNext<PunctuationToken>().assert { it is PunctuationToken && it.character == start }

        while (lexer.peek() !is PunctuationToken || (lexer.peek() as PunctuationToken).character != end){
            val element = parser.invoke()
            result.add(element)

            if (separatorIgnorePredicate.invoke(element)) {
                if (lexer.peek() is PunctuationToken && (lexer.peek() as PunctuationToken).character == end) break
            } else {
                val next = lexer.strictNext<PunctuationToken>()
                next.assert { it is PunctuationToken && (it.character == separator || it.character == end) }

                if (next.character == end) break
            }
        }

        if (lexer.peek() is PunctuationToken && (lexer.peek() as PunctuationToken).character == end)
            lexer.next()

        return result
    }
}