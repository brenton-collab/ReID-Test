package ca.brentzinck.fintracid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Finite, authorable capture-session grammar for Relay. */
class WorkflowProfile {
    String id;
    String name;
    String category;
    String entityType;
    boolean showEntity;
    boolean showMatter;
    boolean showSessionNote;
    String mode; // single | sequence | repeat
    ArrayList<WorkflowStep> steps;
    ArrayList<String> destinationIds;

    WorkflowProfile(String id, String name, String category, String entityType,
                    boolean showEntity, boolean showMatter, boolean showSessionNote,
                    String mode, ArrayList<WorkflowStep> steps, ArrayList<String> destinationIds) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.entityType = entityType;
        this.showEntity = showEntity;
        this.showMatter = showMatter;
        this.showSessionNote = showSessionNote;
        this.mode = mode;
        this.steps = steps;
        this.destinationIds = destinationIds;
    }

    static WorkflowProfile seeded(String name, String category, String entityType,
                                  boolean showEntity, boolean showMatter, boolean showSessionNote,
                                  String mode, WorkflowStep... workflowSteps) {
        ArrayList<WorkflowStep> steps = new ArrayList<>();
        for (WorkflowStep step : workflowSteps) steps.add(step);
        return new WorkflowProfile(UUID.randomUUID().toString(), name, category, entityType,
                showEntity, showMatter, showSessionNote, mode, steps, new ArrayList<>());
    }

    JSONObject toJson() throws Exception {
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("name", name);
        j.put("category", category);
        j.put("entity_type", entityType);
        j.put("show_entity", showEntity);
        j.put("show_matter", showMatter);
        j.put("show_session_note", showSessionNote);
        j.put("mode", mode);
        JSONArray s = new JSONArray();
        for (WorkflowStep step : steps) s.put(step.toJson());
        j.put("steps", s);
        JSONArray d = new JSONArray();
        for (String destinationId : destinationIds) d.put(destinationId);
        j.put("destination_ids", d);
        return j;
    }

    static WorkflowProfile fromJson(JSONObject j) {
        ArrayList<WorkflowStep> steps = new ArrayList<>();
        JSONArray s = j.optJSONArray("steps");
        if (s != null) for (int i = 0; i < s.length(); i++) steps.add(WorkflowStep.fromJson(s.optJSONObject(i)));
        if (steps.isEmpty()) steps.add(new WorkflowStep("Capture", 1, 1, false));

        ArrayList<String> destinations = new ArrayList<>();
        JSONArray d = j.optJSONArray("destination_ids");
        if (d != null) for (int i = 0; i < d.length(); i++) destinations.add(d.optString(i));

        return new WorkflowProfile(
                j.optString("id", UUID.randomUUID().toString()),
                j.optString("name", "Capture"),
                j.optString("category", "General Capture"),
                j.optString("entity_type", "None"),
                j.optBoolean("show_entity", true),
                j.optBoolean("show_matter", true),
                j.optBoolean("show_session_note", true),
                j.optString("mode", "single"),
                steps,
                destinations
        );
    }

    String workflowSummary() {
        ArrayList<String> parts = new ArrayList<>();
        for (WorkflowStep step : steps) {
            String count = step.max == 0 ? (step.min + "+") : (step.min == step.max ? String.valueOf(step.min) : step.min + "–" + step.max);
            parts.add(step.label + " " + count + (step.noteRequired ? " · note" : ""));
        }
        return String.join("  •  ", parts);
    }

    WorkflowProfile copy() {
        ArrayList<WorkflowStep> cloned = new ArrayList<>();
        for (WorkflowStep s : steps) cloned.add(new WorkflowStep(s.label, s.min, s.max, s.noteRequired));
        return new WorkflowProfile(id, name, category, entityType, showEntity, showMatter, showSessionNote,
                mode, cloned, new ArrayList<>(destinationIds));
    }
}

class WorkflowStep {
    String label;
    int min;
    int max; // 0 means unlimited
    boolean noteRequired;

    WorkflowStep(String label, int min, int max, boolean noteRequired) {
        this.label = label;
        this.min = Math.max(0, min);
        this.max = Math.max(0, max);
        this.noteRequired = noteRequired;
    }

    JSONObject toJson() throws Exception {
        JSONObject j = new JSONObject();
        j.put("label", label);
        j.put("min", min);
        j.put("max", max);
        j.put("note_required", noteRequired);
        return j;
    }

    static WorkflowStep fromJson(JSONObject j) {
        if (j == null) return new WorkflowStep("Capture", 1, 1, false);
        return new WorkflowStep(j.optString("label", "Capture"), j.optInt("min", 1), j.optInt("max", 1), j.optBoolean("note_required", false));
    }

    String describe() {
        String count = max == 0 ? min + "+" : (min == max ? String.valueOf(min) : min + "–" + max);
        return label + " · " + count + (min > 0 ? " required minimum" : " optional") + (noteRequired ? " · note each" : "");
    }
}