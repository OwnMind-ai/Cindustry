package org.cindustry.parser

import kotlin.math.min

class CharStream(private val file: String, val fileName: String) {
    private val lines = file.split("\n")
    var lineNumber = 0
        private set
    var columnNumber = 0
        private set

    private var pointer: Int = 0

    fun next(): Char {
        val c = file[pointer++]
        if (c == '\n'){
            lineNumber++
            columnNumber = 0
        } else
            columnNumber++

        return c
    }

    fun peek(): Char {
        return file[pointer]
    }

    fun peek(amount: Int): String{
        return file.substring(pointer, min(pointer + amount, file.length))
    }

    fun ended(): Boolean{
        return pointer >= file.length
    }

    fun takeWhile(predicate: (Char) -> Boolean): String{
        var value = ""
        while(!ended() && predicate.invoke(peek()))
            value += next()

        return value
    }

    fun getLine(): String{
        return lines[lineNumber]
    }
}