# Контентные изменения 26.3 — что затрагивает мод

`API-CHECKLIST-26.3.md` отвечает на вопрос «что не скомпилируется». Этот файл — про другое:
ваниль добавила и переделала **контент**, и мод от этого не ломается, а начинает вести себя
неправильно или недосчитывать нового.

Список пришёл из патчноутов; **каждый пункт ниже сверен по `/opt/mc-src-26.3` и `javap` по
jar-ам обеих версий**, потому что патчноуты называют вещи по-человечески, а моду нужны
идентификаторы. Где сверка разошлась со списком — это отмечено.

> **Осторожно с деревом сверки.** `/opt/mc-src-26.3` — это **snapshot-9**
> (`SharedConstants.WORLD_VERSION = 5011`), а мод собирается против **snapshot-10** (5015).
> Часть имён между этими снапшотами переехала, и первая редакция файла унаследовала имена
> snapshot-9 — см. §2, §4 и §5. Перепроверено по байткоду, который резолвит сборка:
> `~/.gradle/caches/fabric-loom/26.3-snapshot-10/minecraft-merged.jar`. Любой вопрос
> «существует ли и с какой сигнатурой» решать `unzip -l`/`javap -p` по этому jar-у, а дерево
> snapshot-9 использовать только для чтения логики.

---

## 1. Соломенная кровать — и почему она молчаливо сломает расселение ⬜

**Это самый дорогой пункт файла.** Он не даёт ни одной ошибки компиляции.

*Терминология, потому что она путается: кровать **соломенная**, а не «сломанная».* Блок
называется `Blocks.STRAW_BED`, класс `StrawBedBlock` — то есть кровать из соломы. Одноразовой
её делает поведение, а не состояние: `destroyOnUse` и `destroyOnLeave` уничтожают её после
использования. «Сломанная» — это слипшееся «соломенная» плюс «саморазрушается». Но важно не это, а перестроенная иерархия:

| | 26.2 | 26.3 |
|---|---|---|
| базовый класс | **нет** | `AbstractBedBlock extends HorizontalDirectionalBlock` |
| обычная кровать | `BedBlock` | `BedBlock extends AbstractBedBlock` |
| соломенная | — | `StrawBedBlock extends AbstractBedBlock` — **сестра `BedBlock`, не наследник** |
| `PART`, `OCCUPIED`, `FACING` | на `BedBlock` | подняты на `AbstractBedBlock` |

Проверено: в 26.2 класса `AbstractBedBlock` нет вообще (`javap` → `class not found`).

Мод определяет кровати **по классу, а не по тегу**: 29 площадок с `BedBlock`, ни одного
использования `BlockTags.BEDS`. Ключевая — `BedHandlingModule:62`:

```java
if (blockState.getBlock() instanceof BedBlock)          // компилируется в 26.3
{
    if (blockState.getValue(BedBlock.PART) == BedPart.FOOT)   // тоже компилируется
```

`BedBlock` никуда не делся, `BedBlock.PART` наследуется от `AbstractBedBlock` и доступен по
старому имени. **Всё соберётся зелёным — и соломенные кровати просто не будут считаться
кроватями.** Житель их не займёт, здание не зарегистрирует.

Что делать: `instanceof AbstractBedBlock`, либо (лучше) перевести распознавание на тег
`BlockTags.BEDS`, заодно подобрав кровати из других модов. Решить надо и вопрос смысла:
**нужна ли колонии одноразовая кровать вообще** — житель ляжет и разрушит её. Возможно,
правильный ответ — явно исключить `StrawBedBlock` из пригодных, но осознанно, а не потому
что `instanceof` промахнулся.

---

## 2. Тополь — полный набор, и он шире, чем в патчноутах ⬜

Проверено по `Blocks.java`. В списке было «лог, стрипнутый лог, wood, доски, плита, ступени,
дверь, люк, забор, калитка, табличка + саженец + листья 3 цветов». На деле набор больше:

