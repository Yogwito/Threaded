# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
mvn compile          # compilar
mvn test             # ejecutar todos los tests
mvn -q compile       # compilar silencioso (útil para verificar sin ruido)
mvn javafx:run       # lanzar la aplicación
./scripts/check_javadoc_coverage.sh src/main/java   # validar cobertura de Javadoc
```

Para un test individual:
```bash
mvn test -Dtest=NombreDeClaseTest
mvn test -Dtest=NombreDeClaseTest#nombreDelMetodo
```

`mvn javafx:run` puede emitir warnings sobre `native access` y `sun.misc.Unsafe` en JDK 21 — son conocidos e inofensivos.

## Arquitectura

El proyecto es un juego cooperativo 2D multijugador sobre UDP, Java 21 + JavaFX. Hay cuatro capas estrictas:

```
presentation/   UI JavaFX (controladores, renders, flows por pantalla)
application/    coordinadores, casos de uso, servicios de simulación
domain/         entidades puras y reglas sin efectos secundarios
infrastructure/ red UDP, serialización JSON (Jackson), audio procedural
```

### Composition root

`AppContext` (`application/runtime/AppContext.java`) es el único lugar donde se construyen y cablea todas las dependencias. No hay inyección de frameworks. Cuando el usuario vuelve al menú, `AppRuntimeManager` recrea `AppContext` desde cero para limpiar el estado de sesión.

### Flujo de escenas

`SceneNavigator` carga los FXML e inyecta dependencias en los controladores a través de interfaces `*FlowAware` y `*ContextAware`. Los controladores no importan `AppContext` directamente: reciben una *fachada de pantalla* (`StartMenuFlow`, `LobbyScreenFlow`, `GameScreenFlow`, `GameOverScreenFlow`) que encapsula lo que necesita cada vista.

`SceneLifecycleAware` (`onSceneShown` / `onSceneHidden`) es el mecanismo para arrancar y liberar timers y suscripciones al evento bus al entrar y salir de cada pantalla.

### Modelo host autoritativo

- El host simula física, colisiones, hilo, score, avance de nivel y cajas en cada tick.
- Los clientes envían `MOVE_TARGET` y `JUMP`; reciben snapshots y los aplican via `SessionSnapshotApplier`.
- `HostMatchService` → `GameplaySessionCoordinator` → `SessionSnapshotBuilder/Applier` es la cadena del loop autoritativo.
- Los mensajes críticos (`START_GAME`, `GAME_OVER`) usan `broadcastBurst` para compensar UDP no confiable.

### Render

`GameRenderer` dibuja sobre un `Canvas` JavaFX usando `GraphicsContext`. El estado de un frame se encapsula en `GameRenderState` (record inmutable) construido por `GameRenderStateFactory`. El renderer aplica un transform de escala uniforme (`gc.save/translate/scale/restore`) para que el viewport 980×620 unidades-mundo llene el canvas dinámicamente con letterbox si el aspect ratio difiere. `GameRenderer` expone `getRenderOffsetX/Y/Scale()` para que `GameController` traduzca coordenadas de mouse a mundo de forma consistente.

### Bus de eventos

`EventBus` es el canal interno de notificaciones desacopladas. Los observers `ScoreBoardObserver` y `EventLogObserver` escuchan gameplay events para proyectar ranking y bitácora. `SoundManager` también suscribe al mismo bus para audio procedural reactivo. El contrato de nombres de eventos está en `domain/events/EventNames.java`.

### Niveles

Los niveles son matrices numéricas en `src/main/resources/com/dino/levels/levelN.txt`. Formato:
```
name=Nombre del nivel
background=forest|desert|cave|snow
tileSize=64

0,1,2,3,...   ← filas de tiles
```
Códigos de tiles en `TileType`. El número de niveles disponibles se detecta en runtime con `LevelLoader.countAvailableLevels()` — no hay constante hardcodeada.

### Configuración central

`GameConfig.java` contiene **todas** las constantes del juego: dimensiones de ventana, viewport (`VIEWPORT_W=980, VIEWPORT_H=620`), mundo (`LEVEL_WIDTH=1800, LEVEL_HEIGHT=900`), física, red y score. Cambiar balance o parámetros de red se hace ahí.

## Convenciones importantes

- Todo método público no trivial lleva Javadoc. El script `check_javadoc_coverage.sh` lo verifica.
- Los controladores JavaFX son delgados: delegan lógica a la fachada de pantalla (`*Flow`), nunca al runtime completo.
- Las suscripciones al bus se gestionan con `SubscriptionGroup` para cancelarlas juntas en `onSceneHidden`.
- El canvas del juego es dinámico: su tamaño lo controla un listener en `GameController.initialize()` enlazado a `arenaPane`. No usar `width`/`height` fijos en el FXML del canvas.
- No hay TCP; toda la red es UDP. No añadir dependencias de red adicionales sin discutirlo.
