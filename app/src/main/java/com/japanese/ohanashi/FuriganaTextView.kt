package com.japanese.ohanashi

import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.text.TextPaint
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.japanese.ohanashi.yutani.FuriganaView


data class FuriganaToken(var kanjiStart: Int, var start: Int, var end: Int)
data class FuriganaInsertToken(var start: Int, var furigana: String)

fun Float.PixelsToDP(context: Context) : Dp = Dp(this / (context.resources.displayMetrics.densityDpi / 160f))

// https://github.com/sh0/furigana-view
// https://github.com/fkt3/FuriganaTextView/tree/master/Furigana/furiganatextview
// different styles of resolving layout issues with furigana (rubi)
// https://japanese.stackexchange.com/questions/24187/whats-it-called-when-furigana-push-characters-apart-because-theyre-so-long
/**
fun Text(
text: String,
modifier: Modifier = Modifier,
color: Color = Color.Unspecified,
fontSize: TextUnit = TextUnit.Unspecified,
fontStyle: FontStyle? = null,
fontWeight: FontWeight? = null,
fontFamily: FontFamily? = null,
letterSpacing: TextUnit = TextUnit.Unspecified,
textDecoration: TextDecoration? = null,
textAlign: TextAlign? = null,
lineHeight: TextUnit = TextUnit.Unspecified,
overflow: TextOverflow = TextOverflow.Clip,
softWrap: Boolean = true,
maxLines: Int = Int.MAX_VALUE,
onTextLayout: (TextLayoutResult) -> Unit = {},
style: TextStyle = LocalTextStyle.current
)
 */

@Composable
fun YutaniFuriganaText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontColor: Color = Color.Unspecified,
    lineHeight: TextUnit = if (fontSize.isUnspecified) 22.sp else fontSize * 1.8f,
    showFurigana: Boolean = true
) {
    val textSizePx = with(LocalDensity.current) { fontSize.toPx() }
    val textColor = fontColor.takeOrElse { getTextColor() }
    AndroidView(
        factory = { context ->
            FuriganaView(context).apply {
                val tp = TextPaint()
                tp.textSize = textSizePx
                text_set(tp, text, 0, 0)
            }
        },
        modifier = modifier,
        update = {
            val tp = TextPaint()
            tp.textSize = textSizePx
            tp.color = textColor.toArgb()
            it.text_set(tp, text, 0, 0)
        }
    )
}

@Composable
fun FuriganaText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontColor: Color = Color.Unspecified,
    lineHeight: TextUnit = if (fontSize.isUnspecified) 22.sp else fontSize * 1.8f,
    showFurigana: Boolean = true,
    hideKanji: Boolean = true
) {
    val tokens: MutableList<FuriganaToken> = mutableListOf()
    var i = 0
    while(i < text.length)
    {
        if (text[i] == '[')
        {
            val kanji_start_i = i
            var start_i = i+1
            while (start_i < text.length)
            {
                if (text[start_i] == ';') {
                    var end_i = i;
                    while (text[end_i] != ']' )
                    {
                        ++end_i
                        if (end_i >= text.length) error("furigana bracket opened at index $end_i was never closed")
                    }

                    tokens.add(FuriganaToken(kanjiStart = kanji_start_i, start = start_i, end = end_i))
                    i = end_i

                    break
                }
                // if we hit another open bracket before we hit a ';' we just stop parsing this as a token
                if (text[start_i] == '[')
                    error("furigana bracket opened inside furigana bracket at $start_i in <${text.subSequence(kanji_start_i, start_i)}>")
                if (text[start_i] == ']') {
                    Log.e("FuriganaParsing", "furigana bracket closed without content $start_i in <${text.subSequence(kanji_start_i, start_i+1)}>")
                    tokens.add(FuriganaToken(kanjiStart = kanji_start_i, start = start_i, end = start_i+1))
                    break;
                }

                ++start_i
            }
        }
        ++i
    }

    var text_markup_removed = text
    var token_i = tokens.size-1
    while (token_i >= 0)
    {
        val token = tokens[token_i]
        if (hideKanji)
        {
            // remove closing bracket
            text_markup_removed = text_markup_removed.removeRange(token.end, token.end+1)
            // remove opening bracket and kanji
            text_markup_removed = text_markup_removed.removeRange(token.kanjiStart, token.start+1)
        }
        else
        {
            // remove furigana and bordering markup
            text_markup_removed = text_markup_removed.removeRange(token.start, token.end+1)
            // remove opening bracket before kanji
            text_markup_removed = text_markup_removed.removeRange(token.kanjiStart, token.kanjiStart+1)
        }
        --token_i
    }
    val insertTokens: MutableList<FuriganaInsertToken> = mutableListOf()
    if (!hideKanji)
    {
        var skipped_chars = 0
        for(insert_i in 0..tokens.size-1)
        {
            val token = tokens[insert_i]
            val token_text = text.substring(token.start+1, token.end)
            insertTokens.add(FuriganaInsertToken(token.kanjiStart - skipped_chars, token_text))
            skipped_chars += (token.end + 1) - token.start
            ++skipped_chars
        }
    }

    // do the actual text --------------------------------------------------------------------
    Box (modifier = modifier) {
        var _fontSize = fontSize;
        if (fontSize.isUnspecified) _fontSize = 14.sp
        val textLayoutResult: MutableState<TextLayoutResult?> = remember { mutableStateOf(null) }

        BaseText(
            text = text_markup_removed,
            fontSize = _fontSize,
            lineHeight = lineHeight,
            textLayoutResult = textLayoutResult,
            fontColor = fontColor,
            fontWeight = fontWeight
        )
        if (showFurigana && !hideKanji) {
            FuriganaCanvas(
                insertTokens = insertTokens,
                textLayoutResult = textLayoutResult,
                fontSize = _fontSize,
                fontColor = fontColor,
                fontWeight = fontWeight
            )
        }
    }
}

