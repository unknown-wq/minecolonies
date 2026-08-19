# Что можно взять из ванили 26.2 вместо того, что приехало с портом

Дата: 2026-08-09. Дерево: `/home/user/minecolonies/26.2` (Fabric, MC 26.2). Оракул: `/home/user/minecolonies/1.21.1`
(NeoForge-оригинал, только чтение). Ванильные данные — из `/opt/vanilla/server-1.21.1.jar` и
`/opt/vanilla/server-26.2.jar` рядом; исходники 26.2 — `/opt/mc-src`.

**Это предложение, а не отчёт о работе. В дереве ничего не изменено** — `git status` чист (проверено
перед сдачей; временный сервер поднимался в скретчпаде, отдельной копией, и остановлен).

Каждый пункт помечен **[ИЗМЕРЕНО]** (показано на живом сервере / вынуто из ванильных данных или
байткода — сказано, чем именно), **[ПО КОДУ]** (прочитаны обе стороны, в игре не запускалось) или
**[ПРЕДПОЛОЖЕНИЕ]** (выглядит перспективно, не проверял). Ни одно рассуждение не выдаётся за замер.

Пункты, уже записанные в `PORT-STATUS.md` и `TODO.md`, здесь **не переоткрываются** — они проверены и
пропущены сознательно, см. §5.

---

# 1. Вердикт

Материала **немного, но он есть, и один пункт — настоящая поломка**.

Порт сделан аккуратнее, чем ожидалось: из 126 `PORT-NOTE` в 120 файлах только 22 не являются
grep-маркерами Structurize, и большая их часть уже содержит правильный вывод («проверено, ничего не
потеряно»). Механическая сверка **всех 145 ванильных тегов**, которые мод упоминает в коде, между
1.21.1 и 26.2 (§3.10) не нашла второго `#minecraft:dirt`. То есть класс ошибок, найденный
`FARMER-AUDIT.md`, действительно закрыт.

Зато нашлось другое: ваниль 26.2 **перевела на теги ровно те решения, которые в 1.21.1 принимал
NeoForge-хук**, и там, где порт этот хук просто выбросил, поведение потерялось молча.

**Если бы владелец сказал «делай», я бы взял в этом порядке:**

1. **Компостированная земля больше не держит цветы — флорист выращивает то, что тут же уничтожается**
   (§3.1). Это потеря порта: `Block#canSustainPlant → TRUE` из 1.21.1 выброшен, а в 26.2 на этот вопрос
   отвечает ванильный тег `#minecraft:supports_vegetation`. Показано на живом сервере: цветок на
   компостированной земле сносится первым же обновлением соседнего блока, на `minecraft:dirt` — нет.
   Правка — **одна строка в датагене**.
2. **Подсказка на предмете-хижине потеряна, а причина, записанная в комментарии, — неправда** (§3.2).
   Уровень постройки и номер колонии не показываются на хижине в инвентаре. Комментарий утверждает, что
   `TooltipDisplay` доступен только конвейеру предметов; на деле мод сам переопределяет
   `Item#appendHoverText` с этим аргументом в **пятнадцати** других классах. Обе реализации
   `TooltipProvider` (`HutBlockData`, `ColonyId`) написаны и **мертвы**. ~15 строк.
3. **Ваниль 26.2 добавила семь копий, и мод их не видит** (§3.3). У кавалерии тип экипировки — ровно
   `ModItems.spear`; ванильные `#minecraft:spears` не входят ни туда, ни в `#minecraft:swords`. Игрок,
   давший кавалеристу железное копьё, не получит ничего. Правка — одна дизъюнкция.

Всё остальное — либо мелочь на десяток строк, либо честное «оставить». Отдельно скажу: **раздела
«новые возможности ванили, ради которых стоит писать код» здесь почти нет**. 26.2 принесла много
данных (реестры `dialog`, `villager_trade`, `timeline`, `world_clock`), но ни один из этих реестров не
решает задачу, которую мод сегодня решает своим кодом, — см. §4.

---

# 2. Ранжированная таблица

| # | Что | Категория | Метка | Размер | Рекомендация |
|---|---|---|---|---|---|
| 3.1 | Компостированная земля не держит растения → `#minecraft:supports_vegetation` | компромисс порта | **[ИЗМЕРЕНО]** | 1–3 строки датагена + прогон | **брать** |
| 3.2 | Подсказка предмета-хижины → `Item#appendHoverText` в `ItemBlockHut` | компромисс порта | **[ПО КОДУ]** | ~15 строк | **брать** |
| 3.3 | Ванильные копья 26.2 → `ItemTags.SPEARS` в типе экипировки | новая ваниль | **[ИЗМЕРЕНО]** данные + **[ПО КОДУ]** поведение | 1–2 строки | **брать** |
| 3.4 | Куры 26.2 несут `brown_egg` / `blue_egg` → `ItemTags.EGGS` | новая ваниль | **[ИЗМЕРЕНО]** данные | 3–10 строк + решение по рецептам | брать частично |
| 3.5 | `toolMaterialOf` угадывает материал по прочности → `DataComponents.REPAIRABLE` | компромисс порта | **[ПО КОДУ]** | ~10 строк | решение владельца |
| 3.6 | Цветы пчеловода: `#minecraft:flowers` → `#minecraft:bee_attractive` | новая ваниль | **[ИЗМЕРЕНО]** данные | ~5 строк | оставить (обосновано) |
| 3.7 | AccessWidener: 14 строк из 50 мертвы | уборка | **[ИЗМЕРЕНО]** javap + grep | удаление 14 строк | брать (дёшево) |
| 3.8 | `PlayerMainInvWrapper.MAIN_SIZE = 36` → `Inventory.INVENTORY_SIZE` | уборка | **[ИЗМЕРЕНО]** исходник | 1 строка | брать заодно |
| 3.9 | `TicketType` мода не зарегистрирован в `Registries.TICKET_TYPE` | новая ваниль | **[ПО КОДУ]** | 3 строки | оставить |
| 3.10 | Сверка всех 145 ванильных тегов мода 1.21.1 ↔ 26.2 | проверка | **[ИЗМЕРЕНО]** | — | чисто, кроме §3.4 |

