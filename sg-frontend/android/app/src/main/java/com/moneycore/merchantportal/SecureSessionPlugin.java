package com.moneycore.merchantportal;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

@CapacitorPlugin(name = "SecureSession")
public class SecureSessionPlugin extends Plugin {
    private static final String KEY_ALIAS = "merchant_portal_session_key";
    private static final String PREFERENCES = "merchant_portal_secure_session";
    private static final String TOKEN = "encrypted_access_token";

    @PluginMethod
    public void set(PluginCall call) {
        String value = call.getString("value");
        if (value == null || value.isBlank()) { call.reject("A token is required"); return; }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            ByteBuffer payload = ByteBuffer.allocate(4 + iv.length + encrypted.length)
                    .putInt(iv.length).put(iv).put(encrypted);
            preferences().edit().putString(TOKEN,
                    Base64.encodeToString(payload.array(), Base64.NO_WRAP)).apply();
            call.resolve();
        } catch (Exception exception) { call.reject("Cannot protect session", exception); }
    }

    @PluginMethod
    public void get(PluginCall call) {
        JSObject result = new JSObject();
        String encoded = preferences().getString(TOKEN, null);
        if (encoded == null) { result.put("value", JSObject.NULL); call.resolve(result); return; }
        try {
            ByteBuffer payload = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP));
            int ivLength = payload.getInt();
            if (ivLength < 12 || ivLength > 32 || payload.remaining() <= ivLength)
                throw new IllegalStateException("Invalid protected session");
            byte[] iv = new byte[ivLength]; payload.get(iv);
            byte[] encrypted = new byte[payload.remaining()]; payload.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            result.put("value", new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
            call.resolve(result);
        } catch (Exception exception) {
            preferences().edit().remove(TOKEN).apply();
            call.reject("Cannot restore session", exception);
        }
    }

    @PluginMethod
    public void clear(PluginCall call) {
        preferences().edit().remove(TOKEN).apply();
        call.resolve();
    }

    private SharedPreferences preferences() {
        return getContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
