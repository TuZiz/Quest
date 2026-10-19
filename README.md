# Quest

Kotlin + Maven 的空岛团队任务插件。本版在原引擎上增加「第一章 · 星海初醒」，仅包含「星海启航 → 能源启动 → 第一批资源」三个现有任务。前置目标完成即自动解锁下一任务，无需先领奖；任务奖励与章节奖励默认完成后自动发放，不需要邮箱或手动操作。任一有 `quest.use` 的空岛成员均可操作，奖励每岛一次。

## 构建与安装

本次以本地实际项目为准：JDK **25**、Kotlin **2.3.10**、Maven **3.9.12**、Paper API **26.3.build.28-alpha**、CraftEngine **26.9.2**。本地 Genlite 的插件描述要求 Minecraft **26.3**，因此此联动包不能直接用于 26.2。Quest 使用普通 Paper 所有权线程模型，支持相应版本的 Purpur / Leaf，不声明 Folia 兼容。

需要本地完整的 DreamSkyblock、Genlite 源码，以及 CraftEngine JAR。默认使用与 Quest 同级的两个源码目录；DreamSkyblock 的 `libs/ArcMenu-3.0.1.jar`、`libs/PlaceholderAPI-2.12.3.jar` 仅用于编译。CraftEngine 的 Maven 依赖沿用真实本地插件 JAR，没有猜测线上坐标。

```powershell
# 默认路径与当前工作区一致；不会改写原始依赖工程或正式服务端。
./build.ps1 -Offline

# 其他机器可覆盖路径；去掉 -Offline 可以下载 Maven 依赖。
./build.ps1 -ProjectsRoot D:/projects -Jdk C:/Java/jdk-25 -PluginsDir D:/server/plugins
```

脚本在 `.build/dependencies/` 中复制并构建依赖，只覆盖 `compat/` 中的兼容文件；然后构建 Quest。API 依赖准备完成后可单独执行 `mvn clean package -Dkotlin.compiler.daemon=false`。首次不能绕过准备步骤直接构建 Quest。

交付文件在 `target/release/`：

- `Quest-1.0.0.jar`
- `DreamSkyblock-1.0-SNAPSHOT.jar`，带提交后空岛成员事件
- `Genlite-1.0.0.jar`，带提交后燃料/生产累计计数 API

停服备份后，以交付 JAR 替换同名插件，避免并存重复版本，保留现有插件数据目录。另需安装 CraftEngine 26.9.2，并按 Genlite README 安装其真实 `craftengine/genlite` 资源包。Quest 不替换 CraftEngine 配置，不包含服务端 API、第三方插件或客户端资源包。启动后默认导出：

```text
plugins/Quest/
  config.yml
  lang/zh_cn.yml
  chapters/第一章·星海初醒.yml
  提示.yml
  quests/星海启航.yml
  quests/能源启动.yml
  quests/第一批资源.yml
  gui/main.yml
  gui/detail.yml
  gui/chapters.yml
  gui/chapter-detail.yml
  data/quest.db
```

普通玩家默认使用常驻任务 HUD，不开放 `/quest` 菜单；管理员仍可打开全部章节作检查。`gui/main.yml:player-access` 缺省为 false。章节列表展示状态和完成数量，章节详情展示任务链、完成奖励及独立领取入口；任务详情显示条件和原任务奖励。所有列表支持分页。菜单仍为原版箱子。没有 Java 客户端专用输入、MOD、聊天点击或依赖玩家名称的逻辑；可供 Geyser/Floodgate 映射使用。基岩客户端点击及材质展示仍需实际客户端验收。

## 默认玩法与物品证据

1. 创建/加入 DreamSkyblock 空岛，或使用已有 ACTIVE 空岛，完成「星海启航」后自动发放 16 个 `genlite:fuel`、8 个面包、石镐，以及有条件的首台 `genlite:cobblestone_generator`。
2. 按 Genlite 原有操作向生成器存入燃料，完成「能源启动」后自动获得各 2 个 `genlite:mechanical_parts` 和 `genlite:copper_coil`。
3. 燃料目标完成后即开始计数，由岛内生成器实际生产 32 个 `minecraft:cobblestone`，自动获得 2 个 `genlite:basic_upgrade_unit`，对应当前原石生成器 1→2 级的实际升级成本。

第一章全部任务完成后自动发放独立章节奖励，默认 2 个 `minecraft:diamond`，可在 `chapters/第一章·星海初醒.yml` 修改。完成时默认发送聊天提示、简洁 Title 和升级音效。任务名称、目标、图标与任务奖励在 quests/ 中文任务 YAML 中；任务归属与前置关系统一配置在 chapters/ 对应章节文件。物品 ID 来自本地 `Genlite/craftengine/genlite/configuration/materials.yml`、`machines/cobblestone_generator.yml`，燃料和产出来自 `Genlite/src/main/resources/genlite/cobblestone_generator.yml`，没有使用展示名称匹配或猜测 ID。

