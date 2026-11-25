package uz.pultanuvchi;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * OCR matnidan banknota qiymatini aniqlash mantiqi (Android'ga bog'liq emas).
 *
 * Uch xil dalil ishlatiladi:
 *  1) raqam: banknotada bosilgan "50000" yoki "100" kabi yozuv;
 *  2) so'z: "ELLIK MING SO'M" yoki "FIVE DOLLARS" kabi yozuv;
 *  3) valyuta yozuvi: bank nomi va boshqa doimiy yozuvlar (Banknotes dagi CurrencyInfo.identity).
 *
 * So'zlar "skelet" shaklga keltiriladi, shuning uchun hozirgi alifbo (O', G', SH, CH,
 * turli apostroflar) ham, yangi alifbo (o-umlaut, g-breve, s-cedilla, c-cedilla) ham
 * bir xil qolipga tushadi. OCR xatolariga qarshi taxminiy (fuzzy) solishtirish ishlatiladi.
 */
public final class BanknoteMatcher {

    static final double NUMBER_ONE = 2.5;
    static final double NUMBER_MANY = 4.0;
    static final double WORD_SCORE = 5.0;
    static final double HEADER_SCORE = 1.5;

    /** OCR ko'pincha adashtiradigan o'xshash harflar guruhlari. */
    private static final String[] CONFUSABLE = {"IL", "OQD", "UV", "EF", "CG"};

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PLAIN = Pattern.compile("^\\d+$");
    private static final Pattern GROUPED = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");

    private static final double EPS = 1e-9;

    private BanknoteMatcher() {
    }

    // ------------------------------------------------------------------ skelet

    /**
     * Matnni katta ASCII harflardan iborat "skelet"ga aylantiradi:
     * apostroflar tashlanadi, yangi alifbo harflari ikki harfli shaklga qaytariladi
     * (s-cedilla -> SH, c-cedilla -> CH, g-breve -> G, o-umlaut -> O), diakritiklar olib tashlanadi.
     * Harf bilan aralash so'zdagi raqamlar o'xshash harfga almashtiriladi (0->O, 1->I, 5->S, 8->B, 2->Z);
     * sof raqamli so'zlar tashlab yuboriladi.
     */
    public static String skeleton(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String s = Normalizer.normalize(input, Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (String token : WHITESPACE.split(s)) {
            if (token.isEmpty()) {
                continue;
            }
            boolean hasLetter = false;
            for (int i = 0; i < token.length(); i++) {
                if (Character.isLetter(token.charAt(i))) {
                    hasLetter = true;
                    break;
                }
            }
            if (!hasLetter) {
                continue;
            }
            appendSkeleton(out, token);
        }
        return out.toString();
    }

    private static void appendSkeleton(StringBuilder sb, String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            switch (c) {
                case '\u015E': // S-cedilla
                case '\u0160': // S-caron
                case '\u0218': // S-comma
                    sb.append("SH");
                    break;
                case '\u00C7': // C-cedilla
                case '\u010C': // C-caron
                    sb.append("CH");
                    break;
                case '\u011E': // G-breve
                    sb.append('G');
                    break;
                case '\u00D6': // O-umlaut
                case '\u014C': // O-macron
                case '\u00D4':
                case '\u00D2':
                case '\u00D3':
                case '\u00D5':
                    sb.append('O');
                    break;
                case '\u00DC': // U-umlaut
                case '\u00DB':
                case '\u00DA':
                case '\u00D9':
                case '\u016A':
                    sb.append('U');
                    break;
                case '\u0130': // I with dot
                    sb.append('I');
                    break;
                case '\u00D1': // N-tilde
                    sb.append("NG");
                    break;
                case '0':
                    sb.append('O');
                    break;
                case '1':
                case '|':
                case '!':
                    sb.append('I');
                    break;
                case '5':
                    sb.append('S');
                    break;
                case '8':
                    sb.append('B');
                    break;
                case '2':
                    sb.append('Z');
                    break;
                default:
                    if (c >= 'A' && c <= 'Z') {
                        sb.append(c);
                    } else if (c > 127) {
                        String d = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
                        for (int k = 0; k < d.length(); k++) {
                            char e = d.charAt(k);
                            if (e >= 'A' && e <= 'Z') {
                                sb.append(e);
                            }
                        }
                    }
                    break;
            }
        }
    }

    // ------------------------------------------------------- taxminiy solishtirish

    static double substitutionCost(char a, char b) {
        if (a == b) {
            return 0.0;
        }
        for (String group : CONFUSABLE) {
            if (group.indexOf(a) >= 0 && group.indexOf(b) >= 0) {
                return 0.35;
            }
        }
        return 1.0;
    }

