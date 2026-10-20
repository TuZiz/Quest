# 兼容接口变更

原始 `D:/codex/DreamSkyblock`、`D:/codex/Genlite` 不改写；服务端部署均先备份。`compat/` 是源文件覆盖包，应用于本次已核查的本地源码副本；不是跨版本通用补丁。更新上游后需要重新核对差异，不能盲目覆盖。

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

Quest已移除Xerr运行/构建依赖、HUD适配及其补丁工具。任务和章节完成默认通过聊天消息提示，不需要Quest资源包。
