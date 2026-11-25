package uz.pultanuvchi;

import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Google Gemini (REST API) bilan aloqa. Kalitni foydalanuvchining o'zi ilovaga kiritadi; u faqat
 * shu telefonda saqlanadi va faqat Google serveriga (HTTPS, sarlavhada) yuboriladi. Kalit hech qachon
 * jurnalga yozilmaydi.
 *
 * Bu sinf tarmoq bilan ishlaydi: uni asosiy oqimdan chaqirmang.
 */
final class GeminiClient {
    static final int OK = 0;
    static final int NETWORK = 1;
    static final int KEY = 2;
    static final int QUOTA = 3;
    static final int OTHER = 4;

    /**
     * So'rov natijasi: xato turi, topilgan banknotaning Banknotes.ALL dagi indeksi (-1 - topilmadi)
     * va HTTP kodi (0 - yo'q).
     */
    static final class Result {
        final int error;
        final int note;
        final int httpCode;

        Result(int error, int note, int httpCode) {
            this.error = error;
            this.note = note;
            this.httpCode = httpCode;
        }
    }

    private static final String TAG = "PulTanuvchi";
    private static final String DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";

    /** Faqat sinov uchun almashtiriladi (tools/ dagi mahalliy soxta server). Ilovada o'zgarmaydi. */
    static volatile String endpoint = DEFAULT_ENDPOINT;

    /**
     * Tezkor va arzon modellar, tartib bilan. Biri topilmasa (eskirib o'chirilsa) yoki limiti tugasa,
     * keyingisi sinab ko'riladi; ishlagani eslab qolinadi. Oxirgisi - Google'ning "eng yangi" nomi.
     */
    private static final String[] MODELS = {
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.6-flash",
            "gemini-3.8-flash",
            "gemini-flash-latest"
    };
    private static volatile int preferred = 0;

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 25000;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;

    private GeminiClient() {
    }

    /** Rasmdagi banknotaning valyutasi va qiymatini aniqlaydi. */
    static Result recognize(String apiKey, byte[] jpeg) {
        return call(apiKey, recognizeBody(Base64.encodeToString(jpeg, Base64.NO_WRAP)), true);
    }

    /** Kalit ishlayotganini qisqa matnli so'rov bilan tekshiradi. */
    static Result check(String apiKey) {
        return call(apiKey, pingBody(), false);
    }

    /** Rasm bilan so'rov tanasi (JSON). Topshiriq matnida JSON uchun maxsus belgilar yo'q (LogicCheck tekshiradi). */
    static String recognizeBody(String base64Jpeg) {
        return "{\"contents\":[{\"parts\":[{\"text\":\"" + AiAnswer.PROMPT + "\"},"
                + "{\"inline_data\":{\"mime_type\":\"image/jpeg\",\"data\":\"" + base64Jpeg + "\"}}]}]}";
    }

    /** Kalitni tekshirish uchun qisqa matnli so'rov tanasi. */
    static String pingBody() {
        return "{\"contents\":[{\"parts\":[{\"text\":\"" + AiAnswer.PING_PROMPT + "\"}]}]}";
    }

    private static Result call(String apiKey, String body, boolean wantNote) {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        Result last = new Result(OTHER, -1, 0);
        int start = preferred;
        for (int attempt = 0; attempt < MODELS.length; attempt++) {
            int index = (start + attempt) % MODELS.length;
            int code;
            String text;
            try {
                HttpURLConnection connection = (HttpURLConnection)
                        URI.create(endpoint + MODELS[index] + ":generateContent").toURL().openConnection();
                try {
                    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    connection.setReadTimeout(READ_TIMEOUT_MS);
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setUseCaches(false);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    connection.setRequestProperty("x-goog-api-key", apiKey);
                    connection.setFixedLengthStreamingMode(payload.length);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(payload);
                    }
                    code = connection.getResponseCode();
                    boolean success = code >= 200 && code < 300;
                    InputStream in = success ? connection.getInputStream() : connection.getErrorStream();
                    text = in == null ? "" : readLimited(in);
                } finally {
                    connection.disconnect();
                }
            } catch (IOException e) {
                // Xabar matnini yozmaymiz (u manzil yoki sarlavhani o'z ichiga olishi mumkin).
                Log.w(TAG, "Gemini: tarmoq xatosi (" + e.getClass().getSimpleName() + ")");
                return new Result(NETWORK, -1, 0);
            } catch (RuntimeException e) {
                Log.w(TAG, "Gemini: so'rovni yuborib bo'lmadi (" + e.getClass().getSimpleName() + ")");
                return new Result(OTHER, -1, 0);
            }

            if (code >= 200 && code < 300) {
                preferred = index;
                if (!wantNote) {
                    return new Result(OK, -1, code);
                }
                return new Result(OK, AiAnswer.parseNote(extractText(text)), code);
            }
            Log.w(TAG, "Gemini: model=" + MODELS[index] + " kod=" + code);
            last = new Result(errorFor(code, text), -1, code);
            if (!shouldTryNextModel(code)) {
                return last;
            }
        }
        return last;
    }

    /** Model topilmadi (404), limit (429) yoki server band (500, 503) bo'lsa, keyingi modelni sinaymiz. */
    static boolean shouldTryNextModel(int code) {
        return code == 404 || code == 429 || code == 500 || code == 503;
    }

    static int errorFor(int code, String body) {
        if (code == 429) {
            return QUOTA;
        }
        if (code == 401 || code == 403) {
            return KEY;
        }
        if (code == 400 && body != null
                && (body.contains("API_KEY_INVALID") || body.contains("API key not valid"))) {
            return KEY;
        }
        return OTHER;
    }

    private static String readLimited(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                if (out.size() >= MAX_RESPONSE_BYTES) {
                    break;
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Javob JSON'idan birinchi nomzodning matnini oladi (fikrlash "thought" qismlari tashlanadi). */
    static String extractText(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                return "";
            }
            JSONObject first = candidates.optJSONObject(0);
            JSONObject content = first == null ? null : first.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            if (parts == null) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null || part.optBoolean("thought", false)) {
                    continue;
                }
                text.append(part.optString("text", ""));
            }
            return text.toString();
        } catch (JSONException e) {
            return "";
        }
    }
}
