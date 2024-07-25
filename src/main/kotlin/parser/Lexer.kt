package org.cindustry.parser

class Lexer(private val stream: CharStream) {
    companion object {
        const val WHITESPACES = " \n\t\r"
        val OPERATORS = listOf(
            "+", "-", "*", "/", "%",
            "=", "+=", "*=", "-=", "/=", "++", "--",
            ">", "<", ">=", "<=", "==", "===",
            "!", "&&", "||", "&", "|", ">>", "<<",
            "@")

        val OPERATOR_CHARS = OPERATORS.flatMap{ it.toList() }.map { it.toString() }.distinct().joinToString("")

        const val PUNCTUATIONS = ".,(){}[];"
        const val PARSE_NUMBER_AS_NEGATIVE_AFTER = "([{;,"
    }

    private var last: Token?

    init {
        last = parseToken()
    }

    fun peek(): Token {
        return last ?: throw ParserException("EOF")
    }

    inline fun <reified T: Token> strictNext(): T {
        val token = next()
        if (token !is T) throw ParserException("Unexpected token")

        return token
    }

    fun next(): Token {
        val previous = last ?: throw ParserException("EOF")

        skipUnimportantCharacters()
        last = if (stream.ended()) null
               else parseToken()
        return previous
    }

    fun ended(): Boolean = stream.ended()

    private fun parseToken(): Token{
        skipUnimportantCharacters()

        if (this.canParseNumber())
            return this.parseNumber()

        if (this.canParseWord())
            return this.parseWord()

        if (this.canParseString())
            return this.parseString()

        if (this.canParseOperator())
            return this.parseOperator()

        if (this.canParsePunctuation())
            return PunctuationToken(stream.next().toString())

        throw ParserException("Invalid token")
    }

    private fun canParseString(): Boolean = stream.peek() == '"'

    private fun parseString(): StringToken {
        stream.next()

        val result = stream.takeWhile { it != '"' }
        stream.next()

        return StringToken(result)
    }

    private fun canParsePunctuation(): Boolean = PUNCTUATIONS.contains(stream.peek())

    private fun canParseOperator(): Boolean = OPERATOR_CHARS.contains(stream.peek())

    private fun parseOperator(): OperatorToken {
        val operator = stream.takeWhile { OPERATOR_CHARS.contains(it) }
        if (!OPERATORS.contains(operator))
            throw ParserException("Invalid operator: $operator")

        return OperatorToken(operator)
    }

    private fun canParseWord(): Boolean = stream.peek().isLetter()

    private fun parseWord(): WordToken {
        val token = WordToken("")

        while (stream.peek().isLetter() || stream.peek().isDigit() || stream.peek() == '_')
            token.word += stream.next().toString()

        return token
    }

    private fun canParseNumber(): Boolean = stream.peek().isDigit() || (last !is WordToken && stream.peek() == '.')
            || (last is PunctuationToken && (last as PunctuationToken).character in PARSE_NUMBER_AS_NEGATIVE_AFTER
                && stream.peek() == '-' && stream.peek(2)[1] != '-')

    private fun parseNumber(): NumberToken {
        var isNegative = false
        if (stream.peek() == '-') {
            isNegative = true
            stream.next()
            skipUnimportantCharacters()
        }

        var intPart = ""
        var fractionPart: String? = null

        while (stream.peek().isDigit())
            intPart += stream.next()

        if(stream.peek() == '.') {
            stream.next()
            fractionPart = ""
            while (stream.peek().isDigit())
                fractionPart += stream.next()
        }

        if (intPart.isEmpty() && fractionPart == null)
            throw ParserException("Invalid number format")  //TODO add code position
        else if (intPart.isEmpty())
            intPart = "0"

        return NumberToken((if (isNegative) "-" else "") + if (fractionPart == null) intPart else "$intPart.$fractionPart")
    }

    private fun skipUnimportantCharacters() {
        skipWhitespaces()
        skipComments()
        skipWhitespaces()
    }

    private fun skipComments() {
        val peeked = stream.peek(2)
        val ending = when(peeked) {
            "//" -> "\n"
            "/*" -> "*/"
            else -> return
        }

        stream.next() // Skips peeked (2 chars)
        stream.next()

        while (!stream.ended() && stream.peek(ending.length) != ending)
            stream.next()

        for (i in 1..ending.length)
            stream.next()
    }

    private fun skipWhitespaces() {
        stream.takeWhile { WHITESPACES.contains(it) }
    }
}