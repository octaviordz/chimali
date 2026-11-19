package com.chimali.security;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemClock;
import android.content.Intent;
import android.view.Gravity;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.regex.Pattern;

/** Test-APK-only clipboard observer. It reads description metadata and never reads clip text. */
public final class ClipboardObserverActivity extends Activity {
    public static final String EXTRA_RUN_ID = "com.chimali.security.extra.RUN_ID";
    public static final String EXTRA_MODE = "com.chimali.security.extra.MODE";
    public static final String MODE_OBSERVE = "observe";
    public static final String MODE_CONTROL_MATCH = "control-match";
    public static final String MODE_CONTROL_MISMATCH = "control-mismatch";
    public static final String MODE_CLEAR_IF_OWNED = "clear-if-owned";
    private static final String TOKEN_KEY = "com.chimali.clipboard.OWNERSHIP_TOKEN";
    private static final String EXPECTED_TOKEN_FILE = "clipboard-observer.expected";
    private static final String RESULT_PREFIX = "clipboard-observer-result-";
    private TextView statusView;
    private String pendingRunId;
    private String pendingMode;
    private boolean resultWritten;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        acceptIntent(getIntent());
        statusView = new TextView(this);
        statusView.setText("Clipboard observation ready");
        statusView.setGravity(Gravity.CENTER);
        setContentView(statusView);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptIntent(intent);
        if (hasWindowFocus()) writePendingResult();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) writePendingResult();
    }

    private void acceptIntent(Intent intent) {
        pendingRunId = intent.getStringExtra(EXTRA_RUN_ID);
        pendingMode = intent.getStringExtra(EXTRA_MODE);
        if (pendingMode == null) pendingMode = MODE_OBSERVE;
        resultWritten = false;
    }

    private void writePendingResult() {
        if (resultWritten || pendingRunId == null || statusView == null) return;
        resultWritten = true;
        String result = "invalid-run";
        if (Pattern.matches("[A-Za-z0-9_-]{1,64}", pendingRunId)) {
            result = observe(pendingRunId, pendingMode);
        }
        statusView.setText("Clipboard observation: " + result);
    }

    private String observe(String runId, String mode) {
        String expected = readExpectedToken();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String controlResult = "not-run";
        if (MODE_CONTROL_MATCH.equals(mode) || MODE_CONTROL_MISMATCH.equals(mode)) {
            controlResult = runCapabilityControl(clipboard, expected, mode);
        }
        String classification = classify(clipboard, expected);
        String cleanupOutcome = "not-requested";
        if (MODE_CLEAR_IF_OWNED.equals(mode)) {
            cleanupOutcome = clearOnlyIfStillOwned(clipboard, expected, classification);
            classification = classify(clipboard, expected);
        }
        KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean locked = keyguard != null && keyguard.isKeyguardLocked();
        try {
            JSONObject record = new JSONObject();
            record.put("runId", runId);
            record.put("elapsedRealtimeMs", SystemClock.elapsedRealtime());
            record.put("observerPid", Process.myPid());
            record.put("classification", classification);
            record.put("windowFocused", hasWindowFocus());
            record.put("deviceLocked", locked);
            record.put("capabilityControl", controlResult);
            record.put("cleanupOutcome", cleanupOutcome);
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    openFileOutput(RESULT_PREFIX + runId + ".json", Context.MODE_PRIVATE)))) {
                writer.write(record.toString());
            }
        } catch (Exception ignored) {
            return "inaccessible";
        }
        return classification;
    }

    private String clearOnlyIfStillOwned(ClipboardManager clipboard, String expected, String classification) {
        if (clipboard == null || expected == null || "inaccessible".equals(classification)) {
            return "skipped-ownership-unavailable";
        }
        if (!"marker-match".equals(classification)) {
            return "skipped-replaced-or-absent";
        }
        try {
            ClipDescription description = clipboard.getPrimaryClipDescription();
            String observed = description == null || description.getExtras() == null
                    ? null
                    : description.getExtras().getString(TOKEN_KEY);
            if (!expected.equals(observed)) return "skipped-replaced-or-absent";
            clipboard.clearPrimaryClip();
            return "clear-request-submitted";
        } catch (SecurityException | IllegalStateException ignored) {
            return "skipped-ownership-unavailable";
        }
    }

    private String readExpectedToken() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(openFileInput(EXPECTED_TOKEN_FILE)))) {
            String token = reader.readLine();
            return token == null || token.isEmpty() ? null : token;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String runCapabilityControl(ClipboardManager clipboard, String expected, String mode) {
        if (clipboard == null || expected == null) return "inaccessible";
        String token = MODE_CONTROL_MATCH.equals(mode)
                ? expected
                : "deliberately-different-observer-control";
        try {
            ClipData clip = ClipData.newPlainText("synthetic observer capability control", "");
            PersistableBundle extras = new PersistableBundle();
            extras.putString(TOKEN_KEY, token);
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
            clipboard.setPrimaryClip(clip);
            return classify(clipboard, expected);
        } catch (SecurityException | IllegalStateException ignored) {
            return "inaccessible";
        }
    }

    private String classify(ClipboardManager clipboard, String expected) {
        if (clipboard == null || expected == null) return "inaccessible";
        try {
            ClipDescription description = clipboard.getPrimaryClipDescription();
            if (description == null) return "absent";
            String observed = description.getExtras() == null ? null : description.getExtras().getString(TOKEN_KEY);
            return expected.equals(observed) ? "marker-match" : "replaced";
        } catch (SecurityException | IllegalStateException ignored) {
            return "inaccessible";
        }
    }
}
