# Threaded

Juego cooperativo local/red hecho con JavaFX y UDP. Un jugador actúa como host autoritativo, simula la partida y distribuye snapshots del estado; los clientes envían input y renderizan el mundo sincronizado.

## Estado actual

- Proyecto Maven con Java 21 y JavaFX 21.
- Arquitectura por capas: `presentation`, `application`, `domain`, `infrastructure`.
- Partidas multiplayer simples sobre UDP con lobby, ready check y sincronización por snapshots.
- Campaña basada en niveles de texto dentro de `src/main/resources/com/dino/levels`.

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

## Cómo jugar

1. Inicia una instancia como host y define IP, puerto local y cantidad esperada de jugadores.
2. Inicia una o más instancias cliente y usa la IP/puerto del host.
3. En el lobby, cada jugador marca `Listo`.
4. El host inicia la partida.
5. Durante la partida el movimiento apunta con el mouse y el salto se ejecuta con clic izquierdo.

## Arquitectura

- `com.dino.MainApp`: bootstrap de JavaFX, servicios globales y lifecycle.
- `application.services.SessionService`: estado compartido de lobby y partida.
- `application.services.HostMatchService`: simulación autoritativa del host.
- `application.levels.LevelLoader`: carga de niveles y metadata desde archivos `.txt`.
- `presentation.controllers.*`: controladores de menú, lobby, juego y pantalla final.
- `presentation.components.*`: observadores de scoreboard y event log.
- `infrastructure.network.UdpPeer`: transporte UDP.
- `infrastructure.serialization.MessageSerializer`: protocolo base de mensajes.

## Estructura del repositorio

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

## Niveles

Los niveles se definen como matrices numéricas en recursos. Cada archivo puede incluir metadata al inicio:

```text
name=Nivel 1 - Ruta Basica
background=forest
tileSize=64
```

Después de la metadata, cada fila representa tiles del mundo separados por comas. La conversión del código numérico a tipo de tile vive en `TileType`.

## Notas de implementación

- El host mantiene la simulación y emite snapshots periódicos del estado completo.
- Los clientes no resuelven la lógica principal del juego; reflejan snapshots y envían input.
- El render usa una estética pixel art con paletas por bioma.
- El personaje host usa una variante visual inspirada en la bandera del orgullo.

## Limitaciones actuales

- No hay suite de tests automatizados en el repositorio.
- La confiabilidad de mensajes críticos sobre UDP todavía es básica.
- El estado global de runtime está centralizado en `MainApp`, lo que simplifica el proyecto pero aumenta el acoplamiento.
