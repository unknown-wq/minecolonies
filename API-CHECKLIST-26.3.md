# Карта изменений API — чеклист порта 26.2 → 26.3-snapshot-9

В отличие от `API-CHECKLIST-26.2.md`, который собран из внешних гайдов и потом проверен grep-ом,
этот список **получен из компилятора**: неизменённое дерево `26.2/src/main/java` (2176 файлов)
собрано против 26.3-snapshot-9, и каждая строка ниже стоит на конкретной ошибке javac.

Порт идёт **по одной оси** — только ваниль. Лоадер тот же (Fabric), Java та же (25),
обфускации по-прежнему нет.

| | |
|---|---|
| Целевая версия | `26.3-snapshot-9`, выпущена 2026-08-17 (последняя в `version_manifest_v2.json`) |
| Loom | `1.17.19` (в 26.2 — 1.17.13) |
| fabric-loader | `0.19.3` — **не менялся** |
| fabric-api | `0.158.0+26.3` (в 26.2 — 0.154.2+26.2); `depends.minecraft` = `~26.3-` |
| Ошибок компиляции | **303** по счёту javac → **292** уникальных площадок в **125 файлах** после вычета шума пробника |
| Ванильных классов | 7055 → 7201 (249 удалено, 395 добавлено) |

Разбивка по слоям: **231** ошибка в рантайме/клиенте/api, **61** в датагене (`**/generation/**`).

**Как пользоваться:** пункт → grep из строки → правишь → галочка. Ни одну новую сигнатуру не писать
по памяти: сверяться с `/opt/mc-src-26.3` (7201 файл, `genSources` Vineflower).

Категории местами пересекаются: один корень даёт два разных сообщения javac (у блоков — и
«cannot find symbol: simpleCodec», и «does not override» на `codec()`).

---

## Ось A — крупные переделки (не переименования)

Здесь новая сигнатура не выводится из старой; нужно решение, а не `sed`.

### A1. Анимация удара: `swing(InteractionHand)` больше нет — 55 ошибок, 38 файлов ⬜

Самая массовая поломка. По `javap` обеих версий:

```java
// 26.2
public void swing(InteractionHand)
public void swing(InteractionHand, boolean)
protected void updateSwingTime()
public boolean swinging;  public InteractionHand swingingArm;  public int swingTime;

// 26.3 -- обе перегрузки, метод и все три поля удалены, осталось:
public boolean swing(InteractionHand, SwingAnimation, boolean)
public void swingAndResetAttackStrength(InteractionHand, SwingAnimation, boolean)
```

`SwingAnimation` — новый компонент предмета, `net.minecraft.world.item.component.SwingAnimation`,
`record SwingAnimation(SwingAnimationType type, int duration)`. То есть анимация теперь берётся
от предмета в руке, а не подразумевается.

Решение принимать один раз на весь мод: какой `SwingAnimation` подставляют рабочие, когда машут
инструментом. Механически заменить нельзя — у метода поменялась и арность, и смысл.

```sh
grep -rn "\.swing(\|updateSwingTime" 26.2/src/main/java | wc -l
```

### A2. Варка зелий стала рецептом — 9 ошибок, 3 файла ⬜

Класс `PotionBrewing` и аксессор `Level.potionBrewing()` **удалены целиком**. Вместо
захардкоженного реестра — обычные рецепты: `BrewingRecipe`, `BrewingInput`,
`BrewingRecipeBuilder`, `BrewingProvider`, `VanillaBrewingProvider`, компонент `BrewingFuel`.

Задеты `core/recipes/BrewingCraftingType.java` и `api/inventory/container/ContainerCraftingBrewingstand.java` —
их надо переписать на `RecipeManager`, а не подпереть.

### A3. Рендер: `com.mojang.blaze3d.pipeline` → `com.mojang.renderpearl` — 9 ошибок, 2 файла ⬜

`RenderPipeline`, `BlendFunction`, `ColorTargetState`, `DepthStencilState` уехали в новую
библиотеку: `com/mojang/renderpearl/api/pipeline/RenderPipeline.java`. В `blaze3d/pipeline`
остались только `RenderTarget`/`TextureTarget`/`MainTarget`/`PipelineCache`.

Основной пострадавший — `core/client/render/worldevent/RenderTypes.java` (9 ошибок). Это не
переименование пакета: библиотека другая, API надо читать заново.

### A4. GLFW → SDL — 3 ошибки, 2 файла ⬜

