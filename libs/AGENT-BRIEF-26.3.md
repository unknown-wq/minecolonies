# Брифинг агента: порт трёх библиотек ldtteam на 26.3-snapshot-9

Читаешь целиком, потом — свою зону. Три общих решения (§4) уже приняты и проверены
компилятором: их надо **применить**, а не исследовать заново.

Что именно сломалось в моде — `API-CHECKLIST-26.3.md` (там же ось B: переименования,
которые повторяются в библиотеках). Порядок работ и объёмы — `PORT-PLAN-26.3.md`.
Пересказывать их здесь не буду, ссылки по месту.

---

## 1. Что уже сделано, переделывать не надо

- Исходники всех трёх библиотек скопированы в `libs/*/26.3/` — **правишь здесь**.
  `libs/*/26.2/` и `26.2/` (мод) — **только чтение**, это база для диффа.
- Пины переставлены: `minecraft_version=26.3-snapshot-9`, `fabric_version=0.158.0+26.3`,
  `loom_version=1.17.19`. `loader_version=0.19.3` не менялся.
- В `fabric.mod.json` предикат `minecraft` уже `"~26.3-"`.
- `blockui.accesswidener` в части `CursorType` (`extendable class`, `<init>`, поле `name`)
  в 26.3 всё ещё валиден — сигнатуры не менялись. Не трогай.
- Каждая библиотека уже собрана против снапшота, логи компиляции лежат в
  `/tmp/claude-0/-home-user-minecolonies-fabric/df94c8a8-3adc-5824-a338-1350efff330e/scratchpad/compile-logs/`.
  Читай их, а не гадай.

## 2. Правила, нарушение которых ломает работу другим

1. **Gradle только через `tools/mc-build.sh <project-dir> <task>`.** Loom нельзя запускать
   дважды одновременно — два прогона делят `~/.gradle/caches/fabric-loom` и портят кэш,
   а не ждут друг друга. Скрипт даёт глобальный `flock` и очередь, ждать не страшно.
   Он же чинит ловушку контейнера: тот экспортирует `JAVA_HOME=java-21`, и Gradle отвечает
   `error: release version 25 not supported`. Скрипт подменяет `JAVA_HOME` на Java 25.
   ```sh
   tools/mc-build.sh libs/blockui/26.3 compileJava
   tools/mc-build.sh libs/structurize/26.3 build
   ```
2. **Правь только свою зону.** Нашёл проблему в чужой библиотеке — строкой в отчёт,
   руками не трогай. Один владелец на файл.
3. **`26.2/` и `libs/*/26.2/` — только чтение.**
4. **Git не трогаешь вообще.** Ни `add`, ни `commit`, ни `branch`, ни `stash`.
   Правишь файлы; историю пишет оркестратор.
5. Документы верхнего уровня (`PORT-PLAN-26.3.md`, `API-CHECKLIST-26.3.md`, этот файл)
   не редактируешь.

## 3. Источники истины, по убыванию

1. `/opt/mc-src-26.3` — декомпилированная ваниль 26.3-snapshot-9, 7201 файл.
   `grep -rn` отвечает на любое «а как это теперь называется».
   **Не угадывай имена — проверяй.**
2. `javap -cp /opt/mc-26.3-snapshot-9/client.jar <класс>` — точные сигнатуры.
   Для «а как было» — `/opt/mc-26.2/client.jar`. Java:
   `/usr/lib/jvm/java-25-openjdk-amd64/bin/javap`. Флаг `-constants` печатает значения
   `static final` полей — им пользоваться, а не памятью.
3. `libs/*/26.2/` — как было написано до порта, вместе с комментариями прошлого порта.

Ванильные jar-ы уже деобфусцированы и ремапнуты (в них `net.minecraft.resources.Identifier`),
так что быстрая самопроверка отдельным `javac` возможна; classpath — merged-jar из
`libs/<своя>/26.3/.gradle/loom-cache/minecraftMaven/net/minecraft/*/26.3-snapshot-9/*.jar`
плюс jar-ы из `/root/.gradle/caches/modules-2`.

---

## 4. Три общих решения

Три вещи ломают BlockUI и Structurize одновременно. Решения ниже **проверены javac-ом**
против настоящего merged-jar 26.3-snapshot-9 (пробник в
`.../scratchpad/probe/src/probe/Probe.java` и `Probe2.java`, компилируется без ошибок).
Полной сборкой библиотек они не проверялись, и клиент никто не запускал.

### 4.1. `com.mojang.blaze3d.pipeline` → `com.mojang.renderpearl.api.pipeline`

