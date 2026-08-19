# WP5 — Phase 5 consent UX and crash guards (D1, D2, D3): report

Branch `claude/assetfetch-wp5`, on top of the merged WP0/WP1/WP2/WP3/WP4 work.
Eight new classes, one wired line in the client initializer, 23 gated window-open call
sites, one gated particle spawn, 43 new translation keys. Build green; **no client was
run** — §7 says exactly what that leaves unverified.

---

## 1. What was built

| task | class | what it does |
| --- | --- | --- |
| D1 | `client/assetfetch/AssetFetchClient` | title-screen hook + the `/minecolonies-client fetchassets` client command |
| D1 | `client/assetfetch/gui/AssetConsentScreen` | the ask: what, from where, licence, Download / Not now / use a local jar |
| D1 | `client/assetfetch/gui/AssetInstallScreen` | progress while it runs; installed / cancelled / failed when it stops |
| D1 | `client/assetfetch/gui/AssetLocalJarScreen` | source 4: a path field driving `AssetInstaller.forLocalJar` |
| D1 | `client/assetfetch/gui/AssetFetchLang` | the translation keys, in one greppable place |
| D1 | `client/assetfetch/gui/AssetFetchScreenSupport` | byte/count formatting and the one cross-thread `setScreen` |
| D2 | `client/assetfetch/AssetFetchGate` | `openOrOffer(Supplier<BOWindow>)`, `ready()`, `offerInstall()`, `openConsent()` |
| D2 | `client/assetfetch/gui/AssetsMissingScreen` | what opens instead of a window, with the download offered on it |
| D3 | (edit) `network/messages/client/SleepingParticleMessage` | one `isReady()` guard before `addParticle` |

Everything the player sees is built from **vanilla widgets** (`StringWidget`,
`MultiLineTextWidget`, `EditBox`, `Button`, `LinearLayout`). No BlockUI: BlockUI windows
are precisely what cannot be opened in this state.

## 2. The UI flow

```
title screen (first arrival, not installed, not declined)
   │  ScreenEvents.AFTER_INIT -> client.execute(...)
   ▼
AssetConsentScreen ──"Not now"──▶ recordDeclined(), back to the title screen
   │                                    (way back in: the client command, or the D2 gate screen)
   ├──"Use a jar I already have..."──▶ AssetLocalJarScreen ──path──┐
   │                                        └──Cancel──▶ back      │
   └──"Download"────────────────────────────────────────────────┐  │
                                                                ▼  ▼
                                                      AssetInstallScreen
                                                      (phase · source · bytes/files · Cancel)
                                                                │
                    ┌───────────────────────────────────────────┼──────────────────────────┐
                    ▼                                           ▼                          ▼
             INSTALLED                                   NO_SOURCE / FAILED            CANCELLED
   reloadResourcePacks() on the client thread     every attempt listed with its    "nothing was
   → "installed, N files verified, X MB"          HTTP status, bytes and error;    changed"
   → Close                                        "nothing was changed";            → Try again /
                                                  where to report it;                 Not now
                                                  → Try again / Not now
```

And the gate:

```
right-click a hut / use an item / server sends an Open*Message
   │  AssetFetchGate.openOrOffer(() -> new WindowX(...))
   ├── assets ready ──▶ window opens exactly as before
   └── assets absent ─▶ AssetsMissingScreen ──"Download"──▶ AssetConsentScreen ──▶ …
```

### Threading

`InstallListener` callbacks arrive on the installer's thread, so `AssetInstallScreen`
only writes volatile fields there; `tick()` reads them on the client thread.
`onFinished` hands the report over with `Minecraft.execute(...)`. The resource reload —
which the installer deliberately never does — happens there, on the client thread, and
only after an `INSTALLED` report; the screen shows a "reloading" state and re-enables its
Close button when the returned future completes.

### The title-screen hook

`fabric-screen-api-v1`'s `ScreenEvents.AFTER_INIT`, filtered to `TitleScreen`. **No
mixin** — the port has exactly one, for the pack injection, and this did not need a
second. Two details that matter:

- the screen change is queued with `client.execute(...)` rather than done inside the
  title screen's `init`, so it lands at the next task drain instead of re-entering a
  screen change from within one;
