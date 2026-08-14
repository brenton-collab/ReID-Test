package ca.brentzinck.fintracid;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_FOLDER_REQUEST = 1001;
    private static final String PREFS = "fintrac_id_prefs";
    private static final String KEY_FOLDER_URI = "folder_uri";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Intent pendingShareIntent;
    private TextView statusView;
    private Button chooseFolderButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void buildUi() {
        int pad = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("FINTRAC ID");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(32), 0, dp(16));
        root.addView(title);

        TextView body = new TextView(this);
        body.setText("Choose one Google Drive intake folder once. After that, share ID photos or PDFs to FINTRAC ID from Android's Share sheet and they will be copied there automatically.");
        body.setTextSize(17);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(0, 0, 0, dp(24));
        root.addView(body);

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setGravity(Gravity.CENTER_HORIZONTAL);
        statusView.setPadding(0, 0, 0, dp(20));
        root.addView(statusView);

        chooseFolderButton = new Button(this);
        chooseFolderButton.setText("Choose intake folder");
        chooseFolderButton.setOnClickListener(v -> chooseFolder());
        root.addView(chooseFolderButton);

        setContentView(root);
        refreshStatus();
    }

    private void handleIncomingIntent(Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            refreshStatus();
            return;
        }

        pendingShareIntent = intent;
        Uri folderUri = getSavedFolderUri();
        if (folderUri == null) {
            statusView.setText("First use: choose the Drive folder where FINTRAC IDs should land.");
            chooseFolder();
            return;
        }

        copySharedItems(intent, folderUri);
    }

    private void chooseFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, PICK_FOLDER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FOLDER_REQUEST || resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == PICK_FOLDER_REQUEST) {
                Toast.makeText(this, "No intake folder selected.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        Uri treeUri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(treeUri, flags);
        } catch (SecurityException e) {
            Toast.makeText(this, "Android could not preserve access to that folder.", Toast.LENGTH_LONG).show();
            return;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FOLDER_URI, treeUri.toString()).apply();
        refreshStatus();

        if (pendingShareIntent != null) {
            Intent toProcess = pendingShareIntent;
            pendingShareIntent = null;
            copySharedItems(toProcess, treeUri);
        }
    }

    private Uri getSavedFolderUri() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = prefs.getString(KEY_FOLDER_URI, null);
        return raw == null ? null : Uri.parse(raw);
    }

    private void refreshStatus() {
        Uri uri = getSavedFolderUri();
        if (uri == null) {
            statusView.setText("No intake folder configured yet.");
            chooseFolderButton.setText("Choose intake folder");
        } else {
            statusView.setText("Intake folder configured. Share an ID photo or PDF to FINTRAC ID.");
            chooseFolderButton.setText("Change intake folder");
        }
    }

    private void copySharedItems(Intent shareIntent, Uri folderUri) {
        List<Uri> items = extractSharedUris(shareIntent);
        if (items.isEmpty()) {
            Toast.makeText(this, "No image or PDF was received.", Toast.LENGTH_LONG).show();
            return;
        }

        statusView.setText("Saving " + items.size() + (items.size() == 1 ? " item…" : " items…"));
        chooseFolderButton.setEnabled(false);

        executor.execute(() -> {
            int saved = 0;
            String errorMessage = null;
            try {
                DocumentFile folder = DocumentFile.fromTreeUri(this, folderUri);
                if (folder == null || !folder.canWrite()) {
                    throw new IllegalStateException("The selected folder is not writable.");
                }

                for (Uri source : items) {
                    saveOne(source, folder);
                    saved++;
                }
            } catch (Exception e) {
                errorMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }

            final int savedCount = saved;
            final String finalError = errorMessage;
            runOnUiThread(() -> {
                chooseFolderButton.setEnabled(true);
                if (finalError == null) {
                    String message = savedCount == 1 ? "ID saved to Drive intake." : savedCount + " IDs saved to Drive intake.";
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    statusView.setText(message);
                    finish();
                } else {
                    statusView.setText("Could not save the shared item. You can re-select the intake folder and try again.");
                    Toast.makeText(this, "FINTRAC ID: " + finalError, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private List<Uri> extractSharedUris(Intent intent) {
        List<Uri> uris = new ArrayList<>();

        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ClipData clipData = intent.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    if (uri != null) uris.add(uri);
                }
            }
            ArrayList<Uri> parcelables = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris.isEmpty() && parcelables != null) uris.addAll(parcelables);
        } else {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) uris.add(uri);
            if (uri == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
                Uri clipUri = intent.getClipData().getItemAt(0).getUri();
                if (clipUri != null) uris.add(clipUri);
            }
        }
        return uris;
    }

    private void saveOne(Uri source, DocumentFile folder) throws Exception {
        ContentResolver resolver = getContentResolver();
        String mime = resolver.getType(source);
        if (mime == null || mime.isBlank()) mime = "application/octet-stream";

        String originalName = getDisplayName(source);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.CANADA).format(new Date());
        String safeName = sanitizeName(originalName == null ? defaultNameForMime(mime) : originalName);
        String outputName = timestamp + "_" + safeName;

        DocumentFile target = folder.createFile(mime, outputName);
        if (target == null) throw new IllegalStateException("The Drive provider could not create the destination file.");

        try (InputStream in = resolver.openInputStream(source);
             OutputStream out = resolver.openOutputStream(target.getUri(), "w")) {
            if (in == null || out == null) throw new IllegalStateException("Could not open the shared file.");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String defaultNameForMime(String mime) {
        if ("application/pdf".equals(mime)) return "FINTRAC_ID.pdf";
        if (mime.startsWith("image/")) return "FINTRAC_ID.jpg";
        return "FINTRAC_ID.bin";
    }

    private String sanitizeName(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "FINTRAC_ID" : cleaned;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) executor.shutdown();
    }
}
