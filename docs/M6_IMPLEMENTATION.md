# M6 — Práctica

## M6.1 — Transposición de canciones

Desde el detalle de una canción, **Transponer** abre una vista previa con un intervalo
de −11 a +11 semitonos. Muestra la tonalidad y cada símbolo reconocido antes/después.
Cancelar no escribe cambios; **Guardar transposición** actualiza acordes y tonalidad
en una sola transacción Room. El modo mayor/menor se conserva.

Se desplazan raíz y bajo de inversiones, manteniendo calidad y extensión. La escritura
usa la preferencia global de sostenidos/bemoles y la vista respeta letras/solfeo.
Los símbolos desconocidos se enumeran explícitamente y se conservan sin cambios.
Un intervalo cero conserva el texto original y no habilita guardar.

Las formas escritas son relativas al capo: el capo permanece fijo y la tonalidad
sonora se desplaza por el mismo intervalo. Las grabaciones vinculadas conservan su
audio original. No se cambian letras, secciones, IDs, anclajes, orden de acordes,
instrumento, afinación, BPM ni vínculos. No requiere migración ni dependencias nuevas.

La vista previa no ofrece reproducción transpuesta ni deshacer editorial exacto:
aplicar el intervalo inverso recupera las alturas, pero puede normalizar enarmonías
y la notación original según la preferencia actual.

## Verificación

Las pruebas del núcleo cubren inversiones, notación latina, calidades/extensiones,
símbolos desconocidos, intervalo cero y transposición inversa en todos los intervalos.
Las pruebas instrumentadas verifican persistencia de raíz/bajo, conservación de
anclajes Unicode, letra, capo y metadatos, símbolos desconocidos, canciones sin
tonalidad y rechazo de intervalos inválidos sin escrituras.

Verificación del 8 de octubre de 2026: `:core:test` (1.154 casos, uno omitido),
`:app:testDebugUnitTest` (11 casos), `:app:connectedDebugAndroidTest` (15 casos en
Samsung SM-G998B / Android 15), `:app:lintDebug` y `:app:assembleDebug` completaron
con `BUILD SUCCESSFUL`, sin fallos de pruebas. La revisión visual/manual del diálogo,
incluidos fuente grande y TalkBack, queda pendiente; las pruebas instrumentadas
validan el repositorio, no la interacción Compose.

## Resto de M6

Pendientes, para entregar de a una según el roadmap: velocidad de reproducción,
loop A–B, recorte no destructivo y exportación recortada.
