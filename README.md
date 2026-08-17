# online-players

A Velocity plugin that records network-wide and per-server online player counts to a JSON file
every time they change.

## Why

Anything that wants to know "how many players are online right now" - a status page, a
monitoring script, a Discord bot - would otherwise have to poll the proxy via RCON. This plugin
writes the current counts to a file on every change instead, so any process on the same host can
just read it.

## How it works

Recomputes counts from Velocity's live state (not incremented/decremented) on every
`ServerConnectedEvent` (`com.velocitypowered.api.event.player.ServerConnectedEvent`) /
`DisconnectEvent` (`com.velocitypowered.api.event.connection.DisconnectEvent`), and once
immediately on startup so the file always reflects reality, not just the next connect/disconnect.
Writes are skipped when the counts didn't actually change, so the file's mtime only moves when the
numbers do - except for a heartbeat every 45 seconds, which rewrites the file unconditionally so
`updated` keeps advancing during quiet periods with no joins/leaves. Writes `online-players.json`
under its own plugin data directory
(`plugins/online-players/`, relative to wherever Velocity runs):

```json
{
  "total": 8,
  "servers": { "lobby": 5, "survival": 3 },
  "updated": "2026-07-25T18:49:52.189653072Z"
}
```

## Requirements

- JDK 25 to build (Gradle toolchain-managed)
- Velocity 4.x

## Building

```bash
./gradlew build
```

## Releases

A merged PR labeled `release:major`, `release:minor`, or `release:patch` triggers
`semantic-release` on merge to `main`, which tags the resulting commit `vX.Y.Z`. That tag push
triggers the `Release` workflow, which builds the jar and attaches it to a GitHub Release.
`release:none` skips this entirely - use it for docs/CI-only changes.

## Testing a release build

Tagging `main` with `vX.Y.Z` (or running the `Release` workflow manually with a `tag` input)
builds the jar and attaches it to a GitHub Release. Download and drop it into a Velocity
server's `plugins/` directory to test:

```bash
gh release download vX.Y.Z -R miikkak/online-players -p '*.jar' -D /path/to/velocity/plugins/
```

There is no automated deploy yet - this is manual, on-demand testing only.

## Design notes

- Dependency versions are pinned in `gradle.lockfile` (`dependencyLocking` in `build.gradle.kts`)
  so CI vulnerability scanning has a real dependency graph to check. Gradle fails the build if a
  declared dependency's resolved version drifts from the lock - after bumping a version in
  `build.gradle.kts`, regenerate it with `./gradlew dependencies --write-locks` and commit the
  result alongside the change.
- `gson` is deliberately pinned to `2.8.0` and not shaded - it's _meant_ to be the exact version
  Velocity bundles at runtime, but the resolved `compileClasspath` actually locks to `2.14.0`
  (`velocity-api:4.0.0` transitively requires it, and wins ordinary conflict resolution over our
  declared `2.8.0`) - see [#50](https://github.com/miikkak/online-players/issues/50). Bumping the
  pin requires re-verifying against whatever Velocity actually ships, not an automated dependency
  update (see `renovate.json`, which excludes it from Renovate for this reason).
- The plugin's reported version (shown in Velocity's "Loaded plugin ..." log line) is generated
  from the Gradle project version at build time, so it can't drift from the jar filename.

## License

TBD
