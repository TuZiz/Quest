package ym.quest

import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import ym.quest.reward.ItemFactory
import ym.quest.reward.RewardItemProvider

class RewardItemIdentityTest {
    @Test fun `previews never roll and each nonstackable reward is generated individually`() {
        var rolls=0
        val preview=object : ItemStack() {}
        val provider=object : RewardItemProvider {
            override fun build(id: String, player: org.bukkit.entity.Player?)=preview
            override fun reward(id: String, player: org.bukkit.entity.Player?): ItemStack {
                rolls++
                return object : ItemStack() { override fun getMaxStackSize()=1 }
            }
        }
        val factory=ItemFactory(listOf(provider))
        repeat(5) { assertSame(preview,factory.build("itemhub:星芽:短剑")) }
        assertEquals(0,rolls)
        val rewards=factory.reward("itemhub:星芽:短剑",3)
        assertEquals(3,rolls)
        assertEquals(3,rewards.size)
        assertNotSame(rewards[0],rewards[1])
        assertNotSame(rewards[1],rewards[2])
    }
    @Test fun `factory preserves custom provider stack without reading or rewriting metadata`() {
        // The protected constructor avoids server registries; any metadata access must fail.
        val canonical = object : ItemStack() {
            override fun getItemMeta(): org.bukkit.inventory.meta.ItemMeta =
                error("The reward factory must preserve provider metadata")
        }
        val provider = object : RewardItemProvider {
            override fun build(id: String, player: org.bukkit.entity.Player?) = canonical
        }
        val factory = ItemFactory(listOf(provider))
        for(id in listOf("genlite:copper_coil", "genlite:mechanical_parts", "genlite:gear")) {
            assertSame(canonical, factory.build(id))
        }
    }
}