@Composable
fun BaseText(
    text: String,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontColor: Color,
    lineHeight: TextUnit,
    fontWeight: FontWeight? = null,
    textLayoutResult: MutableState<TextLayoutResult?>
) {
    textLayoutResult.value = null
    Text(
        text,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        color = fontColor,
        onTextLayout = { textLayoutResult.value = it }
    )
}

@Composable
fun Furigana(
    insertTokens: MutableList<FuriganaInsertToken>,
    textLayoutResult: MutableState<TextLayoutResult?>,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = 7.sp,
    fontColor: Color,
) {
    if (textLayoutResult.value != null) {
        val fontSizeDp = with(LocalDensity.current) { fontSize.toDp() }
        val last_baseline_offset = with(LocalDensity.current)
        {
            (textLayoutResult.value!!.size.height - textLayoutResult.value!!.lastBaseline).toDp()
        }
        val general_baseline_offset = with(LocalDensity.current)
        {
            (textLayoutResult.value!!.getLineBottom(0) - textLayoutResult.value!!.getLineTop(0) - textLayoutResult.value!!.firstBaseline).toDp()
        }
        val context = LocalContext.current

        for (token in insertTokens) {
            val bb = textLayoutResult.value!!.getBoundingBox(token.start)
            val line_index = textLayoutResult.value!!.getLineForOffset(token.start)
            val baseline_offset = if (line_index == textLayoutResult.value!!.lineCount - 1) last_baseline_offset else general_baseline_offset

            /**
            Box(
            modifier = Modifier
            .offset(x = bb.left.PixelsToDP(con), y = bb.top.PixelsToDP(con))
            .height(bb.height.PixelsToDP(con)).width(bb.width.PixelsToDP(con))
            .background(Color(0.8f, 0.5f, 0f, 0.5f))
            )
             */
            /**
            Box(
            modifier = Modifier
            .offset(x = bb.left.PixelsToDP(con), y = bb.top.PixelsToDP(con))
            .height(bb.height.PixelsToDP(con)).width(bb.width.PixelsToDP(con))
            .background(Color(0.8f, 0.5f, 0f, 0.5f))
            )
             */
            Box(modifier = Modifier.offset(x = bb.left.PixelsToDP(context), y = (bb.bottom).PixelsToDP(context) - baseline_offset - (fontSizeDp * 1.8f)))
            {
                Text(
                    token.furigana,
                    fontSize = fontSize * 0.4f,
                    color = fontColor,
                    letterSpacing = -0.1.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = fontWeight,
                )
            }
        }
    }
}

//@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun FuriganaCanvas(
    insertTokens: MutableList<FuriganaInsertToken>,
    textLayoutResult: MutableState<TextLayoutResult?>,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = 7.sp,
    fontColor: Color,
) {
    if (textLayoutResult.value != null) {
        val layoutResult = textLayoutResult.value!!

        val lastBaselineOffset = (layoutResult.size.height - layoutResult.lastBaseline)
        val firstLineHeight = layoutResult.getLineBottom(0) - layoutResult.getLineTop(0)
        val generalBaselineOffset = firstLineHeight - layoutResult.firstBaseline

        val textColor = fontColor.takeOrElse { getTextColor() }.toArgb()
        val fontSizePx = with(LocalDensity.current) { fontSize.toPx() }
        val textSizePx = with(LocalDensity.current) { fontSize.toPx() * 0.4f }

        Canvas(modifier = Modifier) {
            val textPaint = Paint().apply {
                color = textColor
                textAlign = Paint.Align.LEFT
                this.textSize = textSizePx
                letterSpacing = -0.1f
            }

            for (token in insertTokens) {
                val bb = layoutResult.getBoundingBox(token.start)
                val lineIndex = layoutResult.getLineForOffset(token.start)
                val baselineOffset = if (lineIndex == layoutResult.lineCount - 1) lastBaselineOffset else generalBaselineOffset
                val y = bb.bottom - baselineOffset - fontSizePx

                this.drawContext.canvas.nativeCanvas.drawText(token.furigana, bb.left, y, textPaint)
                // for debugging
//                drawRect(
//                    topLeft = bb.topLeft,
//                    size = bb.size,
//                    color = Color(1f, 1f, 1f, 0.2f),
//                )
            }
        }
    }
}