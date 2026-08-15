package com.comichub.cli

import com.comichub.source.runtime.PluginPackageLoader
import com.comichub.source.runtime.PluginParseResult
import com.comichub.source.runtime.PluginSignature
import com.comichub.source.runtime.PluginTrustStore
import com.comichub.source.runtime.PluginLoadResult
import com.comichub.source.runtime.PluginRepositoryIndexLoader
import com.comichub.source.runtime.SignedPluginEnvelope
import com.comichub.source.runtime.sha256Hex
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

private val prettyJson = Json {
    prettyPrint = true
    explicitNulls = false
}

private val loaderJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Small, offline-first developer CLI for ComicHub plugin packages. */
object Cli {
    private val usage = """
ComicHub Plugin CLI

Usage:
  comic-source init <directory> [--id <plugin-id>] [--name <name>]
  comic-source validate <package-or-directory> [--require-signature --public-key <pem> --key-id <id>]
  comic-source test <package-or-directory> [--fixture-dir <directory>]
  comic-source fixture capture <https-url-or-file> <output-file> [--force]
  comic-source package <package-or-directory> <output-file> [--private-key <pem> --key-id <id>]
  comic-source keygen <directory> [--force]
""".trimIndent()

    fun run(args: List<String>, out: Appendable, err: Appendable): Int {
        return try {
            if (args.isEmpty()) return fail(err, usage)
            when (args.first()) {
                "init" -> init(args.drop(1), out, err)
                "validate" -> validate(args.drop(1), out, err)
                "test" -> test(args.drop(1), out, err)
                "fixture" -> fixture(args.drop(1), out, err)
                "package" -> packagePlugin(args.drop(1), out, err)
                "keygen" -> keygen(args.drop(1), out, err)
                "help", "--help", "-h" -> {
                    line(out, usage)
                    0
                }
                else -> fail(err, "未知命令：${args.first()}\n\n$usage")
            }
        } catch (error: Exception) {
            fail(err, "失败：${error.message ?: error::class.simpleName}")
        }
    }

    private fun init(args: List<String>, out: Appendable, err: Appendable): Int {
        val parsed = Options.parse(args)
        val target = parsed.positionals.singleOrNull()?.let(::File)
            ?: return fail(err, "用法：init <directory> [--id <plugin-id>] [--name <name>]")
        if (target.exists() && target.listFiles()?.isNotEmpty() == true) {
            return fail(err, "目标目录非空，为避免覆盖现有插件请先清空：${target.path}")
        }
        val id = parsed.value("id") ?: "com.example.${target.name.lowercase().replace(Regex("[^a-z0-9_.-]"), "-")}"
        val name = parsed.value("name") ?: target.name.replace('-', ' ').replaceFirstChar { it.uppercase() }
        target.mkdirs()
        val fixtures = File(target, "fixtures").apply { mkdirs() }
        writeNew(File(target, "package.json"), templatePackage(id, name))
        writeNew(File(fixtures, "search.html"), searchFixture)
        writeNew(File(fixtures, "detail.html"), detailFixture)
        writeNew(File(fixtures, "pages.html"), pagesFixture)
        writeNew(File(fixtures, "fixture.json"), fixtureMetadata)
        writeNew(
            File(target, "README.md"),
            """# $name

Run offline parser checks with:

`comic-source validate .`

`comic-source test .`

Create a distributable package with `comic-source package . dist/$id.json`.
""".trimIndent() + "\n"
        )
        line(out, "已创建插件模板：${target.absolutePath}")
        return 0
    }

    private fun validate(args: List<String>, out: Appendable, err: Appendable): Int {
        val parsed = Options.parse(args)
        val input = parsed.positionals.singleOrNull()?.let(::File)?.let(::packageFile)
            ?: return fail(err, "用法：validate <package-or-directory>")
        if (!input.isFile) return fail(err, "找不到插件包：${input.path}")
        val loader = loader(parsed, err) ?: return 1
        return when (val result = loader.parse(input.readText(Charsets.UTF_8))) {
            is PluginParseResult.Success -> {
                val manifest = result.definition.manifest
                line(out, "有效插件：${manifest.id} ${manifest.version} (${definitionName(result.definition)})")
                0
            }
            is PluginParseResult.Failure -> fail(err, result.errors.joinToString("\n"))
        }
    }

