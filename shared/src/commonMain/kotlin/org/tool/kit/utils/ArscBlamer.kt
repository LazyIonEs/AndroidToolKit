package org.tool.kit.utils

/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.google.devrel.gmscore.tools.apk.arsc.PackageChunk
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import com.google.devrel.gmscore.tools.apk.arsc.ResourceValue
import com.google.devrel.gmscore.tools.apk.arsc.TypeChunk
import java.util.Collections

/**
 * Analyzes an APK to:
 *
 * <ul>
 * <li>Blame resource configurations on their entry count (entries keeping the config around).
 * <li>Blame strings in resources.arsc that have no base configuration.
 * <li>Blame resources on their different configurations.
 * </ul>
 */
class ArscBlamer(private val resourceTable: ResourceTableChunk) {

    /** Maps package key pool indices to blamed resources. */
    private val keyToBlame = HashMap<PackageChunk, Array<ArrayList<ResourceEntry>>>()

    /** Maps types to blamed resources. */
    private val typeToBlame = HashMap<PackageChunk, Array<ArrayList<ResourceEntry>>>()

    /** Maps package to blamed resources. */
    private val packageToBlame: Multimap<PackageChunk, ResourceEntry> = HashMultimap.create()

    /** Maps string indices to blamed resources. */
    private val stringToBlame: Array<ArrayList<ResourceEntry>> = createEntryListArray(resourceTable.stringPool.stringCount)

    /** Maps type chunk entries to blamed resources. */
    private val typeEntryToBlame: Multimap<TypeChunk.Entry, ResourceEntry> = HashMultimap.create()

    /** Maps resources to the type chunk entries they reference. */
    private var resourceEntries: Multimap<ResourceEntry, TypeChunk.Entry>? = null

    /** Maps resources which have no base config to the type chunk entries they reference. */
    private var baselessKeys: Multimap<ResourceEntry, TypeChunk.Entry>? = null

    /** Contains all of the type chunks in [resourceTable]. */
    private var typeChunks: List<TypeChunk>? = null

    /** Generates blame mappings. */
    fun blame() {
        val entries = getResourceEntries()
        for ((resourceEntry, chunkEntries) in entries.asMap()) {
            val packageChunk = checkNotNull(resourceTable.getPackages(resourceEntry.packageName).getOrNull(0))
            val keyCount = packageChunk.keyStringPool.stringCount
            val typeCount = packageChunk.typeStringPool.stringCount

            for (chunkEntry in chunkEntries) {
                blameKeyOrType(keyToBlame, packageChunk, chunkEntry.keyIndex(), resourceEntry, keyCount)
                blameKeyOrType(typeToBlame, packageChunk, chunkEntry.parent().id - 1, resourceEntry, typeCount)
                blameFromTypeChunkEntry(chunkEntry)
            }
            blamePackage(packageChunk, resourceEntry)
        }

        Multimaps.invertFrom(entries, typeEntryToBlame)
        for (entry in typeEntryToBlame.keySet()) {
            blameFromTypeChunkEntry(entry)
        }
    }

    private fun blameKeyOrType(
        keyOrType: MutableMap<PackageChunk, Array<ArrayList<ResourceEntry>>>,
        packageChunk: PackageChunk,
        keyIndex: Int,
        entry: ResourceEntry,
        entryCount: Int
    ) {
        val array = keyOrType.getOrPut(packageChunk) { createEntryListArray(entryCount) }
        array[keyIndex].add(entry)
    }

    private fun blamePackage(packageChunk: PackageChunk, entry: ResourceEntry) {
        packageToBlame.put(packageChunk, entry)
    }

    private fun blameFromTypeChunkEntry(chunkEntry: TypeChunk.Entry) {
        for (value in getAllResourceValues(chunkEntry)) {
            for (entry in typeEntryToBlame.get(chunkEntry)) {
                // 假设 ResourceValue.Type 是一个枚举
                if (value.type() == ResourceValue.Type.STRING) {
                    blameString(value.data(), entry)
                }
            }
        }
    }

