package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Relay Capture v1.3 Universal Intake. */
public class MainActivityV13 extends MainActivityV124 {
    TextView incomingSummary;
    boolean receivedExternalShare = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (receivedExternalShare) { showStage(0); updateWorkflowUi(); }
    }

    @Override protected void onNewIntent(Intent in) {
        int before = items.size();
        super.onNewIntent(in);
        if (items.size() > before) {
            WorkflowProfile p = currentWorkflowProfile();
            String fallback = p.steps.isEmpty() ? "Capture" : p.steps.get(0).label;
            for (int i = before; i < items.size(); i++) itemSteps.put(items.get(i), fallback);
            receivedExternalShare = true;
            showStage(0);
            updateWorkflowUi();
        }
    }

    @Override void build() {
        super.build();
        applyTopInset(mainScreen);
    }

    void applyTopInset(View screen) {
        if (!(screen instanceof ScrollView)) return;
        ScrollView sc = (ScrollView) screen;
        if (sc.getChildCount() == 0) return;
        View child = sc.getChildAt(0);
        int left = child.getPaddingLeft(), baseTop = child.getPaddingTop(), right = child.getPaddingRight(), bottom = child.getPaddingBottom();
        child.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(left, baseTop + insets.getSystemWindowInsetTop(), right, bottom);
            return insets;
        });
        child.requestApplyInsets();
    }

    @Override LinearLayout productRoot(String title, String subtitle) {
        LinearLayout body = super.productRoot(title, subtitle);
        View content = getWindow().getDecorView().findViewById(android.R.id.content);
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0)
            applyTopInset(((ViewGroup) content).getChildAt(0));
        return body;
    }

    @Override void buildContextStage() {
        super.buildContextStage();
        incomingSummary = new TextView(this);
        incomingSummary.setTextSize(13);
        incomingSummary.setTextColor(Color.rgb(75,82,94));
        incomingSummary.setPadding(dp(12),dp(10),dp(12),dp(10));
        incomingSummary.setBackground(roundRect(Color.rgb(244,247,252),dp(10),Color.rgb(220,226,236)));
        incomingSummary.setVisibility(View.GONE);
        contextStage.addView(incomingSummary, 1);
    }

    @Override void receive(Intent in) {
        if (in == null) return;
        String action = in.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) return;
        int before = items.size();
        for (Uri u : uris(in)) items.add(Item.uri(u, getName(u), "shared"));
        if (Intent.ACTION_SEND.equals(action)) {
            CharSequence sharedText = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && sharedText.length() > 0 && items.size() == before) {
                String value = sharedText.toString();
                boolean url = looksLikeUrl(value.trim());
                File stagedText = stageTextArtifact(value, url ? "shared-link" : "shared-text");
                if (stagedText != null) items.add(Item.file(stagedText, url ? "shared-url" : "shared-text"));
            }
        }
        if (items.size() > before) receivedExternalShare = true;
        in.setAction(Intent.ACTION_MAIN);
        state();
    }

    boolean looksLikeUrl(String s) {
        String x = s.toLowerCase(Locale.CANADA);
        return x.startsWith("http://") || x.startsWith("https://") || x.startsWith("mailto:") || x.startsWith("tel:");
    }

    File stageTextArtifact(String text, String stem) {
        try {
            File dir = new File(getFilesDir(), "relay_pending");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File f = new File(dir, stem + "_" + System.currentTimeMillis() + ".txt");
            try (OutputStream out = new FileOutputStream(f)) { out.write(text.getBytes(StandardCharsets.UTF_8)); out.flush(); }
            return f;
        } catch (Exception e) { toast("Relay could not stage the shared text."); return null; }
    }

    @Override void chooseImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, supportedImportMimeTypes());
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, IMPORT);
    }

    String[] supportedImportMimeTypes() {
        return new String[]{"image/*","application/pdf","text/*","application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation","application/rtf",
                "application/json","application/xml","text/csv","text/plain"};
    }

    @Override protected void onActivityResult(int rq, int rc, Intent d) {
        super.onActivityResult(rq, rc, d);
        if (rc == RESULT_OK && rq == IMPORT) updateWorkflowUi();
    }

    @Override void updateWorkflowUi() {
        super.updateWorkflowUi();
        if (incomingSummary != null) {
            if (items.isEmpty()) incomingSummary.setVisibility(View.GONE);
            else {
                ArrayList<String> parts = new ArrayList<>();
                for (Item it : items) parts.add(sourceLabel(it.source) + " · " + it.name);
                incomingSummary.setText((receivedExternalShare ? "Received by Relay\n" : "Staged in Relay\n") + String.join("\n", parts));
                incomingSummary.setVisibility(View.VISIBLE);
            }
        }
    }

    String sourceLabel(String source) {
        if (source == null) return "ITEM";
        String s = source.toLowerCase(Locale.CANADA);
        if (s.contains("camera")) return "CAMERA";
        if (s.contains("screenshot")) return "SCREENSHOT";
        if (s.contains("shared-url")) return "LINK";
        if (s.contains("shared-text")) return "TEXT";
        if (s.contains("shared")) return "SHARED";
        if (s.contains("import")) return "IMPORTED";
        if (s.contains("recover")) return "RECOVERED";
        return source.toUpperCase(Locale.CANADA);
    }

    @Override void reviewWorkflowItems() {
        if (items.isEmpty()) { toast("Nothing captured yet."); return; }
        String[] rows = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i); String step = itemSteps.getOrDefault(it, "Capture"); String n = itemNotes.get(it);
            rows[i] = sourceLabel(it.source) + "  ·  " + step + "\n" + it.name + (n == null || n.isEmpty() ? "" : "\n" + n);
        }
        new AlertDialog.Builder(this).setTitle("Captured items").setItems(rows, (dialog, which) -> editCapturedItem(items.get(which))).setPositiveButton("Done", null).show();
    }

    @Override void resetForNewSession() { receivedExternalShare = false; super.resetForNewSession(); }
}
