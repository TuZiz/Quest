# 章节系统升级验证记录

## 2026-10-22 GitHub 整体提交前验证

- Java25执行Maven离线package，80项测试全部通过；最终JAR检查确认仅三份GUI，不含Paper/Bukkit API、图片素材、源码或制作脚本。
- 待提交文件未发现明显Token/私钥或大体积二进制文件；构建目录、缓存和备份沿用.gitignore排除。既有systemPath、JDK及shade重复元数据警告仍存在。

## 2026-10-22 底板客户端图集限制补验

- 实际客户端JAR字节码确认FontTexture图集为256×256；此前仅检查生成包RGBA一致，未检查单字形上限，验证范围不足。
- 修复后的运行贴图为176×126。隔离服原生菜单及CraftEngine生成包验证通过，增加字形尺寸上限、运行图RGBA一致性与字体height126/ascent13检查。后续用户截图显示底板和连接式返回按钮正常出现。
- 用户最终选择暂时保留高亮；未部署客户端模组或全局着色器替换。

## 2026-10-22 上半菜单、连接返回及浏览器隐藏

- Java25 Maven package，MenuVisualStatesTest/ResourcesTest/JournalTest共8项通过，新增pack-item合法ID与非法类型诊断。资源包按钮使用该ID，无包保留原item。
- 隔离Leaf26.3/CraftEngine26.9.2真实状态物品/ItemMeta：45卡片热区、48/49/50返回、左下角无信息按钮、返回图标透明且无包回退指南针、六状态和局部槽位更新、相同重绘零写入、递归非斜体通过。
- 原生浏览器分类集合与原all_items结果去掉quest_settings后的集合完全一致；初次宽泛排除minecraft命名空间误伤其10个destroy_stage辅助项，检查发现后改为保留这些当前版本既有项，最终检查通过，没有删除物品注册。
- CraftEngine生成/校验/ZIP通过，provider height126/ascent13；新图1482×1061按126高显示宽175.996，终点126小于玩家背包标签127。ZIP图RGBA与未修改的生成原图完全一致，23份实际TextRenderer Component已导出。证据在服务端CraftEngine/design/quest-settings-cards/top-only-v3。
- 新JAR已暂存plugins/update且哈希与隔离服测试JAR一致，源GUI/字体配置/图像/过滤配置已同步；正式服未重启或发包，实际客户端最终显示仍待确认。深蓝HUD、TAB、任务奖励和玩家数据库不改。

## 2026-10-22 回退导航与新六状态图集

- 撤回同窗口导航实现及其固定标题测试，恢复上一版源路径；Java25 Maven package 和 JournalTest/MenuVisualStatesTest/ResourcesTest 共7项通过。
- 隔离Leaf26.3同次运行 JournalSmoke 与 CardsSmoke 通过：原章节→任务→返回章节→设置共4次独立窗口打开；分类复用同一库存；追踪及三个开关正常保存、关闭HUD保留所选任务。162个真实ItemStack递归非斜体、13章分页、相同渲染零写入、局部槽位比较继续通过。
- 新imagegen图集1133×1388，SHA256=f4770273a1084dd82f93211ce7d1a75f52b2a097805289935c6326e593f0e30c。直接复制生成原图，通过模型UV选择六卡，没有编辑图片。六CE状态、45热区、独立卡片更新及无资源包原版回退通过。CraftEngine生成/校验/压缩通过；ZIP图集解码RGBA与新图一致，六模型与源JSON一致。
- 服务端证据为 `CraftEngine/design/quest-settings-cards/regenerated-v2/verification.log`、`manifest.json`、`imagegen-prompt.txt`；该功能的验证资料保留。
- 正式服仅同步源图集/模型及暂存JAR；没有正式服重启、资源包发送或玩家数据变更。真实Minecraft客户端的最终样式和交互仍待确认。生成图色彩受AI纹理影响，不宣称与目标色号逐像素相等。

## 2026-10-22 当前参考色板主题验证

- `mvn -o package -Dkotlin.compiler.daemon=false -Dtest=HudColorMapTest,QuestHudTest,MenuVisualStatesTest,JournalTest,ResourcesTest`：17项通过。包含可选颜色表兼容、RGB归一化与非法配置诊断。
- Leaf26.3 / Java25 / Xerr2.2.0 / CraftEngine26.9.2 隔离服：六状态原生物品、45格整卡点击、独立区域更新、相同渲染零写入、实际 ItemMeta 递归非斜体、无资源包原版回退通过；原生 TabooLib Components 颜色映射、字面转义、10px/12px真实定位字体、完整步骤、通知清理与槽位恢复通过。
- 顺序运行 Xerr pack 与 CraftEngine generate/validate/zip，通过；合并包内参考图集、底板、三张卡片、侧栏及完成通知共7张PNG解码RGBA与源素材一致。设置页25份实际共享 TextRenderer Component 校验无问题。
- 证据在正式服 `plugins/CraftEngine/design/quest-settings-cards/warm-verification.log`、`warm-neuron-report.json`、`warm-deployment.json`。HUD探针日志沿用了旧的“TAB bridge off”描述；本次已移除该旧断言，TAB配置没有改动，不能将这段历史标签当作TAB关闭的证据。
- 最新快照641文件SHA256与额外Xerr2.2二进制校验通过。旧备份清理被自动审批拒绝，仍保留。正式服只同步配置/源资产及暂存JAR，没有重启、发送包或操作玩家数据库；真实Minecraft客户端视觉与缩放未验收。

验证日期：2026-10-19。以下记录本次真实执行结果，尚未执行的客户端和故障注入不视为通过。

## 构建与测试

环境为 JDK 25.0.2、Kotlin 2.3.10、Maven 3.9.12、Paper API 26.3.build.28-alpha，隔离服为 Leaf 26.3-4，CraftEngine 26.9.2。Folia 继续不支持。

