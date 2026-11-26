package ym.quest

import ym.quest.storage.*
import ym.quest.domain.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class SharedEnergyProgressUpgradeTest {
    @TempDir lateinit var root: Path
    @Test fun carriesOldEarnedProgressOnceAndContinuesUsingCoreEnergyCounter() {
        val db=Database(root.resolve("quest.db"),Logger.getAnonymousLogger());db.open().get(5,TimeUnit.SECONDS)
        try {
            db.submit {db.transaction {c ->
                c.update("INSERT INTO facts VALUES(?,?,?)","home","genlite.energy",7)
                c.update("INSERT INTO objectives VALUES(?,?,?,?,?)","home","logistics_supply","supply",50,2)
                c.update("INSERT INTO claims VALUES(?,?,?,?)","other","logistics_supply","player",1)
            }}.get(5,TimeUnit.SECONDS)
            val q=QuestDefinition("logistics_supply","能源物流",emptyList(),"genlite:fuel",emptyList(),listOf(Objective("supply","genlite.energy",10,null,"供能")),emptyList())
            val service=QuestService(db);service.configure(listOf(q)).get(5,TimeUnit.SECONDS)
            service.sync("home",mapOf("genlite.energy" to 10L)).get(5,TimeUnit.SECONDS)
            assertEquals(5L,service.views("home").get(5,TimeUnit.SECONDS).single().progress.single().progress)
            service.configure(listOf(q)).get(5,TimeUnit.SECONDS)
            service.sync("home",mapOf("genlite.energy" to 11L)).get(5,TimeUnit.SECONDS)
            assertEquals(6L,service.views("home").get(5,TimeUnit.SECONDS).single().progress.single().progress)
            db.submit {db.transaction {c ->
                assertEquals(1L,c.number("SELECT COUNT(*) FROM audit WHERE action='shared-core-energy-v1'"))
                assertEquals(1L,c.number("SELECT COUNT(*) FROM claims WHERE home='other'"))
            }}.get(5,TimeUnit.SECONDS)
        } finally {db.closeAsync().get(5,TimeUnit.SECONDS)}
    }
}
