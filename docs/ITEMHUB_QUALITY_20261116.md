# Quest ItemHub 品质奖励 · 2026-11-16

初次试炼继续奖励itemhub:星芽:短剑一把，奖励配置无需改ID。实际发奖按件抽品质，名称使用像素品质框，Lore首行显示同一快照的品质与成色，攻击与暴击数值来自ItemHub PDC；不复制品质表到Quest。

预览/菜单/AutomaticRewards检查使用不抽取的previewItem，MailboxDelivery发放准备阶段使用reward；满包或状态变化时丢弃未发放结果，重规划只使用本次已准备stacks，不再抽。WAITING仍存ID/数量，不宣称等待中物品字节已经固定。原SENDING不盲目重放，已领取任务不补发。普通材料和原版物品沿用原路径，多件不可堆叠品质装备独立生成。

验证：ItemHub77项测试/package通过；Quest24项相关测试/package通过（Java25、Xerr2.3.0依赖覆盖）。全量166项测试有12项失败；去掉本次改动的隔离baseline25项重现相同12个失败用例、0错误，属于现有TAB文案与追踪默认预期不一致，未扩写修复。

Purpur26.3隔离服真实Quest/ItemHub加载，QUEST_QUALITY_PASS及最终QUALITY_PASS：实际provider+factory预览不抽、逐件发奖、名称字形/Lore品质成色/RPG攻击一致、刷新稳定、递归非斜体。隔离服缺DreamSkyblock/CE等依赖，有原有HomeMenuOpenedEvent注册警告，未把此测试当完整Quest集成或真人任务发奖验收。生产服未重启，客户端观感待验收。Quest依然不支持Folia。

本地备份C:/Users/32633/Desktop/leaf/.codex-backups/quest-quality-20261116；两JAR暂存plugins/update。ItemHub/qualities.yml补lore-quality行，未知字段和已有注释保留。源配置src/main/resources/qualities.yml；已有配置缺字段时也会在内存补齐，不自动覆盖用户文件。

云端两JAR已按标准脚本SHA校验暂存D:/Purpur-server/plugins/update，配置同步成功。配置备份D:/Purpur-deploy-backups/quest-quality-20261116；JAR备份ItemHub:36fad0e685cd4c13baf64b7393fe8a27、Quest:08b230a4671f468c91010f664356f9f6。无需修改Quest任务YAML和数据库。等待正常重启加载；已有七级字体仍须客户端接受对应CE合并资源包。

产物SHA256：
- ItemHub: `07f1aef6a89db1f4a02349075e7ca15b78a6633aa978316db1207a3c1842f68f`
- Quest: `06690469593a749346012b248bc83f61ad05cecc55e875035d26f257be4cfdef`