| 范围 | 结果 |
|---|---|
| Quest | `mvn -o -Dkotlin.compiler.daemon=false clean package` 通过；任务依赖归章后再次 `package`，最终 26 项测试全通过 |
| 自动推进 | 未领取任一任务奖励即可连续完成三任务；章节完成独立于任务领奖；同一快照完成前置并建立后续基线时，历史生产不计入 |
| 累计防重 | 重复与迟到/回退快照不重计，31/32 边界正确；来源未就绪及菜单查询不初始化累计基线 |
| 领奖与恢复 | 40 次章节领取竞争、20 次任务领取竞争仅一次成功；重启后不重领；章节与任务记录隔离；邮箱 SENDING 竞争与人工恢复保持正确，重载不改变奖励载荷 |
| SQLite v1 → v2 | 真实旧表结构夹具迁移：既有基线 100、进度 12 保留并正常推进至 20；旧完成/领奖记录、WAITING/SENDING/DELIVERED 记录保留；已全部完成的岛识别章节完成；未知未来版本拒绝且原数据保留；重复启动审计不重复 |
| 配置资源 | 默认仅一章三任务，中文任务名及文件名；YAML、版本、菜单映射、语言引用、RGB、章节/任务混合 DAG 校验通过；重复 ID、缺引用、环均拒绝；分支与跨章前置测试通过 |
| 资源迁移 | 三个旧英文文件原子更名；冲突不覆盖；语言新块追加保留中文注释/未知字段；旧计数提示精准更新；重复迁移幂等，未来资源版本不改写 |
| Genlite 回归 | `.build/dependencies/Genlite` 执行 `mvn -o -Dkotlin.compiler.daemon=false test`，105 项全部通过，包含成功事务计数、实际生产与持久化；未修改其源代码或接口 |
| DreamSkyblock 回归 | `.build/dependencies/DreamSkyblock` 执行同样测试，81 项中 80 通过、1 项因宿主不允许创建符号链接而跳过，无失败；未修改其源代码或接口 |
| 联动隔离服 | 公开 API 就绪；5 个原 CraftEngine ID 与真实 ItemStack 匹配；背包满拒绝和空槽规划通过 |
| 新 GUI 与提示 | `CHAPTER_GUI_PASSED`：用真实服务端 ItemStack 渲染章节列表、章节详情和原任务奖励预览；三个任务入口、章节领奖入口、章节奖励预览存在；最终名称/Lore 递归非斜体；玩家代理捕获 Title、音效、聊天调用，全关闭时无调用 |
| 隔离服旧版本升级 | 从首提交 `928dfd7` 准备旧资源和 v1 数据库，启动自动升级；三条完成记录、旧 sky_start 领奖、legacy-sending 奖励全部保留；章节已完成、中文任务文件存在、旧英文文件已更名 |
| 重载与停止 | 注入非法任务 YAML 后 reload 拒绝，debug 保留三个旧定义；恢复配置后成功 reload；`stop` 正常退出码 0。错误日志来自主动注入，无未修复启动错误 |
| 缺少可选插件 | 隔离服仅启用 Quest：三个集成 missing，无 NoClassDefFoundError，指令诊断正常，stop 退出码 0 |
| Neuron | task_c51d4024faa64a78a6bb；本地检查与 MCP validate_task 通过，validation_id=check_2a42a9ab972349108fc2；91 个真实 TextRenderer Component 样本，issues/pending 均为空。此检查验证提交材料，不等于真人客户端验收 |
| 最终 JAR | 14,029,498 字节，包含中文任务与章节资源，无旧英文任务资源、Paper/Bukkit/第三方插件 API、JUnit、探针、源码或缓存。源码职责检查：最大的主源文件 QuestFiles 为 127 行 |

## 证据位置

- `target/surefire-reports/`
- `.build/dependencies/Genlite/target/surefire-reports/`
- `.build/dependencies/DreamSkyblock/target/surefire-reports/`
- `target/verification/components.json`
- `target/verification/chapter-neuron-report.json`
- `target/verification/chapter-evidence.json`
- `target/verification/legacy-smoke-fixture.json`
- `target/verification/legacy-smoke-console.log`
- `target/verification/smoke-console.log`
- `target/verification/optional-smoke-console.log`

隔离服全部位于 `target/smoke`，旧数据为人工构造测试夹具，没有复制或操作正式玩家数据，没有将 JAR 部署到正式服务器。默认数据、图标和奖励 ID 沿用原项目；联动源码基线与兼容说明见 COMPATIBILITY.md。

## 已有警告与未验证范围

Maven 本地 systemPath、Kotlin/JDK Unsafe、测试 SLF4J/SQLite native-access、Shade 的 MANIFEST/module-info 重叠以及 DreamSkyblock 原有 Bukkit 弃用警告仍存在，不属于测试失败；本次新增源码无编译警告。DreamSkyblock 的 1 项跳过为已有环境限制。

- 未接入真人 Java/Geyser/Floodgate 客户端，未实测菜单点击、资源包材质和高延迟。GUI 探针使用玩家代理，不宣称已通过真实客户端验收。
- 未运行 Purpur；Leaf 26.3 是已测组合。未增加 Folia 支持或其他 Minecraft 版本声明。
- 未用真人创建/邀请空岛、手动存入燃料或生产；验证为公开 API、既有源事务测试与不可变累计快照的服务层回归。
- 没有硬断电/操作系统杀进程故障注入。SQLite 与玩家存档缺少跨存储原子事务；SENDING 保持隔离，由管理员核对。不能声称任意硬崩溃下零丢失或自动 exactly-once。

升级步骤和基线迁移取舍详见 UPGRADE.md。首次正式升级应停服备份并保留原数据目录，再在测试环境完成两位仅有 quest.use 的成员从任务链到独立章节领奖的客户端验收。

## 文件布局调整复验（2026-10-19）

GUI 配置文件改为 `gui/chapters.yml` 和 `gui/chapter-detail.yml`，章节配置移至 `chapters/章节.yml`。同步加载器、诊断路径、组件导出测试和说明文档；菜单中文文案、中文任务文件与全部稳定 ID 保持不变。

执行 `mvn -o -Dkotlin.compiler.daemon=false clean package`，17 项测试全部通过。新增测试覆盖三条旧路径的原文搬迁、中文注释与未知字段保留、重复执行幂等，以及新旧路径冲突时两份文件均保留。最终 shaded JAR 已检查：包含三条新路径，不含旧路径。此次只调整资源布局，未重复运行上一轮联动实服或客户端验收；前述隔离服记录属于上一轮实现证据。

## 任务关系归章复验（2026-10-19）

当前格式：`quests/` 只定义任务内容；`chapters/第一章·星海初醒.yml` 独立定义第一章，以 `tasks.quest/requires` 配置本章任务前置。任务/章节 schema=2，其他资源 schema=1，SQLite user_version=2。旧 `sequential` 只在迁移代码中读取。

- Maven `clean package` 及后续最终 `package` 通过，26 项 JUnit 零失败、零跳过。新增覆盖单章独立解析、多章引用、展示顺序不影响依赖、分支与 AND 汇合、同快照累计基线、重复/缺失/循环/跨章任务引用拒绝。
- 迁移覆盖原始仅任务安装及旧章节集合拆分；ID、任务和章节奖励、未知字段、中文整行及行尾注释保留；CRLF 旧文件兼容；中断后从持久计划恢复；外部修改冲突时拒绝覆盖；章节名与旧集合文件重名时不会误删新章节。配置迁移的数据库字节不变断言通过。
- 在 `target/smoke` 生成隔离夹具，使用首提交 928dfd7 的真实任务配置、schema1 章节集合和人工 SQLite v1 数据，启动 Leaf 26.3-4。新章节文件和 task schema2 正常加载，注释/自定义字段保留，旧集合移出活跃目录，恢复计划标记完成。
- 隔离服迁移后核对原累计 baseline=100、progress=12，三个完成记录、sky_start 领奖和 SENDING 奖励均保留，旧岛识别章节完成。API 就绪、5 个真实 CE ID、实际 ItemStack 章节菜单/提示探针通过；非法 YAML 重载拒绝且保持旧定义；服务器正常停止。
- 新文件布局及最终 JAR 资源检查通过，不含旧集合、英文任务文件、服务端 API、测试类或源码。源码最大文件 QuestFiles 为 127 行；章节解析、提示解析、图迁移与文件恢复计划分别归属独立文件。