**Это чистый переезд пакета, а не новое API.** Проверено `javap` по обеим версиям:
`RenderPipeline.Builder` — тот же набор методов (`withLocation` / `withVertexShader` /
`withFragmentShader` / `withVertexBinding(int, VertexFormat)` / `withPrimitiveTopology` /
`withColorTargetState` / `withDepthStencilState` / `withCull` / `buildSnippet` / `build`,
плюс новые `withColorTargetStates`, `withPushConstantSize`). Конструкторы
`ColorTargetState(BlendFunction)` и `DepthStencilState(CompareOp, boolean[, float, float])`
не менялись. Константы `PrimitiveTopology` и `CompareOp` совпадают один в один;
в `BlendFunction` только добавился `MAX`.

Таблица импортов — вся правка:

| 26.2 | 26.3 |
|---|---|
| `com.mojang.blaze3d.PrimitiveTopology` | `com.mojang.renderpearl.api.pipeline.PrimitiveTopology` |
| `com.mojang.blaze3d.pipeline.RenderPipeline` | `com.mojang.renderpearl.api.pipeline.RenderPipeline` |
| `com.mojang.blaze3d.pipeline.BlendFunction` | `com.mojang.renderpearl.api.pipeline.BlendFunction` |
| `com.mojang.blaze3d.pipeline.ColorTargetState` | `com.mojang.renderpearl.api.pipeline.ColorTargetState` |
| `com.mojang.blaze3d.pipeline.DepthStencilState` | `com.mojang.renderpearl.api.pipeline.DepthStencilState` |
| `com.mojang.blaze3d.pipeline.BindGroupLayout` | `com.mojang.renderpearl.api.pipeline.BindGroupLayout` |
| `com.mojang.blaze3d.platform.CompareOp` | `com.mojang.renderpearl.api.pipeline.CompareOp` |
| `com.mojang.blaze3d.platform.PolygonMode` | `com.mojang.renderpearl.api.pipeline.PolygonMode` |
| `com.mojang.blaze3d.GpuFormat` | `com.mojang.renderpearl.api.GpuFormat` |

Не переехали и остаются на месте: `com.mojang.blaze3d.vertex.DefaultVertexFormat`,
`VertexConsumer`, `PoseStack`, `net.minecraft.client.renderer.RenderPipelines`,
`RenderType`, а в `com.mojang.blaze3d.pipeline` остались только `RenderTarget`,
`TextureTarget`, `MainTarget`, `PipelineCache`. Тип `VertexFormat` теперь
`com.mojang.renderpearl.api.vertex.VertexFormat` — но `DefaultVertexFormat.POSITION_COLOR`
и соседи уже отдают именно его, так что явный импорт нужен только там, где `VertexFormat`
назван по имени.

**Было (BlockUI `UiRenderMacros`, 26.2):**
```java
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;

public static final RenderPipeline GUI_POS_COLOR_TRIANGLES = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
    .withLocation(BlockUI.resLoc("gui_pos_color_triangles"))
    .withVertexShader("core/position_color")
    .withFragmentShader("core/position_color")
    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
    .build();
```

**Стало (26.3) — меняются только две строки импортов:**
```java
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

public static final RenderPipeline GUI_POS_COLOR_TRIANGLES = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
    .withLocation(BlockUI.resLoc("gui_pos_color_triangles"))
    .withVertexShader("core/position_color")
    .withFragmentShader("core/position_color")
    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
    .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
    .build();
```

**Регистрация — тоже без изменений** (`RenderPipelines.register(RenderPipeline)` на месте,
`/opt/mc-src-26.3/net/minecraft/client/renderer/RenderPipelines.java:1355`). Целиком
`createPipeline` из Structurize `WorldRenderMacros` после правки импортов:
```java
public static RenderPipeline createPipeline(final Identifier location,
    final PrimitiveTopology topology,
    final BlendFunction blend,
    final CompareOp depthTest,
    final boolean writeDepth,
    final boolean cull)
{
    return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
        .withLocation(location)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
        .withPrimitiveTopology(topology)
        .withColorTargetState(new ColorTargetState(blend))
        .withDepthStencilState(new DepthStencilState(depthTest, writeDepth))
        .withCull(cull)
        .build());
}
```
Ванильный образец той же формы, на который можно сверяться, —
`RenderPipelines.DEBUG_FILLED_SNIPPET` (`RenderPipelines.java:337-347`) и
`GUI_SNIPPET` (`:417-426`).

**Никакого общего слоя-обёртки над новым API не заводим.** Раз это переименование,
обёртка была бы чистым долгом. Каждая библиотека правит свои импорты у себя.

### 4.2. GLFW → SDL

26.3 выкинул `lwjgl-glfw` и `lwjgl-tinyfd`, добавил `lwjgl-sdl` 3.4.2. Пакета
`org.lwjgl.glfw` нет. Есть `org.lwjgl.sdl.*` — он на compile-classpath, потому что ваниль
сама им пользуется (`InputConstants` импортирует `SDLKeyboard`, `SDLMouse`, `SDLError`).

**Главное, и это молчаливая ловушка.** В 26.3 у `KeyEvent` поменялся смысл поля, а не тип:

