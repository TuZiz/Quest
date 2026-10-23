# 第一章教学扩展开发状态（2026-10-23）

当前为阶段性开发提交，尚未正式部署，不可当作完整验收交付。
Neuron task_id: task_aa99ce45797343f88e6f。

已核对 upstream Genlite 493a326、DreamSkyblock fd415dc；位于 .build/upstream，未改它们的工作树。
Genlite 26.3；DreamSkyblock 上游26.2，本轮依赖构建显式覆盖paper.version=26.3.build.28-alpha。
JAVA_HOME=C:/Program Files/Zulu/zulu-25。

已实现：14份新任务资源（10主线+4支线），连同旧4个共享任务与个人NPC引导共15主线4支线；optional支线不参与章节奖励；真实机器等级状态补读；成功绑定/切换/传输/领取计数；真实生成器实体来源标记拾取；DreamSkyblock成功打开菜单事件；本地战斗与浏览操作持久化；幂等配置迁移留存原文备份；SQLite表结构仍5，不清空旧数据。
已增加TutorialProgressTest和TutorialUpgradeTest，完整构建日志 .build/tutorial-build.log。
兼容扩展由tools/make_tutorial_compat.py在make_compat末尾生成。不要直接手改自动生成覆盖文件。

尚需完成并验证：
- Quest Maven package已通过（85项测试，0失败、0错误）；仍需重新prepare_compat后复建Genlite/DreamSkyblock并补齐依赖libs。prepare会清空暂存libs，上游git不含ArcMenu/PAPI，需从D:/codex/DreamSkyblock/libs与正式服对应JAR复制至upstream/libs或完善prepare的依赖拷贝。
- unlock-when以含点metric作为YAML映射键，需要实测Bukkit ConfigurationSection是否拆分点路径；如拆分，改成块式记录列表metric/target，并增加真实默认任务解析测试。
- 当前Genlite仍原生三输出模式，中央仓库本轮未新增免费开放，但“仅显示锁定说明”尚未落实，必须处理此边界，不可宣称已满足。
- MythicCombat动态事件适配仅经官方5.4文档核对，正式服没有MM，未做真实MM运行测试；要补模拟事件或标记明确待验收。默认原版击杀是否需排除MM僵尸尚需处理。
- 实际操作链隔离服验证：所有任务、拾取部分背包/重投物品/拆装、真实工具绑定、输出模式、收集器提取ACK、等级、战斗世界和归因、多人/重登/奖励，保持GUI/HUD不变。
- ProductionPickup当前接管带来源标记实体的玩家拾取，普通实体不受影响；需要验证取消/满背包/跨岛与ItemMerge，原版僵尸任务不靠持有物品。
- 教学奖励根据正式机型真实成本2/6/16/40分段发升级单元；首次Lv2奖励收集器+工具；拾取16奖励木材生成器；原任务数量奖励保留。服务端无升级单元制作配方，故补足成本避免卡死。
- 部署前对正式Quest配置和SQLite使用在线backup API做一致快照；保存旧3JAR，打包3插件配套JAR、配置与升级说明，审计JAR不含服务端API/素材/测试。尚未备份或暂存正式更新。
- 最后Neuron validate_task/finish_task和真实渲染检查；文档最终报告实际通过项，禁止把未执行项说完成。

当前仅沿用三份GUI与已有资源，不新增素材，不改玩家数据库，不重启正式服。
