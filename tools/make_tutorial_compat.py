"""Executed by make_compat.py, with its checked read/change/write helpers in scope."""
def extend(project, path, replacements):
    target = root / 'compat' / project / path
    base_overlay = path in {g+'domain/MachineActions.java',g+'domain/Production.java',g+'api/GenliteQuestApi.java',g+'GenlitePlugin.java'} and path != g+'domain/Production.java'
    text = target.read_text(encoding='utf-8') if base_overlay else read(project, path)
    for old, new in replacements:
        assert text.count(old) == 1, (path, old, 'tutorial source drift')
        text = text.replace(old, new)
    write(project, path, text)

extend('Genlite',g+'domain/MachineActions.java',[
    ('d.machines.put(m.id(),m.settings(m.level(),m.paused(),m.locked(),m.connected(),c.id()).output(OutputMode.COLLECTOR));',
     'd.count(m.home(),"genlite.bind",1);\n        d.machines.put(m.id(),m.settings(m.level(),m.paused(),m.locked(),m.connected(),c.id()).output(OutputMode.COLLECTOR));'),
    ('d.machines.put(id,switch(action)', 'if(action.equals("mode")) d.count(m.home(),"genlite.mode",1);\n        d.machines.put(id,switch(action)')])
extend('Genlite',g+'domain/Production.java',[
    ('transfers.add(new CollectorTransfer(', 'draft.count(m.home(),"genlite.transfer",count);\n            transfers.add(new CollectorTransfer(')])
# ACK only, after successful actual inventory delivery. Failed reservations/restores do not count.
extend('Genlite',g+'gui/DirectWithdrawals.java',[
    ('engine.transact(d -> {d.parcels.remove(parcel.id());return null;}).whenComplete',
     '''engine.transact(d -> {
                if(d.parcels.remove(parcel.id())!=null && source!=null && source.kind()==ContainerStock.Kind.COLLECTOR) {
                    Machine machine=d.machines.get(source.id());
                    if(machine!=null) d.count(machine.home(),"genlite.extract",parcel.amount());
                }
                return null;
            }).whenComplete''')])
extend('Genlite',g+'api/GenliteQuestApi.java',[
    ('boolean hasGenerator(UUID home, Set<UUID> members);','''boolean hasGenerator(UUID home, Set<UUID> members);
    /** Current committed machine state, including type-specific level and placement. */
    Map<String,Long> teachingSnapshot();
    record TeachingMachine(UUID home,String type,int level) {}
    TeachingMachine teachingMachine(UUID id);''')])
extend('Genlite',g+'GenlitePlugin.java',[
    ('public java.util.Map<String,Long> counters() {return engine.snapshot().questCounters();}', '''public java.util.Map<String,Long> counters() {return engine.snapshot().questCounters();}
            public cn.genlite.api.GenliteQuestApi.TeachingMachine teachingMachine(java.util.UUID id) {
                var m=engine.snapshot().machines().get(id);
                return m==null || m.removed()?null:new cn.genlite.api.GenliteQuestApi.TeachingMachine(m.home(),m.type(),m.level());
            }
            public java.util.Map<String,Long> teachingSnapshot() {
                var result=new java.util.HashMap<String,Long>(engine.snapshot().questCounters());
                for(var machine:engine.snapshot().machines().values()) {
                    if(machine.removed()) continue;
                    String prefix=machine.home()+"|";
                    result.merge(prefix+"genlite.level:"+machine.type(),(long)machine.level(),Math::max);
                    result.put(prefix+"genlite.placed:"+machine.type(),1L);
                    if(machine.collectorType()) result.put(prefix+"genlite.collector",1L);
                }
                return java.util.Map.copyOf(result);
            }'''),
    ('engine.start().whenComplete((v,error) -> {', 'getServer().getPluginManager().registerEvents(new cn.genlite.platform.ProductionPickup(this,engine,dream::member),this);\n        engine.start().whenComplete((v,error) -> {')])
extend('Genlite',g+'platform/ShellDrops.java',[
    ('entity.setVelocity(new org.bukkit.util.Vector(0,0.16,0));entity.setPickupDelay(10);',
     '''entity.setVelocity(new org.bukkit.util.Vector(0,0.16,0));entity.setPickupDelay(10);
                            var machine=engine.snapshot().machines().values().stream().filter(m -> m.position().equals(parcel.dropPosition())).findFirst().orElse(null);
                            if(machine!=null) ProductionPickup.tag(entity,plugin,parcel.id(),machine.home());''')])
extend('DreamSkyblock',d+'gui/HomeGuiManager.kt',[
    ('if (invero.open(player, request.menu, data.values(player, home, request))) return',
     'if (invero.open(player, request.menu, data.values(player, home, request))) { tutorialOpened(player,request); return }'),
    ('} else if (arc?.open(player, request.menu) == true) return',
     '} else if (arc?.open(player, request.menu) == true) { tutorialOpened(player,request); return }'),
    ('player.openInventory(inventory)', 'player.openInventory(inventory)\n        tutorialOpened(player,request)'),
    ('    fun click(event: InventoryClickEvent) {', '''    private fun tutorialOpened(player: Player, request: MenuRequest) {
        if(request.menu != HomeMenu.ISLAND) return
        plugin.server.scheduler.runTask(plugin,Runnable {
            if(!plugin.isEnabled || !player.isOnline || !viewing(player)) return@Runnable
            val home=homes.byPlayer(player.uniqueId) ?: return@Runnable
            plugin.server.pluginManager.callEvent(ym.dreamSkyblock.api.HomeMenuOpenedEvent(home.id,player.uniqueId,request.menu.name))
        })
    }

    fun click(event: InventoryClickEvent) {''')])