26.3 выкинул из зависимостей `lwjgl-glfw` и `lwjgl-tinyfd`, добавил `lwjgl-sdl` (весь LWJGL
3.4.1 → 3.4.2). Поэтому:

* `org.lwjgl.glfw.GLFW` недоступен — `core/client/gui/modules/building/WindowListRecipes.java`;
* `InputConstants.Type` (`KEYSYM`/`SCANCODE`) больше нет — `api/client/ModKeyMappings.java`.
  В `InputConstants` теперь плоские int-константы (`MOUSE_BUTTON_LEFT` = 1 и т. д.).

Кейбинды мода надо переводить на новую модель ввода.

### A5. `ContextAwarePredicate` удалён — 32 ошибки, 16 файлов ⬜

В 26.2 мод уже жил на `net.minecraft.advancements.predicates` (пакет переехал раньше). В 26.3
из него **удалён сам класс** `ContextAwarePredicate` — grep по всему дереву ванили не даёт ни
одного попадания.

Ровно те же 16 файлов ломает и следующий пункт: это все `api/advancements/*Trigger.java`.

### A6. `EntityPredicate.ADVANCEMENT_CODEC` удалён — 16 ошибок, 16 файлов ⬜

Поле заменено на набор `wrap(...)`:

```java
// net/minecraft/advancements/predicates/entity/EntityPredicate.java:68-80
public static Holder<LootItemCondition> wrap(EntityPredicate.Builder singlePredicate)
public static Optional<Holder<LootItemCondition>> wrap(Optional<EntityPredicate> singlePredicate)
public static List<Holder<LootItemCondition>> wrap(EntityPredicate.Builder... predicates)
```

Триггеры мода строятся на `ADVANCEMENT_CODEC` в каждом `*TriggerInstance` — 16 файлов
переписываются одинаково, но руками: тип результата поменялся на `Holder<LootItemCondition>`.

---

## Ось B — переименования и переезды (механические)

### B1. Loot: классы переименованы — 21 ошибка, 5 файлов ⬜

| 26.2 | 26.3 |
|---|---|
| `entries.LootPoolSingletonContainer` | разъехался: класс → `SingleEntryContainerBase`, **`Builder` → `UniformContainerBase.Builder`** |
| `predicates.LootItemBlockStatePropertyCondition` | `predicates.MatchBlock` |

**Про `Builder` — это не придирка, а место, где легко потерять час.** Иерархия в 26.3:
`SingleEntryContainerBase extends UniformContainerBase extends LootPoolEntryContainer`, и
вложенный `Builder` объявлен в `UniformContainerBase`, а не в `SingleEntryContainerBase`.
`LootItem.lootTableItem()` возвращает именно `UniformContainerBase.Builder<?>`
(`entries/LootItem.java:42`). Кто напишет `SingleEntryContainerBase.Builder`, получит
«cannot find symbol» и пойдёт искать несуществующую проблему.

Условие тоже не просто переименовано: `LootItemBlockStatePropertyCondition.hasBlockStateProperties(b).setProperties(p)`
→ `MatchBlock.blockMatches(blocks, b, p)`.

Пакет `entries` пересобран целиком: появились `ExpandableContainerBase`, `UniformContainerBase`,
`SingleEntryContainerBase`, `SlotLoot`. Сверяться с `/opt/mc-src-26.3/net/minecraft/world/level/storage/loot/`.

*Найдено при порте Domum Ornamentum, где эти классы встретились первыми; проверено по
исходникам ванили. В первой редакции здесь стояло просто «→ `SingleEntryContainerBase`».*

### B2. `LootTable`: `ResourceKey` → `Holder` — 10 ошибок, 3 файла ⬜

API теперь принимает `Holder<LootTable>` там, где брал `ResourceKey<LootTable>`.
`core/event/EventHandler.java`, `DefaultFishermanLootProvider`, `DefaultBlockLootTableProvider`.

### B3. Кодеки блоков удалены — 7 файлов, по 2 ошибки каждый ⬜

`Block.simpleCodec(...)`, `propertiesCodec()` и `MapCodec` в `Block`/`BlockBehaviour` **исчезли
полностью** (0 попаданий `MapCodec` в обоих файлах). Ваниль объявляет блок без кодека:

```java
public class BarrelBlock extends BaseEntityBlock {
	public BarrelBlock(final BlockBehaviour.Properties properties) { ... }
```

Это работа на удаление: убрать поле `CODEC` и `@Override codec()`. Полный список файлов —
`BlockBarrel`, `BlockDecorationController`, `BlockPlantationField`, `BlockColonyFlagBanner`,
`BlockColonyFlagWallBanner`, `BlockConstructionTape`, `client/gui/containers/WindowBrewingstandCrafting`.

