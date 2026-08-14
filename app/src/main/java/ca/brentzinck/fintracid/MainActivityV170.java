package ca.brentzinck.fintracid;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

/** Relay Capture v1.7: typed destination registry + Email/Compose signal destinations. */
public class MainActivityV170 extends MainActivityV161 {
    static final String EMAIL_AUTHORITY_SUFFIX = ".relayfiles";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        pruneEmailCache();
    }

    @Override void showSettings() {
        LinearLayout body=productRoot("Relay setup","Build procedures first. Reusable definitions sit underneath them.");
        body.addView(settingsHero());
        body.addView(groupLabel("Profiles"));
        body.addView(navRow("Capture Profiles","Complete procedures: context → workflow → delivery",v->manageWorkflowProfiles()),spaced());
        body.addView(groupLabel("Reusable definitions"));
        body.addView(navRow("Artifact Types","How an artifact is acquired: framing, orientation, guidance",v->manageArtifactTypes()),spaced());
        body.addView(navRow("Categories","Why a capture exists; Collection also groups members",v->manageCats()),spaced());
        body.addView(navRow("Entity Types","What the evidence belongs to",v->manageEntityTypes()),spaced());
        body.addView(groupLabel("Delivery"));
        body.addView(navRow("Destinations","Drive, Email and web targets with semantic roles and payload policies",v->manageDestinations()),spaced());
        TextView signature=text("Relay Capture v1.7.0  ·  © 2026 Brenton Zinck\nAll rights reserved.",11,Color.rgb(125,131,141),false);
        signature.setGravity(Gravity.CENTER); signature.setPadding(dp(8),dp(28),dp(8),dp(10)); body.addView(signature,matchWrap());
    }

    @Override void manageDestinations() {
        LinearLayout body=productRoot("Destinations","Reusable delivery targets. Profiles and one-off sessions select from the same registry.");
        for (Destination d:destinations()) {
            String detail;
            if ("email".equalsIgnoreCase(d.type)) {
                EmailConfig c=emailConfig(d);
                detail="Email · "+c.role+" · "+payloadLabel(c.payload)+(d.enabled?"":" · Disabled");
            } else if ("http".equalsIgnoreCase(d.type)) {
                detail="Web signal · "+(d.enabled?"Enabled":"Disabled");
            } else {
                detail=(d.required?"Required":"Optional")+" · Durable"+(d.enabled?"":" · Disabled");
            }
            body.addView(navRow(d.label,detail,v->editDestination(d)),spaced());
        }
        body.addView(primary("＋  Add Drive folder",v->addDriveDestination()),spaced());
        body.addView(secondary("＋  Add Email destination",v->editEmail(null)),spaced());
        body.addView(secondary("＋  Add web endpoint",v->editEndpoint(null)),spaced());
        int pending=pendingSignalCount();
        if(pending>0){body.addView(groupLabel("Signal queue"));body.addView(navRow("Pending web signals",pending+" awaiting retry",v->showPendingSignals()),spaced());}
    }

    @Override void editDestination(Destination dest) {
        if(dest!=null && "email".equalsIgnoreCase(dest.type)){editEmail(dest);return;}
        super.editDestination(dest);
    }

    void editEmail(Destination existing) {
        EmailConfig old=existing==null?new EmailConfig():emailConfig(existing);
        LinearLayout body=productRoot(existing==null?"Add Email destination":"Edit "+existing.label,"Email is a Signal destination. It never authorizes Relay cleanup.");
        EditText label=edit("Destination label, e.g. NexOne GoEmail");label.setText(existing==null?"":existing.label);body.addView(fieldLabel("Label"));body.addView(label,spaced());
        EditText address=edit("recipient@example.com");address.setText(old.address);body.addView(fieldLabel("Email address(es)"));body.addView(address,spaced());body.addView(info("Multiple recipients may be separated by commas or semicolons."),spaced());
        TextView role=pickerValue(old.role);body.addView(pickerRow("Semantic role",role,v->showPicker("Destination role",new ArrayList<>(Arrays.asList("Person","Matter","Compliance","Organization","Archive","Other")),role.getText().toString(),role::setText,false)),spaced());
        TextView payload=pickerValue(payloadLabel(old.payload));body.addView(pickerRow("Default payload",payload,v->showPicker("Email payload",new ArrayList<>(Arrays.asList("Artifacts only","Artifacts + manifest","Summary only")),payload.getText().toString(),payload::setText,false)),spaced());
        EditText subject=edit("Subject template");subject.setText(old.subject);body.addView(fieldLabel("Subject"));body.addView(subject,spaced());
        EditText message=edit("Optional message template");message.setMinLines(3);message.setText(old.message);body.addView(fieldLabel("Message"));body.addView(message,spaced());
        body.addView(info("Templates may use {{profile}}, {{entity}}, {{matter}}, {{category}} and {{capture_id}}. A Profile can choose this destination as one of its defaults."),spaced());
        CheckBox enabled=modernCheck("Enabled",existing==null||existing.enabled);body.addView(enabled,spaced());
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.addView(secondary("Cancel",v->manageDestinations()),weight());actions.addView(primary("Save Email",v->{
            String a=address.getText().toString().trim(); if(!validEmails(a)){address.setError("Enter at least one valid email address.");return;}
            String l=label.getText().toString().trim(); if(l.isEmpty())l=a;
            EmailConfig c=new EmailConfig();c.address=a;c.role=role.getText().toString();c.payload=payloadCode(payload.getText().toString());c.subject=subject.getText().toString().trim();c.message=message.getText().toString().trim();
            Destination d=existing==null?new Destination(UUID.randomUUID().toString(),l,"email",c.toJson().toString(),false,false,enabled.isChecked()):new Destination(existing.id,l,"email",c.toJson().toString(),false,false,enabled.isChecked());
            List<Destination> all=destinations();if(existing==null)all.add(d);else replaceDestination(all,d);saveDestinations(all);if(d.enabled)selectedDestinationIds.add(d.id);else selectedDestinationIds.remove(d.id);manageDestinations();
        }),weight());body.addView(actions,spaced());
        if(existing!=null)body.addView(link("Remove destination",DANGER,v->confirmRemoveDestination(existing)),matchWrap());
    }

    @Override void selectDestinations() {
        List<Destination> enabled=new ArrayList<>();for(Destination d:destinations())if(d.enabled)enabled.add(d);
        if(enabled.isEmpty()){toast("Add a destination first.");manageDestinations();return;}
        String[] names=new String[enabled.size()];boolean[] checked=new boolean[enabled.size()];
        for(int i=0;i<enabled.size();i++){Destination d=enabled.get(i);String kind="email".equalsIgnoreCase(d.type)?"Email":"http".equalsIgnoreCase(d.type)?"Web signal":"Drive";names[i]=d.label+" · "+kind+(d.required?" · required":"");checked[i]=selectedDestinationIds.contains(d.id);}
        new AlertDialog.Builder(this).setTitle("Send this capture to").setMultiChoiceItems(names,checked,(dialog,which,isChecked)->{Destination d=enabled.get(which);if(isChecked)selectedDestinationIds.add(d.id);else selectedDestinationIds.remove(d.id);}).setNeutralButton("Manage",(d,w)->manageDestinations()).setPositiveButton("Done",(d,w)->state()).show();
    }

    @Override void secure() {
        try {
            String workflowProblem=workflowValidationProblem();if(workflowProblem!=null){toast(workflowProblem);return;}
            List<Destination> chosen=selectedDestinations();if(chosen.isEmpty()){toast("Select at least one destination.");selectDestinations();return;}
            ArrayList<Destination> durable=new ArrayList<>(),httpSignals=new ArrayList<>(),emails=new ArrayList<>();boolean hasRequiredDurable=false;
            for(Destination d:chosen){if("email".equalsIgnoreCase(d.type))emails.add(d);else if("http".equalsIgnoreCase(d.type))httpSignals.add(d);else{durable.add(d);if(d.durable&&d.required)hasRequiredDurable=true;}}
            if(!hasRequiredDurable){toast("Relay requires at least one selected required durable Drive destination before Secure can run.");return;}
            WorkflowProfile wp=currentWorkflowProfile();String prof=wp.name;String cat=wp.category==null||wp.category.trim().isEmpty()?"General Capture":wp.category;String typ=type==null||type.getSelectedItem()==null?wp.entityType:String.valueOf(type.getSelectedItem());String ent=entity==null?"":entity.getText().toString().trim();String mat=matter==null?"":matter.getText().toString().trim();String nt=note==null?"":note.getText().toString().trim();String cid=UUID.randomUUID().toString();long when=System.currentTimeMillis();JSONObject payload=buildSignalPayload(cid,when,wp,typ,ent,mat,nt,chosen);
            if(secure!=null)secure.setEnabled(false);if(status!=null){status.setText("TRANSFER IN PROGRESS");status.setVisibility(View.VISIBLE);}
            exec.execute(()->{
                ArrayList<String> requiredFailures=new ArrayList<>(),optionalFailures=new ArrayList<>(),signalWarnings=new ArrayList<>();JSONArray destinationResults=new JSONArray();int durableSucceeded=0,signalsSucceeded=0;ArrayList<Intent> emailIntents=new ArrayList<>();
                for(Destination dest:durable){JSONObject dr=destinationResult(dest);try{saveToDriveDestination(dest,cid,prof,cat,typ,ent,mat,nt,when,chosen);durableSucceeded++;dr.put("status","verified");}catch(Throwable e){String message=errorText(e);try{dr.put("status","failed");dr.put("error",message);}catch(Exception ignored){}String failure=dest.label+": "+message;if(dest.required)requiredFailures.add(failure);else optionalFailures.add(failure);}destinationResults.put(dr);}
                boolean preservationVerified=requiredFailures.isEmpty();
                if(preservationVerified){try{payload.put("durable_results",destinationResults);}catch(Exception ignored){}
                    for(Destination dest:httpSignals){JSONObject sr=destinationResult(dest);try{HttpResult r=postJson(dest.value,payload);if(!r.success)throw new IOException("HTTP "+r.code+(r.message.isEmpty()?"":" · "+r.message));signalsSucceeded++;sr.put("status","sent");sr.put("http_status",r.code);}catch(Throwable e){String message=errorText(e);try{sr.put("status","pending");sr.put("error",message);}catch(Exception ignored){}queueSignal(dest,payload,cid,message);signalWarnings.add(dest.label+": queued for retry");}destinationResults.put(sr);}
                    for(Destination dest:emails){JSONObject sr=destinationResult(dest);try{Intent email=buildEmailIntent(dest,payload,cid,prof,cat,ent,mat);emailIntents.add(email);signalsSucceeded++;sr.put("status","compose_prepared");EmailConfig ec=emailConfig(dest);sr.put("role",ec.role);sr.put("payload_policy",ec.payload);}catch(Throwable e){String message=errorText(e);try{sr.put("status","failed");sr.put("error",message);}catch(Exception ignored){}signalWarnings.add(dest.label+": email could not be prepared");}destinationResults.put(sr);}
                } else {for(Destination dest:httpSignals){JSONObject sr=destinationResult(dest);try{sr.put("status","not_sent");sr.put("error","Preservation was not verified.");}catch(Exception ignored){}destinationResults.put(sr);}for(Destination dest:emails){JSONObject sr=destinationResult(dest);try{sr.put("status","not_prepared");sr.put("error","Preservation was not verified.");}catch(Exception ignored){}destinationResults.put(sr);}}
                int durableOk=durableSucceeded;int signalOk=signalsSucceeded;runOnUiThread(()->{try{if(secure!=null)secure.setEnabled(true);if(preservationVerified){for(Intent email:emailIntents)try{startActivity(Intent.createChooser(email,"Send Relay evidence"));}catch(Throwable e){signalWarnings.add("Email chooser could not open: "+errorText(e));}for(Item it:new ArrayList<>(items))if(it.file!=null)try{it.file.delete();}catch(Throwable ignored){}String outcome=(optionalFailures.isEmpty()&&signalWarnings.isEmpty())?"secured":"secured_with_warnings";recordHistoryV14(cid,outcome,chosen,requiredFailures,optionalFailures,signalWarnings,destinationResults,when);ArrayList<String>warnings=new ArrayList<>(optionalFailures);warnings.addAll(signalWarnings);if(!emails.isEmpty())warnings.add(emails.size()+" email compose window"+(emails.size()==1?"":"s")+" prepared. Relay does not claim the email was sent.");showOutcomeModal(true,durableOk,warnings,()->{items.clear();resetForNewSession();});}else{recordHistoryV14(cid,"failed",chosen,requiredFailures,optionalFailures,signalWarnings,destinationResults,when);if(status!=null)status.setText("LOCAL ONLY · Required durable destination did not complete. Local captures retained.");showOutcomeModal(false,durableOk,requiredFailures,()->{showStage(2);updateWorkflowUi();});}}catch(Throwable ui){if(secure!=null)secure.setEnabled(true);toast(preservationVerified?"Preserved successfully."+(signalOk<(httpSignals.size()+emails.size())?" A signal needs attention.":""):"Not secured. Local capture retained.");}});
            });
        }catch(Throwable e){if(secure!=null)secure.setEnabled(true);toast("Could not start Secure: "+errorText(e));}
    }

    Intent buildEmailIntent(Destination dest,JSONObject manifest,String cid,String profile,String category,String entityName,String matterName) throws Exception {
        EmailConfig c=emailConfig(dest);String subject=expand(c.subject.isEmpty()?"Relay · {{profile}} · {{entity}}":c.subject,cid,profile,category,entityName,matterName);String body=expand(c.message,cid,profile,category,entityName,matterName);if(body.isEmpty())body="Captured and preserved by Relay.\n\nProfile: "+profile+(entityName.isEmpty()?"":"\nEntity: "+entityName)+(matterName.isEmpty()?"":"\nMatter: "+matterName)+"\nCapture ID: "+cid;
        Intent i=new Intent(Intent.ACTION_SEND_MULTIPLE);i.setType("*/*");i.putExtra(Intent.EXTRA_EMAIL,splitEmails(c.address));i.putExtra(Intent.EXTRA_SUBJECT,subject);i.putExtra(Intent.EXTRA_TEXT,body);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ArrayList<Uri> uris=new ArrayList<>();File dir=new File(getCacheDir(),"relay_email/"+cid+"/"+safeFile(dest.label));if(!dir.exists()&&!dir.mkdirs())throw new IOException("Could not create email cache");
        if(!"summary".equals(c.payload)){int n=0;for(Item it:items){File out=new File(dir,String.format(Locale.CANADA,"%02d_%s",++n,safeFile(it.name==null?"artifact":it.name)));copyItem(it,out);uris.add(FileProvider.getUriForFile(this,getPackageName()+EMAIL_AUTHORITY_SUFFIX,out));}}
        if("artifacts_manifest".equals(c.payload)){File mf=new File(dir,"relay_manifest_"+cid+".json");try(FileOutputStream out=new FileOutputStream(mf)){out.write(manifest.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}uris.add(FileProvider.getUriForFile(this,getPackageName()+EMAIL_AUTHORITY_SUFFIX,mf));}
        i.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);return i;
    }

    void copyItem(Item it,File out) throws Exception {try(InputStream in=it.file!=null?new FileInputStream(it.file):getContentResolver().openInputStream(it.uri);OutputStream o=new FileOutputStream(out)){if(in==null)throw new IOException("Could not read "+it.name);byte[]b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);}}
    String expand(String s,String cid,String profile,String category,String entityName,String matterName){return (s==null?"":s).replace("{{capture_id}}",cid).replace("{{profile}}",profile==null?"":profile).replace("{{category}}",category==null?"":category).replace("{{entity}}",entityName==null?"":entityName).replace("{{matter}}",matterName==null?"":matterName);}
    String[] splitEmails(String s){return s.trim().split("\\s*[,;]\\s*");}
    boolean validEmails(String s){String[] all=splitEmails(s);if(all.length==0)return false;for(String x:all)if(!android.util.Patterns.EMAIL_ADDRESS.matcher(x).matches())return false;return true;}
    String safeFile(String s){String x=s==null?"item":s.replaceAll("[^A-Za-z0-9._-]+","_");return x.isEmpty()?"item":x;}
    void pruneEmailCache(){try{File root=new File(getCacheDir(),"relay_email");long cutoff=System.currentTimeMillis()-24L*60L*60L*1000L;prune(root,cutoff);}catch(Throwable ignored){}}
    void prune(File f,long cutoff){if(f==null||!f.exists())return;if(f.isDirectory()){File[]kids=f.listFiles();if(kids!=null)for(File k:kids)prune(k,cutoff);}if(f.lastModified()<cutoff)f.delete();}

    EmailConfig emailConfig(Destination d){try{return EmailConfig.from(new JSONObject(d.value));}catch(Exception e){EmailConfig c=new EmailConfig();c.address=d.value==null?"":d.value;return c;}}
    String payloadLabel(String p){return "summary".equals(p)?"Summary only":"artifacts_manifest".equals(p)?"Artifacts + manifest":"Artifacts only";}
    String payloadCode(String p){return "Summary only".equals(p)?"summary":"Artifacts + manifest".equals(p)?"artifacts_manifest":"artifacts";}

    static class EmailConfig {
        String address="",role="Other",payload="artifacts",subject="Relay · {{profile}} · {{entity}}",message="";
        JSONObject toJson(){JSONObject o=new JSONObject();try{o.put("address",address);o.put("role",role);o.put("payload",payload);o.put("subject",subject);o.put("message",message);}catch(Exception ignored){}return o;}
        static EmailConfig from(JSONObject o){EmailConfig c=new EmailConfig();c.address=o.optString("address","");c.role=o.optString("role","Other");c.payload=o.optString("payload","artifacts");c.subject=o.optString("subject",c.subject);c.message=o.optString("message","");return c;}
    }
}
