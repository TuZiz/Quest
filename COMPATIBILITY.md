# 兼容接口变更

2026-10-22 当前主题版本使用正式服实际安装的 Xerr **2.2.0**（原2.1.0 JAR已不存在），已核对依赖签名并在 Leaf26.3 / Java25 / CraftEngine26.9.2 隔离服完成原生 Components、真实字体、菜单与资源包合并验证。下文2.1.0为历史基线；本次不声称2.1.0运行兼容。Folia继续不支持，真实客户端最终观感待确认。

Genlite、DreamSkyblock 当前源码已包含 Quest 教学接口。构建准备工具识别原生接口后使用当前源码，不再覆盖旧兼容副本；旧版源码仍通过 `compat/` 的窄范围补丁构建。服务端部署先备份，更新上游后需要重新核对差异，不能盲目覆盖。

## DreamSkyblock

- 新增 `ym.dreamSkyblock.api.HomeMembershipCommittedEvent(homeId, playerId)`。Paper 主线程、持久化成功后发布。
- `HomeManager.create`：仅世界创建和 ACTIVE 状态提交均成功时触发。
- `HomeManager.addOwner`：仅成员事务成功后触发。
- 读取仍使用既有 `DreamSkyblockApi`：`isReady`、`findHomeByMember`、`findHome`、`allHomes`、`isMember`、`permission`。Quest 领取要求成员身份和 `stranger_container` 权限；不读写 DreamSkyblock 数据库。
- 本版未修改世界、模板、成员权限语义和设施价值实现。

## Genlite

- 新增 `cn.genlite.api.GenliteQuestApi`，通过 Bukkit ServicesManager 注册，停止时注销。`ready()`、`counters()`、`hasGenerator(home, members)` 均为非阻塞只读快照。
- `counters()` 的 key 为 `空岛UUID|metric`，metric 为 `genlite.fuel` 或 `genlite.produce:实际资源ID`，value 为已持久化的累计物品数。
- 新增 `GenliteCountersCommittedEvent`，`Event.isAsynchronous == true`，在原子写入成功后调用。监听器禁止访问线程受限 API；事件丢失可通过服务快照补读。
- `WorldState` 增加不可变 `questCounters`，旧存档缺失时按空表读取；原有 3/4/5 参数构造入口保留。Draft 复制并提交计数，使用 `Math.addExact` 防溢出。
- `MachineActions.deposit` 在真实燃料变更分支记录存入物品数量，并拒绝零数和负数；异常事务不会发布计数。
- `MachineEngine.tick` 在 `Production.generate` 成功后，使用生产前后 buffer 的差值记录产量，再执行原有搬运与持久化。通知异常不会让已经提交的事务被当成未提交；每次通知有可补读快照。
- 上述字段继续由 Genlite 自己的原子状态文件保存，不引入新数据库、不绕过其状态线程、不改变生产规则或库存转移。
- 新增 `QuestCountersTest` 验证燃料变更、转移不计数、持久重读，以及提交后真实 2 个/批生产计数。

`tools/make_compat.py` 保留本次补丁的生成依据，会核对需要替换的原始片段；日常构建不需要重新运行它。运行 `build.ps1` 即可应用已保存的覆盖包。

## 显示集成

Quest 当前使用 Xerr 2.2.0，保留任务侧栏与完成通知；材质、字体和布局只放在服务端 Xerr/CraftEngine，Quest 源码和 JAR 不携带材质。TAB 主线与支线独立显示。
## Genlite 联动接口缺失（2026-10-22）

正式服日志出现 `Genlite compatibility patch missing: ... cn/genlite/api/GenliteQuestApi` 时，原版 Genlite 不向 Quest 提供机器快照和燃料/产出计数，安放生成器会停在 0/1，后续燃料与产出任务也无法正常同步。需要使用交付包中与 Quest 配套的 Genlite JAR，而非仅替换 Quest。

经隔离服验证的兼容包通过 `tools/stage_genlite_compat.py` 放入正式服 `plugins/update/Genlite-1.0.0.jar`，原 JAR 备份在 `plugins/Quest/backups/genlite-api-*`，不修改运行中的机器或玩家任务数据。必须完整重启服务器；`quest reload` 无法给已加载的 Genlite 增加 Java 接口。旧机器数据缺失计数字段时按空计数读取，已有生成器的放置状态从机器记录识别，无需人工补任务完成或再次发奖。