首台机器检查 Genlite 全部机器历史（包含已拆机器）、当前队员的 Genlite 待发机器，以及在线队员背包中的相应 CE 物品；Quest 自身的岛级领奖唯一约束阻止重复发放。本次核查的 DreamSkyblock 默认模板与 Genlite 启动流程没有自动赠送首台机器。**迁入旧服前若曾通过管理员、其他插件或离线背包发过未放置的裸机器，必须先核对；无法从现有公开 API 证明其发放历史。此类服请从第一任务奖励中移除首台机器条目，或先补齐既有机器历史。**不扫描玩家 NBT，不猜测其他插件的账本。

## 指令与权限

| 指令 | 权限 | 作用 |
|---|---|---|
| `/quest` | `quest.use` | 任务中心，全部普通功能 |
| `/quest admin reload` | `quest.admin` | 异步读入并校验配置，失败保留当前有效配置 |
| `/quest admin reset <空岛UUID> <任务ID>` | `quest.admin` | 重置该目标的累计基线，保留已领记录与待发奖励 |
| `/quest admin debug [空岛UUID]` | `quest.admin` | 查看来源累计值、每任务进度和待发奖励 UUID；控制台需指定空岛 |
| `/quest admin resolve <奖励UUID> delivered` | `quest.admin` | 人工核对后，将不确定的发放记为已送达 |
| `/quest admin resolve <奖励UUID> retry` | `quest.admin` | 仅在确认未收到时恢复待发状态 |

普通权限默认开放，管理权限默认 OP。不新增任务或按钮子权限。管理命令、无权限和普通通知均从中文语言文件读取。

## 进度、线程与恢复

- 稳定主体为 DreamSkyblock `homeId`，不使用世界名或队长 UUID 代替岛 ID。成员离开、玩家下线或世界卸载不会清空岛进度。预留 `ProgressScope.PERSONAL`，本版配置明确只接受 `island`。
- DreamSkyblock 创建成功、加入成功事件均在落盘提交后发布；启动加载已有岛及玩家加入时补读当前状态。
- Genlite 在原有生产事务内记录**成功生产量**，生产转移前计算数量；燃料计数只在 `MachineActions.deposit` 的实际成功变更分支递增。退回、拆装、收集器、仓库和玩家库存转移不生成生产计数。
- 计数与 Genlite 自己的机器状态原子保存，并通过公开服务返回不可变快照；Quest 从不读写 Genlite 数据文件。补丁兼容读取没有新字段的旧存档，新增字段在首次真实写入时保存，不修改旧字段。
- 提交后异步事件驱动正常更新；每秒只做服务就绪检查和累计快照补读，未变快照不提交数据库。不是每 tick 扫描全部任务。累计值重复/迟到均幂等，批量实际产量可一次入账。恢复备份必须协调 Quest 与 Genlite，不能只回滚其中一个账本。
- SQLite 使用单独执行器、WAL、FULL 同步和事务；进度、解锁基线、岛级领奖唯一键及不可变奖励快照持久化。重载不改变已经生成的待发奖励；重置不删除历史领奖资格，避免管理误操作刷奖励。
- 所有库存、菜单、消息和 Bukkit 调用在 Paper 主线程执行；文件/JDBC 在独立线程执行。停止时取消任务、关闭菜单与集成监听、排空已接受的数据库事务并异步关闭连接，不在服务器线程 `get/join/sleep`。
- 可选插件缺失时保留 Quest 数据，相关功能提示不可用；运行中禁用联动插件会暂停当前 Quest 运行实例，重启后恢复，防止旧对象继续回调。

`奖励.yml` 的 `automatic: true` 默认开启任务和章节自动发放，`retry-ticks: 100` 每 5 秒检查在线空岛。每岛选择一位有权限的在线成员接收，奖励每岛一次。玩家侧没有邮箱；内部保留旧的持久化发放记录以兼容历史数据。奖励先事务性记录，背包足够时才按 `WAITING → SENDING → DELIVERED` 发放；容量在异步提交前后各检查一次，不向地面丢弃溢出物。发放失败或退出时未应用的奖励回到 WAITING。跨岛、离线、禁用和权限变化会在应用前重新核验。背包不足时安全保留并自动重试；成员离线后可由其他在线岛员接收。旧 SENDING 不盲目重发，仍由管理员核对。

