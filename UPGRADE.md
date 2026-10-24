# 2026-10-24 HUD漂移修复

恢复服务端Xerr/core/regex/common.yml基础测宽，合并Quest两字体特殊符号正则；之前清理误删规则导致Xerr将普通文字宽度算作0。布局和素材未改。执行xerr reload即可，不需更换Quest/Xerr JAR或重生成资源包。当前JAR原生测宽探针复现旧故障并验证修复；真实客户端仍待验收。备份hud-width-fix-20261024-130854。保留默认物品栏等HUD关闭。

# 2026-10-23 配套整体更新

服务端旧版本仅4份任务，Genlite缺教学API。14份任务（10共享主线+4支线）、章节、教学、缺失语言和迁移记录已同步，保留原GUI/HUD/全局配置。个人NPC引导额外1项。Quest修正带点unlock-when条件被Bukkit展开的问题；Java25/Paper26.3构建及15项相关测试通过，真实解析器/任务图和重复迁移通过。

三配套JAR均放入服务端plugins/update；Quest和Genlite根JAR被运行进程占用，须完整重启触发替换；DreamSkyblock根已为当前兼容版本。备份与在线SQLite一致快照在Quest/backups/full-update-20261023-211919。更新脚本未改玩家DB。章节/任务奖励沿用既有claims防重，不重发历史奖励。具体任务奖励说明已复制Quest/docs。完整玩家流程、资源拾取/绑定/多人奖励及客户端翻页仍待实测。

# 章节任务系统升级说明

## 2026-10-22 客户端底板字体图集修复

- 当前客户端FontTexture图集为256×256；旧1482×1061单字形无法装入，导致设置底板与返回按钮消失、标题左移。运行底图转换为176×126，原始生成图留在服务端设计目录。字体height126/ascent13保持不变。
- 修复仅涉及服务端CraftEngine源资源，无需更新Quest JAR；执行ce reload pack并接受新包生效。后续用户截图已显示底板及返回按钮。
- 按用户最终决定，保留槽位悬停高亮，不添加客户端模组、不修改全局高亮或着色器。素材和制作脚本继续仅存放服务端。

## 2026-10-22 仅上半菜单换肤与物品浏览器过滤

- 新imagegen底板只绘制176×126的菜单，字体height=126/ascent=13，背包标签从127开始，不再覆盖玩家背包。原三张六状态卡片保留；底板中央自带连接边框的返回按钮，删除左下角信息按钮。
- 返回占48/49/50格，中间按钮用可选pack-item隐藏原指南针图标，两侧仅有包时启用；无包仍显示一个原版指南针。标题、名称、Lore继续使用原入口，非斜体保持代码处理。
- CraftEngine internal:all 改用26.9.2原生regex源，隐藏quest_settings专用物品，保留此前其他自定义条目及该版本自带destroy_stage辅助项。不是删除物品注册，菜单仍能调用。
- 素材/配置及制作验证脚本仅在服务端CraftEngine，当前入口 `design/quest-settings-cards/top-only-v3/`；旧安装脚本会覆盖新皮肤。回退前文件校验保存在已有最新快照before-top-only-settings中。正常完整重启后ce reload pack，接受新包；本次不改深蓝HUD/TAB/任务数据。

## 2026-10-22 恢复上一版导航与重新生图

- 按用户最新反馈，撤回全会话共用窗口及固定标题。恢复上一版各页独立打开，分别按原 Shape/title/fallback-title 渲染，设置页自己的资源包背景恢复；分类、同页翻页与三个开关仍使用原有逐槽比较更新。
- 设置卡片已重新调用内置 imagegen 生成六状态图集，并实际替换服务端 CraftEngine 图集与六个模型 UV。卡片仍为48×88，整卡45格点击和原版回退保留，玩家设置与奖励不改。制作入口改为 `CraftEngine/design/quest-settings-cards/regenerated-v2/install.py`，不要再执行旧 warm_install.py 覆盖新图。
- 待更新 JAR 位于 plugins/update。完整重启后执行 `ce reload pack`，客户端接受新包；本次没有改HUD/TAB/Xerr素材，不需要重生成Xerr包。

