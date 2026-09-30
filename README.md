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
$env:ASB_RENDER_CHECK = 'armor' # mobs / armor / weapons / items / spells / shrines
.\gradlew.bat runClient
Remove-Item Env:ASB_RENDER_CHECK
```

这些命令创建开发测试世界，截图写入 `run-client/screenshots/`。GameTest 类、空测试模板及测试世界数据包均不进入发布 JAR。

## 验证

已通过 `build` 和 45 项 GameTest（含 Accessories 装备与施法磨损集成测试），并完成客户端盔甲、披风、法术特效和两座神殿在真实天境世界自然生成的截图检查。截图位于 `docs/screenshots/`。

GameTest 神殿测试使用真实天境噪声，在结构注册的放置候选区块生成并验证模板；专用测试服务器关闭自动结构生成，因此此测试直接调用结构生成入口。

## 移植说明

- 改用 NeoForge ModDevGradle、Java 21、DeferredHolder 和新事件接口。
- 装备属性采用 1.21.1 的 Holder、ResourceLocation 属性标识和物品组件；盔甲材质通过注册表注册。
- 迁移 Aether 的附件数据和数据驱动恐鸟类型、Iron's Spells 的投射物与武器接口。
- 更新顶点渲染、客户端皮肤接口以及实体同步数据构建方式。
- 数据包迁移至单数目录、1.21.1 配方输出和附魔战利品格式；内置冰系伤害数据包保持可选。
- NeoForge 的 FakePlayer 禁止骑乘，骑乘测试使用带空连接的 ServerPlayer。专用 GameTest 服务器默认丢弃模组维度，因此测试环境提供独立的平坦主世界与固定天境生物群系预设；只用于测试，随发布 JAR 排除。

完整内容、配方及玩法介绍见 [主项目 README](../README.md)。
