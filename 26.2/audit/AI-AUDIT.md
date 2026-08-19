# План работ по AI-подсистеме MineColonies (Fabric / MC 26.2)

> **Статус документа.** Аудит составлен 2026-08-01 против коммита `222c36bc`. Это исходный текст аудита,
> перенесённый в дерево как есть; правились только номера строк, уехавшие после `322573c3`
> («build without resources» в `AbstractEntityAIStructure*`) — а именно ссылки на
> `AbstractEntityAIStructure.java` (было 795-835) и `AbstractEntityAIStructureWithWorkOrder.java`
> (было 352,367,524,529). Остальные номера перепроверены и верны на `322573c3`.
>
> **Аудит — это гипотеза, а не спецификация.** Каждое утверждение, по которому что-то делалось, было
> перевыведено заново; расхождения зафиксированы в `AI-FIXES.md`.
>
> **Что сделано по этому документу:** пункты 1-10, 17, 18 ранжированного списка (раздел 4) реализованы.
> Пункт 6 закрыт решением в пользу паритета с 1.21.1 — обоснование в `AI-FIXES.md`.
> Пункты 11-16, 19, 20 **не** реализованы, каждый со своей причиной — список причин там же.
> Отчёт о выполненном: **`AI-FIXES.md`**.

Дата: 2026-08-01. Дерево: `/home/user/minecolonies/26.2`, оракул: `/home/user/minecolonies/1.21.1`.

## Как это проверялось

* Полный `diff -ru` по трём областям: `core/entity/ai` (2596 строк дифа), `core/entity/pathfinding` (491), `api/entity/ai` (88). Файловый состав совпадает (92 / 39 файлов с обеих сторон), новых и удалённых файлов нет.
* Ванильный исходник 26.2 доступен локально в `/opt/mc-src` — все утверждения про ваниллу проверены по нему, а не по памяти.
* Fabric API проверялся распаковкой того самого артефакта, от которого зависит порт: `fabric-api-0.154.2+26.2` → `META-INF/jars/fabric-events-interaction-v0-5.2.6+8f57f7ee9e.jar`.
* Один пункт (P-1) **измерен** микробенчмарком; остальное — вывод из графа вызовов, и это помечено явно.
* Порт **не содержит ни одного миксина**: `src/main/resources/fabric.mod.json` не имеет ключа `mixins`, есть только `minecolonies.accesswidener`. Это ограничивает часть починок (см. 1.Б).

Файлы `AbstractEntityAIStructure.java`, `AbstractEntityAIStructureWithWorkOrder.java`, `BuildingBuilder.java`, `BuildingModules.java` правятся параллельно другим агентом; ссылки на них ниже даны по снапшоту, номера строк могли уехать.

---

# 1. Регрессии порта

## 1.А. Восстановимо прямо сейчас, замена существует

### R-1. `FakePlayer` объявлен несуществующим — он существует (3 места)

`net.fabricmc.fabric.api.entity.FakePlayer` присутствует в `fabric-api-0.154.2+26.2`
(`fabric-events-interaction-v0`, классы `FakePlayer.class` и `FakePlayer$FakePlayerKey.class`).
Порт **уже им пользуется** в четырёх местах:
`core/entity/ai/combat/TargetAI.java:15`, `core/entity/ai/workers/AbstractEntityAIBasic.java` (импорт + `getFakePlayer()` через `FakePlayer.get(...)`),
`core/entity/ai/workers/production/herders/AbstractEntityAIHerder.java:34`,
`core/entity/ai/workers/production/herders/EntityAIWorkCowboy.java:227`.
Значит утверждение «нет Fabric-аналога» в трёх оставшихся местах просто ложно.

**R-1a. `api/entity/ai/combat/threat/ThreatTable.java:63-68`** — `if (attacker instanceof FakePlayer) return;` заменено на `if (false)`.
Что происходит в игре: любой урон от fake-player попадает в таблицу угроз. Fake-player берётся, в частности,
из собственного кода мода (`core/entity/ai/workers/AbstractEntityAIBasic.java:1908-1912`, `EntityAIWorkCowboy.java:227`) —
им ломают блоки и доят коров. Стража и рейдеры теперь набирают агро на сущность, которой нет в мире:
`TargetAI.checkForTarget()` (`core/entity/ai/combat/TargetAI.java:60-80`) достаёт её из `ThreatTable.getTarget()`,
`isEntityValidTarget` (там же, :88) отсеивает её по `instanceof FakePlayer` — но она уже заняла верх таблицы,
и каждый такой проход заканчивается `resetTarget()` вместо атаки настоящего врага. Симптом: стража «залипает»
на пустом месте на такте, когда рядом работал фермер/ковбой.
Починка: вернуть `attacker instanceof FakePlayer`, импорт уже доступен. Одна строка.

**R-1b. `api/util/EntityUtils.java:72-76`** — вместо `instanceof FakePlayer` стоит эвристика по имени класса:
`player.getClass() != ServerPlayer.class && !player.getClass().getName().startsWith("net.minecraft.")`.
Fabric-овский `FakePlayer` эту проверку проходит (пакет `net.fabricmc.*`), но её проходит и **любой** чужой
подкласс `ServerPlayer` из другого мода. Для таких игроков `getPlayerOfFakePlayer` начинает подменять
объект игрока на найденного по UUID — то есть права/владение колонией разрешаются не для того объекта.
Починка: одна строка на `instanceof FakePlayer`.

