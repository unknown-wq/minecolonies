# Что сделано по аудиту вне AI-подсистемы (Fabric / MC 26.2)

Дата: 2026-08-01. Дерево: `/home/user/minecolonies/26.2`, база — `0b646f7a`, оракул: `/home/user/minecolonies/1.21.1`.
Исходный аудит: **`OPT-PLAN.md`** (там же ранжированный список, на номера которого ссылается этот файл).

Сделаны пункты **1-10** — весь ближайший PR. Каждое утверждение аудита перед правкой выводилось заново;
где выводы разошлись, это отмечено ниже. Сборка:
`JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 /opt/gradle-9.6.1/bin/gradle build -x test`, BUILD SUCCESSFUL.
Проверка каждого изменения — **по байткоду собранного артефакта** `build/libs/minecolonies-26.2-0.0.11.jar`,
а не по исходнику: у этого порта есть история дефектов, которые в исходнике выглядели правильно.

Итог: 57 файлов, +316 / −117 строк.

> Про коммит `d904a71f`. По ходу прохода кто-то извне снял чекпойнт рабочего дерева отдельным коммитом
> («Checkpoint the optimization audit fixes while the pass is still running», 38 файлов). Это не мой коммит;
> сам я `git commit` не звал. Полный набор правок этого прохода = `d904a71f` + незакоммиченное рабочее дерево,
> и весь он одинаково подтверждён по jar'у, собранному уже после всех правок.

---

## 1. Суточный цикл вернулся колониям вне верхнего мира

**Файлы:** `api/util/WorldUtil.java`, `core/colony/buildings/workerbuildings/BuildingNetherWorker.java`

**Что было.** `world.getDefaultClockTime() % 24000` вместо оракульного `getDayTime()`.

**Перепроверено самостоятельно, не по аудиту.** По ванильным исходникам:
`Level#getDefaultClockTime()` (`/opt/mc-src/net/minecraft/world/level/Level.java:891-893`) читает
`dimensionType().defaultClock()`, а `getClockTimeTicks` (`:895-897`) на `Optional.empty()` возвращает `0`.
У Нижнего мира `defaultClock` — ровно `Optional.empty()`
(`/opt/mc-src/net/minecraft/data/worldgen/DimensionTypes.java:99`, последний аргумент конструктора
`BuiltinDimensionTypes.NETHER`), у Энда — собственные часы `WorldClocks.THE_END` (`:130`).
Верный аналог — `Level#getOverworldClockTime()` (`:887-889`), и порт им **уже пользуется** в
`CitizenAI.java:245` и `CitizenSleepHandler.java:265`. Всё сошлось с аудитом.

**Что видел игрок.** `isDayTime(нижний мир)` = `0 <= 12600` = всегда true, значит
`Colony.checkDayTime()` не срабатывал никогда: ни `onNightFall` (рейдов вне верхнего мира нет вообще),
ни `updateCitizenSleep`, ни `checkCitizensForHappiness`, ни инкремент `day`. Колонии вне верхнего мира
разрешены по умолчанию.

**Что стало.** Две строки в `WorldUtil` и три в `BuildingNetherWorker` (там `snapTime` считался от тех же
нулей, то есть `Math.abs(0 - 0) >= 24000` не выполнялось никогда и период работы в Нижнем не тикал).
У оверворлда `defaultClock` = `WorldClocks.OVERWORLD`, так что для колонии в верхнем мире результат
побитово тот же — риск нулевой.

**Доказательство в артефакте:**
```
WorldUtil.isDayTime(Level):
   1: invokevirtual #147  // Method net/minecraft/world/level/Level.getOverworldClockTime:()J
   4: ldc2_w        #151  // long 24000l
   7: lrem
   8: ldc2_w        #153  // long 12600l
```
`getDefaultClockTime` в `WorldUtil` — **0** вхождений (было 2); в `BuildingNetherWorker` —
`getOverworldClockTime` 3 раза, `getDefaultClockTime` 0 раз.

---

## 2. Проверка «подписчик — не fake-player» снова работает

**Файл:** `core/colony/managers/ColonyPackageManager.java`

**Что было.** `if (subscriber.connection == null)` с пометкой «у Fabric нет типа FakePlayer».

**Перепроверено по самому jar'у, а не по аудиту.** Распакован
`fabric-api-0.154.2+26.2` → `META-INF/jars/fabric-events-interaction-v0-5.2.6+8f57f7ee9e.jar`,
класс `net/fabricmc/fabric/api/entity/FakePlayer.class` на месте. Байткод его конструктора:
```
protected FakePlayer(ServerLevel, GameProfile):
  10: invokespecial #53  // ServerPlayer."<init>"(...)
  14: new           #58  // class net/fabricmc/.../FakePlayerPacketListener
  19: invokespecial #60
  22: putfield      #63  // Field connection:Lnet/minecraft/server/network/ServerGamePacketListenerImpl;
  25: return
```
То есть `connection` у fabric-овского fake-player **всегда** непустой, и старая проверка не срабатывала
никогда. Утверждение аудита подтверждено независимо.

