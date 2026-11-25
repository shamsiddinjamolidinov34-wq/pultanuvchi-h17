import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import uz.pultanuvchi.AiAnswer;
import uz.pultanuvchi.AiGate;
import uz.pultanuvchi.Alphabet;
import uz.pultanuvchi.Banknote;
import uz.pultanuvchi.BanknoteMatcher;
import uz.pultanuvchi.Banknotes;
import uz.pultanuvchi.CurrencyInfo;
import uz.pultanuvchi.Decision;
import uz.pultanuvchi.DenominationTracker;
import uz.pultanuvchi.FrameEvidence;
import uz.pultanuvchi.Nv21;

/**
 * Tanish mantiqini Android'siz tekshiradi.
 * Ishga tushirish (JDK 17+):
 *   javac -encoding UTF-8 -d out app/src/main/java/uz/pultanuvchi/{CurrencyInfo,Banknote,Banknotes,FrameEvidence,Decision,Alphabet,BanknoteMatcher,DenominationTracker,AiAnswer,AiGate,Nv21}.java tools/LogicCheck.java
 *   java -cp out LogicCheck
 */
public class LogicCheck {
    static int failures = 0;
    static int total = 0;

    static void check(String name, boolean ok, String detail) {
        total++;
        if (!ok) {
            failures++;
        }
        System.out.println((ok ? "OK    " : "XATO  ") + name + (detail.isEmpty() ? "" : "  -> " + detail));
    }

    /** So'm uchun faqat qiymat (\"50000\"), boshqa valyutalar uchun \"USD:100\" ko'rinishida. */
    static String label(int index) {
        Banknote b = Banknotes.ALL.get(index);
        return b.currency == Banknotes.UZS ? String.valueOf(b.value) : b.currency.code + ":" + b.value;
    }

    /** Bir xil (yoki turlicha) kadrlarni ketma-ket tahlil qiladi; birinchi qaror va kadr raqamini qaytaradi. */
    static String run(String[]... frames) {
        DenominationTracker t = new DenominationTracker();
        for (int i = 0; i < frames.length; i++) {
            FrameEvidence ev = BanknoteMatcher.analyze(Arrays.asList(frames[i]));
            Decision d = t.update(ev);
            if (d != null) {
                return label(d.index) + (d.high ? " ISHONCHLI" : " EHTIMOL") + " kadr=" + (i + 1);
            }
        }
        return t.isAmbiguous() ? "NOANIQ" : "YOQ";
    }

    /** Valyuta kodi va qiymat bo'yicha banknota (jadvalda bo'lishi shart). */
    static Banknote note(String code, int value) {
        int i = Banknotes.indexOf(code, value);
        if (i < 0) {
            throw new IllegalArgumentException("jadvalda yo'q: " + code + " " + value);
        }
        return Banknotes.ALL.get(i);
    }