**R-1c. `api/entity/mobs/AbstractEntityMinecoloniesRaider.java:425-435`** — из условия
`!(damageSource.getEntity() instanceof LivingEntity) || damageSource.getEntity() instanceof FakePlayer`
(1.21.1, строка 424) выпала вторая половина. Апстрим считал урон от fake-player средовым и пропускал его
через три проверки: `tempEnvDamageImmunity`, `envDmgCooldown`, `getMinRemainingHealthForEnvironmentalDamage`.
Сейчас урон от fake-player идёт как обычный урон живой сущности — то есть **рейдеров можно добивать
автоматикой на fake-player насмерть**, чего апстрим не позволял (порог здоровья). Прямая ломающая баланс дыра.
Починка: одна строка.

### R-2. Рейдеры не исчезают на Peaceful

`api/entity/mobs/AbstractEntityMinecoloniesMonster.java:404-406` — переопределение `shouldDespawnInPeaceful() → true`
удалено с комментарием «в 26.2 метода нет». Метода действительно нет, но замена есть и она полноценная:
`Mob.checkDespawn()` (`/opt/mc-src/net/minecraft/world/entity/Mob.java:685-688`) делает
`if (level().getDifficulty() == PEACEFUL && !getType().isAllowedInPeaceful()) discard();`,
а флаг ставится билдером `EntityType.Builder#notInPeaceful()` (`/opt/mc-src/net/minecraft/world/entity/EntityType.java:593-596`).
По умолчанию `allowedInPeaceful = true` (`EntityType.java:479`), и `Builder.of(..., MobCategory.MONSTER)` его **не меняет**.
В `apiimp/initializer/EntityInitializer.java:101-134` (barbarian / archerbarbarian / chiefbarbarian / pirate /
archerpirate / chiefpirate и далее мумии, египтяне, амазонки, норманны) `notInPeaceful()` нигде не вызывается.
`checkDespawn` мод не переопределяет (переопределён только `removeWhenFarAway`, `AbstractEntityMinecoloniesRaider.java:201`).
Что в игре: переключение сложности на Peaceful больше не убирает уже заспавненных рейдеров — они продолжают
штурмовать колонию. Менеджер рейдов при этом новых не спавнит, так что игрок остаётся с бессмертной волной.
Починка: `.notInPeaceful()` в билдеры всех рейдерских типов. ~12 строк, полностью механически.

### R-3. Стрелок-марксман теперь стреляет по другой траектории

`core/entity/ai/combat/CombatUtils.java:88-97`. Апстрим:
```java
final double distanceMultiplier = arrow.shotFromCrossbow() ? 0.05 : AIM_SLIGHTLY_HIGHER_MULTIPLIER;
```
`shotFromCrossbow()` — геттер флага стрелы; стрела создаётся в `createArrowForShooter` как
`ModEntities.MC_NORMAL_ARROW`, флаг никогда не выставляется, поэтому у стражи он **всегда был false**.
Порт заменил это на вывод по оружию стрелка:
```java
final boolean shotFromCrossbow = arrow.getOwner() instanceof LivingEntity s && s.getMainHandItem().getItem() instanceof CrossbowItem;
```
`JobMarksman.getEquipmentType()` (`core/colony/jobs/guard/JobMarksman.java:27`) = `ModEquipmentTypes.crossbow`,
а `ModEquipmentTypes.java:109` определяет его как `itemStack.getItem() instanceof CrossbowItem`.
То есть у марксмана условие теперь **истинно**. `RANGED_AIM_SLIGHTLY_HIGHER_MULTIPLIER = 0.2`
(`api/util/constant/GuardConstants.java:104`), новый множитель — `0.05`.
Что в игре: марксман компенсирует гравитацию вчетверо слабее, чем в 1.21.1 — стрелы недолетают по дальним
целям; плюс на каждый выстрел на позиции **цели** проигрывается `SoundEvents.CROSSBOW_SHOOT`
(`CombatUtils.java:96`), чего в апстриме не было.
Заметьте: соседний PORT-NOTE в `core/entity/ai/workers/guard/RangeCombatAI.java:212-213` пишет
«ничего не потеряно» — это верно про ту строку, но не про `CombatUtils`, куда логику вернули в другом виде.
Починка (вариант «как в апстриме»): `final boolean shotFromCrossbow = false;` — или явно оставить новое
поведение, но тогда это надо записать как балансное изменение.

### R-4. Синхронизация экипировки гражданина больше не троттлится

`api/entity/citizen/AbstractEntityCitizen.java:679-681`. Апстрим (1.21.1, строки 647-660) переопределял
`detectEquipmentUpdates()` так: работать только если `isEquipmentDirty` **и** `tickCount % 20 == randomVariance`.
В 26.2 метод приватный (`/opt/mc-src/net/minecraft/world/entity/LivingEntity.java:2918`), AccessWidener
делает приватный метод public **и final**, поэтому переопределить нельзя, и порт его просто убрал.
Ванилла зовёт его безусловно каждый тик из `LivingEntity.tick()` (`LivingEntity.java:2768`);
`collectEquipmentChanges` (`:2928-2945`) обходит все шесть `EquipmentSlot.VALUES`, на каждом делает
`lastEquipmentItems.get(slot)`, `getItemBySlot(slot)` и `!ItemStack.matches(...)` (`:2969-2971`,
`ItemStack.java:610-616`).
Оценка (**рассуждение, не измерение**): ~100-200 нс на гражданина в тик против ~1/20 этого в апстриме;
на 200 граждан это 20-40 мкс/тик, 0.04-0.08 % бюджета тика. Абсолютно немного, но это строгая регрессия,
и она масштабируется линейно по числу граждан.
Починка требует миксина на `LivingEntity` (метод private+final через AW). В порте миксинов сейчас нет —
это следующая работа, не пункт финального PR.

