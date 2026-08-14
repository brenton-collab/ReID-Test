package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import org.json.*;
import java.util.*;

/** Relay Capture v1.4.2: first-class workflow authoring. */
public class MainActivityV142 extends MainActivityV141 {
    static final String KEY_STEP_META_V142 = "workflow_step_meta_v142";
    TextView activeInstruction;

    @Override void buildCaptureStage() {
        super.buildCaptureStage();
        activeInstruction = helper("Choose a workflow step, then capture or import its artifacts.");
        captureStage.addView(activeInstruction, 1, spaced());
    }

    @Override void updateWorkflowUi() {
        super.updateWorkflowUi();
        if (activeInstruction != null) {
            WorkflowStep s = activeWorkflowStep();
            StepMeta m = stepMeta(currentWorkflowProfile().id, s.label);
            String instruction = m.instruction.trim();
            String behaviour = "continuous".equals(m.behaviour)
                    ? "Keep adding to this step until you choose Done."
                    : "Relay advances when this step is complete.";
            activeInstruction.setText((instruction.isEmpty() ? s.label : instruction) + "\n" + behaviour + " · Per-capture note: " + noteModeLabel(m.noteMode));
        }
    }

    @Override void editWorkflowProfile(WorkflowProfile original) {
        WorkflowProfile draft = original == null
                ? WorkflowProfile.seeded("New Profile", "General Capture", "None", true, true, true, "sequence", new WorkflowStep("Capture", 1, 1, false))
                : original.copy();
        if (draft.steps.isEmpty()) draft.steps.add(new WorkflowStep("Capture",1,1,false));

        ArrayList<StepMeta> metas = loadMetasForDraft(draft);
        LinearLayout body = productRoot(original == null ? "New Profile" : "Edit Profile", "Define context, workflow and delivery defaults");

        body.addView(groupLabel("Profile"));
        EditText name = edit("Profile name"); name.setText(draft.name); body.addView(name, spaced());

        body.addView(groupLabel("Classification"));
        Spinner cat = new Spinner(this); List<String> cats = cats(); cat.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats)); int ci=cats.indexOf(draft.category); if(ci>=0)cat.setSelection(ci); body.addView(cat, spaced());
        Spinner et = new Spinner(this); List<String> ets=entityTypes(); et.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ets)); int ei=ets.indexOf(draft.entityType); if(ei>=0)et.setSelection(ei); body.addView(et, spaced());

        body.addView(groupLabel("Context fields"));
        CheckBox se=check("Ask for entity/name",draft.showEntity); body.addView(se);
        CheckBox sm=check("Ask for matter/project",draft.showMatter); body.addView(sm);
        CheckBox sn=check("Allow a session-level note",draft.showSessionNote); body.addView(sn,spaced());

        body.addView(groupLabel("Workflow"));
        body.addView(helper("Workflow steps describe what a capture session expects. Each step can be required or optional, single or repeating, and can control per-capture notes."), spaced());
        LinearLayout stepHost = new LinearLayout(this); stepHost.setOrientation(LinearLayout.VERTICAL); body.addView(stepHost, matchWrap());
        Runnable redraw = () -> renderStepEditor(stepHost,draft,metas);
        redraw.run();
        Button add = secondary("＋  Add workflow step", v -> editStep(draft, metas, -1, redraw)); body.addView(add, spaced());

        body.addView(groupLabel("Delivery defaults"));
        TextView delivery = helper(draft.destinationIds == null || draft.destinationIds.isEmpty() ? "No profile-specific destinations selected. Relay will use the current selection." : draft.destinationIds.size()+" destination default"+(draft.destinationIds.size()==1?"":"s")+" configured."); body.addView(delivery,spaced());
        Button dest = secondary("Use current destination selection", v -> { draft.destinationIds = new ArrayList<>(selectedDestinationIds); delivery.setText(draft.destinationIds.size()+" destination default"+(draft.destinationIds.size()==1?"":"s")+" configured."); body.addView(dest,spaced());

        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel=secondary("Cancel",v->manageWorkflowProfiles()); actions.addView(cancel,weight());
        Button save=primary("Save profile",v->{
            String profileName=name.getText().toString().trim(); if(profileName.isEmpty()){name.setError("Give this profile a name.");return;}
            if(draft.steps.isEmpty()){toast("Add at least one workflow step.");return;}
            draft.name=profileName; draft.category=String.valueOf(cat.getSelectedItem()); draft.entityType=String.valueOf(et.getSelectedItem()); draft.showEntity=se.isChecked(); draft.showMatter=sm.isChecked(); draft.showSessionNote=sn.isChecked(); draft.mode="sequence";
            List<WorkflowProfile> all=workflowProfiles(); if(original==null)all.add(draft); else replaceWorkflowProfile(all,draft); saveWorkflowProfiles(all); saveMetas(draft.id,draft.steps,metas);
            refreshWorkflowProfileSpinner(); workflowProfileIndex=Math.max(0,indexOfWorkflowProfile(all,draft.id)); applyWorkflowProfile(workflowProfileIndex); manageWorkflowProfiles();
        }); actions.addView(save,weight()); body.addView(actions,spaced());

        if(original!=null){Button remove=secondary("Remove profile",v->confirmRemoveProfile(original));body.addView(remove,spaced());}
    }

    CheckBox check(String text, boolean value){CheckBox c=new CheckBox(this);c.setText(text);c.setChecked(value);c.setTextSize(14);c.setTextColor(Color.rgb(45,50,58));c.setPadding(dp(4),dp(4),0,dp(4));return c;}

    void renderStepEditor(LinearLayout host, WorkflowProfile draft, ArrayList<StepMeta> metas){
        host.removeAllViews();
        for(int i=0;i<draft.steps.size();i++){
            final int index=i; WorkflowStep s=draft.steps.get(i); StepMeta m=metaAt(metas,i,s);
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(12),dp(8),dp(12));card.setBackground(roundRect(Color.WHITE,dp(14),Color.rgb(226,230,236)));
            LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);
            TextView title=new TextView(this);title.setText((i+1)+" · "+s.label);title.setTextSize(15);title.setTypeface(Typeface.DEFAULT_BOLD);title.setTextColor(Color.rgb(36,41,49));words.addView(title);
            TextView detail=new TextView(this);detail.setText(stepSummary(s,m));detail.setTextSize(12);detail.setTextColor(Color.rgb(96,103,114));detail.setPadding(0,dp(3),0,0);words.addView(detail);
            if(!m.instruction.trim().isEmpty()){TextView inst=new TextView(this);inst.setText(m.instruction.trim());inst.setTextSize(12);inst.setTextColor(Color.rgb(73,80,90));inst.setMaxLines(2);inst.setPadding(0,dp(4),0,0);words.addView(inst);} card.addView(words,weight());
            TextView edit=miniAction("Edit",false);edit.setOnClickListener(v->editStep(draft,metas,index,()->renderStepEditor(host,draft,metas)));card.addView(edit,new LinearLayout.LayoutParams(dp(58),dp(38)));
            host.addView(card,spaced());
        }
    }

    String stepSummary(WorkflowStep s, StepMeta m){
        String requirement=s.min>0?"Required":"Optional";
        String count;
        if(s.min==1&&s.max==1)count="exactly 1";
        else if(s.max==0)count=(s.min>0?"at least "+s.min:"unlimited");
        else if(s.min==s.max)count="exactly "+s.min;
        else count=s.min+"–"+s.max+" captures";
        String behaviour="continuous".equals(m.behaviour)?"keep adding until Done":"advance when complete";
        return requirement+" · "+count+" · "+behaviour+" · note "+noteModeLabel(m.noteMode).toLowerCase(Locale.CANADA);
    }

    void editStep(WorkflowProfile draft, ArrayList<StepMeta> metas, int index, Runnable redraw){
        boolean adding=index<0; WorkflowStep original=adding?new WorkflowStep("New step",0,0,false):draft.steps.get(index); StepMeta originalMeta=adding?new StepMeta():metaAt(metas,index,original);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),dp(6));
        EditText label=edit("Step name, e.g. Front, Back, Page, Inspection photo");label.setText(original.label);box.addView(label,spaced());
        EditText instruction=edit("Optional instruction shown during capture");instruction.setText(originalMeta.instruction);instruction.setMinLines(2);box.addView(instruction,spaced());

        CheckBox required=check("Required step",original.min>0);box.addView(required);
        TextView countHelp=helper("Minimum and maximum apply to this step. Use 0 maximum for unlimited captures.");box.addView(countHelp,spaced());
        LinearLayout counts=new LinearLayout(this);counts.setOrientation(LinearLayout.HORIZONTAL);
        EditText min=edit("Minimum");min.setInputType(InputType.TYPE_CLASS_NUMBER);min.setText(String.valueOf(original.min));counts.addView(min,weight());
        EditText max=edit("Maximum · 0 = unlimited");max.setInputType(InputType.TYPE_CLASS_NUMBER);max.setText(String.valueOf(original.max));counts.addView(max,weight());box.addView(counts,spaced());

        box.addView(groupLabel("Capture behaviour"));
        Spinner behaviour=new Spinner(this);String[] behaviours={"Advance when complete","Keep adding until Done"};behaviour.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,behaviours));behaviour.setSelection("continuous".equals(originalMeta.behaviour)?1:0);box.addView(behaviour,spaced());

        box.addView(groupLabel("Per-capture note"));
        Spinner noteMode=new Spinner(this);String[] notes={"Not used","Optional","Required"};noteMode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,notes));noteMode.setSelection("required".equals(originalMeta.noteMode)?2:("none".equals(originalMeta.noteMode)?0:1));box.addView(noteMode,spaced());

        ScrollView sc=new ScrollView(this);sc.addView(box);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(adding?"Add workflow step":"Edit "+original.label).setView(sc).setPositiveButton("Save",null).setNegativeButton("Cancel",null).setNeutralButton(adding?null:"Delete",null).create();
        d.setOnShowListener(x->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String lab=label.getText().toString().trim();if(lab.isEmpty()){label.setError("Give this step a name.");return;}
                int mn=parseCount(min,required.isChecked()?1:0);int mx=parseCount(max,0);if(!required.isChecked())mn=0;if(mx>0&&mx<mn){max.setError("Maximum cannot be less than minimum.");return;}
                String nm=String.valueOf(noteMode.getSelectedItem());boolean noteReq="Required".equals(nm);
                WorkflowStep replacement=new WorkflowStep(lab,mn,mx,noteReq);StepMeta meta=new StepMeta();meta.instruction=instruction.getText().toString().trim();meta.behaviour=behaviour.getSelectedItemPosition()==1?"continuous":"advance";meta.noteMode=noteReq?"required":("Not used".equals(nm)?"none":"optional");
                if(adding){draft.steps.add(replacement);metas.add(meta);}else{draft.steps.set(index,replacement);while(metas.size()<=index)metas.add(new StepMeta());metas.set(index,meta);}d.dismiss();redraw.run();
            });
            if(!adding)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{if(draft.steps.size()<=1){toast("A profile needs at least one workflow step.");return;}draft.steps.remove(index);if(index<metas.size())metas.remove(index);d.dismiss();redraw.run();});
        });d.show();
    }

    int parseCount(EditText e,int fallback){try{return Math.max(0,Integer.parseInt(e.getText().toString().trim()));}catch(Exception x){return fallback;}}

    void confirmRemoveProfile(WorkflowProfile original){new AlertDialog.Builder(this).setTitle("Remove "+original.name+"?").setMessage("This removes the capture profile and its workflow definition. Existing capture receipts are unchanged.").setPositiveButton("Remove",(d,w)->{List<WorkflowProfile> all=workflowProfiles();if(all.size()<=1){toast("Relay needs at least one profile.");return;}all.removeIf(x->x.id.equals(original.id));saveWorkflowProfiles(all);removeMetas(original.id);workflowProfileIndex=0;refreshWorkflowProfileSpinner();applyWorkflowProfile(0);manageWorkflowProfiles();}).setNegativeButton("Cancel",null).show();}

    String noteModeLabel(String mode){if("required".equals(mode))return "Required";if("none".equals(mode))return "Not used";return "Optional";}

    ArrayList<StepMeta> loadMetasForDraft(WorkflowProfile p){ArrayList<StepMeta> out=new ArrayList<>();JSONArray a=profileMetaArray(p.id);for(int i=0;i<p.steps.size();i++){JSONObject x=i<a.length()?a.optJSONObject(i):null;StepMeta m=x==null?new StepMeta():StepMeta.from(x);WorkflowStep s=p.steps.get(i);if(x==null)m.noteMode=s.noteRequired?"required":"optional";out.add(m);}return out;}
    StepMeta metaAt(ArrayList<StepMeta> metas,int index,WorkflowStep s){while(metas.size()<=index){StepMeta m=new StepMeta();m.noteMode=s.noteRequired?"required":"optional";metas.add(m);}return metas.get(index);}
    StepMeta stepMeta(String profileId,String label){WorkflowProfile p=currentWorkflowProfile();int i=0;for(;i<p.steps.size();i++)if(p.steps.get(i).label.equals(label))break;JSONArray a=profileMetaArray(profileId);JSONObject x=i<a.length()?a.optJSONObject(i):null;if(x!=null)return StepMeta.from(x);StepMeta m=new StepMeta();for(WorkflowStep s:p.steps)if(s.label.equals(label))m.noteMode=s.noteRequired?"required":"optional";return m;}

    JSONObject metaRoot(){try{return new JSONObject(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_STEP_META_V142,"{}"));}catch(Exception e){return new JSONObject();}}
    JSONArray profileMetaArray(String id){JSONObject r=metaRoot();JSONArray a=r.optJSONArray(id);return a==null?new JSONArray():a;}
    void saveMetas(String id,List<WorkflowStep> steps,List<StepMeta> metas){try{JSONObject r=metaRoot();JSONArray a=new JSONArray();for(int i=0;i<steps.size();i++){StepMeta m=i<metas.size()?metas.get(i):new StepMeta();a.put(m.json());}r.put(id,a);getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_STEP_META_V142,r.toString()).apply();}catch(Exception ignored){}}
    void removeMetas(String id){try{JSONObject r=metaRoot();r.remove(id);getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_STEP_META_V142,r.toString()).apply();}catch(Exception ignored){}}

    @Override void editArtifactProduct(Item it){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);
        box.addView(helper(it.name+"\n"+sourceLabel(it.source)+" · source provenance is retained by Relay"),spaced());
        box.addView(groupLabel("Workflow step"));Spinner step=new Spinner(this);ArrayList<String> labels=workflowStepLabels();step.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));int idx=labels.indexOf(itemSteps.getOrDefault(it,labels.get(0)));if(idx>=0)step.setSelection(idx);box.addView(step,spaced());
        box.addView(groupLabel("Attached note"));EditText note=edit("Note for this capture");note.setText(itemNotes.getOrDefault(it,""));note.setMinLines(3);box.addView(note);
        TextView notePolicy=helper("");box.addView(notePolicy,spaced());
        Runnable policy=()->{String lab=String.valueOf(step.getSelectedItem());StepMeta m=stepMeta(currentWorkflowProfile().id,lab);note.setVisibility("none".equals(m.noteMode)?View.GONE:View.VISIBLE);notePolicy.setText("Per-capture note: "+noteModeLabel(m.noteMode));};
        step.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?>p,View v,int pos,long id){policy.run();}public void onNothingSelected(AdapterView<?>p){}});policy.run();
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Edit item").setView(box).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String lab=String.valueOf(step.getSelectedItem());StepMeta m=stepMeta(currentWorkflowProfile().id,lab);String value=note.getText().toString().trim();if("required".equals(m.noteMode)&&value.isEmpty()){note.setError("A note is required for this workflow step.");return;}itemSteps.put(it,lab);if("none".equals(m.noteMode)||value.isEmpty())itemNotes.remove(it);else itemNotes.put(it,value);d.dismiss();updateWorkflowUi();}));d.show();
    }

    static class StepMeta{
        String instruction="";String behaviour="advance";String noteMode="optional";
        JSONObject json(){JSONObject x=new JSONObject();try{x.put("instruction",instruction);x.put("behaviour",behaviour);x.put("note_mode",noteMode);}catch(Exception ignored){}return x;}
        static StepMeta from(JSONObject x){StepMeta m=new StepMeta();m.instruction=x.optString("instruction","");m.behaviour=x.optString("behaviour","advance");m.noteMode=x.optString("note_mode","optional");return m;}
    }
}