```java
// 26.2:  record KeyEvent(int key, int scancode, int modifiers)   -- key это GLFW keysym
// 26.3:  record KeyEvent(int key, int keycode,  int modifiers)   -- key это SDL scancode,
//                                                                   keycode это SDL keycode
//        плюс default int shortcutKey() -> keycode
```
Код вида `event.key() == 263` продолжит компилироваться и будет реагировать не на ту
клавишу. Ищи все сравнения `event.key()` глазами, компилятор их не поймает.

Две параллельные семьи констант в `InputConstants`, путать нельзя:

* **`KEY_*` — SDL scancode** (физическая позиция клавиши). `KEY_A = 4`, `KEY_UP = 82`,
  `KEY_LCONTROL = 224`. Это то, что лежит в `InputConstants.Key`, в `KeyMapping`,
  в `event.key()` и что принимает `isKeyDown(int)`.
* **`KEYCODE_* ` — SDL keycode** (символ с учётом раскладки). `KEYCODE_BACKSPACE = 8`,
  `KEYCODE_LEFT = 1073741904`. Это `event.keycode()` / `event.shortcutKey()`, и именно
  на них ваниль строит текстовое редактирование.

Что ещё изменилось:

| 26.2 | 26.3 |
|---|---|
| `InputConstants.Type.KEYSYM` | `InputConstants.Type.KEYBOARD` |
| `InputConstants.Type.SCANCODE` | нет (в enum остались `KEYBOARD` и `MOUSE`) |
| `InputConstants.isKeyDown(Window, int)` | `InputConstants.isKeyDown(int)` |
| `InputConstants.KEY_LSUPER` / `KEY_RSUPER` | `KEY_LGUI` / `KEY_RGUI` |
| `GLFW.GLFW_MOUSE_BUTTON_LEFT` = 0 | `InputConstants.MOUSE_BUTTON_LEFT` = **1** |
| `GLFW.GLFW_MOUSE_BUTTON_RIGHT` = 1 | `InputConstants.MOUSE_BUTTON_RIGHT` = **3** |
| `GLFW.GLFW_MOUSE_BUTTON_MIDDLE` = 2 | `InputConstants.MOUSE_BUTTON_MIDDLE` = 2 |
| `GLFW.GLFW_KEY_LAST` = 348 | нет; граница — `SDLScancode.SDL_SCANCODE_COUNT` = 512 |
| `CursorType.select(Window)` | `CursorType.select()` |

Нумерация кнопок мыши поменялась — просто оставить старые литералы нельзя.
`InputConstants.UNKNOWN`, `InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY`,
`InputWithModifiers.hasControlDownWithQuirk()` / `isCopy()` / `isPaste()` — на месте.

**Кейбинд. Было (Structurize `ModKeyMappings`, 26.2):**
```java
import org.lwjgl.glfw.GLFW;

public static final Supplier<KeyMapping> TELEPORT = lazy(() -> new KeyMapping("key.structurize.teleport",
        InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
public static final Supplier<KeyMapping> MOVE_FORWARD = lazy(() -> new KeyMapping("key.structurize.move_forward",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UP, CATEGORY));
public static final Supplier<KeyMapping> MOVE_DOWN = lazy(() -> new KeyMapping("key.structurize.move_down",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_SUBTRACT, CATEGORY));
```
**Стало:**
```java
import org.lwjgl.sdl.SDLScancode;   // только для клавиш, которым нет имени в InputConstants

public static final Supplier<KeyMapping> TELEPORT = lazy(() -> new KeyMapping("key.structurize.teleport",
        InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
public static final Supplier<KeyMapping> MOVE_FORWARD = lazy(() -> new KeyMapping("key.structurize.move_forward",
        InputConstants.Type.KEYBOARD, InputConstants.KEY_UP, CATEGORY));
public static final Supplier<KeyMapping> MOVE_DOWN = lazy(() -> new KeyMapping("key.structurize.move_down",
        InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_KP_MINUS, CATEGORY));
```
Порядок предпочтения при выборе константы: сначала `InputConstants.KEY_*`, и только если
имени там нет (numpad `-`, например) — `org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_*`.
Голых чисел не писать. `KeyMapping.Category.register(Identifier)` и
`KeyMappingHelper.registerKeyMapping(...)` не менялись.

Ванильный образец объявления — `Options.java:639-659`:
`new KeyMapping("key.forward", 26, KeyMapping.Category.MOVEMENT)`, где 26 = `KEY_W`.

