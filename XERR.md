# Quest × Xerr 部署

Quest 仅增加可选显示适配，不修改任务推进、累计基线、SQLite 或奖励发放流程。目标版本：Leaf/Paper 26.3、Java 25、Xerr 2.1.0、PacketEvents 2.14.0、CraftEngine 26.9.2；不声明 Folia 兼容。

## 文件归属

- `plugins/Quest/显示.yml`：总开关、常驻面板开关、弹窗开关、只读刷新间隔与布局 ID。
- `plugins/Xerr/core/layout/quest_tracker.yml`：常驻面板布局、文案、位置与字体。
- `plugins/Xerr/core/layout/quest_notifications.yml`：五类动态通知布局。
- `plugins/Xerr/core/display/popups/quest.yml`：右上角三个显示槽位、排队数量、时长、进出动画。
- `plugins/Xerr/core/assets/image/quest/`：两张面板、六张图标及材质配置。
- `plugins/Xerr/design/quest/`：生成原图、提示词、素材排版预览和移除记录。使用用户指定接口的 flare / sunburst 模型；没有保存密钥。
- `plugins/Xerr/backups/Quest接入前-20261019-173124.zip`：原始 Xerr 配置完整备份；另存原文件 SHA256 清单及 CraftEngine 原配置备份。

贴图、字体和 Xerr 布局均不随 Quest JAR 分发，不会在 Quest 启动时重新释放或覆盖。用户修改已有任务、章节、语言文件的内容继续保留。没有删除或覆盖正式玩家数据库。

## 打包与生效

已在 CraftEngine 的 `resource-pack.merge-external-folders` 中添加 `Xerr/generated/resource_pack`，保留原合并项、配置字段及注释。使用 CraftEngine 本身的合并、验证、压缩和发送能力，没有增加第二套发送器或下载服务。

首次部署或修改 Xerr 材质/字体后：执行 `xerr reload`，再执行 `xerr pack async` 并等待成功，最后执行 `ce reload pack`。后续 CraftEngine 打包会自动合并已生成的 Xerr 包。修改布局、字体或位置后也必须重打包；修改 Quest 显示行为使用 `quest admin reload`。这里的自动合并不等于 CraftEngine 会替 Xerr 重新生成源资源包。

默认血条、快捷栏、罗盘、演示屏幕、通知和 Stack 已备份后移除；保留空分类目录，避免 Xerr 重新导出示例。关闭默认原版生命/饥饿隐藏与外部 BossBar 接管。只保留 Quest 使用的一个 `ui` 字体定义，规避 Xerr 2.1 同一个 TTF 被多字号并发复制时的 Windows 文件竞争。

## 显示行为与边界

任务目标完成、后续任务解锁、章节开始/完成时使用原有语言文案产生动态弹窗，文字不烘焙入图。原聊天、ActionBar、Title 和声音开关仍由 `提示.yml` 控制，弹窗独立由 `显示.yml` 控制。

常驻面板显示第一个已解锁且进行中的任务、章节完成数及当前目标进度，不显示待领奖数或玩家菜单指令。上一任务未领奖不阻止跟踪下一任务。全部完成后保留章节完成状态，文案来自 `lang/zh_cn.yml:hud-completed`。进度来自异步只读查询；每个空岛合并请求并限制一个查询在途，返回时重新验证玩家、权限、空岛与重载代次。只移除 Quest 自己的布局，不清空其他插件 HUD。

仅在玩家资源包加载成功后显示，同时补读 Paper 当前成功状态，避免启动晚于成功事件而漏收。未安装/禁用 Xerr、拒绝资源包或基岩端未加载 Java 包时，仍保留原任务箱子菜单和原提示。基岩端专属常驻 HUD 不在本次范围。中途热启用 Quest 时已有成功状态也可恢复；未加载成功仍不强行显示。

素材预览不是 Minecraft 客户端截图。隔离服已验证实际 Xerr 字体、图片和变量渲染，以及 CraftEngine 合并后的 PNG 像素/透明度一致；真人 Java/Geyser 客户端显示、位置缩放与点击验收仍需实测。

## 启动后没有 HUD 的已确认故障

2026-10-19 正式服的旧 Genlite 兼容构建要求英文机器文件，遇到中文文件名启动失败，Quest 随依赖停用暂停。修正版基于当前 Genlite 中文配置实现重新构建，保留原文件和物品名称解析，已用正式服 12 个 YAML 的副本完成隔离启动验证。

