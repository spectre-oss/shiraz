// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.accounts.Account;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.HashSet;

/** Typed request admission; queued work is not evidence of adapter execution. */
public final class SyncRequests {
    private SyncRequests() {}
    public static void request(Account account, String authority, Bundle extras) {
        byte[] request;
        try { request = encode(account, authority, extras); }
        catch (IOException error) { throw new IllegalArgumentException("Invalid sync request", error); }
        ServiceBrokerClient client = RuntimeBroker.client();
        if (client == null) throw new UnsupportedOperationException("Sync requests require a registered host session");
        try {
            int queued = client.requestSync(request);
            if (queued > 0) System.err.println("SHIRAZ_SYNC_QUEUED count=" + queued);
        } catch (IOException error) { throw new IllegalStateException("Host sync admission failed", error); }
    }
    private static void account(JsonWriter writer, Account account) throws IOException {
        if (account == null) writer.nullValue();
        else writer.beginObject().name("name").value(account.name).name("type").value(account.type).endObject();
    }
    static byte[] encode(Account account, String authority, Bundle extras) throws IOException {
        if (extras == null) throw new IllegalArgumentException("Sync extras required");
        Bundle snapshot = new Bundle(extras);
        android.content.ContentResolver.validateSyncExtrasBundle(snapshot);
        if (snapshot.size() > 128) throw new IllegalArgumentException("Sync extras count limit");
        StringWriter text = new StringWriter();
        JsonWriter writer = new JsonWriter(text);
        writer.beginObject().name("op").value("request_sync").name("request").beginObject().name("account");
        account(writer, account);
        writer.name("authority").value(authority).name("extras").beginObject();
        for (String key : snapshot.keySet()) {
            Object value = snapshot.get(key);
            String type = value == null ? "null" : value instanceof Boolean ? "boolean" : value instanceof Integer ? "int" :
                value instanceof Long ? "long" : value instanceof Float ? "float" : value instanceof Double ? "double" :
                value instanceof Account ? "account" : "string";
            writer.name(key).beginObject().name("type").value(type).name("value");
            if (value == null) writer.nullValue();
            else if (value instanceof Account) account(writer, (Account)value);
            else if (value instanceof Boolean) writer.value((Boolean)value);
            else if (value instanceof Number) writer.value((Number)value);
            else writer.value((String)value);
            writer.endObject();
        }
        writer.endObject().endObject().endObject(); writer.close();
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32768) throw new IllegalArgumentException("Sync request byte limit");
        return bytes;
    }
    static int decode(byte[] bytes) throws IOException {
        if (bytes.length > 4096) throw new IOException("Sync reply byte limit");
        try (JsonReader reader = new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()))) {
            HashSet<String> fields = new HashSet<String>();
            Boolean ok = null; Integer queued = null; String error = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if (!fields.add(field)) throw new IOException("Duplicate sync reply field");
                if (field.equals("ok") && reader.peek() == JsonToken.BOOLEAN) ok = reader.nextBoolean();
                else if (field.equals("queued") && reader.peek() == JsonToken.NUMBER) {
                    String number = reader.nextString();
                    if (!number.matches("0|[1-9][0-9]{0,3}")) throw new IOException("Invalid queued count");
                    queued = Integer.valueOf(number);
                } else if (field.equals("error") && reader.peek() == JsonToken.STRING) error = reader.nextString();
                else throw new IOException("Unknown or mistyped sync reply field");
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || ok == null) throw new IOException("Incomplete sync reply");
            if (!ok) {
                if (error == null || queued != null) throw new IOException("Malformed sync denial");
                throw new SecurityException("Host denied sync request");
            }
            if (error != null || queued == null || queued > 128) throw new IOException("Malformed sync admission");
            return queued;
        } catch (IllegalStateException | IllegalArgumentException error) { throw new IOException("Malformed sync reply", error); }
    }
}