**Проверка нажатия. Было (BlockUI `BlockUIClient`, 26.2):**
```java
final InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(OPEN_TEST_GUI);
return key.getType() == InputConstants.Type.KEYSYM
    && key.getValue() != InputConstants.UNKNOWN.getValue()
    && InputConstants.isKeyDown(mc.getWindow(), key.getValue());
```
**Стало:**
```java
final InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(OPEN_TEST_GUI);
return key.getType() == InputConstants.Type.KEYBOARD
    && key.getValue() != InputConstants.UNKNOWN.getValue()
    && InputConstants.isKeyDown(key.getValue());
```
Тот же приём в Structurize `ClientEventSubscriber.hasControlDown()`:
```java
if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY)
{
    return InputConstants.isKeyDown(InputConstants.KEY_LGUI) || InputConstants.isKeyDown(InputConstants.KEY_RGUI);
}
return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
```
`Window` больше не нужен — если он в методе больше ни для чего, убрать и его.

**Текстовое поле — отдельный случай, `KEY_*` тут неверны.** BlockUI `TextField.handleKey`
в 26.2 разбирал `event.key()` по `GLFW_KEY_*`. Ваниль 26.3 в `EditBox.keyPressed`
(`/opt/mc-src-26.3/net/minecraft/client/gui/components/EditBox.java:273-336`) разбирает
`event.shortcutKey()` по значениям семьи `KEYCODE_*`. Повторяем ваниль:
```java
switch (event.shortcutKey())
{
    case InputConstants.KEYCODE_BACKSPACE:  // 8
    case InputConstants.KEYCODE_DELETE:     // 127
        return handleDelete(event);
    case InputConstants.KEYCODE_HOME:       // 1073741898
    case InputConstants.KEYCODE_END:        // 1073741901
        return handleHomeEnd(event);
    case InputConstants.KEYCODE_RIGHT:      // 1073741903
    case InputConstants.KEYCODE_LEFT:       // 1073741904
        return handleArrowKeys(event);
    case InputConstants.KEYCODE_TAB:        // 9
        return handleTab();
    ...
}
```
`KEYCODE_ESCAPE` в `InputConstants` не объявлен — брать
`org.lwjgl.sdl.SDLKeycode.SDLK_ESCAPE` (=27). Внутренние `handleArrowKeys` / `handleHomeEnd` /
`handleDelete` тоже сравнивают `event.key()` — их надо перевести на `event.shortcutKey()`
и те же `KEYCODE_*`, иначе направление курсора окажется привязано к позиции клавиши,
а не к её смыслу.

**Курсоры (только BlockUI).** `CursorType.createStandardCursor(int shape, ...)` жив, но
`shape` теперь SDL system cursor, а не `GLFW_*_CURSOR`. Значения сверены с
`CursorTypes.java` и `org.lwjgl.sdl.SDLMouse -constants`:
```java
// было
public static final CursorType RESIZE_NWSE = CursorType.createStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR, "resize_nwse", Cursor.DEFAULT);
public static final CursorType RESIZE_NESW = CursorType.createStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR, "resize_nesw", Cursor.DEFAULT);
// стало
public static final CursorType RESIZE_NWSE = CursorType.createStandardCursor(SDLMouse.SDL_SYSTEM_CURSOR_NWSE_RESIZE, "resize_nwse", Cursor.DEFAULT);
public static final CursorType RESIZE_NESW = CursorType.createStandardCursor(SDLMouse.SDL_SYSTEM_CURSOR_NESW_RESIZE, "resize_nesw", Cursor.DEFAULT);
```
Текстурный курсор (`CursorTexture` + `Cursor.TexturedCursorType`) — `GLFWImage` больше нет,
её место занимает `SDL_Surface`. Форму брал у ванили: `Window.createIconSurface`
(`/opt/mc-src-26.3/com/mojang/blaze3d/platform/Window.java:129-132`), она делает ровно то же
из `NativeImage`. Скомпилировано:
```java
final SDL_Surface surface = SDLSurface.SDL_CreateSurfaceFrom(nativeImage.getWidth(),
    nativeImage.getHeight(),
    SDLPixels.SDL_PIXELFORMAT_ABGR8888,     // 376840196, та же константа, что у ванили
    nativeImage.getPixelBytes(),
    nativeImage.getWidth() * 4);            // pitch
if (surface != null)
{
    sdlCursorAddress = SDLMouse.SDL_CreateColorCursor(surface, meta.hotspotX, meta.hotspotY);
    SDLSurface.SDL_DestroySurface(surface);
}
// выбор курсора:  GLFW.glfwSetCursor(window.handle(), addr)  ->  SDLMouse.SDL_SetCursor(addr)
// освобождение:   GLFW.glfwDestroyCursor(addr)               ->  SDLMouse.SDL_DestroyCursor(addr)
// и select(Window window) -> select(), окно больше не параметр
```
**Не проверено:** что `SDL_CreateColorCursor` копирует пиксели, а не держит ссылку на
буфер `NativeImage`. Ваниль уничтожает свою поверхность сразу после
`SDL_SetWindowIcon`, поэтому форма выше ей симметрична, но подтвердить это можно только
запуском клиента. Если решишь перестраховаться — не закрывай `NativeImage` до
`SDL_DestroyCursor` и напиши об этом в отчёт.

