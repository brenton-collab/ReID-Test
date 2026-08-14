package ca.brentzinck.fintracid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Relay Capture v1.2.x crash-safety + profile-category patch.
 *
 * v1.2 moved Category out of the main wizard and into Capture Profiles, but the
 * secure path still dereferenced the old category Spinner. Because that Spinner
 * is intentionally not created by the wizard UI, every Secure press could throw
 * synchronously before the transfer worker started. Category is now sourced
 * from the active WorkflowProfile, which is the canonical v1.2 model.
 */
public class MainActivityV121 extends MainActivityV12 {

    @Override void secure() {
        try {
            String workflowProblem = workflowValidationProblem();
            if (workflowProblem != null) { toast(workflowProblem); return; }

            List<Destination> chosen = selectedDestinations();
            if (chosen.isEmpty()) { toast("Select at least one destination."); selectDestinations(); return; }

            boolean hasRequiredDurable = false;
            for (Destination d : chosen) if (d.durable && d.required) hasRequiredDurable = true;
            if (!hasRequiredDurable) {
                toast("Relay requires at least one selected required durable destination before local cleanup is permitted.");
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

            if (secure != null) secure.setEnabled(false);
            if (status != null) status.setText("TRANSFER IN PROGRESS");

            exec.execute(() -> {
                ArrayList<String> requiredFailures = new ArrayList<>();
                ArrayList<String> optionalFailures = new ArrayList<>();
                int succeeded = 0;

                try {
                    for (Destination dest : chosen) {
                        try {
                            saveToDriveDestination(dest, cid, prof, cat, typ, ent, mat, nt, when, chosen);
                            succeeded++;
                        } catch (Throwable x) {
                            String detail = x.getMessage();
                            if (detail == null || detail.trim().isEmpty()) detail = x.getClass().getSimpleName();
                            String msg = dest.label + ": " + detail;
                            if (dest.required) requiredFailures.add(msg); else optionalFailures.add(msg);
                        }
                    }
                } catch (Throwable outer) {
                    String detail = outer.getMessage();
                    if (detail == null || detail.trim().isEmpty()) detail = outer.getClass().getSimpleName();
                    requiredFailures.add("Relay transfer: " + detail);
                }

                int successCount = succeeded;
                runOnUiThread(() -> {
                    try {
                        if (secure != null) secure.setEnabled(true);

                        if (requiredFailures.isEmpty()) {
                            for (Item it : new ArrayList<>(items)) {
                                if (it.file != null) {
                                    try { it.file.delete(); } catch (Throwable ignored) {}
                                }
                            }

                            String outcome = optionalFailures.isEmpty() ? "secured" : "secured_with_warnings";
                            try { recordHistory(cid, outcome, chosen, requiredFailures, optionalFailures, when); }
                            catch (Throwable ignored) {}

                            try {
                                showOutcomeModal(true, successCount, optionalFailures, () -> {
                                    try {
                                        items.clear();
                                        resetForNewSession();
                                    } catch (Throwable resetFailure) {
                                        items.clear();
                                        if (status != null) status.setText("✓ SECURED · Start a new capture session.");
                                    }
                                });
                            } catch (Throwable modalFailure) {
                                items.clear();
                                try { resetForNewSession(); } catch (Throwable ignored) {}
                                toast("Secured successfully. The completion receipt could not be displayed.");
                            }
                        } else {
                            try { recordHistory(cid, "failed", chosen, requiredFailures, optionalFailures, when); }
                            catch (Throwable ignored) {}

                            if (status != null) status.setText("LOCAL ONLY · Required durable destination did not complete. Private local captures retained.");

                            try {
                                showOutcomeModal(false, successCount, requiredFailures, () -> {
                                    try { showStage(2); updateWorkflowUi(); } catch (Throwable ignored) {}
                                });
                            } catch (Throwable modalFailure) {
                                toast("Not secured. Local capture retained for retry. " + joinFailures(requiredFailures));
                                try { showStage(2); updateWorkflowUi(); } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable uiFailure) {
                        if (secure != null) secure.setEnabled(true);
                        if (!requiredFailures.isEmpty()) {
                            if (status != null) status.setText("LOCAL ONLY · Transfer failed. Private local captures retained for retry.");
                            toast("Transfer failed. Local capture retained for retry.");
                        } else {
                            if (status != null) status.setText("✓ SECURED · Destination verified.");
                            toast("Secured successfully.");
                        }
                    }
                });
            });
        } catch (Throwable synchronousFailure) {
            if (secure != null) secure.setEnabled(true);
            if (status != null) status.setText("LOCAL ONLY · Relay could not start the transfer. Local captures retained.");
            String detail = synchronousFailure.getMessage();
            if (detail == null || detail.trim().isEmpty()) detail = synchronousFailure.getClass().getSimpleName();
            toast("Could not start Secure: " + detail);
        }
    }

    @Override void recordHistory(String cid, String outcome, List<Destination> chosen,
                                 List<String> requiredFailures, List<String> optionalFailures, long when) {
        try {
            WorkflowProfile p = currentWorkflowProfile();
            JSONObject h = new JSONObject();
            h.put("capture_id", cid);
            h.put("timestamp", when);
            h.put("outcome", outcome);
            h.put("profile", p.name);
            h.put("category", p.category);
            h.put("entity_type", type == null || type.getSelectedItem() == null ? p.entityType : String.valueOf(type.getSelectedItem()));
            h.put("entity", entity == null ? "" : entity.getText().toString().trim());
            h.put("matter", matter == null ? "" : matter.getText().toString().trim());
            h.put("item_count", items.size());

            JSONArray ds = new JSONArray();
            for (Destination d : chosen) ds.put(d.label);
            h.put("destinations", ds);

            JSONArray rf = new JSONArray();
            for (String s : requiredFailures) rf.put(s);
            h.put("required_failures", rf);

            JSONArray of = new JSONArray();
            for (String s : optionalFailures) of.put(s);
            h.put("optional_failures", of);

            JSONArray itemMeta = new JSONArray();
            for (Item it : items) {
                JSONObject x = new JSONObject();
                x.put("name", it.name);
                x.put("source", it.source);
                x.put("workflow_step", itemSteps.getOrDefault(it, "Capture"));
                x.put("note", itemNotes.getOrDefault(it, ""));
                itemMeta.put(x);
            }
            h.put("items", itemMeta);

            JSONArray old = history();
            JSONArray next = new JSONArray();
            next.put(h);
            for (int i = 0; i < old.length() && i < 99; i++) next.put(old.optJSONObject(i));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_HISTORY_V12, next.toString()).apply();
        } catch (Throwable ignored) {}
    }
}
