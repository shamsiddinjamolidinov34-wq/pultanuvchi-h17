package uz.pultanuvchi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Bitta valyuta haqida ma'lumot (Android'ga bog'liq emas).
 *
 * <p>{@code identity} - banknotada doimiy bosilgan yozuvlar (bank nomi va h.k.), ASCII "skelet" shaklida
 * (qarang: {@link BanknoteMatcher#skeleton}). Ular valyutani tasdiqlaydi. Masalan, "100" raqami dollarda ham,
 * manatda ham, yevroda ham bor, shuning uchun bunday raqam faqat valyuta yozuvi bilan birga hisobga olinadi.
 */
public final class CurrencyInfo {
    /** CURRENCIES ro'yxatidagi o'rni. */
    public final int index;
    /** ISO kodi, masalan "USD". */
    public final String code;
    /** Inglizcha nomi (sun'iy intellektga yuboriladigan topshiriq uchun). */
    public final String name;
    /** Ovozda aytiladigan valyuta nomi (hozirgi alifbo): so'm, dollar va h.k. */
    public final String unit;
    /**
     * true bo'lsa, shu valyutadagi natija valyuta yozuvi (bank nomi) ko'rinmaguncha "ehtimol" bo'lib qoladi
     * va raqamlar yozuvsiz hisobga olinmaydi. O'zbek so'mining raqamlari (1000 ... 200000) boshqa
     * valyutalarda uchramaydi, shuning uchun so'm uchun false.
     */
    public final boolean strict;
    /** Valyutaga xos doimiy yozuvlar (skelet shaklida, faqat A-Z harflari). */
    public final List<String> identity;

    public CurrencyInfo(int index, String code, String name, String unit, boolean strict, String... identity) {
        this.index = index;
        this.code = code;
        this.name = name;
        this.unit = unit;
        this.strict = strict;
        this.identity = Collections.unmodifiableList(Arrays.asList(identity));
    }
}
