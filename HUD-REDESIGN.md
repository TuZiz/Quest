# 智能任务 HUD

增量修改显示层和玩家偏好，不改章节图、条件、生产基线、奖励事务、既有任务数据或菜单业务。版本：Leaf/Paper 26.3、Java 25、Xerr 2.1.0、CraftEngine 26.9.2；客户端26.2。真人客户端GUI缩放与像素观感待游戏内验收。

## 指令与配置

- `/quest help`：玩家帮助；管理员额外显示管理帮助。
- `/quest reload`：重载配置，保留 `quest.admin` 权限；旧 `/quest admin reload` 兼容。
- `/quest hud`：切换 smart → always → off。
- `/quest hud smart`：明确选择smart立即收起为单行；首次推进、10/25/50/75/90%节点或新任务激活时短暂展开。
- `/quest hud always`：完整显示当前有效任务。
- `/quest hud off`：关闭追踪，保留重要通知。
- `/quest hud view`：请求临时展开；`/quest hud track <任务ID>`：切换当前已激活任务。
- `/quest hud notifications on|off`：独立控制通知。

普通功能统一 `quest.use`。个人模式、通知开关和追踪ID以UUID异步持久化；保存成功后刷新。旧PDC关闭标记仅首次迁移。所有模式在没有有效任务、全部完成或没有空岛上下文时隐藏；新任务出现会恢复。

`plugins/Quest/显示.yml` schema 3：`tracker`、`notifications` 独立开关；`compact-layout`、`tracker-layout` 控制布局；`expand-ticks: 120` 为6秒，支持100～160ticks；`expand-cooldown-ticks: 200` 节流生产提示；`expand-milestones` 配置重要节点；`progress-bar` 控制展开条；`notification-ticks: 60` 为3秒，队列默认最多4条。重复累计值不展开，连续展开最多8秒，新任务切换不受生产冷却阻挡。迁移保留旧配置原有数值，不强制覆盖自定义时长。

## 修改文件清单

- `hud/HudMode.kt`、`SmartHudState.kt`：模式与每位玩家的纯计时状态。
- `hud/HudPreferenceSessions.kt`、`storage/HudPreferenceStore.kt`、`Database.kt`：异步偏好会话及SQLite 2→3迁移，只新增表和迁移审计。
- `hud/QuestHud.kt`、`QuestTrackerHud.kt`、`TrackerSnapshot.kt`：只读任务投影、同岛合并查询、变化才提交渲染、退出及重载清理。
- `hud/HudControls.kt`、`HudSettings.kt`、`platform/QuestCommands.kt`、`QuestRuntime.kt`：玩家指令、帮助、参数校验与装配。
- `hud/QuestNotificationHud.kt`：统一时钟、双行通知，沿用单槽位、有界去重合并队列。
- `config/HudConfigUpgrade.kt`、`QuestFiles.kt`、`ResourceUpgrade.kt`、`显示.yml`、`lang/zh_cn.yml`：可恢复幂等配置升级，保留未知字段及用户文案。
- `SmartHudStateTest`、`HudPreferenceStoreTest`、`TrackerSnapshotTest`、`HudConfigUpgradeTest`、`LegacyMigrationTest`、`ResourcesTest` 与 `tools/smoke/QuestSmoke.java`：相关自动验证。
- `tools/install_compact_hud.py`、`tools/run_smoke.py`、`tools/run_xerr_smoke.py`：服务器资源安装及隔离服验证。

## 服务端资源

贴图、字体及Xerr布局只在 `C:/Users/32633/Desktop/leaf/plugins/Xerr/`，不进入Quest JAR。

- `design/quest/布局.yml`：GUI单位尺寸、偏移、间距与透明度。右偏移6、上偏移2；并非物理屏幕像素。
- `core/layout/quest_tracker_compact.yml`：无面板单行布局；展开布局 `quest_tracker.yml` 为144×64；通知 `quest_notifications.yml` 为160×44，顶部76，与展开面板留10单位间距。
- `core/assets/image/quest/bars/textures/empty.png`、`full.png`：130×3，左内距12、右内距2，满值延伸到右内边缘。原生progress使用units=1。
- `core/assets/image/quest/panels/`、`icons/`：沿用已生成素材，统一深灰、细边框、金色标题和青绿进度。
- `backups/compact-hud-*`：被替换资源备份。

修改设计参数后运行 `python tools/install_compact_hud.py C:/Users/32633/Desktop/leaf/plugins/Xerr`。新代码JAR须正常重启加载，不用Bukkit reload。随后 `xerr reload` → `xerr pack async`（等待成功）→ `ce reload pack`（等待上传和发送）→ 客户端接受新包。行为配置之后可直接 `/quest reload`。实际托管包为CE `cache/hosted/default/resource_pack.zip`。

## 游戏内验收

1. 使用有进行中任务的测试空岛，保留正式玩家数据。默认smart显示右上单行；全部完成的空岛隐藏是预期行为。
2. 首次推进或达到重要节点展开约6秒；连续生产不反复展开，重复同一进度不延长。任务完成自动切换，展开区域下方单条合并通知约3秒后关闭。
3. 检查进度0、中间及满值，满条到右内边缘；GUI缩放2、3、4及自动检查顶部、文字裁切和通知间距。资源包不能自动探测第三方计分板尺寸。
4. 逐项执行smart、always、off、view、track、notifications；退出重进和正常重启后个人设置保持，同岛其他玩家不受影响。
5. 管理员 `/quest help` 能看到 `/quest reload`；普通玩家可用全部HUD指令，但不能reload。有效配置重载成功；错误配置不破坏上一份有效配置。
6. 全部任务完成后隐藏；新任务激活重新显示。加载资源包、Quest配置重载或Xerr重载后，不应因缓存误判而永久丢失有效HUD。

自动验证不等于客户端动画、字形、边缘位置和GUI缩放已验收，以客户端加载最新托管包后的结果为准。

2026-10-19截图回归：明确切smart不再先展开，notifications仅改通知开关。任务与章节HUD名称去掉菜单装饰前缀（字体cmap无✦），图标仍走资源包图片。未修改任务名称配置；客户端文字/进度条实际对齐仍待复核。