---

# 3. Кандидаты

## 3.1. Компостированная земля перестала держать растения — флорист бьёт сам себя **[ИЗМЕРЕНО]**

### Что мод делает сейчас

`core/blocks/BlockCompostedDirt.java:62-68` — комментарий вместо кода:

```
// TODO(port-26.2): DISABLED -- NeoForge's Block#canSustainPlant(...) has no counterpart in Fabric or
// vanilla 26.2, and there is no Fabric registry for it either.
//     public TriState canSustainPlant(...) { return TriState.TRUE; }
```

В оракуле (`1.21.1/.../BlockCompostedDirt.java:61-65`) этот метод есть и возвращает `TRUE` — то есть на
1.21.1 компостированная земля держала **любое** растение.

`core/tileentities/TileEntityCompostedDirt.java:103-121` — это и есть весь смысл блока: раз в секунду с
шансом `percentage` он ставит цветок в `worldPosition.above()` через `setBlockAndUpdate` (или
`DoublePlantBlock#placeAt`). Список цветов — `ModTags.floristFlowers`
(`core/generation/defaults/DefaultItemTagsProvider.java:186-205`): подсолнух, сирень, розовый куст,
пион, высокая трава, папоротники и 12 мелких цветков. **Все они — потомки `VegetationBlock`.**

Блок не входит ни в один ванильный «почвенный» тег: в jar-е мода
(`data/minecraft/tags/block/**`) есть только `mineable/axe`, `mineable/pickaxe`, `mineable/shovel`.

### Что предлагает ваниль 26.2

`VegetationBlock.mayPlaceOn` (`/opt/mc-src/net/minecraft/world/level/block/VegetationBlock.java:23-24`):

```java
protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
    return state.is(BlockTags.SUPPORTS_VEGETATION);
}
```

и `canSurvive` (строки 42-46) спрашивает ровно это про блок снизу. Состав тега (26.2, из jar-а):

```
#minecraft:supports_vegetation = #minecraft:substrate_overworld + minecraft:farmland
```

Причём `#supports_vegetation` **вложен** в `#supports_dry_vegetation`, `#supports_stem_fruit`,
`#supports_azalea`, `#supports_mangrove_propagule`, `#supports_nether_sprouts`, `#supports_warped_roots`
и ещё несколько (`/opt/mc-src/net/minecraft/data/tags/VanillaBlockTagsProvider.java:1062-1111`) — то
есть одна запись покрывает почти весь набор, который на 1.21.1 давал `canSustainPlant → TRUE`.

### Было ли это в 1.21.1

Тега `#minecraft:supports_vegetation` в 1.21.1 **не существовало** — в `server-1.21.1.jar` его файла
нет. На 1.21.1 вопрос решался NeoForge-хуком. Это тот самый случай: **порт потерял поведение, а ваниль
26.2 отвечает на тот же вопрос штатно**.

### Что изменилось для игрока

Замер на живом выделенном сервере (изолированная копия `/home/user/fabric-server-26.2`, тот же
`minecolonies-26.2-0.0.12.jar`, `Done (0.619s)`, ноль ошибок кроме офлайнового
`Failed to request yggdrasil public key`). Площадка расчищена `fill ... air`, чтобы «изменение соседа»
было настоящим изменением, а не установкой того же блока:

```
setblock 4 70 4 minecolonies:composted_dirt ; setblock 8 70 4 minecraft:dirt
setblock 4 71 4 minecraft:poppy             ; setblock 8 71 4 minecraft:poppy
  → E1-COMPOST-POPPY-PLACED    E2-DIRT-POPPY-PLACED        (оба поставились)
setblock 5 71 4 minecraft:stone             ; setblock 9 71 4 minecraft:stone
  → F1-COMPOST-POPPY-GONE      F2-DIRT-POPPY-SURVIVED
```

Контроль на камне: `C2-POPPY-ON-STONE-GONE` — механизм тот, что я думал, а не артефакт стенда.
Принадлежность тегам проверена командой на том же сервере: `execute if block 4 64 4 #minecraft:supports_vegetation`,
`#minecraft:dirt`, `#minecraft:substrate_overworld` — **ни одно не сработало**.

Реалистичный сценарий флориста, тоже замер: две соседние клетки компостированной земли, цветок на
первой, затем цветок на второй —

