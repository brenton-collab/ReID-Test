package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    static final int FOLDER_OLD = 1001, IMPORT = 1002, CAMERA = 1003, SCREENSHOT = 1004, ADD_DESTINATION = 1005;
    static final String PREFS = "relay_capture_prefs";
    static final String KEY_OLD_FOLDER = "folder_uri", KEY_CATS = "categories", KEY_DESTINATIONS = "destinations_json", KEY_PROFILES = "profiles_json";

    static final List<String> SEEDED_CATEGORIES = Arrays.asList(
            "FINTRAC ID", "Client Document", "Signed Page", "Property / Problem",
            "Utility / Statement", "Receipt / Expense", "General Capture"
    );
    static final List<String> TYPES = Arrays.asList("None", "Person", "Matter", "Property", "Organization", "Other");

    final List<Item> items = new ArrayList<>();
    final ExecutorService exec = Executors.newSingleThreadExecutor();
    final LinkedHashSet<String> selectedDestinationIds = new LinkedHashSet<>();

    TextView status, staged, destinationSummary;
    Spinner profile, category, type;
    EditText entity, matter, note;
    Button secure;
    boolean suppressProfileCallback = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        migrateLegacyDestination();
        ensureProfiles();
        build();
        recoverPending();
        receive(getIntent());
        applyProfileByIndex(0);
        state();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        receive(i);
    }

    void build() {
        int p = dp(18);
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.rgb(245, 246, 248));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, dp(30));
        sc.addView(root);

        TextView title = new TextView(this);
        title.setText("Relay Capture");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(28, 30, 34));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Capture · Context · Secure handoff");
        sub.setTextSize(15);
        sub.setTextColor(Color.rgb(95, 100, 108));
        sub.setPadding(0, dp(3), 0, dp(14));
        root.addView(sub);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(Color.rgb(65, 70, 78));
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setBackground(roundRect(Color.WHITE, dp(12), Color.rgb(225, 228, 233)));
        root.addView(status, matchWrap());

        LinearLayout captureCard = card();
        TextView captureLabel = sectionTitle("CAPTURE");
        captureCard.addView(captureLabel);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(primary("Camera", v -> startActivityForResult(new Intent(this, CaptureActivity.class), CAMERA)), weight());
        row.addView(primary("Screenshot", v -> startActivityForResult(new Intent(this, ScreenshotActivity.class), SCREENSHOT)), weight());
        row.addView(primary("Import", v -> chooseImport()), weight());
        captureCard.addView(row);
        staged = new TextView(this);
        staged.setTextSize(14);
        staged.setPadding(0, dp(10), 0, dp(4));
        captureCard.addView(staged);
        captureCard.addView(secondary("Review / remove staged items", v -> review()), matchWrap());
        root.addView(captureCard, cardParams());

        LinearLayout contextCard = card();
        contextCard.addView(sectionTitle("CONTEXT"));
        contextCard.addView(label("Capture profile"));
        LinearLayout pr = new LinearLayout(this);
        pr.setOrientation(LinearLayout.HORIZONTAL);
        profile = new Spinner(this);
        refreshProfilesSpinner(0);
        profile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!suppressProfileCallback) applyProfileByIndex(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        pr.addView(profile, weight());
        pr.addView(smallButton("Save", v -> saveCurrentAsProfile()), new LinearLayout.LayoutParams(dp(76), -2));
        pr.addView(smallButton("Edit", v -> manageProfiles()), new LinearLayout.LayoutParams(dp(76), -2));
        contextCard.addView(pr);

        contextCard.addView(label("Category"));
        LinearLayout cr = new LinearLayout(this);
        cr.setOrientation(LinearLayout.HORIZONTAL);
        category = new Spinner(this);
        refreshCats(null);
        cr.addView(category, weight());
        cr.addView(smallButton("+", v -> addCat()), new LinearLayout.LayoutParams(dp(56), -2));
        cr.addView(smallButton("Edit", v -> manageCats()), new LinearLayout.LayoutParams(dp(76), -2));
        contextCard.addView(cr);

        contextCard.addView(label("Entity type"));
        type = new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, TYPES));
        contextCard.addView(type);
        contextCard.addView(label("Person / property / organization"));
        entity = edit("Optional entity name or identifier"); contextCard.addView(entity);
        contextCard.addView(label("Matter / transaction / project"));
        matter = edit("Optional matter reference"); contextCard.addView(matter);
        contextCard.addView(label("Note"));
        note = edit("Optional context"); note.setMinLines(2); contextCard.addView(note);
        root.addView(contextCard, cardParams());

        LinearLayout destinationCard = card();
        destinationCard.addView(sectionTitle("DESTINATIONS"));
        destinationSummary = new TextView(this);
        destinationSummary.setTextSize(14);
        destinationSummary.setTextColor(Color.rgb(65, 70, 78));
        destinationSummary.setPadding(0, dp(4), 0, dp(8));
        destinationCard.addView(destinationSummary);
        LinearLayout destButtons = new LinearLayout(this);
        destButtons.setOrientation(LinearLayout.HORIZONTAL);
        destButtons.addView(secondary("Select", v -> selectDestinations()), weight());
        destButtons.addView(secondary("Manage", v -> manageDestinations()), weight());
        destinationCard.addView(destButtons);
        root.addView(destinationCard, cardParams());

        secure = primary("SECURE", v -> secure());
        secure.setTextSize(16);
        LinearLayout.LayoutParams secureParams = matchWrap(); secureParams.topMargin = dp(4);
        root.addView(secure, secureParams);

        setContentView(sc);
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(14));
        c.setBackground(roundRect(Color.WHITE, dp(14), Color.rgb(225, 228, 233)));
        return c;
    }
    LinearLayout.LayoutParams cardParams() { LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(12); return p; }
    TextView sectionTitle(String s) { TextView v = new TextView(this); v.setText(s); v.setTextSize(12); v.setTextColor(Color.rgb(80, 92, 116)); v.setTypeface(Typeface.DEFAULT_BOLD); v.setPadding(0, 0, 0, dp(7)); return v; }
    Button primary(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackground(roundRect(Color.rgb(37, 84, 170), dp(10), Color.rgb(37, 84, 170))); b.setOnClickListener(l); return b; }
    Button secondary(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.rgb(48, 55, 67)); b.setBackground(roundRect(Color.rgb(241, 243, 247), dp(10), Color.rgb(220, 224, 231))); b.setOnClickListener(l); return b; }
    Button smallButton(String s, View.OnClickListener l) { return secondary(s, l); }
    GradientDrawable roundRect(int fill, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(radius); g.setStroke(dp(1), stroke); return g; }
    TextView label(String s) { TextView v = new TextView(this); v.setText(s); v.setTextSize(13); v.setTextColor(Color.rgb(75, 80, 88)); v.setTypeface(Typeface.DEFAULT_BOLD); v.setPadding(0, dp(10), 0, dp(2)); return v; }
    EditText edit(String h) { EditText e = new EditText(this); e.setHint(h); e.setTextSize(15); e.setBackground(roundRect(Color.rgb(249, 250, 252), dp(8), Color.rgb(222, 226, 232))); e.setPadding(dp(10), dp(9), dp(10), dp(9)); LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(2); e.setLayoutParams(p); return e; }
    LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(2), 0, dp(2), 0); return p; }
    LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }

    void receive(Intent in) {
        if (in == null) return;
        String a = in.getAction();
        if (!Intent.ACTION_SEND.equals(a) && !Intent.ACTION_SEND_MULTIPLE.equals(a)) return;
        for (Uri u : uris(in)) items.add(Item.uri(u, getName(u), "shared"));
        in.setAction(Intent.ACTION_MAIN);
        state();
    }

    void chooseImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, IMPORT);
    }

    void addDriveDestination() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i, ADD_DESTINATION);
    }

    @Override protected void onActivityResult(int rq, int rc, Intent d) {
        super.onActivityResult(rq, rc, d);
        if (rc != RESULT_OK || d == null) return;
        if (rq == ADD_DESTINATION && d.getData() != null) {
            Uri u = d.getData();
            int flags = d.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(u, flags); }
            catch (Exception x) { toast("Could not preserve folder access."); return; }
            promptDestinationDetails(u);
            return;
        }
        if (rq == IMPORT) {
            if (d.getClipData() != null) for (int i = 0; i < d.getClipData().getItemCount(); i++) {
                Uri u = d.getClipData().getItemAt(i).getUri(); items.add(Item.uri(u, getName(u), "imported"));
            } else if (d.getData() != null) {
                Uri u = d.getData(); items.add(Item.uri(u, getName(u), "imported"));
            }
            state(); return;
        }
        if (rq == CAMERA) {
            String p = d.getStringExtra(CaptureActivity.EXTRA_CAPTURE_PATH);
            if (p != null) items.add(Item.file(new File(p), "camera"));
            state(); return;
        }
        if (rq == SCREENSHOT) {
            String p = d.getStringExtra(ScreenshotActivity.EXTRA_CAPTURE_PATH);
            if (p != null) items.add(Item.file(new File(p), "screenshot"));
            state();
        }
    }

    void promptDestinationDetails(Uri uri) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), 0, dp(18), 0);
        EditText name = new EditText(this); name.setHint("Human-readable label"); box.addView(name);
        CheckBox required = new CheckBox(this); required.setText("Required durable destination"); required.setChecked(true); box.addView(required);
        new AlertDialog.Builder(this).setTitle("Add Drive destination").setView(box)
                .setPositiveButton("Add", (dialog, which) -> {
                    String label = name.getText().toString().trim(); if (label.isEmpty()) label = "Drive Intake";
                    List<Destination> list = destinations();
                    Destination dest = new Destination(UUID.randomUUID().toString(), label, "drive", uri.toString(), true, required.isChecked(), true);
                    list.add(dest); saveDestinations(list); selectedDestinationIds.add(dest.id); state();
                }).setNegativeButton("Cancel", null).show();
    }

    void manageDestinations() {
        List<Destination> list = destinations();
        ArrayList<String> labels = new ArrayList<>();
        for (Destination d : list) labels.add(d.label + " · " + (d.required ? "required" : "optional") + (d.enabled ? "" : " · disabled"));
        labels.add("＋ Add Drive destination");
        new AlertDialog.Builder(this).setTitle("Destinations")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which == list.size()) { addDriveDestination(); return; }
                    editDestination(list.get(which));
                }).setNegativeButton("Done", null).show();
    }

    void editDestination(Destination dest) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), 0, dp(18), 0);
        EditText name = new EditText(this); name.setText(dest.label); box.addView(name);
        CheckBox enabled = new CheckBox(this); enabled.setText("Enabled"); enabled.setChecked(dest.enabled); box.addView(enabled);
        CheckBox required = new CheckBox(this); required.setText("Required durable destination"); required.setChecked(dest.required); box.addView(required);
        new AlertDialog.Builder(this).setTitle("Edit destination").setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    dest.label = name.getText().toString().trim().isEmpty() ? dest.label : name.getText().toString().trim();
                    dest.enabled = enabled.isChecked(); dest.required = required.isChecked();
                    List<Destination> all = destinations(); replaceDestination(all, dest); saveDestinations(all);
                    if (!dest.enabled) selectedDestinationIds.remove(dest.id); state();
                }).setNeutralButton("Remove", (d, w) -> confirmRemoveDestination(dest))
                .setNegativeButton("Cancel", null).show();
    }

    void confirmRemoveDestination(Destination dest) {
        new AlertDialog.Builder(this).setTitle("Remove " + dest.label + "?")
                .setMessage("This only removes Relay's destination configuration. It does not delete files already stored there.")
                .setPositiveButton("Remove", (d, w) -> {
                    List<Destination> all = destinations(); all.removeIf(x -> x.id.equals(dest.id)); saveDestinations(all); selectedDestinationIds.remove(dest.id); scrubDestinationFromProfiles(dest.id); state();
                }).setNegativeButton("Cancel", null).show();
    }

    void selectDestinations() {
        List<Destination> enabled = new ArrayList<>(); for (Destination d : destinations()) if (d.enabled) enabled.add(d);
        if (enabled.isEmpty()) { toast("Add a Drive destination first."); addDriveDestination(); return; }
        String[] names = new String[enabled.size()]; boolean[] checked = new boolean[enabled.size()];
        for (int i = 0; i < enabled.size(); i++) { Destination d = enabled.get(i); names[i] = d.label + (d.required ? " · required" : " · optional"); checked[i] = selectedDestinationIds.contains(d.id); }
        new AlertDialog.Builder(this).setTitle("Send this capture to")
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
                    Destination d = enabled.get(which); if (isChecked) selectedDestinationIds.add(d.id); else selectedDestinationIds.remove(d.id);
                }).setPositiveButton("Done", (d, w) -> state()).show();
    }

    void review() {
        if (items.isEmpty()) { toast("Nothing staged yet."); return; }
        String[] a = new String[items.size()]; for (int i = 0; i < a.length; i++) a[i] = (i + 1) + ". " + items.get(i).display();
        new AlertDialog.Builder(this).setTitle("Tap an item to remove it").setItems(a, (d, w) -> {
            Item it = items.remove(w); if (it.file != null) it.file.delete(); state();
        }).setNeutralButton("Clear all", (d, w) -> { for (Item it : items) if (it.file != null) it.file.delete(); items.clear(); state(); })
          .setPositiveButton("Done", null).show();
    }

    void secure() {
        if (items.isEmpty()) { toast("Capture or import something first."); return; }
        List<Destination> chosen = selectedDestinations();
        if (chosen.isEmpty()) { toast("Select at least one destination."); selectDestinations(); return; }
        boolean hasRequiredDurable = false; for (Destination d : chosen) if (d.durable && d.required) hasRequiredDurable = true;
        if (!hasRequiredDurable) { toast("At least one selected destination must be marked required and durable before Relay can safely clean up local captures."); return; }

        String prof = profile.getSelectedItem() == null ? "General Capture" : String.valueOf(profile.getSelectedItem());
        String cat = String.valueOf(category.getSelectedItem()), typ = String.valueOf(type.getSelectedItem());
        String ent = entity.getText().toString().trim(), mat = matter.getText().toString().trim(), nt = note.getText().toString().trim();
        String cid = UUID.randomUUID().toString(); long when = System.currentTimeMillis();
        secure.setEnabled(false); status.setText("TRANSFER IN PROGRESS");

        exec.execute(() -> {
            ArrayList<String> requiredFailures = new ArrayList<>(), optionalFailures = new ArrayList<>();
            int destinationsSucceeded = 0;
            for (Destination dest : chosen) {
                try {
                    saveToDriveDestination(dest, cid, prof, cat, typ, ent, mat, nt, when, chosen);
                    destinationsSucceeded++;
                } catch (Exception x) {
                    String msg = dest.label + ": " + (x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage());
                    if (dest.required) requiredFailures.add(msg); else optionalFailures.add(msg);
                }
            }
            int successCount = destinationsSucceeded;
            runOnUiThread(() -> {
                secure.setEnabled(true);
                if (requiredFailures.isEmpty()) {
                    for (Item it : items) if (it.file != null) it.file.delete();
                    items.clear();
                    resetForNewSession();
                    if (optionalFailures.isEmpty()) status.setText("✓ SECURED · " + successCount + (successCount == 1 ? " destination" : " destinations") + " verified. Local temporary copies removed.");
                    else status.setText("✓ SECURED · Required destination succeeded. " + optionalFailures.size() + " optional destination" + (optionalFailures.size() == 1 ? "" : "s") + " failed; local copies removed.");
                    if (!optionalFailures.isEmpty()) toast(joinFailures(optionalFailures));
                } else {
                    status.setText("LOCAL ONLY · A required destination failed. Private local captures retained for retry.");
                    toast(joinFailures(requiredFailures));
                    state();
                }
            });
        });
    }

    void saveToDriveDestination(Destination destination, String cid, String prof, String cat, String typ, String ent, String mat, String nt, long secured, List<Destination> chosen) throws Exception {
        Uri folderUri = Uri.parse(destination.value);
        DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri);
        if (folder == null || !folder.canWrite()) throw new Exception("folder is not writable");
        int seq = 1; for (Item it : new ArrayList<>(items)) save(folder, destination, it, seq++, cid, prof, cat, typ, ent, mat, nt, secured, chosen);
    }

    void save(DocumentFile folder, Destination destination, Item it, int seq, String cid, String prof, String cat, String typ, String ent, String mat, String nt, long secured, List<Destination> chosen) throws Exception {
        ContentResolver r = getContentResolver();
        String mime = it.file != null ? (it.file.getName().endsWith(".png") ? "image/png" : "image/jpeg") : r.getType(it.uri);
        if (mime == null) mime = "application/octet-stream";
        String ex = ext(it.name, mime), date = new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA).format(new Date());
        String anchor = !ent.isEmpty() ? ent : (!mat.isEmpty() ? mat : "Unassigned");
        String fn = safe(date + " - " + anchor + " - " + cat + " - " + seq) + ex;
        DocumentFile target = folder.createFile(mime, fn); if (target == null) throw new Exception("could not create destination file");
        long bytes = 0;
        try (InputStream in = it.file != null ? new FileInputStream(it.file) : r.openInputStream(it.uri); OutputStream out = r.openOutputStream(target.getUri(), "w")) {
            if (in == null || out == null) throw new Exception("could not open capture");
            byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) { out.write(b, 0, n); bytes += n; } out.flush();
        }
        if (!target.exists() || (target.length() == 0 && bytes > 0)) throw new Exception("destination verification failed");

        JSONObject j = new JSONObject();
        j.put("relay_schema_version", 2); j.put("capture_id", cid); j.put("captured_at", it.created); j.put("secured_at", secured);
        j.put("source", it.source); j.put("profile", prof); j.put("category", cat); j.put("entity_type", typ); j.put("entity", ent); j.put("matter", mat); j.put("note", nt);
        j.put("original_name", it.name); j.put("app_version", "1.1.0"); j.put("mime_type", mime); j.put("file_name", fn); j.put("bytes_written", bytes);
        j.put("destination_id", destination.id); j.put("destination_label", destination.label); j.put("destination_type", destination.type); j.put("destination_required", destination.required); j.put("destination_durable", destination.durable);
        JSONArray destinationLabels = new JSONArray(); for (Destination d : chosen) destinationLabels.put(d.label); j.put("selected_destinations", destinationLabels);

        DocumentFile side = folder.createFile("application/json", fn + ".json"); if (side == null) throw new Exception("could not create provenance sidecar");
        try (OutputStream out = r.openOutputStream(side.getUri(), "w")) { if (out == null) throw new Exception("could not open provenance sidecar"); out.write(j.toString(2).getBytes(StandardCharsets.UTF_8)); out.flush(); }
        if (!side.exists() || side.length() == 0) throw new Exception("provenance verification failed");
    }

    void resetForNewSession() {
        entity.setText(""); matter.setText(""); note.setText(""); type.setSelection(0);
        applyProfileByIndex(0);
        state();
    }

    void addCat() {
        EditText x = new EditText(this); x.setHint("New category");
        new AlertDialog.Builder(this).setTitle("Add category").setView(x).setPositiveButton("Add", (d, w) -> {
            String v = x.getText().toString().trim(); if (!v.isEmpty()) { Set<String> s = customCats(); s.add(v); saveCats(s); refreshCats(v); }
        }).setNegativeButton("Cancel", null).show();
    }

    void manageCats() {
        List<String> all = cats(); String[] a = all.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Edit or remove custom category").setItems(a, (d, w) -> {
            String old = a[w]; if (SEEDED_CATEGORIES.contains(old)) { toast("Built-in categories remain available."); return; }
            EditText x = new EditText(this); x.setText(old);
            new AlertDialog.Builder(this).setTitle("Edit category").setView(x)
                    .setPositiveButton("Save", (dd, ww) -> { String nv = x.getText().toString().trim(); Set<String> s = customCats(); s.remove(old); if (!nv.isEmpty()) s.add(nv); saveCats(s); refreshCats(nv); })
                    .setNeutralButton("Remove", (dd, ww) -> { Set<String> s = customCats(); s.remove(old); saveCats(s); refreshCats(null); })
                    .setNegativeButton("Cancel", null).show();
        }).setNegativeButton("Done", null).show();
    }

    void saveCurrentAsProfile() {
        EditText x = new EditText(this); x.setHint("Profile name");
        new AlertDialog.Builder(this).setTitle("Save capture profile").setMessage("The profile will remember the current category and selected destinations.").setView(x)
                .setPositiveButton("Save", (d, w) -> {
                    String name = x.getText().toString().trim(); if (name.isEmpty()) return;
                    Profile p = new Profile(UUID.randomUUID().toString(), name, String.valueOf(category.getSelectedItem()), new ArrayList<>(selectedDestinationIds));
                    List<Profile> all = profiles(); all.add(p); saveProfiles(all); refreshProfilesSpinner(all.size() - 1); applyProfileByIndex(all.size() - 1);
                }).setNegativeButton("Cancel", null).show();
    }

    void manageProfiles() {
        List<Profile> all = profiles(); String[] names = new String[all.size()]; for (int i = 0; i < all.size(); i++) names[i] = all.get(i).name;
        new AlertDialog.Builder(this).setTitle("Capture profiles").setItems(names, (d, which) -> editProfile(all.get(which))).setNegativeButton("Done", null).show();
    }

    void editProfile(Profile p) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18), 0, dp(18), 0);
        EditText name = new EditText(this); name.setText(p.name); box.addView(name);
        TextView hint = new TextView(this); hint.setText("Save can also replace this profile's defaults with the category and destinations currently selected on the main screen."); hint.setPadding(0, dp(8), 0, 0); box.addView(hint);
        new AlertDialog.Builder(this).setTitle("Edit profile").setView(box)
                .setPositiveButton("Save current defaults", (d, w) -> {
                    p.name = name.getText().toString().trim().isEmpty() ? p.name : name.getText().toString().trim();
                    p.category = String.valueOf(category.getSelectedItem()); p.destinationIds = new ArrayList<>(selectedDestinationIds);
                    List<Profile> all = profiles(); replaceProfile(all, p); saveProfiles(all); refreshProfilesSpinner(indexOfProfile(all, p.id));
                }).setNeutralButton("Remove", (d, w) -> {
                    List<Profile> all = profiles(); if (all.size() <= 1) { toast("Relay needs at least one profile."); return; }
                    all.removeIf(x -> x.id.equals(p.id)); saveProfiles(all); refreshProfilesSpinner(0); applyProfileByIndex(0);
                }).setNegativeButton("Cancel", null).show();
    }

    void applyProfileByIndex(int index) {
        List<Profile> all = profiles(); if (all.isEmpty()) return; if (index < 0 || index >= all.size()) index = 0;
        Profile p = all.get(index);
        if (profile != null) { suppressProfileCallback = true; profile.setSelection(index); suppressProfileCallback = false; }
        if (category != null) refreshCats(p.category);
        selectedDestinationIds.clear();
        for (String id : p.destinationIds) { Destination d = destinationById(id); if (d != null && d.enabled) selectedDestinationIds.add(id); }
        if (selectedDestinationIds.isEmpty()) for (Destination d : destinations()) if (d.enabled && d.required) selectedDestinationIds.add(d.id);
        state();
    }

    void migrateLegacyDestination() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!prefs.getString(KEY_DESTINATIONS, "").isEmpty()) return;
        String old = prefs.getString(KEY_OLD_FOLDER, null);
        if (old != null) {
            List<Destination> list = new ArrayList<>(); list.add(new Destination(UUID.randomUUID().toString(), "Relay Intake", "drive", old, true, true, true)); saveDestinations(list);
        }
    }

    void ensureProfiles() {
        if (!profiles().isEmpty()) return;
        List<Profile> p = new ArrayList<>();
        p.add(new Profile(UUID.randomUUID().toString(), "General Capture", "General Capture", new ArrayList<>()));
        p.add(new Profile(UUID.randomUUID().toString(), "FINTRAC ID", "FINTRAC ID", new ArrayList<>()));
        p.add(new Profile(UUID.randomUUID().toString(), "Receipt", "Receipt / Expense", new ArrayList<>()));
        p.add(new Profile(UUID.randomUUID().toString(), "Property Problem", "Property / Problem", new ArrayList<>()));
        p.add(new Profile(UUID.randomUUID().toString(), "Signed Page", "Signed Page", new ArrayList<>()));
        saveProfiles(p);
    }

    void recoverPending() {
        File d = new File(getFilesDir(), "relay_pending"); File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) if (f.isFile()) items.add(Item.file(f, f.getName().startsWith("screenshot_") ? "screenshot" : "camera/recovered"));
    }

    void state() {
        if (staged != null) staged.setText(items.isEmpty() ? "Nothing staged" : items.size() + (items.size() == 1 ? " item staged" : " items staged"));
        List<Destination> selected = selectedDestinations();
        if (destinationSummary != null) {
            if (destinations().isEmpty()) destinationSummary.setText("No destinations configured yet.");
            else if (selected.isEmpty()) destinationSummary.setText("No destinations selected for this capture.");
            else { ArrayList<String> labels = new ArrayList<>(); for (Destination d : selected) labels.add(d.label); destinationSummary.setText(String.join(" · ", labels)); }
        }
        if (status != null && !status.getText().toString().startsWith("✓") && !status.getText().toString().startsWith("TRANSFER") && !status.getText().toString().startsWith("LOCAL ONLY · A required")) {
            status.setText(destinations().isEmpty() ? "LOCAL ONLY · Configure a durable destination before securing." : "Ready · Local captures are retained until required durable destinations verify.");
        }
        if (secure != null) secure.setEnabled(!items.isEmpty());
    }

    List<Destination> selectedDestinations() { List<Destination> out = new ArrayList<>(); for (Destination d : destinations()) if (d.enabled && selectedDestinationIds.contains(d.id)) out.add(d); return out; }
    Destination destinationById(String id) { for (Destination d : destinations()) if (d.id.equals(id)) return d; return null; }
    void replaceDestination(List<Destination> all, Destination d) { for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(d.id)) { all.set(i, d); return; } }
    void replaceProfile(List<Profile> all, Profile p) { for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(p.id)) { all.set(i, p); return; } }
    int indexOfProfile(List<Profile> all, String id) { for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(id)) return i; return 0; }
    void scrubDestinationFromProfiles(String id) { List<Profile> ps = profiles(); for (Profile p : ps) p.destinationIds.remove(id); saveProfiles(ps); }

    List<Destination> destinations() {
        ArrayList<Destination> out = new ArrayList<>(); String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_DESTINATIONS, ""); if (raw.isEmpty()) return out;
        try { JSONArray a = new JSONArray(raw); for (int i = 0; i < a.length(); i++) out.add(Destination.fromJson(a.getJSONObject(i))); } catch (Exception ignored) {}
        return out;
    }
    void saveDestinations(List<Destination> list) { JSONArray a = new JSONArray(); try { for (Destination d : list) a.put(d.toJson()); } catch (Exception ignored) {} getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_DESTINATIONS, a.toString()).apply(); }

    List<Profile> profiles() {
        ArrayList<Profile> out = new ArrayList<>(); String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PROFILES, ""); if (raw.isEmpty()) return out;
        try { JSONArray a = new JSONArray(raw); for (int i = 0; i < a.length(); i++) out.add(Profile.fromJson(a.getJSONObject(i))); } catch (Exception ignored) {}
        return out;
    }
    void saveProfiles(List<Profile> list) { JSONArray a = new JSONArray(); try { for (Profile p : list) a.put(p.toJson()); } catch (Exception ignored) {} getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_PROFILES, a.toString()).apply(); }
    void refreshProfilesSpinner(int selected) { if (profile == null) return; List<Profile> ps = profiles(); ArrayList<String> names = new ArrayList<>(); for (Profile p : ps) names.add(p.name); suppressProfileCallback = true; profile.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names)); if (!names.isEmpty()) profile.setSelection(Math.max(0, Math.min(selected, names.size() - 1))); suppressProfileCallback = false; }

    Set<String> customCats() { return new LinkedHashSet<>(getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet(KEY_CATS, new LinkedHashSet<>())); }
    void saveCats(Set<String> s) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putStringSet(KEY_CATS, s).apply(); }
    List<String> cats() { LinkedHashSet<String> s = new LinkedHashSet<>(SEEDED_CATEGORIES); s.addAll(customCats()); return new ArrayList<>(s); }
    void refreshCats(String sel) { if (category == null) return; List<String> a = cats(); category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, a)); if (sel != null && a.indexOf(sel) >= 0) category.setSelection(a.indexOf(sel)); }

    List<Uri> uris(Intent i) { List<Uri> out = new ArrayList<>(); if (i.getClipData() != null) for (int x = 0; x < i.getClipData().getItemCount(); x++) { Uri u = i.getClipData().getItemAt(x).getUri(); if (u != null) out.add(u); } if (out.isEmpty()) { Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM); if (u != null) out.add(u); } return out; }
    String getName(Uri u) { try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) { if (c != null && c.moveToFirst()) return c.getString(0); } catch (Exception ignored) {} return "capture"; }
    String ext(String n, String m) { if (n != null && n.contains(".")) { String e = n.substring(n.lastIndexOf('.')); if (e.length() < 8) return e; } if ("application/pdf".equals(m)) return ".pdf"; if ("image/png".equals(m)) return ".png"; return m.startsWith("image/") ? ".jpg" : ".bin"; }
    String safe(String s) { return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim(); }
    String joinFailures(List<String> f) { return String.join("\n", f); }
    void toast(String s) { Toast.makeText(this, s == null ? "Unknown error" : s, Toast.LENGTH_LONG).show(); }
    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { super.onDestroy(); if (isFinishing()) exec.shutdown(); }

    static class Item {
        Uri uri; File file; String name, source; long created;
        static Item uri(Uri u, String n, String s) { Item i = new Item(); i.uri = u; i.name = n; i.source = s; i.created = System.currentTimeMillis(); return i; }
        static Item file(File f, String s) { Item i = new Item(); i.file = f; i.name = f.getName(); i.source = s; i.created = f.lastModified(); return i; }
        String display() { return name + " · " + source; }
    }

    static class Destination {
        String id, label, type, value; boolean durable, required, enabled;
        Destination(String id, String label, String type, String value, boolean durable, boolean required, boolean enabled) { this.id = id; this.label = label; this.type = type; this.value = value; this.durable = durable; this.required = required; this.enabled = enabled; }
        JSONObject toJson() throws Exception { JSONObject j = new JSONObject(); j.put("id", id); j.put("label", label); j.put("type", type); j.put("value", value); j.put("durable", durable); j.put("required", required); j.put("enabled", enabled); return j; }
        static Destination fromJson(JSONObject j) { return new Destination(j.optString("id"), j.optString("label"), j.optString("type", "drive"), j.optString("value"), j.optBoolean("durable", true), j.optBoolean("required", true), j.optBoolean("enabled", true)); }
    }

    static class Profile {
        String id, name, category; ArrayList<String> destinationIds;
        Profile(String id, String name, String category, ArrayList<String> destinationIds) { this.id = id; this.name = name; this.category = category; this.destinationIds = destinationIds; }
        JSONObject toJson() throws Exception { JSONObject j = new JSONObject(); j.put("id", id); j.put("name", name); j.put("category", category); JSONArray a = new JSONArray(); for (String d : destinationIds) a.put(d); j.put("destination_ids", a); return j; }
        static Profile fromJson(JSONObject j) { ArrayList<String> ids = new ArrayList<>(); JSONArray a = j.optJSONArray("destination_ids"); if (a != null) for (int i = 0; i < a.length(); i++) ids.add(a.optString(i)); return new Profile(j.optString("id"), j.optString("name"), j.optString("category", "General Capture"), ids); }
    }
}
