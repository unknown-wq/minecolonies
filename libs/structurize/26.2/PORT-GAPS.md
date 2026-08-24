# PORT-GAPS — что отключено, деградировано или не проверено

Рабочий список на «потом». Заполняется оркестратором по отчётам агентов, по строке на каждый срез.
Правило заказчика: **зелёная сборка важнее полноты фич, но всё вырезанное должно быть
записано так, чтобы это можно было починить, не занимаясь археологией.**

Каждой строке здесь обязан соответствовать маркер в коде:
```java
// TODO(port-26.2): DISABLED — <причина одной строкой>
/* … оригинальный код нетронутым … */
```
Сверка: `grep -rn "TODO(port-26.2)" src | wc -l` == сумме колонки «маркеров».

Колонки:
- **Что видно в игре** — наблюдаемое следствие. Если следствия нет, так и писать («невидимо»).
- **Как чинить** — конкретная зацепка: какой класс, какой API 26.2, что проверить.
- **Приоритет** — 🔴 серверный геймплей · 🟡 клиентская визуалка · 🟢 совместимость/косметика.

**Счёт маркеров: 37** (сверено после фазы 4: `gradle build` зелёный, `runServer` поднимает мир за 0.7 с, ноль `ERROR`/`FATAL`).
🔴 нет ни одного — серверный геймплей не резался нигде.

---

## Отключённый контент