```
G1-FIRST-FLOWER-OK
G2-FIRST-FLOWER-KILLED-BY-NEIGHBOUR
(G3-SECOND-FLOWER-PRESENT не напечаталось — второй цветок снесло следом, каскадом)
```

То есть каждая новая посадка убивает предыдущую, а снос предыдущей убивает новую. **[ПО КОДУ]**:
отсюда следует, что флорист на 26.2 в лучшем случае отдаёт по одному цветку из изолированной клетки, а
на плотной сетке компостированной земли — почти ничего. Целиком цикл «хижина флориста → компост →
сбор» я **не запускал**: сцену колонии командами не собрать (см. `FARMER-AUDIT.md` §2.2), и это
единственное место, где я останавливаюсь на выводе, а не на замере.

### Стоимость и риск

Одна строка в `core/generation/defaults/DefaultBlockTagsProvider.java`:
`tagOf(BlockTags.SUPPORTS_VEGETATION).add(ModBlocks.blockCompostedDirt)` — плюс `runDatagen` и сборка,
отдельными вызовами (`PORT-STATUS.md`). Клиент не нужен: всё серверное.

Риск: тег глобальный, поэтому игрок сможет сажать на компостированную землю саженцы и траву руками.
**Это ровно то, что было на 1.21.1**, так что это возврат паритета, а не расширение. Чего тег **не**
покрывает — грядочные культуры: `#minecraft:supports_crops` в 26.2 состоит буквально из одной
`minecraft:farmland`, и пшеница на компостированной земле, которая на 1.21.1 работала бы, не заработает.
Это отдельное решение владельца, а не часть исправления.

### Рекомендация

**Брать, первым номером.** Это единственная найденная потеря, которую игрок видит как «работник ничего
не производит» — тот же внешний симптом, что у фермера, и та же цена ошибки.

---

## 3.2. Подсказка на предмете-хижине потеряна, и записанная причина неверна **[ПО КОДУ]**

### Что мод делает сейчас

`api/blocks/AbstractBlockHut.java:360-362`:

```
// TODO(port-26.2): DISABLED — Block#appendHoverText no longer exists (only Item has it in 26.2), and
// ItemStack#addToTooltip now needs a TooltipDisplay argument that only the item tooltip pipeline has.
// Hut item tooltips therefore no longer show the building level / owning colony lines.
```

В оракуле (`1.21.1/.../AbstractBlockHut.java:351-358`) метод есть и делает ровно два вызова
`stack.addToTooltip(ModDataComponents.HUT_COMPONENT, ...)` и `... COLONY_ID_COMPONENT ...`.

### Что предлагает ваниль 26.2

Первая половина комментария верна: `Block#appendHoverText` в 26.2 нет. **Вторая половина неверна.**

* `Item#appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`
  существует (`/opt/mc-src/net/minecraft/world/item/Item.java:322-326`) — `TooltipDisplay` приходит
  **параметром**, его не нужно ниоткуда доставать;
* `ItemStack.addToTooltip(DataComponentType<T>, TooltipContext, TooltipDisplay, Consumer, TooltipFlag)`
  публичен (`ItemStack.java:837-844`);
* у мода **уже есть собственный класс предмета** `api/items/ItemBlockHut.java` (26 строк, только
  конструктор) — переопределять есть где;
* мод переопределяет этот же метод с этой же сигнатурой в **пятнадцати** других классах предметов;
  образец в двух шагах: `core/items/ItemColonySign.java:200-221`;
* оба носителя данных уже реализуют `TooltipProvider` с 26.2-шной сигнатурой:
  `api/items/component/HutBlockData.java:21,51` и `api/items/component/ColonyId.java:28,78`.

Последнее означает, что **обе реализации подсказки сегодня — мёртвый код**: `grep` по дереву показывает
для `HUT_COMPONENT` / `COLONY_ID_COMPONENT` только `set`/`get`, ни одного `addToTooltip`.

### Было ли это в 1.21.1

Да, работало. Это чистая потеря порта.

### Что изменилось для игрока

Хижина в инвентаре и в сундуке не показывает свой уровень и колонию, к которой привязана. Для
«запакованной» хижины (`HutBlockData.pastable`) это заметно: игрок не отличает пустую хижину от
сохранённой первого уровня.

### Стоимость и риск

~15 строк в `ItemBlockHut`. Риск близок к нулю — метод помечен `@Deprecated` в ванили (в пользу
`getTooltipLines`), но мод уже так делает в 15 местах, и расхождения стилей не возникнет.

**Проверить это в контейнере нельзя**: подсказка рисуется клиентом, дисплея нет, `runClient` не
стартует. Проверка — только глазами в игре.

### Рекомендация

**Брать.** Дешёво, видно игроку, и заодно снимается комментарий, который следующий читатель принял бы за
установленный факт — та же порода ошибки, что `PORT-STATUS.md` уже ловил пять раз.

---

## 3.3. Ваниль 26.2 добавила семь копий, и мод их не видит **[ИЗМЕРЕНО]** данные / **[ПО КОДУ]** поведение

### Что мод делает сейчас

`api/equipment/ModEquipmentTypes.java:169-174`:

```java
spear = register("spear", builder -> builder
    .setIsEquipment((itemStack, equipmentType) -> itemStack.is(ModItems.spear))
    .setEquipmentLevel((itemStack, e) -> durabilityBasedLevel(itemStack, new ItemStack(ModItems.spear).getMaxDamage()))
    .build());
```

