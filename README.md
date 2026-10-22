# Quest

2026-10-22 设置页皮肤仅覆盖上半176×126菜单，玩家背包和快捷栏恢复原版。新生图底框包含相连的中央返回按钮，48/49/50三格可返回，左下角信息按钮删除。`gui/settings.yml` 的可选 `pack-item` 仅在资源包已加载时替换按钮图标，无包仍用原 `item` 指南针。CraftEngine全部物品分类排除任务菜单专用物品，原有其他条目经原生集合比较保持不变。新JAR已暂存plugins/update，完整重启后执行 `ce reload pack`；真实客户端待确认。

2026-10-22 最新HUD已按用户截图回退为原深蓝半透明侧栏及金色边框完成通知，配套文字恢复浅色并移除暖色专用color-map；当前设置菜单的新六状态贴图保留。本次仅服务端两张PNG与Xerr/quest.yml修改，不换JAR。依次 `quest reload` → `xerr reload` → `xerr pack`（等待完成）→ `ce reload pack` 后客户端接受新包。原生Xerr2.2和合并包像素检查通过，真实客户端待确认。下文暖色HUD为历史记录。


2026-10-22 已按用户最新要求撤回全菜单同窗口导航，恢复章节、任务、设置各自的 Shape、标题与背景；原有分类和同页操作继续逐槽更新。设置页现使用 imagegen 重新生成的六状态卡片图集，并通过六个模型 UV 接入，不再从参考截图取图标/开关。奶油纸面、木牌与棕色正文按参考色板约束；AI纹理不视为逐像素色号保证。HUD 与完成通知保持此次回退前状态。所有贴图、字体、模型及制作脚本仍仅放服务端 Xerr/CraftEngine。原生菜单、状态和合并包像素检查通过，客户端最终观感待确认。

当前本地依赖为服务端实际安装的 **Xerr 2.2.0**；HUD 继续使用 TabooLib Components。`Xerr/quest.yml` 可选 `color-map` 将目标/步骤中已有浅色 Components 文字映射为纸面可读颜色，未配置时保留原色。原始任务配置和玩家进度不变。

Kotlin + Maven 的空岛团队任务插件。第一章顺序为「初见引航员 → 星海启航 → 安放生成器 → 能源启动 → 第一批资源」。交谈是 UUID 个人任务，无需先入岛；其余任务按空岛共享。入岛发生成器，实际放置后才发燃料。任务奖励与章节奖励默认完成后自动发放，奖励每岛一次。

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
  引导.yml
  quests/第一章/星海启航.yml
  quests/第一章/放置生成器.yml
  quests/第一章/能源启动.yml
  quests/第一章/第一批资源.yml
  gui/chapters.yml
  gui/tasks.yml
  gui/settings.yml
  data/quest.db
