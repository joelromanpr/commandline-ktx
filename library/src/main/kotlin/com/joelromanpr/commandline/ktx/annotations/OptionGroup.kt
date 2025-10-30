package com.joelromanpr.commandline.ktx.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class OptionGroup(
    val name: String,
    val required: Boolean = false,
    val helpText: String = ""
)
