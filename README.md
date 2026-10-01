# Emperor Time — Fabric 1.21.11

This mod adds two right-click items inspired by the TikTok “Emperor Time” effect:

- **Caboom** (echo shard): aim at a block and right-click. It starts a 200-block-wide bowl crater with sculk/deepslate fragments, tall jagged sculk growths, soul-sand patches, soul-fire patches, block-shard particles, cyan sculk souls, sonic-boom flashes and sound. The crater is carved from the terrain under the target. It works through the server and spreads its block edits over time to reduce a single-tick freeze.
- **Flight** (feather): right-click to toggle survival flight on or off.

The effect is an approximation using Minecraft’s vanilla blocks and particles; the video uses custom mod visuals.

## Get the items

Use either creative inventory (Tools & Utilities) or:

```text
/give @s godcore:caboom
/give @s godcore:flight
```

Caboom changes terrain over a very large area. Make a world backup before using it. Some crater blocks beyond currently loaded chunks may be skipped.

## Install

Put the built `emperor-time-1.0.0.jar` into the `mods` folder for your Fabric 1.21.11 instance. Fabric API is required.

## Build the JAR on GitHub

This project includes a GitHub Actions workflow at `.github/workflows/build.yml`.

1. Create a GitHub repository and upload the contents of this project folder (not the ZIP file itself).
2. Open the repository's **Actions** tab and select **Build Fabric mod**.
3. Click **Run workflow**. The workflow builds the mod and uploads an artifact named `emperor-time-fabric-mod`.
4. Open the completed workflow run, download the artifact, unzip it, and put `emperor-time-1.0.0.jar` in your Fabric 1.21.11 `mods` folder.

The workflow uses Java 21 and Gradle 9.7.1. GitHub may ask you to enable Actions the first time you open the tab.

## Build from source

Use Java 21 and Gradle 9.7.1 or newer, then run `gradle build` in this folder. The output JAR is created in `build/libs`.
