# Toolbox M1 — implementación y verificación

6 de octubre de 2026. Alcance: navegación, configuración y ciclo de vida del afinador.
M2 **no iniciado**. M0 sigue abierto: validación física parcial con variación durante
decay por dominancia de parciales, pendiente de resolver o aceptar explícitamente.

## Cambios

| Área | Implementación |
| --- | --- |
| Navegación | `navigation/AppShell.kt`: Navigation Compose, inicio en Afinador, cinco destinos, Atrás, conservación de estado por destino y Ajustes fuera de la barra inferior. Los otros cuatro destinos son shells informativos, sin motores, editores ni persistencia de contenido. |
| Contexto futuro | `navigation/TunerRoute.kt`: IDs de instrumento/afinación + A4; `openTuner(configuration)` apila un afinador contextual. Contexto inválido produce error visible, nunca guitarra estándar silenciosamente. Cambios de selección contextuales son locales a esa entrada. No hay enlaces externos ni pantalla de Canción ficticia. |
| Tema | `ui/AnarkeyTheme.kt`: paleta oscura/lavanda compartida por shell, afinador, ajustes y diagnóstico. `TunerScreen` salió de MainActivity; medidor y espectro conservan su implementación. |
| Catálogo | `core/music/TuningCatalog.kt`: guitarra Standard/Drop D/DADGAD, ukelele Sol agudo/Sol grave, violín Standard; más modo cromático sin cuerdas. Datos con IDs, instrumento, número físico y MIDI de cada cuerda. UI sin lista EADGBE, conteo fijo de seis ni cálculo manual basado en 440. |
| Preferencias | `settings/`: Preferences DataStore local, selección de instrumento/afinación, A4 de 400–480 Hz con decimales y cuatro notaciones: letras/solfeo × sostenidos/bemoles. Carga antes de activar audio; errores de lectura/escritura visibles y reintento sin borrar datos. |
| Lifecycle | `MainActivity` usa el lifecycle RESUMED de la entrada del afinador, no el de toda la Activity. Salir, abrir Ajustes o ir a segundo plano cancela captura y libera AudioRecord. Un mutex en el ViewModel serializa la finalización completa antes del siguiente inicio. La pausa manual se conserva mientras vive el ViewModel. |
| A4 | Parámetro nuevo en TunerEngine, aplicado únicamente a nota, histéresis de nombre y cents. AudioCapture lo pasa al crear el motor; cambios de configuración crean una sesión nueva y limpian historial de lecturas anterior. Diagnósticos comparativos usan la misma referencia y registran A4 al iniciar. |

No se modificaron YIN, seguimiento de parciales, filtro, ventanas, hop, umbrales,
algoritmos de decay ni tests existentes. Se verificaron los hashes previos de
`YinPitchDetector.kt` y `PartialAnchoredReading.kt`; el diff de `TunerEngine` solo
agrega/valida A4 y lo pasa a tres conversiones de PitchMath.

Dependencias directas nuevas: Navigation Compose 2.9.0 y Preferences DataStore
1.1.7. Licencias y transitivas nuevas documentadas en `THIRD_PARTY_NOTICES.md`;
Apache-2.0 y BSD-3-Clause incluidos en assets. No Room, Media3, servicio foreground,
grabador, canciones, acordes, metrónomo, cuenta, red o telemetría.

## Verificación automatizada

Comando: `gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain`.

- Core: 1127 casos aprobados; 1 omitido intencionalmente (`WavDiagnosticReplayTest`,
  requiere `ANARKEY_DIAGNOSTIC_WAV`). Incluye las regresiones existentes de decay.
- App: 10 casos aprobados. Total: **1137 aprobados, 0 fallos, 1 omitido**.
- Nuevos: 9 tests puros de catálogo/referencia y 2 de navegación contextual.
  Cubren compatibilidad, Drop D, orden reentrante, referencia no estándar,
  igualdad de estados con A4 predeterminado/explícito a 440 y rechazo de contextos inválidos.
- Debug APK generado. Lint: **0 errores, 41 advertencias**: 34 recursos sin uso y
  7 avisos de actualización de dependencias. No se agregaron supresiones ni baseline.
  Se corrigieron tres traducciones de diagnóstico preexistentes que faltaban en español.

Reportes: `core/build/reports/tests/test/index.html`,
`app/build/reports/tests/testDebugUnitTest/index.html`,
`app/build/reports/lint-results-debug.html`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256 del APK instalado:
`8D41327019607A3528EFF8478489D4F08DFC1C2E2BB91D460560F25F863EC431`.
El manifiesto final conserva RECORD_AUDIO y no incluye INTERNET ni permisos de
servicio foreground; AndroidX agrega únicamente su permiso interno de receptor.

## Verificación en dispositivo

Samsung **SM-G998B, Android 15** conectado por ADB. Instalación conservando datos y
apertura correctas. Se inspeccionó la pantalla y su jerarquía en español.

Comprobado: los cinco destinos, regreso con Atrás, parada del micrófono al salir,
reanudar al volver, A4=442.5 y solfeo/bemoles tras force-stop/reapertura,
Drop D con sexta cuerda Re2, ukelele reentrante Sol4–Do4–Mi4–La4, cromático sin
controles de cuerdas, y parada al enviar la app a segundo plano. Se restauraron
guitarra estándar, 440 Hz y letras/sostenidos. No se exportaron audios durante estas pruebas.

Sobre el APK final se volvió a comprobar conservación de la cuerda seleccionada
al navegar y volver, cambio Standard→Drop D→Standard y entrada/salida de Ajustes.
Los eventos muestran cada `CAPTURE_STOPPED` antes del siguiente `CAPTURE_STARTING`;
sin errores de captura ni excepciones fatales en el log de ese proceso. La app se
dejó en segundo plano con el micrófono liberado tras la verificación.

Esta prueba funcional no valida afinación acústica ni resuelve decay. Falta aceptación
manual ampliada de TalkBack, fuente grande, rotación, permisos revocados y bloqueo
de pantalla; el lifecycle cubre la salida de RESUMED, pero no se afirma una prueba
física de cada condición. El retorno contextual completo desde Canción se probará
cuando exista ese módulo; en M1 se prueba el contrato de argumentos sin implementarlo.

## Decisiones y deuda

- Las cuatro herramientas futuras se muestran como shells por el alcance explícito
  de M1, sin controles falsos de grabación o reproducción.
- Se conserva el perfil de captura con pasaaltos a 60 Hz. No se ofrece bajo ni se
  declara aceptación acústica de nuevos instrumentos; sus afinaciones están por
  encima de ese corte incluso con A4=400. Cambiar el perfil sería trabajo DSP separado.
- El rango de referencia elegido es 400–480 Hz; editor de afinaciones propias y
  Guided/Normal/Pro quedan pendientes, fuera de M1.
- No hay coordinador genérico de audio aún: solo existe un consumidor real. La
  captura y su ciclo de vida quedan aislados para incorporar propiedad exclusiva
  del micrófono al implementar el servicio de M2.
- DataStore contiene únicamente preferencias. Las reglas existentes excluyen backup
  automático y transferencia; backup/exportación de contenido siguen siendo futuros.
- Se preservan recursos antiguos sin uso para no mezclar una limpieza del prototipo
  con M1; los avisos de dependencias no motivan actualizar todo el toolchain.

No se necesita una nueva decisión para utilizar M1. **Detenerse aquí antes de M2.**
