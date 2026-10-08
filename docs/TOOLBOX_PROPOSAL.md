# Anarkey: de afinador a herramienta de práctica

Propuesta para revisar — 6 de octubre de 2026. No implementa funcionalidades ni agrega dependencias.

**Estado: propuesta adoptada.** Decisiones tomadas y su motivo (sustituyen las preguntas del apartado 13):

- M1 se implementó de forma aislada: la validación física del afinador es parcial (M0 sigue abierto por la
  variación durante el decay) y M1 no toca YIN ni el decay, así que no se bloquean entre sí.
- M2 incluye continuidad con pantalla bloqueada mediante servicio de primer plano; velocidad, A–B y recorte
  quedan en M6. Motivo: sin continuidad se interrumpen los ensayos largos, y acotar M2 permite validarlo antes.
- 6/8 cuenta el BPM en negras con puntillo, con la unidad visible. Motivo: sin unidad, «60 BPM» es ambiguo.
- Los acordes se escriben relativos al capo y la tonalidad es el sonido real. Motivo: separa lo que se toca
  de lo que suena y permite transponer.

Este documento cubre el alcance de M1; M2 se planifica aparte.

## 1. Punto de partida y arquitectura

Revisé `MainActivity`, `TunerViewModel`, `AudioCapture`, `TunerEngine`, `PitchMath`, Gradle, manifiesto, reglas de backup y documentación existente.

El código está más avanzado que el README en UI: ya tiene fondo oscuro, acento violeta, selección de cuerda y barra inferior. Pero esa barra es decorativa: los destinos tienen callbacks vacíos. No hay persistencia ni navegación real. Guitarra estándar está hardcodeada en varias partes de `MainActivity`; los selectores no seleccionan instrumentos. `PitchMath` admite A4, pero pantalla y motor usan su valor predeterminado. Guided/Normal/Pro y preferencias de notas siguen pendientes.

La separación útil ya existe: `:core` es Kotlin/JVM con DSP y matemática musical; `:app` contiene AudioRecord, ViewModel/StateFlow y Compose. Mantener ambos módulos. Organizar `app` por paquetes de funcionalidad y agregar paquetes pequeños compartidos para datos, audio, navegación y tema. En `core`, incorporar teoría musical y cálculo temporal cuando los respectivos hitos lo necesiten.

Flujo: **Compose → ViewModel → repositorio o controlador Android → Room/archivo/audio**. Inyección manual mediante un contenedor de aplicación; sin Hilt, bus de eventos, capas de casos de uso obligatorias ni un módulo Gradle por pantalla. Reutilizar modelos puros donde sirvan; separar entidades Room solamente donde la persistencia lo requiera.

Compartir vocabulario musical, catálogo de instrumentos/afinaciones, preferencias, tema, selección de acordes y política de uso del audio. Un coordinador pequeño concede el micrófono al afinador o grabador, nunca a ambos. El mutex actual de AudioCapture protege solo esa instancia: no resuelve competencia entre herramientas. No convertir el buffer diagnóstico de 30 segundos en un grabador.

## 2. Navegación y diseño

Barra inferior con **Afinador · Grabador · Canciones · Acordes · Metrónomo**; Ajustes en el menú superior. Es la evolución directa de la UI actual y deja las herramientas al alcance del pulgar. Un drawer oculta funciones frecuentes; combinar ambos agrega navegación redundante. Comprobar cinco etiquetas en español, pantallas pequeñas y fuente ampliada antes de cerrar el diseño.

Arranque normal siempre en Afinador, sin dashboard. Cada destino conserva su estado de pantalla. Los accesos desde una canción apilan la herramienta con contexto y Atrás vuelve a esa canción, conservando posición y edición. Pasar IDs y opciones pequeñas, no ViewModels ni canciones completas. El acceso contextual no sobrescribe silenciosamente preferencias globales.

Tocar un acorde abre una bottom sheet con posiciones, notas y afinación; «Abrir explorador» es secundario. En entregas parciales, mostrar solo destinos funcionales, sin cuatro pantallas vacías. Extraer el tema ya existente: grafito, blanco/gris y lavanda, controles amplios, semántica accesible. Mantener traducciones ES/EN.