**Что видел игрок.** Ничего — симптом молчаливый. Fake-player оседал в `closeSubscribers` /
`importantColonyPlayers` (`HashSet<ServerPlayer>` с сильными ссылками), а `FakePlayer.FAKE_PLAYER_MAP`
кэширует их навсегда: утечка на время жизни сервера плюс полная сериализация вида колонии раз в секунду
в заглушку `send`, плюс форс полной пересылки вида всем через `newSubscribers`.

**Что стало.** `subscriber instanceof FakePlayer || subscriber.connection == null` в обеих точках.

**Судейское решение:** оставлена и старая половина условия. Оракул проверял только `instanceof FakePlayer`,
но соседний файл того же пакета (`ColonyPermissionEventHandler.java:251`) уже использует ровно эту пару
проверок, и она строго шире: покрывает и fake-player'ов чужих модов, не наследующих fabric-овский класс.
У настоящего игрока `connection` не бывает null, так что ложных срабатываний это не добавляет.

**Доказательство в артефакте:**
```
addCloseSubscriber(ServerPlayer):
   0: aload_1
   1: instanceof #278  // class net/fabricmc/fabric/api/entity/FakePlayer
   4: ifne 14
   7: aload_1
   8: getfield   #280  // Field ServerPlayer.connection:...
  11: ifnonnull 33
  14: (Log.warn + return)
```
Тот же байткод в `addImportantColonyPlayer`. Это настоящий `instanceof` против настоящего класса Fabric API,
короткое замыкание `||` на месте.

---

## 3. `Burnable` снова принимает изношенное топливо

**Файл:** `api/colony/requestsystem/requestable/Burnable.java`

**Что было.** `getFuel().contains(new ItemStorage(stack))`.

**Перепроверено.** `FuelValues#isFuel` — это `this.values.containsKey(itemStack.getItem())`
(`/opt/mc-src/net/minecraft/world/level/block/entity/FuelValues.java:26-28`), сравнение **по предмету**.
Однопараметрический `ItemStorage(stack)` (`api/crafting/ItemStorage.java:119-125`) ставит оба флага в
`false`, а `equals` (`:264-276`) тогда зовёт `compareItemStacksIgnoreStackSize(..., true, true)`, где есть
`itemStack1.getDamageValue() == itemStack2.getDamageValue()` (`api/util/ItemStackUtils.java:574`).
Множество `fuel` наполняется неповреждёнными стеками из креативных вкладок
(`CompatibilityManager.java:799-805`).

**Что видел игрок.** Любое ванильное топливо, у которого бывает прочность, переставало считаться топливом:
деревянные инструменты, лук, удочка, а также любой стек с кастомным именем или лишним компонентом.
Пекарь/шахтёр/плавильщик такое не принимал и перезаказывал.

**Что стало.** `new ItemStorage(stack, true, true)`. `equals` у `ItemStorage` симметрично-дизъюнктивен
(`!(this.shouldIgnoreDamageValue || that.shouldIgnoreDamageValue)`), так что флаги достаточно задать на
стороне запроса; `hashCode` = `Objects.hash(stack.getItem())`, то есть корзина и так по предмету и поиск
в `HashSet` остаётся O(1). Итог тождествен `FuelValues#isFuel`.

**Доказательство в артефакте:**
```
Burnable.matches(ItemStack):
  13: new           #114  // class com/minecolonies/api/crafting/ItemStorage
  17: aload_1
  18: iconst_1
  19: iconst_1
  20: invokespecial #116  // ItemStorage."<init>":(Lnet/minecraft/world/item/ItemStack;ZZ)V
  23: invokeinterface #119 // java/util/Set.contains
```
`iconst_1 iconst_1` — это именно трёхаргументный конструктор с `true, true`, а не однопараметрический.

---

## 4. Пакеты перестали слать мусорный хвост буфера

**Файлы:** `api/util/Utils.java` (новый хелпер) + 14 мест отправки.

**Что было.** `buf.writeByteArray(scratch.array())` — весь backing array вместо записанной части.

**Замер переснят заново, на том же netty из зависимостей сборки** (`io.netty:netty-buffer:4.2.15.Final`
из `~/.gradle/caches`, Java 25; `scratchpad/bench/BufWaste2.java`):

| записано байт | `array().length` | мусор | коэффициент |
|---|---|---|---|
| 40 | 256 | 216 | 6.40× |
| 250 | 256 | 6 | 1.02× |
| 256 | 256 | 0 | 1.00× |
| 257 | 512 | 255 | 1.99× |
| 2500 | 4096 | 1596 | 1.64× |
| 33000 | 65536 | 32536 | 1.99× |
| 60000 | 65536 | 5536 | 1.09× |

