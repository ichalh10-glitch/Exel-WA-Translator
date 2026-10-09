package com.exel.watranslator;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.icu.text.Transliterator;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

public class MainActivity extends Activity {

    private EditText inputText;
    private TextView resultText;

    private Translator chineseToIndonesian;
    private Translator indonesianToChinese;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // =========================
        // TAMPILAN UTAMA
        // =========================

        ScrollView scrollView = new ScrollView(this);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(32, 40, 32, 40);

        TextView title = new TextView(this);
        title.setText("Exel WA Translator");
        title.setTextSize(26);
        title.setPadding(0, 0, 0, 12);

        TextView info = new TextView(this);
        info.setText(
                "Mandarin ↔ Indonesia\n" +
                "Deteksi bahasa otomatis\n" +
                "Hanzi + Pinyin + Bahasa Indonesia"
        );
        info.setTextSize(16);
        info.setPadding(0, 0, 0, 24);

        inputText = new EditText(this);
        inputText.setHint(
                "Masukkan teks Mandarin atau Bahasa Indonesia..."
        );
        inputText.setMinLines(5);
        inputText.setGravity(android.view.Gravity.TOP);
        inputText.setPadding(20, 20, 20, 20);

        Button translateButton = new Button(this);
        translateButton.setText("TERJEMAHKAN");

        Button copyButton = new Button(this);
        copyButton.setText("SALIN HASIL");

        Button shareButton = new Button(this);
        shareButton.setText("BAGIKAN HASIL");

        Button clearButton = new Button(this);
        clearButton.setText("HAPUS");

        resultText = new TextView(this);
        resultText.setTextSize(18);
        resultText.setPadding(0, 30, 0, 30);
        resultText.setText(
                "Hasil terjemahan akan muncul di sini."
        );

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

        // =========================
        // TRANSLATOR
        // =========================

        TranslatorOptions chineseOptions =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(
                                TranslateLanguage.CHINESE
                        )
                        .setTargetLanguage(
                                TranslateLanguage.INDONESIAN
                        )
                        .build();

        chineseToIndonesian =
                Translation.getClient(chineseOptions);

        TranslatorOptions indonesianOptions =
                new TranslatorOptions.Builder()
                        .setSourceLanguage(
                                TranslateLanguage.INDONESIAN
                        )
                        .setTargetLanguage(
                                TranslateLanguage.CHINESE
                        )
                        .build();

        indonesianToChinese =
                Translation.getClient(indonesianOptions);

        // =========================
        // TOMBOL
        // =========================

        translateButton.setOnClickListener(
                v -> translateAutomatically()
        );

        copyButton.setOnClickListener(
                v -> copyResult()
        );

        shareButton.setOnClickListener(
                v -> shareResult()
        );

        clearButton.setOnClickListener(v -> {

            inputText.setText("");

            resultText.setText(
                    "Hasil terjemahan akan muncul di sini."
            );
        });

        // =========================
        // TERIMA SHARE WHATSAPP
        // =========================