`MemoryStack`/`MemoryUtil` для этого пути больше не нужны — импорты подчистить.

### 4.3. Новый абстрактный `VertexConsumer.setUv3(float, float)`

Метод называется `setUv3`, не `setUvN` (в задании было приблизительно). Это единственное
изменение интерфейса: `com.mojang.blaze3d.vertex.VertexConsumer` в 26.3 добавил
`VertexConsumer setUv3(float u, float v)` и default-метод `putBakedQuadWithGlint`.
Остальные девять абстрактных методов те же.

Что он значит: **UV3 — отдельный float-канал для «наклеенной» текстуры (glint / decal)**.
В 26.2 блеск зачарования делался обёрткой `SheetedDecalTextureGenerator`, которая
переписывала обычный `setUv`. В 26.3 у него собственный атрибут вершины:
`SheetedDecalTextureGenerator.setSheetedDecalUv(...)` в конце вызывает
`delegate.setUv3(-worldPos.x * scale, -worldPos.y * scale)`
(`/opt/mc-src-26.3/com/mojang/blaze3d/vertex/SheetedDecalTextureGenerator.java:88-103`).

Важная деталь, снимающая половину вопросов: атрибут `UV3` есть **только** у формата
`DefaultVertexFormat.ENTITY_GLINT_SPECIAL` (`DefaultVertexFormat.java:48-56`).
В `BufferBuilder.setUv3` запись идёт через `beginElement(5)`, который для формата без
UV3 возвращает `-1` и запись молча пропускается (`BufferBuilder.java:236-244`). То есть
для всей геометрии, которую рисуют BlockUI и Structurize (`POSITION_COLOR`,
`POSITION_TEX_COLOR`, `BLOCK`, `ENTITY`), `setUv3` — фактически no-op.

Отсюда семантика по каждой из четырёх обёрток. Одинаковой заглушки нет, но и расходятся
они ровно так же, как уже расходились их `setUv`/`setUv2`:

| обёртка | что делает | `setUv3` |
|---|---|---|
| `NoopVertexConsumer` (BlockUI, `BlockStatePipRenderer:275`) | глотает всё | `return this;` |
| `PoseTransformingVertexConsumer` (BlockUI, `BlockStatePipRenderer:331`) | трансформирует позицию и нормаль, остальное пробрасывает | `parent.setUv3(u, v); return this;` |
| `ColouredVertexConsumer` (BlockUI) | сквозной делегат с полем `defaultColor` | `parent.setUv3(u, v); return this;` |
| `ChunkOffsetBufferBuilderWrapper` (Structurize) | смещает позицию, остальное пробрасывает | `return delegate.setUv3(u, v);` |

Образцы кода — ванильные реализации: `SpriteCoordinateExpander:46-49` (сквозной делегат,
`return this.delegate.setUv3(u, v);`) и `SheetedDecalTextureGenerator:70-72`.

**Почему у `PoseTransformingVertexConsumer` именно проброс, а не трансформация.** UV3 —
двумерная текстурная координата, вычисленная вызывающим кодом из уже трансформированной
позиции; матрицей 4×4 её осмысленно не преобразовать. Проброс — то же, что этот класс
уже делает с `setUv`/`setUv2`. Если однажды окажется, что через него идёт glint-геометрия
(формат `ENTITY_GLINT_SPECIAL`), координата наклейки будет посчитана в чужом
пространстве. На сегодня такого пути в BlockUI нет; **проверить это можно только
запуском клиента, чего в этом контейнере не будет** — так и запиши в «не проверено».

Возвращаемый тип: если класс сужает возврат (`ColouredVertexConsumer` возвращает
`ColouredVertexConsumer`), сузь и здесь — иначе ковариантность разъедется.

### 4.4. Четвёртой общей темы нет

Проверил пересечение корневых причин по трём логам. Пересечение BlockUI ∩ Structurize —
это ровно §4.1–4.3 и ничего больше. Domum Ornamentum не пересекается с ними **ни одним**
пунктом: его поломки — блоки и датаген, а не рендер и ввод.

Единственная вещь, которую всё же стоит решить один раз, — форма провайдеров рецептов
(§5.3). Она общая не между библиотеками, а между Domum и модом (фаза 4, ось C
чеклиста, 61 ошибка). Агент 2 решает её первым; его решение станет образцом для мода,
поэтому пусть будет ванильной формы, а не самодельной.

Отдельно, чтобы не тратили время на «а не общее ли это»: поломки, которые уже описаны
в `API-CHECKLIST-26.3.md` для мода и повторяются в одной библиотеке, — берите готовый
ответ оттуда, а не изобретайте свой:
`PushReaction` (B9) и кодеки блоков (B3) → Domum;
`FriendlyByteBuf.readList/writeCollection` (B6) и `new BlockPos(BlockPos)` (B4) → Structurize;
`PotionBrewing` (A2) и `FuelValues` (B12) → BlockUI.

