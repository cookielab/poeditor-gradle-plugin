package io.cookielab.android.poeditor.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class representing response from POEditor.
 */
@Serializable
internal data class PoeditorExportResponse(
    @SerialName("response")
    val response: Response,
    @SerialName("result")
    val result: Result? = null,
) {

    @Serializable
    data class Response(
        @SerialName("status")
        val status: String,
        @SerialName("code")
        val code: String,
        @SerialName("message")
        val message: String,
    )

    @Serializable
    data class Result(
        @SerialName("url")
        val url: String,
    )
}
