# PORT-STATUS — Domum Ornamentum → Fabric / Minecraft 26.2

Живой документ порта. Создан оркестратором на шаге 0, дополняется всеми агентами.
Закон порта — `../porting-26.2/PORT-ANY-MOD-26.2.md`. Выше него по авторитету только
декомпилированные исходники игры в `/opt/mc-src`.

## Toolchain — ГОТОВО, НЕ ПЕРЕУСТАНАВЛИВАТЬ

| | |
|---|---|
| Java | `/usr/lib/jvm/java-25-openjdk-amd64` (Java 25) |
| Gradle | `/opt/gradle-9.6.1/bin/gradle` — **только он**, `./gradlew` не работает (403 на ассеты GitHub) |
| `/opt/mc-src` | декомпилированный Minecraft 26.2, готов — **только grep, никогда не перегенерировать** |
| Проект | `/home/user/Domum-Ornamentum/26.2/` |
| Ветка | `claude/happy-johnson-7bzvob` |

Любая сборка:
```sh
cd /home/user/Domum-Ornamentum/26.2 && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 \
  /opt/gradle-9.6.1/bin/gradle <task> --no-daemon 2>&1 | tee /tmp/errors.txt
```
**Одна инвокация Gradle одновременно на весь чекаут.** Две параллельные портят кэш Loom.

### Пины версий

`minecraft 26.2` · `fabric-loader 0.19.3` · `fabric-api 0.154.2+26.2` · `loom 1.17.13`
· `gradle 9.6.1` · `java 25` · **строки `mappings` нет**

### Референсные порты на диске (приоритет выше своей памяти)

| Путь | Чем полезен |
|---|---|
| `/workspace/simple-planes/26.2/` | **Ближайший аналог: NeoForge 1.21.1 → Fabric 26.2 одним хопом.** Регистрация, меню/контейнеры, рецепты, сеть |
| `/workspace/desolation/` | Fabric-датаген (`fabricApi.configureDataGeneration`), worldgen, дельта 26.1.2 → 26.2 |
| `/workspace/fabric-luckytntmod/` | Рендереры 26.2, HUD, сущности, взрывы |
| `/home/user/Domum-Ornamentum/26.1/` | **Источник порта. ТОЛЬКО ЧТЕНИЕ, не редактировать.** NeoForge 26.1 |
| `/home/user/Domum-Ornamentum/1.21.1/` | Три месяца мод-фиксов после 30.03.2026. Только чтение, переносить точечно |

## Rules (выжимка §9 + §10 закона — прочитать первым)

**DO**
1. Прежде чем писать — найти тот же паттерн в портированном референсном моде на диске.
2. Любую версионно-зависимую сигнатуру подтвердить `grep -rn '<symbol>' /opt/mc-src/`.
3. Работать **от ошибок, а не от файлов**: список ошибок → открыть только падающие строки → починить.
4. Держаться своего списка файлов. Нужна правка в чужом — написать в отчёте, самому не трогать.
5. Диффы маленькие и механические.
6. Если `/opt/mc-src` противоречит инструкции — **прав он**; сделать по нему и явно сказать в отчёте.

**DON'T**
1. Не запускать `./gradlew`, ничего не качать (403). Gradle не запускать, если роль не разрешает.
2. Не коммитить и не пушить — это делает оркестратор.
3. Не изобретать имена методов. Не подтвердил после двух grep-ов — §10.
4. Никаких yarn-имён (`MinecraftClient`, `World`, `NbtCompound`, `Text`, `Vec3d`, `DrawContext`,
   `Item.Settings`, `class_XXXX`), никакого `ResourceLocation`, никакого `Identifier.of`.
   Класс — `net.minecraft.resources.Identifier`, фабрика — `Identifier.fromNamespaceAndPath(...)`.
5. Не доверять туториалам до 2026 года и собственной памяти по сигнатурам.
6. Не редактировать `26.1/` и `1.21.1/` — это источники, только чтение.
7. Вопросов пользователю не задавать.

**§10 — правило деградации.** Сопротивляется после ~двух честных попыток → **не блокировать сборку
и не удалять код**, а спуститься по лестнице: (1) отключить строку регистрации → (2) заглушить тело
метода, оригинал оставить рядом в комментарии → (3) функциональная деградация → (4) выкинуть
объект данных. Приоритет: **зелёная сборка важнее полноты фич**; серверный геймплей > клиентская
визуалка > совместимость.

