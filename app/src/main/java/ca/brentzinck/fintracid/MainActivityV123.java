package ca.brentzinck.fintracid;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;

import java.util.*;

/** Relay v1.2.3 UX pass. Functional preservation/session machinery remains inherited. */
public class MainActivityV123 extends MainActivityV121 {
    LinearLayout typeBlock;

    @Override void build() {
        ensureWorkflowProfilesV12();
        int p = dp(18);
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Color.rgb(247,248,250));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, dp(12), p, dp(28));
        sc.addView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        Button menu = quietIcon("☰", v -> showMainMenu(v));
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(50), dp(50)));
        TextView title = new TextView(this);
        title.setText("Relay"); title.setTextSize(28); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(28,30,34)); title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, weight());
        Button gear = quietIcon("⚙", v -> showSettings());
        toolbar.addView(gear, new LinearLayout.LayoutParams(dp(50), dp(50)));
        root.addView(toolbar, matchWrap());

        stepIndicator = new TextView(this);
        stepIndicator.setTextSize(13); stepIndicator.setTextColor(Color.rgb(92,99,112));
        stepIndicator.setGravity(Gravity.CENTER); stepIndicator.setPadding(0,dp(10),0,dp(14));
        root.addView(stepIndicator, matchWrap());

        status = new TextView(this);
        status.setTextSize(13); status.setTextColor(Color.rgb(73,80,91));
        status.setPadding(dp(12),dp(9),dp(12),dp(9));
        status.setBackground(roundRect(Color.WHITE,dp(10),Color.rgb(228,231,236)));
        root.addView(status, matchWrap());

        wizardHost = new LinearLayout(this); wizardHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = matchWrap(); wp.topMargin = dp(12); root.addView(wizardHost, wp);
        buildContextStage(); buildCaptureStage(); buildReviewStage();
        wizardHost.addView(contextStage, matchWrap()); wizardHost.addView(captureStage, matchWrap()); wizardHost.addView(reviewStage, matchWrap());
        setContentView(sc);
    }

    Button quietIcon(String text, View.OnClickListener l) {
        Button b = secondary(text,l); b.setTextSize(20); b.setPadding(0,0,0,0); return b;
    }

    @Override void buildContextStage() {
        contextStage = card();
        TextView prompt = new TextView(this); prompt.setText("What are you capturing?"); prompt.setTextSize(21); prompt.setTypeface(Typeface.DEFAULT_BOLD); prompt.setTextColor(Color.rgb(30,33,38)); prompt.setPadding(0,0,0,dp(8)); contextStage.addView(prompt);

        profile = new Spinner(this); refreshWorkflowProfileSpinner();
        profile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id){ if(position != workflowProfileIndex) applyWorkflowProfile(position); }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        contextStage.addView(profile);
        profileSummary = new TextView(this); profileSummary.setTextSize(12); profileSummary.setTextColor(Color.rgb(105,110,120)); profileSummary.setPadding(dp(4),dp(4),0,dp(10)); contextStage.addView(profileSummary);

        entityBlock = new LinearLayout(this); entityBlock.setOrientation(LinearLayout.VERTICAL);
        TextView about = label("What is this about?"); entityBlock.addView(about);
        typeBlock = new LinearLayout(this); typeBlock.setOrientation(LinearLayout.VERTICAL);
        type = new Spinner(this); type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, entityTypes())); typeBlock.addView(type); entityBlock.addView(typeBlock);
        entity = edit("Name or identifier"); LinearLayout.LayoutParams ep=matchWrap(); ep.topMargin=dp(6); entity.setLayoutParams(ep); entityBlock.addView(entity);
        contextStage.addView(entityBlock);

        matterBlock = new LinearLayout(this); matterBlock.setOrientation(LinearLayout.VERTICAL);
        matterBlock.addView(label("Matter (optional)")); matter = edit("Transaction, property, project, or reference"); matterBlock.addView(matter); contextStage.addView(matterBlock);

        noteBlock = new LinearLayout(this); noteBlock.setOrientation(LinearLayout.VERTICAL);
        noteBlock.addView(label("Note (optional)")); note = edit("Add context for this capture session"); note.setMinLines(1); noteBlock.addView(note); contextStage.addView(noteBlock);

        Button next = primary("Continue", v -> { applyContextVisibility(); showStage(1); updateWorkflowUi(); });
        LinearLayout.LayoutParams np=matchWrap(); np.topMargin=dp(16); contextStage.addView(next,np);
    }

    @Override void buildCaptureStage() {
        captureStage = card();
        TextView prompt = new TextView(this); prompt.setText("Capture"); prompt.setTextSize(21); prompt.setTypeface(Typeface.DEFAULT_BOLD); prompt.setTextColor(Color.rgb(30,33,38)); captureStage.addView(prompt);
        workflowSummary = new TextView(this); workflowSummary.setTextSize(13); workflowSummary.setTextColor(Color.rgb(88,94,104)); workflowSummary.setPadding(0,dp(4),0,dp(10)); captureStage.addView(workflowSummary);
        workflowStepSpinner = new Spinner(this); captureStage.addView(workflowStepSpinner);

        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(primary("Camera",v->startActivityForResult(new android.content.Intent(this,CaptureActivity.class),CAMERA)),weight());
        row.addView(primary("Screenshot",v->startActivityForResult(new android.content.Intent(this,ScreenshotActivity.class),SCREENSHOT)),weight());
        row.addView(primary("Import",v->chooseImport()),weight()); captureStage.addView(row);
        staged=new TextView(this); staged.setTextSize(13); staged.setTextColor(Color.rgb(80,86,96)); staged.setPadding(0,dp(12),0,dp(6)); captureStage.addView(staged);
        captureStage.addView(secondary("Review items",v->reviewWorkflowItems()),matchWrap());
        LinearLayout nav=new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.addView(secondary("Back",v->showStage(0)),weight()); nav.addView(primary("Done",v->{String problem=workflowValidationProblem();if(problem!=null){toast(problem);return;}showStage(2);updateWorkflowUi();}),weight()); LinearLayout.LayoutParams n=matchWrap();n.topMargin=dp(14);captureStage.addView(nav,n);
    }

    @Override void buildReviewStage() {
        reviewStage=card(); TextView prompt=new TextView(this); prompt.setText("Ready to preserve"); prompt.setTextSize(21); prompt.setTypeface(Typeface.DEFAULT_BOLD); prompt.setTextColor(Color.rgb(30,33,38)); reviewStage.addView(prompt);
        reviewSummary=new TextView(this); reviewSummary.setTextSize(14); reviewSummary.setTextColor(Color.rgb(65,70,78)); reviewSummary.setPadding(0,dp(6),0,dp(12)); reviewStage.addView(reviewSummary);
        destinationSummary=new TextView(this); destinationSummary.setTextSize(14); destinationSummary.setTextColor(Color.rgb(65,70,78)); destinationSummary.setPadding(0,0,0,dp(8)); reviewStage.addView(destinationSummary);
        reviewStage.addView(secondary("Change destinations",v->selectDestinations()),matchWrap());
        secure=primary("Secure",v->secure()); secure.setTextSize(17); LinearLayout.LayoutParams sp=matchWrap();sp.topMargin=dp(14);reviewStage.addView(secure,sp);
        Button back=secondary("Back to capture",v->showStage(1)); LinearLayout.LayoutParams bp=matchWrap();bp.topMargin=dp(6);reviewStage.addView(back,bp);
    }

    @Override void showStage(int which) {
        stage=Math.max(0,Math.min(2,which)); if(contextStage==null)return;
        contextStage.setVisibility(stage==0?View.VISIBLE:View.GONE); captureStage.setVisibility(stage==1?View.VISIBLE:View.GONE); reviewStage.setVisibility(stage==2?View.VISIBLE:View.GONE);
        if(stepIndicator!=null) stepIndicator.setText(stage==0?"●  ○  ○    Context":stage==1?"●  ●  ○    Capture":"●  ●  ●    Secure");
        updateWorkflowUi();
    }

    @Override void applyContextVisibility() {
        WorkflowProfile p=currentWorkflowProfile();
        if(entityBlock!=null) entityBlock.setVisibility(p.showEntity?View.VISIBLE:View.GONE);
        if(matterBlock!=null) matterBlock.setVisibility(p.showMatter?View.VISIBLE:View.GONE);
        if(noteBlock!=null) noteBlock.setVisibility(p.showSessionNote?View.VISIBLE:View.GONE);
        if(typeBlock!=null) {
            boolean fixed=p.entityType!=null && !p.entityType.trim().isEmpty() && !"None".equalsIgnoreCase(p.entityType);
            typeBlock.setVisibility(fixed?View.GONE:View.VISIBLE);
        }
    }

    @Override void manageCats() {
        List<String> all=cats(); ArrayList<String> rows=new ArrayList<>(all); rows.add("＋ Add category");
        new AlertDialog.Builder(this).setTitle("Categories").setItems(rows.toArray(new String[0]),(d,w)->{
            if(w==all.size()){ addCat(); return; }
            String old=all.get(w); if(SEEDED_CATEGORIES.contains(old)){ toast("Built-in categories remain available."); return; }
            EditText x=new EditText(this);x.setText(old);
            new AlertDialog.Builder(this).setTitle("Edit category").setView(x).setPositiveButton("Save",(dd,ww)->{Set<String>s=customCats();s.remove(old);String v=x.getText().toString().trim();if(!v.isEmpty())s.add(v);saveCats(s);}).setNeutralButton("Remove",(dd,ww)->{Set<String>s=customCats();s.remove(old);saveCats(s);}).setNegativeButton("Cancel",null).show();
        }).setNegativeButton("Done",null).show();
    }
}
