package ym.quest.gui.journal

import org.bukkit.configuration.ConfigurationSection

/** Page changes affect repeated entries, navigation and explicitly displayed page numbers. */
internal object JournalPageSlots {
    fun dependsOnPage(component: ConfigurationSection): Boolean {
        if(component.getString("action") in setOf("journal-chapter","journal-quest","previous","next")) return true
        if(component.getBoolean("page-visual")) return true
        return (listOf(component.getString("name","")!!) + component.getStringList("lore"))
            .any { "<page>" in it || "<pages>" in it }
    }
}
