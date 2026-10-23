package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.*
import ym.quest.domain.*
import ym.quest.storage.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class GeneratorPlacementTest {
    @TempDir lateinit var root: Path
    private fun <T> CompletableFuture<T>.await(): T = get(10,TimeUnit.SECONDS)
    private val task get() = Files.readString(Path.of("src/main/resources/quests/第一章/放置生成器.yml"))
    private fun put(name: String,raw: String) { val file=root.resolve(name); Files.createDirectories(file.parent); Files.writeString(file,raw) }
    private fun yaml(raw: String)=YamlConfiguration().apply { loadFromString(raw) }
    @Test fun `fresh and v1 installations both reach the four task shared graph`() {
        val bundled=Files.readString(Path.of("src/main/resources/${ChapterConfigUpgrade.defaultFile}"))
        for(id in listOf("sky_start","first_fuel","first_resources")) {
            put("quests/$id.yml","schema-version: 1\nid: $id\n")
        }
        ChapterConfigUpgrade.migrate(root,bundled)
        GeneratorPlacementUpgrade.migrate(root,task)
        val graph=ChapterFiles.parse(yaml(Files.readString(root.resolve(ChapterConfigUpgrade.defaultFile))))
        assertEquals(listOf("sky_start","place_generator","first_fuel","first_resources"),graph.quests)
        val fresh=root.resolve("fresh")
        Files.createDirectories(fresh.resolve("chapters"));Files.createDirectories(fresh.resolve("quests"))
        Files.writeString(fresh.resolve(ChapterConfigUpgrade.defaultFile),bundled)
        Files.writeString(fresh.resolve("quests/星海启航.yml"),Files.readString(Path.of("src/main/resources/quests/第一章/星海启航.yml")))
        repeat(2) { GeneratorPlacementUpgrade.migrate(fresh,task) }
        assertEquals(task,Files.readString(fresh.resolve(GeneratorPlacementUpgrade.taskFile)))
    }
    @Test fun `migration moves customized fuel reward preserves comments fields and is repeatable`() {
        val chapter=Files.readString(Path.of("src/main/resources/${ChapterConfigUpgrade.defaultFile}"))
            .replace("  - quest: place_generator\n    requires:\n      - sky_start\n", "")
            .replace("      - place_generator", "      - sky_start")
        put(ChapterConfigUpgrade.defaultFile,chapter+"\n# 自定义\ncustom: '保留'\n")
        val starter=Files.readString(Path.of("src/main/resources/quests/第一章/星海启航.yml")).replace("\r\n","\n")
            .replace("rewards:\n","rewards:\n  - item: genlite:fuel\n    amount: 24\n    # 自定义燃料\n")
        put("quests/星海启航.yml",starter)
        repeat(2) { GeneratorPlacementUpgrade.migrate(root,task) }
        val migrated=Files.readString(root.resolve("quests/放置生成器.yml"))
        assertEquals(24,yaml(migrated).getMapList("rewards").single()["amount"])
        assertTrue(migrated.contains("# 自定义燃料"))
        assertEquals("sky_start",yaml(migrated).getMapList("rewards").single()["unless-rewarded-by"])
        assertFalse(yaml(Files.readString(root.resolve("quests/星海启航.yml"))).getMapList("rewards").any { it["item"] == "genlite:fuel" })
        val ch=yaml(Files.readString(root.resolve(ChapterConfigUpgrade.defaultFile)))
        assertEquals("保留",ch.getString("custom"))
        assertEquals(listOf("sky_start","place_generator","first_fuel","first_resources"),ChapterFiles.parse(ch).quests.take(4))
        assertTrue(ChapterFiles.parse(ch).taskRequirements.getValue("first_fuel").contains("place_generator"))
    }
    @Test fun `placement state unlocks fuel and all old parcel phases suppress duplicate fuel after restart`() {
        var db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger()); db.open().await()
        val start=QuestDefinition("sky_start","入岛",emptyList(),"minecraft:book",emptyList(),listOf(Objective("island","island.member",1,null,"入岛")),listOf(Reward("genlite:cobblestone_generator",1,true)))
        val placed=QuestDefinition("place_generator","放置",emptyList(),"minecraft:furnace",listOf("sky_start"),listOf(Objective("placed","genlite.placed",1,null,"放置")),listOf(Reward("genlite:fuel",16,false,"sky_start")))
        val fuel=QuestDefinition("first_fuel","燃料",emptyList(),"minecraft:coal",listOf("place_generator"),listOf(Objective("fuel","genlite.fuel",1,null,"加燃料")),emptyList())
        val defs=listOf(start,placed,fuel)
        fun facts(n: Long)=mapOf("island.member" to 1L,"genlite.placed" to n,"genlite.fuel" to 0L)
        try {
            val service=QuestService(db);service.configure(defs).await()
            service.sync("new",facts(0)).await()
            assertEquals(QuestStatus.ACTIVE,service.views("new").await()[1].status)
            assertEquals(QuestStatus.LOCKED,service.views("new").await()[2].status)
            assertFalse(service.claim("new","owner","place_generator",facts(0),false).await())
            assertTrue(service.claim("new","owner","place_generator",facts(1),true).await())
            assertEquals(16,service.mailbox("new").await().single().amount)
            assertFalse(service.claim("new","owner","place_generator",facts(1),true).await())
            for(phase in listOf("WAITING","SENDING","DELIVERED")) {
                db.submit { db.transaction { c -> c.update("INSERT INTO parcels VALUES(?,?,'sky_start','genlite:fuel',16,?,'owner')",phase,phase,phase) } }.await()
                assertTrue(service.claim(phase,"owner","place_generator",facts(1),true).await())
                assertEquals(1,service.mailbox(phase).await().size)
            }
        } finally { db.closeAsync().await() }
        db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger());db.open().await()
        try {
            val service=QuestService(db);service.configure(defs).await()
            assertFalse(service.claim("new","owner","place_generator",facts(1),true).await())
            assertEquals(1,service.mailbox("new").await().size)
        } finally { db.closeAsync().await() }
    }
}
