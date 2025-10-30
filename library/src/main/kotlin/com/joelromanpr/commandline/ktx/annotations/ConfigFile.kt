package com.joelromanpr.commandline.ktx.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
public annotation class ConfigFile(val path: String)
