package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.*;

import java.util.*;

/**
 * Relay Product UI 2.0.
 * Presentation-only rebuild over the validated v1.5 evidence engine.
 */
public class MainActivityV160 extends MainActivityV151 {
    static final int INK=Color.rgb(29,34,42), MUTED=Color.rgb(103,111,123), BLUE=Color.rgb(45,91,184),
            BG=Color.rgb(246,247,249), SURFACE=Color.WHITE, LINE=Color.rgb(225,229,235), SOFT=Color.rgb(242,245,249),
            DANGER=Color.rgb(176,62,62), SUCCESS=Color.rgb(40,122,79);

    @Override void build() {
        ensureV150Definitions();
        ensureWorkflowProfilesV12();
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); sc.setBackgroundColor(BG); sc.setClipToPadding(false);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(8),dp(20),dp(32)); sc.addView(root);

        LinearLayout toolbar=new LinearLayout(this); toolbar.setOrientation(LinearLayout.HORIZONTAL); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton history=iconButton(R.drawable.ic_history_relay,"History",v->showHistory()); toolbar.addView(history,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout brand=new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setPadding(dp(10),0,0,0);
        TextView title=text("Relay",30,INK,true); brand.addView(title); TextView sub=text("Capture with context. Preserve with proof.",12,MUTED,false); brand.addView(sub); toolbar.addView(brand,weight());
        ImageButton settings=iconButton(R.drawable.ic_settings_relay,"Settings",v->showSettings()); toolbar.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(46))); root.addView(toolbar,matchWrap());

        status=text("",13,MUTED,false); status.setPadding(dp(14),dp(11),dp(14),dp(11)); status.setBackground(shape(Color.WHITE,14,LINE)); status.setVisibility(View.GONE); LinearLayout.LayoutParams sp=matchWrap();sp.topMargin=dp(12);root.addView(status,sp);

        stepIndicator=text("",12,MUTED,true); stepIndicator.setGravity(Gravity.CENTER); stepIndicator.setPadding(0,dp(16),0,dp(12)); root.addView(stepIndicator,matchWrap());

        wizardHost=new LinearLayout(this); wizardHost.setOrientation(LinearLayout.VERTICAL); root.addView(wizardHost,matchWrap());
        buildContextStage(); buildCaptureStage(); buildReviewStage(); wizardHost.addView(contextStage,matchWrap()); wizardHost.addView(captureStage,matchWrap()); wizardHost.addView(reviewStage,matchWrap());

        mainScreen=sc; auxiliaryScreen=false; setContentView(sc); applyTopInset(sc);
    }

    @Override void buildContextStage(){
        contextStage=surface();
        contextStage.addView(eyebrow("START A SESSION"));
        contextStage.addView(hero("What are you capturing?","Choose a procedure. Relay will reveal only the context it needs."));

        profile=new Spinner(this); styleSpinner(profile); refreshWorkflowProfileSpinner();
        profile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?>p,View v,int pos,long id){if(pos!=workflowProfileIndex)applyWorkflowProfile(pos);}public void onNothingSelected(AdapterView<?>p){}});
        contextStage.addView(fieldLabel("Capture profile")); contextStage.addView(profile,spaced());
        profileSummary=text("",13,MUTED,false); contextStage.addView(profileSummary,spaced());

        entityBlock=new LinearLayout(this);entityBlock.setOrientation(LinearLayout.VERTICAL);
        entityBlock.addView(fieldLabel("What does this belong to?"));
        type=new Spinner(this); styleSpinner(type); type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,entityTypes())); entityBlock.addView(type,spaced());
        entity=edit("Name or identifier"); entityBlock.addView(entity,spaced()); contextStage.addView(entityBlock);

        matterBlock=new LinearLayout(this);matterBlock.setOrientation(LinearLayout.VERTICAL);matterBlock.addView(fieldLabel("Matter / transaction / project"));matter=edit("Optional reference");matterBlock.addView(matter,spaced());contextStage.addView(matterBlock);
        noteBlock=new LinearLayout(this);noteBlock.setOrientation(LinearLayout.VERTICAL);noteBlock.addView(fieldLabel("Session note"));note=edit("Context that applies to the whole session");note.setMinLines(2);noteBlock.addView(note,spaced());contextStage.addView(noteBlock);

        collectionBlock=new LinearLayout(this);collectionBlock.setOrientation(LinearLayout.VERTICAL);collectionBlock.addView(fieldLabel("Collection title"));collectionTitle=edit("Name this evidence packet");collectionBlock.addView(collectionTitle,spaced());collectionBlock.addView(info("Collection keeps mixed artifacts together while preserving each member's own role and note."),spaced());contextStage.addView(collectionBlock);

        Button next=primary("Begin capture",v->{applyContextVisibility();showStage(1);updateWorkflowUi();}); LinearLayout.LayoutParams np=matchWrap();np.topMargin=dp(10);contextStage.addView(next,np);
    }

    @Override void buildCaptureStage(){
        captureStage=surface(); captureStage.addView(eyebrow("CAPTURE"));
        workflowSummary=text("",13,MUTED,false); captureStage.addView(workflowSummary,spaced());
        activeInstruction=heroText("Ready","Relay will guide this step."); captureStage.addView(activeInstruction,spaced());

        workflowStepSpinner=new Spinner(this); styleSpinner(workflowStepSpinner); workflowStepSpinner.setVisibility(View.GONE); captureStage.addView(workflowStepSpinner,new LinearLayout.LayoutParams(1,1));

        LinearLayout sources=new LinearLayout(this);sources.setOrientation(LinearLayout.VERTICAL);
        sources.addView(actionTile("Camera","Capture with this step's framing and guidance","CAMERA",v->launchGuidedCamera()),spaced());
        LinearLayout minor=new LinearLayout(this);minor.setOrientation(LinearLayout.HORIZONTAL);minor.addView(secondary("Screenshot",v->startActivityForResult(new Intent(this,ScreenshotActivity.class),SCREENSHOT)),weight());minor.addView(secondary("Import / files",v->chooseImport()),weight());sources.addView(minor,spaced());captureStage.addView(sources);

        staged=text("",13,MUTED,true); staged.setPadding(0,dp(4),0,dp(8));captureStage.addView(staged);
        captureStage.addView(secondary("Review staged artifacts",v->reviewWorkflowItems()),spaced());

        doneCaptureButton=primary("Done",v->handleDoneCurrentStep());captureStage.addView(doneCaptureButton,spaced());
        TextView abort=link("Abort session",DANGER,v->confirmAbortSession());captureStage.addView(abort,matchWrap());
    }

    @Override void buildReviewStage(){
        reviewStage=surface();reviewStage.addView(eyebrow("REVIEW & SECURE"));reviewStage.addView(hero("Check the evidence","Confirm each artifact, then preserve it to the required durable destination."));
        reviewSummary=text("",13,MUTED,false);reviewStage.addView(reviewSummary,spaced());
        reviewItemsHost=new LinearLayout(this);reviewItemsHost.setOrientation(LinearLayout.VERTICAL);reviewStage.addView(reviewItemsHost,matchWrap());
        reviewStage.addView(fieldLabel("Delivery"));destinationSummary=text("",13,MUTED,false);destinationSummary.setPadding(dp(14),dp(12),dp(14),dp(12));destinationSummary.setBackground(shape(SOFT,14,LINE));reviewStage.addView(destinationSummary,spaced());
        LinearLayout dr=new LinearLayout(this);dr.setOrientation(LinearLayout.HORIZONTAL);dr.addView(secondary("Change destinations",v->selectDestinations()),weight());dr.addView(secondary("Manage",v->manageDestinations()),weight());reviewStage.addView(dr,spaced());
        reviewStage.addView(info("Secure removes Relay's local working copies only after a required durable destination verifies preservation. Signal destinations never authorize cleanup."),spaced());
        secure=primary("Secure evidence",v->secure());secure.setTextSize(16);reviewStage.addView(secure,spaced());
        reviewStage.addView(link("Back to capture",MUTED,v->showStage(1)),matchWrap());
    }

    @Override void showStage(int which){
        stage=Math.max(0,Math.min(2,which)); if(contextStage==null)return;
        View[] stages={contextStage,captureStage,reviewStage}; for(int i=0;i<stages.length;i++){View v=stages[i];if(i==stage){if(v.getVisibility()!=View.VISIBLE){v.setAlpha(0f);v.setTranslationY(dp(8));v.setVisibility(View.VISIBLE);v.animate().alpha(1f).translationY(0).setDuration(180).setInterpolator(new AccelerateDecelerateInterpolator()).start();}}else v.setVisibility(View.GONE);}
        stepIndicator.setText(stage==0?"1  CONTEXT     2  CAPTURE     3  SECURE":stage==1?"1  ✓     2  CAPTURE     3  SECURE":"1  ✓     2  ✓     3  SECURE");updateWorkflowUi();
    }

    @Override void updateWorkflowUi(){
        super.updateWorkflowUi();
        if(status!=null){String s=status.getText().toString();boolean important=s.startsWith("TRANSFER")||s.startsWith("LOCAL ONLY")||s.startsWith("✓")||s.startsWith("!");status.setVisibility(important?View.VISIBLE:View.GONE);}
        if(collectionBlock!=null)collectionBlock.setVisibility(collectionEnabled()?View.VISIBLE:View.GONE);
        if(activeInstruction!=null){WorkflowStep s=activeWorkflowStep();StepMeta m=stepMeta(currentWorkflowProfile().id,s.label);String instruction=m.instruction.trim().isEmpty()?"Capture "+s.label:m.instruction.trim();String completion=("continuous".equals(m.behaviour)||s.max==0)?"Keep adding until you choose Done.":"Relay advances automatically when this step is complete.";activeInstruction.setText(s.label+"\n"+instruction+"\n"+completion);}
        renderReviewItems();
    }

    @Override void showSettings(){
        LinearLayout body=productRoot("Relay setup","Build procedures first. Reusable definitions sit underneath them.");
        body.addView(settingsHero());
        body.addView(groupLabel("Profiles"));body.addView(navRow("Capture Profiles","Complete procedures: context → workflow → delivery",v->manageWorkflowProfiles()),spaced());
        body.addView(groupLabel("Reusable definitions"));body.addView(navRow("Artifact Types","How an artifact is acquired: framing, orientation, guidance",v->manageArtifactTypes()),spaced());body.addView(navRow("Categories","Why a capture exists; Collection also groups members",v->manageCats()),spaced());body.addView(navRow("Entity Types","What the evidence belongs to",v->manageEntityTypes()),spaced());
        body.addView(groupLabel("Delivery"));body.addView(navRow("Destinations","Durable preservation and optional signal targets",v->manageDestinations()),spaced());
    }

    View settingsHero(){LinearLayout x=surface();x.setPadding(dp(16),dp(15),dp(16),dp(15));x.addView(text("How Relay fits together",17,INK,true));x.addView(text("Profile",12,BLUE,true));x.addView(text("Context  →  Category + Entity Type\nWorkflow → Steps → Artifact Type + completion + notes\nDelivery  →  Destinations",13,MUTED,false));LinearLayout.LayoutParams p=spaced();p.bottomMargin=dp(18);x.setLayoutParams(p);return x;}

    @Override void manageWorkflowProfiles(){
        LinearLayout body=productRoot("Capture Profiles","Choose a procedure to edit, or build one from scratch.");
        if(workflowProfiles().isEmpty()){body.addView(emptyState("No capture profiles yet","A Profile is the complete procedure Relay executes.","Build your first profile",v->editWorkflowProfile(null)),spaced());return;}
        for(WorkflowProfile p:workflowProfiles()){String detail=p.category+"  ·  "+p.steps.size()+" step"+(p.steps.size()==1?"":"s")+(p.destinationIds!=null&&!p.destinationIds.isEmpty()?"  ·  "+p.destinationIds.size()+" destination"+(p.destinationIds.size()==1?"":"s"):"");body.addView(profileCard(p,detail),spaced());}
        body.addView(primary("Build new profile",v->editWorkflowProfile(null)),spaced());
    }

    View profileCard(WorkflowProfile p,String detail){LinearLayout c=surface();c.setPadding(dp(16),dp(14),dp(12),dp(14));LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.addView(text(p.name,17,INK,true));words.addView(text(detail,12,MUTED,false));row.addView(words,weight());TextView edit=chip("Edit",false);edit.setOnClickListener(v->editWorkflowProfile(p));row.addView(edit,new LinearLayout.LayoutParams(dp(68),dp(38)));c.addView(row);return c;}

    @Override void editWorkflowProfile(WorkflowProfile original){
        final WorkflowProfile draft=original==null?WorkflowProfile.seeded("","General Capture","None",true,true,true,"sequence",new WorkflowStep("Capture",1,1,false)):original.copy();if(draft.steps.isEmpty())draft.steps.add(new WorkflowStep("Capture",1,1,false));final ArrayList<StepMeta> metas=loadMetas(draft);
        LinearLayout body=productRoot(original==null?"Build a Profile":"Edit "+draft.name,"A Profile is a procedure. Build it from top to bottom.");
        body.addView(builderSection("1","Identity","Name the procedure so an operator knows when to choose it."));EditText name=edit("Profile name, e.g. FINTRAC ID");name.setText(draft.name);body.addView(name,spaced());

        body.addView(builderSection("2","Context","Describe why this evidence exists and what it belongs to."));
        final TextView catValue=pickerValue(draft.category);body.addView(pickerRow("Category",catValue,v->chooseCategory(catValue,draft)),spaced());
        final TextView entityValue=pickerValue(draft.entityType);body.addView(pickerRow("Entity Type",entityValue,v->chooseEntity(entityValue,draft)),spaced());
        CheckBox se=modernCheck("Ask for entity / name",draft.showEntity),sm=modernCheck("Ask for matter / project",draft.showMatter),sn=modernCheck("Allow a session note",draft.showSessionNote);body.addView(se);body.addView(sm);body.addView(sn,spaced());

        body.addView(builderSection("3","Workflow","Define what happens in order. Each Step owns its obligation and uses an Artifact Type for acquisition."));LinearLayout stepHost=new LinearLayout(this);stepHost.setOrientation(LinearLayout.VERTICAL);body.addView(stepHost,matchWrap());Runnable redraw=()->renderStepTimeline(stepHost,draft,metas);redraw.run();body.addView(primary("Add workflow step",v->editStepProduct(draft,metas,-1,redraw)),spaced());

        body.addView(builderSection("4","Delivery","Choose where completed evidence should be preserved or signalled."));TextView delivery=info(draft.destinationIds==null||draft.destinationIds.isEmpty()?"No profile-specific defaults. Relay will use the operator's current destination selection.":draft.destinationIds.size()+" default destination"+(draft.destinationIds.size()==1?"":"s"));body.addView(delivery,spaced());LinearLayout dar=new LinearLayout(this);dar.setOrientation(LinearLayout.HORIZONTAL);dar.addView(secondary("Use current selection",v->{draft.destinationIds=new ArrayList<>(selectedDestinationIds);delivery.setText(draft.destinationIds.size()+" default destination"+(draft.destinationIds.size()==1?"":"s"));}),weight());dar.addView(secondary("Manage",v->manageDestinations()),weight());body.addView(dar,spaced());

        LinearLayout saveRow=new LinearLayout(this);saveRow.setOrientation(LinearLayout.HORIZONTAL);saveRow.addView(secondary("Cancel",v->manageWorkflowProfiles()),weight());saveRow.addView(primary("Save Profile",v->{String n=name.getText().toString().trim();if(n.isEmpty()){name.setError("Give this Profile a name.");return;}draft.name=n;draft.category=catValue.getText().toString();draft.entityType=entityValue.getText().toString();draft.showEntity=se.isChecked();draft.showMatter=sm.isChecked();draft.showSessionNote=sn.isChecked();draft.mode="sequence";List<WorkflowProfile> all=workflowProfiles();if(original==null)all.add(draft);else replaceWorkflowProfile(all,draft);saveWorkflowProfiles(all);saveMetas(draft.id,draft.steps,metas);refreshWorkflowProfileSpinner();workflowProfileIndex=Math.max(0,indexOfWorkflowProfile(all,draft.id));applyWorkflowProfile(workflowProfileIndex);manageWorkflowProfiles();}),weight());body.addView(saveRow,spaced());if(original!=null)body.addView(link("Remove profile",DANGER,v->confirmRemoveProfile(original)),matchWrap());
    }

    void chooseCategory(TextView target,WorkflowProfile draft){ArrayList<String> vals=new ArrayList<>(cats());showPicker("Choose Category",vals,target.getText().toString(),choice->{if("＋ Create new…".equals(choice)){promptInlineDefinition("New Category","Category name",value->{Set<String>s=customCats();s.add(value);saveCats(s);target.setText(value);});}else target.setText(choice);},true);}
    void chooseEntity(TextView target,WorkflowProfile draft){ArrayList<String> vals=new ArrayList<>(entityTypes());showPicker("Choose Entity Type",vals,target.getText().toString(),choice->{if("＋ Create new…".equals(choice)){promptInlineDefinition("New Entity Type","Entity type name",value->{Set<String>s=customEntityTypes();s.add(value);saveEntityTypes(s);target.setText(value);});}else target.setText(choice);},true);}

    void renderStepTimeline(LinearLayout host,WorkflowProfile draft,ArrayList<StepMeta> metas){host.removeAllViews();for(int i=0;i<draft.steps.size();i++){final int idx=i;WorkflowStep s=draft.steps.get(i);StepMeta m=metaAt(metas,i,s);ArtifactType a=artifactTypeForStep(draft,s.label);LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);line.setGravity(Gravity.TOP);TextView rail=text((i+1)+"",12,Color.WHITE,true);rail.setGravity(Gravity.CENTER);rail.setBackground(shape(BLUE,99,BLUE));line.addView(rail,new LinearLayout.LayoutParams(dp(30),dp(30)));LinearLayout c=surface();c.setPadding(dp(14),dp(12),dp(12),dp(12));c.addView(text(s.label,16,INK,true));c.addView(text(a.name+"  ·  "+stepSummary(s,m),12,MUTED,false));if(!m.instruction.trim().isEmpty())c.addView(text(m.instruction,12,MUTED,false));TextView edit=link("Edit step",BLUE,v->editStepProduct(draft,metas,idx,()->renderStepTimeline(host,draft,metas)));c.addView(edit);LinearLayout.LayoutParams cp=weight();cp.leftMargin=dp(10);line.addView(c,cp);host.addView(line,spaced());}}

    void editStepProduct(WorkflowProfile draft,ArrayList<StepMeta> metas,int index,Runnable redraw){
        boolean adding=index<0;WorkflowStep old=adding?new WorkflowStep("",1,1,false):draft.steps.get(index);StepMeta oldMeta=adding?new StepMeta():metaAt(metas,index,old);String oldArtifact=adding?inferArtifactType(draft,old.label):stepArtifactTypeId(draft.id,old.label,draft);
        LinearLayout body=productRoot(adding?"Add Workflow Step":"Edit Step","A Step defines evidence meaning, acquisition and completion.");EditText label=edit("Step name, e.g. Front, Back, Inspection photo");label.setText(old.label);body.addView(label,spaced());EditText instruction=edit("Instruction shown during capture");instruction.setText(oldMeta.instruction);instruction.setMinLines(2);body.addView(instruction,spaced());
        TextView artifactValue=pickerValue(artifactType(oldArtifact).name);body.addView(pickerRow("Artifact Type",artifactValue,v->chooseArtifactType(artifactValue)),spaced());body.addView(info("Artifact Type controls camera framing, orientation and acquisition guidance. This Step controls what the artifact means here."),spaced());
        CheckBox required=modernCheck("Required step",old.min>0);body.addView(required,spaced());
        LinearLayout counts=new LinearLayout(this);counts.setOrientation(LinearLayout.HORIZONTAL);EditText min=edit("Minimum");min.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);min.setText(String.valueOf(old.min));counts.addView(min,weight());EditText max=edit("Maximum · 0 = open-ended");max.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);max.setText(String.valueOf(old.max));counts.addView(max,weight());body.addView(counts,spaced());
        TextView completion=pickerValue("continuous".equals(oldMeta.behaviour)?"Operator chooses Done":"Auto-advance when complete");body.addView(pickerRow("Completion",completion,v->showPicker("Completion",new ArrayList<>(Arrays.asList("Auto-advance when complete","Operator chooses Done")),completion.getText().toString(),completion::setText,false)),spaced());
        TextView noteMode=pickerValue(noteModeLabel(oldMeta.noteMode));body.addView(pickerRow("Per-capture note",noteMode,v->showPicker("Per-capture note",new ArrayList<>(Arrays.asList("Not used","Optional","Required")),noteMode.getText().toString(),noteMode::setText,false)),spaced());
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.addView(secondary("Cancel",v->editWorkflowProfile(draft)),weight());actions.addView(primary("Save Step",v->{String lab=label.getText().toString().trim();if(lab.isEmpty()){label.setError("Give this Step a name.");return;}int mn=parseCount(min,required.isChecked()?1:0),mx=parseCount(max,0);if(!required.isChecked())mn=0;if(mx>0&&mx<mn){max.setError("Maximum cannot be less than minimum.");return;}boolean reqNote="Required".contentEquals(noteMode.getText());WorkflowStep replacement=new WorkflowStep(lab,mn,mx,reqNote);StepMeta meta=new StepMeta();meta.instruction=instruction.getText().toString().trim();meta.behaviour="Operator chooses Done".contentEquals(completion.getText())?"continuous":"advance";meta.noteMode=reqNote?"required":("Not used".contentEquals(noteMode.getText())?"none":"optional");ArtifactType selected=artifactTypeByName(artifactValue.getText().toString());if(adding){draft.steps.add(replacement);metas.add(meta);}else{draft.steps.set(index,replacement);while(metas.size()<=index)metas.add(new StepMeta());metas.set(index,meta);if(!old.label.equals(lab))removeStepArtifact(draft.id,old.label);}saveStepArtifact(draft.id,lab,selected.id);editWorkflowProfile(draft);}),weight());body.addView(actions,spaced());if(!adding)body.addView(link("Delete this step",DANGER,v->{if(draft.steps.size()<=1){toast("A Profile needs at least one Step.");return;}removeStepArtifact(draft.id,old.label);draft.steps.remove(index);if(index<metas.size())metas.remove(index);editWorkflowProfile(draft);}),matchWrap());
    }

    void chooseArtifactType(TextView target){ArrayList<String> names=new ArrayList<>();for(ArtifactType a:artifactTypes())names.add(a.name);showPicker("Choose Artifact Type",names,target.getText().toString(),choice->{if("＋ Create new…".equals(choice)){editArtifactType(null);}else target.setText(choice);},true);}
    ArtifactType artifactTypeByName(String name){for(ArtifactType a:artifactTypes())if(a.name.equals(name))return a;return artifactTypes().get(0);}

    @Override LinearLayout productRoot(String title,String subtitle){auxiliaryScreen=true;ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(8),dp(20),dp(32));sc.addView(root);LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);TextView back=link("‹",INK,v->returnToMain());back.setTextSize(34);bar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text(title,24,INK,true));if(subtitle!=null&&!subtitle.isEmpty())titles.addView(text(subtitle,12,MUTED,false));LinearLayout.LayoutParams tp=weight();tp.leftMargin=dp(5);bar.addView(titles,tp);root.addView(bar,matchWrap());LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams bp=matchWrap();bp.topMargin=dp(18);root.addView(body,bp);setContentView(sc);applyTopInset(sc);return body;}

    @Override LinearLayout card(){return surface();}
    LinearLayout surface(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(17),dp(18),dp(18));c.setBackground(shape(SURFACE,20,LINE));return c;}
    @Override Button primary(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(Color.WHITE);b.setMinHeight(dp(52));b.setBackground(shape(BLUE,16,BLUE));b.setOnClickListener(l);b.setStateListAnimator(null);return b;}
    @Override Button secondary(String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(INK);b.setMinHeight(dp(48));b.setBackground(shape(SOFT,15,LINE));b.setOnClickListener(l);b.setStateListAnimator(null);return b;}
    @Override TextView groupLabel(String s){TextView v=text(s.toUpperCase(Locale.CANADA),11,MUTED,true);v.setPadding(dp(2),dp(18),0,dp(8));return v;}
    @Override TextView label(String s){return fieldLabel(s);}
    @Override EditText edit(String h){EditText e=new EditText(this);e.setHint(h);e.setHintTextColor(Color.rgb(145,151,160));e.setTextColor(INK);e.setTextSize(15);e.setBackground(shape(Color.WHITE,14,LINE));e.setPadding(dp(14),dp(12),dp(14),dp(12));e.setMinHeight(dp(50));return e;}
    @Override LinearLayout navRow(String title,String detail,View.OnClickListener click){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(15),dp(13),dp(15));row.setBackground(shape(Color.WHITE,17,LINE));row.setOnClickListener(click);LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.addView(text(title,16,INK,true));if(detail!=null&&!detail.isEmpty())words.addView(text(detail,12,MUTED,false));row.addView(words,weight());TextView chev=text("›",28,Color.rgb(145,151,160),false);chev.setGravity(Gravity.CENTER);row.addView(chev,new LinearLayout.LayoutParams(dp(30),dp(40)));return row;}

    TextView text(String s,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);v.setLineSpacing(0,1.08f);return v;}
    TextView eyebrow(String s){TextView v=text(s,11,BLUE,true);v.setLetterSpacing(.08f);v.setPadding(0,0,0,dp(8));return v;}
    TextView fieldLabel(String s){TextView v=text(s,12,MUTED,true);v.setPadding(dp(2),dp(8),0,dp(6));return v;}
    TextView info(String s){TextView v=text(s,12,MUTED,false);v.setPadding(dp(13),dp(11),dp(13),dp(11));v.setBackground(shape(SOFT,13,LINE));return v;}
    View hero(String title,String detail){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.addView(text(title,22,INK,true));TextView d=text(detail,13,MUTED,false);d.setPadding(0,dp(4),0,dp(13));x.addView(d);return x;}
    TextView heroText(String title,String detail){TextView v=text(title+"\n"+detail,16,INK,true);v.setPadding(dp(15),dp(14),dp(15),dp(14));v.setBackground(shape(SOFT,16,LINE));return v;}
    View builderSection(String n,String title,String detail){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);TextView badge=text(n,12,Color.WHITE,true);badge.setGravity(Gravity.CENTER);badge.setBackground(shape(BLUE,99,BLUE));row.addView(badge,new LinearLayout.LayoutParams(dp(30),dp(30)));TextView t=text(title,18,INK,true);LinearLayout.LayoutParams tp=weight();tp.leftMargin=dp(10);row.addView(t,tp);x.addView(row);TextView d=text(detail,12,MUTED,false);d.setPadding(dp(40),dp(3),0,dp(10));x.addView(d);return x;}
    TextView pickerValue(String value){TextView v=text(value,15,INK,false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    View pickerRow(String label,TextView value,View.OnClickListener click){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(11),dp(12),dp(11));row.setBackground(shape(Color.WHITE,14,LINE));LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.addView(text(label,11,MUTED,true));words.addView(value);row.addView(words,weight());row.addView(text("⌄",20,MUTED,false),new LinearLayout.LayoutParams(dp(28),dp(36)));row.setOnClickListener(click);return row;}
    TextView chip(String s,boolean selected){TextView v=text(s,12,selected?Color.WHITE:BLUE,true);v.setGravity(Gravity.CENTER);v.setBackground(shape(selected?BLUE:SOFT,99,selected?BLUE:LINE));return v;}
    TextView link(String s,int color,View.OnClickListener l){TextView v=text(s,14,color,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),dp(12),dp(10),dp(12));v.setOnClickListener(l);return v;}
    CheckBox modernCheck(String s,boolean value){CheckBox c=new CheckBox(this);c.setText(s);c.setTextSize(14);c.setTextColor(INK);c.setChecked(value);c.setPadding(dp(2),dp(4),0,dp(4));return c;}
    View actionTile(String title,String detail,String badge,View.OnClickListener l){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(14),dp(14),dp(14));row.setBackground(shape(BLUE,18,BLUE));LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.addView(text(title,18,Color.WHITE,true));words.addView(text(detail,12,Color.rgb(225,233,250),false));row.addView(words,weight());TextView b=text("›",30,Color.WHITE,false);row.addView(b,new LinearLayout.LayoutParams(dp(28),dp(42)));row.setOnClickListener(l);return row;}
    View emptyState(String title,String detail,String action,View.OnClickListener l){LinearLayout x=surface();x.setGravity(Gravity.CENTER);TextView t=text(title,19,INK,true);t.setGravity(Gravity.CENTER);x.addView(t);TextView d=text(detail,13,MUTED,false);d.setGravity(Gravity.CENTER);d.setPadding(dp(8),dp(7),dp(8),dp(14));x.addView(d);x.addView(primary(action,l),matchWrap());return x;}
    GradientDrawable shape(int fill,int radius,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    void styleSpinner(Spinner s){s.setPadding(dp(12),dp(8),dp(12),dp(8));s.setBackground(shape(Color.WHITE,14,LINE));s.setMinimumHeight(dp(50));}

    interface ChoiceHandler{void choose(String value);} interface ValueHandler{void accept(String value);}
    void showPicker(String title,ArrayList<String> values,String current,ChoiceHandler handler,boolean allowCreate){ArrayList<String> rows=new ArrayList<>(values);if(allowCreate)rows.add("＋ Create new…");String[] a=rows.toArray(new String[0]);new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(a,Math.max(-1,rows.indexOf(current)),(d,w)->{String choice=a[w];d.dismiss();handler.choose(choice);}).setNegativeButton("Cancel",null).show();}
    void promptInlineDefinition(String title,String hint,ValueHandler handler){EditText e=edit(hint);LinearLayout box=new LinearLayout(this);box.setPadding(dp(18),dp(6),dp(18),0);box.addView(e,matchWrap());new AlertDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("Create",(d,w)->{String v=e.getText().toString().trim();if(!v.isEmpty())handler.accept(v);}).setNegativeButton("Cancel",null).show();}
}
