# Control Flex × EMI 兼容桥 — 设计文档

> **分支说明（1.21.1）**：本文最初为 1.20.1 分支所写，1.21.1 分支沿用同一套架构，差异只有三条：
> ① 加载器是 **Fabric + NeoForge**（1.21.1 没有 Forge）；② 目标是 MC 1.21.1 / Java 21；
> ③ EMI 为 `1.1.24+1.21.1`，发布产物同样是 25 个 `EmiBind`（没有 `displayAllRecipes`），动作数仍是 22。
> 文中以 Forge 为名的部分（`mods.toml`、`net.minecraftforge.*` 事件）在 1.21.1 上对应 `neoforge.mods.toml`
> 与 `net.neoforged.neoforge.client.event.*`；事件总线改由 `@Mod` 构造器注入的 `ModContainer` 取得。

- 项目：`cfx-compat-emi`
- 目标版本：Minecraft 1.20.1 / Forge 47.x + Fabric 0.15.x
- 依赖：ControlFlex ≥ 0.8.9、EMI 1.1.24（`dev.emi:emi-*`）
- 状态：设计已确认，待实现

## 1. 背景

EMI 的 26 个键位全部走自建体系：`dev.emi.emi.input.EmiBind` 存在 EMI 自己的 QDCSS 配置（`binds.*`），在 EMI 的屏幕里用 `matchesKey` / `matchesMouse` 匹配，**不注册任何 Minecraft `KeyMapping`**。

ControlFlex 的动态动作池来自 `mc.options.keyMappings` 扫描（`ModCompatManager.scanModdedKeyMappings()`），因此 EMI 在 CF 的模组适配列表里是空集，原版「控制 → 按键绑定」里同样看不到。玩家（以及我们自己）无法用手柄触发查看配方、收藏、合成这些操作。

调研结论见 `control-flex-1.20.1/docs/research/research-emi-compat-bridge.md`。本设计是该调研的落地版本，并修正了其中一处判断（见 D2）。

## 2. 目标

**v1 只做「动作层」：手柄按下后，EMI 里真的发生那件事。**

- 22 个 EMI 动作可在 ControlFlex 中绑定、改绑、分层；
- 动作在 EMI 界面内正确执行（查看配方/用途、收藏、合成、隐藏、作弊取出、前进后退、搜索控制等）；
- Forge 与 Fabric 双加载器行为一致；
- 不需要 ControlFlex 本体做任何改动，不需要 mixin。

## 3. 非目标

- **EMI 键位自身的改绑**：CF 里的绑定不写回 EMI 的 QDCSS 配置。玩家在 CF 里改的是「手柄哪个键触发哪个动作」，不是「EMI 的 R 键改成什么」。
- **三个残留动作**：`defaultStack`、`deleteCursorStack`、`showCraft`。前两个是 `RecipeScreen` / `mouseReleased` 里的内联逻辑，为它们开 mixin 不划算；`showCraft` 是按住态（`isHeld()` 轮询），与其余 22 个「瞬时动作」不是同一类语义。
- **`displayAllRecipes`**：EMI 1.1.24 的源码树里有这个 `EmiBind`，但发布产物里没有（实测 25 个字段）。本桥对齐发布产物，故不暴露。
- **表现层打磨**：guide 角标（「按 X 查看配方」）、径向菜单图标、无光标时的目标回退。留待 v2。
- **NeoForge / 1.21**：本分支只做 1.20.1 的 forge + fabric。
- **改动 ControlFlex 或 control-flex-api**。

## 4. 反目标（即使能跑也算失败）

- 需要玩家手动编辑 JSON 或配置文件才能用；
- 与 EMI 自身键位双触发（一次按下执行两遍）；
- 在没有 EMI 的界面上按动作时抛出异常或刷屏日志；
- 装了这个桥，但 CF 列表里出现 22 个按下去没反应的死键。

## 5. 约束

- **无 mixin**。EMI 与 CF 的公开入口已足够（`EmiScreenManager.stackInteraction` / `genericInteraction` / `recipeInteraction` / `toggleVisibility` 均为 `public static`）。
- **跨映射**：EMI 的 xplat 产物在 Fabric 是 Yarn 名（`MinecraftClient` / `InputUtil`），Forge 是 Mojmap 名（`Minecraft` / `InputConstants`）。`common/src/main/java` 会被两个 loader 子工程各自 `srcDir` 收编并**编译两次**，所以 common 里不能出现任何 EMI 或 MC 的类型。
- **客户端模组**：`environment: client`，不需要服务端。
- **EMI 必须是硬依赖**：没有 EMI 时整个模组无意义。

## 6. 关键决策

### D1 — 用伪 `KeyMapping` 让 CF 认识这些动作

CF 的 `scanModdedKeyMappings()` 只过滤原版键名和 `key.controlflex.` 前缀，其余全部收编；`registerDynamicActions()` 会为每个键注册 `groupKey:keyName` 形式的动态动作，**不要求存在 compat JSON**。

所以桥接注册 22 个 `KeyMapping` 即可让 CF 自动发现，CF 侧零改动。这是唯一不改 CF 又能出现在列表里的路径。