- a static latch makes it a genuine one-shot. Closing the consent screen puts the title
  screen back, which re-runs its `init` and re-fires `AFTER_INIT`; without the latch the
  prompt would reopen forever. "Not now" additionally persists through
  `AssetInstaller.recordDeclined()`.

### The ways back in after a decline

1. `/minecolonies-client fetchassets` — a **client** command (`ClientCommandRegistrationCallback`
   from `fabric-command-api-v2`), registered in `MineColoniesClient`. Deliberately not
   under `/minecolonies`: that root is the server's, registered through
   `CommandRegistrationCallback` in `EventHandler`, and this has to work regardless of
   what the server has. It reports "already installed" when there is nothing to do.
2. The **Download** button on `AssetsMissingScreen`, i.e. the D2 gate itself — the player
   who just tried to open a MineColonies window is exactly the player who wants the
   assets.

## 3. D2 — the choke-point table

The crash is thrown by `Loader.createFromXMLFile`, which BlockUI calls from the
**`BOWindow` constructor** (`BOWindow(Identifier, boolean)` → `Loader.createFromXMLFile`),
not from `open()`. So a gate around `open()` would be too late; every gated site therefore
passes a `Supplier<BOWindow>` that is not invoked unless `AssetFetch.isReady()`.

Enumeration method: `grep -rn "\.open()\|\.openAsLayer()"` over `26.2/src/main/java`
(98 hits, 107 files mentioning `BOWindow`/`BOScreen`), then classifying each by whether it
is reachable **without a MineColonies window already open**. Sites inside
`com/minecolonies/core/client/gui/**` and `com/minecolonies/core/debug/gui/**` are button
handlers on an open window by construction and are covered by the gate that opened it.

### Gated (23 call sites, 22 files)

| # | file:line | reached by |
| --- | --- | --- |
| 1 | `colony/buildings/views/AbstractBuildingView.java:350` (`openGui`) | **the central one** — every `AbstractColonyBlock` right-click (`AbstractColonyBlock:254`), `BlockHutTownHall:201`, and `OpenBuildingUIMessage:74` |
| 2 | `colony/ColonyManager.java:439` (`openReactivationWindow`) | right-click a deactivated hut (`AbstractColonyBlock:231`) |
| 3 | `colony/buildings/registry/BuildingDataManager.java:104` (`openBuildingBrowser`) | right-click air holding a hut block (`ClientEventHandler:487`) |
| 4 | `blocks/BlockDecorationController.java:193` | block right-click |
| 5 | `blocks/BlockPlantationField.java:150` | block right-click |
| 6 | `blocks/BlockScarecrow.java:119` | block right-click |
| 7 | `blocks/huts/BlockHutGateHouse.java:79` | block right-click without hut permission |
| 8 | `entity/citizen/EntityCitizen.java:391` | right-click a citizen |
| 9 | `entity/visitor/VisitorCitizen.java:443` | right-click a visitor |
| 10 | `items/ItemBannerRallyGuards.java:193` | item use |
| 11 | `items/ItemClipboard.java:122` | item use |
| 12 | `items/ItemColonyMap.java:98` | item use |
| 13 | `items/ItemQuestLog.java:105` | item use |
| 14 | `items/ItemResourceScroll.java:90` | item use |
| 15 | `items/ItemScanAnalyzer.java:124` | item use |
| 16–17 | `items/ItemSupplyCampDeployer.java` (story window, supplies window) | item use |
| 18–19 | `items/ItemSupplyChestDeployer.java` (story window, supplies window) | item use |
| 20 | `network/messages/client/OpenBuildWindowMessage.java:79` | server-sent |
| 21 | `network/messages/client/OpenCantFoundColonyWarningMessage.java:60` | server-sent |
| 22 | `network/messages/client/OpenColonyFoundingCovenantMessage.java:48` | server-sent |
| 23 | `network/messages/client/OpenDeleteAbandonColonyMessage.java:63` | server-sent |
| 24 | `network/messages/client/OpenReactivateColonyMessage.java:48` | server-sent |
| 25 | `network/messages/client/OpenSuggestionWindowMessage.java:74` | server-sent |