### B4. `new BlockPos(BlockPos)` — копирующего конструктора нет — 12 ошибок, 8 файлов ⬜

Остался только `BlockPos(int, int, int)`. `BlockPos` иммутабелен, так что копия почти везде
просто не нужна: `new BlockPos(start)` → `start`. Проверять по месту — часть вызовов
защищалась от мутабельного `BlockPos.MutableBlockPos`, там нужен `.immutable()`.

Больше всего в `core/entity/pathfinding/pathjobs/AbstractPathJob.java` (5).

### B5. `BlockState.blocksMotion()` удалён — 12 ошибок, 6 файлов ✅ решено

Проверено `javap` по обеим версиям: в 26.2 у `BlockBehaviour$BlockStateBase` есть
`blocksMotion`, в 26.3 — нет; `isSolid`, `isSolidRender`, `canOcclude` остались.

**`isSolid()` — неверная замена.** В первой редакции здесь стоял «кандидат `isSolid()`, сверять
по месту». Разбор тела 26.2 через `javap -c` показал, что это не приближение, а другая функция:

```java
blocksMotion() == block != Blocks.COBWEB && block != Blocks.BAMBOO_SAPLING && isSolid()
```

Оба исключённых блока объявлены с `forceSolidOn()` (`Blocks.java:732`, `:4138`), а `isSolid()`
в 26.3 возвращает `legacySolid`, который `forceSolidOn` включает безусловно
(`BlockBehaviour.java:483-489`). То есть при механической замене **паутина и бамбуковый росток
становятся стеной для пасфайндинга, оставаясь проходимыми физически** — застревающий ИИ ровно
того сорта, которого этот пункт и опасался.

Решение: хелпер `PathfindingUtils.blocksMotion(BlockState)`, воспроизводящий тело 26.2 буква
в букву. Все 7 площадок мода переведены на него (площадок оказалось 7, а не 6 файлов, как
считал первый прогон). Наблюдаемая разница была бы на пяти из семи — в том числе рельсовые
рёбра обрубались бы паутиной над путём, а шахтёр считал бы паутину надёжной стеной и не
закладывал бы дыру.

*Найдено и доказано агентом зоны сущностей; проверено оркестратором по байткоду 26.2.*

### B6. `FriendlyByteBuf`: коллекционные хелперы удалены — 16 ошибок, 6 файлов ⬜

`writeCollection` / `readList` / `readMap` / `writeMap` у `RegistryFriendlyByteBuf` больше нет.
Переводить на `ByteBufCodecs` (`ByteBufCodecs.collection`, `.map`).

### B7. `SignText` / `SignTextSlot` — 9 ошибок, 3 файла ⬜

* `SignBlockEntity.setText(text, boolean front)` → второй параметр теперь `SignTextSlot`
  (`net/minecraft/world/level/block/entity/SignTextSlot.java`).
* `SignText.setMessage(int, Component)` больше нет — `SignText` иммутабелен, есть `withGlowingText`
  и конструктор `SignText(List<Component>, List<Component>, DyeColor, boolean)`.

`TileEntityColonyBuilding.java`, `core/util/WorkerUtil.java`.

### B8. `LivingEntity.drop` — новый параметр `Prediction` — 6 ошибок, 4 файла ⬜

```java
// LivingEntity.java:777
public @Nullable ItemEntity drop(ItemStack itemStack, boolean thrownFromHand, Prediction prediction)
```

`net.minecraft.util.Prediction` — новый класс (клиентское предсказание действия).

### B9. `PushReaction` — переименован весь enum — 3 ошибки, 3 файла ⬜

| 26.2 | 26.3 |
|---|---|
| `NORMAL` | `PUSH_PULL` |
| `PUSH_ONLY` | `PUSH` |
| `DESTROY` | `POPPED` |
| `BLOCK` | `IMMOVEABLE` |
| `IGNORE` | `IGNORE_ENTITY` |

**Мапить по ordinal нельзя**: порядок объявления переставлен (в 26.2 — `NORMAL, DESTROY, BLOCK,
IGNORE, PUSH_ONLY`, в 26.3 — `PUSH_PULL, PUSH, POPPED, IMMOVEABLE, IGNORE_ENTITY`), так что
`DESTROY` и `PUSH` совпадают по номеру и различаются по смыслу. Соответствие выше — по семантике.

Тот же enum ломает и Domum Ornamentum (`PUSH_ONLY`, 3 ссылки).

