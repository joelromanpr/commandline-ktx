package com.joelromanpr.commandline.ktx.converters

import kotlin.reflect.KClass

public interface TypeConverter<T : Any> {
    public fun convert(value: String): T
}
