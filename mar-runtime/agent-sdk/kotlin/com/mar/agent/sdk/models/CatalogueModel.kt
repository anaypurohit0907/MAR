package com.mar.agent.sdk.models

data class CatalogueModel(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val sizeMb: Long,
    val quantization: String,
    val family: String,
    val parameters: String
) {
    companion object {
        fun fromHfResult(result: HfSearchResult, file: HfFileInfo): CatalogueModel {
            val parts = result.id.split("/")
            return CatalogueModel(
                id = file.name.replace(".gguf", ""),
                name = "${parts.getOrNull(1) ?: result.id} (${file.quantization})",
                description = result.id,
                url = "https://huggingface.co/${result.id}/resolve/main/${file.name}",
                sizeMb = file.size / (1024 * 1024),
                quantization = file.quantization,
                family = parts.firstOrNull() ?: "",
                parameters = ""
            )
        }
    }
}
