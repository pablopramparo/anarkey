# M5.1 — Rediseño UX del metrónomo

## Distribución

La pantalla principal concentra BPM y botones +/- en el centro, slider y Tap Tempo en la misma tarjeta, el patrón de pulsos inmediatamente debajo y un selector compacto de compás en una fila. El botón Iniciar/Detener queda fijo en la parte inferior del destino, sobre la navegación global. El sonido actualmente elegido se identifica en el acceso al panel; sonido, volumen, mantener pantalla encendida y reinicio/leyenda de acentos viven en una hoja inferior desplazable.

El indicador de pulsos también sirve para configurar el patrón: cada toque recorre pulso normal → principal → secundario → normal. Los estados usan relleno violeta, borde violeta y borde neutro respectivamente; el pulso que está sonando recibe un borde claro adicional. En 6/8 un separador marca la agrupación 3+3 y el BPM sigue indicando negra con puntillo. Cada pulso expone su número, acento actual y siguiente estado a lectores de pantalla.

## Datos y reproducción

Los patrones se guardan por compás en el DataStore de preferencias existente; si falta un patrón guardado, se conserva el acento predeterminado anterior. Cambiar de compás muestra/edita su patrón propio. La acción del panel permite restablecer el patrón del compás actual al valor predeterminado.

El diseño no cambia AudioTrack, el planificador de muestras ni el renderizado PCM. Las ediciones del patrón llaman al mismo mecanismo de actualización de ajustes, que entrega el cambio al siguiente pulso programado. No se agregaron dependencias ni se alteró Canciones, Grabador, Acordes o afinador.

## Validación

Se añadieron pruebas al modelo de pulsos para verificar acentos personalizados por cada pulso y conservación de patrones separados por compás. `:core:test` pasó con 1151 tests (uno omitido), `:app:testDebugUnitTest` con 11; lint y build pasaron. Lint no reporta problemas en los archivos de M5.1.

El APK se instaló en el Samsung SM-G998B. A fuente normal, toda la pantalla principal cabe sin desplazamiento; el árbol de UI tampoco declara un contenedor desplazable para ese destino. Verifiqué apertura del panel, compás 6/8 con separación 3+3, la unidad BPM, edición directa de un pulso, persistencia al cambiar de compás y reproducción con pulso activo/botón Detener visibles. En una comprobación breve, el indicador pasó del pulso 1 al 3 mientras seguía sonando. El sonido salió por AudioTrack. Restauré los ajustes de tempo/compás del dispositivo a 100 BPM y 4/4, y el patrón 6/8 a los acentos por defecto.

Las pruebas visuales en dispositivo cubrieron el tamaño de fuente normal y la orientación vertical. El comportamiento a fuente muy ampliada y en horizontal no tuvo una pasada manual independiente; la fila de seis pulsos usa controles de 44 dp y descripciones accesibles, pero esas configuraciones aún requieren aceptación visual en hardware.

## Cierre visual M5.1

En la revisión final, el contorno blanco del pulso activo quedó desacoplado del color del número: el borde solo indica reproducción y el texto conserva el contraste definido por su acentuación (normal, principal o secundario), tanto detenido como activo. La unidad se presenta de forma compacta como `♩ = BPM` en 2/4, 3/4 y 4/4, y `♩. = BPM` en 6/8; la descripción de accesibilidad sigue explicando negras o negras con puntillo por minuto. En el Samsung comprobé un pulso normal activo con número legible y la unidad con puntillo en 6/8. No se modificaron audio, planificación ni semántica del tempo.