## 1.Б. Регрессии без доступной замены

### R-5. `PreventRemoteMovement` больше не соблюдается — и не может

`core/entity/ai/workers/AbstractEntityAIInteract.java:375-379`. Ушёл фильтр
`!item.getPersistentData().getBoolean("PreventRemoteMovement")`. `Entity#getPersistentData` — расширение
NeoForge; в Fabric API аналога нет, а сама конвенция тега — соглашение экосистемы NeoForge, которое на
Fabric никто не пишет. Практический эффект на Fabric: нулевой. **Действий не требуется**, но комментарий
стоит переформулировать с «нельзя прочитать» на «на Fabric эта конвенция не существует».

### R-6. `checkBedExists` / `updateSwimAmount` — private+final, нужен миксин

`api/entity/other/AbstractFastMinecoloniesEntity.java:115-117` и `:289-291`,
`api/entity/citizen/AbstractCivilianEntity.java:77`.
Про `updateSwimAmount` см. 1.В-3 — это не стоит починки.
Про `checkBedExists`: **я не могу определить, регрессия это или нет.** В 26.2 он зовётся из
`LivingEntity.tick()`: `if (isSleeping() && (!canInteractWithLevel() || !checkBedExists())) stopSleeping();`
(`LivingEntity.java:2773-2775`), `isSleeping()` = `getSleepingPos().isPresent()` (`:3744`).
`CitizenSleepHandler.trySleep` (`core/entity/citizen/citizenhandlers/CitizenSleepHandler.java:100-118`)
ставит `setSleepingPos` только на блок из `BlockTags.BEDS`, так что `checkBedExists()` вернёт true и
ничего не сломается — **но** апстримовский override возвращал жёсткий `false`, что при том же коде ваниллы
приводило бы к `stopSleeping()` каждый тик, а граждане в 1.21.1 спят нормально. Значит вызывающая сторона
в 1.21.1 была другой. Что закроет вопрос: посмотреть `LivingEntity#tick` в исходниках 1.21.1
(в этом окружении их нет — есть только `/opt/mc-src` для 26.2). До этого — не трогать.

### R-7. `isControlledByLocalInstance` больше не подавляется

`api/entity/citizen/AbstractEntityCitizen.java:191-196`. В 26.2 `Entity#isLocalInstanceAuthoritative()`
объявлен `final` (`/opt/mc-src/net/minecraft/world/entity/Entity.java:3568`). Апстрим возвращал
`isEffectiveAi()`, что на сервере эквивалентно `true`, поэтому серверный тик не меняется; расхождение
только клиентское (`Entity.java:770,784,795` — предсказание столкновений и падения на клиенте).
Приоритет низкий, серверного TPS не касается.

## 1.В. Проверено — это НЕ регрессии (пометки порта преувеличивают потери)

Эти пункты выглядят как долг, но по коду ваниллы ничего не потеряно. Их надо не чинить, а переписать комментарий,
чтобы они не всплывали в следующем аудите.

1. **`AbstractFastMinecoloniesEntity.java:202-206`, `updateFluidOnEyes`.** Комментарий утверждает, что троттлинг
   «раз в 20 тиков» потерян и состояние жидкости у глаз пересчитывается с ванильной частотой. Это неверно.
   В 26.2 расчёт «глаза в жидкости» переехал внутрь `EntityFluidInteraction.update(...)`
   (`/opt/mc-src/net/minecraft/world/entity/EntityFluidInteraction.java:32-70`, поле `tracker.eyesInside`),
   а вызывается он ровно из одного места — `Entity.updateFluidInteraction()` (`Entity.java:1643-1644`).
   Этот метод порт **по-прежнему переопределяет и троттлит** — `AbstractFastMinecoloniesEntity.java:208-217`,
   раз в 10 тиков. То есть работа, которую раньше троттлили дважды, троттлится один раз, и чаще (10 вместо 20).
   Действий не требуется.
2. **`AbstractFastMinecoloniesEntity.java:280-282`, `sendDebugPackets`.** Метода `Entity#sendDebugPackets`
   в 26.2 нет вообще (grep по `/opt/mc-src/net/minecraft/world/entity/Entity.java` пуст) — значит нет и работы,
   которую апстрим подавлял. Стоимость нуль.
3. **`AbstractFastMinecoloniesEntity.java:289-291`, `updateSwimAmount`.** Тело метода в 26.2 —
   `/opt/mc-src/net/minecraft/world/entity/LivingEntity.java:3455-3462`: два присваивания float и
   `Math.min`/`Math.max`. Потеря пустого override стоит единицы наносекунд на сущность в тик. Не оптимизировать.
4. **`EntityAIWorkFarmer.java:400-417`, инлайн вспашки мотыгой.** Порт заменил
   `blockState.getToolModifiedState(ctx, ItemAbilities.HOE_TILL, true)` на явное
   `(GRASS_BLOCK || DIRT_PATH || DIRT) && воздух сверху`. Это **точный** эквивалент ванильной таблицы
   `HoeItem.TILLABLES` в части перехода в `Blocks.FARMLAND` (остальные записи, `COARSE_DIRT` и `ROOTED_DIRT`,
   дают `DIRT`, а исходный код всё равно отбрасывал всё, что не `FARMLAND`). Собственный блок мода
   `core/blocks/MinecoloniesFarmland.java` в TILLABLES нигде не регистрировался — grep по 1.21.1 находит
   единственное упоминание `HOE_TILL`, в самом фермере. Действий не требуется.
