package ca.brentzinck.fintracid;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Relay Product UI 2.0 + visible product copyright signature. */
public class MainActivityV161 extends MainActivityV160 {
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
        body.addView(navRow("Destinations","Durable preservation and optional signal targets",v->manageDestinations()),spaced());

        TextView signature=text("Relay Capture v1.6.1  ·  © 2026 Brenton Zinck\nAll rights reserved.",11,Color.rgb(125,131,141),false);
        signature.setGravity(Gravity.CENTER);
        signature.setPadding(dp(8),dp(28),dp(8),dp(10));
        body.addView(signature,matchWrap());
    }
}
