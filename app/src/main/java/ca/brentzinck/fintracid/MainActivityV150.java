package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import androidx.documentfile.provider.DocumentFile;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Relay Capture v1.5: guided workflow runtime + operational evidence definitions. */
public class MainActivityV150 extends MainActivityV142 {
    static final String KEY_ARTIFACT_TYPES_V150 = "artifact_types_v150";
    static final String KEY_STEP_ARTIFACT_V150 = "workflow_step_artifact_v150";
    static final String COLLECTION_CATEGORY = "Collection";

    final IdentityHashMap<Item,String> itemArtifactTypes = new IdentityHashMap<>();
    LinearLayout collectionBlock;
    EditText collectionTitle;
    Button doneCaptureButton;
    String collectionIdSnapshot = "";
    String collectionTitleSnapshot = "";

    @Override void build() {
        ensureV150Definitions();
        super.build();
    }

    void ensureV150Definitions() {
        Set<String> custom = customCats();
        if (!cats().contains(COLLECTION_CATEGORY)) {
            custom.add(COLLECTION_CATEGORY);
            saveCats(custom);
        }
    }

    @Override void buildContextStage() {
        super.buildContextStage();
        collectionBlock = new LinearLayout(this);
        collectionBlock.setOrientation(LinearLayout.VERTICAL);
        collectionBlock.addView(label("Collection title"));
        collectionTitle = edit("What should these artifacts be treated as together?");
        collectionBlock.addView(collectionTitle);
        collectionBlock.addView(helper("Collection keeps otherwise mixed artifacts together as one evidence packet. Each member still retains its own source, artifact type, workflow role and note."), spaced());
        int at = Math.max(0, contextStage.getChildCount()-1);
        contextStage.addView(collectionBlock, at);
    }

    @Override void buildCaptureStage() {
        super.buildCaptureStage();
        Button camera = findButton(captureStage, "Camera");
        if (camera != null) camera.setOnClickListener(v -> launchGuidedCamera());
        doneCaptureButton = findButton(captureStage, "Done capturing");
        if (doneCaptureButton != null) doneCaptureButton.setOnClickListener(v -> handleDoneCurrentStep());
    }