Сплошной прогон 32..65536: **1.3333×** (2 147 515 920 полезных байт против 2 863 325 440 отправленных) —
цифры сошлись с аудитом до последнего разряда.

**Расхождение с аудитом — в сторону усиления вывода.** Аудит дал одно число по одному сплошному прогону,
что зависит от выбранного распределения размеров. Дополнительно посчитана амплификация **внутри каждой
октавы ёмкости**:

```
octave [  256,  512): 1.3325x     octave [ 4096,  8192): 1.3333x
octave [  512, 1024): 1.3329x     octave [ 8192, 16384): 1.3333x
octave [ 1024, 2048): 1.3331x     octave [16384, 32768): 1.3333x
octave [ 2048, 4096): 1.3332x     octave [32768, 65536): 1.3333x
```

Она везде 4/3, а не только в среднем по прогону. Это ровно аналитический результат
(`2^(k+1) / E[размер в октаве] = 2^(k+1) / (1.5·2^k) = 4/3`), и значит вывод **не зависит от того, какого
размера пакеты у мода на самом деле**, пока размеры внутри своей октавы распределены хоть сколько-нибудь
равномерно. Экономия — 25 % отправляемых байт, худший случай (один байт за границей степени двойки) — 50 %.

**Что стало.** Общий хелпер вместо 14 копипаст:
```java
public static void writeBufferContents(final FriendlyByteBuf out, final ByteBuf source)
{
    final int length = source.writerIndex();
    out.writeVarInt(length);
    out.writeBytes(source, 0, length);
}
```

**Судейское решение — почему `writeBytes(ByteBuf, int, int)`, а не `array()/arrayOffset()`.** Аудит
предлагал вариант через `array()`. Он работает, но падает на прямом (direct) буфере, а сигнатура хелпера
принимает любой `ByteBuf`. Абсолютное чтение из исходного буфера не двигает его `readerIndex` и не требует
heap-хранилища. Формат провода не меняется: `FriendlyByteBuf.writeByteArray` — это ровно
`VarInt.write(len)` + `writeBytes` (`/opt/mc-src/net/minecraft/network/FriendlyByteBuf.java:287-290`),
а читающая сторона делает `VarInt.read` + `readBytes` (`:296-302`), то есть остаётся нетронутой.

**Что НЕ тронуто и почему.** `core/datalistener/QuestJsonListener.java:66` тоже зовёт `writeByteArray`,
но там аргумент — настоящий `byte[]` из `String.getBytes()`, у которого `length` и есть длина содержимого.
Мусора нет, править нечего. Проверено в артефакте: перед вызовом стоит `String.getBytes:()[B`.

**Доказательство в артефакте:**
```
Utils.writeBufferContents(FriendlyByteBuf, ByteBuf):
   1: invokevirtual #271  // io/netty/buffer/ByteBuf.writerIndex:()I
   4: istore_2
   7: invokevirtual #277  // FriendlyByteBuf.writeVarInt:(I)
  13: iconst_0
  14: iload_2
  15: invokevirtual #283  // FriendlyByteBuf.writeBytes:(Lio/netty/buffer/ByteBuf;II)
```
По пакетам `core/network`, `core/research`, `core/colony/crafting`:
`Utils.writeBufferContents` — **14** вызовов, `FriendlyByteBuf.writeByteArray` — **0**.
В `ColonyViewMessage.toBytes` последняя инструкция перед `return` — `invokestatic Utils.writeBufferContents`.

---

## 5. Сняты 14 ложных шапок «файл исключён из компиляции и не портирован»

**Файлы:** `api/util/{ColonyUtils, EntityUtils, BlockPosUtil, LoadOnlyStructureHandler,
CreativeBuildingStructureHandler}.java`, `api/colony/buildings/{IBuilding, ISchematicProvider}.java`,
`api/colony/buildings/views/IBuildingView.java`, `api/colony/managers/interfaces/IEventStructureManager.java`,
`api/colony/workorders/IWorkOrder.java`, `api/tileentities/{AbstractTileEntityColonyBuilding,
AbstractTileEntityPlantationField}.java`, `api/crafting/{RecipeStorage, GenericRecipe}.java`.

**Что было.** «This file is excluded from compilation via 26.2/structurize-blocked.txt and has NOT been
ported to 26.2 — everything in it is still 1.21.1 NeoForge code.»

**Почему это ложь — три независимых признака, все перепроверены.**
1. `build.gradle:128-133` прямо пишет, что исключение было опробовано и отменено, и что
   «this build never reads it». Грепом по `build.gradle`: `structurize-blocked` встречается **один раз**,
   и это тот самый комментарий — никакого `exclude` по этому файлу в сборке нет.
