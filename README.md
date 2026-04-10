# Threaded

`Threaded` es un juego cooperativo 2D hecho con Java 21, JavaFX y Maven. Cada partida usa un modelo multijugador host autoritativo sobre UDP: una instancia actúa como host, simula toda la partida y distribuye snapshots; las demás instancias funcionan como clientes ligeros que envían input y renderizan el estado sincronizado.

El proyecto no busca ser un motor nuevo ni una reescritura total del género. El objetivo actual del repositorio es ser un juego académico/prototipo serio: jugable, defendible técnicamente, con arquitectura limpia para su escala, documentación útil y una base razonablemente mantenible.

Repositorio: https://github.com/Yogwito/Threaded

## Estado actual

- Lenguaje y runtime: Java 21
- UI: JavaFX
- Build: Maven
- Red: UDP con modelo host autoritativo
- Niveles actuales: 8 (`level1.txt` a `level8.txt`)
- Jugadores: 2 a 4
- Arquitectura: capas separadas entre presentación, aplicación, dominio e infraestructura
- Estado del multiplayer: funcional en red local, con snapshots y lobby operativo
- Estado visual: HUD, lobby y render de gameplay ya pasaron por una fase de pulido importante
- Estado de testing: existe suite automatizada útil para sesión, protocolo, snapshots y coordinadores; no hay testing visual JavaFX ni integración real multiproceso completa

La campaña actual se detecta dinámicamente desde `src/main/resources/com/dino/levels` usando `LevelLoader.countAvailableLevels()`.

## Requisitos

- JDK 21
- Maven 3.9 o superior

## Compilar y ejecutar

### Compilar

```bash
mvn compile
```

### Ejecutar tests

```bash
mvn test
```

### Arrancar la aplicación

```bash
mvn javafx:run
```

## Flujo de uso

1. Inicia una instancia y elige `Crear sala` para actuar como host.
2. Configura IP local, puerto local y cantidad esperada de jugadores.
3. Inicia una o más instancias cliente con `Unirse`, usando la IP y puerto del host.
4. Cada jugador entra al lobby, confirma nombre/color y marca `READY`.
5. El host inicia la partida.
6. Durante el gameplay, el mouse define el objetivo de movimiento y el clic izquierdo ejecuta el salto.
7. La campaña termina cuando se completan todos los niveles y se muestra la pantalla final con ranking.

## Cómo jugar

### Controles

- Moverse: apuntar con el mouse
- Saltar: clic izquierdo

### Objetivo general

Avanzar en equipo por una campaña de plataformas, controlar la tensión del hilo, evitar hazards, empujar cajas cuando haga falta, recoger monedas y llegar juntos a la salida de cada nivel.

## Reglas del juego

1. El host es la única autoridad física: clientes no resuelven colisiones, score ni avance de nivel.
2. Los jugadores quedan unidos por un hilo virtual en cadena según el orden de unión al lobby.
3. Si la separación entre vecinos crece demasiado, el hilo aplica tensión y puede corregir movimiento.
4. El hilo no debe atravesar sólidos: plataformas, puertas cerradas y bloques empujables bloquean correcciones directas.
5. Si un jugador cae al vacío o toca un hazard, el host reinicia la sala actual para todos los jugadores conectados.
6. Un nivel solo termina cuando todos los jugadores conectados y vivos están dentro de la salida.
7. Los jugadores pueden apilarse: si uno está apoyado sobre otro, el de arriba cuenta como `grounded` y puede saltar.
8. Las cajas visibles de la campaña son empujables y forman parte real del recorrido.

## Sistema de puntaje

El score se calcula exclusivamente en el host y se replica por snapshot.

- Moneda pequeña: `+10`
- Moneda grande: `+25`
- Primer jugador en llegar a la salida: `+100`
- Segundo jugador en llegar a la salida: `+70`
- Jugadores restantes que llegan después: `+50`
- Caer al vacío o tocar hazard: `-15`

El HUD muestra ranking en vivo por nombre y puntaje. La pantalla final ordena a los jugadores por score acumulado y declara un ganador.

## Niveles y progresión

La campaña actual contiene 8 niveles rediseñados para tener una progresión más clara:

