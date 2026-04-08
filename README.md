# Threaded

Juego cooperativo distribuido hecho con JavaFX y sockets UDP. Una instancia actúa como host autoritativo, simula la partida completa y distribuye snapshots del estado; las demás instancias funcionan como clientes ligeros que envían input y renderizan el mundo sincronizado.

Repositorio: https://github.com/Yogwito/Threaded

## Descripción del juego

`Threaded` es un juego cooperativo de plataformas para 2 a 4 jugadores. Los personajes avanzan por una campaña de niveles conectados por un hilo virtual: si se separan demasiado, la cuerda aplica tensión y puede frenar o corregir el movimiento. El objetivo es coordinarse para evitar hazards, recoger objetos y llegar juntos a la meta.

La campaña actual se carga desde archivos de texto ubicados en `src/main/resources/com/dino/levels`. En el estado actual del repositorio hay 8 niveles (`level1.txt` a `level8.txt`) y el host los detecta dinámicamente con `LevelLoader.countAvailableLevels()`.

## Requisitos

- JDK 21
- Maven 3.9 o superior

## Ejecutar

```bash
mvn javafx:run
```

## Compilar

```bash
mvn compile
```

## Flujo básico de uso

1. Inicia una instancia como host y define IP, puerto local y cantidad esperada de jugadores.
2. Inicia una o más instancias cliente y usa la IP/puerto del host.
3. En el lobby, cada jugador marca `Listo`.
4. El host inicia la partida.
5. Durante la partida, el movimiento se controla con el mouse y el salto con clic izquierdo.

## Reglas del juego

1. Todos los jugadores se controlan con mouse: la posición del cursor define la dirección horizontal y el clic izquierdo ejecuta el salto.
2. Los jugadores están unidos por un hilo en cadena fija según el orden de unión a la sala. El hilo tiene slack hasta `THREAD_REST_DISTANCE`, tensión suave hasta `THREAD_MAX_DISTANCE` y un límite absoluto `THREAD_HARD_LIMIT`.
3. El hilo no debe atravesar sólidos: paredes, plataformas sólidas, puertas cerradas y bloques empujables bloquean correcciones posicionales directas.
4. Si un jugador cae al vacío o toca un hazard, el host reinicia la sala actual para todos los jugadores conectados.
5. El host mantiene la física autoritativa: los clientes no deciden puntaje, colisiones ni resolución final del movimiento.
6. Un nivel solo se completa cuando todos los jugadores conectados y vivos están dentro de la salida.
7. Los jugadores pueden apilarse. Si un jugador está apoyado encima de otro, el de arriba cuenta como `grounded` y puede saltar.

## Sistema de puntaje

El puntaje se calcula del lado host y se replica por snapshot:

- moneda pequeña: `+10`
- moneda grande: `+25`
- primer jugador en llegar a la salida: `+100`
- segundo jugador en llegar a la salida: `+70`
- jugadores restantes que llegan después: `+50`
- caer al vacío o tocar hazard: `-15`

El HUD muestra un ranking en vivo con nombre y puntaje, y la pantalla final muestra el ganador según el mayor puntaje acumulado.

## Condiciones de ganar y perder

- Ganar nivel: todos los jugadores conectados llegan a la salida.
- Ganar campaña: se completan todos los niveles detectados por el host.
- Perder progreso de sala: cualquier caída al vacío o contacto con hazard reinicia la sala actual.

## Interfaz y feedback

- Pantalla inicial con nombre del jugador, IP, puerto y modo host/cliente.
- Lobby con lista de jugadores y estado `listo/conectado`.
- HUD en vivo con tiempo, nivel, estado de sala, ranking y log de eventos.
- Pantalla final con nombres, puntajes, duración total y ganador.
- Sonidos automáticos disparados por eventos del juego, implementados en `SoundManager`.

## Arquitectura del sistema

El proyecto está organizado por capas para separar interfaz, aplicación, dominio e infraestructura:

- `com.dino.MainApp`: bootstrap de JavaFX y ciclo de vida principal.
- `application.runtime.AppContext`: composición del runtime compartido de una instancia.
- `presentation.navigation.SceneNavigator`: carga de vistas e inyección del contexto en controladores.
- `application.services.SessionService`: estado compartido de lobby y partida.
- `application.services.SessionSnapshotMapper`: armado y aplicación de snapshots UDP.
- `application.services.HostMatchService`: coordinador de la simulación autoritativa del host.
- `application.services.PlayerPhysicsService`: física base, colisiones y push blocks.
- `application.services.ThreadConstraintService`: restricciones del hilo/cuerda.
- `application.services.LevelFlowService`: score, hazards, salida, reseteos y avance de nivel.
- `application.levels.LevelLoader`: carga de niveles y metadata desde archivos `.txt`.
- `application.usecases.*`: creación y unión de sesiones.
- `domain.rules.GameRules`: reglas puras del juego y colisiones.
- `presentation.controllers.*`: menú inicial, lobby, partida y pantalla final.
- `presentation.components.*`: observadores de scoreboard y bitácora de eventos.
- `infrastructure.network.UdpPeer`: transporte UDP.
- `infrastructure.serialization.MessageSerializer`: tipos y armado de mensajes.
- `infrastructure.audio.SoundManager`: audio procedural disparado por eventos.

```text
src/main/java/com/dino
├── application
│   ├── levels
│   ├── services
│   └── usecases
├── config
├── domain
│   ├── entities
│   ├── events
│   └── rules
├── infrastructure
│   ├── audio
│   ├── network
│   └── serialization
└── presentation
    ├── components
    ├── controllers
    └── render
```