2. В файлах стоит портированный код (`ColonyUtils.java` импортирует `net.fabricmc.loader.api.FabricLoader`).
3. Все 14 классов **лежат в отгружаемом jar'е** — проверено поимённо в
   `build/libs/minecolonies-26.2-0.0.11.jar`, 14 из 14 present.

**Что стало.** Шапки не удалены, а переписаны на то, что есть на самом деле — в том же виде, в каком уже
сформулированы 5 корректных шапок этого же маркера (`AbstractBlockHut`, `ItemNbtCalculator`, три
провайдера датагена): «re-check list only … build.gradle:128-133 states outright that this build never
reads it». Комментарий «проверено, вот почему» стоит дороже отсутствующего: он не даёт следующему аудиту
открыть вопрос заново. Маркер `PORT-TODO(structurize)` намеренно оставлен во всех 19 файлах — он полезен
как грепаемая метка «сверить с настоящим API».

**Доказательство:** строк `NOT been ported to 26.2` в дереве — **0** (было 14); файлов с маркером
`PORT-TODO(structurize)` — по-прежнему 19.

---

## 6. Убрано двойное обновление списков в GUI

**Файлы:** `core/client/gui/modules/building/WindowBuilderResModule.java`,
`core/client/gui/WindowResourceList.java`, `core/client/gui/modules/building/WindowListRecipes.java`

**Что было.** В `onUpdate()` этих окон, поверх `super.onUpdate()`, руками звался **принудительный**
`refreshElementPanes()`.

**Перепроверено по байткоду BlockUI** (`.staged-libs/.../blockui-0.0.1.jar`), а не по именам методов:
```
ScrollingList.onUpdate():
   1: invokespecial ScrollingView.onUpdate:()V
   5: iconst_0
   6: invokevirtual refreshElementPanes:(Z)V     <- force = false

ScrollingList.refreshElementPanes():
   1: iconst_1
   2: invokevirtual refreshElementPanes:(Z)V     <- force = true
```
В `ScrollingListContainer.refreshElementPanes(...)` `force` влияет ровно на две вещи: ранний выход по
`DataProvider.shouldUpdate()` (offset 28-42) и вызов `shouldUpdate(int)` на строку (offset 282-295).
Переопределений `shouldUpdate` в `core/client/` — **ноль** (грепом), а дефолт обоих — `true`. Значит
`force=true` и `force=false` сегодня делают ровно одно и то же, и ручной вызов был чистым дублем.

`View.onUpdate()` рекурсивно зовёт `Pane.onUpdate()` у детей, но только при `shouldDraw()`
(offset 34-42). Поэтому дополнительно проверено, что во всех трёх окнах список — **прямой всегда видимый
ребёнок окна**: `layouthuts/layoutbuilderres.xml`, `windowresourcescroll.xml`,
`layouthuts/layoutlistrecipes.xml` — `<list>` лежит на верхнем уровне `<window>`, без `visible="false"`
и без переключаемых родительских view. Значит `ScrollingList.onUpdate` срабатывает каждый клиентский тик,
и снятие ручного вызова не может оставить список несвежим.

**Расхождение с аудитом.** Аудит назвал **четыре** места, четвёртое — `townhall/WindowInfoPage.java:182`.
Оно не подходит: это тело `deleteWorkOrder(Button)`, обработчик нажатия, а не `onUpdate`. Ни выигрыша
(вызывается на клик, не на тик), ни безопасности (список только что изменили, обновить его надо сразу)
там нет. Пункт сделан на **трёх** местах. `onUpdate` у `WindowInfoPage` вообще не содержит
`refreshElementPanes`, так что дубля там и не было.

**Доказательство в артефакте:** во всех трёх `onUpdate()` последняя инструкция — `return`, вызова
`refreshElementPanes` не осталось ни одного. Пример:
```
WindowResourceList.onUpdate():
   1: invokespecial AbstractWindowSkeleton.onUpdate:()V
   5: invokevirtual pullResourcesFromHut:()V
   8: return
```

**Сверх аудита, в том же файле, без изменения поведения.** `WindowInfoPage.updateWorkOrders()` вызывается
из `onUpdate`, то есть 20 раз в секунду, пока открыта ратуша, и делал
`workOrders.addAll(colony.getWorkOrders().stream().filter(...).collect(Collectors.toList()))` — стрим,
захват лямбды и целый промежуточный список на каждый тик, чтобы потом скопировать его во второй список.
Заменено обычным циклом с тем же предикатом. В артефакте: ссылок на `java/util/stream` в классе — **0**,
тело метода — `List.clear` → `Collection.iterator` → `shouldShowIn` → `List.add`.

---

## 7. Серверный тик перестал строить список всех колоний ради пустых методов

