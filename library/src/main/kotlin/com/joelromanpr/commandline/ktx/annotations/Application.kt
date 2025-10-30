package com.joelromanpr.commandline.ktx.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Application(
    val name: String,
    val version: String = "1.0.0",
    val description: String = ""
)
