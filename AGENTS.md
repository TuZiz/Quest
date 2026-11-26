# Quest 项目协作补充

- 先读README.md、pom.xml和相关实现；遵循用户Minecraft全局规则。目标Paper，Java25/Kotlin2.3.10；不声明Folia兼容，不将AsterStats/ItemHub的Folia声明套给Quest。现有Xerr依赖通过-Dxerr.version=2.3.0匹配leaf安装版本构建。
- Quest奖励定义按物品ID/数量进入邮箱，原WAITING→SENDING→DELIVERED协议不变；不要盲目重放不确定SENDING。IO在Database执行器，背包操作在Paper所有权线程。
- ItemFactory.build用于确定性预览/可用性检查；reward用于实际发奖。ItemHubItemProvider通过可选服务反射接入品质API；预览禁止抽取，发奖按件调用createRandomItem。不要在预览、可用性扫描、背包容量重新规划时反复生成已选定的成品。WAITING还没有持久化品质字节，不能声称它已固定随机结果。
- 品质、成色、数值与name/Lore渲染由ItemHub维护，Quest不复制品质表或Lore数字。已发奖励不因更新重新授予。
- leaf交付前读取C:/Users/32633/Desktop/leaf/AGENTS.md与docs/cloud-plugin-deployment.md；JAR暂存update，自动SSH上传云端，不擅自重启/热加载。
- 2026-11-16全量测试存在12项TAB文案/默认追踪旧预期失败，已在去掉本次品质改动的隔离baseline中复现同12项；不得误报全量通过，也不要在奖励任务中顺便改这些预期。
