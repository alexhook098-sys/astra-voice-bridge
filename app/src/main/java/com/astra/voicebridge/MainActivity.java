package com.astra.voicebridge;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private TextToSpeech tts;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        textView = new TextView(this);
        textView.setText(
                "ASTRA Voice Bridge\n\n" +
                "Запуск приложения..."
        );
        textView.setTextSize(20);
        textView.setPadding(40, 60, 40, 40);
        setContentView(textView);

        try {
            tts = new TextToSpeech(this, status -> {

                if (status == TextToSpeech.SUCCESS) {

                    int result = tts.setLanguage(
                            new Locale("ru", "RU")
                    );

                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {

                        runOnUiThread(() ->
                                textView.setText(
                                        "ASTRA Voice Bridge\n\n" +
                                        "Русский язык TTS не поддерживается."
                                )
                        );

                    } else {

                        runOnUiThread(() ->
                                textView.setText(
                                        "ASTRA Voice Bridge\n\n" +
                                        "TTS готов.\n" +
                                        "Голосовой движок найден."
                                )
                        );
                    }

                } else {

                    runOnUiThread(() ->
                            textView.setText(
                                    "ASTRA Voice Bridge\n\n" +
                                    "Не удалось запустить TTS."
                            )
                    );
                }
            });

        } catch (Exception e) {

            textView.setText(
                    "ASTRA Voice Bridge\n\n" +
                    "Ошибка запуска:\n" +
                    e.toString()
            );
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
