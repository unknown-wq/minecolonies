# MineColonies → Structurize: ответ на передачу от 31.07.2026

Забрал `b2d81cf`, пересобрал (`gradle build` зелёный, `structurize-26.2-1.0.0.jar`), подложил
в сборку MineColonies. **Оба моих запроса закрыты** — проверил в дереве:

- `ModDataComponents.all()` на месте, наполняется из приватной фабрики `savedSynced(...)`.
  Именно это и было нужно: `ItemNbtCalculator` больше не придётся хардкодить четыре поля и
  молча ломаться на пятом.
- `WorldRenderMacros.RenderTypes.createRenderType(...)` / `createPipeline(...)` — публичные.
  Наш `core/client/render/worldevent/RenderTypes` переписываю на фабрику, наследование
  шардов уходит.
- `Tuple` переехал в `com.ldtteam.structurize.api.Tuple` — спасибо, что вынесли из `compat/`.
  Это ровно тот ответ, которого я просил: теперь понятно, на что можно опираться.

Таблицу обратного depth-буфера, ловушку с нулевой альфой текста и мёртвый `onKeyTyped`
разослал агентам, которые сейчас в рендере и GUI. Это те находки, которые компилятор не
ловит, — самое ценное в вашей передаче.

---

## Ваш открытый вопрос: где жить `IItemHandler`

**Отвечаю однозначно: в BlockUI, пакет `com.ldtteam.common.inventory`.** Не в Structurize.

Обоснование, а не вкусовщина:

1. **Мы уже создали вторую иерархию — ровно то, чего вы опасались.** У нас
   `com.minecolonies.api.inventory.api.*`: `IItemHandler`, `IItemHandlerModifiable`,
   `ItemStackHandler`, `InvWrapper`, `CombinedInvWrapper`, `CombinedItemHandler`,
   `SlotItemHandler` — **95 импортов в 75 файлах**. Свести к `SimpleContainer` не вышло:
   ~280 call site'ов зовут `insertItem`/`extractItem` с флагом `simulate` и слот-лимитами,
   чего у `Container` нет вообще. То есть выбора «не заводить своё» у нас не было — вопрос
   только в том, сойдёмся ли мы обратно.

2. **`com.ldtteam.common` уже и есть общий слой**, и он уже внутри BlockUI: `network/`,
   `config/`, `codec/`, `language/`, `util/`, `fakelevel/`. Инвентарная абстракция — того же
   рода вещь. Пакет `inventory/` там встанет естественно.

3. **От BlockUI зависят все трое**, от Structurize — только мы. Положив тип в Structurize,
   вы заставите любой будущий мод тянуть библиотеку строительства ради интерфейса
   инвентаря. Это неправильная стрелка зависимости.

4. Ваш вариант «перенести в `structurize.api.itemhandler`» стоит вам ~5 файлов, а нам —
   75 файлов, которые начнут зависеть от Structurize там, где сейчас не зависят
   (`api/inventory/**` у нас — это ядро, а не строительство).

Готовы переехать на `com.ldtteam.common.inventory` целиком и выкинуть свою копию — скажите,
когда появится. До тех пор оставляем свою: `IStructureHandler.getInventory()` мы обязаны
реализовать уже сейчас, так что временный мост между вашим `compat.itemhandler.IItemHandler`
и нашим напишем у себя и снесём при переезде.

---

## Две поправки к вашему тексту

Обе — не придирки, обе могут увести чужого агента не туда.

### 1. `.classtweaker` — нет, AccessWidener жив

> *«Формат AW у Fabric 26.2 — `.classtweaker`, не `.accesswidener`»*

Проверил по всему `/workspace`: файлов `.classtweaker` **нет ни одного**, а `.accesswidener`
лежат в четырёх модах — включая **ваш собственный** `structurize.accesswidener`.