### D2 — 用 `KeyMapping` 状态触发，而不是 `IActionStateProvider`

调研时以为可以轮询 `isGuiActionActive(actionId)`。**这个判断是错的**：

`BindingMapper` 的 GUI 相位只有 `HOLD` / `TOGGLE` / `DELAY_HOLD` / `LONG_HOLD` / `DOUBLE_HOLD` / `DOUBLE_TOGGLE` 会把动作放进 `activeGuiActions`；而 `PRESS` / `TAP` / `RELEASE` / `LONG_PRESS` / `DELAY_PRESS` / `DOUBLE_PRESS` / `DOUBLE_TAP` 只走 `addPendingGuiRelease()`，`isGuiActionActive()` 恒为 false。手柄最自然的 TAP 映射会完全失灵。

改用 CF 一定会做的那个动作：`GuiActionHandler.handleDynamicCompatAction()` 无条件执行 `controlflex$setPressed(true)`（virtualKbm 通道除外）。于是：

- `KeyMapping.isDown()` → 按住；
- `KeyMapping.consumeClick()` → 一次性点击。

要注意 `controlflex$setPressed(true)` 是 `setDown(true)` + **无条件** `clickCount++`（每次调用都加，不判状态翻转），所以 HOLD 模式下 `clickCount` 会逐 tick 增长。检测器必须先看 `isDown` 的上升沿，`clicked` 只作为「按住已经结束、但这一帧内发生过按下」的兜底，并且每帧都要把 `clicked` 读掉（`consumeClick()` 读即消费）。

另外 `processPendingGuiReleases` 的注释写明释放发生在**后续 tick**（"The release must be dispatched on a subsequent tick"），所以一次性动作的 `isDown` 在按下那一帧一定还为 true——上升沿路径是主路径，`consumeClick()` 是保险。

两个信号组合即可覆盖全部触发模式，且不依赖 `IActionStateProvider`。副作用是桥接对 CF API 的依赖降到只剩 `ICompatAssetInstaller`（0.8.5+）。

### D3 — 用谓词调 EMI 分发器，不注入按键

`EmiScreenManager.stackInteraction(EmiStackInteraction, Function<EmiBind, Boolean>)` 这类签名允许传入「只匹配某一个 bind」的谓词，从而直接复用 EMI 自己的完整行为分支（含合成、作弊、隐藏等）。

比注入按键好在：不依赖 `EmiInput.getCurrentModifiers()` 的真实 GLFW 修饰键状态（它读 `Screen.hasControlDown()` / `InputUtil.isKeyPressed`），不受扫描码匹配限制。焦点让位规则不交给宿主屏幕的 `keyPressed` 去决定，而是在 D7 里显式复刻 EMI 自己的判定。

### D4 — 全部动作归 GUI 层

EMI 只在开了界面时有意义。CF 对无配置的动态动作回落到 `MAIN` 层（`resolveDefaultLayers()` 的 `isEmpty()` 分支），GUI 相位不派发。所以必须随包安装 compat JSON，把 22 个键全部写进 `screenKeys`，并显式声明 `channels: ["keyMapping"]`。`ModCompatConfig.DEFAULT_SCREEN` 就是 `{keyMapping, screenInput}` —— 不声明的话 CF 一定会走 `screenInput` 通道，把 `UNKNOWN_KEY`（键码 -1）送进 `screen.keyPressed`。

### D5 — 键位用自有命名空间

不复用 EMI 的 `key.emi.*` 作为 `KeyMapping` 名。`KeyMapping` 名是全局标识（Forge 的 `KeyMapping.MAP` 以 name 为键），一旦 EMI 未来真的注册了同名键位，两个 KeyMapping 会撞名，我们的那个静默失效。

改用 `key.cfx_compat_emi.<action>` + 自带中英语言文件（22 条 + 1 条分类，成本可忽略）。分类同样用自有 key `key.categories.cfx_compat_emi`。**分组归属不由前缀决定**：CF 按 compat JSON 的 `mod_id`（归属索引）分组，所以这套键名照样显示在 "EMI" 分组下；前缀只用于避免与 EMI 未来注册的同名 `KeyMapping` 冲突。

### D6 — `common` 承载全部映射相关代码（实现期修正）

原计划是「`common` 只放映射无关代码，EMI 调用在两个 loader 各写一份」。**实现时前提不成立**：Fabric 侧用的是 `loom.officialMojangMappings()`（与 `cfx-compat-create` 一致），两个加载器编译出的 MC / EMI 类型名完全相同，写两份既无收益又容易漂移。

最终结构：`common` 放 `EmiKeys` / `EmiDispatch` / `EmiTicker`（含 EMI 与 MC 类型），per-loader 只剩注册与 tick 接线（各约 25 行）。代价是 `common` 与映射绑定：若将来某个加载器改用 Yarn，需要把这三个文件搬回各自 loader。

### D7 — 门禁按 EMI 的判定复刻，不自作主张

CF 的哲学是「手柄按键是命令，不是输入」（必要时先让输入框失焦再送键）。本桥**不采用**这条：手柄按下的语义是「替玩家执行 EMI 的某个动作」，如果 EMI 自己都不认这个键，桥接过去只会造成「我在铁砧里打字，角色却在翻配方」的错乱。

