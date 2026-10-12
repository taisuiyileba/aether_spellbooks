# 法术书模型与贴图 / Forge 1.20.1

造型与配色参考为 `textures/item/slider_codex.png`、`valkyrie_grimoire.png`、`solar_codex.png` 三张原始 16×16 物品图标。它们没有改动；3D 图集使用对应图标中已有的颜色。

本模型从 1.21.1 同步而来，最初的风格参考来自 **Iron's Spells 'n Spellbooks 1.21.1-3.16.3** 的实际 PNG 和模型，而不是概念效果图：

| 参考资源（`assets/irons_spellbooks/` 下） | 采用的画法与比例 |
| --- | --- |
| `textures/item/spell_book_models/gold_spell_book.png` | 7×10 像素封面、两级金属包角与书脊色带，色块顺着封皮转折分布 |
| `textures/item/spell_book_models/ice_spell_book.png` | 少量冷色阶形成的凹槽与亮色中心，清晰的大像素轮廓 |
| `textures/item/spell_book_models/blaze_spell_book.png` | 紧凑的橙黄／浅色核心，深色封面衬托小面积最亮像素 |
| `models/item/template_spell_book_model.json`、`template_spell_book_model_open.json` | 常规合书的 4×7×10 体量、1 单位厚封皮、5×8 书页面和每单位 1 像素的密度 |

参考纹理只在本地缓存中用于对照，没有复制进发布资源。新纹理由源脚本中的逐像素字符画原创绘制，手工安排石面凹槽、皮面折痕、金属高光和页上字迹，不使用噪声、棋盘铺色或平滑渐变。对照截图通过游戏中原依赖的物品渲染器显示参考书。

| 法术书 | 对齐的视觉特征 |
| --- | --- |
| 魔石法典 | 灰蓝石质封面、铜质四角、青色横向眼纹与浅色中心 |
| 武神圣典 | 蓝色封面、金色四角、银白色上扬羽翼与底部蓝色宝石 |
| 烈阳法典 | 暗红封面、金色四角、橙黄太阳与浅黄色中心 |

图标未显示背面、内衬和展开书页，这些部位沿用封面的材质与色板补全，并非声称从图标完整还原。

## 模型与 UV

- 两个本模组模板：`models/item/template_spell_book.json` 和 `template_spell_book_open.json`。三本书保留 `forge:separate_transforms`，GUI 使用原图标，HEAD 使用展开模型，其他场景使用合书模型。
- 合书外形为 **4×7×10**，封皮厚 **1**，书页内收 **1**。封面、封底、书脊、书芯共 4 个体块，包角和纹饰使用与铁魔法常规书籍相同密度的像素纹理。比上一轮减半封面像素密度、恢复厚实书体。
- 展开模型为 4 个体块和 2 张双面书页；移除薄页原本无面积的侧面，不增加重叠装饰层。
- 每本书使用 **32×32 RGBA** 图集。正面为 `(0,0)-(7,10)`，背面 `(8,0)-(15,10)`，内衬 `(16,0)-(23,10)`，书脊 `(24,0)-(26,10)`，其余 UV 岛定义在源脚本 `UV` 中。封皮长边、短边和纸页端面分别按真实尺寸映射，展开后的半侧书页取对应半幅侧边纹理。模型 JSON 使用 Minecraft 的 0–16 归一化坐标。
- 原手持变换实际面向 WEST，图案上方对应模型的 +Z。正面和图案方向据此修正；第一人称缩放为 0.8，上移 1 单位。第三人称和 HEAD 附着参数保持原值，FIXED 单独对正展示框。

可编辑的权威源为 `tools/build_spellbook_models.py`，已导出的 JSON／PNG 直接用于游戏。修改源脚本后运行：

```powershell
python tools/build_spellbook_models.py
python tools/build_spellbook_models.py --check
.\gradlew.bat build
```

## 检查与证据

```powershell
$env:ASB_RENDER_CHECK = 'books'
.\gradlew.bat runClient
Remove-Item Env:ASB_RENDER_CHECK
```

移植后的客户端使用 Forge 1.20.1 与 Iron's Spells 3.16.0；下列模型、参考、手持和环绕截图在 1.20.1 客户端重新生成。开发检查通过真实物品渲染器加载最终模型。对照图使用固定预览光照；手持截图使用游戏中的实际光照。`BookModelRenderCheck` 与其他 GameTest 辅助类一样被排除在发布 JAR 外。

- 资源检查：三张 GUI 图标逐字节保持不变；三本书合书／展开的所有 UV 均位于图集内且采样区域完全不透明。
- 实机参考对照：[图标、铁魔法参考、正面、侧面、背面及展开](screenshots/asb_books_references.png)。
- 模型检查图：[图标、正面、书页侧、书脊侧、展示框与展开](screenshots/asb_books_comparison.png)。
- 环绕记录：[完整 36 帧旋转](screenshots/asb_books_turntable.gif)，每帧转动 10°，同时变化俯仰角，覆盖三本书的合书、展开及 FIXED 模型。
- 手持记录：[左右手与第三人称](screenshots/asb_books_hands.png)。
- 构建：`gradlew.bat build`。

视觉检查使用上述 Minecraft 客户端连续帧完成。没有验证第三方光影包及资源包覆盖后的效果，也未把图案中的亮色宣称为自发光。
