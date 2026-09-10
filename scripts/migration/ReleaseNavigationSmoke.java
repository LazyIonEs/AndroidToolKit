package migration.smoke;

import java.lang.reflect.Method;
import java.util.Arrays;

/** Exercise the retained reflection path against the actual optimized release classes. */
public final class ReleaseNavigationSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 10) throw new AssertionError("serializer and nine navigation classes required");
        var constructor = Class.forName(args[0]).getDeclaredConstructor();
        constructor.setAccessible(true);
        Object serializer = constructor.newInstance();
        // Ktor's retained API exposes the delivered Json type without assuming obfuscated names.
        Method defaultJson = Class.forName("io.ktor.serialization.kotlinx.json.JsonSupportKt").getMethod("getDefaultJson");
        Object json = defaultJson.invoke(null);
        Class<?> jsonType = defaultJson.getReturnType();
        Method encode = Arrays.stream(jsonType.getMethods()).filter(m -> m.getReturnType() == String.class
            && m.getParameterCount() == 2 && m.getParameterTypes()[1] == Object.class
            && m.getParameterTypes()[0].isInstance(serializer)).findFirst().orElseThrow();
        Method decode = Arrays.stream(jsonType.getMethods()).filter(m -> m.getReturnType() == Object.class
            && m.getParameterCount() == 2 && m.getParameterTypes()[1] == String.class
            && m.getParameterTypes()[0].isInstance(serializer)).findFirst().orElseThrow();
        for (int i = 1; i < args.length; i++) {
            Class<?> type = Class.forName(args[i]);
            Object key = type.getField("INSTANCE").get(null);
            String text = (String) encode.invoke(json, serializer, key);
            if (!text.contains("\"type\":\"" + type.getName() + "\"") || !text.contains("\"value\":{}")) {
                throw new AssertionError("Unexpected navigation payload: " + text);
            }
            Object roundTrip = decode.invoke(json, serializer, text);
            if (roundTrip != key || !encode.invoke(json, serializer, roundTrip).equals(text)) {
                throw new AssertionError("Navigation reflection round trip failed: " + type.getName());
            }
            System.out.println("PASS packaged navigation: " + text);
        }
    }
}
