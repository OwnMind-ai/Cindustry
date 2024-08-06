package org.cindustry

import org.cindustry.transpiler.Transpiler
import java.io.File

fun main() {
    val tree = Transpiler.getParsed(File("sample.cind"))
    println(Transpiler(tree, File(System.getProperty("user.dir"))).transpile())
}