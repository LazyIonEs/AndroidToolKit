package org.tool.kit.data.generator

import org.tool.kit.domain.junk.CURRENT_JUNK_GENERATOR_VERSION

/** The first four choices are base-4 digits. Their sum determines the fifth choice. */
data class JunkGenerationProfile(val id: Int, val generatorVersion: Int = GENERATOR_VERSION) {
    init {
        require(id in 0 until CAPACITY) { "Profile ID must be in 0..${CAPACITY - 1}: $id" }
        requireSupportedVersion(generatorVersion)
    }

    internal enum class Roles(val valueType: JunkValueType) {
        MODELS(JunkValueType.NUMBER), RULES(JunkValueType.PREDICATE),
        COLLECTIONS(JunkValueType.ACCUMULATOR), CONFIGURATION(JunkValueType.TEXT),
    }
    internal enum class Input(val descriptor: String, val slots: Int = 1) {
        INTEGER("(I)I"), PAIR("(II)I", 2), WIDE("(J)I", 2), TEXT("(Ljava/lang/String;)I");

        val parameters = descriptor.substring(1, descriptor.indexOf(')'))
    }
    internal enum class Calls(val context: JunkTopologyContext) {
        FAN_OUT(JunkTopologyContext.NONE), CHAIN(JunkTopologyContext.CHAIN),
        PAIRS(JunkTopologyContext.PAIRS), TREE(JunkTopologyContext.TREE),
    }
    internal enum class Program(val resultType: JunkValueType) {
        ARITHMETIC(JunkValueType.NUMBER), BRANCHING(JunkValueType.PREDICATE),
        ACCUMULATION(JunkValueType.ACCUMULATOR), TEXT(JunkValueType.TEXT),
    }
    internal enum class State(val descriptor: String) {
        SCALAR("I"), WIDE("J"), ARRAY("[I"), BOXED("Ljava/lang/Integer;")
    }

    internal val roles = Roles.entries[id / 64]
    internal val input = Input.entries[id / 16 % 4]
    internal val calls = Calls.entries[id / 4 % 4]
    internal val program = Program.entries[id % 4]
    internal val state = State.entries[(roles.ordinal + input.ordinal + calls.ordinal + program.ordinal) % 4]
    internal val usesTypedStages = generatorVersion >= 2
    internal val usesProfiledComponentCalls = generatorVersion >= 3
    internal val usesPairedViewStages = generatorVersion >= 4 && calls == Calls.PAIRS
    internal val usesTopologyStages = generatorVersion >= 5
    internal val usesProgramContracts = generatorVersion >= 6
    internal val usesRoleCalculationContext = generatorVersion >= 7
    internal val usesPreparedOperands = generatorVersion >= 8
    internal val usesTopologyCalculationContext = generatorVersion >= 9
    internal val usesProfiledMethodBodies = generatorVersion >= 10
    internal val usesRoleSpecificMethods = generatorVersion >= 11
    internal val usesRequiredFieldsOnly = generatorVersion >= 11
    internal val reusesIdenticalResources = generatorVersion >= 11
    internal val usesProgramSpecificMethods = generatorVersion >= 12
    internal val usesProfiledBranchText = generatorVersion >= 13
    internal val usesProfiledLongAndPredicateMethods = generatorVersion >= 14
    internal val usesProgramSpecificLongMethods = generatorVersion >= 15
    internal val usesProfiledTextOperands = generatorVersion >= 16
    internal val usesProfiledLongOperands = generatorVersion >= 17
    internal val usesProfiledFilters = generatorVersion >= 18
    internal val groupsStringResources = generatorVersion >= 18
    internal val usesDrawablePalettes = generatorVersion >= 19
    internal val sharesVectorIcons = generatorVersion >= 20
    internal val sharesResourcePresets = generatorVersion >= 21
    internal val usesPurposeSpecificLayouts = generatorVersion >= 22
    internal val usesAdjustmentPageLayouts = generatorVersion >= 23
    internal val usesOptionalDtoStringAdapters = generatorVersion >= 24
    internal val prefersNonblankTextValue = generatorVersion >= 25 && input == Input.TEXT
    internal val usesOptionalDtoComparison = generatorVersion >= 26
    internal val usesTopologyIntSteps = generatorVersion >= 26
    internal val usesProgramSpecificModelCounts = generatorVersion >= 26

    internal fun snapshot() = linkedMapOf(
        "id" to id,
        "generatorVersion" to generatorVersion,
        "roles" to roles.name,
        "input" to input.name,
        "calls" to calls.name,
        "program" to program.name,
        "state" to state.name,
    )

    companion object {
        const val CAPACITY = 256
        const val GENERATOR_VERSION = CURRENT_JUNK_GENERATOR_VERSION

        internal fun requireSupportedVersion(version: Int) {
            require(version in 1..GENERATOR_VERSION) { "Unsupported project generator version $version" }
        }
    }
}