门禁顺序完全对齐 `EmiScreenManager.keyPressed()`：

1. 无屏幕 / `EmiScreenBase.getCurrent()` 为空 → 丢弃；
2. `EmiScreenManager.isDisabled()`（即 `!EmiReloadManager.isLoaded() || !EmiConfig.enabled`）→ 只放行 `toggleVisibility`，其余丢弃。这一条同时挡住「EMI 被关掉」和「EMI 正在重载」两种状态，避免在重载窗口里反复抛异常再被 `catch Throwable` 记成 warn；
3. 屏幕元素树内存在 `isActive() && visible` 的 `TextFieldWidget` → 全部丢弃。EMI 的 `hasFocusedTextField(screen, 10)` 是 private，两个 loader 各复刻一份：递归遍历 `ParentElement.children()`，深度 10。

副作用要说清：在铁砧命名框、告示牌、书、其他模组的输入框里打字时，绑定动作不会触发 —— 与 EMI 自身键位的表现一致。

### D8 — `controlflex` 是硬依赖

桥接离开 CF 没有意义，所以按生态惯例写死：Forge `mods.toml` 里 `mandatory=true`、`ordering="AFTER"`、`versionRange="${controlflex_version_range}"`；Fabric `fabric.mod.json` 的 `depends` 放同一区间。运行期不再判断 `isModLoaded("controlflex")`（加载器已经保证），也不保留「没装 CF 也能启动」的分支。

### D9 — 配方上下文取并集

`EmiApi.getRecipeContext(EmiIngredient)` 只对 `EmiFavorite` 返回非空，直接用它会让 `copyId` 在配方页上变成死键。而 `EmiStackInteraction.getRecipeContext()` 是 public 的，配方页悬停槽位、侧栏收藏项都返回非空。

故 `RECIPE` 类动作的上下文解析顺序：先 `hovered.getRecipeContext()`，为 null 时退回 `EmiApi.getRecipeContext(hovered.getStack())`。

**实测修正**：只有侧栏收藏项（以及带 recipe context 的合成结果槽）能给出非空上下文。配方页 `RecipeScreen` 不是 `HandledScreen`，`EmiApi.getHoveredStack()` 在那里拿到的是 `lastStackTooltipRendered` 兜底，recipe 为 null；EMI 自己在配方页用的是私有的 `group.recipe`，无 mixin 够不到。因此 **`copyId` 在配方页是空操作**，v1 接受并在 README 说明。

## 7. 架构

```
cfx-compat-emi/
├── settings.gradle                 include 'forge', 'fabric'
├── gradle.properties               mod 元数据 + 版本号
├── common/src/main/java/com/ifels/cfx/emi/
│   ├── CfxEmiPlugin.java           IControlFlexPlugin：导出 compat JSON
│   ├── EmiAction.java              动作表（id / bindKey / kind）
│   ├── ActionKind.java             STACK / STACK_THEN_RECIPE / RECIPE / GENERIC
│   ├── EdgeDetector.java           按下/点击去重状态机（纯 Java）
│   ├── EmiKeys.java                22 个 KeyMapping + 查找表
│   ├── EmiDispatch.java            EmiConfig / EmiScreenManager 调用（谓词分发）
│   └── EmiTicker.java              每帧采样 + 门禁 + 触发
├── common/src/main/resources/
│   ├── assets/cfx_compat_emi/compat/cfx_compat_emi.json
│   ├── assets/cfx_compat_emi/lang/{en_us,zh_cn}.json
│   └── META-INF/services/com.ifels.controlflex.api.IControlFlexPlugin
├── forge/src/main/java/com/ifels/cfx/emi/forge/
│   └── CfxEmiMod.java              @Mod：RegisterKeyMappingsEvent + ClientTickEvent.Post
└── fabric/src/main/java/com/ifels/cfx/emi/fabric/
    └── CfxEmiMod.java              ClientModInitializer：KeyBindingHelper + END_CLIENT_TICK
```

### 7.1 `CfxEmiPlugin`（common）

实现 `IControlFlexPlugin`，只做一件事：`onInstallCompatConfigs(ICompatAssetInstaller)` 里把 `/assets/cfx_compat_emi/compat/cfx_compat_emi.json` 安装成 `mods/cfx_compat_emi.json`。

实现说明：v1 直接调用 `installer.install(...)`，没有抄 epicfight 的 SHA-1 比对，因此 CF 每次启动都会重写一份同内容文件。可接受（几十字节、无副作用），若将来文件变大再加 `needsOverwrite` 门。

`getModId()` 返回 `cfx_compat_emi`；`onControlFlexReady()` 只打一行日志（v1 不需要 CF 的任何运行时服务）。

SPI 注册文件：`META-INF/services/com.ifels.controlflex.api.IControlFlexPlugin`。

### 7.2 `EmiAction`（common）

一个枚举，22 个条目，字段：

| 字段 | 说明 |
|---|---|
| `id` | CF 动作名，`key.cfx_compat_emi.<name>` |
| `bindKey` | 对应 `EmiBind` 的字段名（`viewRecipes` 等），仅用于两个 loader 的 switch |
| `kind` | 分发类别，见 §8.5 |

