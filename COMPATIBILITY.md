# Compatibility verification

Verified on October 25, 2026 for 1.0.0. The release compiles against Spigot 1.19, declares `api-version: '1.19'`, and uses Java 17 bytecode. No CraftBukkit, NMS, or Paper-only API is required by production code.

## Automated results

- Production sources compiled successfully against all **29 published stable Spigot API versions** from 1.19 through 26.3.
- All **159 Maven unit/regression tests passed**, including 26 audit regressions and hardening checks for cancellation, reconnects, storage failures, migration rollback, concurrency, and shared cell resources.
- The same release JAR passed **134 feature checks on each of seven Paper servers**, using real worlds, item metadata, inventories, scoreboards, events, JDBC drivers, and schedulers. Player behavior is driven through a simulated Bukkit `Player` interface.

| Paper version | Build | Java | Feature checks |
| --- | ---: | --- | --- |
| 1.19 | 81 | 17 | 134 passed |
| 1.19.4 | 550 | 17 | 134 passed |
| 1.20.6 | 151 | 21 | 134 passed |
| 1.21.11 | 132 | 21 | 134 passed |
| 26.1.2 | 74 | 25 | 134 passed |
| 26.2 | 135 | 25 | 134 passed |
| 26.3 | 223 | 25 | 134 passed |

The matrix exercises item payments without Vault, flatfile storage, and SQLite, including injected failures during property withdrawal/release. Before the locale update, an additional **108 checks passed on each of Paper 1.19 and 26.3** with Vault 1.7.3 and an isolated MariaDB 11.4.8 instance. These include upgrading old MySQL BLOB columns and persisting/reloading custom item data larger than 64 KiB. Those optional integrations were not rerun for the locale update; the SQL schema and item-storage implementation are unchanged.

The locale update adds 17 complete UTF-8 files with 275 entries each. The same release JAR executes localized and English player-help commands in every locale on every server in the table (34 new checks per server). Unit tests also cover aliases, syntax labels, tab completion, permission filtering, clickable navigation, prisoner command whitelisting, placeholder parity, nonrecursive substitutions, fallback behavior, preservation of custom values/comments and damaged YAML, runtime reload, sign defaults, and alias collision protection.

Release SHA-256: `a90c1910718405eb5fdaf7384398ee1667ecf1aeb3d8deded11c83075e8144ce`.

The probe checks loading, API metadata, commands and completion, the creation wand, jails and cells, signs, inmate clothing, scoreboards, book/item metadata serialization, offhand storage, inventory restoration, handcuff movement and teleport handling, escape/block/command/item protections, chat muting, payment calculations, Vault balance checks and withdrawals, transfers, force releases, sentence timers, storage migrations, exact SQL sentence-time persistence, and reload cleanup.

## In-game feedback update

The full audit findings have been addressed:

- Offline transfers finish placement without re-confiscating saved property on another login. Replacement armor is preserved when original armor is restored, including with general inventory storage disabled.
- Force-clear parses its target separately from flags and removes durable prisoner records, cache entries, and online state through the release lifecycle. Logged actors retain the initiating administrator.
- Teleport events and vertical/world movement enforce containment on the real event. Canceled kicks/transfers retain protection state. Activity tracking remains enabled in escape mode.
- SQL release failures retain both inventory and prisoner state. Withdrawals persist remaining property before awarding items. Failed migrations roll back SQL writes and retain the prior connection and retry flags. Data/record YAML uses atomic replacement; malformed data files are rejected without overwriting them.
- Finite sentence reductions stop at zero and schedule release. Region lookup checks the world. Hook substitutions treat reasons literally and isolate failed commands. Billing rounds remaining minutes up and rejects partial payments with no sentence benefit.
- Concurrent maps, volatile prisoner flags, and a deduplicated concurrent release queue protect async lookups and release requests. Chat recipient filtering avoids async server player lookups.
- Cell creation rejects shared signs/chest halves. Existing conflicts retain their data but disable access and ambiguous sign updates until corrected; checks avoid forcing unloaded chunks.
- Cell assignments and world names load independently of world availability. Block-origin explosions receive the same region protection as entity explosions. Config candidates are validated before managers are replaced, and occupied-cell transfers return feedback safely.
- Commons Lang is updated to 3.21.0, bundled libraries are relocated, original attribution/license files remain packaged, and obsolete upstream deployment destinations are removed.

