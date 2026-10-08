# Catálogo de digitaciones — ampliación de guitarra estándar

## Cobertura

El selector y las pruebas de cobertura comparten `ChordQuality`: 19 familias por
12 fundamentales, **228 combinaciones con al menos una posición en guitarra
estándar**, tanto en notación anglosajona como latina y con sostenidos o bemoles.

Familias: mayor, menor, 7, maj7, m7, dim, aug, sus2, sus4, dim7, m7b5, 6, m6,
add9, madd9, 9, 11, 13 y 5. Esta cobertura no incluye todas las inversiones.

Se conservan las formas anteriores y sus IDs para favoritos. Se añaden posiciones
abiertas y 24 plantillas cerradas que se trasladan por semitonos, conservando
dedos, cejilla y cuerdas silenciadas. Solo se transportan formas sin cuerdas al
aire. No se genera un diagrama mediante búsqueda combinatoria ni se aplican formas
de guitarra estándar a otras afinaciones.

El catálogo resultante contiene **330 posiciones**, de las cuales **317 son de
guitarra estándar**. Ukelele y afinaciones alternativas conservan su cobertura
anterior y requieren una ampliación específica. No se agregan dependencias,
archivos descargados, permisos ni cambios en Room.

## Otros instrumentos (ukelele, mandolina, banjo, bajo)

Se completaron con formas **generadas por un script** (`tools/chords/generate_voicings.py`), que escribe
`OtherInstrumentVoicingData.kt`. No se edita a mano: se cambia el script y se vuelve a ejecutar. Esto reemplaza
la regla anterior de "no generar diagramas": para cuatro y cinco cuerdas hay pocas combinaciones y es más
fiable validarlas con reglas fijas que escribirlas a mano.

Cada forma debe usar solo notas del acorde, incluir sus intervalos esenciales, caber en una mano (4 trastes
de apertura y máximo 4 dedos), usar cuerdas al aire solo en las primeras posiciones y llevar cejilla solo
cuando tres o más cuerdas comparten el traste más bajo. Se guardan hasta dos posiciones por acorde, separadas
al menos tres trastes, ordenadas por menos dedos y menor traste.

- **Ukelele** (Sol agudo y Sol grave, que comparten formas): 437 posiciones por afinación; solo faltan A#5 y B5.
- **Mandolina** (G D A E): 425 posiciones; falta G#5.
- **Banjo** (Sol abierto, quinta cuerda primero): 416 posiciones. La quinta cuerda queda al aire cuando es nota
  del acorde y silenciada si no.
- **Bajo** (E A D G): 342 posiciones. Exigen la fundamental como nota más grave y cuerdas contiguas, así que
  algunos acordes extendidos (por ejemplo los 13) no tienen forma.
- **Violín**: se mantiene solo con afinador; no hay diagramas de acordes.

Se conservan las formas curadas anteriores del ukelele y se agregaron Em (0432) y E7 (1202). Las pruebas
validan cada posición contra las notas de su afinación. Como en la guitarra, no hay certificación física de
comodidad. El bajo no se probó con un instrumento real: el afinador se verificó con señales sintéticas.

## Selección y digitación

F7 incluye `131211`, con cejilla en el primer traste, una forma basada en La y
`xx3545` sin cejilla. Las posiciones se ordenan por traste máximo; Principiante
prioriza además ausencia de cejilla y cantidad de dedos distintos.

La consulta agrupa las posiciones por las notas de la fórmula, filtra instrumento
y afinación, y valida el bajo real cuando se pide una inversión. Los acordes
extendidos pueden omitir notas opcionales: la forma de 11 conserva fundamental,
tercera, séptima menor, novena y oncena; la de 13 conserva fundamental, tercera,
séptima menor y trecena. No se promete que todas las notas teóricas suenen en cada
posición de guitarra.

Cuando falta una posición, la UI explica que aún no está en el catálogo para el
acorde, instrumento, afinación y bajo elegidos. No lo presenta como un acorde
inexistente ni como un error de afinación del usuario.

## Procedencia y límites

Datos de posiciones codificados localmente y comprobados con las notas derivadas
de la afinación. Se contrastó la posición convencional de F7 con la explicación
de [Fender](https://www.fender.com/articles/chords/learn-how-to-play-the-f7-chord).
No se incorporaron bases de datos, ilustraciones, código ni textos de terceros.

Las pruebas de dedos verifican asignación por cuerda, dedos compartidos a un único
traste con cejilla, ausencia de notas por debajo de la cejilla en su recorrido,
extensión máxima de tres trastes y que la forma quepa en el diagrama. Estas
comprobaciones no equivalen a una certificación física de comodidad para todas
las manos; no se hizo una ejecución instrumental de las 330 posiciones.

## Pruebas

`ChordCatalogueCoverageTest` comprueba la cobertura del selector, equivalencias
de notación, F7 y sus notas exactas, geometría de dedos/cejillas, filtro de bajo,
afinaciones y favoritos anteriores. `ChordTheoryTest` valida cada posición
empaquetada contra las notas y los intervalos requeridos por su fórmula.

La suite completa `:core:test` pasó con 1.158 casos (uno omitido preexistente),
`:app:testDebugUnitTest` con 11 casos, y `:app:lintDebug` / `:app:assembleDebug`
completaron correctamente. Lint conserva advertencias anteriores del proyecto.

En el Samsung SM-G998B se seleccionó F + séptima dominante desde el selector
estructurado, se abrió el resultado F7 y se recorrieron sus tres posiciones.
La revisión detectó que la cejilla se dibujaba repetidamente sobre los números:
se corrigió para pintarla una sola vez, debajo de todos los dedos. En posiciones
altas el encabezado indica el traste inicial en lugar de llamar cejuela al borde.
La instalación se realiza con `adb install -r`, sobre la app existente.

Tras la corrección visual volvieron a pasar las pruebas unitarias de app, lint y
build. Se instaló el APK final y se verificaron los números de cejilla y el rótulo
del traste alto: [F7 sin cejilla](screenshots/chord-catalogue/f7.png),
[F7 en primer traste](screenshots/chord-catalogue/f7-barre.png),
[F7 en octavo traste](screenshots/chord-catalogue/f7-high.png).
El Samsung queda con la app instalada y la posición de F7 en primer traste abierta.
