# 紧凑任务 HUD 更新

本次只改显示层，不修改任务图、条件、累计基线、奖励事务、SQLite 或菜单核心业务。Leaf/Paper 26.3 / Java 25 / Xerr 2.1.0 / CraftEngine 26.9.2；未声明 Folia 兼容。真人客户端像素位置及 GUI 缩放仍需游戏内确认。

## 修改文件

- `hud/QuestHud.kt`、`TrackerSnapshot.kt`：只读追踪、完成自动隐藏、个人显示偏好、清理和刷新缓存。
- `hud/HudNotice.kt`、`HudNoticeQueue.kt`、`QuestNotificationHud.kt`：事件投影、合并去重、有界队列和单槽位通知；`XerrDisplay.kt` 保留原生定位转换并支持轻滑偏移。
- `hud/HudSettings.kt`、`HudText.kt`：参数校验及等宽文案截断。
- `platform/QuestRuntime.kt`、`QuestCommands.kt`：批量传递显示事件；普通 `quest.use` 玩家可 `/quest hud [on|off]`。
- `config/HudConfigUpgrade.kt`、`QuestFiles.kt`、`ResourceUpgrade.kt`：显示配置 schema 1→2，可恢复、保留自定义开关和注释；追加缺失语言块。
- `src/main/resources/显示.yml`、`lang/zh_cn.yml`：行为开关、时长、队列、进度条、完成隐藏及单引号文案。
- `HudNotificationsTest`、`HudConfigUpgradeTest`、`TrackerSnapshotTest`、`ResourcesTest`、`tools/smoke/QuestSmoke.java`：本次自动验证。
- `tools/install_compact_hud.py`：部署服务器侧贴图及布局，覆盖前备份，重复执行不重复修改。

## 服务器侧资源

位置：`C:/Users/32633/Desktop/leaf/plugins/Xerr/`。Quest JAR 不包含材质、TTF、Xerr 布局或生成包。

- `design/quest/hud-redesign-source.png`：AI 生成透明底素材原图。
- `design/quest/布局.yml`：GUI单位尺寸、右/上偏移、通知间距和 alpha；通知顶部自动计算。
- `core/layout/quest_tracker.yml`、`quest_notifications.yml`：字体、颜色、对齐和独立区域。
- `core/assets/image/quest/panels/`：深灰半透明细边框；`icons/`：小型状态图标；`bars/`：青绿细进度条。
- `core/display/popups/quest.yml`：清空旧 Quest 多弹窗定义，保留文件防止默认示例重新导出。
- `backups/compact-hud-*`：替换前文件，可用于回退；不涉及玩家数据。

生成使用 imagegen 技能 CLI fallback、用户已授权模型 `gpt-image-2.5-flare`；内置生图接口无权限后使用既有授权。未保存密钥。美术提示要点：透明底生产用像素HUD素材表，两个空白紧凑横面板，半透明炭灰背景，极细石板灰像素边框，少量金色/青绿角标，无文字、无厚重石头边框、无发光；文字由游戏字体实时渲染。最终面板从原图裁切并按 GUI 单位缩放，状态图标和进度条是简单像素几何图形。

修改布局参数后运行：

```powershell
python D:/codex/Quest/tools/install_compact_hud.py C:/Users/32633/Desktop/leaf/plugins/Xerr
```

行为参数用 `quest admin reload` 生效；贴图、字体、坐标必须重新打包。完整生效顺序：重启加载新 Quest JAR → `xerr reload` → `xerr pack async`（等待成功）→ `ce reload pack`（等待上传和发送完成）→ 客户端接受新包。不能只替换 generated ZIP，实际托管包在 `CraftEngine/cache/hosted/default/resource_pack.zip`。

## 游戏内验收

1. 用有进行中任务的测试空岛、仅拥有 `quest.use` 的账号加载新包；不要重置正式玩家任务。已完成所有章节的空岛默认不显示追踪，这是预期行为。
2. GUI缩放 2、3、4及自动分别检查：追踪右上，通知在其下方，间距不重叠，文字不越界；较小窗口也检查计分板。资源包 HUD 无法自动探测其他插件计分板尺寸，如冲突通过布局偏移调整。
3. 完成任务1、2：显示一条“任务完成 / 下一任务”通知，追踪自动切换，奖励正常自动发放；完成任务3：章节完成通知后追踪隐藏。
4. 快速触发多个事件，确认始终仅一个通知；通知按序关闭，重复不刷屏，队列不会无限增长。
5. `/quest hud off` 隐藏、重新登录仍隐藏；`/quest hud on` 恢复有效任务。检查退出、切换空岛、Quest配置重载不会残留通知。
6. `/quest` 菜单按原服务器访问设置使用；此次未修改菜单业务或奖励策略。基岩端未加载 Java 资源包时不强制显示 Java HUD。

自动测试只能证明事件队列、布局数据、实际 Xerr 渲染组件及资源合并正确，不能替代客户端动画流畅度和最终像素位置验收。
