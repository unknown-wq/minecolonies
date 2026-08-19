# Что сделано по аудиту AI-подсистемы (Fabric / MC 26.2)

Дата: 2026-08-01. Дерево: `/home/user/minecolonies/26.2`, база — `322573c3`, оракул: `/home/user/minecolonies/1.21.1`.
Исходный аудит: **`AI-AUDIT.md`** (там же ранжированный список, на номера которого ссылается этот файл).

Сделаны пункты **1-10, 17, 18**. Каждое утверждение аудита перед правкой выводилось заново — где выводы
разошлись, это отмечено ниже. Сборка: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 /opt/gradle-9.6.1/bin/gradle build`,
BUILD SUCCESSFUL. Проверка каждого поведенческого изменения — **по байткоду собранного артефакта**
`build/libs/minecolonies-26.2-0.0.11.jar`, а не по исходнику: у этого порта есть история дефектов, которые
в исходнике выглядели правильно.

---

## 1. `ThreatTable` — урон от fake-player снова не попадает в таблицу угроз

**Файл:** `api/entity/ai/combat/threat/ThreatTable.java`

**Что было.** Апстримовое `if (attacker instanceof FakePlayer) return;` заменено на `if (false)` с пометкой
«у FakePlayer нет Fabric-аналога (contract C4)». Утверждение ложно: `net.fabricmc.fabric.api.entity.FakePlayer`
лежит в том самом артефакте, от которого зависит порт —
`fabric-api-0.154.2+26.2` → `META-INF/jars/fabric-events-interaction-v0-5.2.6+8f57f7ee9e.jar`,
классы `FakePlayer.class` и `FakePlayer$FakePlayerKey.class`. Порт уже импортировал этот класс в четырёх
других местах (`TargetAI`, `ColonyPermissionEventHandler`, `EntityAIWorkCowboy`, `AbstractEntityAIHerder`),
то есть «аналога нет» было написано рядом с работающим использованием аналога.

**Что видел игрок.** Fake-player берётся из собственного кода мода — им ломают блоки
(`AbstractEntityAIBasic#getFakePlayer`) и доят коров. Любой такой урон садился в таблицу угроз и всплывал
наверх. Дальше `TargetAI.checkForTarget()` доставал эту цель из `ThreatTable.getTarget()`, а
`isEntityValidTarget` отбраковывал её обратно по `instanceof FakePlayer` — и проход заканчивался
`resetTarget()` вместо атаки. Симптом: стража «залипает» на пустом месте на тактах, когда рядом работал
фермер или ковбой.

**Что стало.** Восстановлено `attacker instanceof FakePlayer`, добавлен импорт. Комментарий переписан:
теперь он объясняет, что аналог есть и где именно.

**Доказательство в артефакте:**
```
ThreatTable.addThreat(LivingEntity, int):
   0: aload_1
   1: instanceof #26  // class net/fabricmc/fabric/api/entity/FakePlayer
   4: ifeq 8
   7: return
```
Это настоящий `instanceof` против настоящего класса Fabric API, а не свёрнутая константа.
`fabric-api` объявлен жёсткой зависимостью в `fabric.mod.json` (`"fabric-api": "*"`), так что класс
гарантированно на classpath в рантайме.

---

## 2. `AbstractEntityMinecoloniesRaider` — рейдера снова нельзя добить автоматикой

**Файл:** `api/entity/mobs/AbstractEntityMinecoloniesRaider.java`

**Что было.** Из условия `!(getEntity() instanceof LivingEntity) || getEntity() instanceof FakePlayer`
выпала вторая половина — по той же ложной причине, что и в пункте 1.

**Что видел игрок.** Апстрим считает урон от fake-player *средовым* и гонит его через три проверки:
`tempEnvDamageImmunity`, `envDmgCooldown` и порог `getMinRemainingHealthForEnvironmentalDamage`. Последний —
это пол по здоровью, ниже которого средовой урон рейдера не убивает. Без ветки урон от fake-player шёл как
обычный урон живой сущности, и рейдеров стало можно **добивать автоматикой насмерть**, чего апстрим не
позволял. Это дыра в балансе рейдов, а не косметика.

**Что стало.** Ветка возвращена дословно.

