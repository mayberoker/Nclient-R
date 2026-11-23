package com.nclientnext.data

import com.nclientnext.BuildConfig
import com.nclientnext.data.api.NhApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Where Nclient-R lives. The About screen, update checks and feedback links all point here. */
object Project {
    const val OWNER = "mayberoker"
    const val REPO = "Nclient-R"
    const val URL = "https://github.com/$OWNER/$REPO"
    const val RELEASES = "$URL/releases"
    const val LATEST_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
    const val FEATURE = "$URL/issues/new?template=feature_request.yml"
    const val BUG = "$URL/issues/new?template=bug_report.yml"
}

data class AppUpdate(val version: String, val url: String, val notes: String)

object Updates {
    /** Returns the newest GitHub release if it is newer than this build. */
    suspend fun check(api: NhApi): AppUpdate? {
        val o = Json.parseToJsonElement(api.rawText(Project.LATEST_API)) as? JsonObject ?: return null
        val tag = (o["tag_name"] as? JsonPrimitive)?.content ?: return null
        if (!isNewer(tag, BuildConfig.VERSION_NAME)) return null
        return AppUpdate(
            version = tag.removePrefix("v"),
            url = (o["html_url"] as? JsonPrimitive)?.content ?: Project.RELEASES,
            notes = (o["body"] as? JsonPrimitive)?.content.orEmpty(),
        )
    }

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote); val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