### B10. `RegistryCodecs` переехал — 1 ошибка ⬜

`net.minecraft.core.RegistryCodecs` → `net.minecraft.core.registries.codec.RegistryCodecs`.

### B11. Классы инструментов удалены — 1+ ошибка ⬜

`net.minecraft.world.item.AxeItem` (и соседи `PickaxeItem`/`SwordItem`/`HoeItem`) из пакета
исчезли — завершение перевода инструментов на дата-компоненты. `core/entity/ai/workers/guard/MeleeCombatAI.java`
определяет топор по классу; надо по компоненту/тегу.

### B12. `Level.fuelValues()` удалён — 1 ошибка ⬜

Класса `FuelValues` в дереве нет. `api/compatibility/CompatibilityManager.java:809` определяет
топливо через него.

### B13. Прочие одиночные ⬜

| Файл | Что |
|---|---|
| `core/colony/VisitorDataView.java:76` | `com.mojang.authlib.yggdrasil.ProfileResult` недоступен |
| `api/util/WorldUtil.java:431` | `getVisibilityPercent(Entity)` → `getVisibilityPercent(ServerLevel, Entity)` |
| `core/client/render/TileEntityColonyFlagRenderer.java:195` | `BannerRenderer.submitPatterns` — другая сигнатура |
| `core/colony/buildings/workerbuildings/BuildingLumberjack.java:300-306` | `BonemealableBlock.isValidBonemealTarget` / `isBonemealSuccess` / `performBonemeal` — другие сигнатуры |
| `core/entity/other/NewBobberEntity.java:72,165` | `InterpolationHandler` стал абстрактным; `getInterpolation()` больше не переопределяется |
| `core/entity/ai/workers/production/EntityAIWorkNether.java:419` | `Entity.invulnerableTime` приватно → в accesswidener |
| `core/items/ItemScepterPermission.java:176` | `BlockPos.withinManhattan` — другая сигнатура |
| `MineColonies.java:71` | `net.fabricmc.fabric.api.registry.CompostableRegistry` — переехал в fabric-api 0.158 |

---

## Ось C — датаген (61 ошибка) ⬜

Отдельная ось, потому что чинится последней: без `runDatagen` не соберётся jar, но и датаген
не запустить, пока не компилируется рантайм.

* **`Provider` → `Context`** — `HolderLookup.Provider` в провайдерах заменён на контекст
  (`DefaultEntityLootProvider`, `DefaultRecipeProvider:87` ждёт `BootstrapContext<Recipe<?>>`).
* **«does not override»** в 8 провайдерах (`DefaultBlockLootTableProvider`, `DefaultCropsLootProvider`,
  `DefaultLuckyOreLootProvider`, `DefaultRecipeProvider`, `DefaultSupplyLootProvider`,
  `DefaultFishermanLootProvider`, `DefaultRecipeLootProvider`) — сигнатуры базовых провайдеров поменялись.
* **`MineColoniesDataGenerator:213-219`** — 7 конструкторных ссылок `Provider::new` перестали
  подходить: это следствие предыдущего пункта, чинится вместе с ним.
* **`Advancement.display(...)`** — 5 ошибок в `DefaultAdvancementsProvider`, другая сигнатура.
* **`RecipeBuilder.unlocked(ResourceKey<Recipe<?>>)`**, `DatagenLootTableManager:95`,
  `ItemNbtCalculator:147`, три лямбды с «bad return type» в крафт-провайдерах.

---

---

## Ось D — молчаливые поломки: компилируется, работает не так ⬜

Весь список выше построен на ошибках компилятора, и потому у него есть слепое пятно: если
сигнатура осталась прежней, а смысл поменялся, javac промолчит. Ни одна такая поломка не
попала в 292 ошибки — их надо искать отдельно и глазами.

### D1. `KeyEvent.key()` сменил смысл с GLFW keysym на SDL scancode ⬜

Найдено при разборе перехода на SDL, подтверждено `javap` по обеим версиям:

```java
// 26.2
public record KeyEvent(int key, int scancode, int modifiers)
	public int key()        // GLFW keysym
	public int scancode()

// 26.3
public record KeyEvent(int key, int keycode, int modifiers)
	public int key()        // SDL scancode  <-- то же имя, другой смысл
	public int keycode()
	public int shortcutKey()
```

Оба — record из трёх `int` с аксессором `key()`. **Код, сравнивающий `key()` с числовой
константой, скомпилируется без единой ошибки и будет реагировать не на ту клавишу.**

