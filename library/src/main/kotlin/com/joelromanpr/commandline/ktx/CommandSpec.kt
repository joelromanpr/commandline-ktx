/*
 * Copyright (C) 2025 joelromanpr (Joel Roman)
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.joelromanpr.commandline.ktx

import com.joelromanpr.commandline.ktx.annotations.Application
import com.joelromanpr.commandline.ktx.annotations.ConfigFile
import com.joelromanpr.commandline.ktx.annotations.EnvVar
import com.joelromanpr.commandline.ktx.annotations.Option
import com.joelromanpr.commandline.ktx.annotations.OptionGroup
import com.joelromanpr.commandline.ktx.annotations.Range
import com.joelromanpr.commandline.ktx.annotations.Value
import java.util.Collections
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/** The JSON-compatible shape of an annotated property. */
public enum class InputKind {
    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    STRING_LIST,
    CUSTOM
}

/** A named command-line input, ordered by its canonical name in [CommandSpec.options]. */
public data class OptionSpec(
    public val propertyName: String,
    public val longName: String?,
    public val shortName: Char?,
    public val kind: InputKind,
    public val kotlinType: KClass<*>,
    public val required: Boolean,
    public val description: String,
    public val defaultValue: String?,
    public val environmentVariable: String?,
    public val group: String?,
    public val separator: String?,
    public val minimum: Int?,
    public val maximum: Int?,
    public val sensitive: Boolean
) {
    /** The stable key used for structured input and schema export. */
    public val name: String get() = longName ?: shortName.toString()
}

/** A positional input, ordered by [index] in [CommandSpec.positionals]. */
public data class PositionalSpec(
    public val propertyName: String,
    public val index: Int,
    public val kind: InputKind,
    public val kotlinType: KClass<*>,
    public val required: Boolean,
    public val description: String,
    public val group: String?,
    public val minimum: Int?,
    public val maximum: Int?
) {
    /** The stable key used for structured input and schema export. */
    public val name: String get() = propertyName
}

/** A mutually exclusive group declared on the command class. */
public data class GroupSpec(
    public val name: String,
    public val required: Boolean,
    public val description: String,
    public val members: List<String>
)

/**
 * Immutable description of one annotated command. Parsing, help, and tool adapters can share
 * these names and constraints instead of inspecting annotations independently.
 */