## 2026-10-22 严格参考色板主题

- 设置页、任务 HUD、完成通知改为参考图奶油纸面、橙框与木牌。HUD 纸面按最新要求不透明，保留原有原生10px/12px字体和全部步骤。设置仍为三个开关及整卡点击，沿用局部刷新与原版回退。
- 服务器现为 Xerr 2.2.0，Quest 已据当前 JAR/API 构建并在隔离服验证；无需修改玩家数据。可选 `Xerr/quest.yml:color-map` 只映射原有目标/步骤的 Components RGB 颜色，不迁移任务文本语法。
- 新 Quest JAR 已放正式服 `plugins/update/Quest-1.0.0.jar`；菜单与 Xerr/CraftEngine 源资源已同步。正常完整重启后依次执行 `xerr reload`、`xerr pack`（等待完成）、`ce reload pack`，再由客户端接受资源包。正式服尚未自动重启或重新生成资源包。
- 最新修改前快照为 `plugins/Quest/backups/latest-ui-20261022-164021`，641文件已逐一校验，另有 Xerr2.2.0 二进制校验补充。旧备份删除被自动审批拒绝（`blocked by policy`），仍保留；持久迁移记录和玩家数据没有删除。
- 当前服务器制作入口为 `CraftEngine/design/quest-settings-cards/warm_install.py`、`Xerr/design/quest-warm/install.py`；旧 `install.py` 为历史版本，不应用于当前主题。实际客户端缩放与观感尚待确认。

## 2026-10-22 GUI 收敛为三份配置

- 有效 `gui/` 仅为 `chapters.yml`、`tasks.yml`、`settings.yml`。章节首页继续保留第2/5/7列、玻璃板、分类、分页、章节介绍/进度/奖励；点击章节进入任务列表。任务详情仅Lore，点击ACTIVE任务直接保存追踪，不再打开独立详情或重复总览。
- 删除旧菜单类、会话、渲染器、运行时手动领奖回调、旧中心字段及详情/总览路由。新安装与重载只加载/导出三份配置；旧路径仅由一次性迁移读取和归档，不保留运行兼容空壳。
- `MenuConfigUpgrade` 优先采用当前 `home.yml` 的布局/文案和 `journal-tasks.yml` 的任务列表，settings保留原文；已有新版chapters/tasks优先。原文、中文注释与未知根字段保留。章节GUI schema1→2以区分新首页，tasks/settings为1；业务章节和任务的数据schema不变。
- 所有源文件/目标文件先校验，持久化 `.migrations/menus-v3/plan.properties` 与完整before/after再发布三份文件、移除已退休菜单。中断可恢复，遇到并发编辑、未来schema或无效动作拒绝覆盖；重复加载不改写。正式服额外提供可读备份 `Quest/backups/three-menus-20261022/gui/`。
- 需要正常重启加载新JAR，再使用 `/quest`。不要用旧进程的reload替代本次代码更新，以免旧导出逻辑重新生成已退休配置。无资源包变更，不改TAB、HUD、任务进度或奖励结算。

## 2026-10-22 章节列号纠正与玻璃板填充

- 章节按整个9列箱子的第2、5、7列放置，中间四行共12位，不再计算中央区域的列偏移。其他空位与无章节的预留位填无名灰色玻璃板，现有分类与操作按钮不移动。
- `gui/home.yml` 使用 `#` 装饰映射和 `empty-slots: '#'`，渲染器只为未占用格补装饰，不添加动作。原章节介绍、进度、奖励、分页及任务层级保留。
- 正式配置/JAR修改前保存在 `Quest/backups/chapter-columns-20261022`；有渲染补位代码变更，正常重启加载新JAR后生效，无需生成资源包。

## 2026-10-22 章节首页布局

