package org.tool.kit.tests.support

import java.util.prefs.AbstractPreferences
import java.util.prefs.Preferences
import java.util.prefs.PreferencesFactory

/** Keeps every preference lookup in the test JVM in memory. */
class IsolatedPreferencesFactory : PreferencesFactory {
    private val user = MemoryPreferences(null, "")
    private val system = MemoryPreferences(null, "")
    override fun userRoot(): Preferences = user
    override fun systemRoot(): Preferences = system
}

private class MemoryPreferences(parent: AbstractPreferences?, name: String) : AbstractPreferences(parent, name) {
    private val values = mutableMapOf<String, String>()
    private val children = mutableMapOf<String, MemoryPreferences>()
    override fun putSpi(key: String, value: String) { values[key] = value }
    override fun getSpi(key: String): String? = values[key]
    override fun removeSpi(key: String) { values.remove(key) }
    override fun removeNodeSpi() {
        (parent() as? MemoryPreferences)?.children?.remove(name())
        values.clear()
        children.clear()
    }
    override fun keysSpi(): Array<String> = values.keys.toTypedArray()
    override fun childrenNamesSpi(): Array<String> = children.keys.toTypedArray()
    override fun childSpi(name: String): AbstractPreferences =
        children.getOrPut(name) { MemoryPreferences(this, name) }
    override fun syncSpi() = Unit
    override fun flushSpi() = Unit
}
