# Клиент без дисплея, и скриншоты с него

Дисплея на этой машине нет, но есть `Xvfb` и программный растеризатор Mesa — этого хватает
для настоящего клиента Minecraft: `GL 4.5 (Core Profile) Mesa 25.2.8 / llvmpipe`. Работает
для **26.2 и 26.3**, но 26.3 требует двух добавок — см. §7.

|  | 26.2 | 26.3 |
|---|---|---|
| окно открывает | GLFW | **SDL** |
| нужен пакет `libegl1` | нет | **да** |
| нужен `SDL_VIDEO_FORCE_EGL=1` | нет (переменная игнорируется) | **да** |
| каталог проекта | `26.2/` | `26.3/` |

Всё остальное — `Xvfb`, `options.txt`, захват и клики через `java.awt.Robot` — одинаково.

`xdotool`, `scrot`, `import`, `xwd`, `ffmpeg` в контейнере **отсутствуют** и не ставились.
И скриншоты, и клики делает `java.awt.Robot`, который говорит с X-сервером напрямую.
(`docs/assetfetch/WP6-ACCEPTANCE.md` §4.4 заключил, что ввод невозможен; это неверно — см. §4.)

Результат: `docs/screenshots/26.3-title.png` и `docs/screenshots/26.3-assetfetch.png`.

## 1. Поднять дисплей

```bash
Xvfb :99 -screen 0 1280x720x24 +extension GLX +extension RANDR +render -noreset \
  >/tmp/xvfb99.log 2>&1 &
```

Обычно один уже поднят; `DISPLAY=:99` ниже подразумевает именно его.

**Глубина только 24.** На `1280x720x32` Xvfb здесь не стартует вообще —
`(EE) Couldn't add screen 0`. Перебор глубин бессмыслен: sRGB-визуал от этого не появляется
(§7), а 24 бита клиенту достаточно.

## 2. Засеять run-каталог до первого запуска

Прокликать меню настроек нельзя, поэтому всё нужное должно лежать в
`<версия>/run/options.txt` **до** старта клиента. Пути — от корня репозитория:

```bash
mkdir -p 26.3/run
cat > 26.3/run/options.txt <<'EOF'
lang:en_us
guiScale:2
soundCategory_master:0.0
pauseOnLostFocus:false
EOF
```

Для 26.2 — то же самое в `26.2/run/`. Каталог `*/run` покрыт `.gitignore`, так что засеять
его придётся заново на каждом свежем клоне. `lang:` — на вкус: скриншоты в `docs/screenshots/`
сняты с `en_us`, ранние прогоны 26.2 шли с `ru_ru`.

## 3. Запустить клиент

Только через `tools/mc-build.sh` — он держит глобальный Gradle-лок, а два Gradle разом
портят кэш Loom. Никогда напрямую `gradle`/`gradlew`.

```bash
cd /home/user/minecolonies-fabric

# 26.3
DISPLAY=:99 SDL_VIDEO_FORCE_EGL=1 \
  nohup tools/mc-build.sh 26.3 runClient > /tmp/runclient.log 2>&1 &

# 26.2
DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 \
  nohup tools/mc-build.sh 26.2 runClient > /tmp/runclient.log 2>&1 &
```

(`LIBGL_ALWAYS_SOFTWARE=1` в строке 26.2 — как в исходном рецепте, по которому 26.2 был
проверен. Для 26.3 он не нужен, см. §7; для 26.2 заново не перепроверялся.)

Ждать по логу, а не по таймеру:

```bash
until grep -qE "Could not authorize you against Realms|Created:.*blockui_gui" /tmp/runclient.log; do sleep 5; done
sleep 20   # титульному экрану нужно ещё несколько секунд после последнего атласа
```

Для 26.3 есть более ранний и более точный признак — что окно вообще создалось:

```
Created window using SDL video driver: x11
Using graphics backend OpenGL, using drivers: 4.5 (Core Profile) Mesa 25.2.8
```

Если вместо этого в логе `BackendCreationException` — читать §7, клиент до окна не дошёл.

**Первый запуск долгий.** `:downloadAssets` тянет ~640 МБ ванильных ассетов и **ничего не
печатает**; пять-десять минут тишины — норма, а не зависание. Последующие запуски его
пропускают: с прогретым кэшем и собранным модом от старта до титула около минуты.