**SQLite 与 Minecraft 玩家存档没有跨存储原子事务。**进程恰在物品应用/确认期间崩溃的 SENDING 记录会隔离，不能自动重发；debug 显示物品、数量、接收者和 UUID，管理员核对后使用 resolve，操作写入审计表。已标记 DELIVERED 但尚未被 Minecraft 保存的背包仍有服务端硬崩溃窗口，需依据发放审计/整服备份核对，不承诺玩家数据的零丢失。停服一致性备份应覆盖玩家存档、Quest、DreamSkyblock 和 Genlite；运行中不要只复制 SQLite 主文件而遗漏 WAL。

## 配置和扩展

任务和章节 YAML 使用 `schema-version: 2`，其余资源保持 1，SQLite 保持 `user_version=2`。升级边界见 [UPGRADE.md](UPGRADE.md)。加载前校验完整任务与章节 DAG、重复 ID、缺失引用、目标、菜单及物品；reload 失败保留上一份有效运行配置。

`quests/` 只放任务内容，一个文件一个任务；不再配置 `prerequisites`。`chapters/` 一个文件一个章节，文件名可使用中文，引用始终使用稳定 ID。默认是 `chapters/第一章·星海初醒.yml`：

```yaml
schema-version: 2
id: sky_awakening
name: '<#7DD3FC>第一章 · 星海初醒'
icon: minecraft:ender_eye
order: 1
tasks:
  - quest: sky_start
  - quest: first_fuel
    requires:
      - sky_start
  - quest: first_resources
    requires:
      - first_fuel
rewards:
  - item: minecraft:diamond
    amount: 2
```

- `order` 控制章节展示顺序，`tasks` 列表控制章节内展示顺序，调换列表不会改变解锁关系。
- 任务只写 `requires` 前置，后续任务自动推导；多个前置必须全部完成，省略表示章节解锁后即可开始。没有 `sequential` 开关，也不同时维护前后两套关系。
- 每个任务必须且只能归属一章。任务前置只能引用本章任务；跨章节解锁使用章节根节点 `prerequisites`，引用章节 ID，同样要求全部完成，无需先领奖。
- 章节还支持 `description`、`story`、图标和独立 `rewards`。增加后续章节主要新增 `quests/中文任务名.yml` 与 `chapters/中文章节名.yml`，无需修改引擎；默认仍只有一章三任务。
- 旧版章节集合与任务前置会先校验再迁移为新格式；原文和恢复计划保存在 `.migrations/chapter-v2/`。中断后自动恢复；冲突拒绝覆盖。迁移不会改写玩家数据库。
- `提示.yml` 对章节开始、任务开始、任务完成、下一任务解锁、章节完成分别控制聊天、ActionBar、Title、音效；可全部关闭。文案位于 `lang/zh_cn.yml:notices`，章节开场正文位于章节 `story`，随章节开始的聊天开关控制。默认下一任务仅显示 ActionBar，任务开始发送聊天，避免重复聊天。

目标支持 AND 多条件，`type` 为 `island.member`、`genlite.fuel`、`genlite.produce`；生产条件必须给出真实 `resource`。累计目标以正式解锁时的同一份真实累计快照为基线；若来源未就绪，等待完整目标快照，菜单查询不会提前建立基线。成员条件可依据当前真实状态完成。

`ConditionType` / `ConditionRegistry` 提供新条件注册，`QuestService.sync` 接受不可变绝对计数，`RewardItemProvider` 提供新物品奖励构建，`IslandGateway` / `ProductionGateway` 隔离第三方 API。新的扩展由各自适配器产生稳定、可重放的绝对计数，不能把重复的增量事件直接当绝对累计值。本版只实现物品奖励，没有任意命令奖励、每日/每周、NPC 对话、职业、商会或科技树。

兼容修改的完整文件在 `compat/`，修改说明见 [COMPATIBILITY.md](COMPATIBILITY.md)。构建时生成 `.build/dependencies/source-manifest.json`，记录被替换源文件的 SHA-256。源码来源：[DreamSkyblock](https://github.com/TuZiz/DreamSkyblock)、[Genlite](https://github.com/TuZiz/Genlite)；实际编译以已检查的本地源码为准。

验证结果、已知上游警告和待实机验收范围见 [VALIDATION.md](VALIDATION.md)。

HUD 材质与布局仅配置在服务端 `plugins/Xerr/core/`：常驻面板贴右边缘、文字居中并分层配色；通知固定最右上角单槽排队，面板留在通知区下方。移除待领奖数与 `/quest` 帮助。修改布局后必须重建 Xerr 资源包并由 CraftEngine 合并。
