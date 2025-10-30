package com.joelromanpr.commandline.ktx.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class EnvVar(
    val name: String
)