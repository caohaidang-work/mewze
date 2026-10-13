package com.caohaidang.edward.music.utils

object Romanizer {
    private val INI = arrayOf("g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h")
    private val MED = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe",
        "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    )

    private class Fin(val end: String, val left: String, val moved: String)

    private val FIN = arrayOf(
        Fin("", "", ""), Fin("k", "", "g"), Fin("k", "", "kk"), Fin("k", "k", "s"), Fin("n", "", "n"),
        Fin("n", "n", "j"), Fin("n", "n", ""), Fin("t", "", "d"), Fin("l", "", "r"), Fin("k", "l", "g"),
        Fin("m", "l", "m"), Fin("l", "l", "b"), Fin("l", "l", "s"), Fin("l", "l", "t"), Fin("p", "l", "p"),
        Fin("l", "l", ""), Fin("m", "", "m"), Fin("p", "", "b"), Fin("p", "p", "s"), Fin("t", "", "s"),
        Fin("t", "", "ss"), Fin("ng", "", ""), Fin("t", "", "j"), Fin("t", "", "ch"), Fin("k", "", "k"),
        Fin("t", "", "t"), Fin("p", "", "p"), Fin("t", "", "")
    )

    private fun isSyllable(c: Char) = c in '가'..'힣'

    fun romanizeKorean(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (!isSyllable(text[i])) {
                out.append(text[i]); i++; continue
            }
            var j = i
            while (j < text.length && isSyllable(text[j])) j++
            val codes = (i until j).map { text[it].code - 0xAC00 }
            val ini = codes.map { INI[it / 588] }.toTypedArray()
            val med = codes.map { MED[(it % 588) / 28] }
            val finIdx = codes.map { it % 28 }
            val iniIdx = codes.map { it / 588 }
            val medIdx = codes.map { (it % 588) / 28 }
            val fin = finIdx.map { FIN[it].end }.toTypedArray()

            for (k in 0 until codes.size - 1) {
                val f = finIdx[k]
                if (f == 0) continue
                val ni = iniIdx[k + 1]
                val info = FIN[f]

                if (f == 27 || f == 6 || f == 15) {
                    val asp = when (ni) { 0 -> "k"; 3 -> "t"; 12 -> "ch"; else -> null }
                    if (asp != null) { ini[k + 1] = asp; fin[k] = info.left; continue }
                    if (ni == 11) { fin[k] = info.left; ini[k + 1] = ""; continue }
                }

                if (ni == 11 && f != 21) {
                    fin[k] = info.left
                    ini[k + 1] = when {
                        medIdx[k + 1] == 20 && f == 7 -> "j"
                        medIdx[k + 1] == 20 && f == 25 -> "ch"
                        else -> info.moved
                    }
                    continue
                }
                val end = info.end
                val isStop = end == "k" || end == "t" || end == "p"

                if (isStop && ni == 18) { ini[k + 1] = end; fin[k] = info.left; continue }

                val nasal = when (end) { "k" -> "ng"; "t" -> "n"; "p" -> "m"; else -> null }
                if (nasal != null && (ni == 2 || ni == 6)) { fin[k] = nasal; continue }
                if (nasal != null && ni == 5) { ini[k + 1] = "n"; fin[k] = nasal; continue }
                if ((end == "m" || end == "ng") && ni == 5) { ini[k + 1] = "n"; continue }

                if (f == 8 && ni == 2) { ini[k + 1] = "l"; continue }
                if (f == 4 && ni == 5) { fin[k] = "l"; ini[k + 1] = "l"; continue }
                if (fin[k] == "l" && ni == 5) ini[k + 1] = "l"
            }
            for (k in codes.indices) out.append(ini[k]).append(med[k]).append(fin[k])
            i = j
        }
        return out.toString()
    }

    private val KANA = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'ゐ' to "i", 'ゑ' to "e", 'を' to "o",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
        'ゔ' to "vu"
    )

    private val YOON_BASE = mapOf(
        'き' to "ky", 'ぎ' to "gy", 'し' to "sh", 'じ' to "j", 'ち' to "ch", 'ぢ' to "j", 'に' to "ny",
        'ひ' to "hy", 'び' to "by", 'ぴ' to "py", 'み' to "my", 'り' to "ry", 'ゔ' to "v"
    )
    private val YOON_VOWEL = mapOf('ゃ' to 'a', 'ゅ' to 'u', 'ょ' to 'o')

    private fun toHiragana(c: Char): Char =
        if (c in 'ァ'..'ヶ') (c.code - 0x60).toChar() else c

    private fun isKana(c: Char) = c in '぀'..'ヿ'

    fun romanizeJapanese(text: String): String? {
        if (text.any { it in '一'..'鿿' }) return null
        if (text.none { isKana(it) }) return null

        val chars = text.map(::toHiragana)
        val sb = StringBuilder()
        var doubleNext = false
        var i = 0
        fun emit(s: String) {
            if (doubleNext && s.isNotEmpty() && s[0].isLetter() && s[0] !in "aiueon") {
                sb.append(if (s.startsWith("ch")) "t" else s[0].toString())
            }
            doubleNext = false
            sb.append(s)
        }
        while (i < chars.size) {
            val c = chars[i]
            when {
                c == 'っ' -> { doubleNext = true; i++ }
                c == 'ん' -> {
                    val next = chars.getOrNull(i + 1)
                    val nextRoman = next?.let { KANA[it] }

                    emit(if (nextRoman != null && (nextRoman[0] in "aiueoy")) "n'" else "n")
                    i++
                }
                c == 'ー' -> {
                    sb.lastOrNull { it in "aiueo" }?.let { sb.append(it) }
                    i++
                }
                else -> {
                    val next = chars.getOrNull(i + 1)
                    val yoon = if (next != null) YOON_VOWEL[next] else null
                    val base = YOON_BASE[c]
                    if (base != null && yoon != null) {
                        emit(base + yoon)
                        i += 2
                    } else {
                        val roman = KANA[c]
                        if (roman != null) emit(roman) else { doubleNext = false; sb.append(chars[i]) }
                        i++
                    }
                }
            }
        }
        return sb.toString()
    }

    fun romanize(text: String, korean: Boolean): String? {
        val result = if (korean) romanizeKorean(text) else romanizeJapanese(text)
        return result?.trim()?.takeIf { it.isNotEmpty() }
    }
}
