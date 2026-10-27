package com.chen.myviewer.reader.comic

import java.util.Locale
import kotlin.math.abs

/** Only recognized thumbnail names with an unambiguous source page are omitted. */
internal class ComicImageIndex(candidates: List<ComicImage>, size: (ComicImage) -> ImageSize?) {
    private data class NamedImage(val image: ComicImage, val path: String, val leaf: String, val thumbnail: Boolean)
    private data class Target(val path: String, val leaf: String, val trailingT: Boolean = false)
    private val aliases = mutableMapOf<String, String>()
    val images: List<ComicImage>
    private val positions: Map<String, Int>

    init {
        val named = candidates.map(::name)
        val originals = named.filterNot { it.thumbnail }
        val paths = originals.groupBy { it.path }
        val leaves = originals.groupBy { it.leaf }
        for (thumbnail in named) {
            val targets = buildList {
                if (thumbnail.thumbnail) add(Target(thumbnail.path, thumbnail.leaf))
                // Keep original names ending in t eligible as counterparts: forestt -> forest.
                if (thumbnail.leaf.length > 1 && thumbnail.leaf.endsWith('t')) {
                    val leaf = thumbnail.leaf.dropLast(1).trimEnd('_', '-', '.', ' ')
                    if (leaf.isNotEmpty()) add(Target(thumbnail.path.dropLast(thumbnail.leaf.length) + leaf, leaf, true))
                }
            }
            for (target in targets) {
                val matches = paths[target.path]
                    ?: if (target.path == target.leaf) leaves[target.leaf].orEmpty() else emptyList()
                val original = matches.singleOrNull() ?: continue
                if (original.image.name == thumbnail.image.name) continue
                val small = size(thumbnail.image) ?: continue
                val full = size(original.image) ?: continue
                if (small.width > full.width || small.height > full.height) continue
                val x = small.width.toLong() * full.height
                val y = small.height.toLong() * full.width
                if (!target.trailingT && abs(x - y).toDouble() > maxOf(x, y) * .02) continue
                val smaller = small.width < full.width || small.height < full.height
                val sameBytes = thumbnail.image.entry.crc >= 0 &&
                    thumbnail.image.entry.crc == original.image.entry.crc && thumbnail.image.entry.size == original.image.entry.size
                val lowerQuality = thumbnail.image.entry.size in 1 until original.image.entry.size
                if (smaller || if (target.trailingT) lowerQuality else sameBytes) {
                    aliases[thumbnail.image.name] = original.image.name
                    break
                }
            }
        }
        images = candidates.filterNot { it.name in aliases }
            .sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
        positions = images.withIndex().associate { it.value.name to it.index }
    }

    fun restoreIndex(entry: String, fallback: Int): Int {
        var original = entry
        val seen = mutableSetOf<String>()
        while (seen.add(original)) original = aliases[original] ?: break
        return positions[original] ?: fallback.coerceIn(0, images.lastIndex)
    }

    companion object {
        private val folders = setOf("thumb", "thumbs", "thumbnail", "thumbnails", "preview", "previews")
        private val prefix = Regex("^(?:thumb(?:nail)?s?|previews?)[_. -]+")
        private val suffix = Regex("[_. -]+(?:thumb(?:nail)?s?|previews?)$")

        private fun name(image: ComicImage): NamedImage {
            val parts = image.name.replace('\\', '/').lowercase(Locale.ROOT).split('/')
            val directories = parts.dropLast(1)
            val stem = parts.last().substringBeforeLast('.')
            val leaf = stem.replace(prefix, "").replace(suffix, "")
            val thumbnail = directories.any { it in folders } || leaf != stem
            val path = (directories.filterNot { it in folders } + leaf).joinToString("/")
            return NamedImage(image, path, leaf, thumbnail)
        }
    }
}
