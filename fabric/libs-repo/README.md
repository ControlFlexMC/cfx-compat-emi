# libs-repo

A tiny local Maven repository holding the two mod artifacts this branch compiles against:

| Coordinate | Jar |
|---|---|
| `cfx.local:fabric-api-base:1.21.1` | extracted from the Fabric API bundle |
| `cfx.local:fabric-key-binding-api-v1:1.21.1` | extracted from the Fabric API bundle |
| `cfx.local:fabric-lifecycle-events-v1:1.21.1` | extracted from the Fabric API bundle |
| `cfx.local:emi-fabric:1.21.1` | `emi-1.1.24+1.21.1+fabric.jar` |

## Why

`maven.fabricmc.net` and `repo.sleeping.town` stall for minutes (or time out) from some networks,
which makes `./gradlew build` unusable there. Both artifacts are ordinary jars, so they are
served from disk instead.

The coordinates are deliberately *not* the upstream ones (`cfx.local:*` instead of
`net.fabricmc.fabric-api:*` / `dev.emi:*`): Gradle caches module metadata per coordinate, and
reusing upstream coordinates makes it fall back to the remote repository even when the jar is
sitting right here.

The jars themselves are git-ignored (binary, a few MB). Populate them with:

```bash
tools/fetch-deps.sh
```

It copies from a local mods folder when one is given, otherwise downloads from upstream.
