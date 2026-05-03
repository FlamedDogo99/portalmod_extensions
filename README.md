# PortalMod Extensions

Adds energy pellets, dispensers, and receivers to [PortalMod](https://github.com/FlamedDogo99/PortalMod). Built for Minecraft 1.16.5 with Forge.

## Requirements

- Minecraft `1.16.5`
- Forge `36.2.34`
- [PortalMod](https://github.com/snowy-shack/PortalMod) `1.2-forge-1.16.5`

---

## Building from source

### Prerequisites

- `Java 11 (JDK)` required to run Gradle and ForgeGradle
- A copy of `portalmod-*.jar` placed in the `./libs/` folder

> The `libs/` folder is used as a local compile-time dependency. Without portalmod present there, the build will fail.

> The project targets Java 8 bytecode but the build toolchain requires Java 11. If Gradle picks up the wrong JDK, set `JAVA_HOME` before building.

### Steps

```bash
# Clone the repo
git clone https://github.com/FlamedDogo99/portalmod_extensions
cd portalmod_extensions

# Place portalmod jar in libs/
cp /path/to/portalmod-1.2-forge-1.16.5.jar libs/

# macOS: point JAVA_HOME at your Java 11 install
export JAVA_HOME=$(/usr/libexec/java_home -v 11)

# Build
./gradlew build        # Linux/macOS
gradlew.bat build      # Windows
```

The output jar will be in `build/libs/`.

> **Note:** The first build will take a while as ForgeGradle downloads Minecraft and deobfuscation mappings.
