package org.cindustry.exceptions

import org.cindustry.parser.Token

open class TokenException(
    private val errorType: String,
    private val errorMessage: String,
    private val token: Token? = null
) : RuntimeException(){
    companion object{
        const val COMPILATION = "CompilationError"
        const val IMPORT = "ImportError"
        const val NAME = "NameError"
        const val SYNTAX = "SyntaxError"
        const val FIELD = "FieldError"
        const val OPERATION = "OperationError"
        const val CALL = "CallError"
        const val TYPE = "TypeError"
    }

    override fun toString(): String {
        var location = ""

        try {
            token?.computeErrorData()
            if (token?.lineNumber != null) {
                val position = " (${token.lineNumber?.plus(1)}, ${token.columnNumber?.plus(1)})"
                val canShowPosition = token.lineNumber != null && token.columnNumber != null

                location = "At ${token.file}${if (canShowPosition) position else ""}:"

                val prefix = " ${token.lineNumber!! + 1} "
                location += "\n$prefix| ${token.line}"

                if (canShowPosition)
                    location += "\n${" ".repeat(prefix.length)}| ${getPointingRow()}"

                location += "\n\n"
            }
        } catch (_: Exception){
            location = "Something went wrong during the retrieval of error's location\n\n"
        }

        return "${location}$errorType: $errorMessage"
    }

    private fun getPointingRow(): String {
        val l = token?.tokenLength?.plus(-1) ?: 0
        if (token == null) throw NullPointerException()

        return " ".repeat(token.columnNumber!!) + "^" + "~".repeat(l)
    }
}