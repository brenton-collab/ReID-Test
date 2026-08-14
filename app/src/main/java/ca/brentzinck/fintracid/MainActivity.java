package ca.brentzinck.fintracid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_FOLDER_REQUEST = 1001;
    private static final int PICK_IMPORT_REQUEST = 1002;
    private static final int CAMERA_REQUEST = 1003;
    private static final String PREFS = "relay_capture_prefs";
    private static final String KEY_FOLDER_URI = "folder_uri";
    private static final String KEY_CATEGORIES = "categories";

    private static final List<String> SEEDED_CATEGORIES = Arrays.asList(
            "FINTRAC ID", "Client Document", "Signed Page", "Property / Problem",
            "Utility / Statement", "Receipt / Expense", "General Capture"
    );
    private static final List<String> ENTITY_TYPES = Arrays.asList(
            "None", "Person", "Matter", "Property", "Organization", "Other"
    );

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<SourceItem> stagedItems = new ArrayList<>();

    private TextView statusView;
    private TextView stagedView;
    private Spinner categorySpinner;
    private Spinner entityTypeSpinner;
    private EditText entityField;
    private EditText matterField;
    private EditText noteField;
    private Button secureButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        handleIncomingIntent(getIntent());
        updateState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void buildUi() {
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Relay Capture");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Capture. Context. Secure handoff.");
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setPadding(0, 0, 0, dp(12));
        root.addView(statusView);

        LinearLayout captureRow = new LinearLayout(this);
        captureRow.setOrientation(LinearLayout.HORIZONTAL);
        captureRow.setGravity(Gravity.CENTER);

        Button camera = new Button(this);
        camera.setText("Camera");
        camera.setOnClickListener(v -> startActivityForResult(new Intent(this, CaptureActivity.class), CAMERA_REQUEST));
        captureRow.addView(camera, weightParams());

        Button screenshot = new Button(this);
        screenshot.setText("Screenshot");
        screenshot.setOnClickListener(v -> Toast.makeText(this, "Screenshot capture is part of locked v1 and is the next implementation pass.", Toast.LENGTH_LONG).show());
        captureRow.addView(screenshot, weightParams());

        Button importButton = new Button(this);
        importButton.setText("Import");
        importButton.setOnClickListener(v -> chooseImport());
        captureRow.addView(importButton, weightParams());
        root.addView(captureRow);

        stagedView = new TextView(this);
        stagedView.setTextSize(15);
        stagedView.setPadding(0, dp(14), 0, dp(10));
        root.addView(stagedView);

        TextView catLabel = label("Category"); root.addView(catLabel);
        LinearLayout catRow = new LinearLayout(this); catRow.setOrientation(LinearLayout.HORIZONTAL);
        categorySpinner = new Spinner(this); refreshCategorySpinner(null);
        catRow.addView(categorySpinner, weightParams());
        Button addCat = new Button(this); addCat.setText("+"); addCat.setOnClickListener(v -> promptAddCategory());
        catRow.addView(addCat, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));
        Button editCat = new Button(this); editCat.setText("Edit"); editCat.setOnClickListener(v -> manageCategories());
        catRow.addView(editCat, new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(catRow);

        root.addView(label("Entity type"));
        entityTypeSpinner = new Spinner(this);
        entityTypeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ENTITY_TYPES));
        root.addView(entityTypeSpinner);

        root.addView(label("Person / property / organization"));
        entityField = edit("Optional entity name or identifier"); root.addView(entityField);

        root.addView(label("Matter / transaction / project"));
        matterField = edit("Optional matter reference"); root.addView(matterField);

        root.addView(label("Note"));
        noteField = edit("Optional context"); noteField.setMinLines(2); root.addView(noteField);

        secureButton = new Button(this);
        secureButton.setText("Secure to Relay Intake");
        secureButton.setOnClickListener(v -> secureStaged());
        LinearLayout.LayoutParams secureParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secureParams.topMargin = dp(16);
        root.addView(secureButton, secureParams);

        Button folder = new Button(this);
        folder.setText("Choose / change intake folder");
        folder.setOnClickListener(v -> chooseFolder());
        root.addView(folder);

        setContentView(scroll);
    }

    private TextView label(String text) {
        TextView v = new TextView(this); v.setText(text); v.setTypeface(Typeface.DEFAULT_BOLD); v.setPadding(0, dp(12), 0, dp(3)); return v;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(false); return e;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); p.setMargins(dp(2), 0, dp(2), 0); return p;
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) return;
        for (Uri uri : extractSharedUris(intent)) stagedItems.add(SourceItem.shared(uri, getDisplayName(uri)));
        intent.setAction(Intent.ACTION_MAIN);
        updateState();
    }

    private void chooseImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_IMPORT_REQUEST);
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_FOLDER_REQUEST && data.getData() != null) {
            Uri treeUri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(treeUri, flags); }
            catch (SecurityException e) { Toast.makeText(this, "Could not preserve access to that folder.", Toast.LENGTH_LONG).show(); return; }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FOLDER_URI, treeUri.toString()).apply();
            updateState();
            return;
        }

        if (requestCode == PICK_IMPORT_REQUEST) {
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    stagedItems.add(SourceItem.shared(uri, getDisplayName(uri)));
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData(); stagedItems.add(SourceItem.shared(uri, getDisplayName(uri)));
            }
            updateState(); return;
        }

        if (requestCode == CAMERA_REQUEST) {
            String path = data.getStringExtra(CaptureActivity.EXTRA_CAPTURE_PATH);
            if (path != null) stagedItems.add(SourceItem.local(new File(path), "camera"));
            updateState();
        }
    }

    private void secureStaged() {
        if (stagedItems.isEmpty()) { Toast.makeText(this, "Capture or import something first.", Toast.LENGTH_SHORT).show(); return; }
        Uri folderUri = getSavedFolderUri();
        if (folderUri == null) { Toast.makeText(this, "Choose the Relay Intake folder first.", Toast.LENGTH_LONG).show(); chooseFolder(); return; }

        String category = String.valueOf(categorySpinner.getSelectedItem());
        String entityType = String.valueOf(entityTypeSpinner.getSelectedItem());
        String entity = entityField.getText().toString().trim();
        String matter = matterField.getText().toString().trim();
        String note = noteField.getText().toString().trim();
        String captureId = UUID.randomUUID().toString();
        long securedAt = System.currentTimeMillis();

        secureButton.setEnabled(false); statusView.setText("TRANSFER IN PROGRESS");
        executor.execute(() -> {
            String failure = null; int saved = 0;
            try {
                DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri);
                if (folder == null || !folder.canWrite()) throw new IllegalStateException("Relay Intake is not writable.");
                int seq = 1;
                for (SourceItem item : new ArrayList<>(stagedItems)) {
                    saveItem(folder, item, seq++, captureId, category, entityType, entity, matter, note, securedAt);
                    saved++;
                }
            } catch (Exception e) { failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }

            int finalSaved = saved; String finalFailure = failure;
            runOnUiThread(() -> {
                secureButton.setEnabled(true);
                if (finalFailure == null) {
                    for (SourceItem item : stagedItems) if (item.localFile != null) item.localFile.delete();
                    stagedItems.clear(); updateState();
                    statusView.setText("✓ SECURED · " + finalSaved + (finalSaved == 1 ? " item" : " items") + " saved to Relay Intake. Local temporary copies removed.");
                } else {
                    statusView.setText("LOCAL ONLY · Transfer failed. Relay retained private local captures for retry.");
                    Toast.makeText(this, finalFailure, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void saveItem(DocumentFile folder, SourceItem item, int seq, String captureId, String category,
                          String entityType, String entity, String matter, String note, long securedAt) throws Exception {
        ContentResolver resolver = getContentResolver();
        String mime = item.localFile != null ? "image/jpeg" : resolver.getType(item.uri);
        if (mime == null || mime.isBlank()) mime = "application/octet-stream";
        String ext = extensionFor(item.originalName, mime);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA).format(new Date());
        String anchor = !entity.isBlank() ? entity : (!matter.isBlank() ? matter : "Unassigned");
        String filename = sanitize(date + " - " + anchor + " - " + category + " - " + seq) + ext;

        DocumentFile target = folder.createFile(mime, filename);
        if (target == null) throw new IllegalStateException("Destination file could not be created.");
        long bytes = 0;
        try (InputStream in = item.localFile != null ? new FileInputStream(item.localFile) : resolver.openInputStream(item.uri);
             OutputStream out = resolver.openOutputStream(target.getUri(), "w")) {
            if (in == null || out == null) throw new IllegalStateException("Capture could not be opened.");
            byte[] buf = new byte[64 * 1024]; int n;
            while ((n = in.read(buf)) != -1) { out.write(buf, 0, n); bytes += n; }
            out.flush();
        }
        if (!target.exists()) throw new IllegalStateException("Destination verification failed.");
        long providerLength = target.length();
        if (providerLength == 0 && bytes > 0) throw new IllegalStateException("Destination reported zero bytes after transfer.");

        JSONObject meta = new JSONObject();
        meta.put("relay_schema_version", 1);
        meta.put("capture_id", captureId);
        meta.put("captured_at", item.createdAt);
        meta.put("secured_at", securedAt);
        meta.put("source", item.source);
        meta.put("category", category);
        meta.put("entity_type", entityType);
        meta.put("entity", entity);
        meta.put("matter", matter);
        meta.put("note", note);
        meta.put("original_name", item.originalName == null ? "" : item.originalName);
        meta.put("app_version", "0.2.0");
        meta.put("mime_type", mime);
        meta.put("file_name", filename);
        meta.put("bytes_written", bytes);

        DocumentFile sidecar = folder.createFile("application/json", filename + ".json");
        if (sidecar == null) throw new IllegalStateException("Provenance sidecar could not be created.");
        try (OutputStream out = resolver.openOutputStream(sidecar.getUri(), "w")) {
            if (out == null) throw new IllegalStateException("Provenance sidecar could not be opened.");
            out.write(meta.toString(2).getBytes(StandardCharsets.UTF_8)); out.flush();
        }
    }

    private void promptAddCategory() {
        EditText input = new EditText(this); input.setHint("New category");
        new AlertDialog.Builder(this).setTitle("Add category").setView(input)
                .setPositiveButton("Add", (d, w) -> { String value = input.getText().toString().trim(); if (!value.isBlank()) { addCategory(value); refreshCategorySpinner(value); } })
                .setNegativeButton("Cancel", null).show();
    }

    private void manageCategories() {
        List<String> categories = getCategories();
        String[] values = categories.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Remove a category")
                .setItems(values, (d, which) -> {
                    String selected = values[which];
                    if (SEEDED_CATEGORIES.contains(selected)) {
                        Toast.makeText(this, "Seed categories stay available in v1. Add your own alternatives freely.", Toast.LENGTH_LONG).show();
                    } else {
                        Set<String> custom = getCustomCategorySet(); custom.remove(selected);
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY_CATEGORIES, custom).apply(); refreshCategorySpinner(null);
                    }
                }).setNegativeButton("Done", null).show();
    }

    private void addCategory(String value) {
        Set<String> custom = getCustomCategorySet(); custom.add(value);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY_CATEGORIES, custom).apply();
    }

    private Set<String> getCustomCategorySet() {
        return new LinkedHashSet<>(getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet(KEY_CATEGORIES, new LinkedHashSet<>()));
    }

    private List<String> getCategories() {
        LinkedHashSet<String> all = new LinkedHashSet<>(SEEDED_CATEGORIES); all.addAll(getCustomCategorySet()); return new ArrayList<>(all);
    }

    private void refreshCategorySpinner(String select) {
        if (categorySpinner == null) return;
        List<String> cats = getCategories();
        categorySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
        if (select != null) { int idx = cats.indexOf(select); if (idx >= 0) categorySpinner.setSelection(idx); }
    }

    private Uri getSavedFolderUri() {
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_FOLDER_URI, null); return raw == null ? null : Uri.parse(raw);
    }

    private void updateState() {
        if (statusView == null) return;
        if (getSavedFolderUri() == null) statusView.setText("Relay Intake not configured. Choose a Drive folder before securing.");
        else if (stagedItems.isEmpty()) statusView.setText("Ready. New camera captures stay private on this device until secured.");
        stagedView.setText(stagedItems.isEmpty() ? "No staged items." : stagedItems.size() + (stagedItems.size() == 1 ? " item staged." : " items staged."));
        secureButton.setEnabled(!stagedItems.isEmpty());
    }

    private List<Uri> extractSharedUris(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        ClipData clip = intent.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) if (clip.getItemAt(i).getUri() != null) uris.add(clip.getItemAt(i).getUri());
        if (uris.isEmpty() && Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM); if (list != null) uris.addAll(list);
        } else if (uris.isEmpty()) {
            Uri one = intent.getParcelableExtra(Intent.EXTRA_STREAM); if (one != null) uris.add(one);
        }
        return uris;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) { int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) return c.getString(i); }
        } catch (Exception ignored) {}
        return null;
    }

    private String extensionFor(String original, String mime) {
        if (original != null) { int dot = original.lastIndexOf('.'); if (dot >= 0 && dot > original.length() - 8) return original.substring(dot); }
        if ("application/pdf".equals(mime)) return ".pdf";
        if ("image/png".equals(mime)) return ".png";
        return mime.startsWith("image/") ? ".jpg" : ".bin";
    }

    private String sanitize(String s) {
        String clean = s.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim(); return clean.isEmpty() ? "Relay Capture" : clean;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() { super.onDestroy(); if (isFinishing()) executor.shutdown(); }

    private static class SourceItem {
        final Uri uri; final File localFile; final String source; final String originalName; final long createdAt;
        private SourceItem(Uri uri, File localFile, String source, String originalName) {
            this.uri = uri; this.localFile = localFile; this.source = source; this.originalName = originalName; this.createdAt = System.currentTimeMillis();
        }
        static SourceItem shared(Uri uri, String name) { return new SourceItem(uri, null, "share-import", name); }
        static SourceItem local(File file, String source) { return new SourceItem(null, file, source, file.getName()); }
    }
}