**Каждый срез логируется ТРЕМЯ способами** (требование заказчика — чинить будем потом):
1. В коде: `// TODO(port-26.2): DISABLED — <причина одной строкой>`, оригинал рядом, не удалять.
2. Строка в этом файле → раздел «Disabled content».
3. Строка в `PORT-GAPS.md` — полная таблица с тем, что видно в игре и как чинить.

Финальная сверка оркестратора: `grep -rn "TODO(port-26.2)" src | wc -l` == числу строк в `PORT-GAPS.md`.

## Копилка знаний для порт-кита (требование заказчика)

Всё, чего **не было** в `../porting-26.2/*.md` и что пришлось выяснять самому — новый класс,
переехавший пакет, изменившаяся сигнатура, мёртвый API, рабочий обходной путь — агент пишет
в **свой** файл `../porting-26.2/FINDINGS-<A|B|C|D>.md` в формате:

```
### <Символ или тема>
- **Было (NeoForge 26.1 / старый Fabric):** ...
- **Стало (26.2):** ...
- **Подтверждено:** /opt/mc-src/<путь>:<строка>  ИЛИ  /workspace/<мод>/<файл>:<строка>
- **Комментарий:** грабли, порядок инициализации, что ломается молча
```

Файлы пер-агентные, чтобы четверо не дрались за один документ. Оркестратор в конце сольёт их
в `NOTES-A/B/C.md` и `PORT-MOD-26.2.md` — из этого собирается общий бандл для портов куда угодно.
Дублировать то, что в ките уже есть, не надо: копилка только для нового.

## Маршрут

База — `26.1/` (NeoForge 26.1, Java 25, `Identifier` уже везде). Ванильная ось 1.21.1 → 26.1
пройдена апстримом. Остаётся **одна ось — смена лоадера NeoForge → Fabric** плюс дельта 26.1 → 26.2.
Мод автономен: ноль `[[dependencies]]`, внешних имён было два — JEI и `com.ldtteam.data`.

## Замороженные контракты

- **C1 — форма полей реестра сохраняется.** `DeferredRegister`/`DeferredHolder` → статический
  `register(...)`, возвращающий `Supplier<T>`. Все `.get()` по коду **не трогаются**.
  ```java
  public static <T extends Item> Supplier<T> register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
      ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name));
      T value = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
      return () -> value;
  }
  ```
- **C2 — entrypoints.** `DomumOrnamentum implements ModInitializer` (общий) и
  `DomumOrnamentumClient implements ClientModInitializer` (весь клиент: цвета, модели, экраны,
  превью-рендер, клиентский тик). Оба файла — **только у агента A**.
- **C3 — сеть.** Владелец `network/` — агент B. Ровно два метода: `register()` дёргается из
  общего entrypoint, `registerClient()` — из клиентского. Payload'ы: `CustomPacketPayload` +
  `StreamCodec`, регистрация через `PayloadTypeRegistry.serverboundPlay()/clientboundPlay()`.
- **C4 — capabilities отсутствуют.** В моде их и не было (0 вхождений `IItemHandler`,
  `ItemStackHandler`, `RegisterCapabilities`) — сверять нечего.
- **C5 — события.** 6 `@EventBusSubscriber`/`@SubscribeEvent` удаляются, логика переезжает в
  Fabric-колбэки, регистрируемые из соответствующего entrypoint.
- **C6 — владение общими файлами.** `build.gradle`, `settings.gradle`, `gradle.properties`,
  `fabric.mod.json`, оба entrypoint — **единственный редактор: агент A**.
- **C7 — формат JSON кастомной модели ЗАМОРОЖЕН как есть.** Развязывает агентов C и D:
  ```json
  { "parent": "domum_ornamentum:block/fence/fence_post_spec",
    "loader": "domum_ornamentum:materially_textured" }
  ```
  Агент **C** пишет фабричный лоадер, читающий **ровно эту** форму. Агент **D** генерирует
  **ровно эту** форму. Ни один не ждёт другого, и 747 закоммиченных JSON остаются валидными.
- **C8 — сгенерированный контент уже в ресурсах.** 954 JSON из `26.1/src/datagen/generated`
  скопированы в `src/main/resources`. Мод укомплектован контентом **без запуска датагена**:
  красный `runDatagen` не блокирует зелёную сборку. Для агента D эти файлы — **оракул**:
  выход `runDatagen` диффится против них.

