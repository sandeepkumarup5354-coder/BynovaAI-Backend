package com.bynova.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.Locale;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends Activity {

    final int BG = Color.rgb(7, 11, 24);
    final int CARD = Color.rgb(18, 24, 45);
    final int CARD_2 = Color.rgb(25, 31, 56);
    final int PRIMARY = Color.rgb(108, 99, 255);
    final int WHITE = Color.rgb(245, 246, 255);
    final int MUTED = Color.rgb(165, 171, 198);

    final String API_BASE_URL = "http://127.0.0.1:8080";
    final String BACKEND_URL = API_BASE_URL + "/chat";
    final String STREAM_URL = API_BASE_URL + "/chat/stream";
    final String IMAGE_URL = API_BASE_URL + "/image";
    String CLIENT_ID;
    String currentChatId = "";
    android.net.Uri pendingImageUri = null;

    String getClientId() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("bynova_ai", MODE_PRIVATE);

        String id = prefs.getString("client_id", "");

        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString("client_id", id).apply();
        }

        return id;
    }

    LinearLayout messages;
    String lastUserMessage = "";
    ScrollView chatScroll;

    EditText chatInput;
    TextView micButton;

    SpeechRecognizer speechRecognizer;
    TextToSpeech textToSpeech;
    boolean voiceMode = false;
    boolean isListening = false;
    TextToSpeech tts;
    boolean voiceReplyMode = false;
    static final int VOICE_REQUEST = 7002;

    int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    GradientDrawable round(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CLIENT_ID = getClientId();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                try {
                    // Use clear Hindi (India) voice.
                    Locale locale = new Locale("hi", "IN");

                    if (tts.isLanguageAvailable(locale)
                            >= TextToSpeech.LANG_AVAILABLE) {
                        tts.setLanguage(locale);
                    } else {
                        tts.setLanguage(Locale.US);
                    }

                    tts.setSpeechRate(0.95f);
                    tts.setPitch(1.05f);

                    if (android.os.Build.VERSION.SDK_INT >= 21) {
                        tts.setAudioAttributes(
                                new android.media.AudioAttributes.Builder()
                                        .setUsage(
                                                android.media.AudioAttributes.USAGE_ASSISTANT
                                        )
                                        .setContentType(
                                                android.media.AudioAttributes.CONTENT_TYPE_SPEECH
                                        )
                                        .build()
                        );
                    }

                    if (android.os.Build.VERSION.SDK_INT >= 15) {
                        tts.setOnUtteranceProgressListener(
                                new UtteranceProgressListener() {

                            @Override
                            public void onStart(String utteranceId) {
                            }

                            @Override
                            public void onDone(String utteranceId) {
                                runOnUiThread(() -> {
                                    if (voiceMode && !speechQueue.isEmpty()) {
                                        speakNextChunk();
                                    } else {
                                        ttsSpeaking = false;
                                    }
                                });
                            }

                            @Override
                            public void onError(String utteranceId) {
                                runOnUiThread(() -> {
                                    if (voiceMode && !speechQueue.isEmpty()) {
                                        speakNextChunk();
                                    } else {
                                        ttsSpeaking = false;
                                    }
                                });
                            }
                        });
                    }

                } catch (Exception ignored) {
                }
            }
        });

        showHome();

        // Request location permission when needed
        requestLocationPermission();

        initVoice();
    }

    void setTtsLanguageForReply(String text) {
        if (tts == null || text == null || text.trim().isEmpty()) {
            return;
        }

        try {
            String sample = text.trim();
            Locale selected = Locale.US;

            // Detect the reply language from its script.
            if (sample.matches(".*[\\u0900-\\u097F].*")) {
                selected = new Locale("hi", "IN");
            } else if (sample.matches(".*[\\u0980-\\u09FF].*")) {
                selected = new Locale("bn", "IN");
            } else if (sample.matches(".*[\\u0A00-\\u0A7F].*")) {
                selected = new Locale("pa", "IN");
            } else if (sample.matches(".*[\\u0A80-\\u0AFF].*")) {
                selected = new Locale("gu", "IN");
            } else if (sample.matches(".*[\\u0B80-\\u0BFF].*")) {
                selected = new Locale("ta", "IN");
            } else if (sample.matches(".*[\\u0C00-\\u0C7F].*")) {
                selected = new Locale("te", "IN");
            } else if (sample.matches(".*[\\u0C80-\\u0CFF].*")) {
                selected = new Locale("kn", "IN");
            } else if (sample.matches(".*[\\u0D00-\\u0D7F].*")) {
                selected = new Locale("ml", "IN");
            } else if (sample.matches(".*[\\u0D80-\\u0DFF].*")) {
                selected = new Locale("si", "LK");
            }

            Locale actualLocale = Locale.US;

            if (tts.isLanguageAvailable(selected)
                    >= TextToSpeech.LANG_AVAILABLE) {
                actualLocale = selected;
                tts.setLanguage(selected);
            } else {
                tts.setLanguage(Locale.US);
            }

            // On Android 5+, prefer the best installed voice matching
            // the detected language. Prefer offline, high-quality voices.
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                java.util.Set<android.speech.tts.Voice> voices =
                        tts.getVoices();

                android.speech.tts.Voice bestVoice = null;
                int bestScore = Integer.MIN_VALUE;

                if (voices != null) {
                    for (android.speech.tts.Voice voice : voices) {
                        if (voice == null || voice.getLocale() == null) {
                            continue;
                        }

                        Locale voiceLocale = voice.getLocale();

                        if (!voiceLocale.getLanguage()
                                .equalsIgnoreCase(
                                        actualLocale.getLanguage())) {
                            continue;
                        }

                        int score = 0;

                        // Exact country match is better.
                        if (actualLocale.getCountry().equalsIgnoreCase(
                                voiceLocale.getCountry())) {
                            score += 30;
                        }

                        // Prefer voices that are not network dependent.
                        if (!voice.isNetworkConnectionRequired()) {
                            score += 20;
                        }

                        // Prefer voices with better reported quality.
                        if (voice.getQuality()
                                >= android.speech.tts.Voice.QUALITY_NORMAL) {
                            score += 10;
                        }

                        // Prefer a female voice when the engine exposes
                        // gender information through its voice name.
                        String name = voice.getName() == null
                                ? ""
                                : voice.getName().toLowerCase(Locale.US);

                        if (name.contains("female")
                                || name.contains("feminine")
                                || name.contains("woman")
                                || name.contains("girl")) {
                            score += 15;
                        }

                        if (score > bestScore) {
                            bestScore = score;
                            bestVoice = voice;
                        }
                    }
                }

                if (bestVoice != null) {
                    tts.setVoice(bestVoice);
                }
            }

            // Natural conversational settings.
            tts.setSpeechRate(0.92f);
            tts.setPitch(1.0f);

        } catch (Exception ignored) {
        }
    }

    void requestLocationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        7001
                );
                return;
            }

            getCurrentLocation();
        }
    }

    void getCurrentLocation() {
        try {
            android.location.LocationManager manager =
                    (android.location.LocationManager)
                            getSystemService(LOCATION_SERVICE);

            android.location.Location location = null;

            if (checkSelfPermission(
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

                location = manager.getLastKnownLocation(
                        android.location.LocationManager.GPS_PROVIDER
                );
            }

            if (location == null &&
                checkSelfPermission(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

                location = manager.getLastKnownLocation(
                        android.location.LocationManager.NETWORK_PROVIDER
                );
            }

            if (location != null) {
                Toast.makeText(
                        MainActivity.this,
                        "Location detected",
                        Toast.LENGTH_SHORT
                ).show();
            }

        } catch (Exception e) {
            Toast.makeText(
                    MainActivity.this,
                    "Could not access location",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == 7001) {
            if (grantResults.length > 0 &&
                grantResults[0] ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {

                getCurrentLocation();

            } else {
                Toast.makeText(
                        MainActivity.this,
                        "Location permission denied",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }

        if (requestCode == VOICE_REQUEST) {
            if (grantResults.length > 0 &&
                grantResults[0] ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {

                if (speechRecognizer == null) {
                    initVoice();
                }

                if (speechRecognizer != null) {
                    voiceMode = true;
                    voiceReplyMode = true;
                    isListening = false;
                    updateMicUi();
                    startListeningNow();
                } else {
                    Toast.makeText(
                            MainActivity.this,
                            "Voice recognition is not available",
                            Toast.LENGTH_SHORT
                    ).show();
                }

            } else {
                Toast.makeText(
                        MainActivity.this,
                        "Microphone permission denied",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    void updateMicUi() {
        runOnUiThread(() -> {
            if (micButton == null) {
                return;
            }

            if (voiceMode && isListening) {
                micButton.setText("■");
                micButton.setTextColor(WHITE);
                micButton.setBackground(round(PRIMARY, 16));
                micButton.setContentDescription(
                        "Microphone ON. Listening. Tap to stop."
                );
            } else {
                micButton.setText("🎙");
                micButton.setTextColor(WHITE);
                micButton.setBackground(round(CARD, 16));
                micButton.setContentDescription(
                        "Microphone OFF. Tap to speak."
                );
            }
        });
    }

    void stopVoiceInput(boolean showMessage) {
        voiceMode = false;
        voiceReplyMode = false;
        isListening = false;

        try {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            }
        } catch (Exception ignored) {
        }

        updateMicUi();

        if (showMessage) {
            Toast.makeText(
                    MainActivity.this,
                    "Microphone OFF",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    void startVoiceInput() {

        // Mic ON -> one tap immediately turns it OFF.
        if (voiceMode) {
            stopVoiceInput(true);
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(
                        android.Manifest.permission.RECORD_AUDIO
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            android.Manifest.permission.RECORD_AUDIO
                    },
                    VOICE_REQUEST
            );
            return;
        }

        if (speechRecognizer == null) {
            initVoice();
        }

        if (speechRecognizer == null) {
            Toast.makeText(
                    MainActivity.this,
                    "Voice recognition is not available",
                    Toast.LENGTH_SHORT
            ).show();
            updateMicUi();
            return;
        }

        voiceMode = true;
        voiceReplyMode = true;
        isListening = false;

        updateMicUi();

        Toast.makeText(
                MainActivity.this,
                "Microphone ON — Listening...",
                Toast.LENGTH_SHORT
        ).show();

        startListeningNow();
    }

    void startListeningNow() {
        if (!voiceMode ||
                speechRecognizer == null ||
                isListening) {
            return;
        }

        try {
            Intent intent = new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "hi-IN"
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "hi-IN"
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,
                    false
            );

            // Enable live/partial speech recognition.
            intent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
            );

            isListening = true;
            updateMicUi();

            speechRecognizer.startListening(intent);

        } catch (Exception e) {
            isListening = false;
            voiceMode = false;
            voiceReplyMode = false;

            updateMicUi();

            Toast.makeText(
                    MainActivity.this,
                    "Could not start microphone",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    void initVoice() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(
                    MainActivity.this
            )) {
                speechRecognizer = null;
                return;
            }

            speechRecognizer =
                    SpeechRecognizer.createSpeechRecognizer(
                            MainActivity.this
                    );

            speechRecognizer.setRecognitionListener(
                    new android.speech.RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    isListening = false;

                    // Do not restart immediately for every error.
                    // This prevents microphone/request loops.
                    if (voiceMode &&
                            error != SpeechRecognizer.ERROR_CLIENT &&
                            error != SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {

                        new android.os.Handler(
                                android.os.Looper.getMainLooper()
                        ).postDelayed(
                                () -> {
                                    if (voiceMode && !isListening) {
                                        startListeningNow();
                                    }
                                },
                                1200
                        );
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;

                    ArrayList<String> matches =
                            results.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                            );

                    if (matches == null ||
                            matches.isEmpty()) {

                        if (voiceMode) {
                            new android.os.Handler(
                                    android.os.Looper.getMainLooper()
                            ).postDelayed(
                                    () -> {
                                        if (voiceMode && !isListening) {
                                            startListeningNow();
                                        }
                                    },
                                    500
                            );
                        }
                        return;
                    }

                    String spoken = matches.get(0);

                    if (spoken == null) {
                        return;
                    }

                    spoken = spoken.trim();

                    if (spoken.isEmpty()) {
                        if (voiceMode) {
                            startListeningNow();
                        }
                        return;
                    }

                    // Send exactly one voice request.
                    voiceReplyMode = voiceMode;
                    sendVoiceMessage(spoken);
                }

                @Override
                public void onPartialResults(
                        Bundle partialResults
                ) {
                }

                @Override
                public void onEvent(
                        int eventType,
                        Bundle params
                ) {
                }
            });

        } catch (Exception e) {
            speechRecognizer = null;

            Toast.makeText(
                    MainActivity.this,
                    "Voice setup failed",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    void sendVoiceMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        final String spoken = message.trim();

        runOnUiThread(() -> {
            addUserMessage(spoken);
            lastUserMessage = spoken;
        });

        sendToStreamingBackend(spoken);
    }

    // ================= NATURAL VOICE SYSTEM =================

    boolean ttsSpeaking = false;

    // Voice interruption system.
    // While AI is speaking, this listener watches for commands
    // such as "stop", "ruko", "ruk jao", "bas", etc.
    boolean interruptionListening = false;
    boolean interruptionRequested = false;

    final java.util.concurrent.ConcurrentLinkedQueue<String> speechQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    boolean isStopCommand(String text) {
        if (text == null) {
            return false;
        }

        String command = text
                .toLowerCase(Locale.ROOT)
                .trim();

        command = command
                .replaceAll("[^a-zA-Z0-9\\u0900-\\u097F ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return command.equals("stop")
                || command.equals("ruko")
                || command.equals("ruk jao")
                || command.equals("ruk ja")
                || command.equals("bas")
                || command.equals("band karo")
                || command.equals("bolna band karo")
                || command.contains("ruko")
                || command.contains("ruk jao")
                || command.contains("stop karo")
                || command.contains("bas karo");
    }

    void interruptSpeech() {
        interruptionRequested = true;

        try {
            speechQueue.clear();

            if (tts != null) {
                tts.stop();
            }

            ttsSpeaking = false;

            if (speechRecognizer != null) {
                try {
                    speechRecognizer.stopListening();
                    speechRecognizer.cancel();
                } catch (Exception ignored) {
                }
            }

            isListening = false;

        } catch (Exception ignored) {
        }
    }

    void listenForInterruption() {
        if (!voiceMode || interruptionListening) {
            return;
        }

        if (speechRecognizer == null) {
            return;
        }

        interruptionListening = true;

        try {
            Intent intent = new Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "hi-IN"
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    "hi-IN"
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
            );

            intent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
            );

            speechRecognizer.setRecognitionListener(
                    new android.speech.RecognitionListener() {

                @Override
                public void onReadyForSpeech(Bundle params) {
                    isListening = true;
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                    isListening = false;
                }

                @Override
                public void onError(int error) {
                    isListening = false;
                    interruptionListening = false;

                    if (voiceMode && ttsSpeaking) {
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> listenForInterruption(),
                                250
                        );
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    isListening = false;
                    interruptionListening = false;

                    ArrayList<String> matches =
                            results.getStringArrayList(
                                    SpeechRecognizer.RESULTS_RECOGNITION
                            );

                    if (matches != null && !matches.isEmpty()) {

                        String heard = matches.get(0);

                        if (isStopCommand(heard)) {
                            interruptSpeech();

                            Toast.makeText(
                                    MainActivity.this,
                                    "Stopped",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }
                    }

                    if (voiceMode && ttsSpeaking) {
                        new Handler(Looper.getMainLooper()).postDelayed(
                                () -> listenForInterruption(),
                                250
                        );
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(
                        int eventType,
                        Bundle params
                ) {
                }
            });

            speechRecognizer.startListening(intent);

        } catch (Exception ignored) {
            interruptionListening = false;
            isListening = false;
        }
    }

    void stopSpeaking() {
        try {
            speechQueue.clear();

            if (tts != null) {
                tts.stop();
            }

            ttsSpeaking = false;

        } catch (Exception ignored) {
        }
    }

    String prepareSpeech(String text) {
        if (text == null) {
            return "";
        }

        String spoken = text;

        // Never read programming code aloud.
        spoken = spoken.replaceAll(
                "```[\\s\\S]*?```",
                " "
        );

        // Remove markdown links but keep their visible title.
        spoken = spoken.replaceAll(
                "\\[([^\\]]+)\\]\\([^\\)]+\\)",
                "$1"
        );

        // Remove raw URLs.
        spoken = spoken.replaceAll(
                "(https?://|www\\.)\\S+",
                " "
        );

        // Remove inline code markers.
        spoken = spoken.replace("`", "");

        // Remove markdown emphasis.
        spoken = spoken.replace("**", "");
        spoken = spoken.replace("__", "");

        // Remove heading markers.
        spoken = spoken.replaceAll(
                "(?m)^\\s*#{1,6}\\s*",
                ""
        );

        // Convert bullets into natural pauses.
        spoken = spoken.replaceAll(
                "(?m)^\\s*[-•*]\\s+",
                ""
        );

        // Remove common technical formatting characters.
        spoken = spoken.replaceAll(
                "[{}\\[\\]<>|]",
                " "
        );

        // Make punctuation readable by the TTS engine.
        spoken = spoken.replace(";", ". ");
        spoken = spoken.replace(":", ": ");

        // Normalize excessive whitespace.
        spoken = spoken.replaceAll(
                "[ \\t]+",
                " "
        );

        spoken = spoken.replaceAll(
                "\\n{3,}",
                "\\n\\n"
        );

        return spoken.trim();
    }

    java.util.ArrayList<String> splitSpeech(String text) {

        java.util.ArrayList<String> result =
                new java.util.ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return result;
        }

        // Split naturally at sentence boundaries.
        String[] sentences = text.split(
                "(?<=[.!?।！？])\\s+"
        );

        StringBuilder current =
                new StringBuilder();

        for (String sentence : sentences) {

            if (sentence == null) {
                continue;
            }

            sentence = sentence.trim();

            if (sentence.isEmpty()) {
                continue;
            }

            if (current.length() == 0) {
                current.append(sentence);
            } else if (
                    current.length() + sentence.length() + 1 <= 220
            ) {
                current.append(" ")
                       .append(sentence);
            } else {
                result.add(current.toString().trim());
                current.setLength(0);
                current.append(sentence);
            }
        }

        if (current.length() > 0) {
            result.add(current.toString().trim());
        }

        return result;
    }

    void speakNextChunk() {

        if (!voiceMode ||
                tts == null) {
            speechQueue.clear();
            ttsSpeaking = false;
            return;
        }

        String chunk = speechQueue.poll();

        if (chunk == null ||
                chunk.trim().isEmpty()) {
            ttsSpeaking = false;
            return;
        }

        try {
            ttsSpeaking = true;

            setTtsLanguageForReply(chunk);

            // Slightly slower than normal gives Hindi/Hinglish
            // words more room to sound clear.
            tts.setSpeechRate(0.88f);
            tts.setPitch(1.0f);

            if (android.os.Build.VERSION.SDK_INT >= 21) {

                android.os.Bundle params =
                        new android.os.Bundle();

                String utteranceId =
                    "bynova_chunk_" + System.nanoTime();

                tts.speak(
                        chunk,
                        ttsSpeaking && !speechQueue.isEmpty()
                                ? TextToSpeech.QUEUE_ADD
                                : TextToSpeech.QUEUE_FLUSH,
                        params,
                        utteranceId
                );


            } else {

                tts.speak(
                        chunk,
                        TextToSpeech.QUEUE_FLUSH,
                        null
                );
            }

        } catch (Exception e) {
            ttsSpeaking = false;
            speechQueue.clear();
        }
    }

    void speakReply(String reply) {

        if (!voiceMode ||
                tts == null ||
                reply == null ||
                reply.trim().isEmpty()) {
            return;
        }

        try {

            stopSpeaking();

            String spoken = prepareSpeech(reply);

            if (spoken.isEmpty()) {
                return;
            }

            java.util.ArrayList<String> chunks =
                    splitSpeech(spoken);

            if (chunks.isEmpty()) {
                return;
            }

            speechQueue.addAll(chunks);

            speakNextChunk();

        } catch (Exception ignored) {
            speechQueue.clear();
            ttsSpeaking = false;
        }
    }

    // ============================================================


    void showHome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Top bar
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(8));

        TextView menu = text("☰", 24, WHITE);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(round(CARD_2, 14));

        header.addView(
                menu,
                new LinearLayout.LayoutParams(dp(46), dp(46))
        );

        TextView title = text(
                "Bynova AI",
                20,
                WHITE
        );
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);

        header.addView(
                title,
                new LinearLayout.LayoutParams(0, dp(46), 1)
        );

        TextView newChat = text("+", 24, WHITE);
        newChat.setGravity(Gravity.CENTER);
        newChat.setTypeface(Typeface.DEFAULT_BOLD);
        newChat.setBackground(round(CARD_2, 14));
        newChat.setContentDescription("New Chat");

        header.addView(
                newChat,
                new LinearLayout.LayoutParams(dp(46), dp(46))
        );

        menu.setOnClickListener(v -> showChatHistory());

        newChat.setOnClickListener(v -> {
            clearConversation();
            showHome();
        });

        root.addView(
                header,
                new LinearLayout.LayoutParams(-1, dp(64))
        );

        // Conversation area
        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setBackgroundColor(BG);
        chatScroll.setPadding(
                dp(10),
                dp(6),
                dp(10),
                dp(6)
        );

        messages = new LinearLayout(this);
        messages.setOrientation(
                LinearLayout.VERTICAL
        );

        chatScroll.addView(
                messages,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );

        root.addView(
                chatScroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        // Composer
        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(
                dp(10),
                dp(8),
                dp(10),
                dp(10)
        );

        composer.setBackground(
                round(CARD_2, 22)
        );

        chatInput = new EditText(this);
        EditText input = chatInput;

        input.setHint(
                "Message Bynova AI..."
        );
        input.setHintTextColor(MUTED);
        input.setTextColor(WHITE);
        input.setTextSize(15);
        input.setSingleLine(false);
        input.setMaxLines(5);
        input.setBackgroundColor(
                Color.TRANSPARENT
        );
        input.setPadding(
                dp(8),
                0,
                dp(8),
                0
        );

        composer.addView(
                input,
                new LinearLayout.LayoutParams(
                        0,
                        dp(58),
                        1
                )
        );

        TextView aiMode = text(
                "AI",
                13,
                WHITE
        );
        aiMode.setGravity(Gravity.CENTER);
        aiMode.setTypeface(Typeface.DEFAULT_BOLD);
        aiMode.setBackground(round(CARD, 16));
        aiMode.setContentDescription("AI features");

        LinearLayout.LayoutParams aiParams =
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                );

        aiParams.setMargins(
                dp(4),
                0,
                dp(4),
                0
        );

        composer.addView(aiMode, aiParams);

        TextView image = text(
                "＋",
                23,
                WHITE
        );
        image.setGravity(Gravity.CENTER);
        image.setBackground(round(CARD, 16));
        image.setContentDescription("Attach image");

        composer.addView(
                image,
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                )
        );

        TextView mic = micButton = text(
                "🎙",
                21,
                WHITE
        );
        mic.setGravity(Gravity.CENTER);
        mic.setBackground(round(CARD, 16));
        mic.setContentDescription("Voice input");

        LinearLayout.LayoutParams micParams =
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                );

        micParams.setMargins(
                dp(4),
                0,
                dp(2),
                0
        );

        composer.addView(mic, micParams);

        TextView send = text(
                "➤",
                23,
                WHITE
        );
        send.setGravity(Gravity.CENTER);
        send.setBackground(
                round(PRIMARY, 16)
        );

        LinearLayout.LayoutParams sendParams =
                new LinearLayout.LayoutParams(
                        dp(48),
                        dp(48)
                );

        sendParams.setMargins(
                dp(6),
                0,
                0,
                0
        );

        composer.addView(
                send,
                sendParams
        );

        aiMode.setOnClickListener(v -> {
            final String[] options = {
                    "Ask AI",
                    "Analyze Image",
                    "Generate Image",
                    "Files",
                    "Voice"
            };

            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Bynova AI")
                    .setItems(
                            options,
                            (dialog, which) -> {
                                if (which == 0) {
                                    input.requestFocus();
                                    Toast.makeText(
                                            MainActivity.this,
                                            "Ask AI selected",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                } else if (which == 1) {
                                    openImagePicker();
                                } else if (which == 2) {
                                    input.requestFocus();
                                    Toast.makeText(
                                            MainActivity.this,
                                            "Image generation is being connected",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                } else if (which == 3) {
                                    showFeatureUnavailable(
                                            "File analysis",
                                            "File analysis is not connected yet."
                                    );
                                } else if (which == 4) {
                                    startVoiceInput();
                                }
                            }
                    )
                    .show();
        });

        image.setOnClickListener(
                v -> openImagePicker()
        );

        mic.setOnClickListener(v -> startVoiceInput());

        send.setOnClickListener(v -> {
            String message =
                    input.getText()
                            .toString()
                            .trim();

            if (message.isEmpty()) {
                input.requestFocus();
                return;
            }

            input.setText("");

            if (pendingImageUri != null) {
                android.net.Uri imageUri = pendingImageUri;
                pendingImageUri = null;

                addUserMessage("Image: " + message);
                sendImageToBackend(imageUri, message);
            } else {
                addUserMessage(message);
                sendToStreamingBackend(message);
            }

            // Keep input ready for the next message.
            input.requestFocus();
        });

        input.setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (actionId ==
                            android.view.inputmethod.EditorInfo
                                    .IME_ACTION_SEND) {

                        send.performClick();
                        return true;
                    }

                    return false;
                }
        );

        root.addView(
                composer,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(76)
                )
        );

        setContentView(root);

        input.requestFocus();
        restoreChatInputFocus();
        scrollChatToBottom();
    }

    void showChatHistory() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(14),
                dp(14),
                dp(14),
                dp(10)
        );
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹", 32, WHITE);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(CARD_2, 14));

        header.addView(
                back,
                new LinearLayout.LayoutParams(
                        dp(46),
                        dp(46)
                )
        );

        TextView title = text(
                "Chat History",
                21,
                WHITE
        );
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(12), 0, 0, 0);

        header.addView(
                title,
                new LinearLayout.LayoutParams(
                        0,
                        dp(46),
                        1
                )
        );

        TextView selectAll = text(
                "Select All",
                12,
                WHITE
        );
        selectAll.setGravity(Gravity.CENTER);
        selectAll.setPadding(
                dp(10),
                0,
                dp(10),
                0
        );
        selectAll.setBackground(round(CARD_2, 12));

        header.addView(
                selectAll,
                new LinearLayout.LayoutParams(
                        dp(82),
                        dp(40)
                )
        );

        root.addView(
                header,
                new LinearLayout.LayoutParams(
                        -1,
                        dp(54)
                )
        );

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setPadding(
                dp(4),
                dp(8),
                dp(4),
                dp(8)
        );

        TextView status = text(
                "Loading conversations...",
                13,
                MUTED
        );

        actionBar.addView(
                status,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        TextView deleteSelected = text(
                "Delete Selected",
                12,
                WHITE
        );
        deleteSelected.setGravity(Gravity.CENTER);
        deleteSelected.setPadding(
                dp(10),
                0,
                dp(10),
                0
        );
        deleteSelected.setBackground(
                round(PRIMARY, 12)
        );

        actionBar.addView(
                deleteSelected,
                new LinearLayout.LayoutParams(
                        dp(118),
                        dp(40)
                )
        );

        root.addView(actionBar);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        scroll.addView(
                list,
                new ScrollView.LayoutParams(
                        -1,
                        -2
                )
        );

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        -1,
                        0,
                        1
                )
        );

        java.util.ArrayList<String> selectedIds =
                new java.util.ArrayList<>();

        java.util.ArrayList<CheckBox> checkBoxes =
                new java.util.ArrayList<>();

        java.util.ArrayList<String> allIds =
                new java.util.ArrayList<>();

        back.setOnClickListener(v -> showHome());

        selectAll.setOnClickListener(v -> {
            boolean select = false;

            for (CheckBox box : checkBoxes) {
                if (!box.isChecked()) {
                    select = true;
                    break;
                }
            }

            for (int i = 0; i < checkBoxes.size(); i++) {
                CheckBox box = checkBoxes.get(i);
                box.setChecked(select);

                String id = allIds.get(i);

                if (select) {
                    if (!selectedIds.contains(id)) {
                        selectedIds.add(id);
                    }
                } else {
                    selectedIds.remove(id);
                }
            }

            status.setText(
                    selectedIds.size() +
                    " selected"
            );
        });

        deleteSelected.setOnClickListener(v -> {
            if (selectedIds.isEmpty()) {
                Toast.makeText(
                        MainActivity.this,
                        "Select chats first",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            new android.app.AlertDialog.Builder(
                    MainActivity.this
            )
                    .setTitle("Delete chats?")
                    .setMessage(
                            "Delete " +
                            selectedIds.size() +
                            " selected conversation(s)?"
                    )
                    .setNegativeButton(
                            "Cancel",
                            null
                    )
                    .setPositiveButton(
                            "Delete",
                            (dialog, which) -> {

                                java.util.ArrayList<String>
                                        idsToDelete =
                                        new java.util.ArrayList<>(
                                                selectedIds
                                        );

                                new Thread(() -> {
                                    int deleted = 0;

                                    for (String id :
                                            idsToDelete) {
                                        try {
                                            URL url =
                                                    new URL(
                                                            API_BASE_URL + "/chats/"
                                                                    + java.net.URLEncoder.encode(
                                                                            id,
                                                                            "UTF-8"
                                                                    )
                                                                    + "?client_id="
                                                                    + java.net.URLEncoder.encode(
                                                                            CLIENT_ID,
                                                                            "UTF-8"
                                                                    )
                                                    );

                                            HttpURLConnection connection =
                                                    (HttpURLConnection)
                                                            url.openConnection();

                                            connection.setRequestMethod(
                                                    "DELETE"
                                            );

                                            connection.setConnectTimeout(
                                                    5000
                                            );

                                            connection.setReadTimeout(
                                                    10000
                                            );

                                            int code =
                                                    connection.getResponseCode();

                                            connection.disconnect();

                                            if (code >= 200 &&
                                                    code < 300) {
                                                deleted++;
                                            }

                                        } catch (Exception ignored) {
                                        }
                                    }

                                    final int totalDeleted =
                                            deleted;

                                    runOnUiThread(() -> {
                                        Toast.makeText(
                                                MainActivity.this,
                                                totalDeleted +
                                                " chat(s) deleted",
                                                Toast.LENGTH_SHORT
                                        ).show();

                                        showChatHistory();
                                    });

                                }).start();
                            }
                    )
                    .show();
        });

        setContentView(root);

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(
                        API_BASE_URL + "/chats?client_id="
                                + java.net.URLEncoder.encode(
                                        CLIENT_ID,
                                        "UTF-8"
                                )
                );

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);

                int code =
                        connection.getResponseCode();

                InputStream input =
                        code >= 200 && code < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                String json = readStream(input);

                if (code < 200 || code >= 300) {
                    throw new Exception(
                            "History request failed: " + code
                    );
                }

                runOnUiThread(() -> {
                    list.removeAllViews();
                    checkBoxes.clear();
                    allIds.clear();
                    selectedIds.clear();

                    String chats =
                            extractJsonArray(
                                    json,
                                    "chats"
                            );

                    if (chats.isEmpty() ||
                            chats.equals("[]")) {

                        status.setText(
                                "No saved conversations yet."
                        );

                        return;
                    }

                    status.setText("");

                    try {
                        org.json.JSONArray array =
                                new org.json.JSONArray(chats);

                        for (int i = 0;
                                i < array.length();
                                i++) {

                            org.json.JSONObject chat =
                                    array.getJSONObject(i);

                            String chatId =
                                    chat.optString(
                                            "chat_id",
                                            ""
                                    );

                            String chatTitle =
                                    chat.optString(
                                            "title",
                                            "New Chat"
                                    );

                            int count =
                                    chat.optInt(
                                            "messages",
                                            0
                                    );

                            if (chatId.isEmpty()) {
                                continue;
                            }

                            allIds.add(chatId);

                            LinearLayout row =
                                    new LinearLayout(
                                            MainActivity.this
                                    );

                            row.setGravity(
                                    Gravity.CENTER_VERTICAL
                            );

                            row.setPadding(
                                    dp(8),
                                    dp(8),
                                    dp(8),
                                    dp(8)
                            );

                            row.setBackground(
                                    round(CARD, 16)
                            );

                            CheckBox check =
                                    new CheckBox(
                                            MainActivity.this
                                    );

                            check.setButtonTintList(
                                    android.content.res.ColorStateList.valueOf(
                                            WHITE
                                    )
                            );

                            checkBoxes.add(check);

                            row.addView(
                                    check,
                                    new LinearLayout.LayoutParams(
                                            dp(42),
                                            dp(52)
                                    )
                            );

                            LinearLayout info =
                                    new LinearLayout(
                                            MainActivity.this
                                    );

                            info.setOrientation(
                                    LinearLayout.VERTICAL
                            );

                            TextView titleView =
                                    text(
                                            chatTitle,
                                            15,
                                            WHITE
                                    );

                            titleView.setTypeface(
                                    Typeface.DEFAULT_BOLD
                            );

                            TextView countView =
                                    text(
                                            count +
                                            " messages",
                                            11,
                                            MUTED
                                    );

                            countView.setPadding(
                                    0,
                                    dp(3),
                                    0,
                                    0
                            );

                            info.addView(titleView);
                            info.addView(countView);

                            row.addView(
                                    info,
                                    new LinearLayout.LayoutParams(
                                            0,
                                            -2,
                                            1
                                    )
                            );

                            TextView open =
                                    text(
                                            "Open",
                                            11,
                                            WHITE
                                    );

                            open.setGravity(
                                    Gravity.CENTER
                            );

                            open.setBackground(
                                    round(CARD_2, 10)
                            );

                            row.addView(
                                    open,
                                    new LinearLayout.LayoutParams(
                                            dp(54),
                                            dp(36)
                                    )
                            );

                            LinearLayout.LayoutParams
                                    rowParams =
                                    new LinearLayout.LayoutParams(
                                            -1,
                                            -2
                                    );

                            rowParams.setMargins(
                                    0,
                                    0,
                                    0,
                                    dp(8)
                            );

                            list.addView(
                                    row,
                                    rowParams
                            );

                            check.setOnCheckedChangeListener(
                                    (buttonView, isChecked) -> {
                                        if (isChecked) {
                                            if (!selectedIds.contains(
                                                    chatId
                                            )) {
                                                selectedIds.add(
                                                        chatId
                                                );
                                            }
                                        } else {
                                            selectedIds.remove(
                                                    chatId
                                            );
                                        }

                                        status.setText(
                                                selectedIds.size() +
                                                " selected"
                                        );
                                    }
                            );

                            View.OnClickListener
                                    openChat =
                                    v -> loadChat(chatId);

                            open.setOnClickListener(
                                    openChat
                            );
                        }

                    } catch (Exception e) {
                        status.setText(
                                "Could not read chat history."
                        );
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        status.setText(
                                "Could not load chat history."
                        )
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    void loadChat(String chatId) {
        Toast.makeText(
                MainActivity.this,
                "Opening chat...",
                Toast.LENGTH_SHORT
        ).show();

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                String encodedChatId =
                        java.net.URLEncoder.encode(
                                chatId,
                                "UTF-8"
                        );

                String encodedClientId =
                        java.net.URLEncoder.encode(
                                CLIENT_ID,
                                "UTF-8"
                        );

                URL url = new URL(
                        API_BASE_URL + "/chats/" +
                        encodedChatId +
                        "?client_id=" +
                        encodedClientId
                );

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(15000);

                int code =
                        connection.getResponseCode();

                InputStream input =
                        code >= 200 && code < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                String response =
                        readStream(input);

                if (code < 200 || code >= 300) {
                    throw new Exception(
                            "Chat load failed: " + code
                    );
                }

                org.json.JSONObject object =
                        new org.json.JSONObject(response);

                org.json.JSONArray history =
                        object.optJSONArray("messages");

                if (history == null) {
                    throw new Exception(
                            "No messages found"
                    );
                }

                runOnUiThread(() -> {
                    currentChatId = chatId;

                    showHome();

                    messages.removeAllViews();

                    for (int i = 0;
                            i < history.length();
                            i++) {

                        try {
                            org.json.JSONObject item =
                                    history.getJSONObject(i);

                            String role =
                                    item.optString(
                                            "role",
                                            ""
                                    );

                            org.json.JSONArray parts =
                                    item.optJSONArray(
                                            "parts"
                                    );

                            if (parts == null ||
                                    parts.length() == 0) {
                                continue;
                            }

                            String message =
                                    parts.optJSONObject(0)
                                            != null
                                    ? parts.optJSONObject(0)
                                            .optString(
                                                    "text",
                                                    ""
                                            )
                                    : "";

                            if (message.isEmpty()) {
                                continue;
                            }

                            if ("user".equals(role)) {
                                addUserMessage(message);
                            } else if ("model".equals(role)) {
                                addBotMessage(message);
                            }

                        } catch (Exception ignored) {
                        }
                    }

                    scrollChatToBottom();

                    Toast.makeText(
                            MainActivity.this,
                            "Chat opened",
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Could not open chat: " +
                                e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    void clearConversation() {
        if (messages != null) {
            messages.removeAllViews();
        }

        currentChatId = "";
        lastUserMessage = "";

        Toast.makeText(
                MainActivity.this,
                "New chat started",
                Toast.LENGTH_SHORT
        ).show();
    }

    void sendToBackend(final String message) {
        lastUserMessage = message;

        addThinkingMessage();

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(BACKEND_URL);
                connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );

                String safeMessage = message
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");

                String safeChatId = currentChatId
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"");

                String json =
                        "{\"message\":\"" + safeMessage
                        + "\",\"client_id\":\"" + CLIENT_ID
                        + "\",\"chat_id\":\"" + safeChatId + "\"}";

                byte[] body =
                        json.getBytes(StandardCharsets.UTF_8);

                OutputStream output =
                        connection.getOutputStream();

                output.write(body);
                output.flush();
                output.close();

                int responseCode =
                        connection.getResponseCode();

                InputStream stream;

                if (responseCode >= 200 &&
                        responseCode < 300) {
                    stream = connection.getInputStream();
                } else {
                    stream = connection.getErrorStream();
                }

                String response = readStream(stream);
                String reply = extractReply(response);

                String returnedChatId =
                        extractJsonValue(
                                response,
                                "chat_id"
                        );

                if (!returnedChatId.isEmpty()) {
                    currentChatId = returnedChatId;
                }

                if (reply.isEmpty()) {
                    reply = response;
                }

                final String finalReply = reply;

                runOnUiThread(() -> {
                    removeThinkingMessage();
                    addBotMessage(finalReply);

                    if (voiceReplyMode) {
                        speakReply(finalReply);
                        voiceReplyMode = false;
                    }
                });

            } catch (Exception e) {

                final String error =
                        "Connection problem.\n\n"
                        + e.getMessage();

                runOnUiThread(() -> {
                    removeThinkingMessage();
                    addBotError(error);
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }



    void addThinkingMessage() {
        if (messages == null) {
            messages = new LinearLayout(this);
            messages.setOrientation(
                    LinearLayout.VERTICAL
            );
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(14),
                dp(10),
                dp(14),
                dp(10)
        );
        card.setBackground(round(CARD_2, 18));

        TextView label = text(
                "Bynova AI",
                11,
                MUTED
        );
        label.setTypeface(Typeface.DEFAULT_BOLD);

        TextView thinking = text(
                "Thinking...",
                14,
                WHITE
        );

        thinking.setPadding(
                0,
                dp(4),
                0,
                0
        );

        card.addView(label);
        card.addView(thinking);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        params.setMargins(
                dp(4),
                dp(6),
                dp(45),
                dp(6)
        );

        messages.addView(card, params);
        scrollChatToBottom();
    }

    void removeStreamingMessage() {
        if (messages == null ||
                activeStreamingCard == null) {
            return;
        }

        android.view.ViewParent parent =
                activeStreamingCard.getParent();

        if (parent instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) parent)
                    .removeView(activeStreamingCard);
        }

        activeStreamingCard = null;
    }

    void removeThinkingMessage() {
        if (messages == null ||
                messages.getChildCount() == 0) {
            return;
        }

        View last =
                messages.getChildAt(
                        messages.getChildCount() - 1
                );

        messages.removeView(last);
    }

    void sendToStreamingBackend(final String message) {
        lastUserMessage = message;

        runOnUiThread(() -> {
            removeStreamingMessage();
            addThinkingMessage();
        });

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(STREAM_URL);
                connection = (HttpURLConnection) url.openConnection();

                // Configure the request BEFORE obtaining the output stream
                // or calling getResponseCode().
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(60000);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                );
                connection.setRequestProperty(
                        "Accept",
                        "application/x-ndjson"
                );
                connection.setRequestProperty(
                        "Accept-Charset",
                        "UTF-8"
                );

                String safeMessage = message
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");

                String safeChatId = currentChatId
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"");

                String json =
                        "{\"message\":\"" + safeMessage
                        + "\",\"client_id\":\"" + CLIENT_ID
                        + "\",\"chat_id\":\"" + safeChatId + "\"}";

                OutputStream output = connection.getOutputStream();
                output.write(json.getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.close();

                int responseCode = connection.getResponseCode();

                if (responseCode < 200 || responseCode >= 300) {
                    InputStream errorStream = connection.getErrorStream();
                    String errorResponse = readStream(errorStream);

                    runOnUiThread(() -> {
                        removeThinkingMessage();
                        addBotError(
                                "AI connection error (" +
                                responseCode + ")\n\n" +
                                errorResponse
                        );
                    });
                    return;
                }

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8
                                ),
                                8192
                        );

                StringBuilder fullReply = new StringBuilder();
                String line;

                runOnUiThread(() -> {
                    removeThinkingMessage();

                    activeStreamingCard =
                            (LinearLayout) createStreamingCard();

                    messages.addView(
                            activeStreamingCard,
                            new LinearLayout.LayoutParams(
                                    -2,
                                    -2
                            )
                    );

                    scrollChatToBottom();
                });

                try {
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();

                        if (line.isEmpty()) {
                            continue;
                        }

                        try {
                        org.json.JSONObject object =
                                new org.json.JSONObject(line);

                        String type =
                                object.optString("type", "");

                        if ("chunk".equals(type)) {
                            String chunk =
                                    object.optString("text", "");

                            if (!chunk.isEmpty()) {
                                fullReply.append(chunk);

                                final String liveText =
                                        fullReply.toString();

                                runOnUiThread(() -> {
                                    if (activeStreamingCard == null) {
                                        activeStreamingCard =
                                                (LinearLayout) createStreamingCard();

                                        messages.addView(
                                                activeStreamingCard,
                                                new LinearLayout.LayoutParams(
                                                        -2,
                                                        -2
                                                )
                                        );
                                    }

                                    if (activeStreamingCard.getChildCount() >= 2) {
                                        View body =
                                                activeStreamingCard.getChildAt(1);

                                        if (body instanceof TextView) {
                                            ((TextView) body).setText(liveText);
                                        }
                                    }

                                    scrollChatToBottom();
                                });
                            }
                        }

                        String returnedChatId =
                                object.optString("chat_id", "");

                        if (!returnedChatId.isEmpty()) {
                            currentChatId = returnedChatId;
                        }

                        if ("error".equals(type)) {
                            String error =
                                    object.optString(
                                            "message",
                                            "Streaming error"
                                    );

                            runOnUiThread(() -> {
                                removeStreamingMessage();
                                addBotError(error);
                            });
                        }

                        } catch (Exception ignored) {
                            // Ignore malformed/non-JSON streaming lines.
                        }
                    }
                } catch (java.io.EOFException ignored) {
                    // Server closed the streaming connection after sending data.
                    // Treat already received chunks as the response.
                }

                reader.close();

                final String finalReply =
                        fullReply.toString().trim();

                runOnUiThread(() -> {
                    if (!finalReply.isEmpty()) {
                        removeStreamingMessage();
                        addBotMessage(finalReply);
                        restoreChatInputFocus();

                        if (voiceReplyMode) {
                            speakReply(finalReply);
                            voiceReplyMode = false;
                        }
                    } else {
                        addBotError("AI returned an empty response.");
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    removeThinkingMessage();
                    removeStreamingMessage();
                    String detail = e.toString();

                    addBotError(
                            "Connection problem.\n\n" +
                            detail
                    );
                });

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }


    private LinearLayout activeStreamingCard = null;

    void updateStreamingMessage(String message) {
        if (messages == null) {
            return;
        }

        runOnUiThread(() -> {
            if (activeStreamingCard == null) {
                activeStreamingCard = (LinearLayout) createStreamingCard();

                messages.addView(
                        activeStreamingCard,
                        new LinearLayout.LayoutParams(
                                -2,
                                -2
                        )
                );
            }

            if (activeStreamingCard.getChildCount() >= 2) {
                View body =
                        activeStreamingCard.getChildAt(1);

                if (body instanceof TextView) {
                    TextView liveBody = (TextView) body;

                    liveBody.setText(
                            buildSmartReply(message)
                    );

                    liveBody.setTextSize(16);
                    liveBody.setTextIsSelectable(true);
                }
            }

            scrollChatToBottom();
        });
    }


    View createStreamingCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(14),
                dp(10),
                dp(10),
                dp(8)
        );
        card.setBackground(round(CARD_2, 18));

        TextView label = text(
                "Bynova AI",
                11,
                MUTED
        );

        label.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(label);

        TextView body = text(
                "",
                16,
                WHITE
        );

        body.setPadding(
                0,
                dp(5),
                dp(4),
                dp(8)
        );

        body.setTextIsSelectable(true);
        card.addView(body);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        params.setMargins(
                dp(4),
                dp(6),
                dp(45),
                dp(6)
        );

        card.setLayoutParams(params);

        return card;
    }





    String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                stream,
                                StandardCharsets.UTF_8
                        )
                );

        StringBuilder result =
                new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line);
        }

        reader.close();

        return result.toString();
    }

    String extractReply(String json) {
        String key = "\"reply\":\"";
        int start = json.indexOf(key);

        if (start < 0) {
            return "";
        }

        start += key.length();

        StringBuilder result =
                new StringBuilder();

        boolean escaped = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                if (c == 'n') {
                    result.append('\n');
                } else if (c == 'r') {
                    result.append('\r');
                } else if (c == 't') {
                    result.append('\t');
                } else if (c == 'b') {
                    result.append('\b');
                } else if (c == 'f') {
                    result.append('\f');
                } else if (c == '"') {
                    result.append('"');
                } else if (c == '\\') {
                    result.append('\\');
                } else if (c == 'u' &&
                           i + 4 < json.length()) {

                    String hex =
                            json.substring(i + 1, i + 5);

                    try {
                        result.append(
                                (char) Integer.parseInt(
                                        hex,
                                        16
                                )
                        );

                        i += 4;

                    } catch (NumberFormatException e) {
                        result.append("\\u");
                    }

                } else {
                    result.append(c);
                }

                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                break;
            }

            result.append(c);
        }

        return result.toString();
    }

    void addUserMessage(String message) {
        if (messages == null) {
            messages = new LinearLayout(this);
            messages.setOrientation(
                    LinearLayout.VERTICAL
            );
        }

        lastUserMessage = message;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(14),
                dp(10),
                dp(14),
                dp(10)
        );
        card.setBackground(round(PRIMARY, 18));

        TextView label = text(
                "You",
                11,
                Color.rgb(220, 220, 255)
        );
        label.setTypeface(Typeface.DEFAULT_BOLD);

        TextView body = text(
                message,
                14,
                WHITE
        );

        body.setPadding(
                0,
                dp(4),
                0,
                0
        );

        card.addView(label);
        card.addView(body);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        params.gravity = Gravity.RIGHT;

        params.setMargins(
                dp(45),
                dp(6),
                dp(4),
                dp(6)
        );

        messages.addView(card, params);
        scrollChatToBottom();
    }

    String extractJsonArray(String json, String key) {
        try {
            org.json.JSONObject object =
                    new org.json.JSONObject(json);

            org.json.JSONArray array =
                    object.optJSONArray(key);

            return array != null ? array.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    String extractJsonValue(String json, String key) {
        try {
            String source = json == null ? "" : json.trim();

            if (source.startsWith("[")) {
                org.json.JSONArray array =
                        new org.json.JSONArray(source);

                if (array.length() == 0) {
                    return "";
                }

                Object first = array.opt(0);

                if (first instanceof org.json.JSONObject) {
                    return ((org.json.JSONObject) first)
                            .optString(key, "");
                }

                return "";
            }

            org.json.JSONObject object =
                    new org.json.JSONObject(source);

            return object.optString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    void addBotMessage(String message) {
        if (messages == null) {
            messages = new LinearLayout(this);
            messages.setOrientation(LinearLayout.VERTICAL);
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(14),
                dp(10),
                dp(10),
                dp(8)
        );
        card.setBackground(round(CARD_2, 18));

        TextView label = text(
                "Bynova AI",
                11,
                MUTED
        );
        label.setTypeface(Typeface.DEFAULT_BOLD);

        card.addView(label);

        boolean hasCode =
                message.contains("```");

        if (hasCode) {
            addFormattedResponse(card, message);
        } else {
            TextView body = createSmartReplyView(message);

            body.setPadding(
                    0,
                    dp(5),
                    dp(4),
                    dp(8)
            );

            body.setTextIsSelectable(true);

            card.addView(body);
        }

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.LEFT);

        TextView copy = text(
                "Copy",
                11,
                MUTED
        );

        copy.setGravity(Gravity.CENTER);
        copy.setPadding(
                dp(10),
                dp(5),
                dp(10),
                dp(5)
        );
        copy.setBackground(round(CARD, 12));

        copy.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager)
                            getSystemService(
                                    CLIPBOARD_SERVICE
                            );

            android.content.ClipData data =
                    android.content.ClipData.newPlainText(
                            "Bynova AI response",
                            message
                    );

            clipboard.setPrimaryClip(data);

            Toast.makeText(
                    MainActivity.this,
                    "Response copied",
                    Toast.LENGTH_SHORT
            ).show();
        });

        tools.addView(
                copy,
                new LinearLayout.LayoutParams(
                        dp(62),
                        dp(34)
                )
        );

        card.addView(tools);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        -2,
                        -2
                );

        params.setMargins(
                dp(4),
                dp(6),
                dp(45),
                dp(6)
        );

        messages.addView(card, params);
        scrollChatToBottom();
    }

    TextView createSmartReplyView(String message) {

        TextView view = text(
                "",
                16,
                WHITE
        );

        view.setTextIsSelectable(true);
        view.setPadding(
                0,
                dp(5),
                dp(4),
                dp(8)
        );

        android.text.SpannableStringBuilder builder =
                buildSmartReply(message);

        view.setText(builder);

        return view;
    }

    android.text.SpannableStringBuilder buildSmartReply(
            String message
    ) {

        android.text.SpannableStringBuilder builder =
                new android.text.SpannableStringBuilder();

        if (message == null) {
            return builder;
        }

        String[] lines = message.split("\\n", -1);

        for (int i = 0; i < lines.length; i++) {

            String line = lines[i].trim();

            if (line.isEmpty()) {
                builder.append("\n");
                continue;
            }

            boolean heading =
                    line.matches("^#{1,6}\\s+.+")
                    || line.matches(
                            "^(Topic|Answer|Summary|Important|Note|Steps|Solution|Result|Conclusion|Warning|Success|Error)\\s*:?$"
                    );

            String clean = line.replaceFirst(
                    "^#{1,6}\\s+",
                    ""
            ).trim();

            int start = builder.length();

            builder.append(clean);

            int end = builder.length();

            if (heading) {

                builder.setSpan(
                        new android.text.style.StyleSpan(
                                Typeface.BOLD
                        ),
                        start,
                        end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                builder.setSpan(
                        new android.text.style.ForegroundColorSpan(
                                PRIMARY
                        ),
                        start,
                        end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                builder.setSpan(
                        new android.text.style.RelativeSizeSpan(
                                1.10f
                        ),
                        start,
                        end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

            } else {

                builder.setSpan(
                        new android.text.style.RelativeSizeSpan(
                                1.0f
                        ),
                        start,
                        end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }

            if (i < lines.length - 1) {
                builder.append("\n");
            }
        }

        // Highlight important status words.
        String[] importantWords = {
                "IMPORTANT",
                "Important",
                "WARNING",
                "Warning",
                "SUCCESS",
                "Success",
                "ERROR",
                "Error",
                "NOTE",
                "Note"
        };

        for (String word : importantWords) {

            java.util.regex.Matcher statusMatcher =
                    java.util.regex.Pattern
                            .compile(
                                    "\\b" + java.util.regex.Pattern.quote(word) + "\\b"
                            )
                            .matcher(builder.toString());

            while (statusMatcher.find()) {

                int start = statusMatcher.start();
                int end = statusMatcher.end();

                int statusColor = PRIMARY;

                if (word.equalsIgnoreCase("SUCCESS")) {
                    statusColor = Color.rgb(90, 210, 140);
                } else if (word.equalsIgnoreCase("WARNING")) {
                    statusColor = Color.rgb(255, 190, 80);
                } else if (word.equalsIgnoreCase("ERROR")) {
                    statusColor = Color.rgb(255, 100, 110);
                }

                builder.setSpan(
                        new android.text.style.ForegroundColorSpan(
                                statusColor
                        ),
                        start,
                        end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                builder.setSpan(
                        new android.text.style.StyleSpan(
                                Typeface.BOLD
                        ),
                        start,
                        end,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        // Highlight inline code such as `gradle build`
        java.util.regex.Pattern inlineCode =
                java.util.regex.Pattern.compile("`([^`]+)`");

        java.util.regex.Matcher codeMatcher =
                inlineCode.matcher(builder.toString());

        while (codeMatcher.find()) {

            int start = codeMatcher.start();
            int end = codeMatcher.end();

            builder.setSpan(
                    new android.text.style.ForegroundColorSpan(
                            PRIMARY
                    ),
                    start,
                    end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            builder.setSpan(
                    new android.text.style.StyleSpan(
                            Typeface.BOLD
                    ),
                    start,
                    end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            builder.setSpan(
                    new android.text.style.TypefaceSpan(
                            Typeface.MONOSPACE
                    ),
                    start,
                    end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        // Highlight URLs without changing the actual URL text.
        java.util.regex.Pattern urlPattern =
                java.util.regex.Pattern.compile(
                        "(https?://[^\\s]+)"
                );

        java.util.regex.Matcher urlMatcher =
                urlPattern.matcher(builder.toString());

        while (urlMatcher.find()) {

            int start = urlMatcher.start();
            int end = urlMatcher.end();

            builder.setSpan(
                    new android.text.style.ForegroundColorSpan(
                            PRIMARY
                    ),
                    start,
                    end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            builder.setSpan(
                    new android.text.style.UnderlineSpan(),
                    start,
                    end,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        return builder;
    }

    void addSmartFormattedText(
            LinearLayout card,
            String part
    ) {

        if (part == null || part.trim().isEmpty()) {
            return;
        }

        TextView explanation =
                createSmartReplyView(part.trim());

        card.addView(explanation);
    }

    void applyCodeSyntaxHighlight(
            android.text.SpannableStringBuilder builder,
            String code,
            String language
    ) {

        if (code == null || code.isEmpty()) {
            return;
        }

        String upperLanguage =
                language == null
                        ? "CODE"
                        : language.toUpperCase();

        int keywordColor =
                Color.rgb(180, 150, 255);

        int stringColor =
                Color.rgb(120, 220, 170);

        int numberColor =
                Color.rgb(255, 190, 110);

        int commentColor =
                Color.rgb(130, 145, 170);

        int typeColor =
                Color.rgb(100, 190, 255);

        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile(
                        "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'"
                );

        java.util.regex.Matcher matcher =
                pattern.matcher(code);

        while (matcher.find()) {
            builder.setSpan(
                    new android.text.style.ForegroundColorSpan(
                            stringColor
                    ),
                    matcher.start(),
                    matcher.end(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        java.util.regex.Pattern numberPattern =
                java.util.regex.Pattern.compile(
                        "\\b\\d+(?:\\.\\d+)?\\b"
                );

        matcher = numberPattern.matcher(code);

        while (matcher.find()) {
            builder.setSpan(
                    new android.text.style.ForegroundColorSpan(
                            numberColor
                    ),
                    matcher.start(),
                    matcher.end(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        String keywords;

        if (upperLanguage.equals("PYTHON")) {
            keywords =
                    "\\b(def|class|return|if|else|elif|for|while|in|"
                    + "import|from|as|try|except|finally|with|lambda|"
                    + "True|False|None|and|or|not|is|async|await|"
                    + "yield|pass|break|continue)\\b";
        } else if (
                upperLanguage.equals("JAVA") ||
                upperLanguage.equals("KOTLIN")
        ) {
            keywords =
                    "\\b(public|private|protected|class|interface|"
                    + "static|final|void|int|long|double|float|boolean|"
                    + "new|return|if|else|for|while|switch|case|break|"
                    + "continue|try|catch|finally|throw|throws|"
                    + "extends|implements|this|super|true|false|null|"
                    + "fun|val|var|when|object|data|is|in)\\b";
        } else if (
                upperLanguage.equals("JAVASCRIPT") ||
                upperLanguage.equals("JS")
        ) {
            keywords =
                    "\\b(const|let|var|function|return|if|else|for|"
                    + "while|new|class|extends|import|from|export|"
                    + "async|await|try|catch|throw|true|false|null|"
                    + "undefined|this)\\b";
        } else if (
                upperLanguage.equals("BASH") ||
                upperLanguage.equals("SHELL")
        ) {
            keywords =
                    "\\b(if|then|else|fi|for|while|do|done|in|case|"
                    + "esac|function)\\b";
        } else {
            keywords =
                    "\\b(class|public|private|protected|return|if|"
                    + "else|for|while|new|true|false|null|void|"
                    + "function|const|let|var|import|from|export)\\b";
        }

        matcher =
                java.util.regex.Pattern
                        .compile(keywords)
                        .matcher(code);

        while (matcher.find()) {
            builder.setSpan(
                    new android.text.style.ForegroundColorSpan(
                            keywordColor
                    ),
                    matcher.start(),
                    matcher.end(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        java.util.regex.Pattern typePattern =
                java.util.regex.Pattern.compile(
                        "\\b(String|Integer|Long|Double|Float|Boolean|"
                        + "List|Map|Set|ArrayList|HashMap|"
                        + "StringBuilder|TextView|LinearLayout|"
                        + "JSONObject|JSONArray)\\b"
                );

        matcher = typePattern.matcher(code);

        while (matcher.find()) {
            builder.setSpan(
                    new android.text.style.ForegroundColorSpan(
                            typeColor
                    ),
                    matcher.start(),
                    matcher.end(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        java.util.regex.Pattern commentPattern =
                java.util.regex.Pattern.compile(
                        "(//.*$|#.*$)",
                        java.util.regex.Pattern.MULTILINE
                );

        matcher = commentPattern.matcher(code);

        while (matcher.find()) {
            builder.setSpan(
                    new android.text.style.ForegroundColorSpan(
                            commentColor
                    ),
                    matcher.start(),
                    matcher.end(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }

    void addFormattedResponse(
            LinearLayout card,
            String message
    ) {
        String[] parts = message.split("```", -1);

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            if (part.trim().isEmpty()) {
                continue;
            }

            if (i % 2 == 0) {
                addSmartFormattedText(
                        card,
                        part.trim()
                );

            } else {
                String code = part.trim();
                String language = "CODE";

                int newline = code.indexOf('\n');

                if (newline > 0) {
                    String firstLine =
                            code.substring(0, newline)
                                    .trim()
                                    .toLowerCase();

                    if (
                            firstLine.equals("java") ||
                            firstLine.equals("python") ||
                            firstLine.equals("javascript") ||
                            firstLine.equals("js") ||
                            firstLine.equals("kotlin") ||
                            firstLine.equals("xml") ||
                            firstLine.equals("html") ||
                            firstLine.equals("css") ||
                            firstLine.equals("c") ||
                            firstLine.equals("cpp") ||
                            firstLine.equals("c++") ||
                            firstLine.equals("bash") ||
                            firstLine.equals("shell")
                    ) {
                        language = firstLine.toUpperCase();
                        code = code.substring(newline + 1).trim();
                    }
                }

                LinearLayout codeCard =
                        new LinearLayout(this);

                codeCard.setOrientation(
                        LinearLayout.VERTICAL
                );

                codeCard.setPadding(
                        dp(10),
                        dp(8),
                        dp(10),
                        dp(8)
                );

                codeCard.setBackground(
                        round(
                                Color.rgb(8, 12, 22),
                                14
                        )
                );

                LinearLayout codeHeader =
                        new LinearLayout(this);

                codeHeader.setGravity(
                        Gravity.CENTER_VERTICAL
                );

                TextView languageView = text(
                        language,
                        10,
                        MUTED
                );

                languageView.setTypeface(
                        Typeface.DEFAULT_BOLD
                );

                codeHeader.addView(
                        languageView,
                        new LinearLayout.LayoutParams(
                                0,
                                dp(30),
                                1
                        )
                );

                TextView copyCode = text(
                        "Copy Code",
                        10,
                        WHITE
                );

                copyCode.setGravity(Gravity.CENTER);
                copyCode.setPadding(
                        dp(10),
                        dp(5),
                        dp(10),
                        dp(5)
                );

                copyCode.setBackground(
                        round(CARD_2, 10)
                );

                final String codeToCopy = code;

                copyCode.setOnClickListener(v -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager)
                                    getSystemService(
                                            CLIPBOARD_SERVICE
                                    );

                    android.content.ClipData data =
                            android.content.ClipData.newPlainText(
                                    "Bynova AI code",
                                    codeToCopy
                            );

                    clipboard.setPrimaryClip(data);

                    Toast.makeText(
                            MainActivity.this,
                            "Code copied",
                            Toast.LENGTH_SHORT
                    ).show();
                });

                codeHeader.addView(
                        copyCode,
                        new LinearLayout.LayoutParams(
                                dp(92),
                                dp(30)
                        )
                );

                codeCard.addView(codeHeader);

                HorizontalScrollView codeScroll =
                        new HorizontalScrollView(this);

                codeScroll.setFillViewport(false);

                android.text.SpannableStringBuilder codeBuilder =
                        new android.text.SpannableStringBuilder(code);

                applyCodeSyntaxHighlight(
                        codeBuilder,
                        code,
                        language
                );

                TextView codeView = text(
                        "",
                        13,
                        WHITE
                );

                codeView.setText(codeBuilder);

                codeView.setTypeface(
                        Typeface.MONOSPACE
                );

                codeView.setTextIsSelectable(true);
                codeView.setPadding(
                        dp(4),
                        dp(8),
                        dp(12),
                        dp(8)
                );

                codeScroll.addView(
                        codeView,
                        new HorizontalScrollView.LayoutParams(
                                -2,
                                -2
                        )
                );

                codeCard.addView(
                        codeScroll,
                        new LinearLayout.LayoutParams(
                                -1,
                                -2
                        )
                );

                card.addView(
                        codeCard,
                        new LinearLayout.LayoutParams(
                                -1,
                                -2
                        )
                );
            }
        }
    }

    void addBotError(String message) {
        addBotMessage(message);

        TextView retry = text(
                "Retry",
                12,
                WHITE
        );

        retry.setGravity(Gravity.CENTER);
        retry.setBackground(round(PRIMARY, 14));
        retry.setPadding(
                dp(12),
                dp(7),
                dp(12),
                dp(7)
        );

        retry.setOnClickListener(v -> {
            if (!lastUserMessage.isEmpty()) {
                sendToStreamingBackend(lastUserMessage);
            }
        });

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        dp(72),
                        dp(38)
                );

        params.setMargins(
                dp(4),
                0,
                0,
                dp(8)
        );

        messages.addView(retry, params);
        scrollChatToBottom();
    }

    void restoreChatInputFocus() {
        if (chatInput == null) {
            return;
        }

        chatInput.postDelayed(() -> {
            if (chatInput == null) {
                return;
            }

            chatInput.requestFocus();

            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);

            if (imm != null) {
                imm.showSoftInput(
                        chatInput,
                        android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
                );
            }
        }, 150);
    }

    void scrollChatToBottom() {
        if (chatScroll != null) {
            chatScroll.postDelayed(
                    () -> chatScroll.fullScroll(
                            ScrollView.FOCUS_DOWN
                    ),
                    100
            );
        }
    }

    void addAction(
            LinearLayout parent,
            String icon,
            String name
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(
                dp(6),
                dp(8),
                dp(6),
                dp(8)
        );
        card.setBackground(round(CARD, 18));

        TextView iconView = text(icon, 22, WHITE);
        iconView.setGravity(Gravity.CENTER);

        TextView nameView = text(name, 11, MUTED);
        nameView.setGravity(Gravity.CENTER);
        nameView.setPadding(0, dp(4), 0, 0);

        card.addView(iconView);
        card.addView(nameView);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(90),
                        1
                );

        params.setMargins(
                dp(4),
                0,
                dp(4),
                0
        );

        parent.addView(card, params);

        card.setOnClickListener(v -> {

            if ("Ask AI".equals(name)) {
                Toast.makeText(
                        MainActivity.this,
                        "Ask AI is ready",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if ("Image".equals(name)) {
                openImagePicker();
                return;
            }

            if ("Files".equals(name)) {
                openFilePicker();
                return;
            }

            if ("Voice".equals(name)) {
                startVoiceInput();
                return;
            }

            if ("Security".equals(name)) {
                showSecurityCheck();
                return;
            }
        });
    }

    void openFilePicker() {
        android.content.Intent intent =
                new android.content.Intent(
                        android.content.Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                android.content.Intent.CATEGORY_OPENABLE
        );

        intent.setType("*/*");

        startActivityForResult(intent, 9003);
    }

    void openImagePicker() {
        android.content.Intent intent =
                new android.content.Intent(
                        android.content.Intent.ACTION_OPEN_DOCUMENT
                );

        intent.addCategory(
                android.content.Intent.CATEGORY_OPENABLE
        );

        intent.setType("image/*");

        startActivityForResult(intent, 9002);
    }

    void showSecurityCheck() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Bynova Security Check")
                .setMessage(
                        "Security analysis can check an APK for:\n\n" +
                        "• App package information\n" +
                        "• Debug / release build\n" +
                        "• APK signing information\n" +
                        "• Requested permissions\n" +
                        "• Potentially sensitive permissions\n\n" +
                        "Select an APK to begin the security check."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        "Select APK",
                        (dialog, which) -> {
                            android.content.Intent intent =
                                    new android.content.Intent(
                                            android.content.Intent.ACTION_OPEN_DOCUMENT
                                    );

                            intent.addCategory(
                                    android.content.Intent.CATEGORY_OPENABLE
                            );

                            intent.setType("application/vnd.android.package-archive");

                            startActivityForResult(intent, 9001);
                        }
                )
                .show();
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            android.content.Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if ((requestCode != 9001 &&
                requestCode != 9002 &&
                requestCode != 9003) ||
                resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        android.net.Uri selectedUri = data.getData();

        if (requestCode == 9002) {
            pendingImageUri = selectedUri;
            Toast.makeText(
                    this,
                    "Image attached — type your question",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (requestCode == 9003) {
            sendFileToBackend(selectedUri);
            return;
        }

        android.net.Uri apkUri = selectedUri;

        try {
            android.content.pm.PackageManager pm =
                    getPackageManager();

            String apkPath =
                    getPathFromUri(apkUri);

            if (apkPath == null || apkPath.isEmpty()) {
                showSecurityResult(
                        "Could not read the selected APK."
                );
                return;
            }

            android.content.pm.PackageInfo info =
                    pm.getPackageArchiveInfo(
                            apkPath,
                            android.content.pm.PackageManager.GET_PERMISSIONS
                    );

            if (info == null) {
                showSecurityResult(
                        "Could not analyze this APK."
                );
                return;
            }

            StringBuilder report =
                    new StringBuilder();

            report.append("Package: ")
                    .append(info.packageName)
                    .append("\n\n");

            if (android.os.Build.VERSION.SDK_INT >= 28) {
                report.append("Version: ")
                        .append(info.versionName)
                        .append(" (")
                        .append(info.getLongVersionCode())
                        .append(")\n\n");
            } else {
                report.append("Version: ")
                        .append(info.versionName)
                        .append("\n\n");
            }

            boolean debug =
                    (info.applicationInfo.flags &
                    android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
                    != 0;

            report.append("Build: ")
                    .append(debug ? "DEBUG" : "RELEASE")
                    .append("\n\n");

            report.append("Permissions:\n");

            String[] permissions =
                    info.requestedPermissions;

            if (permissions == null ||
                    permissions.length == 0) {

                report.append("None declared\n");

            } else {
                for (String permission : permissions) {
                    report.append("• ")
                            .append(permission)
                            .append("\n");
                }
            }

            report.append("\nSecurity note:\n")
                    .append(
                        "This is a static APK check. " +
                        "It does not guarantee that an app is safe."
                    );

            showSecurityResult(
                    report.toString()
            );

        } catch (Exception e) {
            showSecurityResult(
                    "Security check failed:\n\n" +
                    e.getMessage()
            );
        }
    }

    void sendFileToBackend(android.net.Uri uri) {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                String fileName = "file";

                android.database.Cursor cursor =
                        getContentResolver().query(
                                uri,
                                null,
                                null,
                                null,
                                null
                        );

                if (cursor != null) {
                    int nameIndex =
                            cursor.getColumnIndex(
                                    android.provider.OpenableColumns.DISPLAY_NAME
                            );

                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }

                    cursor.close();
                }

                InputStream input =
                        getContentResolver().openInputStream(uri);

                if (input == null) {
                    throw new Exception("Could not read file");
                }

                java.io.ByteArrayOutputStream buffer =
                        new java.io.ByteArrayOutputStream();

                byte[] data = new byte[8192];
                int length;

                while ((length = input.read(data)) != -1) {
                    buffer.write(data, 0, length);
                }

                input.close();

                byte[] fileBytes = buffer.toByteArray();

                if (fileBytes.length == 0) {
                    throw new Exception("File is empty");
                }

                String boundary =
                        "----BynovaFile" +
                        System.currentTimeMillis();

                URL url = new URL(
                        API_BASE_URL + "/file"
                );

                connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(60000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "multipart/form-data; boundary=" +
                                boundary
                );

                OutputStream output =
                        connection.getOutputStream();

                output.write(
                        ("--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; " +
                        "name=\"message\"\r\n\r\n" +
                        "Analyze this file and explain its contents." +
                        "\r\n").getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                String mime =
                        getContentResolver().getType(uri);

                if (mime == null) {
                    mime = "application/octet-stream";
                }

                output.write(
                        ("--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; " +
                        "name=\"file\"; filename=\"" +
                        fileName.replace("\"", "") +
                        "\"\r\n" +
                        "Content-Type: " + mime +
                        "\r\n\r\n").getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                output.write(fileBytes);

                output.write(
                        ("\r\n--" +
                        boundary +
                        "--\r\n").getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                output.flush();
                output.close();

                int responseCode =
                        connection.getResponseCode();

                InputStream stream;

                if (responseCode >= 200 &&
                        responseCode < 300) {
                    stream = connection.getInputStream();
                } else {
                    stream = connection.getErrorStream();
                }

                String response = readStream(stream);

                String reply = extractReply(response);

                if (reply.isEmpty()) {
                    reply = response;
                }

                final String finalReply = reply;
                final String finalFileName = fileName;

                runOnUiThread(() -> {
                    addUserMessage(
                            "File: " + finalFileName
                    );

                    addBotMessage(finalReply);
                });

            } catch (Exception e) {
                final String error =
                        "File analysis failed.\n\n" +
                        e.getMessage();

                runOnUiThread(() ->
                        addBotError(error)
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    void sendImageToBackend(android.net.Uri uri, String message) {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                java.io.InputStream input =
                        getContentResolver().openInputStream(uri);

                if (input == null) {
                    throw new Exception("Could not read image");
                }

                java.io.ByteArrayOutputStream buffer =
                        new java.io.ByteArrayOutputStream();

                byte[] data = new byte[8192];
                int length;

                while ((length = input.read(data)) != -1) {
                    buffer.write(data, 0, length);
                }

                input.close();

                byte[] imageBytes =
                        buffer.toByteArray();

                if (imageBytes.length == 0) {
                    throw new Exception("Selected image is empty");
                }

                String boundary =
                        "----BynovaImage" +
                        System.currentTimeMillis();

                URL url = new URL(
                        IMAGE_URL
                );

                connection =
                        (HttpURLConnection)
                        url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(60000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "multipart/form-data; boundary=" +
                        boundary
                );

                OutputStream output =
                        connection.getOutputStream();

                output.write(
                        ("--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; " +
                        "name=\"message\"\r\n\r\n" +
                        message +
                        "\r\n").getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                String mime =
                        getContentResolver()
                                .getType(uri);

                if (mime == null) {
                    mime = "image/jpeg";
                }

                output.write(
                        ("--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; " +
                        "name=\"image\"; filename=\"image\"\r\n" +
                        "Content-Type: " + mime +
                        "\r\n\r\n").getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                output.write(imageBytes);

                output.write(
                        ("\r\n--" +
                        boundary +
                        "--\r\n").getBytes(
                                StandardCharsets.UTF_8
                        )
                );

                output.flush();
                output.close();

                InputStream stream;

                if (connection.getResponseCode() >= 200 &&
                        connection.getResponseCode() < 300) {
                    stream = connection.getInputStream();
                } else {
                    stream = connection.getErrorStream();
                }

                String response =
                        readStream(stream);

                String reply =
                        extractReply(response);

                if (reply.isEmpty()) {
                    reply = response;
                }

                final String finalReply =
                        reply;

                runOnUiThread(() -> {
                    addUserMessage("Image");
                    addBotMessage(finalReply);
                });

            } catch (Exception e) {
                final String error =
                        "Image analysis failed: " +
                        e.getMessage();

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                error,
                                Toast.LENGTH_LONG
                        ).show()
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    String getPathFromUri(android.net.Uri uri) {
        try {
            java.io.InputStream input =
                    getContentResolver().openInputStream(uri);

            if (input == null) {
                return null;
            }

            java.io.File file =
                    new java.io.File(
                            getCacheDir(),
                            "security_check.apk"
                    );

            java.io.FileOutputStream output =
                    new java.io.FileOutputStream(file);

            byte[] buffer = new byte[8192];
            int length;

            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }

            output.flush();
            output.close();
            input.close();

            return file.getAbsolutePath();

        } catch (Exception e) {
            return null;
        }
    }

    void showSecurityResult(String report) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Security Report")
                .setMessage(report)
                .setPositiveButton("OK", null)
                .show();
    }

    void showFeatureUnavailable(
            String feature,
            String message
    ) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(feature)
                .setMessage(message)
                .setPositiveButton(
                        "OK",
                        null
                )
                .show();
    }

}
