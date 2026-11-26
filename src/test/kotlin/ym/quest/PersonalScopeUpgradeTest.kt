package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.PersonalScopeUpgrade
import ym.quest.domain.*
import ym.quest.hud.HudProjection
import ym.quest.text.TextRenderer
import java.nio.file.Files
import java.nio.file.Path

class PersonalScopeUpgradeTest {
    @TempDir lateinit var root: Path
    private fun resource(name: String)=checkNotNull(javaClass.getResourceAsStream("/"+ym.quest.config.QuestTaskFiles.bundled(name))).bufferedReader().use { it.readText() }
    @Test fun `migration changes only scopes and shared logistics gate with backups and idempotency`() {
        val chapter=root.resolve("chapters/第一章.yml");Files.createDirectories(chapter.parent)
        val beforeChapter=resource("chapters/第一章·星海初醒.yml").replace("\r\n","\n").replace("      - 'first_extract'","      - 'first_extract'\n      - 'stone_site_materials'")+"\ncustom: 17\n# 保留章节备注\n"
        Files.writeString(chapter,beforeChapter)
        val tasks=listOf("初次试炼","石址寻材").map { name ->
            val path=root.resolve("quests/自定义目录/$name.yml");Files.createDirectories(path.parent)
            Files.writeString(path,resource("quests/第一章/支线/$name.yml").replace("scope: 'personal'","scope: 'island'")+"\ncustom: 42\n# 保留任务备注\n")
            path
        }
        PersonalScopeUpgrade.migrate(root)
        val after=tasks.map { Files.readString(it) }
        tasks.forEach { path ->
            val raw=Files.readString(path);val y=YamlConfiguration().apply { loadFromString(raw) }
            assertEquals("personal",y.getString("scope"));assertEquals(42,y.getInt("custom"));assertTrue(raw.contains("# 保留任务备注"))
        }
        val ch=Files.readString(chapter)
        assertEquals(beforeChapter.replace("      - 'first_extract'\n      - 'stone_site_materials'","      - 'first_extract'"),ch)
        PersonalScopeUpgrade.migrate(root)
        assertEquals(after,tasks.map { Files.readString(it) });assertEquals(ch,Files.readString(chapter))
        assertTrue(Files.exists(root.resolve(".migrations/personal-combat-scope-v1/complete")))
        assertTrue(Files.walk(root.resolve(".migrations")).use { paths -> paths.filter { Files.isRegularFile(it) }.anyMatch { runCatching { Files.readString(it).contains("scope: 'island'") }.getOrDefault(false) } })
    }
    @Test fun `HUD uses actual quest scope for mixed side tasks and completion notices`() {
        val text=TextRenderer(YamlConfiguration().apply { loadFromString(resource("lang/zh_cn.yml")) })
        val projection=HudProjection(text,28)
        val o=Objective("kill","combat.kill",3,"hostile","击杀怪物")
        val personal=QuestDefinition("trial","石址试炼",emptyList(),"minecraft:stone",emptyList(),listOf(o),emptyList(),QuestCategory.SIDE,scope=ProgressScope.PERSONAL)
        val shared=personal.copy(id="business",name="能源物流",scope=ProgressScope.ISLAND)
        val main=shared.copy(id="main",category=QuestCategory.MAIN)
        val view=JournalView(listOf(main,personal,shared).map { QuestView(it,QuestStatus.ACTIVE,listOf(ObjectiveProgress(o,0))) },emptyList())
        assertEquals("主线任务 · 空岛",projection.projectGrouped(view)!!["category"])
        assertEquals("支线任务 · 个人",projection.projectGrouped(view,"trial")!!["side-category"])
        assertEquals("支线任务 · 空岛",projection.projectGrouped(view,"business")!!["side-category"])
        val notices=projection.completion(listOf(ProgressChange("island","business",true),ProgressChange("player","trial",true)),listOf(personal,shared),emptyList())
        assertEquals("空 岛 任 务 完 成",notices.single { it.key=="task:business" }.values["heading"])
        assertEquals("任 务 完 成",notices.single { it.key=="task:trial" }.values["heading"])
    }
}