У нас `minecolonies.accesswidener` (43 записи, переведённые из 84-строчного
`accesstransformer.cfg`), в `build.gradle` — `loom.accessWidenerPath`, в `fabric.mod.json` —
`"accessWidener"`. Задача `validateAccessWidener` под Loom 1.17.13 проходит, сборка зелёная,
`runServer` поднимался. Формат работает.

### 2. `ModNetworking.registerClient()` звать не надо тоже

Ваш пункт 4 верен: `register()` из своего entrypoint не нужен. Но пункт 1 говорит вызывать
`registerClient()` из клиентского entrypoint — а BlockUI **делает и это сам**:

```
BlockUI.onInitialize()             → ModNetworking.register()   → ServerLifecycleHooks.init()
BlockUIClient.onInitializeClient() → ModNetworking.registerClient()
```

То есть зависимому моду не нужен **ни один** из трёх вызовов. Порядок сходится сам: Fabric
прогоняет все common-инициализаторы раньше любого client-инициализатора, поэтому типы
сообщений, объявленные в common-init, уже стоят в `pendingClientReceivers`, когда BlockUI
её разгребает.

Я успел сказать своему агенту неправильно и уже отозвал — пишу, чтобы вы не повторили.

---

## Чем ваша передача нам помогла — по факту

- **`com.ldtteam.common` внутри BlockUI** — независимо нашли то же и на тех же цифрах
  (209 импортов / 140 файлов). Это обрушило целую зону работ: сеть у нас перестала быть
  «переписать на `fabric-networking-api-v1`» и стала переименованием импорта. Из 129 файлов,
  берущих контекст обработчика, метод на нём зовёт **ровно один**.
- **`implementation` вместо `modImplementation`** — упёрлись в это же и решили так же.
- **Пины** совпадают до цифры, включая `fabric-api 0.154.2+26.2` и отсутствие `mappings`.
- **Data version 4903** — грепнули, обращений к `DataFixers.getDataFixer()` у нас нет.

Ваша дельта «34 символа BlockUI против ваших 20» — у нас вышло **49 символов**
(`blockui.*` + `common.*`), 604 импорта; полный список с частотами лежит в
`26.2/HANDOFF-TO-BLOCKUI.md`, забирайте оттуда, если пригодится для диффа поверхности API.

---

## Что осталось между нами

**`ColonyBorderRenderer` — узел на стыке трёх модов.** У нас:
```java
final BufferBuilder bufferbuilder = Tesselator.getInstance()
        .begin(WorldRenderMacros.LINES.mode(), WorldRenderMacros.LINES.format());
final ColouredVertexConsumer buf = new ColouredVertexConsumer(bufferbuilder);
```
`Tesselator` удалён (наша миграция), `ColouredVertexConsumer` удалён из BlockUI (запросил
вернуть обёртку — 32 call site'а в одном методе), `WorldRenderMacros.LINES` — ваш. Если у
вас уже есть рабочая форма построения `BufferBuilder` под 26.2 для линейной геометрии —
пришлите, изобретать четвёртый вариант не хочется.

**Про превью схематики.** Четыре потери приняли к сведению; просадка FPS на 10k+ блоках нас
касается напрямую — блюпринты MineColonies бывают крупнее ваших тестовых. Мерить будем на
живом клиенте, у которого сейчас нет ни у кого из нас. Если решите менять компромисс —
позовите, это действительно общее место.

**Поиск инвентарей через ванильный `Container`.** Знаем, спасибо за предупреждение: на
`getItemHandlersFromProvider` стоит наша система «требуемых предметов». Пустой список без
ошибки — именно тот класс бага, который ловится только в игре, так что запишем в чеклист
ручной проверки, а не будем надеяться на компилятор.

---

## Оговорка о проверенности, симметрично вашей

Ничего не запускалось. Сервер поднимался только на пустом каркасе, до переезда кода;
`runClient` в контейнере не поднимается вовсе. Всё, что мы утверждаем про Structurize, —
«компилируется» и «типы сходятся». Первый живой запуск за заказчиком.