---

## 5. Врезки по библиотекам

Числа — из логов компиляции, а не из таблицы плана. «Ошибок» — итоговый счёт javac;
«площадок» — уникальных пар файл+строка (у javac одна строка иногда даёт несколько ошибок).
**У Domum Ornamentum в `PORT-PLAN-26.3.md` стоит «33 файла» — это неверно, файлов 13.**

### 5.1. BlockUI — 63 ошибки, 11 файлов, 58 площадок

Рендер и ввод. Почти всё покрывается тремя общими решениями.

| файл | площадок | что |
|---|---|---|
| `blockui/UiRenderMacros.java` | 13 | §4.1 целиком — только импорты |
| `blockui/controls/TextField.java` | 12 | §4.2, вариант с `KEYCODE_*` |
| `common/fakelevel/FakeLevel.java` | 10 | своё, см. ниже |
| `blockui/util/texture/CursorTexture.java` | 6 | §4.2, SDL-курсор из `NativeImage` |
| `blockui/util/cursor/Cursor.java` | 5 | §4.2, системные курсоры + `select()` |
| `blockui/BOScreen.java` | 5 | §4.2, кнопки мыши и граница кодов клавиш |
| `blockui/mod/BlockUIClient.java` | 2 | §4.2, `isKeyDown` |
| `blockui/mod/item/BlockStatePipRenderer.java` | 2 | §4.3, две вложенные обёртки |
| `blockui/util/color/ColouredVertexConsumer.java` | 1 | §4.3 |
| `common/fakelevel/FakeLevelChunkSection.java` | 1 | `fillBiomesFromNoise` |
| `common/fakelevel/FakeChunk.java` | 1 | `fillBiomesFromNoise` |

**Смотреть в первую очередь:** `UiRenderMacros` — 13 площадок снимаются правкой двух
импортов, это самый дешёвый кусок и он сразу сократит лог вдвое. Дальше `TextField`:
там не механическая замена, а выбор семьи констант, и ошибиться легко.

Что в BlockUI своё, вне общих решений:

* `FakeLevel.java` — три удалённых типа. `AbortableIterationConsumer.Continuation` стал
  самостоятельным `net.minecraft.util.Continuation` (тот же enum `CONTINUE`/`ABORT`);
  `PotionBrewing` и `Level.potionBrewing()` удалены целиком (чеклист A2);
  `FuelValues` и `Level.fuelValues()` удалены (чеклист B12). `FakeLevel` — фасад над
  настоящим миром, поэтому оба метода теперь просто нечего переопределять: снимай
  `@Override` вместе с методом. Это ступень 4 лестницы деградации — строкой в отчёт.
* `FakeChunk:300` и `FakeLevelChunkSection:151` — у `fillBiomesFromNoise` пропал параметр
  `Climate.Sampler`: `ChunkAccess.fillBiomesFromNoise(BiomeResolver)` и
  `LevelChunkSection.fillBiomesFromNoise(BiomeResolver, int, int, int)`.
  В `FakeLevelChunkSection` рядом ещё `acquire()` и `write(FriendlyByteBuf)` — проверь
  по `javap` обеих версий, какие из них ещё существуют.
* `Cursor.java:86` — «does not override» на `select(Window)`; см. §4.2.
* `BOScreen:177` — `key >= 0 && key <= GLFW.GLFW_KEY_LAST` было «клавиша без печатного
  представления». Верхняя граница теперь `SDLScancode.SDL_SCANCODE_COUNT` (512);
  проверку можно и просто свести к `key >= 0`, но тогда напиши почему.

### 5.2. Structurize — 76 ошибок, 9 файлов, 50 площадок

Самый узкий фронт: две трети — один файл.

| файл | площадок | что |
|---|---|---|
| `util/WorldRenderMacros.java` | 26 | §4.1 целиком — только импорты |
| `client/ModKeyMappings.java` | 12 | §4.2, кейбинды |
| `util/BlockUtils.java` | 4 | своё, см. ниже |
| `event/ClientEventSubscriber.java` | 2 | §4.2, `hasControlDown` |
| `network/messages/OperationHistoryMessage.java` | 2 | чеклист B6 |
| `client/BlueprintRenderer.java` | 1 | `shouldRender` |
| `placement/.../PlacementHandlers.java` | 1 | `DirtPathBlock` |
| `client/ChunkOffsetBufferBuilderWrapper.java` | 1 | §4.3 |
| `blueprints/v1/BlueprintUtil.java` | 1 | чеклист B4 |

**Смотреть в первую очередь:** `WorldRenderMacros` — 26 из 50 площадок, и все они
снимаются переписыванием шести строк импортов. Начни с него, дальше лог станет читаемым.

Своё, вне общих решений:

