package com.example.notifycollector.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.notifycollector.data.AiConfig
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 本地模型对一条通知的结构化解析结果 */
data class AiResult(
    val category: String?,
    val code: String?,
    val company: String?,
    val location: String?,
    val confidence: Double
)

/**
 * 离线 AI 解析器（单例）。封装 MediaPipe LLM Inference：
 * - 模型懒加载（首次推理时构建，耗时 1~2s，必须在后台线程）；
 * - 推理串行化（同引擎并发 generateResponse 不安全，用锁保护）；
 * - 任何失败（无模型 / 推理异常 / 低置信度）都返回 null，调用方据此退回正则结果。
 */
object NotificationAiAnalyzer {

    enum class Status { NOT_DOWNLOADED, DOWNLOADING, READY, ERROR }

    private const val TAG = "AiAnalyzer"

    @Volatile private var engine: LlmInference? = null
    private val loadLock = Mutex()
    private val inferLock = Mutex()

    private val _state = MutableStateFlow(Status.NOT_DOWNLOADED to 0)
    val state: StateFlow<Pair<Status, Int>> = _state

    fun modelFile(context: Context): File =
        File(context.filesDir, "ai/${AiConfig.MODEL_FILENAME}")

    fun isModelPresent(context: Context): Boolean = modelFile(context).exists()

    /** 确保引擎已加载；返回是否可用。重入安全。 */
    private suspend fun ensureLoaded(context: Context): Boolean = withContext(Dispatchers.Default) {
        if (engine != null) return@withContext true
        val f = modelFile(context)
        if (!f.exists()) {
            _state.value = Status.NOT_DOWNLOADED to 0
            return@withContext false
        }
        return@withContext loadLock.withLock {
            if (engine != null) return@withLock true
            runCatching {
                val opts = LlmInferenceOptions.builder()
                    .setModelPath(f.absolutePath)
                    .setMaxTokens(AiConfig.MAX_TOKENS)
                    .build()
                engine = LlmInference.createFromOptions(context.applicationContext, opts)
                _state.value = Status.READY to 100
                true
            }.onFailure { e ->
                Log.e(TAG, "load model failed", e)
                _state.value = Status.ERROR to 0
            }.getOrDefault(false)
        }
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
    }

    private fun isWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val net = cm?.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        return cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * 下载模型到 filesDir/ai/。[requireWifi] 为 true 时仅在 Wi-Fi 下执行（避免蜂窝流量拉取上 GB 文件）。
     * 通过 [onProgress] 回报 0-100；返回 null 表示成功，否则返回人类可读的失败原因。
     */
    suspend fun downloadModel(
        context: Context,
        url: String = AiConfig.DEFAULT_MODEL_URL,
        requireWifi: Boolean = true,
        onProgress: (Int) -> Unit = {}
    ): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext "未填写模型地址，请先填写或改用「从本机导入」"
        if (requireWifi && !isWifi(context)) {
            _state.value = Status.ERROR to 0
            return@withContext "当前未连接 Wi-Fi，已取消下载（避免消耗蜂窝流量）"
        }
        _state.value = Status.DOWNLOADING to 0
        try {
            val dir = File(context.filesDir, "ai")
            dir.mkdirs()
            val target = modelFile(context)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 60_000
                connect()
            }
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                _state.value = Status.ERROR to 0
                return@withContext "服务器返回 HTTP ${conn.responseCode}（地址可能失效或被拦截）"
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            val input = BufferedInputStream(conn.inputStream)
            val output = FileOutputStream(target)
            val buf = ByteArray(64 * 1024)
            var read: Int
            var soFar = 0L
            while (input.read(buf).also { read = it } != -1) {
                output.write(buf, 0, read)
                soFar += read
                if (total > 0) {
                    val pct = (soFar * 100 / total).toInt().coerceIn(0, 100)
                    _state.value = Status.DOWNLOADING to pct
                    onProgress(pct)
                } else {
                    // 服务器未返回 Content-Length 时无法算百分比，仍更新状态避免一直卡在 0%
                    _state.value = Status.DOWNLOADING to -1
                }
            }
            output.flush(); output.close(); input.close(); conn.disconnect()
            onProgress(100)
            _state.value = Status.NOT_DOWNLOADED to 0
            null
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            _state.value = Status.ERROR to 0
            e.message ?: "下载异常：${e.javaClass.simpleName}"
        }
    }

    /**
     * 从本机选择一个 .task 文件并复制到应用私有目录（完全不联网）。
     * 返回 null 表示成功，否则返回失败原因。
     */
    suspend fun importModelFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "ai")
            dir.mkdirs()
            val target = modelFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) out.write(buf, 0, n)
                    out.flush()
                }
            } ?: return@withContext "无法读取所选文件"
            _state.value = Status.NOT_DOWNLOADED to 0
            null
        } catch (e: Exception) {
            Log.e(TAG, "import model failed", e)
            e.message ?: "导入失败：${e.javaClass.simpleName}"
        }
    }

    /**
     * 分析一条通知。返回结构化结果；任何失败（无模型 / 推理异常 / 解析失败 / 低置信）返回 null，
     * 调用方应退回正则结果，主流程不受影响。
     */
    suspend fun analyze(context: Context, title: String, text: String): AiResult? =
        withContext(Dispatchers.Default) {
            if (!ensureLoaded(context)) return@withContext null
            val eng = engine ?: return@withContext null
            val prompt = AiConfig.buildPrompt(title, text)
            val raw = inferLock.withLock {
                runCatching { eng.generateResponse(prompt) }.onFailure { e ->
                    Log.e(TAG, "inference failed", e)
                }.getOrNull()
            } ?: return@withContext null
            parseJson(raw)
        }

    private fun parseJson(raw: String): AiResult? {
        val jsonStr = runCatching {
            val s = raw.indexOf('{')
            val e = raw.lastIndexOf('}')
            if (s < 0 || e <= s) return@runCatching null
            raw.substring(s, e + 1)
        }.getOrNull() ?: return null

        return runCatching {
            val j = JSONObject(jsonStr)
            val conf = j.optDouble("confidence", -1.0)
            // 显式给出了低置信度 -> 不可信，退回正则
            if (conf in 0.0..1.0 && conf < AiConfig.MIN_CONFIDENCE) return@runCatching null
            AiResult(
                category = j.optString("category").takeIf { it.isNotBlank() },
                code = j.optString("code").takeIf { it.isNotBlank() },
                company = j.optString("company").takeIf { it.isNotBlank() },
                location = j.optString("location").takeIf { it.isNotBlank() },
                confidence = if (conf < 0) 0.0 else conf
            )
        }.getOrNull()
    }
}