- `/quest` 首页只显示章节、三个分类、HUD设置、翻页与关闭。六行Shape，中央4×7区域每行局部第2/5/7列为章节位，对应箱子第3/6/8列，每页12章；四角依次为设置、关闭、上一页和下一页，分类位于顶部。
- 章节Lore展示原章节介绍、完成数量/总数量、百分比与真实物品完成奖励，不发放或重复领取奖励。首页分页按章节数量计算，空分类无额外中央按钮，分类Lore显示空状态。
- 原任务查看层移到新增 `gui/task.yml`；点击章节→任务列表→任务查看，返回顺序相反。原上一/下一任务、追踪及个人设置保留。旧章节总览配置保留兼容。
- 正式 `gui/home.yml` 修改前备份至 `Quest/backups/chapter-home-20261022`。部署只替换首页的Shape/components，保留未知根字段和其他菜单。新增task配置随JAR导出。本次有运行代码变更，须正常重启加载新JAR；不改TAB、Xerr或任务/奖励数据。

## 2026-10-22 移除任务菜单背景并恢复 TAB 详细信息

- 四份 `/quest` 菜单保留 Shape、组件、Lore 和动作，只将服务端标题恢复为普通文字。CraftEngine 的任务日志专用资源和设计目录移至 `Quest/backups/no-texture-tab-20261022`，不再作为有效资源加载；其他 CraftEngine 资源及 Xerr HUD 保留。
- `Quest/tab.yml:enabled` 与 `TAB/config.yml:layout.enabled` 恢复 true，保留原三列任务/玩家布局、页眉页脚及其他设置。旧任务 scoreboard 不启用。源码新安装模板同步开启。
- 已生成/客户端缓存的旧包需在正式服执行 `ce reload pack` 后更新；菜单标题不再引用旧字形。加载新 JAR 可正常重启，已运行的兼容版本也可先 `tab reload` 再 `quest reload` 读取配置。

## 2026-10-21 任务日志与 CraftEngine 箱子背景

- `/quest` 现在向所有持有 `quest.use` 的玩家开放任务首页、分类章节总览、章节任务列表和个人设置。新菜单分别为 `gui/home.yml`、`gui/journal-chapters.yml`、`gui/journal-tasks.yml`、`gui/settings.yml`；只导出缺失文件，不覆盖旧 GUI。
- 上一/下一只查看；进行中的任务可手动追踪，锁定或完成任务可阅读但不能追踪。不增加任务、奖励或独立详情页。原 `main.yml:player-access` 仅属于旧页面，不再限制新日志。
- SQLite 4 → 5 为事务内加表 `journal_options`，保存自动追踪与声音；既有 `hud_preferences` 保存 HUD 模式、提示和追踪 ID。保留旧偏好、任务、基线、领奖和包裹。旧 SMART 读取为常驻，不擅自覆盖旧行。新增语言根 `journal` 按原有追加流程迁移，保留中文注释和未知字段。
- 新 GUI 资源只放正式服务端 `plugins/CraftEngine/resources/quest-journal`，制作脚本及备份在 `plugins/CraftEngine/design/quest-journal`。HUD 资源仍在 Xerr；Quest 源码和 JAR 不含图片/字体。服务端四份菜单标题引用 CE 字形，源码标题保持普通文字。
- 正常重启加载新 JAR，再执行 `ce reload pack` 生成并分发最新整服资源包。不能把隔离服的 CE 包复制到正式服，以免覆盖其他插件资源。真人客户端贴图对齐与缩放观感待验收。

## 2026-10-21 适配 Genlite 燃料仓

