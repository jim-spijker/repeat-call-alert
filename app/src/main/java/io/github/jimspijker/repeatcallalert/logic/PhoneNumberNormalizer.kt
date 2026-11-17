package io.github.jimspijker.repeatcallalert.logic

/**
 * Turns an incoming phone number into a comparison key.
 *
 * Deliberately conservative: if a number cannot be recognised reliably (empty, withheld, SIP address,
 * letters, too short or too long), [normalize] returns `null` and no match is ever claimed.
 *
 * Rules:
 *  - formatting characters (space, -, ., /, parentheses) are removed; post-dial digits after ',' or ';' are ignored
 *  - a leading "00" becomes "+"
 *  - with a known country calling code, a national number with trunk prefix "0" becomes "+<cc><rest>"
 *  - without a usable country code the key stays national ("nat:<digits>"), so the same notation still
 *    matches but no international form is guessed
 */
object PhoneNumberNormalizer {

    private const val MIN_DIGITS = 3
    private const val MAX_DIGITS = 15 // E.164 maximum

    fun normalize(raw: String?, defaultCountryCallingCode: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        val cut = s.indexOfFirst { it == ',' || it == ';' }
        if (cut >= 0) s = s.substring(0, cut)
        s = s.filterNot { it == ' ' || it == '-' || it == '.' || it == '/' || it == '(' || it == ')' || it == ' ' }
        if (s.isEmpty()) return null
        if (s.count { it == '+' } > 1) return null
        if (s.contains('+') && !s.startsWith("+")) return null
        val body = s.removePrefix("+")
        if (body.isEmpty() || !body.all { it in '0'..'9' }) return null

        val cc = defaultCountryCallingCode?.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } }

        val key = when {
            s.startsWith("+") -> "+$body"
            body.startsWith("00") -> "+" + body.substring(2)
            body.startsWith("0") && cc != null -> "+$cc" + body.substring(1)
            else -> "nat:$body"
        }
        val digits = key.count { it in '0'..'9' }
        if (digits < MIN_DIGITS || digits > MAX_DIGITS) return null
        if (key == "+" || key.startsWith("+0")) return null
        return key
    }

    /** Country calling code (without '+') for an ISO 3166-1 alpha-2 code, or `null` when unknown. */
    fun callingCodeForIso(iso: String?): String? = when (iso?.lowercase()) {
        "nl" -> "31"; "be" -> "32"; "fr" -> "33"; "es" -> "34"; "pt" -> "351"; "lu" -> "352"
        "ie" -> "353"; "it" -> "39"; "ch" -> "41"; "at" -> "43"; "gb", "uk" -> "44"; "dk" -> "45"
        "se" -> "46"; "no" -> "47"; "pl" -> "48"; "de" -> "49"; "cz" -> "420"; "fi" -> "358"
        "gr" -> "30"; "tr" -> "90"; "us", "ca" -> "1"; "au" -> "61"; "nz" -> "64"; "in" -> "91"
        "za" -> "27"; "br" -> "55"; "mx" -> "52"; "jp" -> "81"; "sg" -> "65"
        else -> null // Unknown or numbering plans where the trunk prefix differs (e.g. Italy keeps the 0): don't guess.
    }

    /** Masks a number for the activity log: only the last 3 digits stay visible. */
    fun mask(raw: String?): String {
        val digits = raw?.filter { it in '0'..'9' }.orEmpty()
        return if (digits.length <= 3) "…" else "…" + digits.takeLast(3)
    }
}
