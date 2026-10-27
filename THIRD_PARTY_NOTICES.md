# Third-party notices

The Java source is licensed under BSD-2-Clause. RuneLite is an external compile-time dependency and is not redistributed as a client in this repository.

The bundled NPC, item and effect thumbnails are rendered from Old School RuneScape game assets. Those assets belong to Jagex; the source-code license does not grant ownership of Jagex artwork. The model IDs and rendering provenance are listed in src/main/resources/tv/moonga/tfaqol/icons/SOURCES.txt.

The Gradle wrapper is distributed under Gradle's Apache-2.0 license. JUnit and Mockito are test dependencies only. Replay test fixtures contain anonymized participant identifiers and game-event evidence, not account credentials or saved RuneLite profiles.

## Loot options

Loot options adapt the behavior of LootLeftClickBankAll and LootNoEarlySparkle from [The Fractured Archive](https://github.com/LlemonDuck/the-fractured-archive), revision 44c61af9b6bf1cc9f3575dcf83f873a5acd09857, copyright (c) 2026 LlemonDuck. The BSD-2-Clause notice is bundled in src/main/resources/tv/moonga/tfaqol/licenses/the-fractured-archive-LICENSE.txt and distributed in the plugin JAR. Adaptations narrow sparkle removal to its own effect slots, retain unrelated effects and avoid restoring delayed state into a different world view/session.
