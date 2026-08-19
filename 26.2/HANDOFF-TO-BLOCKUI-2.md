# MineColonies → BlockUI: два краша на старте мода

**Дата:** 2026-07-31
**Патч:** `26.2/BLOCKUI-RUNTIME-FIXES.patch` (в этом же репозитории, применяется к корню BlockUI)

Первый живой запуск порта — `runDatagen` — падает **дважды подряд в `com.ldtteam.common.language`**,
и оба раза до того, как хоть одна строка кода MineColonies успевает что-то сделать. Оба бага
ваши, оба однострочные, оба чинятся приложенным патчем. Мы применили его локально, чтобы не
стоять; в наш репозиторий он не попадёт — landing за вами.

**Важно: второй краш ловит и вас самих.** В первом отчёте о падении подавленным исключением
идёт `Structurize.onInitialize` (`Structurize.java:44`) — он зовёт `loadLangPath` ровно так же
и падает ровно там же. Кто первым дошёл до класса, тот и получил `ExceptionInInitializerError`,
второму достался `NoClassDefFoundError`. То есть это не «MineColonies что-то делает не так»,
а общий дефект для всех потребителей.

---

## Баг 1. `ClientLocale.getLocale()` — проверка на одно поле мельче реальности

```java
// было
return Minecraft.getInstance() == null ? null : Minecraft.getInstance().options.languageCode;
```

Комментарий над этой строкой предупреждает: *«Trust me, Minecraft.getInstance() can be null,
when you run Data Generators!»* — и это правда, но в 26.2 отказ другой:

```
java.lang.NullPointerException: Cannot read field "languageCode"
    because "net.minecraft.client.Minecraft.getInstance().options" is null
	at com.ldtteam.common.language.ClientLocale.getLocale(ClientLocale.java:10)
	at com.ldtteam.common.language.LanguageHandler$LanguageCache.load(LanguageHandler.java:68)
	at com.ldtteam.common.language.LanguageHandler$LanguageCache.<clinit>(LanguageHandler.java:56)
	at com.ldtteam.common.language.LanguageHandler.loadLangPath(LanguageHandler.java:50)
	at com.minecolonies.core.MineColonies.onInitialize(MineColonies.java:115)
```

`getInstance()` **не** null — точки входа модов вызываются из `Minecraft.<init>` (строка 456),
то есть экземпляр уже есть, а `options` ещё не присвоены. Проверять надо и поле:

```java
final Minecraft mc = Minecraft.getInstance();
return mc == null || mc.options == null ? null : mc.options.languageCode;
```

Вернуть `null` безопасно — `load()` подставит его в `String.format`, ничего не найдёт и уйдёт
на `defaultLocale`. Ровно тот путь, который вы уже заложили.

## Баг 2. `LanguageCache.load()` разыменовывает отсутствующий ресурс

После починки первого бага падает следующая строка:

```
java.lang.NullPointerException
	at java.io.InputStreamReader.<init>(InputStreamReader.java:119)
	at com.ldtteam.common.language.LanguageHandler$LanguageCache.load(LanguageHandler.java:75)
```

```java
InputStream is = ...getResourceAsStream(String.format(path, locale));
if (is == null)
{
    is = ...getResourceAsStream(String.format(path, defaultLocale));   // тоже может быть null
}
languageMap.putAll(new Gson().fromJson(new InputStreamReader(is, ...), ...));  // <- бум
```

Фолбэк на `en_us` не гарантирует ничего: **у нас этого файла нет и не будет.**
`assets/minecolonies/lang/en_us.json` приходит из пайплайна переводов, а не из гита — в
репозитории лежат только `default.json` (датаген), `quests.json`, `tag.item.json` и
`manual_en_us.json`. В dev- и датаген-прогонах оба обращения возвращают `null`.

Мы это проверили и по вашей доportной ветке: в `26.1.2` строка **та же самая, без проверки**.
Так что баг не порт внёс — просто на NeoForge он по каким-то причинам не стрелял.

Фикс — ранний выход с предупреждением вместо падения. Пустой кэш здесь и есть правильный
исход: `translateKey` при промахе всё равно уходит в `Language.getInstance()`.

---

## Что это значит для приоритетов

Оба фикса ничего не меняют в вашем публичном API и ни на что не влияют, кроме поведения при
отсутствующем файле. Но без них **ни один зависимый мод не стартует** — ни мы, ни Structurize,
ни в датагене, ни в клиенте. Это блокер выше всего, что мы обсуждали в прошлой передаче
(обёртка `ColouredVertexConsumer`, `unstitch`-атлас, синк server-конфигов).

## Оговорка о проверенности

Патч собран (`gradle build` по BlockUI зелёный) и с ним `runDatagen` проходит обе эти точки —
дальше падает уже на нашем собственном баге (`Block id not set`, отдельная работа у нас).
То есть проверено ровно одно: эти два краша уходят. Ни клиент, ни сервер с патчем ещё не
поднимались.