    /** Qolip matnning istalgan bo'lagiga eng kamida qancha "xato" bilan o'xshashligi. */
    static double fuzzyDistance(String text, String pattern) {
        int m = pattern.length();
        if (m == 0) {
            return 0.0;
        }
        double[] prev = new double[m + 1];
        double[] cur = new double[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        double best = prev[m];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            cur[0] = 0.0;
            for (int j = 1; j <= m; j++) {
                double v = prev[j - 1] + substitutionCost(pattern.charAt(j - 1), c);
                double del = prev[j] + 1.0;
                double ins = cur[j - 1] + 1.0;
                if (del < v) {
                    v = del;
                }
                if (ins < v) {
                    v = ins;
                }
                cur[j] = v;
            }
            if (cur[m] < best) {
                best = cur[m];
            }
            double[] t = prev;
            prev = cur;
            cur = t;
        }
        return best;
    }

    /**
     * Qolip uzunligiga qarab ruxsat etilgan "xato" miqdori.
     * Qisqa qoliplarda faqat o'xshash harflar almashuviga (0.35) ruxsat beriladi:
     * aks holda "ONMING" kabi qolip tasodifiy matnga yopishib qoladi.
     */
    static double maxDistance(int len) {
        if (len >= 15) {
            return 3.0;
        }
        if (len >= 11) {
            return 2.0;
        }
        if (len >= 8) {
            return 1.0;
        }
        if (len >= 6) {
            return 0.8;
        }
        return 0.0;
    }

    /** 0..1: qolip matnda qanchalik aniq topildi (0 - topilmadi). */
    static double quality(String text, String pattern) {
        return quality(text, pattern, Double.MAX_VALUE);
    }

    /** Xuddi shu, lekin ruxsat etilgan xato miqdori yana cap bilan cheklanadi. */
    static double quality(String text, String pattern, double cap) {
        double limit = Math.min(maxDistance(pattern.length()), cap);
        double d = fuzzyDistance(text, pattern);
        if (d > limit + EPS) {
            return 0.0;
        }
        return 1.0 - d / pattern.length();
    }

    // ------------------------------------------------------------------ raqamlar

    private static boolean isSeparator(char c) {
        return c == '.' || c == ',' || c == '\'' || c == '`' || c == '\u00B4'
                || c == '\u2018' || c == '\u2019' || c == '\u02BB' || c == '\u02BC';
    }

