package io.github.krosstack.kscope.semantic.worker.tokenizer

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.readText

internal class TokenizerFile(path: Path) {
    private val root = KscopeJson.parseToJsonElement(path.readText()).jsonObject
    private val model = root.getValue("model").jsonObject
    private val normalizer = root.getValue("normalizer").jsonObject
    val vocabulary: Map<String, Long> = model.getValue(
        "vocab",
    ).jsonObject.mapValues { it.value.jsonPrimitive.int.toLong() }
    val unknown: Long = vocabulary.getValue(model.getValue("unk_token").jsonPrimitive.content)
    val continuation: String = model.getValue("continuing_subword_prefix").jsonPrimitive.content
    val maxWord: Int = model.getValue("max_input_chars_per_word").jsonPrimitive.int
    val lowercase: Boolean = normalizer.flag("lowercase")
    val stripAccents: Boolean = normalizer.getValue("strip_accents").jsonPrimitive.booleanOrNull ?: lowercase
    val cleanText: Boolean = normalizer.flag("clean_text")
    val chinese: Boolean = normalizer.flag("handle_chinese_chars")
    val special: List<String> = root.getValue("added_tokens").jsonArray.map {
        it.jsonObject.getValue("content").jsonPrimitive.content
    }
    val cls: Long = vocabulary.getValue("[CLS]")
    val sep: Long = vocabulary.getValue("[SEP]")
    val pad: Long = vocabulary.getValue("[PAD]")

    init {
        require(model.getValue("type").jsonPrimitive.content == "WordPiece")
        require(normalizer.getValue("type").jsonPrimitive.content == "BertNormalizer")
        require(root.getValue("pre_tokenizer").jsonObject.getValue("type").jsonPrimitive.content == "BertPreTokenizer")
    }

    private fun JsonObject.flag(name: String): Boolean = getValue(name).jsonPrimitive.boolean
}