| # | Файл:строка | Марк. | Что отключено | Почему | Что видно в игре | Как чинить | Приоритет |
|---|---|---:|---|---|---|---|---|
| 1 | ~~`client/gui/**`~~ — **ЗАКРЫТО в фазе 4** | 0 | Весь GUI мода | BlockUI на 26.2 не был готов на момент фаз 1–2 | — | Портировано: блок `exclude` снят, 13 файлов (4496 строк) в сборке, 9 тел `GuiStubs` возвращены. XML-layout'ы не потребовали ни одного изменения. `GuiStubs` **оставлен фасадом** — он же граница клиент/сервер | ✅ |
| 2 | `items/ItemScanTool.java:264`, `items/ItemTagSubstitution.java:127` | 2 | `IItemExtension#getHighlightTip` | NeoForge-расширение, ванильного аналога нет | Имя предмета над хотбаром не дописывает « - \<слот\>» / « - \<блок\>». Тот же текст остался в тултипе | Миксин на рендер имени предмета в `Gui` | 🟢 |
| 3 | `items/ItemBuildTool.java:65`, `items/ItemShapeTool.java:49` | 2 | `getCraftingRemainingItem` / `hasCraftingRemainingItem` | NeoForge-расширения; ванильный `craftRemainder(Item)` не умеет «вернуть тот же стек» | Билд-тул и шейп-тул в крафте расходуются. В моде рецепта с ними нет | Миксин на `ItemStack#getCraftingRemainingItem` | 🟢 |
| 4 | `blockentities/BlockEntityTagSubstitution.java:245` | 1 | `BlockEntity#removeComponentsFromTag(CompoundTag)` | Метода в 26.2 нет (0 вхождений) | Невидимо: ваниль сама вычищает component-ключи | Проверить, что тег `captured_block` не дублируется в NBT предмета | 🟢 |
| 5 | `event/ClientLifecycleSubscriber.java:79` | 1 | `ItemBlockRenderTypes.setRenderLayer(blockSubstitution, translucent)` | Класса в 26.2 нет, слой задаётся JSON-ом модели | **✅ ПОЧИНЕНО в фазе 3** — `"render_type": "translucent"` добавлен в `models/block/blocksubstitution.json:3`. Маркер оставлен как документация переезда | — | 🟢 |
| 6 | `event/ClientLifecycleSubscriber.java:86` | 1 | Регистрация `OverlaidModelLoader` | `IGeometryLoader` — NeoForge; сам лоадер тоже мёртв (строки 16–18) | Оверлей-модель тега не грузится | **ЗАКРЫТО ОКОНЧАТЕЛЬНО** — регистрировать некуда и нечего | 🟢 |
| 8 | `event/ClientLifecycleSubscriber.java:96` | 1 | `WorldRenderMacros.RenderTypes.registerBuffer` | `RegisterRenderBuffersEvent` — NeoForge; батчинг делает `SubmitNodeCollection` | Невидимо — в 26.2 буферов мода не существует | **ЗАКРЫТО ОКОНЧАТЕЛЬНО** | 🟢 |
| 9 | `event/ClientLifecycleSubscriber.java:102` | 1 | `IClientItemExtensions#getCustomRenderer` для `blockTagSubstitution` | NeoForge-only + `BlockEntityWithoutLevelRenderer` удалён | Предмет-заместитель в руке рисуется обычной моделью | Вместе со строкой 15 | 🟡 |
| 10 | `util/WorldRenderMacros.java:51` | 1 | `Stage` → свой enum, все 3 стадии стреляют подряд из одного `COLLECT_SUBMITS` | `RenderLevelStageEvent.Stage` — NeoForge; в 26.2 стадий нет, порядок задаёт `RenderType` | Возможные артефакты сортировки прозрачного | Если сортировка врёт — раскидать по `LevelRenderEvents.AFTER_OPAQUE_TERRAIN` / `AFTER_TRANSLUCENT_TERRAIN` | 🟡 |
| 11 | `util/WorldRenderMacros.java:125` | 1 | Ручной push модельно-видовой матрицы | `RenderSystem.applyModelViewMatrix` удалён | Невидимо | — | 🟢 |
| 12 | `util/WorldRenderMacros.java:156,168` | 2 | `pushShaderMvMatrixFromPose` / `popShaderMvMatrix` → no-op | То же | Невидимо, вызывающих нет | — | 🟢 |
| 13 | `util/WorldRenderMacros.java:1142` | 1 | **НЕ ГЭП — отображение точное.** `NEVER_DEPTH_TEST` → `CompareOp.NEVER_PASS` | В `1.21.1/.../WorldRenderMacros.java` **два разных** шарда: `NeverDepthTestStateShard:1244` → `depthFunc(GL_NEVER)` и `AlwaysDepthTestStateShard:1258` → `depthFunc(GL_ALWAYS)`. Первый и правда не проходит никогда | Ничего: `GLINT_LINES` и `COLORED_TRIANGLES_NC_ND` в 1.21.1 сидели на `GL_NEVER`, поведение сохранено | **Прежний совет «заменить на `ALWAYS_PASS`» был неверен и снят** — это сломало бы семантику. «Без depth-теста» — это `ALWAYS_DEPTH_TEST` → `CompareOp.ALWAYS_PASS`, он уже стоит на `GLINT_LINES_WITH_WIDTH`. Обе константы теперь публичны, см. `RenderTypes.createRenderType(...)` | ✅ |
| 14 | `util/WorldRenderMacros.java:1174` | 1 | `RenderTypes.registerBuffer` / `finishBuffer` | `RegisterRenderBuffersEvent` — NeoForge | Невидимо | — | 🟢 |
| 15 | `client/TagSubstitutionRenderer.java:123` | 1 | `renderByItem` — предметный рендер якоря | `BlockEntityWithoutLevelRenderer` удалён, `IClientItemExtensions#getCustomRenderer` — NeoForge | Предмет в инвентаре рисует плоскую модель без захваченного блока внутри | `SpecialModelRenderer` через `"minecraft:special"` в `items/blocktagsubstitution.json` + `BuiltInBlockModelsCallback` | 🟡 |
| 16–18 | `client/model/Overlaid{BakedModel,Geometry,ModelLoader}.java` | 3 | Весь кастомный лоадер моделей `structurize:overlaid` | `BakedModel`, `BakedModelWrapper`, `IUnbakedGeometry`, `IGeometryLoader`, `ItemOverrides` — всё удалено (ровно строки 10/12 `PORT-GAPS.md` DO) | Блок якоря рисуется плоской родительской моделью; ключ `"loader"` в JSON ваниль игнорирует | `ModelLoadingPlugin.Context#modifyBlockModelAfterBake` из `fabric-model-loading-api-v1` | 🟢 |
| 19 | `client/ClientItemStackTooltip.java:51` | 1 | Шрифт предмета из `IClientItemExtensions#getFont` | NeoForge-only | Тултип всегда дефолтным шрифтом | — | 🟢 |
| 20 | `client/ModKeyMappings.java:22` | 1 | `IKeyConflictContext` / `BLUEPRINT_WINDOW` — **осталась только декларативная половина** | NeoForge-only, у `KeyMapping` контекста нет | Поведение восстановлено: `isBlueprintWindowActive()` — реальный тест, кейбинды окна чертежа вне окна больше не срабатывают. Осталась косметика: `X`/`Z`/`M`/`Enter`/стрелки подсвечиваются в настройках управления как конфликтующие с ванильными | Вернуть нечем — у ванильного экрана управления понятия контекста нет | 🟢 |
| 21 | `client/ModKeyMappings.java:77` | 1 | `KeyModifier.SHIFT` у `ROTATE_CW/CCW` | Модификаторов в 26.2 нет | Поворот превью перевешен с `Shift+←/→` на `X` / `Z` | — | 🟡 |
| 22 | `client/ChunkOffsetBufferBuilderWrapper.java:13` | 1 | Класс не используется | Следствие деградации 6 | Невидимо | — | 🟢 |
| 23 | `assets/structurize/shaders/alpha.frag:2` | 1 | GLSL 120 шейдер | 26.2 — GLSL 450 + UBO bind groups, шейдер объявляется из `RenderPipeline` | Невидимо, из java не используется | Вместе с деградацией 5 | 🟢 |

