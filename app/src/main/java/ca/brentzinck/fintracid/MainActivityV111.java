package ca.brentzinck.fintracid;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Relay Capture v1.1.1 transaction patch.
 *
 * Cloud-backed SAF providers such as Google Drive can report stale file-size
 * metadata immediately after a successful write. v1.1.0 treated that lag as a
 * failed destination and aborted before writing the provenance sidecar.
 *
 * This activity preserves the proven MainActivity capture/profile/destination
 * implementation and overrides only the destination save transaction. A write
 * is verified by reopening and reading the created URI, with short retries.
 * Partial destination artifacts are deleted on transaction failure where the
 * provider permits it.
 */
public class MainActivityV111 extends MainActivity {

    @Override
    void save(DocumentFile folder, MainActivity.Destination destination, MainActivity.Item it,
              int seq, String cid, String prof, String cat, String typ, String ent,
              String mat, String nt, long secured, List<MainActivity.Destination> chosen) throws Exception {

        ContentResolver resolver = getContentResolver();
        String mime = it.file != null
                ? (it.file.getName().endsWith(".png") ? "image/png" : "image/jpeg")
                : resolver.getType(it.uri);
        if (mime == null) mime = "application/octet-stream";

        String ex = ext(it.name, mime);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA).format(new Date());
        String anchor = !ent.isEmpty() ? ent : (!mat.isEmpty() ? mat : "Unassigned");
        String fileName = safe(date + " - " + anchor + " - " + cat + " - " + seq) + ex;

        DocumentFile target = folder.createFile(mime, fileName);
        if (target == null) throw new Exception("could not create destination file");

        DocumentFile sidecar = null;
        try {
            long bytesWritten = 0;
            try (InputStream in = it.file != null ? new FileInputStream(it.file) : resolver.openInputStream(it.uri);
                 OutputStream out = resolver.openOutputStream(target.getUri(), "w")) {
                if (in == null || out == null) throw new Exception("could not open capture");
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    bytesWritten += n;
                }
                out.flush();
            }

            if (bytesWritten <= 0) throw new Exception("capture contained no data");
            if (!verifyReadable(target.getUri())) throw new Exception("destination verification failed");

            JSONObject meta = new JSONObject();
            meta.put("relay_schema_version", 2);
            meta.put("capture_id", cid);
            meta.put("captured_at", it.created);
            meta.put("secured_at", secured);
            meta.put("source", it.source);
            meta.put("profile", prof);
            meta.put("category", cat);
            meta.put("entity_type", typ);
            meta.put("entity", ent);
            meta.put("matter", mat);
            meta.put("note", nt);
            meta.put("original_name", it.name);
            meta.put("app_version", "1.1.1");
            meta.put("mime_type", mime);
            meta.put("file_name", fileName);
            meta.put("bytes_written", bytesWritten);
            meta.put("destination_id", destination.id);
            meta.put("destination_label", destination.label);
            meta.put("destination_type", destination.type);
            meta.put("destination_required", destination.required);
            meta.put("destination_durable", destination.durable);

            JSONArray destinationLabels = new JSONArray();
            for (MainActivity.Destination d : chosen) destinationLabels.put(d.label);
            meta.put("selected_destinations", destinationLabels);

            byte[] jsonBytes = meta.toString(2).getBytes(StandardCharsets.UTF_8);
            sidecar = folder.createFile("application/json", fileName + ".json");
            if (sidecar == null) throw new Exception("could not create provenance sidecar");

            try (OutputStream out = resolver.openOutputStream(sidecar.getUri(), "w")) {
                if (out == null) throw new Exception("could not open provenance sidecar");
                out.write(jsonBytes);
                out.flush();
            }

            if (!verifyReadable(sidecar.getUri())) throw new Exception("provenance verification failed");

        } catch (Exception failure) {
            // Best-effort rollback keeps retries from accumulating known partial copies.
            try { if (sidecar != null) sidecar.delete(); } catch (Exception ignored) {}
            try { target.delete(); } catch (Exception ignored) {}
            throw failure;
        }
    }

    /**
     * Verify persistence through the provider's actual content stream rather
     * than immediately-consistent metadata such as DocumentFile.length().
     */
    private boolean verifyReadable(Uri uri) {
        final long[] delaysMs = {0, 150, 350, 750, 1200};
        for (long delay : delaysMs) {
            if (delay > 0) {
                try { Thread.sleep(delay); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null && in.read() != -1) return true;
            } catch (Exception ignored) {
                // Cloud-backed providers can transiently reject a read while
                // committing the just-closed write. Retry within a short bound.
            }
        }
        return false;
    }
}