    /** Faylni UTF-8 matn sifatida o'qiydi; fayl bo'lmasa null. */
    static String readFile(String path) {
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    static String[][] repeat(int n, String... lines) {
        String[][] f = new String[n][];
        for (int i = 0; i < n; i++) {
            f[i] = lines;
        }
        return f;
    }

    static void expect(String name, String got, String want) {
        check(name, got.startsWith(want), "natija: " + got + " | kutilgan: " + want);
    }


    // ---------------------------------------------------------------- yordamchilar (yangi imkoniyatlar)

    static String shown(String text) {
        return text == null ? "null" : text.replace("\n", "\\n");
    }

    /** wantCode == null bo'lsa, javob \"topilmadi\" (-1) bo'lishi kerak. */
    static void aiCase(String text, String wantCode, int wantValue) {
        int want = wantCode == null ? -1 : Banknotes.indexOf(wantCode, wantValue);
        int got = AiAnswer.parseNote(text);
        String wantText = wantCode == null ? "topilmadi" : wantCode + ":" + wantValue;
        String gotText = got < 0 ? "topilmadi" : Banknotes.ALL.get(got).currency.code + ":" + Banknotes.ALL.get(got).value;
        check("AI javobi: \"" + shown(text) + "\" -> " + wantText, got == want, "olingan: " + gotText);
    }

    /** Kadrlar har 200 ms da keladi va ularda matn bor: [from, to] oralig'ini beradi; oxirgi vaqtni qaytaradi. */
    static long feed(AiGate g, long from, long to) {
        long t = from;
        g.onFrame(t, true);
        while (t + 200L <= to) {
            t += 200L;
            g.onFrame(t, true);
        }
        return t;
    }

    /** Semi-planar (pixelStride 2) kadrning bayt tekisliklarini yasaydi va NV21 ni kutilgan natija bilan solishtiradi. */
    static byte[] logicalNv21(int w, int h, int cw, int ch) {
        byte[] out = new byte[w * h + 2 * cw * ch];
        int pos = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[pos++] = (byte) (y * 16 + x);
            }
        }
        for (int r = 0; r < ch; r++) {
            for (int c = 0; c < cw; c++) {
                out[pos++] = (byte) (200 + r * 10 + c);   // V
                out[pos++] = (byte) (100 + r * 10 + c);   // U
            }
        }
        return out;
    }

    static String repeatChar(char c, int n) {
        char[] a = new char[n];
        Arrays.fill(a, c);
        return new String(a);
    }

    public static void main(String[] args) {
        final String BANK_OLD = "O\u2018ZBEKISTON RESPUBLIKASI MARKAZIY BANKI";
        final String BANK_NEW = "\u00D6ZBEKISTON RESPUBLIKASI MARKAZIY BANKI";
        final String CLAUSE_OLD = "O\u2018ZBEKISTON SO\u2018MI RESPUBLIKA HUDUDIDA HAMMA TO\u2018LOVLAR UCHUN O\u2018Z QIYMATI BO\u2018YICHA QABUL QILINISHI SHART";

        // --- hozirgi banknotalar (eski alifbo)
        expect("50 000 old tomon, to'liq", run(repeat(3, BANK_OLD, "50000", "ELLIK MING SO\u2018M", CLAUSE_OLD, "50000", "AB 1234567")), "50000 ISHONCHLI kadr=1");
        expect("5 000 faqat so'z", run(repeat(4, "BESH MING SO'M")), "5000 ISHONCHLI kadr=2");
        expect("10 000 (O'N MING SO'M + raqam)", run(repeat(3, "O'N MING SO'M", "10000", "10000")), "10000 ISHONCHLI kadr=1");
        expect("100 000 (BIR YUZ MING) + raqam", run(repeat(3, "BIR YUZ MING SO'M", "100000")), "100000 ISHONCHLI");
        expect("100 000 faqat so'z (BIR YuZ MING)", run(repeat(4, "BIR YuZ MING SO\u2018M")), "100000 ISHONCHLI kadr=2");
        expect("200 000 (IKKI YUZ MING)", run(repeat(3, "IKKI YUZ MING SO'M", "200000", "200000")), "200000 ISHONCHLI kadr=1");
        expect("2 000 (IKKI MING)", run(repeat(4, "IKKI MING SO'M", "2000")), "2000 ISHONCHLI");
        expect("1 000 (BIR MING)", run(repeat(4, "BIR MING SO'M", "1000")), "1000 ISHONCHLI");
        expect("20 000 (YIGIRMA MING)", run(repeat(4, "YIGIRMA MING SO'M", "20000", BANK_OLD)), "20000 ISHONCHLI");

        // --- yangi alifbo
        expect("yangi alifbo: BEŞ MING SÖM + 5000", run(repeat(3, BANK_NEW, "BE\u015E MING S\u00D6M", "5000", "5000")), "5000 ISHONCHLI kadr=1");
        expect("yangi alifbo: ÖN MING SÖM", run(repeat(4, "\u00D6N MING S\u00D6M")), "10000 ISHONCHLI");
        expect("yangi alifbo: faqat raqam + bank nomi", run(repeat(5, BANK_NEW, "10000", "10000")), "10000 ISHONCHLI kadr=3");
        expect("yangi alifbo: bitta raqam + bank nomi (sekinroq)", run(repeat(9, BANK_NEW, "50000")), "50000 ISHONCHLI");
        expect("ö/ş harflari OCRda o'/s bo'lib qolsa (BES MING SOM)", run(repeat(4, "BES MING SOM")), "5000 ISHONCHLI");
        expect("diakritiksiz (ON MING SOM)", run(repeat(4, "ON MING SOM")), "10000 ISHONCHLI");

        // --- raqam formatlari
        expect("bo'lingan raqam 10 000", run(repeat(3, "10 000", "10 000", "MARKAZIY BANKI")), "10000 ISHONCHLI");
        expect("bo'lingan raqam 100 000 (bank nomi bilan)", run(repeat(3, "100 000", "100 000", BANK_OLD)), "100000 ISHONCHLI");
        expect("noto'g'ri bo'lingan '10 00' 1000 bo'lmasligi", run(repeat(8, "10 00", "10 00")), "YOQ");

        // --- OCR shovqini
        expect("OCR shovqini: ELLlK MlNG S0M + 5OOOO", run(repeat(3, "ELLlK MlNG S0M", "5OOOO")), "50000 ISHONCHLI");
        expect("OCR shovqini: YIGIRNA MING SOM", run(repeat(4, "YIGIRNA MING SOM")), "20000");

        // --- xavfsizlik: ziddiyat va yolg'on natijalar
        expect("nol tushib qolgan raqam (5000) + ELLIK MING so'zi -> 50 000", run(repeat(4, "ELLIK MING SO'M", "5000", "MARKAZIY BANKI")), "50000 ISHONCHLI");
        expect("narx yorlig'i (faqat raqam) -> 'ehtimol'", run(repeat(6, "50 000 so'm")), "50000 EHTIMOL");
        expect("ikki xil pul bir kadrda -> noaniq", run(repeat(3, "BESH MING SO'M", "ELLIK MING SO'M")), "NOANIQ");
        expect("tasodifiy matn -> hech narsa", run(repeat(8, "Salom dunyo", "12345", "AB 1234567")), "YOQ");
        expect("faqat 'Markaziy bank' yozuvi -> hech narsa", run(repeat(8, BANK_OLD)), "YOQ");
        expect("seriya raqami -> hech narsa", run(repeat(8, "AA 5000000", "BB 1000000")), "YOQ");

        // --- oddiy o'zbekcha matn yolg'on natija bermasligi kerak
        String[] news = {
                "Banklar naqd pulga bo\u2018lgan ehtiyojni oldindan elektron shaklda rejalashtiradi.",
                "Markaziy bank talabga ko\u2018ra bir nominaldagi pul belgilarini boshqa nominaldagi banknotlarga almashtiradi.",
                "500 ming so\u2018mlik yangi banknot haqidagi xabarlar asossiz, 100 ming so\u2018mlik banknotdan ko\u2018p foydalaniladi.",
                "Oylik 5 ming so\u2018m, 2 ming dona, 10 ming kishi, 20 ming nusxa va 50 ming dollar.",
                "Yangi o\u2018zbek alifbosi: islohotlar va amaliyot uyg\u2018unligi"
        };
        expect("oddiy yangilik matni -> hech narsa", run(repeat(8, news)), "YOQ");

        // --- galereya (bitta rasm)
        Decision s1 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList(BANK_OLD, "50000", "ELLIK MING SO'M")));
        check("rasm: to'liq 50 000", s1 != null && s1.high && Banknotes.ALL.get(s1.index).value == 50000, "");
        Decision s2 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList(BANK_NEW, "10000", "10000")));
        check("rasm: yangi alifbo 10 000 (raqam x2 + bank nomi)", s2 != null && s2.high && Banknotes.ALL.get(s2.index).value == 10000, "");
        Decision s3 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList("Salom", "123")));
        check("rasm: pul yo'q", s3 == null, "");
        Decision s4 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList("BESH MING SO'M", "ELLIK MING SO'M")));
        check("rasm: ikki xil pul -> noaniq", s4 == null, "");

        // --- skelet: ikkala alifbo bir xil bo'lishi kerak
        String a = BanknoteMatcher.skeleton("O\u2018N MING SO\u2018M");
        String b = BanknoteMatcher.skeleton("\u00D6N MING S\u00D6M");
        String sc = BanknoteMatcher.skeleton("O\u02BBn ming so\u02BBm");
        String d = BanknoteMatcher.skeleton("O'N MING SO'M");
        check("skelet: eski == yangi == ʻ == ASCII", a.equals("ONMINGSOM") && a.equals(b) && a.equals(sc) && a.equals(d), a + " " + b + " " + sc + " " + d);
        check("skelet: BEŞ == BESH", BanknoteMatcher.skeleton("BE\u015E").equals(BanknoteMatcher.skeleton("BESH")), BanknoteMatcher.skeleton("BE\u015E"));
        check("skelet: ÇIROQ == CHIROQ", BanknoteMatcher.skeleton("\u00C7iroq").equals("CHIROQ"), BanknoteMatcher.skeleton("\u00C7iroq"));
        check("skelet: ʻ va ğ", BanknoteMatcher.skeleton("G\u02BBAZNA").equals("GAZNA") && BanknoteMatcher.skeleton("\u011EAZNA").equals("GAZNA"), "");
        check("skelet: sof raqamli so'z tashlanadi", BanknoteMatcher.skeleton("50000 ELLIK").equals("ELLIK"), BanknoteMatcher.skeleton("50000 ELLIK"));

        // --- alifbo o'tkazgich
        check("alifbo: Oʻn ming soʻm -> Ön ming söm", Alphabet.toNew("O\u02BBn ming so\u02BBm").equals("\u00D6n ming s\u00F6m"), Alphabet.toNew("O\u02BBn ming so\u02BBm"));
        check("alifbo: Besh -> Beş", Alphabet.toNew("Besh ming").equals("Be\u015F ming"), "");
        check("alifbo: Chiroqni -> Çiroqni", Alphabet.toNew("Chiroqni yoqish").equals("\u00C7iroqni yoqi\u015F"), Alphabet.toNew("Chiroqni yoqish"));
        check("alifbo: Qorongʻi -> Qoronği", Alphabet.toNew("Qorong\u02BBi").equals("Qoron\u011Fi"), Alphabet.toNew("Qorong\u02BBi"));
        check("alifbo: ng o'zgarmaydi (yangi)", Alphabet.toNew("yangi").equals("yangi"), "");
        check("alifbo: tutuq belgisi o'zgarmaydi", Alphabet.toNew("ma\u02BClumot").equals("ma\u02BClumot"), "");


        // --- ovozda aytiladigan matn (takroriy e'lon): "so'm" bilan va usiz
        String[] withUnit = {"ming soʻm", "ikki ming soʻm", "besh ming soʻm", "oʻn ming soʻm", "yigirma ming soʻm", "ellik ming soʻm", "yuz ming soʻm", "ikki yuz ming soʻm"};
        String[] withoutUnit = {"ming", "ikki ming", "besh ming", "oʻn ming", "yigirma ming", "ellik ming", "yuz ming", "ikki yuz ming"};
        boolean spokenOk = Banknotes.indexOfValue(200000) == 7;
        for (int i = 0; i < 8 && spokenOk; i++) {
            spokenOk = Banknotes.spokenText(Banknotes.ALL.get(i), true).equals(withUnit[i])
                    && Banknotes.spokenText(Banknotes.ALL.get(i), false).equals(withoutUnit[i]);
        }
        check("ovoz: 8 ta so'm banknotasi, so'm bilan va usiz", spokenOk, Banknotes.spokenText(Banknotes.ALL.get(1), true) + " / " + Banknotes.spokenText(Banknotes.ALL.get(1), false));
        check("ovoz: qiymat bo'yicha indeks", Banknotes.indexOfValue(2000) == 1 && Banknotes.indexOfValue(200000) == 7
                && Banknotes.indexOfValue(1500) == -1 && Banknotes.indexOfValue(0) == -1, "");
        check("ovoz: yangi alifboda ham to'g'ri", Alphabet.toNew(Banknotes.spokenText(Banknotes.ALL.get(3), true)).equals("ön ming söm"),
                Alphabet.toNew(Banknotes.spokenText(Banknotes.ALL.get(3), true)));

        // --- sun'iy intellekt javobini tahlil qilish (valyuta kodi + qiymat)
        aiCase("NOTE=USD:100", "USD", 100);
        aiCase("NOTE=UZS:2000", "UZS", 2000);
        aiCase("NOTE = UZS : 50 000", "UZS", 50000);
        aiCase("note: eur : 50", "EUR", 50);
        aiCase("NOTE=TMT:200", "TMT", 200);
        aiCase("NOTE=TMT:1", "TMT", 1);
        aiCase("NOTE=UZS:100,000", "UZS", 100000);
        aiCase("NOTE=UZS:1.000", "UZS", 1000);
        aiCase("NOTE=USD:100\n", "USD", 100);
        aiCase("```\nNOTE=EUR:20\n```", "EUR", 20);
        aiCase("NOTE=NONE", null, 0);
        aiCase("NOTE=", null, 0);
        aiCase("NOTE=USD:75", null, 0);
        aiCase("NOTE=UZS:100", null, 0);
        aiCase("NOTE=EUR:1", null, 0);
        aiCase("NOTE=USD:99999999999999", null, 0);
        aiCase("NOTE=RUB:5000", null, 0);
        aiCase("NOTE=USD:100 and also NOTE=EUR:50", "USD", 100);
        aiCase("It is a 20000 UZS note.", "UZS", 20000);
        aiCase("It looks like USD 100.", "USD", 100);
        aiCase("Maybe 5000 UZS or 50000 UZS", null, 0);
        aiCase("USD 100 or EUR 50", null, 0);
        aiCase("It is a 20000 som note.", null, 0);
        aiCase("No banknote visible", null, 0);
        aiCase("", null, 0);
        aiCase(null, null, 0);

        boolean promptSafe = true;
        for (char ch : (AiAnswer.PROMPT + AiAnswer.PING_PROMPT).toCharArray()) {
            if (ch == '"' || ch == '\\' || ch < 32 || ch > 126) {
                promptSafe = false;
            }
        }
        check("AI topshiriq: JSON ichiga xavfsiz (tirnoq, teskari chiziq, yangi qator, ASCII bo'lmagan belgi yo'q)", promptSafe, "");
        boolean promptValues = true;
        for (int i = 0; i < Banknotes.ALL.size(); i++) {
            if (!AiAnswer.PROMPT.contains(String.valueOf(Banknotes.ALL.get(i).value))) {
                promptValues = false;
            }
        }
        check("AI topshiriq: barcha banknota qiymatlari yozilgan", promptValues, "");

        // --- Google kalitini tozalash va tekshirish (sinov uchun to'qib chiqarilgan kalit, haqiqiy emas)
        String sampleKey = "TEST-key_0123456789abcdefghijklmnop";
        check("kalit: tozalash (bo'shliq, tirnoq, NOM=)",
                AiAnswer.cleanKey("  GEMINI_API_KEY=\"" + sampleKey + "\"\n").equals(sampleKey)
                        && AiAnswer.cleanKey("‘" + sampleKey + "’").equals(sampleKey)
                        && AiAnswer.cleanKey(sampleKey + "\r\n").equals(sampleKey)
                        && AiAnswer.cleanKey("  " + sampleKey + "  ").equals(sampleKey)
                        && AiAnswer.cleanKey(null).isEmpty(), "");
        check("kalit: to'g'ri shakl qabul qilinadi", AiAnswer.isPlausibleKey(sampleKey), sampleKey.length() + " belgi");
        check("kalit: noto'g'ri shakllar rad etiladi", !AiAnswer.isPlausibleKey("") && !AiAnswer.isPlausibleKey("qisqa")
                && !AiAnswer.isPlausibleKey(null) && !AiAnswer.isPlausibleKey(sampleKey + " x")
                && !AiAnswer.isPlausibleKey(sampleKey + "ʻ") && !AiAnswer.isPlausibleKey(repeatChar('a', 201))
                && AiAnswer.isPlausibleKey(repeatChar('a', 200)) && !AiAnswer.isPlausibleKey(repeatChar('a', 19)), "");

        // --- kadrni NV21 ga joylash (qator uzunligi kengaytirilgan, haqiqiy kameradagidek)
        {
            int w = 6, h = 4, cw = 3, ch = 2;
            byte[] expected = logicalNv21(w, h, cw, ch);

            // semi-planar: Y qatori 8 bayt (2 bayt bo'sh joy), V va U aralash; U tekisligi V dan 1 bayt keyin boshlanadi
            int yStride = 8;
            byte[] y = new byte[(h - 1) * yStride + w];
            Arrays.fill(y, (byte) 0x77);
            for (int r = 0; r < h; r++) {
                for (int c = 0; c < w; c++) {
                    y[r * yStride + c] = (byte) (r * 16 + c);
                }
            }
            int uvStride = 8;
            byte[] vu = new byte[(ch - 1) * uvStride + 2 * cw];
            Arrays.fill(vu, (byte) 0x55);
            for (int r = 0; r < ch; r++) {
                for (int c = 0; c < cw; c++) {
                    vu[r * uvStride + c * 2] = (byte) (200 + r * 10 + c);
                    vu[r * uvStride + c * 2 + 1] = (byte) (100 + r * 10 + c);
                }
            }
            ByteBuffer vPlane = ByteBuffer.wrap(vu);
            ByteBuffer uPlane = ByteBuffer.wrap(vu, 1, vu.length - 1).slice();
            ByteBuffer yPlane = ByteBuffer.wrap(y);
            byte[] got = Nv21.pack(yPlane, yStride, uPlane, vPlane, uvStride, 2, w, h);
            check("NV21: semi-planar, qator uzunligi kengaytirilgan", Arrays.equals(got, expected), "uzunlik " + got.length);
            check("NV21: kirish buferlari o'zgarmaydi", yPlane.position() == 0 && yPlane.limit() == y.length && uPlane.position() == 0 && vPlane.position() == 0, "");

            // planar: U va V alohida, pixelStride = 1, qator uzunligi 5
            int pStride = 5;
            byte[] u = new byte[(ch - 1) * pStride + cw];
            byte[] v = new byte[(ch - 1) * pStride + cw];
            for (int r = 0; r < ch; r++) {
                for (int c = 0; c < cw; c++) {
                    u[r * pStride + c] = (byte) (100 + r * 10 + c);
                    v[r * pStride + c] = (byte) (200 + r * 10 + c);
                }
            }
            byte[] got2 = Nv21.pack(ByteBuffer.wrap(y), yStride, ByteBuffer.wrap(u), ByteBuffer.wrap(v), pStride, 1, w, h);
            check("NV21: planar (pixelStride 1)", Arrays.equals(got2, expected), "uzunlik " + got2.length);

            // kesilgan buferlar yiqilmasligi kerak, uzunligi to'g'ri bo'ladi
            byte[] got3 = Nv21.pack(ByteBuffer.wrap(new byte[10]), yStride, ByteBuffer.wrap(new byte[2]), ByteBuffer.wrap(new byte[2]), uvStride, 2, w, h);
            check("NV21: kesilgan buferlarda yiqilmaydi", got3.length == w * h + 2 * cw * ch, "uzunlik " + got3.length);
            byte[] got4 = Nv21.pack(ByteBuffer.wrap(new byte[40]), 5, ByteBuffer.wrap(new byte[40]), ByteBuffer.wrap(new byte[40]), 5, 2, 5, 3);
            check("NV21: toq o'lchamlar (5x3)", got4.length == 5 * 3 + 2 * 3 * 2, "uzunlik " + got4.length);
        }

        // --- sun'iy intellektga yuborish qoidalari (vaqt qo'lda beriladi)
        {
            final long t0 = 100000L;
            AiGate g = new AiGate();
            boolean never = true;
            for (long t = t0; t < t0 + 60000L; t += 200L) {
                g.onFrame(t, false);
                if (g.shouldAsk(t)) {
                    never = false;
                }
            }
            check("AI qoidasi: matn yo'q bo'lsa hech qachon so'ramaydi", never, "");

            g = new AiGate();
            long first = -1L;
            for (long t = t0; t < t0 + 20000L; t += 200L) {
                g.onFrame(t, true);
                if (first < 0 && g.shouldAsk(t)) {
                    first = t;
                }
            }
            check("AI qoidasi: matn ko'ringandan 5 soniya o'tgach so'raydi", first == t0 + AiGate.WAIT_MS, "birinchi so'rov: +" + (first - t0) + " ms");

            // topilmasa: 12, 24, 48, 60, 60 soniya kutiladi
            long[] gaps = {12000L, 24000L, 48000L, 60000L, 60000L};
            g = new AiGate();
            long now = feed(g, t0, t0 + AiGate.WAIT_MS);
            boolean gapsOk = true;
            String gapDetail = "";
            for (long gap : gaps) {
                if (!g.shouldAsk(now)) {
                    gapsOk = false;
                    gapDetail += " [ruxsat yo'q: " + now + "]";
                    break;
                }
                int token = g.begin(now);
                boolean busy = g.isBusy(now + 200L) && !g.shouldAsk(now + 200L);
                long endAt = now + 1000L;
                boolean ended = g.end(token, endAt, false);
                now = feed(g, endAt, endAt + gap - 200L);
                boolean tooEarly = g.shouldAsk(now);
                now = feed(g, now, endAt + gap);
                boolean onTime = g.shouldAsk(now);
                if (!busy || !ended || tooEarly || !onTime) {
                    gapsOk = false;
                    gapDetail += " [kutish " + gap + ": band=" + busy + " tugadi=" + ended + " erta=" + tooEarly + " vaqtida=" + onTime + "]";
                    break;
                }
            }
            check("AI qoidasi: muvaffaqiyatsizlikdan keyin 12/24/48/60/60 soniya kutadi", gapsOk, gapDetail);

            // muvaffaqiyatli javob kutishni 12 soniyaga qaytaradi
            g = new AiGate();
            now = feed(g, t0, t0 + AiGate.WAIT_MS);
            int tk = g.begin(now);
            g.end(tk, now, false);
            now = feed(g, now, now + 12000L);
            tk = g.begin(now);
            g.end(tk, now, false);                       // ikkinchi xato: keyingisi 24 s
            now = feed(g, now, now + 24000L);
            tk = g.begin(now);
            g.end(tk, now, true);                        // topildi: ketma-ketlik tugaydi
            long found = now;
            now = feed(g, found, found + 11800L);
            boolean early = g.shouldAsk(now);
            now = feed(g, now, found + 12000L);
            boolean exact = g.shouldAsk(now);
            tk = g.begin(now);
            g.end(tk, now, false);                       // yana xato: yana 12 s (ketma-ketlik boshidan)
            now = feed(g, now, now + 11800L);
            boolean early2 = g.shouldAsk(now);
            now = feed(g, now, now + 200L);
            boolean exact2 = g.shouldAsk(now);
            check("AI qoidasi: topilgach kutish 12 soniya, xatolar ketma-ketligi qaytadan boshlanadi", !early && exact && !early2 && exact2,
                    "erta=" + early + " vaqtida=" + exact + " erta2=" + early2 + " vaqtida2=" + exact2);

            // bekor qilish
            g = new AiGate();
            now = feed(g, t0, t0 + AiGate.WAIT_MS);
            tk = g.begin(now);
            g.cancel();
            boolean stale = !g.end(tk, now + 500L, true);
            check("AI qoidasi: bekor qilingan so'rovning kech javobi e'tiborga olinmaydi", stale && !g.isBusy(now + 500L) && g.shouldAsk(now + 500L), "");

            // javob kelmay qolsa, "band" holati o'zi o'chadi
            g = new AiGate();
            now = feed(g, t0, t0 + AiGate.WAIT_MS);
            g.begin(now);
            check("AI qoidasi: javob kelmasa 45 soniyadan keyin band emas",
                    g.isBusy(now + AiGate.STUCK_MS - 1L) && !g.isBusy(now + AiGate.STUCK_MS), "");

            // matn uzilib qolsa, kutish qaytadan hisoblanadi
            g = new AiGate();
            now = feed(g, t0, t0 + 4000L);
            long resume = now + 4000L;
            now = feed(g, resume, resume + 4800L);
            boolean notYet = !g.shouldAsk(now);
            now = feed(g, now, resume + 5000L);
            check("AI qoidasi: matn uzilib qolsa, 5 soniya qaytadan sanaladi", notYet && g.shouldAsk(now), "");

            // pul olib qo'yilganda epizod qaytadan boshlanadi
            g = new AiGate();
            now = feed(g, t0, t0 + AiGate.WAIT_MS);
            boolean ready = g.shouldAsk(now);
            g.restartEpisode(now);
            boolean waits = !g.shouldAsk(now);
            now = feed(g, now, now + AiGate.WAIT_MS);
            check("AI qoidasi: epizodni qayta boshlash", ready && waits && g.shouldAsk(now), "");

            // matn ko'rilgan vaqt
            g = new AiGate();
            boolean none = !g.textSeenWithin(100L, 2500L);
            g.onFrame(100L, true);
            boolean within = g.textSeenWithin(2600L, 2500L) && !g.textSeenWithin(2601L, 2500L);
            g.forgetText();
            check("AI qoidasi: ko'rilgan matn vaqti", none && within && !g.textSeenWithin(150L, 2500L), "");
        }

        // =====================================================================
        //  Yangi valyutalar: Turkmaniston manati, AQSh dollari, yevro
        // =====================================================================
        final String TM_BANK = "T\u00DCRKMENISTANY\u0147 MERKEZI BANKY";
        final String TM_CLAUSE = "\u015EU BANKNOT T\u00D6LEGLERI\u0147 \u00C4HLI G\u00D6RN\u00DC\u015ELERI \u00DC\u00C7IN \u00DD\u00D6RE\u00DD\u00C4R";
        final String US_BANK = "THE UNITED STATES OF AMERICA";
        final String US_FRB = "FEDERAL RESERVE NOTE";
        final String EU_ECB = "BCE ECB EZB EKT EKP";

        // --- jadval to'g'riligi
        {
            boolean tableOk = Banknotes.ALL.size() == 8 + 7 + 7 + 7 && Banknotes.CURRENCIES.size() == 4;
            String tableDetail = "banknotalar: " + Banknotes.ALL.size();
            for (int c = 0; c < Banknotes.CURRENCIES.size(); c++) {
                CurrencyInfo cur = Banknotes.CURRENCIES.get(c);
                if (cur.index != c) {
                    tableOk = false;
                    tableDetail += " [indeks " + cur.code + "]";
                }
                java.util.Set<Integer> seen = new java.util.HashSet<>();
                for (Banknote note : Banknotes.ALL) {
                    if (note.currency == cur && !seen.add(note.value)) {
                        tableOk = false;
                        tableDetail += " [takror " + cur.code + ":" + note.value + "]";
                    }
                }
                if (seen.isEmpty()) {
                    tableOk = false;
                    tableDetail += " [banknota yo'q " + cur.code + "]";
                }
                for (String p : cur.identity) {
                    if (!p.matches("[A-Z]+")) {
                        tableOk = false;
                        tableDetail += " [yozuv " + p + "]";
                    }
                }
            }
            for (Banknote note : Banknotes.ALL) {
                for (String p : note.phrases) {
                    if (!p.matches("[A-Z]+")) {
                        tableOk = false;
                        tableDetail += " [qolip " + p + "]";
                    }
                }
            }
            check("jadval: 8 so'm + 7 manat + 7 dollar + 7 yevro, takrorlanmas qiymatlar, qoliplar faqat A-Z", tableOk, tableDetail);

            boolean gateOk = true;
            for (int i = 0; i < Banknotes.ALL.size(); i++) {
                boolean uzs = Banknotes.ALL.get(i).currency == Banknotes.UZS;
                if (Banknotes.numberNeedsIdentity(i) == uzs) {
                    gateOk = false;
                }
            }
            check("jadval: so'm raqamlari yozuvsiz ham hisobga olinadi, dollar/manat/yevro raqamlari yozuvsiz olinmaydi", gateOk, "");

            int i100 = Banknotes.indexOf("TMT", 100);
            int i200 = Banknotes.indexOf("TMT", 200);
            boolean contained = false;
            for (int j : Banknotes.dominatedBy(i100)) {
                if (j == i200) {
                    contained = true;
                }
            }
            boolean uzsFree = true;
            for (int i = 0; i < 8; i++) {
                if (Banknotes.dominatedBy(i).length != 0) {
                    uzsFree = false;
                }
            }
            check("jadval: 'YUZ MANAT' yozuvi 'IKI YUZ MANAT' ichida ekani aniqlangan, so'mda bunday holat yo'q",
                    contained && Banknotes.dominatedBy(i200).length == 0 && uzsFree, "");
            check("jadval: 200 manat yozuvi xatoga cheklangan (1,5), boshqalarida cheklov yo'q",
                    Banknotes.phraseCap(i200, 0) == 1.5 && Banknotes.phraseCap(i100, 0) == Double.MAX_VALUE
                            && Banknotes.phraseCap(Banknotes.indexOf("USD", 100), 0) == Double.MAX_VALUE,
                    "cheklov: " + Banknotes.phraseCap(i200, 0));
            check("jadval: indeks (valyuta, qiymat)", Banknotes.indexOf("USD", 100) >= 0 && Banknotes.indexOf("usd", 100) == Banknotes.indexOf("USD", 100)
                    && Banknotes.indexOf("USD", 200) == -1 && Banknotes.indexOf("UZS", 100) == -1 && Banknotes.indexOf("RUB", 100) == -1
                    && Banknotes.indexOf(null, 100) == -1 && Banknotes.indexOf("EUR", 500) >= 0, "");
        }

        // --- ovoz va ekrandagi yozuv
        {
            check("ovoz: dollar, manat, yevro nomi so'zsiz rejimda ham aytiladi",
                    Banknotes.spokenText(note("USD", 100), false).equals("yuz dollar")
                            && Banknotes.spokenText(note("USD", 1), false).equals("bir dollar")
                            && Banknotes.spokenText(note("TMT", 200), false).equals("ikki yuz manat")
                            && Banknotes.spokenText(note("TMT", 10), true).equals("o\u02BBn manat")
                            && Banknotes.spokenText(note("EUR", 500), false).equals("besh yuz yevro")
                            && Banknotes.spokenText(note("EUR", 20), true).equals("yigirma yevro"),
                    Banknotes.spokenText(note("USD", 100), false));
            check("ovoz: so'mda so'm so'zi faqat yoqilganda aytiladi",
                    Banknotes.spokenText(note("UZS", 1000), true).equals("ming so\u02BBm")
                            && Banknotes.spokenText(note("UZS", 1000), false).equals("ming"), "");
            check("ovoz: yangi alifboda manat (Ön manat)", Alphabet.toNew(Banknotes.spokenText(note("TMT", 10), true)).equals("\u00F6n manat"),
                    Alphabet.toNew(Banknotes.spokenText(note("TMT", 10), true)));
            check("ekran: raqamli ko'rinish (10 000 so'm, 100 USD, 200 TMT, 500 EUR)",
                    Banknotes.digitsText(note("UZS", 10000), true).equals("10 000 so\u02BBm")
                            && Banknotes.digitsText(note("UZS", 10000), false).equals("10 000")
                            && Banknotes.digitsText(note("USD", 100), true).equals("100 USD")
                            && Banknotes.digitsText(note("USD", 100), false).equals("100 USD")
                            && Banknotes.digitsText(note("TMT", 200), false).equals("200 TMT")
                            && Banknotes.digitsText(note("EUR", 500), true).equals("500 EUR")
                            && note("UZS", 1000).digits.equals("1 000") && note("UZS", 200000).digits.equals("200 000"),
                    Banknotes.digitsText(note("USD", 100), true));
        }

        // --- manat
        expect("manat 1 (BIR MANAT)", run(repeat(3, TM_BANK, "BIR MANAT", "1", "1")), "TMT:1 ISHONCHLI kadr=1");
        expect("manat 5 (B\u00C4\u015E MANAT + yozuv)", run(repeat(3, TM_BANK, "B\u00C4\u015E MANAT", "5", TM_CLAUSE)), "TMT:5 ISHONCHLI kadr=1");
        expect("manat 5 (BE\u015E MANAT)", run(repeat(3, TM_BANK, "BE\u015E MANAT", "5")), "TMT:5 ISHONCHLI");
        expect("manat 5 (cedilla tushib qolgan: BAS MANAT)", run(repeat(3, TM_BANK, "BAS MANAT")), "TMT:5 ISHONCHLI");
        expect("manat 10 (ON MANAT)", run(repeat(3, TM_BANK, "ON MANAT", "10", "10")), "TMT:10 ISHONCHLI kadr=1");
        expect("manat 20 (\u00DDIGRIMI MANAT)", run(repeat(3, TM_BANK, "\u00DDIGRIMI MANAT", "20")), "TMT:20 ISHONCHLI");
        expect("manat 20 (\u00DDGRIMI MANAT, harf tushgan)", run(repeat(3, TM_BANK, "\u00DDGRIMI MANAT", "20")), "TMT:20 ISHONCHLI");
        expect("manat 50 (ELLI MANAT)", run(repeat(3, TM_BANK, "ELLI MANAT", "50")), "TMT:50 ISHONCHLI");
        expect("manat 100 (\u00DD\u00DCZ MANAT)", run(repeat(3, TM_BANK, "\u00DD\u00DCZ MANAT", "100", "100")), "TMT:100 ISHONCHLI kadr=1");
        expect("manat 200 (IKI \u00DD\u00DCZ MANAT)", run(repeat(3, TM_BANK, "IKI \u00DD\u00DCZ MANAT", "200", "200")), "TMT:200 ISHONCHLI kadr=1");
        expect("manat 100: bank nomi yonidagi satr 200 bilan adashmaydi (faqat so'z)", run(repeat(4, TM_BANK, "\u00DD\u00DCZ MANAT")), "TMT:100 ISHONCHLI");
        expect("manat 100: bank nomi oxiri 'BANKI' bo'lib o'qilsa ham (faqat so'z)", run(repeat(4, "TURKMENISTANYN MERKEZI BANKI", "YUZ MANAT")), "TMT:100 ISHONCHLI");
        expect("manat 200: 'I' tushib qolsa va raqam yo'q -> noaniq (100 deb ishonch bilan aytilmaydi)", run(repeat(4, TM_BANK, "KI YUZ MANAT")), "NOANIQ");
        expect("manat 200: 'I' tushib qolgan + raqam 200", run(repeat(4, TM_BANK, "KI YUZ MANAT", "200", "200")), "TMT:200 ISHONCHLI");
        expect("manat: faqat so'z, bank nomisiz -> 'ehtimol'", run(repeat(4, "B\u00C4\u015E MANAT")), "TMT:5 EHTIMOL");
        expect("manat: bank nomi + raqam (so'zsiz)", run(repeat(6, TM_BANK, "100", "100")), "TMT:100 ISHONCHLI kadr=3");
        expect("manat: bank nomisiz faqat raqam -> hech narsa", run(repeat(8, "100", "100")), "YOQ");
        expect("manat: faqat bank nomi -> hech narsa", run(repeat(8, TM_BANK)), "YOQ");

        // --- dollar
        expect("dollar 100 (to'liq)", run(repeat(3, US_BANK, US_FRB, "ONE HUNDRED DOLLARS", "100", "100")), "USD:100 ISHONCHLI kadr=1");
        expect("dollar 50", run(repeat(3, US_BANK, "FIFTY DOLLARS", "50", "50")), "USD:50 ISHONCHLI");
        expect("dollar 20 (TWENTY, TEN bilan adashmaydi)", run(repeat(3, US_BANK, "TWENTY DOLLARS", "20", "20")), "USD:20 ISHONCHLI");
        expect("dollar 10", run(repeat(3, US_BANK, "TEN DOLLARS", "10")), "USD:10 ISHONCHLI");
        expect("dollar 5", run(repeat(3, US_BANK, "FIVE DOLLARS", "5", "5")), "USD:5 ISHONCHLI");
        expect("dollar 2", run(repeat(3, US_BANK, "TWO DOLLARS", "2")), "USD:2 ISHONCHLI");
        expect("dollar 1", run(repeat(3, US_BANK, "ONE DOLLAR", "1", "1")), "USD:1 ISHONCHLI");
        expect("dollar: so'z + bank nomi, raqamsiz", run(repeat(4, US_BANK, "ONE HUNDRED DOLLARS")), "USD:100 ISHONCHLI");
        expect("dollar: orqa tomon (so'zsiz): raqam + bank yozuvi", run(repeat(5, US_BANK, "IN GOD WE TRUST", "50", "50")), "USD:50 ISHONCHLI");
        expect("dollar: so'z ikki satrga bo'lingan, bank nomisiz -> 'ehtimol'", run(repeat(4, "ONE HUNDRED", "DOLLARS")), "USD:100 EHTIMOL");
        expect("dollar: bank nomisiz faqat raqam -> hech narsa", run(repeat(8, "50", "50")), "YOQ");

        // --- yevro
        expect("yevro 50 (EURO + raqam + ECB)", run(repeat(5, "EURO", "50", "50", EU_ECB)), "EUR:50 ISHONCHLI");
        expect("yevro 5", run(repeat(5, "EURO", "5", "5")), "EUR:5 ISHONCHLI");
        expect("yevro 10", run(repeat(5, "EURO", "10", "10")), "EUR:10 ISHONCHLI");
        expect("yevro 20", run(repeat(5, "EURO", "20", "20")), "EUR:20 ISHONCHLI");
        expect("yevro 100 (ikki raqam bir qatorda: '100 100')", run(repeat(5, "EURO", "100 100")), "EUR:100 ISHONCHLI");
        expect("yevro 200", run(repeat(5, "EURO", "200", "200")), "EUR:200 ISHONCHLI");
        expect("yevro 500", run(repeat(5, "EURO", "500", "500")), "EUR:500 ISHONCHLI");
        expect("yevro: faqat ECB qisqartmalari + raqam", run(repeat(5, EU_ECB, "10", "10")), "EUR:10 ISHONCHLI");
        expect("yevro: EURO yozuvisiz faqat raqam -> hech narsa", run(repeat(8, "50", "50")), "YOQ");

        // --- aralash va xavfsizlik
        expect("so'm va dollar bir kadrda -> noaniq", run(repeat(3, "BESH MING SO'M", "FIVE DOLLARS")), "NOANIQ");
        expect("manat va dollar bank nomlari bir kadrda -> noaniq", run(repeat(3, TM_BANK, US_BANK, "10", "10")), "NOANIQ");
        expect("so'm: raqam bo'lib qolgan narx yorlig'i hamon 'ehtimol'", run(repeat(6, "50 000 so'm")), "50000 EHTIMOL");
        String[] news2 = {
                "Oylik 10 dollar, 20 manat, 50 yevro va 100 ming so'm.",
                "Kurs: 1 dollar = 12 000 so'm, 100 yevro, 5 manat, 200 manat.",
                "The price is 100 USD or 50 EUR, about 20 dollars."
        };
        expect("yangilik: dollar/manat/yevro so'zlari yolg'on natija bermaydi", run(repeat(8, news2)), "YOQ");

        // --- galereya (bitta rasm): yangi valyutalar
        Decision u1 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList(US_BANK, "ONE HUNDRED DOLLARS", "100")));
        check("rasm: 100 dollar", u1 != null && u1.high && label(u1.index).equals("USD:100"), u1 == null ? "null" : label(u1.index));
        Decision u2 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList(TM_BANK, "B\u00C4\u015E MANAT", "5")));
        check("rasm: 5 manat", u2 != null && u2.high && label(u2.index).equals("TMT:5"), u2 == null ? "null" : label(u2.index));
        Decision u3 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList("EURO", "50", "50")));
        check("rasm: 50 yevro (EURO + ikki raqam)", u3 != null && u3.high && label(u3.index).equals("EUR:50"), u3 == null ? "null" : label(u3.index));
        Decision u4 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList("FIVE DOLLARS")));
        check("rasm: bank nomisiz 5 dollar -> 'ehtimol'", u4 != null && !u4.high && label(u4.index).equals("USD:5"), u4 == null ? "null" : label(u4.index));
        Decision u5 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList("100", "100")));
        check("rasm: faqat raqam (valyutasiz) -> pul yo'q", u5 == null, "");
        Decision u6 = BanknoteMatcher.decideStill(BanknoteMatcher.analyze(Arrays.asList("BESH MING SO'M", "FIVE DOLLARS")));
        check("rasm: so'm va dollar birga -> noaniq", u6 == null, "");

        // --- ikki raqam bir qatorda ("100 100") birlashib ketmasligi, "10 000" esa birlashishi kerak
        {
            FrameEvidence e1 = BanknoteMatcher.analyze(Arrays.asList("EURO", "100 100"));
            FrameEvidence e2 = BanknoteMatcher.analyze(Arrays.asList("10 000"));
            FrameEvidence e3 = BanknoteMatcher.analyze(Arrays.asList("200 200"));
            check("raqamlar: '100 100' ikkita 100 bo'ladi, '10 000' bitta 10000 bo'ladi",
                    e1.numberScores[Banknotes.indexOf("EUR", 100)] > 3.0 && e2.numberScores[Banknotes.indexOf("UZS", 10000)] > 0.0
                            && e3.numberScores[Banknotes.indexOf("TMT", 200)] > 3.0, "");
        }

        // --- ogohlantirish: doim ekranning eng yuqorisida turishi kerak
        {
            String strings = readFile("app/src/main/res/values/strings.xml");
            String layout = readFile("app/src/main/res/layout/activity_main.xml");
            String main = readFile("app/src/main/java/uz/pultanuvchi/MainActivity.java");
            if (strings == null || layout == null || main == null) {
                System.out.println("O'TKAZILDI  ekran va matn tekshiruvlari (loyiha papkasidan ishga tushiring)");
            } else {
                int s0 = strings.indexOf("name=\"disclaimer\"");
                String disclaimer = s0 < 0 ? "" : strings.substring(s0, strings.indexOf("</string>", s0));
                check("ogohlantirish matni: miqdorni aniqlash + haqiqiylik mas'uliyati foydalanuvchida",
                        disclaimer.contains("pulning miqdorini aniqlashga xizmat qiladi")
                                && disclaimer.contains("haqiqiy yoki haqiqiy emasligini aniqlash")
                                && disclaimer.contains("zingizda qoladi"), disclaimer);

                int idView = layout.indexOf("android:id=\"@+id/previewView\"");
                int idNote = layout.indexOf("android:id=\"@+id/tvDisclaimer\"");
                int idPanel = layout.indexOf("android:id=\"@+id/panel\"");
                boolean order = idView >= 0 && idNote > idView && idPanel > idNote;
                String noteElement = "";
                if (idNote >= 0) {
                    int from = layout.lastIndexOf("<TextView", idNote);
                    int to = layout.indexOf("/>", idNote);
                    noteElement = from >= 0 && to > from ? layout.substring(from, to) : "";
                }
                check("ekran: ogohlantirish kamera ustida, eng yuqorida (layout_gravity=top), pastki paneldan oldin",
                        order && noteElement.contains("android:layout_gravity=\"top\"") && noteElement.contains("android:layout_width=\"match_parent\"")
                                && !noteElement.contains("android:visibility"), "");
                check("ekran: ogohlantirish ekran o'quvchiga ko'rinadi va fokus oladi",
                        noteElement.contains("android:importantForAccessibility=\"yes\"") && noteElement.contains("android:focusable=\"true\""), "");
                check("kod: ogohlantirish matni yangilanadi va hech qachon yashirilmaydi",
                        main.contains("tvDisclaimer.setText(t(R.string.disclaimer))") && !main.contains("tvDisclaimer.setVisibility")
                                && !main.contains("tvDisclaimer.setText(\"\")") && !main.contains("tvDisclaimer.setAlpha"), "");
                check("kod: ogohlantirish ovozda ham bir marta aytiladi", main.contains("t(R.string.disclaimer) + \" \" + message"), "");

                String noComments = strings.replaceAll("(?s)<!--.*?-->", "");
                check("strings.xml: ASCII apostrof yo'q (aapt xatosi bermasligi uchun)", !noComments.contains("'"), "");
                boolean xmlOk = true;
                try {
                    javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                    f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(strings.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(layout.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    xmlOk = false;
                }
                check("strings.xml va activity_main.xml to'g'ri tuzilgan XML", xmlOk, "");
            }
        }

        System.out.println();
        System.out.println(failures == 0 ? "HAMMASI YAXSHI (" + total + " ta tekshiruv)" : failures + " ta XATO (" + total + " dan)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
