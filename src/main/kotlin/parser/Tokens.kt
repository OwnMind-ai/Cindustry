package org.cindustry.parser

interface Token

// LEXER LEVEL

data class WordToken(
    var word: String
) : Token

data class StringToken(    // TODO add chars
    var content: String
) : ExpressionToken

data class PunctuationToken(
    var character: String
) : Token

data class OperatorToken(
    var operator: String
) : Token

data class NumberToken(
    var number: String
) : ExpressionToken

// PARSER LEVEL

interface ExpressionToken : Token

data class CodeBlockToken(
    var statements: List<ExpressionToken>
) : Token

data class InitializationToken(
    var type: WordToken,
    var name: WordToken,
    var value: ExpressionToken
) : ExpressionToken

data class OperationToken(
    var operator: OperatorToken,
    var left: ExpressionToken,
    var right: ExpressionToken,
) : ExpressionToken