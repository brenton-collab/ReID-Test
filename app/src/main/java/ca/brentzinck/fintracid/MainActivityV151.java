package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.util.*;

/** Relay Capture v1.5.1: hierarchy-led authoring + explicit escape UX. */
public class MainActivityV151 extends MainActivityV150 {

    @Override void showSettings() {
        LinearLayout body=productRoot("Settings","Build capture procedures first; manage their reusable parts when needed");

        body.addView(groupLabel("Build & use"));
        TextView model=helper("A Profile is the complete capture procedure. It defines Context, then a Workflow of Steps, then Delivery. Each Step uses an Artifact Type to control acquisition guidance.");
        body.addView(model,spaced());
        body.addView(navRow("Capture Profiles",workflowProfiles().size()+" procedures · context → workflow → delivery",v->manageWorkflowProfiles()),spaced());

        body.addView(groupLabel("Reusable definitions"));
        body.addView(navRow("Artifact Types",artifactTypes().size()+" kinds of evidence · controls camera/acquisition guidance",v->manageArtifactTypes()),spaced());
        body.addView(navRow("Categories",cats().size()+" reasons or session classes · Collection adds grouping behaviour",v->manageCats()),spaced());
        body.addView(navRow("Entity Types",entityTypes().size()+" what the evidence belongs to",v->manageEntityTypes()),spaced());

        body.addView(groupLabel("Delivery"));
        body.addView(navRow("Destinations",destinations().size()+" durable preservation and signal targets",v->manageDestinations()),spaced());

        TextView map=helper("PROFILE\n  Context → Category + Entity Type (+ Collection when used)\n  Workflow → Step → Artifact Type + obligation + completion + note policy\n  Delivery → Destinations");
        LinearLayout.LayoutParams mp=spaced(); mp.topMargin=dp(12); body.addView(map,mp);
    }

    @Override void manageWorkflowProfiles() {
        LinearLayout body=productRoot("Capture Profiles","Each Profile is a complete guided capture procedure");
        body.addView(helper("Start here. Relay will lead you through what the capture means, what happens in order, how each artifact should be acquired, and where the finished evidence goes."),spaced());
        for(WorkflowProfile p:workflowProfiles()) {
            String detail=p.category+" · "+p.steps.size()+" workflow step"+(p.steps.size()==1?"":"s");
            if(p.destinationIds!=null&&!p.destinationIds.isEmpty()) detail+=" · "+p.destinationIds.size()+" destination"+(p.destinationIds.size()==1?"":"s");
            body.addView(navRow(p.name,detail,v->editWorkflowProfile(p)),spaced());
        }
        body.addView(primary("＋  Build new profile",v->editWorkflowProfile(null)),spaced());
    }

    @Override void editWorkflowProfile(WorkflowProfile original) {
        final WorkflowProfile draft=original==null
                ? WorkflowProfile.seeded("New Profile","General Capture","None",true,true,true,"sequence",new WorkflowStep("Capture",1,1,false))
                : original.copy();
        if(draft.steps.isEmpty())draft.steps.add(new WorkflowStep("Capture",1,1,false));
        final ArrayList<StepMeta> metas=loadMetas(draft);

        LinearLayout body=productRoot(original==null?"Build Profile":"Edit Profile","Context → Workflow → Delivery");

        TextView intro=helper("A Profile is what the operator chooses at the start of a Relay session. The definitions below are ingredients of this procedure, not separate workflows.");
        body.addView(intro,spaced());

        body.addView(numberedHeading("1","Identity","What should this procedure be called?"));
        EditText name=edit("Profile name, e.g. FINTRAC ID"); name.setText(draft.name); body.addView(name,spaced());

        body.addView(numberedHeading("2","Context","Why does this evidence exist, and what does it belong to?"));
        TextView catHelp=helper("Category describes the purpose/session class. Choosing Collection makes the session a named evidence packet whose members stay linked together."); body.addView(catHelp,spaced());
        Spinner cat=new Spinner(this); ArrayList<String> catValues=new ArrayList<>(cats()); ArrayAdapter<String> catAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,catValues); cat.setAdapter(catAdapter); int ci=catValues.indexOf(draft.category); if(ci>=0)cat.setSelection(ci); body.addView(cat,spaced());
        LinearLayout catActions=new LinearLayout(this); catActions.setOrientation(LinearLayout.HORIZONTAL);
        catActions.addView(secondary("＋ New category",v->createCategoryInline(catValues,catAdapter,cat)),weight());
        catActions.addView(secondary("Manage",v->manageCats()),weight()); body.addView(catActions,spaced());