The automated player is simulated; real-client inventory gestures and network reconnects still deserve normal in-game acceptance testing. These checks exercise failed operations and retries, not arbitrary power-loss atomicity across Minecraft player/world files and a separate database.

- The brick wand consumes block/item use before selecting signs and double chests, including duplicate and offhand events. Runtime probes select real signs and connected double chests through the creation steps.
- `/jail` opens MailboxGUI-style help with gold/yellow titles, permission-filtered player/admin sections, eight-line pages, and clickable blue gradient navigation. The header includes `Originally By: graywolf336` between the version and command title. Explicit player/admin help views retain their view when paging.
- New installations default to SQLite. All seven penalties default to numeric `0`; zero without units and existing duration formats are tested.
- Interaction defaults include `'*'`, covering every runtime material in both hands, against blocks, in the air, and for item use on entities. Unsupported explicit names do not require material resolution or produce warnings. The configured allowance for the prisoner's own double chest remains available.
- Every shipped scalar/list setting has explanatory comments. Startup and reload refresh documentation without resetting owner values. The header includes all requested original/fork credit, download/source/support links, no-sale statement, and unchanged-license policy.
- The unchanged original `LICENSE` and fork `NOTICE` are included in the release JAR.

## Reload and sign feedback (1.0.0)

- The JAR filename, plugin descriptor, config header, and command/help output use plain `1.0.0`, without snapshot, compatibility-range, or build suffixes.
- A deleted config is regenerated with the complete header and comments on each setting. Obsolete updater values are removed from existing configs.
- Invalid YAML and invalid vote/AFK durations are rejected before replacing live settings. Reload does not overwrite malformed YAML.
- Reload refreshes debug state, AFK settings, and scheduler mode; unchanged settings do not duplicate sentence or autosave tasks. Unit tests also exercise switching between Java and Bukkit timers.
- Dirty data is flushed before storage is reopened. Failed connection/table setup preserves the previous connection; changing database identity or table prefix migrates cached jail data.
- Registered signs are waxed automatically where the API supports waxing, and remain writable through plugin updates. Player edit and interaction events are blocked, including both hands. Loaded signs are refreshed on startup/reload and on chunk load without force-loading other chunks.
- The server probe covers native waxing, rejected sign edits, repeated reload task counts, debug/AFK updates, malformed YAML, config deletion, comment restoration, and prisoner persistence after regeneration.

## Property chest and uniform protection