证据：`target/surefire-reports/`、`target/verification/upgrade-fixture.json`、`upgrade-result.json`、`smoke-console.log`、`components.json`。本次未修改联动插件源码，未重跑前述上游套件；其结果为此前阶段记录。旧阶段证据可能已被 clean 清理，不能当作本轮新日志。没有接入真人 Java/Geyser 客户端，实际菜单点击及材质验收仍待进行；Folia 继续不支持。

本轮 Neuron：task_1262a9b3c507498eafa1，validation_id=check_9bacda95279d4f31b514。本地检查和 MCP 检查均 passed，91 条文本、98 个真实 TextRenderer 组件样本，issues/pending 为空。报告为 target/verification/chain-neuron-report.json；检查范围为提交源码、资源与真实测试组件，不代表客户端实测。

最终 JAR 再次启动同一隔离服通过；迁移幂等，schema-migration 审计仍只有一条，baseline=100/progress=12、原完成/领奖/SENDING 记录不变。初次升级日志为 upgrade-smoke-console.log，最终重启日志为 smoke-console.log。
# Xerr HUD 增量验证 · 2026-10-19

- Maven package 成功，29 项 JUnit 测试零失败（含只读跟踪、未领奖继续追踪、全部领取后隐藏）。
- Leaf 26.3 隔离服：Xerr 2.1.0 + PacketEvents 2.14.0，五类弹窗 ID 与实际字体/图片/变量渲染通过；原章节菜单、物品奖励及非法配置重载保留旧状态回归通过。服务器正常退出。
- Xerr 生成资源包、CraftEngine 26.9.2 原生目录合并/验证/压缩通过；8 张 Quest PNG 解码后逐像素和透明度一致。CraftEngine 会优化 PNG 编码，不使用压缩文件字节相等代替视觉数据检查。
- 缺少所有可选依赖（含 Xerr）的隔离服启动及关闭通过。Quest JAR 不包含 Xerr 类、图片或字体；服务端材质由 Xerr 独立维护。
- 初次测试下载语言文件等待、未暂存 PacketEvents 的测试环境缺类、Xerr 多字号复制同一 TTF 的竞争均已定位；最终测试使用缓存、完整依赖及单字体配置通过。原 Windows OSHI 性能计数器警告仍属已有环境问题。
- 未执行真人 Java/Geyser 客户端显示、点击或资源包拒绝/重新加载操作；API 渲染代理不作为客户端验收。没有操作正式玩家数据库。

## 正式服启动失败修复（2026-10-19）

正式服日志确认：旧暂存源码生成的 Genlite 补丁仍要求英文机器文件，导致中文配置校验失败并停用；Quest 随后因依赖停用而暂停任务与 HUD。修复兼容源码生成流程：每次从当前联动项目重新生成覆盖文件，清空一次性源码暂存目录，依赖执行 clean package，保留当前 Genlite 的中文文件和物品名称解析。

- 更新后的 Genlite 110 项测试、Quest 29 项测试全部通过，无失败或跳过。
- 复制正式服全部 12 个 Genlite YAML 到隔离服，Leaf 26.3 启动通过；Genlite、Quest API、Xerr 五类通知及字体/图片/变量渲染探针通过，非法重载保留原任务配置，正常退出。
- 正式服 12 个 YAML 的 SHA256 全部不变；隔离启动也没有改写这 12 个副本。未操作正式玩家数据库。
- 更新的 Genlite JAR 放入正式服 plugins/update/，供下一次完整重启加载；当前进程中已经暂停的 Quest 不能只靠 xerr reload 恢复。未停止正式服务器，重启后的真实客户端显示仍待验证。

后续观察到正式服于 18:12 再次启动，Genlite 配置/JAR 已被外部更换为旧英文版本，DreamSkyblock 也已更换；新日志报 HomeMembershipCommittedEvent 缺失。保留这些外部文件变更，为下次重启同时暂存测试过的配套 DreamSkyblock 与 Genlite JAR。上述 12 文件不变结论对应首次配置副本验证时点，不表示外部更换后仍与原快照相同。

配套 DreamSkyblock 使用 Java 25 重新构建通过，81 项测试无失败或错误，1 项已有环境限制跳过。首次误用 Java 21 运行 Java 25 测试类导致版本错误，切换至目标 JDK 后通过；此环境失败不计为业务测试失败。

证据：target/surefire-reports、.build/dependencies/Genlite/target/surefire-reports、target/verification/smoke-console.log、.build/hud/live-genlite-config-hashes.json。

## HUD 位置及字体图集修复（2026-10-19）

用户截图确认文本位于右下且面板缺失。读取实际客户端 26.2 的 FontTexture/GlyphStitcher/FontSet 字节码，确认 256×256 图集容量与超大字形无法烘焙；客户端加载缓存与服务端 CE 包 SHA1 相同，包内源图仍为 677×719、950×161，因此不是图片未合并或客户端仍用旧包。Xerr 的坐标及图片字体编码也通过实际 JAR 字节码核对。

服务端配置锚点从右中改为右上，运行 PNG 转换为 151×160、236×40；修复前文件备份于 Xerr/backups/Quest-HUD-atlas-20261019-182652，原设计稿保留。没有修改任务、奖励或玩家数据库。

修正先前探针使用非锚定模式且仅验证变量的问题：现在使用与 Bossbar 相同的锚定渲染，断言图片字体及定位标记存在，导出 xerr-tracker-render.txt。资源包验证增加所有 Quest bitmap provider 源字形大小检查，继续核对 CE 合并 PNG 像素与透明度。API/包检查不替代真实客户端显示验证。

实际运行 tools/run_smoke.py、tools/run_xerr_smoke.py 均通过：锚定渲染、变量、非法重载保留原任务状态通过；8 张 Quest 贴图合并后像素一致，958 个 bitmap provider 的单格源尺寸均不超过 256×256，隔离服正常退出。正式服 RCON 未启用，没有发送重载命令或主动停服；当时需要重载 Xerr 并重新加载资源包，真实客户端结果待确认；该集成现已退役。此次仅修改服务端素材/锚点与验证脚本，没有重新构建或更换 Quest JAR。

用户后续截图确认贴图已显示但位置仍旧。检查正式服 Xerr 与 CE 包，均仍含右侧中线的旧着色器；Xerr 生成 ZIP 时间未更新，虽已重载配置，CE 仍合并旧生成内容。核对正式服/隔离服 19 个输入文件 SHA256 完全一致，提取前轮验证通过的 Xerr ZIP 到同卷暂存目录，备份并替换正式服生成目录及 ZIP，51 个输出逐字节与验证 ZIP 一致，清除旧输出目录残留，右上锚点着色器不再含 50% Y。没有重发正式服命令、停止服务器或改动玩家数据；只待用户执行 ce reload pack，客户端位置仍未确认。部署记录：.build/hud/generated-sync-evidence.json。