## 4. Снять экран и кликнуть

Скомпилировать один раз; делает обе работы.

```java
// Shot.java
import java.awt.*; import java.awt.event.InputEvent;
import java.io.File; import javax.imageio.ImageIO;

public final class Shot {
    public static void main(final String[] args) throws Exception {
        final Robot robot = new Robot();
        final Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
        switch (args[0]) {
            case "click" -> {                                   // Shot click <x> <y>
                robot.mouseMove(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                Thread.sleep(400);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                Thread.sleep(90);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }
            case "burst" -> {                                   // Shot burst <prefix> <n> <gapMs>
                for (int i = 0; i < Integer.parseInt(args[2]); i++) {
                    ImageIO.write(robot.createScreenCapture(new Rectangle(size)), "png",
                        new File(String.format("%s-%03d.png", args[1], i)));
                    Thread.sleep(Long.parseLong(args[3]));
                }
            }
            default -> ImageIO.write(robot.createScreenCapture(new Rectangle(size)), "png",
                new File(args[0]));                             // Shot <file.png>
        }
    }
}
```

```bash
JAVA=/usr/lib/jvm/java-25-openjdk-amd64/bin
$JAVA/javac -d /tmp/shot Shot.java
DISPLAY=:99 $JAVA/java -cp /tmp/shot Shot /tmp/shot/title.png
DISPLAY=:99 $JAVA/java -cp /tmp/shot Shot click 766 465
DISPLAY=:99 $JAVA/java -cp /tmp/shot Shot burst /tmp/shot/dl 45 300
```

Файлы падают в рабочий каталог команды `java` — **держать их вне репозитория**, кроме тех,
что осознанно кладутся в `docs/screenshots/`. `burst` — способ поймать движущийся экран:
скачивание 78 МБ вместе с проверкой укладывается секунд в пятнадцать, так что 45 кадров по
300 мс покрывают его целиком, а нужный кадр выбирается потом.

**Координаты — координаты корневого окна, и игра не занимает весь экран.** Клиент открывает
окно 854×480 по центру рута 1280×720, то есть его собственный (0,0) — примерно (213,120).
Не вычислять положение кнопок из GUI-раскладки: снять скриншот, посмотреть на него, снять
центр кнопки с картинки и кликнуть туда. Захват идёт со всего рута, так что система
координат у скриншота и у `Robot` одна и та же.

Проверить, что окно вообще есть и где оно, можно без картинки:

```bash
DISPLAY=:99 xwininfo -root -children
#   0x200033 "Minecraft* 26.3 Snapshot 9": ("java" "com.mojang.minecraft")  854x480+213+120
```

### Порядок экранов

На 26.3 титульный экран показывается **не первым**. Поверх него MineColonies открывает свой
экран «MineColonies needs its assets» с кнопками Download / Not now / Use a jar I already
have… (`docs/screenshots/26.3-assetfetch.png`). Координаты рута, снятые с этого скриншота:

| кнопка | клик |
|---|---|
| Download | `Shot click 511 465` |
| Not now | `Shot click 766 465` |
| Use a jar I already have… | `Shot click 638 521` |

Клик по «Not now» открывает обычный титул (`docs/screenshots/26.3-title.png`) — внизу слева
`Minecraft 26.3 Snapshot 9 (Modded)`. На 26.2 тот же экран мода, но кнопка Download была на
`511 473`; сверяться со свежим скриншотом, а не с этой таблицей.

Экран мода полезен сам по себе: он доказывает, что клиентский entrypoint отработал и
перезагрузка ресурсов прошла, — то есть проверяет порт содержательнее, чем ванильный титул.

## 5. Что так проверяется, а что нет

Проверяется: любой экран, который появляется сам или по клику мышью — титул, экран согласия,
экран прогресса скачивания, экраны результата и всё, куда можно прокликаться. Перезагрузка
ресурсов и лог — как обычно.

Не проверяется: всё, что требует клавиатуры (обёртки над `Robot.keyPress` здесь нет, текстовые
поля не пробовались), и всё, что требует мира — вход в мир упирается в долгую генерацию на
llvmpipe, так что правый клик по хижине, партикл спящего жителя и команда `/minecolonies-client`
остаются непроверенными.

## 6. Остановить

```bash
pkill -f net.fabricmc.devlaunchinjector
```

