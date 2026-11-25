package uz.pultanuvchi;

import android.content.Context;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/**
 * Ilovaning o'z ovozi (Android TextToSpeech, tizimdagi tanlangan ovoz dvigateli ishlatiladi).
 * Ekran o'quvchi ishlayotgan bo'lsa, odatda o'chiq turadi (ikki marta o'qilmasligi uchun).
 */
public final class Speaker implements TextToSpeech.OnInitListener {
    /** Dvigatel "tugadi" xabarini bermasa ham, shu vaqtdan keyin gapirmayapti deb hisoblaymiz. */
    private static final long MAX_UTTERANCE_MS = 8000L;

    private TextToSpeech tts;
    private boolean ready = false;
    private int counter = 0;
    private volatile String currentId = "";
    private volatile boolean speaking = false;
    private volatile long speakStartedAt = 0L;

    public Speaker(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            return;
        }
        Locale uz = Locale.forLanguageTag("uz-UZ");
        if (tts.isLanguageAvailable(uz) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(uz);
        }
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                finished(utteranceId);
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onError(String utteranceId) {
                finished(utteranceId);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                finished(utteranceId);
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                finished(utteranceId);
            }
        });
        ready = true;
    }

    /** Faqat eng so'nggi gap tugaganda "gapirmayapti" holatiga o'tamiz (eskisi to'xtatilganda emas). */
    private void finished(String utteranceId) {
        if (utteranceId != null && utteranceId.equals(currentId)) {
            speaking = false;
        }
    }

    /** Ovoz dvigateli ishga tushib bo'ldimi (tayyor bo'lmaguncha speak() hech narsa aytmaydi). */
    public boolean isReady() {
        return ready && tts != null;
    }

    public void speak(String text) {
        if (ready && tts != null) {
            counter++;
            String id = "pul" + counter;
            currentId = id;
            speakStartedAt = SystemClock.elapsedRealtime();
            speaking = true;
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                speaking = false;
            }
        }
    }

    /** Ilova ovozi hozir gapiryaptimi (takroriy e'lon eski gapni bo'lib qo'ymasligi uchun). */
    public boolean isSpeaking() {
        return speaking && SystemClock.elapsedRealtime() - speakStartedAt < MAX_UTTERANCE_MS;
    }

    public void stop() {
        speaking = false;
        if (tts != null) {
            tts.stop();
        }
    }

    public void shutdown() {
        speaking = false;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        ready = false;
    }
}