- Nivel 1: tutorial de movimiento y lectura de ruta
- Nivel 2: cooperación básica y saltos en pareja
- Nivel 3: introducción al uso visible de cajas
- Nivel 4: coordinación con múltiples cajas
- Nivel 5: ascenso con castigo moderado
- Nivel 6: zigzag con más tensión de cuerda
- Nivel 7: tramo avanzado con plataformas especiales
- Nivel 8: cierre de campaña combinando cooperación, tensión y cajas

Los niveles se cargan desde matrices numéricas en archivos `.txt`. La metadata soportada hoy es:

```text
name=Nivel 1 - Sendero Guiado
background=forest
tileSize=64
```

Después de la metadata, cada fila representa tiles separados por comas. La traducción de códigos vive en `TileType`.

## Arquitectura

El proyecto está separado por capas para evitar que UI, red, sesión y reglas queden mezcladas.

### Bootstrap y runtime

- `com.dino.MainApp`: punto de entrada JavaFX
- `application.runtime.AppRuntimeManager`: ciclo de vida global del runtime
- `application.runtime.AppContext`: composition root de la instancia

### Aplicación

- `application.usecases.CreateSessionUseCase` y `JoinSessionUseCase`: creación y unión de sesiones
- `application.services.SessionService`: store compartido de sesión
- `application.services.SessionLifecycleService`: transición macro entre lobby, gameplay y resultados
- `application.services.SessionTransitionPolicy` + `SessionStateMachine`: regla y máquina de estados explícitas
- `application.services.HostMatchService`: coordinador del tick autoritativo del host
- `application.services.LobbySessionCoordinator`: protocolo UDP previo a la partida
- `application.services.GameplaySessionCoordinator`: polling de gameplay, snapshots y mensajes críticos

### Simulación y reglas

- `application.services.PlayerPhysicsService`: física base de jugadores
- `application.services.PlayerContactService`: apilamiento y contactos entre jugadores
- `application.services.PushBlockPhysicsService`: física de bloques empujables
- `application.services.ThreadConstraintService`: tensión y correcciones del hilo
- `application.services.LevelFlowService`: score, hazards, resets, salida y avance de nivel
- `domain.rules.GameRules`: reglas puras y cálculos sin efectos secundarios

### Niveles

- `application.levels.LevelCatalog` + `ResourceLevelCatalog`: acceso abstracto y fuente actual de niveles
- `application.levels.LevelLoader`: parser del formato textual de niveles
- `application.levels.LevelData`: estructura parseada del nivel

### Presentación

- `presentation.flow.*`: fachadas por pantalla para no acoplar controladores al runtime completo
- `presentation.controllers.*`: controladores JavaFX delgados
- `presentation.components.*`: scoreboard y event log observables
- `presentation.render.GameRenderer` + `GameRenderState`: render de gameplay desacoplado
- `presentation.render.LobbyPreviewRenderer`: preview visual del lobby

### Infraestructura

- `infrastructure.network.NetworkPeer` + `UdpPeer`: transporte UDP
- `infrastructure.serialization.MessageCodec` + `MessageSerializer`: serialización JSON actual
- `infrastructure.serialization.ProtocolMessageValidator`: validación estructural mínima del protocolo
- `infrastructure.audio.SoundManager` + `ProceduralAudioEngine`: audio procedural orientado por eventos

## Red y sincronización

La red del proyecto usa exclusivamente UDP. No hay TCP.

### Modelo real implementado

- El host simula física, score, hazards, cajas, salida, tiempo y avance de campaña.
- Los clientes envían input y renderizan.
- El host difunde snapshots completos del estado visible.
- Los clientes aplican snapshots autoritativos y no re-simulan la lógica principal.

### Qué manda el cliente

- `JOIN`
- `READY`
- `MOVE_TARGET`
- `JUMP`
- `DISCONNECT`

### Qué decide el host

- Movimiento final y colisiones
- Tensión del hilo
- Estado de cajas
- Recogida de monedas
- Penalizaciones por caída/hazard
- Score
- Reinicios de sala
- Avance de nivel
- Final de campaña

### Snapshots

- La frecuencia sale de `GameConfig.SNAPSHOT_RATE_HZ`
- Esa configuración es la fuente de verdad del runtime
- Los snapshots incluyen `seq`
- Los clientes ignoran snapshots fuera de orden
- El pipeline vive en `SessionSnapshotService`, `SessionSnapshotBuilder` y `SessionSnapshotApplier`

