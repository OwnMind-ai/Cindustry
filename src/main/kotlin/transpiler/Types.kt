package org.cindustry.transpiler

enum class Types{
    NUMBER, VOID, STRING, BUILDING, ANY, BOOL, CONTENT
}

fun Types.compatible(other: Types): Boolean{
    return other != Types.VOID && this != Types.VOID &&
            (other == Types.ANY || this == Types.ANY || other == this)
}