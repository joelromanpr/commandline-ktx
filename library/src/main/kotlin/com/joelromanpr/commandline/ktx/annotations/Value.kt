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