Отсюда правило для всей работы по вводу: в 26.3 две параллельные семьи констант —
`InputConstants.KEY_*` (115 штук, scancode) и `InputConstants.KEYCODE_*` (36 штук, keycode).
Кейбинды и `isKeyDown` берут `KEY_*`; текстовое редактирование (Ctrl+C и подобное) берёт
`KEYCODE_*` через `shortcutKey()`, как это делает ванильный `EditBox`. Подробности и образцы
— в `libs/AGENT-BRIEF-26.3.md` §4.2.

Из этого же семейства: кнопки мыши перенумерованы (GLFW левая = 0 → SDL левая = 1,
правая = 1 → 3). Тоже молча.

### D2. `instanceof BedBlock` перестал ловить все кровати ⬜

В 26.3 появился базовый `AbstractBedBlock` (в 26.2 его не было вовсе), `PART`/`OCCUPIED`/`FACING`
подняты на него, а новый `StrawBedBlock` — **сестра** `BedBlock`, а не наследник.

`BedHandlingModule:62` проверяет `getBlock() instanceof BedBlock` и читает `BedBlock.PART`.
И то и другое компилируется в 26.3 без замечаний — и молча не видит соломенных кроватей.
Всего в моде 29 площадок с `BedBlock` и ни одной с `BlockTags.BEDS`.

Подробности и что с этим делать — `CONTENT-CHANGES-26.3.md` §1.

### D3. Что ещё стоит перепроверить руками ⬜

Кандидаты того же рода, не проверенные:

* `BlockState.blocksMotion()` → `isSolid()` (B5) — если заменить механически, разница между
  «не пройти» и «непрозрачный» уедет в пасфайндинг молча.
* `PushReaction` (B9) — порядок констант переставлен, маппинг по ordinal скомпилируется.
* Любое место, где ванильный record или enum сохранил арность и имена аксессоров.

---

## Чего этот прогон **не** проверил

* **Миксины.** `minecolonies.mixins.json` не валидировался — до него компиляция не дошла.
  Инжекты в ванильные методы ломаются молча, и их поломки в этих 292 ошибках нет.
* **AccessWidener.** `validateAccessWidener` не запускался по той же причине; `invulnerableTime`
  (B13) — первый признак, что файл потребует правки.
* **Ресурсы и датаген по существу.** Ни одного `runDatagen`; ломались ли форматы данных — неизвестно.
* **Три библиотеки ldtteam.** Собраны под 26.2 и в classpath брались как есть
  (`depends.minecraft` у них `>=26.2 <26.3`). Их собственные поломки под 26.3 в этот счёт
  не входят — BlockUI, Domum Ornamentum и Structurize придётся портировать **до** мода,
  и их ошибки добавятся сверху.
* **Рантайм.** Ничего не запускалось.

## Как воспроизвести

Gradle ставится `./gradle-dist/install.sh` (Gradle 9.6.1 + JDK 25), дальше:

```sh
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH=/opt/gradle-9.6.1/bin:$JAVA_HOME/bin:$PATH
```

Пробник — отдельный проект вне репозитория, который компилирует `26.2/src/main/java` **на месте**,
не копируя (иначе он разъедется с реальными исходниками). Пины:

```properties
minecraft_version=26.3-snapshot-9
loader_version=0.19.3
fabric_version=0.158.0+26.3
loom_version=1.17.19
```

Три библиотеки ldtteam, когда `/workspace` недоступен, достаются из шипнутого jar — они лежат
там как Jar-in-Jar:

```sh
unzip -o dist/minecolonies-26.2-0.0.55.jar 'META-INF/jars/*' -d /tmp/jij
```

Контрольный прогон того же пробника под 26.2 обязателен: он отделяет поломки 26.3 от артефактов
самого пробника. Здесь он дал 7 ошибок, все — про отсутствующий Simple Planes.

Декомпилированная ваниль 26.3 (`gradle genSources`, Vineflower, 7201 файл) — в `/opt/mc-src-26.3`.

## Побочная находка про сборку 26.2

Комментарий в `26.2/build.gradle` обещает, что без jar-а Simple Planes «the build still succeeds»
— и исключает пакет `com/minecolonies/core/compatibility/simpleplanes/**`. На деле сборка падает:
`core/MineColonies.java` (строки 32, 33, 191, 192, 205, 210) и
`core/commands/colonycommands/CommandColonyBlastProtection.java:9` импортируют этот пакет напрямую,
в обход прокси `AircraftCompat`. Проверено контрольным прогоном под 26.2. На машине с
`/workspace` это не видно, потому что jar там есть.