**Файлы:** `core/event/FMLEventHandler.java`, `core/colony/ColonyManager.java`, `core/colony/Colony.java`

**Что было.** `ServerTickEvents.START_SERVER_TICK` → `ColonyManager.onServerTick` →
`for (IColony c : getAllColonies()) c.onServerTick(server);`, где `getAllColonies()` на каждый вызов
аллоцирует `ArrayList`, обходит все `ServerLevel`, на каждом дёргает `getOrComputeSaveData` и
`ColonyList.getCopyAsList()` (ещё один `ArrayList` с полным копированием). А тела
`Colony.onServerTick` (`:1115`) и `ColonyView.onServerTick` (`:1136`) — **пустые**, как и в оракуле
(`1.21.1/.../Colony.java:1106-1109`). Итого 1 + N списков в тик, 80 списков в секунду на трёх измерениях,
чтобы вызвать методы с пустым телом.

**Что стало.** Регистрация убрана; на `START_SERVER_TICK` осталась только ссылка на метод
`DataPackSyncEventHandler.ServerEvents::load`.

**Судейское решение — метод не удалён, а отвязан.** `IColonyManager#onServerTick` и `IColony#onServerTick`
— публичное API, на которое может опираться аддон, и удалять их ради тика, который ничего не делает,
несоразмерно. Вместо этого `ColonyManager.onServerTick` оставлен рабочим (кто позовёт — получит корректную
рассылку) и снабжён комментарием, а в `FMLEventHandler` и в `Colony.onServerTick` записано, почему хук
снят и что надо сделать, если у `IColony#onServerTick` когда-нибудь появится тело.

**Проверено, что вместе с хуком ничего не потерялось.** Аудит сам оговаривал риск (пункт 14 раздела
«не проблемы»): статическое поле `ServerColonySaveData.registries` инициализируется побочно, из
`IServerColonySaveData.getOrComputeSaveData`, который дёргался как раз из `getAllColonies()` каждый тик.
Проверено, что этот побочный эффект сохраняется полностью: `ServerTickEvents.START_LEVEL_TICK` →
`ColonyManager.onWorldTick(level)` → `getColonies(level)` → `getColonySaveData(serverLevel)` →
`getOrComputeSaveData(w)` — то есть `setRegistries` + `computeIfAbsent(TYPE)` + `setOverworld` по-прежнему
выполняются **для каждого серверного уровня каждый тик**. Покрытие уровней у `START_LEVEL_TICK` и
`getAllColonies()` одинаковое (`MinecraftServer` тикает `getAllLevels()`). Экономия чистая: −(1 + N)
списков в тик, ничего не приобретено взамен.

**Доказательство в артефакте:**
```
FMLEventHandler.register():
   0: getstatic     ServerTickEvents.START_SERVER_TICK
   3: invokedynamic #0:onStartTick:()...StartTick

BootstrapMethods #0:
   REF_invokeStatic com/minecolonies/core/event/DataPackSyncEventHandler$ServerEvents.load:(MinecraftServer)V
```
Это прямая ссылка на метод, а не лямбда с телом: у неё нет `lambda$register$N`, то есть в обработчике
физически не осталось второго вызова. `IColonyManager.onServerTick` в классах `core/event/*` и
`core/colony/*` — **0** ссылок.

---

## 8. Защитные копии списка граждан убраны с тиковых путей

**Файлы:** `api/colony/managers/interfaces/ICitizenManager.java`, `core/colony/managers/CitizenManager.java`,
`core/colony/Colony.java`, `core/entity/citizen/citizenhandlers/CitizenHappinessHandler.java`,
`core/colony/buildings/modules/{CourierAssignmentModule, GuardBuildingModule, LivingBuildingModule,
QuarryModule, ChildrenBuildingModule}.java`

**Что было.** `CitizenManager.getCitizens()` — это `new ArrayList<>(citizens.values())`, полная копия на
каждый вызов, и её звали по нескольку раз подряд там, где хватало одного взгляда.

**Замер (не утверждение).** Модель: `HashMap<Integer, Citizen>` на N граждан, два способа доступа, по
одной JVM на способ (в первой версии стенда обе формы грелись вперемешку, и профиль инлайнинга сползал —
первый прогон давал бессмысленный результат; каждая форма вынесена в свой процесс). Java 25, 500 тыс.
прогревочных и 2 млн замерных проходов.

| N | `new ArrayList<>(values())` + обход | `unmodifiableCollection(values())` + обход | выигрыш |
|---|---|---|---|
| 25 | 185-236 нс | 96-99 нс | ~2.0× |
| 50 | 457-517 нс | 179-203 нс | ~2.5× |
| 200 | 1771-1889 нс | 734-753 нс | ~2.4× |

То есть на колонии в 200 граждан каждая лишняя копия — это ~1.1 мкс серверного потока и один `ArrayList`
плюс один `Object[]` в мусор.

