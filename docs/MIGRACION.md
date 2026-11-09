# Migración a Waybridge

## 1. Actualización del mod (AMP, Essential o Minecraft local)

1. Detén el servidor o cierra el mundo.
2. Respalda `world/`, `mods/` y la configuración de TP si contiene autorizaciones.
3. Retira **solo** `mods/servercito-teleport-1.0.0.jar` (si lo tenías). No es necesario borrar otros mods ni volver a instalar Homestead.
4. Añade `mods/waybridge-1.0.0.jar` (compilado desde el código de Waybridge).
5. **No elimines** `config/servercito-teleport.json`.
6. Inicia Minecraft. Al encontrar el archivo anterior sin un `config/waybridge.json`, Waybridge valida la configuración antigua, copia sus valores a `config/waybridge.json` y conserva el original.
7. Comprueba en consola AMP: `stadmin list`. Los UUIDs autorizados deberían ser los mismos.
8. Prueba `/stp Jugador` y `/stwp X Y Z`. No necesitas cambiar las plantillas actuales de Xaero (`/stp` y `/stwp`).

Si `config/waybridge.json` ya existe, **no se sobrescribe ni se fusiona** con el archivo antiguo. Para volver a importar, haz una copia de seguridad primero y decide explícitamente qué archivo conservar.

## 2. Mantener el historial Git de tu repositorio

Si ya subiste `servercito-teleport` a GitHub, **no tienes que crear otro repositorio**. Renómbralo en GitHub → **Settings → General → Repository name** y actualiza tu `origin`.

Tu repositorio local y el nuevo ZIP son dos carpetas distintas. Si quieres conservar los commits originales, **trabaja en la carpeta local que ya contiene `.git`**. Copia los archivos del nuevo proyecto allí, sustituye el contenido de `src`, `README.md`, los `.gradle`, scripts y demás ficheros del proyecto; borra el Java/JSON antiguos y conserva `.git`, `gradlew*` y `gradle/wrapper` existentes. No copies ni subas `build/`, `.gradle/`, `run/` o `.herramientas/`.

Ejemplo de comandos desde la raíz de ese repositorio con PowerShell (tras copiar los archivos actualizados):

```powershell
git status
git add -A
git commit -m "Rename project to Waybridge"
git remote set-url origin https://github.com/TU_USUARIO/waybridge.git
git push -u origin main
```

Si aún **no** tienes repositorio, abre la carpeta `waybridge` y crea uno nuevo en GitHub llamado `waybridge` (sin README inicial). Luego usa `git init`, `git add .`, `git commit` y `git push` como normalmente.

La versión del proyecto sigue siendo **1.0.0** porque este cambio es de identidad, no la implementación planeada de teletransporte a waypoints entre dimensiones. En GitHub puedes documentar la actualización antes de etiquetar una nueva release.

## 3. Qué cambió internamente

| Antes | Ahora |
| --- | --- |
| `servercito-teleport` | `waybridge` (Gradle, JAR, repositorio propuesto) |
| `servercitoteleport` | `waybridge` (Fabric mod ID) |
| `sv.servercito.teleport.ServercitoTeleport` | `dev.waybridge.WaybridgeMod` (Java / entrypoint) |
| `config/servercito-teleport.json` | `config/waybridge.json` (migración automática) |
| `/stp`, `/stwp`, `/stadmin` | Sin cambios |

**No** instales los dos mods a la vez: ambos registrarían `/stp`, `/stwp` y `/stadmin`.