## Функциональная деградация

| # | Файл:строка | Марк. | Что деградировало | Почему | Что видно в игре | Как чинить | Приоритет |
|---|---|---:|---|---|---|---|---|
| 1 | `compat/itemhandler/ItemHandlers.java:14`, `api/ItemStackUtils.java:106,206` | 3 | Поиск инвентарей через capability-API NeoForge — заменён ванильным `Container` | На Fabric capability-API нет, `fabric-transfer-api-v1` несовместим по семантике | «Требуемые предметы» для блока, чей инвентарь публикуется **только** модовой capability, выходят пустыми. Все ванильные контейнеры считаются полностью | Мост на `ItemStorage.SIDED` внутри `ItemHandlers` — фасад уже изолирует это в одном месте | 🟢 |
| 2 | `com.ldtteam.common.config.*` — **в jar-е BlockUI, маркеров в нашем дереве нет** | 0 | Конфиг **не персистится и не синхронизируется** | Контракт K4 порта BlockUI: `ModConfigSpec` мёртв, `Configurations` держит значения только в памяти. Наша копия из фазы 1 умела JSON, настоящая библиотека — нет | Настройки превью (прозрачность, свет, share) **сбрасываются при каждом запуске**; клиент на удалённом сервере видит свои значения `getServer()` | Чинить **один раз в BlockUI**: `ConfigValue#save()` + чтение/запись в `Configurations`. Тогда почини́тся и у MineColonies | 🟡 |
| 3 | `com.ldtteam.common.fakelevel.IFakeLevelLightProvider` — **в jar-е BlockUI** | 0 | `getShade` | В 26.2 нет `Level#getShade`; затенение уехало в `BlockModelLighter`. **Порт BlockUI сделал то же самое независимо** — значит это не наша ошибка | Превью затеняет грани по-ванильному, а не принудительно ровно | `/opt/mc-src/net/minecraft/client/renderer/block/BlockModelLighter.java` | 🟡 |
| 5 | `blueprints/v1/BlueprintUtils.java:47` (`instantiateTileEntities`) | 1 | Убран параметр `Map<BlockPos, ModelData>` | `net.neoforged.neoforge.client.model.data.ModelData` на Fabric не существует | Ничего — чисто клиентский рендер-путь | Канал 26.2 — `RenderDataBlockEntity#getRenderData()`, внешняя карта не нужна | 🟢 |
| 6 | `client/BlueprintRenderer.java:175` | 1 | **Жидкости в превью** | `BlockRenderDispatcher#renderLiquid`, `ItemBlockRenderTypes` удалены; `FluidRenderer` живёт внутри сборщика секций и фейк-левелу недоступен | Вода и лава в схематике не рисуются | Миксин на `FluidRenderer` либо генерация квадов вручную через `QuadEmitter` | 🟡 |
| 7 | `client/BlueprintRenderer.java:345` | 1 | `Lighting.setupLevel/setupNetherLevel`, `FogRenderer.setupFog/setupNoFog` | Сигнатур нет, туман — `GpuBufferSlice` уровня | Превью может не попадать под туман точно как ванильная геометрия | — | 🟢 |
| 8 | `client/BlueprintRenderer.java:396` | 1 | `Entity#noCulling` | Поля нет, решение у `EntityRenderer#affectedByCulling` (protected) | Сущность, торчащая далеко за AABB чертежа, может пропадать | AW на `affectedByCulling` | 🟢 |
| 9 | `client/BlueprintRenderer.java:465` | 1 | Per-BE фрустум-куллинг превью | `BlockEntityRenderer#getRenderBoundingBox` удалён | Невидимо (чуть больше работы) | — | 🟢 |
| 10 | `client/BlueprintRenderer.java:511` (`TransparencyHack`) | 1 | **Прозрачность превью** | `glBlendColor`, `GlStateManager.BLEND`, `RenderSystem.blendFunc/enableBlend` удалены; blend — свойство иммутабельного `RenderPipeline` | Превью рисуется непрозрачным, настройка `rendererTransparency` не работает | Свой `RenderPipeline` с `BlendFunction.TRANSLUCENT` + альфа в вершинный цвет через враппер `BlockStateModel` | 🟡 |
| 11 | **вся архитектура** `client/BlueprintRenderer.java` | 1 | Bake в `VertexBuffer` + свой шейдер → per-block `BlockModelRenderState#submit` | `VertexBuffer`, `ShaderInstance`, `Uniform.CHUNK_OFFSET`, `BakedModel`, `ModelData`, `MultiBufferSource` удалены | Превью «плоско освещено» (item-листы, без AO), **возможна просадка FPS на больших чертежах** — один submit на блок на кадр вместо одного draw-call на весь чертёж | Долгосрочно — свой сборщик секций поверх `SectionBufferBuilderPack` + `StagedVertexBuffer` | 🟡 |
| 12 | `client/TagSubstitutionRenderer.java:85` | 1 | Блок-сущность внутри якоря замены | `tryExtractRenderState` куллит по позиции камеры реального уровня и отбрасывает BE в `BlockPos.ZERO` фейк-левела | Сундук в якоре показан без анимированной крышки | Вручную `renderer.createRenderState()` + `extractRenderState(...)` минуя диспетчер | 🟡 |
| 13 | `client/TagSubstitutionRenderer.java:115` | 1 | `NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT` | Класса нет; лист выбирает `BlockModelRenderState#setupModel` | Замена внутри якоря больше не «слоёно-прозрачная» | — | 🟢 |