**Доказательство в артефакте:**
```
hurtServer(ServerLevel, DamageSource, float):
   1: invokevirtual DamageSource.getEntity()
   4: instanceof    LivingEntity
   7: ifeq 20                  <- не живой -> средовая ветка
  11: invokevirtual DamageSource.getEntity()
  14: instanceof    net/fabricmc/fabric/api/entity/FakePlayer
  17: ifeq 81                  <- живой и не fake -> обычный урон
  20: (tempEnvDamageImmunity / envDmgCooldown / порог здоровья)
```
Короткое замыкание `||` восстановлено ровно как в 1.21.1.

---

## 3. `EntityUtils.getPlayerOfFakePlayer` — больше не подменяет личность чужим модам

**Файл:** `api/util/EntityUtils.java`

**Что было.** Вместо `instanceof FakePlayer` стояла эвристика по имени класса:
`player.getClass() != ServerPlayer.class && !player.getClass().getName().startsWith("net.minecraft.")`.

**Что видел игрок.** Эвристика **шире** апстримовой: её проходит не только fabric-овский `FakePlayer`
(пакет `net.fabricmc.*`), но и **любой** подкласс `ServerPlayer` из любого другого мода. Для такого игрока
метод начинал подменять объект на найденного по UUID — а это ровно тот объект, против которого потом
разрешаются права и владение колонией (`ColonyPermissionEventHandler:495,546`). То есть тихий баг прав
доступа, срабатывающий только в присутствии третьего мода.

**Что стало.** Одна строка на `instanceof FakePlayer`.

**Доказательство в артефакте:**
```
getPlayerOfFakePlayer(Player, Level):
   0: aload_0
   1: instanceof #7 // class net/fabricmc/fabric/api/entity/FakePlayer
   4: ifeq 22
```
Ни `getClass()`, ни `getName()`, ни `startsWith` в методе не осталось.

---

## 4. Рейдеры снова исчезают на Peaceful

**Файлы:** `apiimp/initializer/EntityInitializer.java`, `api/entity/mobs/AbstractEntityMinecoloniesMonster.java`

**Что было.** Переопределение `shouldDespawnInPeaceful() → true` удалено с пометкой «в 26.2 метода нет».
Метода действительно нет — но замена есть и она полноценная, чего пометка не сказала.

Проверено по ванильным исходникам 26.2 в `/opt/mc-src`:
* `Mob.checkDespawn()` (`world/entity/Mob.java:685-688`) делает
  `if (level().getDifficulty() == PEACEFUL && !getType().isAllowedInPeaceful()) discard();`;
* флаг ставится билдером `EntityType.Builder#notInPeaceful()` (`world/entity/EntityType.java:593-596`);
* по умолчанию `allowedInPeaceful = true` (`EntityType.java:479`), и `MobCategory.MONSTER` его **не меняет**;
* `checkDespawn` мод не переопределяет.

**Что видел игрок.** Переключение сложности на Peaceful переставало убирать уже заспавненных рейдеров.
Менеджер рейдов при этом новых не спавнит — игрок оставался один на один с бессмертной волной, которую
нечем разогнать.

**Что стало.** `.notInPeaceful()` добавлен во все билдеры рейдерских типов.

**Расхождение с аудитом (в бо́льшую сторону).** Аудит оценил объём в «~12 строк» и перечислил только
рейдовые типы. На самом деле переопределение `shouldDespawnInPeaceful` жило в
`AbstractEntityMinecoloniesMonster` — общей базе и рейдовых, и **лагерных** мобов
(`AbstractEntityBarbarian`, `AbstractEntityAmazon`, `AbstractEntityEgyptian`, `AbstractEntityPirate`,
`AbstractEntityNorsemen`, `AbstractDrownedEntityPirate` наследуются от неё напрямую, а
`AbstractEntityMinecoloniesRaider` — тоже от неё). Поэтому флаг проставлен **всем 36** типам категории
`MobCategory.MONSTER`, а не двенадцати. Иначе половина мобов (лагерные) осталась бы бессмертной на Peaceful.

