package org.cindustry.parser

import org.cindustry.exceptions.ParserException
import org.cindustry.transpiler.Type

class Parser(private val lexer: Lexer) {
    fun parse(fileName: String): FileToken{
        val result = FileToken(fileName, ArrayList(), ArrayList(), ArrayList(), ArrayList()).file(fileName)
        while (!lexer.ended())
            parseOnFileLevel(result)

        return result
    }

    private fun parseOnFileLevel(file: FileToken) {
        if (lexer.peek() is WordToken) {
            val wordToken = lexer.peek() as WordToken
            when (wordToken.word) {
                "use" -> {
                    lexer.next()

                    val name = lexer.strictNext<WordToken>("Invalid building name")
                    name.assertNotKeyword()

                    if((lexer.peek() as? WordToken)?.word == "as"){
                        lexer.next()
                        val alias = lexer.strictNext<WordToken>("Invalid building name")

                        //TODO can be optimized: treat as '#DEFINE <alias> <name>'
                        file.globalVariables.add(InitializationToken(WordToken("building"), name, BuildingToken(name.word)))
                        file.globalVariables.add(InitializationToken(WordToken("building"), alias, VariableToken(name)))
                    } else {
                        file.globalVariables.add(InitializationToken(WordToken("building"), name, BuildingToken(name.word)))
                    }

                    lexer.strictNext<PunctuationToken>("Semicolon expected")
                        .also { it.assert { i -> i.character == ";" } }
                }
                "global" -> {
                    lexer.next()
                    file.globalVariables.add(parseInitialization(lexer.strictNext()))
                    lexer.strictNext<PunctuationToken>("Semicolon expected").assert { it.character == ";" }
                }
                "enum" -> {
                    lexer.next()
                    val name = lexer.strictNext<WordToken>()
                    name.assertNotKeyword()

                    val values = delimiter("{", ",", "}", {
                        lexer.strictNext<WordToken>().apply { this.assertNotKeyword() }
                    })

                    file.enums.add(EnumToken(name, values))
                }
                "import" -> {
                    lexer.next()
                    val result = ArrayList<Token>()
                    while ((lexer.peek() as? PunctuationToken)?.character != ";"){
                        val token = lexer.next()
                        if (token is WordToken || (token as? PunctuationToken)?.character == ".")
                            result.add(token)
                        else
                            throw ParserException("Invalid import statement", token)
                    }

                    lexer.strictNext<PunctuationToken>()
                    file.imports.add(ImportToken(result))
                }
                else -> {
                    file.functions.add(parseFunction())
                }
            }
        } else {
            file.functions.add(parseFunction())
        }
    }

    private fun parseFunction(): FunctionDeclarationToken {
        val returnType = lexer.strictNext<WordToken>("Invalid return type")
        val name = lexer.strictNext<WordToken>("Invalid name")
        val parameters = parseParameters()
        val code = parseCodeBlock()

        parameters.filter { it.type.word == "vararg" }.let { found ->
            if (found.isEmpty()) return@let
            if (found.size > 1)
                throw ParserException("Only one vararg can be defined", found.last())
            if (parameters.indexOf(found.first()) != parameters.size - 1)
                throw ParserException("vararg must be the last parameter", found.first())
        }

        evaluateParametersMutability(parameters, code)

        return FunctionDeclarationToken(name, returnType, parameters, code)
    }

    private fun parseBody(): CodeBlockToken{
        return if ((lexer.peek() is PunctuationToken) && (lexer.peek() as PunctuationToken).character == "{")
            parseCodeBlock()
        else {
            val line = parseExpression()

            if (line !is BlockToken)
                lexer.strictNext<PunctuationToken>().assert { it.character == ";" }

            CodeBlockToken(mutableListOf(line)).loadData(line)
        }
    }