`id` 与 `bindKey` 分开是为了让 common 不需要引用 EMI 类型，同时让 per-loader 的 switch 有稳定键。

### 7.3 `EdgeDetector`（common）

每个动作一个实例，纯状态机：

```java
public boolean update(boolean isDown, boolean clicked) {
    boolean fire = (isDown && !lastDown) || (!isDown && clicked);
    lastDown = isDown;
    return fire;
}
```

- 按住（HOLD 模式）：`isDown` 持续 true，只有首帧 fire；
- 轻点（TAP/PRESS 模式）：若在两帧之间按下又松开，采样时 `isDown` 已回落，但 `consumeClick()` 为 true → fire；
- 同一 tick 内连点两次：`clickCount` 累加到 2，`consumeClick()` 只返回一次 true → 只 fire 一次（可接受）。

调用方**每帧都必须把 `clicked` 抖干净**（`keyMapping.consumeClick()` 读一次就算消费），否则门禁跳过的那些帧会把点击积攒到后面误触发。

## 8. 详细设计

### 8.1 键位注册

- 22 个 `KeyMapping`，name = `key.cfx_compat_emi.<bindKey 转 snake_case>`，category = `key.categories.cfx_compat_emi`，默认键 = `InputConstants.UNKNOWN_KEY`（`InputConstants.UNKNOWN`）。
- 默认键为空是刻意的：伪键位若绑了真实键，玩家直接敲键盘也会触发，且可能与 EMI 自身绑定双触发。
- **`controlflex` 由加载器保证在位**（D8），运行期不再判断 `isModLoaded`；模组本身是 client-only，不需要额外分发。
- 注册时机：Forge `RegisterKeyMappingsEvent`；Fabric `KeyBindingHelper.registerKeyBinding`（`ClientModInitializer`）。
- 顺序安全：CF 的扫描发生在 deferred init（`ModCompatManager.initialize()`），晚于所有模组的键位注册。
- 自定义分类在 Forge 直接 `new KeyMapping` 会进 `KeyBindsScreen` 的 `Arrays.sort`；CF 的 `KeyMappingAccessorMixin` 已把 `compareTo` 改成「缺失分类不参与比较」的兜底，所以不会 NPE（Fabric 侧 `KeyBindingHelper` 另有 `addCategory` 补偿）。这条依赖同样由硬依赖保证。

### 8.2 触发循环

Forge：`ClientTickEvent.Post`；Fabric：`ClientTickEvents.END_CLIENT_TICK`。每帧：

```
Screen screen = mc.screen;
if (screen == null) { drainAll(); resetAll(); wasInEmi = false; return; }
if (EmiScreenBase.getCurrent().isEmpty()) { drainAll(); resetAll(); wasInEmi = false; return; }
if (!wasInEmi) { drainAll(); resetAll(); wasInEmi = true; }   // 进入 EMI 界面那一帧：丢掉陈旧按下

boolean disabled = EmiScreenManager.isDisabled();
boolean focusedField = !disabled && hasFocusedTextField(screen);  // 关闭态先判开关，不看聚焦

List<EmiAction> fire = new ArrayList<>();
for (EmiAction action : EmiAction.values()) {
    KeyMapping km = keyMap.get(action);
    boolean down = km.isDown();
    boolean clicked = km.consumeClick();
    if (!down && !clicked) continue;                 // 快路径，绝大多数帧走这里

    if (disabled && action != EmiAction.TOGGLE_VISIBILITY) continue;
    if (focusedField) continue;

    if (detectors[action].update(down, clicked)) fire.add(action);
}
if (fire.isEmpty()) return;

EmiScreenManager.recalculate();                      // 复刻 EMI keyPressed 的前置
for (EmiAction action : fire) dispatch(action);      // 见 8.4
```

三个辅助动作：

- `drainAll()`：对 22 个键各调一次 `consumeClick()`，防止在非 EMI 界面里的点击滞留到下次打开 EMI 时集中触发。
- `resetAll()`：把 22 个 `EdgeDetector` 的 `lastDown` 复位。**这一条是必需的**：手柄键在打开界面前就按着的话（比如用同一个键开背包），第一帧会看到 `isDown == true` 而 `lastDown == false`，产生一次虚假触发。
- `recalculate()` 每帧最多调一次，不放在循环里。

### 8.3 门禁

按顺序短路，任一不满足即丢弃本次输入：

| 门禁 | 判据 | 理由 |
|---|---|---|
| 有界面 | `mc.screen != null` | EMI 只在屏幕内工作 |
| EMI 在场 | `!EmiScreenBase.getCurrent().isEmpty()`，或当前界面是 `BoMScreen`（配方树） | `keyPressed` 前两行即 `base.isEmpty()` 返回 false；配方树不走 `EmiScreenBase.of()`，必须单列，否则从配方树里连「返回」都按不出来 |
| EMI 就绪 | `EmiScreenManager.isDisabled()` 为真时只放行 `toggleVisibility`，其余丢弃 | 复刻分支顺序：关闭 / 重载中只有开关能按，否则玩家没法用手柄把 EMI 重新打开 |
| 无输入框聚焦 | 屏幕元素树内不存在 `isActive() && visible` 的 `TextFieldWidget`，且 `EmiApi.isSearchFocused()` 为假 | EMI 的搜索框是手工渲染的，**不在元素树里**，只走树扫描会漏掉它 |