```
POPLAR_LOG            STRIPPED_POPLAR_LOG      POPLAR_PLANKS     POPLAR_SLAB
POPLAR_WOOD           STRIPPED_POPLAR_WOOD     POPLAR_STAIRS     POPLAR_FENCE
POPLAR_DOOR           POPLAR_TRAPDOOR          POPLAR_FENCE_GATE POPLAR_BUTTON
POPLAR_PRESSURE_PLATE POPLAR_SIGN              POPLAR_WALL_SIGN
POPLAR_HANGING_SIGN   POPLAR_WALL_HANGING_SIGN POPLAR_SHELF      POPLAR_SAPLING
```

**Листьев ровно три блока:** `ORANGE_POPLAR_LEAVES`, `RED_POPLAR_LEAVES`,
`YELLOW_POPLAR_LEAVES` — бесцветного `POPLAR_LEAVES` в `Blocks` нет ни в snapshot-9, ни в
snapshot-10 (проверено `javap` по `Blocks` и по `BlockItemIds`: три поля, три id). Первая
редакция насчитала четыре и заодно записала в блоки `POPLAR_LEAVES_AMBIENT` — это не блок, а
звук, `SoundEvents.POPLAR_LEAVES_AMBIENT`, который вешается на все три вида листьев через
`AmbientLeavesBlockSoundPlayer` и тег `BlockTags.REQUIRED_FOR_POPLAR_LEAF_AMBIENCE`.

**`POPLAR_SHELF`** — в патчноутах его не было, и это не тополиная особенность: полки
(`*_SHELF`) появились у всех пород дерева, включая `OAK_SHELF`, `BAMBOO_SHELF` и остальные.
Это новый тип блока сам по себе, отдельная строка работы.

### Что от этого требуется моду

* **Лесоруб — бесплатно на уровне тегов.** Мод ищет древесину через `BlockTags.LOGS` /
  `ItemTags.LOGS`, а не по списку пород (единственный хардкод — `Items.OAK_LOG` в
  `DefaultMechanicCraftingProvider:45`, и он про конкретный рецепт, не про распознавание).
  Тополь попадёт в теги ванилью. **Но** алгоритм обхода дерева у лесоруба свой, и три
  разных вида листьев на одной кроне он раньше не встречал — проверить, что срубание считает
  крону целиком. ⬜
* **Лесопилка — руками.** `DefaultSawmillCraftingProvider` перечисляет рецепты; тополь
  туда надо добавить явно, вместе с новыми `SHELF` у всех пород. ⬜
* **Саженец в питомнике/лесничестве** — проверить, что `POPLAR_SAPLING` подхватывается. ⬜

---

## 3. Шерсть и бетон: ступени и плиты — 16 блоков, как и было ✅ разобрано

**В первой редакции этот пункт был неверен, и ошибка была моя.** Здесь стояло «не 16 блоков, а
один блок с цветом в состоянии». Это неправда, и рассуждение, построенное на этом, тоже.

Как я ошибся: `javap` показывает у `Blocks` **одно** поле `WOOL_STAIRS`, и я прочитал это как
«один блок». Смотреть надо было на **тип** поля, а не на имя:

```java
public static final ColorCollection<Block> WOOL_STAIRS;
```

`ColorCollection<T>` — это record с **шестнадцатью** типизированными полями
(`white, orange, magenta, lightBlue, …, black`), то есть контейнер на 16 отдельных блоков, а не
блок с цветовым свойством. Каждый регистрируется отдельно:
`BlockItemIds.createSimpleColored("wool_stairs")` разворачивается в `white_wool_stairs` …
`black_wool_stairs`.

Проверено тремя способами: в jar лежат **16 отдельных** `assets/minecraft/blockstates/*_wool_stairs.json`;
в `red_wool_stairs.json` варианты идут только по `facing/half/shape/waterlogged` — **свойства
цвета там нет**; на каждое семейство приходится по 16 файлов рецептов.

