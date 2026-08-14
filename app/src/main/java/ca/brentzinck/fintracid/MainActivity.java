package ca.brentzinck.fintracid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private static final int FOLDER = 1001;
    private static final int IMPORT = 1002;
    private static final int CAMERA = 1003;
    private static final int SCREENSHOT = 1004;

    private static final String PREFS = "relay_capture_prefs";
    private static final String KEY_FOLDER = "folder_uri";
    private static final String KEY_CATS = "categories";

    private static final int INK = Color.rgb(25, 27, 31);
    private static final int MUTED = Color.rgb(103, 108, 117);
    private static final int SURFACE = Color.rgb(246, 247, 249);
    private static final int CARD = Color.WHITE;
    private static final int BLUE = Color.rgb(31, 86, 180);
    private static final int BORDER = Color.rgb(222, 225, 230);
    private static final int SUCCESS = Color.rgb(28, 110, 72);

    private static final List<String> SEEDED = Arrays.asList(
            "FINTRAC ID", "Client Document", "Signed Page", "Property / Problem",
            "Utility / Statement", "Receipt / Expense", "General Capture"
    );

    private static final List<String> TYPES = Arrays.asList(
            "None", "Person", "Matter", "Property", "Organization", "Other"
    );

    private final List<Item> items = new ArrayList<>();
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private TextView status;
    private TextView staged;
    private Spinner category;
    private Spinner type;
    private EditText entity;
    private EditText matter;
    private EditText note;
    private Button secure;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        build();
        recoverPending();
        receive(getIntent());
        state();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receive(intent);
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(SURFACE);

        LinearLayout root = vertical();
        root.setPadding(dp(20), dp(24), dp(20), dp(36));
        scroll.addView(root);

        TextView eyebrow = text("RELAY", 12, BLUE, Typeface.BOLD);
        eyebrow.setLetterSpacing(.16f);
        root.addView(eyebrow);

        TextView title = text("Capture", 34, INK, Typeface.BOLD);
        title.setPadding(0, dp(2), 0, 0);
        root.addView(title);

        TextView subtitle = text("Get it off the edge and into the system.", 16, MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        LinearLayout captureCard = card();
        captureCard.addView(sectionTitle("Capture or import"));
        captureCard.addView(sectionHint("Everything enters the same secure staging flow."));

        LinearLayout captureRow = new LinearLayout(this);
        captureRow.setOrientation(LinearLayout.HORIZONTAL);
        captureRow.setPadding(0, dp(12), 0, 0);
        captureRow.addView(actionButton("Camera", v -> startActivityForResult(new Intent(this, CaptureActivity.class), CAMERA)), weightWithMargins());
        captureRow.addView(actionButton("Screenshot", v -> startActivityForResult(new Intent(this, ScreenshotActivity.class), SCREENSHOT)), weightWithMargins());
        captureRow.addView(actionButton("Import", v -> chooseImport()), weightWithMargins());
        captureCard.addView(captureRow);
        root.addView(captureCard, cardParams());

        LinearLayout stagingCard = card();
        stagingCard.addView(sectionTitle("Staging"));
        staged = text("Nothing staged.", 16, INK, Typeface.BOLD);
        staged.setPadding(0, dp(7), 0, dp(4));
        stagingCard.addView(staged);
        stagingCard.addView(secondaryButton("Review staged items", v -> review()));
        root.addView(stagingCard, cardParams());

        LinearLayout contextCard = card();
        contextCard.addView(sectionTitle("Context"));
        contextCard.addView(sectionHint("Enough provenance to find and route this later."));

        contextCard.addView(fieldLabel("Category"));
        LinearLayout catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        category = new Spinner(this);
        styleSpinner(category);
        refreshCats("General Capture");
        catRow.addView(category, weightWithMargins());
        catRow.addView(compactButton("+", v -> addCat()), new LinearLayout.LayoutParams(dp(52), dp(48)));
        Button editCat = compactButton("Edit", v -> manageCats());
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(dp(72), dp(48));
        editParams.leftMargin = dp(6);
        catRow.addView(editCat, editParams);
        contextCard.addView(catRow);

        contextCard.addView(fieldLabel("Entity type"));
        type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TYPES));
        styleSpinner(type);
        contextCard.addView(type, fullFieldParams());

        contextCard.addView(fieldLabel("Person / property / organization"));
        entity = edit("Optional entity name or identifier");
        contextCard.addView(entity, fullFieldParams());

        contextCard.addView(fieldLabel("Matter / transaction / project"));
        matter = edit("Optional matter reference");
        contextCard.addView(matter, fullFieldParams());

        contextCard.addView(fieldLabel("Note"));
        note = edit("Optional context");
        note.setMinLines(2);
        note.setGravity(Gravity.TOP);
        contextCard.addView(note, fullFieldParams());

        root.addView(contextCard, cardParams());

        status = text("", 14, MUTED, Typeface.NORMAL);
        status.setPadding(dp(4), dp(2), dp(4), dp(12));
        root.addView(status);

        secure = primaryButton("Secure to Relay Intake", v -> secure());
        root.addView(secure, fullButtonParams());

        Button destination = secondaryButton("Destination settings", v -> chooseFolder());
        root.addView(destination, fullButtonParams());

        setContentView(scroll);
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundRect(CARD, BORDER, 18));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(12);
        return p;
    }

    private TextView sectionTitle(String value) {
        return text(value, 18, INK, Typeface.BOLD);
    }

    private TextView sectionHint(String value) {
        TextView view = text(value, 14, MUTED, Typeface.NORMAL);
        view.setPadding(0, dp(3), 0, 0);
        return view;
    }

    private TextView fieldLabel(String value) {
        TextView view = text(value, 13, MUTED, Typeface.BOLD);
        view.setPadding(0, dp(14), 0, dp(5));
        return view;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(145, 149, 157));
        e.setTextColor(INK);
        e.setTextSize(16);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(roundRect(Color.WHITE, BORDER, 12));
        return e;
    }

    private void styleSpinner(Spinner spinner) {
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setBackground(roundRect(Color.WHITE, BORDER, 12));
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button b = baseButton(label, listener);
        b.setTextColor(INK);
        b.setBackground(roundRect(Color.rgb(241, 243, 247), Color.rgb(230, 232, 237), 14));
        return b;
    }

    private Button primaryButton(String label, View.OnClickListener listener) {
        Button b = baseButton(label, listener);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(roundRect(BLUE, BLUE, 14));
        return b;
    }

    private Button secondaryButton(String label, View.OnClickListener listener) {
        Button b = baseButton(label, listener);
        b.setTextColor(INK);
        b.setBackground(roundRect(Color.WHITE, BORDER, 12));
        return b;
    }

    private Button compactButton(String label, View.OnClickListener listener) {
        Button b = baseButton(label, listener);
        b.setTextColor(INK);
        b.setMinWidth(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(roundRect(Color.rgb(241, 243, 247), BORDER, 12));
        return b;
    }

    private Button baseButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        b.setStateListAnimator(null);
        return b;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout.LayoutParams weightWithMargins() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1f);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        return p;
    }

    private LinearLayout.LayoutParams fullFieldParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.bottomMargin = dp(9);
        return p;
    }

    private void receive(Intent incoming) {
        if (incoming == null) return;
        String action = incoming.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) return;
        for (Uri uri : uris(incoming)) items.add(Item.uri(uri, getName(uri), "shared"));
        incoming.setAction(Intent.ACTION_MAIN);
        state();
    }

    private void chooseImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, IMPORT);
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == FOLDER && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, flags);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FOLDER, uri.toString()).apply();
            } catch (Exception e) {
                toast("Could not preserve folder access.");
            }
            state();
            return;
        }

        if (requestCode == IMPORT) {
            ClipData clipData = data.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    items.add(Item.uri(uri, getName(uri), "imported"));
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                items.add(Item.uri(uri, getName(uri), "imported"));
            }
            state();
            return;
        }

        if (requestCode == CAMERA) {
            String path = data.getStringExtra(CaptureActivity.EXTRA_CAPTURE_PATH);
            if (path != null) items.add(Item.file(new File(path), "camera"));
            state();
            return;
        }

        if (requestCode == SCREENSHOT) {
            String path = data.getStringExtra(ScreenshotActivity.EXTRA_CAPTURE_PATH);
            if (path != null) items.add(Item.file(new File(path), "screenshot"));
            state();
        }
    }

    private void review() {
        if (items.isEmpty()) {
            toast("Nothing staged yet.");
            return;
        }
        String[] labels = new String[items.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = (i + 1) + ". " + items.get(i).display();

        new AlertDialog.Builder(this)
                .setTitle("Staged items")
                .setMessage("Tap an item to remove it.")
                .setItems(labels, (dialog, which) -> {
                    Item item = items.remove(which);
                    if (item.file != null) item.file.delete();
                    state();
                })
                .setNeutralButton("Clear all", (dialog, which) -> {
                    for (Item item : items) if (item.file != null) item.file.delete();
                    items.clear();
                    state();
                })
                .setPositiveButton("Done", null)
                .show();
    }

    private void secure() {
        if (items.isEmpty()) {
            toast("Capture or import something first.");
            return;
        }

        Uri folderUri = folder();
        if (folderUri == null) {
            toast("Choose Relay Intake first.");
            chooseFolder();
            return;
        }

        String cat = String.valueOf(category.getSelectedItem());
        String entityType = String.valueOf(type.getSelectedItem());
        String entityValue = entity.getText().toString().trim();
        String matterValue = matter.getText().toString().trim();
        String noteValue = note.getText().toString().trim();
        String captureId = UUID.randomUUID().toString();
        long securedAt = System.currentTimeMillis();

        secure.setEnabled(false);
        setStatus("TRANSFER IN PROGRESS", BLUE);

        exec.execute(() -> {
            String failure = null;
            int saved = 0;
            try {
                DocumentFile targetFolder = DocumentFile.fromTreeUri(this, folderUri);
                if (targetFolder == null || !targetFolder.canWrite()) throw new Exception("Relay Intake is not writable.");
                int sequence = 1;
                for (Item item : new ArrayList<>(items)) {
                    save(targetFolder, item, sequence++, captureId, cat, entityType, entityValue, matterValue, noteValue, securedAt);
                    saved++;
                }
            } catch (Exception e) {
                failure = e.getMessage();
            }

            int finalSaved = saved;
            String finalFailure = failure;
            runOnUiThread(() -> {
                secure.setEnabled(true);
                if (finalFailure == null) {
                    for (Item item : items) if (item.file != null) item.file.delete();
                    resetSuccessfulSession();
                    setStatus("✓ SECURED · " + finalSaved + (finalSaved == 1 ? " item" : " items") + " saved. Ready for a new capture.", SUCCESS);
                } else {
                    setStatus("LOCAL ONLY · Transfer failed. Private local captures retained for retry.", Color.rgb(160, 69, 46));
                    toast(finalFailure);
                }
            });
        });
    }

    private void resetSuccessfulSession() {
        items.clear();
        entity.setText("");
        matter.setText("");
        note.setText("");
        type.setSelection(0);
        List<String> values = cats();
        int general = values.indexOf("General Capture");
        category.setSelection(general >= 0 ? general : 0);
        state(false);
    }

    private void save(DocumentFile folder, Item item, int sequence, String captureId, String cat,
                      String entityType, String entityValue, String matterValue, String noteValue,
                      long securedAt) throws Exception {
        ContentResolver resolver = getContentResolver();
        String mime = item.file != null
                ? (item.file.getName().endsWith(".png") ? "image/png" : "image/jpeg")
                : resolver.getType(item.uri);
        if (mime == null) mime = "application/octet-stream";

        String extension = ext(item.name, mime);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA).format(new Date());
        String anchor = !entityValue.isEmpty() ? entityValue : (!matterValue.isEmpty() ? matterValue : "Unassigned");
        String filename = safe(date + " - " + anchor + " - " + cat + " - " + sequence) + extension;

        DocumentFile target = folder.createFile(mime, filename);
        if (target == null) throw new Exception("Could not create destination file.");

        long bytes = 0;
        try (InputStream in = item.file != null ? new FileInputStream(item.file) : resolver.openInputStream(item.uri);
             OutputStream out = resolver.openOutputStream(target.getUri(), "w")) {
            if (in == null || out == null) throw new Exception("Could not open capture.");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                bytes += count;
            }
            out.flush();
        }

        if (!target.exists() || (target.length() == 0 && bytes > 0)) throw new Exception("Destination verification failed.");

        JSONObject json = new JSONObject();
        json.put("relay_schema_version", 1);
        json.put("capture_id", captureId);
        json.put("captured_at", item.created);
        json.put("secured_at", securedAt);
        json.put("source", item.source);
        json.put("category", cat);
        json.put("entity_type", entityType);
        json.put("entity", entityValue);
        json.put("matter", matterValue);
        json.put("note", noteValue);
        json.put("original_name", item.name);
        json.put("app_version", "1.0.1");
        json.put("mime_type", mime);
        json.put("file_name", filename);
        json.put("bytes_written", bytes);

        DocumentFile sidecar = folder.createFile("application/json", filename + ".json");
        if (sidecar == null) throw new Exception("Could not create provenance sidecar.");
        try (OutputStream out = resolver.openOutputStream(sidecar.getUri(), "w")) {
            if (out == null) throw new Exception("Could not open provenance sidecar.");
            out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private void addCat() {
        EditText input = new EditText(this);
        input.setHint("New category");
        new AlertDialog.Builder(this)
                .setTitle("Add category")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        Set<String> set = custom();
                        set.add(value);
                        saveCats(set);
                        refreshCats(value);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void manageCats() {
        List<String> all = cats();
        String[] values = all.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Edit categories")
                .setItems(values, (dialog, which) -> {
                    String old = values[which];
                    if (SEEDED.contains(old)) {
                        toast("Built-in categories remain available.");
                        return;
                    }
                    EditText input = new EditText(this);
                    input.setText(old);
                    new AlertDialog.Builder(this)
                            .setTitle("Edit category")
                            .setView(input)
                            .setPositiveButton("Save", (d, w) -> {
                                String next = input.getText().toString().trim();
                                Set<String> set = custom();
                                set.remove(old);
                                if (!next.isEmpty()) set.add(next);
                                saveCats(set);
                                refreshCats(next);
                            })
                            .setNegativeButton("Remove", (d, w) -> {
                                Set<String> set = custom();
                                set.remove(old);
                                saveCats(set);
                                refreshCats("General Capture");
                            })
                            .show();
                })
                .setNegativeButton("Done", null)
                .show();
    }

    private Set<String> custom() {
        return new LinkedHashSet<>(getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet(KEY_CATS, new LinkedHashSet<>()));
    }

    private void saveCats(Set<String> values) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY_CATS, values).apply();
    }

    private List<String> cats() {
        LinkedHashSet<String> values = new LinkedHashSet<>(SEEDED);
        values.addAll(custom());
        return new ArrayList<>(values);
    }

    private void refreshCats(String selected) {
        List<String> values = cats();
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        if (selected != null && values.indexOf(selected) >= 0) category.setSelection(values.indexOf(selected));
    }

    private void recoverPending() {
        File directory = new File(getFilesDir(), "relay_pending");
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) items.add(Item.file(file, file.getName().startsWith("screenshot_") ? "screenshot" : "camera/recovered"));
            }
        }
    }

    private void state() {
        state(true);
    }

    private void state(boolean updateStatus) {
        if (staged != null) {
            staged.setText(items.isEmpty()
                    ? "Nothing staged"
                    : items.size() + (items.size() == 1 ? " item staged" : " items staged"));
        }
        if (secure != null) secure.setEnabled(!items.isEmpty());
        if (updateStatus && status != null) {
            if (folder() == null) setStatus("LOCAL ONLY · Choose a Relay Intake destination before securing.", MUTED);
            else setStatus(items.isEmpty() ? "Relay Intake configured · Ready" : "Ready to secure · Local captures remain protected until handoff succeeds.", MUTED);
        }
    }

    private void setStatus(String value, int color) {
        if (status == null) return;
        status.setText(value);
        status.setTextColor(color);
    }

    private Uri folder() {
        String value = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_FOLDER, null);
        return value == null ? null : Uri.parse(value);
    }

    private List<Uri> uris(Intent intent) {
        List<Uri> output = new ArrayList<>();
        if (intent.getClipData() != null) {
            for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                Uri uri = intent.getClipData().getItemAt(i).getUri();
                if (uri != null) output.add(uri);
            }
        }
        if (output.isEmpty()) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) output.add(uri);
        }
        return output;
    }

    private String getName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
        }
        return "capture";
    }

    private String ext(String name, String mime) {
        if (name != null && name.contains(".")) {
            String extension = name.substring(name.lastIndexOf('.'));
            if (extension.length() < 8) return extension;
        }
        if ("application/pdf".equals(mime)) return ".pdf";
        if ("image/png".equals(mime)) return ".png";
        return mime.startsWith("image/") ? ".jpg" : ".bin";
    }

    private String safe(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private void toast(String value) {
        Toast.makeText(this, value == null ? "Unknown error" : value, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) exec.shutdown();
    }

    static class Item {
        Uri uri;
        File file;
        String name;
        String source;
        long created;

        static Item uri(Uri uri, String name, String source) {
            Item item = new Item();
            item.uri = uri;
            item.name = name;
            item.source = source;
            item.created = System.currentTimeMillis();
            return item;
        }

        static Item file(File file, String source) {
            Item item = new Item();
            item.file = file;
            item.name = file.getName();
            item.source = source;
            item.created = file.lastModified();
            return item;
        }

        String display() {
            return name + " · " + source;
        }
    }
}