```

持有 `quest.use` 的玩家通过 `/quest` 打开章节首页，无需 OP，也可在入岛前阅读个人引导与未解锁任务。首页中间四行按整个9列箱子的第2、5、7列放章节，每页12个；章节Lore显示介绍、完成进度和章节奖励。其余空位填无名灰色玻璃板，操作按钮保持在边缘，任务分类切换按钮位于上边缘。首页不展示当前任务卡、追踪按钮或重复总览入口。

当前只保留三个菜单：`chapters.yml` 章节首页、`tasks.yml` 章节任务列表、`settings.yml` 个人设置。点击章节进入任务列表，任务说明、目标进度和真实奖励名称/数量直接显示在 Lore 中；点击进行中的任务即可追踪并开启 HUD，不再打开任务详情页。任务列表的翻页切换本章任务，返回按钮回到章节首页。普通功能统一使用 `quest.use`。

`settings.yml` 只保留任务追踪、任务提示和任务声音三个开关，按玩家 UUID 异步保存。新玩家任务追踪与 HUD 默认关闭，已有保存状态保持不变。任务追踪同时控制自动追踪与侧边栏 HUD，关闭后保留已选任务；点击进行中的任务会重新开启追踪并更新目标。提示开关不影响操作反馈、奖励到账或 NPC 对话。声音开关仅控制已在 `提示.yml` 配置的音效。任务配置可选 `category: 'main'`、`'side'` 或 `'special'`，缺省主线；首页仅一个分类按钮，按主线→支线→特殊循环切换，保留当前 Inventory，仅写入变化槽位，空分类显示空状态。

菜单采用六行原版箱子和普通文字标题，目前不使用 CraftEngine 背景贴图。首页、分类、章节、追踪和设置功能保留，无需资源包即可使用。TAB 展开恢复左侧任务详细信息及中间/右侧玩家列表；右侧 HUD 保留，旧任务计分板继续关闭。

## 默认玩法与物品证据

0. 新玩家先右键 Adyeshach 编号为 `新人` 的已有 NPC。交谈前每秒显示 ActionBar「右键大厅中的引航员交谈｜查看右侧任务栏」，开启追踪后侧栏优先显示个人任务，不要求玩家拥有空岛。Typewriter 管理简短对话和蹲下确认；不可用时降级聊天确认。完成后再次右键 NPC 执行 DreamSkyblock 自身的 `home gui`，选择创建或加入空岛。已有空岛的老玩家免做此个人任务。
1. 创建/加入 DreamSkyblock 空岛，完成「星海启航」后自动发放 8 个面包、石镐，以及有条件的首台 `genlite:cobblestone_generator`。
2. 在空岛放置生成器，完成「安放生成器」后自动获得 16 份 `genlite:fuel`。背包中的物品和 Genlite 待发机器不算放置；成功提交的机器历史可在重启后补读。
3. 右键生成器打开管理面板，点击「燃料」进入燃料仓；Shift 点击背包燃料，或将燃料放入上方格子。成功存入至少 1 份后完成「能源启动」，自动获得各 2 个 `genlite:mechanical_parts` 和 `genlite:copper_coil`。打开燃料仓、取回燃料或存入失败均不推进任务。
4. 燃料目标完成后开始计数，实际生产 32 个 `minecraft:cobblestone`，自动获得 2 个 `genlite:basic_upgrade_unit`。

第一章全部任务完成后自动发放独立章节奖励，默认 2 个 `minecraft:diamond`，可在 `chapters/第一章·星海初醒.yml` 修改。完成时默认发送聊天提示、简洁 Title 和升级音效。任务名称、目标、图标与任务奖励在 quests/ 中文任务 YAML 中；任务归属与前置关系统一配置在 chapters/ 对应章节文件。物品 ID 来自本地 `Genlite/craftengine/genlite/configuration/materials.yml`、`machines/cobblestone_generator.yml`，燃料和产出来自 `Genlite/src/main/resources/genlite/cobblestone_generator.yml`，没有使用展示名称匹配或猜测 ID。

首台机器检查 Genlite 全部机器历史（包含已拆机器）、当前队员的 Genlite 待发机器，以及在线队员背包中的相应 CE 物品；Quest 自身的岛级领奖唯一约束阻止重复发放。本次核查的 DreamSkyblock 默认模板与 Genlite 启动流程没有自动赠送首台机器。**迁入旧服前若曾通过管理员、其他插件或离线背包发过未放置的裸机器，必须先核对；无法从现有公开 API 证明其发放历史。此类服请从第一任务奖励中移除首台机器条目，或先补齐既有机器历史。**不扫描玩家 NBT，不猜测其他插件的账本。

## 指令与权限

| 指令 | 权限 | 作用 |
|---|---|---|
| `/quest` | `quest.use` | 任务中心，全部普通功能 |
| `/quest guide skip` | `quest.use` | 跳过个人交谈，不发放团队奖励 |
| `/quest guide replay` | `quest.use` | 完成后重新查看交谈 |
| `/quest guide finish` | `quest.use` | 确认当前聊天降级引导，不可绕过尚未开始的交谈 |
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

任务、章节、`gui/chapters.yml` 及 `提示.yml` 使用 `schema-version: 2`，其余资源保持 1，SQLite 使用 `user_version=5`，保留 UUID 个人交谈与显示偏好；原任务、奖励和 HUD 偏好数据保留。升级边界见 [UPGRADE.md](UPGRADE.md)。加载前校验完整任务与章节 DAG、重复 ID、缺失引用、目标、菜单及物品；reload 失败保留上一份有效运行配置。

`quests/` 按章节建立子文件夹（默认 `quests/第一章/`），递归加载，一个文件一个任务；不再配置 `prerequisites`。`chapters/` 一个文件一个章节，文件名可使用中文，引用始终使用稳定 ID。默认是 `chapters/第一章·星海初醒.yml`：

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
- 章节还支持 `description`、`story`、图标和独立 `rewards`。增加后续章节主要新增 `quests/章节文件夹/中文任务名.yml` 与 `chapters/中文章节名.yml`，无需修改引擎；默认仍只有一章三任务。
- 旧版章节集合与任务前置会先校验再迁移为新格式；原文和恢复计划保存在 `.migrations/chapter-v2/`。中断后自动恢复；冲突拒绝覆盖。迁移不会改写玩家数据库。
- `提示.yml` 对章节开始、任务开始、任务完成、下一任务解锁、章节完成分别控制聊天、ActionBar、Title、音效；可全部关闭。文案位于 `lang/zh_cn.yml:notices`，章节开场正文位于章节 `story`，随章节开始的聊天开关控制。默认任务开始发送聊天，下一任务不额外播报；不使用 ActionBar。

目标支持 AND 多条件，`type` 为 `island.member`、`genlite.fuel`、`genlite.produce`；生产条件必须给出真实 `resource`。累计目标以正式解锁时的同一份真实累计快照为基线；若来源未就绪，等待完整目标快照，菜单查询不会提前建立基线。成员条件可依据当前真实状态完成。

`ConditionType` / `ConditionRegistry` 提供新条件注册，`QuestService.sync` 接受不可变绝对计数，`RewardItemProvider` 提供新物品奖励构建，`IslandGateway` / `ProductionGateway` 隔离第三方 API。新的扩展由各自适配器产生稳定、可重放的绝对计数，不能把重复的增量事件直接当绝对累计值。本版只实现物品奖励，没有任意命令奖励、每日/每周、NPC 对话、职业、商会或科技树。

兼容修改的完整文件在 `compat/`，修改说明见 [COMPATIBILITY.md](COMPATIBILITY.md)。构建时生成 `.build/dependencies/source-manifest.json`，记录被替换源文件的 SHA-256。源码来源：[DreamSkyblock](https://github.com/TuZiz/DreamSkyblock)、[Genlite](https://github.com/TuZiz/Genlite)；实际编译以已检查的本地源码为准。

验证结果、已知上游警告和待实机验收范围见 [VALIDATION.md](VALIDATION.md)。

任务 HUD 第一版已重新接入可选 Xerr 2.1.0：右侧任务追踪、顶部完成提示；`/quest hud`及测试指令仍不提供。聊天通知保持现有配置；HUD 只向已成功加载资源包的玩家显示。`提示.yml` 从版本1安全迁移至2：仅将两种完成事件改为聊天开启、ActionBar/Title/音效关闭，其他字段和注释保留；迁移前原文保存到 `.migrations/notices-v2/0.before`，版本2已有配置不会被重复覆盖。旧 `显示.yml` 和语言HUD字段不再加载，数据库不删表、不删数据。`/quest help` 显示当前帮助，管理员仍使用 `/quest reload` 重载。

日志只加载和导出章节、任务、设置三份菜单，严格按 Shape 渲染按钮。Lore 中整行 `<description>`、`<objectives>`、`<rewards>` 分别展开任务说明、条件进度和奖励列表；根级 `reward-format` 配置每条奖励的 `<reward>` 真实名称及 `<amount>` 数量。文案统一以 YAML 单引号包裹。旧 GUI 首次启动时通过 `.migrations/menus-v3` 完整备份并迁移，已退休文件不会重新导出。

### TAB 展开任务详情与玩家列表

Quest `tab.yml:enabled` 与 TAB `layout.enabled` 默认开启，展开 TAB 时左侧显示当前任务的章节、目标、进度与操作步骤，中间和右侧保留玩家列表。正式服恢复原有三列布局及任务占位符，页眉页脚保持原样；Xerr 侧栏保留，旧任务专用 scoreboard 继续关闭。

### 紧凑半透明任务 HUD（2026-10-21）

任务、章节和奖励逻辑保持不变。当前 ACTIVE 任务显示在右侧；默认优先个人引航员任务，手动追踪可选择其他进行中任务；没有可追踪任务或个人设置关闭HUD时隐藏。侧栏宽156、高194 GUI单位，右边距2；显示“主线任务”、章节、任务名称、目标、数字进度及最多6行自动换行的编号操作步骤。关键词RGB颜色保留，不复制任务说明。移除图片进度条、共享说明及TAB提示；深蓝面板主体alpha=150，约41%透明。任务完成通知显示约 3.6 秒，章节完成约 5.6 秒，同批最后一个任务与章节完成合并为章节通知；一条通知槽位、有界队列、淡入淡出。完成提示不宣称奖励已经到账。

所有素材、字体、Xerr 布局、行为设置及制作脚本仅位于服务端 `plugins/Xerr`，不放入 Quest 源码或 JAR：

- `quest.yml`：schema-version 1；开关、布局 ID、刷新与通知时长、队列上限、文字宽度、TabooLib Components 模板。
- `core/layout/quest_tracker.yml`、`core/layout/quest_complete.yml`：位置与元素映射。
- `core/assets/image/quest/star/`：RGBA 像素底图与进度条；单张不超过 256 像素。
- `core/assets/font/text.yml` 中 `quest_sidebar` 使用原生10px字体；顶部通知继续用 `quest_ui` 原生12px字体。分别配置对应测宽规则，字号不做非整数缩放。
- `backups/quest-v1-*`：本次修改前备份；`design/quest-v1`：服务端制作与验证脚本。

HUD 使用 [TabooLib Components](https://iplugin.hiusers.com/docs/invero/text/taboolib-components)，例如 `[{value}](color=#65E6FF)`，不使用 MiniMessage。Quest 通过 Xerr 自带的 Components 解析器先处理文字，再交给 Xerr 测宽和字体定位；内部颜色传输不改变配置语法。HUD 普通文案在 `lang/zh_cn.yml:hud`；已有任务名与说明仍沿用原任务文件格式，投影时取其可见文字。

`quest reload` 异步读取 `Xerr/quest.yml`，无该文件时 HUD 关闭；格式或版本错误拒绝重载并保留当前实例。Xerr 缺失时任务系统继续运行。退出、跨岛、失去权限、资源包状态变化、重载和禁用会清理或重建 HUD；Xerr 重载清空原生槽位后自动补交。纸面/服务端验证不代表真实客户端缩放与着色器已验收。


新版会将已知的旧TAB操作提示迁移为右侧任务栏提示，修改前原文保存于 `.migrations/sidebar-text-v1/lang.before`，保留其他文案、未知字段和中文注释。顶部完成通知的布局、材质和字号保持现状。服务端调整入口为 `Xerr/quest.yml` 与 `Xerr/core/layout/quest_tracker.yml`；本次素材制作脚本仅在 `Xerr/design/quest-sidebar-v2`。
任务设置支持服务端专用 `item-on` / `item-off` 成对配置，根据追踪、提示、声音状态选择物品；`pack-only: true` 可隐藏未加载资源包时的透明点击区域。未加载资源包时仍使用配置的原版 `item`。此功能不修改三个开关的业务或保存方式。当前服务端卡片美术、模型、字体及制作/验证脚本仅保存在 CraftEngine 的 `resources/quest-settings-cards` 和 `design/quest-settings-cards`，Quest 源码及 JAR 不包含这些资产。
