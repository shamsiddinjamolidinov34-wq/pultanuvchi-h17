package uz.pultanuvchi;

/**
 * Sun'iy intellektdan (Google Gemini) qachon so'rash kerakligi qoidalari.
 * Android'ga bog'liq emas, shuning uchun vaqtni qo'lda berib sinab ko'rish mumkin (tools/LogicCheck.java).
 *
 * So'rov faqat quyidagilar birga bo'lganda yuboriladi:
 *  - kadrda matn ko'rinib turibdi (bo'sh xona yoki qorong'i kadr yuborilmaydi);
 *  - ilova shu matn ko'ringandan beri WAIT_MS davomida pulni o'zi aniqlay olmagan;
 *  - oldingi so'rov tugagan va kutish vaqti (cooldown) o'tgan.
 *
 * Javob topilmasa yoki xato bo'lsa, kutish vaqti ikki baravar oshadi (12, 24, 48, 60 soniya),
 * shunda foydalanuvchining Google kalitidagi limit bekorga sarflanmaydi.
 * Barcha metodlar bitta oqimdan (asosiy oqim) chaqiriladi.
 */
public final class AiGate {
    /** Matn ko'ringandan beri shuncha vaqt aniqlanmasa, sun'iy intellektga yuboriladi. */
    public static final long WAIT_MS = 5000L;
    /** Matn shuncha vaqt ko'rinmasa, "yangi epizod" boshlanadi va kutish qaytadan hisoblanadi. */
    public static final long TEXT_GAP_MS = 3000L;
    /** Oldingi so'rovdan keyin kamida shuncha kutiladi. */
    public static final long COOLDOWN_MS = 12000L;
    /** Muvaffaqiyatsizliklar ketma-ket bo'lganda kutishning eng uzoq muddati. */
    public static final long COOLDOWN_MAX_MS = 60000L;
    /** Javob kelmay qolsa, shuncha vaqtdan keyin "band" holati o'zi o'chadi. */
    public static final long STUCK_MS = 45000L;

    private long lastTextAt = -1L;
    private long episodeStart = 0L;
    private long nextAllowedAt = 0L;
    private int failStreak = 0;
    private boolean busy = false;
    private long busySince = 0L;
    private int token = 0;

    /** Har bir kadrdan keyin chaqiriladi: kadrda matn bormi. */
    public void onFrame(long now, boolean hasText) {
        if (!hasText) {
            return;
        }
        if (lastTextAt < 0L || now - lastTextAt > TEXT_GAP_MS) {
            episodeStart = now;
        }
        lastTextAt = now;
    }

    /** So'nggi windowMs ichida kadrda matn ko'rindimi. */
    public boolean textSeenWithin(long now, long windowMs) {
        return lastTextAt >= 0L && now - lastTextAt <= windowMs;
    }

    /** Kutishni shu paytdan boshlab qaytadan hisoblaydi (masalan, pul olib qo'yilganda). */
    public void restartEpisode(long now) {
        episodeStart = now;
    }

    /** Ko'ringan matn haqidagi ma'lumotni unutadi (to'xtatib, qayta boshlanganda). */
    public void forgetText() {
        lastTextAt = -1L;
    }

    /** So'rov hozir yuborilgan yoki javob kutilyaptimi. */
    public boolean isBusy(long now) {
        return busy && now - busySince < STUCK_MS;
    }

    /** Hozir sun'iy intellektdan so'rash kerakmi. */
    public boolean shouldAsk(long now) {
        return !isBusy(now)
                && textSeenWithin(now, TEXT_GAP_MS)
                && now - episodeStart >= WAIT_MS
                && now >= nextAllowedAt;
    }

    /** So'rov boshlandi. Qaytgan raqamni end() ga berish kerak. */
    public int begin(long now) {
        busy = true;
        busySince = now;
        token++;
        return token;
    }

    /**
     * So'rov tugadi.
     *
     * @param found sun'iy intellekt haqiqiy banknota qiymatini qaytardi
     * @return false bo'lsa, javob eskirgan (bekor qilingan so'rovniki) va e'tiborga olinmaydi
     */
    public boolean end(int requestToken, long now, boolean found) {
        if (requestToken != token || !busy) {
            return false;
        }
        busy = false;
        if (found) {
            failStreak = 0;
            nextAllowedAt = now + COOLDOWN_MS;
        } else {
            failStreak++;
            nextAllowedAt = now + cooldownAfterFailures(failStreak);
        }
        return true;
    }

    /** Kutilayotgan so'rovni bekor qiladi: uning javobi keyin kelsa, e'tiborga olinmaydi. */
    public void cancel() {
        busy = false;
        token++;
    }

    /** Kutish vaqtini bekor qiladi (foydalanuvchi kalitni almashtirganda yoki yoqqanda). */
    public void clearCooldown() {
        nextAllowedAt = 0L;
        failStreak = 0;
    }

    static long cooldownAfterFailures(int streak) {
        int shift = Math.min(Math.max(streak - 1, 0), 3);
        return Math.min(COOLDOWN_MAX_MS, COOLDOWN_MS << shift);
    }
}
