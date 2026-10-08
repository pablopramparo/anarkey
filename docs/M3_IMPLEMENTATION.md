# M3 — Canciones

## Alcance

M3 agrega biblioteca local de canciones, letra editable en bloques, secciones, acordes con anclajes de texto, metadatos, favoritos, etiquetas e integración con grabaciones y afinador. La base continúa en los módulos existentes `app` y `core`. No se agregaron dependencias ni permisos de red. No se implementan el diccionario/voicings de M4, el metrónomo de M5 ni transposición/velocidad/loop/trim de M6.

## Persistencia y migración

Room avanza del esquema 2 al 3 mediante `MIGRATION_2_3`; se mantiene `MIGRATION_1_2` para instalaciones que todavía parten del esquema inicial. El esquema 3 incluye `SongEntity`, `SongSectionEntity`, `SongLineEntity`, `ChordPlacementEntity`, `TagEntity` y `SongTagEntity`. Los archivos de definición están en [`app/schemas`](../app/schemas/org.anarkey.app.recording.data.RecordingDatabase/).

La migración conserva sesiones, todas las columnas de grabación, waveform y marcadores. Reconstruye `recordings` para declarar `songId → songs.id ON DELETE SET NULL`; conserva y restaura `recording_markers` dentro de la transacción porque SQLite eliminaría los marcadores al retirar la tabla anterior. `songId` tiene índice junto con `sessionId`; las relaciones a secciones/líneas y etiquetas usan cascada. Al borrar una canción, el repositorio desvincula explícitamente las grabaciones dentro de una transacción; el FK también proporciona `SET NULL` como respaldo. No se toca ningún archivo de audio.

`Song` guarda título, artista, tonalidad sonora separada en raíz/modo, BPM, compás, IDs de instrumento y afinación del catálogo existente, capo, notas, favorito y fechas. Solo el título es obligatorio. La validación permite metadata ausente y verifica BPM (1–400), numerador/denominador, capo (0–24) y correspondencia instrumento/afinación. En 6/8 la UI rotula el tempo en negras con puntillo; en otros compases lo muestra en negras.

## Letra, secciones y acordes

La letra se divide en filas `SongLine`; el editor de bloque acepta pegar el texto completo y conserva saltos y filas vacías finales. No obliga a crear secciones: se puede empezar en el bloque general y mover líneas a Intro, Verso, Preestribillo, Estribillo, Puente, Solo, Outro o una sección personalizada. Se pueden reordenar filas y secciones, renombrar y eliminar secciones; al eliminar una sección sus filas pasan al bloque general.

Cada acorde es una fila estructurada con símbolo original, posición en puntos de código Unicode y orden estable en ese anclaje. El parser mínimo conserva raíz/letra/alteración, calidad/extensión y bajo alternativo si los reconoce; los símbolos desconocidos mantienen su texto y quedan sin identidad interpretada. El editor coloca acordes desde la selección/cursor del campo de letra, permite editar/mover/eliminar, y la lectura alinea símbolos sobre letra monoespaciada. No usa coordenadas de pantalla.

Al editar texto, se mantienen los acordes fuera del fragmento cambiado y se desplazan con el sufijo común. Los anclajes dentro del texto sustituido se llevan al inicio del fragmento. Si se quitan filas que contienen acordes, sus acordes se trasladan a la última fila conservada con su posición acotada. Esa regla evita perderlos sin aviso y está cubierta por prueba instrumentada con emoji, inserción de texto y reducción de filas.

## Biblioteca e integraciones

La biblioteca ofrece búsqueda por título/artista, filtro de favoritas y de etiqueta, orden por favoritas y título, creación solo con título y confirmación al borrar. Las etiquetas se normalizan para evitar duplicados. La canción ofrece una vista de lectura y otra de edición.

Una grabación puede conservar Session y recibir/quitar Song simultáneamente. Desde Song se vinculan grabaciones existentes, se abre su detalle/reproducción y se inicia una captura ya asociada a Song; no se copian los archivos. Borrar la canción deja disponible tanto la fila de grabación como su archivo.

El afinador contextual usa IDs del catálogo de la canción y A4 de las preferencias. Si faltan instrumento/afinación, se explica cómo configurarlos y no se inicia el destino contextual con una afinación implícita. La selección temporal contextual no escribe las preferencias globales; Atrás vuelve a la canción.

## Pendientes puntuales de M2 resueltos

La constante `FOREGROUND_SERVICE_TYPE_MICROPHONE` existe desde API 30. El proyecto declara `minSdk 26` y `targetSdk 36`. `RecordingService` pasa el tipo microphone desde `Build.VERSION_CODES.R` (API 30) y pasa `0` antes de API 30, cuando el tipo no existe. La comprobación de lint `InlinedApi` queda protegida por la versión real de la constante; no hay supresión global ni cambio de dependencias. El canal de notificación se crea sin guarda redundante porque `minSdk` ya es 26.

El botón de marcador muestra el conteo que llega de Room en tiempo real (por ejemplo, “Agregar marcador · 1”), por lo que TalkBack anuncia el valor junto al nombre de la acción sin diálogo ni interrupción.

## Pruebas y verificación

`SongDomainTest` cubre parser de acordes, preservación de símbolos desconocidos, offsets Unicode y validación de metadata. `SongRepositoryTest` cubre creación por título, letra multilínea/filas vacías, conservación de acordes, reordenamiento/organización de filas y secciones, favoritos, etiquetas, vinculación simultánea Session/Song y eliminación sin pérdida de grabación/archivo. `RecordingMigrationTest` abre bases SQLite reales de versiones 1 y 2 y valida columnas, FK `SET NULL` y conservación/cascada de marcadores.

Verificación final en 2026-10-06: `:core:test`, `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, `:app:lintDebug` y `:app:assembleDebug` completaron con `BUILD SUCCESSFUL`. Las suites JVM sumaron 1.146 casos: 0 fallos, 0 errores y 1 omitido preexistente. En el Samsung SM-G998B con Android 15 pasaron los 9 tests instrumentados (1.155 casos combinados). Lint terminó con 0 errores y 46 advertencias sobre versiones de dependencias disponibles, uso de kapt y recursos sin uso; no se actualizaron dependencias. El APK debug se instaló en el dispositivo.

Recorrido manual en el Samsung: se creó una canción solo con título, se inició y guardó una captura desde ella, se confirmó el vínculo en la canción y se abrió su reproducción; al cerrar el detalle/modal se volvió a la canción. También se concedieron los permisos de micrófono/notificaciones y se observó la pantalla de grabación. Las pruebas instrumentadas cubren pegado multilinea con filas vacías, secciones, anclajes de acordes, búsqueda/favoritos/etiquetas y borrado de canción conservando grabación/archivo. No se ingresaron acordes manualmente en el dispositivo.

## Límites

No hay importación de sitios, sincronización, cloud, cuentas, telemetría, Internet, transposición, metrónomo ni diccionario de acordes. La forma de lectura alinea con fuente monoespaciada para anclajes por carácter; una disposición tipográfica más avanzada, edición por arrastre, deshacer y modo escenario siguen pendientes. El editor de acordes se validó mediante pruebas instrumentadas, pero su flujo de entrada no se probó manualmente en dispositivo.
