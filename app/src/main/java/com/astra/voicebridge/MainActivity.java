package com.astra.voicebridge;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusText;
    private EditText textInput;


    // =========================================================
    // WAV READY RECEIVER
    // =========================================================

    private final BroadcastReceiver wavReadyReceiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(
                        Context context,
                        Intent intent
                ) {

                    if (
                            VoiceBridgeService
                                    .ACTION_WAV_READY
                                    .equals(
                                            intent.getAction()
                                    )
                    ) {

                        String wavName =
                                intent.getStringExtra(
                                        VoiceBridgeService
                                                .EXTRA_WAV_NAME
                                );


                        if (
                                wavName != null
                        ) {

                            statusText.setText(
                                    "✅ WAV создан\n"
                                            + wavName
                                            + "\n"
                                            + "Папка: Download/ASTRA"
                            );

                        } else {

                            statusText.setText(
                                    "✅ WAV создан\n"
                                            + "Папка: Download/ASTRA"
                            );
                        }
                    }
                }
            };


    // =========================================================
    // CREATE
    // =========================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(savedInstanceState);


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


        TextView title =
                new TextView(this);

        title.setText(
                "ASTRA Voice Bridge"
        );

        title.setTextSize(
                26
        );


        textInput =
                new EditText(this);

        textInput.setText(
                "Привет. Я ASTRA. Это тест моего голоса."
        );

        textInput.setTextSize(
                18
        );


        Button speakButton =
                new Button(this);

        speakButton.setText(
                "Создать WAV"
        );


        statusText =
                new TextView(this);

        statusText.setText(
                "Запуск Voice Bridge..."
        );

        statusText.setTextSize(
                18
        );


        layout.addView(title);
        layout.addView(textInput);
        layout.addView(speakButton);
        layout.addView(statusText);

        setContentView(layout);


        // =====================================================
        // REGISTER RECEIVER
        // =====================================================

        IntentFilter filter =
                new IntentFilter(
                        VoiceBridgeService
                                .ACTION_WAV_READY
                );


        if (
                Build.VERSION.SDK_INT
                        >=
                Build.VERSION_CODES.TIRAMISU
        ) {

            registerReceiver(
                    wavReadyReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(
                    wavReadyReceiver,
                    filter
            );
        }


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


                    generateWav(text);
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
    // START SERVICE
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
                    "Voice Bridge ONLINE\n"
                            + "HTTP 8765 • TTS ONLINE"
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
                    "🔄 WAV создаётся..."
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

        try {

            unregisterReceiver(
                    wavReadyReceiver
            );

        } catch (Exception ignored) {
        }


        /*
         * MainActivity НЕ останавливает
         * VoiceBridgeService.
         *
         * Service продолжает работать
         * после закрытия окна приложения.
         */

        super.onDestroy();
    }
                        }