**Доказательство в артефакте:** 36 вызовов `EntityType$Builder.notInPeaceful` в `EntityInitializer`,
ровно по числу `MobCategory.MONSTER`-билдеров в том же классе (тоже 36), каждый непосредственно перед
`build(...)`/`putstatic` своего типа. Полный список покрытых типов:
```
AMAZON AMAZONCHIEF AMAZONSPEARMAN ARCHERBARBARIAN ARCHERMUMMY ARCHERPIRATE BARBARIAN
CAMP_AMAZON CAMP_AMAZONCHIEF CAMP_AMAZONSPEARMAN CAMP_ARCHERBARBARIAN CAMP_ARCHERMUMMY
CAMP_ARCHERPIRATE CAMP_BARBARIAN CAMP_CHIEFBARBARIAN CAMP_CHIEFPIRATE CAMP_DROWNED_ARCHERPIRATE
CAMP_DROWNED_CHIEFPIRATE CAMP_DROWNED_PIRATE CAMP_MUMMY CAMP_NORSEMEN_ARCHER CAMP_NORSEMEN_CHIEF
CAMP_PHARAO CAMP_PIRATE CAMP_SHIELDMAIDEN CHIEFBARBARIAN CHIEFPIRATE DROWNED_ARCHERPIRATE
DROWNED_CHIEFPIRATE DROWNED_PIRATE MUMMY NORSEMEN_ARCHER NORSEMEN_CHIEF PHARAO PIRATE SHIELDMAIDEN
```
(36 различных типов). В `AbstractEntityMinecoloniesMonster` на месте удалённого override оставлен
комментарий с указанием, куда переехало поведение — чтобы следующий аудит не открывал вопрос заново.

---

## 5. `RecentTargetCache.getExtraCost` — часы больше не читаются на каждый узел A*

**Файлы:** `core/entity/pathfinding/RecentTargetCache.java`, `core/entity/pathfinding/pathjobs/AbstractPathJob.java`

**Что было.** `getExtraCost` безусловно звал `cleanup()`, а `cleanup()` начинается с
`System.currentTimeMillis()`. Зовётся `getExtraCost` из `getEndNodeScoreWithExtraCost`, а тот — из основного
цикла поиска (`AbstractPathJob:396,424,497`), то есть **один раз на каждый раскрытый узел**, до
`MAX_NODES = 8000` за задание, на **единственном** потоке поиска пути
(`Pathfinding.java:50` — `ThreadPoolExecutor(1, 1, ...)`).

**Что видел игрок.** Прямое снижение пропускной способности поиска пути на длинных маршрутах: пока один
рабочий считает длинный путь, все остальные ждут в очереди.

**Что стало.** `cleanup()` вынесен из горячего пути и зовётся один раз на задание — первой строкой
`AbstractPathJob.search()`. Срок жизни записей 200 секунд, заданий в секунду много, так что раз на задание
более чем достаточно; чистка кэша к тому же не корректностная операция. Файл был идентичен апстримовому —
это апстримовый недосмотр, а не регрессия порта.

**Измерение (не утверждение).** Модель — построчная копия `RecentTargetCache`, `BlockPos` заменён на
`record Pos(int,int,int)`; Java 25, три прогона, 2000 заданий по 8000 узлов на прогон. «После» моделирует
именно то, что уехало в артефакт: `cleanup()` один раз на задание плюс чистый `cache.get` на узел.

| прогон | до (на задание 8000 узлов) | после | выигрыш |
|---|---|---|---|
| 0 | 0.309 мс | 0.025 мс | 0.285 мс (12.5×) |
| 1 | 0.279 мс | 0.027 мс | 0.252 мс (10.2×) |
| 2 | 0.277 мс | 0.025 мс | 0.252 мс (10.9×) |

Поштучно: 38.0-42.6 нс/вызов до против 2.6-5.0 нс/вызов после. Это воспроизводит замер аудита
(36.4-38.5 нс до, 2.8-3.1 нс после) — цифры сошлись, вывод аудита подтверждён независимо.
Оговорка аудита про среду в силе: `currentTimeMillis` в этом контейнере дороже обычного (~28 нс),
но относительный вывод от этого не зависит.

**Доказательство в артефакте:**
```
RecentTargetCache.getExtraCost(BlockPos):     // cleanup встречается 0 раз
   0: getstatic  cache
   4: invokeinterface Map.get
   9: checkcast  TargetInfo
  13: ifnonnull 19 / 17: dconst_0 / 18: dreturn
  20: getfield   TargetInfo.extraCost / 23: dreturn

AbstractPathJob.search():
   0: invokestatic RecentTargetCache.cleanup:()V   <- первая инструкция
```
`RecentTargetCache.cleanup` встречается в `AbstractPathJob` ровно 1 раз.

---

## 6. Марксман: вывод «выстрел из арбалета» убран — РЕШЕНИЕ в пользу паритета с 1.21.1

