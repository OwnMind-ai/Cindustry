package org.cindustry.parser

class Lexer(private val stream: CharStream) {
    companion object {
        const val WHITESPACES = " \n\t\r"
        val OPERATORS = listOf(
            "+", "-", "*", "/", "%",
            "=", "+=", "*=", "-=", "++", "--",
            ">", "<", ">=", "<=", "==", "===",
            "!", "&&", "||", "&", "|", ">>", "<<",
            "@")

        val OPERATOR_CHARS = OPERATORS.flatMap{ it.toList() }.map { it.toString() }.distinct().joinToString("")

        const val PUNCTUATIONS = ".,(){}[];"
    }

    private var last: Token?

    init {
        last = parseToken()
    }

    fun peek(): Token {
        return last ?: throw ParserException("EOF")
    }

    fun next(): Token {
        val previous = last ?: throw ParserException("EOF")

        last = if (stream.ended()) null
               else parseToken()
        return previous
    }

    fun ended(): Boolean = stream.ended()

    private fun parseToken(): Token{
        this.skipWhitespaces()
        this.skipComments()

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

        while (stream.peek().isLetter() || stream.peek().isDigit())
            token.word += stream.next().toString()

        return token
    }

    private fun canParseNumber(): Boolean = stream.peek().isDigit() || (last !is WordToken && stream.peek() == '.')

    private fun parseNumber(): NumberToken {
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

        return NumberToken(if (fractionPart == null) intPart else "$intPart.$fractionPart")
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

        for (i in 0..ending.length)
            stream.next()

        skipWhitespaces()
    }

    private fun skipWhitespaces() {
        stream.takeWhile { WHITESPACES.contains(it) }
    }
}