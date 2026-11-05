# Neural Link

A Fabric mod for Minecraft. Implant a mob, put on the visor and control it from the inside. Your own body stays where you left it.

The mob stays itself: a ghast floats slowly and spits fireballs, a fish chokes on land, an iron golem sinks, a slime can only jump.

## Features

- Any mob, with up to three abilities and its own way of moving. Bosses too, once they're turned on in the settings.
- One visor holds up to 8 implants. Hop between mobs without going back to your body.
- The implants belong to the visor: whoever has it gets the mobs.
- The visor marks your mobs through walls and points to the ones off screen. The signal fades with distance and deep underground.
- Works on servers: the server checks everything, and a disconnect or crash puts you back in your body.

## Requirements

- Fabric Loader and Fabric API
- Install on both client and server
- Optional: Mod Menu for the settings screen, Cloth Config for a second settings window

Supported Minecraft versions are listed on Modrinth. The version this code builds for is set in `gradle.properties`.

## Building

Needs Java 21.

```
./gradlew build
```

On Windows run `gradlew.bat build`. The mod jar is in `build/libs`, the one without `-sources`.

## License

Code is MIT (`LICENSE`), assets are CC BY-NC-SA 4.0 (`LICENSE-ASSETS`). Videos, streams (monetized too) and modpacks are fine, no need to ask.
