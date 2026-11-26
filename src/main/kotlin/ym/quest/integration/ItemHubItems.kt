package ym.quest.integration

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import ym.quest.reward.RewardItemProvider
import ym.quest.reward.RewardProviderNotReady

/** Main thread only. Optional linkage to the installed ItemHub public Bukkit service. */
class ItemHubItemProvider(private val plugin: JavaPlugin) : RewardItemProvider {
    override fun build(id: String, player: Player?): ItemStack? = create(id,false)
    override fun reward(id: String, player: Player?): ItemStack? = create(id,true)
    private fun create(id: String, random: Boolean): ItemStack? {
        if(!id.startsWith("itemhub:")) return null
        val dependency=plugin.server.pluginManager.getPlugin("ItemHub")?.takeIf { it.isEnabled }
            ?: error("ItemHub unavailable for reward $id")
        val api=dependency.javaClass.classLoader.loadClass("ym.itemhub.api.ItemHubApi")
        val service=plugin.server.servicesManager.load(api) ?: error("ItemHub API service unavailable for reward $id")
        if(api.getMethod("isReady").invoke(service)!=true) throw RewardProviderNotReady("ItemHub still loading reward $id")
        val qualityApi=try { dependency.javaClass.classLoader.loadClass("ym.itemhub.api.ItemHubQualityApi") }
            catch(_: ClassNotFoundException) { null }
        val quality=qualityApi?.let { plugin.server.servicesManager.load(it) }
        if(quality!=null) {
            return qualityApi.getMethod(if(random) "createRandomItem" else "previewItem",String::class.java)
                .invoke(quality,id.removePrefix("itemhub:")) as? ItemStack
                ?: error("ItemHub item not registered: $id")
        }
        return api.getMethod("createItem",String::class.java,Int::class.javaPrimitiveType)
            .invoke(service,id.removePrefix("itemhub:"),1) as? ItemStack
            ?: error("ItemHub item not registered: $id")
    }
}