Потребитель ровно один: `core/colony/jobs/guard/JobCavalry.java:190` — `getWeaponType()` кавалерии.

### Что предлагает ваниль 26.2

26.2 добавила семь предметов-копий и тег под них:

```
#minecraft:spears = wooden_spear, stone_spear, copper_spear, iron_spear,
                    golden_spear, diamond_spear, netherite_spear
```

(файл `data/minecraft/tags/item/spears.json` внутри `server-26.2.jar`;
`ItemTags.SPEARS` — `/opt/mc-src/net/minecraft/tags/ItemTags.java:173`). Они создаются через новый
`Item.Properties#spear(ToolMaterial, …)` (`Items.java:1628-1648`) и несут компонент
`KineticWeapon` (`/opt/mc-src/net/minecraft/world/item/component/KineticWeapon.java`) — вооружение для
конного разгона: `dismountConditions`, `forwardMovement`, `damageMultiplier`.

**Запасного пути нет** — сверка составов тегов (§3.10) показывает, что `#minecraft:swords` в 26.2
получил только `copper_sword`; копий там нет. Зато они попали в `#minecraft:weapon_enchantable`
(13 → 22 предмета), то есть ваниль считает их оружием.

### Было ли это в 1.21.1

Нет. Копий в 1.21.1 не существовало — это чистое приобретение 26.2.

### Что изменилось для игрока

Игрок, который на 26.2 скрафтил железное копьё и положил его кавалеристу, получит «нет оружия».
Единственное копьё, которое понимает мод, — его собственное, `minecolonies:spear`.

Попутно: комментарий `core/items/ItemSpear.java:81-87` утверждает, что «cavalry melee use now depends on
the spear being tagged in `#minecraft:swords` by the datagen». **Это неверно дважды**: датаген мода в
`#minecraft:swords` ничего не кладёт (`src/main/generated/data/minecraft/tags/item/swords.json` не
существует), и кавалерии это не нужно — `JobCavalry.getWeaponType()` спрашивает `ModEquipmentTypes.spear`
напрямую. Комментарий стоит переписать независимо от того, что решит владелец.

### Стоимость и риск

**Вариант А (рекомендую):** одна дизъюнкция —
`itemStack.is(ModItems.spear) || itemStack.is(ItemTags.SPEARS)`, и уровень считать по
`durabilityBasedLevel` от того же эталона. Риск: баланс. Ванильное netherite-копьё прочнее модового
(2031 против 250), `durabilityBasedLevel` упрётся в потолок 5, то есть его примет только хижина
максимального уровня — механика «инструмент слишком хорош» отработает как обычно.

**Вариант Б (решение владельца, не рекомендую делать вслепую):** переписать `ItemSpear` с
`extends TridentItem` на ванильный `Item.Properties#spear(...)`. Мод получит бесплатно всю конную
механику 26.2, но потеряет метание — сейчас `ItemSpear.releaseUsing` бросает `SpearEntity`. Это
изменение геймплея, а не порта.

### Рекомендация

**Брать вариант А**; вариант Б — на решение владельца, и только после того, как кто-то посмотрит на
кавалерию в игре.

---

## 3.4. Куры 26.2 несут коричневые и синие яйца, а птичник знает только белое **[ИЗМЕРЕНО]** (ванильные данные)

### Что мод делает сейчас

* `core/colony/jobs/JobChickenHerder.java:48` —
  `if (pickedUpStack.getItem() == Items.FEATHER || pickedUpStack.getItem() == Items.EGG)` — от этого
  зависит бросок на навык при подборе;
* `core/colony/buildings/workerbuildings/BuildingChickenHerder.java:75` — `.withOutput(Items.EGG)` в
  списке «что производит птичник» (это JEI-витрина);
* рецепты пекаря и повара требуют именно `minecraft:egg`:
  `generation/defaults/workers/DefaultBakerCraftingProvider.java:137,154,165,203`,
  `DefaultChefCraftingProvider.java:192`, плюс `DefaultRecipeProvider.java:1051,1151,1162`.

### Что предлагает ваниль 26.2

Ванильная таблица кладки (`data/minecraft/loot_table/gameplay/chicken_lay.json` в `server-26.2.jar`)
выбирает предмет по варианту курицы:

```
minecraft:chicken/variant = temperate → minecraft:egg
                            warm      → minecraft:brown_egg
                            cold      → minecraft:blue_egg
```

Тега `#minecraft:eggs` в 1.21.1 не было; в 26.2 он есть и равен
`{egg, blue_egg, brown_egg}`. **Ваниль сама на него перешла**: `recipe/cake.json` и
`recipe/pumpkin_pie.json` в 26.2 требуют `#minecraft:eggs`, а в 1.21.1 требовали `minecraft:egg`.

### Было ли это в 1.21.1

Нет: ни вариантов кур, ни цветных яиц, ни тега. Это приобретение 26.2, и оно делает существующий код
мода тихо неверным.

### Что изменилось для игрока

Колония в саванне/пустыне (warm) или в тайге/снежниках (cold) держит кур, которые несут только
коричневые или только синие яйца. Тогда:

