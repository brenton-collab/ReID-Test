package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Relay Capture v1.2.4 product UI pass.
 * Functional capture/workflow/preservation behavior remains inherited from
 * v1.2.2/v1.2.3. This layer replaces utility-style navigation and top-level
 * settings/history dialogs with coherent full-screen product surfaces.
 */
public class MainActivityV124 extends MainActivityV123 {
    View mainScreen;
    boolean auxiliaryScreen = false;

    @Override void build() {
        ensureWorkflowProfilesV12();
        int p = dp(18);
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackgroundColor(Color.rgb(247,248,250));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, dp(10), p, dp(28));
        sc.addView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton historyButton = iconButton(ca.brentzinck.fintracid.R.drawable.ic_history_relay, "History", v -> showHistory());
        toolbar.addView(historyButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = new TextView(this);
        title.setText("Relay"); title.setTextSize(29); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(27,30,35)); title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tp = weight(); tp.leftMargin = dp(8); toolbar.addView(title, tp);
        ImageButton settingsButton = iconButton(ca.brentzinck.fintracid.R.drawable.ic_settings_relay, "Settings", v -> showSettings());
        toolbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(toolbar, matchWrap());

        stepIndicator = new TextView(this);
        stepIndicator.setTextSize(13); stepIndicator.setTextColor(Color.rgb(92,99,112));
        stepIndicator.setGravity(Gravity.CENTER); stepIndicator.setPadding(0,dp(8),0,dp(12));
        root.addView(stepIndicator, matchWrap());

        status = new TextView(this);
        status.setTextSize(13); status.setTextColor(Color.rgb(73,80,91));
        status.setPadding(dp(12),dp(9),dp(12),dp(9));
        status.setBackground(roundRect(Color.WHITE,dp(10),Color.rgb(228,231,236)));
        status.setVisibility(View.GONE);
        root.addView(status, matchWrap());

        wizardHost = new LinearLayout(this); wizardHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = matchWrap(); wp.topMargin = dp(10); root.addView(wizardHost, wp);
        buildContextStage(); buildCaptureStage(); buildReviewStage();
        wizardHost.addView(contextStage, matchWrap()); wizardHost.addView(captureStage, matchWrap()); wizardHost.addView(reviewStage, matchWrap());

