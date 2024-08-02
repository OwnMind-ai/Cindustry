package org.cindustry.transpiler

@Suppress("unused", "EnumEntryName", "SpellCheckingInspection")
enum class BuildingFields (val resultType: Types = Types.NUMBER, val mutable: Boolean = false, actualName: String? = null){
    copper, lead, metaglass, graphite, sand, coal, titanium, thorium, scrap, silicon, plastanium,
    pyratite, beryllium, tungsten, oxide, carbide,
    phaseFabric(Types.NUMBER, false, "phase-fabric"),
    surgeAlloy(Types.NUMBER, false,"surge-alloy"),
    sporePod(Types.NUMBER, false,"spore-pod"),
    blastCompound(Types.NUMBER, false,"blast-compound"),

    water, slag, oil, cryofluid, neoplasm, arkycite, ozone, hydrogen, nitrogen, cyanogen,

    totalItems, firstItem(Types.CONTENT), totalLiquids, totalPower, itemCapacity, liquidCapacity, powerCapacity, powerNetStored,
    powerNetCapacity, powerNetIn, powerNetOut, ammo, ammoCapacity, health, maxHealth, heat, shield, efficiency,
    progress, timescale, rotation, x, y, shootX, shootY, size, dead(Types.BOOL), range, shooting(Types.BOOL), boosting(Types.BOOL), mineX, mineY,
    mining(Types.BOOL), speed, team, type(Types.CONTENT), flag, controlled(Types.ANY), controller(Types.ANY), _name(Types.STRING, false, "name"),
    payloadCount, payloadType(Types.CONTENT), id, enabled(Types.BOOL, true), config(Types.ANY, true), color(Types.NUMBER, true);

    val actualName: String = actualName ?: this.name

    companion object{
        fun getField(name: String): BuildingFields? {
            if (name == "name") return _name
            return entries.find { it.name == name}
        }
    }
}

enum class Types{
    NUMBER, VOID, STRING, BUILDING, ANY, BOOL, CONTENT, UNIT;

    fun compatible(other: Types): Boolean{
        return other != VOID && this != VOID &&
                (other == ANY || this == ANY || other == this)
    }
}