* бросок на навык в `pickupSuccess` не срабатывает — цветные яйца всегда проходят по ветке `return true`;
* витрина птичника обещает белое яйцо, которого в колонии не бывает;
* пекарь не сможет испечь торт из того, что несут его же куры, хотя ванильный верстак — сможет.

Последнее — самое заметное и самое неприятное: **мод строже ванили на своей же кухне**.

### Стоимость и риск

Первые два пункта — по строке (`stack.is(ItemTags.EGGS)`). Рецепты — решение владельца: перевод
ингредиента на тег меняет `runDatagen` и требует сверки с оракулом
(`1.21.1/src/datagen/generated`), где тега не было. Риск в том, что система запросов мода считает
ингредиенты по `ItemStorage`, и «ингредиент-тег» там ведёт себя иначе, чем «ингредиент-предмет»; это я
**не проверял**.

### Рекомендация

**Брать код (две строки), рецепты — отдельным решением.** Половинчатость тут честнее, чем трогать
датаген вслепую.

---

## 3.5. Материал инструмента угадывается по прочности **[ПО КОДУ]**

### Что мод делает сейчас

`api/equipment/ModEquipmentTypes.java:326-345`:

```java
final int durability = stack.getMaxDamage();
for (final ToolMaterial material : TOOL_MATERIALS) {
    if (material.durability() == durability) { return material; }
}
return null;
```

с комментарием «26.2 removed `TieredItem`… материал восстанавливается сопоставлением прочности».
`TOOL_MATERIALS` — жёсткий массив из семи ванильных материалов (строки 310-313).

### Что предлагает ваниль 26.2

`ToolMaterial` — это `record(..., TagKey<Item> repairItems)`
(`/opt/mc-src/net/minecraft/world/item/ToolMaterial.java:20-28`), и `applyCommonProperties` вешает на
предмет `properties.repairable(this.repairItems)`, то есть компонент
`DataComponents.REPAIRABLE` (`DataComponents.java:196`, запись
`/opt/mc-src/net/minecraft/world/item/enchantment/Repairable.java:14`). Соответствующие теги —
`#minecraft:wooden_tool_materials` … `#minecraft:netherite_tool_materials` — **новые в 26.2**: в 1.21.1
существовал только `stone_tool_materials`.

То есть материал можно спросить, а не угадать: сравнить `stack.get(DataComponents.REPAIRABLE)` с
`material.repairItems()`.

### Было ли это в 1.21.1

Нет — там был `Item#getTier()`, который ваниль убрала. Так что это компромисс порта, но и ваниль
подвинулась: `REPAIRABLE`-компонента в 1.21.1 не существовало.

### Что изменилось для игрока

Скорее всего ничего. Для **всех** ванильных инструментов прочности различны (59/131/190/250/1561/32/2031),
и сопоставление точное. Ломается это только на моде, чей инструмент собран не через
`ToolMaterial#applyToolProperties`, — но такой инструмент и по `REPAIRABLE` не опознается. Реальная
разница одна: посторонний предмет из `#minecraft:pickaxes` с прочностью ровно 250 сегодня будет объявлен
железным, а по `REPAIRABLE` — не будет.

### Стоимость и риск

~10 строк. Риск: если модовый инструмент задаёт `repairable(...)` своим тегом, метод вернёт `null` там,
где сегодня возвращал материал по совпадению прочности, и предмет **вообще перестанет** регистрироваться
в `Compatibility`. Тогда правильная форма — «сначала `REPAIRABLE`, затем прочность как запасной путь»,
и это уже не упрощение, а два пути вместо одного.

### Рекомендация

**Решение владельца, и я склоняюсь к «оставить».** Выигрыш — гипотетическая коллизия прочностей,
цена — второй путь в горячем месте (метод зовётся для каждого предмета игры при `TAGS_LOADED`).
Записываю, потому что комментарий в коде звучит как «пришлось выкручиваться», а на самом деле в 26.2
есть штатный ответ, и следующий читатель должен об этом знать.

---

## 3.6. Цветы пчеловода: `#minecraft:flowers` против `#minecraft:bee_attractive` **[ИЗМЕРЕНО]**

### Что мод делает сейчас

`api/compatibility/CompatibilityManager.java` — `getImmutableFlowers` разворачивает
`BlockItemTags.FLOWERS.item()` из `BuiltInRegistries.ITEM`. (Раньше это был скан креативных вкладок
`discoverBeekeeperFlowers` плюс пересылка списка клиенту; и то и другое удалено — см. PORT-NOTE(26.2)
у поля `beekeeperflowers`.)

### Что предлагает ваниль 26.2

Новый блок-тег `#minecraft:bee_attractive` — то, чем ваниль отвечает на вопрос «опылит ли пчела этот
цветок». Развёртка из jar-а:

```
#minecraft:flowers        = 31 блок
#minecraft:bee_attractive = 29 блоков
разница: closed_eyeblossom, golden_dandelion  (в bee_attractive нет)
обратной разницы нет
```

Замер сходится с логом живого сервера: `Finished discovering flowers 31`.

### Было ли это в 1.21.1

Тега `#minecraft:bee_attractive` в 1.21.1 нет. Заодно сам `#minecraft:flowers` вырос с 26 до 31
(`cactus_flower`, `closed_eyeblossom`, `golden_dandelion`, `open_eyeblossom`, `wildflowers`).

