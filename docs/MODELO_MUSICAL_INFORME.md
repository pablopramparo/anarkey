# Informe: modelo musical de Canciones y camino hacia el pentagrama

Estado: **solo investigación, sin implementar**. Revisado sobre el código a octubre de 2026
(`core/.../music/*`, `app/.../recording/data/*`, base Room v6).

## Resumen

Canciones es hoy una **hoja de acordes con letra** (lead sheet). Cada "marca" (acorde, nota o
silencio) cuelga de un carácter de la letra y el tiempo es **implícito**: sale del orden de lectura y
de la duración de cada marca. Eso alcanza para leer, tocar y transponer, pero **no es una línea de
tiempo musical**: no hay compases, pulsos, voces ni eventos simultáneos. Un pentagrama de una sola
voz se puede *derivar* del modelo actual sin migrar nada; todo lo demás (ligaduras, cambios de
compás, polifonía, piano a dos pentagramas) exige datos nuevos.

## 1. Respuestas a las diez preguntas

**1. ¿Están los acordes, notas y silencios anclados exclusivamente a posiciones de caracteres?**
Sí. `ChordPlacementEntity` guarda `lineId`, `position` (punto de código dentro del texto de la
línea), `orderInPosition`, `originalSymbol` y `figure`. No hay otra forma de ubicar una marca. Las
líneas viven en una sección o sueltas, y se ordenan por `position`. Una línea con texto vacío es
válida, y entonces todas sus marcas caen en la posición 0 y se desempatan por `orderInPosition`.

**2. ¿Tiene cada evento una posición temporal musical propia (compás, pulso, subdivisión)?**
No. El "cuándo" de un evento es la suma de las duraciones de los anteriores en el orden de lectura.
No se guarda ni se valida que las figuras llenen un compás, ni existen barras de compás, anacrusa o
pulso. Dos marcas en la misma posición de carácter no suenan juntas: suenan una después de otra.

**3. ¿Cómo se calculan hoy las duraciones y la secuencia de reproducción?**
`SongPlaybackPlan` (core):
* Orden: líneas sin sección, luego secciones por `position`; dentro de cada línea, marcas por
  (`position`, `orderInPosition`).
* Duración: la figura de la marca en negras (redonda 4, blanca 2, negra 1, corchea ½, semicorchea ¼,
  ×1,5 si lleva puntillo), convertida a segundos con el BPM. En compuestos (6/8, 9/8, 12/8) el BPM
  cuenta negras con puntillo, igual que el metrónomo. Sin figura, un acorde dura **un compás**
  (`barSeconds`). Sin BPM se usa 100; sin compás, 4/4.
* Sonido: `ChordSounding` elige la digitación del instrumento de la canción (rasgueo) o, si no hay,
  una voz de teclado; una nota suena a su altura; un silencio no suena pero ocupa su tiempo.

**4. ¿Se pueden representar varias notas simultáneas?**
No como evento. Un acorde produce varias notas a la vez, pero como *símbolo* del que se deriva una
digitación; no son notas editables. No se puede escribir "nota + acorde" ni dos voces a la vez.

**5. ¿Es posible representar una melodía sin letra?**
En la práctica sí, de forma incómoda: una línea de texto vacío admite marcas, todas en la posición 0.
Suena en orden y se transpone, pero visualmente las marcas se empujan hacia la derecha para no
pisarse; no hay ninguna noción de compás ni de altura en la pantalla. No es una melodía "de verdad".

**6. ¿Admite silencios, ligaduras y cambios de compás dentro de una canción?**
* Silencios: **sí** (`_` con figura, redonda a semicorchea, con puntillo).
* Ligaduras: **no** (ni de prolongación ni de fraseo).
* Cambios de compás o de tempo: **no**. Compás y BPM son un único valor por canción
  (`SongEntity.timeNumerator/timeDenominator/bpm`).
* Tresillos y valores irregulares: no, solo figuras binarias con puntillo. Tampoco repeticiones ni
  casillas de final.

**7. ¿Cómo se relacionan tonalidad, alteraciones, octavas y transposición?**
* La tonalidad es `keyRoot` + `keyMode` de la canción. Se usa para mostrarla y para transponerla;
  **no gobierna la ortografía** de las notas.
* Una nota guarda letra, alteración y octava en su símbolo (`♪C#4`, octava científica: C4 = MIDI 60).
  Los acordes guardan su símbolo tal cual se escribió.
* Transponer mueve cada acorde y nota por semitonos; la ortografía de salida (sostenidos o
  bemoles) sale de la preferencia global de nomenclatura, no de la tonalidad. Una nota que saldría
  de octava 0–8 se deja como estaba. Se registra el desvío acumulado (`transposeOffset`, 0–11)
  para poder volver al original.
* La cejilla desplaza solo las digitaciones de los acordes; las notas son el sonido real.

