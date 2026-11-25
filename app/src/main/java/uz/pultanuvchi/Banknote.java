package uz.pultanuvchi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Bitta banknota turi haqida ma'lumot. */
public final class Banknote {
    /** Qaysi valyutaga tegishli. */
    public final CurrencyInfo currency;
    /** Qiymati (shu valyutada). */
    public final int value;
    /** Ko'rinish uchun, masalan "10 000". */
    public final String digits;
    /** Banknotada bosilgan raqam, masalan "10000". */
    public final String numeral;
    /** Ovoz uchun matn (kichik harf, hozirgi alifbo), valyuta birligisiz: masalan "ikki ming". */
    public final String spoken;
    /** Banknotadagi so'z yozuvining ASCII "skeleti" (eski va yangi alifbo uchun bir xil). Bo'sh bo'lishi mumkin. */
    public final List<String> phrases;

    public Banknote(CurrencyInfo currency, int value, String spoken, String... phrases) {
        this.currency = currency;
        this.value = value;
        this.numeral = Integer.toString(value);
        this.digits = grouped(this.numeral);
        this.spoken = spoken;
        this.phrases = Collections.unmodifiableList(Arrays.asList(phrases));
    }

    /** "10000" -> "10 000". */
    static String grouped(String numeral) {
        StringBuilder sb = new StringBuilder(numeral.length() + 3);
        int len = numeral.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(numeral.charAt(i));
        }
        return sb.toString();
    }
}
