package ym.quest.gui.journal

import org.bukkit.configuration.file.YamlConfiguration

/** Immutable slot groups, validated during configuration loading and used on the Paper owner thread. */
class JournalCardLayout private constructor(val groups: List<List<Int>>) {
    val capacity get() = groups.size
    private val entries = groups.flatMapIndexed { index, slots -> slots.map { it to index } }.toMap()
    fun entry(slot: Int) = entries[slot]
    fun anchor(slot: Int) = entry(slot)?.let { groups[it][1] == slot } == true
    companion object {
        fun parse(y: YamlConfiguration): JournalCardLayout? {
            if (!y.contains("cards")) return null
            require(y.getInt("cards.schema-version") == 1) { "cards.schema-version expected 1" }
            val shape = y.getStringList("Shape").joinToString("")
            val symbols = shape.filter { y.getString("components.$it.action") in setOf("journal-chapter", "journal-quest") }.toSet()
            val groups = symbols.map { symbol -> shape.indices.filter { shape[it] == symbol } }
            require(groups.size == 6) { "cards: expected six distinct card symbols" }
            groups.forEach { slots ->
                val first = slots.first()
                require(first % 9 <= 6 && slots == listOf(first, first+1, first+2, first+9, first+10, first+11)) {
                    "cards: each symbol must occupy a rectangle of three columns and two rows"
                }
            }
            require(!y.getString("cards.blank-item").isNullOrBlank()) { "cards.blank-item missing" }
            require(!y.getString("cards.base-item").isNullOrBlank()) { "cards.base-item missing" }
            require(!y.getString("cards.fallback-model").isNullOrBlank()) { "cards.fallback-model missing" }
            return JournalCardLayout(groups)
        }
    }
}