- 能源启动的新操作为打开管理面板 → 点击「燃料」打开燃料仓 → Shift 点击背包燃料或放入上方格子。`first_fuel`、目标 ID `fuel`、类型 `genlite.fuel`、目标数量及奖励不变，已有完成与奖励记录保留。
- 成功存入的各交互均经过 `ContainerStock.deposit(FUEL)` → `MachineActions.deposit` → Genlite 原有持久化事务；联动计数仍只在成功存入时提交。取回、错误物品、满仓和无权限不计数。
- 旧「能源启动」默认文案会在加载时增量迁移，自定义目标数量、奖励、注释、未知字段和自定义说明保留。
- 当前正式目录的原版 Genlite JAR 缺少 `GenliteQuestApi`。应正常停服并备份后，同时更新本次交付的 Quest 与 Genlite 联动版 JAR；后者基于最新源码保留燃料仓及刷新/关闭生命周期，只附加原有任务计数接口。单独替换 Quest 无法恢复联动计数。

## 2026-10-21 放置后发放燃料

- 新增 `quests/放置生成器.yml`、稳定任务 ID `place_generator`，放在 `sky_start` 与 `first_fuel` 之间；NPC 交谈仍为第一项个人任务。
- 入岛奖励保留生成器，燃料奖励移动至放置任务。配置迁移使用 `.migrations/generator-placement-v1` 日志，保留旧燃料数量、未知字段和注释；重复启动不会重复迁移。
- 不改变 Genlite 生产逻辑或 API。以本地 API 的已提交机器历史确认放置，空成员参数排除待发物品；历史机器包含已拆除记录，因此老玩家不必重放一次。
- 燃料条目的 `unless-rewarded-by: sky_start` 在同一事务中核对旧奖励包裹；旧 WAITING、SENDING、DELIVERED 燃料均不重复生成新包裹。旧任务、奖励和异常待核对记录不删除，SQLite schema 保持 4。

## 2026-10-21 个人交谈前置任务

- `引导.yml` 配置 Adyeshach NPC 编号，默认 `新人`；不修改已有 NPC 的名称、模型或坐标。Typewriter `0.9.0-beta-177` 为可选对话依赖，无须额外扩展；缺失时降级聊天确认。
- 无空岛的新玩家先在 Tab 看到「初见引航员」，ActionBar 每秒提示交谈；对话时暂停，完成或跳过后停止。随后才回到原共享任务显示，再次右键 NPC 打开 DreamSkyblock GUI。
- SQLite 从 3 升至 4，只新增 `personal_introduction` 表；按 UUID 和个人任务 ID 幂等保存，不改共享任务或奖励账本。已有空岛的老玩家免做引导。
- 语言升级只补缺失的个人引导文案及 `introduction.actionbar`，保留已有文案、自定义字段和注释；重复启动和重载不会重复追加。
- 停服备份 Quest 数据后替换 JAR，保留配置目录；新配置自动导出。只执行 `tab reload` 不能加载 Quest 新代码。正式服未由验证脚本启动、停止或替换。

本次为现有 Quest 增量升级，保留 Kotlin/Maven、SQLite、原任务与奖励引擎、箱子菜单及三个联动适配器。没有增加默认任务数量，没有修改 DreamSkyblock/Genlite API，不改写玩家任务数据库；服务端 GUI 部署均先备份。

## 配置与内容

- 默认章 `sky_awakening`：第一章 · 星海初醒。
- 任务 ID 继续为 `sky_start`、`first_fuel`、`first_resources`，奖励物品和数量全部保留；任务文件改为 `星海启航.yml`、`能源启动.yml`、`第一批资源.yml`。
- 新增 `chapters/第一章·星海初醒.yml` 与根目录 `提示.yml`，以及独立菜单 `gui/chapters.yml`、`gui/main.yml`。章节奖励默认 2 个原版钻石，可自由配置。
- 默认奖励自动发放，玩家无需 `/quest` 或邮箱。管理员保留章节 → 任务列表（Lore 展示要求和奖励）作为检查入口；原 claims/parcels 仅作为内部兼容、防重复与恢复记录。
- 完成前置目标后自动推进；不需要先领任务奖励。未领取的任务完成资格、已生成的奖励载荷均持久保存。

## 文件布局调整

GUI 文件名保持英文：`gui/chapters.yml`、`gui/main.yml`；玩家可见文案保留中文。任务中文文件位于 `quests/`，章节中文文件位于 `chapters/`，不放根目录。

