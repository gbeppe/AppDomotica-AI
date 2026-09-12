package com.domopi.app.data

/** Exact partition: joining the chunks restores the displayed answer, including whitespace. */
fun speechChunks(text: String, limit: Int): List<String> {
    require(limit > 0)
    val chunks = mutableListOf<String>()
    var offset = 0
    while (offset < text.length) {
        var end = minOf(offset + limit, text.length)
        if (end < text.length) {
            val space = text.lastIndexOf(' ', end - 1)
            if (space > offset) end = space + 1
            else if (end > offset + 1 && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        }
        chunks += text.substring(offset, end)
        offset = end
    }
    return chunks
}
