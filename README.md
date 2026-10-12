# Aether Spellbooks 1.0.0 — NeoForge 1.21.1

原 Forge 1.20.1 版本位于上一级目录。本目录是独立的 Minecraft 1.21.1 移植工程，保留 16 个法术、两种施法生物、两座神殿、法术书、饰品、两套盔甲和法杖／法剑。

原创代码及素材采用 [MIT License](LICENSE)。第三方模组、运行时引用的模型与纹理仍遵循各自许可证；本工程不将这些依赖打包进发布 JAR。

## 环境与依赖

使用 Java 21。开发依赖固定如下，Gradle 会自动下载：

| 依赖 | 验证版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248 |
| Iron's Spells 'n Spellbooks | 1.21.1-3.16.3 |
| The Aether | 1.21.1-1.5.10-neoforge |
| Iron's Lib | 1.21.1-2.1.0 |
| GeckoLib | 4.7.5.1 |
| Curios API | 9.5.1+1.21.1 |
| Player Animator | 2.0.1+1.21.1 |
| Accessories | 1.1.0-beta.53+1.21.1 |
| owo-lib（NeoForge） | 0.12.15.1-beta.6+1.21 |

Aether 自带 Nitrogen 和 Cumulus；需另装表中的 Accessories 新版以替换 Aether 内置的旧版（旧版在开发环境有 Mixin 兼容问题）。玩家安装时需要另外安装上表中的依赖（启动器通常会自动处理依赖）；务必选择 **1.21.1 NeoForge** 文件。

四件饰品同时支持 Curios 以及新版 Aether 的 Accessories 栏，包括动态项链属性、施法磨损和披风减伤。两套饰品系统的槽位配置可由整合包覆盖。

## 构建与运行

在本目录打开终端：

```powershell
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
```

发布文件为 `build/libs/aether_spellbooks-1.21.1-1.0.0-neoforge.jar`。Linux/macOS 使用 `./gradlew`。

客户端截图检查沿用原工程：

```powershell
$env:ASB_RENDER_CHECK = 'armor' # mobs / armor / weapons / items / books / spells / shrines / slider
.\gradlew.bat runClient
Remove-Item Env:ASB_RENDER_CHECK
```

这些命令创建开发测试世界，截图写入 `run-client/screenshots/`。GameTest 类、空测试模板及测试世界数据包均不进入发布 JAR。

## 法术书模型（1.21.1）

魔石法典、武神圣典和烈阳法典的 3D 模型参考铁魔法的冰霜、金制和烈焰法术书，以每模型单位 1 像素、7×10 像素封面重绘。分别保留灰蓝石面与青色眼纹、蓝色皮面与银白羽翼、暗红皮面与金黄太阳，使用成片的材质明暗和铜／金包角。合书外形为 4×7×10，封皮厚度为 1，书页内收 1，与铁魔法常规书籍的体量一致。32×32 图集为封面、背面、内衬、书脊及书页分配独立 UV；原 16×16 背包图标保持不变。

左右手持显示正面纹饰，第一人称略微缩小、上移，展示框朝向与展开模型同步修正。可重建的图集和模型源脚本为 `tools/build_spellbook_models.py`（需要 Pillow），带 `--check` 参数时只校验导出资源。运行 `ASB_RENDER_CHECK=books` 可生成与铁魔法参考书同尺度、同光照的实机对照、36 帧环绕记录以及左右手和第三人称截图。检查辅助类不进入发布 JAR。

资源与检查记录见 [法术书模型说明](docs/spellbook_models.md)。

## 铁魔法与滑行魔石联动

