package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.QuestTaskFiles
import java.nio.file.Files
import java.nio.file.Path

class QuestTaskFilesTest {
    @TempDir lateinit var root: Path
    @Test fun `groups originals byte for byte and recursively discovers nested and root tasks`() {
        Files.createDirectories(root.resolve("quests"))
        val raw="# 保留注释\nschema-version: 2\nid: sky_start\n"
        Files.writeString(root.resolve("quests/星海启航.yml"),raw)
        Files.writeString(root.resolve("quests/自定义.yml"),"id: custom\n")
        repeat(2) { QuestTaskFiles.groupFirstChapter(root) }
        assertFalse(Files.exists(root.resolve("quests/星海启航.yml")))
        assertEquals(raw,Files.readString(root.resolve("quests/第一章/星海启航.yml")))
        assertEquals(2,QuestTaskFiles.files(root.resolve("quests")).size)
        assertEquals(raw,Files.readString(root.resolve(".migrations/quest-folders-v1/1.before")))
        Files.delete(root.resolve(".migrations/quest-folders-v1/complete"))
        Files.writeString(root.resolve("quests/星海启航.yml"),raw)
        QuestTaskFiles.recover(root)
        assertEquals(2,QuestTaskFiles.files(root.resolve("quests")).size)
    }
    @Test fun `conflicting chapter destination leaves every original intact`() {
        Files.createDirectories(root.resolve("quests/第一章"))
        Files.writeString(root.resolve("quests/星海启航.yml"),"original")
        Files.writeString(root.resolve("quests/第一章/星海启航.yml"),"custom")
        assertThrows(IllegalArgumentException::class.java) { QuestTaskFiles.groupFirstChapter(root) }
        assertEquals("original",Files.readString(root.resolve("quests/星海启航.yml")))
        assertEquals("custom",Files.readString(root.resolve("quests/第一章/星海启航.yml")))
    }
}