**Что стало.** В `ICitizenManager` добавлен `Collection<ICitizenData> getCitizensUnmodifiable()` —
`Collections.unmodifiableCollection(citizens.values())`, с явным предупреждением в javadoc, что это живой
вид и его нельзя обходить там, где гражданин может добавиться или удалиться. Заменено:

* **`Colony.getOverallHappiness`** (пункт 8 аудита) — было три `getCitizens()` (размер, обход, размер),
  стало один `getCurrentCitizenCount()` (это буквально `citizens.size()`, метод уже существовал) и один
  обход по виду. Зовётся из `ColonyView.serializeNetworkData`, то есть раз в секунду на колонию.
* **`CitizenManager.onColonyTick`** — было шесть `getCitizens()`, стало четыре `getCurrentCitizenCount()`,
  один обход по виду и **одна** оставленная копия.
* **пять модулей зданий**, тикающих раз в 500 тиков **на каждое здание** — по одной копии на здание
  за тик, все пять чисто читающие.
* **`CitizenHappinessHandler.getSocialModifier` / `getGuardFactor`** — см. ниже, находка сверх аудита.

**Судейское решение — где копия ОСТАВЛЕНА, и почему.** Замена сделана только там, где доказано, что во
время обхода коллекция граждан не мутируется. Не тронуты:
* `CitizenManager.tickCitizenData` — `ICitizenData.update(tickRate)` доходит до
  `citizenDiseaseHandler.update`, то есть гражданин может умереть прямо в обходе;
* `CitizenManager.onColonyTick`, ветка `updateEntityIfNecessary` — она уходит в
  `spawnOrCreateCivilian` → `world.addFreshEntity` → `registerWithColony`, и это единственная ветка тика,
  которая вообще может тронуть карту; она к тому же срабатывает раз в ~5 минут, так что копия там ничего
  не стоит;
* `ColonyManager:180` (удаление колонии) — там копия обёрнута ещё раз явно и обязана остаться;
* `CitizenManager.updateCitizenMourn` — зовётся на смерть, не на тик; выигрыш нулевой, риск ненулевой.

Для пяти модулей мутация проверена по цепочке: `assignCitizen` → `assignedCitizen.add` + `onAssignment`
(`setHomeBuilding`/`setWorkBuilding` + `calculateMaxCitizens`, который ходит по **зданиям**, не по
гражданам) → `markDirty`. Ни `citizens.put`, ни `citizens.remove` на пути нет.

**Расхождение с аудитом — находка сверх списка, в том же классе.** Аудит перечислял для C-2 пять модулей и
`getOverallHappiness`. Не назван самый дорогой случай: `CitizenHappinessHandler.getSocialModifier` делал
**два** `getCitizens()` (один ради `.size()`), а `getGuardFactor` — ещё один. Обе — не обычные методы, а
**функции факторов счастья**, зарегистрированные в `ModHappinessFactorTypeInitializer:42-43` и
вычисляемые внутри `CitizenHappinessHandler.getHappiness` **на каждого гражданина**. То есть на колонии в
200 граждан один полный пересчёт счастья стоил 200 × 3 полных копий списка на 200 ссылок — квадрат,
~0.4 мс серверного потока и порядка 120 000 скопированных ссылок в мусор. Кэш `cachedHappiness` сбрасывается
в основном из `processDailyHappiness`, то есть всплеск приходится на смену суток. Заменено на один
`getCurrentCitizenCount()` и два обхода по виду; циклы чисто читающие.

Отдельно стоит отметить связь с пунктом 1: пока `checkDayTime` не работал вне верхнего мира,
`checkCitizensForHappiness` там не звался и этот квадрат не проявлялся. После починки пункта 1 он бы
проявился — так что эти две правки логично едут вместе.

**Доказательство в артефакте:**
```
Colony.getOverallHappiness():
   4: invokeinterface ICitizenManager.getCurrentCitizenCount:()I
   9: istore_1
  11: ifgt 18 / 14: ldc2_w 5.5d / 17: dreturn
  24: invokeinterface ICitizenManager.getCitizensUnmodifiable:()Ljava/util/Collection;
  86: iload_1 / 87: i2d / 88: ddiv / 89: dreturn
```
Ни одного `getCitizens()` в методе не осталось. По классам:

| класс | `getCitizens()` | `getCitizensUnmodifiable()` | `getCurrentCitizenCount()` |
|---|---|---|---|
| `Colony.getOverallHappiness` | 0 (было 3) | 1 | 1 |
| `CitizenManager.onColonyTick` | 1 (было 6) | 1 | 4 |
| `CitizenHappinessHandler` | 0 (было 3) | 2 | 1 |
| каждый из 5 модулей | 0 (было 1) | 1 | — |