**Файлы:** `core/entity/ai/combat/CombatUtils.java`, `core/entity/ai/workers/guard/RangeCombatAI.java`

Это **решение**, а не только починка, и записано оно здесь именно как решение, чтобы его можно было
осознанно отменить, а не наткнуться на него.

**Что было.** Апстрим: `final double distanceMultiplier = arrow.shotFromCrossbow() ? 0.05 : AIM_SLIGHTLY_HIGHER_MULTIPLIER;`.
В 26.2 у `AbstractArrow` арбалетного флага нет вообще (grep по
`/opt/mc-src/net/minecraft/world/entity/projectile/arrow/AbstractArrow.java` на «crossbow» пуст), поэтому
ветку пришлось разрешать вручную. Порт разрешил её выводом по оружию стрелка:
`arrow.getOwner() instanceof LivingEntity s && s.getMainHandItem().getItem() instanceof CrossbowItem`.

**Почему это было изменением поведения.** Проверено по оракулу:
* `setShotFromCrossbow` в дереве 1.21.1 **не встречается ни разу**;
* единственное место, которое выглядит как установка флага, — `RangeCombatAI.java:213`:
  `arrow.shotFromCrossbow();` — это **геттер, вызванный как оператор**, результат отброшен. Тот же метод
  на `CombatUtils.java:89` используется в тернарнике по значению, так что это именно геттер;
* стрела строится в `createArrowForShooter` как `ModEntities.MC_NORMAL_ARROW` и стреляется здесь же,
  минуя ванильный `CrossbowItem`, который единственный этот флаг ставит.

Вывод: в 1.21.1 `shotFromCrossbow()` **всегда возвращал false**. Апстрим явно хотел помечать болты
марксмана и не пометил. Множитель у всех стрел был `AIM_SLIGHTLY_HIGHER_MULTIPLIER = 0.18`, звук — всегда
`ARROW_SHOOT`.

А вот у порта условие для марксмана **истинно**: `JobMarksman.getEquipmentType()` = `ModEquipmentTypes.crossbow`,
который определён как `itemStack.getItem() instanceof CrossbowItem`. То есть порт молча уменьшил марксману
компенсацию гравитации с 0.18 до 0.05 — примерно вчетверо, стрелы недолетают по дальним целям — и добавил
`SoundEvents.CROSSBOW_SHOOT` на позиции **цели** на каждый выстрел.

**Решение.** Восстановлен паритет с 1.21.1: множитель всегда `AIM_SLIGHTLY_HIGHER_MULTIPLIER`, звук всегда
`ARROW_SHOOT`. Обоснование: это **порт**, и цель — верность поведению апстрима в рантайме, а не тому, что
апстрим, судя по коду, хотел написать. Тихое изменение баланса — не то, что отгружают по недосмотру.
`CROSSBOW_SHOOT` на позиции цели уходит вместе с ветвью, и это тоже апстримовое поведение.

**Как отменить осознанно.** Если 0.05 всё же нужен — это балансное изменение, и оно должно ехать отдельным
коммитом с явной формулировкой «марксман стреляет по настильной траектории», а не восстановлением ветки.
Заодно оно пересекается с B-7 (`MeleeCombatAI:353-356`, ×2 урона на низком здоровье) и R-8 — балансный
пакет стоит рассматривать целиком.

Комментарии в обоих файлах переписаны так, чтобы они соглашались друг с другом: в `RangeCombatAI` теперь
сказано, почему `CombatUtils` сворачивает флаг в константу, а не выводит его заново.

**Доказательство в артефакте:**
```
CombatUtils.shootArrow(...):
  83: ldc2_w #106  // double 0.18d     <- константа, ветки нет
  94: ldc2_w #106  // double 0.18d
 113: invokevirtual AbstractArrow.shoot(DDDFF)V
 117: getstatic SoundEvents.ARROW_SHOOT
 141: invokevirtual LivingEntity.playSound(...)
```
Во всём классе `CombatUtils`: ссылок на `CROSSBOW_SHOOT` — 0, ссылок на `CrossbowItem` — 0,
ссылок на `ARROW_SHOOT` — 1.

---

## 7. `skipSearch` больше не обрывает поиск цели

**Файлы:** `core/entity/ai/combat/TargetAI.java`, `core/entity/ai/workers/guard/DruidCombatAI.java`