* `BlockUtils:281` и `PlacementHandlers:813` — класса `DirtPathBlock` больше нет.
  Он переименован и обобщён: `net.minecraft.world.level.block.PathBlock`
  (`Blocks.DIRT_PATH = p -> new PathBlock(DIRT, p)`, `Blocks.java:3613-3617`).
  `instanceof DirtPathBlock` → `instanceof PathBlock`. **Внимание на семантику:**
  `PathBlock` теперь общий класс, а не «именно земляная тропа». Если проверка должна
  значить именно ванильную тропу — сравнивай с `Blocks.DIRT_PATH`, а не по классу.
* `BlockUtils:524` — `BedItem` удалён (та же волна, что `AxeItem`/`PickaxeItem`, чеклист
  B11). Двумя строками выше этот же метод уже пишет `stackToPlace.is(ItemTags.BEDS)` —
  используй тот же тег: `itemStack.is(ItemTags.BEDS) ? Direction.UP : Direction.NORTH`.
* `BlockUtils:222` — `NoiseGeneratorSettings.surfaceRule()` переименован в
  `materialRule()` и отдаёт `Holder<SurfaceRules.RuleSource>`, а не сам `RuleSource`
  (`NoiseGeneratorSettings.java:28`). То есть `.surfaceRule().apply(ctx)` →
  `.materialRule().value().apply(ctx)`.
* `BlueprintRenderer:373` — `EntityRenderDispatcher.shouldRender` получил седьмой
  параметр `float partialTicks` (`EntityRenderDispatcher.java:123-127`). Передавай тот
  же partial tick, который уже используется в этом методе рендера, а не `0`.
* `OperationHistoryMessage:33,45` — `readList`/`writeCollection`; готовый ответ в
  чеклисте B6 (`ByteBufCodecs.collection`).
* `BlueprintUtil:165` — `new BlockPos(BlockPos)`; чеклист B4. Здесь аргумент —
  результат `subtract(...)`, то есть уже иммутабельный `BlockPos`: копия просто не нужна.

**Приёмка Structurize отдельная.** Его `build.gradle` берёт BlockUI и Domum как
`implementation files("libs/...")`. `tools/stage-libs.sh` кладёт туда сборки 26.2 из
`dist/`, чтобы можно было начинать не дожидаясь агентов 1 и 2. Скрипт честно печатает,
откуда взял каждый jar. **Порт Structurize не считается проверенным, пока тот же скрипт
не покажет обе зависимости как `ported 26.3 build` и Structurize не пересоберётся против
них.** Сборка против 26.2-jar-ов — это промежуточный результат, и в отчёте её надо
называть именно так.

### 5.3. Domum Ornamentum — 35 ошибок, 13 файлов, 35 площадок

Ни одного пересечения с двумя другими библиотеками. Широко и мелко: 20 из 35 площадок —
один и тот же датагенный шаблон в пяти файлах.

| файл | площадок | что |
|---|---|---|
| `datagen/loot/MaterialLootTableProvider.java` | 5 | лут: два удалённых класса |
| `datagen/global/MateriallyTexturedBlockRecipeProvider.java` | 4 | шаблон провайдера рецептов |
| `datagen/global/GlobalRecipeProvider.java` | 4 | тот же шаблон |
| `datagen/extra/ExtraRecipeProvider.java` | 4 | тот же шаблон |
| `datagen/floatingcarpet/FloatingCarpetRecipeProvider.java` | 4 | тот же шаблон |
| `datagen/bricks/BrickRecipeProvider.java` | 4 | тот же шаблон |
| `block/decorative/ShingleSlabBlock.java` | 2 | кодек блока |
| `block/decorative/PostBlock.java` | 2 | кодек блока |
| `block/decorative/PanelBlock.java` | 2 | кодек блока |
| `recipe/architectscutter/ArchitectsCutterRecipeBuilder.java` | 1 | `unlocked(...)` |
| `block/decorative/DynamicTimberFrameBlock.java` | 1 | `PushReaction` |
| `block/decorative/TimberFrameBlock.java` | 1 | `PushReaction` |
| `block/decorative/FramedLightBlock.java` | 1 | `PushReaction` |

**Смотреть в первую очередь:** шаблон провайдера рецептов. Он одинаковый в пяти файлах,
и его же будет переписывать мод в фазе 4 — решай его так, чтобы можно было скопировать.

* **Провайдеры рецептов, 20 площадок.** В fabric-api 0.158 у `FabricRecipeProvider`
  сменился абстрактный метод (сверено `javap` по обеим версиям jar-а):
  ```java
  // было (fabric-api 0.154 / 25.4.4)
  protected abstract RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput exporter);
  // стало (fabric-api 0.158 / 27.1.4)
  protected abstract RecipeProvider createRecipeProvider(HolderLookup.Provider registries,
                                                         BootstrapContext<Recipe<?>> recipeOutput,
                                                         BootstrapContext<Advancement> advancementOutput);
  ```
  Заодно у самого `RecipeProvider` сменился конструктор — `HolderLookup.Provider` из него
  ушёл: `protected RecipeProvider(BootstrapContext<Recipe<?>>, BootstrapContext<Advancement>)`
  (`/opt/mc-src-26.3/net/minecraft/data/recipes/RecipeProvider.java:98`). Рецепты стали
  обычной регистрацией в реестр, отсюда и `BootstrapContext`. Ванильный образец
  подкласса — `net/minecraft/data/recipes/packs/VanillaRecipeProvider.java:64`.
