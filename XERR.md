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

首次部署或修改 Xerr 材质/字体后：执行 `xerr reload`，再执行 `xerr pack async` 并等待成功，最后执行 `ce reload pack`。后续 CraftEngine 打包会自动合并已生成的 Xerr 包。只有修改布局时通常只需 `xerr reload`，修改 Quest 显示行为使用 `quest admin reload`。这里的自动合并不等于 CraftEngine 会替 Xerr 重新生成源资源包。

默认血条、快捷栏、罗盘、演示屏幕、通知和 Stack 已备份后移除；保留空分类目录，避免 Xerr 重新导出示例。关闭默认原版生命/饥饿隐藏与外部 BossBar 接管。只保留 Quest 使用的一个 `ui` 字体定义，规避 Xerr 2.1 同一个 TTF 被多字号并发复制时的 Windows 文件竞争。

## 显示行为与边界

任务目标完成、后续任务解锁、章节开始/完成时使用原有语言文案产生动态弹窗，文字不烘焙入图。原聊天、ActionBar、Title 和声音开关仍由 `提示.yml` 控制，弹窗独立由 `显示.yml` 控制。

常驻面板显示第一个已解锁且进行中的任务、章节完成数、当前目标进度及未领取任务/章节奖励数。上一任务未领奖不阻止跟踪下一任务。全部完成且奖励已领取时隐藏。进度来自异步只读查询；每个空岛合并请求并限制一个查询在途，返回时重新验证玩家、权限、空岛与重载代次。只移除 Quest 自己的布局，不清空其他插件 HUD。

仅在玩家收到资源包加载成功事件后显示，避免未加载字体时出现乱码。未安装/禁用 Xerr、拒绝资源包或基岩端未加载 Java 包时，仍保留原任务箱子菜单和原提示。基岩端专属常驻 HUD 不在本次范围。中途热启用 Quest 时可能需要重新进服或重新加载资源包才能收到成功事件。

素材预览不是 Minecraft 客户端截图。隔离服已验证实际 Xerr 字体、图片和变量渲染，以及 CraftEngine 合并后的 PNG 像素/透明度一致；真人 Java/Geyser 客户端显示、位置缩放与点击验收仍需实测。

## 启动后没有 HUD 的已确认故障

2026-10-19 正式服的旧 Genlite 兼容构建要求英文机器文件，遇到中文文件名启动失败，Quest 随依赖停用暂停。修正版基于当前 Genlite 中文配置实现重新构建，保留原文件和物品名称解析，已用正式服 12 个 YAML 的副本完成隔离启动验证。

修正 JAR 暂存于 `plugins/update/Genlite-1.0.0.jar`；配套的 `DreamSkyblock-1.0-SNAPSHOT.jar` 同时暂存，下一次完整重启自动替换。后者用于修复 18:12 外部更换旧依赖后出现的 `HomeMembershipCommittedEvent` 缺失。必须先确认 Genlite 和 Quest 正常启用，再进服加载资源包检查 HUD；仅重载 Xerr 无法恢复已暂停的 Quest。不需要改名、删除配置或重置玩家数据。
