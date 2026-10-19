# 验证记录

验证日期：2026-10-19。以下为真实执行结果，不将编译成功等同于真人玩法验收。

## 已通过

| 范围 | 验证与结果 |
|---|---|
| Quest 构建 | JDK 25.0.2 / Maven 3.9.12 / Kotlin 2.3.10，`mvn -o -Dkotlin.compiler.daemon=false package` 成功 |
| Quest 核心测试 | 7 项全部通过：完整三任务领域流程、40 次团队并发首次领奖、20 次最终领奖、重复/迟到计数、解锁基线、多条件、重启、待发记录、领取竞争、人工恢复、重置保留领奖、重载后奖励快照不变、完成状态独立持久化 |
| 默认资源 | 全部 YAML 可解析、版本标记、Shape 字符映射与语言键一致；无传统色码与 Tab |
| 物品渲染 | 使用真正 TextRenderer 方法和项目 Adventure 依赖，显式嵌套斜体、动态子组件及翻译参数均转为非斜体；占位值不会被重新当作 MiniMessage 指令解析 |
| Neuron 本地检查 | `neuron_check.py` 检查全部 Quest Kotlin/YAML，实际渲染入口导出的 Component 样本，结果 `passed`，issues/pending 均为空 |
| Genlite 补丁 | 105 项测试全部通过，其中新增 2 项覆盖燃料事务计数、转移不计数、计数持久化、真实生产 2 个/批，以及通知发生在持久提交之后 |
| DreamSkyblock 补丁 | 81 项测试中 80 通过、1 跳过，无失败；使用原有测试套件，新增成员事件的位置另外经源码检查 |
| 联动启动 | 隔离 Leaf **26.3-4-ver/26.3@4b0dcd2**，Quest + 两个补丁插件 + CraftEngine **26.9.2** 均成功启动 |
| 实服 API 探针 | 临时 QuestSmoke 测试插件确认 DreamSkyblock/Genlite 公开服务 ready，五个 CE 物品 ID 与实际构建 ItemStack 一致；使用真实服务端 ItemStack 验证满背包拒绝及空槽发放规划不修改输入 |
| 重载与停止 | 隔离服注入无效任务 YAML，reload 报错后 debug 仍保留三个旧任务；恢复文件后成功 reload，正常 `stop`，进程退出码 0 |
| 缺少可选插件 | 隔离服只保留 Quest，三个联动均 missing，仍成功启用，控制台指令/诊断正常，无缺失类异常，正常停止退出码 0 |
| JAR 边界 | Quest 约 13.3 MiB，仅含 Quest、Kotlin/注解、SQLite 及必要资源；无 Paper/Bukkit、CraftEngine、DreamSkyblock、Genlite、JUnit、探针、源码或缓存 |

测试报告与证据：

- `target/surefire-reports/`
- `.build/dependencies/Genlite/target/surefire-reports/`
- `.build/dependencies/DreamSkyblock/target/surefire-reports/`
- `target/verification/components.json`
- `target/verification/neuron-report.json`
- `target/verification/smoke-console.log`
- `target/verification/optional-smoke-console.log`
- `.build/dependencies/source-manifest.json`

完整依赖构建由 `build.ps1 -Offline` 执行；最后修正后单独重建 Quest 并通过测试和联动探针。测试期间没有复制 JAR 到正式服务器，没有启动/重载正式服务器，没有触及其玩家数据或世界。

源码基线：本地 Genlite HEAD `9b5589531b64c42b5c0b9aa4ec622c86b02e1659`，DreamSkyblock HEAD `f14b11043be8e1e5723bb61ed78043a059f587de`。HEAD 不代表工作树没有本地改动；精确被覆盖文件以 source-manifest 的原始 SHA-256 为准。

## 已有警告与测试环境情况

- Maven `systemPath` 警告来自本地插件依赖方案；DreamSkyblock 本身也使用该方案。未来升级 Maven 或迁往其他机器时需要按 README 准备相同版本的本地 JAR。
- DreamSkyblock 有原有的 Bukkit 弃用 API、Shade 模块资源重叠警告；未扩展为本次无关重构。其存档归档测试有 1 项原有跳过，原因为宿主环境不允许创建符号链接。
- JDK/Kotlin 的 Unsafe 警告、JUnit 下 SLF4J 无 provider/SQLite native-access 警告不是业务测试失败；隔离服使用 native-access 参数。
- 隔离服首次准备遗漏 CraftEngine `resources/internal`，出现方块映射容量为零的警告。补齐真实内置资源后，最终启动正确加载 5 个自定义方块，探针通过；不是 Quest 生产计数失败。
- 早期测试发现 Windows 在异步连接尚未关闭时清理 JUnit 临时目录会失败。现已提供 `Database.closeAsync()`，生产停止保持非阻塞，测试等待真实关闭后清理，最终全部通过。
- 最终 smoke 日志中的任务 YAML 解析错误为主动注入的重载失败场景；确认保留旧任务后已恢复文件并成功重载，不是未修复的启动错误。
- 初次无交互控制台测试进程使用隔离数据结束后，后续验证改用保留 stdin 的独立子进程，以控制台 `stop` 正常停服。
- Neuron MCP 的 `begin_task` 被环境当前 `approval policy=never` 拒绝，没有获得 task_id，所以没有伪造 validate_task/finish_task 回灌；已运行本地检查器。

## 尚未验证与边界

- **尚未使用真人 Java/基岩版客户端完成整条点击流程**；未实测 Geyser/Floodgate 的箱子点击、资源包材质与高延迟操作。原版箱子交互由代码实现，当前证据不等于客户端验收。
- **Purpur 26.3 尚未运行验证**；Leaf 26.3 为已测组合。当前 Genlite 要求 26.3，26.2/其他 26.x 不在本次可宣称兼容范围。Folia 明确不支持。
- 未对真实空岛执行创建、邀请加入、世界卸载或由真人投入燃料；已验证公开 API 就绪、事件触发位置和 Genlite 事务/实际生产逻辑，实际玩家操作仍待测试服验收。
- SQLite 与玩家存档不能原子提交；SENDING 隔离与人工核对有实测领域覆盖，但硬断电和玩家存档保存窗口没有故障注入实测。不能将其说成严格跨存储 exactly-once 或零丢失。
- 不识别旧服通过其他系统发放、位于离线背包/箱子中的未登记裸机器；部署前按 README 核对或移除任务中的首台机器条目。

建议实际验收：两名仅有 `quest.use` 的同岛成员，创建/加入 → 启航奖励每岛一次 → 真正存入燃料 → 能源奖励 → 实际产出 32 圆石 → 两人竞争领取升级单元；其间断开玩家、重启、卸载空岛世界，再检查进度与待发奖励。重复领取输出、向收集器/仓库存取圆石不应增加生产进度。背包满时奖励必须留在待发列表。
