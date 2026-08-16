/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.caverock.androidsvg.SVG
import kotlinx.serialization.Serializable
import net.aokaze.osupanel.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * "osu-stats-signature" renderer (template `full`, 550x320) for the widget.
 *
 * The template is vendored by `scripts/vendor_signature_assets.py` into two
 * CSS-free SVG layers (all styles inlined) and no dynamic text/images:
 *  - [TEMPLATE_BG]: background rect + header band (cover is drawn in the middle)
 *  - [TEMPLATE_FG]: all decoration — avatar clip, grade badge, static labels,
 *    progress bar, dst (drawn ON TOP of the cover)
 *
 * AndroidSVG only renders SVG; text (name, numbers, flag, mode icon) is drawn
 * via Canvas at the SAME coordinates as the original render.js (viewBox 550x320),
 * then the bitmap is rendered 2x for crispness on screen. Theme colors derive
 * from hsl(hue) like the original project.
 */
object SignatureRenderer {

    const val VIEW_W = 550f
    const val VIEW_H = 320f
    const val SCALE = 2f
    const val OUT_W = (VIEW_W * SCALE).toInt() // 1100
    const val OUT_H = (VIEW_H * SCALE).toInt() // 640

    const val MINI_W = 400f
    const val MINI_H = 120f
    const val MINI_OUT_W = (MINI_W * SCALE).toInt() // 800
    const val MINI_OUT_H = (MINI_H * SCALE).toInt() // 240

    /** Read one SVG template/icon from res/raw (no longer from assets). */
    private fun readRawSvg(context: Context, resId: Int): String {
        return context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
    }

    /** One osu!skills skill (raw value + percentage 0..100). */
    @Serializable
    data class SkillValue(
        val value: Int = 0,
        val percent: Float = 0f,
    )

    /** Skills data from osuskills.com (7 skills + rank tags). */
    @Serializable
    data class SkillsData(
        val stamina: SkillValue = SkillValue(),
        val accuracy: SkillValue = SkillValue(),
        val precision: SkillValue = SkillValue(),
        val reaction: SkillValue = SkillValue(),
        val agility: SkillValue = SkillValue(),
        val tenacity: SkillValue = SkillValue(),
        val memory: SkillValue = SkillValue(),
        val tags: List<String> = emptyList(),
    ) {
        /** The 6 skills drawn on the radar (memory unused — stat-sign counterpart). */
        val radarSkills: List<SkillValue>
            get() = listOf(stamina, accuracy, precision, reaction, agility, tenacity)
    }

    /** All dynamic data shown on the signature. */
    data class Data(
        val username: String,
        val countryCode: String,
        val countryName: String,
        val level: Int,
        val levelProgressPercent: Int, // 0..100
        val pp: String,
        val medals: String,
        val playtime: String,
        val globalRank: String,
        val countryRank: String,
        val rankedScore: String,
        val playCount: String,
        val totalScore: String,
        val totalHits: String,
        val replays: String,
        val acc: String,
        val maxCombo: String,
        val bp: String,
        val firstPlace: String,
        val gradeSsh: Int,
        val gradeSs: Int,
        val gradeSh: Int,
        val gradeS: Int,
        val gradeA: Int,
        val profileColour: String?,
        val playmode: String = "std",
        /** "stats" (default) or "skills" (osu!skills radar). */
        val layout: String = "stats",
        val skills: SkillsData? = null,
        val cover: Bitmap? = null,
        val avatar: Bitmap? = null,
    )

    private enum class HAnchor { LEFT, RIGHT, CENTER }
    private enum class VAnchor { TOP, MIDDLE }

    // Typeface & template load once per process.
    @Volatile
    private var regular: Typeface? = null
    @Volatile
    private var bold: Typeface? = null
    @Volatile
    private var bgSvg: String? = null
    @Volatile
    private var fgSvg: String? = null
    @Volatile
    private var skillsFgSvg: String? = null
    @Volatile
    private var miniFgSvg: String? = null

    private val flagCache = HashMap<String, Bitmap>()
    private val modeCache = HashMap<String, Bitmap>()

