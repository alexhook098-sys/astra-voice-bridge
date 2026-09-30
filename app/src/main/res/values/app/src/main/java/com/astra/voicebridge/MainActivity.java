package com.astra.voicebridge;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private TextToSpeech tts;
    private TextView statusText;
    private EditText textInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);

        TextView title = new TextView(this);
        title.setText("ASTRA Voice Bridge");
        title.setTextSize(26);

        textInput = new EditText(this);
        textInput.setText("Привет. Я ASTRA. Это тест моего голоса.");
        textInput.setTextSize(18);

        Button speakButton = new Button(this);
        speakButton.setText("Создать WAV");

        statusText = new TextView(this);
        statusText.setText("Запуск TTS...");
        statusText.setTextSize(18);

        layout.addView(title);
        layout.addView(textInput);
        layout.addView(speakButton);
        layout.addView(statusText);

        setContentView(layout);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {

                int result = tts.setLanguage(new Locale("ru", "RU"));

                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    statusText.setText("Русский язык TTS недоступен");
                } else {
                    statusText.setText("TTS готов. Нажми «Создать WAV».");
                }

            } else {
                statusText.setText("Не удалось запустить TTS");
            }
        });

        speakButton.setOnClickListener(v -> createWav());

        Intent intent = getIntent();
        String incomingText = intent.getStringExtra("text");

        if (incomingText != null && !incomingText.isEmpty()) {
            textInput.setText(incomingText);
        }
    }

    private void createWav() {

        if (tts == null) {
            statusText.setText("TTS ещё не готов");
            return;
        }

        String text = textInput.getText().toString().trim();

        if (text.isEmpty()) {
            statusText.setText("Нет текста");
            return;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME,
                "astra_" + System.currentTimeMillis() + ".wav");
        values.put(MediaStore.Downloads.MIME_TYPE, "audio/wav");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(
                MediaStore.Downloads.RELATIVE_PATH,
                "Download/ASTRA"
            );
            values.put(MediaStore.Downloads.IS_PENDING, 1);
        }

        Uri uri = getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
        );

        if (uri == null) {
            statusText.setText("Не удалось создать WAV");
            return;
        }

        try {

            ParcelFileDescriptor pfd =
                    getContentResolver().openFileDescriptor(uri, "w");

            if (pfd == null) {
                statusText.setText("Не удалось открыть файл");
                return;
            }

            String utteranceId = "astra_" + System.currentTimeMillis();

            tts.setOnUtteranceProgressListener(
                new android.speech.tts.UtteranceProgressListener() {

                    @Override
                    public void onStart(String id) {
                    }

                    @Override
                    public void onDone(String id) {

                        try {
                            pfd.close();
                        } catch (Exception ignored) {
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ContentValues done = new ContentValues();
                            done.put(MediaStore.Downloads.IS_PENDING, 0);

                            getContentResolver().update(
                                uri,
                                done,
                                null,
                                null
                            );
                        }

                        runOnUiThread(() ->
                            statusText.setText(
                                "ГОТОВО!\nWAV сохранён в Download/ASTRA"
                            )
                        );
                    }

                    @Override
                    public void onError(String id) {

                        try {
                            pfd.close();
                        } catch (Exception ignored) {
                        }

                        getContentResolver().delete(
                            uri,
                            null,
                            null
                        );

                        runOnUiThread(() ->
                            statusText.setText("Ошибка синтеза TTS")
                        );
                    }
                }
            );

            int result = tts.synthesizeToFile(
                    text,
                    null,
                    pfd,
                    utteranceId
            );

            if (result != TextToSpeech.SUCCESS) {
                pfd.close();
                getContentResolver().delete(uri, null, null);
                statusText.setText("TTS не смог создать WAV");
            } else {
                statusText.setText("Создаю WAV...");
            }

        } catch (Exception e) {
            getContentResolver().delete(uri, null, null);
            statusText.setText("Ошибка: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
    }