        TextView entityHelp=helper("Entity Type says what the evidence belongs to, such as Person, Property, Matter or Organization."); body.addView(entityHelp,spaced());
        Spinner et=new Spinner(this); ArrayList<String> entityValues=new ArrayList<>(entityTypes()); ArrayAdapter<String> etAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,entityValues); et.setAdapter(etAdapter); int ei=entityValues.indexOf(draft.entityType); if(ei>=0)et.setSelection(ei); body.addView(et,spaced());
        LinearLayout entityActions=new LinearLayout(this); entityActions.setOrientation(LinearLayout.HORIZONTAL);
        entityActions.addView(secondary("＋ New entity type",v->createEntityInline(entityValues,etAdapter,et)),weight());
        entityActions.addView(secondary("Manage",v->manageEntityTypes()),weight()); body.addView(entityActions,spaced());

        CheckBox se=check("Ask for entity / name",draft.showEntity); body.addView(se);
        CheckBox sm=check("Ask for matter / project",draft.showMatter); body.addView(sm);
        CheckBox sn=check("Allow a session-level note",draft.showSessionNote); body.addView(sn,spaced());

        body.addView(numberedHeading("3","Workflow","What happens, in order?"));
        body.addView(helper("A Workflow is made of Steps. Each Step says what artifact it expects, how many are needed, whether Relay can advance automatically, and whether each capture needs a note."),spaced());
        LinearLayout stepHost=new LinearLayout(this); stepHost.setOrientation(LinearLayout.VERTICAL); body.addView(stepHost,matchWrap());
        Runnable redraw=()->renderSteps(stepHost,draft,metas); redraw.run();
        body.addView(primary("＋  Add workflow step",v->editStep(draft,metas,-1,redraw)),spaced());
        body.addView(secondary("Manage reusable Artifact Types",v->manageArtifactTypes()),spaced());

        body.addView(numberedHeading("4","Delivery","Where should completed evidence go?"));
        TextView delivery=helper(draft.destinationIds==null||draft.destinationIds.isEmpty()?"No profile-specific destination defaults yet. Relay will use the operator's current destination selection.":draft.destinationIds.size()+" destination default"+(draft.destinationIds.size()==1?"":"s")+" configured."); body.addView(delivery,spaced());
        LinearLayout deliveryActions=new LinearLayout(this); deliveryActions.setOrientation(LinearLayout.HORIZONTAL);
        deliveryActions.addView(secondary("Use current selection",v->{draft.destinationIds=new ArrayList<>(selectedDestinationIds);delivery.setText(draft.destinationIds.size()+" destination default"+(draft.destinationIds.size()==1?"":"s")+" configured.");}),weight());
        deliveryActions.addView(secondary("Manage",v->manageDestinations()),weight()); body.addView(deliveryActions,spaced());

        body.addView(numberedHeading("5","Save","Relay will execute this procedure as authored."));
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(secondary("Cancel",v->manageWorkflowProfiles()),weight());
        actions.addView(primary("Save profile",v->{
            String profileName=name.getText().toString().trim(); if(profileName.isEmpty()){name.setError("Give this profile a name.");return;}
            if(draft.steps.isEmpty()){toast("Add at least one workflow step.");return;}
            draft.name=profileName; draft.category=String.valueOf(cat.getSelectedItem()); draft.entityType=String.valueOf(et.getSelectedItem()); draft.showEntity=se.isChecked(); draft.showMatter=sm.isChecked(); draft.showSessionNote=sn.isChecked(); draft.mode="sequence";
            List<WorkflowProfile> all=workflowProfiles(); if(original==null)all.add(draft); else replaceWorkflowProfile(all,draft); saveWorkflowProfiles(all); saveMetas(draft.id,draft.steps,metas);
            refreshWorkflowProfileSpinner(); workflowProfileIndex=Math.max(0,indexOfWorkflowProfile(all,draft.id)); applyWorkflowProfile(workflowProfileIndex); manageWorkflowProfiles();
        }),weight()); body.addView(actions,spaced());
        if(original!=null)body.addView(secondary("Remove profile",v->confirmRemoveProfile(original)),spaced());
    }

    TextView numberedHeading(String number,String title,String detail){
        TextView v=new TextView(this); v.setText(number+" · "+title.toUpperCase(Locale.CANADA)+"\n"+detail); v.setTextSize(13); v.setTypeface(Typeface.DEFAULT_BOLD); v.setTextColor(Color.rgb(64,72,88)); v.setPadding(dp(4),dp(18),dp(4),dp(8)); return v;
    }

    void createCategoryInline(ArrayList<String> values,ArrayAdapter<String> adapter,Spinner spinner){
        EditText x=edit("Category name"); new AlertDialog.Builder(this).setTitle("New category").setView(x).setPositiveButton("Add",(d,w)->{String value=x.getText().toString().trim();if(value.isEmpty())return;Set<String>s=customCats();s.add(value);saveCats(s);values.clear();values.addAll(cats());adapter.notifyDataSetChanged();int i=values.indexOf(value);if(i>=0)spinner.setSelection(i);}).setNegativeButton("Cancel",null).show();
    }

    void createEntityInline(ArrayList<String> values,ArrayAdapter<String> adapter,Spinner spinner){
        EditText x=edit("Entity type"); new AlertDialog.Builder(this).setTitle("New entity type").setView(x).setPositiveButton("Add",(d,w)->{String value=x.getText().toString().trim();if(value.isEmpty())return;Set<String>s=customEntityTypes();s.add(value);saveEntityTypes(s);values.clear();values.addAll(entityTypes());adapter.notifyDataSetChanged();int i=values.indexOf(value);if(i>=0)spinner.setSelection(i);}).setNegativeButton("Cancel",null).show();
    }

    @Override void buildCaptureStage(){
        super.buildCaptureStage();
        Button abort=secondary("Abort session",v->confirmAbortSession());
        abort.setTextColor(Color.rgb(166,62,62));
        LinearLayout.LayoutParams ap=matchWrap(); ap.topMargin=dp(10); captureStage.addView(abort,ap);
    }

    void confirmAbortSession(){
        int count=items.size();
        String msg=count==0?"This will abandon the current Relay session and return to a clean Profile screen.":count+" staged artifact"+(count==1?"":"s")+" will be discarded from Relay. Imported/shared originals are not deleted. Nothing will be secured or signalled.";
        new AlertDialog.Builder(this).setTitle("Abort this Relay session?").setMessage(msg).setPositiveButton("Abort session",(d,w)->abortSession()).setNegativeButton("Keep working",null).show();
    }

    void abortSession(){
        for(Item it:new ArrayList<>(items)) if(it.file!=null) try{it.file.delete();}catch(Throwable ignored){}
        items.clear(); itemSteps.clear(); itemNotes.clear(); itemArtifactTypes.clear(); collectionIdSnapshot=""; collectionTitleSnapshot=""; if(collectionTitle!=null)collectionTitle.setText("");
        resetForNewSession();
        toast("Session aborted. Nothing was secured or sent.");
    }

    @Override public void onBackPressed(){
        if(auxiliaryScreen){super.onBackPressed();return;}
        if(stage==1 && !items.isEmpty()) { confirmAbortSession(); return; }
        super.onBackPressed();
    }
}