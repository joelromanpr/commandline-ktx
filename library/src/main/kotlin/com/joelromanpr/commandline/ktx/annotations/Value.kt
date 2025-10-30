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
 * Annotation to define a positional command-line argument.
 *
 * This annotation is applied to `var` properties within a data class that represents
 * the command-line arguments. Positional arguments are identified by their `index`.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Value(
    /**
     * The zero-based index of the positional argument.
     */
    val index: Int,
    /**
     * If `true`, this positional argument must be provided on the command line.
     */
    val required: Boolean = false,
    /**
     * A descriptive text for the positional argument, used in the generated help message.
     */
    val helpText: String = ""
)
