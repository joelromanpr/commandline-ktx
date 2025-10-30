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