5. **`CombatUtils.java:52-57`, потеря `BowItem#customArrow`.** Ванильный `customArrow` возвращает стрелу
   без изменений; переопределяют его только моды с кастомными луками, и на Fabric такого хука нет ни у кого.
   Действий не требуется.
6. **`EntityAIQuarrier.java:66`, `EntityAIStructureMiner.java:72`, `EntityAIWorkNether.java:141-149` —
   `ItemAbilities` → теги предметов.** Проверено: все три места используют результат только для строки
   `renderData` (какая иконка инструмента рисуется на поясе). Ни одно решение AI от этого не зависит.
   Комментарий «DISABLED» здесь пугает сильнее, чем следует.
7. **Ключ ресурсов `stack.getDescriptionId()` → `stack.getItem().getDescriptionId()`.** Изменение затрагивает
   восемь мест и выглядит как классический источник рассинхрона ключей (строитель бесконечно перезаказывает
   материалы). Проверено грепом: изменены **все** места симметрично —
   `core/colony/buildings/modules/BuildingResourcesModule.java:207,259,321`,
   `core/colony/buildings/AbstractBuildingStructureBuilder.java:105,314`,
   `core/colony/buildings/moduleviews/BuildingResourcesModuleView.java:58`,
   `core/items/ItemResourceScroll.java:185`, `core/client/gui/WindowBuildDecoration.java:269`,
   `core/client/gui/WindowBuildBuilding.java:370`, `core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java:370,384,541,546`.
   Более того, порт **починил** апстримовскую асимметрию: в 1.21.1
   `core/client/gui/WindowResourceList.java:219` уже использовал `getItem().getDescriptionId()`, а остальные — нет.
   Действий не требуется.
8. **`ChunkCache.java:78`, `chunkMap.visibleChunkMap.get(...)` вместо `getVisibleChunkIfPresent`.**
   Оба читают одно и то же поле; в 26.2 оно `volatile` и заменяется клоном целиком
   (`/opt/mc-src/net/minecraft/server/level/ChunkMap.java:128,543`), так что чтение из чужого потока
   безопасно и семантически тождественно. `ChunkCache` вообще конструируется на серверном потоке
   (`core/entity/pathfinding/pathjobs/AbstractPathJob.java:202,272`), до отправки задания в пул.
   Действий не требуется.
9. **`AbstractPathJob.java:59-66` + `:1488`, `VanillaPathTypes.of(cachedBlockLookup, pos)`.**
   Замена NeoForge-расширения `BlockState#getBlockPathType` на ванильный
   `WalkNodeEvaluator.getPathTypeFromState` (`/opt/mc-src/.../WalkNodeEvaluator.java:512`) — корректна,
   и порт при этом ещё и подставил `cachedBlockLookup` вместо `world`, что **быстрее** апстрима
   (кэшированный доступ вместо прямого `Level.getBlockState`). Улучшение, а не потеря.

## 1.Г. Тихие изменения баланса, требующие решения владельца

### R-8. Урон топором у рыцаря и урон мечом в нижнемировой экспедиции считаются иначе

`core/entity/ai/workers/guard/MeleeCombatAI.java:338-341` и `core/entity/ai/workers/production/EntityAIWorkNether.java:427-437`.

Апстрим: `heldItem.getItem().getDamage(heldItem)` и `swordItem.getDamage(sword)`.
Статически `heldItem.getItem()` имеет тип `Item`, а у ванильного `Item`/`AxeItem`/`DiggerItem` в 1.21.1
метода `getDamage(ItemStack)` нет. Значит разрешался он в расширение NeoForge
`IItemExtension#getDamage(ItemStack)`, семантика которого — **накопленный износ стека** (`stack.getDamageValue()`),
а не атака оружия. То есть в апстриме изношенный топор бил сильнее нового.

Порт заменил это на настоящую атаку оружия из компонента:
`heldItem.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, EMPTY).compute(Attributes.ATTACK_DAMAGE, 0.0, MAINHAND)`.

Это, скорее всего, **исправление апстримового бага**, но оно меняет баланс молча: рыцарь с новым алмазным
топором теперь бьёт сильнее, чем раньше, а с почти сломанным — слабее.
Чего мне не хватает, чтобы утверждать наверняка: в этом окружении нет jar'а NeoForge, поэтому я не смог
декомпилировать `net.neoforged.neoforge.common.extensions.IItemExtension` и увидеть тело метода.
Что закроет вопрос: одна декомпиляция `IItemExtension#getDamage(ItemStack)` из зависимостей дерева 1.21.1.

### R-9. Мелкие косметические потери (перечисляю, чтобы закрыть список; чинить не надо)

* `core/entity/ai/minimal/EntityAIFloat.java:43-44` — `getEyeInFluidType().canSwim()` → `isEyeInFluid(WATER) || isEyeInFluid(LAVA)`.
  Модовые жидкости больше не учитываются (на Fabric их и нет в этом смысле), лава теперь считается всегда.
