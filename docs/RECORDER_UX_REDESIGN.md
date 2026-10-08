# Rediseño UX del Grabador — 7 de octubre de 2026

La pantalla en reposo centra un botón circular violeta de 80 dp (64 dp en diseño compacto). Grabaciones y Sesiones son vistas separadas; «Sin sesión» es un filtro de grabaciones. En grabación y pausa, el cronómetro ocupa el centro y Pausar/Reanudar, Marcador y Detener y guardar quedan cerca de la navegación inferior. La lista se oculta durante la captura. «Conservar para recuperar» queda en el menú secundario con una explicación antes de ejecutarse. No se añadió medidor de audio ni se tocó el servicio de captura, Room o la recuperación.

Con fuente grande o pantalla angosta, las vistas y acciones se apilan y el contenido se desplaza completo para mantener todos los controles accesibles. Los mensajes vacíos distinguen grabaciones y sesiones.

## Verificación

- `:core:test`: 1151 tests, 0 fallos (1 omitido).
- `:app:testDebugUnitTest`: 11 tests, 0 fallos.
- `:app:assembleDebug` y `:app:lintDebug`: completados. Lint no señaló el archivo del Grabador.
- APK instalado en Samsung SM-G998B. Revisión visual en reposo, grabando, en pausa, menú y explicación de recuperación, y Sesiones vacías.
- Prueba adicional con fuente 1,5× y tamaño simulado 720×1600: las acciones quedan disponibles desplazando la pantalla. La navegación global también agranda y parte sus rótulos en esa configuración; queda fuera del Grabador.
- Se restauraron fuente 1,0× y tamaño 1080×2400. Se eliminaron las tres grabaciones de prueba creadas durante esta revisión.

Capturas: [reposo](screenshots/recorder-ux/idle.png), [grabando](screenshots/recorder-ux/recording.png), [en pausa](screenshots/recorder-ux/paused.png), [recuperación](screenshots/recorder-ux/recovery.png), [sesiones](screenshots/recorder-ux/sessions.png), [fuente grande](screenshots/recorder-ux/small-font-paused.png).

## Ajuste de reposo — 7 de octubre de 2026

Se quitó la altura proporcional al viewport del bloque «Grabar». El botón circular queda inmediatamente antes de las vistas Grabaciones/Sesiones; el filtro y la lista siguen a continuación. El espacio sobrante cae después de la lista. En pantallas bajas el botón usa 72 dp y las vistas se mantienen en una fila cuando hay ancho; el contenido completo puede desplazarse.

`:app:testDebugUnitTest`, `:app:assembleDebug` y `:app:lintDebug` pasaron. Se instaló el APK en el Samsung SM-G998B y se revisó el reposo en vertical, horizontal y en 720×1600 con fuente 1,5×. En horizontal y en la configuración reducida la lista requiere desplazamiento; se comprobó que sigue accesible. La grabación activa mostró sus controles habituales. Se restauraron la orientación, fuente y resolución originales y se eliminó la grabación usada para esa comprobación.

Capturas del ajuste: [vertical](screenshots/recorder-compact/portrait.png), [horizontal](screenshots/recorder-compact/landscape.png), [lista en horizontal](screenshots/recorder-compact/landscape-scrolled.png), [pantalla reducida y fuente grande](screenshots/recorder-compact/small-large-font.png), [lista desplazada](screenshots/recorder-compact/small-large-font-scrolled.png).