四个门禁与 EMI 自身键位的行为一致（见 D7）。`EmiApi.isSearchFocused()` 不再单独使用：EMI 搜索框本身就是元素树里的 `TextFieldWidget`，已被第四条覆盖。

不门禁但会自然失败的情况（继承 EMI 语义，不额外处理）：`hideStack` 需要 `EmiConfig.editMode`；`cheat*` 需要创造模式 + `SidebarEmiStackInteraction`；`craft*` 需要悬停在侧栏物品上。这些都返回 false，不抛异常。

### 8.4 分发路由

| kind | 调用 |
|---|---|
| `STACK` | `EmiScreenManager.stackInteraction(EmiApi.getHoveredStack(true), b -> b == EmiConfig.<bindKey>)` |
| `STACK_THEN_RECIPE` | 先 `STACK`；返回 false 且 `resolveRecipeContext(hovered) != null` 时再 `recipeInteraction(ctx, ...)`（`favorite` 用） |
| `RECIPE` | `EmiScreenManager.recipeInteraction(resolveRecipeContext(hovered), b -> b == EmiConfig.<bindKey>)`，其中 `resolveRecipeContext` 按 D9 先取 `hovered.getRecipeContext()`、再退回 `EmiApi.getRecipeContext(hovered.getStack())` |
| `GENERIC` | `EmiScreenManager.genericInteraction(b -> b == EmiConfig.<bindKey>)` |

> **实现修正**：v1 没有 `API` 类。EMI 1.1.24 的发布产物里没有 `displayAllRecipes` 这个 `EmiBind`（源码树里有，发布版没有），所以「显示全部配方」不在动作表内；`EmiApi.displayAllRecipes()` 仍然存在，但不在本桥的 v1 范围。

所有调用都在客户端主线程、渲染/事件线程安全的上下文里执行。返回值忽略（v1 不做「动作失败」的 UI 反馈）。

### 8.5 动作表（22 项）

| # | bindKey | kind | 备注 |
|---|---|---|---|
| 1 | `toggleVisibility` | GENERIC | 开关 EMI |
| 2 | `focusSearch` | GENERIC | |
| 3 | `clearSearch` | GENERIC | |
| 4 | `viewRecipes` | STACK | |
| 5 | `viewUses` | STACK | |
| 6 | `favorite` | STACK_THEN_RECIPE | 物品级失败后退到配方级 |
| 7 | `viewStackTree` | STACK | 需要 `recipeContext` |
| 8 | `viewTree` | GENERIC | |
| 9 | `back` | GENERIC | |
| 10 | `forward` | GENERIC | |
| 11 | `craftOne` | STACK | 侧栏物品才有意义 |
| 12 | `craftAll` | STACK | 同上 |
| 13 | `craftOneToInventory` | STACK | 同上 |
| 14 | `craftAllToInventory` | STACK | 同上 |
| 15 | `craftOneToCursor` | STACK | 同上 |
| 16 | `cheatOneToInventory` | STACK | 创造模式 |
| 17 | `cheatStackToInventory` | STACK | 创造模式 |
| 18 | `cheatOneToCursor` | STACK | 创造模式 |
| 19 | `cheatStackToCursor` | STACK | 创造模式 |
| 20 | `hideStack` | STACK | 需 `EmiConfig.editMode` |
| 21 | `hideStackById` | STACK | 同上 |
| 22 | `copyId` | RECIPE | 仅侧栏收藏项等有 recipe 上下文的场合（D9） |

### 8.6 compat JSON

`/assets/cfx_compat_emi/compat/cfx_compat_emi.json` → 安装到 `config/controlflex/compat/mods/cfx_compat_emi.json`：

```json
{
  "mod_id": "cfx_compat_emi",
  "mod_name": "Control Flex x EMI",
  "home_page": "https://github.com/ControlFlexMC/cfx-compat-emi",
  "config_version": "1.0.0",
  "mod_versions": [
    {
      "version": "",
      "screenKeys": {
        "channels": ["keyMapping"],
        "key.cfx_compat_emi.view_recipes": {},
        "...": {}
      }
    }
  ]
}
```

要点：

- `mod_id` 写 `emi`：这份配置在声明「这些是 EMI 的动作」。CF 的 `deriveGroupKey` 第 ① 条用配置的归属索引（`compatOwnedKeyToModId`）把 `key.cfx_compat_emi.*` 归到 `emi`，于是 22 个动作出现在 **EMI** 分组下（显示名取 mod 列表里的 "EMI"）—— 玩家在 CF 里找手柄键位时的直觉路径。
- 配置是晚到的（桥接在 `discoverPlugins()` 阶段才导出），CF 随后会调 `reattributeModIdLessKeys()` 把已注册的 `cfx_compat_emi:*` 迁移成 `emi:*`，并通过 `BindingRegistry.reattributeActionId` 同步迁移已保存的绑定，玩家不用重绑。
- 文件名保持 `cfx_compat_emi.json`，不叫 `emi.json`：安装器是无条件覆盖写，若将来官方/社区也放一份 `emi.json`，同名会互相清掉；不同名则各自存在，冲突时由加载顺序决出（见 §12 风险）。
- `channels: ["keyMapping"]` 显式声明，避免 CF 走 `screenInput` 通道把 `UNKNOWN_KEY` 送进 `screen.keyPressed`。
- 不使用 `ignoreKeys`：22 个键全部有实现，没有需要隐藏的。
- 不加 `dependencies`：这份配置就是给桥接自己的。

