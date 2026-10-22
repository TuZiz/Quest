package ym.quest.introduction

import com.typewritermc.core.entries.Ref
import com.typewritermc.engine.paper.entry.entries.SpeakerEntry
import com.typewritermc.engine.paper.interaction.Confirmation
import com.typewritermc.engine.paper.interaction.ConfirmationKey
import org.bukkit.entity.Player
import java.lang.reflect.Proxy

/** beta-177 owns its Kotlin library loader. Never pass Quest's KClass/Function0/Unit across it. */
internal object TypewriterKotlinBoundary {
    private val loader = Ref::class.java.classLoader
    private val kotlinClass = loader.loadClass("kotlin.reflect.KClass")
    private val reference = Ref::class.java.constructors.single { it.parameterCount == 3 }
    private val classOf = loader.loadClass("kotlin.jvm.internal.Reflection")
        .getMethod("getOrCreateKotlinClass", Class::class.java)
    private val callbackClass = loader.loadClass("kotlin.jvm.functions.Function0")
    private val unit = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
    private val await = loader.loadClass("com.typewritermc.engine.paper.interaction.ConfirmationsKt")
        .getMethod("awaitConfirmation", Player::class.java, ConfirmationKey::class.java, callbackClass)

    @Suppress("UNCHECKED_CAST")
    fun emptySpeaker(): Ref<SpeakerEntry> {
        val type = classOf.invoke(null, SpeakerEntry::class.java)
        check(kotlinClass.isInstance(type))
        return reference.newInstance("", type, null) as Ref<SpeakerEntry>
    }

    fun confirm(player: Player, action: () -> Unit): Confirmation {
        val callback = Proxy.newProxyInstance(loader, arrayOf(callbackClass)) { proxy, method, args ->
            when(method.name) {
                "invoke" -> { action(); unit }
                "toString" -> "QuestIntroductionConfirmation"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> error("Unexpected Typewriter callback method: $method")
            }
        }
        return await.invoke(null, player, ConfirmationKey.SNEAK, callback) as Confirmation
    }
}
