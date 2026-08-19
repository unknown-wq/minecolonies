# Running the 26.2 client headless, and taking screenshots of it

This box has no display, but it has `Xvfb` and Mesa's software rasteriser, which is enough
for a real Minecraft client: `GL 4.5 (Core Profile) Mesa 25.2.8 / llvmpipe`. Everything
below was run for the asset-fetch UI work; WP6 used the same mechanism
(`docs/assetfetch/WP6-ACCEPTANCE.md` §4.2).

There is **no** `xdotool`, `scrot`, `import`, `xwd` or `ffmpeg`, and none were installed.
Both the screen capture and the mouse clicks come from `java.awt.Robot`, which talks to the
X server directly. (WP6 §4.4 concluded that input was impossible; it is not — see §4.)

## 1. Start a display

```bash
Xvfb :99 -screen 0 1280x720x24 >/tmp/xvfb99.log 2>&1 &
```

One is usually already running; `DISPLAY=:99` below assumes this one.

## 2. Seed the run directory before the first launch

You cannot click through the options menu, so anything you need set has to be in
`26.2/run/options.txt` **before** the client starts:

```bash
mkdir -p /workspace/<worktree>/26.2/run
cat > /workspace/<worktree>/26.2/run/options.txt <<'EOF'
lang:ru_ru
guiScale:2
soundCategory_master:0.0
pauseOnLostFocus:false
EOF
```

## 3. Launch the client

Always through `mc-build.sh` — it holds the global Gradle lock, and two Gradle runs at once
corrupt the Loom cache. Never `gradle`/`gradlew` directly.

```bash
cd /workspace/<worktree>/26.2
DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 \
  nohup /home/user/mc-build.sh /workspace/<worktree>/26.2 runClient > /tmp/runclient.log 2>&1 &
```

Then wait on the log rather than on a timer:

```bash
until grep -qE "Could not authorize you against Realms|Created:.*blockui_gui" /tmp/runclient.log; do sleep 5; done
sleep 20   # the title screen needs a few more seconds after the last atlas
```

**First launch is slow.** `:downloadAssets` fetches ~640 MB of vanilla assets and prints
nothing while it does; five to ten minutes with no output is normal, not a hang. Later runs
skip it.

## 4. Capture, and click

Compile this once; it does both jobs.

```java
// Shot.java
import java.awt.*; import java.awt.event.InputEvent; import java.awt.image.BufferedImage;
import java.io.File; import javax.imageio.ImageIO;

public final class Shot {
    public static void main(final String[] args) throws Exception {
        final Robot robot = new Robot();
        final Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
        switch (args[0]) {
            case "click" -> {                                   // Shot click <x> <y>
                robot.mouseMove(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                Thread.sleep(300);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                Thread.sleep(80);
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
/usr/lib/jvm/java-25-openjdk-amd64/bin/javac Shot.java
DISPLAY=:99 /usr/lib/jvm/java-25-openjdk-amd64/bin/java -cp . Shot shot.png
DISPLAY=:99 /usr/lib/jvm/java-25-openjdk-amd64/bin/java -cp . Shot click 511 473
DISPLAY=:99 /usr/lib/jvm/java-25-openjdk-amd64/bin/java -cp . Shot burst dl 45 300
```

Files land in the working directory of the `java` command — put them somewhere outside the
repo. `burst` is how you catch a moving screen: a 78 MB download and its verify pass are
over in about fifteen seconds, so 45 frames at 300 ms covers the whole thing and you pick
the frame you want afterwards.

**Coordinates are root-window coordinates, and the game does not fill the screen.** The
client opens an 854×480 window centred on the 1280×720 root, so the game's own (0,0) is
around (214,120). Do not compute button positions from the GUI layout: take a screenshot,
look at it, read the button's centre off the image, and click that. The capture is of the
whole root window, so the two coordinate systems are the same one.

## 5. What is and is not testable this way

Testable: every screen that appears on its own or behind a mouse click — the title screen,
the consent screen, the download progress screen, result screens, and anything reachable by
clicking through them. Resource reloads and the log are testable as usual.

Not testable: anything needing the keyboard (no `Robot.keyPress` wrapper here, and text
fields were never tried), and anything needing a world — entering one takes a long
llvmpipe world generation, so hut right-clicks, the sleeping-citizen particle and the
`/minecolonies-client` command remain unobserved.

## 6. Stop it

```bash
pkill -f net.fabricmc.devlaunchinjector
```

Gradle then prints `BUILD FAILED` for `runClient`; that is what killing the client looks
like, not a build problem. Check that nothing is left behind — a stray client keeps the
Gradle lock and every later build queues behind it:

```bash
ps aux | grep -E "[d]evlaunchinjector|[g]radle"
```

To make the mod ask for the assets again on the next launch, delete what it installed:

```bash
rm -rf /workspace/<worktree>/26.2/run/minecolonies
```
