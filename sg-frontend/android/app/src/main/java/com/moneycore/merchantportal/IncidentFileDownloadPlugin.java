package com.moneycore.merchantportal;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Base64;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-selected copy only. No URL/token, broad storage permission, arbitrary path, or payload log. */
@CapacitorPlugin(name = "IncidentFileDownload")
public class IncidentFileDownloadPlugin extends Plugin {
    private final AtomicBoolean busy = new AtomicBoolean();
    private File pendingRoot;
    private volatile String activeId;
    private static final String PENDING_ID = "_incidentPendingId";

    @Override public void load() {
        try {
            File cache = getContext().getCacheDir().getCanonicalFile();
            File root = new File(cache, "incident-downloads");
            if ((!root.isDirectory() && !root.mkdir()) || !root.getCanonicalFile().getParentFile().equals(cache)) return;
            pendingRoot = root.getCanonicalFile();
            // Only temporary copies from an interrupted previous app instance; never business data.
            File[] files = pendingRoot.listFiles();
            if (files != null) for (File file : files) {
                if (file.getName().matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.bin") &&
                        file.getCanonicalFile().getParentFile().equals(pendingRoot) && file.isFile() && !file.delete()) {
                    pendingRoot = null;
                    return;
                }
            }
        } catch (Exception ignored) { pendingRoot = null; }
    }

    @PluginMethod public void save(PluginCall call) {
        if (!busy.compareAndSet(false, true)) { call.reject("Une copie est déjà en cours.", "INCIDENT_COPY_BUSY"); return; }
        String encoded = call.getString("base64"), mime = call.getString("contentType"), name = call.getString("filename");
        call.getData().remove("base64"); // Never save this payload in the pending activity call.
        if (pendingRoot == null || !IncidentDownloadPolicy.validName(name, mime) || !IncidentDownloadPolicy.validBase64(encoded)) {
            fail(call, "INCIDENT_COPY_INVALID"); return;
        }
        getActivity().runOnUiThread(() -> {
            if (!trustedAppOrigin()) { fail(call, "INCIDENT_COPY_ORIGIN"); return; }
            execute(() -> prepare(call, encoded, mime, name));
        });
    }

    private boolean trustedAppOrigin() {
        try {
            Uri current = Uri.parse(getBridge().getWebView().getUrl());
            return "https".equals(current.getScheme()) && "localhost".equals(current.getHost()) && current.getPort() == -1;
        } catch (Exception ignored) { return false; }
    }

    private void prepare(PluginCall call, String encoded, String mime, String name) {
        byte[] bytes = null;
        try {
            bytes = Base64.decode(encoded, Base64.NO_WRAP);
            if (!IncidentDownloadPolicy.validContent(mime, bytes)) { fail(call, "INCIDENT_COPY_INVALID"); return; }
            activeId = UUID.randomUUID().toString();
            File file = pending(activeId);
            if (!file.createNewFile()) { fail(call, "INCIDENT_COPY_PREPARE_FAILED"); return; }
            if (!file.setReadable(false, false) || !file.setWritable(false, false) || !file.setReadable(true, true) || !file.setWritable(true, true)) {
                fail(call, "INCIDENT_COPY_PREPARE_FAILED"); return;
            }
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); output.getFD().sync(); }
            call.getData().put(PENDING_ID, activeId);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime);
            intent.putExtra(Intent.EXTRA_TITLE, name);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download"));
            getActivity().runOnUiThread(() -> {
                if (!trustedAppOrigin()) { fail(call, "INCIDENT_COPY_ORIGIN"); return; }
                try { startActivityForResult(call, intent, "saveResult"); }
                catch (Exception ignored) { fail(call, "INCIDENT_COPY_PICKER_UNAVAILABLE"); }
            });
        } catch (Exception ignored) { fail(call, "INCIDENT_COPY_PREPARE_FAILED"); }
        finally { if (bytes != null) Arrays.fill(bytes, (byte) 0); }
    }

    @ActivityCallback private void saveResult(PluginCall call, ActivityResult result) {
        if (call == null) { cleanup(activeId); busy.set(false); return; }
        String id = call.getString(PENDING_ID);
        execute(() -> {
            boolean saved = false;
            String error = null;
            try {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Uri uri = result.getData() == null ? null : result.getData().getData();
                    if (uri == null || !"content".equals(uri.getScheme())) throw new IllegalStateException();
                    File source = pending(id);
                    long expected = source.length();
                    if (expected < 1 || expected > IncidentDownloadPolicy.MAX_BYTES) throw new IllegalStateException();
                    try (FileInputStream input = new FileInputStream(source);
                         OutputStream output = getContext().getContentResolver().openOutputStream(uri, "w")) {
                        if (output == null) throw new IllegalStateException();
                        byte[] buffer = new byte[8192]; long copied = 0;
                        try {
                            for (int size; (size = input.read(buffer)) != -1;) {
                                copied += size;
                                if (copied > expected) throw new IllegalStateException();
                                output.write(buffer, 0, size);
                            }
                            output.flush();
                            if (copied != expected) throw new IllegalStateException();
                        } finally { Arrays.fill(buffer, (byte) 0); }
                    }
                    saved = true; // Success only after both streams closed, never just after picker selection.
                }
            } catch (Exception ignored) { error = "INCIDENT_COPY_NOT_CONFIRMED"; }
            if (!cleanup(id)) error = "INCIDENT_COPY_CLEANUP_FAILED";
            activeId = null; busy.set(false);
            if (error != null) call.reject("Copie non confirmée. Vérifiez le fichier avant une nouvelle demande.", error);
            else { JSObject response = new JSObject(); response.put("saved", saved); call.resolve(response); }
        });
    }

    private File pending(String id) throws Exception {
        if (pendingRoot == null || id == null || !id.matches("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")) throw new IllegalStateException();
        File file = new File(pendingRoot, id + ".bin");
        if (!file.getCanonicalFile().getParentFile().equals(pendingRoot)) throw new IllegalStateException();
        return file;
    }
    private boolean cleanup(String id) {
        if (id == null) return true;
        try { File file = pending(id); return !file.exists() || file.delete(); }
        catch (Exception ignored) { return false; }
    }
    private void fail(PluginCall call, String code) {
        cleanup(activeId); activeId = null; busy.set(false);
        call.reject("Copie non confirmée. Le dossier reste disponible.", code);
    }
}