        handleSharedText(getIntent());
    }

    // =============================================
    // DETEKSI OTOMATIS MANDARIN / INDONESIA
    // =============================================

    private void translateAutomatically() {

        String text =
                inputText
                        .getText()
                        .toString()
                        .trim();

        if (TextUtils.isEmpty(text)) {

            Toast.makeText(
                    this,
                    "Masukkan teks terlebih dahulu.",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (containsHanzi(text)) {

            translateChineseToIndonesian(text);

        } else {

            translateIndonesianToChinese(text);
        }
    }

    // =============================================
    // CEK APAKAH ADA KARAKTER HANZI
    // =============================================

    private boolean containsHanzi(String text) {

        for (int i = 0; i < text.length(); ) {

            int codePoint =
                    text.codePointAt(i);

            Character.UnicodeScript script =
                    Character.UnicodeScript.of(
                            codePoint
                    );

            if (script ==
                    Character.UnicodeScript.HAN) {

                return true;
            }

            i += Character.charCount(
                    codePoint
            );
        }

        return false;
    }

    // =============================================
    // MANDARIN → INDONESIA
    // =============================================

    private void translateChineseToIndonesian(
            String hanzi
    ) {

        resultText.setText(
                "Menyiapkan Mandarin → Indonesia..."
        );

        chineseToIndonesian
                .downloadModelIfNeeded()
                .addOnSuccessListener(
                        unused -> {

                            resultText.setText(
                                    "Menerjemahkan..."
                            );

                            chineseToIndonesian
                                    .translate(hanzi)

                                    .addOnSuccessListener(
                                            indonesian -> {

                                                String pinyin =
                                                        createPinyin(
                                                                hanzi
                                                        );

                                                String output =

                                                        "Hanzi\n" +
                                                        hanzi +

                                                        "\n\nPinyin\n" +
                                                        pinyin +

                                                        "\n\nBahasa Indonesia\n" +
                                                        indonesian;

                                                resultText.setText(
                                                        output
                                                );
                                            }
                                    )

                                    .addOnFailureListener(
                                            this::showError
                                    );
                        }
                )

                .addOnFailureListener(
                        this::showError
                );
    }

    // =============================================
    // INDONESIA → MANDARIN
    // =============================================

    private void translateIndonesianToChinese(
            String indonesian
    ) {

        resultText.setText(
                "Menyiapkan Indonesia → Mandarin..."
        );

        indonesianToChinese
                .downloadModelIfNeeded()

                .addOnSuccessListener(
                        unused -> {

                            resultText.setText(
                                    "Menerjemahkan..."
                            );

                            indonesianToChinese
                                    .translate(indonesian)

                                    .addOnSuccessListener(
                                            hanzi -> {

                                                String pinyin =
                                                        createPinyin(
                                                                hanzi
                                                        );

                                                String output =

                                                        "Hanzi\n" +
                                                        hanzi +

                                                        "\n\nPinyin\n" +
                                                        pinyin +

                                                        "\n\nBahasa Indonesia\n" +
                                                        indonesian;

                                                resultText.setText(
                                                        output
                                                );
                                            }
                                    )

                                    .addOnFailureListener(
                                            this::showError
                                    );
                        }
                )

                .addOnFailureListener(
                        this::showError
                );
    }

    // =============================================
    // HANZI → PINYIN
    // =============================================

    private String createPinyin(
            String hanzi
    ) {

        try {

            Transliterator transliterator =
                    Transliterator.getInstance(
                            "Han-Latin"
                    );

            return transliterator
                    .transliterate(hanzi);

        } catch (Exception e) {

            return hanzi;
        }
    }

    // =============================================
    // ERROR
    // =============================================

    private void showError(
            Exception exception
    ) {

        String message =
                exception.getMessage();

        if (message == null) {

            message =
                    exception.toString();
        }

        resultText.setText(
                "Terjemahan gagal:\n" +
                message
        );
    }

    // =============================================
    // COPY
    // =============================================

    private void copyResult() {

        String result =
                resultText
                        .getText()
                        .toString();

        ClipboardManager clipboard =
                (ClipboardManager)
                        getSystemService(
                                Context.CLIPBOARD_SERVICE
                        );

        ClipData clip =
                ClipData.newPlainText(
                        "Exel WA Translator",
                        result
                );

        clipboard.setPrimaryClip(
                clip
        );

        Toast.makeText(
                this,
                "Hasil sudah disalin.",
                Toast.LENGTH_SHORT
        ).show();
    }

    // =============================================
    // SHARE
    // =============================================

    private void shareResult() {

        String result =
                resultText
                        .getText()
                        .toString();

        Intent shareIntent =
                new Intent(
                        Intent.ACTION_SEND
                );

        shareIntent.setType(
                "text/plain"
        );

        shareIntent.putExtra(
                Intent.EXTRA_TEXT,
                result
        );

        startActivity(
                Intent.createChooser(
                        shareIntent,
                        "Bagikan terjemahan"
                )
        );
    }

    // =============================================
    // SHARE DARI WHATSAPP
    // =============================================

    private void handleSharedText(
            Intent intent
    ) {

        if (intent == null) {

            return;
        }

        if (Intent.ACTION_SEND.equals(
                intent.getAction()
        )) {

            String type =
                    intent.getType();

            if (type != null &&
                    type.startsWith(
                            "text/"
                    )) {

                String sharedText =
                        intent.getStringExtra(
                                Intent.EXTRA_TEXT
                        );

                if (sharedText != null) {

                    inputText.setText(
                            sharedText
                    );

                    Toast.makeText(
                            this,
                            "Teks WhatsApp diterima.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }
        }
    }

    // =============================================
    // TUTUP TRANSLATOR
    // =============================================

    @Override
    protected void onDestroy() {

        if (chineseToIndonesian != null) {

            chineseToIndonesian.close();
        }

        if (indonesianToChinese != null) {

            indonesianToChinese.close();
        }

        super.onDestroy();
    }
}