### 8.7 语言资源

`assets/cfx_compat_emi/lang/en_us.json` 与 `zh_cn.json`：22 个动作名 + `key.categories.cfx_compat_emi`。文案对齐 EMI 官方用词（"View Recipes" / "查看配方"），便于玩家在 EMI 自己的 Binds 页面对照。

### 8.8 版本门控

- 加载器元数据硬依赖：`controlflex` ≥ 0.8.9、`emi`（任意 1.20.1 版本）。两者缺失都由加载器拒绝启动桥接，运行期没有 `isModLoaded` 分支（D8）。
- **区间语法按加载器分开**：Forge 的 `mods.toml` 用 Maven 区间 `[0.8.9,)`；Fabric 的 loader **不支持方括号区间** —— `VersionPredicateParser` 只认 `>=` / `<=` / `<` / `>` / `~` / `^` / `=` 以及空格分隔的多条件，`[0.8.9,)` 会被 `VersionParser` 解析成字面版本串，再以 `EQUAL` 去比 `0.8.9`，必然失败。所以 `fabric.mod.json` 用 `>=0.8.9` / `>=1.1.0`，对应 `gradle.properties` 里的 `controlflex_version_range_fabric`。
- 运行时不做额外版本判断（v1 只用 `ICompatAssetInstaller`，是 0.8.5 起的稳定接口；真正的风险在 EMI 侧的实现类，见 §12）。

### 8.9 日志

统一前缀 `[cfx-compat-emi]`。只在四个点打日志：注册了多少键位、CF 是否就绪、compat JSON 是否安装/跳过（SHA-1 一致）、分发异常（`catch Throwable` 记 warn 并丢弃，绝不让 tick 崩）。

## 9. 时序：一次按下

```
手柄 X
  └─ CF BindingMapper（GUI 相位，TAP）
       ├─ GuiActionHandler.handleDynamicCompatAction("cfx_compat_emi:key.cfx_compat_emi.view_recipes")
       └─ controlflex$setPressed(true)  →  clickCount++
桥接 tick
  ├─ isDown()/consumeClick() 采样
  ├─ 门禁：有屏幕 → EMI 在场 → 搜索框未聚焦
  ├─ EdgeDetector → fire
  ├─ EmiScreenManager.recalculate()
  └─ stackInteraction(EmiApi.getHoveredStack(true), b -> b == EmiConfig.viewRecipes)
       └─ EmiApi.displayRecipes(ingredient) + focusRecipe(context)
```

## 10. 工程结构与构建

- `settings.gradle` 照 `cfx-compat-create`：`include 'forge', 'fabric'`，插件 `net.neoforged.moddev.legacyforge` + `fabric-loom`。
- 两个 loader 子工程都把 `common` 收进自己的 source set：`main.java.srcDir(rootProject.file('common/src/main/java'))` + `main.resources.srcDir(rootProject.file('common/src/main/resources'))`。资源只维护一份，避免 JSON 与语言文件漂移。
- Fabric 侧用 `fabric-loom 1.7-SNAPSHOT`（EMI 产物要求 ≥1.7.435），Forge 侧 `net.neoforged.moddev.legacyforge 2.0.141`。
- 依赖：
  - `compileOnly "com.github.ControlFlexMC:control-flex-api:${controlflex_api_version}"`（JitPack）
  - Forge：`compileOnly fg.deobf("dev.emi:emi-forge:${emi_version}")`（`repo.sleeping.town`）
  - Fabric：`modCompileOnly "dev.emi:emi-fabric:${emi_version}"` + `modLocalRuntime`（便于本地跑）
- `gradle.properties` 关键项：`mod_id=cfx_compat_emi`、`mod_name=Control Flex x EMI`、`minecraft_version=1.20.1`、`emi_version=1.1.24+1.20.1`、`controlflex_version_range=[0.8.9,)`。
- 无 mixin 配置，无 refmap，无 `mixins.json`。
- `common` 内的测试：`EdgeDetector` 的单测放在各 loader 子工程的 `src/test`（沿用 create 的做法，`compileClasspath += sourceSets.main.output`）。

## 11. 测试与验收

**单元**：`EdgeDetector` 的四条路径——按住多帧只触发一次、轻点跨帧触发、无输入不触发、同一帧两次点击只触发一次。

**游戏内验收（Forge + Fabric 各跑一遍）**：

