package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.QuestFileNumbering
import ym.quest.config.QuestTaskFiles
import java.nio.file.Files
import java.nio.file.Path

class QuestFileNumberingTest {
    @TempDir lateinit var root: Path

    @Test fun `old names migrate without rewriting editable task config`() {
        val old = "quests/第一章/收集的力量.yml"
        val numbered = "quests/第一章/主1-07 物流的起点.yml"
        val custom = "quests/第一章/支线/自定义任务.yml"
        val raw = "# 自定义注释\r\nschema-version: 2\r\nid: 'first_collector'\r\nextra: '保留'\r\n"
        put(old, raw)
        put(custom, "schema-version: 2\nid: custom_task\n")

        repeat(2) {
            QuestFileNumbering.recover(root)
            QuestFileNumbering.migrate(root)
            assertFalse(Files.exists(root.resolve(old)))
            assertEquals(raw, Files.readString(root.resolve(numbered)))
            assertTrue(Files.exists(root.resolve(custom)))
        }
        assertEquals(numbered, QuestTaskFiles.bundled(old))
    }

    @Test fun `existing destination cancels all numbering without overwriting files`() {
        val first = "quests/第一章/星海启航.yml"
        val old = "quests/第一章/支线/初次试炼.yml"
        val numbered = "quests/第一章/支线/支1-04 石址试炼.yml"
        val source = "schema-version: 2\nid: first_trial\n"
        val occupied = "schema-version: 2\nid: custom_task\n"
        put(first, "schema-version: 2\nid: sky_start\n")
        put(old, source)
        put(numbered, occupied)

        val error = assertThrows(IllegalArgumentException::class.java) { QuestFileNumbering.migrate(root) }
        assertTrue(error.message.orEmpty().contains(numbered))
        assertTrue(Files.exists(root.resolve(first)))
        assertFalse(Files.exists(root.resolve("quests/第一章/主1-01 星海启航.yml")))
        assertEquals(source, Files.readString(root.resolve(old)))
        assertEquals(occupied, Files.readString(root.resolve(numbered)))
    }

    private fun put(path: String, content: String) {
        val file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }
}
