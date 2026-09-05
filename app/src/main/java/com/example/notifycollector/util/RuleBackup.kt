package com.example.notifycollector.util

import com.example.notifycollector.data.GroupEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 分组规则备份工具：把分组配置序列化为 JSON 并打进 zip（无需任何第三方依赖，
 * 全部使用 Android 自带的 org.json + java.util.zip）。
 *
 * zip 包内固定含一个条目 `groups.json`，内容为：
 * {
 *   "app": "NotifyCollector",
 *   "version": 1,
 *   "groups": [ {分组字段...}, ... ]
 * }
 */
object RuleBackup {

    private const val ZIP_ENTRY = "groups.json"
    private const val JSON_VERSION = 1

    /** 分组列表 -> JSON 字符串 */
    fun groupsToJson(groups: List<GroupEntity>): String {
        val root = JSONObject().apply {
            put("app", "NotifyCollector")
            put("version", JSON_VERSION)
            put("count", groups.size)
        }
        val arr = JSONArray()
        for (g in groups) {
            arr.put(
                JSONObject().apply {
                    put("name", g.name)
                    put("matchType", g.matchType)
                    put("pattern", g.pattern)
                    put("codePattern", g.codePattern)
                    put("expireMinutes", g.expireMinutes)
                    put("sortOrder", g.sortOrder)
                    put("createdAt", g.createdAt)
                }
            )
        }
        root.put("groups", arr)
        return root.toString(2)
    }

    /** JSON 字符串 -> 分组列表（id 置 0，由 Room 重新自增） */
    fun jsonToGroups(json: String): List<GroupEntity> {
        val root = JSONObject(json)
        val arr = root.getJSONArray("groups")
        val out = mutableListOf<GroupEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                GroupEntity(
                    name = o.getString("name"),
                    matchType = o.getString("matchType"),
                    pattern = o.getString("pattern"),
                    codePattern = o.optString("codePattern", ""),
                    expireMinutes = o.optInt("expireMinutes", 0),
                    sortOrder = o.optInt("sortOrder", 0),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return out
    }

    /** 把分组写入 zip 流（单个条目 groups.json） */
    fun writeZip(out: OutputStream, groups: List<GroupEntity>) {
        ZipOutputStream(out.buffered()).use { zos ->
            zos.putNextEntry(ZipEntry(ZIP_ENTRY))
            zos.write(groupsToJson(groups).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    /** 从 zip 流中读取 groups.json 并解析为分组列表；zip 结构不符抛异常 */
    fun readZip(input: InputStream): List<GroupEntity> {
        ZipInputStream(input.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == ZIP_ENTRY) {
                    val text = zis.bufferedReader(Charsets.UTF_8).readText()
                    return jsonToGroups(text)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        throw IllegalStateException("备份包内未找到 $ZIP_ENTRY")
    }
}
