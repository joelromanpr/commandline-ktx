package com.joelromanpr.commandline.ktx.demo

import com.joelromanpr.commandline.ktx.converters.TypeConverter

data class Uri(val scheme: String, val host: String, val port: Int)

class UriConverter : TypeConverter<Uri> {
    override fun convert(value: String): Uri {
        val parts = value.split("://")
        val scheme = parts[0]
        val rest = parts[1].split(":")
        val host = rest[0]
        val port = rest.getOrNull(1)?.toInt() ?: 80
        return Uri(scheme, host, port)
    }
}