* `core/entity/ai/workers/crafting/EntityAIWorkAlchemist.java:280-283`,
  `core/entity/ai/workers/production/EntityAIWorkLumberjack.java:849` — `state.getSoundType(world,pos,entity)` → `state.getSoundType()`.
  Позиционно-зависимые звуки блоков модов не берутся. Только звук.
* `core/entity/ai/workers/production/EntityAIWorkNether.java:445,538,729` — `hurtAndBreak(..., null, ...)`
  вместо передачи `worker`. Теряется `onEquippedItemBroken` (звук и частицы поломки). Урон и Unbreaking считаются как прежде.
* `core/entity/ai/workers/crafting/AbstractEntityAICrafting.java:378-380,473-475` — `ItemStack#getCraftingRemainingItem()`
  → `Item#getCraftingRemainder()`. Теряется зависящий от стека остаток (у NeoForge мог зависеть от NBT). На ванилле эквивалент.
* `core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:565-577` — `createPathFinder` теперь
  возвращает реальный `PathFinder(new WalkNodeEvaluator(), n)` вместо `null`. Он никогда не используется, но
  висит в памяти: `Node[32]` + `BinaryHeap` с `Node[128]` + `Long2ObjectOpenHashMap` + `Object2BooleanOpenHashMap`
  (`/opt/mc-src/.../PathFinder.java:23-27`, `BinaryHeap.java:6`, `WalkNodeEvaluator.java:34-36`) ≈ 1-2 КБ на сущность.
  На 500 граждан — до мегабайта резидентной памяти. Тика не касается.

---

# 2. Производительность

## 2.А. Измерено

### P-1. `RecentTargetCache.getExtraCost` зовёт `System.currentTimeMillis()` на каждый раскрытый узел A*

Код: `core/entity/pathfinding/RecentTargetCache.java:59-66` (`getExtraCost` безусловно вызывает `cleanup()`)
и `:72-92` (`cleanup()` начинается с `System.currentTimeMillis()`).
Кто зовёт: `core/entity/pathfinding/pathjobs/AbstractPathJob.java:1000-1004`
(`getEndNodeScoreWithExtraCost`), а его — основной цикл поиска на `AbstractPathJob.java:396`, `:424`, `:497`,
то есть **один раз на каждый раскрытый узел**. Лимит узлов — `MAX_NODES = 8000` (`AbstractPathJob.java:71`),
фактический — `min(MAX_NODES, range²) * pathNodeLimitMultiplier` (`:205,220,235,252,283,299`).

Замер (`scratchpad/bench/Bench.java` — построчная копия `RecentTargetCache`, `BlockPos` заменён на
`record Pos(int,int,int)` с сопоставимым по стоимости `hashCode`; Java 25, три прогона):

| вариант | нс/вызов |
|---|---|
| как в коде | 36.49 / 36.41 / 38.53 |
| без `cleanup()` в горячем пути | 2.84 / 2.93 / 3.06 |
| **разница** | **33.7 / 33.5 / 35.5** |

Разложение (`scratchpad/bench/B2.java`, 20 млн итераций): `System.currentTimeMillis()` — **28.1 нс/вызов**,
`ConcurrentLinkedQueue.peek()` — 0.73 нс. То есть 84 % накладных расходов — это чтение часов.

Итог: **0.29 мс на задание в 8000 узлов**, из которых 0.27 мс — чистые накладные расходы.
Важно, что это на *единственном* потоке поиска пути (см. P-2), то есть это прямое уменьшение пропускной
способности пути в 1.1-1.2 раза на длинных маршрутах.

Оговорка по среде: `currentTimeMillis` в этом контейнере необычно дорог (28 нс — типично 20-25 нс на голом железе);
относительный вывод («cleanup дороже самого поиска в кэше в 12 раз») от этого не зависит.

Починка: вынести `cleanup()` из `getExtraCost` — звать его один раз на задание (в `search()` перед циклом) либо
раз в N вызовов по счётчику. Риск: нулевой, чистка кэша — не корректностная операция, срок жизни записей 200 с
(`RecentTargetCache.java:22`). Файл идентичен апстримовому — это не регрессия порта, а апстримовый недосмотр.
Правка на 5 строк, полностью локальная.

## 2.Б. Обосновано графом вызовов (не измерено)

### P-2. Поиск пути — один поток, без кэша результатов

`core/entity/pathfinding/Pathfinding.java:50`: `new ThreadPoolExecutor(1, 1, 10, SECONDS, jobQueue, ...)`,
очередь `ArrayBlockingQueue(10000)` (`:17`). Файл **побайтно совпадает** с 1.21.1 — это апстримовое решение,
не регрессия; конфига числа потоков в дереве нет (грепом по `api/configuration/` находится только клиентская
категория GUI `pathfinding`). Кэша результатов тоже нет: `core/entity/pathfinding/pathresults/PathResult.java`
хранит один `Path` на задание и умирает вместе с ним. Повторяющиеся маршруты (курьер склад↔хижина,
строитель сундук↔стройка) пересчитываются целиком каждый раз.
Что бы это закрыло: счётчик заданий и суммарного времени на `Pathfinding.enqueue` + гистограмма `totalNodesVisited`
на живой колонии. Пока этого нет — не менять число потоков вслепую: `AbstractPathJob` читает `ChunkCache`,
и многопоточность апстрим не проверял.

### P-3. Недостижимая цель = полный A* каждые 5 тиков, без отката

