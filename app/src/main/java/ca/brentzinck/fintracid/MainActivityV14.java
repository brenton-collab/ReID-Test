package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Relay Capture v1.4 Signal Destinations.
 *
 * Preservation invariant:
 *   Relay may emit anywhere. Relay may delete locally only after preservation.
 *
 * Drive remains the only durable destination type in v1.4. HTTP/GAS endpoints
 * receive JSON signals only; endpoint failure never invalidates a verified Drive
 * handoff and never requires retained copies of the captured artifact.
 */
public class MainActivityV14 extends MainActivityV13 {
    static final String KEY_SIGNAL_QUEUE_V14 = "signal_queue_v14";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        retryPendingSignals(false);
    }

    @Override void manageDestinations() {
        LinearLayout body = productRoot("Destinations", "Durable preservation and optional signal emission");
        for (Destination d : destinations()) {
            boolean signal = "http".equalsIgnoreCase(d.type);
            String detail = signal
                    ? "Signal · " + (d.enabled ? "Enabled" : "Disabled")
                    : (d.required ? "Required" : "Optional") + " · Durable" + (d.enabled ? "" : " · Disabled");
            body.addView(navRow(d.label, detail, v -> editDestination(d)), spaced());
        }

        Button drive = primary("＋  Add Drive folder", v -> addDriveDestination());
        body.addView(drive, spaced());
        Button endpoint = secondary("＋  Add web endpoint", v -> editEndpoint(null));
        body.addView(endpoint, spaced());

        int pending = pendingSignalCount();
        if (pending > 0) {
            body.addView(groupLabel("Signal queue"));
            body.addView(navRow("Pending signals", pending + " awaiting retry", v -> showPendingSignals()), spaced());
        }
    }

    @Override void editDestination(Destination dest) {
        if (dest != null && "http".equalsIgnoreCase(dest.type)) {
            editEndpoint(dest);
            return;
        }
        super.editDestination(dest);
    }

    void editEndpoint(Destination existing) {
        final Destination draft = existing == null
                ? new Destination(UUID.randomUUID().toString(), "Signal Bus", "http", "", false, false, true)
                : new Destination(existing.id, existing.label, "http", existing.value, false, false, existing.enabled);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), 0, dp(18), 0);

        EditText label = edit("Human-readable label");
        label.setText(draft.label);
        box.addView(label);
        EditText url = edit("https://… endpoint URL");
        url.setText(draft.value);
        LinearLayout.LayoutParams up = matchWrap(); up.topMargin = dp(8); box.addView(url, up);
        CheckBox enabled = new CheckBox(this);
        enabled.setText("Enabled");
        enabled.setChecked(draft.enabled);
        box.addView(enabled);

        TextView note = new TextView(this);
        note.setText("Web endpoints are Signal destinations. They cannot authorize cleanup of local captures.");
        note.setTextSize(12);
        note.setTextColor(Color.rgb(100,106,116));
        note.setPadding(0, dp(8), 0, dp(8));
        box.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add web endpoint" : "Edit web endpoint")
                .setView(box)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton(existing == null ? "Test" : "Remove", null)
                .create();

        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String endpointUrl = url.getText().toString().trim();
                if (!validEndpoint(endpointUrl)) {
                    url.setError("Use a valid https:// endpoint URL.");
                    return;
                }
                draft.label = label.getText().toString().trim().isEmpty() ? "Web Endpoint" : label.getText().toString().trim();
                draft.value = endpointUrl;
                draft.enabled = enabled.isChecked();
                draft.durable = false;
                draft.required = false;
                List<Destination> all = destinations();
                if (existing == null) all.add(draft); else replaceDestination(all, draft);
                saveDestinations(all);
                if (draft.enabled) selectedDestinationIds.add(draft.id); else selectedDestinationIds.remove(draft.id);
                dialog.dismiss();
                manageDestinations();
            });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (existing != null) {
                    new AlertDialog.Builder(this)
                            .setTitle("Remove " + existing.label + "?")
                            .setMessage("Queued signals for this destination will remain visible but cannot be retried unless the destination is recreated.")
                            .setPositiveButton("Remove", (d,w) -> {
                                List<Destination> all = destinations();
                                all.removeIf(z -> z.id.equals(existing.id));
                                saveDestinations(all);
                                selectedDestinationIds.remove(existing.id);
                                scrubDestinationFromProfiles(existing.id);
                                dialog.dismiss();
                                manageDestinations();
                            }).setNegativeButton("Cancel", null).show();
                } else {
                    testEndpoint(url.getText().toString().trim(), dialog.getButton(AlertDialog.BUTTON_NEUTRAL));
                }
            });

            if (existing != null) {
                Button test = secondary("Test endpoint", v -> testEndpoint(url.getText().toString().trim(), null));
                box.addView(test, spaced());
            }
        });
        dialog.show();
    }

    boolean validEndpoint(String value) {
        if (value == null) return false;
        String x = value.trim().toLowerCase(Locale.CANADA);
        return x.startsWith("https://") && x.length() > 12;
    }

    void testEndpoint(String endpoint, Button button) {
        if (!validEndpoint(endpoint)) {
            toast("Enter a valid https:// endpoint first.");
            return;
        }
        if (button != null) button.setEnabled(false);
        exec.execute(() -> {
            String result;
            try {
                JSONObject payload = new JSONObject();
                payload.put("relay_schema_version", 3);
                payload.put("event", "relay.endpoint.test");
                payload.put("test", true);
                payload.put("timestamp", System.currentTimeMillis());
                HttpResult r = postJson(endpoint, payload);
                result = r.success ? "Endpoint responded " + r.code + "." : "Endpoint test failed: HTTP " + r.code + (r.message.isEmpty() ? "" : " · " + r.message);
            } catch (Exception e) {
                result = "Endpoint test failed: " + errorText(e);
            }
            String message = result;
            runOnUiThread(() -> {
                if (button != null) button.setEnabled(true);
                new AlertDialog.Builder(this).setTitle("Endpoint test").setMessage(message).setPositiveButton("Done", null).show();
            });
        });
    }

    @Override void secure() {
        try {
            String workflowProblem = workflowValidationProblem();
            if (workflowProblem != null) { toast(workflowProblem); return; }

            List<Destination> chosen = selectedDestinations();
            if (chosen.isEmpty()) { toast("Select at least one destination."); selectDestinations(); return; }

            ArrayList<Destination> durable = new ArrayList<>();
            ArrayList<Destination> signals = new ArrayList<>();
            boolean hasRequiredDurable = false;
            for (Destination d : chosen) {
                if ("http".equalsIgnoreCase(d.type)) signals.add(d);
                else {
                    durable.add(d);
                    if (d.durable && d.required) hasRequiredDurable = true;
                }
            }
            if (!hasRequiredDurable) {
                toast("Relay requires at least one selected required durable Drive destination before Secure can run.");
                return;
            }

            WorkflowProfile wp = currentWorkflowProfile();
            String prof = wp.name;
            String cat = wp.category == null || wp.category.trim().isEmpty() ? "General Capture" : wp.category;
            String typ = type == null || type.getSelectedItem() == null ? wp.entityType : String.valueOf(type.getSelectedItem());
            String ent = entity == null ? "" : entity.getText().toString().trim();
            String mat = matter == null ? "" : matter.getText().toString().trim();
            String nt = note == null ? "" : note.getText().toString().trim();
            String cid = UUID.randomUUID().toString();
            long when = System.currentTimeMillis();
            JSONObject payload = buildSignalPayload(cid, when, wp, typ, ent, mat, nt, chosen);

            if (secure != null) secure.setEnabled(false);
            if (status != null) { status.setText("TRANSFER IN PROGRESS"); status.setVisibility(View.VISIBLE); }

            exec.execute(() -> {
                ArrayList<String> requiredFailures = new ArrayList<>();
                ArrayList<String> optionalFailures = new ArrayList<>();
                JSONArray destinationResults = new JSONArray();
                int durableSucceeded = 0;

                for (Destination dest : durable) {
                    JSONObject dr = destinationResult(dest);
                    try {
                        saveToDriveDestination(dest, cid, prof, cat, typ, ent, mat, nt, when, chosen);
                        durableSucceeded++;
                        dr.put("status", "verified");
                    } catch (Throwable e) {
                        String message = errorText(e);
                        try {
                            dr.put("status", "failed");
                            dr.put("error", message);
                        } catch (Exception ignored) {}
                        String failure = dest.label + ": " + message;
                        if (dest.required) requiredFailures.add(failure); else optionalFailures.add(failure);
                    }
                    destinationResults.put(dr);
                }

                boolean preservationVerified = requiredFailures.isEmpty();
                ArrayList<String> signalWarnings = new ArrayList<>();
                int signalsSucceeded = 0;

                if (preservationVerified) {
                    try { payload.put("durable_results", destinationResults); } catch (Exception ignored) {}
                    for (Destination dest : signals) {
                        JSONObject sr = destinationResult(dest);
                        try {
                            HttpResult r = postJson(dest.value, payload);
                            if (!r.success) throw new IOException("HTTP " + r.code + (r.message.isEmpty() ? "" : " · " + r.message));
                            signalsSucceeded++;
                            sr.put("status", "sent");
                            sr.put("http_status", r.code);
                        } catch (Throwable e) {
                            String message = errorText(e);
                            try {
                                sr.put("status", "pending");
                                sr.put("error", message);
                            } catch (Exception ignored) {}
                            queueSignal(dest, payload, cid, message);
                            signalWarnings.add(dest.label + ": queued for retry");
                        }
                        destinationResults.put(sr);
                    }
                } else {
                    for (Destination dest : signals) {
                        JSONObject sr = destinationResult(dest);
                        try {
                            sr.put("status", "not_sent");
                            sr.put("error", "Preservation was not verified.");
                        } catch (Exception ignored) {}
                        destinationResults.put(sr);
                    }
                }

                int durableOk = durableSucceeded;
                int signalOk = signalsSucceeded;
                runOnUiThread(() -> {
                    try {
                        if (secure != null) secure.setEnabled(true);
                        if (preservationVerified) {
                            for (Item it : new ArrayList<>(items)) if (it.file != null) try { it.file.delete(); } catch (Throwable ignored) {}

                            String outcome = (optionalFailures.isEmpty() && signalWarnings.isEmpty()) ? "secured" : "secured_with_warnings";
                            recordHistoryV14(cid, outcome, chosen, requiredFailures, optionalFailures, signalWarnings, destinationResults, when);

                            ArrayList<String> warnings = new ArrayList<>(optionalFailures);
                            warnings.addAll(signalWarnings);
                            showOutcomeModal(true, durableOk, warnings, () -> {
                                items.clear();
                                resetForNewSession();
                            });
                        } else {
                            recordHistoryV14(cid, "failed", chosen, requiredFailures, optionalFailures, signalWarnings, destinationResults, when);
                            if (status != null) status.setText("LOCAL ONLY · Required durable destination did not complete. Local captures retained.");
                            showOutcomeModal(false, durableOk, requiredFailures, () -> { showStage(2); updateWorkflowUi(); });
                        }
                    } catch (Throwable ui) {
                        if (secure != null) secure.setEnabled(true);
                        toast(preservationVerified ? "Preserved successfully." + (signalOk < signals.size() ? " A signal is pending retry." : "") : "Not secured. Local capture retained.");
                    }
                });
            });
        } catch (Throwable e) {
            if (secure != null) secure.setEnabled(true);
            toast("Could not start Secure: " + errorText(e));
        }
    }

    JSONObject buildSignalPayload(String cid, long when, WorkflowProfile wp, String typ, String ent, String mat, String nt, List<Destination> chosen) {
        JSONObject p = new JSONObject();
        try {
            p.put("relay_schema_version", 3);
            p.put("event", "relay.capture.secured");
            p.put("capture_id", cid);
            p.put("timestamp", when);
            p.put("profile", wp.name);
            p.put("category", wp.category);
            p.put("entity_type", typ);
            p.put("entity", ent);
            p.put("matter", mat);
            p.put("note", nt);

            JSONArray ds = new JSONArray();
            for (Destination d : chosen) {
                JSONObject x = new JSONObject();
                x.put("id", d.id); x.put("label", d.label); x.put("type", d.type); x.put("durable", d.durable); x.put("required", d.required);
                ds.put(x);
            }
            p.put("selected_destinations", ds);

            JSONArray a = new JSONArray();
            for (Item it : items) {
                JSONObject x = new JSONObject();
                x.put("original_name", it.name);
                x.put("source", it.source);
                x.put("captured_at", it.created);
                x.put("workflow_step", itemSteps.getOrDefault(it, "Capture"));
                x.put("item_note", itemNotes.getOrDefault(it, ""));
                x.put("mime_type", itemMime(it));
                a.put(x);
            }
            p.put("items", a);
        } catch (Exception ignored) {}
        return p;
    }

    String itemMime(Item it) {
        if (it.uri != null) {
            String m = getContentResolver().getType(it.uri);
            if (m != null) return m;
        }
        String n = it.name == null ? "" : it.name.toLowerCase(Locale.CANADA);
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    JSONObject destinationResult(Destination d) {
        JSONObject x = new JSONObject();
        try {
            x.put("destination_id", d.id);
            x.put("label", d.label);
            x.put("type", d.type);
            x.put("durable", d.durable);
            x.put("required", d.required);
        } catch (Exception ignored) {}
        return x;
    }

    HttpResult postJson(String endpoint, JSONObject payload) throws Exception {
        if (!validEndpoint(endpoint)) throw new IOException("Invalid HTTPS endpoint");
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        c.setRequestProperty("User-Agent", "Relay-Capture/1.4.0");
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = c.getOutputStream()) { out.write(bytes); out.flush(); }
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        String body = readSmall(stream);
        boolean success = code >= 200 && code < 300;
        if (success && body != null && body.trim().startsWith("{")) {
            try {
                JSONObject response = new JSONObject(body);
                if (response.has("ok") && !response.optBoolean("ok", false)) success = false;
            } catch (Exception ignored) {}
        }
        c.disconnect();
        return new HttpResult(code, success, body == null ? "" : body.trim());
    }

    String readSmall(InputStream in) {
        if (in == null) return "";
        try (InputStream x = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[2048]; int n; int total = 0;
            while ((n = x.read(b)) != -1 && total < 8192) { int use = Math.min(n, 8192-total); out.write(b,0,use); total += use; }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) { return ""; }
    }

    void queueSignal(Destination dest, JSONObject payload, String cid, String error) {
        try {
            JSONArray q = signalQueue();
            JSONObject x = new JSONObject();
            x.put("queue_id", UUID.randomUUID().toString());
            x.put("capture_id", cid);
            x.put("destination_id", dest.id);
            x.put("destination_label", dest.label);
            x.put("endpoint", dest.value);
            x.put("payload", new JSONObject(payload.toString()));
            x.put("created_at", System.currentTimeMillis());
            x.put("attempts", 0);
            x.put("last_error", error);
            q.put(x);
            saveSignalQueue(q);
        } catch (Exception ignored) {}
    }

    JSONArray signalQueue() {
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SIGNAL_QUEUE_V14, "[]");
        try { return new JSONArray(raw); } catch (Exception e) { return new JSONArray(); }
    }

    void saveSignalQueue(JSONArray q) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SIGNAL_QUEUE_V14, q.toString()).apply();
    }

    int pendingSignalCount() { return signalQueue().length(); }

    void retryPendingSignals(boolean userInitiated) {
        if (pendingSignalCount() == 0) {
            if (userInitiated) toast("No pending signals.");
            return;
        }
        exec.execute(() -> {
            JSONArray old = signalQueue();
            JSONArray keep = new JSONArray();
            int sent = 0;
            for (int i=0;i<old.length();i++) {
                JSONObject x = old.optJSONObject(i); if (x == null) continue;
                try {
                    HttpResult r = postJson(x.optString("endpoint"), x.optJSONObject("payload"));
                    if (!r.success) throw new IOException("HTTP " + r.code);
                    sent++;
                } catch (Throwable e) {
                    try {
                        x.put("attempts", x.optInt("attempts",0)+1);
                        x.put("last_attempt", System.currentTimeMillis());
                        x.put("last_error", errorText(e));
                    } catch (Exception ignored) {}
                    keep.put(x);
                }
            }
            saveSignalQueue(keep);
            int sentCount = sent;
            runOnUiThread(() -> {
                if (userInitiated) toast(sentCount + " signal" + (sentCount==1?"":"s") + " sent · " + pendingSignalCount() + " pending.");
            });
        });
    }

    void showPendingSignals() {
        LinearLayout body = productRoot("Pending Signals", "Metadata-only retry queue");
        JSONArray q = signalQueue();
        if (q.length() == 0) {
            TextView empty = new TextView(this); empty.setText("No pending signals."); empty.setTextSize(15); empty.setPadding(0,dp(40),0,0); body.addView(empty); return;
        }
        for (int i=0;i<q.length();i++) {
            JSONObject x=q.optJSONObject(i); if(x==null)continue;
            String detail=x.optString("capture_id")+" · "+x.optInt("attempts")+" retries";
            body.addView(navRow(x.optString("destination_label","Endpoint"),detail,v->showQueuedSignal(x)),spaced());
        }
        Button retry=primary("Retry all",v->retryPendingSignals(true)); body.addView(retry,spaced());
    }

    void showQueuedSignal(JSONObject x) {
        String message="Capture: "+x.optString("capture_id")+"\nDestination: "+x.optString("destination_label")+"\nAttempts: "+x.optInt("attempts")+"\nLast error: "+x.optString("last_error");
        new AlertDialog.Builder(this).setTitle("Pending signal").setMessage(message).setPositiveButton("Retry all",(d,w)->retryPendingSignals(true)).setNegativeButton("Done",null).show();
    }

    void recordHistoryV14(String cid, String outcome, List<Destination> chosen,
                          List<String> requiredFailures, List<String> optionalFailures,
                          List<String> signalWarnings, JSONArray destinationResults, long when) {
        try {
            WorkflowProfile p = currentWorkflowProfile();
            JSONObject h = new JSONObject();
            h.put("capture_id", cid); h.put("timestamp", when); h.put("outcome", outcome);
            h.put("profile", p.name); h.put("category", p.category);
            h.put("entity_type", type == null || type.getSelectedItem() == null ? p.entityType : String.valueOf(type.getSelectedItem()));
            h.put("entity", entity == null ? "" : entity.getText().toString().trim());
            h.put("matter", matter == null ? "" : matter.getText().toString().trim());
            h.put("item_count", items.size());
            h.put("destination_results", destinationResults);
            JSONArray sw = new JSONArray(); for(String s:signalWarnings)sw.put(s); h.put("signal_warnings",sw);
            JSONArray rf = new JSONArray(); for(String s:requiredFailures)rf.put(s); h.put("required_failures",rf);
            JSONArray of = new JSONArray(); for(String s:optionalFailures)of.put(s); h.put("optional_failures",of);
            JSONArray ds = new JSONArray(); for(Destination d:chosen)ds.put(d.label); h.put("destinations",ds);

            JSONArray itemMeta=new JSONArray();
            for(Item it:items){JSONObject z=new JSONObject();z.put("name",it.name);z.put("source",it.source);z.put("workflow_step",itemSteps.getOrDefault(it,"Capture"));z.put("note",itemNotes.getOrDefault(it,""));itemMeta.put(z);} h.put("items",itemMeta);

            JSONArray old=history(); JSONArray next=new JSONArray(); next.put(h); for(int i=0;i<old.length()&&i<99;i++)next.put(old.optJSONObject(i));
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_HISTORY_V12,next.toString()).apply();
        } catch(Exception ignored){}
    }

    @Override void showHistoryReceipt(JSONObject x) {
        LinearLayout body=productRoot("Capture Receipt",x.optString("outcome").startsWith("secured")?"Preservation verified":"Preservation incomplete");
        addReceipt(body,"Profile",x.optString("profile")); addReceipt(body,"Category",x.optString("category")); addReceipt(body,"Entity",x.optString("entity")); addReceipt(body,"Matter",x.optString("matter")); addReceipt(body,"Items",String.valueOf(x.optInt("item_count"))); addReceipt(body,"Capture ID",x.optString("capture_id"));
        JSONArray results=x.optJSONArray("destination_results");
        if(results!=null){
            body.addView(groupLabel("Destination outcomes"));
            for(int i=0;i<results.length();i++){
                JSONObject r=results.optJSONObject(i);if(r==null)continue;
                String status=r.optString("status","unknown").replace('_',' ');
                String detail=(r.optBoolean("durable")?"Durable":"Signal")+" · "+status;
                if(r.has("http_status"))detail+=" · HTTP "+r.optInt("http_status");
                body.addView(navRow(r.optString("label","Destination"),detail,v->{}),spaced());
            }
        } else {
            JSONArray d=x.optJSONArray("destinations");if(d!=null)addReceipt(body,"Destinations",joinJson(d));
        }
        if(pendingForCapture(x.optString("capture_id"))>0){Button retry=primary("Retry pending signal",v->retryPendingSignals(true));body.addView(retry,spaced());}
    }

    int pendingForCapture(String cid){int n=0;JSONArray q=signalQueue();for(int i=0;i<q.length();i++){JSONObject x=q.optJSONObject(i);if(x!=null&&cid.equals(x.optString("capture_id")))n++;}return n;}

    String errorText(Throwable e) {
        if (e == null) return "Unknown error";
        String m = e.getMessage();
        return m == null || m.trim().isEmpty() ? e.getClass().getSimpleName() : m.trim();
    }

    static class HttpResult {
        final int code; final boolean success; final String message;
        HttpResult(int code, boolean success, String message) { this.code=code; this.success=success; this.message=message==null?"":message; }
    }
}
