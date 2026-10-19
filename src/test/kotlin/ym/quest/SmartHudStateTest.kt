package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.hud.*

class SmartHudStateTest {
    private val settings=HudSettings.parse(YamlConfiguration())
    private fun target(n: Long, quest: String="task", objective: String="produce") = TrackerTarget("island",quest,objective,n,100,emptyMap())
    @Test fun `default smart starts compact and does not replay historical milestones on login`() {
        val state=SmartHudState(); state.observe(target(78),0,settings)
        assertEquals(HudMode.SMART,HudPreference().mode)
        assertEquals(HudState.COMPACT,state.state(HudMode.SMART,true,0))
        state.observe(target(78),2,settings);assertEquals(HudState.COMPACT,state.state(HudMode.SMART,true,2))
    }
    @Test fun `first progress expands six seconds and duplicate counters do not extend it`() {
        val state=SmartHudState();state.observe(target(0),0,settings);state.observe(target(1),20,settings)
        assertEquals(HudState.EXPANDED,state.state(HudMode.SMART,true,139))
        repeat(100) { state.observe(target(1),20+it.toLong(),settings) }
        assertEquals(HudState.COMPACT,state.state(HudMode.SMART,true,140))
    }
    @Test fun `milestones throttle consume skipped nodes and bound a burst to eight seconds`() {
        val state=SmartHudState();state.observe(target(0),0,settings);state.observe(target(1),2,settings)
        state.observe(target(10),40,settings);state.observe(target(25),80,settings)
        state.observe(target(50),120,settings);state.observe(target(75),160,settings)
        assertEquals(HudState.COMPACT,state.state(HudMode.SMART,true,162))
        state.observe(target(90),180,settings)
        assertEquals(HudState.COMPACT,state.state(HudMode.SMART,true,180))
        state.observe(target(90),300,settings)
        assertEquals(HudState.COMPACT,state.state(HudMode.SMART,true,300))
    }
    @Test fun `new task manual request and later important milestone expand independently`() {
        val state=SmartHudState();state.observe(target(1),0,settings);state.observe(target(0,"next"),20,settings)
        assertEquals(HudState.EXPANDED,state.state(HudMode.SMART,true,20))
        state.observe(target(50,"next"),240,settings);assertEquals(HudState.EXPANDED,state.state(HudMode.SMART,true,240))
        state.request();state.observe(target(50,"next"),500,settings)
        assertEquals(HudState.EXPANDED,state.state(HudMode.SMART,true,500))
    }
    @Test fun `new task bypasses production cooldown after previous expansion ends`() {
        val state=SmartHudState();state.observe(target(0),0,settings);state.observe(target(1),2,settings)
        state.observe(target(0,"next"),150,settings)
        assertEquals(HudState.EXPANDED,state.state(HudMode.SMART,true,150))
        assertEquals(HudState.COMPACT,state.state(HudMode.SMART,true,270))
    }
    @Test fun `off always and missing context have distinct meanings and players do not share animation`() {
        val first=SmartHudState();val other=SmartHudState()
        first.observe(target(0),0,settings);other.observe(target(0),0,settings);first.observe(target(1),2,settings)
        assertEquals(HudState.EXPANDED,first.state(HudMode.ALWAYS,true,1000))
        assertEquals(HudState.HIDDEN,first.state(HudMode.OFF,true,2))
        assertEquals(HudState.COMPACT,other.state(HudMode.SMART,true,2))
        first.observe(null,4,settings)
        assertEquals(HudState.HIDDEN,first.state(HudMode.ALWAYS,false,4))
    }
    @Test fun `duration and milestone configuration rejects unsupported values`() {
        for(raw in listOf("expand-ticks: 99","expand-ticks: 161","expand-milestones: [10, 10]","expand-milestones: [100]"))
            assertThrows(IllegalArgumentException::class.java) { HudSettings.parse(YamlConfiguration().apply { loadFromString(raw) }) }
    }
}
