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

    public fun <T : Any> parseArguments(clazz: KClass<T>, args: Array<String>): ParserResult<T> {
        val errors = mutableListOf<ParseError>()
        val instance: T = try {
            clazz.createInstance()
        } catch (_: Exception) {
            errors.add(ParseError.InitializationFailed("Failed to create instance of ${clazz.simpleName}. Ensure it's a data class or has a no-arg constructor."))
            return ParserResult.NotParsed(errors)
        }

        val optionMap = mutableMapOf<String, Pair<KMutableProperty1<T, *>, Option>>()
        val valueMap = mutableMapOf<Int, Pair<KMutableProperty1<T, *>, Value>>()
        val requiredOptions = mutableSetOf<String>()
        val optionGroups = mutableMapOf<String, MutableList<String>>()

        clazz.annotations.filterIsInstance<OptionGroup>().forEach { group ->
            optionGroups[group.name] = mutableListOf()
        }

        val configFile = clazz.findAnnotation<ConfigFile>()
        val configValues = configFile?.let { loadConfigFromFile(it.path) } ?: emptyMap()

        for (property in clazz.memberProperties) {
            if (property is KMutableProperty1<T, *>) {
                property.findAnnotation<Option>()?.let { option ->
                    val optionName = option.longName.ifEmpty { option.shortName.toString() }
                    if (option.shortName != '\u0000') optionMap["-${option.shortName}"] = property to option
                    if (option.longName.isNotEmpty()) optionMap["--${option.longName}"] = property to option
                    if (option.required) requiredOptions.add(optionName)

                    clazz.annotations.filterIsInstance<OptionGroup>().forEach { group ->
                        optionGroups[group.name]?.add(optionName)
                    }
                }
                property.findAnnotation<Value>()?.let { value ->
                    valueMap[value.index] = property to value
                }
            }
        }

        val parsedOptions = mutableSetOf<String>()
        val positionalArgs = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            if (arg.startsWith("-")) {
                if (optionMap.containsKey(arg)) {
                    val (property, option) = optionMap[arg]!!
                    val optionName = option.longName.ifEmpty { option.shortName.toString() }
                    parsedOptions.add(optionName)

                    if (property.returnType.classifier == Boolean::class) {
                        @Suppress("UNCHECKED_CAST")
                        (property as KMutableProperty1<T, Boolean>).set(instance, true)
                        i++
                    } else if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                        i++
                        val value = args[i]
                        setPropertyValue(property, instance, value, option.separator, arg)?.let { errors.add(it) }
                        i++
                    } else {
                        errors.add(ParseError.MissingValue(arg))
                        i++
                    }
                } else {
                    errors.add(ParseError.UnknownOption(arg))
                    i++
                }
            } else {
                positionalArgs.add(arg)
                i++
            }
        }

        valueMap.forEach { (index, pair) ->
            val (property, value) = pair
            if (index < positionalArgs.size) {
                setPropertyValue(
                    property,
                    instance,
                    positionalArgs[index],
                    argName = "arg$index"
                )?.let { errors.add(it) }
            } else if (value.required) {
                errors.add(ParseError.MissingRequired("Argument at index $index"))
            }
        }

        for (property in clazz.memberProperties) {
            if (property is KMutableProperty1<T, *>) {
                val option = property.findAnnotation<Option>() ?: continue
                val optionName = option.longName.ifEmpty { option.shortName.toString() }
                if (optionName in parsedOptions) continue

                val envVar = property.findAnnotation<EnvVar>()
                val envValue = envVar?.let { System.getenv(it.name) }

                val valueToSet = when {
                    configValues.containsKey(optionName) -> configValues[optionName]
                    envValue != null -> envValue
                    option.default.isNotEmpty() -> option.default
                    else -> null
                }

                if (valueToSet != null) {
                    setPropertyValue(
                        property,
                        instance,
                        valueToSet,
                        option.separator,
                        optionName
                    )?.let { errors.add(it) }
                    parsedOptions.add(optionName) // Mark as handled
                }
            }
        }

        requiredOptions.forEach { optionName ->
            if (optionName !in parsedOptions) {
                errors.add(ParseError.MissingRequired(optionName))
            }
        }

        optionGroups.forEach { (groupName, optionsInGroup) ->
            val parsedGroupOptions = optionsInGroup.intersect(parsedOptions)
            if (parsedGroupOptions.size > 1) {
                errors.add(
                    ParseError.ValidationFailed(
                        groupName,
                        "Only one option from group '$groupName' can be used at a time."
                    )
                )
            }
            val groupAnnotation = clazz.annotations.filterIsInstance<OptionGroup>().find { it.name == groupName }
            if (groupAnnotation?.required == true && parsedGroupOptions.isEmpty()) {
                errors.add(ParseError.MissingRequired("One option from group '$groupName' must be provided."))
            }
        }

        return if (errors.isEmpty()) ParserResult.Parsed(instance) else ParserResult.NotParsed(errors)
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

                Double::class -> (property as KMutableProperty1<T, Double>).set(instance, value.toDouble())
                Boolean::class -> (property as KMutableProperty1<T, Boolean>).set(instance, value.toBoolean())
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
                sb.appendLine("  ${group.name}${if (group.required) " (required)" else ""}")
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
