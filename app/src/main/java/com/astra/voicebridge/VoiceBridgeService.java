package com.astra.voicebridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class VoiceBridgeService extends Service {

    private static final String CHANNEL_ID =
            "astra_voice_bridge";

    private static final int NOTIFICATION_ID =
            3001;

    private static final int PORT =
            8765;

    private ServerSocket serverSocket;
    private Thread serverThread;

    private TextToSpeech tts;

    private volatile boolean running =
            false;

    private volatile boolean ttsReady =
            false;


    // =========================================================
    // SERVICE CREATE
    // =========================================================

    @Override
    public void onCreate() {

        super.onCreate();


        createNotificationChannel();


        startForeground(
                NOTIFICATION_ID,
                createNotification(
                        "Voice server starting..."
                )
        );


        running = true;


        // -----------------------------------------------------
        // TTS
        // -----------------------------------------------------

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

                                    ttsReady = false;

                                    updateNotification(
                                            "TTS unavailable"
                                    );

                                } else {

                                    ttsReady = true;

                                    updateNotification(
                                            "Voice server ONLINE • Port 8765"
                                    );
                                }

                            } else {

                                ttsReady = false;

                                updateNotification(
                                        "TTS initialization failed"
                                );
                            }
                        }
                );


        // -----------------------------------------------------
        // HTTP SERVER
        // -----------------------------------------------------

        startHttpServer();


        System.out.println(
                "[ASTRA VOICE BRIDGE] STARTED"
        );
    }


    // =========================================================
    // HTTP SERVER
    // =========================================================

    private void startHttpServer() {

        serverThread =
                new Thread(
                        () -> {

                            try {

                                serverSocket =
                                        new ServerSocket(
                                                PORT
                                        );


                                System.out.println(
                                        "[ASTRA VOICE BRIDGE] "
                                                + "HTTP SERVER "
                                                + "PORT "
                                                + PORT
                                );


                                while (
                                        running
                                                &&
                                        !serverSocket
                                                .isClosed()
                                ) {

                                    Socket socket =
                                            serverSocket
                                                    .accept();


                                    new Thread(
                                            () ->
                                                    handleHttpRequest(
                                                            socket
                                                    )
                                    ).start();
                                }


                            } catch (Exception e) {

                                if (running) {

                                    System.out.println(
                                            "[ASTRA VOICE BRIDGE] "
                                                    + "SERVER ERROR: "
                                                    + e.getMessage()
                                    );
                                }
                            }
                        }
                );


        serverThread.start();
    }


    // =========================================================
    // HTTP REQUEST
    // =========================================================

    private void handleHttpRequest(
            Socket socket
    ) {

        try {

            BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    );


            String requestLine =
                    reader.readLine();


            if (
                    requestLine == null
            ) {

                socket.close();

                return;
            }


            String responseText;


            // =================================================
            // GET /status
            // =================================================

            if (
                    requestLine.startsWith(
                            "GET /status"
                    )
            ) {

                responseText =
                        "{"
                                + "\"status\":\"online\","
                                + "\"tts\":"
                                + ttsReady
                                + ","
                                + "\"port\":"
                                + PORT
                                + "}";


            }


            // =================================================
            // GET /speak
            // =================================================

            else if (
                    requestLine.startsWith(
                            "GET /speak"
                    )
            ) {

                String text =
                        extractTextFromRequest(
                                requestLine
                        );


                if (
                        text == null
                                ||
                        text.trim().isEmpty()
                ) {

                    responseText =
                            "{\"ok\":false,"
                                    + "\"error\":\"text is empty\"}";

                } else {

                    generateWav(
                            text
                    );


                    responseText =
                            "{\"ok\":true,"
                                    + "\"message\":"
                                    + "\"WAV generation started\"}";
                }
            }


            // =================================================
            // POST /speak
            // =================================================

            else if (
                    requestLine.startsWith(
                            "POST /speak"
                    )
            ) {

                String line;

                int contentLength =
                        0;


                while (
                        (line =
                                reader.readLine())
                                != null
                                &&
                        !line.isEmpty()
                ) {

                    if (
                            line.toLowerCase(
                                    Locale.US
                            ).startsWith(
                                    "content-length:"
                            )
                    ) {

                        contentLength =
                                Integer.parseInt(
                                        line.substring(
                                                line.indexOf(
                                                        ":"
                                                ) + 1
                                        ).trim()
                                );
                    }
                }


                char[] body =
                        new char[
                                contentLength
                        ];


                int read =
                        0;


                while (
                        read <
                                contentLength
                ) {

                    int count =
                            reader.read(
                                    body,
                                    read,
                                    contentLength
                                            - read
                            );


                    if (
                            count == -1
                    ) {

                        break;
                    }


                    read += count;
                }


                String text =
                        new String(
                                body
                        ).trim();


                if (
                        text.isEmpty()
                ) {

                    responseText =
                            "{\"ok\":false,"
                                    + "\"error\":\"text is empty\"}";

                } else {

                    generateWav(
                            text
                    );


                    responseText =
                            "{\"ok\":true,"
                                    + "\"message\":"
                                    + "\"WAV generation started\"}";
                }
            }


            // =================================================
            // UNKNOWN
            // =================================================

            else {

                responseText =
                        "{\"ok\":false,"
                                + "\"error\":"
                                + "\"unknown endpoint\"}";
            }


            sendHttpResponse(
                    socket,
                    responseText
            );


        } catch (Exception e) {

            try {

                sendHttpResponse(
                        socket,
                        "{\"ok\":false,"
                                + "\"error\":\""
                                + escapeJson(
                                        e.getMessage()
                                )
                                + "\"}"
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


    // =========================================================
    // EXTRACT TEXT
    // =========================================================

    private String extractTextFromRequest(
            String requestLine
    ) {

        try {

            int question =
                    requestLine.indexOf(
                            "?"
                    );


            if (
                    question == -1
            ) {

                return "";
            }


            int space =
                    requestLine.indexOf(
                            " ",
                            question
                    );


            String query;


            if (
                    space == -1
            ) {

                query =
                        requestLine.substring(
                                question + 1
                        );

            } else {

                query =
                        requestLine.substring(
                                question + 1,
                                space
                        );
            }


            String[] params =
                    query.split(
                            "&"
                    );


            for (
                    String param :
                    params
            ) {

                String[] pair =
                        param.split(
                                "=",
                                2
                        );


                if (
                        pair.length == 2
                                &&
                        pair[0].equals(
                                "text"
                        )
                ) {

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


    // =========================================================
    // SEND HTTP RESPONSE
    // =========================================================

    private void sendHttpResponse(
            Socket socket,
            String body
    ) throws Exception {

        byte[] data =
                body.getBytes(
                        StandardCharsets.UTF_8
                );


        String headers =
                "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: "
                        + "application/json; "
                        + "charset=utf-8\r\n"
                        + "Content-Length: "
                        + data.length
                        + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n";


        OutputStream output =
                socket.getOutputStream();


        output.write(
                headers.getBytes(
                        StandardCharsets.UTF_8
                )
        );


        output.write(
                data
        );


        output.flush();
    }


    // =========================================================
    // TTS → WAV
    // =========================================================

    private synchronized void generateWav(
            String text
    ) {

        if (
                !ttsReady
                        ||
                tts == null
        ) {

            System.out.println(
                    "[ASTRA VOICE BRIDGE] "
                            + "TTS NOT READY"
            );

            return;
        }


        try {

            ContentValues values =
                    new ContentValues();


            values.put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    "astra_"
                            + System.currentTimeMillis()
                            + ".wav"
            );


            values.put(
                    MediaStore.Downloads.MIME_TYPE,
                    "audio/wav"
            );


            if (
                    Build.VERSION.SDK_INT
                            >=
                    Build.VERSION_CODES.Q
            ) {

                values.put(
                        MediaStore.Downloads
                                .RELATIVE_PATH,
                        "Download/ASTRA"
                );


                values.put(
                        MediaStore.Downloads
                                .IS_PENDING,
                        1
                );
            }


            Uri uri =
                    getContentResolver().insert(
                            MediaStore.Downloads
                                    .EXTERNAL_CONTENT_URI,
                            values
                    );


            if (
                    uri == null
            ) {

                System.out.println(
                        "[ASTRA VOICE BRIDGE] "
                                + "FAILED TO CREATE WAV"
                );

                return;
            }


            ParcelFileDescriptor pfd =
                    getContentResolver()
                            .openFileDescriptor(
                                    uri,
                                    "w"
                            );


            if (
                    pfd == null
            ) {

                getContentResolver().delete(
                        uri,
                        null,
                        null
                );

                return;
            }


            String utteranceId =
                    "astra_"
                            + System.currentTimeMillis();


            tts.setOnUtteranceProgressListener(
                    new UtteranceProgressListener() {

                        @Override
                        public void onStart(
                                String id
                        ) {

                            System.out.println(
                                    "[ASTRA VOICE BRIDGE] "
                                            + "TTS START"
                            );
                        }


                        @Override
                        public void onDone(
                                String id
                        ) {

                            try {

                                pfd.close();

                            } catch (Exception ignored) {
                            }


                            if (
                                    Build.VERSION.SDK_INT
                                            >=
                                    Build.VERSION_CODES.Q
                            ) {

                                ContentValues done =
                                        new ContentValues();


                                done.put(
                                        MediaStore.Downloads
                                                .IS_PENDING,
                                        0
                                );


                                getContentResolver()
                                        .update(
                                                uri,
                                                done,
                                                null,
                                                null
                                        );
                            }


                            System.out.println(
                                    "[ASTRA VOICE BRIDGE] "
                                            + "WAV READY"
                            );
                        }


                        @Override
                        public void onError(
                                String id
                        ) {

                            try {

                                pfd.close();

                            } catch (Exception ignored) {
                            }


                            getContentResolver()
                                    .delete(
                                            uri,
                                            null,
                                            null
                                    );


                            System.out.println(
                                    "[ASTRA VOICE BRIDGE] "
                                            + "TTS ERROR"
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


            if (
                    result !=
                            TextToSpeech.SUCCESS
            ) {

                try {

                    pfd.close();

                } catch (Exception ignored) {
                }


                getContentResolver().delete(
                        uri,
                        null,
                        null
                );


                System.out.println(
                        "[ASTRA VOICE BRIDGE] "
                                + "TTS FAILED"
                );
            }


        } catch (Exception e) {

            System.out.println(
                    "[ASTRA VOICE BRIDGE] "
                            + "WAV ERROR: "
                            + e.getMessage()
            );
        }
    }


    // =========================================================
    // NOTIFICATION
    // =========================================================

    private Notification createNotification(
            String text
    ) {

        return new Notification.Builder(
                this,
                CHANNEL_ID
        )
                .setContentTitle(
                        "ASTRA Voice Bridge"
                )
                .setContentText(
                        text
                )
                .setSmallIcon(
                        android.R.drawable
                                .ic_menu_info_details
                )
                .setOngoing(
                        true
                )
                .build();
    }


    private void updateNotification(
            String text
    ) {

        NotificationManager manager =
                getSystemService(
                        NotificationManager.class
                );


        if (
                manager != null
        ) {

            manager.notify(
                    NOTIFICATION_ID,
                    createNotification(
                            text
                    )
            );
        }
    }


    private void createNotificationChannel() {

        if (
                Build.VERSION.SDK_INT
                        >=
                Build.VERSION_CODES.O
        ) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "ASTRA Voice Bridge",
                            NotificationManager
                                    .IMPORTANCE_LOW
                    );


            channel.setDescription(
                    "ASTRA Voice Bridge HTTP server"
            );


            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );


            if (
                    manager != null
            ) {

                manager.createNotificationChannel(
                        channel
                );
            }
        }
    }


    // =========================================================
    // START COMMAND
    // =========================================================

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        return START_STICKY;
    }


    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    public void onDestroy() {

        running = false;


        if (
                serverSocket != null
        ) {

            try {

                serverSocket.close();

            } catch (Exception ignored) {
            }
        }


        if (
                serverThread != null
        ) {

            serverThread.interrupt();
        }


        if (
                tts != null
        ) {

            tts.stop();

            tts.shutdown();
        }


        System.out.println(
                "[ASTRA VOICE BRIDGE] STOPPED"
        );


        super.onDestroy();
    }


    // =========================================================
    // BIND
    // =========================================================

    @Override
    public IBinder onBind(
            Intent intent
    ) {

        return null;
    }


    // =========================================================
    // JSON ESCAPE
    // =========================================================

    private String escapeJson(
            String text
    ) {

        if (
                text == null
        ) {

            return "";
        }


        return text
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\r",
                        "\\r"
                );
    }
}
