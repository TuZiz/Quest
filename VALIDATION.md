# 章节系统升级验证记录

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

实际运行 tools/run_smoke.py、tools/run_xerr_smoke.py 均通过：锚定渲染、变量、非法重载保留原任务状态通过；8 张 Quest 贴图合并后像素一致，958 个 bitmap provider 的单格源尺寸均不超过 256×256，隔离服正常退出。正式服 RCON 未启用，没有发送重载命令或主动停服；需要按 XERR.md 顺序重载并重新加载资源包，真实客户端结果待确认。此次仅修改服务端素材/锚点与验证脚本，没有重新构建或更换 Quest JAR。

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