| 14 | `blueprints/v1/DataVersion.java:15,20` | 0 | Добавлен `v26_2(4903)`, промежуточные релизы 1.21.2…26.1.2 пропущены | Их номера ниоткуда не подтверждаются | Невидимо: цепочка нужна только для пошагового прохода `DataFixerUtils`, ванильный фиксер прыгает сразу | Дописать недостающие `DataVersion`, если найдётся источник номеров | 🟢 |
| 15 | `fabric.mod.json` **мода BlockUI** | 0 | `Unsupported root entry "credits"` | Схема 1 такого поля не знает | WARN в логе на каждом старте, загрузку не ломает | Перенести содержимое в `authors`/`contributors` в дереве BlockUI | 🟢 |

| 16 | `client/gui/AbstractBlueprintManipulationWindow.java:410` | 1 | Валидация числового ввода в окне настроек: `ValueSpec#test(Number)` → проверка только парсибельности | `ModConfigSpec.ValueSpec` не существует; `com.ldtteam.common.config.ConfigValue` отдаёт `getTranslationKey`/`getComment`, а **диапазон — нет**: min/max спрятаны внутри `IntValue`/`DoubleValue` | Поле ввода больше не краснеет при выходе за диапазон; значение молча зажимается сеттером (`Math.clamp`). Данные не портятся | Добавить в BlockUI `ConfigValue#getMin()/getMax()` или `boolean test(T)` — чинится один раз для всех модов | 🟢 |
| 17 | `client/gui/util/ItemUtil.java:20` | 1 | Тест «ведро не пустое»: `BucketItem.content != Fluids.EMPTY` → `getFluidContext() != ClipContext.Fluid.SOURCE_ONLY` | `BucketItem.content` в 26.2 `protected` (NeoForge публиковал его AT-ом) | Для ванили эквивалентно. Модовое ведро, переопределившее `getFluidContext()` не как ваниль, может не попасть в список выбора блока | Строка AccessWidener `accessible field net/minecraft/world/item/BucketItem content Lnet/minecraft/world/level/material/Fluid;` | 🟢 |

