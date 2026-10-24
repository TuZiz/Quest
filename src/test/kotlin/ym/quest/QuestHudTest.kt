package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.domain.*
import ym.quest.hud.*
import ym.quest.text.TextRenderer
import java.nio.file.Files
import java.nio.file.Path

class QuestHudTest {
    private fun projection()=HudProjection(TextRenderer(YamlConfiguration().apply {
        loadFromString(Files.readString(Path.of("src/main/resources/lang/zh_cn.yml")))
    }),28)
    private val objective=Objective("stone","genlite.produce",32,"minecraft:cobblestone","<#D2E7F2>让生成器生产原石")
    private val quest=QuestDefinition("stone","<#FFFFFF>第一批资源",emptyList(),"minecraft:stone",emptyList(),listOf(objective),emptyList())
    private val chapter=ChapterDefinition("first","第一章 · 星海初醒","minecraft:book",emptyList(),1,emptyList(),listOf("stone"),emptyList())
    @Test fun `compact HUD keeps main and selected side together without narrative hints`() {
        val main=QuestView(quest.copy(description=listOf("很长的剧情不应占用面板")),QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,8)))
        val side=QuestView(quest.copy(id="trial",name="初次试炼",category=QuestCategory.SIDE),QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,2)))
        val view=JournalView(listOf(side,main),listOf(ChapterView(chapter,ChapterStatus.ACTIVE,0,false)))
        val result=projection().projectGrouped(view,"trial",false)!!
        assertEquals("主线任务",result["category"])
        assertEquals("第一批资源",result["task"])
        assertEquals("8 / 32",result["progress-text"])
        assertEquals("支线任务",result["side-category"])
        assertEquals("初次试炼",result["side-task"])
        assertEquals("2 / 32",result["side-progress-text"])
        assertTrue(result.getValue("side-objective-1").contains("让生成器生产原石"))
        assertFalse(result.keys.any { it.startsWith("hint-") })
        val finished=projection().projectGrouped(view.copy(quests=listOf(side,main.copy(status=QuestStatus.CLAIMED))))!!
        assertTrue(finished.getValue("task").contains("当前主线章节已全部完成"))
        assertEquals("初次试炼",finished["side-task"])
        assertEquals("",finished["objective-1"])
    }
    @Test fun `explicit tracking can select shared task while personal introduction is active`() {
        val shared=JournalView(listOf(QuestView(quest,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,0)))),listOf(ChapterView(chapter,ChapterStatus.ACTIVE,0,false)))
        val personalQuest=quest.copy(id="intro",name="个人交谈")
        val personal=JournalView(listOf(QuestView(personalQuest,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,0)))),emptyList())
        assertEquals("个人交谈",projection().project(shared,personal=personal)!!["task"])
        assertEquals("第一批资源",projection().project(shared,"stone",false,personal)!!["task"])
        assertNull(projection().project(shared,"removed",false,personal))
        val side=shared.copy(quests=listOf(shared.quests.single().copy(definition=quest.copy(category=QuestCategory.SIDE))))
        assertEquals("支线任务",projection().project(side)!!["category"])
    }
    @Test fun `projection clamps counters selects active objective and removes duplicate TAB guidance`() {
        val view=JournalView(listOf(QuestView(quest,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,64)))),listOf(ChapterView(chapter,ChapterStatus.ACTIVE,0,false)))
        val result=projection().project(view)!!
        assertEquals("32",result["current"]);assertEquals("32 / 32",result["progress-text"])
        assertEquals("第一批资源",result["task"])
        assertFalse(result.containsKey("scope"));assertFalse(result.containsKey("help"))
        assertNull(projection().project(view.copy(quests=listOf(view.quests.single().copy(status=QuestStatus.COMPLETE)))))
    }
    @Test fun `sidebar wraps full instruction text with numbering RGB and no clickable markup`() {
        val q=quest.copy(description=listOf("<#E2E8F0>右键原石生成器<newline>   打开管理面板",
            "<#E2E8F0>点击<#FDE68A><bold>「燃料」</bold><#E2E8F0><newline>   打开燃料仓",
            "<#E2E8F0>Shift点击背包燃料<newline>   或放入上方格子"))
        val result=projection().project(JournalView(listOf(QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,0)))),emptyList()))!!
        assertTrue(result.getValue("hint-1").startsWith("1. "))
        assertTrue(result.getValue("hint-2").contains("打开管理面板"))
        assertTrue(result.getValue("hint-3").contains("#fde68a",ignoreCase=true),result.getValue("hint-3"))
        assertTrue(result.getValue("hint-6").contains("或放入上方格子"))
        assertFalse(result.values.any { it.contains("<newline>") || it.contains("<bold>") })
        val empty=projection().project(JournalView(listOf(QuestView(quest,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,0)))),emptyList()))!!
        assertTrue((1..6).all { empty["hint-$it"]=="" })
    }
    @Test fun `chapter completion merges its final quest and task completion names actual unlocked successor`() {
        val p=projection()
        val merged=p.completion(listOf(ProgressChange("home","stone",true),ProgressChange("home","first",true,chapter=true)),listOf(quest),listOf(chapter))
        assertEquals(1,merged.size);assertTrue(merged.single().chapter)
        val next=quest.copy(id="next",name="下个目标")
        val notices=p.completion(listOf(ProgressChange("home","stone",true),ProgressChange("home","next",false,started=true)),listOf(quest,next),listOf(chapter))
        assertEquals("下一目标：下个目标",notices.single().values["detail"])
        assertFalse(notices.single().values.values.any { it.contains("到账") })
    }
    @Test fun `queue bounds pending notifications deduplicates fades expires and cleans up`() {
        val q=HudNoticeQueue(HudSettings(queueLimit=2,taskTicks=40,fadeTicks=6))
        fun n(id: String)=HudNotice(id,false,mapOf("name" to id))
        q.offer(n("first"));assertTrue(q.frame(0)!!.alpha<255)
        q.offer(n("first"));q.offer(n("dropped"));q.offer(n("second"));q.offer(n("third"))
        assertEquals(255,q.frame(10)!!.alpha)
        assertEquals("second",q.frame(40)!!.notice.key)
        assertEquals("third",q.frame(80)!!.notice.key)
        assertNull(q.frame(120));q.offer(n("new"));q.clear();assertNull(q.frame(122))
    }
    @Test fun `long Unicode labels stay within configured measured units`() {
        val result=projection().clip("<#FFFFFF>这是一个很长很长的任务名称带有星海章节和目标")
        assertTrue(result.endsWith(".."));assertTrue(result.codePoints().toArray().sumOf { if(it>255) 2 else 1 }<=28)
    }
    @Test fun `missing external config disables HUD and malformed version is diagnosed`() {
        val root=Files.createTempDirectory("quest-hud-config")
        val file=root.resolve("quest.yml")
        assertFalse(HudSettings.load(file).enabled)
        Files.writeString(file,"schema-version: 7\n")
        val error=assertThrows(IllegalArgumentException::class.java) { HudSettings.load(file) }
        assertTrue(error.message!!.contains("schema-version=7"))
    }
    @Test fun `retired TAB wording migration preserves custom fields and is repeatable`() {
        val root=Files.createTempDirectory("quest-sidebar-wording")
        val file=root.resolve("lang/zh_cn.yml");Files.createDirectories(file.parent)
        val original="schema-version: 1\n# 保留中文注释\ncustom: '自定义'\nhud:\n  task-detail: '按住 TAB 查看后续任务'\n"
        Files.writeString(file,original)
        ym.quest.config.SidebarTextUpgrade.migrate(root)
        val updated=Files.readString(file)
        assertTrue(updated.contains("查看右侧任务栏，继续旅程"))
        assertTrue(updated.contains("# 保留中文注释\ncustom: '自定义'"))
        assertEquals(original,Files.readString(root.resolve(".migrations/sidebar-text-v1/lang.before")))
        ym.quest.config.SidebarTextUpgrade.migrate(root)
        assertEquals(updated,Files.readString(file))
    }
}