**Что было.** В цикле поиска цели `if (skipSearch(entity)) { return false; }` — выход из **всего** поиска.
`skipSearch` переопределён в `MeleeCombatAI`, `RangeCombatAI`, `DruidCombatAI` и возвращает `true`, когда в
списке нашёлся спящий страж (тогда же ставится `parentAI.setWakeCitizen(citizen)`).

**Что видел игрок.** Если в зоне поиска есть хоть один спящий страж, весь проход обрывается — реальные враги
из того же списка не рассматриваются вообще. Порядок списка — порядок обхода чанков, то есть какой именно
спящий оборвёт проход, недетерминировано. При ночном налёте на казармы бодрствующая стража не реагирует на
рейдеров, пока не разбудит соседей.

**Что стало.** `return false` → `continue`, «кого будить» запоминается флагом. Флаг `skipped` гарантирует,
что `skipSearch` (а значит и `setWakeCitizen`, который регистрирует `AIOneTimeEventTarget(GUARD_WAKE)`)
срабатывает **не более одного раза за проход** — как и раньше. Побудка при этом не теряется: она едет через
отдельный one-time target и не зависит от того, что вернул `searchNearbyTarget`. Теперь страж и будит
соседа, и видит врага.

**Расхождение с аудитом.** Аудит указал одно место (`TargetAI.java:157`). Мест **два**: `DruidCombatAI`
не наследует `searchNearbyTarget`, а имеет собственную копию цикла с тем же `return false`
(`DruidCombatAI:281-284` на базе). Правка внесена в обе.

**Доказательство в артефакте:**
```
TargetAI.searchNearbyTarget():
  82: iload_3 / 83: ifne 100          <- skipped уже был -> skipSearch не зовём
  89: invokevirtual skipSearch(LivingEntity)
  92: ifeq 100
  95: iconst_1 / 96: istore_3         <- skipped = true
  97: goto 49                         <- continue, НЕ ireturn

DruidCombatAI.searchNearbyTarget():
  96: invokevirtual skipSearch(LivingEntity)
  99: ifeq 108
 102: iconst_1 / 103: istore 4
 105: goto 55                         <- continue
```

---

## 8. Страж обходит четыре стороны по кругу, а не бросает кубик

**Файл:** `core/entity/ai/combat/TargetAI.java`

**Что было.** `getSearchArea()` строил бокс вокруг
`Direction.from3DDataValue(user.getRandom().nextInt(4) + 2)` — случайная горизонтальная сторона; в неё бокс
вытянут на `getSearchRange()`, в противоположную — только на `DEFAULT_VISION`.

**Что видел игрок.** Поиск идёт раз в 80 тиков (`AttackMoveAI:44`). Верхней границы на «когда же он
посмотрит в нужную сторону» нет вообще: четыре решки подряд — это 320 тиков (16 секунд), а «никогда» имеет
ненулевую вероятность. Рейдер может стоять за спиной стража и не быть замеченным.

**Что стало.** Детерминированный обход четырёх направлений счётчиком; стартовый индекс по-прежнему
случайный, чтобы соседние стражи не сканировали синхронно. Худший случай — ровно 4 прохода. Стоимость та же:
один бокс, один `getEntitiesOfClass` за проход (проверено — `getSearchArea()` вызывается ровно один раз за
проход в каждом из двух циклов поиска, других вызывающих и переопределений нет).

**Доказательство в артефакте:**
```
TargetAI.getSearchArea():
   9: getfield searchDirectionIndex
  12: ifge 35
  24: invokeinterface RandomSource.nextInt(I)I   <- только на первом проходе
  29: putfield searchDirectionIndex
  35..44: searchDirectionIndex = (searchDirectionIndex + 1) % 4
  53: invokestatic Direction.from3DDataValue(I)
```

---

## 9. Переписаны вводящие в заблуждение PORT-NOTE

**Файлы:** `api/entity/other/AbstractFastMinecoloniesEntity.java` (три места),
`core/entity/ai/workers/production/EntityAIStructureMiner.java`, `.../EntityAIQuarrier.java`

Комментарии не удалены, а переписаны на то, что есть на самом деле. Комментарий вида «проверено, ничего не
потеряно, вот почему» стоит дороже отсутствующего: он не даёт следующему аудиту открыть вопрос заново.