### Что изменилось для игрока

Практически ничего: два цветка из 31 пчеловод примет, а пчёлы проигнорируют. `closed_eyeblossom` —
ночная форма, днём она сама превращается в `open_eyeblossom`, который в теге есть.

### Стоимость и риск

~5 строк, но неудобных: `#minecraft:bee_attractive` существует **только как блок-тег** (предметного
файла в jar-е нет), а список пчеловода предметный, так что понадобится `Block.byItem(...)` и
`defaultBlockState().is(...)`.

### Рекомендация

**Оставить.** Точность растёт на 2 позиции из 31, а код становится длиннее и получает предметно-блочный
переход там, где его сейчас нет. Записано, чтобы следующий не считал это неисследованным.

---

## 3.7. AccessWidener: 14 строк из 50 мертвы **[ИЗМЕРЕНО]** (javap + grep)

### Что мод делает сейчас

`src/main/resources/minecolonies.accesswidener` — 50 содержательных строк (43 записи, унаследованные от
84 строк AT, плюс добавленные при порте).

### Что предлагает ваниль 26.2

Проверено `javap -p` по `/root/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar` и `grep` по
`src/**/*.java`. Мертвы:

| строка AW | цель | почему мертва |
|---|---|---|
| 14 | `ClientAdvancements.progress` | в дереве нет ни одного обращения |
| 36, 37 | `GoalSelector.availableGoals` (×2, `accessible` + `mutable`) | обращений нет, и в 26.2 есть публичный `getAvailableGoals()` |
| 38 | `GoalSelector.disabledFlags` | обращений нет |
| 39 | `GoalSelector.lockedFlags` | обращений нет |
| 42 | `Entity.setRemoved` | в 26.2 метод **уже** `public final`; расширять нечего, и обращений нет |
| 47 | `LivingEntity.updateSwimAmount` | AW делает приватный метод `public final` — переопределить всё равно нельзя, о чём честно сказано в `PORT-NOTE` `AbstractFastMinecoloniesEntity.java:296` |
| 57 | `AbstractArrow.shouldFall` | обращений нет |
| 59 | `AbstractMinecart.flipped` | обращений нет |
| 81–85 | `ValidationContext` (4 поля + приватный конструктор) | обращений нет; в 26.2 есть публичный конструктор `(ProblemReporter, ContextKeySet, HolderGetter.Provider)` и публичные `reporter()` / `resolver()` |

Ещё один кандидат, но **не подтверждённый**: `Mob.moveControl` — поле `protected`, и все шесть
присваиваний в дереве вида `this.moveControl = new MovementHandler(this)` идут из потомков `Mob`, где
`protected` доступен и без AW. Проверяется одной сборкой; я её не делал.

### Было ли это в 1.21.1

Часть — да (AT-строки, пережившие пять версий и потерявшие потребителя при порте), часть — новое:
`Entity.setRemoved` стал публичным именно в 26.x, `GoalSelector.getAvailableGoals()` — тоже.

### Что изменилось для игрока

Ничего. Это чистая уборка.

### Стоимость и риск

Удаление 14 строк и `gradle build` (задача `validateAccessWidener` в сборке уже есть). Риск —
`flipped` относится к вагонетке, а вагонетки/лодки/поиск пути сейчас чужая территория; эту строку стоит
оставить тому, кто ведёт ту работу.

### Рекомендация

**Брать** — но одним коммитом с §3.8 и не трогая строку 59, пока идёт работа по вагонеткам.

---

## 3.8. `PlayerMainInvWrapper` держит 36 своей константой **[ИЗМЕРЕНО]** (исходник ванили)

`core/util/PlayerMainInvWrapper.java:22` — `private static final int MAIN_SIZE = 36;` с комментарием
«36 in 26.2, the size of `Inventory#items`». В 26.2 это публичная ванильная константа:
`Inventory.INVENTORY_SIZE = 36` (`/opt/mc-src/net/minecraft/world/entity/player/Inventory.java:30`);
рядом есть и публичный `getNonEquipmentItems()` (строка 90).

1.21.1: константа тоже существовала, но класса-обёртки у мода не было — его писал NeoForge.

Для игрока — ничего. Одна строка, нулевой риск. **Брать заодно с §3.7.**

Отдельно отмечу: ловушка, описанная в `PORT-STATUS.md` («наивный `InvWrapper` над `Inventory` пустит
ведро молока в слот брони»), в 26.2 подтверждается — `Inventory.getContainerSize()` (строка 408)
действительно считает слоты экипировки. Общая библиотека `com.ldtteam.common.inventory` должна
использовать `INVENTORY_SIZE`, а не своё число.

---

## 3.9. `TicketType` мода не зарегистрирован **[ПО КОДУ]**

`api/util/constant/ColonyConstants.java:35-37` строит `new TicketType(NO_TIMEOUT, FLAG_LOADING | FLAG_SIMULATION)`
напрямую, с комментарием «`TicketType#register` is private». Комментарий точен, но неполон: в 26.2
`TicketType` — **элемент реестра** (`Registries.TICKET_TYPE`, `BuiltInRegistries.TICKET_TYPE`), и
`Registry.register(BuiltInRegistries.TICKET_TYPE, id, new TicketType(...))` открыт для мода —
приватен только ванильный хелпер (`/opt/mc-src/net/minecraft/server/level/TicketType.java:27-29`).

