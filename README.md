# Waybridge

**Waybridge** is a Fabric mod for Minecraft **1.20.1** that allows trusted players to teleport to other players or safe waypoint coordinates **without OP**.

A mod de teletransporte para Minecraft **1.20.1 Fabric**, compatible con servidores dedicados (por ejemplo, AMP) y con mundos locales compartidos mediante Essential. La integración con **Xaero's Minimap / World Map** se realiza mediante comandos configurables: no requiere modificar Xaero ni acceder a sus archivos internos.

> **Estado:** versión 1.0.0, renombrada desde Servercito Teleport. Esta actualización cambia la identidad del mod, pero conserva los comandos y las funcionalidades ya implementadas. **El teletransporte a waypoints de otras dimensiones aún no está implementado**. El código fuente de este paquete no ha sido recompilado en este entorno; verifica la compilación en VS Code antes de usarlo en tu servidor principal.

## Características

- Autorización individual por **UUID**, sin otorgar OP.
- TP libre a un jugador conectado, incluso si está en otra dimensión.
- TP a coordenadas en la **dimensión actual**, con búsqueda de suelo firme y espacio libre.
- Límite de distancia opcional y cooldown configurable (10 segundos de forma predeterminada).
- Migración automática del archivo `config/servercito-teleport.json` a `config/waybridge.json` cuando arranca por primera vez.
- Compatible con FTB Essentials: no reemplaza `/tpa`, `/home`, `/back` ni `/tp` vanilla.
- **Servidor dedicado / integrado:** no es necesario instalar el mod en los clientes de quienes se conectan a un servidor dedicado. En una partida local alojada mediante Essential, el anfitrión lo instala.

## Comandos

| Comando | Uso | Permiso |
|---|---|---|
| `/stp <jugador>` | Viajar hasta un jugador conectado; admite distintas dimensiones | Jugador autorizado u OP |
| `/stwp <x> <y> <z>` | Viajar a coordenadas seguras de la dimensión actual | Jugador autorizado u OP |
| `/stadmin grant <jugador>` | Autorizar a un jugador conectado | OP o consola |
| `/stadmin revoke <jugador>` | Revocar un jugador conectado | OP o consola |
| `/stadmin list` | Mostrar UUIDs autorizados | OP o consola |
| `/stadmin reload` | Volver a leer el archivo JSON | OP o consola |

En la consola AMP, ejecuta los comandos **sin** `/` (por ejemplo, `stadmin grant Steve`). Dentro del chat de Minecraft usa `/`.

## Xaero: comandos a configurar

En el cliente del jugador (Xaero's Minimap / World Map), desactiva `Use Default` y configura el campo correspondiente:

| Función en Xaero | Plantilla |
|---|---|
| **Player Teleport Command** | `/stp {name}` |
| **Map Teleport Command** (normal, no cross-dim) | `/stwp {x} {y} {z}` |
| **Minimap → Waypoint Teleport Command** (normal) | `/stwp {x} {y} {z}` |

No cambies el comando *Cross-Dim Teleport*: la versión 1.0.0 todavía no incluye `/stwpdim`. El botón del mapa y el de waypoints del minimapa son configuraciones separadas.

## Requisitos y compilación (VS Code / Windows)

- JDK 17 (por ejemplo, Eclipse Temurin 17).
- Visual Studio Code con **Extension Pack for Java** y **Gradle for Java**.
- Conexión a Internet en la primera compilación.
- Gradle **8.9**; **no** Gradle 9.x para Fabric Loom 1.7.4.

Abre la carpeta `waybridge` (contiene `build.gradle`) y ejecuta en PowerShell:

```powershell
java -version
javac -version
cmd /c .\compilar_windows.bat
```

El script descarga Gradle 8.9 si hace falta, genera Gradle Wrapper para VS Code y compila el mod. Debe terminar en `BUILD SUCCESSFUL`.

**JAR instalable:** `build/libs/waybridge-1.0.0.jar` (no `*-sources.jar` ni `*-dev.jar`).

Para probar Minecraft en el entorno de desarrollo, luego de generar el wrapper:

```powershell
.\gradlew.bat runClient
```

Para VS Code si el servidor Gradle utiliza Java 25, fija `java.import.gradle.java.home` a tu directorio JDK 17 y recarga la ventana. Una vez creado el wrapper, VS Code debe usar Gradle 8.9.

### Ubuntu

```bash
bash compilar_ubuntu.sh
```

Instala antes un JDK compatible (17 recomendado), `curl` y `unzip` si no están presentes. El JAR también queda en `build/libs/waybridge-1.0.0.jar`.

## Instalación / actualización

1. **Detén Minecraft** en AMP, o cierra el mundo y el cliente si es una partida local. Haz una copia de seguridad de tus datos.
2. Si tenías la versión anterior, **retira `servercito-teleport-1.0.0.jar`** de `mods/`. No dejes ambos JAR en la misma instancia; registran los mismos comandos.
3. Copia `waybridge-1.0.0.jar` a `mods/`.
4. **Conserva el archivo `config/servercito-teleport.json`** si existe: Waybridge lo leerá en el primer arranque y creará `config/waybridge.json` conservando `authorized_uuids`, cooldown y los demás ajustes.
5. Inicia el juego y verifica `stadmin list` (o `/stadmin list` en el chat con OP). Comprueba que siguen autorizados los mismos UUID.
6. Confirma que funcionan `/stp` y `/stwp`. Los comandos de Xaero **no se modifican**.

**Reglas de migración:** solo se copia el archivo antiguo si `config/waybridge.json` no existe. Si ambos existen, gana `waybridge.json`. El JSON original nunca se elimina. Si el JSON antiguo es inválido o no puede guardarse el nuevo, la migración falla sin conceder permisos de manera accidental; corrige el error antes de continuar.

Ejemplo de configuración en [`ejemplos/waybridge.json`](ejemplos/waybridge.json):

```json
{
  "authorized_uuids": [],
  "cooldown_seconds": 10,
  "max_waypoint_distance": 0,
  "safe_search_horizontal_radius": 2,
  "safe_search_vertical_radius": 6
}
```

`max_waypoint_distance: 0` significa sin límite de distancia. Las búsquedas de aterrizaje seguro pueden rechazar destinos sin un espacio libre adecuado o cuando el área está fuera de los límites del mundo.

## Compatibilidad / límites

- Minecraft 1.20.1 · Fabric Loader 0.14.22 o superior · Fabric API · Java 17 o superior.
- Probado previamente por el usuario en Homestead con Xaero usando la versión anterior; **Waybridge renombrado aún requiere validación**.
- `/stp` viaja entre dimensiones al seguir a un jugador; `/stwp` **solo** a coordenadas de la dimensión actual.
- No comprueba explícitamente los reclamos territoriales de Open Parties and Claims o FTB Teams: concede acceso solo a jugadores de confianza.
- No garantiza que FTB Essentials `/back` registre los viajes realizados por Waybridge.

## Desarrollo

El código fuente principal está en `src/main/java/dev/waybridge/WaybridgeMod.java`; el identificador Fabric es `waybridge`. Configuración de compilación en `build.gradle`, `gradle.properties` y `settings.gradle`.

La migración del nombre desde Servercito Teleport y del repositorio GitHub se documenta en [`docs/MIGRACION.md`](docs/MIGRACION.md).

Licencia MIT. Consulta [`LICENSE`](LICENSE).