## 默认自动发奖及 HUD 整理（2026-10-19）

- JDK25 `mvn -q -o -Dkotlin.compiler.daemon=false package` 通过，32 项 JUnit 无失败、错误或跳过。新增覆盖岛级自动领奖并发、手动竞争、重启后不重复生成、SENDING 保留、物品缺失后恢复、配置范围和提示迁移幂等。
- `tools/run_smoke.py` 隔离 Leaf26.3 启动、API/菜单/背包容量探针、错误重载保留原定义通过，正常退出。新探针最初反射内部 LayoutAPI 遇访问限制，改为已验证的公开 XerrLayoutAPI 后通过；这不是正式服异常。
- `tools/run_xerr_smoke.py` 对最终 JAR 通过：实际锚定 Component JSON 验证 RGB，常驻行居中、面板 x=-151/y=-44、通知单槽 x=-236/y=0；Xerr 构包与 CraftEngine 原生合并的 8 张图片 RGBA 一致，26 个 bitmap provider 不超过 256 图集限制。
- 正式服 Xerr 的 19 个输入文件与隔离服 SHA256 一致后，同步已验证的 51 个生成文件并备份旧包。奖励配置默认开启、普通玩家菜单关闭、旧领取提示精确替换；不改写玩家数据库。材质和布局仅在服务器 Xerr 文件夹。
- 新 Quest JAR 已放入 `plugins/update/Quest-1.0.0.jar`，没有停止或重启正式服，也没有发送 CE 重载命令。需要完整重启并重新合并/发送资源包。真人客户端最终像素位置及在线自动入包尚待验证；代理测试不代表实机验收。Neuron 对已提交检查无问题，因该实机范围未执行记录为 pending。
- 证据：`target/verification/auto-rewards-evidence.json`、`auto-rewards-deployment.json`、`xerr-tracker-components.json`、`auto-rewards-neuron.json` 及两份隔离服日志。

## GUI 精简与单引号文案（2026-10-19）

- `mvn -q -o clean package -Dkotlin.compiler.daemon=false`（JDK25）通过，35 项 JUnit 零失败、错误或跳过。新增 GUI 迁移测试覆盖原文备份、Shape/注释/未知字段保留、未使用组件清理、幂等、中断恢复及冲突拒绝。
- 隔离 Leaf26.3 新安装与旧 GUI 升级两种启动均通过。真实 ItemStack 探针逐项验证三任务目标文本和数量、CraftEngine/原版奖励真实名称与数量、所有 Lore 及嵌套翻译参数非斜体；任务不再产生详情页链接，没有邮箱或奖励预览按钮。错误配置重载保留当前有效任务，服务端正常退出。
- YAML AST 检查两个 GUI 的字符串均使用单引号，数值和布尔值仍保持原类型；最终 JAR 的 GUI 资源仅为 `gui/main.yml`、`gui/chapters.yml`，不含退役页面、服务端 API、测试代码、源码或材质。
- 服务端旧 GUI 已完整备份到 Quest/backups/gui-two-pages-20261019-190532，再部署用户当前的两页布局；新 JAR 放入 plugins/update。未改动玩家数据库，也未停止或重启正式服。
- 未重复验收未修改的 Xerr 资源包；真人 Java/Geyser 客户端点击仍待重启后确认，代理渲染不代表客户端验收。
- 本次证据：`target/verification/gui-evidence.json`、`gui-deployment.json`、`gui-neuron.json`、`components.json`、`smoke-console.log` 和 surefire-reports。


## 常驻 HUD 完成状态与启动恢复（2026-10-19）

- 只读查询正式服状态确认三任务、章节和奖励均已完成；旧投影在没有 ACTIVE 任务时返回空值，主动移除面板。此次完成章节保留常驻状态，文案来自语言文件，不重置进度或领奖记录。
- JDK25 Maven package 和 37 项 JUnit 通过，零失败/错误。覆盖完成后已领/未领奖、进行中任务优先、后续锁定章、空任务以及新增语言区块幂等补齐与自定义文案保留。
- 最终 JAR 在 Leaf26.3 隔离服通过 HUD_STARTUP_PASSED：代理玩家模拟既有 SUCCESSFULLY_LOADED 状态补读、ACCEPTED 不显示、重载保留与退出清理；原菜单/五种 CE 物品/API 及非法重载保留回归通过，服务端正常退出。
- Neuron 本地与任务绑定验证通过；实际共享 TextRenderer 导出 48 项语言 Component。新增完成文案 RGB、单引号；JAR 不含服务端 API、测试源码或材质。
- 正式服旧 JAR 已备份，新 JAR 暂存 plugins/update/Quest-1.0.0.jar；语言区块由启动加载器仅补缺失项。未修改正式玩家数据库、未停服或重启、未改动 Xerr 材质，因此无需重新生成资源包。
- 真人客户端重启后的实际显示仍待确认；代理生命周期验证不等于客户端像素验收。证据见 target/verification/startup-hud-*.json、smoke-console.log 和 surefire-reports。


## Xerr 字面颜色与静态 HUD 定位（2026-10-19）

- 截图复现两处问题：文字 value 中 SimpleComponent 方括号颜色被字体包装转义并计入宽度；旧静态 appendLayout 将中间 ui_position_N 字体直接发给客户端，没有经过 PositionedText 的载体字体/位置编码。不是任务进度异常。旧测试只搜索 JSON 中颜色字符串存在，未判断真正样式，不足以证明正常显示。
- Quest XerrDisplay 使用 Xerr 原生 ComponentBuilder → PositionedText → appendLayoutRaw；Xerr2.1 已核实字节码的三个变量出口补充 MiniMessage颜色处理，位于宽度计算之前。工具校验原类 SHA256、拒绝未知类/重复补丁/原地覆盖，只新增一个辅助运行类，不附带服务端 API 或额外库。
- JDK25 Maven package、37 JUnit 零失败/错误。Leaf26.3隔离服捕获实际 Quest update 提交到 BossbarHudContext 的最终数据：无中间数字定位字体、无字面格式，最终载体字体与着色器 RGB 载荷有效。同步和异步原生布局、启动/重载/退出、菜单/CE/API和非法重载回归通过；隔离服正常退出。
- 重新执行 Xerr 原生生成和 CE 合并，8 张任务 PNG 像素及透明度一致、26 个 bitmap provider 符合256像素图集限制、4 个最终字体 JSON 和26个定位着色器文件语义/内容一致。JSON比较按解析结构进行，CE会压缩编码，不能以字节变化误报失败。
- 先核对正式服所有 Xerr/core、CE/resources及其他实际存在的外部合并输入与隔离副本哈希相同，再备份部署 Xerr/CE生成输出；原贴图不改，服务端两个布局改用单引号 MiniMessage，重复迁移字节不变。两 JAR 暂存 plugins/update，未重启正式服，未改玩家数据库。
- 正式备份：plugins/Quest/backups/xerr-render-20261019-193802；布局原文：plugins/Xerr/backups/quest-minimessage-20261019-193735。未进行真人客户端像素、不同 GUI 缩放及 Geyser 显示验收；自动化验证不作实机声明。
- 证据：target/verification/xerr-render-*.json、xerr-tracker-final.json、xerr-tracker-async.json、xerr-smoke-console.log、smoke-console.log及surefire-reports。该旧版 Xerr 补丁现已退役。


