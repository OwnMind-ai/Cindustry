package org.cindustry.transpiler

@Suppress("unused", "EnumEntryName", "SpellCheckingInspection")
enum class BuildingFields (val resultType: Type = Type.NUMBER, val mutable: Boolean = false, actualName: String? = null){
    copper, lead, metaglass, graphite, sand, coal, titanium, thorium, scrap, silicon, plastanium,
    pyratite, beryllium, tungsten, oxide, carbide,
    phaseFabric(Type.NUMBER, false, "phase-fabric"),
    surgeAlloy(Type.NUMBER, false,"surge-alloy"),
    sporePod(Type.NUMBER, false,"spore-pod"),
    blastCompound(Type.NUMBER, false,"blast-compound"),

    water, slag, oil, cryofluid, neoplasm, arkycite, ozone, hydrogen, nitrogen, cyanogen,

    totalItems, firstItem(Type.CONTENT), totalLiquids, totalPower, itemCapacity, liquidCapacity, powerCapacity, powerNetStored,
    powerNetCapacity, powerNetIn, powerNetOut, ammo, ammoCapacity, health, maxHealth, heat, shield, efficiency,
    progress, timescale, rotation, x, y, shootX, shootY, size, dead(Type.BOOL), range, shooting(Type.BOOL), boosting(Type.BOOL), mineX, mineY,
    mining(Type.BOOL), speed, team, type(Type.CONTENT), flag, controlled(Type.ANY), controller(Type.ANY), _name(Type.STRING, false, "name"),
    payloadCount, payloadType(Type.CONTENT), id, enabled(Type.BOOL, true), config(Type.ANY, true), color(Type.NUMBER, true);

    val actualName: String = actualName ?: this.name

    companion object{
        fun getField(name: String): BuildingFields? {
            if (name == "name") return _name
            return entries.find { it.name == name}
        }
    }
}

open class Type(val name: String, private val value: String? = null){
    fun compatible(other: Type): Boolean{
        return other != VOID && this != VOID &&
                (other == ANY || this == ANY || (other == this && other.value == this.value))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Type

        if (name != other.name) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }

    data object NUMBER : Type("NUMBER")
    data object VOID : Type("VOID")
    data object STRING : Type("STRING")
    data object BUILDING : Type("BUILDING")
    data object ANY : Type("ANY")
    data object BOOL : Type("BOOL")
    data object CONTENT : Type("CONTENT")
    data object UNIT : Type("UNIT")
    data object ENUM : Type("ENUM")
    data object VARARG : Type("VARARG")

    companion object {
        const val ENUM_NAME: String = "ENUM_VALUE"

        fun enum(name: String): Type{
            return Type(ENUM_NAME, name)
        }

        fun values(): Array<Type> {
            return arrayOf(NUMBER, VOID, STRING, BUILDING, ANY, BOOL, CONTENT, UNIT, VARARG)
        }

        @Deprecated("")
        fun valueOf(value: String): Type {
            return when (value.uppercase()) {
                "NUMBER" -> NUMBER
                "VOID" -> VOID
                "STRING" -> STRING
                "BUILDING" -> BUILDING
                "ANY" -> ANY
                "BOOL" -> BOOL
                "CONTENT" -> CONTENT
                "UNIT" -> UNIT
                "ENUM" -> ENUM
                "VARARG" -> VARARG
                else -> throw IllegalArgumentException("No object org.cindustry.transpiler.Types.$value")
            }
        }
    }
}