任务和章节资源升级到 `schema-version: 2`，其他资源仍为 1。`quests/` 只保留任务 ID、内容、条件和奖励；每章单独一个 YAML，通过 `tasks` 列表中的 `quest` 和 `requires` 配置依赖。列表顺序只控制展示；多个 `requires` 为 AND。`sequential` 和任务文件的 `prerequisites` 被移除；章节自己的 `prerequisites` 继续引用章节 ID。每个任务归属一章，任务前置限定在本章。

迁移顺序：

1. 旧 GUI 中文文件名与根目录 `章节.yml` 先原文原子搬迁，路径冲突拒绝覆盖。旧章节集合临时路径仍为 `chapters/章节.yml`。
2. 校验所有旧章节、任务引用、归属、重复 ID 和循环；合并旧任务显式前置与 `sequential` 隐式边，生成等效显式 `requires`。旧版仅有任务文件的安装也可迁移到第一章，保留原任务前置关系。
3. 每章拆出独立中文文件，保留 ID、奖励、自定义字段和注释；旧集合根级自定义字段随章节复制。只修改任务版本和依赖块，不重新序列化整份任务配置。旧版非标准章节列表缩进会明确报错并保留原文，需调整为两空格块式列表。
4. 活跃文件改动前，在 `.migrations/chapter-v2/` 原子发布包含所有原文、目标内容和路径的恢复计划，再依次发布章节、清理任务前置、移出旧集合。迁移期间异常可在下次加载恢复，不会从已清理的任务文件重新猜测关系；外部同时修改会拒绝恢复并保留两份内容。
5. 完整成功后写入完成标记，重复加载不重做迁移。保留该目录作备份；正常配置编辑不受已完成计划影响。不操作 SQLite、玩家存档或奖励载荷。

旧版跨章节任务依赖不能无歧义变为新的本章依赖，迁移会明确拒绝，需由服主改为章节前置。已有新章节文件与旧集合同时存在、旧任务前置与新章节冲突时，也会保留原文并拒绝，绝不静默删除关系。

## SQLite 迁移与计数

1. 数据库在独立 JDBC 执行器中事务性新增 `chapter_progress`，将 `PRAGMA user_version` 从 1 升至 2。未知未来版本拒绝加载，迁移失败回滚。迁移写入审计表，重复启动不重复写迁移审计。
2. `facts`、`objectives`、`completions`、`claims`、`parcels` 原有行不删除、不重建。既有任务 `baseline` 和 `progress` 原样保留；旧领奖记录也视为已完成依据。全部完成的岛会在配置装配时识别为章节完成，无需重新完成或领取旧任务奖励。
3. 旧版因等待领奖而没有建立的下一任务基线，升级后等待首次含对应指标的真实快照，再以该快照建立；旧缓存计数和仅打开菜单均不触发初始化。此保守迁移不会追算升级前无法证明属于解锁后产生的历史量。
4. 同一事务先更新单调事实，再完成目标、建立后续基线并完成章节。一次快照内前置完成时发生的累计生产会成为后续任务基线，不重复计入；重复/迟到快照也不会回退再累计。库存转移没有生产累计输入，不能刷进度。
5. 章节奖励使用 `chapter:<章节ID>` 的独立领奖标识；合法任务 ID 不允许冒号，因此不覆盖旧任务记录。唯一键与邮箱载荷同事务提交，多成员竞争只有一次成功。WAITING/SENDING/DELIVERED 状态机和人工核对保持原样。
6. 已完成任务和章节视为持久里程碑，配置提高目标或更改奖励不会自动取消完成记录或重发已领物品；已生成邮箱载荷不随重载变化。管理 reset 仍保留所有奖励记录，不能用于重新领奖。

## 资源迁移