## 3. Datos y persistencia

Room para datos estructurados, archivos para audio y Preferences DataStore para A4, nombres de notas, lateralidad y últimas opciones. Una sola base, IDs estables, claves foráneas e índices; introducir tablas al implementar cada función.

| Tipo | Datos mínimos propuestos |
| --- | --- |
| Song | id, título, artista, tonalidad escrita/modo opcionales, tempo opcional con unidad de pulso, compás opcional, instrumentId/tuningId opcionales, capo, notas, favorita, creación/modificación |
| SongSection | id, songId, orden, tipo y título libre, notas |
| SongLine | id, sectionId, orden, letra |
| ChordPlacement | id, lineId, orden/posición en letra, raíz escrita, fórmula, bajo opcional; texto original si no se reconoce |
| Session | id, nombre, notas, creación/modificación |
| Recording | id, nombre, fechas, duración final, archivo relativo, MIME/codec, sample rate/canales, tamaño, estado, notas, sessionId y songId opcionales |
| RecordingMarker | id, recordingId, posición en audio, texto opcional |
| Tag / SongTag | etiqueta normalizada y relación, cuando se implemente filtrado |
| ChordFavorite | identidad musical y contexto de instrumento/afinación; posición opcional |

`Instrument` y `Tuning` son inicialmente **referencia empaquetada**, con IDs estables, nombres localizables y lista ordenada de notas MIDI abiertas; no asumir seis cuerdas ni orden ascendente de alturas. Las afinaciones personalizadas tendrán datos de usuario cuando se incorporen. Validar que instrumento y afinación sean compatibles.

Fórmulas de acordes y un catálogo pequeño de posiciones revisadas son referencia. Notas componentes, frecuencias, intervalos calculados, transposición y candidatos de reconocimiento son derivados. Waveform es caché regenerable, fuera de la DB. Duración y propiedades del archivo son metadata extraída para listar sin abrir cada audio. No hace falta una tabla global de todos los acordes ni `MetronomePreset` hasta ofrecer presets guardados; las últimas opciones bastan inicialmente.

Exportar esquemas Room y probar migraciones con datos; nunca usar migración destructiva para datos del usuario. Mantener exclusión de backup automático existente. Exportación manual de audio desde el MVP; backup completo futuro mediante archivo versionado con manifiesto, datos y audios, preparado para remapear IDs y rutas al importar. No cloud ni permiso de Internet.

## 4. Song / Session / Recording

Recomiendo la alternativa A con **Session como agrupación opcional**, sin entidad Project:

`Session 1 ← N Recording N → 1 Song`, ambas relaciones opcionales.

Componer: una Song agrupa sus grabaciones por songId, sin sesión obligatoria. Ideas sueltas: sin canción, con o sin sesión. Ensayo: una sesión contiene grabaciones vinculadas a distintas canciones. Pruebas de sonido: sesión sin canción. No duplicar BPM, afinación ni letra en Session.

Una canción puede aparecer en muchas sesiones mediante sus grabaciones. Una grabación larga de ensayo puede quedar ligada solo a Session y tener marcadores; asociar segmentos a varias canciones queda para después. Una relación directa Session–Song solo se justifica si llega una función de repertorio/setlist.

Eliminar Song o Session desvincula grabaciones (`SET NULL`), no destruye audio. Secciones y posiciones sí se eliminan con su canción. Eliminar una grabación elimina sus marcadores y programa eliminación segura del archivo. La UI diferencia «quitar vínculo» de «eliminar grabación».

## 5. Letra y acordes como datos

Editor por secciones y líneas. Insertar acorde en el cursor mediante chip/formulario sencillo; admitir líneas con acordes sin letra. La posición se expresa en límites de caracteres Unicode definidos, nunca coordenadas de pantalla. Al editar texto se desplazan los anclajes; borrar un fragmento que los contiene los lleva al inicio del fragmento conservando su orden, con deshacer. Probar acentos, emoji y saltos de línea.