`core/entity/pathfinding/navigation/EntityNavigationUtils.java:154-183` (`walkToPos`; та же структура в
`walkCloseToXNearY:54-86`, `walkAwayFrom:191-206`, `walkToRandomPosHelper:304-318`).
Логика: `if (nav.isDone() || !isOnRightTask) { ...; nav.walkTo(desiredPosition, ...); }`.
`MinecoloniesAdvancedPathNavigate.isDone()` (`:375-379`) становится истинным, когда путь пройден до конца,
**в том числе когда путь не дошёл до цели**. Дальше проверяется только расстояние
(`BlockPosUtil.dist(...) <= distToDesired`); если оно не сошлось — сразу выдаётся новое задание.
Вызывающая сторона — AI-состояние воркера, тикающее раз в `ENTITY_AI_TICKRATE = 5` тиков
(`api/entity/citizen/AbstractEntityCitizen.java:71,452`; `core/entity/ai/workers/CitizenAI.java:105`).
Счётчика попыток нет ни здесь, ни в `PathResult`. `PathingStuckHandler` (вызывается из
`MinecoloniesAdvancedPathNavigate.tick():383-389`, раз в 11 тиков) лечит «застрял и не двигается», а не
«дошёл куда мог и цель всё ещё недостижима».
Что в игре: один заблокированный рабочий генерирует 4 полных поиска в секунду на единственном потоке поиска.
Десяток таких (типично после того, как игрок перестроил кусок колонии) заполняет очередь и тормозит
пути **всем** гражданам.
Что закроет вопрос: счётчик на `Pathfinding.enqueue` с разбивкой по типу задания на живом сервере —
если `PathJobMoveToLocation` доминирует и `pathReachesDestination` у него в основном false, гипотеза верна.
Починка: экспоненциальный откат в `EntityNavigationUtils` при повторном задании на ту же цель, когда
предыдущий результат не достиг её (`PathResult.isPathReachingDestination()` уже есть). Средний риск —
трогает все виды ходьбы.

### P-4. `EntityAIWorkDeliveryman.deliver` — аллокация на каждый слот инвентаря

`core/entity/ai/workers/service/EntityAIWorkDeliveryman.java:368-380`:
`itemsToDeliver` строится стримом, затем в цикле по всем слотам делается
`itemsToDeliver.contains(new ItemStorage(workerInventory.getStackInSlot(i)))` — новый `ItemStorage` на слот
плюс линейный поиск по списку. Экономия небольшая (27 слотов на доставку), но правка тривиальна:
`HashSet` вместо `List` и вынос конструктора. Риск нулевой.

### P-5. Апстримовый вложенный стрим при подсчёте нужных материалов

`core/entity/ai/workers/AbstractEntityAIStructure.java:794-833` — там же висит апстримовый комментарий
«Why predicate based search when we got our ItemStorages we look for already?». `requestedMap.keySet().stream().anyMatch(...)`
внутри предиката, применяемого ко всему инвентарю, и `foundStacks.stream()` в `removeIf` — O(n·m) с аллокацией
стрима на каждый элемент. Вызывается на смене этапа стройки, не каждый тик, поэтому это средний, а не высокий приоритет.
**Файл сейчас правится другим агентом** — трогать после его PR.

## 2.В. Проверено — это НЕ горячие точки (не оптимизировать)

Три соблазнительные цели, которые при проверке оказались холодными. Записываю, чтобы на них не тратили время.

1. **Диспетчеризация машины состояний.** `TickRateStateMachine.tick()`
   (`api/entity/ai/statemachine/tickratestatemachine/TickRateStateMachine.java:69-104`) обходит четыре списка
   переходов и на каждом делает `checkTransition` → `transition.countdownTicksToUpdate(tickRate)` (`:111-124`),
   то есть один виртуальный вызов и вычитание int. У воркера это ~15 базовых переходов
   (`core/entity/ai/workers/AbstractEntityAIBasic.java:198-256`) плюс переходы задания — порядка 25-40.
   Сама машина тикается раз в 5 тиков (`AbstractEntityCitizen.java:452`, `AbstractAISkeleton.java:58`).
   На 500 граждан это ≈ 3-4 тыс. виртуальных вызовов в тик — десятки микросекунд. Не цель.
2. **`AITarget` с `tickRate = 1`.** Их много (`EntityAIWorkBeekeeper.java:110-115`,
   `AbstractEntityAIGuard.java:170-172`, `EntityAIWorkHealer.java:91-92`, ещё ~20 мест) и выглядят страшно,
   но `tickRate` считается в тиках сервера, а не в тиках машины: `countdownTicksToUpdate` уменьшается на
   `tickRate` машины (5) за проход, так что `tickRate = 1` означает «раз в тик машины» = раз в 5 тиков сервера.
   Сверх того тела этих состояний первым делом зовут `setDelay(...)` — например
   `EntityAIWorkBeekeeper.decideWhatToDo:203` и `prepareForHerding:164`, — а `waitingForSomething` (AI_BLOCKING, `AbstractEntityAIBasic.java:215`
   и тело `:494-514`) блокирует всю машину, пока задержка не истечёт. Дорогие сканы
   (`EntityAIWorkBeekeeper.searchForAnimals:588-602`, `getBeesInHives:145-154`) реально выполняются раз в
   `DECIDING_DELAY`, а не каждые 5 тиков.