- 仅对三个已知英文任务文件执行同目录原子更名，原文内容保留；两种文件名同时存在时拒绝加载，要求解决冲突，绝不覆盖其中一份。
- 旧语言文件以追加新根块方式补齐章节文案，保留原注释、未知字段与自定义文本。部分已存在但不完整的新语言块会报明确缺键，避免猜测覆盖。
- 精准更新两处旧版默认“领奖后计数”措辞；其他自定义文案不重写。配置更新采用临时文件与原子替换，不支持原子操作或写入失败则报错。
- 自定义旧任务集若超出三个默认任务，需要在 `chapters/第一章·星海初醒.yml` 明确归属；重复 ID、遗漏任务、缺失引用、章节/任务混合循环都会拒绝加载，reload 保持当前运行配置。

## 部署与验收边界

停服备份 Quest 目录、玩家存档及联动插件数据后替换 JAR，保留数据目录。不要清空 `data/quest.db`，不要只回滚 Quest 而保留更高的 Genlite 计数。启动后核对 schema=2、quests=3、chapters=1。

当前自动化与隔离服结果见 [VALIDATION.md](VALIDATION.md)。箱子页面和提示经过真实服务端 API 探针；玩家对象使用代理，尚未使用真人 Java/Geyser 客户端点击。Purpur/Folia、硬断电及实际客户端材质不在已通过范围。Folia 继续明确不支持。

SQLite 与玩家存档仍没有跨存储原子提交；崩溃窗口的 SENDING 记录维持隔离，由管理员核对，不能自动重发。该升级不承诺硬崩溃场景下零丢失或自动 exactly-once。

## 自动发奖升级

新增 `奖励.yml`（schema-version: 1），默认 `automatic: true`，在线空岛每 100 tick 自动检查及重试。无需数据库结构升级，不删除任何旧进度、claims 或 parcels；历史 WAITING 自动补发，SENDING 保留待核对，已发奖励不重复创建。旧版默认领奖说明只作精确文字替换，未知键、自定义颜色及注释保留；自动发奖关闭时不执行该文案迁移。

## 两页 GUI 升级

`gui/chapter-detail.yml` 的自定义 Shape 和组件迁入 `gui/main.yml`（schema-version: 2），原任务中心和任务详情页面退休；不再导出 `detail.yml` 或 `chapter-detail.yml`。旧 main、detail、chapter-detail 的完整原文及目标文件先写入 `.migrations/gui-v2/`，随后发布新 main 并移除旧页面；中断可重放，用户并发修改发生冲突时拒绝覆盖。已有新 main 与旧 chapter-detail 并存且无恢复记录时拒绝猜测，以免覆盖用户配置。

仅保留 Shape 使用的组件；退休的邮箱和预览动作从旧布局中移出，不恢复已删除按钮。任务不再可点击进入详情，要求和奖励改由 Lore 的整行展开标记显示。迁移保留注释、未知根字段、现存普通玩家菜单开关和剩余按钮位置。数据库及自动发奖流程不变。


## 2026-10-21：可选 Xerr HUD 第一版

安装新的 Quest JAR；保留现有 Quest 数据目录。HUD 的全部素材与布局仅部署到服务端 Xerr；源码与 Quest JAR 不包含它们。`Xerr/quest.yml` 使用 schema-version 1，不存在时关闭 HUD。语言升级只追加缺失的 `hud` 根块，保留未知字段、原文和注释；不修改数据库版本、旧 HUD 偏好表、任务进度或奖励记录。配置错误拒绝加载，不覆盖原文件。

修改 Xerr 的布局、字体和图片后须重新生成 Xerr 包，再由 CraftEngine 合并及分发。仅 `quest reload` 不会生成资源包。回退可恢复 `plugins/Xerr/backups/quest-v1-*` 中原文件，对 manifest 标记为 null 的本次新增文件单独移出，并恢复原 Quest JAR；不要回滚玩家数据库来回退显示。


## 2026-10-21：侧栏集中任务信息

