package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Relay Capture v1.4.1 review UX + contextual guidance pass. */
public class MainActivityV141 extends MainActivityV14 {
    LinearLayout reviewItemsHost;

    @Override void buildReviewStage() {
        reviewStage = card();
        reviewSummary = new TextView(this);
        reviewSummary.setTextSize(14); reviewSummary.setTextColor(Color.rgb(60,65,72));
        reviewSummary.setPadding(0,0,0,dp(10)); reviewStage.addView(reviewSummary);

        TextView explain = helper("Check each item before securing. Tap a thumbnail to inspect it; use Edit to change its workflow step or attached note.");
        reviewStage.addView(explain, spaced());

        reviewItemsHost = new LinearLayout(this); reviewItemsHost.setOrientation(LinearLayout.VERTICAL);
        reviewStage.addView(reviewItemsHost, matchWrap());

        reviewStage.addView(groupLabel("Delivery"));
        destinationSummary = new TextView(this); destinationSummary.setTextSize(14); destinationSummary.setPadding(0,dp(4),0,dp(8)); reviewStage.addView(destinationSummary);
        LinearLayout drow = new LinearLayout(this); drow.setOrientation(LinearLayout.HORIZONTAL);
        drow.addView(secondary("Select", v -> selectDestinations()), weight()); drow.addView(secondary("Manage", v -> manageDestinations()), weight()); reviewStage.addView(drow);

        TextView preservation = helper("Secure preserves every staged artifact and its metadata to the required durable destination before Relay may remove its local working copy. Signal destinations are emitted separately.");
        LinearLayout.LayoutParams pp=matchWrap(); pp.topMargin=dp(12); reviewStage.addView(preservation,pp);

        secure = primary("SECURE", v -> secure()); secure.setTextSize(16);
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(secondary("Back", v -> showStage(1)), weight()); nav.addView(secure, weight());
        LinearLayout.LayoutParams n = matchWrap(); n.topMargin = dp(14); reviewStage.addView(nav, n);
    }

    TextView helper(String text) {
        TextView v=new TextView(this); v.setText(text); v.setTextSize(12); v.setTextColor(Color.rgb(96,103,114));
        v.setLineSpacing(0,1.08f); v.setPadding(dp(12),dp(10),dp(12),dp(10));
        v.setBackground(roundRect(Color.rgb(247,249,252),dp(12),Color.rgb(226,230,237))); return v;
    }

    @Override void updateWorkflowUi() {
        super.updateWorkflowUi();
        renderReviewItems();
    }

    void renderReviewItems() {
        if(reviewItemsHost==null)return;
        reviewItemsHost.removeAllViews();
        if(items.isEmpty()) { TextView e=helper("No items staged yet."); reviewItemsHost.addView(e,spaced()); return; }
        for(Item it:new ArrayList<>(items)) reviewItemsHost.addView(artifactCard(it),spaced());
    }