    /** Returns all [ResourceValue] for a single `entry`. */
    private fun getAllResourceValues(entry: TypeChunk.Entry): Collection<ResourceValue> {
        val values = HashSet<ResourceValue>()
        entry.value()?.let { values.add(it) }
        values.addAll(entry.values().values)
        return values
    }

    private fun blameString(stringIndex: Int, entry: ResourceEntry) {
        stringToBlame[stringIndex].add(entry)
    }

    /** Must first call [blame]. */
    val keyToBlamedResources: Map<PackageChunk, Array<ArrayList<ResourceEntry>>>
        get() = Collections.unmodifiableMap(keyToBlame)

    /** Must first call [blame]. */
    val typeToBlamedResources: Map<PackageChunk, Array<ArrayList<ResourceEntry>>>
        get() = Collections.unmodifiableMap(typeToBlame)

    /** Must first call [blame]. */
    val packageToBlamedResources: Multimap<PackageChunk, ResourceEntry>
        get() = Multimaps.unmodifiableMultimap(packageToBlame)

    /** Must first call [blame]. */
    val stringToBlamedResources: Array<ArrayList<ResourceEntry>>
        get() = stringToBlame

    /** Must first call [blame]. */
    val typeEntryToBlamedResources: Multimap<TypeChunk.Entry, ResourceEntry>
        get() = Multimaps.unmodifiableMultimap(typeEntryToBlame)

    /** Returns a multimap of keys for which there is no default resource. */
    fun getBaselessKeys(): Multimap<ResourceEntry, TypeChunk.Entry> {
        baselessKeys?.let { return it }

        val result = HashMultimap.create<ResourceEntry, TypeChunk.Entry>()
        for ((resourceEntry, chunkEntries) in getResourceEntries().asMap()) {
            if (!hasBaseConfiguration(chunkEntries)) {
                result.putAll(resourceEntry, chunkEntries)
            }
        }
        baselessKeys = result
        return result
    }

    /** Returns a multimap of resource entries to the chunk entries they reference in this APK. */
    fun getResourceEntries(): Multimap<ResourceEntry, TypeChunk.Entry> {
        resourceEntries?.let { return it }

        val result = HashMultimap.create<ResourceEntry, TypeChunk.Entry>()
        for (typeChunk in getTypeChunks()) {
            for (entry in typeChunk.entries.values) {
                result.put(ResourceEntry.create(entry), entry)
            }
        }
        resourceEntries = result
        return result
    }

    /** Returns all [TypeChunk] in resources.arsc. */
    fun getTypeChunks(): List<TypeChunk> {
        typeChunks?.let { return it }

        val result = ArrayList<TypeChunk>()
        for (packageChunk in resourceTable.packages) {
            result.addAll(packageChunk.typeChunks)
        }
        typeChunks = result
        return result
    }

    private fun hasBaseConfiguration(entries: Collection<TypeChunk.Entry>): Boolean {
        for (entry in entries) {
            if (entry.parent().configuration.isDefault) {
                return true
            }
        }
        return false
    }

    /** Describes a single resource entry. */
    data class ResourceEntry(
        val packageName: String,
        val typeName: String,
        val entryName: String
    ) {
        // 保留原有的同名函数以确保对旧 Java 代码调用的兼容性
        fun packageName() = packageName
        fun typeName() = typeName
        fun entryName() = entryName

        companion object {
            @JvmStatic
            fun create(entry: TypeChunk.Entry): ResourceEntry {
                val packageChunk = checkNotNull(entry.parent().packageChunk)
                val packageName = packageChunk.packageName
                val typeName = entry.typeName()
                val entryName = entry.key()
                return ResourceEntry(packageName, typeName, entryName)
            }
        }
    }

    companion object {
        private fun createEntryListArray(size: Int): Array<ArrayList<ResourceEntry>> {
            // ~90-95% 的 list 最终只有 1 或 2 个元素，故初始化容量为 2
            return Array(size) { ArrayList<ResourceEntry>(2) }
        }
    }
}