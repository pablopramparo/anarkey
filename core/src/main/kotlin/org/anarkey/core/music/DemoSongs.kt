package org.anarkey.core.music

/** One lyric line with its chords written inline, ChordPro style: `[Am]Alas, my [C]love`. */
object ChordPro {
    data class Anchor(val position: Int, val symbol: String)
    data class Line(val text: String, val chords: List<Anchor>)

    /** Positions are code points into the returned text, like every stored chord anchor. */
    fun parse(source: String): Line {
        val text = StringBuilder()
        val anchors = mutableListOf<Anchor>()
        var count = 0
        var index = 0
        while (index < source.length) {
            if (source[index] == '[') {
                val end = source.indexOf(']', index)
                require(end > index) { "unclosed_chord_bracket" }
                anchors += Anchor(count, source.substring(index + 1, end).trim())
                index = end + 1
            } else {
                val codePoint = source.codePointAt(index)
                text.appendCodePoint(codePoint)
                count++
                index += Character.charCount(codePoint)
            }
        }
        return Line(text.toString(), anchors)
    }
}

data class DemoSection(val kind: String, val lines: List<String>)
data class DemoSong(
    val title: String, val artist: String, val keyRoot: String, val keyMode: String,
    val bpm: Int, val numerator: Int, val denominator: Int, val notes: String, val sections: List<DemoSection>,
)

/** The demo as an exchange song, so demos and imported files share one import path. */
fun DemoSong.toExchange(): org.anarkey.core.exchange.ExchangeSong = org.anarkey.core.exchange.ExchangeSong(
    title = title, artist = artist, keyRoot = keyRoot, keyMode = keyMode, bpm = bpm, timeNumerator = numerator,
    timeDenominator = denominator, instrumentId = "guitar", tuningId = "guitar.standard", notes = notes,
    sections = sections.map { section ->
        org.anarkey.core.exchange.ExchangeSection(section.kind, "", lines = section.lines.map { source ->
            val line = ChordPro.parse(source)
            org.anarkey.core.exchange.ExchangeLine(line.text, line.chords.map { org.anarkey.core.exchange.ExchangeMark(it.position, it.symbol) })
        })
    },
)

/** Public-domain songs (traditional or whose authors died long ago), written by hand with simple chords. */
object DemoSongs {
    val all: List<DemoSong> = listOf(
        DemoSong("Amazing Grace", "Traditional", "G", "major", 84, 3, 4, "Public domain.", listOf(
            DemoSection("verse", listOf(
                "[G]Amazing [G7]grace! How [C]sweet the [G]sound",
                "That [G]saved a [Em]wretch like [D]me!",
                "I [G]once was [G7]lost, but [C]now am [G]found;",
                "Was [G]blind, but [D]now I [G]see.")),
            DemoSection("verse", listOf(
                "'Twas [G]grace that [G7]taught my [C]heart to [G]fear,",
                "And [G]grace my [Em]fears re[D]lieved;",
                "How [G]precious [G7]did that [C]grace ap[G]pear",
                "The [G]hour I [D]first be[G]lieved.")),
            DemoSection("verse", listOf(
                "Through [G]many [G7]dangers, [C]toils and [G]snares,",
                "I [G]have al[Em]ready [D]come;",
                "'Tis [G]grace hath [G7]brought me [C]safe thus [G]far,",
                "And [G]grace will [D]lead me [G]home.")),
        )),
        DemoSong("Greensleeves", "Traditional", "A", "minor", 66, 6, 8, "Public domain.", listOf(
            DemoSection("verse", listOf(
                "[Am]Alas, my [C]love, you [G]do me [Em]wrong,",
                "To [Am]cast me [C]off dis[E]courteously;",
                "For [C]I have [G]loved you [Em]well and [Am]long,",
                "De[Am]lighting [E]in your [Am]company.")),
            DemoSection("chorus", listOf(
                "[C]Greensleeves was [G]all my joy,",
                "[Am]Greensleeves was [E]my delight,",
                "[C]Greensleeves was [G]my heart of gold,",
                "And [Am]who but my [E]lady [Am]Greensleeves.")),
            DemoSection("verse", listOf(
                "Your [Am]vows you've [C]broken, [G]like my [Em]heart,",
                "Oh, [Am]why did [C]you so [E]enrapture [Am]me?",
                "Now [C]I re[G]main in a [Em]world a[Am]part",
                "But [Am]my heart [E]remains in cap[Am]tivity.")),
        )),
        DemoSong("Oh! Susanna", "Stephen Foster", "C", "major", 104, 2, 4, "Public domain.", listOf(
            DemoSection("verse", listOf(
                "[C]I come from Ala[G7]bama with my [C]banjo on my [G7]knee,",
                "I'm [C]going to Louis[G7]iana, my [C]true love for to [G7]see.",
                "It [C]rained all night the [G7]day I left, the [C]weather it was [G7]dry,",
                "The [C]sun so hot I [G7]froze to death, Su[C]sanna, don't you [G7]cry.")),
            DemoSection("chorus", listOf(
                "Oh! [C]Susanna, [F]oh don't you [C]cry for [G7]me,",
                "For I [C]come from Ala[G7]bama",
                "With my [G7]banjo on my [C]knee.")),
        )),
        DemoSong("When the Saints Go Marching In", "Traditional", "C", "major", 112, 4, 4, "Public domain.", listOf(
            DemoSection("verse", listOf(
                "Oh, when the [C]saints go marching in,",
                "Oh, when the saints go [G7]marching in,",
                "Oh, I want to be in that [C]number,",
                "When the [G7]saints go marching [C]in.")),
            DemoSection("verse", listOf(
                "Oh, when the [C]trumpet sounds its call,",
                "Oh, when the trumpet [G7]sounds its call,",
                "Oh Lord, I want to be in that [C]number,",
                "When the [G7]trumpet sounds its [C]call.")),
        )),
        DemoSong("Scarborough Fair", "Traditional", "A", "minor", 92, 3, 4, "Public domain.", listOf(
            DemoSection("verse", listOf(
                "[Am]Are you going to [G]Scarborough [Am]Fair?",
                "[Am]Parsley, [C]sage, rose[G]mary and [Am]thyme.",
                "Re[Am]member me to [C]one who [G]lives [Am]there,",
                "She [C]once was a [G]true love of [Am]mine.")),
            DemoSection("verse", listOf(
                "[Am]Tell her to [G]make me a [Am]cambric shirt,",
                "[Am]Parsley, [C]sage, rose[G]mary and [Am]thyme,",
                "With[Am]out no [C]seams nor [G]needle[Am]work,",
                "Then [C]she'll be a [G]true love of [Am]mine.")),
        )),
        DemoSong("Arroz con leche", "Tradicional", "C", "major", 100, 2, 4, "Dominio público.", listOf(
            DemoSection("verse", listOf(
                "[C]Arroz con [G7]leche, me [C]quiero ca[G7]sar",
                "con [C]una seño[G7]rita de San Ni[C]colás.",
                "Que [C]sepa co[G7]ser, que [C]sepa bor[G7]dar,",
                "que [C]sepa abrir la [G7]puerta para [C]ir a [G7]jugar.")),
            DemoSection("chorus", listOf(
                "Con [C]esta sí, con [G7]esta no,",
                "con [C]esta seño[G7]rita me [C]caso yo.")),
        )),
    )
}
