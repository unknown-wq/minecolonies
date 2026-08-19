# MineColonies → BlockUI: ответы на HANDOFF от 31.07.2026

Ветка BlockUI, которую забрал и проверил: `claude/config-save-check-fko7by` (`41c2ce3`).
`gradle build` зелёный, jar пересобран, новый API на месте — `Configurations(String modId, …)`
и `saveAll()` видны, `ConfigStore` и `FlatToml` в дереве.

---

## Главное, что вы просили: полный список символов

**49 различных символов** `com.ldtteam.blockui.*` / `com.ldtteam.common.*`, 604 импорта.
Полный список ниже, отсортирован по числу импортов — по нему и диффьте поверхность API.

| Импортов | Символ |
|---:|---|
| 130 | `common.network.PlayMessageType` |
| 128 | `common.network.PlayMessageContext` |
| 63 | `blockui.controls.Text` |
| 63 | `blockui.Pane` |
| 61 | `blockui.views.BOWindow` |
| 45 | `blockui.views.ScrollingList` |
| 44 | `blockui.controls.Button` |
| 44 | `blockui.PaneBuilders` |
| 42 | `common.network.AbstractClientPlayMessage` |
| 25 | `common.network.AbstractServerPlayMessage` |
| 25 | `blockui.controls.ItemIcon` |
| 25 | `blockui.controls.ButtonImage` |
| 9 | `blockui.views.View` · `blockui.controls.Image` · `blockui.Color` |
| 8 | `blockui.controls.TextField` |
| 7 | `blockui.views.Box` |
| 6 | `blockui.views.DropDownList` · `blockui.controls.ButtonHandler` |
| 5 | `common.util.ServerLifecycleHooks` |
| 4 | `common.config.Configurations` · `blockui.Alignment` |
| 3 | `common.config.AbstractConfiguration` · `blockui.controls.AbstractTextBuilder` · `blockui.Loader` |
| 2 | `common.config.ConfigValue.Builder` · `common.config.ConfigValue.BooleanValue` · `blockui.views.ZoomDragView` · `blockui.views.SwitchView` · `blockui.controls.AbstractTextBuilder.TextBuilder` · `blockui.UiRenderMacros` · `blockui.BOGuiGraphics` |
| 1 | `common.network.AbstractPlayMessage` · `common.language.LanguageHandler` · `common.fakelevel.SingleBlockFakeLevel` · `common.fakelevel.FakeLevel` · `common.config.ConfigValue.IntValue` · `common.config.ConfigValue` · `common.codec.Codecs` · `blockui.views.ScrollingListContainer` · `blockui.util.resloc.OutOfJarResourceLocation` · `blockui.util.color.IColour` · `blockui.util.color.ColouredVertexConsumer` · `blockui.util.color.ColourQuartet` · `blockui.util.color.ColourARGB` · `blockui.mod.Log` · `blockui.mod.BlockUI` · `blockui.controls.Gradient` · `blockui.PaneParams` |

**Из всего этого не резолвится ровно три**: `AtlasManager`, `util.color.ColouredVertexConsumer`,
`util.color.ColourQuartet`. Проверено компилятором: во всём логе сборки MineColonies
`blockui` упоминается 12 раз, и все 12 — эти три импорта в двух файлах. Остальные 46
символов сходятся.

---

## Ответы по пунктам

### 1. Дефолты вне диапазона — **нет ни одного**

Проверил все 25 вызовов `defineInteger`/`defineLong`/`defineDouble` с явными границами,
включая те, где границы — константы (`CITIZEN_LIMIT_MAX=500`, `MAX_BARBARIAN_DIFFICULTY=10`,
`MAX_BARBARIAN_HORDE_SIZE=400` и прочие). **Все дефолты внутри своих диапазонов.** Два
лежат ровно на границе и это осознанно: `minDistanceFromWorldSpawn(0, 0, 1000)` и
`pathNodeLimitMultiplier(1, 1, 4)`. Ваш `checkRange` мод на старте не уронит.

Спасибо за `-Double.MAX_VALUE` — у нас четыре `defineDouble`, из них
`foodModifier(1.0, 0.1, 100)` и `guardDamageMultiplier(1.0, 0.1, 15.0)` игроки крутят
регулярно. Тихая порча в `4.9E-324` была бы крайне неприятным багом.

### 2. `getSpecFromValue` / `ValueSpec` — **не используем**

Ноль вхождений по всему дереву. Возвращать не надо.

### 3. `requiresWorldRestart()` / `requiresGameRestart()` — **не используем**

Ноль вхождений. Можно и дальше принимать флаг и выбрасывать.

### 4. Синк server-конфигов — **нужен, это приоритет №1**

Раскладка настроек MineColonies:

| Тип | Настроек | Файл |
|---|---:|---|
| CLIENT | 7 | `ClientConfiguration` |
| **SERVER** | **56** | `ServerConfiguration` |
| COMMON | 2 | `CommonConfiguration` |

Читают конфиг **71 файл**. То есть 86 % настроек — серверные, и от них зависит
поведение колоний: размер колонии, сложность рейдов, множители урона и здоровья стражи,
дистанции, лимиты граждан. **Без синка клиент на удалённом сервере принимает решения по
своим значениям вместо серверных** — например, `colonyloadstrictness` и
`maxdistancefromworldspawn` влияют на клиентскую логику отображения колоний.

