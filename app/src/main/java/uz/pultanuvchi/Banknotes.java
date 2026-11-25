package uz.pultanuvchi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ilova taniy oladigan banknotalar. Hammasi lotin yozuvida bosilgan, shuning uchun telefonning
 * lotin yozuvini o'qiydigan matn aniqlagichi (ML Kit) ularni o'qiy oladi.
 *
 * <p>O'zbekiston (CBU ma'lumotiga ko'ra): 1 000, 2 000, 5 000, 10 000, 20 000, 50 000, 100 000, 200 000 so'm.
 * So'z yozuvlari (eski alifboda): BIR MING SO'M, IKKI MING SO'M, BESH MING SO'M, O'N MING SO'M,
 * YIGIRMA MING SO'M, ELLIK MING SO'M, BIR YUZ MING SO'M, IKKI YUZ MING SO'M.
 * Yangi alifboda: O' -> O-umlaut, sh -> s-cedilla, so'm -> so-umlaut-m va hokazo.
 *
 * <p>Turkmaniston (manat): 1, 5, 10, 20, 50, 100 manat va 2025-yilgi 200 manat. Yozuvlari turkman lotin
 * alifbosida (bu yerda harflar diakritiksiz yozilgan): BIR MANAT, BAS MANAT (a-umlaut, s-cedilla), ON MANAT,
 * YIGRIMI MANAT, ELLI MANAT, YUZ MANAT, IKI YUZ MANAT; doimiy yozuv: TURKMENISTANYN MERKEZI BANKY.
 *
 * <p>AQSh dollari: 1, 2, 5, 10, 20, 50, 100. Yozuvlari: ONE DOLLAR ... ONE HUNDRED DOLLARS;
 * doimiy yozuvlar: THE UNITED STATES OF AMERICA, FEDERAL RESERVE NOTE, IN GOD WE TRUST.
 *
 * <p>Yevro: 5, 10, 20, 50, 100, 200, 500. Yevro banknotalarida so'z bilan yozilgan qiymat yo'q;
 * raqam EURO yozuvi yoki ECB qisqartmalari bilan birga hisobga olinadi.
 *
 * <p>Skelet shakli har ikkala alifbo uchun bir xil (qarang: {@link BanknoteMatcher#skeleton}).
 * Ovozda aytiladigan matn (spoken) og'zaki nutqdagidek: 1 000 so'm - "ming", 100 000 so'm - "yuz ming".
 */
public final class Banknotes {
    private Banknotes() {
    }

    // ----------------------------------------------------------------- valyutalar

    public static final CurrencyInfo UZS = new CurrencyInfo(0, "UZS", "Uzbekistan som", "so\u02BBm", false,
            "MARKAZIYBANKI", "RESPUBLIKASI", "QALBAKILASHTIRISH", "TOLOVLARUCHUN", "QIYMATIBOYICHA");

    public static final CurrencyInfo TMT = new CurrencyInfo(1, "TMT", "Turkmenistan manat", "manat", true,
            "TURKMENISTANYNMERKEZIBANKY", "MERKEZIBANKY", "TURKMENISTANYN", "AHLIGORNUSHLERI");

    public static final CurrencyInfo USD = new CurrencyInfo(2, "USD", "US dollar", "dollar", true,
            "UNITEDSTATESOFAMERICA", "FEDERALRESERVENOTE", "INGODWETRUST", "LEGALTENDERFORALLDEBTS");

    public static final CurrencyInfo EUR = new CurrencyInfo(3, "EUR", "euro", "yevro", true,
            "EURO", "BCEECB", "EZBEKT", "EZBEKP");

    public static final List<CurrencyInfo> CURRENCIES = Collections.unmodifiableList(Arrays.asList(UZS, TMT, USD, EUR));

    /** Valyuta birligi (hozirgi alifbo): so'm. */
    public static final String UNIT = UZS.unit;

    // ----------------------------------------------------------------- banknotalar

    public static final List<Banknote> ALL = Collections.unmodifiableList(Arrays.asList(
            // O'zbekiston so'mi (indekslar 0..7)
            new Banknote(UZS, 1000, "ming", "BIRMING"),
            new Banknote(UZS, 2000, "ikki ming", "IKKIMING"),
            new Banknote(UZS, 5000, "besh ming", "BESHMING"),
            new Banknote(UZS, 10000, "o\u02BBn ming", "ONMING"),
            new Banknote(UZS, 20000, "yigirma ming", "YIGIRMAMING"),
            new Banknote(UZS, 50000, "ellik ming", "ELLIKMING"),
            new Banknote(UZS, 100000, "yuz ming", "BIRYUZMING"),
            new Banknote(UZS, 200000, "ikki yuz ming", "IKKIYUZMING"),

            // Turkmaniston manati
            new Banknote(TMT, 1, "bir", "BIRMANAT"),
            new Banknote(TMT, 5, "besh", "BASHMANAT", "BESHMANAT"),
            new Banknote(TMT, 10, "o\u02BBn", "ONMANAT"),
            new Banknote(TMT, 20, "yigirma", "YIGRIMIMANAT"),
            new Banknote(TMT, 50, "ellik", "ELLIMANAT"),
            new Banknote(TMT, 100, "yuz", "YUZMANAT"),
            new Banknote(TMT, 200, "ikki yuz", "IKIYUZMANAT"),

            // AQSh dollari
            new Banknote(USD, 1, "bir", "ONEDOLLAR"),
            new Banknote(USD, 2, "ikki", "TWODOLLARS"),
            new Banknote(USD, 5, "besh", "FIVEDOLLARS"),
            new Banknote(USD, 10, "o\u02BBn", "TENDOLLARS"),
            new Banknote(USD, 20, "yigirma", "TWENTYDOLLARS"),
            new Banknote(USD, 50, "ellik", "FIFTYDOLLARS"),
            new Banknote(USD, 100, "yuz", "ONEHUNDREDDOLLARS"),

            // Yevro (so'z bilan yozilgan qiymat yo'q)
            new Banknote(EUR, 5, "besh"),
            new Banknote(EUR, 10, "o\u02BBn"),
            new Banknote(EUR, 20, "yigirma"),
            new Banknote(EUR, 50, "ellik"),
            new Banknote(EUR, 100, "yuz"),
            new Banknote(EUR, 200, "ikki yuz"),
            new Banknote(EUR, 500, "besh yuz")
    ));

    private static final Set<String> NUMERALS = computeNumerals();
    private static final boolean[] NEEDS_IDENTITY = computeNeedsIdentity();
    private static final int[][] DOMINATED_BY = computeDominators();
    private static final double[][] PHRASE_CAP = computePhraseCaps();

    // ----------------------------------------------------------------- hisoblangan jadvallar

    private static Set<String> computeNumerals() {
        Set<String> set = new HashSet<>();
        for (Banknote note : ALL) {
            set.add(note.numeral);
        }
        return Collections.unmodifiableSet(set);
    }

    /**
     * Raqam faqat valyuta yozuvi bilan birga hisobga olinadigan banknotalar: "qat'iy" valyutalar va
     * raqami boshqa valyutada ham uchraydigan banknotalar (masalan, "100" dollarda ham, manatda ham bor).
     */
    private static boolean[] computeNeedsIdentity() {
        int n = ALL.size();
        boolean[] out = new boolean[n];
        for (int i = 0; i < n; i++) {
            Banknote a = ALL.get(i);
            boolean needs = a.currency.strict;
            for (int j = 0; j < n && !needs; j++) {
                Banknote b = ALL.get(j);
                if (b.currency != a.currency && b.numeral.equals(a.numeral)) {
                    needs = true;
                }
            }
            out[i] = needs;
        }
        return out;
    }

    /**
     * Har bir banknota uchun: so'z yozuvi o'zining yozuvini ichiga olgan boshqa banknotalar.
     * Masalan, "IKI YUZ MANAT" ichida "YUZ MANAT" bor: ikkinchisi topilsa ham, birinchisi topilganda
     * 100 manat natijasi bekor qilinadi.
     */
    private static int[][] computeDominators() {
        int n = ALL.size();
        int[][] out = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Integer> list = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (i != j && containsPhraseOf(ALL.get(j), ALL.get(i))) {
                    list.add(j);
                }
            }
            out[i] = new int[list.size()];
            for (int k = 0; k < out[i].length; k++) {
                out[i][k] = list.get(k);
            }
        }
        return out;
    }

    /**
     * Boshqa banknotaning yozuvini ichiga olgan yozuv (masalan, "IKIYUZMANAT" ichida "YUZMANAT" bor) faqat
     * ikkalasining farqi (bu yerda "IKI") yarmidan kam xato bilan qabul qilinadi: aks holda oldingi satr oxiridagi
     * tasodifiy harflar ("...BANKY" + "YUZMANAT") ham farqli qismni o'rnini bosib, 100 manatni 200 manatga o'xshatib qo'yadi.
     * Boshqa yozuvni ichiga olmagan yozuvlar uchun cheklov yo'q (Double.MAX_VALUE).
     */
    private static double[][] computePhraseCaps() {
        int n = ALL.size();
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            List<String> phrases = ALL.get(i).phrases;
            out[i] = new double[phrases.size()];
            for (int k = 0; k < out[i].length; k++) {
                String p = phrases.get(k);
                double cap = Double.MAX_VALUE;
                for (int j = 0; j < n; j++) {
                    if (j == i) {
                        continue;
                    }
                    for (String q : ALL.get(j).phrases) {
                        if (p.length() > q.length() && p.contains(q)) {
                            cap = Math.min(cap, 0.5 * (p.length() - q.length()));
                        }
                    }
                }
                out[i][k] = cap;
            }
        }
        return out;
    }

    private static boolean containsPhraseOf(Banknote big, Banknote small) {
        for (String b : big.phrases) {
            for (String s : small.phrases) {
                if (b.length() > s.length() && b.contains(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- so'rovlar

    /** Raqam hech bo'lmasa bitta banknotada bosiladimi (masalan, "50000", "100"). */
    public static boolean isNumeral(String numeral) {
        return NUMERALS.contains(numeral);
    }

    /** Bu banknotaning raqami valyuta yozuvisiz hisobga olinmaydimi (qarang: computeNeedsIdentity). */
    public static boolean numberNeedsIdentity(int index) {
        return NEEDS_IDENTITY[index];
    }

    /** So'z yozuvi shu banknotaning yozuvini ichiga olgan banknotalar indekslari. */
    public static int[] dominatedBy(int index) {
        return DOMINATED_BY[index];
    }

    /** Banknota yozuvining ruxsat etilgan eng ko'p "xato" miqdori chegarasi (qarang: computePhraseCaps). */
    public static double phraseCap(int noteIndex, int phraseIndex) {
        return PHRASE_CAP[noteIndex][phraseIndex];
    }

    /** Ovoz uchun matn: valyuta nomi bilan (\"ming so'm\") yoki usiz (\"ming\"). So'mdan boshqa valyutada nom doim aytiladi. */
    public static String spokenText(Banknote note, boolean withUnit) {
        boolean unit = withUnit || note.currency != UZS;
        return unit ? note.spoken + " " + note.currency.unit : note.spoken;
    }

    /** Ekrandagi raqamli ko'rinish: \"10 000 so'm\" yoki \"100 USD\". */
    public static String digitsText(Banknote note, boolean withUnit) {
        if (note.currency == UZS) {
            return withUnit ? note.digits + " " + UNIT : note.digits;
        }
        return note.digits + " " + note.currency.code;
    }

    /** Valyuta kodi va qiymat bo'yicha banknota indeksi; bunday banknota bo'lmasa -1. */
    public static int indexOf(String code, int value) {
        if (code == null) {
            return -1;
        }
        for (int i = 0; i < ALL.size(); i++) {
            Banknote note = ALL.get(i);
            if (note.value == value && note.currency.code.equalsIgnoreCase(code)) {
                return i;
            }
        }
        return -1;
    }

    /** So'm banknotasining qiymati bo'yicha indeksi; bunday banknota bo'lmasa -1. */
    public static int indexOfValue(int value) {
        return indexOf(UZS.code, value);
    }
}
