package org.cindustry.parser

import kotlin.math.min

class CharStream(private val file: String) {
    private var pointer: Int = 0

    fun next(): Char {
        return file[pointer++]
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
}