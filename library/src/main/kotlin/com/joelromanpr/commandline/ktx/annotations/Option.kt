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
package com.joelromanpr.commandline.ktx.annotations

/**
 * Annotation to define a command-line option (flag or named argument).
 *
 * This annotation is applied to `var` properties within a data class that represents
 * the command-line arguments.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Option(
    /**
     * The short name for the option (e.g., 'v' for -v). Use `'\u0000'` for no short name.
     */
    val shortName: Char = '\u0000',
    /**
     * The long name for the option (e.g., "verbose" for --verbose).
     */
    val longName: String = "",
    /**
     * If `true`, this option must be provided on the command line or have a non-empty `default` value.
     */
    val required: Boolean = false,
    /**
     * A descriptive text for the option, used in the generated help message.
     */
    val helpText: String = "",
    /**
     * A default string value for the option. If the option is not provided on the command line,
     * this value will be used. It will be converted to the property's type.
     */
    val default: String = "",
    /**
     * For `List<String>` properties, this separator character will be used to split the
     * single provided argument string into multiple list elements (e.g., "," for "a,b,c").
     */
    val separator: String = ""
)