Representación musical: raíz `SpelledPitch(letter, accidental)`, fórmula identificada (`major`, `minor`, `7`, `maj7`, `m7`, `sus2`, `sus4`, `add9`...), y bajo escrito opcional. Ejemplo: F#m7/C# conserva F sostenido, fórmula m7 y C sostenido como bajo. La clase de altura se deriva: conservar solo un entero perdería la diferencia editorial entre F# y Gb.

Parser acotado a esas formas y alias documentados. Un símbolo desconocido se conserva visible y editable, marcado como no interpretable: no se transpone a ciegas. La transposición mueve raíz y bajo, conserva fórmula y elige escritura según tonalidad/preferencia; no modifica el original hasta una acción explícita. Tonalidad sonora, capo y formas escritas deben distinguirse: propongo acordes escritos como digitaciones relativas al capo y tonalidad como sonido real.

MVP: letra+acordes, solo acordes y tamaño de fuente. Después: transposición, modo escenario, pantalla encendida optativa y autoscroll ajustable. No importar formatos musicales ni construir un editor de partituras ahora.

## 6. Motor de acordes

Kotlin puro en `core/music`: fórmulas como grados/alteraciones e intervalos; notas derivadas desde raíz. Separar identidad del acorde de `Voicing(instrumentId, tuningId, frets, fingers?, barres?)`. Traste nulo significa cuerda apagada; cero, abierta. Zurdo refleja el dibujo, no cambia los datos musicales.

MVP con guitarra y posiciones revisadas de autoría propia, múltiples voicings, búsqueda, notas, intervalos y favoritos. Toda posición debe declarar afinación: un diagrama estándar no sirve automáticamente para Drop D. Si no existe posición compatible, mostrar notas y la limitación en lugar de un diagrama incorrecto.

Después, reconocimiento desde diapasón: obtener alturas según afinación/trastes, probar raíces/fórmulas y ordenar candidatos por coincidencia y bajo. Devolver alternativas ante ambigüedad; C6 y Am7 pueden compartir notas. Generación de digitaciones con restricciones de alcance y dificultad es posterior: coincidencia matemática no garantiza tocabilidad. Escucha sintética offline puede reutilizar salida PCM posteriormente, sin banco externo obligatorio.

Tests puros: equivalencias enarmónicas, fórmulas, slash chords, transposición inversa, capo, afinaciones reentrantes, validación de voicings y reconocimiento ambiguo.

## 7. Metrónomo: pulso de audio

