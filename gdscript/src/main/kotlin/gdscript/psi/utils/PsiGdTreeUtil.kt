package gdscript.psi.utils

import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile


object Ansi {
    const val RESET = "\u001B[0m"

    const val BLACK = "\u001B[30m"
    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val MAGENTA = "\u001B[35m"
    const val CYAN = "\u001B[36m"
    const val WHITE = "\u001B[37m"

    const val BG_BLACK = "\u001B[40m"
    const val BG_RED = "\u001B[41m"
    const val BG_GREEN = "\u001B[42m"
    const val BG_YELLOW = "\u001B[43m"
    const val BG_BLUE = "\u001B[44m"
    const val BG_MAGENTA = "\u001B[45m"
    const val BG_CYAN = "\u001B[46m"
    const val BG_WHITE = "\u001B[47m"

    const val BRIGHT_BLACK = "\u001B[90m"
    const val BRIGHT_RED = "\u001B[91m"
    const val BRIGHT_GREEN = "\u001B[92m"
    const val BRIGHT_YELLOW = "\u001B[93m"
    const val BRIGHT_BLUE = "\u001B[94m"
    const val BRIGHT_MAGENTA = "\u001B[95m"
    const val BRIGHT_CYAN = "\u001B[96m"
    const val BRIGHT_WHITE = "\u001B[97m"

    const val BRIGHT_BG_BLACK = "\u001B[100m"
    const val BRIGHT_BG_RED = "\u001B[101m"
    const val BRIGHT_BG_GREEN = "\u001B[102m"
    const val BRIGHT_BG_YELLOW = "\u001B[103m"
    const val BRIGHT_BG_BLUE = "\u001B[104m"
    const val BRIGHT_BG_MAGENTA = "\u001B[105m"
    const val BRIGHT_BG_CYAN = "\u001B[106m"
    const val BRIGHT_BG_WHITE = "\u001B[107m"


    const val BOLD = "\u001B[1m"
    const val ITALIC = "\u001B[3m"
    const val UNDERLINE = "\u001B[4m"

    const val RED_BOLD = RED + BOLD
    const val YELLOW_UNDERLINE = YELLOW + UNDERLINE
    const val YELLOW_BOLD = YELLOW + BOLD
    const val YELLOW_ITALIC = YELLOW + ITALIC


    const val UNICODE_FULLWIDTH_NUMBERS = '０'
    const val UNICODE_CIRCLED_NUMBERS = '⓪'
    const val UNICODE_BLACK_CIRCLED_NUMBERS = '⓿'
    const val UNICODE_SUBSCRIPT_NUMBERS = '₀'
    const val UNICODE_SUPERSCRIPT_NUMBERS = '⁰'
}

object AnsiHelper {
    fun color(text: String, code: String): String {
        return "$code$text${Ansi.RESET}"
    }

    fun colorizeByPatterns(text: String, rules: Map<Regex, String>, bg: String = ""): String {
        if (rules.isEmpty()) return text

        var result = text
        val matches = mutableListOf<Pair<IntRange, String>>()

        for ((regex, color) in rules) {
            regex.findAll(text).forEach { match ->
                matches.add(match.range to color)
            }
        }

        matches.sortByDescending { it.first.first }

        for ((range, color) in matches) {
            val before = result.take(range.first)
            val matchText = result.substring(range)
            val after = result.substring(range.last + 1)
            result = before + color + bg + matchText + Ansi.RESET + bg + after
        }

        return result
    }

    fun shiftUnicode(text: String, offsetChar: Char, referenceChar: Char): String {
        val offset = offsetChar.code - referenceChar.code
        val builder = StringBuilder()

        for (char in text) {
            val shifted = char.code + offset
            val newChar = try {
                shifted.toChar()
            } catch (_: Exception) {
                char
            }

            builder.append(newChar)
        }

        return builder.toString()
    }


    fun String.toSubscriptNumbers(): String {
        return shiftUnicode(this, Ansi.UNICODE_SUBSCRIPT_NUMBERS, '0')
    }

    fun String.toSuperScriptNumbers(): String {
        return shiftUnicode(this, Ansi.UNICODE_SUPERSCRIPT_NUMBERS, '0')
    }

    fun String.toCircledNumbers(): String {
        return shiftUnicode(this, Ansi.UNICODE_CIRCLED_NUMBERS, '0')
    }

    fun String.toFullWidthNumbers(): String {
        return shiftUnicode(this, Ansi.UNICODE_FULLWIDTH_NUMBERS, '0')
    }

    fun String.toBlackCircledNumbers(): String {
        return shiftUnicode(this, Ansi.UNICODE_BLACK_CIRCLED_NUMBERS, '0')
    }
}

object PsiGdTreeUtil {
    fun findFirstPrecedingElement(element: PsiElement, withSelf: Boolean = true, condition: Condition<in PsiElement?>): PsiElement? {
        var el: PsiElement? = element
        if (!withSelf) {
            el = el?.prevSibling ?: el?.parent
        }

        while (el != null) {
            if (el is PsiFile) break // avoid directory traversal
            if (condition.value(el)) {
                return el
            }

            el = el.prevSibling ?: el.parent
        }

        return null
    }
}


object StringHelper {
    private fun formatHexString(
        value: ULong,
        digits: Int,
        prefix: String,
        postfix: String,
        uppercase: Boolean,
        padToTypeWidth: Boolean
    ): String {
        val hex = value.toString(16)
        val normalized = if (uppercase) hex.uppercase() else hex.lowercase()
        val formatted = if (padToTypeWidth) normalized.padStart(digits, '0') else normalized
        return prefix + formatted + postfix
    }

    fun Int.toHexString(
        prefix: String = "0x",
        postfix: String = "",
        uppercase: Boolean = true,
        padToTypeWidth: Boolean = true
    ): String {
        return formatHexString(
            value = this.toUInt().toULong(),
            digits = Int.SIZE_BYTES * 2,
            prefix = prefix,
            postfix = postfix,
            uppercase = uppercase,
            padToTypeWidth = padToTypeWidth
        )
    }

    fun Long.toHexString(
        prefix: String = "0x",
        postfix: String = "",
        uppercase: Boolean = true,
        padToTypeWidth: Boolean = true
    ): String {
        return formatHexString(
            value = this.toULong(),
            digits = Long.SIZE_BYTES * 2,
            prefix = prefix,
            postfix = postfix,
            uppercase = uppercase,
            padToTypeWidth = padToTypeWidth
        )
    }

    fun PsiElement.objToHexString(prefix: String = "0x", postfix: String = ""): String {
        return this.hashCode().toHexString(prefix = prefix, postfix = postfix)
    }
}