    private fun test(args: List<String>, out: Appendable, err: Appendable): Int {
        val parsed = Options.parse(args)
        val input = parsed.positionals.singleOrNull()?.let(::File)?.let(::packageFile)
            ?: return fail(err, "用法：test <package-or-directory> [--fixture-dir <directory>]")
        if (!input.isFile) return fail(err, "找不到插件包：${input.path}")
        val fixtureDir = parsed.value("fixture-dir")?.let(::File) ?: File(input.parentFile, "fixtures")
        val search = File(fixtureDir, "search.html")
        val detail = File(fixtureDir, "detail.html")
        val pages = File(fixtureDir, "pages.html")
        val missing = listOf(search, detail, pages).filterNot(File::isFile)
        if (missing.isNotEmpty()) {
            return fail(err, "缺少 fixture：${missing.joinToString { it.name }}（需要 search.html、detail.html、pages.html）")
        }
        val metadata = readFixtureMetadata(File(fixtureDir, "fixture.json"), err) ?: return 1
        val loader = loader(parsed, err) ?: return 1
        val packageJson = input.readText(Charsets.UTF_8)
        val result = runBlocking {
            loader.load(packageJson) { url ->
                fixtureFor(url, search, detail, pages).readText(Charsets.UTF_8)
            }
        }
        val loaded = when (result) {
            is PluginLoadResult.Success -> result
            is PluginLoadResult.Failure -> return fail(err, result.errors.joinToString("\n"))
        }
        return try {
            val comics = runBlocking { loaded.source.search("星", 1) }
            require(comics.isNotEmpty()) { "search fixture 未解析出漫画" }
            val comicDetail = runBlocking { loaded.source.detail(comics.first().id) }
            require(comicDetail.chapters.isNotEmpty()) { "detail fixture 未解析出章节" }
            val comicPages = runBlocking { loaded.source.pages(comicDetail.chapters.first().id) }
            require(comicPages.isNotEmpty()) { "pages fixture 未解析出页面" }
            verifyFixtureExpectations(metadata, comics, comicDetail, comicPages)
            line(
                out,
                "fixture 通过：${loaded.source.manifest.id} " +
                    "authorization=${metadata.contentAuthorization} " +
                    "search=${comics.size} chapters=${comicDetail.chapters.size} pages=${comicPages.size}"
            )
            0
        } catch (error: Exception) {
            fail(err, "fixture 测试失败：${error.message ?: error::class.simpleName}")
        }
    }

    private fun fixture(args: List<String>, out: Appendable, err: Appendable): Int {
        if (args.firstOrNull() != "capture") {
            return fail(err, "用法：fixture capture <https-url-or-file> <output-file> [--force]")
        }
        val parsed = Options.parse(args.drop(1))
        val source = parsed.positionals.getOrNull(0)
            ?: return fail(err, "缺少 fixture 来源")
        val target = parsed.positionals.getOrNull(1)?.let(::File)
            ?: return fail(err, "缺少 fixture 输出文件")
        if (target.exists() && !parsed.has("force")) {
            return fail(err, "输出文件已存在，使用 --force 才会覆盖：${target.path}")
        }
        val bytes = readFixtureSource(source)
        target.parentFile?.mkdirs()
        Files.write(target.toPath(), bytes)
        line(out, "已捕获 fixture：${target.absolutePath}（${bytes.size} bytes，SHA-256 ${sha256Hex(bytes)}）")
        return 0
    }