1. CF 设置里出现分组 "Control Flex x EMI"，恰好 22 个条目，无重复、无 `key.cfx_compat_emi.*` 原始键名泄漏。
2. 绑定「查看配方」到 X，打开背包指向物品按 X → 打开配方页。
3. 指向 EMI 侧栏物品，绑定「合成一组」按一下 → 合成执行。
4. 绑定「收藏」按一下 → 侧栏收藏出现该物品。
5. 搜索框聚焦时按任意动作 → 不触发（与 EMI 自身键位一致）；让搜索框失焦后恢复。
6. 游戏中（无界面）按动作 → 无反应、无日志刷屏。
7. 移除 CF 后启动 → 加载器报缺依赖并拒绝加载桥接，不写入任何键位。
8. 卸载桥接后启动 → 不崩，`options.txt` 里的残留键名被原版忽略。
9. 在 EMI 配置里关掉 EMI，或触发一次重载 → 除「切换 EMI 显示」外全部不触发，日志无 warn 刷屏。
10. 在铁砧命名框 / 告示牌里打字时按动作 → 不触发。

## 12. 风险登记

| 风险 | 影响 | 处置 |
|---|---|---|
| EMI 改 `EmiScreenManager` / `EmiConfig` 签名 | 编译期报错、运行期 `NoSuchMethodError` | 桥接独立发版，版本号跟 EMI 走；`dispatch` 外层 `catch Throwable` |
| `getHoveredStack` 依赖 `lastMouseX/Y` | 无光标时动作无效 | v1 接受（CF 手柄默认带虚拟光标）；文档写明「先指到物品再按」 |
| 玩家用 HOLD 模式绑定 | 每个 tick 都会 fire（EMI 会连开多层界面） | `EdgeDetector` 已在按住态只触发首帧，天然免疫 |
| CF 未来改 GUI 派发语义（不再 `setPressed`） | 触发链断掉 | 验收用例 2 是回归哨兵；必要时在 CF 侧加一条稳定契约 |
| 22 个键污染 `options.txt` | 卸载后残留 | 可接受；文档说明 |
| `EmiConfig.editMode` / 创造模式门禁 | 动作静默失败，玩家困惑 | v1 接受；v2 做「动作不可用」的提示 |
| Forge 侧 EMI 编译坐标不确定 | 构建卡住 | `NeoGradle legacyforge` 未必支持 `fg.deobf`；退路是 `dev.emi:emi-xplat-mojmap`（EMI README 给 MultiLoader / VanillaGradle 的方案）。实现第一步就验证 |
| 同一按钮绑两个 EMI 动作 | 两个都执行 | 与 CF 对所有动作的语义一致，不做去重；文档说明 |
| 将来出现官方/社区的 `emi.json` | 两份配置争 `compatConfigs["emi"]`，输了的那份失效；我们输了则 22 个动作回落 MAIN、静默失灵 | 文件名用 `cfx_compat_emi.json` 降低覆盖概率；`[ModCompat] Re-attributed key` 日志与分组显示可定位；必要时退回自有 groupKey |

## 14. 评审记录

### 第一轮（API / 事实核对，2026-10-31）

| 编号 | 发现 | 处置 |
|---|---|---|
| R1.1 | `ModCompatConfig.DEFAULT_SCREEN` = `{keyMapping, screenInput}`，`screenKeys` 不写 `channels` 必然走 `screenInput` | D4 从「可能」改成确定表述，并引用常量 |
| R1.2 | `controlflex$setPressed(true)` = `setDown(true)` + **无条件** `clickCount++`，HOLD 模式下逐 tick 增长 | 修正 D2 措辞（原先写「只在状态翻转时递增」是错的）；检测器顺序固定为「先上升沿、`clicked` 兜底」 |
| R1.3 | `processPendingGuiReleases` 注释明确释放发生在后续 tick | D2 补一句：上升沿是主路径，`consumeClick()` 是保险 |
| R1.4 | Forge 侧 EMI 的编译接入方式不确定（`legacyforge` vs `fg.deobf`） | 加入风险表；计划的第一项任务就是先跑通依赖解析 |
| R1.5 | CF 的 `KeyMappingAccessorMixin` 重写了 `compareTo`，为缺失 `CATEGORY_SORT_ORDER` 的自定义分类兜底 | 记入 §8.1 备注：自定义分类在 Forge 直接 `new KeyMapping` 也不会 NPE，前提是 CF 在位（硬依赖已保证） |

### 第二轮（设计 / 边界，2026-10-31）

| 编号 | 发现 | 处置 |
|---|---|---|
| R2.1 | 打开 EMI 界面前手柄键已按住（同一键开背包）→ 首帧虚假触发 | §8.2 增加「进入 EMI 界面时 `drainAll()` + `resetAll()`」 |
| R2.2 | 多个动作同帧触发时 `recalculate()` 被调多次 | 改为每帧最多一次 |
| R2.3 | 关闭态（`isDisabled()`）下若仍检查「EMI 在场」与「输入框聚焦」，玩家无法用开关重新打开 EMI | 重排门禁：`base.isEmpty()` 在最前，`isDisabled()` 只放行开关且跳过聚焦检查，与 EMI 源码分支顺序一致 |
| R2.4 | 同一按钮绑两个 EMI 动作会双触发 | 视为 CF 统一语义，不去重，写入风险表 |
| R2.5 | `toggleVisibility` 在 EMI 开启时走 `genericInteraction`，因此同样受聚焦门禁约束 | 在 §8.3 注明：这是 EMI 自身行为，不是我们的偏差 |

