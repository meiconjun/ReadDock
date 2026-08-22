package com.readdock.cli

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliTest {
    @Test
    fun `init and test run against generated offline fixtures`() {
        val root = Files.createTempDirectory("readdock-cli-").toFile()
        val output = StringBuilder()
        val errors = StringBuilder()

        assertEquals(0, Cli.run(listOf("init", root.resolve("fixture-source").path), output, errors))
        assertEquals(0, Cli.run(listOf("validate", root.resolve("fixture-source").path), output, errors))
        assertEquals(0, Cli.run(listOf("test", root.resolve("fixture-source").path), output, errors))
        assertTrue(output.toString().contains("fixture 通过"), output.toString())
        assertTrue(output.toString().contains("authorization=project-owned synthetic fixture"), output.toString())
        assertEquals("", errors.toString())
    }

    @Test
    fun `query based search routes use the search fixture`() {
        val root = Files.createTempDirectory("readdock-cli-query-search-").toFile()
        val pluginDir = root.resolve("fixture-source")
        val output = StringBuilder()
        val errors = StringBuilder()

        assertEquals(0, Cli.run(listOf("init", pluginDir.path), output, errors))
        pluginDir.resolve("package.json").writeText(
            pluginDir.resolve("package.json").readText().replace("/search?q=", "/comics?q=")
        )

        assertEquals(0, Cli.run(listOf("test", pluginDir.path), output, errors))
        assertTrue(output.toString().contains("fixture 通过"), output.toString())
        assertEquals("", errors.toString())
    }

    @Test
    fun `fixture expectations fail when a snapshot changes unexpectedly`() {
        val root = Files.createTempDirectory("readdock-cli-fixture-contract-").toFile()
        val pluginDir = root.resolve("source")
        val output = StringBuilder()
        val errors = StringBuilder()
        assertEquals(0, Cli.run(listOf("init", pluginDir.path), output, errors))

        pluginDir.resolve("fixtures/fixture.json").writeText(
            """
            {
              "contentAuthorization": "local test content",
              "expected": { "pages": 3 }
            }
            """.trimIndent()
        )

        assertEquals(1, Cli.run(listOf("test", pluginDir.path), StringBuilder(), errors))
        assertTrue(errors.toString().contains("期望 pages=3"), errors.toString())
    }

    @Test
    fun `legacy fixture directories without metadata remain supported`() {
        val root = Files.createTempDirectory("readdock-cli-legacy-fixture-").toFile()
        val pluginDir = root.resolve("source")
        val output = StringBuilder()
        val errors = StringBuilder()
        assertEquals(0, Cli.run(listOf("init", pluginDir.path), output, errors))
        assertTrue(pluginDir.resolve("fixtures/fixture.json").delete())

        assertEquals(0, Cli.run(listOf("test", pluginDir.path), output, errors))
        assertTrue(output.toString().contains("legacy fixture without metadata"), output.toString())
        assertEquals("", errors.toString())
    }

    @Test
    fun `package signs payload and validate accepts trusted key`() {
        val root = Files.createTempDirectory("readdock-cli-sign-").toFile()
        val pluginDir = root.resolve("source")
        val output = StringBuilder()
        val errors = StringBuilder()
        assertEquals(0, Cli.run(listOf("init", pluginDir.path), output, errors))

        val keys = root.resolve("keys")
        assertEquals(0, Cli.run(listOf("keygen", keys.path), output, errors))
        val privateKey = keys.resolve("private_key.pem")
        val publicKey = keys.resolve("public_key.pem")
        val packaged = root.resolve("signed.json")

        assertEquals(
            0,
            Cli.run(
                listOf(
                    "package", pluginDir.path, packaged.path,
                    "--private-key", privateKey.path, "--key-id", "fixture-key"
                ),
                output,
                errors
            )
        )
        assertEquals(
            0,
            Cli.run(
                listOf(
                    "validate", packaged.path,
                    "--require-signature", "--public-key", publicKey.path, "--key-id", "fixture-key"
                ),
                output,
                errors
            )
        )
        assertTrue(packaged.readText().contains("SHA256withRSA"))
    }

    @Test
    fun `fixture capture copies local snapshot and refuses overwrite`() {
        val root = Files.createTempDirectory("readdock-cli-capture-").toFile()
        val input = root.resolve("input.html").apply { writeText("<h1>fixture</h1>") }
        val output = root.resolve("captured.html")
        val firstErrors = StringBuilder()
        assertEquals(0, Cli.run(listOf("fixture", "capture", input.path, output.path), StringBuilder(), firstErrors))
        assertEquals("<h1>fixture</h1>", output.readText())
        val secondErrors = StringBuilder()
        assertEquals(1, Cli.run(listOf("fixture", "capture", input.path, output.path), StringBuilder(), secondErrors))
        assertTrue(secondErrors.toString().contains("--force"))
    }
}
