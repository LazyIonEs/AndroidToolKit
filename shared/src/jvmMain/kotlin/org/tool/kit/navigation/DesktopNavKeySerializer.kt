package org.tool.kit.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer

internal actual fun <T : NavKey> platformNavKeySerializer(): KSerializer<T> = NavKeySerializer()

@OptIn(InternalSerializationApi::class)
private open class NavKeySerializer<T : NavKey> : KSerializer<T> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(serialName = "androidx.navigation.runtime.NavKey") {
            element(elementName = "type", descriptor = serialDescriptor<String>())
            element(
                elementName = "value",
                descriptor = buildClassSerialDescriptor(serialName = "Any"),
            )
        }

    /**
     * Deserializes a concrete [NavKey] implementation.
     *
     * It first reads the class name (`type`), then uses that name to find the corresponding
     * [KSerializer] for the class using **reflection**. Finally, it uses that specific serializer
     * to read the actual object data (`value`).
     */
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(decoder: Decoder): T {
        return decoder.decodeStructure(descriptor) {
            val className = decodeStringElement(descriptor, decodeElementIndex(descriptor))
            val serializer = Class.forName(className).kotlin.serializer()
            decodeSerializableElement(descriptor, decodeElementIndex(descriptor), serializer) as T
        }
    }

    /**
     * Serializes a concrete [NavKey] implementation.
     *
     * It writes the object's fully qualified class name (`type`) first, then uses the object's
     * runtime serializer to write the object's data (`value`).
     */
    @Suppress("UNCHECKED_CAST")
    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeStructure(descriptor) {
            val className = value::class.java.name
            encodeStringElement(descriptor, index = 0, className)
            val serializer = value::class.serializer() as KSerializer<T>
            encodeSerializableElement(descriptor, index = 1, serializer, value)
        }
    }
}