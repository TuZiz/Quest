# 引航员交互：直接打开空岛界面

首次右键现有 Adyeshach NPC `新人`，Quest 立即调用 DreamSkyblock 的 `dreamskyblock:home gui`。无岛玩家进入创建／加入界面，已有空岛玩家进入空岛管理界面。NPC 名称、模型和位置不变。

不再启动 Typewriter 对话、不需要潜行确认，也不需要再次右键才进入菜单。旧 Typewriter 页面和模型文件不会被删除或改写，但 Quest 已停止触发它们；旧页面遗留的完成回调不会推进个人任务。

## 进度与奖励

- `meet_guide` 永久键、UUID 个人进度、旧 DONE / SKIPPED / LEGACY 记录全部保留。
- 首次有权限交互并成功派发空岛 GUI 命令后记录个人任务完成；不发放机器、燃料、金币或章节奖励。
- `sky_start` 仍通过真实的创建／加入空岛状态完成，关闭界面不会完成此任务。
- 重复点击有冷却，重登补读原记录；`/quest guide replay` 现为打开空岛界面的兼容入口，`skip` 保留显式跳过语义。
- 依赖未就绪或缺少 DreamSkyblock 普通使用权限时，提示错误，不保存本次个人完成记录。

## 升级

更新 Quest JAR 并完整重启服务端。`引导.yml` 保留 schema 1；`typewriter-entry-id` 和 `timeout-seconds` 兼容旧配置读取，但不再启动对话。

内置语言会进行幂等的精确默认文案替换，保留自定义字段、未知字段、注释和 CRLF 换行；首次变更前备份到 `plugins/Quest/.migrations/introduction-menu-v1/lang.before`。SQLite 结构和奖励记录不变。其他插件的 Typewriter 对话不受影响。
