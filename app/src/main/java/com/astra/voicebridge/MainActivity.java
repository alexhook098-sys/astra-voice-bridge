package com.astra.voicebridge;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
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
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);


        // =====================================================
        // UI
        // =====================================================

        LinearLayout layout =
                new LinearLayout(this);

        layout.setOrientation(
                LinearLayout.VERTICAL
        );

        layout.setPadding(
                40,
                60,
                40,
                40
        );


        // =====================================================
        // TITLE
        // =====================================================

        TextView title =
                new TextView(this);

        title.setText(
                "ASTRA Voice Bridge"
        );

        title.setTextSize(
                26
        );


        // =====================================================
        // TEXT INPUT
        // =====================================================

        textInput =
                new EditText(this);

        textInput.setText(
                "Привет. Я ASTRA. Это тест моего голоса."
        );

        textInput.setTextSize(
                18
        );


        // =====================================================
        // SPEAK BUTTON
        // =====================================================

        Button speakButton =
                new Button(this);

        speakButton.setText(
                "Создать WAV"
        );


        // =====================================================
        // STATUS
        // =====================================================

        statusText =
                new TextView(this);

        statusText.setText(
                "Запуск TTS..."
        );

        statusText.setTextSize(
                18
        );


        // =====================================================
        // ADD UI
        // =====================================================

        layout.addView(
                title
        );

        layout.addView(
                textInput
        );

        layout.addView(
                speakButton
        );

        layout.addView(
                statusText
        );


        setContentView(
                layout
        );


        // =====================================================
        // TTS FOR UI TEST
        // =====================================================

        tts =
                new TextToSpeech(
                        this,
                        status -> {

                            if (
                                    status ==
                                            TextToSpeech.SUCCESS
                            ) {

                                int result =
                                        tts.setLanguage(
                                                new Locale(
                                                        "ru",
                                                        "RU"
                                                )
                                        );


                                if (
                                        result ==
                                                TextToSpeech
                                                        .LANG_MISSING_DATA
                                                ||
                                        result ==
                                                TextToSpeech
                                                        .LANG_NOT_SUPPORTED
                                ) {

                                    statusText.setText(
                                            "Русский язык TTS недоступен"
                                    );

                                } else {

                                    statusText.setText(
                                            "TTS готов.\n"
                                                    + "Voice Bridge Service запускается..."
                                    );


                                    // =================================
                                    // START FOREGROUND SERVICE
                                    // =================================

                                    startVoiceBridgeService();
                                }

                            } else {

                                statusText.setText(
                                        "Не удалось запустить TTS"
                                );
                            }
                        }
                );


        // =====================================================
        // BUTTON
        // =====================================================

        speakButton.setOnClickListener(
                v -> {

                    statusText.setText(
                            "WAV создаётся через Voice Bridge..."
                    );


                    String text =
                            textInput
                                    .getText()
                                    .toString()
                                    .trim();


                    if (
                            text.isEmpty()
                    ) {

                        statusText.setText(
                                "Нет текста"
                        );

                        return;
                    }


                    // -----------------------------------------
                    // Используем локальный TTS для теста UI.
                    // HTTP /speak работает через Service.
                    // -----------------------------------------

                    String utteranceId =
                            "ui_test_"
                                    + System.currentTimeMillis();


                    tts.speak(
                            text,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            utteranceId
                    );


                    statusText.setText(
                            "TTS воспроизводит текст..."
                    );
                }
        );


        // =====================================================
        // INCOMING TEXT
        // =====================================================

        Intent intent =
                getIntent();


        String incomingText =
                intent.getStringExtra(
                        "text"
                );


        if (
                incomingText != null
                        &&
                !incomingText.isEmpty()
        ) {

            textInput.setText(
                    incomingText
            );
        }
    }


    // =========================================================
    // START VOICE BRIDGE SERVICE
    // =========================================================

    private void startVoiceBridgeService() {

        try {

            Intent serviceIntent =
                    new Intent(
                            this,
                            VoiceBridgeService.class
                    );


            if (
                    Build.VERSION.SDK_INT
                            >=
                    Build.VERSION_CODES.O
            ) {

                startForegroundService(
                        serviceIntent
                );

            } else {

                startService(
                        serviceIntent
                );
            }


            statusText.setText(
                    "TTS готов.\n"
                            + "Voice Bridge Service запускается..."
            );


        } catch (Exception e) {

            statusText.setText(
                    "Ошибка запуска Service:\n"
                            + e.getMessage()
            );
        }
    }


    // =========================================================
    // CLEANUP
    // =========================================================

    @Override
    protected void onDestroy() {

        /*
         * ВАЖНО:
         *
         * Мы НЕ останавливаем VoiceBridgeService здесь.
         *
         * Поэтому закрытие окна SIRIUS/Voice Bridge
         * не должно останавливать HTTP-сервер.
         */


        if (
                tts != null
        ) {

            tts.stop();

            tts.shutdown();
        }


        super.onDestroy();
    }
}
