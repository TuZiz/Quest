> 历史记录：2026-10-20已按用户要求移除Xerr HUD，完成提示改聊天。以下部署和指令不再适用于当前版本，现状见README。

# 常驻文字任务栏

完成通知已于2026-10-20独立改为右上角实色卡片，最新说明见`NOTIFICATION-HUD.md`；下文的旧通知下方定位记录由此替代。

当前设计以用户最新要求为准：有有效任务时常驻右侧，文字为主、文字进度条、可选半透明暗底。无后续任务时隐藏。继续使用Xerr资源包HUD，不占用Bukkit计分板，也不修改章节、任务条件、Genlite基线、奖励和玩家数据。平台：Leaf/Paper26.3、Java25、Kotlin2.3.10、Xerr2.1.0、CraftEngine26.9.2；真实客户端26.2观感仍需游戏内验收。

## 显示和指令

- `/quest hud on`：显示常驻任务栏。旧`smart`、`always`均兼容为常驻显示。
- `/quest hud off`：只关闭追踪，不关闭完成通知。
- `/quest hud`：显示与隐藏之间切换。
- `/quest hud view`：立即刷新当前任务，不切换个人关闭设置。
- `/quest hud track <任务ID>`：切换当前空岛已激活任务。
- `/quest hud notifications on|off`：独立控制完成通知。
- `/quest help`：更新后的帮助。`/quest reload`仍由`quest.admin`保护。

UUID偏好继续异步写入原SQLite表，没有新增数据迁移。所有任务完成或当前无空岛任务上下文时隐藏；新目标出现时恢复。相同内容不重绘；Xerr清空槽位、资源包成功加载后恢复有效追踪。玩家退出、Quest重载及禁用清理显示会话。

## 配置

`plugins/Quest/显示.yml`升级为schema4，迁移保留原字段、注释和关闭设置，原件备份在`.migrations/hud-v4/`。不修改数据库。

- `sidebar-layout: quest_sidebar`：文字任务栏布局ID。
- `sidebar-background: true`：可选半透明纯色暗底；false只显示文字。
- `progress-bar: true`：文字进度条，10格，数字百分比精确到整数；例如8/32为25%，两个整格。
- `tracker`、`notifications`：两个独立全局开关。
- 通知保留60ticks（3秒）、最多4条队列、去重和连续完成/解锁合并。
- 旧`tracker-layout`、`compact-layout`、`expand-*`保留兼容，当前常驻文字侧栏不使用展开计时。

正文显示实际章节名称、当前目标说明、数量和文字进度。目标超长自动分两行，超过两行才省略；不将参考中的章节/任务名写入真实业务配置。颜色由Xerr布局调整。

## 服务端资源

只放在`C:/Users/32633/Desktop/leaf/plugins/Xerr/`，不进入Quest JAR。

- `design/quest/任务栏.yml`：GUI单位坐标、尺寸、间距、背景透明度。默认右边距6、右侧中线锚点100/50、顶部在中线上方72单位。
- `core/layout/quest_sidebar.yml`：常驻文字布局，180×118 GUI单位，无旧装饰面板和图片进度条。
- `core/layout/quest_notifications.yml`：精简双行完成/解锁通知，右边缘对齐，位于侧栏下方，间隔12单位；无顶部中央提示。
- `core/assets/image/quest/sidebar/`：只有可选纯色暗底和通知底图。进度█/░直接使用已核实支持的现有12px字体。
- `backups/sidebar-hud-*`：被替换文件的可恢复备份；其他命名空间和原布局保留。

修改设计参数后运行`python tools/install_sidebar_hud.py <plugins/Xerr>`。脚本校验YAML和范围，备份后逐文件原子替换，重复运行无变化不写入。

## 主要改动文件

- `hud/QuestTrackerHud.kt`、`SidebarText.kt`：常驻显示和纯文字投影。
- `hud/HudControls.kt`、`HudSettings.kt`、`QuestHud.kt`、`QuestNotificationHud.kt`：指令、配置、就绪检查和精简通知。
- `platform/QuestCommands.kt`：新帮助入口。
- `config/HudConfigUpgrade.kt`、`QuestFiles.kt`、`ResourceUpgrade.kt`、`显示.yml`、`lang/zh_cn.yml`：安全配置迁移及新文案。
- `SidebarTextTest`、`HudConfigUpgradeTest`、`ResourcesTest`、`tools/smoke/QuestSmoke.java`：受影响测试。
- `tools/install_sidebar_hud.py`、`tools/run_xerr_smoke.py`：服务端资源安装和最终包验证。

## 部署与游戏内验收

1. 新JAR预置到`plugins/update/Quest-1.0.0.jar`，在正常停服重启时加载；不在运行中热替换插件类。旧显示配置由新JAR启动时安全升级。
2. Xerr布局已更新，生成包应重新构建并由CraftEngine合并。管理员执行`xerr reload`，再`xerr pack async`，等待完成后执行`ce reload pack`，使用服务器已有资源包分发流程让玩家重新加载。不要只执行Quest重载或仅替换JAR。
3. `/quest hud on`：应显示右侧文字任务栏；普通生产只更新进度。`off`隐藏，重新登录保持关闭；`on`恢复。
4. 当前目标为生成器原石时核对8/32对应25%、满32显示100%；不通过搬运库存测试进度。
5. 依正常任务流程完成目标：通知单条出现，下一任务替换正文；所有章节完成且无新任务时整栏隐藏，不保留“3/3”或等待面板。
6. 以GUI缩放2/3/自动及不同窗口尺寸检查靠右位置、最长目标换行、百分比、通知间距及现有其他计分板。服务器不知道其他插件HUD实际占用高度，若发生相邻遮挡，调`任务栏.yml`偏移并重新构建资源包。

自动测试与最终渲染/合并包验证不能替代真人客户端对位置、字形和GUI缩放的验收；发布报告明确区分待重启部署与已运行版本。

## 本次自动验证结果

- `mvn -q -o -Dkotlin.compiler.daemon=false package`：55项测试全部通过。
- `python tools/run_smoke.py`：隔离Leaf启动、真实Xerr文字渲染、500次无变化不重绘、背景/条形开关、SMART/ALWAYS常驻、OFF/完成隐藏、下一任务恢复、外部清槽恢复、通知单槽位、命令和菜单检查通过。故意损坏配置的回滚测试通过。
- `python tools/run_xerr_smoke.py`：重新生成Xerr包和CraftEngine原生合并包；12份Quest纹理RGBA一致，51个bitmap provider符合图集限制，最终渲染引用的8份字体和26份定位shader完整保留。
- JAR检查：约14.2MB，只含运行代码与Kotlin/SQLite依赖，没有Paper/Bukkit/Xerr API、测试类、PNG/TTF或源码。
- Neuron检查无规则错误；真人GUI缩放/位置仍为pending。宿主Windows性能计数器和Kotlin/SQLite运行时已有警告未作为新HUD故障。
- 新JAR已预置到正式服更新目录，Xerr源码资源与生成包已更新和备份；尚未重启正式服或改变其当前托管CraftEngine包。
