package uz.pultanuvchi;

/**
 * Bir necha ketma-ket kadrdagi dalillarni jamlab, qaror chiqaradi.
 * Eski kadrlar ta'siri har kadrda pasayadi (DECAY), shuning uchun bitta xato o'qish
 * natijani o'zgartira olmaydi, lekin pul almashtirilsa, natija tez yangilanadi.
 *
 * Bir nechta valyuta bor: "100" raqami dollarda ham, manatda ham, yevroda ham uchraydi. Shuning uchun
 * bunday raqam faqat o'z valyutasining doimiy yozuvi (bank nomi) ham ko'ringandagina hisobga olinadi;
 * so'z yozuvi ("FIVE DOLLARS") esa o'zi valyutani ham, qiymatni ham ko'rsatadi.
 */
public final class DenominationTracker {

    private static final double DECAY = 0.6;
    private static final double RATIO = 1.3;
    private static final double MIN_GAP = 2.0;
    private static final double HIGH_SUPPORT = 7.5;
    private static final double WORD_PRESENT = 2.5;
    private static final double NUMBER_HIGH = 6.0;
    private static final double NUMBER_HIGH_SUPPORT = 9.0;
    private static final double NUMBER_RATIO = 1.5;
    private static final double LOW_NUMBER = 5.4;
    private static final double LOW_NUMBER_WITH_HEADER = 7.0;
    private static final double AMBIGUOUS_MIN = 5.0;
    private static final double HEADER_CAP = 3.0;
    private static final double HAS_EVIDENCE = 0.2;
    /** Valyuta yozuvi "ko'rindi" deb hisoblanishi uchun to'plangan eng kam ball. */
    private static final double IDENTITY_MIN = 1.0;

    private final int n = Banknotes.ALL.size();
    private final int m = Banknotes.CURRENCIES.size();
    private final double[] numAcc = new double[n];
    private final double[] wordAcc = new double[n];
    private final double[] idAcc = new double[m];
    private final double[] gatedNum = new double[n];
    private final double[] total = new double[n];
    private boolean ambiguous = false;

    public void reset() {
        for (int i = 0; i < n; i++) {
            numAcc[i] = 0.0;
            wordAcc[i] = 0.0;
        }
        for (int c = 0; c < m; c++) {
            idAcc[c] = 0.0;
        }
        ambiguous = false;
    }

    /** Kadrda bir nechta turli banknota belgilari teng kuchda ko'rinyaptimi. */
    public boolean isAmbiguous() {
        return ambiguous;
    }

    public Decision update(FrameEvidence ev) {
        for (int i = 0; i < n; i++) {
            numAcc[i] = numAcc[i] * DECAY + ev.numberScores[i];
            wordAcc[i] = wordAcc[i] * DECAY + ev.wordScores[i];
        }
        for (int c = 0; c < m; c++) {
            idAcc[c] = idAcc[c] * DECAY + ev.currencyScores[c];
        }
        return decide();
    }

    private Decision decide() {
        int best = -1;
        double bestS = 0.0;
        double second = 0.0;
        for (int i = 0; i < n; i++) {
            int c = Banknotes.ALL.get(i).currency.index;
            double nu = numAcc[i];
            if (idAcc[c] < IDENTITY_MIN && Banknotes.numberNeedsIdentity(i)) {
                nu = 0.0; // valyuta yozuvisiz bunday raqam hech narsani isbotlamaydi
            }
            gatedNum[i] = nu;
            double base = nu + wordAcc[i];
            double header = Math.min(idAcc[c], HEADER_CAP);
            double s = base > HAS_EVIDENCE ? base + header : 0.0;
            total[i] = s;
            if (s > bestS) {
                second = bestS;
                bestS = s;
                best = i;
            } else if (s > second) {
                second = s;
            }
        }
        ambiguous = best >= 0 && second >= AMBIGUOUS_MIN && bestS < second * RATIO;
        if (best < 0) {
            return null;
        }
        if (bestS < second * RATIO || bestS - second < MIN_GAP) {
            return null;
        }
        CurrencyInfo currency = Banknotes.ALL.get(best).currency;
        double id = idAcc[currency.index];
        double w = wordAcc[best];
        double nu = gatedNum[best];
        if (w >= WORD_PRESENT) {
            // so'z yozuvi ko'rindi: yetarli dalil to'planguncha kutamiz
            if (bestS < HIGH_SUPPORT) {
                return null;
            }
            // qat'iy valyutada (dollar, manat, yevro) bank nomi ko'rinmaguncha "ehtimol" deymiz
            boolean confirmed = !currency.strict || id >= IDENTITY_MIN;
            return new Decision(best, confirmed);
        }
        // faqat raqam: xato o'qish (masalan, nol tushib qolishi) ehtimoli ko'proq, shuning uchun qattiqroq talab
        if (id >= 1.0 && nu >= NUMBER_HIGH && bestS >= NUMBER_HIGH_SUPPORT && bestS >= second * NUMBER_RATIO) {
            return new Decision(best, true);
        }
        if (nu >= LOW_NUMBER && (id < 1.0 || nu >= LOW_NUMBER_WITH_HEADER)) {
            return new Decision(best, false);
        }
        return null;
    }
}