## 移除HUD并改为聊天完成提示（2026-10-20）

- 移除Xerr编译/运行依赖、HUD渲染及轮询、玩家HUD指令、贴图生成安装器与兼容补丁。原HUD测试随功能删除，保留现有任务、奖励、章节和迁移测试；当前36项测试及Maven构建通过。
- 新增任务与章节完成的真实TextRenderer/Player调用验证：只调用sendMessage，不访问资源包状态、Title、ActionBar或音效；无进度变化不提示。
- 提示配置1→2迁移验证：仅两类完成事件默认聊天开启，保留其他事件、用户未知字段及注释、迁移前备份，重复执行不改；未来版本拒绝覆盖。旧数据库版本及HUD偏好行仅保留历史数据，不再加载HUD功能。
- 无Xerr的隔离Leaf通过启动、三方API/五种CE物品、两套箱子菜单、帮助和Tab补全、聊天完成提示及坏任务配置重载回滚。故意放入损坏的旧显示.yml不影响启动。
- JAR确认无HUD类、显示.yml、Xerr软依赖或其API字节码引用。服务器Quest专属Xerr布局、图像及设计配置移动到Xerr/backups/quest-hud-retired-*；其他插件资源与共享字体不改。新JAR预置plugins/update，未重启正式服、未修改玩家数据库或CE当前托管包。

## TAB 左侧任务与右侧玩家（2026-10-20）

- 用户提供的 TAB 6.1.0 在隔离 Leaf 26.3 启动时明确报告仅支持至 26.2，API 未初始化；未将不兼容 JAR 部署正式服。改用官方 TAB 6.2.1，下载文件 SHA-256 与 GitHub release asset digest 一致：`0b6a21af6fd3b113c05821daaed00ff92234a84ca94b29b3aec0ef1bd313b8a9`。
- Maven 离线构建与 42 项测试通过。新增 6 项验证覆盖中文换行、当前未完成目标、后续任务切换、完成/无岛/不可用状态、同岛只读共享与不发奖励、语言幂等追加及布局槽位。旧提示测试硬编码了已被用户修改的章节名，改为验证配置中的真实名称，保留用户章节修改。
- 最终 Quest JAR 在隔离 Leaf 26.3 + Java 25 + TAB 6.2.1 下验证：40 个固定布局槽位、21 个固定条目、19 个右侧玩家槽位、溢出人数开关、18 个原生按玩家占位符、玩家之间缓存隔离及实际 TAB MiniMessage 解析；未启用 TAB 侧边计分板或 BossBar。
- `tab reload` 与 `quest reload` 后重新验证通过；隔离服务器正常停止。移除所有可选依赖后 Quest 单独启动与管理命令路径通过，正常退出。证据：`target/tab-build.log`、`target/verification/tab-smoke-console.log`、`target/verification/optional-smoke-console.log`、Surefire 报告。
- 默认不使用任务 ActionBar。任务投影只读取原空岛 journal，不修改业务模型、Genlite 生产、奖励发放、SQLite 表或 schema。TAB API 仅编译使用，不打进 Quest JAR。
- 真人客户端的两列宽度、长任务观感、多人实际名单溢出与基岩版 Tab 可见性未实测，保持待验收；服务端解析和配置检查不等同于这些客户端结果。Quest 的 Folia 不支持声明保持不变。
## 2026-10-20 TAB 三列调整

- 布局调整为左侧任务、中间和右侧玩家：60 槽，22 个固定槽、38 个玩家位；保留原 18 个任务占位符和人数溢出提示。
- `mvn -o -Dkotlin.compiler.daemon=false -Dtest=TabQuestProjectionTest test`：6 项通过，无失败；SQLite 原有 native-access 警告不影响测试。
- `python tools/run_tab_smoke.py`：真实 TAB 6.2.1 + 隔离 Leaf 26.3 启动、TAB 重载、Quest 重载均通过，已校验两个玩家槽位区间与原生占位符；隔离服务器正常停止。
- 已备份后精准更新正式服 `plugins/TAB/config.yml`，只改槽数、新增第三列标题及玩家区间，其他配置逐项比较保持不变。本次只修改布局，无需替换 Quest JAR；运行中的正式服需执行 `tab reload`。
- 三列真人客户端观感、38 人以上溢出及基岩版显示尚未实测。

## 2026-10-20 TAB 紧凑文案

- 用户确认的七行能源启动文案已通过真实资源与 `TabQuestProjection` 逐行断言，预览见 `target/verification/tab-compact-preview.txt`。不插入内部空白行，保留三列及 18 个任务占位符。
- 离线 Maven package 成功，43 项测试通过；只出现原有 SQLite native-access 与 shade 元数据重复警告。
- 最终 JAR 使用真实 TAB 6.2.1 / 隔离 Leaf 26.3 验证初始启动、TAB 重载和 Quest 重载，均通过并正常停服；正式服务器没有被停止或重载。
- 正式语言、能源启动文案及 TAB 左列配置备份后精准更新；玩家两列及其他配置保持原值。新 JAR 已放入 `plugins/update/Quest-1.0.0.jar`，需要正常重启正式服加载，单独 `tab reload` 不会应用新版投影代码。
- 三列真人客户端观感、多人溢出和基岩版仍待实测；任务条件、奖励、Genlite 和数据库未修改。
# 2026-10-21 个人交谈任务与 ActionBar

- Java 25 执行 `mvn package`：49 项测试通过，覆盖个人 UUID 进度幂等与重启恢复、SQLite 3→4 保留奖励数据、任务展示覆盖、配置校验、语言增量迁移。
- `python tools/run_introduction_smoke.py`：隔离 Leaf 26.3，真实 Adyeshach 2.2.0-pre.2、Typewriter 0.9.0-beta-177、TAB 6.2.1 初始加载及 Quest 重载均通过；隔离服正常停止。
- 探针验证 Typewriter EntryTrigger→DialogueInteraction、messenger 初始化、确认回调注册/释放、完成仅回调一次及中断不完成；已修复外部 KClass/Function0/Unit 的 Kotlin 类加载边界。
- 无空岛的代理玩家可优先看到交谈目标；ActionBar 在等待时显示、对话中暂停、完成后停止；个人任务完成后返回共享任务显示。验证不创建空岛或发放个人奖励。
- 代理玩家没有真实网络连接，探针跳过 Typewriter 的聊天数据包重播。真人客户端右键、蹲下输入、聊天重播、ActionBar 观感及基岩版仍待验收，不能把 API 验证视为客户端实测。
- 正式服未重启、未更新 JAR；构建中的已有 systemPath、SQLite native-access、shade 元数据和 GuiConfigUpgrade 非空断言警告仍在。


