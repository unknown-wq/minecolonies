# Карта изменений API — чеклист порта MineColonies 1.21.1 → Fabric 26.2

Собран из `porting-26.2/PORTING-GUIDE-26.2.md §3` (пер-версионный список ломок),
`PORT-MOD-26.2.md §4` (таблицы ренеймов) и `PORT-ANY-MOD-26.2.md §8`, но, в отличие от
них, **привязан к этому коду**: каждая строка проверена grep-ом по `1.21.1/src`, цифры —
реальные попадания на HEAD. Пункты с нулём попаданий тоже оставлены: знать, чего в моде
нет, так же полезно, как знать, что есть.

Порт идёт по **двум независимым осям одновременно** — смена лоадера (NeoForge → Fabric)
и ваниль (1.21.1 → 26.2). Считать их надо отдельно, иначе оценка объёма врёт.

**Как пользоваться:** пункт → `grep` из строки → правишь → отмечаешь галочку.
Цифры пересчитываются скриптом в конце файла. Ни одну сигнатуру из этого файла нельзя
писать «по памяти»: сверяться с `/opt/mc-src` (§3 плана) — этот чеклист говорит, *где*
сломается, а не *как именно* выглядит новая сигнатура.

---

## Ось A — NeoForge → Fabric

| | Пункт | Файлов | Вхождений | Что делать |
|---|---|---:|---:|---|
| ☐ | `import net.neoforged.*` — общий счётчик оси | **410** | 722 | К концу порта должно стать 0. Это метрика прогресса, а не отдельная задача |
| ☐ | `@Mod` / `ModLoadingContext` | 1 | 1 | `core/MineColonies.java:101` → `ModInitializer.onInitialize()` + отдельный `ClientModInitializer` (контракт C2) |
| ☐ | `DeferredRegister` / `DeferredHolder` | 41 | 391 | Жадный `Registry.register(BuiltInRegistries.X, Identifier.fromNamespaceAndPath(…), obj)`, **но форму полей сохранить** (`Supplier<T>`, контракт C1) — иначе поедут все call-site'ы |
| ☐ | `@SubscribeEvent` / `@EventBusSubscriber` / `IEventBus` | 20 | 101 | Классы событий удаляются, логика переезжает в Fabric-колбэки (`ServerTickEvents`, `UseEntityCallback`, …), контракт C5. `NOTES-A §2` |
| ☐ | Capabilities: `IItemHandler`, `ItemStackHandler`, `IEnergyStorage` | **52** | **519** | В Fabric их нет. `ItemStackHandler` → `SimpleContainer`, энергия/жидкости → обычные поля владельца (контракт C4). `NOTES-A §5`, `NOTES-B §5`. Самый крупный блок этой оси |
| ☐ | `PacketDistributor` / `PayloadRegistrar` | 1 | 2 | `PayloadTypeRegistry.serverboundPlay()/clientboundPlay()` + `ServerPlayNetworking`/`ClientPlayNetworking`. Замена `sendToPlayersTrackingEntity` — `NOTES-B §4` |
| ☐ | `IEntityWithComplexSpawn` | 1 | 2 | Прямого аналога нет — свой пакет на спавне. Рецепт в `NOTES-B §4` |
| ☐ | `ModConfigSpec` / `IExtensionPoint` | 3 | 6 | Свой конфиг (`NOTES-A §3`); точки расширения выкинуть |
| ☐ | `IGlobalLootModifier` | 2 | 10 | Аналога нет: либо `LootTableEvents.MODIFY`, либо §10 (отключить и залогировать) |
| ☐ | `GatherDataEvent` / `DataMapProvider` | 2 | 4 | Fabric datagen (`fabricApi { configureDataGeneration { … } }`) |
| ☐ | `META-INF/accesstransformer.cfg` | 1 | — | → AccessWidener с заголовком `official` (не `named`), тело перемаппить. §3 плана |
| ☐ | `neoforge.mods.toml` | 1 | — | → `fabric.mod.json`: `fabricloader >=0.19.3`, `minecraft ">=26.2 <26.3"`, `java ">=25"` |

## Ось B — ваниль 1.21.1 → 26.2

