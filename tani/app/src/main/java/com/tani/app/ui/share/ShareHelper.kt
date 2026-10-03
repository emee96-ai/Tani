package com.tani.app.ui.share

import android.content.Context
import android.content.Intent
import com.tani.app.BuildConfig

object ShareHelper {
    fun product(context: Context, productId: String, name: String) = share(
        context,
        "شوفي $name على تاني\n${link("product",productId)}"
    )

    fun store(context: Context, storeId: String, name: String) = share(
        context,
        "شوفي متجر $name على تاني\n${link("store",storeId)}"
    )

    fun referral(context: Context, code: String) = share(
        context,
        "انضمي لتاني بكود الإحالة: $code"
    )

    private fun share(context: Context, text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, "مشاركة عبر"))
    }

    private fun link(kind: String, id: String): String =
        if (BuildConfig.APP_LINK_HOST.endsWith(".invalid")) "tani://$kind/$id"
        else "https://${BuildConfig.APP_LINK_HOST}/$kind/$id"
}
