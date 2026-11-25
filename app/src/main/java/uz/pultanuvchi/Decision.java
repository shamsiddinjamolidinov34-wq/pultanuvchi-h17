package uz.pultanuvchi;

/** Aniqlash natijasi: banknota indeksi va ishonch darajasi. */
public final class Decision {
    public final int index;
    /** true - ishonchli; false - "ehtimol" (faqat raqam asosida). */
    public final boolean high;

    public Decision(int index, boolean high) {
        this.index = index;
        this.high = high;
    }
}