| | Пункт | Файлов | Вхождений | Что делать |
|---|---|---:|---:|---|
| ☐ | `ResourceLocation` | **547** | **3735** | Класса больше нет: `net.minecraft.resources.Identifier`, фабрика `Identifier.fromNamespaceAndPath(...)` (`Identifier.of` не существует). Механический проход скриптом (§7 плана) |
| ☐ | `Item.Properties` | 32 | 261 | Обязателен `.setId(ResourceKey.create(Registries.ITEM, id))`, иначе `NPE: Item id not set` при регистрации. `NOTES-A §1` |
| ☐ | `BlockBehaviour.Properties` / `Block.Properties` | 2 | 2 | То же самое: `.setId(ResourceKey<Block>)` |
| ☐ | `EntityType.Builder` | 2 | 62 | `Builder.of(f, MobCategory)`, `.sized/.clientTrackingRange`, `build(ResourceKey<EntityType<?>>)` вместо `build(String)` |
| ☐ | `EntityType#create(...)` | 4 | 4 | Теперь `create(Level, EntitySpawnReason)` |
| ☐ | `InteractionResultHolder` | 14 | 53 | Схлопнулся в единый `InteractionResult`; `use()` возвращает его |
| ☐ | Рендереры: `extends EntityRenderer` / `EntityRendererProvider` | **37** | 77 | Полная переписка на render-state: `createRenderState()`, `extractRenderState(T,S,float)`, `submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`. Форму копировать из портированного мода, не сочинять. `NOTES-C §2` |
| ☐ | `getTextureLocation(` / `getTexture(` | 26 | 31 | Метода больше нет — текстура уезжает в render state |
| ☐ | `MultiBufferSource` в рендере | 19 | 50 | Старая модель `render(...)`; в 26.2 — submit-очередь. `NOTES-C §2–§3` |
| ☐ | `appendHoverText` | 21 | 33 | Новая сигнатура: `(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` |
| ☐ | `.hurt(` | 22 | 33 | → `hurtServer(ServerLevel, DamageSource, float)`. `NOTES-B §1` |
| ☐ | NBT сущностей/BE: `readAdditionalSaveData` / `saveAdditional` / `load(CompoundTag` | 20 | 54 | Кодек-ориентированные `ValueInput`/`ValueOutput`. **Только эти хуки**, см. предупреждение ниже. `NOTES-B §2` |
| ☐ | `defineSynchedData` | 6 | 10 | В 1.21.1 форма с `SynchedEntityData.Builder` уже правильная — проверить, что не сломалась, и не трогать |
| ☐ | `javax.annotation.Nullable` | 38 | 38 | На classpath 26.2 его нет → `org.jspecify.annotations.Nullable`. `NOTES-C §10` |
| ☐ | `GuiGraphics` / vanilla `Screen` | 17 | 56 | Сигнатуры экранов и виджетов сместились; `Minecraft.getInstance().setScreen(x)` → `minecraft.gui.setScreen(x)`. Объём маленький — см. «GUI» ниже |
| ☐ | `RenderSystem.` / `GL11` | 4 | 10 | Сырой GL под Vulkan не живёт — только Blaze3D. Проверить все 10 вхождений поимённо |
| ☐ | Рецепты: результат в JSON и сериализаторах | — | — | Результат обязан быть `ItemStackTemplate`, а не `ItemStack`, иначе рецепты **молча исчезают** при загрузке дата-пака. Ингредиенты — плоские строки-id. §14 плана |
| ☐ | Дата-пак: раскладка каталогов | — | — | `data/<ns>/<registry>/…` с пространством имён в пути |
| ☐ | Датаген: теги | — | — | `valueLookupBuilder(...)` → `builder(...)`; `TagAppender.add` берёт `ResourceKey<T>` (оборачивать `.builtInRegistryHolder().key()`) |

### ⚠ `CompoundTag` — 339 файлов, 1685 вхождений: это НЕ задача порта

Ломка `ValueInput`/`ValueOutput` касается **только** ванильных хуков сериализации сущностей
и блок-сущностей (строка выше, 20 файлов). Остальные ~1600 вхождений — это собственный
формат хранения колоний MineColonies, который просто использует `CompoundTag` как структуру
данных. Его трогать не надо. Агент, которому не сказать этого явно, попытается переписать
все 1685 мест.

### GUI: 17 файлов ваниль против 146 файлов BlockUI

Весь интерфейс мода построен на `com.ldtteam.blockui`, а не на ванильных `Screen`.
Поэтому пункт «GuiGraphics» в таблице выше маленький и **обманчивый**: реальная работа по
GUI — это судьба BlockUI (см. ниже), а не 56 вхождений `GuiGraphics`.

## Чего в этом моде нет (проверено — 0 попаданий)

Не искать и не планировать под это время:

- **миксинов нет вообще** — NeoForge-мод обходится access transformer'ом. Это снимает
  самый рискованный блок работ из всех четырёх предыдущих портов;
- `MenuScreens.register` — 0;
- `SurfaceRules` / `isBiome` — 0, значит грабли с eager-резолвом биомов (§14.3) не наши;
- `HudRenderCallback` — 0 (это Fabric-специфичный API, у NeoForge свои события отрисовки).

## Вне этого чеклиста: четыре библиотеки ldtteam

Ни один пункт выше не покрывает главного. `BlockUI` (146 файлов), `Structurize`
(147 файлов), `com.ldtteam.common` (209 импортов) и `Domum Ornamentum` (57) существуют
только под Forge/NeoForge, и **9374 файла `.blueprint`** в ресурсах читаются именно
Structurize. Это отдельное решение оркестратора до найма агентов (§4 плана): портировать
библиотеки первыми — как `TntLib` перед `tntmod` — заменить или урезать по §10.
Пока оно не принято, оценивать порт по таблицам выше нельзя.

## Пересчитать цифры

```sh
cd 1.21.1/src
c(){ printf "%-46s %6s files %6s hits\n" "$1" \
  "$(grep -rlE "$2" --include=*.java . | wc -l)" "$(grep -rhoE "$2" --include=*.java . | wc -l)"; }
c "net.neoforged"          "^import net\.neoforged"
c "DeferredRegister"       "DeferredRegister|DeferredHolder|RegistryObject"
c "@SubscribeEvent"        "@SubscribeEvent|@EventBusSubscriber|NeoForge\.EVENT_BUS|IEventBus"
c "capabilities"           "IItemHandler|ItemStackHandler|IEnergyStorage|Capabilities\."
c "ResourceLocation"       "\bResourceLocation\b"
c "Item.Properties"        "Item\.Properties"
c "renderers"              "extends EntityRenderer|EntityRendererProvider"
c "getTexture"             "getTextureLocation|getTexture\("
c "entity NBT hooks"       "readAdditionalSaveData|addAdditionalSaveData|saveAdditional|load\(CompoundTag"
c "appendHoverText"        "appendHoverText"
c "javax Nullable"         "javax\.annotation\.Nullable"
```
