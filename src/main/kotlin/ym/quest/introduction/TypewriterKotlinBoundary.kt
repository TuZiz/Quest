package ym.quest.introduction

import com.typewritermc.core.entries.Ref
import com.typewritermc.core.entries.Entry
import com.typewritermc.core.interaction.EntryContextKey
import com.typewritermc.core.interaction.EntryInteractionContextKey
import com.typewritermc.core.interaction.InteractionContext
import com.typewritermc.engine.paper.entry.TriggerableEntry

/** Typewriter owns its Kotlin loader. Resolve real library entries with its own KClass objects. */
internal object TypewriterKotlinBoundary {
    private val loader=Ref::class.java.classLoader
    private val reference=Ref::class.java.constructors.single { it.parameterCount==3 }
    private val classOf=loader.loadClass("kotlin.jvm.internal.Reflection")
        .getMethod("getOrCreateKotlinClass",Class::class.java)

    fun entry(id: String): TriggerableEntry? {
        val ref=reference.newInstance(id,classOf.invoke(null,TriggerableEntry::class.java),null) as Ref<*>
        return ref.get() as? TriggerableEntry
    }
    @Suppress("UNCHECKED_CAST")
    fun completionContext(command: String): InteractionContext {
        val ref=reference.newInstance("quest_guide_callback_argument",classOf.invoke(null,Entry::class.java),null) as Ref<Entry>
        val argument=checkNotNull(ref.get()) { "Typewriter callback string argument missing" }
        val key=argument.javaClass.classLoader.loadClass("com.typewritermc.basic.entries.command.arguments.StringArgumentContextKeys")
            .getField("VALUE").get(null) as EntryContextKey
        return InteractionContext(mapOf(EntryInteractionContextKey<Any>(ref,key) to command))
    }
}