```
CitizenManager.getCitizensUnmodifiable():
   1: getfield        citizens:Ljava/util/Map;
   4: invokeinterface java/util/Map.values:()Ljava/util/Collection;
   9: invokestatic    java/util/Collections.unmodifiableCollection
  12: areturn
```

---

## 9. Логи системы запросов больше не собирают строку под выключенным флагом

**Файлы:** `api/colony/requestsystem/manager/IRequestManager.java`,
`core/colony/requestsystem/management/manager/StandardRequestManager.java`,
`.../manager/wrapped/AbstractWrappedRequestManager.java`,
`.../handlers/{RequestHandler, ResolverHandler, ProviderHandler}.java`,
`.../requests/AbstractRequest.java`, `.../resolvers/StandardRetryingRequestResolver.java`

**Что было.** `log(String)` внутри проверяет `enableLogging` (по умолчанию выключен, конфиг
`rsEnableDebugLogging`), но аргумент собирался **до** вызова — конкатенация с `toString()` реквестов,
резолверов и UUID-токенов на каждое создание запроса, назначение и переназначение.

**Что стало.** В `IRequestManager` добавлен `boolean isLoggingEnabled()` (реализация в
`StandardRequestManager` — возврат поля, в `AbstractWrappedRequestManager` — делегирование), и все
собирающие строку вызовы обёрнуты в `if (manager.isLoggingEnabled()) { ... }`.

**Судейское решение — почему guard, а не `log(Supplier<String>)`.** Аудит предлагал оба варианта.
Supplier дешевле конкатенации, но не бесплатен: все эти лямбды **захватывающие**, то есть каждая — новый
объект на каждом вызове, даже когда логирование выключено. Guard даёт ровно ноль аллокаций в выключенном
состоянии, а это состояние по умолчанию. Цена — многословнее диффа; выбран guard.

**Расхождение с аудитом (в бо́льшую сторону).** Аудит насчитал **15** мест и перечислил их поимённо.
Мест **17**: не названы `ProviderHandler.java:80` и `:90`, где стоит не конкатенация, а
`String.format("Removing provider: %s", token)` — то есть форматирование, которое дороже конкатенации, и
которое выполнялось безусловно. Обёрнуты все 17.

**Доказательство в артефакте.** Проверялось не наличие вызова, а **порядок**: guard должен стоять до
сборки строки.
```
RequestHandler.createRequest(IRequester, Request):
  71: invokeinterface IStandardRequestManager.isLoggingEnabled:()Z
  76: ifeq 106                                  <- выключено: перепрыгиваем всё
  84: invokestatic  java/lang/String.valueOf    <- сборка строки уже ВНУТРИ guard'а
  88: invokestatic  java/lang/String.valueOf
```
```
StandardRetryingRequestResolver.tick():
  92: invokeinterface IRequestManager.isLoggingEnabled:()Z
  97: ifeq 112
 102: invokedynamic #3:accept:(...)Consumer;    <- лямбда даже не создаётся
 107: invokeinterface java/util/Set.forEach
 112: (log("Finished reassignment.") — константа, guard не нужен)
```
Счётчики `isLoggingEnabled` по классам: `ResolverHandler` 7, `RequestHandler` 5, `ProviderHandler` 2,
`AbstractRequest` 2, `StandardRetryingRequestResolver` 1 — суммарно 17, ровно по числу мест.
Три вызова с литеральной строкой (`"Starting reassignment."`, `"Finished reassignment."` и т. п.)
намеренно оставлены как есть: константа не аллоцирует.

---

## 10. Снято ложное «у Fabric нет FakePlayer» в `DynamicTreeCompat`

**Файл:** `api/compatibility/dynamictrees/DynamicTreeCompat.java`

**Что было.** Шапка утверждала, что класс заглушен по двум причинам: нет сборки Dynamic Trees под 26.2
**и** «Fabric has no `FakePlayer` equivalent (contract C4 — the whole NeoForge common-util layer is gone)».

Вторая половина ложна по тому же основанию, что пункт 2 (и что пункты 1-3 в `AI-FIXES.md`):
`net.fabricmc.fabric.api.entity.FakePlayer` лежит в том самом артефакте, от которого зависит порт, и это
же дерево уже использует его в `ThreatTable`, `TargetAI`, `EntityUtils`, `ColonyPermissionEventHandler` и
теперь `ColonyPackageManager`.

