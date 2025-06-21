package info.mester.network.common

import java.util.UUID

fun UUID.shorten(): String = this.toString().replace("-", "")

fun String.expandToUUID(): UUID {
    require(this.length == 32) { "Input string must be exactly 32 characters long" }
    return UUID.fromString(this.replaceFirst(Regex("(.{8})(.{4})(.{4})(.{4})(.{12})"), "$1-$2-$3-$4-$5"))
}
