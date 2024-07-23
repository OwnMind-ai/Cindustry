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
        return CodeBlockToken(delimiter("{", ";", "}", this::parseExpression, separatorIgnorePredicate = { it is BlockToken}))
    }

    private fun parseExpression(): ExecutableToken {
        val token = buildExpressionTree(tryParseExpression(), 0)
        return if (token is OperationToken) OperationOptimizer.optimize(token) else token
    }

    private fun buildExpressionTree(token: ExecutableToken, currentPriority: Int): ExecutableToken {
        val next = lexer.peek()

        if (token is ExpressionToken && next is OperatorToken) {
            if (next.getPriority() > currentPriority) {
                val operator = lexer.strictNext<OperatorToken>()

                val right = buildExpressionTree(tryParseExpression(), operator.getPriority())
                if (right !is ExpressionToken)
                    throw ParserException("Invalid token, expression was expected")

                return buildExpressionTree(OperationToken(operator, token, right), currentPriority)
            } else if (next.operator == "++" || next.operator == "--")
                    return OperationToken(lexer.strictNext(), token, OperationToken.EmptySide())
        }

        return token
    }

    private fun tryParseExpression(): ExecutableToken {
        val current = lexer.peek()

        if (current is OperatorToken){
            current.assertUnary()

            lexer.next()
            val right = parseExpression()

            if (listOf("@", "++", "--").contains(current.operator) && right !is VariableToken)
                throw ParserException("Invalid operator use")

            if (right !is ExpressionToken)
                throw ParserException("Invalid token, expression was expected")

            return OperationToken(current, OperationToken.EmptySide(), right)
        }

        if (current is WordToken) {
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
            if (token.word == "if") return parseIf()

            if (lexer.peek() is PunctuationToken && (lexer.peek() as PunctuationToken).character == "(")
                return this.parseCall(token)

            return VariableToken(token)
        }

        if (current is ExecutableToken)
            return lexer.next() as ExecutableToken

        throw ParserException("Unexpected token")
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

        return WhileToken(condition, parseCodeBlock())
    }

    private fun parseFor(): ForToken {
        val header = delimiter("(", ";", ")", ::parseExpression)
        if (header.size != 3) throw ParserException("Invalid for statement")

        if (header[1] !is ExpressionToken)
            throw ParserException("Invalid token, expression was expected")

        return ForToken(header[0], header[1] as ExpressionToken, header[2], parseCodeBlock())
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

    private fun <T: Token> delimiter(start: String, separator: String, end: String, parser: () -> T, separatorIgnorePredicate: (T) -> Boolean = { false }): List<T>{
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