        mainScreen = sc;
        auxiliaryScreen = false;
        setContentView(sc);
    }

    ImageButton iconButton(int drawable, String description, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(drawable); b.setContentDescription(description);
        b.setBackground(roundRect(Color.TRANSPARENT, dp(24), Color.TRANSPARENT));
        b.setPadding(dp(12),dp(12),dp(12),dp(12)); b.setOnClickListener(click);
        return b;
    }

    @Override void updateWorkflowUi() {
        super.updateWorkflowUi();
        if (status != null) {
            String s = status.getText().toString();
            boolean important = s.startsWith("TRANSFER") || s.startsWith("LOCAL ONLY") || s.startsWith("✓") || s.startsWith("!");
            status.setVisibility(important ? View.VISIBLE : View.GONE);
        }
    }

    LinearLayout productRoot(String title, String subtitle) {
        auxiliaryScreen = true;
        ScrollView sc = new ScrollView(this); sc.setFillViewport(true); sc.setBackgroundColor(Color.rgb(247,248,250));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(10),dp(18),dp(28)); sc.addView(root);
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = new TextView(this); back.setText("‹"); back.setTextSize(38); back.setGravity(Gravity.CENTER); back.setTextColor(Color.rgb(48,55,67)); back.setContentDescription("Back"); back.setOnClickListener(v -> returnToMain());
        bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout titles = new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL);
        TextView h = new TextView(this); h.setText(title); h.setTextSize(24); h.setTypeface(Typeface.DEFAULT_BOLD); h.setTextColor(Color.rgb(27,30,35)); titles.addView(h);
        if (subtitle != null && !subtitle.isEmpty()) { TextView st = new TextView(this); st.setText(subtitle); st.setTextSize(12); st.setTextColor(Color.rgb(102,108,118)); titles.addView(st); }
        LinearLayout.LayoutParams t = weight(); t.leftMargin=dp(4); bar.addView(titles,t); root.addView(bar,matchWrap());
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams bp=matchWrap(); bp.topMargin=dp(14); root.addView(body,bp);
        setContentView(sc);
        return body;
    }

    void returnToMain() {
        auxiliaryScreen = false;
        if (mainScreen != null) { setContentView(mainScreen); updateWorkflowUi(); showStage(stage); }
        else build();
    }

    @Override public void onBackPressed() {
        if (auxiliaryScreen) { returnToMain(); return; }
        if (stage > 0) { showStage(stage-1); return; }
        super.onBackPressed();
    }

    TextView groupLabel(String text) {
        TextView v = new TextView(this); v.setText(text.toUpperCase(Locale.CANADA)); v.setTextSize(11); v.setTypeface(Typeface.DEFAULT_BOLD); v.setTextColor(Color.rgb(105,111,122)); v.setPadding(dp(4),dp(12),dp(4),dp(7)); return v;
    }

    LinearLayout navRow(String title, String detail, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16),dp(13),dp(12),dp(13)); row.setBackground(roundRect(Color.WHITE,dp(14),Color.rgb(229,232,237))); row.setOnClickListener(click);
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
        TextView a = new TextView(this); a.setText(title); a.setTextSize(16); a.setTypeface(Typeface.DEFAULT_BOLD); a.setTextColor(Color.rgb(37,42,50)); words.addView(a);
        if(detail!=null&&!detail.isEmpty()){TextView b=new TextView(this);b.setText(detail);b.setTextSize(12);b.setTextColor(Color.rgb(103,109,120));b.setPadding(0,dp(2),0,0);words.addView(b);} row.addView(words,weight());
        TextView chevron = new TextView(this); chevron.setText("›"); chevron.setTextSize(28); chevron.setTextColor(Color.rgb(135,141,151)); chevron.setGravity(Gravity.CENTER); row.addView(chevron,new LinearLayout.LayoutParams(dp(32),dp(42)));
        return row;
    }

    LinearLayout.LayoutParams spaced() { LinearLayout.LayoutParams p=matchWrap(); p.bottomMargin=dp(9); return p; }

    @Override void showSettings() {
        LinearLayout body = productRoot("Settings","Configure how Relay captures and preserves");
        body.addView(groupLabel("Capture"));
        body.addView(navRow("Capture Profiles", workflowProfiles().size()+" profiles · workflow + defaults", v -> manageWorkflowProfiles()), spaced());
        body.addView(navRow("Categories", cats().size()+" available labels", v -> manageCats()), spaced());
        body.addView(navRow("Entity Types", entityTypes().size()+" contextual types", v -> manageEntityTypes()), spaced());
        body.addView(groupLabel("Delivery"));
        body.addView(navRow("Destinations", destinations().size()+" configured preservation targets", v -> manageDestinations()), spaced());
        TextView foot=new TextView(this);foot.setText("Relay deletes local capture data only after a required durable destination verifies.");foot.setTextSize(12);foot.setTextColor(Color.rgb(110,116,126));foot.setPadding(dp(5),dp(16),dp(5),0);body.addView(foot);
    }

    @Override void manageWorkflowProfiles() {
        LinearLayout body=productRoot("Capture Profiles","Define the shape of a capture session");
        for(WorkflowProfile p:workflowProfiles()){
            String detail=p.workflowSummary(); if(p.destinationIds!=null&&!p.destinationIds.isEmpty()) detail += " · "+p.destinationIds.size()+" destination"+(p.destinationIds.size()==1?"":"s");
            body.addView(navRow(p.name,detail,v->editWorkflowProfile(p)),spaced());
        }
        Button add=primary("＋  New profile",v->editWorkflowProfile(null)); LinearLayout.LayoutParams ap=matchWrap();ap.topMargin=dp(8);body.addView(add,ap);
    }

    @Override void manageDestinations() {
        LinearLayout body=productRoot("Destinations","Where Relay is allowed to preserve captures");
        for(Destination d:destinations()){
            String detail=(d.required?"Required":"Optional")+" · "+(d.durable?"Durable":"Signal")+(d.enabled?"":" · Disabled");
            body.addView(navRow(d.label,detail,v->editDestination(d)),spaced());
        }
        Button add=primary("＋  Add Drive destination",v->addDriveDestination()); LinearLayout.LayoutParams ap=matchWrap();ap.topMargin=dp(8);body.addView(add,ap);
    }

    @Override void manageCats() {
        LinearLayout body=productRoot("Categories","Vocabulary used by Capture Profiles");
        for(String c:cats()){
            boolean builtIn=SEEDED_CATEGORIES.contains(c);
            body.addView(navRow(c,builtIn?"Built in":"Custom · tap to edit",v->{ if(builtIn) toast("Built-in categories remain available."); else editCategoryProduct(c); }),spaced());
        }
        Button add=primary("＋  Add category",v->addCategoryProduct()); LinearLayout.LayoutParams ap=matchWrap();ap.topMargin=dp(8);body.addView(add,ap);
    }

    void addCategoryProduct(){
        EditText x=edit("Category name");
        new AlertDialog.Builder(this).setTitle("New category").setView(x).setPositiveButton("Add",(d,w)->{String v=x.getText().toString().trim();if(!v.isEmpty()){Set<String>s=customCats();s.add(v);saveCats(s);manageCats();}}).setNegativeButton("Cancel",null).show();
    }
    void editCategoryProduct(String old){
        EditText x=edit("Category name");x.setText(old);
        new AlertDialog.Builder(this).setTitle("Edit category").setView(x).setPositiveButton("Save",(d,w)->{Set<String>s=customCats();s.remove(old);String v=x.getText().toString().trim();if(!v.isEmpty())s.add(v);saveCats(s);manageCats();}).setNeutralButton("Delete",(d,w)->{Set<String>s=customCats();s.remove(old);saveCats(s);manageCats();}).setNegativeButton("Cancel",null).show();
    }

    @Override void manageEntityTypes(){
        LinearLayout body=productRoot("Entity Types","Context labels profiles can use");
        List<String> all=entityTypes();
        for(String e:all){boolean builtIn=SEEDED_ENTITY_TYPES.contains(e);body.addView(navRow(e,builtIn?"Built in":"Custom · tap to edit",v->{if(builtIn)toast("Built-in entity types remain available.");else editEntityProduct(e);}),spaced());}
        Button add=primary("＋  Add entity type",v->addEntityProduct());LinearLayout.LayoutParams ap=matchWrap();ap.topMargin=dp(8);body.addView(add,ap);
    }
    void addEntityProduct(){EditText x=edit("Entity type");new AlertDialog.Builder(this).setTitle("New entity type").setView(x).setPositiveButton("Add",(d,w)->{String v=x.getText().toString().trim();if(!v.isEmpty()){Set<String>s=customEntityTypes();s.add(v);saveEntityTypes(s);manageEntityTypes();}}).setNegativeButton("Cancel",null).show();}
    void editEntityProduct(String old){EditText x=edit("Entity type");x.setText(old);new AlertDialog.Builder(this).setTitle("Edit entity type").setView(x).setPositiveButton("Save",(d,w)->{Set<String>s=customEntityTypes();s.remove(old);String v=x.getText().toString().trim();if(!v.isEmpty())s.add(v);saveEntityTypes(s);manageEntityTypes();}).setNeutralButton("Delete",(d,w)->{Set<String>s=customEntityTypes();s.remove(old);saveEntityTypes(s);manageEntityTypes();}).setNegativeButton("Cancel",null).show();}

    @Override void showHistory(){
        LinearLayout body=productRoot("History","Receipts only · captured documents are not retained here");
        JSONArray h=history();
        if(h.length()==0){TextView empty=new TextView(this);empty.setText("No capture receipts yet.");empty.setTextSize(15);empty.setTextColor(Color.rgb(95,101,111));empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(60),0,0);body.addView(empty);return;}
        int n=Math.min(100,h.length()); SimpleDateFormat fmt=new SimpleDateFormat("MMM d · h:mm a",Locale.CANADA);
        for(int i=0;i<n;i++){
            JSONObject x=h.optJSONObject(i); if(x==null)continue; String outcome=x.optString("outcome"); String entity=x.optString("entity"); String matter=x.optString("matter"); String context=!entity.isEmpty()?entity:(!matter.isEmpty()?matter:"No context");
            String detail=(outcome.startsWith("secured")?"Secured":"Not secured")+" · "+context+" · "+fmt.format(new Date(x.optLong("timestamp")));
            body.addView(navRow(x.optString("profile","Capture"),detail,v->showHistoryReceipt(x)),spaced());
        }
    }

    void showHistoryReceipt(JSONObject x){
        LinearLayout body=productRoot("Capture Receipt",x.optString("outcome").startsWith("secured")?"Preservation verified":"Preservation incomplete");
        addReceipt(body,"Profile",x.optString("profile")); addReceipt(body,"Category",x.optString("category")); addReceipt(body,"Entity",x.optString("entity")); addReceipt(body,"Matter",x.optString("matter")); addReceipt(body,"Items",String.valueOf(x.optInt("item_count"))); addReceipt(body,"Capture ID",x.optString("capture_id"));
        JSONArray d=x.optJSONArray("destinations"); if(d!=null)addReceipt(body,"Destinations",joinJson(d));
    }
    void addReceipt(LinearLayout body,String label,String value){if(value==null||value.isEmpty())return;TextView l=groupLabel(label);body.addView(l);TextView v=new TextView(this);v.setText(value);v.setTextSize(15);v.setTextColor(Color.rgb(42,47,55));v.setPadding(dp(14),dp(12),dp(14),dp(12));v.setBackground(roundRect(Color.WHITE,dp(12),Color.rgb(229,232,237)));body.addView(v,spaced());}

    @Override void showMainMenu(View anchor){ showHistory(); }
}
