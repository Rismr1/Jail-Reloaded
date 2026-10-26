![Jail-Reloaded](assets/branding/jail-reloaded-brand-kit/jail-reloaded-banner.png)

# Jail-Reloaded

**Jail Management, Prisoner Protection &amp; Configurable Escape Gameplay**

Jail-Reloaded is a maintained fork of **graywolf336's original Jail plugin**, updated for **Spigot and Paper servers running Minecraft 1.19–26.3**. Give your staff a configurable alternative to kicks and bans: create jails and individual cells, assign temporary or indefinite sentences, manage prisoner behavior, and return stored belongings when players are released.

Build a secure detention system or an escape-focused roleplay prison. Configure movement restrictions, block and item interactions, chat muting, inmate clothing, property chests, sentence payments, and player votes to suit your server. Familiar commands, guided creation, permission-filtered help, and documented configuration make day-to-day administration easier.

> **Originally By: graywolf336**<br>
> Fork maintained by **Rismr1 | ImagineCraft Plugins**. Jail-Reloaded is free and will never be sold. Original project credit, the repository's original license, and contributor attribution are preserved.

## Explore Jail-Reloaded

[Version Support](#version-support) · [Features](#features--v100) · [Commands and Permissions](#commands-and-permissions) · [Installation](#installation) · [Dependencies](#dependencies) · [Support](#support) · [Original Author and Project Credits](#original-author-and-project-credits)

---

## Version Support

| Compatibility | Details |
| --- | --- |
| **Current release** | Jail-Reloaded 1.0.0 |
| **Platforms** | Spigot • Paper |
| **Minecraft versions** | 1.19–26.3 |
| **Plugin API baseline** | 1.19; one plugin JAR for the supported range. |
| **Java** | Java 17 minimum for the plugin; use the Java version required by your server. Recorded tests use Java 17 for 1.19/1.19.4, Java 21 for 1.20.6/1.21.11, and Java 25 for 26.x. |
| **Storage** | SQLite (default) • MySQL/MariaDB • YAML flatfile |
| **Locale support** | 17 languages; see the list below. |
| **Required plugins** | None for the core jail system. Vault and a compatible economy plugin are required for money-based sentence payments. |
| **Resource pack** | Not required. |
| **Availability** | Free; no paid or Premium edition. |

**Languages:** English • Finnish • German • Spanish • French • Italian • Japanese • Korean • Dutch • Polish • Brazilian Portuguese • Russian • Swedish • Ukrainian • Simplified Chinese • Traditional Chinese • Hong Kong Traditional Chinese

### Jail-Reloaded Release Support

| Release | Support Information |
| --- | --- |
| **1.0.0** | Current maintained fork release, targeting Spigot/Paper 1.19–26.3. Support and updates are provided through ImagineCraft Plugins upon user request. |
| **Original Jail releases** | Historical upstream releases. Back up configuration and prisoner data before migrating; use the original project links for upstream documentation and history. |

**Compatibility verification:** The recorded 1.0.0 checks include compilation against 29 stable Spigot API versions and runtime feature checks on Paper **1.19, 1.19.4, 1.20.6, 1.21.11, 26.1.2, 26.2, and 26.3**. Spigot compatibility is based on public API use and API compilation; the runtime matrix uses Paper. These automated checks use simulated player interfaces; other plugins and live-player behavior still depend on your server setup.

---

## Features — v1.0.0

### Jails and Cells

- Create multiple named jails with defined cuboid regions.
- Maintain jails across multiple worlds.
- Create individually named cells within a jail.
- Use guided in-game creation with a selection wand.
- Set jail/cell arrival positions and a jail release location through the creation process.
- Assign a prisoner to a specific jail and cell, or automatically select an available cell.
- Use a configured default jail or nearest-jail selection.
- List jails and cells, inspect occupancy, and check prisoner information.
- Transfer individual prisoners between jails or cells, or transfer all prisoners from one jail to another.
- Teleport staff or a specified player to a jail's arrival/release location with permission-controlled commands.
- Stop an unfinished creation session and confirm supported destructive management actions.

### Sentences and Release

- Jail online players and queue jailings for offline players, subject to configuration.
- Control whether players who have never joined may be jailed.
- Assign timed or indefinite sentences with a configurable default duration.
- Use readable durations such as `30s`, `5m`, `1h`, and `2d`.
- Add, remove, set, or inspect remaining sentence time.
- Choose whether established sentences continue counting down while a prisoner is offline.
- Complete pending offline jailings when the player next joins.
- Release prisoners when their timed sentence expires, or release them manually.
- Support force-release administration for prisoner records requiring intervention.
- Record the jailer and reason for each jailing.
- Choose the prisoner's jail game mode and optionally restore their previous game mode on release.
- Release players at a configured jail exit or optionally return them to their saved pre-jail location.
- Run configured console commands after jailing and normal release, with player, UUID, and reason placeholders.

### Prisoner Protections

- Movement protection returns attempted escapes to the jail/cell arrival point.
- Block breaking and placement protections with configurable material whitelists.
- Command restrictions with a configurable command whitelist.
- Configurable block-interaction and held-item blacklists.
- Wildcard `'*'` rules cover all runtime materials, including materials introduced by newer server versions.
- Item-use restrictions cover both hands and supported interactions in the air, on blocks, and on entities.
- Crop-trampling protection.
- Separate configurable sentence penalties for prohibited movement, block breaking, block placement, commands, crop trampling, block interactions, and item interactions.
- Set a penalty to `0` to keep the protection active without adding sentence time.
- Automatically mute newly jailed players, or toggle an individual prisoner's chat mute.
- Configure whether prisoners receive normal chat messages.
- Configure minimum and maximum prisoner hunger levels.
- Exclude prisoners from the sleeper count used to skip night.
- Configure AFK kicking for inactive prisoners, or disable it.
- Protect jail-region blocks against explosions and enderman block movement.

**Default behavior:** New installations use strict interaction rules and zero additional sentence penalties. Existing installations retain their saved settings. Review the supplied configuration to choose the rules appropriate for your server.

### Inventory and Property Chests

- Optionally confiscate inventory when a player is jailed and return stored belongings on release.
- Preserve inventory slots, armor, and offhand contents in the saved property snapshot.
- Preserve supported item metadata, including lore, damage, custom model data, books, and persistent custom data.
- Optionally discard configured blacklisted materials instead of storing them.
- Associate a double chest with a cell, including trapped chests.
- Keep stored property available in prisoner data when no property chest is assigned.
- Protect both halves of registered property chests against unauthorized access, destruction, and automated item transfers.
- Restrict chest access to the assigned prisoner when `openChest` is enabled; vacant cells and other players remain protected, including against operator access.
- Show stored property as protected previews while movement protection is enabled.
- Preserve remaining property through jail/cell transfers, reloads, and restarts.
- Restore the original inventory slots when no property has been reclaimed; after reclamation, preserve already-carried items and return only the remaining saved property.
- Drop release overflow at the player's location when their inventory has no space.

**Item compatibility:** Paper uses native item-data serialization where available, with a Bukkit metadata fallback on other supported implementations. Legacy stored snapshots remain readable. Native snapshots require compatible native-byte support; moving newer stored items to older Minecraft versions is not supported.

### Escape Gameplay and Property Reclamation

Use `jailing.during.moveProtection: false` to allow escape gameplay and unlock supported prisoner equipment/property actions.

- Allow prisoners to leave the defined jail region instead of returning them automatically.
- Allow prisoners to remove or replace their issued jail clothing.
- When `openChest` is enabled, let the assigned prisoner reclaim property directly from their own chest into available inventory storage slots.
- Click or shift-click to reclaim a stack; right-click to reclaim one item.
- Leave items safely stored when the player's inventory is full.
- Update saved property as items are reclaimed so release does not return them twice.
- Preserve reclamation state through reloads, restarts, transfers, and later changes to movement protection.
- Continue blocking chest deposits, dragging, hotbar/offhand swaps, and hopper transfers.

Other configured restrictions still apply. Review block/item blacklists and command rules when designing an escape prison. Allowing a player to leave the region does not itself clear their sentence; use normal release or your own command/API integration to complete an escape scenario.

### Inmate Uniforms

- Issue a configurable unbreakable jail uniform when a player is jailed.
- Choose armor materials and RGB colors for leather armor.
- Store original equipment before issuing jail clothing.
- Lock uniforms while movement protection is enabled, including protection against inventory movement, drops, armor swaps, and armor-stand interactions.
- Restore required uniforms after respawning or rejoining in protected mode.
- Allow removable/replacement equipment in escape mode without forcing the uniform back on after transfers, reloads, respawns, or rejoining.
- Re-equip the uniform when movement protection is re-enabled, preserving replacement armor in inventory or dropping overflow.
- Prevent issued uniform pieces from being consumed as jail-payment items.
- Remove tagged jail gear on release.

### Cell Signs and Scoreboards

- Display prisoner information on registered cell signs.
- Customize up to four sign lines with color codes and placeholders for the player, UUID, reason, jailer, remaining minutes, readable sentence time, and AFK time.
- Display a configurable sentence-time sidebar with a custom title and time label.
- Refresh registered signs on startup, reload, and chunk load.
- Protect registered signs from player edits and item interactions.
- Automatically wax registered cell signs on server versions exposing native waxing support.
- Clean, refresh, update, or verify signs through staff commands.

### Handcuffs and Jail Sticks

- Handcuff a player at their current location to restrict movement and teleporting.
- Remove handcuffs with a staff command.
- Enable hit-to-jail tools through `/jail stick` and the configured jail-stick materials.
- Configure each jail stick's material, sentence duration, destination jail, and reason.
- Optionally require a target to be below a configured health threshold before a stick can jail them.
- Control jail-stick access per material and configure who may be jailed by sticks.

### Sentence Payments

- Let players view their jail bill and pay to reduce a timed sentence or buy release.
- Allow a player to contribute a payment toward another prisoner's sentence.
- Configure the price per remaining minute and a separate flat price for indefinite sentences.
- Use physical item payments without Vault.
- Use money payments with Vault and a compatible economy provider.
- Disable timed-sentence or indefinite-sentence payments by setting the corresponding price to `0`.
- Enable or disable the jail-payment system in configuration.

### Player Jail Votes

- Allow permitted players to start a vote to jail an online player.
- Accept yes/no votes with one vote per participant for a given target.
- Configure the vote duration and minimum number of yes votes.
- Require more yes votes than no votes for a successful result.
- Configure the sentence and reason applied by a successful vote.
- Control vote access, vote-start permissions, and target immunity.
- Enable or disable jail voting in configuration.

### Administration and Customization

- Paginated chat help with separate player/admin views, clickable navigation, and permission-filtered entries.
- Tab completion for supported commands, jail names, cells, and player selections.
- Configurable jail broadcasts with permission-controlled recipients.
- Stored jailing history with `/jail record` when profile logging is enabled.
- Configurable console audit logging for completed actions and an optional debug mode.
- SQLite storage by default, external MySQL/MariaDB, or YAML flatfile storage.
- Storage migration handling when changing configured storage; back up data before switching engines.
- Support for large serialized inventory records in MySQL through upgraded inventory columns.
- Support for the 17 languages listed above, with configurable locale selection and editable messages.
- Traditional `&` color codes in supported configurable text, signs, and scoreboard labels.
- Documented configuration settings with refreshed comments and preserved saved values on startup/reload.
- Reload configuration without duplicating recurring tasks; malformed configuration is rejected without replacing the active settings.
- Regenerate a deleted configuration file with its header and setting descriptions.
- No obsolete upstream update-check notifications or background updater queries.

### API Integration

Connect other server plugins through the retained Jail developer API and Bukkit events.

- Access jail and prisoner managers, jail sticks, handcuffs, and jail votes through `JailsAPI`.
- Query jails, cells, and prisoner state through the provided managers.
- Integrate jailing, transfers, release, and sentence-time behavior through the existing API.
- Listen for supported jailing, jail-stick, transfer, release, death, and timer events.
- Use supported cancellable pre-action events to participate in jail workflows.
- Parse the plugin's supported duration strings through the API.

---

## Commands and Permissions

Notation: `<argument>` is required; `[argument]` is optional. Feature settings and any applicable prisoner restrictions still apply. The in-game `/jail help` menu shows commands available to the sender.

### Player Commands

| Command | Permission | Action / Requirement |
| --- | --- | --- |
| `/jail` or `/jail help [page]` | `jail.command.jailhelp` | Open paginated command help. |
| `/jail help players [page]` | `jail.command.jailhelp` | Open the player-command view. |
| `/jail status` | `jail.usercmd.jailstatus` | View your current jail status. |
| `/jail pay [amount] [player]` | `jail.usercmd.jailpay` | View your own bill, pay an amount, or pay toward another prisoner's sentence. Requires enabled payments and a configured payment method. |
| `/jail stick` | `jail.usercmd.jailstick` | Toggle jail-stick use; using a configured stick also requires its material permission. |
| `/jail vote <player> [yes/no]` | `jail.usercmd.jailvote` | Start or participate in a jail vote. Starting a vote also requires `jail.vote.start`; voting must be enabled and a jail must exist. |

### Admin Commands

| Command | Permission | Action |
| --- | --- | --- |
| `/jail help admin [page]` | `jail.command.jailhelp` plus an applicable admin permission | Open the protected admin help view. |
| `/jail <player> [-t time] [-i] [-j jail] [-c cell] [-a] [-m] [-f] [-r reason]` | `jail.command.jail` | Jail a player; `-i` is indefinite, `-a` selects an available cell, `-m` mutes, and `-f` permits a never-joined target. |
| `/unjail <player>` | `jail.command.unjail` | Release a prisoner normally. |
| `/unjailforce <player>` | `jail.command.unjailforce` | Force-release a prisoner. Offline force-release removes their record without restoring inventory or teleporting them; use normal release for routine administration. |
| `/jail check <player>` | `jail.command.jailcheck` | Inspect a prisoner's current information. |
| `/jail time <add/remove/set/show> <player> [time]` | `jail.command.jailtime` | Change or inspect sentence time; provide a duration when changing it. |
| `/jail mute <player>` | `jail.command.jailmute` | Toggle a prisoner's chat mute. |
| `/jail record <player> [display]` | `jail.command.jailrecord` | Inspect recorded jailing history. |
| `/jail create <name>` | `jail.command.jailcreate` | Start guided jail creation in-game. |
| `/jail createcell <jail> [cellname]` | `jail.command.jailcreatecells` | Start guided cell creation in-game. |
| `/jail stop` | `jail.command.jailstop` | Stop your current jail/cell creation session. |
| `/jail list [jail]` | `jail.command.jaillist` | List jails or inspect a specific jail. |
| `/jail listcells <jail>` | `jail.command.jaillistcells` | List the cells in a jail. |
| `/jail transfer -p <player> [-j jail] [-c cell]` | `jail.command.jailtransfer` | Transfer a prisoner to another jail/cell. |
| `/jail transferall <current> <target>` | `jail.command.jailtransferall` | Transfer every prisoner from one jail to another. |
| `/jail telein <jail> [player]` | `jail.command.jailtelein` | Teleport yourself or a specified player to the jail arrival point. |
| `/jail teleout <jail> [player]` | `jail.command.jailteleout` | Teleport yourself or a specified player to the jail release point. |
| `/jail signs <clean/refresh/update/verify> <jail> [cell...]` | `jail.command.jailsigns` | Manage registered cell signs. |
| `/jail clear [-f] [jail]` | `jail.command.jailclear`; force also needs `jail.command.jailclearforce` | Begin a confirmed prisoner-clearing action for one jail or all jails. |
| `/jail delete <jail>` | `jail.command.jaildelete` | Begin a confirmed jail deletion. |
| `/jail deletecell <jail> <cell>` | `jail.command.jaildeletecell` | Delete a cell, subject to occupancy checks. |
| `/jail deletecells <jail>` | `jail.command.jaildeletecells` | Begin a confirmed action to delete a jail's cells. |
| `/jail confirm` | No separate permission; confirms your authorized pending action | Confirm a supported pending management action. |
| `/handcuff <player>` | `jail.command.handcuff` | Restrict a player's movement at their current location. |
| `/unhandcuff <player>` | `jail.command.handcuff` | Remove handcuffs. |
| `/jail reload` | `jail.command.jailreload` | Reload configuration and refresh plugin managers and displays. |
| `/jail version` | `jail.command.jailversion` | Show plugin version information. |
| `/togglejaildebug` | `jail.command.toggledebug` | Toggle diagnostic logging. |

### Additional Permissions

| Permission | Purpose |
| --- | --- |
| `jail.*` | Broad administrator wildcard; includes management and immunity permissions. Review before assigning. |
| `jail.command.*` | Administrative command permission group. |
| `jail.usercmd.*` | Player command permission group. |
| `jail.modifyjail` | Allow permitted jail-region modifications. |
| `jail.cantbejailed` | Protect an online player from ordinary command jailing and being selected as a jail-vote target. |
| `jail.usejailstick.<material>` | Use a configured jail-stick material, for example `jail.usejailstick.stick`. |
| `jail.canbestickjailed` | Allow a player to be jailed by jail sticks. |
| `jail.vote.start` | Start a jail vote. |
| `jail.see.broadcast` | Receive configured jailing broadcasts. |

**Property chests:** Registered property chests have their own assigned-prisoner checks. Operator status or a general chest-access permission does not bypass these protections.

Permission defaults and complete command declarations are listed in the plugin's `plugin.yml`.

---

## Installation

1. Use a supported Spigot or Paper server and its required Java runtime.
2. Stop the server and place `JailReloaded-1.0.0.jar` in the server's `plugins` folder. Install only one Jail/Jail-Reloaded implementation.
3. Start the server to generate `plugins/JailReloaded/config.yml`, locale files, and default SQLite storage.
4. Review the configuration, especially movement protection, inventory storage, uniforms, interaction rules, sentence payments, and permissions.
5. Use `/jail create <name>` and follow the selection-wand instructions. Add cells with `/jail createcell <jail> [cellname]` when needed.
6. Configure optional integrations, run `/jail reload` after supported configuration changes, and test jailing/release with spare items before opening the system to players.

**Upgrading or migrating:** Back up the full plugin folder, prisoner/property data, and any external database before updating or changing storage engines. Preserve existing settings deliberately; new default values do not overwrite an existing configuration. Do not assume an older upstream `plugins/Jail` folder is automatically the same as this fork's `plugins/JailReloaded` folder.

**Java:** The plugin uses Java 17 bytecode. Newer Minecraft servers can require a newer Java runtime; follow your server's requirements. Folia is not supported.

### Configuration Highlights

| Setting | Purpose |
| --- | --- |
| `storage.type` | Select `sqlite`, `mysql`, or `flatfile`. |
| `system.language` | Choose a locale filename without its `.yml` extension. |
| `jailing.during.moveProtection` | Enable secure detention, or disable it for escape gameplay and removable uniforms/property reclamation. |
| `jailing.during.openChest` | Allow the assigned prisoner to access their own protected cell chest. |
| `jailing.jail.inventory.store` | Enable inventory confiscation and restoration. |
| `jailing.jail.clothing` | Configure the issued inmate uniform. |
| `jailing.during.commandWhitelist` | Choose which commands prisoners may use. |
| `jailing.during.preventInteractionBlocks` / `preventInteractionItems` | Choose blocked interactions; quoted `'*'` covers every material. |
| `jailing.during.countDownTimeWhileOffline` | Control whether established sentences count down offline. |
| `jailpay` | Enable payments and configure item/money mode and prices. |
| `jailstick` | Configure hit-to-jail tools and target health thresholds. |
| `jailvote` | Configure jail voting, vote duration, minimum yes votes, and resulting sentence. |

---

## Dependencies

**The core jail system requires no additional plugins.**

| Dependency / Service | Requirement | Link |
| --- | --- | --- |
| **Vault** | Optional; required for money-based sentence payments. Not required for physical item payments. | [Vault](https://www.spigotmc.org/resources/vault.34315/) |
| **Vault-compatible economy provider** | Required alongside Vault when using money payments. | Use an economy plugin compatible with your server and Vault. |
| **MySQL/MariaDB database** | Optional; required only when selecting `storage.type: mysql`. SQLite and YAML flatfile do not require an external database. | [MySQL](https://www.mysql.com/) · [MariaDB](https://mariadb.org/) |

WorldEdit and Multiverse-Core remain declared as optional soft dependencies; neither is required for the guided jail/cell creation workflow. A client resource pack is not required.

---

## Support

- **Discord:** [ImagineCraft Plugins support server](https://discord.gg/kggbGwRt42)

For support, include your Jail-Reloaded version, server software/version, Java version, relevant configuration, and a clear description of the problem. Remove database passwords and other credentials before sharing files or logs.

### QQ: 中文支持 · QQ 交流群

无法访问 Discord，或需要中文帮助？欢迎加入 ImagineCraft 插件 QQ 交流群，咨询插件使用、配置及问题反馈。

QQ群号：**101849876** · 群内联系人：**AFK·落子无悔**

---

## Original Author and Project Credits

### Originally By: **graywolf336**

Jail-Reloaded continues the original **Jail** project created and maintained by **graywolf336**, together with its original contributors. The original plugin, its design, and its existing functionality remain credited to that project. ImagineCraft Plugins maintains this fork for modern server compatibility, fixes, and user-requested updates.

- **Original CurseForge page:** [Jail by graywolf336](https://www.curseforge.com/minecraft/bukkit-plugins/jail)
- **Original GitHub repository:** [graywolf336/Jail](https://github.com/graywolf336/Jail)
- **New maintained fork:** [Rismr1/Jail-Reloaded](https://github.com/Rismr1/Jail-Reloaded)

**Fork maintained by:** Rismr1 | ImagineCraft Plugins.

**Original contributors:** Thank you to everyone who contributed code, translations, testing, documentation, and support to the original Jail project. See the [original repository's contributors](https://github.com/graywolf336/Jail/graphs/contributors) for its project history.

**License and attribution:** The fork retains the original repository's GNU GPL v2 `LICENSE` and includes an attribution `NOTICE`. See the [fork repository](https://github.com/Rismr1/Jail-Reloaded) for the license and notice distributed with Jail-Reloaded. This fork is free, will never be sold, and preserves credit to the original project.

Want to contribute? Join the [Discord support server](https://discord.gg/kggbGwRt42) and post in **Plugin Discussion**. Bug reports, translation contributions, and feature suggestions are welcome.

---

## Locale configuration and customization

Set `system.language` in `config.yml`, then run `/jail reload`. All 17 UTF-8 locale files are installed under `plugins/JailReloaded/locales/`: English (`en`), Finnish (`fi`), German (`de`), Spanish (`es`), French (`fr`), Italian (`it`), Japanese (`ja`), Korean (`ko`), Dutch (`nl`), Polish (`pl`), Brazilian Portuguese (`pt_br`), Russian (`ru`), Swedish (`sv`), Ukrainian (`uk`), Simplified Chinese (`zh_cn`), Hong Kong Traditional Chinese (`zh_hk`), and Taiwan Traditional Chinese (`zh_tw`).

The selected server language covers messages, creation instructions, wand text, help and pagination, handcuffs, sign defaults, payment/vote feedback, and scoreboard defaults. The translations use complete sentences and regional terminology. Existing prisoner names, reasons, saved records, and administrator-written configuration text are preserved. Diagnostic console logs and configuration comments remain in English.

`/jail` always remains `/jail`. The selected locale supplies subcommand aliases and translated argument labels, including tab completion and help links. English subcommands remain usable in every language. For example, with `de`, `/jail hilfe spieler 2` and `/jail help players 2` both open the second player-help page. Other roots such as `/unjail` stay unchanged. Flags such as `-p`, `-t`, and `-r`, duration units (`s`, `m`, `h`, `d`), permissions, player names, jail names, and cell names keep their existing syntax. Translated prisoner commands follow the same command whitelist as English commands.

Customize wording in the selected locale file. Keep `%0%`, `%1%`, and other placeholders intact; their order may change to suit the sentence. Empty message values intentionally hide a message. Missing entries are added from that language’s bundled defaults without replacing custom values; malformed entries fall back individually. Malformed YAML is preserved and uses bundled wording until repaired. Unknown locale codes use an existing custom file when available, with English defaults otherwise. Locale files must use UTF-8.

Edit `language.syntax.words` for single-word subcommand aliases and `language.syntax.arguments` for displayed argument labels. Use distinct aliases; English names take precedence over conflicting custom aliases. `@locale` uses the selected language for scoreboard headings, the time label, and the vote reason; jail-stick reasons accept `@locale:jailstick.running` and `@locale:jailstick.fun`; explicit custom text remains supported. Cell sign lines can use `@locale:sign.reason`. The old default English labels also follow the locale automatically. Existing nondefault custom wording stays unchanged.

## Developing/Building

Build the plugin jar with:

```shell
mvn clean verify
```

The build runs the unit and regression tests and produces `target/JailReloaded-1.0.0.jar`. Replace the old plugin JAR in the server's `plugins` directory and restart the server. Keep existing configuration and data in `plugins/JailReloaded`.

Keep the project folder clean: store temporary verification logs, downloaded servers, test worlds, and backups outside the checkout. After delivering a build, keep only the current release JAR in `target`; clear generated build intermediates and older artifacts.

The tests use Mockito and public Bukkit interfaces instead of versioned CraftBukkit internals. Benchmarks remain available separately with `mvn test -Dtest=BenchmarkTest`.

## Compatibility verification

`python scripts/check_apis.py` compiles production sources against every published stable Spigot API in the target range. It requires JDK 25 to read the newer APIs, while generating Java 17 bytecode. The normal release build always compiles against 1.19.

`python scripts/check_servers.py --accept-eula` builds the release and a separate test-only plugin, downloads checksum-verified Paper servers, and runs them in isolated directories under the OS temporary directory (`JailReloaded-verification`). Review the [Minecraft EULA](https://aka.ms/MinecraftEULA) before supplying that flag. Set `JAVA17_HOME`, `JAVA21_HOME`, and `JAVA25_HOME` if the JDKs are outside their default Windows installation directories. Pass `--versions 1.19 26.3` to choose a smaller matrix.

The server probe exercises enablement, commands and completion, jail/cell data, signs, inmate clothing, scoreboards, inventory and book metadata restoration, offhand items, handcuffs, escape/block/command/item protections, chat muting, payment calculations, transfers, releases, sentence timers, reloads, flatfile storage, and SQLite migration. It uses real server APIs, worlds, inventories and schedulers, with a simulated player interface. Networked clients and other plugins' event overrides need separate multiplayer acceptance testing.

Reports, downloaded test servers, worlds, and verification logs stay outside the project in the OS temporary directory under `JailReloaded-verification` (Windows: `%TEMP%\JailReloaded-verification`). Set `JAIL_VERIFY_DIR` to override this location. Unit-test fixtures also use the OS temporary directory. See [COMPATIBILITY.md](COMPATIBILITY.md) for the recorded results and limits.

For optional integration checks, supply `--vault-jar path/to/Vault.jar` to load the real Vault bridge with a test economy service. Supply `--mysql-port PORT` for a disposable local MySQL/MariaDB instance containing a `jail_probe` database; set `JAIL_PROBE_MYSQL_USER` (defaults to `root`) and `JAIL_PROBE_MYSQL_PASSWORD` in the environment. The probe creates uniquely prefixed tables. Use `--report-file` with a path outside the project to keep optional integration results separate. These checks cover provider binding, balance checks, withdrawals, MySQL storage migration and exact sentence-time persistence.