* **`updateFluidOnEyes`.** Старый текст: троттлинг «раз в 20 тиков» потерян, состояние жидкости у глаз
  считается с ванильной частотой. Неверно. В 26.2 расчёт «глаза в жидкости» переехал внутрь
  `EntityFluidInteraction#update` (`/opt/mc-src/.../EntityFluidInteraction.java:32-70`, поле
  `Tracker.eyesInside`), а у этого метода **единственный вызывающий во всей игре** —
  `Entity#updateFluidInteraction` (`Entity.java:1643-1644`); проверено грепом по всему `/opt/mc-src`.
  Этот метод порт **по-прежнему переопределяет и троттлит**, раз в 10 тиков. Работа, которую апстрим
  троттлил дважды, теперь троттлится один раз и чаще. Действий не требуется.
* **`sendDebugPackets`.** Метода в 26.2 нет — и работы, которую апстрим подавлял, тоже нет: grep по
  `/opt/mc-src/net/minecraft/world/entity/Entity.java` даёт 0 совпадений. Пустой override апстрима ничего
  здесь не экономил. Стоимость потери — нуль.
* **`updateSwimAmount`.** Часть про private+final верна и оставлена. Переоценена была цена: тело метода
  (`/opt/mc-src/.../LivingEntity.java:3455-3462`) — копия float, вызов `isVisuallySwimming()` и
  `Math.min`/`Math.max`, единицы наносекунд на сущность в тик. Ради этого первый в проекте миксин не нужен.
  Помечено как сознательно оставленное.
* **`ItemAbilities` у шахтёра и каменолома.** Старый текст висел **на уровне класса** и начинался со слова
  DISABLED — читалось как рубильник на весь класс. На деле изменилось одно выражение: два
  `canPerformAction(PICKAXE_DIG / SHOVEL_DIG)` в `getRenderMeta` стали
  `stack.is(ItemTags.PICKAXES / SHOVELS)`, а результат уходит **только** в строку `renderData`, то есть
  решает, какая иконка инструмента нарисована у гражданина на поясе. Ни одно решение AI, ни добыча, ни
  выбор инструмента этого не читают. Единственная видимая разница — модовая кирка, не входящая ни в один
  тег, не рисует иконку.

**Уточнение к аудиту.** Аудит перечислил в 1.В-6 ещё и `EntityAIWorkNether.java:141-149`. Там та же замена
`ItemAbilities` → теги (проверено по оракулу — было `canPerformAction`, стало `stack.is`), но **никакого
вводящего в заблуждение комментария нет** — замена сделана молча. Переписывать нечего.

---

## 10. Курьер: `HashSet` вместо линейного поиска по списку

**Файл:** `core/entity/ai/workers/service/EntityAIWorkDeliveryman.java`

**Что было.** `itemsToDeliver` собирался в `List`, а затем на **каждый** слот инвентаря делался
`itemsToDeliver.contains(new ItemStorage(...))` — линейный проход по списку на слот.

**Что стало.** `Collectors.toSet()` и `Set.contains`; `getStackInSlot(i)` вынесен в локальную переменную
(вызывался дважды на слот). Ответы идентичны: членство и там и там решается через
`ItemStorage#equals`. Совместимость `hashCode`/`equals` проверена — `hashCode` = `Objects.hash(stack.getItem())`,
`equals` сравнивает предмет плюс опционально damage/NBT, то есть равные объекты всегда дают равный хэш, а
`equals` симметричен (`this.shouldIgnoreX || that.shouldIgnoreX`). `ItemStorage` и так используется как
ключ `HashMap` в соседнем коде.

**Доказательство в артефакте:** в классе `EntityAIWorkDeliveryman` — `Collectors.toSet` и
`java/util/Set.contains`; `java/util/List.contains` в методе не осталось.

---

## 17. Убраны вложенные стримы в подсчёте нужных материалов

**Файл:** `core/entity/ai/workers/AbstractEntityAIStructure.java` (`hasListOfResInInvOrRequest`)

**Что было.** Два вложенных стрима:
* предикат, применяемый к каждому занятому слоту инвентаря, открывал внутри себя новый
  `requestedMap.keySet().stream().anyMatch(...)`;
* `removeIf` по `requestedMap` открывал `foundStacks.stream().anyMatch(...)` на каждую запись.

O(n·m) с аллокацией стрима на элемент.

