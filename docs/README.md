# 开发文档索引

本分支面向 Minecraft 1.20.1 / Forge，当前模组版本为 1.2.0。

- [玩法、依赖和构建](../README.md)
- [更新记录](../CHANGELOG.md)
- [法术书模型与 UV](spellbook_models.md)
- [饰品实际属性与客户端同步验证](accessory_effects_fix.md)
- [地牢宝库与守卫召回](dungeon_rematch.md)
- [资源工具说明](../tools/README.md)
- [1.21.1 改动回移记录](backport_1.21.1.md)

源码位于 `src/main/java/`，资源和数据包位于 `src/main/resources/`。开发检查代码集中在 `com/aetherspellbooks/gametest/`，测试夹具位于 `src/main/resources/datapacks/asb_gametest_world/`，二者均从发布 JAR 排除。

`docs/screenshots/` 保存已选取的验证截图和动画；完整运行截图、日志、生成预览和编译结果保留在被 Git 忽略的运行目录或 `build/` 中。