public class CommandSpec<T : Any> private constructor(
    public val commandClass: KClass<T>,
    public val applicationName: String?,
    public val applicationDescription: String?,
    public val configFile: String?,
    public val options: List<OptionSpec>,
    public val positionals: List<PositionalSpec>,
    public val groups: List<GroupSpec>
) {
    public companion object {
        /** Compile and validate a command definition before accepting any input. */
        public fun <T : Any> from(
            commandClass: KClass<T>,
            converterTypes: Set<KClass<*>> = emptySet()
        ): CommandSpec<T> {
            val declaredGroups = commandClass.annotations.filterIsInstance<OptionGroup>()
                .sortedBy { it.name }
            require(declaredGroups.map { it.name }.distinct().size == declaredGroups.size) {
                "Option group names must be unique"
            }
            require(declaredGroups.none { it.name.isBlank() }) { "Option group names must not be blank" }
            val groupNames = declaredGroups.map { it.name }.toSet()

            val options = mutableListOf<OptionSpec>()
            val positionals = mutableListOf<PositionalSpec>()
            val aliases = mutableSetOf<String>()
            val positions = mutableSetOf<Int>()
            for (property in commandClass.memberProperties.sortedBy { it.name }) {
                val option = property.findAnnotation<Option>()
                val value = property.findAnnotation<Value>()
                require(option == null || value == null) {
                    "Property '${property.name}' cannot be both an option and a positional value"
                }
                if (option == null && value == null) continue
                require(property is kotlin.reflect.KMutableProperty1<*, *>) {
                    "Annotated property '${property.name}' must be mutable"
                }
                val type = property.returnType.classifier as? KClass<*>
                    ?: throw IllegalArgumentException("Unsupported type on '${property.name}'")
                val kind = if (type in converterTypes) InputKind.CUSTOM else kindOf(property.returnType, type)
                val range = property.findAnnotation<Range>()
                require(range == null || kind == InputKind.INTEGER) {
                    "@Range on '${property.name}' requires an Int property"
                }
                require(range == null || range.min <= range.max) {
                    "@Range on '${property.name}' has a minimum greater than its maximum"
                }
                if (option != null) {
                    val longName = option.longName.takeIf { it.isNotEmpty() }
                    val shortName = option.shortName.takeIf { it != '\u0000' }
                    require(longName != null || shortName != null) {
                        "Option '${property.name}' needs a short or long name"
                    }
                    require(longName == null || longName.matches(Regex("[A-Za-z][A-Za-z0-9-]*"))) {
                        "Option '${property.name}' has an invalid long name"
                    }
                    require(shortName == null || shortName.isLetter()) {
                        "Option '${property.name}' has an invalid short name"
                    }
                    listOfNotNull(longName?.let { "--$it" }, shortName?.let { "-$it" }).forEach {
                        require(aliases.add(it)) { "Duplicate option alias '$it'" }
                    }
                    val group = option.group.takeIf { it.isNotEmpty() }
                    require(group == null || group in groupNames) {
                        "Option '${property.name}' references undeclared group '$group'"
                    }
                    require(!option.sensitive || option.default.isEmpty()) {
                        "Sensitive option '${property.name}' cannot declare an annotation default"
                    }
                    if (option.default.isNotEmpty() && kind != InputKind.CUSTOM) {
                        require(validDefault(kind, option.default, range)) {
                            "Option '${property.name}' has an invalid annotation default"
                        }
                    }
                    options.add(
                        OptionSpec(
                            property.name, longName, shortName, kind, type, option.required,
                            option.helpText, option.default.takeIf { it.isNotEmpty() },
                            property.findAnnotation<EnvVar>()?.name, group,
                            option.separator.takeIf { it.isNotEmpty() },
                            range?.min, range?.max, option.sensitive
                        )
                    )
                }
                if (value != null) {
                    require(value.index >= 0) { "Positional index on '${property.name}' must be non-negative" }
                    require(positions.add(value.index)) { "Duplicate positional index ${value.index}" }
                    val group = value.group.takeIf { it.isNotEmpty() }
                    require(group == null || group in groupNames) {
                        "Positional '${property.name}' references undeclared group '$group'"
                    }
                    positionals.add(
                        PositionalSpec(
                            property.name, value.index, kind, type, value.required,
                            value.helpText, group, range?.min, range?.max
                        )
                    )
                }
            }
            val orderedOptions = options.sortedBy { it.name }
            val orderedPositionals = positionals.sortedBy { it.index }
            require(orderedPositionals.map { it.index } == orderedPositionals.indices.toList()) {
                "Positional indexes must be contiguous from zero"
            }
            require(orderedPositionals.dropWhile { it.required }.none { it.required }) {
                "Required positional values must precede optional values"
            }
            val structuredNames = (orderedOptions.map { it.name } + orderedPositionals.map { it.name })
            require(structuredNames.distinct().size == structuredNames.size) {
                "Structured input names must be unique across options and positionals"
            }
            val groups = declaredGroups.map { group ->
                val members = (orderedOptions.filter { it.group == group.name }.map { it.name } +
                    orderedPositionals.filter { it.group == group.name }.map { it.name }).sorted()
                require(members.isNotEmpty()) { "Option group '${group.name}' has no members" }
                require(orderedOptions.count { it.group == group.name && it.defaultValue != null } <= 1) {
                    "Option group '${group.name}' has more than one annotation default"
                }
                GroupSpec(group.name, group.required, group.helpText, Collections.unmodifiableList(members))
            }
            val app = commandClass.findAnnotation<Application>()
            return CommandSpec(
                commandClass, app?.name, app?.description,
                commandClass.findAnnotation<ConfigFile>()?.path,
                Collections.unmodifiableList(orderedOptions),
                Collections.unmodifiableList(orderedPositionals),
                Collections.unmodifiableList(groups)
            )
        }

        private fun validDefault(kind: InputKind, value: String, range: Range?): Boolean = when (kind) {
            InputKind.STRING, InputKind.STRING_LIST -> true
            InputKind.INTEGER -> value.toIntOrNull()?.let { number ->
                range == null || number in range.min..range.max
            } ?: false
            InputKind.NUMBER -> value.toDoubleOrNull()?.isFinite() == true
            InputKind.BOOLEAN -> value.toBooleanStrictOrNull() != null
            InputKind.CUSTOM -> false
        }

        private fun kindOf(type: kotlin.reflect.KType, classifier: KClass<*>): InputKind = when (classifier) {
            String::class -> InputKind.STRING
            Int::class -> InputKind.INTEGER
            Double::class -> InputKind.NUMBER
            Boolean::class -> InputKind.BOOLEAN
            List::class -> {
                require(type.arguments.singleOrNull()?.type?.classifier == String::class) {
                    "Only List<String> is supported for annotated inputs"
                }
                InputKind.STRING_LIST
            }
            else -> InputKind.CUSTOM
        }
    }

    /**
     * Export an object-root JSON Schema for structured callers. Custom converters need an
     * explicit schema so their input type is never guessed.
     */
    public fun toJsonSchema(
        customTypeSchemas: Map<KClass<*>, Map<String, Any?>> = emptyMap()
    ): Map<String, Any?> {
        require(options.none { it.kind == InputKind.CUSTOM && it.defaultValue != null }) {
            "Custom converter defaults cannot be represented safely in JSON Schema"
        }
        val properties = linkedMapOf<String, Any?>()
        for (option in options) {
            val schema = valueSchema(option.kind, option.kotlinType, option.description,
                option.minimum, option.maximum, customTypeSchemas)
            properties[option.name] = schema
        }
        for (positional in positionals) {
            properties[positional.name] = valueSchema(
                positional.kind, positional.kotlinType, positional.description,
                positional.minimum, positional.maximum, customTypeSchemas
            )
        }
        val result = linkedMapOf<String, Any?>(
            "\$schema" to "https://json-schema.org/draft/2020-12/schema",
            "type" to "object",
            "properties" to properties,
            "additionalProperties" to false
        )
        applicationDescription?.takeIf { it.isNotBlank() }?.let { result["description"] = it }
        val required = (options.filter { it.required && it.defaultValue == null }.map { it.name } +
            positionals.filter { it.required }.map { it.name }).distinct().sorted()
        if (required.isNotEmpty()) result["required"] = required
        val groupRules = mutableListOf<Map<String, Any?>>()
        for (group in groups) {
            val hasDefaultMember = options.any { it.name in group.members && it.defaultValue != null }
            if (group.required && !hasDefaultMember) {
                groupRules.add(mapOf("oneOf" to group.members.map { member -> mapOf("required" to listOf(member)) }))
            } else {
                for (left in group.members.indices) {
                    for (right in left + 1 until group.members.size) {
                        groupRules.add(mapOf("not" to mapOf("required" to listOf(group.members[left], group.members[right]))))
                    }
                }
            }
        }
        if (groupRules.isNotEmpty()) result["allOf"] = groupRules
        @Suppress("UNCHECKED_CAST")
        return freeze(result) as Map<String, Any?>
    }

    private fun freeze(value: Any?): Any? = when (value) {
        is Map<*, *> -> {
            val copy = linkedMapOf<String, Any?>()
            value.forEach { (key, item) -> copy[key as String] = freeze(item) }
            Collections.unmodifiableMap(copy)
        }
        is List<*> -> Collections.unmodifiableList(value.map(::freeze))
        else -> value
    }

    private fun valueSchema(
        kind: InputKind,
        kotlinType: KClass<*>,
        description: String,
        minimum: Int?,
        maximum: Int?,
        customTypeSchemas: Map<KClass<*>, Map<String, Any?>>
    ): Map<String, Any?> {
        val schema: MutableMap<String, Any?> = when (kind) {
            InputKind.STRING -> linkedMapOf("type" to "string")
            InputKind.INTEGER -> linkedMapOf("type" to "integer")
            InputKind.NUMBER -> linkedMapOf("type" to "number")
            InputKind.BOOLEAN -> linkedMapOf("type" to "boolean")
            InputKind.STRING_LIST -> linkedMapOf("type" to "array", "items" to mapOf("type" to "string"))
            InputKind.CUSTOM -> {
                val custom = customTypeSchemas[kotlinType]
                    ?: throw IllegalArgumentException("No JSON Schema supplied for custom type '${kotlinType.qualifiedName}'")
                require(custom["type"] == "string") {
                    "Custom converter schema for '${kotlinType.qualifiedName}' must have type string"
                }
                custom.toMutableMap()
            }
        }
        if (description.isNotBlank()) schema["description"] = description
        if (kind == InputKind.INTEGER) {
            schema["minimum"] = minimum ?: Int.MIN_VALUE
            schema["maximum"] = maximum ?: Int.MAX_VALUE
        }
        return schema
    }
}