## Ownership — списки файлов, пересечений нет

Пути от `26.2/src/main/java/com/ldtteam/domumornamentum/`.

### Агент A — ядро, сборка, регистрация
`build.gradle`, `settings.gradle`, `gradle.properties`, `src/main/resources/fabric.mod.json`,
`DomumOrnamentum.java`, `DomumOrnamentumClient.java` (новый), `block/ModBlocks.java`,
`block/ModCreativeTabs.java`, `entity/block/ModBlockEntityTypes.java`,
`container/ModContainerTypes.java`, `recipe/ModRecipeTypes.java`,
`recipe/ModRecipeSerializers.java`, `component/ModDataComponents.java`, `tag/ModTags.java`,
`api/DomumOrnamentumAPI.java`, `IDomumOrnamentumApi.java`, `event/handlers/ModBusEventHandler.java`,
раскладка ресурсов. **Gradle: можно, он один в чекауте.**

### Агент B — блоки, предметы, логика, сеть
`block/**` кроме `ModBlocks.java` и `ModCreativeTabs.java` (57 файлов), `item/**` (26),
`entity/block/**` кроме `ModBlockEntityTypes.java` (3), `container/ArchitectsCutterContainer.java`,
`network/**` (3), `recipe/architectscutter/**` (4), `shingles/`, `util/**`, `block/components`,
`block/interfaces`, `item/interfaces`. **Gradle: НЕТ.**

### Агент C — клиент и модели
`client/**` (16 файлов), включая `client/model/**`, `client/render/**`, `client/color/**`,
`client/screens/**`, `client/event/handlers/**`. **Gradle: НЕТ.**

### Агент D — датаген
`datagen/**` (87 файлов) целиком. Датаген-часть `ModBusEventHandler` агент A отдаёт ему
отдельным файлом-энтрипоинтом `datagen/DomumOrnamentumDataGenerator.java`. **Gradle: НЕТ.**

Границы проведены **по файлам, а не по пакетам**: всё `*Model*`/`*Renderer*`/`*Screen*`/`Color*`
принадлежит C, где бы ни лежало; `block/ModBlocks.java` остаётся у A, хотя лежит в зоне B;
`datagen/MateriallyTexturedModelBuilder.java` — у D, но формат он не выбирает, а берёт из C7.

## Checklist