Последствий у нерегистрации сегодня нет: сохраняются только тикеты с `FLAG_PERSIST`
(`TicketStorage.packTickets`, `/opt/mc-src/net/minecraft/world/level/TicketStorage.java:71-78`), а у
мода этого флага нет, так что `Ticket.CODEC` с его `BuiltInRegistries.TICKET_TYPE.byNameCodec()` до
модового типа не доходит. Видимая разница ровно одна — `Ticket#toString` печатает
`Util.getRegisteredName(...)`, то есть в отладочном выводе тип безымянный.

В 1.21.1 реестра не было вовсе. Для игрока — ничего.

**Оставить**, пока кто-нибудь не начнёт разбирать чанковые тикеты по-настоящему. Три строки, но и польза
на три строки.

---

## 3.10. Полная сверка ванильных тегов мода между 1.21.1 и 26.2 — чисто **[ИЗМЕРЕНО]**

Это не кандидат, а проверка гипотезы «а вдруг есть второй `#minecraft:dirt`». `FARMER-AUDIT.md` §3.6
сверял **состав** 83 констант; здесь взяты все `BlockTags.*` / `ItemTags.*` / `BlockItemTags.*`,
встречающиеся в дереве (**145** имён), имена сопоставлены с ванильными id по
`/opt/mc-src/net/minecraft/tags/*.java`, и составы развёрнуты рекурсивно в обоих jar-ах.

Расхождения нашлись у 24 тегов. Все разобраны; значимо ровно одно:

| тег | 1.21.1 → 26.2 | вывод |
|---|---|---|
| `#minecraft:mineable/axe` | 297 → 286: **ушёл весь растительный список** (саженцы, пшеница, морковь, свёкла, тростник, грибы, трава, лозы, лианы, кувшинка, `scaffolding`) | **безвредно**: в дереве нет ни одного обращения к `MINEABLE_WITH_AXE` вне датагена |
| `#minecraft:mineable/pickaxe` | 430 → 510: медь, сера, киноварь, смола | безвредно, там же |
| `#minecraft:needs_stone_tool` | 84 → 91: ушли медные двери, пришли медные сундуки и громоотводы | уже разобрано в `FARMER-AUDIT.md` §3.6 |
| `#minecraft:maintains_farmland` | 11 → 24: калитки и `moving_piston` | безвредно, чуть щедрее |
| `#minecraft:swords`, `axes`, `hoes`, `pickaxes`, `shovels`, `*_armor` | +1 медный предмет каждый | мод работает через теги, подхватит сам |
| `#minecraft:weapon_enchantable` | 13 → 22: медь + 7 копий | см. §3.3 |
| `#minecraft:eggs` | новый | **см. §3.4 — единственное значимое** |

Про медь отдельно, потому что это первое, о чём думаешь: **медный ярус не отдельный.**
`#minecraft:incorrect_for_copper_tool` в 26.2 побайтово равен `incorrect_for_stone_tool`
(`{#needs_diamond_tool, #needs_iron_tool}`), а `#minecraft:needs_copper_tool` не существует. Мод даёт
меди уровень 1 (`registerItemTierIfAbsent(item, material, (int) material.attackDamageBonus())`,
`ModEquipmentTypes.java:270`; у меди `attackDamageBonus = 1.0F`, как у камня) — **это совпадает с
ванилью**. Ничего чинить не нужно.

---

# 4. Посмотрел — ничего нет

Коротко, чтобы следующий не переискал.

* **Новые реестры 26.2** — `dialog`, `villager_trade`, `trade_set`, `timeline`, `world_clock`,
  `instrument`, `test_instance`, `sulfur_cube_archetype`, `trial_spawner`, варианты животных
  (69 реестров против 55 в 1.21.1). Ни один не решает задачу, которую мод решает своим кодом: диалоги
  мода живут в BlockUI и обслуживают состояние колонии, а не датапак; торговли у мода нет.
  Оставить.
* **Ванильный аналог `net.neoforged.neoforge.items.*` (8 типов в `api/inventory/api/`)** — в 26.2 его
  **нет**. `ls /opt/mc-src/net/minecraft/world/` и `.../world/inventory/`: только `Container`,
  `WorldlyContainer`, `SimpleContainer`, `CompoundContainer`, `ContainerHelper` — тот же набор, что в
  1.21.1. План общей `com.ldtteam.common.inventory` из `PORT-STATUS.md` остаётся единственным путём.
* **Композиционный тег для «дорожных» блоков.** `ModTags.pathingBlocks` — 60 блоков вручную
  (`DefaultBlockTagsProvider.java:79-150`). Ванильного тега «мощение» в 26.2 нет; ближайшие новинки
  (`#minecraft:concrete`, `#minecraft:glazed_terracotta`, `#minecraft:bars`, `#minecraft:chains`,
  `#minecraft:lanterns`, `#minecraft:wooden_shelves`) покрывают одну-две строчки списка. Не окупается.
* **`ModTags.mushroomBlocks`** — комментарий «sadly forge doesn't provide the block form of this tag»
  всё ещё верен: блочного `#minecraft:mushrooms` в 26.2 тоже нет.