    private fun parseCodeBlock(): CodeBlockToken {
        val token = CodeBlockToken(
            delimiter("{", ";", "}", this::parseExpression,
                separatorIgnorePredicate = { it is BlockToken && (it !is WhileToken || !it.isDoWhile) })
        )

        token.statements.withIndex().find { it.value is ReturnToken && it.index < token.statements.size - 1}?.let {
            throw ParserException("Unreachable statement", token.statements[it.index + 1])
        }

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
                    throw ParserException("Invalid token, expression was expected", right)

                return buildExpressionTree(OperationToken(operator, token, right), currentPriority)
            }
        } else if (token is ExpressionToken && (next as? PunctuationToken)?.character == "."){
            lexer.strictNext<PunctuationToken>()
            val field = lexer.strictNext<WordToken>()
            field.assertNotKeyword()

            if (token !is VariableToken && token !is CallToken && token !is FieldAccessToken && token !is BuildingToken)
                throw ParserException("Unable to access field '${field.word}'", field)

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
            val right = if(current.operator in listOf("++", "--")){
                val next = lexer.next()
                if (next !is WordToken) throw ParserException("Invalid operator use", next)

                val result = parseWordToken(next)
                if (result !is VariableToken)
                    throw ParserException("Invalid operator use", result)

                result
            } else
                parseExpression()

            if (listOf("++", "--").contains(current.operator) && right !is VariableToken)
                throw ParserException("Invalid operator use", current)

            if (right !is ExpressionToken)
                throw ParserException("Invalid token, expression was expected", right)

            return OperationToken(current, if(current.operator in listOf("-", "+")) NumberToken("0") else OperationToken.EmptySide(), right)
        }

        if (current is WordToken) {
            return parseWordToken(lexer.strictNext())
        }

        if (current is PunctuationToken && current.character == "("){
            lexer.next()
            val expression = parseExpression()
            lexer.strictNext<PunctuationToken>().assert { it.character == ")" }

            return expression
        }

        if (current is ExecutableToken)
            return lexer.next() as ExecutableToken

        throw ParserException("Unexpected token", current)
    }

    private fun parseWordToken(current: WordToken): ExecutableToken {
        if (current.word == "const" || (current.word !in WordToken.KEYWORDS && lexer.peek() is WordToken))  // For enums and structs
            return this.parseInitialization(current)

        if (current.word == "true" || current.word == "false")
            return BooleanToken(current.word == "true")

        if (current.word in listOf(ReturnToken.RETURN, ReturnToken.BREAK, ReturnToken.CONTINUE)) {
            return ReturnToken(
                current,
                if (lexer.peek() is PunctuationToken) {
                    null
                }  else {
                    val expression = parseExpression()

                    if (current.word != "return")
                        throw ParserException("Invalid ${current.word} statement", expression)

                    if (expression !is ExpressionToken)
                        throw ParserException("Invalid token, expression was expected", expression)

                    expression
                }
            )
        }

        if (current.word == "for") return parseFor().loadData(current)
        if (current.word == "while") return parseWhile().loadData(current)
        if (current.word == "do") return parseDoWhile().loadData(current)
        if (current.word == "if") return parseIf().loadData(current)
        if (current.word == "foreach") return parseForeach().loadData(current)

        if (lexer.peek() is PunctuationToken && (lexer.peek() as PunctuationToken).character == "(")
            return this.parseCall(current)

        return VariableToken(current)
    }

    private fun parseForeach(): ForEachToken {
        lexer.strictNext<PunctuationToken>().assert { it.character == "(" }

        val variable = lexer.strictNext<WordToken>("Invalid foreach variable name")
        lexer.strictNext<WordToken>().assert { it.word == "in" }
        val from = lexer.strictNext<WordToken>("Invalid foreach source")

        lexer.strictNext<PunctuationToken>().assert { it.character == ")" }

        return ForEachToken(variable.word, from.word, parseBody())
    }

    private fun parseArrayAccess(token: ExpressionToken): ArrayAccessToken {
        lexer.strictNext<PunctuationToken>().assert { it.character == "[" }
        val expression = this.parseExpression()
        val index = expression as? ExpressionToken ?: throw ParserException("Invalid array index", expression)
        lexer.strictNext<PunctuationToken>().assert { it.character == "]" }

        return ArrayAccessToken(token, index)
    }

    private fun parseIf(): IfToken {
        lexer.strictNext<PunctuationToken>().assert { it.character == "(" }
        val condition = parseExpression()
        lexer.strictNext<PunctuationToken>().assert { it.character == ")" }

        val doBlock = parseBody()
        var elseBlock: CodeBlockToken? = null

        if (lexer.peek() is WordToken && (lexer.peek() as WordToken).word == "else") {
            lexer.next()
            elseBlock = parseBody()
        }

        if (condition !is ExpressionToken)
            throw ParserException("Invalid token, expression was expected", condition)

        return IfToken(condition, doBlock, elseBlock)
    }

    private fun parseWhile(): WhileToken {
        lexer.strictNext<PunctuationToken>().assert { it.character == "(" }
        val condition = parseExpression()
        lexer.strictNext<PunctuationToken>().assert { it.character == ")" }

        if (condition !is ExpressionToken)
            throw ParserException("Invalid token, expression was expected", condition)

        return WhileToken(condition, parseBody(), false)
    }

    private fun parseDoWhile(): WhileToken {
        val doBlock = parseBody()

        lexer.strictNext<WordToken>().assert { it.word == "while" }
        lexer.strictNext<PunctuationToken>().assert { it.character == "(" }
        val condition = parseExpression()
        lexer.strictNext<PunctuationToken>().assert { it.character == ")" }

        if (condition !is ExpressionToken)
            throw ParserException("Invalid token, expression was expected", condition)

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
            throw ParserException("Invalid token, expression was expected", header[1])

        return ForToken(header[0], header[1] as ExpressionToken?, header.getOrNull(2), parseBody())
    }

    private fun parseCall(token: WordToken): ExecutableToken {
        val parameters = delimiter("(", ",", ")", ::parseExpression)

        parameters.find { it !is ExpressionToken }?.let {
            throw ParserException("Invalid token, expression was expected", it)
        }

        return CallToken(token, parameters.map { it as ExpressionToken })
    }

    private fun parseInitialization(first: WordToken): InitializationToken {
        var token = first
        val const = token.word == "const"
        if(const)
            token = lexer.strictNext<WordToken>()

        val type = token
        val name = lexer.strictNext<WordToken>()
        var value: ExecutableToken? = null
        name.assertNotKeyword()

        if (lexer.peek() is OperatorToken && (lexer.peek() as OperatorToken).operator == "=") {
            lexer.next()
            value = this.parseExpression()
            if (value !is ExpressionToken)
                throw ParserException("Invalid token, expression was expected", value)
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
        if (type.word == "...")
            return ParameterToken(WordToken(Type.VARARG.name), WordToken(""))

        type.assert { type.word !in WordToken.KEYWORDS }

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
                        throw ParserException("Constant modified", if (it.left is VariableToken) it.left else it.right)

                    param?.const = false
                }
            }
        }

        parameters.filter { it.const == null }.forEach{ it.const = true }
    }

    private fun <T: Token?> delimiter(start: String, separator: String, end: String, parser: () -> T, separatorIgnorePredicate: (T) -> Boolean = { false }): List<T>{
        val result: MutableList<T> = ArrayList()
        val opened = lexer.strictNext<PunctuationToken>()
        opened.assert { it.character == start }

        var endSkipped = false
        while (!lexer.ended() && (lexer.peek() !is PunctuationToken || (lexer.peek() as PunctuationToken).character != end)){
            val element = parser.invoke()
            result.add(element)

            if (separatorIgnorePredicate.invoke(element)) {
                if (lexer.peek() is PunctuationToken && (lexer.peek() as PunctuationToken).character == end) break
            } else {
                val next = lexer.strictNext<PunctuationToken>()
                next.assert { it.character == separator || it.character == end }

                if (next.character == end) {
                    endSkipped = true
                    break
                }
            }
        }

        if (!endSkipped && lexer.ended())
            throw ParserException("Reached the end of file while searching for '${end}'", opened)

        if (!endSkipped && lexer.peek() is PunctuationToken && (lexer.peek() as PunctuationToken).character == end)
            lexer.next()

        return result
    }
}