И `WOOL`/`CONCRETE` были `ColorCollection` **уже в 26.2** — то есть никакого «схлопывания» не
происходило вообще, ни тогда, ни в 26.3. Изменилась только форма Java-аксессора, и изменилась
она раньше.

| | 26.2 | 26.3 |
|---|---|---|
| шерсть | `ColorCollection<Block> WOOL` | + **`WOOL_SLAB`**, **`WOOL_STAIRS`** (тоже по 16) |
| бетон | `ColorCollection<Block> CONCRETE`, `CONCRETE_POWDER` | + **`CONCRETE_SLAB`**, **`CONCRETE_STAIRS`** |

**Следствие: сравнение по `Block` различает все 16 цветов, и поломки, вокруг которой был
построен этот пункт, существовать не может.** Проверено по коду: палитра блюпринта — это
`List<BlockState>` с дедупликацией по `equals`, `getRequiredItems` доходит до
`Item.BY_BLOCK.get(block)`, склад матчит по идентичности предмета. Цвет переживает каждый шаг.
`BlockStateStorage` вообще мёртвый код — ноль обращений и в 26.3, и в 26.2.

### Что действительно требовалось — и это нашлось по другому следу ✅

Настоящий пробел был не в сравнении блоков, а в **рукописных списках материалов**:
`data/minecolonies/tags/block/tier{1,2}blocks.json` перечисляют варианты шерсти и бетона
поимённо и про 64 новых блока не знали. Из-за этого все они получали **tier 0** в
`SchemAnalyzerUtil`, а это не косметика: `costScore` идёт в престиж постройки
(`AbstractBuilding:1138`) и в подсказку в интерфейсе (`ClientEventHandler:194`).

Дописано по образцу самого файла — ступени и плиты наследуют тир базового блока, ровно как уже
сделано для `polished_deepslate`.

### Камнерез — делать нечего ✅

Мод **не моделирует камнерез вообще**: `ModCraftingTypesInitializer` знает только `CRAFTING`,
`SMELTING`, `BREWING` и `ARCHITECTS_CUTTER`. Бетонные ступени при этом достижимы обычным
`crafting_shaped`, а рецепт резчика — лишь альтернатива. Пункт закрыт.

### Побочная находка: каменщику молча досталась шерсть ⬜

