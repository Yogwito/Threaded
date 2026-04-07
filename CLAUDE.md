# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
mvn javafx:run     # Run the application
mvn compile        # Compile only
mvn clean compile  # Clean build then compile
mvn package        # Package as JAR
```

Requirements: JDK 21, Maven 3.9+. No automated test suite exists.

## Architecture

**Threaded** is a cooperative multiplayer platformer where up to 4 players are bound by an invisible "thread" (elastic constraint) that limits how far apart they can spread. One instance acts as the authoritative host; others are clients. Built with JavaFX 21 and UDP networking (Jackson for JSON serialization).

### Layers

- `com.dino.MainApp` — JavaFX bootstrap, all runtime singletons as static fields (`eventBus`, `sessionService`, `udpPeer`, `hostMatchService`, `soundManager`). Reset on menu return via `resetRuntimeState()`.
- `application.services.SessionService` — central state repository (players, platforms, coins, doors, etc.). All mutating methods are `synchronized`.
- `application.services.HostMatchService` — authoritative simulation tick: movement, gravity, AABB collision, thread elasticity, scoring, win/death detection. Broadcasts snapshots at 30 Hz.
- `application.levels.LevelLoader` — parses `level1.txt`–`level5.txt` from resources; metadata lines precede the tile matrix.
- `application.usecases.*` — `CreateSessionUseCase` (host) and `JoinSessionUseCase` (client) for session bootstrap.
- `domain/` — pure state holders (`Player`, `PlatformTile`, `Door`, `ButtonSwitch`, `PushBlock`, etc.) and stateless rules (`GameRules` for AABB checks).
- `infrastructure.network.UdpPeer` — UDP send/receive transport.
- `infrastructure.serialization.MessageSerializer` — factory for typed JSON messages (`JOIN`, `SNAPSHOT`, `MOVE_TARGET`, `JUMP`, `READY`, `START_GAME`, `GAME_OVER`). Critical messages are repeated 3× with delay.
- `presentation.controllers.*` — `StartMenuController`, `LobbyController`, `GameController`, `GameOverController`.
- `presentation.components.*` — `ScoreBoardObserver`, `EventLogObserver` subscribe to `EventBus`.
- `presentation.render.*` — Canvas rendering.
- `config.GameConfig` — single source of truth for all constants (physics, networking, scoring, thread mechanics, camera). Never use magic numbers; add them here.

### Network Protocol

Clients are non-authoritative: they send mouse position (`MOVE_TARGET`) and jump input (`JUMP`), receive full-state snapshots from host. Snapshot sequence numbers prevent out-of-order reversion. Peer timeout at 2.4 s (host drops clients that stop sending heartbeats).

### The "Thread" Mechanic

`HostMatchService.applyThreadElasticity()` applies force pulling players toward each other when distance exceeds `GameConfig.THREAD_MAX_DISTANCE` (300 units). Governed by 8 `THREAD_*` constants in `GameConfig`. This is a game mechanic, not Java threading.

### Level Format

```text
name=Nivel 1 - Ruta Basica
background=forest
tileSize=64
[rows of comma-separated tile IDs]
```

Tile ID → type mapping lives in `TileType`. Levels in `src/main/resources/com/dino/levels/`.

### Scoring

- Coin: 10 pts (small), 25 pts (large)
- Button press: 25 pts (once per level)
- Exit order bonus: 100 / 70 / 50 pts
- Fall death: −15 pts
- Room resets on any player death (cooperative consequence)
