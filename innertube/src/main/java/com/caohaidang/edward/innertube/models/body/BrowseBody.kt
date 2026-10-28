package com.caohaidang.edward.innertube.models.body

import com.caohaidang.edward.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    /** Used by the charts page to pick the country (`selectedValues = ["VN"]`, `"ZZ"` = worldwide). */
    val formData: FormData? = null,
) {
    @Serializable
    data class FormData(
        val selectedValues: List<String>,
    )
}
