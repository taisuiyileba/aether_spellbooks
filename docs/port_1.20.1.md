# 1.20.1 未提交内容同步记录

同步日期：2026-10-25。版本：1.2.0（未发布）。以 `1.20.1` 分支 `ef81ee9` 之后的工作区内容为准，更新 `1.21.1` 分支 `5aa6f7a` 之后已有的移植内容。1.20.1 原有的 113 个已修改或新增文件保持内容不变。

## 玩法对齐

- 地牢藏宝箱安全转换、每位玩家独立领奖、整份合并奖励瞬间弹出。
- 按参战 UUID 直接发放钥匙；跨维度可领取，离线、尚未重生或背包已满时持久保存并每秒补发。
- 守卫召回默认重置宝库领奖记录；重战保留天境 boss 原本掉落，通过钥匙再次领取宝库奖励。移除早期移植版的重战直掉奖励、法术书保底及三张重战战利品表。
- 同步 `resetVaultsOnRevival` 和 `modifyIronsSpellbooksVaults`；死者之王命匣复活联动、兼容关闭时不改铁魔法宝库。
- 宝库防止滑行魔石破坏，武神女王恢复整座银牢，保持房间恢复和藏宝室保护规则。
- 同步全部地牢美术：33 张贴图、动画元数据、14 个立体宝库模型、方块状态、物品模型及中英文文本；圣物配方、材料掉落和生成脚本一并对齐。

## 版本适配

| 1.20.1 / Forge | 1.21.1 / NeoForge |
| --- | --- |
| 铁魔法移植的宝库类 | Minecraft 原版 `VaultBlock`、`VaultBlockEntity` 及宝库数据类 |
| 修改有效方块集合的 `BlockEntityTypeAccessor` | `BlockEntityTypeAddBlocksEvent` 注册三种宝库 |
| `ForgeConfigSpec`、Forge 玩家 tick | `ModConfigSpec`、`PlayerTickEvent.Post` |
| 不带注册表参数的存档接口和物品 NBT | `SavedData.Factory`、`HolderLookup.Provider`、物品组件序列化 |
| `RegistryObject`、旧顶点提交、旧实体同步 | NeoForge 注册对象与 `Supplier`、1.21 顶点和实体同步接口 |
| `loot_tables`、`recipes`、`tags/blocks` | `loot_table`、`recipe`、`tags/block` |
| 配方输出 `item`、旧抢夺条件和函数 | 配方输出 `id`、附魔加成条件和物品数量函数 |

铁魔法宝库兼容只处理 `irons_spellbooks` 命名空间中的宝库，Minecraft 试炼宝库保持自身的弹出和领奖规则；回归测试同时检查这两个边界。

## 检查方式

两边的 74 个本次资源文件经过 JSON 格式归一后逐项相同，二进制美术资源逐字节一致。20 项地牢测试按名称对齐；1.21.1 跨维度测试使用测试世界实际加载的天境。原有饰品 API 测试在两个版本中分别组织，因此完整测试总数相差一项。

同时固定旧武神突进测试中靶子的 AI，避免尸壳在延迟断言前离开冲击波范围；死者之王交互测试采用整格坐标，避免随机远距离测试原点下其浮点出生坐标舍入后无法接受命匣。两处均只调整开发测试夹具。

```powershell
# 1.21.1 使用 Java 21；1.20.1 使用 Java 17。
.\gradlew.bat build --offline --console=plain
.\gradlew.bat runGameTestServer --offline --console=plain
python tools/dungeon_art.py --check

$env:ASB_RENDER_CHECK = 'dungeon'
.\gradlew.bat runClient --offline --console=plain
Remove-Item Env:ASB_RENDER_CHECK
```

本次执行结果：

| 检查 | 结果 |
| --- | --- |
| Forge 1.20.1 构建 | 通过 |
| Forge 1.20.1 GameTest | 85 / 85 项必要测试通过，其中 20 项地牢测试 |
| NeoForge 1.21.1 构建 | 通过 |
| NeoForge 1.21.1 GameTest | 84 / 84 项必要测试通过，其中 20 项地牢测试 |
| 两边 `dungeon_art.py --check` | 33 张贴图与 14 个模型均通过 |
| 本次资源内容对照 | 74 个文件全部一致（保留版本格式适配） |
| 1.21.1 客户端 `ASB_RENDER_CHECK=dungeon` | 运行成功，7 张实机截图逐一检查通过 |

发布 JAR 已检查新类、模型和滑行魔石保护标签，测试辅助类、测试数据包、空测试模板及旧重战战利品表均不进入发布包。

客户端确认了宝库的空闲、激活和俯视模型，日夜两种光照下的守卫之魂、召回仪式与物品栏图标。选取的 1.21.1 实机截图已更新至 [宝库](screenshots/dungeon_vaults_active.png)、[仪式](screenshots/dungeon_ritual.png)和[背包](screenshots/dungeon_inventory.png)。