Propuesta inicial: **AudioTrack en MODE_STREAM**, PCM de clics generados, buffers reutilizados y worker dedicado. El renderizador Kotlin coloca clics en posiciones de muestras mediante acumulador fraccional; el reloj audible es el flujo de muestras, no `delay()` ni recomposición. Android expone modo de baja latencia y timestamps; la ruta real requiere medición. [API AudioTrack](https://developer.android.com/reference/android/media/AudioTrack).

Modelo: numerador, denominador, agrupación, patrón de acentos, BPM y unidad del BPM. No confundir denominador con subdivisión. 4/4 usa cuatro negras; propongo 6/8 como dos negras con puntillo con grupos 3+3 y seis clics de corchea. Mostrar la unidad junto al BPM; así 60 BPM no es ambiguo. La misma estructura admite otras agrupaciones sin ramas por cada compás.

MVP: 30–300 BPM, Start/Stop, Tap Tempo, 2/4–3/4–4/4–6/8, acento principal/secundario, sonidos sintetizados seleccionables, volumen y pantalla activa mientras funciona. Tap calcula mediana de los últimos intervalos válidos, reinicia tras una pausa y filtra valores atípicos; permitir reiniciar la secuencia para cambios grandes de tempo.

La UI observa posición reproducida mediante timestamp/playback head, con fallback, y no controla los clics. Cambios de BPM se aplican en un límite de pulso; cola corta y control de underruns. Probar redondeo sin deriva, acentos y cambios con Kotlin puro; medir estabilidad audible, CPU y rutas altavoz/cable/Bluetooth en dispositivos. Latencia de salida y regularidad son problemas distintos.

AAudio implicaría NDK; Oboe agregaría C++ y dependencia. No los propongo inicialmente. Reevaluar solo si AudioTrack falla criterios medidos. Subdivisiones elegibles, patrones editables, count-in, rampas y temporizador quedan después. El primer metrónomo funciona en primer plano; segundo plano exige su propio alcance de servicio.

## 8. Grabador y archivos

MVP: MediaRecorder, AAC en contenedor M4A privado (`filesDir/recordings/<uuid>.m4a`), mono inicialmente. Pause/resume está disponible desde API 24 y el proyecto parte de API 26. La selección definitiva de fuente/bitrate se prueba con instrumentos reales. [API MediaRecorder](https://developer.android.com/reference/android/media/MediaRecorder).

Incluye grabar, pausar, reanudar, reproducir, seek, renombrar, notas, eliminar, mover entre sesiones, marcador de un toque y compartir/exportar. Marcadores usan tiempo activo monotónico excluyendo pausas, reconciliado con la duración final. Para waveform final, decodificar con MediaExtractor/MediaCodec a picos reducidos en segundo plano; durante captura mostrar nivel aproximado, sin llamarlo waveform exacta.

Media3 ExoPlayer para reproducción local, con 0.5x/0.75x/1x y pitch 1.0. Su API separa velocidad y pitch; calidad a baja velocidad debe escucharse. Loop A–B con segmento recortado reproducible y repetición, sin timer Compose; no prometer loop sin discontinuidad antes de medirlo. Recorte inicial no destructivo como límites de reproducción; exportar un recorte preciso requiere decodificar/recodificar y merece una entrega posterior. [PlaybackParameters](https://developer.android.com/reference/androidx/media3/common/PlaybackParameters).

Crear estado `RECORDING` y archivo temporal; al finalizar, cerrar/verificar, renombrar y pasar a `READY`. DB y filesystem no comparten transacción: reconciliar operaciones incompletas al iniciar. Para borrar: estado `DELETING`, quitar archivo y finalizar metadata de manera reintentable. Barrido de huérfanos solo dentro de directorios propios, excluyendo capturas activas y con margen temporal; no borrar archivos desconocidos inmediatamente. M4A interrumpido puede ser irrecuperable: mostrar fallo, nunca fingir recuperación.

Compartir mediante FileProvider con permiso temporal; exportar copia con Storage Access Framework. Sin permisos amplios de almacenamiento. Nombre visible separado del nombre físico. Controlar espacio insuficiente, llamadas, pérdida de micrófono y cierre inesperado.

Recomiendo que el grabador continúe al bloquear pantalla mediante servicio foreground de tipo microphone iniciado desde UI visible, con notificación y Stop; los requisitos Android deben verificarse contra targetSdk al implementarlo. Es parte sustantiva del hito, no un detalle oculto. Afinador y reproducción se detienen antes de grabar; metrónomo simultáneo queda posterior para evitar grabar el clic por altavoz.

## 9. Integración con el afinador

Introducir `TunerContext(instrumentId, tuningId, a4)` y selección de cuerda dependiente del catálogo. Mantener modo cromático sin objetivo. Sustituir listas MIDI, etiquetas y cálculo manual con 440 de MainActivity; reutilizar `Note` y `PitchMath`. Propagar A4 también al cálculo interno de nota/cents en TunerEngine y reiniciar su estabilización cuando cambie configuración.

AudioCapture configura actualmente un pasaaltos de 60 Hz pensado para guitarra: revisar ese perfil antes de ofrecer bajo u otros registros. No basta con cambiar etiquetas. Conservar YIN, seguimiento y pruebas actuales; tocar solo los puntos que requieren configuración.

Hoy la captura sigue el STARTED de la Activity: con múltiples destinos también deberá depender de visibilidad del afinador y propiedad del micrófono. Esperar liberación antes de iniciar grabación. Desde Song, resolver contexto una vez; una afinación ausente no cae silenciosamente en EADGBE. Volver conserva canción; cambiar algo en la herramienta no modifica Song salvo acción explícita.

## 10. Dependencias

Agregar por necesidad: Navigation Compose al introducir rutas; Room y su procesador al persistir; Preferences DataStore con preferencias; Media3 ExoPlayer con reproducción. Nada para teoría, diagramas, waveform ni metrónomo. KSP solo como herramienta de compilación si lo requiere la configuración de Room elegida.

Verifiqué los LICENSE reales de [AndroidX](https://raw.githubusercontent.com/androidx/androidx/androidx-main/LICENSE.txt) y [Media3](https://raw.githubusercontent.com/androidx/media/release/LICENSE): Apache-2.0. Es una verificación de repositorios, no todavía del árbol transitivo de versiones seleccionadas. Antes de incorporar, fijar versiones compatibles con Kotlin/AGP actuales, auditar artefactos y transitivas, y preservar LICENSE/NOTICE. Sin extensiones FFmpeg ni código GPL/LGPL. No hace falta actualizar todo el toolchain para esta propuesta.

## 11. Riesgos y validación

Los principales riesgos son pérdida de capturas, competencia por micrófono, restricciones de servicio, edición de anclajes, afinaciones fuera del registro probado, loops/recortes AAC y jitter/latencia dependientes del dispositivo. Cubrirlos con pruebas de recuperación, migración y lifecycle, además de las pruebas musicales puras. Validar navegación/rotación/regreso y permisos en dispositivo; no confundir tests sintéticos con aceptación acústica.

README/RESEARCH aún describen audio solo en memoria, aunque el debug ya exporta WAV/logs a disco. Al implementar el grabador habrá que actualizar esas afirmaciones y el alcance de CONTRIBUTING. Mantener cero cuentas, anuncios, tracking, telemetría de contenido o análisis remoto. Exportar/compartir será siempre una acción explícita.

## 12. Hitos recomendados

| Hito | Entrega y condición de cierre |
| --- | --- |
| M0 — base verificada | Confirmar aceptación física del afinador y ajustar documentación del estado real; conservar suite existente como regresión. |
| M1 — navegación y contexto | Shell funcional, tema extraído, catálogo inicial, A4/nombres configurables, afinador scoped a destino. Afinación estándar y Drop D verificadas sin cambiar detector. |
| M2 — capturar y organizar | Room, sesiones, grabación/pausa/reproducción/seek, marcadores, waveform final, notas, gestión y exportación. Recuperación de estados y política de pantalla bloqueada resueltas. |
| M3 — canciones conectadas | Song/secciones/líneas y acordes estructurados con vocabulario mínimo; metadatos, favoritos/tags, vistas de lectura. Song→Afinador y Song↔Recording ya funcionan. |
| M4 — acordes | Ampliar teoría, diccionario/voicings, favoritos, lateralidad y sheet desde Song. Tests musicales y datos de posiciones verificados. |
| M5 — metrónomo | Motor PCM, controles MVP y Song→Metrónomo con BPM/unidad/compás; aceptación audible en hardware. |
| M6 — práctica | Transposición, velocidad/loop A–B, recorte no destructivo y exportación recortada; entregar estas mejoras de una en una. |
| Futuro | Reconocimiento por diapasón, escucha de acordes, más instrumentos, Guided/Normal/Pro, escenario/autoscroll, backup completo, patrones y progresión de tempo. |

Así las integraciones llegan con cada herramienta; no hay un último hito gigante para unir cinco aplicaciones. El parser mínimo de M3 prepara la experiencia sin obligar a construir primero todo el diccionario.

## 13. Decisiones tomadas

1. **Grabación bloqueada:** se incluye continuidad con pantalla bloqueada en M2. Grabar solo con la app visible reduce alcance, pero limita los ensayos.
2. **MVP de grabador:** captura, gestión, marcadores, waveform y exportación en M2; velocidades, A–B y recorte en M6. Se aplazan, no se descartan.
3. **Convenciones:** 6/8 con BPM en negra con puntillo, y acordes relativos al capo con tonalidad sonora separada.
4. **Base:** la prueba con guitarra real dio una aceptación parcial. No bloquea este análisis; M0 sigue abierto por la variación durante el decay.

No quedan decisiones estructurales abiertas para comenzar M1.
