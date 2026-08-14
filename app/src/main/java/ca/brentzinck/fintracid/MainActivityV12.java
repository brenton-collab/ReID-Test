package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Relay Capture v1.2.
 *
 * UI/configuration refactor over the validated v1.1.1 capture + SAF transaction
 * layer. Profiles describe a deliberately finite capture grammar: workflow
 * steps with min/max counts, session-level context, per-item notes, and default
 * destinations. Relay remains a capture/preserve/emit edge, not a general
 * business-process engine.
 */
public class MainActivityV12 extends MainActivityV111 {
    static final String KEY_WORKFLOW_PROFILES_V12 = "workflow_profiles_v12";
    static final String KEY_ENTITY_TYPES_V12 = "entity_types_v12";
    static final String KEY_HISTORY_V12 = "history_v12";
    static final List<String> SEEDED_ENTITY_TYPES = Arrays.asList("None", "Person", "Matter", "Property", "Organization", "Other");

    LinearLayout wizardHost, contextStage, captureStage, reviewStage, entityBlock, matterBlock, noteBlock;
    TextView stepIndicator, profileSummary, workflowSummary, reviewSummary;
    Spinner workflowStepSpinner;
    int stage = 0;
    int workflowProfileIndex = 0;
    final IdentityHashMap<Item, String> itemSteps = new IdentityHashMap<>();
    final IdentityHashMap<Item, String> itemNotes = new IdentityHashMap<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        applyWorkflowProfile(workflowProfileIndex);
        if (!items.isEmpty()) {
            WorkflowProfile p = currentWorkflowProfile();
            String fallback = p.steps.isEmpty() ? "Capture" : p.steps.get(0).label;
            for (Item it : items) if (!itemSteps.containsKey(it)) itemSteps.put(it, fallback);
            showStage(1);
        } else showStage(0);
        updateWorkflowUi();
    }

    @Override void build() {
        ensureWorkflowProfilesV12();
        int p = dp(18);
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.rgb(245, 246, 248));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, dp(28));
        sc.addView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        Button menu = secondary("☰", v -> showMainMenu(v));
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(52), -2));
        TextView title = new TextView(this);
        title.setText("Relay Capture"); title.setTextSize(27); title.setTypeface(Typeface.DEFAULT_BOLD); title.setTextColor(Color.rgb(28,30,34)); title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, weight());
        Button gear = secondary("⚙", v -> showSettings());
        toolbar.addView(gear, new LinearLayout.LayoutParams(dp(52), -2));
        root.addView(toolbar, matchWrap());

        TextView sub = new TextView(this);
        sub.setText("Capture · Context · Preserve"); sub.setTextSize(14); sub.setTextColor(Color.rgb(95,100,108)); sub.setPadding(dp(54), 0, 0, dp(12));
        root.addView(sub);

        status = new TextView(this);
        status.setTextSize(14); status.setTextColor(Color.rgb(65,70,78)); status.setPadding(dp(12),dp(10),dp(12),dp(10)); status.setBackground(roundRect(Color.WHITE,dp(12),Color.rgb(225,228,233)));
        root.addView(status, matchWrap());

        stepIndicator = new TextView(this);
        stepIndicator.setTextSize(12); stepIndicator.setTypeface(Typeface.DEFAULT_BOLD); stepIndicator.setTextColor(Color.rgb(80,92,116)); stepIndicator.setPadding(0,dp(14),0,dp(6));
        root.addView(stepIndicator);

        wizardHost = new LinearLayout(this); wizardHost.setOrientation(LinearLayout.VERTICAL); root.addView(wizardHost, matchWrap());
        buildContextStage(); buildCaptureStage(); buildReviewStage();
        wizardHost.addView(contextStage, matchWrap()); wizardHost.addView(captureStage, matchWrap()); wizardHost.addView(reviewStage, matchWrap());
        setContentView(sc);
    }

    void buildContextStage() {
        contextStage = card();
        contextStage.addView(sectionTitle("1 · PROFILE & CONTEXT"));
        contextStage.addView(label("Capture profile"));
        profile = new Spinner(this);
        refreshWorkflowProfileSpinner();
        profile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != workflowProfileIndex) applyWorkflowProfile(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        contextStage.addView(profile);
        profileSummary = new TextView(this); profileSummary.setTextSize(13); profileSummary.setTextColor(Color.rgb(85,90,98)); profileSummary.setPadding(0,dp(6),0,dp(6)); contextStage.addView(profileSummary);

        entityBlock = new LinearLayout(this); entityBlock.setOrientation(LinearLayout.VERTICAL);
        entityBlock.addView(label("Entity type"));
        type = new Spinner(this); type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, entityTypes())); entityBlock.addView(type);
        entityBlock.addView(label("Person / property / organization")); entity = edit("Name or identifier"); entityBlock.addView(entity);
        contextStage.addView(entityBlock);

        matterBlock = new LinearLayout(this); matterBlock.setOrientation(LinearLayout.VERTICAL);
        matterBlock.addView(label("Matter / transaction / project")); matter = edit("Optional matter reference"); matterBlock.addView(matter);
        contextStage.addView(matterBlock);

        noteBlock = new LinearLayout(this); noteBlock.setOrientation(LinearLayout.VERTICAL);
        noteBlock.addView(label("Session note")); note = edit("Context that applies to the whole capture session"); note.setMinLines(2); noteBlock.addView(note);
        contextStage.addView(noteBlock);

        Button next = primary("Continue to capture", v -> { applyContextVisibility(); showStage(1); updateWorkflowUi(); });
        LinearLayout.LayoutParams np = matchWrap(); np.topMargin = dp(14); contextStage.addView(next, np);
    }

    void buildCaptureStage() {
        captureStage = card(); captureStage.addView(sectionTitle("2 · CAPTURE"));
        workflowSummary = new TextView(this); workflowSummary.setTextSize(14); workflowSummary.setTextColor(Color.rgb(65,70,78)); workflowSummary.setPadding(0,0,0,dp(8)); captureStage.addView(workflowSummary);
        captureStage.addView(label("Current workflow step"));
        workflowStepSpinner = new Spinner(this); captureStage.addView(workflowStepSpinner);

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(primary("Camera", v -> startActivityForResult(new Intent(this, CaptureActivity.class), CAMERA)), weight());
        row.addView(primary("Screenshot", v -> startActivityForResult(new Intent(this, ScreenshotActivity.class), SCREENSHOT)), weight());
        row.addView(primary("Import", v -> chooseImport()), weight());
        captureStage.addView(row);

        staged = new TextView(this); staged.setTextSize(14); staged.setPadding(0,dp(12),0,dp(4)); captureStage.addView(staged);
        captureStage.addView(secondary("Review captured items", v -> reviewWorkflowItems()), matchWrap());

        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(secondary("Back", v -> showStage(0)), weight());
        nav.addView(primary("Done capturing", v -> {
            String problem = workflowValidationProblem();
            if (problem != null) { toast(problem); return; }
            showStage(2); updateWorkflowUi();
        }), weight());
        LinearLayout.LayoutParams n = matchWrap(); n.topMargin = dp(12); captureStage.addView(nav, n);
    }

    void buildReviewStage() {
        reviewStage = card(); reviewStage.addView(sectionTitle("3 · REVIEW & SECURE"));
        reviewSummary = new TextView(this); reviewSummary.setTextSize(14); reviewSummary.setTextColor(Color.rgb(60,65,72)); reviewSummary.setPadding(0,0,0,dp(10)); reviewStage.addView(reviewSummary);
        reviewStage.addView(label("Destinations"));
        destinationSummary = new TextView(this); destinationSummary.setTextSize(14); destinationSummary.setPadding(0,dp(4),0,dp(8)); reviewStage.addView(destinationSummary);
        LinearLayout drow = new LinearLayout(this); drow.setOrientation(LinearLayout.HORIZONTAL);
        drow.addView(secondary("Select", v -> selectDestinations()), weight()); drow.addView(secondary("Manage", v -> manageDestinations()), weight()); reviewStage.addView(drow);

        secure = primary("SECURE", v -> secure()); secure.setTextSize(16);
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(secondary("Back", v -> showStage(1)), weight()); nav.addView(secure, weight());
        LinearLayout.LayoutParams n = matchWrap(); n.topMargin = dp(14); reviewStage.addView(nav, n);
    }

    void showStage(int which) {
        stage = Math.max(0, Math.min(2, which));
        if (contextStage == null) return;
        contextStage.setVisibility(stage == 0 ? View.VISIBLE : View.GONE);
        captureStage.setVisibility(stage == 1 ? View.VISIBLE : View.GONE);
        reviewStage.setVisibility(stage == 2 ? View.VISIBLE : View.GONE);
        stepIndicator.setText(stage == 0 ? "PROFILE & CONTEXT  ›  CAPTURE  ›  SECURE" : stage == 1 ? "PROFILE & CONTEXT  ›  CAPTURE  ›  SECURE" : "PROFILE & CONTEXT  ›  CAPTURE  ›  SECURE");
        updateWorkflowUi();
    }

    @Override protected void onActivityResult(int rq, int rc, Intent d) {
        int before = items.size();
        super.onActivityResult(rq, rc, d);
        if (rc != RESULT_OK) return;
        if (rq == CAMERA || rq == SCREENSHOT || rq == IMPORT) {
            WorkflowStep step = activeWorkflowStep();
            ArrayList<Item> added = new ArrayList<>();
            for (int i = before; i < items.size(); i++) {
                Item it = items.get(i); itemSteps.put(it, step.label); added.add(it);
            }
            promptRequiredItemNotes(added, step, 0);
            showStage(1); updateWorkflowUi();
        }
    }

    void promptRequiredItemNotes(List<Item> added, WorkflowStep step, int index) {
        if (!step.noteRequired || index >= added.size()) { updateWorkflowUi(); return; }
        Item it = added.get(index);
        EditText input = new EditText(this); input.setHint("Note for this " + step.label.toLowerCase(Locale.CANADA)); input.setMinLines(2);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(step.label + " note required").setView(input)
                .setPositiveButton("Save", null).setNegativeButton("Remove item", null).create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = input.getText().toString().trim(); if (value.isEmpty()) { input.setError("A note is required for this workflow step."); return; }
                itemNotes.put(it, value); dialog.dismiss(); promptRequiredItemNotes(added, step, index + 1);
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                items.remove(it); if (it.file != null) it.file.delete(); itemSteps.remove(it); itemNotes.remove(it); dialog.dismiss(); promptRequiredItemNotes(added, step, index + 1);
            });
        });
        dialog.show();
    }

    void reviewWorkflowItems() {
        if (items.isEmpty()) { toast("Nothing captured yet."); return; }
        String[] rows = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i); String step = itemSteps.getOrDefault(it, "Capture"); String n = itemNotes.get(it);
            rows[i] = (i + 1) + ". " + step + " · " + it.name + (n == null || n.isEmpty() ? "" : " · " + n);
        }
        new AlertDialog.Builder(this).setTitle("Captured items").setItems(rows, (dialog, which) -> editCapturedItem(items.get(which)))
                .setPositiveButton("Done", null).show();
    }

    void editCapturedItem(Item it) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),0,dp(18),0);
        TextView name = new TextView(this); name.setText(it.name); name.setPadding(0,0,0,dp(8)); box.addView(name);
        Spinner step = new Spinner(this); ArrayList<String> labels = workflowStepLabels(); step.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        int idx = labels.indexOf(itemSteps.getOrDefault(it, labels.isEmpty() ? "Capture" : labels.get(0))); if (idx >= 0) step.setSelection(idx); box.addView(step);
        EditText itemNote = new EditText(this); itemNote.setHint("Per-item note"); itemNote.setText(itemNotes.getOrDefault(it, "")); itemNote.setMinLines(2); box.addView(itemNote);
        new AlertDialog.Builder(this).setTitle("Captured item").setView(box)
                .setPositiveButton("Save", (d,w) -> { itemSteps.put(it, String.valueOf(step.getSelectedItem())); String n = itemNote.getText().toString().trim(); if (n.isEmpty()) itemNotes.remove(it); else itemNotes.put(it,n); updateWorkflowUi(); })
                .setNeutralButton("Remove", (d,w) -> { items.remove(it); if (it.file != null) it.file.delete(); itemSteps.remove(it); itemNotes.remove(it); updateWorkflowUi(); })
                .setNegativeButton("Cancel", null).show();
    }

    WorkflowStep activeWorkflowStep() {
        WorkflowProfile p = currentWorkflowProfile();
        if (p.steps.isEmpty()) return new WorkflowStep("Capture",1,1,false);
        int i = workflowStepSpinner == null ? 0 : workflowStepSpinner.getSelectedItemPosition();
        return p.steps.get(Math.max(0, Math.min(i, p.steps.size()-1)));
    }

    String workflowValidationProblem() {
        WorkflowProfile p = currentWorkflowProfile();
        for (WorkflowStep step : p.steps) {
            int count = countForStep(step.label);
            if (count < step.min) return step.label + " requires at least " + step.min + " capture" + (step.min == 1 ? "." : "s.");
            if (step.max > 0 && count > step.max) return step.label + " allows at most " + step.max + " capture" + (step.max == 1 ? "." : "s.");
            if (step.noteRequired) for (Item it : items) if (step.label.equals(itemSteps.get(it)) && itemNotes.getOrDefault(it, "").trim().isEmpty()) return "Every " + step.label + " requires a note.";
        }
        return items.isEmpty() ? "Capture or import something first." : null;
    }

    int countForStep(String label) { int n = 0; for (Item it : items) if (label.equals(itemSteps.get(it))) n++; return n; }

    void updateWorkflowUi() {
        if (staged != null) staged.setText(items.isEmpty() ? "Nothing captured yet" : items.size() + (items.size()==1 ? " item captured" : " items captured"));
        WorkflowProfile p = currentWorkflowProfile();
        if (profileSummary != null) profileSummary.setText(p.workflowSummary());
        if (workflowSummary != null) {
            ArrayList<String> parts = new ArrayList<>(); for (WorkflowStep s : p.steps) parts.add(s.label + " " + countForStep(s.label) + "/" + (s.max==0 ? "∞" : s.max) + (s.min>0 ? " · min " + s.min : ""));
            workflowSummary.setText(String.join("\n", parts));
        }
        if (reviewSummary != null) {
            String who = entity == null ? "" : entity.getText().toString().trim(); String mat = matter == null ? "" : matter.getText().toString().trim();
            reviewSummary.setText(p.name + " · " + items.size() + (items.size()==1 ? " item" : " items") + (who.isEmpty()?"":"\n"+who) + (mat.isEmpty()?"":"\n"+mat));
        }
        List<Destination> selected = selectedDestinations();
        if (destinationSummary != null) {
            if (destinations().isEmpty()) destinationSummary.setText("No destinations configured yet.");
            else if (selected.isEmpty()) destinationSummary.setText("No destinations selected for this capture.");
            else { ArrayList<String> labels = new ArrayList<>(); for (Destination d : selected) labels.add(d.label + (d.required ? " · required" : " · optional")); destinationSummary.setText(String.join("\n", labels)); }
        }
        if (status != null && !status.getText().toString().startsWith("TRANSFER")) status.setText(destinations().isEmpty() ? "LOCAL ONLY · Configure a durable destination before securing." : "Ready · Relay deletes local capture data only after required durable preservation verifies.");
    }

    @Override void state() { updateWorkflowUi(); if (secure != null) secure.setEnabled(!items.isEmpty()); }

    void applyWorkflowProfile(int index) {
        List<WorkflowProfile> all = workflowProfiles(); if (all.isEmpty()) return;
        workflowProfileIndex = Math.max(0, Math.min(index, all.size()-1)); WorkflowProfile p = all.get(workflowProfileIndex);
        if (profile != null && profile.getSelectedItemPosition() != workflowProfileIndex) profile.setSelection(workflowProfileIndex);
        refreshCats(p.category);
        if (type != null) {
            List<String> types = entityTypes(); type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types)); int t = types.indexOf(p.entityType); type.setSelection(t < 0 ? 0 : t);
        }
        selectedDestinationIds.clear(); for (String id : p.destinationIds) { Destination d = destinationById(id); if (d != null && d.enabled) selectedDestinationIds.add(id); }
        if (selectedDestinationIds.isEmpty()) for (Destination d : destinations()) if (d.enabled && d.required && d.durable) selectedDestinationIds.add(d.id);
        applyContextVisibility(); refreshWorkflowStepSpinner(); updateWorkflowUi();
    }

    void applyContextVisibility() {
        WorkflowProfile p = currentWorkflowProfile();
        if (entityBlock != null) entityBlock.setVisibility(p.showEntity ? View.VISIBLE : View.GONE);
        if (matterBlock != null) matterBlock.setVisibility(p.showMatter ? View.VISIBLE : View.GONE);
        if (noteBlock != null) noteBlock.setVisibility(p.showSessionNote ? View.VISIBLE : View.GONE);
    }

    void refreshWorkflowProfileSpinner() {
        List<WorkflowProfile> ps = workflowProfiles(); ArrayList<String> names = new ArrayList<>(); for (WorkflowProfile p : ps) names.add(p.name);
        profile.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names)); if (!names.isEmpty()) profile.setSelection(Math.min(workflowProfileIndex,names.size()-1));
    }

    void refreshWorkflowStepSpinner() {
        if (workflowStepSpinner == null) return; ArrayList<String> labels = workflowStepLabels(); workflowStepSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        WorkflowProfile p = currentWorkflowProfile();
        for (int i=0;i<p.steps.size();i++) { WorkflowStep s=p.steps.get(i); if (countForStep(s.label)<s.min) { workflowStepSpinner.setSelection(i); return; } }
        for (int i=0;i<p.steps.size();i++) { WorkflowStep s=p.steps.get(i); if (s.max==0 || countForStep(s.label)<s.max) { workflowStepSpinner.setSelection(i); return; } }
        if (!labels.isEmpty()) workflowStepSpinner.setSelection(labels.size()-1);
    }

    ArrayList<String> workflowStepLabels() { ArrayList<String> out = new ArrayList<>(); for (WorkflowStep s : currentWorkflowProfile().steps) out.add(s.label); if (out.isEmpty()) out.add("Capture"); return out; }
    WorkflowProfile currentWorkflowProfile() { List<WorkflowProfile> all = workflowProfiles(); if (all.isEmpty()) return WorkflowProfile.seeded("General Capture","General Capture","None",true,true,true,"single",new WorkflowStep("Capture",1,1,false)); return all.get(Math.max(0,Math.min(workflowProfileIndex,all.size()-1))); }

    @Override void secure() {
        String workflowProblem = workflowValidationProblem(); if (workflowProblem != null) { toast(workflowProblem); return; }
        List<Destination> chosen = selectedDestinations(); if (chosen.isEmpty()) { toast("Select at least one destination."); selectDestinations(); return; }
        boolean hasRequiredDurable = false; for (Destination d : chosen) if (d.durable && d.required) hasRequiredDurable = true;
        if (!hasRequiredDurable) { toast("Relay requires at least one selected required durable destination before local cleanup is permitted."); return; }

        WorkflowProfile wp = currentWorkflowProfile(); String prof = wp.name; String cat = String.valueOf(category.getSelectedItem()); String typ = String.valueOf(type.getSelectedItem());
        String ent = entity.getText().toString().trim(), mat = matter.getText().toString().trim(), nt = note.getText().toString().trim(); String cid = UUID.randomUUID().toString(); long when = System.currentTimeMillis();
        secure.setEnabled(false); status.setText("TRANSFER IN PROGRESS");

        exec.execute(() -> {
            ArrayList<String> requiredFailures = new ArrayList<>(), optionalFailures = new ArrayList<>(); int succeeded = 0;
            for (Destination dest : chosen) {
                try { saveToDriveDestination(dest,cid,prof,cat,typ,ent,mat,nt,when,chosen); succeeded++; }
                catch (Exception x) { String msg = dest.label + ": " + (x.getMessage()==null?x.getClass().getSimpleName():x.getMessage()); if (dest.required) requiredFailures.add(msg); else optionalFailures.add(msg); }
            }
            int successCount = succeeded;
            runOnUiThread(() -> {
                secure.setEnabled(true);
                if (requiredFailures.isEmpty()) {
                    for (Item it : items) if (it.file != null) it.file.delete();
                    String outcome = optionalFailures.isEmpty() ? "secured" : "secured_with_warnings";
                    recordHistory(cid, outcome, chosen, requiredFailures, optionalFailures, when);
                    showOutcomeModal(true, successCount, optionalFailures, () -> { items.clear(); resetForNewSession(); });
                } else {
                    recordHistory(cid, "failed", chosen, requiredFailures, optionalFailures, when);
                    status.setText("LOCAL ONLY · A required durable destination failed. Private local captures retained.");
                    showOutcomeModal(false, successCount, requiredFailures, () -> { showStage(2); updateWorkflowUi(); });
                }
            });
        });
    }

    @Override void saveToDriveDestination(Destination destination, String cid, String prof, String cat, String typ, String ent, String mat, String nt, long secured, List<Destination> chosen) throws Exception {
        Uri folderUri = Uri.parse(destination.value); DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri); if (folder == null || !folder.canWrite()) throw new Exception("folder is not writable");
        int seq = 1;
        for (Item it : new ArrayList<>(items)) {
            String step = itemSteps.getOrDefault(it, "Capture"); String itemNote = itemNotes.getOrDefault(it, "");
            StringBuilder merged = new StringBuilder(nt == null ? "" : nt.trim());
            if (merged.length() > 0) merged.append("\n\n"); merged.append("Workflow step: ").append(step);
            if (!itemNote.isEmpty()) merged.append("\nItem note: ").append(itemNote);
            save(folder,destination,it,seq++,cid,prof,cat,typ,ent,mat,merged.toString(),secured,chosen);
        }
    }

    void showOutcomeModal(boolean success, int destinationsSucceeded, List<String> warnings, Runnable done) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(28),dp(18),dp(28),dp(8));
        TextView icon = new TextView(this); icon.setText(success ? "✓" : "!"); icon.setTextSize(48); icon.setGravity(Gravity.CENTER); icon.setTextColor(success ? Color.rgb(37,84,170) : Color.rgb(170,72,37)); box.addView(icon);
        TextView title = new TextView(this); title.setText(success ? "Secured" : "Not fully secured"); title.setTextSize(25); title.setTypeface(Typeface.DEFAULT_BOLD); title.setGravity(Gravity.CENTER); title.setPadding(0,dp(6),0,dp(10)); box.addView(title);
        TextView details = new TextView(this); details.setTextSize(15); details.setGravity(Gravity.CENTER); details.setText(success ? destinationsSucceeded + (destinationsSucceeded==1?" destination verified":" destinations verified") + (warnings.isEmpty()?"\nLocal temporary copies removed.":"\n"+warnings.size()+" optional destination warning"+(warnings.size()==1?"":"s")+".") : "Required preservation did not complete.\nLocal captures were retained for retry."); box.addView(details);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).setPositiveButton("Done", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { dialog.dismiss(); done.run(); })); dialog.setCancelable(false); dialog.show();
    }

    @Override void resetForNewSession() {
        entity.setText(""); matter.setText(""); note.setText(""); itemSteps.clear(); itemNotes.clear(); workflowProfileIndex = 0; refreshWorkflowProfileSpinner(); applyWorkflowProfile(0); showStage(0); status.setText("Ready · Start a new capture session.");
    }

    void recordHistory(String cid, String outcome, List<Destination> chosen, List<String> requiredFailures, List<String> optionalFailures, long when) {
        try {
            JSONObject h = new JSONObject(); WorkflowProfile p = currentWorkflowProfile();
            h.put("capture_id",cid); h.put("timestamp",when); h.put("outcome",outcome); h.put("profile",p.name); h.put("category",String.valueOf(category.getSelectedItem())); h.put("entity_type",String.valueOf(type.getSelectedItem())); h.put("entity",entity.getText().toString().trim()); h.put("matter",matter.getText().toString().trim()); h.put("item_count",items.size());
            JSONArray ds = new JSONArray(); for (Destination d : chosen) ds.put(d.label); h.put("destinations",ds);
            JSONArray rf = new JSONArray(); for (String s : requiredFailures) rf.put(s); h.put("required_failures",rf); JSONArray of = new JSONArray(); for (String s : optionalFailures) of.put(s); h.put("optional_failures",of);
            JSONArray itemMeta = new JSONArray(); for (Item it : items) { JSONObject x = new JSONObject(); x.put("name",it.name); x.put("source",it.source); x.put("workflow_step",itemSteps.getOrDefault(it,"Capture")); x.put("note",itemNotes.getOrDefault(it,"")); itemMeta.put(x); } h.put("items",itemMeta);
            JSONArray history = history(); JSONArray next = new JSONArray(); next.put(h); for (int i=0;i<history.length() && i<99;i++) next.put(history.optJSONObject(i)); getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_HISTORY_V12,next.toString()).apply();
        } catch (Exception ignored) {}
    }

    JSONArray history() { try { return new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_HISTORY_V12,"[]")); } catch (Exception e) { return new JSONArray(); } }

    void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this,anchor); menu.getMenu().add("History"); menu.getMenu().add("Pending local captures");
        menu.setOnMenuItemClickListener(item -> { if (item.getTitle().toString().startsWith("History")) showHistory(); else showPending(); return true; }); menu.show();
    }

    void showHistory() {
        JSONArray h = history(); if (h.length()==0) { toast("No Relay history yet."); return; }
        int n = Math.min(50,h.length()); String[] rows = new String[n];
        for (int i=0;i<n;i++) { JSONObject x=h.optJSONObject(i); String outcome=x.optString("outcome"); rows[i]=(outcome.startsWith("secured")?"✓ ":"! ")+x.optString("profile")+" · "+x.optString("entity",x.optString("matter"))+" · "+new java.text.SimpleDateFormat("MMM d, HH:mm",Locale.CANADA).format(new Date(x.optLong("timestamp"))); }
        new AlertDialog.Builder(this).setTitle("Relay history").setItems(rows,(d,w)->showHistoryDetail(h.optJSONObject(w))).setPositiveButton("Done",null).show();
    }

    void showHistoryDetail(JSONObject x) {
        if (x==null) return; StringBuilder s=new StringBuilder(); s.append(x.optString("outcome")).append("\n\nProfile: ").append(x.optString("profile")).append("\nCategory: ").append(x.optString("category")).append("\nEntity: ").append(x.optString("entity")).append("\nMatter: ").append(x.optString("matter")).append("\nItems: ").append(x.optInt("item_count")).append("\nCapture ID: ").append(x.optString("capture_id")); JSONArray d=x.optJSONArray("destinations"); if(d!=null)s.append("\nDestinations: ").append(joinJson(d));
        new AlertDialog.Builder(this).setTitle("Capture receipt").setMessage(s.toString()).setPositiveButton("Done",null).show();
    }

    String joinJson(JSONArray a) { ArrayList<String> out=new ArrayList<>(); for(int i=0;i<a.length();i++)out.add(a.optString(i)); return String.join(", ",out); }
    void showPending() { java.io.File dir=new java.io.File(getFilesDir(),"relay_pending"); java.io.File[] f=dir.listFiles(); int n=f==null?0:f.length; new AlertDialog.Builder(this).setTitle("Pending local captures").setMessage(n==0?"None. All private capture files are clear.":n+" private local capture file"+(n==1?" is":"s are")+" awaiting a successful durable handoff.").setPositiveButton("Done",null).show(); }

    void showSettings() {
        String[] rows={"Capture Profiles","Destinations","Categories","Entity Types"}; new AlertDialog.Builder(this).setTitle("Relay settings").setItems(rows,(d,w)->{ if(w==0)manageWorkflowProfiles(); else if(w==1)manageDestinations(); else if(w==2)manageCats(); else manageEntityTypes(); }).setNegativeButton("Done",null).show();
    }

    void manageWorkflowProfiles() {
        List<WorkflowProfile> all=workflowProfiles(); ArrayList<String> rows=new ArrayList<>(); for(WorkflowProfile p:all)rows.add(p.name+" · "+p.mode); rows.add("＋ Add profile");
        new AlertDialog.Builder(this).setTitle("Capture Profiles").setItems(rows.toArray(new String[0]),(d,w)->{ if(w==all.size())editWorkflowProfile(null); else editWorkflowProfile(all.get(w)); }).setNegativeButton("Done",null).show();
    }

    void editWorkflowProfile(WorkflowProfile original) {
        WorkflowProfile draft = original==null ? WorkflowProfile.seeded("New Profile","General Capture","None",true,true,true,"single",new WorkflowStep("Capture",1,1,false)) : original.copy();
        ScrollView sc=new ScrollView(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),0,dp(18),dp(8)); sc.addView(box);
        EditText name=new EditText(this); name.setHint("Profile name"); name.setText(draft.name); box.addView(name);
        box.addView(label("Category")); Spinner cat=new Spinner(this); List<String> cats=cats(); cat.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,cats)); int ci=cats.indexOf(draft.category); if(ci>=0)cat.setSelection(ci); box.addView(cat);
        box.addView(label("Default entity type")); Spinner et=new Spinner(this); List<String> ets=entityTypes(); et.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ets)); int ei=ets.indexOf(draft.entityType); if(ei>=0)et.setSelection(ei); box.addView(et);
        CheckBox se=new CheckBox(this); se.setText("Show entity/name field"); se.setChecked(draft.showEntity); box.addView(se); CheckBox sm=new CheckBox(this); sm.setText("Show matter/project field"); sm.setChecked(draft.showMatter); box.addView(sm); CheckBox sn=new CheckBox(this); sn.setText("Show session note"); sn.setChecked(draft.showSessionNote); box.addView(sn);
        box.addView(label("Workflow shape")); Spinner mode=new Spinner(this); String[] modes={"single","sequence","repeat"}; mode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes)); int mi=Arrays.asList(modes).indexOf(draft.mode); mode.setSelection(mi<0?0:mi); box.addView(mode);
        TextView ws=new TextView(this); ws.setText(draft.workflowSummary()); ws.setPadding(0,dp(8),0,dp(8)); box.addView(ws); Button configure=secondary("Configure workflow steps",v->{ draft.mode=String.valueOf(mode.getSelectedItem()); showStepEditor(draft,ws); }); box.addView(configure);
        Button dest=secondary("Use current destination selection as defaults",v->{ draft.destinationIds=new ArrayList<>(selectedDestinationIds); toast("Current destination selection staged for this profile."); }); LinearLayout.LayoutParams dp=matchWrap(); dp.topMargin=dp(8); box.addView(dest,dp);
        new AlertDialog.Builder(this).setTitle(original==null?"Add Profile":"Edit Profile").setView(sc).setPositiveButton("Save",(d,w)->{
            draft.name=name.getText().toString().trim().isEmpty()?draft.name:name.getText().toString().trim(); draft.category=String.valueOf(cat.getSelectedItem()); draft.entityType=String.valueOf(et.getSelectedItem()); draft.showEntity=se.isChecked(); draft.showMatter=sm.isChecked(); draft.showSessionNote=sn.isChecked(); draft.mode=String.valueOf(mode.getSelectedItem()); if(draft.steps.isEmpty())draft.steps.add(new WorkflowStep("Capture",1,1,false));
            List<WorkflowProfile> all=workflowProfiles(); if(original==null)all.add(draft); else replaceWorkflowProfile(all,draft); saveWorkflowProfiles(all); refreshWorkflowProfileSpinner(); int idx=indexOfWorkflowProfile(all,draft.id); applyWorkflowProfile(idx); showStage(0);
        }).setNeutralButton(original==null?"Cancel":"Remove",(d,w)->{ if(original!=null){List<WorkflowProfile> all=workflowProfiles(); if(all.size()<=1){toast("Relay needs at least one profile.");return;} all.removeIf(x->x.id.equals(original.id)); saveWorkflowProfiles(all); workflowProfileIndex=0; refreshWorkflowProfileSpinner(); applyWorkflowProfile(0);} }).setNegativeButton("Cancel",null).show();
    }

    void showStepEditor(WorkflowProfile draft, TextView summary) {
        ArrayList<String> rows=new ArrayList<>(); for(WorkflowStep s:draft.steps)rows.add(s.describe()); rows.add("＋ Add workflow step");
        new AlertDialog.Builder(this).setTitle("Workflow steps").setMessage("Finite grammar only: named steps, minimum/maximum count, and optional per-item note requirement.").setItems(rows.toArray(new String[0]),(d,w)->{ if(w==draft.steps.size())showStepForm(draft,null,summary); else showStepForm(draft,draft.steps.get(w),summary); }).setPositiveButton("Done",null).show();
    }

    void showStepForm(WorkflowProfile draft, WorkflowStep original, TextView summary) {
        WorkflowStep temp=original==null?new WorkflowStep("Capture",1,1,false):new WorkflowStep(original.label,original.min,original.max,original.noteRequired);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),0,dp(18),0); EditText label=new EditText(this); label.setHint("Step label, e.g. Front, Back, Page, Photo"); label.setText(temp.label); box.addView(label); EditText min=new EditText(this); min.setHint("Minimum count"); min.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); min.setText(String.valueOf(temp.min)); box.addView(min); EditText max=new EditText(this); max.setHint("Maximum count (0 = unlimited)"); max.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); max.setText(String.valueOf(temp.max)); box.addView(max); CheckBox noteEach=new CheckBox(this); noteEach.setText("Require a note for every captured item in this step"); noteEach.setChecked(temp.noteRequired); box.addView(noteEach);
        new AlertDialog.Builder(this).setTitle(original==null?"Add workflow step":"Edit workflow step").setView(box).setPositiveButton("Save",(d,w)->{ temp.label=label.getText().toString().trim().isEmpty()?"Capture":label.getText().toString().trim(); try{temp.min=Integer.parseInt(min.getText().toString());}catch(Exception e){temp.min=0;} try{temp.max=Integer.parseInt(max.getText().toString());}catch(Exception e){temp.max=0;} temp.min=Math.max(0,temp.min);temp.max=Math.max(0,temp.max); if(temp.max>0&&temp.max<temp.min)temp.max=temp.min; temp.noteRequired=noteEach.isChecked(); if(original==null)draft.steps.add(temp); else {int i=draft.steps.indexOf(original);if(i>=0)draft.steps.set(i,temp);} summary.setText(draft.workflowSummary()); }).setNeutralButton(original==null?"Cancel":"Remove",(d,w)->{if(original!=null&&draft.steps.size()>1){draft.steps.remove(original);summary.setText(draft.workflowSummary());}}).setNegativeButton("Cancel",null).show();
    }

    void manageEntityTypes() {
        List<String> all=entityTypes(); ArrayList<String> rows=new ArrayList<>(all); rows.add("＋ Add entity type"); new AlertDialog.Builder(this).setTitle("Entity Types").setItems(rows.toArray(new String[0]),(d,w)->{if(w==all.size()){EditText x=new EditText(this);x.setHint("New entity type");new AlertDialog.Builder(this).setTitle("Add entity type").setView(x).setPositiveButton("Add",(dd,ww)->{String v=x.getText().toString().trim();if(!v.isEmpty()){Set<String>s=customEntityTypes();s.add(v);saveEntityTypes(s);applyWorkflowProfile(workflowProfileIndex);}}).setNegativeButton("Cancel",null).show();}else if(!SEEDED_ENTITY_TYPES.contains(all.get(w)))editCustomEntityType(all.get(w));else toast("Built-in entity types remain available.");}).setNegativeButton("Done",null).show();
    }

    void editCustomEntityType(String old) { EditText x=new EditText(this);x.setText(old);new AlertDialog.Builder(this).setTitle("Edit entity type").setView(x).setPositiveButton("Save",(d,w)->{Set<String>s=customEntityTypes();s.remove(old);String v=x.getText().toString().trim();if(!v.isEmpty())s.add(v);saveEntityTypes(s);applyWorkflowProfile(workflowProfileIndex);}).setNeutralButton("Remove",(d,w)->{Set<String>s=customEntityTypes();s.remove(old);saveEntityTypes(s);applyWorkflowProfile(workflowProfileIndex);}).setNegativeButton("Cancel",null).show(); }

    List<String> entityTypes() { LinkedHashSet<String>s=new LinkedHashSet<>(SEEDED_ENTITY_TYPES);s.addAll(customEntityTypes());return new ArrayList<>(s); }
    Set<String> customEntityTypes() { return new LinkedHashSet<>(getSharedPreferences(PREFS,MODE_PRIVATE).getStringSet(KEY_ENTITY_TYPES_V12,new LinkedHashSet<>())); }
    void saveEntityTypes(Set<String>s){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putStringSet(KEY_ENTITY_TYPES_V12,s).apply();}

    void ensureWorkflowProfilesV12() {
        if(!workflowProfiles().isEmpty())return; List<WorkflowProfile> p=new ArrayList<>();
        p.add(WorkflowProfile.seeded("General Capture","General Capture","None",true,true,true,"single",new WorkflowStep("Capture",1,1,false)));
        p.add(WorkflowProfile.seeded("FINTRAC ID","FINTRAC ID","Person",true,true,true,"sequence",new WorkflowStep("Front",1,1,false),new WorkflowStep("Back",0,1,false)));
        p.add(WorkflowProfile.seeded("Document","Client Document","Person",true,true,true,"repeat",new WorkflowStep("Page",1,0,false)));
        p.add(WorkflowProfile.seeded("Inspection","Property / Problem","Property",true,true,true,"repeat",new WorkflowStep("Photo",1,0,true)));
        p.add(WorkflowProfile.seeded("Receipt","Receipt / Expense","None",true,true,true,"single",new WorkflowStep("Receipt",1,1,false)));
        p.add(WorkflowProfile.seeded("Signed Pages","Signed Page","Person",true,true,true,"repeat",new WorkflowStep("Page",1,0,false)));
        saveWorkflowProfiles(p);
    }

    List<WorkflowProfile> workflowProfiles(){ArrayList<WorkflowProfile>out=new ArrayList<>();String raw=getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_WORKFLOW_PROFILES_V12,"");if(raw.isEmpty())return out;try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++)out.add(WorkflowProfile.fromJson(a.optJSONObject(i)));}catch(Exception ignored){}return out;}
    void saveWorkflowProfiles(List<WorkflowProfile>list){JSONArray a=new JSONArray();try{for(WorkflowProfile p:list)a.put(p.toJson());}catch(Exception ignored){}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_WORKFLOW_PROFILES_V12,a.toString()).apply();}
    void replaceWorkflowProfile(List<WorkflowProfile>all,WorkflowProfile p){for(int i=0;i<all.size();i++)if(all.get(i).id.equals(p.id)){all.set(i,p);return;}}
    int indexOfWorkflowProfile(List<WorkflowProfile>all,String id){for(int i=0;i<all.size();i++)if(all.get(i).id.equals(id))return i;return 0;}
}