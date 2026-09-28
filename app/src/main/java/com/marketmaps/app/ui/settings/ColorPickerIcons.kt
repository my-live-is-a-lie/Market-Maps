package com.marketmaps.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * أيقونتان مرسومتان يدوياً (نسخ ولصق) بدل إضافة مكتبة أيقونات كاملة:
 * الأيقونتان المطلوبتان (ContentCopy/ContentPaste) في حزمة material-icons-extended
 * التي تضيف حجماً كبيراً للتطبيق مقابل أيقونتين فقط.
 */

/** أيقونة النسخ: ورقتان متراكبتان — الخلفية مفرّغة والأمامية ممتلئة */
@Composable
internal fun CopyCodeIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        val corner = CornerRadius(w * 0.12f, w * 0.12f)

        // الورقة الخلفية (أعلى اليمين) — إطار مفرّغ
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.36f, h * 0.06f),
            size = Size(w * 0.58f, h * 0.58f),
            cornerRadius = corner,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // الورقة الأمامية (أسفل اليسار) — ممتلئة فتغطي التقاطع
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.06f, h * 0.36f),
            size = Size(w * 0.58f, h * 0.58f),
            cornerRadius = corner
        )
    }
}

/** أيقونة اللصق: لوح كتابة بمشبك وورقة داخله */
@Composable
internal fun PasteCodeIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.085f
        val corner = CornerRadius(w * 0.12f, w * 0.12f)
        val outline = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // جسم اللوح
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.14f, h * 0.16f),
            size = Size(w * 0.72f, h * 0.78f),
            cornerRadius = corner,
            style = outline
        )
        // المشبك العلوي
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.34f, h * 0.02f),
            size = Size(w * 0.32f, h * 0.22f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            style = outline
        )
        // الورقة داخل اللوح
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.32f, h * 0.42f),
            size = Size(w * 0.36f, h * 0.32f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