    private fun packagePlugin(args: List<String>, out: Appendable, err: Appendable): Int {
        val parsed = Options.parse(args)
        val input = parsed.positionals.getOrNull(0)?.let(::File)?.let(::packageFile)
            ?: return fail(err, "用法：package <package-or-directory> <output-file> [--private-key <pem> --key-id <id>]")
        val output = parsed.positionals.getOrNull(1)?.let(::File)
            ?: return fail(err, "缺少输出文件")
        if (!input.isFile) return fail(err, "找不到插件包：${input.path}")
        val payload = input.readText(Charsets.UTF_8)
        if (loaderJson.parseToJsonElement(payload).jsonObject.containsKey("payload")) {
            return fail(err, "输入已经是签名信封，请使用未签名的 package.json 作为打包输入")
        }
        when (val result = PluginPackageLoader().parse(payload)) {
            is PluginParseResult.Failure -> return fail(err, result.errors.joinToString("\n"))
            is PluginParseResult.Success -> Unit
        }
        val privateKeyPath = parsed.value("private-key")
        val keyId = parsed.value("key-id")
        if ((privateKeyPath == null) != (keyId == null)) {
            return fail(err, "--private-key 和 --key-id 必须同时提供")
        }
        val packaged = if (privateKeyPath == null) {
            payload
        } else {
            signPayload(payload, keyId!!, File(privateKeyPath))
        }
        output.parentFile?.mkdirs()
        Files.writeString(output.toPath(), packaged, Charsets.UTF_8)
        line(out, "已打包：${output.absolutePath}${if (privateKeyPath != null) "（已签名）" else ""}")
        return 0
    }

    private fun keygen(args: List<String>, out: Appendable, err: Appendable): Int {
        val parsed = Options.parse(args)
        val target = parsed.positionals.singleOrNull()?.let(::File)
            ?: return fail(err, "用法：keygen <directory> [--force]")
        target.mkdirs()
        val privateFile = File(target, "private_key.pem")
        val publicFile = File(target, "public_key.pem")
        if (!parsed.has("force") && (privateFile.exists() || publicFile.exists())) {
            return fail(err, "密钥文件已存在，使用 --force 才会覆盖")
        }
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        Files.writeString(privateFile.toPath(), pem("PRIVATE KEY", pair.private.encoded), Charsets.UTF_8)
        Files.writeString(publicFile.toPath(), pem("PUBLIC KEY", pair.public.encoded), Charsets.UTF_8)
        line(out, "已生成 RSA 密钥：${privateFile.absolutePath}、${publicFile.absolutePath}")
        line(out, "签名时使用：--private-key ${privateFile.path} --key-id <稳定的公钥标识>")
        return 0
    }

    private fun loader(parsed: Options, err: Appendable): PluginPackageLoader? {
        val requireSignature = parsed.has("require-signature")
        val publicKeyPath = parsed.value("public-key")
        val keyId = parsed.value("key-id")
        if (publicKeyPath == null && keyId != null) {
            fail(err, "--key-id 需要同时提供 --public-key")
            return null
        }
        if (publicKeyPath != null && keyId == null) {
            fail(err, "--public-key 需要同时提供 --key-id")
            return null
        }
        val verifier = if (publicKeyPath == null) null else {
            val publicKeyBytes = decodePemOrBase64(File(publicKeyPath), "PUBLIC KEY")
            com.comichub.source.runtime.PluginSignatureVerifier(
                PluginTrustStore(mapOf(keyId!! to Base64.getEncoder().encodeToString(publicKeyBytes)))
            )
        }
        return PluginPackageLoader(
            json = loaderJson,
            signatureVerifier = verifier,
            requireSignature = requireSignature
        )
    }

