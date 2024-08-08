package org.cindustry.exceptions

import org.cindustry.parser.Token

class ParserException(message: String, token: Token? = null) : TokenException(SYNTAX, message, token)