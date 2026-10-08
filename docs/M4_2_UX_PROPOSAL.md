# Propuesta M4.2 — Rediseño de Acordes

**Estado: propuesta implementada en M4.2.** Resultados y deuda: [M4_2_IMPLEMENTATION.md](M4_2_IMPLEMENTATION.md). M5 permanece fuera de alcance.

## Hallazgos sobre M4.1

- [ChordScreens.kt](../app/src/main/java/org/anarkey/app/chords/ChordScreens.kt) implementa la pantalla del diccionario, el selector escrito permanente, los filtros plegables, la hoja y el contenido reutilizado por ambas. `ChordLookupContent` presenta teoría y controles antes de las digitaciones, y apila todas las tarjetas verticalmente; el diagrama del `Canvas` mide 190 dp de alto.
- El estado de símbolo/filtros vive en `ChordDictionaryScreen`; instrumento, afinación, lateralidad y expansión de teoría viven en `ChordLookupContent`. Cambiar el modo puede reinicializar instrumento y afinación, y la selección de forma no se mantiene porque todas se muestran juntas.
- [ChordTheory.kt](../core/src/main/kotlin/org/anarkey/core/music/ChordTheory.kt) ya provee `ChordTheory`, `ChordVoicingCatalog.forChord`, `ChordVoicingValidator`, digitaciones curadas y el orden de Principiante. M4.2 debe seguir consumiendo estos mismos resultados, sin ampliar ni reconstruir el catálogo.
- [SongScreens.kt](../app/src/main/java/org/anarkey/app/songs/SongScreens.kt) abre `ChordLookupSheet` con el símbolo y el instrumento, afinación y capo de la canción. La hoja hoy contiene prácticamente la misma pantalla extensa que el diccionario.
- [ChordFavoriteViewModel.kt](../app/src/main/java/org/anarkey/app/chords/ChordFavoriteViewModel.kt) y Room conservan favoritos por ID de digitación. [PreferencesRepository.kt](../app/src/main/java/org/anarkey/app/settings/PreferencesRepository.kt) persiste el modo y usa la preferencia global de notación. Ambas capacidades pueden reutilizarse sin cambios de esquema.

## Estructura principal propuesta

Se conserva el marco oscuro/lavanda, la barra global de Anarkey y el acceso al modo; se reorganiza el contenido bajo un encabezado compacto:

1. **Acordes** y control de modo compacto en una sola línea. El modo no tendrá una fila de dos chips grandes.
2. **Selector del acorde actual** como botón destacado, con nombre comprensible y símbolo formateado según la preferencia global. Ejemplo: “Do menor séptima” y, en segundo plano, “Dom7”. Tocar el botón abre el selector; desaparece el campo de texto permanente.
3. **Instrumento · afinación** en un control compacto. El diccionario comienza con guitarra estándar; el panel de Canciones conserva el instrumento, afinación y capo de esa canción.
4. **Una tarjeta de digitación**, visible inmediatamente en un teléfono corriente: diagrama adaptable, favorito, indicador “Posición 1 de N” y flechas anterior/siguiente.
5. En Principiante, una línea útil como “Toca 5–1 · evita 6”, seguida por “Cómo colocar los dedos” plegado y una ayuda breve para interpretar el diagrama.
6. En Avanzado, accesos compactos a **Notas**, **Intervalos**, **Fórmula** y **Bajo alternativo** debajo del diagrama; sus detalles se expanden bajo demanda.

Se quitan del flujo normal las explicaciones de validación y el aviso sobre comodidad física. La pantalla sigue permitiendo desplazamiento con fuente grande, acorde complejo o contenido avanzado; el objetivo de no hacer scroll corresponde al caso común en un teléfono normal, no a costa de legibilidad o accesibilidad.

## Selector modal de acordes

Un bottom sheet titulado **Elegir acorde** mantiene su propio borrador temporal:

- Fundamental en una cuadrícula o filas compactas de 12 alturas, nombradas según letras/solfeo y sostenidos/bemoles configurados. La identidad de selección es la altura musical, no el texto dibujado.
- En Principiante, accesos visibles a **Mayor**, **Menor** y **Séptima dominante (7)**; “Más tipos” revela las calidades que ya resuelve el motor.
- En Avanzado, todas las calidades ya soportadas están disponibles sin desplegar grupos. La búsqueda por símbolo permanece opcional y acepta ambas notaciones.
- Para acordes con bajo alternativo, Avanzado puede revelar una selección de bajo opcional; una búsqueda como `Am/C` también se resuelve con el parser existente. No se agregará una calidad ni una digitación por este selector.
- **Ver acorde** confirma el borrador. Cancelar o descartar el sheet conserva exactamente el acorde que ya se estaba mostrando. La búsqueda de un símbolo desconocido conserva el texto original y muestra el estado desconocido existente, sin convertirlo ni inventar teoría.

El modal no cambia la pantalla por cada toque. Hasta confirmar no se modifica el acorde actual ni, en el contexto de Canciones, `originalSymbol` o sus anclajes. La selección confirmada en el diccionario cambia el acorde consultado en memoria; no escribe datos musicales en Room.

## Navegación de digitaciones y estado

La pantalla consulta una sola vez las formas compatibles con acorde, instrumento y afinación mediante `ChordVoicingCatalog.forChord`. Principiante puede aplicar `prioritizeForBeginner`; Avanzado conserva el orden curado del catálogo. Ambos presentan las mismas formas válidas.

