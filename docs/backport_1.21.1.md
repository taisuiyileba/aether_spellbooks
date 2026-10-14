# 1.21.1 改动回移到 Forge 1.20.1

同步日期：2026-10-14。来源为相邻 `aether_spellbooks_1.21.1` 工程的当前工作区：已提交至 `1bf4aa1`，包含随后尚未提交的双槽位饰品说明和测试改动。目标从 `242d9d9` 开始修改。

目标此前已包含 `7a34388` 对应的法术书与滑行魔石联动，因此本次主要同步该提交之后的美术、渲染与饰品改动。

| 范围 | 1.20.1 中的实现 |
|---|---|
| 资源 | 同步 1.21.1 当前工作区的模型、动画、像素图集和中英文饰品说明；三本法术书保留 Forge 的 `forge:separate_transforms` 加载器标识 |
| 法杖 | 五把法杖在所有显示场景使用 3D 模型，武神权杖采用实体羽翼 |
| 法师甲 | 同步两套 Geo 模型、羽翼和冠羽动画、图集、披风及物品图标 |
| 发光投射物 | 雷晶加入带边缘的动画精灵图，烈阳核心改为透明混合；保留外层叠加光晕，并主动推进贴图动画 |
| 粒子 | 羽毛往返翻动，境云粒子在镜头附近淡出，重力微粒两 tick 播放一帧 |
| 饰品 | 保持天境和通用 Curios 槽位标签、相同属性和既有事件处理；移植最新说明与验证，并补上旧版 Curios 的右键装备开关 |
| 开发检查 | 同步新增特效与每把法杖的第一人称截图场景；新增饰品右键和菜单拖放场景 |

版本适配包括：保持 Forge 事件和 Java 17；使用旧版顶点接口 `normal(...).endVertex()`；披风使用 `getCloakTextureLocation()`；天境菜单通过 `AetherPacketHandler` 打开。1.20.1 的天境饰品栏本身使用 Curios，不需要 1.21.1 的 Accessories 桥接类或其依赖。客户端验证发现旧版 Curios 的默认 `canEquipFromUse` 返回 false，故在 `MultiSlotCurioItem` 中为匹配槽位显式启用右键装备。

四件饰品仍支持 `aether_ring`／`ring`、`aether_pendant`／`necklace`、`aether_gloves`／`hands`、`aether_cape`／`back`。开发数据包仅为测试玩家提供四种通用槽位，通过 `asb.gametest=true` 或饰品截图场景启用；数据包和全部开发检查类均从发布 JAR 排除。

## 验证

- Java 17：`gradlew.bat build runGameTestServer --offline --console=plain` 成功，全部 62 项必要测试通过。
- 新增四项饰品测试覆盖两套槽位、实际天境菜单的接纳判定、不匹配槽位的拒绝、属性一致、真实 Forge 右键装备事件、施法磨损、耗尽后属性归零、披风减伤和脱下后的恢复。
- `build_spellbook_models.py`、`item_art.py`、`staff_models.py`、`armor_art.py`、`entity_art.py` 的 `--check` 全部通过。
- 25 张 `.png.mcmeta` 的帧尺寸、帧数和帧序号均在图片范围内。
- 法术书生成脚本额外校验 Forge 加载器标识，防止跨版本复制模型时出现缺失模型。
- 发布包包含 Mixin refmap 和 25 张动画贴图，排除了 GameTest 类、空模板和开发槽位数据包。

客户端通过 `ASB_RENDER_CHECK` 完成饰品、盔甲、武器、法术特效和施法生物五类场景检查。饰品场景使用真实右键和菜单拖放；存在两套空槽时，右键按照 Curios 顺序选择首个匹配槽位，装备在通用槽位的饰品可在 Curios 界面查看。确认四件饰品可以拖入天境对应槽位。修正 Forge 模型加载器后，武器、法术和施法生物场景未出现本附属的模型加载错误。

实机截图保存在 `docs/screenshots/backport_1.21.1/`：

- [两套法师甲](screenshots/backport_1.21.1/asb_armor_front.png)、[背面与披风](screenshots/backport_1.21.1/asb_armor_back.png)。
- [武器展示](screenshots/backport_1.21.1/asb_weapons_wall.png)、[物品栏](screenshots/backport_1.21.1/asb_weapons_inventory.png)、[武神权杖第一人称](screenshots/backport_1.21.1/asb_weapons_hand_valkyrie_scepter.png)。
- [烈阳核心](screenshots/backport_1.21.1/asb_spell_solar_flare.png)、[雷晶](screenshots/backport_1.21.1/asb_spell_thunder_crystal.png)、[西风冲击](screenshots/backport_1.21.1/asb_spell_zephyr_blast.png)。
- [施法生物正面](screenshots/backport_1.21.1/asb_front.png)、[背面](screenshots/backport_1.21.1/asb_back.png)。
- [饰品右键装备](screenshots/backport_1.21.1/asb_accessories_rightclick.png)、[天境界面拖放](screenshots/backport_1.21.1/asb_accessories_drag.png)。

发布文件：`build/libs/aether_spellbooks-1.20.1-1.1.0.jar`。真实多人联机的装备同步与飞行手感仍需人工体验。

## 后续审核修复

两个版本已统一泛用 `curio` 槽中的属性和主动效果，并补上通过正常 tick 验证玩家实际属性与真实客户端同步的检查。当前 1.20.1 全套为 65 项 GameTest；详细记录见 [饰品实际属性与同步验证](accessory_effects_fix.md)。
