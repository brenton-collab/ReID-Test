package ca.brentzinck.fintracid;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Relay v1.7.1: predictable auxiliary navigation + visually legible workflow authoring. */
public class MainActivityV171 extends MainActivityV170 {
    final ArrayDeque<Runnable> relayBackStack=new ArrayDeque<>(); boolean navigatingBack=false;

    @Override LinearLayout productRoot(String title,String subtitle){
        auxiliaryScreen=true;ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);sc.setClipToPadding(false);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(8),dp(20),dp(32));sc.addView(root);
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=text("‹",36,INK,false);back.setGravity(Gravity.CENTER);back.setContentDescription("Back");back.setOnClickListener(v->relayBack());bar.addView(back,new LinearLayout.LayoutParams(dp(52),dp(52)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);titles.addView(text(title,24,INK,true));if(subtitle!=null&&!subtitle.isEmpty())titles.addView(text(subtitle,12,MUTED,false));LinearLayout.LayoutParams tp=weight();tp.leftMargin=dp(3);bar.addView(titles,tp);root.addView(bar,matchWrap());
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams bp=matchWrap();bp.topMargin=dp(14);root.addView(body,bp);setContentView(sc);applyTopInset(sc);return body;
    }
    void pushBack(Runnable r){if(!navigatingBack&&r!=null)relayBackStack.push(r);}void relayBack(){if(!relayBackStack.isEmpty()){Runnable r=relayBackStack.pop();navigatingBack=true;try{r.run();}finally{navigatingBack=false;}return;}returnToMain();}
    @Override public void onBackPressed(){if(auxiliaryScreen){relayBack();return;}if(stage==2){showStage(1);return;}if(stage==1){showStage(0);return;}super.onBackPressed();}

    @Override void showSettings(){pushBack(this::returnToMain);LinearLayout body=productRoot("Relay setup","");body.addView(groupLabel("Profiles"));body.addView(navRow("Capture Profiles","Context · workflow · delivery",v->manageWorkflowProfiles()),spaced());body.addView(groupLabel("Definitions"));body.addView(navRow("Artifact Types","Acquisition and camera guidance",v->manageArtifactTypes()),spaced());body.addView(navRow("Categories","Capture purpose and collections",v->manageCats()),spaced());body.addView(navRow("Entity Types","What evidence belongs to",v->manageEntityTypes()),spaced());body.addView(groupLabel("Delivery"));body.addView(navRow("Destinations","Drive · Email · web",v->manageDestinations()),spaced());TextView signature=text("Relay Capture v1.7.1  ·  © 2026 Brenton Zinck\nAll rights reserved.",11,Color.rgb(125,131,141),false);signature.setGravity(Gravity.CENTER);signature.setPadding(dp(8),dp(28),dp(8),dp(10));body.addView(signature,matchWrap());}
    @Override void manageWorkflowProfiles(){pushBack(this::showSettings);super.manageWorkflowProfiles();}
    @Override void manageArtifactTypes(){pushBack(this::showSettings);super.manageArtifactTypes();}
    @Override void manageCats(){pushBack(this::showSettings);super.manageCats();}
    @Override void manageEntityTypes(){pushBack(this::showSettings);super.manageEntityTypes();}
    @Override void manageDestinations(){pushBack(this::showSettings);super.manageDestinations();}
    @Override void showHistory(){pushBack(this::returnToMain);super.showHistory();}
    @Override void editWorkflowProfile(WorkflowProfile p){pushBack(this::manageWorkflowProfiles);super.editWorkflowProfile(p);}
    @Override void editArtifactType(ArtifactType a){pushBack(this::manageArtifactTypes);super.editArtifactType(a);}
    @Override void editDestination(Destination d){pushBack(this::manageDestinations);super.editDestination(d);}
    @Override void editEmail(Destination d){pushBack(this::manageDestinations);super.editEmail(d);}
    @Override void editStepProduct(WorkflowProfile d,ArrayList<StepMeta> m,int i,Runnable r){pushBack(()->editWorkflowProfile(d));super.editStepProduct(d,m,i,r);}

    @Override void renderStepTimeline(LinearLayout host,WorkflowProfile draft,ArrayList<StepMeta> metas){host.removeAllViews();for(int i=0;i<draft.steps.size();i++){final int idx=i;WorkflowStep s=draft.steps.get(i);StepMeta m=metaAt(metas,i,s);ArtifactType a=artifactTypeForStep(draft,s.label);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.TOP);LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.VERTICAL);rail.setGravity(Gravity.CENTER_HORIZONTAL);TextView number=text(String.format(Locale.CANADA,"%02d",i+1),13,Color.WHITE,true);number.setGravity(Gravity.CENTER);number.setBackground(shape(BLUE,99,BLUE));rail.addView(number,new LinearLayout.LayoutParams(dp(38),dp(38)));if(i<draft.steps.size()-1){View connector=new View(this);connector.setBackgroundColor(LINE);rail.addView(connector,new LinearLayout.LayoutParams(dp(2),dp(34)));}row.addView(rail,new LinearLayout.LayoutParams(dp(42),-2));LinearLayout card=surface();card.setPadding(dp(16),dp(14),dp(14),dp(14));LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(text(s.label,17,INK,true),weight());TextView edit=chip("Edit",false);edit.setOnClickListener(v->editStepProduct(draft,metas,idx,()->renderStepTimeline(host,draft,metas)));head.addView(edit,new LinearLayout.LayoutParams(dp(64),dp(36)));card.addView(head);LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);chips.setPadding(0,dp(9),0,0);chips.addView(chip(a.name,false));chips.addView(chip(s.min>0?"Required":"Optional",false));chips.addView(chip(stepCompact(s,m),false));card.addView(chips);if(!m.instruction.trim().isEmpty()){TextView inst=text(m.instruction,12,MUTED,false);inst.setMaxLines(2);inst.setPadding(0,dp(9),0,0);card.addView(inst);}LinearLayout.LayoutParams cp=weight();cp.leftMargin=dp(10);row.addView(card,cp);LinearLayout.LayoutParams rp=spaced();rp.bottomMargin=dp(8);host.addView(row,rp);}}
    String stepCompact(WorkflowStep s,StepMeta m){if("continuous".equals(m.behaviour)||s.max==0)return"Until Done";if(s.min==1&&s.max==1)return"1 capture";if(s.max>0&&s.min==s.max)return s.max+" captures";return s.max>0?s.min+"–"+s.max+" captures":s.min+"+ captures";}
}
