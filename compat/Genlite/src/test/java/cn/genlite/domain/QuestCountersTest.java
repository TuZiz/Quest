package cn.genlite.domain;

import cn.genlite.storage.AtomicStateFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class QuestCountersTest {
    @TempDir Path directory;
    @Test void fuelChestDepositsCountSuccessfulItemsAndRejectWrongFuelOverflowOrDeniedAccess() throws Exception {
        var rules=TestRules.load();var stock=new ContainerStock(rules,(h,p)->true);
        var machine=TestRules.machine("cobblestone_generator",1,0);
        var draft=new WorldState(1,Map.of(machine.id(),machine),Map.of()).draft();
        var source=new ContainerStock.Source(ContainerStock.Kind.FUEL,machine.id(),rules.fuelItem(machine));
        assertTrue(draft.questCounters.isEmpty());
        // Cursor/drag and Shift-click both commit via this same ContainerStock FUEL branch.
        stock.deposit(draft,TestRules.PLAYER,source,1);
        stock.deposit(draft,TestRules.PLAYER,source,2);
        assertEquals(3L,draft.questCounters.get(TestRules.HOME+"|genlite.fuel"));
        assertThrows(IllegalArgumentException.class,()->stock.deposit(draft,TestRules.PLAYER,new ContainerStock.Source(ContainerStock.Kind.FUEL,machine.id(),"minecraft:dirt"),1));
        assertThrows(IllegalArgumentException.class,()->stock.deposit(draft,TestRules.PLAYER,source,1000000));
        assertThrows(IllegalArgumentException.class,()->new ContainerStock(rules,(h,p)->false).deposit(draft,UUID.randomUUID(),source,1));
        stock.reserve(draft,TestRules.PLAYER,source,1);
        assertEquals(3L,draft.questCounters.get(TestRules.HOME+"|genlite.fuel"));
    }
    @Test void realDepositCountsButWithdrawalAndCollectorMovesDoNotAndCountersSurviveStorage() throws Exception {
        var rules=TestRules.load();var actions=new MachineActions(rules,(h,p)->true);
        var m=TestRules.machine("cobblestone_generator",1,0);
        var draft=new WorldState(1,Map.of(m.id(),m),Map.of()).draft();
        actions.deposit(draft,m.id(),TestRules.PLAYER,"fuel",2,1);
        assertEquals(2L,draft.questCounters.get(TestRules.HOME+"|genlite.fuel"));
        actions.withdraw(draft,m.id(),TestRules.PLAYER,"genlite:fuel","fuel");
        Production.transfer(draft,rules,Set.of(m.id()));
        assertEquals(2L,draft.questCounters.get(TestRules.HOME+"|genlite.fuel"));
        assertEquals(1,draft.questCounters.size());
        var file=new AtomicStateFile(directory.resolve("state.json"));file.write(draft.freeze());
        assertEquals(draft.freeze(),file.read());
        assertThrows(IllegalArgumentException.class,()->actions.deposit(draft,m.id(),TestRules.PLAYER,"fuel",0,1));
    }
    @Test void committedProductionCounterMatchesActualQuantityAndIsPublishedOnlyAfterDurableWrite() throws Exception {
        var rules=TestRules.load();
        var type=rules.types().get("cobblestone_generator");
        var levels=new HashMap<>(type.levels());
        var l=levels.get(1);levels.put(1,new Rules.Level(1,2,l.fuelItems(),l.bufferItems(),l.valueMultiplier(),l.collectorSlots()));
        var types=new HashMap<>(rules.types());types.put("cobblestone_generator",new Rules.Type(type.input(),type.output(),type.fuelPerItem(),type.baseValue(),levels,type.upgrades(),type.fuel(),type.range()));
        var fast=new Rules(types,rules.warehouseSlots());
        var file=new AtomicStateFile(directory.resolve("state.json"));
        var m=TestRules.machine("cobblestone_generator",1,100);
        file.write(new WorldState(1,Map.of(m.id(),m),Map.of()));
        var engine=new MachineEngine(file,fast,java.util.logging.Logger.getAnonymousLogger());
        var notices=new java.util.concurrent.LinkedBlockingQueue<Map<String,Long>>();
        engine.questListener(counters->{try {assertEquals(counters,file.read().questCounters());notices.add(counters);}catch(Exception e){throw new RuntimeException(e);}});
        try {
            engine.start().get(5,TimeUnit.SECONDS);
            for(int n=0;n<50 && notices.isEmpty();n++) {engine.lease(m.id(),true);Thread.sleep(100);}
            var first=notices.poll(1,TimeUnit.SECONDS);assertNotNull(first);
            assertEquals(2L,first.get(TestRules.HOME+"|genlite.produce:minecraft:cobblestone"));
        } finally {engine.close();}
    }
}