Gradle после этого печатает `BUILD FAILED` для `runClient` — так выглядит убийство клиента, а
не поломка сборки. Проверить, что ничего не осталось: забытый клиент держит Gradle-лок, и все
последующие сборки встают за ним в очередь.

```bash
ps aux | grep -E "[d]evlaunchinjector|[g]radle"
flock -n /tmp/mc-build.lock -c true && echo "лок свободен"
```

Чтобы мод снова спросил про ассеты на следующем запуске, удалить то, что он поставил:

```bash
rm -rf 26.3/run/minecolonies
```

---

## 7. 26.3: SDL вместо GLFW, и что для этого нужно

26.2 открывает окно через GLFW, 26.3 — через **SDL**. Из-за этого рецепт выше на 26.3 без двух
добавок не работает вообще: клиент падает в `Minecraft.<init>` строкой 532, **до** окна, до
перезагрузки ресурсов и до любого клиентского entrypoint, так что ни строки кода мода не
исполняется.

```
BackendCreationException: Failed to create window for OpenGL context: Couldn't find matching GLX visual
BackendCreationException: Vulkan is not supported: Installed Vulkan doesn't implement VK_KHR_surface
```

Вулкан здесь не запасной путь: `/usr/share/vulkan/icd.d/` не существует, ICD в системе нет
вообще, так что `libvulkan1` загружать нечего.

### Причина

`GlBackend.createWindow` (`/opt/mc-src-26.3/com/mojang/renderpearl/backend/opengl/GlBackend.java:63-68`)
**безусловно** ставит `SDL_GL_SetAttribute(22, 1)` — это `SDL_GL_FRAMEBUFFER_SRGB_CAPABLE`:

```java
SDL_GL_SetAttribute(17, 3);   // CONTEXT_MAJOR_VERSION
SDL_GL_SetAttribute(18, 3);   // CONTEXT_MINOR_VERSION
SDL_GL_SetAttribute(20, 1);   // CONTEXT_PROFILE_MASK = CORE
SDL_GL_SetAttribute(19, 2);   // CONTEXT_FLAGS
SDL_GL_SetAttribute(22, 1);   // FRAMEBUFFER_SRGB_CAPABLE  <- вот это
```

Дальше всё решает то, каким путём SDL ищет пиксельный формат.

**На GLX-пути** это требование становится атрибутом `GLX_FRAMEBUFFER_SRGB_CAPABLE_ARB`
**при выборе визуала**: SDL добавляет его в список для `glXChooseFBConfig`, а затем ему нужен
визуал, который этому fbconfig соответствует. Расширение `GLX_ARB_framebuffer_sRGB` здесь
заявлено, но подходящих fbconfig'ов ноль — ни под одним драйвером Mesa:

| переменные | fbconfig | sRGB-capable | с визуалом | sRGB + визуал |
|---|---|---|---|---|
| — | 240 | **0** | 80 | **0** |
| `LIBGL_ALWAYS_SOFTWARE=1` | 240 | **0** | 80 | **0** |
| `GALLIUM_DRIVER=softpipe` | 240 | **0** | 80 | **0** |
| `GALLIUM_DRIVER=zink` | 380 | **0** | 100 | **0** |
| `MESA_GL_VERSION_OVERRIDE=4.6` | 240 | **0** | 80 | **0** |

(Колонка «с визуалом» — сколько fbconfig'ов вообще имеют X-визуал; она показывает, что дело
не в визуалах как таковых, а именно в sRGB. Таблица воспроизводится
`DISPLAY=:99 tools/sdl-probe/run.sh glx`.)

То есть **GLX-путь чинить нечем**: ни одна переменная Mesa sRGB-совместимого визуала не
создаёт.

**На EGL-пути такого поиска просто нет.** У EGL-конфигов нет атрибута sRGB — цветовое
пространство в EGL задаётся не конфигу, а поверхности, через `EGL_KHR_gl_colorspace`
(`EGL_GL_COLORSPACE = EGL_GL_COLORSPACE_SRGB`) в момент `eglCreateWindowSurface`. Поэтому
запрос, который на GLX проваливает выбор конфига, на EGL к выбору конфига вообще не
относится. Оба расширения присутствуют: `strings` по `libSDL3.so` показывает и
`GLX_ARB_framebuffer_sRGB`, и `EGL_KHR_gl_colorspace`, а `libEGL_mesa.so.0` последнее
предоставляет.

