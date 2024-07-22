package parser

import org.cindustry.parser.*
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class LexerTest {

    @Test
    fun simple() {
        val lexer = Lexer(CharStream("word /* comment */ 1 1.24 .25 // comment\n + >= a. word(); \"abc\""))

        var next = lexer.next()
        assertEquals(WordToken("word"), next)

        next = lexer.next()
        assertEquals(NumberToken("1"), next)

        next = lexer.next()
        assertEquals(NumberToken("1.24"), next)

        next = lexer.next()
        assertEquals(NumberToken("0.25"), next)

        next = lexer.next()
        assertEquals(OperatorToken("+"), next)

        next = lexer.next()
        assertEquals(OperatorToken(">="), next)

        next = lexer.next()
        assertEquals(WordToken("a"), next)

        next = lexer.next()
        assertEquals(PunctuationToken("."), next)

        next = lexer.next()
        assertEquals(WordToken("word"), next)

        next = lexer.next()
        assertEquals(PunctuationToken("("), next)

        next = lexer.next()
        assertEquals(PunctuationToken(")"), next)

        next = lexer.next()
        assertEquals(PunctuationToken(";"), next)

        next = lexer.next()
        assertEquals(StringToken("abc"), next)
    }
}