* **`WorkerUtil.isShearable`** (замена `IShearable`) — уже переписана на ванильные
  `#minecraft:shears_*_breaking_speed`, которые в 26.2 новые. Сделано правильно, трогать нечего.
* **`WorkerUtil.getBestToolForBlock`** — работает через `ItemStack#isCorrectToolForDrops`, то есть через
  компонент `TOOL`. Автоматически подхватывает и медь, и любые модовые правила. Ничего.
* **`ItemStackUtils.getArmorLevel` / `VANILLA_ARMOR_DISTRIBUTION`** — таблица из 24 предметов не знает
  медной брони 26.2, но запасной путь (сравнение по фактическому значению брони,
  `ItemStackUtils.java:332-355`) её раскладывает. Таблица **побайтово та же в 1.21.1** — апстрим, не
  порт. Оставить.
* **`ItemTags.MUSHROOMS` / `Tags.Items.ORES`** — ванильных `#minecraft:mushrooms` и `#minecraft:ores`
  нет ни в 1.21.1, ни в 26.2 (проверено по обоим jar-ам). Мод использует
  `ConventionalItemTags.MUSHROOMS` и `c:ores` — это и есть правильный ответ на Fabric.
* **`AbstractArrow#shotFromCrossbow`** (`CombatUtils.java:87`, `RangeCombatAI.java:212`) — обе врезки уже
  разобраны в самом дереве и, судя по чтению, разобраны верно: на 1.21.1 это был геттер-выражение без
  эффекта. Ванильной замены нет и не нужно.
* **Ванильные грядки на модовой земле.** `CropBlock.mayPlaceOn` в 26.2 спрашивает
  `#minecraft:supports_crops`, а он состоит из одной `minecraft:farmland`. Добавить туда
  `minecolonies:farmland` технически можно, и на 1.21.1 это, вероятно, работало через
  `canSustainPlant`; но модовая земля существует ради `MinecoloniesCropBlock` (её `canSurvive`
  требует `preferredFarmland`, `MinecoloniesCropBlock.java:194`), и расширение — это дизайн, а не
  возврат. Не предлагаю.
* **Модель копья / фонарь на пугале / плейсхолдер над флагом** — уже записаны в `PORT-STATUS.md`
  («Point degradations»), включая готовый рецепт `"minecraft:special"` для копья. Не переоткрываю.

---

# 5. Чего я не проверял и почему

1. **Всё клиентское.** Дисплея в контейнере нет, `runClient` не стартует. §3.2 (подсказка) и вариант Б
   §3.3 (модель и анимация копья) проверяются только человеком в игре.
2. **Флорист от начала до конца.** Механизм §3.1 показан на живом сервере командами; сама цепочка
   «хижина флориста → компост → сбор цветка работником» не запускалась — колонию командами не создать
   (`IColonyManager#createColony` требует `Player`), а собирать сцену рефлексией, как в
   `AI-SCALE-AUDIT.md`, для одного пункта я не стал. Вывод «флорист почти ничего не отдаёт» помечен
   **[ПО КОДУ]** и должен быть перепроверен перед тем, как правка попадёт в релиз-ноты как
   «исправлен флорист».
3. **Сравнение поверхностей классов между 1.21.1 и 26.2 механически не делалось.** Ванильный
   `server-1.21.1.jar` обфусцирован (`net/minecraft/world/entity/Mob.class` в нём нет), поэтому `javap`
   даёт диффы только по 26.2. Всё «было ли это в 1.21.1» в этом документе опирается либо на **данные**
   (файлы тегов, рецептов, таблиц лута — они не обфусцированы и сверены), либо на исходники оракула.
   Это значит, что **новые методы ванильных классов** 26.2 я мог пропустить; найденное — со стороны
   данных, а не со стороны API.
4. **Вагонетки, лодки и поиск пути** исключены заданием; `AbstractPathJob.java:55`
   (`PORT-NOTE` про `BlockState#getBlockPathType`) я прочитал, но не оценивал.
5. **JEI и JourneyMap** отключены через `optional-integrations.txt` (25 файлов). `BuildingChickenHerder`
   §3.4 частично про JEI-витрину — там правка есть, но проверить её нечем.
6. **Система запросов и рецепты как ингредиенты-теги** (§3.4, вторая половина) — не трогал, потому что
   `ItemStorage` сравнивает предметы, и как он поведёт себя с тег-ингредиентом, я не выяснял.

---

# 6. Как это проверялось

* Ванильные данные: `unzip 'data/minecraft/*'` из обоих jar-ов `/opt/vanilla/`, рекурсивное раскрытие
  `#`-ссылок в тегах (без него сравнение врёт ровно на нужном случае — это урок `FARMER-AUDIT.md` §3.6).
* Видимость и наличие членов 26.2: `javap -p -classpath /root/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar`
  и чтение `/opt/mc-src` (каталог на месте, 7055 файлов — проверено).
* Живой сервер: изолированная копия `/home/user/fabric-server-26.2` в скретчпаде,
  `pause-when-empty-seconds=0`, команды через FIFO на stdin, наблюдения через
  `execute if block … run say`. Тот же собранный jar `minecolonies-26.2-0.0.12.jar`, что в `mods/`.
  Сервер остановлен, копия — вне репозитория.
* Дерево не менялось: ни сборки, ни датагена не запускал; `git status --short` пуст.