## 2026-10-21：星海 Xerr HUD 第一版

- JDK25 / Kotlin2.3.10 / Paper26.3 API 构建通过；原49项测试通过，新增5项 HUD 测试随后与 ResourcesTest、IntroductionTest 共12项相关测试全部通过。未重复运行未修改业务的完整测试集。
- 新增测试覆盖只读投影、个人/共享区分、进度钳制、长标题测宽截断、章节与最后任务通知合并、有界队列去重/淡出/清理、外部配置关闭及版本错误诊断。
- 正式服务端未启动/停止。独立目录 `C:/Users/32633/Desktop/QuestHudVerification-20261021` 使用真实 Xerr2.1.0、Leaf26.3、CraftEngine26.9.2，原生渲染探针在 Xerr reload 前后均通过。验证 Components RGB 与动态括号转义、实际定位字体、0/16/32三种图片进度、通知透明度与移除、原生槽位清空后补交。网络玩家为代理，不能替代客户端验收。
- 原生生成曾暴露 Xerr2.1.0 多字体共享 TTF 路径的并发复制错误。仅通过服务端配置为各声明分配不同路径的同字节 TTF 副本解决，不修改 Xerr JAR；保留原字体 ID、尺寸和其他配置。最终生成无该错误。
- Xerr生成包、CE生成包、CE实际托管包通过12项RGBA纹理对比；CE两份ZIP字节及SHA256完全一致。最终提交组件引用的7种字体均在Xerr包中存在；每张原始bitmap不超过256像素。完整哈希见服务端 `Xerr/design/quest-v1/audit.json`。
- 最终 Quest JAR 为14,183,210字节，SHA256 `c5ca5a1ea208b9daf1b108d711254b07b98f3221fc8f94438991268c9b325f4f`。检查 src 与 JAR 均无PNG/TTF/OTF/WebP，无Xerr/Paper/CE API或测试类打包。源码最大文件198行，本次 HUD 桥接134行左右，投影、通知队列、原生渲染、配置职责分离。
- 已备份并将 Quest JAR、Xerr core配置及测试生成包部署至正式服务端目录。未将隔离服的CE包复制至正式服，避免覆盖正式服其他材质。正式服启动后需执行 `ce reload pack` 并等待客户端成功加载；已部署的Xerr生成包可直接参与合并。后续更改Xerr资源后先执行 `xerr reload`、`xerr pack`，成功后再执行 `ce reload pack`。
- Neuron检查使用实际 Quest TextRenderer 与原生 XerrHudRenderer 的 Component 证据；HUD不加MiniMessage标签来迎合旧检查器。实际客户端字体观感、GUI缩放、通知位置/动画与跨插件避让未验收，结论保持pending。
- 现有构建警告仍有Maven systemPath、GuiConfigUpgrade无必要非空断言、SQLite原生访问/SLF4J及shade元数据重复；本次未新增打包API或修复无关警告。
# 2026-10-21 放置生成器后发燃料

- Java 25 `mvn package`：57 项测试通过；新增测试覆盖未放置时燃料任务锁定、成功放置后燃料仅发一次、重启恢复，以及旧 WAITING/SENDING/DELIVERED 燃料记录去重。
- 配置迁移覆盖新安装、旧 schema 1、现有 schema 2、自定义燃料数量与注释保留、重复迁移；原任务 ID、SQLite schema 和 Genlite 生产代码保留。
- `python tools/run_placement_smoke.py` 在隔离 Leaf 使用真实 Genlite API 写入模拟待发物品及已提交机器，验证物品不算放置、机器历史算放置；同时检查个人交谈、章节四任务展示及 Quest 重载。
- 隔离测试使用模拟机器数据和代理玩家，不代表真人客户端放置、自动背包送达或基岩版实测。正式服务器未替换 JAR 或重启。
# 2026-10-21 Genlite 燃料仓任务适配

- 核对最新 Genlite 源码、正式 JAR 与旧联动 JAR：正式版含 FuelMenus/FuelChestClicks 但缺少 Quest API；旧联动版有 Quest API 但没有新版燃料仓。已从最新源码重新生成联动补丁，不改原项目或燃料存取逻辑。
- Genlite 联动版 `mvn package`：128 项测试通过，覆盖燃料仓共用提交入口、真实存入量计数、错误物品/溢出/权限拒绝/取回不计数及持久化通知。Quest `mvn package`：59 项测试通过，新增文案迁移保留目标、奖励和自定义字段，Tab 三步提示完整展示。
- 隔离验证使用匹配的最新 Genlite 联动 JAR；探针通过真实 ContainerStock 燃料提交与计数接口推进 Quest 的 first_fuel，无存入时保持 ACTIVE，提交后为 COMPLETE，并继续验证个人交谈与重载。
- 真实客户端 Shift 点击、拖拽、背包送达和基岩版仍待验收。正式服务器未替换插件、重载或重启。


## 2026-10-21：紧凑半透明任务侧栏，TAB恢复普通列表

- `mvn -o package -Dkotlin.compiler.daemon=false -Dtest=QuestHudTest,ResourcesTest,IntroductionTest,TabQuestProjectionTest`：21项相关测试通过。覆盖任务全文编号换行、原RGB关键词、空步骤、数字进度、旧TAB提示迁移的中文注释/自定义字段保留与幂等性，以及关闭TAB布局的默认设置。未重新运行未受影响的业务测试。
- 独立 Leaf26.3 / Xerr2.1.0 / TAB6.2.1 / Java25 服务端探针在 Xerr reload 前后通过：Quest TAB桥接为空；侧栏实际组件含“主线任务”和能源启动的6行操作步骤，使用真实 `quest_sidebar_position_dark` 字体，显示0/1与1/1；无图片进度、共享说明或TAB提示；完成通知保持 `quest_ui` 12px，透明度、关闭及槽位恢复通过。
- 侧栏156×194 GUI单位、右边距2、主体alpha150；原生10px字体43种示例字符在10px采样下无半透明边缘。Xerr生成包、隔离CE合并包及托管包12项RGBA比对通过；两份CE ZIP一致，最终组件的6种字体均在资源包中存在。原生12px完成通知配置/图像未改。
- 正式TAB配置仅修改layout.enabled与任务专用scoreboard.enabled为false，Quest/tab.yml.enabled为false；页眉、页脚与其他字段保留。现有服务端文件和JAR均有备份。没有执行正式服重启、资源包发送或修改任务/奖励业务数据。
- 已暂存新的Quest JAR和Xerr生成资源包。需要正常重启加载新JAR，再由正式服执行 `ce reload pack` 合并分发。实际客户端的半透明对比度、10px可读性及边缘位置待验收，保持pending。

## 2026-10-22：任务日志与 CraftEngine 箱子背景