**8. ¿Qué información falta para renderizar un pentagrama monofónico correctamente?**
Con lo que hay se puede derivar casi todo; falta:
* **Compases:** segmentar la secuencia acumulando figuras contra el compás. Necesita decidir qué
  hacer cuando las figuras no suman (hoy nada lo impide) y una marca de **anacrusa** (primer compás
  incompleto).
* **Ligaduras** y **ligadura de expresión**: un enlace nota-con-nota.
* **Clave** (derivable de la tesitura, mejor guardada) y **armadura** (derivable de `keyRoot`/
  `keyMode`).
* **Reglas de alteraciones** por compás, **plicas, corchetes** y **vigas**: derivables, sin datos
  nuevos.
* **Sílabas:** asociar cada nota a una sílaba. Hoy la letra es texto por línea; el ancla de carácter
  sirve, pero el ritmo y las posiciones de texto son dos órdenes que pueden contradecirse.
* Dirección, dinámica y matices: opcionales, no hacen falta para un primer pentagrama.

**9. ¿Qué cambios harían falta para polifonía o piano a dos pentagramas?**
Un **modelo de eventos con tiempo propio**, separado de la letra:
* Posición temporal absoluta por evento (por ejemplo en *ticks* por negra), no solo orden.
* Varias notas con el mismo inicio (acorde real), y **voz** y **pentagrama** por evento.
* Partes (mano derecha/izquierda o instrumentos), cambios de clave, compás y tempo en el tiempo.
* Ligaduras entre notas de voces distintas, notas cruzadas de pentagrama, adornos.

Lo razonable es una **capa de partitura nueva** (tablas propias) que *convive* con la hoja de
acordes: la letra y los acordes siguen como están y la partitura es opcional por canción.

**10. ¿Qué limitaciones hay hoy para MusicXML?**
* **Exportar** una hoja de acordes de una voz es viable: compases derivados de la secuencia,
  `<harmony>` para los acordes, `<note>` y `<rest>` para notas y silencios, `<lyric>` desde el texto.
  Pierde lo que no existe (ligaduras, voces) y exige asumir compases completos.
* **Importar** es con pérdida: una sola parte y voz, sin polifonía, tresillos, ligaduras ni cambios a
  mitad de pieza; una letra por estrofa. Solo cubre melodías con cifrado.
* En la práctica hay que leer XML (en Android existe `XmlPullParser`, en el módulo `core` JVM no) y
  `.mxl` (zip). Mejor decidirlo cuando exista el modelo de partitura.

## 2. Qué existe, qué se puede sumar sin migrar, qué la exige

| Ya implementado | Se puede agregar sin tocar el modelo | Exige migración |
|---|---|---|
| Acordes, notas y silencios anclados a la letra | Derivar compases de la secuencia y avisar si no suman | Anacrusa, barras de compás explícitas |
| Figuras (redonda a semicorchea, puntillo) | Pentagrama **de solo lectura** de una voz, desde la secuencia | Ligaduras |
| Reproducción secuencial, un compás por acorde sin figura | Clave y armadura derivadas de la tonalidad | Compás y tempo que cambian en la canción |
| Transposición con vuelta al original | Exportar MusicXML monofónico (lead sheet) | Notas simultáneas, voces, pentagramas |
| ChordPro y formato nativo JSON | Resaltar el compás que suena | Tiempo absoluto por evento |
| | | Tresillos, repeticiones, casillas |
| | | Importar MusicXML con fidelidad |

## 3. Recomendación y orden

1. **Prototipo de lectura** (sin migración): dibujar un pentagrama monofónico *derivado* de la
   secuencia actual. Es la forma barata de descubrir qué datos faltan de verdad, y de ver cuántas
   canciones tienen figuras que no suman.
2. **Datos aditivos** (migración simple, sin tocar nada existente): `measure` explícito/anacrusa y
   `tieToNext` por marca, columnas opcionales con valor por defecto.
3. **Capa de partitura** (tablas nuevas): eventos con tiempo absoluto, voces y pentagramas. Las
   marcas actuales se pueden convertir de forma determinista a una voz de esa capa.
4. **MusicXML** al final, cuando la capa exista: exportar primero, importar después y con límites
   declarados.

Riesgo principal: **dos órdenes que pueden contradecirse**. La letra manda hoy con sus posiciones y
un pentagrama manda con su tiempo; mientras las marcas dependan del texto, mover una palabra mueve
el ritmo. Conviene decidir antes de la capa de partitura si la letra pasa a colgar de las notas
(sílabas) en lugar de al revés.

## 4. Lo que **no** se hizo

No se modificó el modelo de datos para esta investigación, no se implementó pentagrama y no se
añadió MusicXML. La importación y exportación implementadas (ChordPro y formato nativo) están
descritas en [SONG_FILE_FORMAT.md](SONG_FILE_FORMAT.md).
