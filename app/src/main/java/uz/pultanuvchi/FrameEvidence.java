package uz.pultanuvchi;

/** Bitta kadrdan olingan dalillar. */
public final class FrameEvidence {
    /** Har bir banknota turi uchun: banknotadagi raqam ko'rindimi (Banknotes.ALL tartibida). */
    public final double[] numberScores;
    /** Har bir banknota turi uchun: so'z yozuvi (masalan, "ELLIK MING SO'M", "FIVE DOLLARS") ko'rindimi. */
    public final double[] wordScores;
    /** Har bir valyuta uchun: valyutaga xos doimiy yozuv (bank nomi va h.k.) ko'rindimi (Banknotes.CURRENCIES tartibida). */
    public final double[] currencyScores;
    public final boolean hasText;

    public FrameEvidence(double[] numberScores, double[] wordScores, double[] currencyScores, boolean hasText) {
        this.numberScores = numberScores;
        this.wordScores = wordScores;
        this.currencyScores = currencyScores;
        this.hasText = hasText;
    }

    /** Kadrda banknotaga oid biror belgi (raqam, so'z yoki bank nomi yozuvi) bormi. */
    public boolean anyEvidence() {
        for (double v : currencyScores) {
            if (v > 0.0) {
                return true;
            }
        }
        for (double v : numberScores) {
            if (v > 0.0) {
                return true;
            }
        }
        for (double v : wordScores) {
            if (v > 0.0) {
                return true;
            }
        }
        return false;
    }
}