**Что стало.** Ключи вынесены в массив один раз, оба `anyMatch` заменены обычными циклами. Семантика
идентична: тот же компаратор `ItemStackUtils.compareItemStacksIgnoreStackSize`, тот же порядок, то же
короткое замыкание. Поведение не меняется вообще — меняется только количество аллокаций. Вызывается на
смене этапа стройки, а не каждый тик, поэтому эффект умеренный; правка бралась как бесплатная и безрисковая.

Пункт был отложен только потому, что файл правил другой агент; тот закончил (`322573c3`), блокировка снята.
Номера строк в аудите к этому моменту уехали — код искался по содержимому (комментарий
«Why predicate based search…»), а ссылка в `AI-AUDIT.md` исправлена на актуальную.

**Доказательство в артефакте:** `Stream.anyMatch` в классе `AbstractEntityAIStructure` — **0** вызовов
(было 2). Тело перенесённого предиката — обычный обход массива:
```
lambda$hasListOfResInInvOrRequest$1(ItemStack[], ItemStack):
   3: arraylength
  17: aaload
  23: invokestatic ItemStackUtils.compareItemStacksIgnoreStackSize
  32: iconst_1 / 33: ireturn      <- короткое замыкание сохранено
  37: goto 8
```

---

## 18. Лесоруб ломает листву и по дороге в зону

**Файл:** `core/entity/ai/workers/production/EntityAIWorkLumberjack.java`

**Что было.** Апстримовый TODO: «On walking to the zone (no tree found) leaves are not getting broken».
Разобрано: пара `checkIfStuck()` / `tryUnstuck()` — это на самом деле не «застрял», а «идя по пути, расчищай
листву впереди» (`checkIfStuck` возвращает true всё время, пока навигация активна и в пути остались узлы;
`tryUnstuck` минует блоки из `BlockTags.LEAVES` и `ModTags.hugeMushroomBlocks` на трёх ближайших узлах пути).
Звалась эта пара **только** из `chopTree()`, то есть только когда дерево уже найдено. А `PathJobFindTree`
уводит лесоруба в сторону зоны поиска ещё до того, как дерево найдено, — и на этом отрезке листву не ломал
никто.

**Что видел игрок.** Лесоруб уходит в крону, застревает, и его вытаскивает `PathingStuckHandler` —
телепортом. На экране это рывок рабочего.

**Что стало.** Та же расчистка вызывается в начале `findTree()`. Ломать безусловно безопасно: вход в
состояние `LUMBERJACK_SEARCHING_TREE` идёт через `PREPARING` → `prepareForWoodcutting`, который не пускает
дальше без топора (`checkForToolOrWeapon(ModEquipmentTypes.axe)`).

**Доказательство в артефакте:**
```
EntityAIWorkLumberjack.findTree():
  10: invokeinterface ICitizenData.setVisibleStatus(SEARCH)
  16: invokevirtual checkIfStuck:()Z
  19: ifeq 26
  23: invokevirtual tryUnstuck:()V
  26: getfield pathResult ...
```

---

# Отклонённые утверждения аудита

Ни одно утверждение аудита по пунктам 1-10, 17, 18 при перепроверке не развалилось — все двенадцать
подтвердились по оракулу `/home/user/minecolonies/1.21.1`, ванильным исходникам `/opt/mc-src` и артефакту
`fabric-api-0.154.2+26.2`. Разошлись три **детали**, и все три — в сторону расширения, а не отмены:

1. **Пункт 4, объём.** Аудит: «~12 строк», только рейдовые типы. Факт: базовый класс с удалённым
   `shouldDespawnInPeaceful` — общий для рейдовых **и лагерных** мобов, флаг нужен всем 36 типам
   `MobCategory.MONSTER`. Реализовано на 36. Если бы делали по букве аудита, лагерные мобы остались бы
   бессмертными на Peaceful, и следующий аудит нашёл бы ровно ту же ошибку.
2. **Пункт 7, число мест.** Аудит указал `TargetAI.java:157` и назвал `DruidCombatAI` только как одно из
   переопределений `skipSearch`. Факт: `DruidCombatAI` имеет **собственную копию цикла поиска** с тем же
   `return false`. Правка внесена в оба цикла.
3. **Пункт 9, состав.** Аудит отнёс к 1.В-6 три файла, включая `EntityAIWorkNether.java:141-149`. Факт:
   в Nether-файле замена `ItemAbilities` → теги сделана без комментария вообще, переписывать нечего.
   Переписаны два комментария (шахтёр, каменолом), как и указано в строке 9 ранжированной таблицы.