### 第三轮（实现期核对，2026-10-31）

| 编号 | 发现 | 处置 |
|---|---|---|
| R3.1 | EMI **1.1.24 发布产物**里只有 25 个 `EmiBind`，没有 `displayAllRecipes`（`research/emi` 源码树比发布版新） | 动作表 26 → 25 → 22；删除 `DISPLAY_ALL_RECIPES` 与 `ActionKind.API`，JSON / lang / 测试同步 |
| R3.2 | `EmiBind` 在 `dev.emi.emi.input`，不在 `.config` | 修正 import |
| R3.3 | Mojmap 1.20.1 没有 `ParentElement`，元素树接口是 `ContainerEventHandler` | `hasFocusedTextField` 改为 `Screen.children()` + `ContainerEventHandler` 递归 |
| R3.4 | 1.20.1 的 `KeyMapping` 构造器只接受 `int` 键码 | 用 `InputConstants.UNKNOWN.getValue()` |
| R3.5 | `compileOnly` 不会进入 `testCompileClasspath`，插件测试编译失败 | 两个 loader 都加 `testImplementation control-flex-api` |
| R3.6 | EMI 的 Fabric 产物用 Loom 1.7.435 构建，Loom 1.6 拒绝配置 | `settings.gradle` 升到 `fabric-loom 1.7-SNAPSHOT` |
| R3.7 | 两个 loader 都是 Mojmap，per-loader 复制分发逻辑已无必要 | D6 改为 common 持有 `EmiKeys` / `EmiDispatch` / `EmiTicker`，per-loader 只剩接线 |

### 第四轮（整分支评审，2026-10-31）

| 编号 | 发现 | 处置 |
|---|---|---|
| R4.1 | **EMI 搜索框不在屏幕元素树里**（`addWidgets()` 只定位不挂 child），元素树扫描永远看不到它 → 聚焦后 22 个动作照发 | 门禁补 `EmiApi.isSearchFocused()`，抽成 `EmiGate.focusBlocks(...)`，7 条单测 |
| R4.2 | 配方树 `BoMScreen` 不进 `EmiScreenBase.of()`，进去之后连「返回」都失效 | 门禁加 `BoMScreen` 例外，抽成 `EmiGate.emiScreenActive(...)` |
| R4.3 | 进屏首帧只跳过一帧；HOLD 绑定在第二帧仍会误触（当时靠 CF 的抑制兜底） | `EdgeDetector.seed()` 记录真实按下状态，进屏帧只 seed 不触发 |
| R4.4 | `recalculate()` 在 try 之外，且 EMI 重载时也会调用 | 移入 try，且只在 `!disabled` 时调用 |
| R4.5 | 干净克隆无法构建：wrapper jar 被 `*.jar` 忽略；`org.gradle.java.home` 写死本机路径 | `.gitignore` 放行 `gradle/wrapper/gradle-wrapper.jar`；属性改成注释，README 说明 JAVA_HOME |
| R4.6 | `copyId` 在配方页拿不到 recipe 上下文（`RecipeScreen` 不是 `HandledScreen`） | 不改代码，D9 / 动作表 / README 改为「仅侧栏收藏项等」 |
| R4.7 | §8.9 的日志点只实现 1/4（安装结果被丢弃、无 ready 行） | 插件记录安装失败与 ready 行 |

### 第五轮（真机启动，2026-11-01）

| 编号 | 发现 | 处置 |
|---|---|---|
| R5.1 | Fabric 实例启动直接失败：`requires version [0.8.9,) of mod 'ControlFlex', but only the wrong version is present: 0.8.9` | Fabric loader 不支持方括号区间（源码 `VersionPredicateParser` 只认运算符前缀），方括号串被当字面版本做等值比较 → 改用 `>=0.8.9` / `>=1.1.0`；Forge 的 `mods.toml` 维持 Maven 区间。已重打包并部署到 1.20.1-fabric 实例 |

### 第六轮（分组归属，2026-11-01）

| 编号 | 发现 | 处置 |
|---|---|---|
| R6.1 | 动作显示在 "Control Flex x EMI" 分组下，而玩家的直觉是去 EMI 里找手柄键位 | compat JSON 的 `mod_id` / `mod_name` / `home_page` 改成 EMI；分组名来自 `mod_id` 的模组显示名，`mod_name` 本身不参与分组。CF 的归属索引（`deriveGroupKey` 规则 ①）与 `reattributeModIdLessKeys()` 会把已注册的 `cfx_compat_emi:*` 连同已保存绑定一起迁到 `emi:*` |

## 13. 留给实现者的决定

- 类名与方法名可调整，`EmiAction` 的字段划分可改（只要 common 不出现 MC/EMI 类型）。
- `dispatch` 的异常策略可细化（v1 只要求「不崩、不刷屏」）。
- `EdgeDetector` 是否合并进 per-loader（若发现两个 loader 行为需要不同采样点）。
- 是否给 `forward` / `back` 之外的动作也加「不可用」日志（v1 不加）。
