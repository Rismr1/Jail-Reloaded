package com.graywolf336.jail;

import java.lang.reflect.Method;
import org.bukkit.block.Sign;

/** Native honeycomb protection where available, while retaining the 1.19 API baseline. */
public final class SignProtection {
    private static final Method WAX = findWaxMethod();

    private SignProtection() { }

    private static Method findWaxMethod() {
        try { return Sign.class.getMethod("setWaxed", boolean.class); }
        catch(NoSuchMethodException olderApi) { return null; }
    }

    /** Waxing prevents player edits but does not prevent plugin setLine/update calls. */
    public static void protect(Sign sign) {
        if(WAX == null) return;
        try { WAX.invoke(sign, true); }
        catch(ReflectiveOperationException failure) {
            throw new IllegalStateException("Unable to protect registered jail sign", failure);
        }
    }
}
