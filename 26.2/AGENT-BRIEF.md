# Брифинг агента порта MineColonies → Fabric 26.2

Читаешь это первым, целиком. Затем — свою роль в задании.

## Что уже сделано, переделывать не надо

- Окружение стоит: Java 25, Gradle 9.6.1, `/opt/mc-src` (7055 файлов декомпилированной ванили 26.2).
- Исходник скопирован в `26.2/src/main/java` — **правишь здесь**. `1.21.1/` только для чтения.
- Переехавшие импорты ванили уже перенаправлены (148 штук).
- `ResourceLocation` → `Identifier` уже сделан целиком: 2087 ссылок, 1648 конструкторов
  переписаны на `Identifier.fromNamespaceAndPath(...)`.
- `IPayloadContext` → `PlayMessageContext`, `ModConfigSpec.*` → `ConfigValue.*` — сделано.
- AccessWidener готов и валиден: `26.2/src/main/resources/minecolonies.accesswidener`.

## Правила, нарушение которых ломает работу другим

1. **Не запускай Gradle.** Одна инвокация Loom одновременно, её делает интегратор.
   Проверяй себя офлайновым `javac` (см. ниже).
2. **Правь только файлы своей зоны.** Нашёл проблему в чужой — опиши в финальном отчёте,
   не трогай. Один владелец на файл.
3. **Не редактируй `1.21.1/`** — это эталон.
4. **Не правь `PORT-STATUS.md` и `PORT-PLAN.md`** — их пишет оркестратор.
5. Маркер `PORT-TODO(structurize)` в 147 файлах **больше не значит «не трогай»**. Библиотека
   приехала 31.07.2026, стабы сняты, эти файлы портируются как все. Маркер оставлен только
   как grep-список того, что стоит перепроверить против настоящего API. Текст маркера в
   файлах своей зоны поправь на правду, если он ещё врёт про «ждёт библиотеку».

## Источники истины, по убыванию

1. `/opt/mc-src` — декомпилированная ваниль 26.2. `grep -rn` по ней отвечает на любой
   вопрос «а как это теперь называется». **Не угадывай имена — проверяй.**
2. Портированные моды на диске — форма, которую надо копировать:
   - `/workspace/domum-ornamentum/26.2` — тот же LDT Team, NeoForge → Fabric; регистрация,
     блоки, предметы, датаген, `PORT-STATUS.md` и `PORT-GAPS.md` завершённого порта
   - `/workspace/simple-planes/26.2` — **NeoForge 1.21.1 → Fabric 26.2, тот же маршрут**
   - `/workspace/blockui/26.2` — GUI, а также `com/ldtteam/common` (сеть и конфиг)
   - `/workspace/structurize/26.2` — **портирован и собран**, это настоящая зависимость;
     рядом `1.21.1/` как «было → стало»
3. `../porting-26.2/PORTING-BUNDLE-26.2.md` — закон порта: контракты, правило деградации, приёмка.

## Контракты — соблюдать, не изобретать

- **C1** Поля реестров остаются `Supplier<T>`. `DeferredRegister`/`DeferredHolder` уходят,
  но 229 вызовов `.get()` по коду должны продолжать компилироваться.
- **C2** Точки входа: `com.minecolonies.core.MineColonies implements ModInitializer`,
  `MineColoniesClient implements ClientModInitializer`.
- **C3** Сеть уже есть — `com.ldtteam.common.network` из BlockUI. `PlayMessageType`,
  `AbstractClientPlayMessage`, `AbstractServerPlayMessage`, `PlayMessageContext`,
  `ModNetworking.register()/registerClient()`. **Не пиши свою.**
- **C4** Капабилити не переносятся. `IItemHandler` → `SimpleContainer` или
  `fabric-transfer-api-v1`, по месту.
- **C5** `@EventBusSubscriber` / `@SubscribeEvent` → коллбеки Fabric.
- **C6** Если пришлось изменить чужую сигнатуру против контракта — **обязательно** в отчёт,
  отдельным пунктом «Contract deviation», с причиной.

## Правило деградации (§10 бандла)

Если что-то не переносится — спускайся по лестнице, **не блокируйся и не спрашивай**:
1. отключить регистрацию;
2. оставить тело метода, но обезвредить;
3. функциональная деградация;
4. убрать объект данных.
Каждое применение — строкой в отчёт, оркестратор перенесёт в `Disabled content`.

## Самопроверка без Gradle

**Первым в classpath обязан идти merged-jar с уже применённым AccessWidener**, иначе javac
выдаст сотню ложных «has private access» на членах, которые AW расширил. Loom кладёт такой
jar в `loom-cache/minecraftMaven/`, по одному на каждое состояние AW, — берём самый свежий.
`~/.gradle/caches/fabric-loom/26.2/minecraft-server.jar` — это jar **без** AW, в classpath
он попадать не должен.

```sh
MC=$(ls -t /home/user/minecolonies/26.2/.gradle/loom-cache/minecraftMaven/net/minecraft/\
minecraft-merged-*/26.2/*.jar | grep -v sources | head -1)
LIBS=$(find ~/.gradle/caches/fabric-loom -name '*.jar' 2>/dev/null \
        | grep -v '/26.2/minecraft-' | tr '\n' ':')
CP="$MC:$LIBS/workspace/blockui/26.2/build/libs/blockui-0.0.1.jar"
CP="$CP:/workspace/domum-ornamentum/26.2/build/libs/domum_ornamentum-26.2-1.0.0.jar"
CP="$CP:/workspace/structurize/26.2/build/libs/structurize-26.2-1.0.0.jar"

/usr/lib/jvm/java-25-openjdk-amd64/bin/javac --release 25 -nowarn -Xmaxerrs 2000 \
  -cp "$CP" -sourcepath /home/user/minecolonies/26.2/src/main/java \
  -d /tmp/jc-$$ <твои файлы> 2>&1 | head -50
```

Если после этого всё равно осталось «has private access» — сверься с
`26.2/src/main/resources/minecolonies.accesswidener`: возможно, член там действительно
не перечислен, и тогда это настоящая ошибка, а не артефакт.

## Формат финального отчёта

Коротко и по делу, без пересказа процесса:

1. **Сделано** — что за зона, сколько файлов тронуто.
2. **Contract deviations** — каждая изменённая чужая сигнатура, с причиной.
3. **Деградации** — что отключено и на какой ступени лестницы.
4. **Осталось / чужое** — проблемы в чужих зонах, которые ты видел, но не трогал.
5. **Не проверено** — что ты не смог проверить и почему.

Не приукрашивай. «Скомпилировалось» и «работает» — разные утверждения; клиент в этом
контейнере никто не запускал и не запустит.
