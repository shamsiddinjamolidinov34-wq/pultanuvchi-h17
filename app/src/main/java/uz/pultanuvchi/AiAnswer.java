package uz.pultanuvchi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI yordami (Google Gemini) uchun Android'ga bog'liq bo'lmagan yordamchilar:
 * modelga beriladigan topshiriq, javobni tahlil qilish va foydalanuvchi kiritgan kalitni tozalash.
 */
public final class AiAnswer {
    private AiAnswer() {
    }

    /** Valyuta kodlari: "UZS|TMT|USD|EUR" (regex uchun). */
    private static final String CODES = codesAlternation();

    /**
     * Rasm bilan birga yuboriladigan topshiriq. U JSON ichiga to'g'ridan-to'g'ri qo'yiladi,
     * shuning uchun unda qo'shtirnoq, teskari qiya chiziq, qator ko'chirish va ASCII bo'lmagan belgilar bo'lmasligi kerak.
     * Banknotalar ro'yxati Banknotes jadvalidan olinadi, shuning uchun yangi valyuta qo'shilsa, bu yerda o'zgartirish shart emas.
     */
    public static final String PROMPT = buildPrompt();

    /** Kalitni tekshirish uchun qisqa matnli so'rov. */
    public static final String PING_PROMPT = "Reply with the single word OK.";

    private static final Pattern NOTE_LINE = Pattern.compile(
            "NOTE *[=:] *([A-Z]{3}) *[:/ \\-]? *([0-9][0-9 .,'_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_THEN_NUMBER = Pattern.compile(
            "\\b(" + CODES + ")\\b\\W{0,3}([0-9][0-9 .,'_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_THEN_CODE = Pattern.compile(
            "([0-9][0-9 .,'_]*)\\W{0,3}\\b(" + CODES + ")\\b", Pattern.CASE_INSENSITIVE);

    private static String codesAlternation() {
        StringBuilder sb = new StringBuilder();
        for (CurrencyInfo c : Banknotes.CURRENCIES) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(c.code);
        }
        return sb.toString();
    }

    private static String buildPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("The photo may show a banknote. Identify its currency and denomination. ");
        sb.append("The only valid banknotes are (currency: denominations): ");
        for (int c = 0; c < Banknotes.CURRENCIES.size(); c++) {
            CurrencyInfo currency = Banknotes.CURRENCIES.get(c);
            if (c > 0) {
                sb.append("; ");
            }
            sb.append(currency.code).append(" (").append(currency.name).append("): ");
            boolean first = true;
            for (Banknote note : Banknotes.ALL) {
                if (note.currency == currency) {
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(note.numeral);
                    first = false;
                }
            }
        }
        sb.append(". Reply with exactly one line in the form NOTE=<CURRENCY>:<VALUE>, for example NOTE=USD:100. ");
        sb.append("Use NOTE=NONE if no such banknote is clearly visible or you are not sure. ");
        sb.append("Do not write anything else.");
        return sb.toString();
    }

    /**
     * Modelning javob matnidan banknotani ajratadi va Banknotes.ALL dagi indeksini qaytaradi.
     * Faqat jadvaldagi haqiqiy banknotalar qabul qilinadi; noaniq, noto'g'ri yoki bir nechta turli
     * banknota bo'lsa -1 qaytadi.
     */
    public static int parseNote(String text) {
        if (text == null) {
            return -1;
        }
        Matcher strict = NOTE_LINE.matcher(text);
        if (strict.find()) {
            return noteIndex(strict.group(1), strict.group(2));
        }
        // Model formatga amal qilmagan bo'lsa: matndagi yagona to'g'ri banknotani olamiz
        // (valyuta kodi va qiymat yonma-yon bo'lishi shart: "USD 100" yoki "100 USD").
        int found = -1;
        Matcher a = CODE_THEN_NUMBER.matcher(text);
        while (a.find()) {
            int idx = noteIndex(a.group(1), a.group(2));
            if (idx >= 0) {
                if (found >= 0 && found != idx) {
                    return -1;
                }
                found = idx;
            }
        }
        Matcher b = NUMBER_THEN_CODE.matcher(text);
        while (b.find()) {
            int idx = noteIndex(b.group(2), b.group(1));
            if (idx >= 0) {
                if (found >= 0 && found != idx) {
                    return -1;
                }
                found = idx;
            }
        }
        return found;
    }

    private static int noteIndex(String code, String rawNumber) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < rawNumber.length(); i++) {
            char c = rawNumber.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        if (digits.length() == 0 || digits.length() > 9) {
            return -1;
        }
        int value = Integer.parseInt(digits.toString());
        return Banknotes.indexOf(code, value);
    }

    /** Kiritilgan yoki qo'yilgan kalitdan bo'shliq, tirnoq va "NOM=" qismini olib tashlaydi. */
    public static String cleanKey(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        int eq = s.lastIndexOf('=');
        if (eq >= 0) {
            s = s.substring(eq + 1);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean quote = c == '"' || c == '\'' || c == '`' || c == (char) 0x2018 || c == (char) 0x2019
                    || c == (char) 0x201C || c == (char) 0x201D;
            if (!quote && !Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Kalit Google API kalitiga o'xshaydimi (qat'iy format emas: faqat uzunlik va belgilar). */
    public static boolean isPlausibleKey(String key) {
        if (key == null || key.length() < 20 || key.length() > 200) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