3. **AABB-сканы поиска цели у стражи.** `TargetAI.searchNearbyTarget` (`core/entity/ai/combat/TargetAI.java:139-166`)
   регистрируется с частотой из `AttackMoveAI` — `super(owner, 80, stateMachine)`
   (`core/entity/ai/combat/AttackMoveAI.java:44`), то есть раз в 80 тиков (4 с). Один `getEntitiesOfClass` +
   один `AABB` на стража раз в 4 секунды — не проблема (проблема в другом, см. B-1 и B-2).
4. **`AbstractFastMinecoloniesEntity.pushEntities`** (`api/entity/other/AbstractFastMinecoloniesEntity.java:157-181`)
   уже кэширует список отталкиваемых на 10 тиков. Единственная мелочь: `entityPushCache` держит сильные ссылки
   на `LivingEntity` до 10 тиков после их удаления. Косметика.

---

# 3. Прокачка поведения

### B-1. Страж рядом со спящим стражем слепнет

`core/entity/ai/combat/TargetAI.java:150-165`:
```java
for (final LivingEntity entity : entities) {
    ...
    if (skipSearch(entity)) { return false; }   // <- выход из всего поиска
    if (isEntityValidTarget(entity) && ...) { addThreat(...); foundTarget = true; }
}
```
`skipSearch` переопределён в `core/entity/ai/workers/guard/MeleeCombatAI.java:415-427`,
`RangeCombatAI.java:390-402`, `DruidCombatAI.java:322-335` и возвращает `true`, когда в списке найден
спящий страж (тогда же ставится `parentAI.setWakeCitizen(citizen)`).
Что в игре: если в зоне поиска есть спящий страж, весь проход поиска цели прерывается — реальные враги
в том же списке не рассматриваются вообще. Порядок списка — порядок обхода чанков, то есть недетерминирован.
При ночном налёте на казармы это означает, что бодрствующая стража не реагирует на рейдеров, пока не разбудит соседей.
Правка: заменить `return false` на `continue` и запоминать «кого будить» отдельно, не прерывая поиск.
Файл идентичен апстримовому — это апстримовая проблема, но правка мелкая и локальная.

### B-2. Страж смотрит только в одну случайную сторону из четырёх

`core/entity/ai/combat/TargetAI.java:174-190` (`getSearchArea`): бокс строится вокруг
`Direction.from3DDataValue(user.getRandom().nextInt(4) + 2)` — случайная горизонтальная сторона, и в эту сторону
бокс вытянут на `getSearchRange()`, в противоположную — только на `DEFAULT_VISION`.
С частотой поиска 80 тиков (`AttackMoveAI.java:44`) стражу в худшем случае нужно 4 прохода (320 тиков ≈ 16 с),
чтобы просто посмотреть в нужную сторону. Это осознанная апстримовая экономия, но она читается как тупость:
рейдер может 15 секунд стоять за спиной стража.
Правка с сохранением экономии: детерминированный цикл по четырём направлениям (счётчик вместо `nextInt`),
что снижает худший случай с «может никогда» до ровно 4 проходов, стоимость та же.

### B-3. «Стою и перепрокладываю» вместо признания недостижимости

Тот же код, что в P-3 (`EntityNavigationUtils.java:154-183`). Визуальный симптом отличается от производительного:
рабочий, чья цель отгорожена, встаёт на месте и молча дёргается 4 раза в секунду, не переходя в
состояние ошибки и не выдавая игроку интеракцию. Ни `PathingStuckHandler`, ни AI не считают это провалом.
Правка (после счётчика попыток из P-3): после N неудачных попыток дойти — вернуть состояние в `IDLE`
и выдать `StandardInteraction` («не могу дойти»), как это уже делается для отсутствующих ресурсов.

### B-4. Курьер выгружает в здание всё, что нёс

`core/entity/ai/workers/service/EntityAIWorkDeliveryman.java:393` — апстримовый TODO
«Please only push items into the target that were actually requested», рядом с
`InventoryUtils.forceItemStackToItemHandler(targetBuilding.getItemHandlerCap(), stack, ((IBuilding) targetBuilding)::isItemStackInRequest)`.
Предикат управляет только тем, что **можно вытеснить** из целевого инвентаря, а не тем, что **кладётся**.
В связке с TODO на `:328` («precondition: инвентарь курьера состоит только из запрошенного стека») это
даёт классический симптом: курьер, подобравший что-то по дороге, разгружает это в чужую хижину.
Правка средней сложности: фильтровать по `itemsToDeliver` перед вставкой (список уже собран на `:368-369`).

### B-5. Пастух заказывает по чуть-чуть всего, вместо достаточного количества одного

`core/entity/ai/workers/production/herders/AbstractEntityAIHerder.java:860-861` — апстримовый TODO
«currently this will request some of all items, when really we should be happy with enough of *any* of».
Следствие: коровник/овчарня непрерывно висят в списке запросов на несколько видов корма одновременно,
курьер бегает лишние рейсы, а разведение всё равно упирается в один вид. Правка — заменить
конъюнкцию запросов на `StackList` с общим количеством.

### B-6. Лесоруб не ломает листву, когда идёт в зону без дерева

`core/entity/ai/workers/production/EntityAIWorkLumberjack.java:491` — апстримовый TODO
«On walking to the zone (no tree found) leaves are not getting broken». Лесоруб застревает в листве,
после чего его вытаскивает `PathingStuckHandler` — телепортом. Игрок видит рывок.

### B-7. Рыцарь бьёт вдвое сильнее на низком здоровье

