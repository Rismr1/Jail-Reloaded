package com.graywolf336.jail;

import java.io.*;
import java.lang.reflect.Method;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;

/** Full native NBT on servers exposing the byte API; Bukkit metadata serialization otherwise. */
public final class ItemBytes {
    private static final String NATIVE = "JAIL2N:";
    private static final String BUKKIT = "JAIL2B:";
    // Persist that property has been reclaimed, so release merges rather than clears.
    static final String RECLAIMED = "JAIL2R:";
    private static final Method WRITE = method("serializeAsBytes");
    private static final Method READ = method("deserializeBytes", byte[].class);
    private ItemBytes() { }

    private static Method method(String name, Class<?>... args) {
        try { return ItemStack.class.getMethod(name, args); }
        catch(NoSuchMethodException unavailable) { return null; }
    }

    public static boolean isCurrent(String value) {
        return value.startsWith(NATIVE) || value.startsWith(BUKKIT) || value.startsWith(RECLAIMED);
    }

    public static String encode(ItemStack[] items) {
        if(WRITE == null || READ == null) return BUKKIT + Util.legacyItemStackArrayToBase64(items);
        try(ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(items.length);
            for(ItemStack item : items) {
                if(item == null || item.getType().isAir()) { out.writeInt(-1); continue; }
                byte[] encoded = (byte[]) WRITE.invoke(item);
                out.writeInt(encoded.length);
                out.write(encoded);
            }
            out.flush();
            return NATIVE + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch(Exception failure) { throw new IllegalStateException("Unable to preserve complete item data", failure); }
    }

    public static ItemStack[] decode(String value) throws IOException {
        if(value.startsWith(RECLAIMED)) value = value.substring(RECLAIMED.length());
        if(value.startsWith(BUKKIT)) return Util.legacyItemStackArrayFromBase64(value.substring(BUKKIT.length()));
        if(!value.startsWith(NATIVE)) return Util.legacyItemStackArrayFromBase64(value);
        if(READ == null) throw new IOException("This inventory requires a server with native item-byte deserialization; stored items have been retained.");
        try(DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value.substring(NATIVE.length()))))) {
            int count = in.readInt();
            if(count < 0 || count > 1024) throw new IOException("Invalid stored inventory size");
            ItemStack[] items = new ItemStack[count];
            for(int slot = 0; slot < count; slot++) {
                int length = in.readInt();
                if(length == -1) continue;
                if(length < 0 || length > in.available()) throw new IOException("Invalid stored item length");
                items[slot] = (ItemStack) READ.invoke(null, (Object) in.readNBytes(length));
            }
            if(in.available() != 0) throw new IOException("Unexpected trailing inventory data");
            return items;
        } catch(IOException failure) { throw failure; }
        catch(Exception failure) { throw new IOException("Unable to restore complete item data; inventory retained", failure); }
    }
}
