package ca.brentzinck.fintracid;

import java.util.*;

/**
 * Relay Capture v1.2.1 crash-safety patch.
 *
 * The v1.2 secure flow correctly caught ordinary destination Exceptions, but
 * a provider/runtime failure or a failure while rendering/recording the outcome
 * could still escape and terminate the Activity. This wrapper keeps the local
 * source authoritative until a required durable destination completes and
 * fences both the worker and UI completion paths.
 */
public class MainActivityV121 extends MainActivityV12 {

    @Override void secure() {
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
        String cat = String.valueOf(category.getSelectedItem());
        String typ = String.valueOf(type.getSelectedItem());
        String ent = entity.getText().toString().trim();
        String mat = matter.getText().toString().trim();
        String nt = note.getText().toString().trim();
        String cid = UUID.randomUUID().toString();
        long when = System.currentTimeMillis();

        secure.setEnabled(false);
        status.setText("TRANSFER IN PROGRESS");

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
                        // Durable preservation has completed. Deleting private source files is now permitted.
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
                            resetForNewSession();
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
                    // Last-resort containment: never let outcome rendering destroy a recoverable session.
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
    }
}
