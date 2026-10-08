# M4.2 — Implementación UX de Acordes

## Resultado

La pantalla Acordes ahora presenta un acorde a la vez. El selector principal muestra el nombre completo como rótulo principal y el símbolo conforme a la preferencia global como dato secundario. Principiante queda como modo inicial; ambos modos reutilizan el mismo catálogo, motor, validación y favoritos. Avanzado conserva el orden y las variantes disponibles, con teoría bajo demanda.

El selector es un bottom sheet con búsqueda directa por símbolo, selección estructurada de fundamental/calidad y confirmación explícita. La búsqueda usa `ChordSymbolParser`; acepta tanto símbolos anglosajones como latinos y respeta la preferencia global al mostrar el resultado. Un símbolo no interpretable permanece como texto original. El selector no cambia la consulta actual hasta confirmar.

El diagrama usa menos altura y conserva todos sus elementos visibles; la tarjeta tiene navegación anterior/siguiente, indicador de posición y favorito. En Principiante, la guía de dedos y la leyenda permanecen plegadas, y se indica qué cuerdas tocar y evitar. Los controles conservan scroll/reflujo para contenido que no cabe. Se acortó el rótulo de afinación en los controles para evitar repetir el nombre del instrumento.

La vista contextual de Canciones reutiliza la tarjeta compacta con acorde, instrumento, afinación y capo existentes; no cambia el acorde almacenado ni sus anclajes. El componente anterior del diagrama solo recibió ajustes de presentación (altura y escala gráfica); no se modificó la lógica musical.

## Verificación

- `:core:test`: 1.142 tests, 0 fallos, 1 omitido.
- `:app:testDebugUnitTest`: 11 tests, 0 fallos.
- `:app:connectedDebugAndroidTest`: 12 tests en Samsung SM-G998B / Android 15, 0 fallos.
- `:app:lintDebug` y `:app:assembleDebug`: completados.
- Instalación directa del APK debug en el Samsung.
- Revisión manual en pantalla 1080 × 2400: pantalla inicial de Acordes, búsqueda `Rem` y confirmación (resuelve el acorde menor de Re; el rótulo usa la preferencia global actual), búsqueda anglosajona `Cmaj7`, avance a la posición 2 de 2 de Do mayor y cambio a Avanzado. Se comprobó que el modo elegido permanece al cerrar y volver a abrir la app. La digitación común cabe sin desplazamiento vertical a escala de fuente normal.

Capturas completas, con las explicaciones desplegadas: [Principiante](screenshots/M4_2/beginner_expanded.png) y [Avanzado](screenshots/M4_2/advanced_expanded.png). Capturas adicionales de la prueba manual: [selector](screenshots/M4_2/chord_picker.png), [búsqueda latina](screenshots/M4_2/search_latin.png), [búsqueda anglosajona](screenshots/M4_2/search_anglo.png), [resultado](screenshots/M4_2/latin_result.png) y [posición siguiente](screenshots/M4_2/next_voicing.png).

## Limitaciones y deuda

- La biblioteca de Canciones del dispositivo estaba vacía, por lo que no se pudo abrir una canción real para comprobar manualmente la hoja contextual con capo, afinación y anclajes. La integración visual quedó conectada y la suite instrumentada pasó.
- La revisión visual usó tamaño de fuente normal. Falta una pasada manual con fuente grande y TalkBack; el contenido permite scroll y los controles tienen descripciones, pero esos casos no se validaron en dispositivo en esta entrega.
- No se agregaron pruebas automatizadas específicas de Compose para el borrador del modal o la navegación entre tarjetas. La búsqueda se validó manualmente en el Samsung; las pruebas existentes del núcleo cubren el parser y el formateador de ambas notaciones.
- La selección de bajo alternativo por chips se conserva en el selector Avanzado; la búsqueda directa también admite inversiones. Las calidades siguen limitadas exactamente a las que reconoce el catálogo actual.

M4.2 queda terminado. M5 no se inició.

## Ajustes de cierre M4.2

Se normalizaron los rótulos de afinación para mostrar el instrumento y una afinación breve una sola vez, también en el contexto de Canciones. El selector ahora se mide según su contenido hasta un máximo adaptable y desplaza el contenido si el teclado reduce el espacio disponible. En la tarjeta queda un solo contador de posición; las flechas laterales se mantienen y siguen deshabilitadas en los extremos o cuando hay una sola forma.

El diagrama incorpora referencias permanentes discretas para la cejuela y los números de cuerda en ambos extremos, respetando la orientación diestro/zurdo. Se mantuvo la altura compacta del dibujo y se redujeron separaciones redundantes.

Se volvieron a ejecutar `:core:test`, `:app:testDebugUnitTest`, `:app:connectedDebugAndroidTest`, `:app:lintDebug` y `:app:assembleDebug`: todos aprobaron; 12 pruebas instrumentadas corrieron en SM-G998B / Android 15. Se instaló el APK final y se verificaron la tarjeta, el selector adaptado y la entrada de texto con el teclado visible. Capturas: [Acordes final](screenshots/M4_2/final_dictionary.png), [selector adaptado](screenshots/M4_2/final_picker.png) y [selector con teclado](screenshots/M4_2/final_picker_ime.png).
