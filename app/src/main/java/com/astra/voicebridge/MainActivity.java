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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextToSpeech tts;
    private TextView statusText;
    private EditText textInput;

    private ServerSocket serverSocket;
    private Thread serverThread;

    private static final int PORT = 8765;

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

                    statusText.setText(
                        "TTS готов.\nHTTP-сервер запускается..."
                    );

                    startHttpServer();
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

    // =========================================================
    // HTTP SERVER
    // =========================================================

    private void startHttpServer() {

        serverThread = new Thread(() -> {

            try {

                serverSocket = new ServerSocket(PORT);

                runOnUiThread(() ->
                    statusText.setText(
                        "TTS готов.\nHTTP-сервер: порт " + PORT
                    )
                );

                while (!serverSocket.isClosed()) {

                    Socket socket = serverSocket.accept();

                    new Thread(() ->
                        handleHttpRequest(socket)
                    ).start();
                }

            } catch (Exception e) {

                runOnUiThread(() ->
                    statusText.setText(
                        "HTTP ошибка: " + e.getMessage()
                    )
                );
            }

        });

        serverThread.start();
    }

    private void handleHttpRequest(Socket socket) {

        try {

            BufferedReader reader =
                new BufferedReader(
                    new InputStreamReader(
                        socket.getInputStream(),
                        StandardCharsets.UTF_8
                    )
                );

            String requestLine = reader.readLine();

            if (requestLine == null) {
                socket.close();
                return;
            }

            String responseText;

            // -------------------------------------------------
            // GET /status
            // -------------------------------------------------

            if (requestLine.startsWith("GET /status")) {

                responseText =
                    "{\"status\":\"online\",\"tts\":true,\"port\":" +
                    PORT + "}";

            }

            // -------------------------------------------------
            // GET /speak?text=...
            // -------------------------------------------------

            else if (requestLine.startsWith("GET /speak")) {

                String text = extractTextFromRequest(requestLine);

                if (text == null || text.trim().isEmpty()) {

                    responseText =
                        "{\"ok\":false,\"error\":\"text is empty\"}";

                } else {

                    final String speechText = text;

                    runOnUiThread(() -> {
                        textInput.setText(speechText);
                        createWav();
                    });

                    responseText =
                        "{\"ok\":true,\"message\":\"WAV generation started\"}";
                }

            }

            // -------------------------------------------------
            // POST /speak
            // -------------------------------------------------

            else if (requestLine.startsWith("POST /speak")) {

                String line;
                int contentLength = 0;

                while ((line = reader.readLine()) != null &&
                       !line.isEmpty()) {

                    if (line.toLowerCase(Locale.US)
                           .startsWith("content-length:")) {

                        contentLength =
                            Integer.parseInt(
                                line.substring(
                                    line.indexOf(":") + 1
                                ).trim()
                            );
                    }
                }

                char[] body = new char[contentLength];

                int read = 0;

                while (read < contentLength) {

                    int count =
                        reader.read(
                            body,
                            read,
                            contentLength - read
                        );

                    if (count == -1) {
                        break;
                    }

                    read += count;
                }

                String text = new String(body).trim();

                if (text.isEmpty()) {

                    responseText =
                        "{\"ok\":false,\"error\":\"text is empty\"}";

                } else {

                    final String speechText = text;

                    runOnUiThread(() -> {
                        textInput.setText(speechText);
                        createWav();
                    });

                    responseText =
                        "{\"ok\":true,\"message\":\"WAV generation started\"}";
                }

            }

            // -------------------------------------------------
            // UNKNOWN
            // -------------------------------------------------

            else {

                responseText =
                    "{\"ok\":false,\"error\":\"unknown endpoint\"}";
            }

            sendHttpResponse(socket, responseText);

        } catch (Exception e) {

            try {

                sendHttpResponse(
                    socket,
                    "{\"ok\":false,\"error\":\"" +
                    escapeJson(e.getMessage()) +
                    "\"}"
                );

            } catch (Exception ignored) {
            }

        } finally {

            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private String extractTextFromRequest(String requestLine) {

        try {

            int question =
                requestLine.indexOf("?");

            if (question == -1) {
                return "";
            }

            int space =
                requestLine.indexOf(" ", question);

            String query;

            if (space == -1) {
                query =
                    requestLine.substring(question + 1);
            } else {
                query =
                    requestLine.substring(
                        question + 1,
                        space
                    );
            }

            String[] params =
                query.split("&");

            for (String param : params) {

                String[] pair =
                    param.split("=", 2);

                if (pair.length == 2 &&
                    pair[0].equals("text")) {

                    return URLDecoder.decode(
                        pair[1],
                        "UTF-8"
                    );
                }
            }

        } catch (Exception ignored) {
        }

        return "";
    }

    private void sendHttpResponse(
        Socket socket,
        String body
    ) throws Exception {

        byte[] data =
            body.getBytes(StandardCharsets.UTF_8);

        String headers =
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: " + data.length + "\r\n" +
            "Connection: close\r\n" +
            "\r\n";

        OutputStream output =
            socket.getOutputStream();

        output.write(
            headers.getBytes(StandardCharsets.UTF_8)
        );

        output.write(data);
        output.flush();
    }

    private String escapeJson(String text) {

        if (text == null) {
            return "";
        }

        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    // =========================================================
    // TTS -> WAV
    // =========================================================

    private void createWav() {

        if (tts == null) {
            statusText.setText("TTS ещё не готов");
            return;
        }

        String text =
            textInput.getText().toString().trim();

        if (text.isEmpty()) {
            statusText.setText("Нет текста");
            return;
        }

        ContentValues values =
            new ContentValues();

        values.put(
            MediaStore.Downloads.DISPLAY_NAME,
            "astra_" +
            System.currentTimeMillis() +
            ".wav"
        );

        values.put(
            MediaStore.Downloads.MIME_TYPE,
            "audio/wav"
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            values.put(
                MediaStore.Downloads.RELATIVE_PATH,
                "Download/ASTRA"
            );

            values.put(
                MediaStore.Downloads.IS_PENDING,
                1
            );
        }

        Uri uri =
            getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            );

        if (uri == null) {

            statusText.setText(
                "Не удалось создать WAV"
            );

            return;
        }

        try {

            ParcelFileDescriptor pfd =
                getContentResolver()
                    .openFileDescriptor(uri, "w");

            if (pfd == null) {

                statusText.setText(
                    "Не удалось открыть файл"
                );

                return;
            }

            String utteranceId =
                "astra_" +
                System.currentTimeMillis();

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

                        if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.Q) {

                            ContentValues done =
                                new ContentValues();

                            done.put(
                                MediaStore.Downloads.IS_PENDING,
                                0
                            );

                            getContentResolver().update(
                                uri,
                                done,
                                null,
                                null
                            );
                        }

                        runOnUiThread(() ->
                            statusText.setText(
                                "ГОТОВО!\n" +
                                "WAV сохранён в Download/ASTRA"
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
                            statusText.setText(
                                "Ошибка синтеза TTS"
                            )
                        );
                    }
                }
            );

            int result =
                tts.synthesizeToFile(
                    text,
                    null,
                    pfd,
                    utteranceId
                );

            if (result != TextToSpeech.SUCCESS) {

                pfd.close();

                getContentResolver().delete(
                    uri,
                    null,
                    null
                );

                statusText.setText(
                    "TTS не смог создать WAV"
                );

            } else {

                statusText.setText(
                    "Создаю WAV..."
                );
            }

        } catch (Exception e) {

            getContentResolver().delete(
                uri,
                null,
                null
            );

            statusText.setText(
                "Ошибка: " +
                e.getMessage()
            );
        }
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    @Override
    protected void onDestroy() {

        if (serverSocket != null) {

            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
        }

        if (tts != null) {

            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
            }
