package com.tani.app.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * يفك الصورة بحجم مصغَّر مسبقاً بدل تحميل صورة الكاميرا كاملة في الذاكرة.
 * النتيجة لا يقل ضلعها الأطول عن [maxSize] إن كانت الصورة الأصلية أكبر منه،
 * ويبقى التصغير الدقيق على من يستدعيها.
 */
fun ContentResolver.decodeBoundedBitmap(uri: Uri, maxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSize)
    }
    return openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

/** أكبر قوة للعدد 2 تُبقي الضلع الأطول بعد القسمة عليها أكبر من [maxSize] أو مساوياً له. */
internal fun sampleSizeFor(width: Int, height: Int, maxSize: Int): Int {
    val largest = maxOf(width, height)
    var sample = 1
    while (largest / (sample * 2) >= maxSize) sample *= 2
    return sample
}