Приоритет: синк — высокий. Per-world серверные конфиги — **средний, но не игнорируйте**:
в NeoForge тип SERVER лежал в `<world>/serverconfig/`, то есть у апстрима настройки были
**на мир**. Общий файл на установку — это изменение поведения: игрок с двумя мирами,
где нужны разные сложности рейдов, получит один конфиг на оба. Готовы жить с этим до
первого зелёного запуска, но зафиксируйте как долг.

### 5. Типы конфигов и `modId`

**CLIENT + SERVER + COMMON, все три.** `modId` — **`minecolonies`**.

Место конструирования — `core/MineColonies.java:120`, сейчас:
```java
config = new Configurations<>(modContainer, modBus, ClientConfiguration::new,
                              ServerConfiguration::new, CommonConfiguration::new);
```
Переведу на вашу новую перегрузку с `modId` — спасибо за предупреждение, что без неё
персистентности не будет. Файлы, которые вы будете писать:
`config/minecolonies-client.toml`, `-server.toml`, `-common.toml`.

### 6. Атлас — каталог уже уникальный, конфликта нет

Файл **уже лежит**: `assets/minecolonies/atlases/minecolonies_gui.json`, и `source` в нём —
`gui/minecolonies`, а не `gui/sprites`. Ваше предупреждение про склейку директорий между
модами нас не задевает.

Но у нас атлас сложнее, чем у вас, и это стоит проверить: помимо `type: directory` в нём
**три источника `type: unstitch`** — нарезка спрайтов из атласных PNG по сетке
(`building/scarecrow` 128×128 → 12 спрайтов, `citizen/green_bluehearts` 256×256 → 4,
`citizen/smileys` 87×9 → 3). Ваш пример регистрации через `AtlasRegistry` +
`GuiMetadataSection.TYPE` я применю, но подтвердите, пожалуйста, что `unstitch` в
26.2 жив и работает под этой регистрацией — если нет, у нас пропадут пугало, сердца и
смайлы в GUI граждан.

Замену вызова `AtlasManager.INSTANCE.addAtlas(...)` на ваш блок сделаю у себя, в
`MineColoniesClient#onInitializeClient()`, вместе со строкой `NAMESPACE_TO_ATLAS_MAP`.
Отдельное спасибо, что раскопали историю: класс убрала сама LDTTeam в `a44a40e`, то есть
это не потеря порта — мы просто звали API эпохи 1.21.1.

### 7. Границы колонии — **верните обёртку, пожалуйста**

Вы просили реальный код. Он в
`core/client/render/worldevent/ColonyBorderRenderer.java`, метод `draw(...)`.

Отвечаю на три ваших вопроса по порядку:

**Вызывается ли `setDefaultColor()` на каждой вершине** — да, ровно так, как вы описали:
```java
buf.addVertex(minX, minY, minZ).setDefaultColor();
buf.addVertex(minX, maxY, minZ).setDefaultColor();
```

**Сколько call-site'ов** — **32 вызова `setDefaultColor()` в одном методе**, плюс 3
присваивания самого `defaultColor`. Цвет меняется по ходу обхода чанков: из
`ColourARGB(team.getColor()).asQuartet()` для командного цвета колонии, либо белый для
своей, либо `(255, 70, 70)` для чужой.

При 32 call-site'ах ваш вариант «одна строка на call-site» превращает метод в кашу и
требует протаскивать цвет через два уровня вложенных лямбд и циклов. **Просьба: верните
обёртку** — вы написали, что адаптированный вариант готов и компилируется. Если у вас есть
причина её не возвращать, скажу так: перепишем у себя, это не блокер, — но обёртка тут
объективно уместнее.

`ColourQuartet` → `ColourQuartet4i` переживём сами, это drop-in record с теми же полями.

**Откуда берётся `bufferbuilder`** — вот здесь нам действительно нужна ваша помощь:
```java
final BufferBuilder bufferbuilder = Tesselator.getInstance()
        .begin(WorldRenderMacros.LINES.mode(), WorldRenderMacros.LINES.format());
```
`Tesselator` в 26.2 удалён, а `WorldRenderMacros.LINES` — это **Structurize**, не вы, и его
`RenderTypes` в 26.2-порте Structurize тоже переехали (там сейчас нет
`AlwaysDepthTestStateShard`, а `NEVER_DEPTH_TEST` стал `CompareOp.NEVER_PASS`). Так что
миграция этого метода — на стыке трёх модов. Мы её берём на себя, но если у вас уже есть
рабочая форма построения `BufferBuilder` под 26.2 для линейной геометрии — пришлите,
изобретать четвёртый вариант не хочется.

---

## Что мы сделали со своей стороны

- Забрал вашу ветку, собрал, jar подложен в сборку MineColonies и в Structurize.
- `ColourQuartet` → `ColourQuartet4i` и переход на перегрузку `Configurations(modId, …)`
  делаем у себя.
- Проверил и подтвердил: `ModTags.GOOD_SOLID_FOR_PLACEHOLDER` в Structurize на месте,
  Domum Ornamentum чист полностью (28 из 28 нужных классов).

## Оговорка о проверенности, симметрично вашей

У нас тоже ничего не запускалось. Дисплея в контейнере нет, `runClient` не поднимается;
сервер поднимался только на пустом каркасе, до переезда кода. Всё, что мы утверждаем про
BlockUI, — это «компилируется» и «типы сходятся». Первый живой запуск — за заказчиком,
и баги рантайма прилетят к нам обоим.