使用本次Quest JAR并正常重启；普通TAB列表与原页眉页脚保留，关闭Quest/tab.yml和TAB自定义任务layout、旧任务专用scoreboard。Xerr侧栏改为原生10px、156单位宽、半透明背景，读取原任务description的编号步骤。顶部通知继续使用原生12px。资源、字体和制作脚本仍只放在服务端Xerr；Quest JAR不含材质。

语言schema仍为1，已知旧TAB提示按 `.migrations/sidebar-text-v1` 幂等迁移，保留原文备份和用户其他字段/注释。不改任务/章节图或数据库版本。完成重启后执行 `ce reload pack` 合并已暂存的Xerr生成包并发送客户端。
## 2026-10-22：合并任务追踪开关

设置页合并 HUD 与自动追踪为一个“任务追踪”按钮。关闭时同时隐藏 HUD、停止自动追踪并保留已选任务；点击进行中的任务会一次保存目标、开启 HUD 与自动追踪。任务提示与声音仍独立。启动时 `tracking-hud-v1` 迁移删除旧自动追踪组件及其 Shape 占位，仅更新已知默认文案，保留其他配置与注释并备份原文；失败保留恢复记录。

## 2026-10-22：任务按章节分文件夹

默认四个任务放入 `quests/第一章/`，仍为一任务一文件。递归读取 `quests/` 下所有 YAML，允许后续新增章节文件夹，也保留根目录自定义任务。旧版四个默认任务通过 `quest-folders-v1` 原文迁移并备份，目标冲突拒绝覆盖，任务 ID、章节引用和数据库不变；默认导出及生成器升级识别子文件夹已有任务，避免重启后重复生成。

## 2026-10-22：单分类按钮与局部更新

章节首页分类控件合为一个，按主线→支线→特殊循环切换，切换重置页码但保留 Inventory 与会话。渲染对比完整 ItemStack，仅写入变化槽位，不清空重填菜单；章节、页码、空分类及点击动作同步更新。`category-button-v1` 迁移保留最上行靠中间的分类位置，其他分类位恢复填充，保留章节格、其他组件和未知字段，保存原文并支持恢复。新玩家默认 HUD/自动追踪关闭，主动开启追踪后显示；已有保存偏好不强制修改。

## 2026-10-22：服务端任务设置卡片

新增可选菜单字段 `item-on` / `item-off`，仅用于已有追踪、提示、声音开关且必须成对配置；`pack-only` 必须为布尔值。加载资源包后根据个人状态选择图标，未加载时使用原 `item`，透明热区不显示。配置重载验证状态物品 ID，仍使用完整 ItemStack 比较仅更新变化槽位。

本服第一版卡片通过 imagegen 生图，以单张透明图集和六个 UV 模型提供 ON/OFF，配合原生尺寸深蓝底板及独立字体。实际素材、菜单部署候选、原生验证和制作脚本只在服务端 CraftEngine `resources/quest-settings-cards` / `design/quest-settings-cards`，不纳入源码/JAR。新 Quest JAR 暂存 `plugins/update`，旧菜单及 JAR 已备份；完整重启后执行 `/ce reload pack` 并让客户端接受资源包。

## 2026-10-24 最新版本发布

- Quest 与 Genlite 配套更新：主支线 HUD 并存、TAB 支线目标、菜单局部分页、逐任务自动领取与 Genlite 教学接口。
- Genlite 主仓库已内置 `GenliteQuestApi`（含 `TeachingMachine`）、成功操作计数与提交事件；不得替换为缺少该接口的旧构建。
- 构建准备工具会识别依赖仓库中的原生教学接口并使用当前源码，避免重放旧覆盖文件。
- Leaf 停服期间已备份并直接替换 `plugins/Quest-1.0.0.jar`、`plugins/Genlite-1.0.0.jar`，清除这两个插件已处理的更新目录副本。DreamSkyblock 当前已是配套接口版本。
- 启动 Leaf 后加载新 JAR；不使用热重载。保留现有任务配置、Xerr/CraftEngine 资源、玩家数据库与奖励记录。