`core/entity/ai/workers/guard/MeleeCombatAI.java:353-356` (`if (user.getHealth() <= user.getMaxHealth()*0.2) addDmg *= 2;`)
рядом с апстримовым TODO `:352` «Recheck balancing, do we need this». Это накладывается на R-8
(изменённая формула урона топором) — если R-8 будет принят как исправление, множитель ×2 стоит перепроверить
в тех же тестах, иначе рыцари станут заметно сильнее, чем в 1.21.1.

---

# 4. Ранжированный список: что делать

Столбец «PR» — можно ли класть в финальный PR порта: **сейчас** = изменение локальное, механическое,
без нового API и без риска для остальной сборки; **потом** = требует новых миксинов, замеров на живом мире
или согласования баланса.

| # | Что | Тип | Файл | Объём | Риск | PR |
|---|---|---|---|---|---|---|
| 1 | Вернуть `instanceof FakePlayer` в таблицу угроз | регрессия | `api/entity/ai/combat/threat/ThreatTable.java:63-68` | 1 строка | нет | **сейчас** |
| 2 | Вернуть `instanceof FakePlayer` в проверку урона рейдеру | регрессия | `api/entity/mobs/AbstractEntityMinecoloniesRaider.java:430` | 1 строка | нет | **сейчас** |
| 3 | Вернуть `instanceof FakePlayer` в `getPlayerOfFakePlayer` | регрессия | `api/util/EntityUtils.java:72-76` | 1 строка | нет | **сейчас** |
| 4 | `.notInPeaceful()` на все рейдерские `EntityType.Builder` | регрессия | `apiimp/initializer/EntityInitializer.java:101-200` | ~12 строк | нет | **сейчас** |
| 5 | Убрать `cleanup()` из горячего пути `getExtraCost` | перф (**измерено**: −0.27 мс на задание 8000 узлов) | `core/entity/pathfinding/RecentTargetCache.java:59-66` | ~5 строк | нет | **сейчас** |
| 6 | Решить судьбу вывода «выстрел из арбалета» у марксмана | регрессия поведения | `core/entity/ai/combat/CombatUtils.java:88-97` | 1 строка + решение | низкий | **сейчас**, если возвращаем апстримовое поведение |
| 7 | `skipSearch` → `continue` вместо прерывания поиска цели | поведение | `core/entity/ai/combat/TargetAI.java:157` (+3 переопределения) | ~10 строк | низкий | **сейчас** |
| 8 | Детерминированный обход 4 направлений в `getSearchArea` | поведение | `core/entity/ai/combat/TargetAI.java:176` | ~5 строк | низкий | **сейчас** |
| 9 | Переписать вводящие в заблуждение PORT-NOTE (1.В, пункты 1-3, 6) | долг | `AbstractFastMinecoloniesEntity.java:202,280,289`, `EntityAIQuarrier.java:66`, `EntityAIStructureMiner.java:72` | комментарии | нет | **сейчас** |
| 10 | `HashSet` вместо `List.contains` в разгрузке курьера | перф | `core/entity/ai/workers/service/EntityAIWorkDeliveryman.java:368-380` | ~4 строки | нет | **сейчас** |
| 11 | Откат при повторной прокладке на недостижимую цель | перф + поведение | `core/entity/pathfinding/navigation/EntityNavigationUtils.java:154-206` | ~30 строк | средний (трогает всю ходьбу) | потом |
| 12 | Проверить R-8 (урон топором/мечом) по исходникам NeoForge и зафиксировать баланс | баланс | `MeleeCombatAI.java:338`, `EntityAIWorkNether.java:430` | исследование + решение | средний | потом |
| 13 | Миксин на `LivingEntity#detectEquipmentUpdates`, вернуть троттлинг | перф (регрессия) | `api/entity/citizen/AbstractEntityCitizen.java:679` | новый миксин + `fabric.mod.json` | средний (первый миксин в проекте) | потом |
| 14 | Курьер кладёт только запрошенное | поведение | `EntityAIWorkDeliveryman.java:393-402` | ~15 строк | средний | потом |
| 15 | Пастух: «достаточно любого» вместо «немного всего» | поведение | `AbstractEntityAIHerder.java:860` | ~20 строк | средний | потом |
| 16 | Инструментировать пул поиска пути (счётчики заданий/узлов/времени), потом решать про кэш и потоки | перф (исследование) | `core/entity/pathfinding/Pathfinding.java:68-71` | замер | нет | потом |
| 17 | Убрать вложенные стримы в подсчёте материалов | перф | `AbstractEntityAIStructure.java:794-833` | ~20 строк | низкий | потом (файл в работе у другого агента) |
| 18 | Лесоруб ломает листву по пути в зону | поведение | `EntityAIWorkLumberjack.java:491` | ~15 строк | низкий | потом |
| 19 | Выяснить статус `checkBedExists` по исходникам 1.21.1 | неизвестно | `AbstractFastMinecoloniesEntity.java:115`, `AbstractCivilianEntity.java:77` | исследование | — | потом |
| 20 | `isLocalInstanceAuthoritative` — клиентское предсказание | регрессия (клиент) | `AbstractEntityCitizen.java:191-196` | требует миксина | низкий | потом |

Пункты 1-10 — это финальный PR: десять правок, суммарно меньше сотни строк, ни одна не требует
нового API и ни одна не трогает файлы, которые сейчас правит другой агент.
Всё остальное — отдельная итерация, и её первым шагом должен быть пункт 16: без счётчиков на живой колонии
решения про кэш путей и число потоков будут гаданием.
