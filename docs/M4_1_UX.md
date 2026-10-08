# M4.1 — UX de Acordes

## Alcance

M4.1 agrega dos presentaciones del diccionario y del panel contextual de acordes, manteniendo el mismo parser, teoría, validador y catálogo de digitaciones de M4. La preferencia **Principiante / Avanzado** se guarda en el DataStore local y su valor inicial es Principiante. No se agregan tablas ni se modifican acordes persistidos.

## Principiante

- Prioriza el nombre del acorde y los diagramas disponibles; usa guitarra estándar cuando no hay instrumento indicado.
- Ordena las formas para mostrar primero las que no tienen cejilla, requieren menos dedos, tienen trastes más bajos y menos cuerdas silenciadas. Conserva todas las formas compatibles y no crea formas nuevas.
- Explica X, O, puntos, dedos y cejilla, y enumera las cuerdas para tocar y evitar.
- Oculta notas, intervalos y bajo alternativo hasta que la persona los consulte. Los filtros de fundamental, calidad y extensión empiezan plegados para reservar espacio.
- Si no hay forma verificada, informa esa condición y conserva disponible el acorde teórico.

## Avanzado

Conserva notas, intervalos, inversión, selección de afinación, favoritas y todas las variantes del catálogo con prioridad de consulta rápida. La preferencia de notación global de M1 se reutiliza en ambos modos y en el panel de Canciones.

## Orientación

La opción «Orientación de diagramas: diestro / zurdo» invierte la lectura visual del diagrama. No determina ni presupone la orientación física del instrumento de la persona.

## Persistencia y verificación

La preferencia de modo usa la clave `chord_presentation_mode` en el DataStore existente. No cambia `originalSymbol`, las posiciones de anclaje, afinaciones, datos de grabación ni el motor DSP. Tests cubren el valor inicial/persistencia y que el orden Principiante conserva todas las formas existentes.