El botón anterior/siguiente cambia la forma activa sin apilar otras tarjetas. Se muestra “Posición X de N”; las flechas se deshabilitan en los extremos (no hay salto circular). Con una forma se muestra “Posición 1 de 1” y ambas flechas quedan deshabilitadas. El favorito actúa sobre el ID de esa forma.

El estado de consulta conserva acorde, instrumento, afinación, modo y forma activa. Cambiar de modo no reinicia la consulta. Al cambiar acorde o afinación, si el ID activo deja de ser compatible, se selecciona la primera forma disponible; sin resultados, se presenta el estado vacío. No se filtran los resultados por favoritos por defecto ni se ocultan formas alternativas.

## Diagramas y accesibilidad

Se reemplaza el lienzo alto actual por un diagrama de altura moderada y ancho disponible, con geometría relativa al ancho y al número real de cuerdas. Se preservan trastes, marcadores de dedo, X/O, cejilla, número de traste inicial y orientación para diestro/zurdo. La orientación invierte solo la representación.

La guía de Principiante es contextual, no se repite bajo cada forma: “Toca … · evita …”; “Cómo colocar los dedos” lista instrucciones legibles y plegables. La ayuda de X/O, puntos y cejilla puede abrirse desde un icono claramente etiquetado o desde ese encabezado, en una hoja educativa breve. Así, el dibujo explica la mayor parte del uso sin texto técnico persistente.

Riesgos y tratamiento:

- **Fuentes grandes y pantallas pequeñas:** no fijar alturas que recorten contenido; permitir scroll, reflujo de textos y selector de fundamental desplazable. Verificar al menos fuente normal y grande.
- **TalkBack:** etiquetas de acorde y afinación completas; favorito con estado “guardado/no guardado”; flechas anunciadas como posición anterior/siguiente; indicador X de N; no depender solo de lavanda/color o del dibujo.
- **Áreas táctiles:** flechas, favorito, acorde y modo mantienen objetivos accesibles aunque el diagrama sea menor.
- **Nomenclatura:** todas las etiquetas de notas visibles pasan por el formateador compartido de M4; el borrador, el buscador y el bajo alternativo aceptan las dos notaciones.
- **Sin forma compatible:** mantener el nombre y la teoría, explicar que no hay digitación para ese instrumento/afinación y ofrecer cambiar instrumento/afinación. No crear una forma sustituta.

## Avanzado y contenido musical

Avanzado conserva exactamente el mismo acorde seleccionado, afinación, forma y navegación que Principiante. Bajo el diagrama ofrece acceso rápido a notas, intervalos y fórmula; el bajo alternativo se presenta junto con la teoría de inversión. La información se consulta en paneles plegables y no agrega advertencias de implementación. El selector de acorde mantiene acceso inmediato a todas las calidades ya interpretables y a la búsqueda.

## Integración con Canciones

La hoja contextual reutilizará el nuevo componente de visualización con variante **compacta**: nombre actual, instrumento/afinación/capo de la canción, una forma, favorito y navegación por las formas compatibles. No montará el encabezado del diccionario, su selector global, filtros, favoritos solamente ni otra pantalla completa dentro del bottom sheet.

La hoja puede cambiar la forma que se consulta mientras permanece abierta, pero no edita el acorde de la canción. Cambiar el símbolo guardado seguirá siendo una acción explícita del editor de Canciones. Al cerrar se conserva íntegro el contexto de la canción y sus anclajes.

## Reutilización y alcance de implementación

- Reutilizar `ChordTheory`, `ChordSymbolParser`, `ChordSymbolFormatter`, `ChordVoicingCatalog`, `ChordVoicingValidator` y `ChordFavoriteViewModel`.
- Separar un selector modal con estado borrador y un componente común de acorde/diagrama/navegación que admita presentación de página o compacta para Canciones.
- Reutilizar el DataStore existente para modo y notación, y Room para favoritos. **No** requiere dependencias, tablas, migraciones ni cambios en DSP, grabador o datos de canciones.
- Mantener Principiante como modo inicial, la notación global existente, las formas y su validación, los acordes almacenados y los anclajes M3.

## Plan y criterios de aceptación

1. Definir estado de consulta y selección temporal; pruebas unitarias del reducer/modelo para cancelar, confirmar, conservar estado al cambiar modo y restaurar la primera forma compatible al cambiar contexto.
2. Implementar selector modal; probar fundamentales, calidades destacadas, “Más tipos”, búsqueda Anglo/Latina, alteraciones, bajo alternativo, desconocidos y cancelación sin mutación.
3. Implementar tarjeta única y navegación; probar cero/una/múltiples formas, límites anterior/siguiente, favoritos por ID, afinaciones/instrumentos y conservación de catálogo.
4. Adaptar diagrama y accesibilidad; verificar guitarra de 6 cuerdas, ukelele de 4, zurdo/diestro, fuente grande, TalkBack labels y controles táctiles.
5. Integrar variante compacta en Canciones; verificar instrumento, afinación, capo, `originalSymbol` y anclajes sin cambios al abrir, navegar o cerrar.
6. Ejecutar tests, build y lint, instalar en Samsung y revisar ambos modos, selector, desplazamiento y hoja contextual.

Se considera aceptable cuando, con el acorde inicial C en Principiante y guitarra estándar, un Samsung de tamaño normal muestra un diagrama útil al abrir Acordes sin scroll; el acorde no cambia hasta confirmar el selector; modo y navegación preservan su estado; solo aparece una forma a la vez; la hoja de Canciones conserva contexto y anclajes; y ninguna variante nueva de digitación ni cambio musical/DSP aparece en el alcance.