## Починено в фазе 3 (не гэпы — исправленные баги)

| Что | Симптом | Причина | Лечение |
|---|---|---|---|
| `blueprints/v1/DataVersion` | **Сервер не стартовал вообще**: `RuntimeException: You are trying to run old mod on much newer vanilla` из `Structurize.checkDataFixer()`, до регистрации чего-либо | Перечисление заканчивалось на `v1_21_1(3955)`/`UPCOMING(3956)`, а данные версии 26.2 — **4903** (`/opt/mc-src/net/minecraft/DetectedVersion.java:28`) | `v26_2(4903, "26.2", UPCOMING)` + `UPCOMING(4904)` |
| Все 6 рецептов мода | `Couldn't parse data file 'structurize:<recipe>' … No key fabric:type in MapLike[{"tag":"c:ingots/iron"}]` — рецептов в игре просто нет | Ингредиент в 26.2 — **строка**: `"minecraft:iron_ingot"` или `"#minecraft:logs"`, а не объект `{"item":…}` / `{"tag":…}` | Переписаны все ключи всех рецептов. Рецепты Structurize рукописные, а не датаген — `runDatagen` их бы не поймал |
| BER якоря тега | Якорь не показывал заменяемый блок | Писалось, пока `TagSubstitutionRenderer` был не портирован | `BlockEntityRendererRegistry.register(ModBlockEntities.TAG_SUBSTITUTION.get(), TagSubstitutionRenderer::new)` включён |
| Фиксер блупринтов 1.12.2 | `fixCross1343` был закомментирован целиком | `FLOWER_POT_MAP`/`NOTE_BLOCK_MAP` уехали в приватный `ChunkPalettedStorageFix$MappingConstants` | Три строки AccessWidener (класс + два поля), тело расшито |
| AccessWidener | Две мёртвые строки | `Frustum.cubeInFrustum(DDDDDD)I` заменён публичным `isVisible(AABB)`; `Camera.setPosition(Vec3)` не нужен | Удалены, сборка и `runServer` зелёные |

## Поведенческие решения (маркеров нет, но знать надо)

