package com.exel.watranslator;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.icu.text.Transliterator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText inputText;
    private TextView resultText;
    private Button translateButton;
    private Translator chineseToIndonesian;
    private Translator indonesianToChinese;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService pinyinWorker = Executors.newSingleThreadExecutor();
    private final DownloadConditions downloadConditions = new DownloadConditions.Builder().build();
    private boolean chineseModelReady = false;
    private boolean indonesianModelReady = false;
    private int requestId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(32, 40, 32, 40);

        TextView title = new TextView(this);
        title.setText("Exel WA Translator v0.5");
        title.setTextSize(26);
        title.setPadding(0, 0, 0, 12);
        TextView info = new TextView(this);
        info.setText("Mandarin ↔ Indonesia\nDeteksi bahasa otomatis\nHanzi + Pinyin + Bahasa Indonesia\nUnduhan model pertama dapat memakai data seluler.");
        info.setTextSize(16);
        info.setPadding(0, 0, 0, 24);

        inputText = new EditText(this);
        inputText.setHint("Masukkan teks Mandarin atau Bahasa Indonesia...");
        inputText.setMinLines(5);
        inputText.setGravity(Gravity.TOP);
        inputText.setPadding(20, 20, 20, 20);
        translateButton = new Button(this);
        translateButton.setText("TERJEMAHKAN / COBA LAGI");
        Button copyButton = new Button(this);
        copyButton.setText("SALIN HASIL");
        Button shareButton = new Button(this);
        shareButton.setText("BAGIKAN HASIL");
        Button clearButton = new Button(this);
        clearButton.setText("HAPUS");
        resultText = new TextView(this);
        resultText.setTextSize(18);
        resultText.setPadding(0, 30, 0, 30);
        resultText.setText("Hasil terjemahan akan muncul di sini.");
        main.addView(title);
        main.addView(info);
        main.addView(inputText);
        main.addView(translateButton);
        main.addView(copyButton);
        main.addView(shareButton);
        main.addView(clearButton);
        main.addView(resultText);
        scrollView.addView(main);
        setContentView(scrollView);

        chineseToIndonesian = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.INDONESIAN).build());
        indonesianToChinese = Translation.getClient(new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.INDONESIAN)
                .setTargetLanguage(TranslateLanguage.CHINESE).build());

        translateButton.setOnClickListener(v -> translateAutomatically());
        copyButton.setOnClickListener(v -> copyResult());
        shareButton.setOnClickListener(v -> shareResult());
        clearButton.setOnClickListener(v -> {
            requestId++;
            inputText.setText("");
            resultText.setText("Hasil terjemahan akan muncul di sini.");
        });
        handleSharedText(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSharedText(intent);
    }

    private void translateAutomatically() {
        String text = inputText.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "Masukkan teks terlebih dahulu.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean fromChinese = containsHanzi(text);
        final Translator translator = fromChinese ? chineseToIndonesian : indonesianToChinese;
        final String direction = fromChinese ? "Mandarin → Indonesia" : "Indonesia → Mandarin";
        final int id = ++requestId;
        translateButton.setEnabled(false);
        resultText.setText("Menyiapkan " + direction + "...\nModel bahasa mungkin sedang diunduh melalui data seluler.");
        handler.postDelayed(() -> {
            if (id == requestId && !isFinishing() && !translateButton.isEnabled()) {
                translateButton.setEnabled(true);
                resultText.setText("Persiapan " + direction + " lebih dari 30 detik.\n" +
                        "Periksa koneksi dan ruang penyimpanan. Tekan COBA LAGI jika perlu.\n" +
                        "Unduhan yang sedang berjalan tidak otomatis dibatalkan.");
            }
        }, 30000);
        boolean modelReady = fromChinese ? chineseModelReady : indonesianModelReady;
        if (modelReady) {
            performTranslation(translator, text, fromChinese, id);
        } else {
            // Tidak mensyaratkan Wi-Fi; mengizinkan pengunduhan melalui data seluler.
            translator.downloadModelIfNeeded(downloadConditions)
                    .addOnSuccessListener(unused -> {
                        if (fromChinese) chineseModelReady = true;
                        else indonesianModelReady = true;
                        if (id == requestId && !isFinishing()) {
                            performTranslation(translator, text, fromChinese, id);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("ExelTranslator", "Model preparation failed: " + direction, e);
                        if (id == requestId && !isFinishing()) showError(e);
                    });
        }
    }

    private void performTranslation(Translator translator, String original, boolean fromChinese, int id) {
        if (id != requestId || isFinishing()) return;
        resultText.setText("Menerjemahkan...");
        handler.postDelayed(() -> {
            if (id == requestId && !isFinishing() && !translateButton.isEnabled()) {
                translateButton.setEnabled(true);
                resultText.setText("Proses terjemahan terlalu lama. Silakan tekan COBA LAGI.");
            }
        }, 30000);
        translator.translate(original)
                .addOnSuccessListener(translated -> {
                    if (id != requestId || isFinishing()) return;
                    final String hanzi = fromChinese ? original : translated;
                    final String indonesian = fromChinese ? translated : original;
                    resultText.setText("Menyiapkan Pinyin...");
                    pinyinWorker.execute(() -> {
                        String pinyin = createPinyin(hanzi);
                        handler.post(() -> {
                            if (id != requestId || isFinishing()) return;
                            resultText.setText("Hanzi\n" + hanzi + "\n\nPinyin\n" + pinyin +
                                    "\n\nBahasa Indonesia\n" + indonesian);
                            translateButton.setEnabled(true);
                        });
                    });
                })
                .addOnFailureListener(e -> {
                    Log.e("ExelTranslator", "Translation failed", e);
                    if (id == requestId && !isFinishing()) showError(e);
                });
    }

    private boolean containsHanzi(String text) {
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    private String createPinyin(String hanzi) {
        try {
            return Transliterator.getInstance("Han-Latin").transliterate(hanzi);
        } catch (Exception e) {
            Log.e("ExelTranslator", "Pinyin conversion failed", e);
            return hanzi;
        }
    }

    private void showError(Exception e) {
        translateButton.setEnabled(true);
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        resultText.setText("Terjemahan gagal:\n" + message + "\n\nPeriksa data seluler dan tekan COBA LAGI.");
    }

    private void copyResult() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Exel WA Translator", resultText.getText().toString()));
        Toast.makeText(this, "Hasil sudah disalin.", Toast.LENGTH_SHORT).show();
    }

    private void shareResult() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, resultText.getText().toString());
        startActivity(Intent.createChooser(share, "Bagikan terjemahan"));
    }

    private void handleSharedText(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())
                && intent.getType() != null && intent.getType().startsWith("text/")) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) inputText.setText(sharedText);
        }
    }

    @Override
    protected void onDestroy() {
        requestId++;
        handler.removeCallbacksAndMessages(null);
        pinyinWorker.shutdown();
        if (chineseToIndonesian != null) chineseToIndonesian.close();
        if (indonesianToChinese != null) indonesianToChinese.close();
        super.onDestroy();
    }
}