### Решение

```sh
DEBIAN_FRONTEND=noninteractive apt-get install -y libegl1 libegl-mesa0
```

До этого `libEGL` в системе отсутствовал целиком (`ldconfig -p` знал только `libGL`, `libGLX`,
`libGLX_mesa`), из-за чего `SDL_VIDEO_FORCE_EGL=1` падал на «Could not load EGL library».

Тянет апгрейд `libgl1-mesa-dri`, `libglx-mesa0`, `libgbm1`, `mesa-libgallium` с
`25.2.8-0ubuntu0.24.04.1` на `…-0ubuntu0.24.04.2` — **тот же upstream Mesa 25.2.8**,
отличается только ubuntu-ревизия. После установки проверены полная перекомпиляция мода и бут
сервера — зелёные. Клиент 26.2 после этого апгрейда заново **не** прогонялся.

Дальше — **одна переменная**, минимальность проверена отдельно:

| | результат |
|---|---|
| после установки EGL, без переменных | `Couldn't find matching GLX visual` |
| **`SDL_VIDEO_FORCE_EGL=1`** | окно создаётся, `GL 4.5 (Core Profile) Mesa 25.2.8 / llvmpipe` |

`LIBGL_ALWAYS_SOFTWARE`, `EGL_PLATFORM`, `GALLIUM_DRIVER`, `XDG_RUNTIME_DIR` **не нужны**
(последняя лишь убирает косметическое `error: XDG_RUNTIME_DIR is invalid or not set`).
Важно: сам по себе апгрейд Mesa ничего не чинит — контрольный запуск без переменных шёл уже
на `…24.04.2` и всё равно падал на GLX. Работает именно связка libEGL плюс
`SDL_VIDEO_FORCE_EGL=1`.

Переменная должна попасть в процесс клиента. `tools/mc-build.sh` запускает Gradle с
`--no-daemon`, поэтому окружение доходит до форкнутой JVM само; при запуске Gradle иначе,
с переиспользованием демона, переменную можно потерять.

### Быстрая проверка без клиента: `tools/sdl-probe/`

`tools/sdl-probe/SdlProbe.java` воспроизводит `GlBackend.createWindow` дословно — те же пять
`SDL_GL_SetAttribute`, включая 22 — и отвечает за секунды, не занимая глобальный Gradle-лок.
Варианты окружения нужно перебирать им, а не запусками клиента.

```bash
DISPLAY=:99 tools/sdl-probe/run.sh                          # как просит 26.3  -> FAIL на GLX
DISPLAY=:99 SDL_VIDEO_FORCE_EGL=1 tools/sdl-probe/run.sh    # EGL-путь         -> OK
DISPLAY=:99 tools/sdl-probe/run.sh nosrgb                   # контроль: без атрибута 22 -> OK
DISPLAY=:99 tools/sdl-probe/run.sh glx                      # перепись fbconfig'ов
```

`run.sh` сам собирает classpath из LWJGL 3.4.2 в кэше Gradle (`~/.gradle/caches/modules-2`),
компилирует в `/tmp/sdl-probe` и пробрасывает окружение как есть. Кэш должен быть прогрет
хотя бы одной сборкой. Печатает `RESULT=OK` или `RESULT=FAIL` последней строкой.

Пара `default` / `nosrgb` — то самое доказательство, что виноват именно атрибут 22, а не
что-то ещё в конфигурации.

### Чего делать не надо

Соблазнительный обход — миксин, снимающий `SDL_GL_SetAttribute(22, 1)` у `GlBackend`. Так
делать **нельзя**: такой миксин уедет в отгружаемый jar и поменяет рендер у всех игроков ради
здешнего CI. Проблема окружения чинится в окружении.

### Прочие пути

`SDL_VIDEODRIVER=offscreen` после установки EGL тоже создаёт окно и контекст, но X-окна при
этом не появляется, и `Robot` снять ничего не может — для скриншотов бесполезно.
`EGL_PLATFORM=x11` вместе с `SDL_VIDEO_FORCE_EGL=1` работает, но избыточен. Предупреждения
`libEGL warning: DRI3 error: Could not get DRI3 device` безвредны — это откат на swrast.