| Файл | Решение | Почему |
|---|---|---|
| `event/LifecycleSubscriber` | `ServerStructurePackLoader.onServerStarting()` повешен на `SERVER_STARTING` **под `server.isDedicatedServer()`** | На NeoForge его звал `FMLDedicatedServerSetupEvent`, который на встроенном сервере не срабатывает. Без проверки в одиночной игре серверный загрузчик пакетов стартует вторым экземпляром поверх клиентского |
| `management/Manager` | `player.displayClientMessage(msg, false)` → `player.sendSystemMessage(msg)` | Метод удалён; семантика (сообщение в чат) сохранена |
| `util/ChangeStorage` | `level.markAndNotifyBlock(...)` → `level.sendBlockUpdated(...)` | Метод был NeoForge. Оба call-site'а шли сразу после полноценного `setBlock(pos, state, UPDATE_FLAG)`, который уже делает рассылку и обновление соседей — поведение эквивалентно |
| `storage/rendering/types/BlueprintPreviewData` | `@OnlyIn(Dist.CLIENT)` → `@Environment(EnvType.CLIENT)`, **не удаление** | Fabric Loader физически вырезает помеченные члены. Без стриппинга загрузка класса на dedicated server кончается `NoClassDefFoundError` |
| `commands/AbstractCommand` | NeoForge `EnumArgument` → `StringArgumentType.word()` + `suggests(...)` + свой `getEnum(...)` | Свой `ArgumentType` потребовал бы `ArgumentTypeInfo` в `BuiltInRegistries.COMMAND_ARGUMENT_TYPE`, иначе дерево команд не сериализуется клиенту |
| `build.gradle` | Добавлен `testImplementation "junit:junit:4.13.2"` | `src/test` несёт один JUnit 4 тест; на 1.21.1 junit приходил из родительского NeoForge-скрипта, которого больше нет |

## Не проверено (и почему)

| Область | Что именно не проверено | Причина |
|---|---|---|
| `client/**` целиком | Рендер превью схематики, рамки, текст тегов, кейбинды, тултип | В контейнере нет дисплея. `runServer` клиентский код не исполняет. Установлена только чистая компиляция. **Проверку выполняет заказчик на живом клиенте (фаза 5).** Порядок проверки — ниже |
| AccessWidener | Что все перенесённые строки нужны | Loom валидирует существование члена, но не использование |
| Датаген | `runDatagen` ни разу не запускался | Оракул из `1.21.1/` скопирован в ресурсы, мод укомплектован контентом без него |
| `compat/common/fakelevel/FakeLevel` | Что 29 реализованных абстрактных методов ведут себя как на NeoForge | Компиляция проходит; поведение видно только на клиенте |

## Порядок проверки на живом клиенте (фаза 5)

По убыванию вероятности расхождения — всё ниже написано вслепую.

1. **Белая рамка превью и красная рамка якоря.** Это `submitCustomGeometry` + семь новых `RenderPipeline`. Симптом «мимо»: рамок не видно, или видно сквозь блоки. Причина №1 — **обратный depth-буфер 26.2**: отображено `LEQUAL → GREATER_THAN_OR_EQUAL`, `GREATER → LESS_THAN_OR_EQUAL`. Если глубина инвертирована — поменять эти два `CompareOp` местами в `WorldRenderMacros.RenderTypes`, одна строка на тип.
2. **Само превью схематики.** (а) рисуется ли вообще; (б) **FPS на схематике в 10k+ блоков**; (в) освещение — будет «плоским», без AO; (г) прозрачность не работает; (д) воды и лавы в превью нет.
3. **Текст тегов над блоками** (tag tool). `drawInBatch` → `submitText`, порядок аргументов другой — смотреть цвет и фон.
4. **Сущности и блок-сущности внутри превью** (сундуки, таблички, мобы). Извлечение состояния идёт в фазе `COLLECT_SUBMITS`, ваниль извлекает раньше.
5. **Якорь замены тега** в мире — должен показывать модель захваченного блока (после расшивки строки 7).
6. **Кейбинды**: категория покажется сырым ключом `key.category.structurize.general`, пока не добавлена строка в lang. `Rotate CW/CCW` перевешены с `Shift+←/→` на `X` / `Z`.
7. **Тултип со стаком** — переписан на `extractText`/`extractImage`, проверить выравнивание иконки и текста.

