# 右上角任务完成卡片

2026-10-20按用户确认的生成图增量实现。保留原常驻任务栏，本次仅更改通知皮肤、位置和显示文案。任务、章节、奖励、Genlite统计及SQLite不改。

## 外观和布局

- 220×44 GUI单位的紧凑实色深灰卡片、细像素边框、左侧绿色勾选图标。
- 图标从已确认的gpt-image-2.5-flare设计稿提取，背景按原生像素尺寸重建；任务文字不烘焙到贴图。
- 所有元素统一`anchor.x: 100`、`anchor.y: 0`，右边距0、上边距2。追踪栏保持原中线锚点和位置。
- 现有12px中文字体；第一行动态任务名称，第二行简短提示。文字超长按实际等宽预算省略，不越过卡片右边缘。
- 保留单槽位、有界队列、去重、连续完成/激活合并以及退出/重载清理。默认3秒；已有自定义时长继续生效。

## 资源及配置

只存入服务端`plugins/Xerr/`，不打进Quest JAR：

- `design/quest/完成通知.yml`：右侧/顶部偏移、宽高和图标/文字内距。
- `design/quest/completion-approved-source.png`：已确认原设计稿。
- `core/layout/quest_notifications.yml`：任务/章节完成及激活的四套布局，共用`quest_notice`槽位。
- `core/assets/image/quest/completion/config.yml`、`textures/panel.png`、`textures/check.png`：实色卡片和图标。
- `backups/completion-hud-*`：每次替换资源前的备份。安装器校验后原子替换，重复执行无变化不写入。

首装：`python tools/install_completion_hud.py <plugins/Xerr> --reference <已确认预览.png>`；以后修改设计配置后直接重跑，不需reference。

`plugins/Quest/lang/zh_cn.yml`新增`completion-hud`整组语言键，旧文件仅追加缺失根节点；保留旧自定义文案和注释。标题支持`<name>`，正文支持`<name>`和`<next>`。完成事件只代表目标完成，默认正文使用“奖励自动发放”，不会在异步发放尚未确认时声称“已到账”。任务和章节自动奖励逻辑不变。

## 源码变更

- `hud/CompletionNoticeText.kt`：独立通知文字投影。
- `hud/QuestNotificationHud.kt`：使用新文字投影，队列和时间线不变。
- `config/ResourceUpgrade.kt`、`QuestFiles.kt`、`lang/zh_cn.yml`：安全追加新语言块，并在加载时校验必需键。
- `tools/install_completion_hud.py`：独立通知资源安装；`install_sidebar_hud.py`不再覆盖通知位置。
- `CompletionNoticeTextTest`、`tools/smoke/CompletionHudSmoke.java`：动态文字、宽度、迁移、真实渲染、实色背景、缓存和到期清理验证。
- `tools/run_smoke.py`、`run_xerr_smoke.py`、`QuestSmoke.java`：接入独立通知验证和最终通知字体引用检查。

## 部署

新JAR预置至`plugins/update/Quest-1.0.0.jar`，在正常停服重启时加载。不要仅通过`quest reload`尝试更换插件类。

资源更新流程：`xerr reload` → `xerr pack async` → 等待完成 → `ce reload pack` → 客户端重新加载资源包。贴图、布局与生成包已备份准备；正式服运行实例和CraftEngine当前托管包是否已更新需分别确认。

## 游戏内验收

1. 接受服务器新资源包，按正常流程完成一个仍有效的任务。
2. 检查屏幕右上角卡片右边贴边，绿色完成图标、动态任务名称和简短正文可见。
3. 连续任务完成/解锁保持单条通知，不重复叠加；到期彻底隐藏，原常驻任务栏保持原位置。
4. 重新登录、重载Xerr后，新的任务事件仍能显示通知；个人通知关闭设置仍生效。
5. 以不同GUI缩放检查右上定位、最长标题及其他插件HUD遮挡。服务器渲染和资源包检查不代表真人客户端视觉已验收。

## 本次验证和部署记录

- Maven构建成功，59项测试全部通过。
- 隔离Leaf真实Xerr检查通过：实色背景、四类通知所有元素右上锚点、动态名称/正文、单槽去重、保持期间不重绘、按已有配置时长到期清理；原任务栏、命令/菜单及坏配置回滚检查通过。
- Xerr资源包重建和CraftEngine原生合并通过：14份Quest纹理RGBA一致、54个bitmap provider未超图集限制、最终引用的9份字体和26份定位shader保留；完成通知实际shader颜色载荷为94e88b/c9c6c0。
- 发布前核对正式服与隔离服全部Xerr/core文件及Xerr JAR哈希一致，再备份并更新正式服Xerr生成目录。
- 新Quest JAR预置于正式服更新目录；未重启正式服，未替换其当前CraftEngine托管包。真人客户端缩放、边缘位置及跨插件HUD避让仍待验收。
