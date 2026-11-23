package com.jovan.vrijeme

import android.content.Context
import android.graphics.*
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/** Cijeli ekran se crta ovdje: pozadina po dobu dana, veliki sat, datum i kartice koje se smjenjuju. */
class Prikaz(ctx: Context) : View(ctx) {

    var vrijeme: Vrijeme? = null
    var grad = "Berane"
    var lat = 42.8433
    var lon = 19.8728
    var sekunde = true

    private val start = System.currentTimeMillis()
    private var karticaBaza = start
    private var karticaPomak = 0

    private val tfThin = Typeface.create("sans-serif-thin", Typeface.NORMAL)
    private val tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val tfReg = Typeface.create("sans-serif", Typeface.NORMAL)
    private val tfMed = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val tp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        fontFeatureSettings = "tnum"
    }
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private val BIJELA = 0xFFFFFFFF.toInt()
    private val TOPLO = 0xFFFFB36B.toInt()
    private val HLADNO = 0xFF6EC6FF.toInt()

    // paleta (popunjava se svaki frejm po dobu dana)
    private var cGore = 0; private var cDole = 0; private var cSjaj1 = 0; private var cSjaj2 = 0
    private var akcenat = 0; private var noc = 0f

    // zvijezde za noćnu pozadinu
    private val zvijezde = Random(7).let { r -> Array(70) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() * 6f) } }

    // ključni kadrovi palete: sat, gore, dole, sjaj1, sjaj2, akcenat
    private val kadrovi = arrayOf(
        longArrayOf(0, 0xFF060A18, 0xFF101634, 0xFF2A1F6B, 0xFF0E3B5C, 0xFF9DB8FF),
        longArrayOf(5, 0xFF0A0C22, 0xFF1C1A40, 0xFF3A2A78, 0xFF153E66, 0xFFA9B4FF),
        longArrayOf(7, 0xFF1B1838, 0xFF4E3358, 0xFFE07A5F, 0xFF7A4C9A, 0xFFFFB38A),
        longArrayOf(10, 0xFF0D2846, 0xFF1F5E82, 0xFF4FB3D9, 0xFF2D6FB0, 0xFF9EE6FF),
        longArrayOf(14, 0xFF0B3048, 0xFF176B7C, 0xFF52CDBE, 0xFF3488C8, 0xFFA8FFF0),
        longArrayOf(17, 0xFF261C46, 0xFF7E3F58, 0xFFF08A50, 0xFFB0457A, 0xFFFFC48A),
        longArrayOf(20, 0xFF100D2A, 0xFF261C52, 0xFF5E3898, 0xFF26457E, 0xFFC6A8FF),
        longArrayOf(24, 0xFF060A18, 0xFF101634, 0xFF2A1F6B, 0xFF0E3B5C, 0xFF9DB8FF)
    )

    fun sljedecaKartica() {
        karticaPomak++
        karticaBaza = System.currentTimeMillis()
        invalidate()
    }

    // ───────────────────────── pomoćne ─────────────────────────

    private fun sA(c: Int, a: Float): Int = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private fun mijesaj(a: Int, b: Int, t: Float): Int {
        fun k(x: Int, s: Int) = (x shr s) and 0xFF
        fun l(s: Int) = (k(a, s) + (k(b, s) - k(a, s)) * t).roundToInt()
        return (0xFF shl 24) or (l(16) shl 16) or (l(8) shl 8) or l(0)
    }

    private fun tekst(
        c: Canvas, s: String, x: Float, y: Float, vel: Float, tf: Typeface, boja: Int,
        poravnanje: Paint.Align = Paint.Align.LEFT, razmak: Float = 0f
    ): Float {
        tp.typeface = tf; tp.textSize = vel; tp.color = boja; tp.textAlign = poravnanje; tp.letterSpacing = razmak
        c.drawText(s, x, y, tp)
        return tp.measureText(s)
    }

    private fun sirina(s: String, vel: Float, tf: Typeface, razmak: Float = 0f): Float {
        tp.typeface = tf; tp.textSize = vel; tp.letterSpacing = razmak
        return tp.measureText(s)
    }

    private fun dvije(n: Int) = if (n < 10) "0$n" else "$n"

    private fun hhmm(satiDec: Double): String {
        var m = (satiDec * 60).roundToInt()
        m = ((m % 1440) + 1440) % 1440
        return "${dvije(m / 60)}:${dvije(m % 60)}"
    }

    private fun stepen(t: Double): String {
        val r = t.roundToInt()
        return (if (r == 0) "0" else r.toString()) + "°"
    }

    // ───────────────────────── crtanje ─────────────────────────

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val sad = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val satF = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f + cal.get(Calendar.SECOND) / 3600f
        paleta(satF)
        val tSek = (sad - start) / 1000f

        pozadina(c, w, h, tSek)

        val u = min(w, h) / 100f
        // blagi pomak sadržaja protiv „urezivanja" ekrana
        val ox = (sin(sad / 1000.0 / 113.0) * 1.1 * u).toFloat()
        val oy = (cos(sad / 1000.0 / 149.0) * 0.8 * u).toFloat()
        c.save(); c.translate(ox, oy)

        val polozeno = w > h
        val m = 6f * u
        val lijevo: RectF
        val kartica: RectF
        if (polozeno) {
            lijevo = RectF(m, m, w * 0.575f, h - m)
            kartica = RectF(w * 0.61f, m, w - m, h - m - 4.5f * u)
        } else {
            lijevo = RectF(m, m, w - m, h * 0.47f)
            kartica = RectF(m, h * 0.49f, w - m, h - m - 4.5f * u)
        }
        satBlok(c, lijevo, cal, sad, u)
        kartice(c, kartica, cal, sad, u)
        c.restore()

        postInvalidateDelayed(40)
    }

    private fun paleta(sat: Float) {
        var i = 0
        while (i < kadrovi.size - 2 && kadrovi[i + 1][0] <= sat) i++
        val a = kadrovi[i]; val b = kadrovi[i + 1]
        var t = ((sat - a[0]) / (b[0] - a[0]).toFloat()).coerceIn(0f, 1f)
        t = t * t * (3 - 2 * t)
        cGore = mijesaj(a[1].toInt(), b[1].toInt(), t)
        cDole = mijesaj(a[2].toInt(), b[2].toInt(), t)
        cSjaj1 = mijesaj(a[3].toInt(), b[3].toInt(), t)
        cSjaj2 = mijesaj(a[4].toInt(), b[4].toInt(), t)
        akcenat = mijesaj(a[5].toInt(), b[5].toInt(), t)
        // koliko je „noć" (0 danju, 1 noću) — za zvijezde
        noc = when {
            sat < 5f -> 1f
            sat < 7f -> 1f - (sat - 5f) / 2f
            sat < 18.5f -> 0f
            sat < 20.5f -> (sat - 18.5f) / 2f
            else -> 1f
        }
    }

    private fun pozadina(c: Canvas, w: Float, h: Float, t: Float) {
        fp.color = BIJELA; fp.shader = LinearGradient(0f, 0f, w * 0.35f, h, cGore, cDole, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fp)
        val r = max(w, h)
        sjaj(c, w * (0.22f + 0.10f * sin(t / 37f)), h * (0.28f + 0.12f * cos(t / 43f)), r * 0.62f, cSjaj1, 0.42f)
        sjaj(c, w * (0.82f + 0.08f * cos(t / 41f)), h * (0.78f + 0.10f * sin(t / 29f)), r * 0.55f, cSjaj2, 0.45f)
        sjaj(c, w * (0.55f + 0.20f * sin(t / 71f)), h * (1.05f), r * 0.45f, cSjaj1, 0.18f)
        fp.shader = null

        if (noc > 0.01f) {
            fp.color = BIJELA
            for (z in zvijezde) {
                val tw = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * (0.6f + z[2]) + z[3]))
                fp.alpha = (noc * tw * (90 + 120 * z[2])).toInt().coerceIn(0, 255)
                c.drawCircle(z[0] * w, z[1] * h * 0.9f, 0.6f + z[2] * 1.6f, fp)
            }
            fp.alpha = 255
        }
        // vinjeta
        fp.color = BIJELA; fp.shader = RadialGradient(
            w / 2, h / 2, max(w, h) * 0.75f,
            intArrayOf(0x00000000, 0x00000000, 0x66000000), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, fp)
        fp.shader = null
    }

    private fun sjaj(c: Canvas, x: Float, y: Float, r: Float, boja: Int, a: Float) {
        fp.color = BIJELA; fp.shader = RadialGradient(x, y, r, intArrayOf(sA(boja, a), sA(boja, a * 0.4f), sA(boja, 0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, fp)
    }

    // ───────────────────────── veliki sat ─────────────────────────

    private fun satBlok(c: Canvas, r: RectF, cal: Calendar, sad: Long, u: Float) {
        val sat = cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        val sek = cal.get(Calendar.SECOND)
        val ms = (sad % 1000).toInt()

        val ref = 100f
        val wSat = sirina("88:88", ref, tfThin)
        val wSek = if (sekunde) sirina("88", ref * 0.24f, tfLight) + ref * 0.07f else 0f
        var vel = r.width() * 0.97f / ((wSat + wSek) / ref)
        vel = min(vel, r.height() * 0.52f)
        val capH = vel * 0.715f

        val etiketa = 2.5f * u
        val danH = 3.3f * u
        val datumH = 6f * u
        val ukupno = etiketa + 5.5f * u + capH + 7.5f * u + danH + 3.2f * u + datumH + 5.5f * u + 0.5f * u + 6f * u + 2.4f * u
        var y = r.top + (r.height() - ukupno) / 2f
        val x0 = r.left + 0.4f * u

        // grad + trenutna temperatura
        y += etiketa
        var x = x0
        x += tekst(c, grad.uppercase(), x, y, etiketa, tfMed, sA(BIJELA, 0.72f), razmak = 0.3f)
        vrijeme?.let { v ->
            val tr = v.trenutnoU(sad)
            x += 2.2f * u
            fp.color = sA(akcenat, 0.9f); c.drawCircle(x, y - etiketa * 0.36f, 0.45f * u, fp)
            x += 2.2f * u
            tekst(c, "${stepen(tr.temp)}  ${Tekst.opis(tr.kod).uppercase()}", x, y, etiketa, tfMed, sA(BIJELA, 0.72f), razmak = 0.22f)
        }

        // sat
        y += 5.5f * u + capH
        val hh = dvije(sat); val mm = dvije(min)
        var tx = x0 - vel * 0.035f
        tx += tekst(c, hh, tx, y, vel, tfThin, BIJELA)
        val puls = 0.45f + 0.55f * (0.5f + 0.5f * cos(ms / 1000f * 2f * PI.toFloat()))
        tx += tekst(c, ":", tx, y - vel * 0.04f, vel, tfThin, sA(BIJELA, puls))
        tx += tekst(c, mm, tx, y, vel, tfThin, BIJELA)
        if (sekunde) {
            tekst(c, dvije(sek), tx + vel * 0.06f, y, vel * 0.24f, tfLight, akcenat)
        }

        // dan i datum
        y += 7.5f * u + danH
        tekst(c, Tekst.daniPuni[cal.get(Calendar.DAY_OF_WEEK) - 1].uppercase(), x0, y, danH, tfMed, akcenat, razmak = 0.32f)
        y += 3.2f * u + datumH
        val datum = "${cal.get(Calendar.DAY_OF_MONTH)}. ${Tekst.mjeseci[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}."
        tekst(c, datum, x0 - 0.2f * u, y, datumH, tfLight, BIJELA)

        // traka sekundi
        y += 5.5f * u
        val dužina = min(r.width() * 0.82f, sirina("88:88", vel, tfThin) * 1.05f)
        val prog = (sek * 1000 + ms) / 60000f
        sp.strokeWidth = 0.45f * u
        sp.shader = null
        sp.color = sA(BIJELA, 0.13f)
        c.drawLine(x0, y, x0 + dužina, y, sp)
        sp.color = BIJELA; sp.shader = LinearGradient(x0, y, x0 + dužina, y, sA(akcenat, 0.25f), akcenat, Shader.TileMode.CLAMP)
        c.drawLine(x0, y, x0 + dužina * prog, y, sp)
        sp.shader = null
        val px = x0 + dužina * prog
        fp.color = BIJELA; fp.shader = RadialGradient(px, y, 2.4f * u, intArrayOf(sA(akcenat, 0.7f), sA(akcenat, 0f)), null, Shader.TileMode.CLAMP)
        c.drawCircle(px, y, 2.4f * u, fp); fp.shader = null
        fp.color = BIJELA; c.drawCircle(px, y, 0.55f * u, fp)

        // dan u godini / sedmica
        y += 6f * u + 2.4f * u
        val dg = cal.get(Calendar.DAY_OF_YEAR)
        val ukDana = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        val sed = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; minimalDaysInFirstWeek = 4; timeInMillis = sad }
            .get(Calendar.WEEK_OF_YEAR)
        tekst(c, "SEDMICA $sed   ·   $dg. DAN U GODINI   ·   JOŠ ${Tekst.dana(ukDana - dg).uppercase()}", x0, y, 2.4f * u, tfReg, sA(BIJELA, 0.5f), razmak = 0.18f)
    }

    // ───────────────────────── kartice ─────────────────────────

    private fun kartice(c: Canvas, r: RectF, cal: Calendar, sad: Long, u: Float) {
        val lista = ArrayList<Int>()
        if (vrijeme != null) { lista.add(0); lista.add(1); lista.add(2) }
        lista.add(3); lista.add(4)

        val period = 12_000L
        val prelaz = 900L
        val el = sad - karticaBaza
        val n = lista.size
        val idx = (((el / period) + karticaPomak) % n).toInt()
        val t = el % period

        // staklena ploča
        val rad = 3.5f * u
        fp.color = sA(BIJELA, 0.065f)
        c.drawRoundRect(r, rad, rad, fp)
        sp.strokeWidth = max(1f, 0.12f * u); sp.color = sA(BIJELA, 0.13f)
        c.drawRoundRect(r, rad, rad, sp)

        c.save(); c.clipRect(r)
        if (t > period - prelaz) {
            var f = (t - (period - prelaz)) / prelaz.toFloat()
            f = f * f * (3 - 2 * f)
            kartica(c, lista[idx], r, cal, sad, 1f - f, -f * 2.5f * u)
            kartica(c, lista[(idx + 1) % n], r, cal, sad, f, (1f - f) * 2.5f * u)
        } else kartica(c, lista[idx], r, cal, sad, 1f, 0f)
        c.restore()

        // tačkice
        val aktivna = if (t > period - prelaz / 2) (idx + 1) % n else idx
        val dy = r.bottom + 3f * u
        val korak = 2.6f * u
        var dx = r.centerX() - (n - 1) * korak / 2f
        for (i in 0 until n) {
            if (i == aktivna) {
                fp.color = sA(BIJELA, 0.85f)
                c.drawRoundRect(dx - 1.1f * u, dy - 0.4f * u, dx + 1.1f * u, dy + 0.4f * u, 0.4f * u, 0.4f * u, fp)
            } else {
                fp.color = sA(BIJELA, 0.28f)
                c.drawCircle(dx, dy, 0.4f * u, fp)
            }
            dx += korak
        }
    }

    private fun kartica(c: Canvas, id: Int, r: RectF, cal: Calendar, sad: Long, alfa: Float, dy: Float) {
        if (alfa <= 0.01f) return
        val k = min(r.width() / 100f, r.height() / 130f)
        val bx = r.centerX() - 50f * k
        val by = r.centerY() - 65f * k
        c.saveLayerAlpha(r, (alfa * 255).toInt())
        c.translate(bx, by + dy)
        when (id) {
            0 -> kSada(c, k, sad)
            1 -> kSati(c, k, sad)
            2 -> kDani(c, k, sad)
            3 -> kSunce(c, k, cal, sad)
            4 -> kKalendar(c, k, cal)
        }
        c.restore()
    }

    private fun naslov(c: Canvas, k: Float, s: String, desno: String = "") {
        tekst(c, s, 7f * k, 11f * k, 3.2f * k, tfMed, akcenat, razmak = 0.28f)
        if (desno.isNotEmpty()) tekst(c, desno, 93f * k, 11f * k, 3.0f * k, tfReg, sA(BIJELA, 0.45f), Paint.Align.RIGHT)
    }

    private fun linija(c: Canvas, k: Float, y: Float) {
        sp.strokeWidth = max(1f, 0.15f * k); sp.color = sA(BIJELA, 0.10f); sp.shader = null
        c.drawLine(7f * k, y, 93f * k, y, sp)
    }

    // 0 — trenutno
    private fun kSada(c: Canvas, k: Float, sad: Long) {
        val v = vrijeme ?: return
        val tr = v.trenutnoU(sad)
        naslov(c, k, "TRENUTNO", grad)
        ikona(c, tr.kod, tr.dan, 25f * k, 38f * k, 34f * k)
        tekst(c, stepen(tr.temp), 47f * k, 50f * k, 27f * k, tfThin, BIJELA)
        tekst(c, Tekst.opis(tr.kod), 7f * k, 66f * k, 5.6f * k, tfLight, BIJELA)
        v.daniOdDanas(sad).firstOrNull()?.let { d ->
            var x = 7f * k
            x += tekst(c, "max ", x, 74f * k, 3.6f * k, tfReg, sA(BIJELA, 0.5f))
            x += tekst(c, stepen(d.max), x, 74f * k, 3.6f * k, tfMed, TOPLO)
            x += tekst(c, "    min ", x, 74f * k, 3.6f * k, tfReg, sA(BIJELA, 0.5f))
            tekst(c, stepen(d.min), x, 74f * k, 3.6f * k, tfMed, HLADNO)
        }
        linija(c, k, 81f * k)
        polje(c, k, 7f * k, 91f * k, "OSJEĆAJ", stepen(tr.osjecaj))
        polje(c, k, 53f * k, 91f * k, "VJETAR", "${tr.vjetar.roundToInt()} km/h ${Tekst.strana(tr.smjerVjetra)}")
        polje(c, k, 7f * k, 110f * k, "VLAŽNOST", "${tr.vlaznost} %")
        polje(c, k, 53f * k, 110f * k, "PRITISAK", "${tr.pritisak.roundToInt()} hPa")
        val f = SimpleDateFormat("HH:mm", Locale.US).format(Date(v.preuzeto))
        tekst(c, "ažurirano u $f", 7f * k, 126f * k, 2.6f * k, tfReg, sA(BIJELA, 0.35f))
    }

    private fun polje(c: Canvas, k: Float, x: Float, y: Float, lab: String, vr: String) {
        tekst(c, lab, x, y, 2.6f * k, tfMed, sA(BIJELA, 0.5f), razmak = 0.2f)
        tekst(c, vr, x, y + 7.5f * k, 5.2f * k, tfLight, BIJELA)
    }

    // 1 — narednih 12 sati
    private fun kSati(c: Canvas, k: Float, sad: Long) {
        val v = vrijeme ?: return
        naslov(c, k, "NAREDNIH 12 SATI", grad)
        val i0 = v.indeksSata(sad)
        if (i0 < 0) return
        val tacke = (0..6).map { i0 + it * 2 }.filter { it < v.sati.size }.map { v.sati[it] }
        if (tacke.size < 2) return
        val n = tacke.size
        val xs = FloatArray(n) { 12f * k + it * (76f * k / (n - 1)) }
        val mn = tacke.minOf { it.temp }; val mx = tacke.maxOf { it.temp }
        val opseg = max(mx - mn, 3.0)
        val sr = (mx + mn) / 2
        val yGore = 58f * k; val yDole = 88f * k
        val ys = FloatArray(n) { (yDole - ((tacke[it].temp - (sr - opseg / 2)) / opseg * (yDole - yGore))).toFloat() }
        val fs = SimpleDateFormat("HH", Locale.US)

        for (i in 0 until n) {
            val s = tacke[i]
            tekst(c, if (i == 0) "sad" else fs.format(Date(s.vrijeme)) + "h", xs[i], 23f * k, 3.2f * k,
                if (i == 0) tfMed else tfReg, sA(BIJELA, if (i == 0) 0.95f else 0.6f), Paint.Align.CENTER)
            ikona(c, s.kod, s.dan, xs[i], 34f * k, 9.5f * k)
        }
        // kriva
        val put = Path(); put.moveTo(xs[0], ys[0])
        for (i in 1 until n) {
            val cx = (xs[i - 1] + xs[i]) / 2
            put.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
        }
        val pun = Path(put); pun.lineTo(xs[n - 1], 96f * k); pun.lineTo(xs[0], 96f * k); pun.close()
        fp.color = BIJELA; fp.shader = LinearGradient(0f, yGore, 0f, 96f * k, sA(akcenat, 0.32f), sA(akcenat, 0f), Shader.TileMode.CLAMP)
        c.drawPath(pun, fp); fp.shader = null
        sp.strokeWidth = 0.6f * k; sp.color = akcenat
        c.drawPath(put, sp)
        for (i in 0 until n) {
            fp.color = BIJELA; c.drawCircle(xs[i], ys[i], 1.1f * k, fp)
            tekst(c, stepen(tacke[i].temp), xs[i], ys[i] - 3.2f * k, 4f * k, tfLight, BIJELA, Paint.Align.CENTER)
            val p = tacke[i].padavine
            tekst(c, "$p%", xs[i], 105f * k, 2.9f * k, tfReg, if (p >= 20) HLADNO else sA(BIJELA, 0.35f), Paint.Align.CENTER)
        }
        // sažetak padavina
        val sve = v.sati.subList(i0, min(v.sati.size, i0 + 13))
        val najv = sve.maxByOrNull { it.padavine }
        val opis = if (najv == null || najv.padavine < 20) "Bez značajnih padavina u narednih 12 sati"
        else "Padavine najvjerovatnije oko ${fs.format(Date(najv.vrijeme))}h (${najv.padavine}%)"
        linija(c, k, 112f * k)
        tekst(c, opis, 7f * k, 121f * k, 3.1f * k, tfReg, sA(BIJELA, 0.6f))
    }

    // 2 — narednih dana
    private fun kDani(c: Canvas, k: Float, sad: Long) {
        val v = vrijeme ?: return
        naslov(c, k, "NAREDNIH DANA", grad)
        val dani = v.daniOdDanas(sad).take(5)
        if (dani.isEmpty()) return
        val sMin = dani.minOf { it.min }; val sMax = dani.maxOf { it.max }
        val ops = max(sMax - sMin, 1.0)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        for ((i, d) in dani.withIndex()) {
            val yb = 30f * k + i * 21f * k
            val ime = when (i) {
                0 -> "Danas"; 1 -> "Sjutra"
                else -> { cal.time = fmt.parse(d.datum) ?: Date(); Tekst.cap(Tekst.daniPuni[cal.get(Calendar.DAY_OF_WEEK) - 1]) }
            }
            tekst(c, ime, 7f * k, yb, 4.4f * k, if (i == 0) tfMed else tfLight, BIJELA)
            ikona(c, d.kod, true, 40f * k, yb - 1.6f * k, 10.5f * k)
            if (d.padavine >= 20) tekst(c, "${d.padavine}%", 47.5f * k, yb - 0.3f * k, 2.9f * k, tfMed, HLADNO)
            tekst(c, stepen(d.min), 64.5f * k, yb, 4.2f * k, tfLight, sA(BIJELA, 0.6f), Paint.Align.RIGHT)
            tekst(c, stepen(d.max), 96f * k, yb, 4.2f * k, tfReg, BIJELA, Paint.Align.RIGHT)
            // traka opsega
            val bx0 = 67.5f * k; val bx1 = 86f * k; val byy = yb - 1.5f * k
            sp.strokeWidth = 1.2f * k; sp.shader = null; sp.color = sA(BIJELA, 0.12f)
            c.drawLine(bx0, byy, bx1, byy, sp)
            val a0 = bx0 + ((d.min - sMin) / ops * (bx1 - bx0)).toFloat()
            val a1 = bx0 + ((d.max - sMin) / ops * (bx1 - bx0)).toFloat()
            sp.color = BIJELA; sp.shader = LinearGradient(bx0, 0f, bx1, 0f, HLADNO, TOPLO, Shader.TileMode.CLAMP)
            c.drawLine(a0, byy, max(a1, a0 + 0.1f), byy, sp)
            sp.shader = null
            if (i < dani.size - 1) linija(c, k, yb + 8.5f * k)
        }
    }

    // 3 — sunce i mjesec
    private fun kSunce(c: Canvas, k: Float, cal: Calendar, sad: Long) {
        naslov(c, k, "SUNCE I MJESEC")
        val su = Astro.sunce(cal, lat, lon)
        val cx = 50f * k; val cy = 56f * k; val R = 33f * k
        val luk = RectF(cx - R, cy - R, cx + R, cy + R)
        sp.strokeWidth = 0.45f * k; sp.color = sA(BIJELA, 0.28f); sp.shader = null
        sp.pathEffect = DashPathEffect(floatArrayOf(1.2f * k, 1.6f * k), 0f)
        c.drawArc(luk, 180f, 180f, false, sp)
        sp.pathEffect = null
        sp.strokeWidth = max(1f, 0.15f * k); sp.color = sA(BIJELA, 0.22f)
        c.drawLine(8f * k, cy, 92f * k, cy, sp)
        if (su != null) {
            val (izl, zal) = su
            val satF = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0
            val f = ((satF - izl) / (zal - izl)).toFloat()
            val danju = f in 0f..1f
            val fc = f.coerceIn(0f, 1f)
            if (danju) {
                sp.strokeWidth = 0.7f * k; sp.color = TOPLO
                c.drawArc(luk, 180f, 180f * fc, false, sp)
            }
            val ug = Math.toRadians(180.0 + 180.0 * fc)
            val sx = cx + R * cos(ug).toFloat(); val sy = cy + R * sin(ug).toFloat()
            if (danju) {
                fp.color = BIJELA; fp.shader = RadialGradient(sx, sy, 7f * k, intArrayOf(0x99FFC95C.toInt(), 0x00FFC95C), null, Shader.TileMode.CLAMP)
                c.drawCircle(sx, sy, 7f * k, fp); fp.shader = null
                fp.color = 0xFFFFD77A.toInt(); c.drawCircle(sx, sy, 2.2f * k, fp)
            } else {
                tekst(c, if (f < 0) "Sunce još nije izašlo" else "Sunce je zašlo", cx, cy - 12f * k, 3.4f * k, tfLight, sA(BIJELA, 0.6f), Paint.Align.CENTER)
            }
            tekst(c, "IZLAZAK", 7f * k, 66f * k, 2.6f * k, tfMed, sA(BIJELA, 0.5f), razmak = 0.2f)
            tekst(c, hhmm(izl), 7f * k, 73.5f * k, 5.4f * k, tfLight, BIJELA)
            tekst(c, "ZALAZAK", 93f * k, 66f * k, 2.6f * k, tfMed, sA(BIJELA, 0.5f), Paint.Align.RIGHT, 0.2f)
            tekst(c, hhmm(zal), 93f * k, 73.5f * k, 5.4f * k, tfLight, BIJELA, Paint.Align.RIGHT)
            val duz = ((zal - izl) * 60).roundToInt()
            tekst(c, "dan traje ${duz / 60} h ${duz % 60} min", cx, 73.5f * k, 3f * k, tfReg, sA(BIJELA, 0.55f), Paint.Align.CENTER)
        }
        linija(c, k, 84f * k)
        val st = Astro.starostMjeseca(sad)
        val osv = Astro.osvijetljenost(st)
        mjesec(c, 19f * k, 105f * k, 10.5f * k, st, osv)
        tekst(c, Astro.imeMijene(st), 37f * k, 101f * k, 5.2f * k, tfLight, BIJELA)
        tekst(c, "osvijetljeno ${(osv * 100).roundToInt()} %", 37f * k, 108.5f * k, 3.2f * k, tfReg, sA(BIJELA, 0.6f))
        val sin = 29.530588853
        val sljed = if (st < sin / 2) {
            val d = (sin / 2 - st).roundToInt(); if (d == 0) "pun mjesec danas" else "pun mjesec za ${Tekst.dana(d)}"
        } else {
            val d = (sin - st).roundToInt(); if (d == 0) "mlađ danas" else "mlađ za ${Tekst.dana(d)}"
        }
        tekst(c, sljed, 37f * k, 115f * k, 3.2f * k, tfReg, sA(BIJELA, 0.45f))
    }

    private fun mjesec(c: Canvas, x: Float, y: Float, r: Float, starost: Double, osv: Double) {
        val svijetla = 0xFFF3EAD3.toInt()
        val tamna = 0xFF262B40.toInt()
        fp.color = BIJELA; fp.shader = RadialGradient(x, y, r * 1.8f, intArrayOf(sA(svijetla, (0.25 * osv).toFloat()), sA(svijetla, 0f)), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 1.8f, fp); fp.shader = null
        fp.color = tamna; c.drawCircle(x, y, r, fp)
        val raste = starost < 29.530588853 / 2
        // osvijetljena polovina
        fp.color = svijetla
        val o = RectF(x - r, y - r, x + r, y + r)
        c.drawArc(o, if (raste) -90f else 90f, 180f, true, fp)
        // terminator kao elipsa
        val rx = (r * abs(1 - 2 * osv)).toFloat()
        fp.color = if (osv < 0.5) tamna else svijetla
        c.drawOval(RectF(x - rx, y - r, x + rx, y + r), fp)
        sp.strokeWidth = max(1f, r * 0.03f); sp.color = sA(BIJELA, 0.15f)
        c.drawCircle(x, y, r, sp)
    }

    // 4 — kalendar
    private fun kKalendar(c: Canvas, k: Float, cal: Calendar) {
        val god = cal.get(Calendar.YEAR); val mj = cal.get(Calendar.MONTH); val danas = cal.get(Calendar.DAY_OF_MONTH)
        naslov(c, k, Tekst.mjeseci[mj].uppercase(), "$god")
        val prvi = Calendar.getInstance().apply { clear(); set(god, mj, 1) }
        val pomak = (prvi.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val ukupno = prvi.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cw = 86f * k / 7f
        val slova = arrayOf("P", "U", "S", "Č", "P", "S", "N")
        for (i in 0..6) {
            tekst(c, slova[i], 7f * k + cw * (i + 0.5f), 22f * k, 2.9f * k, tfMed,
                if (i >= 5) sA(akcenat, 0.8f) else sA(BIJELA, 0.45f), Paint.Align.CENTER)
        }
        val praznici = Praznici.zaGodinu(god).filter { it.mjesec == mj }.map { it.dan }.toSet()
        val redH = 10.6f * k
        for (d in 1..ukupno) {
            val p = pomak + d - 1
            val kol = p % 7; val red = p / 7
            val x = 7f * k + cw * (kol + 0.5f)
            val y = 33f * k + red * redH
            if (d == danas) {
                fp.color = akcenat; c.drawCircle(x, y - 1.55f * k, 4.4f * k, fp)
                tekst(c, "$d", x, y, 4f * k, tfMed, 0xFF0B1020.toInt(), Paint.Align.CENTER)
            } else {
                val a = when { d < danas -> 0.35f; kol >= 5 -> 0.75f; else -> 0.9f }
                tekst(c, "$d", x, y, 4f * k, tfLight, if (kol >= 5) sA(akcenat, a) else sA(BIJELA, a), Paint.Align.CENTER)
            }
            if (d in praznici) {
                fp.color = if (d == danas) BIJELA else TOPLO
                c.drawCircle(x, y + 2.3f * k, 0.6f * k, fp)
            }
        }
        linija(c, k, 103f * k)
        val (ime, zaDana) = Praznici.sljedeci(cal)
        if (zaDana >= 0) {
            tekst(c, if (zaDana == 0) "DANAS JE PRAZNIK" else "SLJEDEĆI PRAZNIK", 7f * k, 111f * k, 2.6f * k, tfMed, sA(BIJELA, 0.5f), razmak = 0.2f)
            val kad = when (zaDana) { 0 -> ""; 1 -> "  ·  sjutra"; else -> "  ·  za ${Tekst.dana(zaDana)}" }
            var x = 7f * k
            x += tekst(c, ime, x, 119f * k, 4.4f * k, tfLight, BIJELA)
            tekst(c, kad, x, 119f * k, 3.4f * k, tfReg, sA(akcenat, 0.9f))
        }
    }

    // ───────────────────────── ikone vremena ─────────────────────────

    private fun vrsta(kod: Int): Int = when (kod) {
        0 -> 0                       // vedro
        1, 2 -> 1                    // djelimično
        3 -> 2                       // oblačno
        45, 48 -> 3                  // magla
        in 51..67, in 80..82 -> 4    // kiša
        in 71..77, 85, 86 -> 5       // snijeg
        in 95..99 -> 6               // grmljavina
        else -> 2
    }

    private fun ikona(c: Canvas, kod: Int, dan: Boolean, x: Float, y: Float, s: Float) {
        when (vrsta(kod)) {
            0 -> if (dan) sunce(c, x, y, s) else mjesecIk(c, x, y, s)
            1 -> {
                if (dan) sunce(c, x - s * 0.16f, y - s * 0.15f, s * 0.72f) else mjesecIk(c, x - s * 0.14f, y - s * 0.16f, s * 0.75f)
                oblak(c, x + s * 0.07f, y + s * 0.08f, s * 0.8f, 0xFFF4F6FA.toInt())
            }
            2 -> {
                oblak(c, x + s * 0.14f, y - s * 0.1f, s * 0.62f, 0xFFAEB8CC.toInt())
                oblak(c, x - s * 0.03f, y + s * 0.06f, s * 0.86f, 0xFFF0F2F7.toInt())
            }
            3 -> {
                oblak(c, x, y - s * 0.12f, s * 0.8f, 0xFFD4DAE6.toInt())
                sp.strokeWidth = s * 0.05f; sp.color = 0xCCD4DAE6.toInt(); sp.shader = null
                c.drawLine(x - s * 0.34f, y + s * 0.22f, x + s * 0.26f, y + s * 0.22f, sp)
                c.drawLine(x - s * 0.24f, y + s * 0.33f, x + s * 0.36f, y + s * 0.33f, sp)
            }
            4 -> {
                oblak(c, x, y - s * 0.1f, s * 0.86f, 0xFFE6EAF2.toInt())
                sp.strokeWidth = s * 0.05f; sp.color = 0xFF6CC2FF.toInt(); sp.shader = null
                for (dx in floatArrayOf(-0.18f, 0f, 0.18f)) {
                    c.drawLine(x + s * dx, y + s * 0.2f, x + s * (dx - 0.06f), y + s * 0.36f, sp)
                }
            }
            5 -> {
                oblak(c, x, y - s * 0.1f, s * 0.86f, 0xFFE6EAF2.toInt())
                fp.color = BIJELA
                for ((i, dx) in floatArrayOf(-0.2f, 0f, 0.2f).withIndex()) {
                    c.drawCircle(x + s * dx, y + s * (if (i == 1) 0.34f else 0.26f), s * 0.045f, fp)
                }
            }
            6 -> {
                oblak(c, x, y - s * 0.12f, s * 0.86f, 0xFFB9C1D3.toInt())
                val p = Path()
                p.moveTo(x + s * 0.04f, y + s * 0.08f)
                p.lineTo(x - s * 0.10f, y + s * 0.28f)
                p.lineTo(x - s * 0.00f, y + s * 0.28f)
                p.lineTo(x - s * 0.07f, y + s * 0.46f)
                p.lineTo(x + s * 0.13f, y + s * 0.20f)
                p.lineTo(x + s * 0.03f, y + s * 0.20f)
                p.lineTo(x + s * 0.10f, y + s * 0.08f)
                p.close()
                fp.color = 0xFFFFD35C.toInt(); c.drawPath(p, fp)
            }
        }
    }

    private fun sunce(c: Canvas, x: Float, y: Float, s: Float) {
        fp.color = BIJELA; fp.shader = RadialGradient(x, y, s * 0.5f, intArrayOf(0x66FFC95C, 0x00FFC95C), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, s * 0.5f, fp); fp.shader = null
        sp.strokeWidth = s * 0.05f; sp.color = 0xFFFFC95C.toInt(); sp.shader = null
        for (i in 0 until 8) {
            val a = i * PI / 4
            c.drawLine(x + (s * 0.31f * cos(a)).toFloat(), y + (s * 0.31f * sin(a)).toFloat(),
                x + (s * 0.42f * cos(a)).toFloat(), y + (s * 0.42f * sin(a)).toFloat(), sp)
        }
        fp.color = BIJELA; fp.shader = LinearGradient(x, y - s * 0.22f, x, y + s * 0.22f, 0xFFFFE08A.toInt(), 0xFFFFB547.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, s * 0.22f, fp); fp.shader = null
    }

    private fun mjesecIk(c: Canvas, x: Float, y: Float, s: Float) {
        val a = Path(); a.addCircle(x, y, s * 0.27f, Path.Direction.CW)
        val b = Path(); b.addCircle(x + s * 0.14f, y - s * 0.10f, s * 0.23f, Path.Direction.CW)
        a.op(b, Path.Op.DIFFERENCE)
        fp.color = 0xFFE9E6FF.toInt(); c.drawPath(a, fp)
    }

    private fun oblak(c: Canvas, x: Float, y: Float, s: Float, boja: Int) {
        val p = Path(); p.addCircle(x - s * 0.19f, y + s * 0.05f, s * 0.16f, Path.Direction.CW)
        val q = Path(); q.addCircle(x - s * 0.01f, y - s * 0.07f, s * 0.23f, Path.Direction.CW); p.op(q, Path.Op.UNION)
        val r = Path(); r.addCircle(x + s * 0.21f, y + s * 0.04f, s * 0.17f, Path.Direction.CW); p.op(r, Path.Op.UNION)
        val d = Path(); d.addRoundRect(RectF(x - s * 0.35f, y + s * 0.02f, x + s * 0.38f, y + s * 0.21f), s * 0.095f, s * 0.095f, Path.Direction.CW)
        p.op(d, Path.Op.UNION)
        fp.color = boja; c.drawPath(p, fp)
    }
}
