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
import com.joelromanpr.commandline.ktx.converters.TypeConverter
import com.joelromanpr.commandline.ktx.core.ParseError
import com.joelromanpr.commandline.ktx.core.ParserResult
import com.joelromanpr.commandline.ktx.core.ValueSource
import java.io.File
import kotlin.collections.get
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

public class Parser(
    private val converters: Map<KClass<*>, TypeConverter<*>> = emptyMap()
) {

    public companion object {
        public val Default: Parser = Parser()
    }

    public inline fun <reified T : Any> parseArguments(args: Array<String>): ParserResult<T> {
        return parseArguments(T::class, args)
    }

    public inline fun <reified T : Any> generateHelpText(): String {
        return generateHelpText(T::class)
    }

    /** Compile a deterministic command description for help and structured callers. */
    public inline fun <reified T : Any> describe(): CommandSpec<T> = describe(T::class)

    public fun <T : Any> describe(clazz: KClass<T>): CommandSpec<T> = CommandSpec.from(clazz, converters.keys)

    /** Parse typed fields without reading process environment or configuration files. */
    public inline fun <reified T : Any> parseStructured(values: Map<String, Any?>): ParserResult<T> =
        parseStructured(T::class, values)

    public fun <T : Any> parseArguments(clazz: KClass<T>, args: Array<String>): ParserResult<T> {
        val errors = mutableListOf<ParseError>()
        try {
            describe(clazz)
        } catch (exception: IllegalArgumentException) {
            return ParserResult.NotParsed(
                listOf(ParseError.ValidationFailed("schema", exception.message ?: "Invalid command definition."))
            )
        }
        val instance: T = try {
            clazz.createInstance()
        } catch (_: Exception) {
            errors.add(ParseError.InitializationFailed("Failed to create instance of ${clazz.simpleName}. Ensure it's a data class or has a no-arg constructor."))
            return ParserResult.NotParsed(errors)
        }

        val properties = clazz.memberProperties.filterIsInstance<KMutableProperty1<T, *>>().sortedBy { it.name }
        val optionMap = mutableMapOf<String, Pair<KMutableProperty1<T, *>, Option>>()
        val options = mutableListOf<Pair<KMutableProperty1<T, *>, Option>>()
        val valueMap = mutableMapOf<Int, Pair<KMutableProperty1<T, *>, Value>>()
        val groupDefinitions = clazz.annotations.filterIsInstance<OptionGroup>().associateBy { it.name }
        val groupMembers = groupDefinitions.keys.associateWith { mutableSetOf<String>() }

        for (property in properties) {
            property.findAnnotation<Option>()?.let { option ->
                options.add(property to option)
                option.shortName.takeIf { it != '\u0000' }?.let { optionMap["-$it"] = property to option }
                option.longName.takeIf { it.isNotEmpty() }?.let { optionMap["--$it"] = property to option }
                option.group.takeIf { it.isNotEmpty() }?.let { groupMembers.getValue(it).add(property.name) }
            }
            property.findAnnotation<Value>()?.let { value ->
                valueMap[value.index] = property to value
                value.group.takeIf { it.isNotEmpty() }?.let { groupMembers.getValue(it).add(property.name) }
            }
        }

        val configFile = clazz.findAnnotation<ConfigFile>()
        val configValues = try {
            configFile?.let { loadConfigFromFile(it.path) } ?: emptyMap()
        } catch (exception: Exception) {
            errors.add(ParseError.ValidationFailed("config", "Could not read configuration file: ${exception.message}"))
            emptyMap()
        }
        val attemptedOptions = mutableSetOf<String>()
        val successfulProperties = mutableSetOf<String>()
        val sources = mutableMapOf<String, ValueSource>()
        val positionalArgs = mutableListOf<Pair<String, Int>>()
        var positionalOnly = false
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (!positionalOnly && arg == "--") {
                positionalOnly = true
                i++
                continue
            }
            val positionalProperty = valueMap[positionalArgs.size]?.first
            if (positionalOnly || arg == "-" || !arg.startsWith("-") ||
                (positionalProperty != null && isNegativeNumberFor(positionalProperty, arg))
            ) {
                positionalArgs.add(arg to i)
                i++
                continue
            }

            val optionToken = arg.substringBefore('=')
            val attachedValue = if ('=' in arg) arg.substringAfter('=') else null
            val entry = optionMap[optionToken]
            if (entry == null) {
                errors.add(ParseError.UnknownOption(optionToken).atToken(i))
                i++
                continue
            }
            val (property, option) = entry
            val optionTokenIndex = i
            attemptedOptions.add(property.name)
            val value = when {
                attachedValue != null -> attachedValue
                property.returnType.classifier == Boolean::class -> "true"
                else -> {
                    val next = args.getOrNull(i + 1)
                    if (next == null || next == "--" ||
                        (next.startsWith("-") && next != "-" && !isNegativeNumberFor(property, next))
                    ) {
                        errors.add(ParseError.MissingValue(optionToken).atToken(optionTokenIndex))
                        i++
                        continue
                    }
                    i++
                    next
                }
            }
            val error = setPropertyValue(property, instance, value, option.separator, optionToken)
            if (error == null) {
                successfulProperties.add(property.name)
                sources[property.name] = ValueSource.CLI
            } else errors.add(redactError(error, option.sensitive).atToken(optionTokenIndex))
            i++
        }

        for ((index, pair) in valueMap.toSortedMap()) {
            val (property, value) = pair
            if (index < positionalArgs.size) {
                val error = setPropertyValue(property, instance, positionalArgs[index].first, argName = property.name)
                if (error == null) {
                    successfulProperties.add(property.name)
                    sources[property.name] = ValueSource.CLI
                } else errors.add(error.atToken(positionalArgs[index].second))
            } else if (value.required) {
                errors.add(ParseError.MissingRequired(property.name))
            }
        }
        for (index in positionalArgs.indices) {
            if (index !in valueMap) {
                errors.add(ParseError.ValidationFailed("arg$index", "Unexpected positional argument.")
                    .atToken(positionalArgs[index].second))
            }
        }

        for ((property, option) in options) {
            if (property.name in attemptedOptions) continue
            if (option.group.isNotEmpty() && groupMembers.getValue(option.group).any {
                    it != property.name && sources[it] == ValueSource.CLI
                }) continue
            val optionName = option.longName.ifEmpty { option.shortName.toString() }
            val envVar = property.findAnnotation<EnvVar>()
            val envValue = envVar?.let { System.getenv(it.name) }
            val fallback = when {
                configValues.containsKey(optionName) -> configValues.getValue(optionName) to ValueSource.CONFIG
                envValue != null -> envValue to ValueSource.ENVIRONMENT
                option.default.isNotEmpty() -> option.default to ValueSource.ANNOTATION_DEFAULT
                else -> null
            }
            if (fallback != null) {
                val (valueToSet, source) = fallback
                attemptedOptions.add(property.name)
                val error = setPropertyValue(property, instance, valueToSet, option.separator, optionName)
                if (error == null) {
                    successfulProperties.add(property.name)
                    sources[property.name] = source
                } else errors.add(redactError(error, option.sensitive))
            }
        }

        for ((property, option) in options) {
            if (option.required && property.name !in attemptedOptions && option.default.isEmpty()) {
                errors.add(ParseError.MissingRequired(option.longName.ifEmpty { option.shortName.toString() }))
            }
        }
        for ((groupName, members) in groupMembers) {
            val selectedMembers = members.intersect(successfulProperties)
            if (selectedMembers.size > 1) {
                errors.add(ParseError.ValidationFailed(groupName, "Only one member of group '$groupName' can be used at a time."))
            }
            if (groupDefinitions[groupName]?.required == true && selectedMembers.isEmpty()) {
                errors.add(ParseError.MissingRequired(groupName))
            }
        }

        return if (errors.isEmpty()) ParserResult.Parsed(instance).withSources(sources)
        else ParserResult.NotParsed(errors)
    }

    /**
     * Parse JSON-compatible values by their canonical names. Annotation defaults still apply;
     * ambient config files and environment variables do not affect a structured tool call.
     */
    public fun <T : Any> parseStructured(clazz: KClass<T>, values: Map<String, Any?>): ParserResult<T> {
        val spec = try {
            describe(clazz)
        } catch (exception: IllegalArgumentException) {
            return ParserResult.NotParsed(
                listOf(ParseError.ValidationFailed("schema", exception.message ?: "Invalid command definition."))
            )
        }
        val instance = try {
            clazz.createInstance()
        } catch (_: Exception) {
            return ParserResult.NotParsed(
                listOf(ParseError.InitializationFailed("Failed to create instance of ${clazz.simpleName}."))
            )
        }
        val properties = clazz.memberProperties.filterIsInstance<KMutableProperty1<T, *>>().associateBy { it.name }
        val knownNames = (spec.options.map { it.name } + spec.positionals.map { it.name }).toSet()
        val errors = mutableListOf<ParseError>()
        val sources = mutableMapOf<String, ValueSource>()
        val supplied = mutableSetOf<String>()
        values.keys.filterNot { it in knownNames }.sorted().forEach { errors.add(ParseError.UnknownOption(it)) }

        for (option in spec.options) {
            val property = properties.getValue(option.propertyName)
            val explicit = option.name in values
            val otherGroupMemberSupplied = option.group != null && spec.groups
                .first { it.name == option.group }.members.any { it != option.name && it in values }
            val raw = when {
                explicit -> values[option.name]
                otherGroupMemberSupplied -> null
                else -> option.defaultValue
            }
            if (raw == null) {
                if (explicit) {
                    errors.add(ParseError.InvalidType(option.name, option.kind.name, "null"))
                } else if (option.required && option.defaultValue == null) {
                    errors.add(ParseError.MissingRequired(option.name))
                }
                continue
            }
            val error = if (explicit) {
                setStructuredPropertyValue(
                    property, instance, raw, option.kind, option.name,
                    option.minimum, option.maximum, option.sensitive
                )
            } else {
                setPropertyValue(property, instance, raw as String, option.separator ?: "", option.name)
            }
            if (error == null) {
                supplied.add(option.name)
                sources[option.propertyName] = if (explicit) ValueSource.STRUCTURED else ValueSource.ANNOTATION_DEFAULT
            } else {
                errors.add(redactError(error, option.sensitive))
            }
        }
        for (positional in spec.positionals) {
            val explicit = positional.name in values
            if (!explicit) {
                if (positional.required) errors.add(ParseError.MissingRequired(positional.name))
                continue
            }
            val raw = values[positional.name]
            if (raw == null) {
                errors.add(ParseError.InvalidType(positional.name, positional.kind.name, "null"))
                continue
            }
            val error = setStructuredPropertyValue(
                properties.getValue(positional.propertyName), instance, raw,
                positional.kind, positional.name, positional.minimum, positional.maximum, false
            )
            if (error == null) {
                supplied.add(positional.name)
                sources[positional.propertyName] = ValueSource.STRUCTURED
            } else {
                errors.add(error)
            }
        }
        for (group in spec.groups) {
            val selected = group.members.count { it in supplied }
            if (selected > 1) {
                errors.add(ParseError.ValidationFailed(group.name, "Only one member of group '${group.name}' can be used."))
            }
            if (group.required && selected == 0) {
                errors.add(ParseError.MissingRequired(group.name))
            }
        }
        return if (errors.isEmpty()) ParserResult.Parsed(instance).withSources(sources)
        else ParserResult.NotParsed(errors)
    }

    private fun <T : Any> setStructuredPropertyValue(
        property: KMutableProperty1<T, *>,
        instance: T,
        raw: Any,
        kind: InputKind,
        name: String,
        minimum: Int?,
        maximum: Int?,
        sensitive: Boolean
    ): ParseError? {
        val actual = if (sensitive) "<redacted>" else raw::class.simpleName ?: "unknown"
        if (kind == InputKind.CUSTOM) {
            if (raw !is String) return ParseError.InvalidType(name, "String", actual)
            return setPropertyValue(property, instance, raw, argName = name)
        }
        val converted: Any = when (kind) {
            InputKind.STRING -> raw as? String ?: return ParseError.InvalidType(name, "String", actual)
            InputKind.INTEGER -> {
                val integer = when (raw) {
                    is Byte, is Short, is Int, is Long -> (raw as Number).toLong()
                    else -> return ParseError.InvalidType(name, "Int", actual)
                }
                if (integer !in Int.MIN_VALUE..Int.MAX_VALUE) return ParseError.InvalidType(name, "Int", actual)
                if (minimum != null && integer < minimum || maximum != null && integer > maximum) {
                    return ParseError.ValidationFailed(name, "Value must be within the declared range.")
                }
                integer.toInt()
            }
            InputKind.NUMBER -> {
                val number = (raw as? Number)?.toDouble()
                    ?: return ParseError.InvalidType(name, "Double", actual)
                if (!number.isFinite()) return ParseError.InvalidType(name, "finite Double", actual)
                number
            }
            InputKind.BOOLEAN -> raw as? Boolean ?: return ParseError.InvalidType(name, "Boolean", actual)
            InputKind.STRING_LIST -> {
                val list = raw as? List<*> ?: return ParseError.InvalidType(name, "List<String>", actual)
                if (list.any { it !is String }) return ParseError.InvalidType(name, "List<String>", actual)
                list.filterIsInstance<String>()
            }
            InputKind.CUSTOM -> error("Handled above")
        }
        return try {
            @Suppress("UNCHECKED_CAST")
            (property as KMutableProperty1<T, Any>).set(instance, converted)
            null
        } catch (_: Exception) {
            ParseError.InvalidType(name, kind.name, actual)
        }
    }

    private fun redactError(error: ParseError, sensitive: Boolean): ParseError {
        if (!sensitive) return error
        return when (error) {
            is ParseError.InvalidType -> error.copy(actual = "<redacted>")
            is ParseError.ValidationFailed -> error.copy(reason = "Value did not pass validation.")
            else -> error
        }
    }

    private fun <T : Any> isNegativeNumberFor(property: KMutableProperty1<T, *>, value: String): Boolean {
        if (!value.startsWith("-") || value.length == 1) return false
        return when (property.returnType.classifier) {
            Int::class -> value.toIntOrNull() != null
            Double::class -> value.toDoubleOrNull() != null
            else -> false
        }
    }

    private fun loadConfigFromFile(path: String): Map<String, String> {
        val file = File(path)
        if (!file.exists()) return emptyMap()
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }.toMap()
    }

    private fun <T : Any> setPropertyValue(
        property: KMutableProperty1<T, *>,
        instance: T,
        value: String,
        separator: String = "",
        argName: String
    ): ParseError? {
        val type = property.returnType.classifier as? KClass<*>
        if (converters.containsKey(type)) {
            return try {
                @Suppress("UNCHECKED_CAST")
                (property as KMutableProperty1<T, Any>).set(instance, converters[type]!!.convert(value))
                null
            } catch (_: Exception) {
                ParseError.InvalidType(argName, type!!.simpleName!!, value)
            }
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            when (property.returnType.classifier) {
                String::class -> (property as KMutableProperty1<T, String>).set(instance, value)
                Int::class -> {
                    val intValue = value.toInt()
                    val range = property.findAnnotation<Range>()
                    if (range != null && (intValue < range.min || intValue > range.max)) {
                        return ParseError.ValidationFailed(
                            argName,
                            "Value must be between ${range.min} and ${range.max}, got $intValue"
                        )
                    }
                    (property as KMutableProperty1<T, Int>).set(instance, intValue)
                }

                Double::class -> {
                    val doubleValue = value.toDouble()
                    if (!doubleValue.isFinite()) return ParseError.InvalidType(argName, "finite Double", value)
                    (property as KMutableProperty1<T, Double>).set(instance, doubleValue)
                }
                Boolean::class -> {
                    val booleanValue = value.toBooleanStrictOrNull()
                        ?: return ParseError.InvalidType(argName, "Boolean (true or false)", value)
                    (property as KMutableProperty1<T, Boolean>).set(instance, booleanValue)
                }
                List::class -> {
                    val items = if (separator.isNotEmpty()) value.split(separator) else listOf(value)
                    (property as KMutableProperty1<T, List<String>>).set(instance, items)
                }

                else -> return ParseError.InvalidType(argName, "supported type", property.returnType.toString())
            }
            null
        } catch (_: Exception) {
            ParseError.InvalidType(argName, property.returnType.toString(), value)
        }
    }

    public fun <T : Any> generateHelpText(clazz: KClass<T>): String {
        describe(clazz)
        val sb = StringBuilder()
        val appInfo = clazz.findAnnotation<Application>()
        if (appInfo != null) {
            sb.appendLine("${appInfo.name} ${appInfo.version}")
            if (appInfo.description.isNotEmpty()) sb.appendLine(appInfo.description)
            sb.appendLine()
        }

        val options = clazz.memberProperties.mapNotNull { prop -> prop.findAnnotation<Option>()?.let { prop to it } }
        val values = clazz.memberProperties.mapNotNull { prop -> prop.findAnnotation<Value>()?.let { prop to it } }
            .sortedBy { it.second.index }
        val optionGroups = clazz.annotations.filterIsInstance<OptionGroup>()
        val configFile = clazz.findAnnotation<ConfigFile>()

        sb.append("Usage: ${appInfo?.name?.lowercase()?.replace(" ", "-") ?: "app"}")
        if (options.isNotEmpty()) sb.append(" [options]")
        values.forEach { (_, value) ->
            sb.append(if (value.required) " <arg${value.index}>" else " [arg${value.index}]")
        }
        sb.appendLine("\n")

        if (configFile != null) {
            sb.appendLine("Configuration file: ${configFile.path}\n")
        }

        if (options.isNotEmpty()) {
            sb.appendLine("Options:")
            options.forEach { (prop, option) ->
                val names = listOfNotNull(
                    option.shortName.takeIf { it != '\u0000' }?.let { "-$it" },
                    option.longName.takeIf { it.isNotEmpty() }?.let { "--$it" }).joinToString(", ")
                val envVar = prop.findAnnotation<EnvVar>()?.let { "(env: ${it.name})" } ?: ""
                sb.appendLine("  ${names.padEnd(20)} ${option.helpText} $envVar")
            }
        }

        if (optionGroups.isNotEmpty()) {
            sb.appendLine("\nOption Groups:")
            optionGroups.forEach { group ->
                val members = options.mapNotNull { (_, option) ->
                    if (option.group == group.name) option.longName.takeIf { it.isNotEmpty() }?.let { "--$it" }
                        ?: option.shortName.takeIf { it != '\u0000' }?.let { "-$it" } else null
                } + values.mapNotNull { (_, value) ->
                    if (value.group == group.name) "<arg${value.index}>" else null
                }
                sb.appendLine("  ${group.name}${if (group.required) " (required)" else ""}: ${members.joinToString(", ")}")
            }
        }

        if (values.isNotEmpty()) {
            sb.appendLine("\nArguments:")
            values.forEach { (_, value) ->
                sb.appendLine("  <arg${value.index}>${if (value.required) " (required)" else ""} ${value.helpText}")
            }
        }
        return sb.toString()
    }
}
