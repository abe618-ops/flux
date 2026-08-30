package com.codexmeter.app

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class PhoneRuntimeState(
    val packaged: Boolean,
    val ready: Boolean,
    val version: String,
    val binary: File?,
    val detail: String,
)

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

class PhoneRuntimeManager(private val context: Context) {
    private val root = File(context.filesDir, "phone-codex")
    private val runtimeDir = File(root, "runtime/" + BuildConfig.CODEX_PHONE_RUNTIME_VERSION)
    val homeDir = File(root, "home")
    val stateDir = File(homeDir, ".codex")
    val authFile = File(stateDir, "auth.json")
    val configFile = File(stateDir, "config.toml")
    val backendLog = File(root, "app-server.log")
    private var backendProcess: Process? = null

    suspend fun ensureReady(): PhoneRuntimeState = withContext(Dispatchers.IO) {
        homeDir.mkdirs(); stateDir.mkdirs()
        ensureConfig()
        if (Build.SUPPORTED_ABIS.none { it.equals("arm64-v8a", true) }) {
            return@withContext PhoneRuntimeState(false, false, BuildConfig.CODEX_PHONE_RUNTIME_VERSION, null, "当前设备不是 arm64-v8a")
        }
        if (!BuildConfig.CODEX_PHONE_RUNTIME_PACKAGED) {
            return@withContext PhoneRuntimeState(false, false, BuildConfig.CODEX_PHONE_RUNTIME_VERSION, null, "此 APK 构建未注入手机 Codex runtime")
        }
        val marker = File(runtimeDir, ".version")
        var binary = findBinary()
        if (!marker.exists() || marker.readText().trim() != BuildConfig.CODEX_PHONE_RUNTIME_VERSION || binary == null) {
            extract()
            marker.parentFile?.mkdirs()
            marker.writeText(BuildConfig.CODEX_PHONE_RUNTIME_VERSION)
            binary = findBinary()
        }
        if (binary == null) {
            return@withContext PhoneRuntimeState(true, false, BuildConfig.CODEX_PHONE_RUNTIME_VERSION, null, "runtime 已解压，但未找到 Codex 可执行文件")
        }
        binary.setExecutable(true, false)
        binary.parentFile?.listFiles()?.filter { it.isFile }?.forEach { it.setExecutable(true, false) }
        val probe = runArgs(listOf(binary.absolutePath, "--version"), 6000)
        PhoneRuntimeState(
            packaged = true,
            ready = probe.exitCode == 0,
            version = BuildConfig.CODEX_PHONE_RUNTIME_VERSION,
            binary = binary,
            detail = if (probe.exitCode == 0) probe.stdout.trim().ifBlank { "Codex runtime ready" }
                else "Codex runtime 无法执行: " + probe.stderr.take(240),
        )
    }

    suspend fun ensureBackend(): Result<Unit> = runCatching {
        val state = ensureReady()
        val binary = state.binary ?: error(state.detail)
        if (!state.ready) error(state.detail)
        if (isListening()) return@runCatching
        backendLog.parentFile?.mkdirs()
        backendLog.writeText("")
        val pb = ProcessBuilder(
            binary.absolutePath,
            "app-server",
            "--listen",
            "ws://127.0.0.1:8765",
        )
        pb.directory(homeDir)
        pb.environment()["HOME"] = homeDir.absolutePath
        pb.environment()["CODEX_HOME"] = stateDir.absolutePath
        val binDir = binary.parentFile?.absolutePath.orEmpty()
        pb.environment()["PATH"] = listOf(binDir, "/system/bin", "/system/xbin", System.getenv("PATH").orEmpty())
            .filter { it.isNotBlank() }.joinToString(":")
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(backendLog))
        backendProcess = pb.start()
        repeat(16) {
            if (isListening()) return@runCatching
            if (backendProcess?.isAlive == false) {
                error("Codex app-server 已退出: " + readLogTail())
            }
            delay(500)
        }
        error("Codex app-server 未能监听 127.0.0.1:8765: " + readLogTail())
    }

    suspend fun isListening(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use {
                it.connect(InetSocketAddress("127.0.0.1", 8765), 500)
                true
            }
        } catch (_: Throwable) { false }
    }

    fun hasAuth(): Boolean {
        if (!authFile.exists()) return false
        val text = runCatching { authFile.readText() }.getOrDefault("")
        return text.contains("access_token") || text.contains("refresh_token") || text.contains("OPENAI_API_KEY")
    }

    fun readLogTail(lines: Int = 30): String =
        if (!backendLog.exists()) "" else runCatching { backendLog.readLines().takeLast(lines).joinToString("\n") }.getOrDefault("")

    private fun ensureConfig() {
        stateDir.mkdirs()
        val current = if (configFile.exists()) configFile.readText() else ""
        var next = current
        next = upsert(next, "cli_auth_credentials_store", "\"file\"")
        next = upsert(next, "forced_login_method", "\"chatgpt\"")
        if (!configFile.exists() || next != current) configFile.writeText(next.trimEnd() + "\n")
    }

    private fun upsert(content: String, key: String, value: String): String {
        val regex = Regex("(?m)^\\s*" + Regex.escape(key) + "\\s*=.*$")
        return when {
            regex.containsMatchIn(content) -> content.replace(regex, key + " = " + value)
            content.isBlank() -> key + " = " + value + "\n"
            else -> content.trimEnd() + "\n" + key + " = " + value + "\n"
        }
    }

    private fun extract() {
        runtimeDir.deleteRecursively()
        runtimeDir.mkdirs()
        context.assets.open(BuildConfig.CODEX_PHONE_RUNTIME_ASSET).use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val target = runtimeDir.resolve(entry.name).normalize()
                    if (!target.canonicalPath.startsWith(runtimeDir.canonicalPath)) {
                        error("Unsafe runtime entry: " + entry.name)
                    }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    private fun findBinary(): File? {
        if (!runtimeDir.exists()) return null
        val files = runtimeDir.walkTopDown().filter { it.isFile }.toList()
        val preferred = listOf("codex.bin", "codex-aarch64-linux-android", "codex-aarch64-unknown-linux-musl", "codex")
        preferred.forEach { name -> files.firstOrNull { it.name == name }?.let { return it } }
        return files.firstOrNull { it.name.startsWith("codex") && !it.name.endsWith(".js") && !it.name.startsWith("codex-exec") }
    }

    private fun runArgs(args: List<String>, timeoutMs: Long): ShellResult {
        val p = try {
            ProcessBuilder(args)
                .directory(homeDir)
                .apply {
                    environment()["HOME"] = homeDir.absolutePath
                    environment()["CODEX_HOME"] = stateDir.absolutePath
                }
                .start()
        } catch (t: Throwable) {
            return ShellResult(-1, "", t.message ?: t.javaClass.simpleName)
        }
        val out = StringBuilder()
        val err = StringBuilder()
        val t1 = Thread { p.inputStream.bufferedReader().use { out.append(it.readText()) } }.apply { start() }
        val t2 = Thread { p.errorStream.bufferedReader().use { err.append(it.readText()) } }.apply { start() }
        val ok = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!ok) p.destroyForcibly()
        t1.join(500); t2.join(500)
        return ShellResult(if (ok) p.exitValue() else -1, out.toString(), err.toString())
    }
}