    private fun signPayload(payload: String, keyId: String, keyFile: File): String {
        val privateKey = decodePrivateKey(keyFile)
        val signer = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(payload.toByteArray(StandardCharsets.UTF_8))
        }
        val signature = PluginSignature(
            algorithm = "SHA256withRSA",
            keyId = keyId,
            value = Base64.getEncoder().encodeToString(signer.sign())
        )
        return prettyJson.encodeToString(SignedPluginEnvelope(payload = payload, signature = signature)) + "\n"
    }

    private fun decodePrivateKey(file: File): PrivateKey {
        val bytes = decodePemOrBase64(file, "PRIVATE KEY")
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    private fun decodePemOrBase64(file: File, label: String): ByteArray {
        require(file.isFile) { "找不到密钥文件：${file.path}" }
        val raw = file.readBytes()
        val text = raw.toString(Charsets.US_ASCII).trim()
        val pemStart = "-----BEGIN $label-----"
        val pemEnd = "-----END $label-----"
        val encoded = if (text.contains(pemStart) && text.contains(pemEnd)) {
            text.substringAfter(pemStart).substringBefore(pemEnd).replace(Regex("\\s"), "")
        } else {
            if (raw.all { it in 32..126 || it == '\n'.code.toByte() || it == '\r'.code.toByte() || it == '\t'.code.toByte() }) {
                text.replace(Regex("\\s"), "")
            } else {
                return raw
            }
        }
        return Base64.getDecoder().decode(encoded)
    }

    private fun pem(label: String, bytes: ByteArray): String {
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(bytes)
        return "-----BEGIN $label-----\n$encoded\n-----END $label-----\n"
    }

    private fun fixtureFor(url: String, search: File, detail: File, pages: File): File {
        val path = URI(url).path.orEmpty()
        return when {
            path.contains("search") -> search
            path.contains("chapter") || path.contains("page") -> pages
            else -> detail
        }
    }

    private fun readFixtureMetadata(file: File, err: Appendable): FixtureMetadata? {
        if (!file.isFile) {
            // Keep older plugin directories working; metadata is an opt-in contract.
            return FixtureMetadata(contentAuthorization = "legacy fixture without metadata")
        }
        return try {
            val metadata = loaderJson.decodeFromString<FixtureMetadata>(file.readText(Charsets.UTF_8))
            if (metadata.contentAuthorization.isBlank()) {
                fail(err, "fixture.json 的 contentAuthorization 不能为空")
                null
            } else {
                metadata
            }
        } catch (error: Exception) {
            fail(err, "fixture.json 无法解析：${error.message ?: "格式错误"}")
            null
        }
    }

    private fun verifyFixtureExpectations(
        metadata: FixtureMetadata,
        comics: List<com.comichub.source.api.ComicSummary>,
        detail: com.comichub.source.api.ComicDetail,
        pages: List<com.comichub.source.api.ComicPage>
    ) {
        val expected = metadata.expected ?: return
        expected.searchResults?.let { require(comics.size == it) { "fixture 期望 search=$it，实际为 ${comics.size}" } }
        expected.title?.let { require(comics.first().title == it) { "fixture 期望标题为 $it，实际为 ${comics.first().title}" } }
        expected.chapters?.let { require(detail.chapters.size == it) { "fixture 期望 chapters=$it，实际为 ${detail.chapters.size}" } }
        expected.pages?.let { require(pages.size == it) { "fixture 期望 pages=$it，实际为 ${pages.size}" } }
    }

    private fun readFixtureSource(source: String): ByteArray {
        val local = File(source)
        if (local.isFile) return local.readBytes()
        val uri = URI(source)
        require(uri.scheme == "https") { "fixture capture 只允许 HTTPS URL 或本地文件" }
        val request = HttpRequest.newBuilder(uri).GET().build()
        val response = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .send(request, HttpResponse.BodyHandlers.ofByteArray())
        require(response.statusCode() in 200..299) { "fixture URL 返回 HTTP ${response.statusCode()}" }
        return response.body()
    }

    private fun packageFile(input: File): File = if (input.isDirectory) File(input, "package.json") else input

    private fun definitionName(definition: Any): String = when (definition::class.simpleName) {
        "JavaScriptSourceDefinition" -> "javascript"
        else -> "declarative"
    }

    private fun writeNew(file: File, text: String) {
        if (!file.createNewFile()) throw IllegalStateException("文件已存在：${file.path}")
        file.writeText(text, Charsets.UTF_8)
    }

    private fun templatePackage(id: String, name: String): String {
        val safeId = escapeJson(id)
        val safeName = escapeJson(name)
        return """
            {
              "manifest": {
                "id": "$safeId",
                "name": "$safeName",
                "version": "0.1.0",
                "apiVersion": 1,
                "baseUrl": "https://fixture.example",
                "domains": ["fixture.example"],
                "capabilities": ["search", "detail", "chapters", "pages"],
                "permissions": ["network"],
                "rateLimit": {"requestsPerMinute": 20, "concurrency": 1},
                "license": "MIT"
              },
              "search": {
                "pathTemplate": "/search?q={query}&page={page}",
                "itemSelector": ".comic-card",
                "title": {"css": ".title"},
                "url": {"css": "a", "attribute": "href"},
                "cover": {"css": "img", "attribute": "src"}
              },
              "detail": {
                "title": {"css": "h1"},
                "author": {"css": ".author"},
                "description": {"css": ".description"},
                "chapterItemSelector": ".chapter",
                "chapterTitle": {"css": ".chapter-title"},
                "chapterUrl": {"css": "a", "attribute": "href"}
              },
              "pages": {
                "pageSelector": ".page img",
                "image": {"css": "", "attribute": "data-src"}
              }
            }
        """.trimIndent() + "\n"
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun line(out: Appendable, message: String) {
        out.append(message).append('\n')
    }

    private fun fail(err: Appendable, message: String): Int {
        line(err, message)
        return 1
    }

    private data class Options(
        val positionals: List<String>,
        val values: Map<String, String>,
        val flags: Set<String>
    ) {
        fun value(name: String): String? = values[name]
        fun has(name: String): Boolean = name in flags

        companion object {
            fun parse(args: List<String>): Options {
                val positionals = mutableListOf<String>()
                val values = mutableMapOf<String, String>()
                val flags = mutableSetOf<String>()
                var index = 0
                while (index < args.size) {
                    val arg = args[index]
                    if (!arg.startsWith("--")) {
                        positionals += arg
                        index++
                        continue
                    }
                    val name = arg.removePrefix("--")
                    if (index + 1 < args.size && !args[index + 1].startsWith("--")) {
                        values[name] = args[index + 1]
                        index += 2
                    } else {
                        flags += name
                        index++
                    }
                }
                return Options(positionals, values, flags)
            }
        }
    }

    private const val searchFixture = """
        <main>
          <article class="comic-card">
            <a href="/comic/sky"><span class="title">星海信使</span><img src="/covers/sky.jpg"></a>
          </article>
        </main>
    """

    private const val detailFixture = """
        <main>
          <h1>星海信使</h1>
          <div class="author">林默</div>
          <div class="description">一段穿越星海的旅程。</div>
          <div class="chapter"><a href="/chapter/1"><span class="chapter-title">第一话：出发</span></a></div>
        </main>
    """

    private const val pagesFixture = """
        <main>
          <div class="page"><img data-src="/pages/sky-1.jpg"></div>
          <div class="page"><img data-src="/pages/sky-2.jpg"></div>
        </main>
    """

    private const val fixtureMetadata = """
        {
          "contentAuthorization": "local synthetic fixture owned by the ComicHub project",
          "expected": {
            "searchResults": 1,
            "title": "星海信使",
            "chapters": 1,
            "pages": 2
          }
        }
    """
}

@Serializable
private data class FixtureMetadata(
    val contentAuthorization: String,
    val expected: FixtureExpectations? = null
)

@Serializable
private data class FixtureExpectations(
    val searchResults: Int? = null,
    val title: String? = null,
    val chapters: Int? = null,
    val pages: Int? = null
)

fun main(args: Array<String>) {
    val status = Cli.run(args.toList(), System.out, System.err)
    if (status != 0) kotlin.system.exitProcess(status)
}