- **投掷**：扔出的镐子及其他符合天境挖掘工具规则的物品可以唤醒并伤害滑行魔石。投掷工具的伤害为 **法术伤害 + 该工具的攻击伤害**，支持主手／副手、锋利附魔及力量等攻击属性加成；不会借用另一只手中武器的攻击力或重复叠加。物品与伤害在发射时确定，换手或修改原物品不影响飞行中的投射物。普通物品不会因此获得破防能力。
- 无额外加成时，一级投掷的木／石／铁／钻石／下界合金镐面板伤害分别为 **3 / 4 / 5 / 6 / 7**。实际伤害继续经过目标抗性和天境的 `tools_debuff` 规则；默认情况下，天境还会削弱外来投射物，因此实际扣血可能低于面板数值。
- **幽冥锤**：可以直接瞄准 16 格内的滑行魔石施放，复用原有锤子模型、动画和音效，按原动画中锤头下缘的位置对齐滑行魔石顶面，在第 13 tick（约 0.65 秒）落锤时结算一次伤害。生成时锁定挥击方向，尚未消失的锤子特效不阻挡后续施法。目标离开落点、出现墙体遮挡或施法者离开地牢房间时会落空。攻击分支不会挖掉周围方块，普通挖矿用法保持原样。
- 幽冥锤默认基础伤害为 **6 / 8 / 10 / 12 / 14**，受法术强度、唤魔强度、铁魔法的法术倍率配置和目标抗性影响。法术详情新增中英文滑行魔石伤害提示。
- 保留天境的和平难度、地牢房间边界、首次命中唤醒并回满生命、仇恨和受击流程；幽冥锤仍遵守冒险模式限制。碎岩飞刃等既有兼容继续生效。

配置位于 `config/aether_spellbooks-common.toml`；修改后重启游戏／服务器生效：

```toml
[sliderCompatibility]
thrownToolsDamageSlider = true
spectralHammerDamageSlider = true
hammerBaseDamage = 6.0
hammerDamagePerLevel = 2.0
```

联机时以服务端数值为准，客户端使用相同配置可保持法术详情显示一致。兼容通过本附属的四个 Mixin 实现，不需要替换天境或铁魔法的 JAR。

## 验证

已通过 `build` 和 59 项 GameTest（原有 45 项，加上 14 项滑行魔石兼容测试）。新增测试覆盖真实投掷施法和换手、各材质镐的投射物伤害与实际扣血、副手锋利附魔及物品快照、力量加成且不重复叠加工具伤害、工具筛选、普通法术仍被拒绝、幽冥锤唤醒与单次扣血、射程／墙体／实体遮挡、冒险与和平模式、地牢边界、移动目标、配置开关、原有挖矿和攻击时不挖方块。

客户端幽冥锤已从正面和侧面各检查完整的 32 帧序列，包含起手、命中和收招，记录见 [正面动画](docs/screenshots/slider_hammer_front.gif) 与 [侧面动画](docs/screenshots/slider_hammer_side.gif)。运行 `ASB_RENDER_CHECK=slider` 可重新生成连续截图。真实多人联机手感仍需人工体验。

此前已完成客户端盔甲、披风、法术特效和两座神殿在真实天境世界自然生成的截图检查，截图位于 `docs/screenshots/`。

GameTest 神殿测试使用真实天境噪声，在结构注册的放置候选区块生成并验证模板；专用测试服务器关闭自动结构生成，因此此测试直接调用结构生成入口。

## 移植说明

- 改用 NeoForge ModDevGradle、Java 21、DeferredHolder 和新事件接口。
- 装备属性采用 1.21.1 的 Holder、ResourceLocation 属性标识和物品组件；盔甲材质通过注册表注册。
- 迁移 Aether 的附件数据和数据驱动恐鸟类型、Iron's Spells 的投射物与武器接口。
- 更新顶点渲染、客户端皮肤接口以及实体同步数据构建方式。
- 数据包迁移至单数目录、1.21.1 配方输出和附魔战利品格式；内置冰系伤害数据包保持可选。
- NeoForge 的 FakePlayer 禁止骑乘，骑乘测试使用带空连接的 ServerPlayer。专用 GameTest 服务器默认丢弃模组维度，因此测试环境提供独立的平坦主世界与固定天境生物群系预设；只用于测试，随发布 JAR 排除。

完整内容、配方及玩法介绍见 [Forge 1.20.1 README](../aether_spellbooks_1.20.1/README.md)。
