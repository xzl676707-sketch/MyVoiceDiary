package com.jingwen.voicediary.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.jingwen.voicediary.data.StickerCatalog
import kotlinx.coroutines.delay

/** About two body-text lines; ALIGN_BOTTOM keeps following text beside its lower line. */
private const val STICKER_SIZE_DP = 44

private fun TextView.renderInlineStickers() {
    val spannable = text as? Spannable ?: return
    spannable.getSpans(0, spannable.length, ImageSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        if (start < 0 || end != start + 1 || StickerCatalog.fromInlineChar(spannable[start]) == null) {
            spannable.removeSpan(span)
        }
    }
    val size = (STICKER_SIZE_DP * resources.displayMetrics.density).toInt()
    spannable.forEachIndexed { index, value ->
        val sticker = StickerCatalog.fromInlineChar(value) ?: return@forEachIndexed
        val alreadyRendered = spannable.getSpans(index, index + 1, ImageSpan::class.java).any {
            spannable.getSpanStart(it) == index && spannable.getSpanEnd(it) == index + 1
        }
        if (alreadyRendered) return@forEachIndexed
        val bitmap = runCatching {
            context.assets.open(sticker.assetPath).use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return@forEachIndexed
        val drawable = BitmapDrawable(resources, bitmap).apply { setBounds(0, 0, size, size) }
        spannable.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private class InlineStickerEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditText(context, attrs) {
    var suppressChanges = false
    var onTextAndCursor: ((String, Int) -> Unit)? = null
    var onCursor: ((Int) -> Unit)? = null

    init {
        background = null
        setPadding(0, 0, 0, 0)
        gravity = Gravity.TOP or Gravity.START
        setTextColor(AndroidColor.rgb(0x5E, 0x4D, 0x50))
        setHintTextColor(AndroidColor.rgb(0xC3, 0xAF, 0xB2))
        textSize = 16f
        includeFontPadding = false
        setLineSpacing(7f * resources.displayMetrics.density, 1.15f)
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        isSingleLine = false
        isVerticalScrollBarEnabled = false
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(value: Editable?) {
                if (suppressChanges || value == null) return
                renderInlineStickers()
                onTextAndCursor?.invoke(value.toString(), selectionStart.coerceAtLeast(0))
            }
        })
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!suppressChanges && selStart >= 0) onCursor?.invoke(selStart)
    }

    fun applyModel(value: String) {
        if (text?.toString() == value) {
            renderInlineStickers()
            return
        }
        suppressChanges = true
        setText(value)
        renderInlineStickers()
        setSelection(value.length)
        suppressChanges = false
    }
}

@Composable
fun InlineStickerEditorText(
    value: String,
    placeholder: String,
    enabled: Boolean,
    isLast: Boolean,
    focusRequestId: Long,
    focusCursorOffset: Int,
    onChange: (String, Int) -> Unit,
    onCursor: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val editor = remember { arrayOfNulls<InlineStickerEditText>(1) }
    AndroidView(
        factory = { context ->
            InlineStickerEditText(context).also {
                editor[0] = it
                it.hint = placeholder
                it.minHeight = ((if (isLast) 72 else 44) * context.resources.displayMetrics.density).toInt()
                it.onTextAndCursor = onChange
                it.onCursor = onCursor
                it.applyModel(value)
            }
        },
        update = {
            editor[0] = it
            it.isEnabled = enabled
            it.hint = placeholder
            // Keep the final block easy to tap without making Android scroll a tall,
            // mostly empty EditText above the keyboard.
            it.minHeight = ((if (isLast) 72 else 44) * it.resources.displayMetrics.density).toInt()
            it.onTextAndCursor = onChange
            it.onCursor = onCursor
            it.applyModel(value)
        },
        modifier = modifier
    )
    LaunchedEffect(focusRequestId) {
        if (focusRequestId <= 0L) return@LaunchedEffect
        delay(120)
        editor[0]?.let {
            it.requestFocus()
            it.setSelection(focusCursorOffset.coerceIn(0, it.text?.length ?: 0))
            (it.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}

@Composable
fun InlineStickerBodyText(
    value: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF5E4D50)
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            TextView(context).apply {
                setTextColor(color.toArgb())
                textSize = 16f
                includeFontPadding = false
                setLineSpacing(8f * resources.displayMetrics.density, 1.2f)
                setPadding(0, 0, 0, 0)
            }
        },
        update = { view ->
            view.setTextColor(color.toArgb())
            if (view.text.toString() != value || view.text !is Spannable) {
                view.setText(SpannableString(value), TextView.BufferType.SPANNABLE)
            }
            view.renderInlineStickers()
        },
        modifier = modifier
    )
}
