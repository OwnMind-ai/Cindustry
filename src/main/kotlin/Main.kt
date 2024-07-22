package org.cindustry

import org.cindustry.parser.CharStream
import org.cindustry.parser.Lexer
import org.cindustry.parser.Parser
import org.cindustry.transpiler.Transpiler
import java.io.File

fun main() {
    val tree = Parser(Lexer(CharStream(File("sample.cind").readText()))).parse()
    println(Transpiler(tree).transpile())
}