- Cell property chests are read-only with `moveProtection: true`. With `false`, the assigned prisoner may click/shift-click to reclaim a stack or right-click for one item directly into storage slots. `openChest` must also be enabled. Vacant cells, other prisoners, outsiders, and operators cannot open them. Both halves, including separate block inventories exposed to hoppers, are protected.
- Reclamation decrements the authoritative snapshot and persists merge-on-release behavior through reload/restart/transfer; reclaimed items are neither duplicated nor cleared on release. Full inventories leave property stored. Deposits, drags, swaps, automated extraction/deposits, chest destruction, and explosion drops remain blocked. Jail gear is issued when jailed but may be removed/replaced with `moveProtection: false`; rejoining, respawning, transferring, and reloading do not force it back on in that mode. Unit checks cover every click type in protected mode.
- Original property is captured synchronously before clothing is issued, using a slot-preserving snapshot as the authoritative store. The chest displays copies. Transfers between cells, no-cell jails, and chests preserve that snapshot and no longer leave duplicate jail entries. Property is restored before its prisoner record is removed.
- Paper snapshots use native item-NBT bytes, with a Bukkit metadata fallback elsewhere. Legacy serialized inventories remain readable. Native byte snapshots require a server exposing the native byte API, and newer item formats cannot be downgraded to older Minecraft versions.
- Runtime checks preserve custom model data, lore, damage, a 90,000-byte random PDC payload, null slots, original head-slot equipment, and offhand contents. Both native and legacy/Bukkit metadata roundtrips are exercised.
- MySQL inventory and armor columns use LONGBLOB and existing BLOB columns are upgraded. Extra checks verify both schema migration and save/reload of the large custom item on 1.19 and 26.3.
- With movement protection enabled, required uniforms are marked, unbreakable, and protected from clicks, drags, shifts, right-click swaps (including newer equippable components), drops, and armor stands. In that mode uniforms do not become death loot and are reissued after respawn/join. Original armor is saved even when general inventory confiscation is disabled. Item payments cannot consume tagged uniform pieces, even if the configured payment material matches them. Release removes tagged gear from both armor and storage slots.
- The approved CONSOLE-CONCEPT.txt ASCII banner and structured startup/player/reload/shutdown logs are implemented. Runtime checks confirm one banner, one ready line, startup summaries, completed player actions, and shutdown summaries on every server. Unit tests cover log injection, audit settings, UUID visibility, rate limiting, failure reporting, and partial-startup shutdown safety.

## Changes needed for this range

- Set both the compile API and plugin descriptor to 1.19, with Java 17 bytecode.
- Replace SnakeYAML's removed internal Base64 helper with Java's Base64 API; older wrapped inventory data remains readable.
- Remove the obsolete upstream update checker, its join notifications, scheduled queries, settings, and now-unused JSON Simple dependency. Prevent VaultAPI from introducing an older Bukkit API into the compile classpath.
- Deliver sentence-timer events and serialize jail state on the server thread.
- Use string scoreboard entries and cancel the previous scoreboard task during reloads.
- Route namespaced jail commands using the registered command name.
- Handle air/offhand item interactions and check connected cell chests, including trapped chests.
- Redirect handcuffed movement/teleports without starting recursive teleport events.
- Correct missing-cell transfer events, online-state reporting, and release/transfer cache cleanup.
- Clear jail-stick users safely when reloading or disabling the feature.
- Allow item payments without Vault and use the configured price for infinite sentences.
- Support modern and legacy MySQL driver class names, close replaced connections, save unchanged prisoners when migrating storage, and write sentence times as SQL `BIGINT` without float rounding.
- Restore the default reason when omitted and substitute language variables literally.
- Replace obsolete PowerMock/CraftBukkit test fixtures with Mockito and public API interfaces.

## Reproduction and limits

Build and unit tests: `mvn clean verify`.

API matrix: `python scripts/check_apis.py`, using JDK 25.

Runtime matrix: `python scripts/check_servers.py --accept-eula`. See the README for optional Vault/MySQL flags and environment variables. Downloaded server JARs are verified against Paper's published SHA-256 values. The probe is built separately and is excluded from the release JAR. Test worlds and logs stay outside the project under the OS temporary directory in `JailReloaded-verification`; no existing server is accessed.

Source compilation covers all API versions in the range; runtime checks cover the seven versions listed above. Spigot runtime behavior is inferred from public API usage and Spigot API compilation, rather than a separate Spigot server run. Live networked clients, particular economy providers, other plugins' event overrides, still need environment-specific acceptance testing. The legacy WorldEdit/Multiverse soft dependencies remain declared; this change does not add commands listed as unfinished in the upstream roadmap.

Future machine-readable reports are written under the OS temporary directory in `JailReloaded-verification/results.json` and `JailReloaded-verification/apis/results.json`; optional integration report paths are supplied with `--report-file`. Historical scratch reports were removed when cleaning the project. Each server directory contains `build.json` and `probe.log`.