修正 JAR 暂存于 `plugins/update/Genlite-1.0.0.jar`；配套的 `DreamSkyblock-1.0-SNAPSHOT.jar` 同时暂存，下一次完整重启自动替换。后者用于修复 18:12 外部更换旧依赖后出现的 `HomeMembershipCommittedEvent` 缺失。必须先确认 Genlite 和 Quest 正常启用，再进服加载资源包检查 HUD；仅重载 Xerr 无法恢复已暂停的 Quest。不需要改名、删除配置或重置玩家数据。

## HUD 位置和贴图修复

常驻面板使用右上角锚点 `anchor.x: 100`、`anchor.y: 0`。Xerr 的负 Y 值表示向下偏移，原先 `anchor.y: 50` 会把整个面板放到右侧中线以下。面板及全部文本必须使用相同锚点。

图片配置的 `height` 只控制显示高度，不会缩小 PNG 源字形。Minecraft 26.2 的原版字体图集为 256×256；原 tracker.png（677×719）及 notification.png（950×161）无法作为单个字形放入图集。服务端运行素材分别转换为 151×160、236×40，保留透明度，原图留在 design/quest 及修复前备份。以后生成面板也必须在导入前将每个源字形控制在 256×256 以内；超过时应拆分字形，而非只调小 height。

修改均位于服务端 Xerr 目录，没有将材质加入 Quest JAR。生效顺序仍是 `xerr reload` → `xerr pack async`（等完成）→ `ce reload pack`。没有命令通道时不自动停服或修改 RCON 设置。

如果贴图出现但位置仍旧，检查生成目录里的定位着色器，不要只确认布局 YAML 已改。2026-10-19 18:31 正式服虽已重载 Xerr，生成 ZIP 的时间仍为旧时间，CE 合并的着色器保留 `anchor.y: 50`，因此新贴图与旧位置同时出现。已核对正式服和隔离服全部 19 个输入文件一致，将隔离生成且验证过的 51 个输出文件及 ZIP 同步至正式服 `Xerr/generated/`，旧输出整体备份于 `backups/Quest-generated-sync-20261019-183459`；此次只需再次 `ce reload pack` 发送新包。后续修改布局仍须完整执行上述三步，Xerr 配置重载本身不等于重建字体和着色器。

## Xerr 2.1 文字格式与最终定位通路

- Xerr 的字体包装会转义 value 内的 SimpleComponent 方括号；`[文字](color=...)` 会成为可见文字，并错误参与宽度计算。服务器 `core/layout/quest_tracker.yml` 和 `quest_notifications.yml` 改用单引号 MiniMessage RGB。材质/布局仍只存服务器目录。
- `compat/Xerr/LayoutMiniMessage.java` 为当前 Xerr 2.1 的同步、异步和 Canvas 共用布局路径补上颜色解析：变量处理后、宽度计算和方括号转义前转换为其原生字体包装所需的内部文本传输。只解析 RGB/渐变，不开放点击指令标签；不打包 Paper/Adventure，不访问 Bukkit 或玩家数据。
- 运行 `python tools/patch_xerr.py <原始Xerr-2.1.0.jar> <另一个输出路径>` 构建补丁。脚本固定校验原 LayoutEntity SHA256 和三个注入点，拒绝不同版本、重复打补丁或原地覆盖；源码升级后需重新核实，不能盲用。ASM 仅用于本地构建，不进入 JAR。
- `python tools/migrate_xerr_quest_text.py <服务器plugins/Xerr>` 只替换已知 Quest 旧 value 格式，先备份，保留其他内容、未知字段及注释，重复执行不修改。
- `ui_position_N` 是 Xerr 中间定位字体，最终应该由 `PositionedText.render` 转成资源包内的 `ui_position_dark/light` 和位置标记；颜色由 Xerr 的着色器载荷携带。Quest 原 `appendLayout` 路径省略了转换，现改用 Xerr 自己的 ComponentBuilder → PositionedText → appendLayoutRaw，不自建渲染引擎。
- 自动验证捕获实际 Quest update 提交的数据，断言没有中间字体和格式语法，检查最终 RGB 载荷；同时检查同步、异步、最终字体引用、所有版本定位着色器及 CE 合并后的实际资源。只搜索 JSON 中有颜色字符串并不能证明格式已生效。
- 这些测试仍不代表真人客户端像素验收。补丁部署后需完整重启加载 Quest/Xerr，使用重新生成并由 CE 合并的资源包。