### GUI — две вещи, найденные и починенные вслепую, смотреть первыми

**A. Читаются ли надписи на кнопках** (build tool: варианты чертежа, уровни, категории, опции размещения; shape tool: то же). В 1.21.1 они красились `ChatFormatting.BLACK.getColor()` == `0x000000`, и `Font.drawInBatch` сам дописывал альфу. В 26.2 фиксапа нет, а `GuiGraphicsExtractor#text` начинается с `if (ARGB.alpha(color) != 0)` — текст с нулевой альфой **не рисуется вообще**. Заменено на `ARGB.opaque(TextColor.BLACK.getValue())`. **Симптом «мимо»: кнопки есть, надписей на них нет.** Правится в четырёх местах: `AbstractBlueprintManipulationWindow:255`, `WindowExtendedBuildTool:742,801,943`.

**B. Реагируют ли окна на ввод с клавиатуры.** В 26.2 нажатие клавиши и ввод символа — разные события, а `Pane#onKeyTyped(char,int)` на уровне окна не вызывается вообще (при этом компилируется как `@Override`). Переписано на `onKeyEvent`/`onCharactedEvent`. Проверять:
- **Shape tool** — ввести размеры руками в поля width/length/height/frequency и в поле уравнения: превью должно перегенериться сразу, без нажатия кнопок;
- **Tag tool** — печатать в поле тега: список подсказок должен фильтроваться на лету;
- **Scan tool** — цифры `0`–`9` при **не сфокусированном** текстовом поле переключают слот сканирования;
- **Build/shape tool** — стрелки, `+`/`−`, `X`/`Z` (поворот), `M` (зеркало), `Enter` (разместить). И отдельно: **эти же клавиши при закрытом окне не должны делать ничего**.

### GUI — дальше по окнам

8. **Build tool** — открывается ли вообще, список паков/категорий/чертежей, иконки категорий (`OutOfJarResourceLocation` — картинки грузятся с диска, не из jar-а), кнопка «Switch pack».
9. **Окно настроек build tool'а** (шестерёнка): подписи и тултипы (если видны сырые ключи `structurize.config.*.comment` — нарушен порядок `loadLangPath` / `new Configurations` в `Structurize.java:44,45`); ввод числа вне диапазона молча зажимается вместо покраснения поля; диалог подтверждения прозрачности (`openAsLayer()` в 26.2 реализован по-новому) — «Cancel» должен вернуть в окно настроек, а не в мир.
10. **Scan tool** — списки блоков и **сущностей**. Иконка сущности строится через `EntityType#create(level, EntitySpawnReason.LOAD)`, который может вернуть `null` — тогда пустой слот, а не краш. Проверить лодку, стойку для брони, сундук-вагонетку.
11. **Contents-окно чертежа.** Здесь была ловушка границ: `getMaxBuildHeight()` был исключающим, `getMaxY()` — включающий, и то же у `IFakeLevelBlockGetter#getMaxX/getMaxZ`. Снят `−1` со всех трёх осей. **Проверять счётом:** чертёж 3×3×3 полного камня должен показать **27** блоков, а не 8.
12. **Replace block** — сообщение «ambiguous properties» печатает `Direction` для направленных свойств; сообщения уходят в **чат**, а не в строку над хотбаром.
13. **Switch pack** — две колонки паков, иконки с диска, тултип на отключённом паке.
14. **Undo/Redo** — открывается из scan tool и shape tool, список операций приходит с сервера.
15. **Select res** — фильтр по имени теперь ищет по `item.getDescriptionId()` вместо `stack.getDescriptionId()`.
16. **Ведро в списке выбора блока** — водяное и лавовое должны быть, пустое нет.

## Расхождения датагена с оракулом

Оракул — JSON в `src/datagen/generated/structurize/`, скопированные из `1.21.1/`.

_(пусто — заполняется после первого `runDatagen`)_