- 新增首页、分类章节总览、章节任务列表、个人设置四份独立 Shape 菜单。上一/下一只切换查看；锁定和完成任务可阅读，只有进行中任务可追踪。个人引导未结束时也可手动选择其他进行中任务。自动焦点会随晚到的引导/进度数据更新，用户显式浏览的任务保持不变。
- 最近相关构建执行 `JournalTest,ResourcesTest,QuestHudTest,ChapterResourcesTest` 共17项通过，覆盖 UUID 设置持久化、SQLite4→5保留旧数据与幂等性、手动/自动追踪、分类/章节边界、个人/共享任务选择、配置解析和实际 TextRenderer。此前全量检查的61项未受影响用例通过；新测试的模拟降级夹具审计数量已修正。最终仅调整渲染焦点的版本重新编译打包，并由下述服务端探针验证。
- 隔离 Leaf26.3 / Java25 使用最终 Quest JAR：31个原生 ItemStack 验证四页配置、完整目标/实际奖励 Lore、换行拆分与递归非斜体、上一/下一、锁定任务不能追踪、空分类，以及仅持有 quest.use、无OP/无空岛的入口。通过原生 InventoryClickEvent 验证首页→总览→章节→任务→设置路由、取消物品移动和四项设置写入。玩家为代理对象，未声称真人客户端点击通过。
- CraftEngine26.9.2 真实生成合并包：四张背景均有对应字形，height222/ascent13，位移-8/-177/+8，生成包纹理逐张解码RGBA与服务端原文件一致，ZIP完整。资源仅保存在正式服 `CraftEngine/resources/quest-journal`，制作脚本及原文备份在其 `design/quest-journal`。隔离服生成包未复制到正式服；正式服其他资源未覆盖。
- JAR审计确认没有PNG/TTF、服务端API类、源码或探针插件。保留既有构建警告：本地systemPath依赖、Kotlin Unsafe与shade元信息重叠；本次没有引入新依赖。
- Neuron静态/组件检查无规则问题，唯一pending为真人Java/Geyser客户端的贴图位置、缩放和悬停观感。正式目录的新JAR备份后暂存；没有执行正式服重启、命令或资源包发送。生效需正常重启，再执行 `ce reload pack`，玩家用 `/quest` 打开。

## 2026-10-22：保留普通任务菜单，恢复 TAB 展开详情

- 正式服四份日志配置只替换标题，逐份解析比较确认 Shape、组件、Lore、动作及未知字段均未改变；标题无自定义字形或字体引用。专用 CE 资源与设计目录已移至 `Quest/backups/no-texture-tab-20261022`，有效 resources 下不再存在；其他 CE 资源与 Xerr 文件未修改。
- 正式 `Quest/tab.yml.enabled` 与 `TAB/config.yml.layout.enabled` 恢复 true，解析比较确认仅对应开关变化，原页眉、页脚、三列装饰、任务槽位及其他字段完整保留。任务 scoreboard 仍关闭。源资源/安装模板与断言同步更新。
- `mvn -o package -Dtest=TabQuestProjectionTest,ResourcesTest -Dkotlin.compiler.daemon=false`：9项通过；实际任务投影含章节、任务、目标、进度与编号操作说明，RGB换行及配置解析通过。
- `python tools/run_tab_smoke.py` 在隔离 Leaf26.3/TAB6.2.1/Java25 启动、TAB重载、Quest重载三个阶段通过：原生60槽位、22固定槽位、38玩家槽位、18任务占位符、玩家独立缓存、MiniMessage兼容与溢出文本；TAB scoreboard/BossBar均未启用。隔离服已正常停止。
- 新JAR已备份后暂存正式 plugins，JAR审计无材质、API类或测试代码。正式服未执行命令、重启或发包，真人客户端待验收。运行中可 `tab reload` → `quest reload` 应用配置，`ce reload pack` 清理后续分发包中的旧GUI素材；新JAR正常重启加载。

## 2026-10-22：章节首页 12 格布局

- 首页仅有章节、主/支线/特殊分类、HUD设置、上一/下一页和关闭；四角为设置、关闭、上一页、下一页，顶部为分类。章节区域为中间四行七列的局部第2/5/7列，原生零基槽位11/14/16、20/23/25、29/32/34、38/41/43，每页12章。
- 章节ItemStack的Lore显示实际章节介绍、完成数量/总数量/百分比、真实物品奖励名和数量。原任务浏览层迁入 `gui/task.yml`，保持任务上一/下一及追踪；章节任务列表返回首页，任务查看返回章节列表。
- 相关构建与 `ResourcesTest,JournalTest` 共5项通过。原生36个ItemStack及InventoryClickEvent探针通过，包含13章跨页/首尾边界、空分类、四角/分类/中央槽位、实际章节说明与奖励进度、无OP/无岛的quest.use入口、章节→任务→任务查看与逐级返回，以及四项个人设置。探针发现的空分类null查询已修复，最终JAR重新打包并通过同一探针；代理玩家不代表真人实机验收。
- 正式首页修改前完整备份；只替换Shape/components，解析确认其他根字段保持不变。新增任务查看配置，未覆盖章节/任务/设置配置或TAB/Xerr。正式JAR备份后暂存，与隔离测试JAR哈希一致；打包审计无材质或服务端API。
- 没有重启正式服或发送资源包。须正常重启加载本次运行代码，实机菜单视觉与点击仍待验收。没有新增贴图，不需要为本次菜单改版生成资源包。

## 2026-10-22：列号修正与玻璃板填充

- 章节为整个9列箱子的第2/5/7列，四行零基槽位10/13/15、19/22/24、28/31/33、37/40/42。其他空格及空章节预留格补无名灰色玻璃板，不添加动作，现有操作按钮位置保留。
- 相关 `ResourcesTest` 两项与打包通过，检查Shape映射、YAML及实际共享组件渲染。没有扩展测试未改动的业务引擎。
- 隔离Leaf原生探针通过82个ItemStack、13章分页与首尾边界、整箱子列坐标、第二页所有54格填充、无名且非斜体玻璃、空分类、章节Lore及点击/返回路径。正式配置语义比较仅预期布局/装饰字段变化，原按钮与章节介绍/进度/奖励保留。
- 新JAR和首页配置修改前备份，验证后放入正式目录；没有重启正式服，真人客户端待确认。须正常重启加载补位逻辑，本次不需要资源包生成。

## 2026-10-22：GUI 仅保留章节、任务、设置三页

