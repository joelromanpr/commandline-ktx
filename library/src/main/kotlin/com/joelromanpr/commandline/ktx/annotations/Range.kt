package com.joelromanpr.commandline.ktx.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
public annotation class Range(
    val min: Int = Int.MIN_VALUE,
    val max: Int = Int.MAX_VALUE
)
