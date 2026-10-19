package ym.quest.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.TranslationArgument
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration

class TextRenderer(@Volatile var language: YamlConfiguration) {
    private val parser = MiniMessage.miniMessage()
    fun render(text: String, values: Map<String,Component> = emptyMap()): Component =
        parser.deserialize(text,*values.map { Placeholder.component(it.key,it.value) }.toTypedArray())
    fun item(text: String, values: Map<String,Component> = emptyMap()) = noItalic(render(text,values))
    fun format(key: String, values: Map<String,Component> = emptyMap()) = render(checkNotNull(language.getString(key)) { "Missing lang/zh_cn.yml:$key" },values)
    fun send(to: CommandSender, key: String, values: Map<String,Component> = emptyMap()) = to.sendMessage(format(key,values))
    companion object {
        fun noItalic(component: Component): Component {
            val result = component.decoration(TextDecoration.ITALIC,false).children(component.children().map(::noItalic))
            return if(result is TranslatableComponent) result.arguments(result.arguments().map { argument ->
                val value = argument.value()
                if(value is Component) TranslationArgument.component(noItalic(value)) else argument
            }) else result
        }
        fun values(vararg pairs: Pair<String,Any>) = pairs.associate { it.first to Component.text(it.second.toString()) }
    }
}
