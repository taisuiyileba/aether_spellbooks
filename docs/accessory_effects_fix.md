# 饰品泛用槽与实际属性回归验证

修复日期：2026-10-14；版本：1.20.1。

四件饰品在对应天境槽、对应通用槽及 Curios 的泛用 `curio` 槽中提供完整效果。属性、项链施法磨损与披风减伤使用同一个槽位规则；不匹配的专用槽位不会提供属性或触发主动效果。没有向正式游戏额外添加槽位。

## 服务端测试

`ASAccessoriesGameTests` 共 7 项，放在独立的 `aether_spellbooks_accessories` 批次。测试玩家由真实世界 tick 驱动原版 `doTick`，让 Curios / Accessories 自己添加、刷新、移除属性；断言读取玩家的实际属性值。

覆盖四件饰品在泛用槽中的真实属性加成与卸下后的移除，以及各支持系统中项链的装备、施法只磨损一次、磨损后加成增长、耗尽后的移除、修复后的恢复、卸下后的恢复原值和披风减伤。另强制将物品放入不匹配的专用槽，验证不会磨损或减伤。

`gradlew.bat build runGameTestServer --offline --console=plain` 已通过，全工程 65 项必要测试全部成功。日志：`build/accessory-fix-verification.log`。

## 客户端同步检查

设置 `ASB_RENDER_CHECK=accessories` 后运行 `gradlew.bat runClient --offline --console=plain`。原有右键与菜单拖放检查结束后，`AccessorySyncCheck` 通过真实 Minecraft 客户端和内置服务器验证实际网络同步路径，同时检查项链物品耐久和玩家实际法术强度。

五阶段期望值（默认配置）：装备 1.05 / 耐久损耗 0；施法磨损 1.0505016722408027 / 1；耗尽 1.0 / 299；修复 1.05 / 0；卸下 1.0 / 空槽。每阶段均检查服务端实际属性，再等客户端收到一致的物品和属性；不同步会使检查报错。

成功标记：`[ASB-ACC-SYNC] all 5 stages passed (equip, wear, depletion, repair, unequip)`。日志：`build/accessory-fix-client.log`。此检查验证单个真实客户端与内置服务器的同步，没有模拟公网延迟或多个玩家同时装备。

开发检查类、临时泛用槽定义和整个测试数据包均已排除在发布 JAR 外。