**Практических последствий у правки нет** — весь класс остаётся заглушкой, потому что настоящая блокировка
(нет jar'а Dynamic Trees под 26.2) никуда не делась. Смысл в другом: формулировка уже начала
воспроизводиться из файла в файл, и её надо было погасить в источнике. Шапка переписана так, что теперь
явно сказано, какая из двух причин настоящая, и что блокирует ровно одна.

**Доказательство:** правка чисто документационная, в байткоде javadoc не остаётся. Проверено по исходнику:
`Fabric has no FakePlayer equivalent` как утверждение — 0 вхождений; строка встречается один раз, в
кавычках, как цитата снятого утверждения.

---

# Расхождения с аудитом

Ни одно утверждение аудита по пунктам 1-10 при перепроверке не развалилось. Разошлись четыре **детали**:

1. **Пункт 6, состав.** Аудит назвал четыре принудительных `refreshElementPanes()` в `onUpdate`.
   Их **три**: `WindowInfoPage.java:182` — это `deleteWorkOrder(Button)`, обработчик клика, а не `onUpdate`;
   там вызов и нужен, и ничего не стоит. Пункт сделан на трёх местах.
2. **Пункт 9, число мест.** Аудит: 15. Факт: **17** — не названы `ProviderHandler.java:80,90`, где стоит
   `String.format`, то есть форматирование дороже конкатенации.
3. **Пункт 8, охват.** Аудит свёл пункт 8 к `getOverallHappiness`, а модули отнёс в отложенный пункт 13.
   Сделаны и модули, и — сверх обоих пунктов — `CitizenHappinessHandler.getSocialModifier/getGuardFactor`,
   который аудит не заметил вовсе и который стоит дороже всего остального в этой группе вместе взятого
   (квадрат по числу граждан).
4. **Пункт 4, сила вывода.** Число аудита (1.333×) воспроизвелось точно, но оно было получено на одном
   распределении размеров. Показано, что 4/3 держится внутри каждой октавы ёмкости по отдельности, то есть
   вывод не зависит от того, какие размеры пакетов у мода в реальности.

Дополнительно **сверх аудита**, в файле, который аудит и так упоминал, без изменения поведения:
`WindowInfoPage.updateWorkOrders()` (стрим + промежуточный список 20 раз в секунду → обычный цикл).

---

# Наблюдения — не правил, вне списка

1. **`StandardRetryingRequestResolver.java:191`**: внутри `successfully.forEach(t -> ...)` логируется `id`
   (поле резолвера), а не `t` — параметр лямбды не используется вообще. Плюс сама формулировка
   («Failed to reassign») навешена на множество, которое по построению содержит **успешно** переназначенные
   токены. Похоже на апстримовую описку, но это тело пункта **14** аудита («переписать `tick` одним
   проходом»), который помечен «потом» и требует игрового прогона. Строка только обёрнута guard'ом,
   содержимое не тронуто.
2. **Там же, `:159-161`**: `assignedRequests.get(t) < getMaximalTries()` и
   `Integer currentAttempt = assignedRequests.get(t)` — автораспаковка без проверки на null. Токен, попавший
   в `delays`, но выпавший из `assignedRequests`, даст NPE, которую проглотит `catch (Exception ex)` на
   `:170` вместе с настоящими ошибками переназначения. Тоже пункт 14, не трогал.
3. **`ColonyViewBuildingExtensionsUpdateMessage.toBytes`** аллоцирует новый
   `RegistryFriendlyByteBuf` **на каждое расширение** внутри цикла. Правка пункта 4 туда внесена (мусорный
   хвост больше не летит), но сама аллокация на элемент осталась — это уже переделка формата сообщения, а не
   двухстрочная правка.
4. **`CitizenManager.onColonyTick:599`**: переменная цикла названа `citizens` и затеняет одноимённое поле
   класса (`Map<Integer, ICitizenData>`), причём внутри цикла это `ICitizenData`. Читается плохо.
   Не переименовал: файл параллельно правит другой агент, и переименование ради стиля не стоит конфликта.

---

# Сборка и проверка

```
cd /home/user/minecolonies/26.2 && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 \
  /opt/gradle-9.6.1/bin/gradle build -x test
→ BUILD SUCCESSFUL
```

Артефакт: `build/libs/minecolonies-26.2-0.0.11.jar`. Байткод смотрелся так:
```
unzip -q build/libs/minecolonies-26.2-0.0.11.jar -d <dir>
javap -p -c <dir>/com/minecolonies/<путь к классу>.class
javap -p -v <dir>/com/minecolonies/core/event/FMLEventHandler.class   # для BootstrapMethods
```
Зависимости смотрелись распаковкой самих артефактов, а не по памяти:
`fabric-api-0.154.2+26.2` (включая вложенные `fabric-events-interaction-v0-5.2.6`,
`fabric-lifecycle-events-v1-4.1.3`) из `~/.gradle/caches`, `blockui-0.0.1.jar` из `.staged-libs`,
`netty-buffer-4.2.15.Final` для замера.

Все десять пунктов подтверждены в отгружаемых классах, а не в исходнике; два из них (5 и 10) —
документационные, и для них доказательством служит присутствие 14 классов в jar'е и грепы по дереву.
Изменено 57 файлов, +316 / −117 строк.
