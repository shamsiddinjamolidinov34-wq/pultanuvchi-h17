package uz.pultanuvchi;

/**
 * Ilovaning o'z matnlarini hozirgi alifbodan (o', g', sh, ch) yangi alifboga
 * (o-umlaut, g-breve, s-cedilla, c-cedilla) o'tkazadi.
 * Matnlar manba sifatida hozirgi alifboda yoziladi; "ng" o'zgarmaydi.
 */
public final class Alphabet {
    private static final char TURNED_COMMA = '\u02BB';

    private Alphabet() {
    }

    public static String toNew(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            char next = (i + 1 < s.length()) ? s.charAt(i + 1) : '\0';
            if ((c == 'O' || c == 'o') && next == TURNED_COMMA) {
                sb.append(c == 'O' ? '\u00D6' : '\u00F6');
                i += 2;
            } else if ((c == 'G' || c == 'g') && next == TURNED_COMMA) {
                sb.append(c == 'G' ? '\u011E' : '\u011F');
                i += 2;
            } else if ((c == 'S' || c == 's') && (next == 'h' || next == 'H')) {
                sb.append(c == 'S' ? '\u015E' : '\u015F');
                i += 2;
            } else if ((c == 'C' || c == 'c') && (next == 'h' || next == 'H')) {
                sb.append(c == 'C' ? '\u00C7' : '\u00E7');
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }
}
