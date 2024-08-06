package org.cindustry.transpiler

import org.cindustry.parser.EnumToken

class ObjectsRegistry {
    val enums: MutableList<EnumData> = ArrayList()

    fun addEnum(enum: EnumToken, module: String){
        enums.add(EnumData(
            enum.name.word,
            module,
            enum.values.map { it.word }
        ))
    }

    fun findType(name: String): Type {
        return enums.find { it.name == name }?.getType() ?: Type.valueOf(name)
    }
}

data class EnumData(
    val name: String,
    val from: String,
    val values: List<String>,
) {
    fun getType(): Type{
        return Type.enum(name)
    }
}