package org.cindustry.transpiler.instructions

open class Instruction(
    private var code: String,
    val id: Int
) {
    open fun getCode(lineProvider: (Int) -> Int): String{
        return code
    }
}