## Patrones de diseño usados

### Observer

Se implementa mediante `EventBus` y varios suscriptores:

- `EventBus` publica eventos internos del juego.
- `ScoreBoardObserver` reconstruye el ranking a partir de snapshots.
- `EventLogObserver` mantiene una bitácora legible para la interfaz.
- `SoundManager` escucha los mismos eventos para reproducir sonidos automáticamente.

Esto desacopla la lógica de juego del HUD y del audio. El host publica eventos una sola vez y varios subsistemas reaccionan sin depender entre sí.

### Adapter

Se implementa en `UdpPeer`, que envuelve `DatagramSocket` y ofrece una API más simple para:

- `bind`
- `send`
- `broadcast`
- `broadcastBurst`
- `receive`

El resto del proyecto no trabaja directamente con la API baja de Java networking, sino con este adaptador.

### Factory ligera

`MessageSerializer` centraliza los tipos de mensaje y el método `build(...)` para armar mapas serializables. No es el patrón principal de la sustentación, pero sí ayuda a mantener consistente el protocolo.

## Comunicación UDP y modelo de red

La comunicación del proyecto es exclusivamente por UDP. No se usa TCP.

Modelo real implementado:

- el host es la única autoridad física;
- los clientes envían input (`MOVE_TARGET`, `JUMP`, `READY`, `JOIN`);
- el host simula movimiento, colisiones, puntaje, hazards, salida, cuerda y objetos interactivos cargados en la sesión;
- el host difunde snapshots completos del estado;
- los clientes aplican snapshots y renderizan.

Esto no es una malla P2P completa; es un modelo host autoritativo sobre UDP. Para la sustentación debe defenderse como un sistema distribuido funcional con interacción en tiempo real entre múltiples pantallas.

### Sincronización actual

- La frecuencia de snapshots sale de `GameConfig.SNAPSHOT_RATE_HZ`.
- El runtime usa esa misma configuración como fuente única de verdad.
- Los snapshots incluyen un `seq` para ignorar paquetes fuera de orden.
- Los clientes no re-simulan la lógica principal; aceptan el estado autoritativo del host.

### Confiabilidad UDP actual

El proyecto no implementa un protocolo confiable completo con ACK y reintentos generales. La estrategia actual es deliberadamente simple:

- input y snapshots regulares viajan como UDP best-effort;
- los mensajes críticos `START_GAME` y `GAME_OVER` se reenvían en ráfaga corta con `UdpPeer.broadcastBurst(...)`;
- esto reduce el riesgo de perder transiciones críticas sin cambiar el modelo de red ni introducir una capa confiable completa.

## Aplicación de SOLID

La aplicación de SOLID sigue siendo parcial, pero la refactorización actual dejó el diseño bastante más defendible:

- `S` fuerte/parcial: `HostMatchService` pasó a ser un coordinador y la lógica antes monolítica se separó en `PlayerPhysicsService`, `ThreadConstraintService` y `LevelFlowService`. `SessionService` conserva el estado vivo y delega snapshots a `SessionSnapshotMapper`.
- `O` parcial: las mecánicas siguen coordinándose desde el host, pero la extracción por servicios hace más sencillo extender física, hilo o flujo de nivel sin volver a crecer una sola clase gigante.
- `L` parcial: el proyecto sigue favoreciendo composición sobre herencia. Las abstracciones introducidas (`EventPublisher`, `EventChannel`, `NetworkPeer`) son contratos pequeños y sustituibles.
- `I` parcial: se introdujeron interfaces enfocadas para publicación de eventos, canal completo de eventos y transporte de red, evitando depender de APIs más grandes de lo necesario.
- `D` parcial/fuerte: los casos de uso y varios servicios núcleo dependen ahora de abstracciones (`EventPublisher`, `EventChannel`, `NetworkPeer`) y los controladores reciben un `AppContext` inyectado por `SceneNavigator` en lugar de resolver múltiples singletons globales.

## Dificultades técnicas y soluciones

- Cuerda atravesando sólidos:
  se corrigió la topología del hilo a cadena fija por orden de unión y la corrección posicional ahora valida colisiones para no arrastrar jugadores a través de muros.
- Jugador encima de otro sin poder saltar:
  se agregó una actualización explícita de estado de apilamiento para que el jugador superior cuente como `grounded`.
- Inconsistencia en snapshots:
  la frecuencia de snapshots quedó alineada a `GameConfig.SNAPSHOT_RATE_HZ` como fuente única de verdad y el mapeo de snapshots se extrajo a `SessionSnapshotMapper`.
- Mensajes críticos perdidos por UDP:
  `START_GAME` y `GAME_OVER` se envían con ráfaga corta para mejorar la entrega sin rediseñar el protocolo.
- Acoplamiento excesivo en runtime y controladores:
  se introdujeron `AppContext` y `SceneNavigator` para centralizar composición, navegación e inyección de dependencias sin meter un framework de DI.

## Niveles

Los niveles se definen como matrices numéricas en recursos. Cada archivo puede incluir metadata al inicio:

```text
name=Nivel 1 - Ruta Basica
background=forest
tileSize=64
```

Después de la metadata, cada fila representa tiles del mundo separados por comas. La conversión del código numérico a tipo de tile vive en `TileType`.

## Limitaciones actuales

- No hay suite de tests automatizados en el repositorio.
- La confiabilidad UDP sigue siendo mínima y focalizada solo en transiciones críticas.
- `MainApp` todavía actúa como bootstrap global de JavaFX y el runtime sigue siendo stateful, por lo que SOLID no está aplicado de forma perfecta en todo el proyecto.
