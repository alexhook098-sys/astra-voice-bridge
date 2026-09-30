package com.astra.voicebridge;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView textView = new TextView(this);
        textView.setText("ASTRA Voice Bridge\n\nИнициализация TTS...");
        textView.setTextSize(20);
        textView.setPadding(40, 60, 40, 40);
        setContentView(textView);

        tts = new TextToSpeech(this, status -> {

            if (status == TextToSpeech.SUCCESS) {

                tts.setLanguage(new Locale("ru", "RU"));

                File output = new File(
                        getExternalFilesDir(null),
                        "astra_test.wav"
                );

                tts.setOnUtteranceProgressListener(
                        new UtteranceProgressListener() {

                            @Override
                            public void onStart(String utteranceId) {
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                runOnUiThread(() ->
                                        textView.setText(
                                                "ASTRA Voice Bridge\n\n" +
                                                "Готово!\n\n" +
                                                output.getAbsolutePath()
                                        )
                                );
                            }

                            @Override
                            public void onError(String utteranceId) {
                                runOnUiThread(() ->
                                        textView.setText(
                                                "Ошибка синтеза TTS"
                                        )
                                );
                            }
                        }
                );

                String text =
                        "Привет. Я ASTRA. Это тест моего голоса.";

                tts.synthesizeToFile(
                        text,
                        null,
                        output,
                        "astra_test"
                );

            } else {
                textView.setText("Не удалось запустить TTS");
            }
        });
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