(Numbering runs to 25 because the two supply deployers each held two distinct windows; the
`.open()` statement count in the diff is 23 after the redundant-branch removal below.)

Both supply deployers had the shape `if (pos == null) { new WindowSupplies(pos, name).open(); return; } new WindowSupplies(pos, name).open();`
— the same window with the same arguments either way. The dead branch is gone; behaviour
is unchanged.

### Consciously exempted

| site | why |
| --- | --- |
| `AbstractBuildingView.openGui(true)` — the shift-click branch | it sends `OpenInventoryMessage`; the server answers with a menu whose screen is `WindowBuildingInventory`, a plain `AbstractContainerScreen`. Checked all of `client/gui/containers/`: seven of the eight are vanilla container screens with no BlockUI XML; the eighth, `WindowField`, is BlockUI but is only ever constructed from `BlockScarecrow` (#6, gated). A missing texture on a vanilla screen is D4 behaviour — checkerboard, no crash. |
| `colony/interactionhandling/ServerCitizenInteraction.java:180` | `onClientResponseTriggered(..., BOWindow window)` — it is a response handler on an already-open citizen window (#8/#9). |
| `colony/interactionhandling/RequestBasedInteraction.java:167` | same signature, same reason. |
| `colony/buildings/moduleviews/DOCraftingModuleView.java:25` | a module-tab action inside an open building window (#1). |
| `colony/buildings/modules/settings/BlockSetting.java:157` | a settings widget's button handler inside an open building window (#1). |
| `colony/buildings/modules/settings/GuardTaskSetting.java:92` | same. |
| everything under `client/gui/**` and `debug/gui/**` (73 sites) | all `registerButton`/handler bodies on an open window. |
| `commands/colonycommands/CommandColonyDiagnose.java:206` | false positive: `requestCounts.open()` is a request-count field, not a window. |
| Structurize windows opened from our code (`WindowExtendedBuildTool`, `WindowSelectRes`, `WindowSwitchPack`) | their XML ships in the Structurize jar, so they do not depend on the fetched pack — and every one of them is reached from an already-open MineColonies window anyway. |
| the `getWindow()` factories on module views and buildings (~20) | they *return* a window; the only callers are `AbstractBuildingView.openGui` (#1) and in-window navigation (`AbstractBuildingWindow`, `WindowPostBoxMain`, `GuardTaskSetting`). |

Grep proof after the change — the only `.open()`/`.openAsLayer()` calls left outside
`client/gui/**` and `debug/gui/**` are the six exempted ones plus the gate's own:

```
colony/interactionhandling/ServerCitizenInteraction.java:180
colony/interactionhandling/RequestBasedInteraction.java:167
colony/buildings/modules/settings/BlockSetting.java:157
colony/buildings/modules/settings/GuardTaskSetting.java:92
colony/buildings/moduleviews/DOCraftingModuleView.java:25
client/assetfetch/AssetFetchGate.java:62          <- the gate itself
commands/colonycommands/CommandColonyDiagnose.java:206   <- false positive
```

## 4. D3 — the particle sites

The mod registers exactly one particle type: `SLEEPINGPARTICLE_TYPE`
(`apiimp/initializer/ModParticleTypesInitializer`), a sprite-set particle whose provider
is `SleepingParticle.Factory` and whose sprite set is bound from
`assets/minecolonies/particles/particle/sleeping.json` — confirmed present in
`assetfetch/manifest.json`, i.e. it comes with the download and is not in this jar.

Client-side spawn sites of mod particles, from `grep -rn "addParticle"`:

| site | action |
| --- | --- |
| `network/messages/client/SleepingParticleMessage.java:53` | **gated** on `AssetFetch.isReady()` — the only one |
| `CircleParticleEffectMessage`, `StreamParticleEffectMessage`, `VanillaParticleMessage`, `LocalizedParticleEffectMessage`, `ItemParticleEffectMessage`, `CompostParticleMessage`, `BlockParticleEffectMessage`, `ItemAssistantHammer`, `CitizenExperienceHandler` | not gated: every caller passes a **vanilla** particle type (`HEART`, `ENCHANT`, `HAPPY_VILLAGER`, `EXPLOSION`, `ITEM`, `BLOCK`, `INSTANT_EFFECT`) — checked every construction site of the three generic messages |

`EntityAISleep:239` is the (server-side) sender; the guard is on the receiving end, where
the particle is actually created. No placeholder `sleeping.json` was added, and must never
be: the fetched pack is at `Position.BOTTOM`, so a file in this jar would permanently mask
the real one.

## 5. Translation keys

43 keys appended to `26.2/src/main/resources/assets/minecolonies/lang/en_us.json` (360 →
403), unsorted-append style, all prefixed `com.minecolonies.coremod.assetfetch.`:

- `consent.title|body|licence|manual`
- `button.download|notnow|localjar|retry|cancel|close`
- `localjar.title|body|hint|notafile`
- `progress.title|source|bytes|bytes.unknown|files|files.unknown|reloading`
- `phase.starting|downloading|checking_jar|extracting|patching|verifying|installing|done`
- `done.title|body`
- `failed.title|reason|attempts|attempt.ok|attempt.bad|unchanged|report`
- `cancelled.title|body`
- `gate.title|body`
- `command.alreadyinstalled`

They live in the **port's own** lang file, which ships inside this jar, because these are
the screens shown when the fetched pack is absent. Checked mechanically: every key named
in `AssetFetchLang` (and every `phase.*` derived from `InstallPhase`) exists in the file,
and no added key is unreferenced. Placeholder counts were checked against each
`Component.translatable` call; the two multi-argument attempt lines use indexed
(`%1$s`…`%5$s`) placeholders because they reorder their arguments.

## 6. Build

`/home/user/mc-build.sh /workspace/wt-wp5/26.2 build` → **BUILD SUCCESSFUL** (46 s cold,
25 s after the last edit). `runDatagen` was not run (WP2 owns it and nothing here changes
a generator).

Jar content check on `build/libs/minecolonies-26.2-0.0.51.jar`:

- `com/minecolonies/core/client/assetfetch/AssetFetchClient.class`, `AssetFetchGate.class`
  present;
- `com/minecolonies/core/client/assetfetch/gui/` contains `AssetConsentScreen`,
  `AssetInstallScreen`, `AssetLocalJarScreen`, `AssetsMissingScreen`, `AssetFetchLang`,
  `AssetFetchScreenSupport`;
- `assets/minecolonies/lang/en_us.json` in the jar has 403 keys, 43 of them
  `com.minecolonies.coremod.assetfetch.*`.

## 7. Not verified

**No client was started in this environment**, so everything below is read from 26.2's own
sources and compiled, but not seen:

1. **Screen layout.** That the `LinearLayout` arrangements fit at common resolutions, that
   the wrapped body text does not overflow, and that the failure screen's attempt list —
   capped at 14 rows — is enough for a long chain. 26.2 replaced immediate-mode
   `GuiGraphics` drawing with render-state extraction, which is why these screens are
   composed purely of stock widgets and override no drawing method at all; that is a
   design choice made to *avoid* the risk, not a measurement of it.
2. **That the crash is actually prevented.** The gate is placed before construction and the
   throw is demonstrably in the constructor's `Loader.createFromXMLFile` call, but no
   asset-less client was booted and no hut was right-clicked. Likewise D3: no citizen was
   watched sleeping.
3. **The reload.** `Minecraft.reloadResourcePacks()` is called on the client thread after
   an `INSTALLED` report, and the returned future re-enables the Close button. That the
   injected pack then appears in the `Reloading ResourceManager` line, and that windows
   open afterwards without restarting, is WP6's acceptance criterion 4 and is untested here.
4. **The title-screen trigger firing exactly once.** The latch plus `client.execute` is
   reasoned from `Screen.init`/`rebuildWidgets` and Fabric's event contract; it was not
   observed.
5. **The client command.** Registration compiles against `fabric-command-api-v2`; it was
   not typed into a chat box. Note it is only available in-world, which is why it is a
   secondary route and the D2 gate screen is the primary one.
6. **The local-jar path field on Windows.** `Path.of` + `Files.isRegularFile` with quotes
   stripped; not run on Windows.
7. **Text quality in other languages.** Only `en_us` exists for these keys; the fetched
   pack's 97 POEditor files know nothing about them, so non-English players see English
   here until someone translates them.
