package com.tani.app.ui.share

import android.content.Context
import android.content.Intent

object ShareHelper {
    fun product(context: Context, productId: String, name: String) = share(
        context,
        "شوفي $name على تاني\ntani://product/$productId"
    )

    fun store(context: Context, storeId: String, name: String) = share(
        context,
        "شوفي متجر $name على تاني\ntani://store/$storeId"
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
}
