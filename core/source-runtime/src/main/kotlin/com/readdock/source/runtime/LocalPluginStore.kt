package com.readdock.source.runtime

import com.readdock.source.api.ComicSource
import com.readdock.source.api.SourceManifest
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class InstalledPluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val canRollback: Boolean = false
)

sealed interface PluginStoreResult {
    data class Installed(val plugin: InstalledPluginInfo) : PluginStoreResult
    data class RolledBack(val plugin: InstalledPluginInfo) : PluginStoreResult
    data class Rejected(val errors: List<String>) : PluginStoreResult
    data class NotFound(val id: String) : PluginStoreResult
    data class NoRollback(val id: String) : PluginStoreResult
    data class Completed(val id: String) : PluginStoreResult
}

data class PluginLoadReport(
    val sources: List<ComicSource>,
    val failures: Map<String, List<String>>
)

/** Stores user-installed JSON plugins under the app's private files directory. */
class LocalPluginStore(
    private val directory: File,
    private val loaderProvider: () -> PluginPackageLoader = { PluginPackageLoader() }
) {
    private val historyDirectory = File(directory, "history")

    init {
        directory.mkdirs()
        historyDirectory.mkdirs()
    }

    fun install(
        packageJson: String,
        loader: PluginPackageLoader = loaderProvider()
    ): PluginStoreResult {
        val definition = when (val parsed = loader.parse(packageJson)) {
            is PluginParseResult.Failure -> return PluginStoreResult.Rejected(parsed.errors)
            is PluginParseResult.Success -> parsed.definition
        }
        val id = definition.manifest.id
        val target = packageFile(id)
        val historyFile = try {
            if (target.isFile) preserveCurrent(id, target) else null
        } catch (error: Exception) {
            return PluginStoreResult.Rejected(
                listOf("插件备份失败：${error.message ?: "无法保存历史版本"}")
            )
        }
        val temporary = File(directory, "$id.json.tmp")
        try {
            temporary.writeText(packageJson, Charsets.UTF_8)
            replaceFile(temporary, target)
        } catch (error: Exception) {
            historyFile?.delete()
            return PluginStoreResult.Rejected(
                listOf("插件保存失败：${error.message ?: "无法写入插件文件"}")
            )
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        return PluginStoreResult.Installed(info(definition.manifest))
    }

    fun list(): List<InstalledPluginInfo> = directory
        .listFiles { file -> file.isFile && file.extension == "json" }
        ?.mapNotNull { file ->
            val parsed = loaderProvider().parse(file.readText(Charsets.UTF_8))
            val definition = (parsed as? PluginParseResult.Success)?.definition ?: return@mapNotNull null
            info(definition.manifest)
        }
        .orEmpty()
        .sortedBy { it.name.lowercase() }

    fun setEnabled(id: String, enabled: Boolean): PluginStoreResult {
        if (!packageFile(id).isFile) return PluginStoreResult.NotFound(id)
        val marker = disabledFile(id)
        if (enabled) marker.delete() else marker.writeText("disabled", Charsets.UTF_8)
        return PluginStoreResult.Completed(id)
    }

    fun uninstall(id: String): PluginStoreResult {
        if (!packageFile(id).isFile) return PluginStoreResult.NotFound(id)
        packageFile(id).delete()
        disabledFile(id).delete()
        historyDirectory(id).deleteRecursively()
        return PluginStoreResult.Completed(id)
    }

    fun rollback(id: String): PluginStoreResult {
        val target = packageFile(id)
        val previous = historyFiles(id).maxByOrNull { it.lastModified() }
            ?: return PluginStoreResult.NoRollback(id)
        if (!target.isFile) return PluginStoreResult.NoRollback(id)
        val previousJson = try {
            previous.readText(Charsets.UTF_8)
        } catch (error: Exception) {
            return PluginStoreResult.Rejected(
                listOf("历史版本读取失败：${error.message ?: "无法执行回滚"}")
            )
        }
        val previousDefinition = when (val parsed = loaderProvider().parse(previousJson)) {
            is PluginParseResult.Success -> parsed.definition
            is PluginParseResult.Failure -> {
                return PluginStoreResult.Rejected(parsed.errors)
            }
        }

        val currentHistory = try {
            preserveCurrent(id, target)
        } catch (error: Exception) {
            return PluginStoreResult.Rejected(
                listOf("当前版本备份失败：${error.message ?: "无法执行回滚"}")
            )
        }
        try {
            replaceFile(previous, target)
        } catch (error: Exception) {
            currentHistory.delete()
            return PluginStoreResult.Rejected(
                listOf("插件回滚失败：${error.message ?: "无法替换插件文件"}")
            )
        }
        return PluginStoreResult.RolledBack(info(previousDefinition.manifest))
    }

    suspend fun loadEnabled(
        fetchHtml: suspend (manifest: SourceManifest, url: String) -> String
    ): PluginLoadReport {
        val sources = mutableListOf<ComicSource>()
        val failures = mutableMapOf<String, List<String>>()
        list().filter { it.enabled }.forEach { plugin ->
            val json = packageFile(plugin.id).readText(Charsets.UTF_8)
            when (val result = loaderProvider().load(json) { url ->
                fetchHtml(pluginManifest(plugin.id), url)
            }) {
                is PluginLoadResult.Success -> sources += result.source
                is PluginLoadResult.Failure -> failures[plugin.id] = result.errors
            }
        }
        return PluginLoadReport(sources, failures)
    }

    private fun pluginManifest(id: String): SourceManifest = when (
        val parsed = loaderProvider().parse(packageFile(id).readText(Charsets.UTF_8))
    ) {
        is PluginParseResult.Success -> parsed.definition.manifest
        is PluginParseResult.Failure -> error("插件无法解析：$id")
    }

    private fun info(manifest: SourceManifest): InstalledPluginInfo = InstalledPluginInfo(
        id = manifest.id,
        name = manifest.name,
        version = manifest.version,
        enabled = !disabledFile(manifest.id).exists(),
        canRollback = historyFiles(manifest.id).isNotEmpty()
    )

    private fun packageFile(id: String): File = File(directory, "$id.json")

    private fun disabledFile(id: String): File = File(directory, "$id.disabled")

    private fun historyDirectory(id: String): File = File(historyDirectory, id)

    private fun historyFiles(id: String): List<File> = historyDirectory(id)
        .listFiles { file -> file.isFile && file.extension == "json" }
        ?.toList()
        .orEmpty()

    private fun preserveCurrent(id: String, target: File): File {
        val history = historyDirectory(id).apply { mkdirs() }
        val backup = File.createTempFile("previous-", ".json", history)
        target.copyTo(backup, overwrite = true)
        return backup
    }

    private fun replaceFile(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