`DefaultItemTagsProvider:538,568` кладёт `BlockItemTags.STAIRS`/`SLABS` в теги ингредиентов и
продуктов каменщика, а ваниль 26.3 дописала в эти теги `#minecraft:wool_stairs` и
`#wool_slabs`. Значит **каменщика теперь можно обучить рецептам шерстяных ступеней**, хотя
простая шерсть ему намеренно не выдаётся (`ItemTags.WOOL` есть только у фletcher'а). Это
привнесённая ванилью несогласованность, а не замысел. Лечится добавлением
`WOOL_STAIRS`/`WOOL_SLABS` в исключения каменщика; файл в `generation/`.

---

## 4. Worldgen: мода не касается, Structurize касается ⬜

* **`DensityFunction` переехал и сменил точность.** Проверено:
  `net.minecraft.world.level.levelgen.DensityFunction` →
  `net.minecraft.world.level.levelgen.densityfunction.DensityFunction`. Патчноуты про
  single-precision верны.

  **Поправка по snapshot-10.** Первая редакция писала «`double compute(...)` →
  `float compute(...)`» — это состояние snapshot-9. В snapshot-10 метода `compute` у
  `DensityFunction` нет вообще, как нет и `DensityFunction.FunctionContext`: функция теперь
  сначала компилируется — `compileSampler(DensityFunction.CompileContext)` → `DensitySampler` —
  и уже сэмплер считает, либо поточечно `float sampleValue(SamplerContext, int, int, int)`,
  либо пачкой `void sampleVolume(SamplerContext, DensityBuffer, DensityVolume)`. Точность
  осталась одинарной; изменилась именно форма вычисления. Проверено `javap -p` по
  snapshot-10 jar-у.
* **Захардкоженные feature types убраны в пользу конфигурируемых.**

**Мод генерацией не пользуется вообще:** grep по `26.2/src/main/java` даёт **0** попаданий
на `DensityFunction`, `NoiseGeneratorSettings`, `SurfaceRules` и ни одного `ConfiguredFeature`/
`PlacedFeature`. Так что для MineColonies этот раздел пустой.

**А вот Structurize задет** — у него в списке ошибок стоит `NoiseGeneratorSettings.surfaceRule()`.
Это его зона, отдельной строкой в его отчёте.

---

## 5. Карты исследователя — отдельные предметы ⬜

Подтверждено: вместо переименованного `filled_map` теперь самостоятельные предметы, и их много:

```
ABANDONED_CAMP_MAP         BURIED_ANCIENT_CITY_MAP    BURIED_MINESHAFT_MAP
BURIED_TREASURE_MAP        BURIED_TRIAL_CHAMBERS_MAP  DESERT_PYRAMID_MAP
DESERT_VILLAGE_MAP         JUNGLE_PYRAMID_MAP         OCEAN_MONUMENT_MAP
PLAINS_VILLAGE_MAP         SAVANNA_VILLAGE_MAP        SNOWY_VILLAGE_MAP
SWAMP_HUT_MAP              TAIGA_VILLAGE_MAP          WARM_OCEAN_RUINS_MAP
WOODLAND_MANSION_MAP
```

**Пять имён в первой редакции были из snapshot-9 — snapshot-10 их переименовал.** Карта
теперь называется по тому, что на ней, а не «карта исследователя»:

| snapshot-9 | snapshot-10 | id для датапаков |
|---|---|---|
| `JUNGLE_EXPLORER_MAP` | `JUNGLE_PYRAMID_MAP` | `jungle_pyramid_map` |
| `OCEAN_EXPLORER_MAP` | `OCEAN_MONUMENT_MAP` | `ocean_monument_map` |
| `SWAMP_EXPLORER_MAP` | `SWAMP_HUT_MAP` | `swamp_hut_map` |
| `TRIAL_EXPLORER_MAP` | `BURIED_TRIAL_CHAMBERS_MAP` | `buried_trial_chambers_map` |
| `WOODLAND_EXPLORER_MAP` | `WOODLAND_MANSION_MAP` | `woodland_mansion_map` |

Остальные одиннадцать имён совпадают в обоих снапшотах. Проверено `javap -p` по
`Items`/`BlockItemIds` и по строкам id в `references.ItemIds` snapshot-10 jar-а. Кода мода это
не касается — он не упоминает ни одного из этих предметов, только `FILLED_MAP`.

`FILLED_MAP` при этом остался. Зум работает только для тега `ItemTags.EXTENDABLE_MAPS`
(`ItemTags.java:164`, подтверждено — тег есть и в snapshot-10).

Что проверить в моде:

* **`ItemNbtCalculator:116`** явно кладёт `Items.FILLED_MAP.getDefaultInstance()` — компилируется,
  но теперь описывает не то множество предметов, что раньше. ⬜
* **Лут и торговля картографа** — если мод раздаёт или принимает карты, датапаки должны
  сменить предмет. ⬜
* **`ITownHallView.MapEntry`** держит `MapItemSavedData` — проверить, что карты колонии
  переживают смену. ⬜

---

## Как это соотносится с остальными документами

| | про что |
|---|---|
| `API-CHECKLIST-26.3.md` | что не скомпилируется (292 ошибки, оси A–C) + ось D, молчаливые поломки |
| **этот файл** | что скомпилируется, но станет вести себя не так, потому что изменился контент |
| `PORT-PLAN-26.3.md` | порядок работ и объёмы |

Пункт 1 (кровати) по своей природе относится к **оси D** чеклиста — компилируется, работает
неправильно — и продублирован там ссылкой.

Ни один пункт этого файла не входит в 292 ошибки компилятора и не будет найден сборкой.
Всё здесь проверяется только чтением кода и, когда появится способ, игрой.