- [x] **Шаг 0 (оркестратор)** — окружение, `/opt/mc-src` (7055 файлов), скелет `26.2/`, срезы, механический проход, эти документы
- [x] **A** — `26.2/` собирается как Fabric-проект; регистрация и оба entrypoint компилятся; `fabric.mod.json` валиден
- [x] **B** — блоки/предметы/блок-сущности/контейнер/рецепты/сеть компилятся (0 ошибок в зоне B при офлайн-`javac`); `net.neoforged.*` и обращений к API NeoForge в зоне B ноль. Точка входа сети — `network/ModNetworking.register()` / `.registerClient()` (контракт C3)
- [x] **C** — клиент компилится (0 ошибок в `client/**` при офлайн-`javac`, кроме `MenuScreens.register has private access` — вот он действительно ложный, метод открыт транзитивным widener'ом fabric-api); вердикт по модельному конвейеру вынесен: **вариант 1 лестницы D6** — Fabric `ModelLoadingPlugin` (`modifyBlockModelAfterBake`) + собственный `BlockStateModel`-враппер поверх FRAPI `emitQuads`. Подробности — `../porting-26.2/FINDINGS-C.md`
- [x] **D** — все 87 генераторов переписаны на Fabric, ноль отключений; `runDatagen` проходит; выход продиффен против оракула: **746 из 746 файлов оракула воспроизведены, ни одного пропавшего**
- [x] **Интеграция** — `compileJava` зелёный на всём дереве (26 с, 20 warning'ов о deprecated, ноль ошибок)
- [x] **Приёмка** — `build` зелёный, `runServer` на чистом мире доходит до `Done (5.604s)!`, **ноль строк `/ERROR]`**

## Contract deviations

_(Любая сигнатура, которую агент был вынужден изменить против контракта. Интегратор читает первым.)_

| # | Кто | Что изменено | Почему |
|---|---|---|---|
| 1 | A | `IDomumOrnamentumApi#getMaterialTextureComponentType()` возвращает **`ModDataComponents.ComponentType<MaterialTextureData>`** вместо `Supplier<DataComponentType<MaterialTextureData>>`. `ModDataComponents.TEXTURE_DATA` того же типа | `ComponentType<D> implements DataComponentType<D>, Supplier<DataComponentType<D>>` (рецепт `NOTES-A §1`). В коде есть **оба** вида обращений: `TEXTURE_DATA.get()` (датаген) и `componentBuilder.set(TEXTURE_DATA, …)` / `itemStack.set(api.getMaterialTextureComponentType(), …)` (блок-сущности, `MaterialTextureData`). NeoForge даёт `Supplier`-перегрузки `ItemStack#get/set/getOrDefault`, ванилла — нет. Двойной интерфейс оставляет все 229 `.get()` и все прямые обращения компилируемыми **без правок в чужих файлах** |
| 2 | A | `ModBlocks.registerCustomBlockItem(String, Supplier<B>, …)` третий параметр стал `BiFunction<B, Item.Properties, ? extends BlockItem>` вместо `Function<B, ? extends BlockItem>` | `Item.Properties` обязан получить `.setId(ResourceKey<Item>)`, а имя предмета известно только методу регистрации. Все вызовы — внутри `ModBlocks` (мой файл), сведены к ссылкам на конструктор `XxxBlockItem::new`; сами классы предметов не тронуты |
| 3 | A | Новые файлы вне исходного дерева: `core/BlockIdContext.java`, `mixin/BlockBehaviourPropertiesMixin.java`, `src/main/resources/domum_ornamentum.mixins.json` | `BlockBehaviour.Properties` обязан нести `ResourceKey<Block>` **до** конструктора `BlockBehaviour` (`/opt/mc-src/.../BlockBehaviour.java:1155,1289`), а все 57 блоков DO строят `Properties` внутри собственных безаргументных конструкторов (13 абстрактных корней). Миксин ставит id из `BlockIdContext` — альтернатива потребовала бы правки 13 чужих файлов. Подробности в `../porting-26.2/FINDINGS-A.md` |

## Disabled content

| Файл | Что отключено | Почему |
|---|---|---|
| `client/../jei/*` (4 файла) | Интеграция с JEI вырезана целиком | Сборки JEI под 26.2 не существует. У апстрима в `port/26.1` блок JEI в `gradle/dependencies.gradle` уже закомментирован — автор сам показал, что сборки нет. Подробности и план возврата — в `PORT-GAPS.md` |
| `client/render/ModelGhostRenderer.java` + `client/event/handlers/MateriallyTexturedBlockPreviewRenderHandler.java` | Полупрозрачное превью ставящегося блока | Immediate-mode буфера в 26.2 нет (`RenderType#draw(MeshData)`, `Tesselator`, `BufferUploader` удалены), драйвером был NeoForge-only `RenderLevelStageEvent`. Косметика, серверный геймплей цел. `PORT-GAPS.md` №2–3 |
| `client/render/ModRenderTypes.java` | 10 кастомных `RenderType` | `RenderStateShard` и `CompositeState.builder()` удалены; единственная фабрика — `RenderType.create(String, RenderSetup)` поверх `RenderPipeline`. Невидимо: потребителем был только `ModelGhostRenderer`. `PORT-GAPS.md` №4 |
| `client/color/MateriallyTexturedBlockItemColor.java` + `client/event/handlers/RegisterColorHandlersEventHandler.java` + `client/color/MateriallyTexturedBlockBlockColor.java` | Регистрация цветовых хендлеров и перетекстурирование **предметов** | `BlockColor`/`ItemColor` удалены из ванилы; tintIndex — теперь индекс в `List<BlockTintSource>`. Для **блоков** потерь нет: тинт применяется при эмиссии квадров. Предметы рисуются базовой моделью. `PORT-GAPS.md` №5, 8, 9 |
| `client/event/handlers/ModBusEventHandler.java` | `ItemProperties.register(...)` (6 шт.) и `ItemBlockRenderTypes.setRenderLayer(...)` (~20 шт.) | Обоих классов в 26.2 нет. Оверрайды моделей предметов и слои рендера стали data-driven → задача датагена (агент D). `PORT-GAPS.md` №6–7 |
| `client/model/geometry/MateriallyTexturedGeometry.java`, `client/model/properties/ModProperties.java`, `client/model/baked/SpecificRenderTypeBakedModelWrapper.java` | Три класса NeoForge-модельного конвейера | Заменены, а не потеряны: bake-time перетекстурирование → render-time (`ModelLoadingPlugin` + FRAPI `emitQuads`), `ModelData` → `RenderDataBlockEntity#getRenderData()`, `ChunkRenderTypeSet` → слой на каждом квадре. `PORT-GAPS.md` №10–12 |
| `block/ModCreativeTabs.java` | `CreativeModeTab.Builder#withTabsBefore(...)` на двух вкладках | Метода нет в 26.2 (`/opt/mc-src/net/minecraft/world/item/CreativeModeTab.java:120-192`); порядок модовых вкладок задаёт `fabric-creative-tab-api-v1`. Оригинал сохранён в комментарии рядом |
| `block/vanilla/*` + `block/decorative/*` (20 файлов, 40 маркеров) | `getExplosionResistance(state, level, pos, explosion)` и `getSoundType(state, level, pos, entity)` по материалу «шкурки» | Обе перегрузки — NeoForge-only. Ваниль 26.2 знает только `Block#getExplosionResistance()` (`Block.java:445`) и `BlockBehaviour#getSoundType(BlockState)` (`BlockBehaviour.java:404`) — позиции нет, блок-сущность не прочитать; во Fabric-хуках (`FabricBlock#getAppearance`) замены нет. Подробности — `PORT-GAPS.md` строки 2-3 «Функциональная деградация» |
| `block/decorative/{DynamicTimberFrame,FramedLight,TimberFrame}Block.java` (3 маркера) | `shouldDisplayFluidOverlay(...)` | `IBlockExtension`-хук NeoForge; в 26.2 нет нигде (`grep -rn shouldDisplayFluidOverlay /opt/mc-src` → 0). `PORT-GAPS.md` строка 4 |
| `block/decorative/DynamicTimberFrameBlock.java` (1 маркер) | `rotate(state, level, pos, rotation)` — поворот блок-сущности вместе с blockstate | Перегрузка с позицией NeoForge-only; ваниль знает только `rotate(BlockState, Rotation)` (`BlockBehaviour.java:255`). `PORT-GAPS.md` строка 5 |
| `item/SelfUpgradingBlockItem.java` + `item/SelfUpgradingDoubleHighBlockItem.java` (2 маркера) | Собственный DFU предметов DO при загрузке стака | Хук `IItemExtension#verifyComponentsAfterLoad` — NeoForge-only, во `FabricItem` его нет. Логика жива в `SelfUpgradingBlockItem.upgrade(ItemStack)`, потеряна только вызывающая сторона. `PORT-GAPS.md` строка 6 |
| `util/SingleBlockLevelReader.java` (1 маркер) | Делегирование `LevelReader#getShade(Direction, boolean)` | Метода в 26.2 на `LevelReader` нет; в моде его никто не звал. `PORT-GAPS.md` строка 7 |

## Verification

_(Заполняется по мере прогона. Что осталось непроверенным — писать честно.)_

| Задача | Статус |
|---|---|
| Конфигурация Gradle + резолв зависимостей | ✅ проходит (Loom 1.17.13, MC 26.2, loader 0.19.3, fabric-api 0.154.2+26.2, `sponge-mixin` и `mixinextras-fabric` подтягиваются транзитивно). Только **онлайн**: `--offline` падает, этих двух артефактов нет в кэше |
| `compileJava` — файлы агента A | ✅ ноль ошибок во всех 16 файлах, кроме одной внешней зависимости: `DomumOrnamentumClient` ждёт `client/ClientRegistrations.register()` от агента C |
| `compileJava` (весь проект) | ✅ **зелёный.** 26 с, ноль ошибок, 20 warning'ов о deprecated (внутримодовые `BlockItemWithClientBePlacement` и т.п.). Промежуточный замер «2448 ошибок» относился к моменту, когда B/C/D ещё работали |
| `compileJava` — файлы агента D (`datagen/**`, 98 файлов) | ✅ ноль ошибок. Проверено `javac`-прогоном по всему `src/main/java` (без миксинов) против **проектного** AW-джарника `26.2/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-043a8b3edf/26.2/…jar`. Важно: против `~/.gradle/caches/fabric-loom/…-deobf-….jar` та же проверка даёт «has private access» на `BlockModelGenerators.modelOutput/blockStateOutput/itemModelOutput`. **Это были не ложные срабатывания:** classtweaker fabric-api их не открывает (проверено грепом по `fabric-transitive-access-wideners-v1.classtweaker` — записей на `BlockModelGenerators` там нет), их открывал только AW-джарник Loom'а, которого в рантайме нет. Закрыто собственным `src/main/resources/domum_ornamentum.accesswidener` |
| `build` | ✅ **зелёный.** Миксин `BlockBehaviourPropertiesMixin` применяется, jar собирается: `build/libs/domum_ornamentum-26.2-1.0.0.jar`, 1.1 МБ, 289 классов + 1051 JSON + 57 PNG |
| `runDatagen` | ❌ прогон оркестратора упал на валидации тегов (`Couldn't define tag domum_ornamentum:default as it is missing following references: #c:end_stones, #minecraft:logs, …`). **Починено агентом D:** ссылки на чужие теги идут через Fabric-овский `forceAddTag` (`datagen/utils/{Block,Item}TagAppender#addTag`, правило по неймспейсу). JSON на выходе не меняется — `ForcedTagEntry` остаётся `required: true` и сериализуется голой строкой `"#minecraft:logs"`. Требуется повторный прогон |
| `runDatagen` (повторный прогон после фикса) | ✅ **зелёный.** 841 файл записан за 535 мс. Диff против оракула: **0 файлов пропало**, 533 совпали байт-в-байт, 213 разошлись ожидаемо (плоские ингредиенты в 56 рецептах, дефолты `bonus_rolls`/`random_sequence`/`count` опущены в 98 лут-таблицах, `0` → `0.0` в 46 моделях предметов, нормализация поворотов в 9 blockstate'ах), **+95 новых `assets/domum_ornamentum/items/*.json`** — закрывают дыру, которой не было и в апстримовском оракуле |
| Клиент, инвентарь (заказчик, живой запуск) | ❌→🔧 предметы 6 типов с вариантами — `panel`, `post`, `vanilla_trapdoors_compat`, `fancy_trapdoors`, `vanilla_doors_compat`, `fancy_door` — рисовались **пустым слотом** (тултип есть, геометрии нет); `architectscutter` и оба барреля — «missing model». Причина: их item-модель воспроизводила мёртвый с 1.21.4 `"overrides"` поверх родителя без геометрии. **Починено агентом D:** `items/<name>.json` теперь `minecraft:select` по `minecraft:block_state` (`block_state_property: "type"`), геометрия варианта — в новых `models/item/<name>_<type>.json` (44 файла) с DO-шными display-трансформами; три рукописных блока получают определение из датагена. Требуется повторный `runDatagen` + осмотр в клиенте |
| `runServer` | ✅ **зелёный.** Чистый мир, `Done (5.604s)! For help, type "help"`, **ноль строк `/ERROR]`**, мод в списке (`domum_ornamentum 1.0.0`), миксины на `JAVA_25`. Ошибки `Block id not set` нет — значит миксин отработал и все 57 блоков зарегистрировались |

**Что эта приёмка принципиально не покрывает:** клиент. В контейнере нет дисплея — модели,
рендереры, экраны и цветовые хендлеры никогда не исполнялись. Для `client/**` установлена
**только чистая компиляция**, и выдавать это за проверенную работоспособность нельзя.
Первая же вещь, которую надо проверить на живом клиенте, — перетекстурирование размещённых
блоков (агент C прошёл по первой ступени D6, но подтверждения запуском у него не было).

**Единственный `/ERROR]`, который встречался, — не мода:** `Failed to request yggdrasil public key`
в прогоне с уже существующим миром. Это контейнер без доступа к серверам авторизации Mojang.
На чистом мире не воспроизвёлся.

**Разделение источников (выполнено оркестратором после зелёного датагена).** 746 файлов,
которые датаген воспроизводит, удалены из `src/main/resources` — иначе `processResources`
падает с `Entry … is a duplicate`, потому что `configureDataGeneration` монтирует
`src/main/generated` вторым resource-root'ом. В `resources` осталось 267 рукописных файлов
(198 `_spec`-моделей, 57 текстур, 3 blockstate'а, `fabric.mod.json`, конфиг миксинов).
Сгенерированный пак закоммичен в `src/main/generated` — ровно та практика, что в референсе
`/workspace/desolation`. Список удалённого — `DATAGEN-OWNED-FILES.txt`, восстановление
`git checkout -- src/main/generated`.