    /** So'z raqamga o'xshasa, uni raqamlar (va ajratuvchi nuqta) ko'rinishiga keltiradi; aks holda null. */
    static String digitize(String token) {
        boolean hasDigit = false;
        StringBuilder sb = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
                sb.append(c);
            } else if (c == 'O' || c == 'o' || c == 'Q' || c == 'D') {
                sb.append('0');
            } else if (c == 'I' || c == 'l' || c == '|' || c == 'i') {
                sb.append('1');
            } else if (c == 'S' || c == 's') {
                sb.append('5');
            } else if (c == 'B') {
                sb.append('8');
            } else if (c == 'Z' || c == 'z') {
                sb.append('2');
            } else if (isSeparator(c)) {
                sb.append('.');
            } else {
                return null;
            }
        }
        return hasDigit ? sb.toString() : null;
    }

    private static String cleanNumber(String t) {
        if (PLAIN.matcher(t).matches()) {
            return t;
        }
        if (GROUPED.matcher(t).matches()) {
            return t.replace(".", "");
        }
        return null;
    }

    private static String stripEdges(String s) {
        int a = 0;
        int b = s.length();
        while (a < b && !Character.isLetterOrDigit(s.charAt(a))) {
            a++;
        }
        while (b > a && !Character.isLetterOrDigit(s.charAt(b - 1))) {
            b--;
        }
        return s.substring(a, b);
    }

    /**
     * Satrdagi sonlarni topadi. "10 000" kabi uch xonali guruhlar bo'lingan sonlar birlashtiriladi;
     * faqat aynan uch xonali guruhlar qo'shiladi (nol tushib qolgan "10 00" qiymatga aylanmaydi).
     */
    static List<String> numbersIn(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) {
            return out;
        }
        String[] raw = WHITESPACE.split(line.trim());
        int n = raw.length;
        String[] clean = new String[n];
        boolean[] plain = new boolean[n];
        for (int i = 0; i < n; i++) {
            String tok = stripEdges(raw[i]);
            if (tok.isEmpty()) {
                continue;
            }
            String dg = digitize(tok);
            if (dg == null) {
                continue;
            }
            String cl = cleanNumber(dg);
            if (cl == null) {
                continue;
            }
            clean[i] = cl;
            plain[i] = PLAIN.matcher(dg).matches();
        }
        int i = 0;
        while (i < n) {
            if (clean[i] == null) {
                i++;
                continue;
            }
            StringBuilder cur = new StringBuilder(clean[i]);
            int j = i + 1;
            if (plain[i] && clean[i].length() <= 3) {
                while (j < n && clean[j] != null && plain[j] && clean[j].length() == 3) {
                    cur.append(clean[j]);
                    j++;
                }
            }
            if (j > i + 1 && !Banknotes.isNumeral(cur.toString())) {
                // "100 100" kabi ikki alohida raqam birlashib ketmasin: birinchisini o'zini olamiz
                cur = new StringBuilder(clean[i]);
                j = i + 1;
            }
            out.add(cur.toString());
            i = j;
        }
        return out;
    }

    // -------------------------------------------------------------------- tahlil

    /** Bitta kadrdagi OCR satrlaridan dalillarni to'playdi. */
    public static FrameEvidence analyze(List<String> lines) {
        int n = Banknotes.ALL.size();
        int m = Banknotes.CURRENCIES.size();
        double[] numberScores = new double[n];
        double[] wordScores = new double[n];
        double[] currencyScores = new double[m];
        if (lines == null || lines.isEmpty()) {
            return new FrameEvidence(numberScores, wordScores, currencyScores, false);
        }

        Map<String, Integer> counts = new HashMap<>();
        StringBuilder flat = new StringBuilder();
        List<String> skeletons = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            for (String num : numbersIn(line)) {
                Integer c = counts.get(num);
                counts.put(num, c == null ? 1 : c + 1);
            }
            String sk = skeleton(line);
            if (!sk.isEmpty()) {
                skeletons.add(sk);
            }
            flat.append(sk);
        }
        for (int i = 0; i < n; i++) {
            Integer c = counts.get(Banknotes.ALL.get(i).numeral);
            if (c != null) {
                numberScores[i] = c >= 2 ? NUMBER_MANY : NUMBER_ONE;
            }
        }

        String text = flat.toString();
        for (int i = 0; i < n; i++) {
            double best = 0.0;
            List<String> phrases = Banknotes.ALL.get(i).phrases;
            for (int k = 0; k < phrases.size(); k++) {
                String p = phrases.get(k);
                double cap = Banknotes.phraseCap(i, k);
                double q;
                if (cap == Double.MAX_VALUE) {
                    q = quality(text, p);
                } else {
                    // Boshqa yozuvni ichiga olgan yozuv ("IKI YUZ MANAT") satrlar chegarasida tasodifan
                    // hosil bo'lmasligi uchun har bir satrning o'zida alohida qidiriladi.
                    q = 0.0;
                    for (String line : skeletons) {
                        double ql = quality(line, p, cap);
                        if (ql > q) {
                            q = ql;
                        }
                    }
                }
                if (q > best) {
                    best = q;
                }
            }
            wordScores[i] = WORD_SCORE * best;
        }
        suppressContained(wordScores);

        for (int c = 0; c < m; c++) {
            for (String p : Banknotes.CURRENCIES.get(c).identity) {
                if (quality(text, p) > 0.0) {
                    currencyScores[c] = HEADER_SCORE;
                    break;
                }
            }
        }
        return new FrameEvidence(numberScores, wordScores, currencyScores, true);
    }

    /**
     * Yozuvi boshqa banknotaning yozuvi ichida to'liq bor banknotalarni ("YUZ MANAT" - "IKI YUZ MANAT" ichida)
     * bekor qiladi, lekin faqat uzunroq yozuv xatosiz (aniq) topilgan bo'lsa. Aks holda 200 manat 100 manat bilan
     * teng ball olib, noaniq bo'lib qolardi. Uzunroq yozuv faqat taxminan topilgan bo'lsa (masalan, oldingi satrning
     * oxiri "IKI" ga o'xshab qolgan bo'lsa), hech narsa bekor qilinmaydi: natijani raqamlar yoki keyingi kadrlar
     * hal qiladi, ular hal qila olmasa, ilova noaniq deb qoladi, lekin 100 manatni 200 manat deb ishonch bilan aytmaydi.
     */
    static void suppressContained(double[] wordScores) {
        for (int i = 0; i < wordScores.length; i++) {
            if (wordScores[i] <= 0.0) {
                continue;
            }
            for (int j : Banknotes.dominatedBy(i)) {
                if (wordScores[j] >= WORD_SCORE - EPS) {
                    wordScores[i] = 0.0;
                    break;
                }
            }
        }
    }

    /** Bitta rasm (galereyadan) uchun qaror: ko'p kadr kutilmaydi. */
    public static Decision decideStill(FrameEvidence ev) {
        int n = Banknotes.ALL.size();
        int best = -1;
        double bestS = 0.0;
        double second = 0.0;
        double bestNum = 0.0;
        for (int i = 0; i < n; i++) {
            Banknote note = Banknotes.ALL.get(i);
            double id = ev.currencyScores[note.currency.index];
            double nu = ev.numberScores[i];
            if (id <= 0.0 && Banknotes.numberNeedsIdentity(i)) {
                nu = 0.0; // valyuta yozuvisiz bunday raqam hech narsani isbotlamaydi
            }
            double base = nu + ev.wordScores[i];
            double s = base > 0.0 ? base + id : 0.0;
            if (s > bestS) {
                second = bestS;
                bestS = s;
                best = i;
                bestNum = nu;
            } else if (s > second) {
                second = s;
            }
        }
        if (best < 0 || bestS < 4.0) {
            return null;
        }
        if (bestS < second * 1.3 || bestS - second < 1.5) {
            return null;
        }
        CurrencyInfo currency = Banknotes.ALL.get(best).currency;
        double id = ev.currencyScores[currency.index];
        boolean confirmed = !currency.strict || id > 0.0;
        boolean high = confirmed
                && (ev.wordScores[best] >= 2.5 || (bestNum >= NUMBER_MANY && id > 0.0));
        return new Decision(best, high);
    }
}
