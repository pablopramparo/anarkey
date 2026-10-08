# M5 — Metrónomo

## Implementación

El metrónomo sintetiza clics PCM mono a 48 kHz/16 bits y los transmite mediante `AudioTrack` en `MODE_STREAM`, con modo de baja latencia y un hilo dedicado de prioridad de audio. `SamplePulsePlanner` acumula la fracción de muestras entre pulsos, así que el reloj no depende de temporizadores de Compose ni deriva por redondeos acumulados. Los cambios de parámetros se recogen al programar el siguiente pulso. El generador reutiliza buffers y produce tres timbres locales (madera, digital y campana), con ganancia y tono según acento.

La UI observa el playback head de AudioTrack y compara sus frames reproducidos con los frames de los pulsos programados; no anima ni determina el audio. En 2/4, 3/4 y 4/4 hay un clic por negra. En 6/8 se mantienen seis clics de corchea por compás, agrupados 3+3; cada BPM cuenta negras con puntillo y la unidad se muestra al lado del control.

La preferencia del metrónomo se guarda en un DataStore separado e incluye tempo, compás, acentos, sonido, volumen y pantalla encendida. La opción de pantalla activa solo opera mientras reproduce. Detener o abandonar la pantalla detiene y libera AudioTrack. Mientras suena, un servicio de primer plano (`MetronomeService`, tipo `mediaPlayback`) mantiene el proceso vivo con una notificación que permite detenerlo, para que el sonido no se corte al apagar la pantalla o cambiar de aplicación. Motivo: con la regla anterior, que lo detenía al salir de primer plano, el metrónomo dejaba de servir al apagar la pantalla.

Desde Canciones, el nuevo acceso contextual pasa BPM y compás como argumentos de navegación. Esos valores inicializan una copia local de pantalla y nunca se guardan en las preferencias globales del metrónomo. Si el BPM de una canción está fuera de 30–300 se limita al rango y se informa; los compases que no admite M5 dejan intacto el compás guardado y muestran una nota. Atrás vuelve al detalle de la canción.

Tap Tempo usa la mediana de hasta cinco intervalos, descarta intervalos improbables (200–2000 ms), descarta valores atípicos fuera de ±50% de la mediana cuando ya hay suficientes muestras y reinicia la serie tras una pausa larga.

## Verificación automatizada

Las pruebas de `:core` cubren los cuatro compases, acentos principales/secundarios, muestras fraccionarias sin deriva a largo plazo, actualización de tempo/compás, formas de onda/timbres/volumen, indicador por frame reproducido y mediana/outliers/reinicio de Tap Tempo. También se ejecuta la suite unitaria de la app como regresión.

## Decisiones y limitaciones

- Se usa playback head como reloj visual. La precisión audible la determina el stream PCM; la ruta real y su latencia siguen dependiendo del dispositivo y de la salida elegida.
- M5 funciona en primer plano. No agrega notificación, servicio ni reproducción con pantalla bloqueada.
- El sistema puede mezclar o atenuar el audio por políticas de audio/interrupciones del dispositivo; no se promete sincronía externa para Bluetooth.
- En 6/8 los seis indicadores muestran subdivisiones de corchea, con acentos por defecto en la primera y cuarta corchea. No hay patrones editables ni subdivisiones configurables.
- Se permiten cambios de BPM durante reproducción; se aplican al próximo límite de pulso programado y el cambio de compás reinicia la posición del compás.

## Prueba física

Dispositivo: Samsung SM-G998B (Android 15). Instalación e inicio correctos. Confirmé `AudioTrack` con eventos `started`/`stopped`, pulsos visibles avanzando en 6/8, unidad BPM en negra con puntillo, Tap Tempo actualizando el tempo y ajuste de BPM durante reproducción (84→86). Desactivé el acento principal y comprobé su estado visual mientras el flujo seguía activo; al detener, el sistema registró `stopped`. Restauré las preferencias de prueba a 100 BPM, 4/4, ambos acentos activos y madera. El recorrido Song→Metrónomo no se probó manualmente en la base del dispositivo.

## Resultado de checks

`:core:test` pasó con 1150 tests (uno omitido por estar marcado `@Ignore`). `:app:testDebugUnitTest` pasó con 11 tests. `:app:lintDebug` y `:app:assembleDebug` pasaron. Lint conserva advertencias de dependencias desactualizadas y parámetros Modifier en pantallas anteriores; no reportó advertencias en los archivos nuevos de M5. El APK final se instaló y abrió en el Samsung.