    View artifactCard(Item it) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10),dp(10),dp(8),dp(10)); row.setBackground(roundRect(Color.WHITE,dp(16),Color.rgb(226,230,236)));

        ImageView thumb=new ImageView(this); thumb.setScaleType(ImageView.ScaleType.CENTER_CROP); thumb.setBackground(roundRect(Color.rgb(241,244,248),dp(11),Color.rgb(226,230,236)));
        setArtifactVisual(thumb,it); thumb.setContentDescription("Preview "+it.name); thumb.setOnClickListener(v->previewArtifact(it));
        row.addView(thumb,new LinearLayout.LayoutParams(dp(64),dp(64)));

        LinearLayout words=new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(12),0,dp(6),0);
        TextView name=new TextView(this); name.setText(it.name); name.setTextSize(14); name.setTypeface(Typeface.DEFAULT_BOLD); name.setTextColor(Color.rgb(36,41,49)); name.setMaxLines(1); name.setEllipsize(android.text.TextUtils.TruncateAt.END); words.addView(name);
        String step=itemSteps.getOrDefault(it,"Capture");
        TextView meta=new TextView(this); meta.setText(sourceLabel(it.source)+" · "+step); meta.setTextSize(11); meta.setTextColor(Color.rgb(105,111,122)); meta.setPadding(0,dp(3),0,0); words.addView(meta);
        String note=itemNotes.getOrDefault(it,"").trim();
        if(!note.isEmpty()){TextView nv=new TextView(this);nv.setText(note);nv.setTextSize(12);nv.setTextColor(Color.rgb(75,82,92));nv.setMaxLines(2);nv.setEllipsize(android.text.TextUtils.TruncateAt.END);nv.setPadding(0,dp(4),0,0);words.addView(nv);}
        row.addView(words,weight());

        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.VERTICAL); actions.setGravity(Gravity.CENTER);
        TextView edit=miniAction("Edit",false); edit.setOnClickListener(v->editArtifactProduct(it)); actions.addView(edit,new LinearLayout.LayoutParams(dp(58),dp(34)));
        TextView del=miniAction("Delete",true); del.setOnClickListener(v->confirmDeleteArtifact(it)); LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(58),dp(34));dpv.topMargin=dp(3);actions.addView(del,dpv);
        row.addView(actions);
        return row;
    }

    TextView miniAction(String text, boolean destructive){TextView v=new TextView(this);v.setText(text);v.setTextSize(11);v.setGravity(Gravity.CENTER);v.setTextColor(destructive?Color.rgb(174,55,55):Color.rgb(54,76,113));v.setBackground(roundRect(Color.rgb(247,248,250),dp(12),Color.rgb(228,231,236)));return v;}

    void setArtifactVisual(ImageView view,Item it){
        if(isImage(it)){
            try{
                Bitmap b=null;
                if(it.file!=null) b=decodeSampled(it.file.getAbsolutePath(),160,160);
                else if(it.uri!=null){try(InputStream in=getContentResolver().openInputStream(it.uri)){if(in!=null)b=BitmapFactory.decodeStream(in);}}
                if(b!=null){view.setImageBitmap(b);return;}
            }catch(Throwable ignored){}
        }
        view.setScaleType(ImageView.ScaleType.CENTER); view.setImageDrawable(fileTileDrawable(extension(it.name)));
    }

    boolean isImage(Item it){String m=itemMime(it);return m!=null&&m.toLowerCase(Locale.CANADA).startsWith("image/");}
    String extension(String n){if(n==null)return "FILE";int i=n.lastIndexOf('.');return i>=0&&i<n.length()-1?n.substring(i+1).toUpperCase(Locale.CANADA):"FILE";}

    Drawable fileTileDrawable(String label){
        Bitmap b=Bitmap.createBitmap(dp(64),dp(64),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.TRANSPARENT);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(86,94,108));p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(label.length()>4?10:12));
        c.drawText(label,dp(32),dp(37),p);return new BitmapDrawable(getResources(),b);
    }

    Bitmap decodeSampled(String path,int reqW,int reqH){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(path,o);int s=1;while(o.outWidth/s>reqW*2||o.outHeight/s>reqH*2)s*=2;o.inSampleSize=s;o.inJustDecodeBounds=false;return BitmapFactory.decodeFile(path,o);}

    void editArtifactProduct(Item it){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);
        TextView filename=helper(it.name+"\n"+sourceLabel(it.source)+" · source provenance is retained by Relay");box.addView(filename,spaced());
        TextView sl=groupLabel("Workflow step");box.addView(sl);
        Spinner step=new Spinner(this);ArrayList<String> labels=workflowStepLabels();step.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));int idx=labels.indexOf(itemSteps.getOrDefault(it,labels.isEmpty()?"Capture":labels.get(0)));if(idx>=0)step.setSelection(idx);box.addView(step);
        TextView nl=groupLabel("Attached note");box.addView(nl);
        EditText note=edit("Optional note for this item");note.setText(itemNotes.getOrDefault(it,""));note.setMinLines(3);box.addView(note);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Edit item").setView(box).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{WorkflowStep selected=null;String selectedLabel=String.valueOf(step.getSelectedItem());for(WorkflowStep s:currentWorkflowProfile().steps)if(s.label.equals(selectedLabel)){selected=s;break;}String value=note.getText().toString().trim();if(selected!=null&&selected.noteRequired&&value.isEmpty()){note.setError("A note is required for this workflow step.");return;}itemSteps.put(it,selectedLabel);if(value.isEmpty())itemNotes.remove(it);else itemNotes.put(it,value);d.dismiss();updateWorkflowUi();}));d.show();
    }

    void confirmDeleteArtifact(Item it){new AlertDialog.Builder(this).setTitle("Delete this staged item?").setMessage(it.name+" will be removed from this Relay session. The original external source is not deleted.").setPositiveButton("Delete",(d,w)->removeArtifact(it)).setNegativeButton("Cancel",null).show();}
    void removeArtifact(Item it){items.remove(it);if(it.file!=null)try{it.file.delete();}catch(Throwable ignored){}itemSteps.remove(it);itemNotes.remove(it);updateWorkflowUi();}

    void previewArtifact(Item it){
        if(!isImage(it)){new AlertDialog.Builder(this).setTitle(it.name).setMessage("Preview is available for image artifacts. This "+extension(it.name)+" item will be preserved in its original form.").setPositiveButton("Done",null).show();return;}
        try{
            ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setPadding(dp(8),dp(8),dp(8),dp(8));
            Bitmap b=null;if(it.file!=null)b=decodeSampled(it.file.getAbsolutePath(),1400,1800);else if(it.uri!=null){try(InputStream in=getContentResolver().openInputStream(it.uri)){if(in!=null)b=BitmapFactory.decodeStream(in);}}
            if(b==null)throw new IOException("Preview unavailable");image.setImageBitmap(b);
            ScrollView sc=new ScrollView(this);sc.addView(image,new ScrollView.LayoutParams(-1,-2));
            Dialog dialog=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(12),dp(12),dp(12));root.setBackgroundColor(Color.rgb(247,248,250));TextView close=secondaryText("Done");close.setOnClickListener(v->dialog.dismiss());root.addView(close,new LinearLayout.LayoutParams(-1,dp(48)));root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(root);dialog.setOnShowListener(x->applyDialogInsets(root));dialog.show();
        }catch(Throwable e){toast("Preview unavailable for this item.");}
    }

    TextView secondaryText(String text){TextView v=new TextView(this);v.setText(text);v.setTextSize(14);v.setTypeface(Typeface.DEFAULT_BOLD);v.setTextColor(Color.rgb(54,76,113));v.setGravity(Gravity.CENTER);v.setBackground(roundRect(Color.WHITE,dp(14),Color.rgb(226,230,236)));return v;}

    void applyDialogInsets(View root){int l=root.getPaddingLeft(),t=root.getPaddingTop(),r=root.getPaddingRight(),b=root.getPaddingBottom();root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(l+i.getSystemWindowInsetLeft(),t+i.getSystemWindowInsetTop(),r+i.getSystemWindowInsetRight(),b+i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();}

    @Override void reviewWorkflowItems(){ if(items.isEmpty()){toast("Nothing captured yet.");return;} showStage(2); updateWorkflowUi(); }
}