- 源资源、JAR和正式 `gui/` 均严格为 `chapters.yml`、`tasks.yml`、`settings.yml`。删除旧QuestMenus/JournalMenuRenderer/MenuSession、旧Settings菜单字段、手动GUI领奖回调、旧GUI升级器及详情/重复总览页面路由。类输出目录先归档后重新编译，避免已删除class或资源混入JAR；旧路径只在归档迁移中使用。
- 全量 `mvn -o package -Dkotlin.compiler.daemon=false`：68项测试通过。6项三菜单迁移测试覆盖新安装、七份旧配置、原文/中文注释/未知字段保留、重复加载、中断恢复、并发编辑拒绝、未来schema/无效动作与已退休文件恢复冲突。
- 隔离Leaf26.3/Java25真实验证：3文件/3页面、68个原生ItemStack、章节2/5/7列及玻璃、13章分页、完整章节/任务Lore与实际奖励名/数量、仅ACTIVE任务可追踪、原生InventoryClickEvent直接持久化追踪且保持任务列表、返回章节和四项设置均通过。仅向隔离数据库写入模拟未完成目标；未发放测试奖励。探针停止时恢复临时网关，隔离服正常退出。
- 正式七份原文件完整备份至 `Quest/backups/three-menus-20261022/gui`，实际迁移由本次测试过的JVM `MenuConfigUpgrade` 执行；第二次运行逐文件字节不变。恢复日志在 `.migrations/menus-v3`，先保存所有before/after再发布/删除；正式有效目录已确认只有三份。
- 已暂存与构建/隔离测试SHA256一致的新JAR；打包审计限制三资源并拒绝旧GUI类、素材、服务端API和测试代码。现有systemPath/Kotlin Unsafe/shade元信息及隔离服Windows OSHI警告未在本次扩写或修复。
- 正式服未重启或执行重载，真人客户端待验收。须正常重启加载新代码，不能用旧进程reload替代，否则旧加载器可能重新导出退休文件；本次无需资源包操作，TAB/HUD/任务进度/奖励结算未改。
## 2026-10-22：任务追踪与 HUD 合并

- 14 项相关测试及 Maven package 通过；涵盖菜单资源、偏好持久化、旧菜单迁移、追踪文案迁移、删除重复组件、未知字段/注释保留及幂等。
- Leaf26.3 隔离服原生 InventoryClickEvent 通过：先关闭追踪，再点击 ACTIVE 任务，目标、HUD 与自动追踪同时保存；再次关闭保留任务 ID，设置页没有独立自动追踪按钮，提示和声音仍可切换。
- 未进行真实客户端视觉验收，正式服未自动重启；既有 Maven systemPath、shade 重复元数据及 JDK native-access 警告仍存在。

## 2026-10-22：任务章节子文件夹

- 任务资源原文移入 `quests/第一章/`，加载、章节升级、生成器补全和燃料文案迁移支持子文件夹；默认导出识别嵌套文件。
- 17 项相关测试与 Maven package 通过，覆盖递归扫描、原文保留、冲突拒绝、幂等及中断恢复。首次全套检查发现资源测试仍按直接父目录判断 schema，修正后相关检查通过，其余原测试无失败。
- Leaf26.3 隔离服已完成旧根目录迁移并通过原生菜单点击，再次启动仍恰好四个嵌套任务且未重新生成根目录文件；最终 JAR 同样仅含四个嵌套任务。正式服原文件和 JAR 已备份并同步，本次不修改任务 ID、奖励、数据库或 GUI 布局。真实客户端观感不在此次目录整理范围。

## 2026-10-22：分类单按钮与槽位差量刷新

- 13 项相关测试及 Maven package 通过；旧三按钮迁移只留下居中的循环按钮，保留章节位置、注释、未知字段和备份，重复执行内容一致。
- Leaf26.3 原生 InventoryClickEvent 连续主线→支线→特殊→主线，Inventory/会话引用及打开计数不变，切换重置页码；空分类没有旧章节动作。真实原生 ItemStack 渲染探针确认相同内容零槽位写入，分类变化仅写相关槽位，未调用 clear/setContents。
- 隔离服新 UUID 默认 HUD/自动追踪关闭，点击 ACTIVE 任务后同时开启；旧 SMART 偏好和已有保存值保留测试通过。最终 JAR、隔离服测试 JAR 和正式服暂存 JAR 的 SHA256 一致，原配置已备份。未自动重启正式服，未进行真实客户端视觉验收。
- 既有 Maven systemPath、shade 元数据重叠和 JDK native-access 警告不影响本次验证。

## 2026-10-22：安放生成器卡在 0/1 的正式服部署排查

- 正式服 15:36:35 日志明确报告 `NoClassDefFoundError: cn/genlite/api/GenliteQuestApi`。当前 Genlite JAR SHA256 `5cf99cb771d360fa3ee52db2ab098c3aabf925b50dd42044df6c20e9917e8a1e`，不含接口；配套兼容包 SHA256 `d2e89a3754251e3a0b78f49834b658ad97d1e0b9bf82fea5cf83229439db4560` 包含接口和持久化计数事件。未发现兼容覆盖范围以外的 Genlite 源码漂移。
- 11 项 Genlite 放置相关计数/燃料容器测试通过。现有 machines.json 的只读副本由实际兼容版 AtomicStateFile 成功读取，6 台机器记录保留，1 台未移除生成器存在，缺失计数字段默认空值，读取不改原文。
- Leaf26.3 隔离服通过真实 Genlite 服务注册/快照与 Quest 流程：背包包裹不算放置，持久化机器识别为已放置并解锁能源任务；空燃料仓不完成，成功存入燃料计数才完成。引导及重载检查同时通过。
- 经校验的 Genlite 兼容包暂存正式服 `plugins/update/Genlite-1.0.0.jar`，旧包已备份。未写正式服机器/任务/奖励数据库，未重启正式服；实际服恢复进度待完整重启后确认。

## 2026-10-22：三张设置卡片及 ON/OFF 贴图

- 内置 imagegen 生成六态透明图集，原图保存在服务端 CraftEngine；模型直接 UV 采样，没有缩放或裁切原图。额外底板从零按原生 176×222 槽位坐标绘制。
- 7 项相关 JUnit 与 Maven package 通过，覆盖成对状态字段校验、布尔标志、偏好保存和资源渲染。Leaf26.3 原生 CE 注册/ItemMeta 验证六种状态、45 个可点击槽位、独立区域差量更新、不变时零写入、去斜体及无资源包时回退三个原版按钮。
- 隔离服显式 generate/validate/zip 工作流通过，六模型、透明物品模型、双 PNG 和独立字体均进入最终 ZIP；图集和底板解码像素与源文件一致，背景字体 height222/ascent13 正确。最初隔离服 workflows 为空，仅重载未打包；验证器改为显式隔离工作流后通过。
- 正式服配置和资源已部署，Quest 新 JAR 暂存 `plugins/update` 并备份原件；未重启正式服、未触发正式服资源包下发。最终客户端比例/观感待完整重启、`ce reload pack` 和实际进服确认。Quest 源码/JAR 未含任何图片或字体。

## 2026-10-22 撤回独立星港面板

- 按用户要求移除独立 board 模块、配置、布局素材、指令与接线，恢复原 TAB Layout；保留任务日志、设置卡片、个人引导和原 Xerr 侧栏。
- Maven package 通过：82 tests，0 failures/errors；JAR 不含 board 类或资源。回退版暂存正式 plugins/update，未热替换或重启。
- 一份新增 TTF 被运行中的 Xerr 锁定，需正常停服后执行 backups/board-rollback-20261022-180642/cleanup-locked-font.ps1；其他本次正式配置已恢复备份，未改数据库。
