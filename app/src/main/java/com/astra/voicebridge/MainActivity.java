package com.astra.voicebridge;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusText;
    private EditText textInput;


    // =========================================================
    // CREATE
    // =========================================================

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
        // WAV BUTTON
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
                "Запуск Voice Bridge..."
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
        // START SERVICE
        // =====================================================

        startVoiceBridgeService();


        // =====================================================
        // BUTTON
        // =====================================================

        speakButton.setOnClickListener(
                v -> {

                    String text =
                            textInput
                                    .getText()
                                    .toString()
                                    .trim();


                    if (
                            text.isEmpty()
                    ) {

                        statusText.setText(
                                "Введите текст"
                        );

                        return;
                    }


                    // -----------------------------------------
                    // Отправляем текст напрямую в Service
                    // -----------------------------------------

                    generateWav(
                            text
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
                    "Voice Bridge Service запущен"
            );


        } catch (Exception e) {

            statusText.setText(
                    "Ошибка запуска Service:\n"
                            + e.getMessage()
            );
        }
    }


    // =========================================================
    // GENERATE WAV
    // =========================================================

    private void generateWav(
            String text
    ) {

        try {

            Intent serviceIntent =
                    new Intent(
                            this,
                            VoiceBridgeService.class
                    );


            serviceIntent.setAction(
                    VoiceBridgeService
                            .ACTION_GENERATE_WAV
            );


            serviceIntent.putExtra(
                    VoiceBridgeService
                            .EXTRA_TEXT,
                    text
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
                    "WAV создаётся...\n"
                            + "Папка: Download/ASTRA"
            );


        } catch (Exception e) {

            statusText.setText(
                    "Ошибка создания WAV:\n"
                            + e.getMessage()
            );
        }
    }


    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    protected void onDestroy() {

        /*
         * ВАЖНО:
         *
         * MainActivity НЕ останавливает
         * VoiceBridgeService.
         *
         * Service продолжает работать
         * после закрытия окна приложения.
         */

        super.onDestroy();
    }
                }