    /** Renders the signature at 1100x640. Safe to call from any thread. */
    fun render(context: Context, data: Data): Bitmap {
        ensureAssets(context)

        val hue = hueFromColour(data.profileColour)
        val h1 = hslColor(hue, 1f, 0.7f)
        val b5 = hslColor(hue, 0.1f, 0.15f)
        val b6 = hslColor(hue, 0.1f, 0.1f)
        val b4 = hslColor(hue, 0.1f, 0.2f)
        val f1 = hslColor(hue, 0.1f, 0.6f)

        val out = Bitmap.createBitmap(OUT_W, OUT_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 1. Lapisan latar (rects + band header).
        val svgBg = SVG.getFromString(substitute(bgSvg!!, hue, h1, b5, b6, b4, f1, levelBarFg(data.levelProgressPercent, h1)))
        svgBg.setDocumentWidth(OUT_W.toFloat())
        svgBg.setDocumentHeight(OUT_H.toFloat())
        svgBg.renderToCanvas(canvas)

        // 2. Profile cover — in the middle of the z-order (above the bg rect, below fg decoration).
        data.cover?.let { drawCover(canvas, it) }

        // 3. Decoration layer — template per layout (stats / skills).
        val isSkills = data.layout == "skills"
        val fg = if (isSkills) skillsFgSvg!! else fgSvg!!
        val svgFg = SVG.getFromString(substitute(fg, hue, h1, b5, b6, b4, f1, levelBarFg(data.levelProgressPercent, h1)))
        svgFg.setDocumentWidth(OUT_W.toFloat())
        svgFg.setDocumentHeight(OUT_H.toFloat())
        svgFg.renderToCanvas(canvas)

        // 4. Dynamic elements above the decoration.
        data.avatar?.let { drawAvatar(canvas, it) }
        drawFlag(context, canvas, data.countryCode)
        drawModeIcon(context, canvas, data.playmode)

        val reg = regular!!.let { Typeface.create(it, Typeface.NORMAL) }
        val bld = bold!!.let { Typeface.create(it, Typeface.NORMAL) }

        // Name / country / mode
        drawText(canvas, data.username, 130f, 20f, 28f, bld, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.countryName.ifBlank { data.countryCode }, 161f, 59.5f, 14f, reg, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, playmodeName(data.playmode), 150f, 89f, 12f, reg, HAnchor.LEFT, VAnchor.TOP)

        // Level + progress (percentage enlarged slightly so the % icon is clearly visible)
        drawText(canvas, data.level.toString(), 290f, 143f, 12f, bld, HAnchor.CENTER, VAnchor.MIDDLE)
        drawText(canvas, "${data.levelProgressPercent}%", 259.5f, 145f, 11f, reg, HAnchor.RIGHT, VAnchor.TOP)

        // Grade counts (SSH, SS, SH, S, A)
        val grades = listOf(data.gradeSsh, data.gradeSs, data.gradeSh, data.gradeS, data.gradeA)
        var gradeX = 360.7f
        for (count in grades) {
            drawText(canvas, count.toString(), gradeX, 153f, 9f, reg, HAnchor.CENTER, VAnchor.MIDDLE)
            gradeX += 38.62f
        }

        // Left column: PP / medals / playtime
        drawText(canvas, data.pp, 20f, 202f, 13f, reg, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.medals, 82f, 202f, 13f, reg, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.playtime, 126f, 202f, 13f, reg, HAnchor.LEFT, VAnchor.TOP)

        // Ranks
        val rankSize = if (data.globalRank.length < 10) 27f else 25f
        drawText(canvas, data.globalRank, 268f, 211f, rankSize, reg, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.countryRank, 269f, 277f, 17f, reg, HAnchor.LEFT, VAnchor.TOP)

        if (isSkills) {
            // Skills mode: the osu!skills radar replaces the stats column.
            drawSkills(canvas, data.skills, reg, h1, f1, b5)
        } else {
            // Bottom-left stats (right-aligned at x=218) — stats mode only.
            val stats = listOf(data.rankedScore, data.playCount, data.totalScore, data.totalHits, data.replays)
            var statY = 227f
            for (value in stats) {
                drawText(canvas, value, 218f, statY, 10f, reg, HAnchor.RIGHT, VAnchor.TOP)
                statY += 16f
            }
        }

        // Right column: acc / max combo / bp / first place
        drawText(canvas, data.acc, 424f, 202f, 13f, reg, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.maxCombo, 483f, 202f, 13f, reg, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.bp, 424f, 249f, 13f, reg, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.firstPlace, 483f, 249f, 13f, reg, HAnchor.LEFT, VAnchor.TOP)

        return out
    }

    /**
     * Renders the MINI signature at 800x240 (template `mini`, 400x120) — for the PP widget.
     * Text/flag/icon positions are measured directly from the original reference render
     * (the same trick as the full template).
     */
    fun renderMini(context: Context, data: Data): Bitmap {
        ensureAssets(context)
        if (miniFgSvg == null) {
            synchronized(this) {
                if (miniFgSvg == null) {
                    miniFgSvg = readRawSvg(context, R.raw.template_mini_fg)
                }
            }
        }

        val hue = hueFromColour(data.profileColour)
        val h1 = hslColor(hue, 1f, 0.7f)
        val b5 = hslColor(hue, 0.1f, 0.15f)
        val b4 = hslColor(hue, 0.1f, 0.2f)

        val out = Bitmap.createBitmap(MINI_OUT_W, MINI_OUT_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val s = SCALE

        // 1. Kartu dasar (rect hsl-b5, rounded 8).
        canvas.drawRoundRect(
            RectF(0f, 0f, MINI_W * s, MINI_H * s),
            8f * s, 8f * s,
            Paint().apply { color = b5 },
        )

        // 2. Cover (opacity 0.5, clipped to the full rounded card).
        data.cover?.let { cover ->
            val clip = Path().apply {
                addRoundRect(RectF(0f, 0f, MINI_W, MINI_H), 8f, 8f, Path.Direction.CW)
                transform(android.graphics.Matrix().apply { setScale(s, s) })
            }
            canvas.save()
            canvas.clipPath(clip)
            val src = centerCropRect(cover, MINI_W, MINI_H)
            val dst = RectF(0f, 0f, MINI_W * s, MINI_H * s)
            canvas.drawBitmap(cover, src, dst, Paint().apply { alpha = (0.5f * 255).roundToInt() })
            canvas.restore()
        }

        // 3. Dark template overlay: bottom (y 50..120, hsl-b5 0.9) & top (y 0..50, hsl-b4 0.6).
        val bottom = Path().apply {
            addRoundRect(RectF(0f, 50f, MINI_W, MINI_H), floatArrayOf(0f, 0f, 0f, 0f, 8f, 8f, 8f, 8f), Path.Direction.CW)
            transform(android.graphics.Matrix().apply { setScale(s, s) })
        }
        canvas.drawPath(bottom, Paint().apply { color = b5; alpha = (0.9f * 255).roundToInt() })
        val top = Path().apply {
            addRoundRect(RectF(0f, 0f, MINI_W, 50f), floatArrayOf(8f, 8f, 8f, 8f, 0f, 0f, 0f, 0f), Path.Direction.CW)
            transform(android.graphics.Matrix().apply { setScale(s, s) })
        }
        canvas.drawPath(top, Paint().apply { color = b4; alpha = (0.6f * 255).roundToInt() })

        // 4. Static fg decoration (labels & divider).
        val svg = SVG.getFromString(
            miniFgSvg!!
                .replace("{{hsl-b5}}", hex(b5))
                .replace("{{hsl-b4}}", hex(b4))
                .replace("{{hsl-h1}}", hex(h1)),
        )
        svg.setDocumentWidth(MINI_OUT_W.toFloat())
        svg.setDocumentHeight(MINI_OUT_H.toFloat())
        svg.renderToCanvas(canvas)

        // 5. Avatar, flag, mode icon.
        data.avatar?.let { drawMiniAvatar(canvas, it) }
        drawFlagAt(context, canvas, data.countryCode, 365.5f, 8f, 18f)
        drawModeIconAt(context, canvas, data.playmode, 372f, 30f, 12f)

        // 6. Text — exact positions from the original reference render (text-to-svg).
        val reg = regular!!.let { Typeface.create(it, Typeface.NORMAL) }
        val bld = bold!!.let { Typeface.create(it, Typeface.NORMAL) }
        drawText(canvas, data.username, 118f, 14f, 25f, bld, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.countryRank, 360f, 12f, 10f, reg, HAnchor.RIGHT, VAnchor.TOP)
        drawText(canvas, "lv.${data.level}", 369f, 31f, 10f, reg, HAnchor.RIGHT, VAnchor.TOP)
        val gRankSize = if (data.globalRank.length < 10) 18f else 17f
        drawText(canvas, data.globalRank, 120f, 86f, gRankSize, reg, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.pp, 226f, 81.5f, 13f, reg, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.acc, 281f, 81.5f, 13f, reg, HAnchor.LEFT, VAnchor.TOP)
        drawText(canvas, data.playCount, 336f, 81.5f, 13f, reg, HAnchor.LEFT, VAnchor.TOP)

        return out
    }

    /** Mini avatar: 4px rounded clip (x 16..106, y 15..105), 90x90 image. */
    private fun drawMiniAvatar(canvas: Canvas, avatar: Bitmap) {
        val s = SCALE
        val clip = Path().apply {
            addRoundRect(RectF(16f, 15f, 106f, 105f), 4f, 4f, Path.Direction.CW)
            transform(android.graphics.Matrix().apply { setScale(s, s) })
        }
        canvas.save()
        canvas.clipPath(clip)
        val dst = RectF(16f * s, 15f * s, 106f * s, 105f * s)
        val src = centerCropRect(avatar, 90f, 90f)
        canvas.drawBitmap(avatar, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    // ── Template & colors ──────────────────────────────────────────────

    private fun ensureAssets(context: Context) {
        if (bgSvg == null || fgSvg == null) {
            synchronized(this) {
                if (bgSvg == null) {
                    bgSvg = readRawSvg(context, R.raw.template_bg)
                    fgSvg = readRawSvg(context, R.raw.template_fg)
                    // Skills template: drop the memory icon (reaction is used —
                    // the stat-sign default without ?skillmemory).
                    skillsFgSvg = readRawSvg(context, R.raw.template_skills_fg)
                        .let { removeSvgGroup(it, "skill-memory") }
                }
            }
        }
        if (regular == null) {
            synchronized(this) {
                if (regular == null) {
                    // Fonts moved from assets to res/font (Comfortaa).
                    regular = context.resources.getFont(R.font.comfortaa_regular)
                    bold = context.resources.getFont(R.font.comfortaa_bold)
                }
            }
        }
    }

    private fun substitute(
        template: String,
        hue: Float,
        h1: Int,
        b5: Int,
        b6: Int,
        b4: Int,
        f1: Int,
        levelBar: String,
    ): String {
        return template
            .replace("{{hsl-h1}}", hex(h1))
            .replace("{{hsl-b5}}", hex(b5))
            .replace("{{hsl-b6}}", hex(b6))
            .replace("{{hsl-b4}}", hex(b4))
            .replace("{{hsl-f1}}", hex(f1))
            .replace("{{level-bar-fg}}", levelBar)
    }

    private fun levelBarFg(progressPercent: Int, h1: Int): String {
        val x = ((progressPercent / 100f) * (256 - 21) + 21).roundToInt().coerceIn(21, 256)
        return "<path fill=\"${hex(h1)}\" d=\"M20,135a2.5,2.5,0,0,0,2.5,2.5H${x}.833a2.5,2.5,0,0,0,0-5H22.5A2.5,2.5,0,0,0,20,135Z\" transform=\"translate(0 2)\" />"
    }

    private fun hueFromColour(colour: String?): Float {
        val c = colour ?: return 336f
        val v = runCatching { android.graphics.Color.parseColor(c) }.getOrNull() ?: return 336f
        val r = Color.red(v) / 255f
        val g = Color.green(v) / 255f
        val b = Color.blue(v) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d == 0f) return 336f
        var h = when {
            max == r -> 60f * (((g - b) / d) % 6)
            max == g -> 60f * ((b - r) / d + 2)
            else -> 60f * ((r - g) / d + 4)
        }
        if (h < 0) h += 360f
        return h
    }

    /** HSL -> ARGB conversion (s & l in 0..1). */
    private fun hslColor(h: Float, s: Float, l: Float): Int {
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = l - c / 2
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color.rgb(((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    private fun hex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)

    // ── Dynamic drawing (Canvas) ───────────────────────────────────────

    /** Cover: clipped per the template (rounded top + rect 10..120), opacity 0.2. */
    private fun drawCover(canvas: Canvas, cover: Bitmap) {
        val clip = Path().apply {
            fillType = Path.FillType.WINDING
            addRoundRect(RectF(0f, 0f, 550f, 50f), 8f, 8f, Path.Direction.CW)
            addRect(RectF(0f, 10f, 550f, 120f), Path.Direction.CW)
        }
        val s = SCALE
        clip.transform(android.graphics.Matrix().apply { setScale(s, s) })
        canvas.save()
        canvas.clipPath(clip)
        val paint = Paint().apply { alpha = (0.2f * 255).roundToInt() }
        canvas.drawBitmap(cover, null, RectF(0f, 0f, VIEW_W * s, 120f * s), paint)
        canvas.restore()
    }

    /** Avatar: 4px rounded clip (x 20..105, y 18..103), center-cropped 85x85 image. */
    private fun drawAvatar(canvas: Canvas, avatar: Bitmap) {
        val s = SCALE
        val clip = Path().apply {
            addRoundRect(RectF(20f, 18f, 105f, 103f), 4f, 4f, Path.Direction.CW)
            transform(android.graphics.Matrix().apply { setScale(s, s) })
        }
        canvas.save()
        canvas.clipPath(clip)
        val dst = RectF(20f * s, 20f * s, 105f * s, 105f * s)
        val src = centerCropRect(avatar, 85f, 85f)
        canvas.drawBitmap(avatar, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    /** Flag: PNG from `flag_{code}.png` in res/drawable at the full template's original position. */
    private fun drawFlag(context: Context, canvas: Canvas, countryCode: String) {
        drawFlagAt(context, canvas, countryCode, 132.2f, 56f, 20f)
    }

    /** Playmode icon: modes SVG rendered 15x15 at the full template's original position. */
    private fun drawModeIcon(context: Context, canvas: Canvas, playmode: String) {
        drawModeIconAt(context, canvas, playmode, 130f, 88f, 15f)
    }

    private fun drawFlagAt(context: Context, canvas: Canvas, countryCode: String, x: Float, y: Float, size: Float) {
        val code = countryCode.uppercase()
        if (code.length != 2) return
        // SINGLE flag source: `flag_{code}.png` in res/drawable — the same PNG
        // used by CountryFlagImage in the UI. The duplicated SVG flags folder was removed.
        val bitmap = flagCache.getOrPut(code) {
            val resId = context.resources.getIdentifier(
                "flag_${code.lowercase()}", "drawable", context.packageName
            )
            if (resId == 0) EMPTY else runCatching {
                context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: EMPTY
        }
        if (bitmap === EMPTY) return
        // Flag PNG 150x108 (osu! web ratio) — draw at the template's design
        // width, height scaled to keep the ratio, vertically centered.
        val w = size
        val h = size * (bitmap.height.toFloat() / bitmap.width.toFloat())
        drawScaled(canvas, bitmap, x, y + (size - h) / 2f, w, h)
    }

    private fun drawModeIconAt(context: Context, canvas: Canvas, playmode: String, x: Float, y: Float, size: Float) {
        val bitmap = modeCache.getOrPut("$playmode@${size.toInt()}") {
            renderModeIcon(context, playmode, (size * SCALE).toInt()) ?: return@getOrPut EMPTY
        }
        if (bitmap === EMPTY) return
        drawScaled(canvas, bitmap, x, y, size, size)
    }

    /** Render one mode icon from res/raw (`mode_{std|catch|taiko|mania}.svg`). */
    private fun renderModeIcon(context: Context, playmode: String, sizePx: Int): Bitmap? {
        val resId = when (playmode) {
            "catch" -> R.raw.mode_catch
            "taiko" -> R.raw.mode_taiko
            "mania" -> R.raw.mode_mania
            else -> R.raw.mode_std
        }
        return runCatching {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val svg = SVG.getFromString(readRawSvg(context, resId))
            svg.setDocumentWidth(sizePx.toFloat())
            svg.setDocumentHeight(sizePx.toFloat())
            svg.renderToCanvas(canvas)
            bmp
        }.getOrNull()
    }

    private fun drawScaled(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, w: Float, h: Float) {
        val s = SCALE
        val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        val dst = RectF(x * s, y * s, (x + w) * s, (y + h) * s)
        canvas.drawBitmap(bitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    // ── Teks (anchor ala text-to-svg: left/right/center + top/middle) ──

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        typeface: Typeface,
        hAnchor: HAnchor,
        vAnchor: VAnchor,
    ) {
        if (text.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size * SCALE
            color = Color.WHITE
            textAlign = when (hAnchor) {
                HAnchor.LEFT -> Paint.Align.LEFT
                HAnchor.RIGHT -> Paint.Align.RIGHT
                HAnchor.CENTER -> Paint.Align.CENTER
            }
        }
        val fm = paint.fontMetrics
        val baseline = when (vAnchor) {
            // 'top': em-box top at y -> baseline = y - fm.ascent (fm.ascent is negative).
            VAnchor.TOP -> y * SCALE - fm.ascent
            // 'middle': em-box middle at y -> baseline = y - (ascent + descent) / 2
            // (text-to-svg counterpart: baseline = y + (ascender + descender)/2, ascender>0, descender<0).
            VAnchor.MIDDLE -> y * SCALE - (fm.ascent + fm.descent) / 2f
        }
        canvas.drawText(text, x * SCALE, baseline, paint)
    }

    // ── Skills (osu!skills radar — counterpart of render.js's skills part) ──

    /**
     * Draw the skills radar + rank tags. Coordinates & proportions match render.js:
     * origin (118,268), 6 axes from -120° (hexagon), radius 45, rings at
     * 11.25/22.5/33.75/45, tags at the top right (530,96) shifting left.
     */
    private fun drawSkills(canvas: Canvas, skills: SkillsData?, reg: Typeface, h1: Int, f1: Int, b5: Int) {
        if (skills == null) {
            drawText(canvas, "No skills data", 118f, 265f, 15f, reg, HAnchor.CENTER, VAnchor.MIDDLE)
            return
        }
        val s = SCALE
        val cx = 118f * s
        val cy = 268f * s
        val r = 45f * s

        // Ring hexagon (stroke f1, opacity per template).
        val ringRanges = listOf(11.25f to 0.25f, 22.5f to 0.4f, 33.75f to 0.25f, 45f to 0.8f)
        for ((radius, alpha) in ringRanges) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f * s
                color = f1
                this.alpha = (alpha * 255).roundToInt()
            }
            canvas.drawPath(hexagonPath(cx, cy, radius * s), paint)
        }

        // Axis lines origin → vertex (white stroke 1/3, dash 2 3).
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * s
            color = Color.WHITE
            this.alpha = (0.33f * 255).roundToInt()
            pathEffect = DashPathEffect(floatArrayOf(2f * s, 3f * s), 0f)
        }
        for (i in 0..5) {
            val (vx, vy) = vertex(cx, cy, 43f * s, i)
            canvas.drawLine(cx, cy, vx, vy, axisPaint)
        }

        // Skills polygon (fill h1 40%, stroke h1) — radius per axis = percent.
        val polygon = Path()
        for (i in 0..6) {
            val percent = skills.radarSkills[i % 6].percent.coerceIn(0f, 100f) / 100f
            val (vx, vy) = vertex(cx, cy, r * percent, i)
            if (i == 0) polygon.moveTo(vx, vy) else polygon.lineTo(vx, vy)
        }
        polygon.close()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = h1
            this.alpha = (0.4f * 255).roundToInt()
        }
        canvas.drawPath(polygon, fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * s
            color = h1
        }
        canvas.drawPath(polygon, stroke)

        // Rank tags at the top right (render.js counterpart: posX 530, posY 96).
        drawSkillTags(canvas, skills.tags, reg, b5)
    }

    /** One skill tag: pill bg (hsl-b5 40%) + per-rank colored border + text. */
    private fun drawSkillTags(canvas: Canvas, tags: List<String>, reg: Typeface, b5: Int) {
        if (tags.isEmpty()) return
        val s = SCALE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = reg
            textSize = 10f * s
            color = Color.WHITE
            textAlign = Paint.Align.RIGHT
        }
        val fm = paint.fontMetrics
        // render.js: posX starts at 530, posY 96; the pill is sized from the text width.
        var posX = 530f
        val posY = 96f
        val widthPx = paint.measureText(tags[0])
        val heightPx = fm.descent - fm.ascent
        for (tag in tags) {
            val width = if (tag == tags[0]) widthPx else paint.measureText(tag)
            val cx = posX * s
            val cy = posY * s
            val baseline = posY * s - (fm.ascent + fm.descent) / 2f
            // Pill bg: [posX-width-5 .. posX+5] x [posY-h/2-5 .. posY+h/2+5]
            canvas.drawRoundRect(
                RectF(cx - width - 5f * s, cy - heightPx / 2f - 5f * s, cx + 5f * s, cy + heightPx / 2f + 5f * s),
                5f * s, 5f * s,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = b5
                    this.alpha = (0.4f * 255).roundToInt()
                },
            )
            // Border: [posX-width-3 .. posX+3]
            canvas.drawRoundRect(
                RectF(cx - width - 3f * s, cy - heightPx / 2f - 3f * s, cx + 3f * s, cy + heightPx / 2f + 3f * s),
                4f * s, 4f * s,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.2f * s
                    color = skillRankColor(tag)
                },
            )
            val textPaint = Paint(paint)
            canvas.drawText(tag, cx, baseline, textPaint)
            posX -= (width / s) + 12f
        }
    }

    /** Tag border color per rank name (getColorBySkillRankName counterpart). */
    private fun skillRankColor(name: String): Int {
        val colors = mapOf(
            "Hardy" to "#464ac1", "Tenacious" to "#ff0066", "Swift" to "#fcc013",
            "Perceptive" to "#24d8fe", "Volcanic" to "#ef525b", "Furious" to "#f8095c",
            "Sturdy" to "#1bad58", "Adventurous" to "#79de4f", "Adamant" to "#4dceff",
            "Spirited" to "#d0dc05", "Berserk" to "#b00106", "Fearless" to "#a8157d",
            "Frantic" to "#468c00", "Volatile" to "#dc4ad2", "Versatile" to "#e9ce14",
            "Ambitious" to "#46d1a7", "Sage" to "#1baec0", "Sharpshooter" to "#9b1400",
            "Psychic" to "#66d9b7", "Pirate" to "#d90606", "Seer" to "#1368bd",
            "Sniper" to "#519216", "Daredevil" to "#c01900",
        )
        return runCatching { Color.parseColor(colors[name] ?: "#ffffff") }.getOrDefault(Color.WHITE)
    }

    /** The i-th hexagon point around the center (angle -120° + i*60°). */
    private fun vertex(cx: Float, cy: Float, radius: Float, i: Int): Pair<Float, Float> {
        val angle = (-120 + i * 60) / 180f * PI.toFloat()
        return Pair(cx + cos(angle) * radius, cy + sin(angle) * radius)
    }

    /** Closed hexagon path (radius r, 6 points). */
    private fun hexagonPath(cx: Float, cy: Float, r: Float): Path {
        val path = Path()
        for (i in 0..6) {
            val (x, y) = vertex(cx, cy, r, i)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** Removes one SVG group (with its contents) from the template string. */
    private fun removeSvgGroup(svg: String, groupId: String): String {
        val start = svg.indexOf("<g id=\"$groupId\">")
        if (start < 0) return svg
        var i = start + ("<g id=\"$groupId\">").length
        var depth = 0
        while (i < svg.length) {
            val open = svg.indexOf("<g", i)
            val close = svg.indexOf("</g>", i)
            if (close < 0) return svg
            if (open >= 0 && open < close) {
                depth++
                i = open + 2
            } else {
                if (depth == 0) {
                    return svg.substring(0, start) + svg.substring(close + 4)
                }
                depth--
                i = close + 4
            }
        }
        return svg
    }

    private fun playmodeName(mode: String): String = WidgetMode.displayName(mode)

    /** Source rect so the bitmap center-crops to fill the target w x h (in bitmap px). */
    private fun centerCropRect(bmp: Bitmap, w: Float, h: Float): android.graphics.Rect {
        val scale = maxOf(w / bmp.width, h / bmp.height)
        val sw = w / scale
        val sh = h / scale
        val sx = ((bmp.width - sw) / 2f).roundToInt()
        val sy = ((bmp.height - sh) / 2f).roundToInt()
        return android.graphics.Rect(sx, sy, sx + sw.roundToInt(), sy + sh.roundToInt())
    }

    private val EMPTY: Bitmap by lazy {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