    Button findButton(View root, String text) {
        if (root instanceof Button && text.equals(((Button)root).getText().toString())) return (Button)root;
        if (root instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++){Button b=findButton(g.getChildAt(i),text);if(b!=null)return b;}
        }
        return null;
    }

    boolean collectionEnabled() {
        WorkflowProfile p=currentWorkflowProfile();
        return p!=null && COLLECTION_CATEGORY.equalsIgnoreCase(p.category==null?"":p.category.trim());
    }

    @Override void updateWorkflowUi() {
        super.updateWorkflowUi();
        if (collectionBlock != null) collectionBlock.setVisibility(collectionEnabled()?View.VISIBLE:View.GONE);
        if (doneCaptureButton != null) {
            WorkflowStep s=activeWorkflowStep(); StepMeta m=stepMeta(currentWorkflowProfile().id,s.label);
            if ("continuous".equals(m.behaviour) || s.max==0) doneCaptureButton.setText("Done with "+s.label);
            else doneCaptureButton.setText("Done capturing");
        }
    }

    void launchGuidedCamera() {
        WorkflowStep s=activeWorkflowStep();
        ArtifactType a=artifactTypeForStep(currentWorkflowProfile(),s.label);
        StepMeta m=stepMeta(currentWorkflowProfile().id,s.label);
        Intent i=new Intent(this,CaptureActivity.class);
        i.putExtra(CaptureActivity.EXTRA_GUIDE,a.guide);
        i.putExtra(CaptureActivity.EXTRA_ORIENTATION,a.orientation);
        i.putExtra(CaptureActivity.EXTRA_PERSPECTIVE_GUIDE,a.perspectiveGuide);
        i.putExtra(CaptureActivity.EXTRA_INSTRUCTION,m.instruction.trim().isEmpty()?s.label:m.instruction.trim());
        startActivityForResult(i,CAMERA);
    }

    @Override void promptRequiredItemNotes(List<Item> added, WorkflowStep step, int index) {
        ArtifactType at=artifactTypeForStep(currentWorkflowProfile(),step.label);
        for(Item it:added) if(!itemArtifactTypes.containsKey(it)) itemArtifactTypes.put(it,at.id);
        StepMeta meta=stepMeta(currentWorkflowProfile().id,step.label);
        if(!"required".equals(meta.noteMode) || index>=added.size()) {
            updateWorkflowUi();
            if(index>=added.size() || !"required".equals(meta.noteMode)) advanceAfterCapture(step,added);
            return;
        }
        Item it=added.get(index);
        EditText input=edit("Why does this capture matter?"); input.setMinLines(2);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(step.label+" note required").setView(input).setPositiveButton("Save",null).setNegativeButton("Remove item",null).create();
        d.setOnShowListener(x->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=input.getText().toString().trim();if(value.isEmpty()){input.setError("A note is required for this workflow step.");return;}itemNotes.put(it,value);d.dismiss();promptRequiredItemNotes(added,step,index+1);});
            d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{items.remove(it);if(it.file!=null)try{it.file.delete();}catch(Throwable ignored){}itemSteps.remove(it);itemNotes.remove(it);itemArtifactTypes.remove(it);d.dismiss();promptRequiredItemNotes(added,step,index+1);});
        });
        d.show();
    }

    void advanceAfterCapture(WorkflowStep completed, List<Item> justAdded) {
        StepMeta m=stepMeta(currentWorkflowProfile().id,completed.label);
        if("continuous".equals(m.behaviour) || completed.max==0) return;
        if(completed.max<=0 || countForStep(completed.label)<completed.max) return;
        int current=indexOfStep(completed.label);
        if(current<0)return;
        int next=current+1;
        if(next>=currentWorkflowProfile().steps.size()) {
            String problem=workflowValidationProblem();
            if(problem==null){showStage(2);updateWorkflowUi();}
            return;
        }
        workflowStepSpinner.setSelection(next);
        updateWorkflowUi();
        WorkflowStep ns=currentWorkflowProfile().steps.get(next);
        boolean nextRequired=ns.min>0;
        boolean cameFromCamera=!justAdded.isEmpty() && "camera".equalsIgnoreCase(justAdded.get(justAdded.size()-1).source);
        if(nextRequired && cameFromCamera) workflowStepSpinner.postDelayed(this::launchGuidedCamera,220);
    }

    int indexOfStep(String label){List<WorkflowStep>s=currentWorkflowProfile().steps;for(int i=0;i<s.size();i++)if(s.get(i).label.equals(label))return i;return -1;}

    void handleDoneCurrentStep() {
        WorkflowStep s=activeWorkflowStep();
        int count=countForStep(s.label);
        if(count<s.min){toast(s.label+" requires at least "+s.min+" capture"+(s.min==1?".":"s."));return;}
        int idx=indexOfStep(s.label);
        if(idx>=0 && idx<currentWorkflowProfile().steps.size()-1){workflowStepSpinner.setSelection(idx+1);updateWorkflowUi();return;}
        String problem=workflowValidationProblem();
        if(problem!=null){toast(problem);return;}
        showStage(2);updateWorkflowUi();
    }

    @Override void showSettings() {
        LinearLayout body=productRoot("Settings","Configure how Relay captures, describes and preserves evidence");
        body.addView(groupLabel("Capture"));
        body.addView(navRow("Capture Profiles",workflowProfiles().size()+" profiles · workflow + defaults",v->manageWorkflowProfiles()),spaced());
        body.addView(navRow("Artifact Types",artifactTypes().size()+" acquisition definitions",v->manageArtifactTypes()),spaced());
        body.addView(navRow("Categories",cats().size()+" semantic / session definitions",v->manageCats()),spaced());
        body.addView(navRow("Entity Types",entityTypes().size()+" contextual definitions",v->manageEntityTypes()),spaced());
        body.addView(groupLabel("Delivery"));
        body.addView(navRow("Destinations",destinations().size()+" configured preservation targets",v->manageDestinations()),spaced());
        TextView foot=new TextView(this);foot.setText("Artifact Type controls acquisition guidance. Workflow controls evidence role and completion. Category describes why the evidence exists; Collection also groups its members into one evidence packet.");foot.setTextSize(12);foot.setTextColor(Color.rgb(110,116,126));foot.setPadding(dp(5),dp(16),dp(5),0);body.addView(foot);
    }

    void manageArtifactTypes() {
        LinearLayout body=productRoot("Artifact Types","Reusable definitions for how physical or digital evidence is acquired");
        for(ArtifactType a:artifactTypes()) body.addView(navRow(a.name,artifactTypeSummary(a),v->editArtifactType(a)),spaced());
        body.addView(primary("＋  New artifact type",v->editArtifactType(null)),spaced());
    }

    String artifactTypeSummary(ArtifactType a){String g="none".equals(a.guide)?"No camera guide":("id_card".equals(a.guide)?"ID card guide":"Document guide");return g+" · "+capitalize(a.orientation)+(a.perspectiveGuide?" · perspective aid":"");}
    String capitalize(String s){if(s==null||s.isEmpty())return "Auto";return s.substring(0,1).toUpperCase(Locale.CANADA)+s.substring(1);}

    void editArtifactType(ArtifactType existing) {
        ArtifactType draft=existing==null?new ArtifactType(UUID.randomUUID().toString(),"New Artifact Type","none","auto",false,false):existing.copy();
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),dp(6));
        EditText name=edit("Artifact type name");name.setText(draft.name);box.addView(name,spaced());
        box.addView(groupLabel("Camera guide"));Spinner guide=new Spinner(this);String[] guides={"None","ID Card","Document Page"};guide.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,guides));guide.setSelection("id_card".equals(draft.guide)?1:("document".equals(draft.guide)?2:0));box.addView(guide,spaced());
        box.addView(groupLabel("Preferred orientation"));Spinner orient=new Spinner(this);String[] ors={"Auto","Portrait","Landscape"};orient.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ors));orient.setSelection("portrait".equals(draft.orientation)?1:("landscape".equals(draft.orientation)?2:0));box.addView(orient,spaced());
        CheckBox perspective=check("Show perspective / keystone alignment aid",draft.perspectiveGuide);box.addView(perspective);
        box.addView(helper("Guides are acquisition aids only. They are never burned into the preserved image. Perspective aid in this version is visual guidance, not automatic image correction."),spaced());
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(existing==null?"New artifact type":"Edit artifact type").setView(box).setPositiveButton("Save",null).setNegativeButton("Cancel",null);
        if(existing!=null&&!existing.builtIn)b.setNeutralButton("Delete",null);
        AlertDialog d=b.create();
        d.setOnShowListener(x->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("Give this artifact type a name.");return;}draft.name=n;draft.guide=guide.getSelectedItemPosition()==1?"id_card":(guide.getSelectedItemPosition()==2?"document":"none");draft.orientation=orient.getSelectedItemPosition()==1?"portrait":(orient.getSelectedItemPosition()==2?"landscape":"auto");draft.perspectiveGuide=perspective.isChecked();saveArtifactType(draft);d.dismiss();manageArtifactTypes();});
            if(existing!=null&&!existing.builtIn)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{deleteArtifactType(existing.id);d.dismiss();manageArtifactTypes();});
        });d.show();
    }

    @Override void editStep(WorkflowProfile draft, ArrayList<StepMeta> metas, int index, Runnable redraw) {
        boolean adding=index<0;
        WorkflowStep old=adding?new WorkflowStep("New step",0,0,false):draft.steps.get(index);
        StepMeta oldMeta=adding?new StepMeta():metaAt(metas,index,old);
        String oldArtifact=adding?inferArtifactType(draft,old.label):stepArtifactTypeId(draft.id,old.label,draft);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),dp(6));
        EditText label=edit("Step name, e.g. Front, Back, Page, Inspection photo");label.setText(old.label);box.addView(label,spaced());
        EditText instruction=edit("Instruction shown during capture and preserved as evidence semantics");instruction.setText(oldMeta.instruction);instruction.setMinLines(2);box.addView(instruction,spaced());
        box.addView(groupLabel("Artifact type"));List<ArtifactType> ats=artifactTypes();ArrayList<String> names=new ArrayList<>();for(ArtifactType a:ats)names.add(a.name);Spinner artifact=new Spinner(this);artifact.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));int ai=indexOfArtifact(ats,oldArtifact);if(ai>=0)artifact.setSelection(ai);box.addView(artifact,spaced());
        CheckBox required=check("Required step",old.min>0);box.addView(required);
        box.addView(helper("A finite maximum lets Relay know when it can advance automatically. Use maximum 0 when only the operator can decide the step is complete."),spaced());
        LinearLayout counts=new LinearLayout(this);counts.setOrientation(LinearLayout.HORIZONTAL);EditText min=edit("Minimum");min.setInputType(InputType.TYPE_CLASS_NUMBER);min.setText(String.valueOf(old.min));counts.addView(min,weight());EditText max=edit("Maximum · 0 = open-ended");max.setInputType(InputType.TYPE_CLASS_NUMBER);max.setText(String.valueOf(old.max));counts.addView(max,weight());box.addView(counts,spaced());
        box.addView(groupLabel("Completion"));Spinner behaviour=new Spinner(this);String[] behaviours={"Auto-advance when maximum is reached","Operator chooses Done"};behaviour.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,behaviours));behaviour.setSelection("continuous".equals(oldMeta.behaviour)?1:0);box.addView(behaviour,spaced());
        box.addView(groupLabel("Per-capture note"));Spinner noteMode=new Spinner(this);String[] notes={"Not used","Optional","Required"};noteMode.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,notes));noteMode.setSelection("required".equals(oldMeta.noteMode)?2:("none".equals(oldMeta.noteMode)?0:1));box.addView(noteMode,spaced());
        ScrollView sc=new ScrollView(this);sc.addView(box);AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(adding?"Add workflow step":"Edit "+old.label).setView(sc).setPositiveButton("Save",null).setNegativeButton("Cancel",null);if(!adding)b.setNeutralButton("Delete",null);AlertDialog d=b.create();
        d.setOnShowListener(x->{
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String lab=label.getText().toString().trim();if(lab.isEmpty()){label.setError("Give this step a name.");return;}int mn=parseCount(min,required.isChecked()?1:0);int mx=parseCount(max,0);if(!required.isChecked())mn=0;if(mx>0&&mx<mn){max.setError("Maximum cannot be less than minimum.");return;}String nm=String.valueOf(noteMode.getSelectedItem());boolean noteReq="Required".equals(nm);WorkflowStep replacement=new WorkflowStep(lab,mn,mx,noteReq);StepMeta meta=new StepMeta();meta.instruction=instruction.getText().toString().trim();meta.behaviour=behaviour.getSelectedItemPosition()==1?"continuous":"advance";meta.noteMode=noteReq?"required":("Not used".equals(nm)?"none":"optional");String artifactId=ats.get(Math.max(0,artifact.getSelectedItemPosition())).id;if(adding){draft.steps.add(replacement);metas.add(meta);}else{draft.steps.set(index,replacement);while(metas.size()<=index)metas.add(new StepMeta());metas.set(index,meta);if(!old.label.equals(lab))removeStepArtifact(draft.id,old.label);}saveStepArtifact(draft.id,lab,artifactId);d.dismiss();redraw.run();});
            if(!adding)d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{if(draft.steps.size()<=1){toast("A profile needs at least one workflow step.");return;}removeStepArtifact(draft.id,old.label);draft.steps.remove(index);if(index<metas.size())metas.remove(index);d.dismiss();redraw.run();});
        });d.show();
    }

    int indexOfArtifact(List<ArtifactType> list,String id){for(int i=0;i<list.size();i++)if(list.get(i).id.equals(id))return i;return 0;}

    String inferArtifactType(WorkflowProfile p,String step) {
        String x=((p==null?"":p.name)+" "+step).toLowerCase(Locale.CANADA);
        if(x.contains("id")||x.contains("fintrac")||x.contains("licence")||x.contains("license")||x.contains("passport"))return "government_id";
        if(x.contains("receipt"))return "receipt";
        if(x.contains("page")||x.contains("document")||x.contains("agreement")||x.contains("signature"))return "document_page";
        if(x.contains("inspection"))return "inspection_photo";
        return "general_image";
    }

    ArtifactType artifactTypeForStep(WorkflowProfile p,String step){return artifactType(stepArtifactTypeId(p.id,step,p));}
    String stepArtifactTypeId(String profileId,String step,WorkflowProfile p){try{JSONObject root=new JSONObject(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_STEP_ARTIFACT_V150,"{}"));JSONObject prof=root.optJSONObject(profileId);String id=prof==null?"":prof.optString(step,"");return id.isEmpty()?inferArtifactType(p,step):id;}catch(Exception e){return inferArtifactType(p,step);}}
    void saveStepArtifact(String profileId,String step,String id){try{JSONObject root=new JSONObject(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_STEP_ARTIFACT_V150,"{}"));JSONObject prof=root.optJSONObject(profileId);if(prof==null)prof=new JSONObject();prof.put(step,id);root.put(profileId,prof);getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_STEP_ARTIFACT_V150,root.toString()).apply();}catch(Exception ignored){}}
    void removeStepArtifact(String profileId,String step){try{JSONObject root=new JSONObject(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_STEP_ARTIFACT_V150,"{}"));JSONObject prof=root.optJSONObject(profileId);if(prof!=null){prof.remove(step);root.put(profileId,prof);getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_STEP_ARTIFACT_V150,root.toString()).apply();}}catch(Exception ignored){}}

    List<ArtifactType> artifactTypes(){LinkedHashMap<String,ArtifactType> map=new LinkedHashMap<>();for(ArtifactType a:seedArtifactTypes())map.put(a.id,a);try{JSONArray arr=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_ARTIFACT_TYPES_V150,"[]"));for(int i=0;i<arr.length();i++){ArtifactType a=ArtifactType.from(arr.optJSONObject(i));if(a!=null)map.put(a.id,a);}}catch(Exception ignored){}return new ArrayList<>(map.values());}
    List<ArtifactType> seedArtifactTypes(){return Arrays.asList(new ArtifactType("general_image","General Image","none","auto",false,true),new ArtifactType("document_page","Document Page","document","auto",true,true),new ArtifactType("government_id","Government ID","id_card","landscape",true,true),new ArtifactType("receipt","Receipt","document","portrait",true,true),new ArtifactType("inspection_photo","Inspection Photo","none","auto",false,true),new ArtifactType("screenshot","Screenshot","none","auto",false,true),new ArtifactType("general_file","General File","none","auto",false,true));}
    ArtifactType artifactType(String id){for(ArtifactType a:artifactTypes())if(a.id.equals(id))return a;return artifactTypes().get(0);}
    void saveArtifactType(ArtifactType a){try{JSONArray old=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_ARTIFACT_TYPES_V150,"[]"));JSONArray out=new JSONArray();boolean found=false;for(int i=0;i<old.length();i++){ArtifactType x=ArtifactType.from(old.optJSONObject(i));if(x!=null&&x.id.equals(a.id)){out.put(a.json());found=true;}else if(x!=null)out.put(x.json());}if(!found)out.put(a.json());getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_ARTIFACT_TYPES_V150,out.toString()).apply();}catch(Exception ignored){}}
    void deleteArtifactType(String id){try{JSONArray old=new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_ARTIFACT_TYPES_V150,"[]"));JSONArray out=new JSONArray();for(int i=0;i<old.length();i++){ArtifactType x=ArtifactType.from(old.optJSONObject(i));if(x!=null&&!x.id.equals(id))out.put(x.json());}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_ARTIFACT_TYPES_V150,out.toString()).apply();}catch(Exception ignored){}}

    @Override void secure(){collectionIdSnapshot=collectionEnabled()?ensureCollectionId():"";collectionTitleSnapshot=collectionEnabled()&&collectionTitle!=null?collectionTitle.getText().toString().trim():"";super.secure();}
    String ensureCollectionId(){if(collectionIdSnapshot==null||collectionIdSnapshot.isEmpty())collectionIdSnapshot="col_"+UUID.randomUUID().toString();return collectionIdSnapshot;}

    @Override JSONObject buildSignalPayload(String cid,long when,WorkflowProfile wp,String typ,String ent,String mat,String nt,List<Destination> chosen){JSONObject p=super.buildSignalPayload(cid,when,wp,typ,ent,mat,nt,chosen);try{p.put("relay_schema_version",4);p.put("evidence_model","meaning-obligation-acquisition-assembly");if(collectionEnabled()){JSONObject c=new JSONObject();c.put("collection_id",ensureCollectionId());c.put("label",collectionTitle==null?"":collectionTitle.getText().toString().trim());c.put("member_count",items.size());p.put("collection",c);}JSONArray a=p.optJSONArray("items");if(a!=null){for(int i=0;i<a.length()&&i<items.size();i++){JSONObject x=a.optJSONObject(i);Item it=items.get(i);String step=itemSteps.getOrDefault(it,"Capture");WorkflowStep ws=workflowStepByLabel(step);StepMeta sm=stepMeta(wp.id,step);ArtifactType at=artifactType(itemArtifactTypes.getOrDefault(it,stepArtifactTypeId(wp.id,step,wp)));x.put("artifact_type",at.name);x.put("artifact_type_id",at.id);x.put("evidence_role",step);x.put("instruction",sm.instruction);x.put("required",ws.min>0);x.put("step_min",ws.min);x.put("step_max",ws.max);x.put("completion_mode","continuous".equals(sm.behaviour)?"operator_done":"auto_at_max");x.put("sequence_in_session",i+1);if(collectionEnabled()){x.put("collection_id",ensureCollectionId());x.put("collection_member_index",i+1);x.put("collection_member_count",items.size());}}}}catch(Exception ignored){}return p;}
    WorkflowStep workflowStepByLabel(String label){for(WorkflowStep s:currentWorkflowProfile().steps)if(s.label.equals(label))return s;return new WorkflowStep(label,0,0,false);}

    @Override void save(DocumentFile folder, Destination destination, Item it, int seq, String cid, String prof, String cat, String typ, String ent, String mat, String nt, long secured, List<Destination> chosen) throws Exception {
        String mime=it.file!=null?localMime(it.file.getName()):getContentResolver().getType(it.uri);if(mime==null)mime="application/octet-stream";
        String ext=evidenceExt(it.name,mime);String date=new SimpleDateFormat("yyyy-MM-dd",Locale.CANADA).format(new Date());String anchor=!ent.isEmpty()?ent:(!mat.isEmpty()?mat:"Unassigned");String fileName=evidenceSafe(date+" - "+anchor+" - "+cat+" - "+seq)+ext;
        DocumentFile target=folder.createFile(mime,fileName);if(target==null)throw new Exception("could not create destination file");DocumentFile sidecar=null;
        try{long bytes=0;try(InputStream in=it.file!=null?new FileInputStream(it.file):getContentResolver().openInputStream(it.uri);OutputStream out=getContentResolver().openOutputStream(target.getUri(),"w")){if(in==null||out==null)throw new Exception("could not open capture");byte[]buf=new byte[65536];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);bytes+=n;}out.flush();}if(bytes<=0)throw new Exception("capture contained no data");if(!verifyEvidenceReadable(target))throw new Exception("destination verification failed");
            String step=itemSteps.getOrDefault(it,"Capture");WorkflowStep ws=workflowStepByLabel(step);StepMeta sm=stepMeta(currentWorkflowProfile().id,step);ArtifactType at=artifactType(itemArtifactTypes.getOrDefault(it,stepArtifactTypeId(currentWorkflowProfile().id,step,currentWorkflowProfile())));
            JSONObject meta=new JSONObject();meta.put("relay_schema_version",4);meta.put("capture_id",cid);meta.put("captured_at",it.created);meta.put("secured_at",secured);meta.put("profile",prof);meta.put("category",cat);meta.put("entity_type",typ);meta.put("entity",ent);meta.put("matter",mat);meta.put("session_note",nt);meta.put("source",it.source);meta.put("original_name",it.name);meta.put("mime_type",mime);meta.put("file_name",fileName);meta.put("bytes_written",bytes);meta.put("app_version","1.5.0");
            JSONObject evidence=new JSONObject();evidence.put("artifact_type_id",at.id);evidence.put("artifact_type",at.name);evidence.put("workflow_step",step);evidence.put("evidence_role",step);evidence.put("instruction",sm.instruction);evidence.put("required",ws.min>0);evidence.put("minimum",ws.min);evidence.put("maximum",ws.max);evidence.put("completion_mode","continuous".equals(sm.behaviour)?"operator_done":"auto_at_max");evidence.put("item_note",itemNotes.getOrDefault(it,""));evidence.put("acquisition_guide",at.guide);evidence.put("preferred_orientation",at.orientation);evidence.put("perspective_guide",at.perspectiveGuide);evidence.put("sequence_in_session",Math.max(1,items.indexOf(it)+1));meta.put("evidence",evidence);
            if(!collectionIdSnapshot.isEmpty()){JSONObject c=new JSONObject();c.put("collection_id",collectionIdSnapshot);c.put("label",collectionTitleSnapshot);c.put("member_index",Math.max(1,items.indexOf(it)+1));c.put("member_count",items.size());meta.put("collection",c);}
            JSONObject dest=new JSONObject();dest.put("id",destination.id);dest.put("label",destination.label);dest.put("type",destination.type);dest.put("required",destination.required);dest.put("durable",destination.durable);meta.put("preserved_to",dest);JSONArray ds=new JSONArray();for(Destination d:chosen){JSONObject z=new JSONObject();z.put("id",d.id);z.put("label",d.label);z.put("type",d.type);z.put("durable",d.durable);z.put("required",d.required);ds.put(z);}meta.put("selected_destinations",ds);
            sidecar=folder.createFile("application/json",fileName+".json");if(sidecar==null)throw new Exception("could not create evidence sidecar");try(OutputStream out=getContentResolver().openOutputStream(sidecar.getUri(),"w")){if(out==null)throw new Exception("could not open evidence sidecar");out.write(meta.toString(2).getBytes(StandardCharsets.UTF_8));out.flush();}if(!verifyEvidenceReadable(sidecar))throw new Exception("evidence sidecar verification failed");
        }catch(Exception e){try{if(sidecar!=null)sidecar.delete();}catch(Exception ignored){}try{target.delete();}catch(Exception ignored){}throw e;}
    }

    String evidenceSafe(String x){String s=x==null?"":x.replaceAll("[\\\\/:*?\"<>|]","-").replaceAll("\\s+"," ").trim();return s.isEmpty()?"Relay Capture":s;}
    String evidenceExt(String name,String mime){String n=name==null?"":name;int dot=n.lastIndexOf('.');if(dot>=0&&dot<n.length()-1&&n.length()-dot<=8)return n.substring(dot);if("image/jpeg".equals(mime))return ".jpg";if("image/png".equals(mime))return ".png";if("application/pdf".equals(mime))return ".pdf";if("text/plain".equals(mime))return ".txt";return "";}
    boolean verifyEvidenceReadable(DocumentFile f){for(long delay:new long[]{0,150,350,750,1200}){if(delay>0)try{Thread.sleep(delay);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}try(InputStream in=getContentResolver().openInputStream(f.getUri())){if(in!=null&&in.read()!=-1)return true;}catch(Exception ignored){}}return false;}

    @Override void resetForNewSession(){super.resetForNewSession();itemArtifactTypes.clear();collectionIdSnapshot="";collectionTitleSnapshot="";if(collectionTitle!=null)collectionTitle.setText("");}

    static class ArtifactType {
        String id,name,guide,orientation;boolean perspectiveGuide,builtIn;
        ArtifactType(String i,String n,String g,String o,boolean p,boolean b){id=i;name=n;guide=g;orientation=o;perspectiveGuide=p;builtIn=b;}
        ArtifactType copy(){return new ArtifactType(id,name,guide,orientation,perspectiveGuide,builtIn);}
        JSONObject json(){JSONObject x=new JSONObject();try{x.put("id",id);x.put("name",name);x.put("guide",guide);x.put("orientation",orientation);x.put("perspective_guide",perspectiveGuide);x.put("built_in",builtIn);}catch(Exception ignored){}return x;}
        static ArtifactType from(JSONObject x){if(x==null)return null;String id=x.optString("id","");if(id.isEmpty())return null;return new ArtifactType(id,x.optString("name","Artifact"),x.optString("guide","none"),x.optString("orientation","auto"),x.optBoolean("perspective_guide",false),x.optBoolean("built_in",false));}
    }
}