Отдельно, как наблюдение, а не как правка: в шапке `api/util/EntityUtils.java` висит блок
`PORT-TODO(structurize)`, утверждающий, что файл «исключён из компиляции через
`26.2/structurize-blocked.txt` и не портирован — всё в нём всё ещё 1.21.1 NeoForge-код». Это неверно:
`build.gradle` (комментарий на ~строке 129) прямо говорит, что исключение для 147 structurize-блокированных
файлов было опробовано и **отменено**, и `structurize-blocked.txt` этой сборкой не читается вовсе — файл
компилируется как все прочие. Комментарий не тронут: он вне списка пунктов этого прохода. Стоит снять
отдельной правкой.

---

# Не сделано намеренно — и почему

Это не забытое, а исключённое. Формулировки такие, чтобы через месяц было видно, что именно осталось и
на чём оно заблокировано.

| # | Что | Почему не сделано | Что разблокирует |
|---|---|---|---|
| **11** | Откат при повторной прокладке на недостижимую цель (`EntityNavigationUtils`) | Настоящая находка, но меняет **всю** ходьбу всех граждан. Компиляция здесь ничего не доказывает. | Прогон на живой колонии; логически идёт после пункта 16 |
| **12** | Формула урона топором/мечом (`MeleeCombatAI:338`, `EntityAIWorkNether:430`) | Нужны исходники NeoForge `IItemExtension#getDamage(ItemStack)`, которых в этом окружении нет (jar'а NeoForge нет, декомпилировать нечего). Плюс это балансное решение владельца, а не порта. | Одна декомпиляция `IItemExtension` из зависимостей дерева 1.21.1 + решение по балансу (вместе с B-7) |
| **13** | Миксин на `LivingEntity#detectEquipmentUpdates`, вернуть троттлинг | Требует **первого миксина в проекте**: `fabric.mod.json` ключа `mixins` не имеет (проверено в собранном артефакте), есть только `minecolonies.accesswidener`. Заводить миксин-инфраструктуру в порт, только что доведённый до рабочего состояния, — отдельная работа со своим риском, а не строчка в конце чужого прохода. | Отдельная задача «миксин-инфраструктура»; сам эффект мал (0.04-0.08 % бюджета тика на 200 граждан) |
| **14** | Курьер кладёт только запрошенное | Меняет поведение **всех** доставок. Нужна проверка в игре. | Игровой прогон |
| **15** | Пастух: «достаточно любого» вместо «немного всего» | Меняет поведение **всего** разведения. Нужна проверка в игре. | Игровой прогон |
| **16** | Инструментировать пул поиска пути | Инструментирование даёт ответ только против живой колонии; в этом окружении её нет. | Живой сервер. Это **первый шаг** следующей итерации: без счётчиков решения про кэш путей и число потоков — гадание |
| **19** | Статус `checkBedExists` | Нужны ванильные исходники 1.21.1, которых здесь нет (`/opt/mc-src` — только 26.2). Аудит прямо пишет, что вопрос не закрывается имеющимися данными. Код не тронут. | `LivingEntity#tick` из исходников 1.21.1 |
| **20** | `isLocalInstanceAuthoritative` — клиентское предсказание | Тоже требует первого миксина (метод `final` в 26.2). Расхождение чисто клиентское, серверного TPS не касается. | То же, что 13 |

Ни один из этих пунктов по ходу работы не превратился в «безопасно и необходимо прямо сейчас».
Ближе всего к этому пункт 11 — он же корень B-3 («стою и перепрокладываю»), самого заметного для игрока
симптома из оставшихся; но правка трогает `walkToPos`, `walkCloseToXNearY`, `walkAwayFrom` и
`walkToRandomPosHelper` разом, то есть каждый вид ходьбы каждого рабочего, и её нельзя принять по зелёной
сборке.

---

# Сборка и проверка

```
cd /home/user/minecolonies/26.2 && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 /opt/gradle-9.6.1/bin/gradle build
→ BUILD SUCCESSFUL
```

Артефакт: `build/libs/minecolonies-26.2-0.0.11.jar`. Байткод смотрелся так:
```
unzip -q build/libs/minecolonies-26.2-0.0.11.jar -d <dir>
javap -p -c <dir>/com/minecolonies/<путь к классу>.class
```
Все двенадцать правок подтверждены в отгружаемых классах, а не в исходнике.
Изменено 17 файлов, +259 / −96 строк.