### Confiabilidad real de UDP

El proyecto no implementa una capa confiable completa con ACK general, retransmisión arbitraria o reconexión transparente.

La estrategia actual es deliberadamente mínima:

- snapshots e input normal viajan como best-effort;
- `START_GAME` y `GAME_OVER` se refuerzan con ráfaga corta usando `UdpPeer.broadcastBurst(...)`;
- esto mejora transiciones críticas sin cambiar el modelo base de red.

## Patrones de diseño reales

### Observer

El patrón aparece de forma explícita en el bus de eventos interno:

- `EventBus` publica eventos del juego;
- `ScoreBoardObserver` construye el ranking visible;
- `EventLogObserver` mantiene la bitácora de eventos;
- `SoundManager` reacciona a esos mismos eventos para generar audio.

Esto desacopla gameplay, HUD y feedback audiovisual.

### Adapter

El patrón aparece de forma clara en `UdpPeer`.

`UdpPeer` adapta `DatagramSocket` a una API pequeña y estable para:

- `bind`
- `send`
- `broadcast`
- `broadcastBurst`
- `receive`

Así, el resto del proyecto no depende directamente de la API baja de networking de Java.

## SOLID

La aplicación de SOLID es fuerte para el tamaño del proyecto, pero no perfecta.

### Lo mejor aplicado

- `S`: controladores JavaFX, coordinadores de red, renderers y slices de sesión ya no mezclan tantas responsabilidades
- `I`: existen contratos pequeños y útiles como `SceneNavigation`, `NetworkPeer`, `MessageCodec`, `EventChannel` y los `*FlowAware`
- `D`: la composición vive en `AppContext`; la UI depende de fachadas por pantalla en vez del runtime completo

### Lo aplicado de forma parcial

- `O`: extender render, niveles o coordinadores es razonable; extender el núcleo de sesión sigue costando más de lo ideal
- `L`: las abstracciones introducidas sí son sustituibles, pero el proyecto todavía no vive enteramente sobre interfaces

### Deuda técnica real

- `SessionService` sigue siendo un agregado mutable importante
- la arquitectura no pretende ser hexagonal pura ni DI completa
- hay partes del protocolo aún apoyadas en `Map<String, Object>`

## Mejoras recientes relevantes

- refactor arquitectónico para separar runtime, flujos de pantalla, coordinadores y slices de estado;
- state machine explícita de sesión;
- pipeline de snapshots extraído del store principal;
- lobby visual con preview de jugadores conectados;
- mejora fuerte del render del gameplay, HUD y pantalla final;
- pulido visual de cuerda, jugadores, cajas, monedas, hazards y meta;
- rediseño de los 8 niveles actuales para mejorar progresión y uso de cooperación/cajas;
- mejoras de jugabilidad en aim, cadencia de snapshots y reinicio visual limpio;
- suite de tests útil para sesión, protocolo, snapshots y coordinadores;
- documentación técnica y JavaDoc reforzadas en servicios, protocolo y contratos de UI.

## Tests automatizados

La suite actual cubre principalmente lógica de alto valor:

- transición de sesión y política de estados;
- coordinadores de lobby y gameplay;
- validación estructural del protocolo;
- snapshots y secuencias;
- registro de peers;
- algunos flujos de pantalla desacoplados de JavaFX real.

No hay tests visuales de JavaFX ni integración multiproceso completa con sockets reales como gate principal.

## Limitaciones actuales

- No existe una capa confiable completa sobre UDP.
- La validación más importante de host + cliente sigue siendo manual.
- `SessionService` está más limpio que al inicio, pero sigue siendo stateful y relativamente grande.
- El lobby no fuerza todavía una política “dura” de readiness a nivel de protocolo para no cambiar comportamiento observable.
- `mvn javafx:run` en JDK 21 puede mostrar warnings conocidos de JavaFX sobre `native access` y `sun.misc.Unsafe`; no corresponden a una excepción propia del juego.

## Notas de ejecución y troubleshooting

- Si un cliente no entra al lobby, revisa primero IP/puerto del host y asegúrate de que ambas instancias estén en la misma red local.
- Si el host cambia de sala o vuelve al menú, conviene cerrar y recrear clientes para una demo limpia.
- El juego fue probado sobre todo como demo manual local; antes de una entrega conviene hacer una validación manual host + cliente en dos instancias reales.
