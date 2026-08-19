# Gradle 9.6.1 distribution (offline)

Minecraft **26.2** needs Java 25, which needs **Gradle 9.x** (Gradle 8.x cannot
run on Java 25). In this environment the Gradle wrapper cannot download its
distribution — `services.gradle.org` redirects to GitHub release assets, which
the egress policy blocks (HTTP 403). So the distribution is vendored here as a
multi-volume RAR (each part is < 100 MB to stay under GitHub's file-size limit).

## Install

`install.sh` is self-contained: it installs the apt packages the build needs
(`unrar`, `unzip` and `openjdk-25-jdk`) and then unpacks Gradle. Nothing has to
be installed by hand first.

```sh
./gradle-dist/install.sh          # installs deps, extracts to /opt/gradle-9.6.1
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH=/opt/gradle-9.6.1/bin:$JAVA_HOME/bin:$PATH
gradle --version                  # Gradle 9.6.1, Launcher JVM 25
```

The script prints those two `export` lines at the end with the paths it actually
resolved, so you can copy them straight out of its output.

Then build with the system Gradle (not `./gradlew`, whose wrapper download is
blocked):

```sh
gradle build --no-daemon
```

It is idempotent — re-running it when everything is present just reprints the
paths. Each step is skipped if already satisfied:

- `unrar` / `unzip` — installed only if not on `PATH`.
- Java 25 — installed only if neither `/usr/lib/jvm/java-25-openjdk-amd64` nor
  the `java` on `PATH` reports major version ≥ 25. A box with only Java 21 gets
  `openjdk-25-jdk`.
- Gradle — extracted only if `/opt/gradle-9.6.1` does not already exist.

It runs `apt-get update` before installing, because the shipped package index is
stale and `openjdk-25-jdk` otherwise fails to fetch with a 404 from
`archive.ubuntu.com` even though `apt-cache policy` lists the version. It uses
`sudo` only when not already root, so it works in a root container where `sudo`
is absent.

Useful knobs:

| Variable | Effect |
|---|---|
| `SKIP_APT=1` | Do not touch apt; fail listing what is missing. |
| `GRADLE_DEST=/some/path` | Install Gradle somewhere other than `/opt/gradle-9.6.1`. |

## Files

- `gradle-9.6.1-bin.part1.rar` … `part5.rar` — volumes of `gradle-9.6.1-bin.zip`.
- `install.sh` — installs `unrar`/`unzip`/`openjdk-25-jdk`, then unpacks the
  volumes and unzips to `/opt/gradle-9.6.1`.

## On the Claude Code container image

The image ships Java 21 and `/opt/gradle` **8.14.3**, and Gradle 8.x cannot run
on Java 25 — it dies with `Unsupported class file major version 69` while
parsing the build script. So always put `/opt/gradle-9.6.1/bin` first on `PATH`
(or invoke it by absolute path) rather than using the `gradle` already there.

`install.sh` handles the rest of the image's gaps on its own.

Dependency resolution needs no extra proxy work: `maven.fabricmc.net`,
`plugins.gradle.org` and `piston-meta.mojang.com` are all reachable, so Loom
downloads Minecraft 26.2 and the Fabric toolchain normally. The first
`compileJava` takes roughly 70 s; afterwards the build is incremental.

`PORT-CHEATSHEET.md` says not to run Gradle because the porting orchestrator
compiled centrally. That applies to the bulk port only — for ordinary work a
local build is the fastest way to check a change.
