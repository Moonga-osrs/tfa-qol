# TFA QoL & Mistake Tracker

A standalone Java 11 RuneLite plugin for The Fractured Archive. It does not require Moonga Launcher or an external server.

**Not approved by RuneLite or Jagex.** Publishing this source and opening a Plugin Hub pull request do not constitute approval. RuneLite currently lists new high-end PvM boss plugins among features not being considered. This repository retains the complete functionality for a transparent review; maintainers may reject it or require substantial reductions.

## Features

- Archive Door spellbook and chinchompa reminders; optional right-click requirement when chinchompas are missing.
- **Bank-all single click** in the TFA lobby and loot room (enabled by default).
- **Hidden loot**: hide the sparkle on your own chests until crossing the reveal threshold (disabled by default). These features adapt LlemonDuck's implementation with its BSD-2-Clause attribution retained.
- Local side-panel observations for Archive Guardian, Ezekiel, Bork and the Smorks, Dreadnoth, Xarth and Ocellus.
- Impact-time protection-prayer checks where supported; ambiguity and status damage remain separate from automatic mistake counters.
- Observed deaths appear as skull counters, once per player per tracked encounter; no cause of death is inferred.
- Optional tracking of Bork tank hits and Ocellus P1 floor cracks.
- Manual `::safetiles 1`, `2`, `3`, `4`, `p1`, `p2`, `p3`, and `off` commands. Portal mode uses the current visible pattern only, without forecasting a future pattern.
- Local JSON reports under RuneLite's dedicated plugin directory. Nothing is uploaded. The Report location action displays the directory; it does not launch an external application.

See [SAFE_TILES.md](SAFE_TILES.md) for the exact highlights and limitations.

## Development

Install a Java 11 JDK, then run:

```sh
./gradlew clean check jar
./gradlew run
```

On Windows use `gradlew.bat`. The development run uses RuneLite's external-plugin entry point. No account details, credentials, profiles or recordings are supplied. Authenticate through your own supported account flow when testing.

The build uses RuneLite 1.13.4 and emits Java 11 bytecode. The distributable JAR contains only this plugin and its image resources; RuneLite and test dependencies are not bundled.

## Review and limitations

This plugin changes the local UI and observes client events; it does not send gameplay actions, perform account switching, load remote code, use reflection in production code, or transmit data to a server. Test code uses Mockito/reflection to inject fixtures; test code is not included in the plugin JAR.

Mistakes are provisional correlations, not authoritative proof of a player's error. Only loaded actors are observable. Ground-effect anchor tiles and object footprints do not prove the complete damage radius. No acceptance or rule-compliance guarantee is made, especially for boss overlays, current safe-position indications and newly released content. See the [RuneLite rejected-feature policy](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).

Source: BSD-2-Clause. Game-art provenance: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
