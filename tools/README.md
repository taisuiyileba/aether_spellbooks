# 资源工具

从仓库根目录运行脚本。Python 依赖为 Pillow 和 NumPy：

```powershell
python -m pip install -r tools/requirements.txt
```

## 当前美术源

| 脚本 | 输出 |
| --- | --- |
| `build_spellbook_models.py` | 三本法术书图集与合书、展开模型 |
| `item_art.py` | 法剑、锻造模板和空槽符文 |
| `staff_models.py` | 五把法杖的模型与图集 |
| `armor_art.py` | 两套法师甲模型、动画、图集、披风及图标 |
| `entity_art.py` | 施法生物、投射物、光环与粒子贴图 |

每个脚本的 `--check` 都只读现有资源，并在内存中重建后对照；省略该参数会导出资源。预览写入 `build/art_preview/`，不纳入 Git。

参考图仅用于可选的预览对照。可用环境变量 `ASB_REF` 指定参考目录；默认使用系统临时目录下的 `asb_ref`，不依赖开发者的用户名或本机路径。检查和生成原创资源均不需要参考目录。

## 数据与结构

当前配方、标签、战利品及神殿模板直接维护在 `src/main/resources/data/`；1.21.1 的资源目录与格式已经完成版本适配。
