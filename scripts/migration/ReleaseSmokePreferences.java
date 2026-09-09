package migration.smoke;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/** Test launcher only: the ProGuard application uses its real factory against memory. */
public final class ReleaseSmokePreferences implements PreferencesFactory {
    private final Memory user = new Memory(null, "");
    private final Memory system = new Memory(null, "");

    public ReleaseSmokePreferences() {
        Preferences p = user.node("toolkit");
        p.put("user_data.defaultOutputPath", System.getProperty("migration.output"));
        p.putBoolean("user_data.duplicateFileRemoval", true);
        p.put("user_data.defaultSignerSuffix", "-sign");
        p.putBoolean("user_data.alignFileSize", true);
        p.putInt("user_data.destStoreType", 0);
        p.putInt("user_data.destStoreSize", 1);
        p.putBoolean("start_check_update", false);
        p.putBoolean("junk_code", true);
        p.putBoolean("always_show_label", true);
        p.put("theme_config", "LIGHT");
    }

    public Preferences userRoot() { return user; }
    public Preferences systemRoot() { return system; }

    private static final class Memory extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Memory> children = new HashMap<>();
        Memory(AbstractPreferences parent, String name) { super(parent, name); }
        protected void putSpi(String key, String value) { values.put(key, value); }
        protected String getSpi(String key) { return values.get(key); }
        protected void removeSpi(String key) { values.remove(key); }
        protected void removeNodeSpi() {
            if (parent() instanceof Memory p) p.children.remove(name());
            values.clear();
            children.clear();
        }
        protected String[] keysSpi() { return values.keySet().toArray(String[]::new); }
        protected String[] childrenNamesSpi() { return children.keySet().toArray(String[]::new); }
        protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, n -> new Memory(this, n));
        }
        protected void syncSpi() {}
        protected void flushSpi() {}
    }
}
