package uz.pultanuvchi;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Pul tanuvchi: kamera orqali banknotaning qiymatini va valyutasini aniqlaydi (O'zbekiston so'mi,
 * Turkmaniston manati, AQSh dollari, yevro) va natijani ekran o'quvchi o'qiy oladigan matn
 * (jonli hudud) sifatida chiqaradi. Ekranning eng yuqorisida doim ogohlantirish turadi: ilova faqat
 * miqdorni aniqlaydi, pulning haqiqiy yoki soxta ekanini tekshirish mas'uliyati foydalanuvchida qoladi.
 *
 * Pul ko'rinib turguncha qiymati takrorlab aytiladi (menyudan o'chiriladi), "so'm" so'zini aytish
 * ham menyudan boshqariladi. Ilova pulni o'zi aniqlay olmasa va foydalanuvchi o'z Google kalitini
 * kiritgan bo'lsa, kadr internet orqali Google Gemini'ga yuboriladi (ixtiyoriy imkoniyat).
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PulTanuvchi";
    /** Pul ko'rinmay qolgach, shuncha vaqtdan keyin keyingi pulni yangidan aytamiz. */
    private static final long RELEASE_MS = 2500L;
    /** Kadrning o'rtacha yorqinligi (0..255) shundan past bo'lsa, qorong'i deb hisoblaymiz. */
    private static final int DARK_LUMA = 30;

    /** Ilova o'z ovozi bilan takrorlaganda: ikki e'lon boshlanishi orasidagi eng kam vaqt. */
    private static final long REPEAT_MS = 1200L;
    /** Gap tugagach, keyingi takrorlashdan oldingi qisqa tanaffus. */
    private static final long REPEAT_PAUSE_MS = 400L;
    /** Ekran o'quvchi o'qiyotganda takrorlash oralig'i (u qancha gapirayotganini bilib bo'lmaydi). */
    private static final long REPEAT_READER_MS = 3500L;

    /**
     * Sun'iy intellekt topgan natija ham "Ehtimol, ..." deb aytilsin (xato qilishi mumkin).
     * Ishonchli aytilishini istasangiz, true qiling.
     */
    private static final boolean AI_RESULT_HIGH = false;
    /** Sun'iy intellekt natijasi shuncha vaqt takrorlanadi, so'ng ilova yana o'zi tekshiradi. */
    private static final long AI_HOLD_MS = 8000L;
    /** Sun'iy intellekt natijasi birinchi e'londan keyin yana necha marta takrorlanadi (jami 3). */
    private static final int AI_EXTRA_REPEATS = 2;
    private static final int AI_JPEG_QUALITY = 80;
    /** Bir xil AI xatosi haqidagi xabar shundan tez-tez aytilmaydi. */
    private static final long AI_ERROR_REPEAT_MS = 20000L;
    /** Vaqtinchalik xotiradan shundan uzun matn kalit sifatida olinmaydi. */
    private static final int MAX_CLIPBOARD_CHARS = 300;

    private PreviewView previewView;
    private View panel;
    private TextView tvResult;
    private TextView tvDigits;
    private TextView tvDisclaimer;
    private Button btnRepeat;
    private Button btnTorch;
    private Button btnPause;
    private Button btnMenu;

    private SharedPreferences prefs;
    private ExecutorService cameraExecutor;
    private ExecutorService aiExecutor;
    private Speaker speaker;
    private TextRecognizer recognizer;
    private final DenominationTracker tracker = new DenominationTracker();
    private final AiGate aiGate = new AiGate();
    private Camera camera;

    private ActivityResultLauncher<String> cameraPermission;
    private ActivityResultLauncher<String> pickImage;

    private volatile boolean scanning = false;
    private boolean cameraBound = false;
    private boolean cameraStarting = false;
    private boolean paused = false;
    private boolean torchOn = false;
    private boolean torchTouchedByUser = false;
    private boolean speakerOn = false;
    private boolean newAlphabet = false;
    private boolean permanentlyDenied = false;
    /** Ogohlantirish ovozda dastur ishga tushgach bir marta aytiladi. */
    private boolean disclaimerSpoken = false;

    private boolean repeatOn = true;
    private boolean unitOn = true;
    private boolean aiOn = true;
    private volatile String aiKey = "";
    private volatile boolean aiFrameWanted = false;
    private volatile int aiToken = 0;

    private String lastMessage = "";
    private int lockedIndex = -1;
    private boolean lockedHigh = false;
    private boolean lockedByAi = false;
    private long aiLockUntil = 0L;
    private int repeatsLeft = 0;
    private long nextRepeatAt = 0L;
    private long lastSpeakingAt = 0L;
    private long lastEvidenceAt = 0L;
    private long lastHintAt = 0L;
    private long scanStartAt = 0L;
    private String lastHintKey = "";
    private int hintStreak = 0;
    private int darkFrames = 0;
    private int lastAiErrorKind = -1;
    private long lastAiErrorAt = 0L;

    // ------------------------------------------------------------ hayot sikli

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        previewView = findViewById(R.id.previewView);
        panel = findViewById(R.id.panel);
        tvResult = findViewById(R.id.tvResult);
        tvDigits = findViewById(R.id.tvDigits);
        tvDisclaimer = findViewById(R.id.tvDisclaimer);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnTorch = findViewById(R.id.btnTorch);
        btnPause = findViewById(R.id.btnPause);
        btnMenu = findViewById(R.id.btnMenu);

        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE);
        newAlphabet = prefs.getBoolean("newAlphabet", false);
        repeatOn = prefs.getBoolean("repeatOn", true);
        unitOn = prefs.getBoolean("unitOn", true);
        aiOn = prefs.getBoolean("aiOn", true);
        String savedKey = AiAnswer.cleanKey(prefs.getString("aiKey", ""));
        aiKey = AiAnswer.isPlausibleKey(savedKey) ? savedKey : "";

        cameraExecutor = Executors.newSingleThreadExecutor();
        aiExecutor = Executors.newCachedThreadPool();
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        speaker = new Speaker(this);
        speakerOn = !screenReaderActive();

        cameraPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        startCameraIfNeeded();
                    } else {
                        permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                                this, Manifest.permission.CAMERA);
                        show(t(R.string.camera_denied), "");
                    }
                });
        pickImage = registerForActivityResult(
                new ActivityResultContracts.GetContent(), uri -> {
                    if (uri != null) {
                        recognizeStill(uri);
                    }
                });

        final View root = findViewById(R.id.root);
        final int baseLeft = panel.getPaddingLeft();
        final int baseTop = panel.getPaddingTop();
        final int baseRight = panel.getPaddingRight();
        final int baseBottom = panel.getPaddingBottom();
        final int noteLeft = tvDisclaimer.getPaddingLeft();
        final int noteTop = tvDisclaimer.getPaddingTop();
        final int noteRight = tvDisclaimer.getPaddingRight();
        final int noteBottom = tvDisclaimer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            panel.setPadding(baseLeft + bars.left, baseTop, baseRight + bars.right, baseBottom + bars.bottom);
            // Ogohlantirish yozuvi holat paneli va kameraning kesigi ostida qolib ketmasin
            tvDisclaimer.setPadding(noteLeft + bars.left, noteTop + bars.top, noteRight + bars.right, noteBottom);
            return insets;
        });

        btnRepeat.setOnClickListener(v -> onRepeat());
        btnTorch.setOnClickListener(v -> toggleTorch(true));
        btnPause.setOnClickListener(v -> togglePause());
        btnMenu.setOnClickListener(v -> showMenu());

        refreshLabels();
        show(t(R.string.starting), "");

        if (!hasCameraPermission()) {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraIfNeeded();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanning = false;
        aiFrameWanted = false;
        speaker.shutdown();
        recognizer.close();
        cameraExecutor.shutdown();
        aiExecutor.shutdownNow();
    }

    // ---------------------------------------------------------------- kamera

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCameraIfNeeded() {
        if (hasCameraPermission() && !cameraBound && !cameraStarting) {
            startCamera();
        }
    }

    private void startCamera() {
        cameraStarting = true;
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ResolutionSelector selector = new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 960),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build();
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(selector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);

                cameraBound = true;
                cameraStarting = false;
                scanning = !paused;
                scanStartAt = SystemClock.elapsedRealtime();
                lastHintAt = 0L;
                aiGate.forgetText();
                aiGate.restartEpisode(scanStartAt);
                show(t(R.string.ready), "");
            } catch (Exception e) {
                Log.e(TAG, "Kamera ishga tushmadi", e);
                cameraStarting = false;
                show(t(R.string.camera_error), "");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /** Kamera oqimidan kelgan har bir kadrni matn aniqlashga beradi. */
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrame(@NonNull ImageProxy proxy) {
        Image media = proxy.getImage();
        if (media == null || !scanning) {
            proxy.close();
            return;
        }
        try {
            final int rotation = proxy.getImageInfo().getRotationDegrees();
            if (aiFrameWanted) {
                aiFrameWanted = false;
                captureForAi(media, rotation);
            }
            final int luma = averageLuma(proxy);
            InputImage image = InputImage.fromMediaImage(media, rotation);
            recognizer.process(image)
                    .addOnSuccessListener(result -> onRecognized(result, luma))
                    .addOnCompleteListener(task -> proxy.close());
        } catch (RuntimeException e) {
            Log.w(TAG, "Kadrni tahlil qilib bo'lmadi", e);
            proxy.close();
        }
    }

    /** Kadrning o'rtacha yorqinligi (Y tekisligidan, siyrak tanlab olinadi). */
    private static int averageLuma(ImageProxy proxy) {
        ImageProxy.PlaneProxy plane = proxy.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int width = proxy.getWidth();
        int height = proxy.getHeight();
        long sum = 0;
        int count = 0;
        for (int y = 0; y < height; y += 16) {
            for (int x = 0; x < width; x += 16) {
                int index = y * rowStride + x * pixelStride;
                if (index < buffer.limit()) {
                    sum += buffer.get(index) & 0xFF;
                    count++;
                }
            }
        }
        return count == 0 ? 128 : (int) (sum / count);
    }

    private static List<String> linesOf(Text text) {
        List<String> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                lines.add(line.getText());
            }
        }
        return lines;
    }

    // ------------------------------------------------------- aniqlash va natija

    private void onRecognized(Text result, int luma) {
        if (!scanning) {
            return;
        }
        long now = SystemClock.elapsedRealtime();

        if (luma < DARK_LUMA) {
            darkFrames++;
        } else {
            darkFrames = 0;
        }
        if (darkFrames >= 3 && !torchOn && !torchTouchedByUser && hasFlash()) {
            setTorch(true);
            darkFrames = 0;
            lastHintAt = now;
            show(t(R.string.torch_auto), "");
            return;
        }

        FrameEvidence evidence = BanknoteMatcher.analyze(linesOf(result));
        aiGate.onFrame(now, evidence.hasText);
        if (evidence.anyEvidence()) {
            lastEvidenceAt = now;
        }

        // Pul ko'rinmay qoldi: keyingi pulni yangidan aytishga tayyor bo'lamiz.
        if (lockedIndex >= 0 && lockExpired(now)) {
            releaseLock(now);
        }

        Decision decision = tracker.update(evidence);
        if (decision != null) {
            boolean changed = decision.index != lockedIndex;
            boolean upgraded = !changed && decision.high && !lockedHigh;
            if (changed || upgraded) {
                lockedIndex = decision.index;
                lockedHigh = decision.high;
                lockedByAi = false;
                repeatsLeft = 0;
                lastEvidenceAt = now;
                lastHintAt = now;
                announce(decision.index, decision.high);
            }
            maybeRepeat(now);
            return;
        }
        if (lockedIndex >= 0) {
            maybeRepeat(now);
            return;
        }
        maybeAskAi(now);
        maybeHint(now, evidence);
    }

    /** Pul ko'rinmay qolganmi (sun'iy intellekt natijasi uchun esa: vaqti tugadimi). */
    private boolean lockExpired(long now) {
        if (lockedByAi) {
            return now >= aiLockUntil || !aiGate.textSeenWithin(now, RELEASE_MS);
        }
        return now - lastEvidenceAt > RELEASE_MS;
    }

    private void releaseLock(long now) {
        lockedIndex = -1;
        lockedHigh = false;
        lockedByAi = false;
        repeatsLeft = 0;
        tracker.reset();
        lastHintAt = now;
        aiGate.restartEpisode(now);
    }

    /**
     * Ovozda aytiladigan va ekranda chiqadigan matn ("Ming so'm", "Yuz dollar" yoki "Ehtimol, ming").
     * So'mdan boshqa valyutada valyuta nomi doim aytiladi.
     */
    private String messageFor(int index, boolean high) {
        String spoken = alpha(Banknotes.spokenText(Banknotes.ALL.get(index), unitOn));
        return high ? cap(spoken) : t(R.string.probably) + " " + spoken;
    }

    /** Ekrandagi raqamli ko'rinish ("10 000 so'm", "100 USD"). */
    private String digitsFor(int index, boolean high) {
        return (high ? "" : "\u2248 ") + alpha(Banknotes.digitsText(Banknotes.ALL.get(index), unitOn));
    }

    private void announce(int index, boolean high) {
        long now = SystemClock.elapsedRealtime();
        show(messageFor(index, high), digitsFor(index, high));
        vibrate(high ? 150L : 70L);
        nextRepeatAt = now + (speakerOn ? REPEAT_MS : REPEAT_READER_MS);
        lastSpeakingAt = now;
    }

    /** Pul ko'rinib turgan paytda natijani yana va yana aytadi (menyudan o'chirish mumkin). */
    private void maybeRepeat(long now) {
        if (!repeatOn || lockedIndex < 0 || now < nextRepeatAt) {
            return;
        }
        if (lockedByAi && repeatsLeft <= 0) {
            return;
        }
        if (speakerOn) {
            if (speaker.isSpeaking()) {
                lastSpeakingAt = now;
                return;
            }
            if (now - lastSpeakingAt < REPEAT_PAUSE_MS) {
                return;
            }
        }
        String message = messageFor(lockedIndex, lockedHigh);
        if (speakerOn) {
            speaker.speak(message);
            lastSpeakingAt = now;
        } else {
            tvResult.announceForAccessibility(message);
        }
        nextRepeatAt = now + (speakerOn ? REPEAT_MS : REPEAT_READER_MS);
        if (lockedByAi) {
            repeatsLeft--;
        }
    }

    private void maybeHint(long now, FrameEvidence evidence) {
        if (aiGate.isBusy(now)) {
            return;
        }
        long interval = hintStreak >= 3 ? 15000L : 6000L;
        long reference = lastHintAt > 0L ? lastHintAt : scanStartAt;
        if (now - reference < interval) {
            return;
        }
        String key;
        int textId;
        if (darkFrames >= 3) {
            key = "dark";
            textId = R.string.hint_dark;
        } else if (tracker.isAmbiguous()) {
            key = "several";
            textId = R.string.hint_several;
        } else if (evidence.anyEvidence() || now - lastEvidenceAt < 3000L) {
            key = "found";
            textId = R.string.hint_found;
        } else {
            key = "none";
            textId = R.string.hint_none;
        }
        hintStreak = key.equals(lastHintKey) ? hintStreak + 1 : 1;
        lastHintKey = key;
        lastHintAt = now;
        show(t(textId), "");
    }

    // ------------------------------------------------- sun'iy intellekt yordami

    private boolean aiReady() {
        return aiOn && !aiKey.isEmpty();
    }

    /** Ilova pulni o'zi aniqlay olmasa, keyingi kadrni Google Gemini'ga yuborishni boshlaydi. */
    private void maybeAskAi(long now) {
        if (!aiReady() || !scanning || tracker.isAmbiguous() || !aiGate.shouldAsk(now)) {
            return;
        }
        aiToken = aiGate.begin(now);
        aiFrameWanted = true;
        lastHintAt = now;
        show(t(R.string.ai_checking), "");
    }

    /** Kamera oqimida chaqiriladi: kadrni JPEG qilib, internet orqali yuboradi. */
    private void captureForAi(Image media, int rotation) {
        final int token = aiToken;
        final String key = aiKey;
        byte[] jpeg = null;
        try {
            jpeg = FrameJpeg.fromImage(media, rotation, AI_JPEG_QUALITY);
        } catch (RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "Kadrni rasmga aylantirib bo'lmadi", e);
        }
        if (jpeg == null || key.isEmpty()) {
            postAiResult(token, new GeminiClient.Result(GeminiClient.OTHER, -1, 0));
            return;
        }
        final byte[] data = jpeg;
        try {
            aiExecutor.execute(() -> postAiResult(token, GeminiClient.recognize(key, data)));
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "Sun'iy intellekt so'rovi boshlanmadi", e);
        }
    }

    private void postAiResult(final int token, final GeminiClient.Result result) {
        runOnUiThread(() -> onAiResult(token, result));
    }

    private void onAiResult(int token, GeminiClient.Result r) {
        if (isDestroyed()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        boolean found = r.error == GeminiClient.OK && r.note >= 0;
        if (!aiGate.end(token, now, found)) {
            return; // eskirgan javob (bu orada to'xtatilgan yoki bekor qilingan)
        }
        if (!scanning || !aiOn) {
            return;
        }
        if (r.error != GeminiClient.OK) {
            reportAiError(r.error, r.httpCode, now);
            return;
        }
        if (lockedIndex >= 0) {
            return; // bu orada ilovaning o'zi aniqladi: uning natijasi ustun
        }
        if (!found) {
            lastHintAt = now;
            show(t(R.string.ai_none), "");
            return;
        }
        int index = r.note;
        if (index < 0 || index >= Banknotes.ALL.size()) {
            return;
        }
        lockedIndex = index;
        lockedHigh = AI_RESULT_HIGH;
        lockedByAi = true;
        aiLockUntil = now + AI_HOLD_MS;
        repeatsLeft = AI_EXTRA_REPEATS;
        lastHintAt = now;
        announce(index, AI_RESULT_HIGH);
    }

    private void reportAiError(int error, int httpCode, long now) {
        if (error == GeminiClient.KEY) {
            // Kalit qabul qilinmadi: bekorga qayta-qayta urinmaslik uchun yordamni o'chiramiz.
            aiOn = false;
            prefs.edit().putBoolean("aiOn", false).apply();
        }
        if (error == lastAiErrorKind && now - lastAiErrorAt < AI_ERROR_REPEAT_MS) {
            return;
        }
        lastAiErrorKind = error;
        lastAiErrorAt = now;
        lastHintAt = now;
        show(aiErrorText(error, httpCode), "");
    }

    private String aiErrorText(int error, int httpCode) {
        switch (error) {
            case GeminiClient.NETWORK:
                return t(R.string.ai_err_net);
            case GeminiClient.KEY:
                return t(R.string.ai_err_key);
            case GeminiClient.QUOTA:
                return t(R.string.ai_err_quota);
            default:
                return httpCode > 0 ? tf(R.string.ai_err_http, httpCode) : t(R.string.ai_err_other);
        }
    }

    private void onAiKeyChecked(GeminiClient.Result r) {
        if (isDestroyed()) {
            return;
        }
        if (r.error == GeminiClient.OK) {
            show(t(R.string.ai_key_ok), "");
            return;
        }
        if (r.error == GeminiClient.KEY) {
            aiOn = false;
            prefs.edit().putBoolean("aiOn", false).apply();
        }
        show(aiErrorText(r.error, r.httpCode), "");
    }

    /** Google kalitini kiritish oynasi: har bir foydalanuvchi o'z kalitini qo'shadi. */
    private void showAiKeyDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setSingleLine(true);
        input.setHint(t(R.string.ai_key_hint));

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(pad, pad / 2, pad, 0);
        holder.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(t(R.string.ai_dialog_title))
                .setMessage(t(R.string.ai_dialog_help) + "\n\n"
                        + t(aiKey.isEmpty() ? R.string.ai_status_none : R.string.ai_status_saved))
                .setView(holder)
                .setPositiveButton(t(R.string.ai_save), null)
                .setNeutralButton(t(R.string.ai_paste), null)
                .setNegativeButton(t(R.string.close), null)
                .create();
        // Tugmalar xato kiritilganda oynani yopib qo'ymasligi uchun bosilishini o'zimiz boshqaramiz.
        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> saveAiKey(input, dialog));
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> pasteAiKey(input));
        });
        dialog.show();
    }

    private void saveAiKey(EditText input, AlertDialog dialog) {
        String key = AiAnswer.cleanKey(input.getText().toString());
        if (key.isEmpty()) {
            if (aiKey.isEmpty()) {
                input.setError(t(R.string.ai_key_invalid));
                return;
            }
            // Maydon bo'sh qoldirilib saqlansa, kalit o'chiriladi.
            aiKey = "";
            aiFrameWanted = false;
            aiGate.cancel();
            prefs.edit().remove("aiKey").apply();
            dialog.dismiss();
            show(t(R.string.ai_removed), "");
            return;
        }
        if (!AiAnswer.isPlausibleKey(key)) {
            input.setError(t(R.string.ai_key_invalid));
            return;
        }
        aiKey = key;
        aiOn = true;
        aiGate.clearCooldown();
        prefs.edit().putString("aiKey", key).putBoolean("aiOn", true).apply();
        dialog.dismiss();
        show(t(R.string.ai_saved_checking), "");
        try {
            aiExecutor.execute(() -> {
                final GeminiClient.Result r = GeminiClient.check(key);
                runOnUiThread(() -> onAiKeyChecked(r));
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "Kalitni tekshirib bo'lmadi", e);
        }
    }

    private void pasteAiKey(EditText input) {
        String key = "";
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).coerceToText(this);
                if (text != null && text.length() <= MAX_CLIPBOARD_CHARS) {
                    key = AiAnswer.cleanKey(text.toString());
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Vaqtinchalik xotirani o'qib bo'lmadi", e);
        }
        if (!AiAnswer.isPlausibleKey(key)) {
            input.setError(t(R.string.ai_clipboard_empty));
            return;
        }
        input.setError(null);
        input.setText(key);
        input.setSelection(key.length());
    }

    // ----------------------------------------------------------- galereyadan rasm

    private void recognizeStill(Uri uri) {
        paused = true;
        scanning = false;
        aiFrameWanted = false;
        aiGate.cancel();
        refreshLabels();
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            show(t(R.string.gallery_working), "");
            recognizer.process(image)
                    .addOnSuccessListener(this::onStillRecognized)
                    .addOnFailureListener(e -> show(t(R.string.gallery_error), ""));
        } catch (Exception e) {
            Log.w(TAG, "Rasmni ochib bo'lmadi", e);
            show(t(R.string.gallery_error), "");
        }
    }

    private void onStillRecognized(Text result) {
        FrameEvidence evidence = BanknoteMatcher.analyze(linesOf(result));
        Decision decision = BanknoteMatcher.decideStill(evidence);
        if (decision == null) {
            show(t(R.string.gallery_none), "");
        } else {
            announce(decision.index, decision.high);
        }
    }

    // ------------------------------------------------------------- tugmalar

    private void onRepeat() {
        if (!hasCameraPermission()) {
            if (permanentlyDenied) {
                openAppSettings();
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA);
            }
            return;
        }
        if (lastMessage.isEmpty()) {
            show(t(R.string.nothing_to_repeat), "");
            return;
        }
        if (speakerOn) {
            speaker.speak(lastMessage);
        } else {
            tvResult.announceForAccessibility(lastMessage);
        }
    }

    private boolean hasFlash() {
        return camera != null && camera.getCameraInfo().hasFlashUnit();
    }

    private void setTorch(boolean on) {
        if (!hasFlash()) {
            return;
        }
        torchOn = on;
        camera.getCameraControl().enableTorch(on);
        refreshLabels();
    }

    private void toggleTorch(boolean byUser) {
        if (!hasFlash()) {
            show(t(R.string.no_flash), "");
            return;
        }
        if (byUser) {
            torchTouchedByUser = true;
        }
        setTorch(!torchOn);
    }

    private void togglePause() {
        paused = !paused;
        scanning = cameraBound && !paused;
        if (paused) {
            speaker.stop();
            aiFrameWanted = false;
            aiGate.cancel();
            show(t(R.string.paused), "");
        } else {
            tracker.reset();
            lockedIndex = -1;
            lockedHigh = false;
            lockedByAi = false;
            repeatsLeft = 0;
            lastHintAt = 0L;
            scanStartAt = SystemClock.elapsedRealtime();
            aiGate.forgetText();
            aiGate.restartEpisode(scanStartAt);
            show(t(R.string.resumed), "");
        }
        refreshLabels();
    }

    private void showMenu() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        labels.add(t(R.string.menu_gallery));
        actions.add(() -> pickImage.launch("image/*"));
        labels.add(t(speakerOn ? R.string.menu_voice_off : R.string.menu_voice_on));
        actions.add(this::toggleVoice);
        labels.add(t(repeatOn ? R.string.menu_repeat_off : R.string.menu_repeat_on));
        actions.add(this::toggleRepeat);
        labels.add(t(unitOn ? R.string.menu_unit_off : R.string.menu_unit_on));
        actions.add(this::toggleUnit);
        labels.add(t(R.string.menu_ai_key));
        actions.add(this::showAiKeyDialog);
        if (!aiKey.isEmpty()) {
            labels.add(t(aiOn ? R.string.menu_ai_off : R.string.menu_ai_on));
            actions.add(this::toggleAi);
        }
        labels.add(t(R.string.menu_alphabet));
        actions.add(this::toggleAlphabet);
        labels.add(t(R.string.menu_about));
        actions.add(this::showAbout);

        new AlertDialog.Builder(this)
                .setTitle(t(R.string.menu_title))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .setNegativeButton(t(R.string.close), null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(t(R.string.app_name))
                .setMessage(t(R.string.about_text))
                .setPositiveButton(t(R.string.close), null)
                .show();
    }

    private void toggleVoice() {
        speakerOn = !speakerOn;
        if (!speakerOn) {
            speaker.stop();
        }
        show(t(speakerOn ? R.string.voice_on_msg : R.string.voice_off_msg), "");
    }

    private void toggleRepeat() {
        repeatOn = !repeatOn;
        prefs.edit().putBoolean("repeatOn", repeatOn).apply();
        show(t(repeatOn ? R.string.repeat_on_msg : R.string.repeat_off_msg), "");
    }

    private void toggleUnit() {
        unitOn = !unitOn;
        prefs.edit().putBoolean("unitOn", unitOn).apply();
        show(t(unitOn ? R.string.unit_on_msg : R.string.unit_off_msg), "");
    }

    private void toggleAi() {
        aiOn = !aiOn;
        prefs.edit().putBoolean("aiOn", aiOn).apply();
        if (aiOn) {
            aiGate.clearCooldown();
        } else {
            aiFrameWanted = false;
            aiGate.cancel();
        }
        show(t(aiOn ? R.string.ai_on_msg : R.string.ai_off_msg), "");
    }

    private void toggleAlphabet() {
        newAlphabet = !newAlphabet;
        prefs.edit().putBoolean("newAlphabet", newAlphabet).apply();
        refreshLabels();
        show(t(newAlphabet ? R.string.alphabet_new_on : R.string.alphabet_old_on), "");
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    // ------------------------------------------------------------ yordamchilar

    /** Natijani ekranga chiqaradi (jonli hudud bo'lgani uchun ekran o'quvchi o'zi o'qiydi). */
    private void show(String message, String digits) {
        lastMessage = message;
        tvResult.setText(message);
        tvDigits.setText(digits);
        tvDigits.setVisibility(digits.isEmpty() ? View.GONE : View.VISIBLE);
        if (speakerOn) {
            String spoken = message;
            if (!disclaimerSpoken && speaker.isReady()) {
                // Ogohlantirish ekranning tepasida doim turadi; ilovaning o'z ovozi bilan ishlaganda
                // uni dastur ishga tushgach birinchi gapdan oldin bir marta ham aytamiz.
                disclaimerSpoken = true;
                spoken = t(R.string.disclaimer) + " " + message;
            }
            speaker.speak(spoken);
        }
    }

    private void refreshLabels() {
        tvDisclaimer.setText(t(R.string.disclaimer));
        btnRepeat.setText(t(R.string.btn_repeat));
        btnTorch.setText(t(torchOn ? R.string.btn_torch_off : R.string.btn_torch_on));
        btnPause.setText(t(paused ? R.string.btn_resume : R.string.btn_pause));
        btnMenu.setText(t(R.string.btn_menu));
    }

    private String t(int resId) {
        return alpha(getString(resId));
    }

    private String tf(int resId, Object... args) {
        return alpha(getString(resId, args));
    }

    private String alpha(String s) {
        return newAlphabet ? Alphabet.toNew(s) : s;
    }

    private static String cap(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private boolean screenReaderActive() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Titrash ishlamadi", e);
        }
    }
}
