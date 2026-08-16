package com.comichub.source.runtime

import com.comichub.source.api.ComicSource

class SourceRegistry(sources: List<ComicSource>) {
    private val sourcesById = LinkedHashMap<String, ComicSource>()

    init {
        sources.forEach(::register)
    }

    val sources: List<ComicSource>
        get() = sourcesById.values.toList()

    fun register(source: ComicSource) {
        sourcesById[source.manifest.id] = source
    }

    fun replace(sources: List<ComicSource>) {
        sourcesById.clear()
        sources.forEach(::register)
    }

    fun require(sourceId: String): ComicSource =
        sourcesById[sourceId] ?: error("找不到漫画源：$sourceId")

    companion object {
        /**
         * Production starts with no bundled content source. Sources are added
         * only by a trusted external plugin or by an injected test fixture.
         */
        fun default(): SourceRegistry = SourceRegistry(emptyList())
    }
}