* **`ArchitectsCutterRecipeBuilder:98`** — `RecipeUnlockedTrigger.unlocked` теперь берёт
  `Holder<Recipe<?>>` (или `HolderSet`), а не `ResourceKey`. Как ваниль достаёт холдер
  из ключа — `net/minecraft/data/recipes/RecipeUnlockAdvancementBuilder.java:28`:
  `RecipeUnlockedTrigger.unlocked(output.lookup(Registries.RECIPE).getOrThrow(id))`.
* **`MaterialLootTableProvider`** — два удалённых класса, оба есть в чеклисте B1:
  `entries.LootPoolSingletonContainer` → класс `entries.SingleEntryContainerBase`, но вложенный
  **`Builder` — в `UniformContainerBase`** (`SingleEntryContainerBase` от него наследуется, а
  `Builder` объявлен у родителя). `LootItem.lootTableItem()` возвращает
  `UniformContainerBase.Builder<?>`; `SingleEntryContainerBase.Builder` не существует.
  Проверено при порте Domum;
  `predicates.LootItemBlockStatePropertyCondition` → `predicates.MatchBlock`.
  У `MatchBlock` другая форма вызова, `hasBlockStateProperties(block)` там нет:
  `MatchBlock.blockMatches(HolderGetter<Block> lookup, Block block, StatePropertiesPredicate.Builder properties)`
  (`MatchBlock.java:34-43`). Ванильные вызовы — `data/loot/packs/VanillaBlockLoot.java:762,779`.
  Обрати внимание, что теперь нужен `HolderGetter<Block>`; в провайдере он берётся оттуда же,
  откуда его берёт ваниль (`this.blocks`).
* **Кодеки блоков (`ShingleSlabBlock`, `PostBlock`, `PanelBlock`)** — чеклист B3, работа
  на удаление: убрать поле `CODEC`, `simpleCodec(...)` и `@Override codec()`.
* **`PushReaction.PUSH_ONLY` → `PUSH`** — чеклист B9. Там же предупреждение: мапить по
  ordinal нельзя, порядок в enum переставлен. Соответствие брать из таблицы чеклиста.

Предупреждения `[removal]` про `BlockItemWithClientBePlacement` в 20 файлах — **не ошибки
и не работа этого порта**: это собственная депрекация Domum, она была и в 26.2.
Не трогай их и не считай их в свои числа.

---

## 6. Правило деградации

Если что-то не переносится — спускайся по лестнице, **не блокируйся и не спрашивай**:

1. отключить регистрацию;
2. оставить тело метода, но обезвредить;
3. функциональная деградация;
4. убрать объект данных.

Каждое применение — отдельной строкой в отчёт: что, на какой ступени, почему.
Комментарий в коде помечай так же, как это делал прошлый порт: `TODO(port-26.3): DEGRADED — ...`,
`TODO(port-26.3): DISABLED — ...`. Маркеры `TODO(port-26.2)` в унаследованном коде
не трогай, если они не про твою правку.

## 7. Готово — это два зелёных прогона

1. `tools/mc-build.sh libs/<своя>/26.3 compileJava` — зелёный;
2. затем `tools/mc-build.sh libs/<своя>/26.3 build` — зелёный.

Обе команды через лок, обе с числами в отчёте (сколько ошибок было в начале, сколько
после каждого прогона). `compileJava` зелёный, а `build` не пробовали — это «не готово».
Для Structurize добавляется условие из §5.2 про `stage-libs.sh`.

## 8. Формат финального отчёта

Коротко, без пересказа процесса:

1. **Сделано** — зона, сколько файлов тронуто, числа ошибок до/после.
2. **Отклонения от контракта** — каждая изменённая чужая сигнатура или публичное API
   библиотеки, с причиной. Downstream (мод и Structurize) на них завязан.
3. **Деградации** — что отключено/обезврежено и на какой ступени лестницы.
4. **Осталось / чужое** — проблемы в чужих зонах, которые видел, но не трогал.
5. **Не проверено** — обязательный пункт. «Скомпилировалось» и «работает» — разные
   утверждения: клиент в этом контейнере никто не запускал и не запустит, миксины и
   AccessWidener на 26.3 не валидировались, датаген не гонялся. Всё, что упирается в
   рантайм — сюда, поимённо.

Не приукрашивай. Где не уверен — так и пиши: это дешевле, чем уверенно сделанное не то.
