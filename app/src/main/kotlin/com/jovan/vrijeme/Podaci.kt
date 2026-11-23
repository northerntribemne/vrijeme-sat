package com.jovan.vrijeme

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/** Trenutno stanje vremena (iz "current" ili, kad je keš star, iz satne prognoze). */
data class Trenutno(
    val temp: Double, val osjecaj: Double, val vlaznost: Int, val kod: Int,
    val vjetar: Double, val smjerVjetra: Int, val dan: Boolean, val pritisak: Double
)

data class Sat(val vrijeme: Long, val temp: Double, val kod: Int, val padavine: Int, val dan: Boolean)
data class Dan(val datum: String, val kod: Int, val max: Double, val min: Double, val padavine: Int)

/** Open-Meteo odgovor, parsiran. */
class Vrijeme(val json: String, val preuzeto: Long) {
    val trenutno: Trenutno
    val sati = ArrayList<Sat>()
    val dani = ArrayList<Dan>()
    private val satnoOsjecaj = ArrayList<Double>()
    private val satnoVlaznost = ArrayList<Int>()
    private val satnoVjetar = ArrayList<Double>()
    private val satnoSmjer = ArrayList<Int>()
    private val satnoPritisak = ArrayList<Double>()

    init {
        val o = JSONObject(json)
        val c = o.getJSONObject("current")
        trenutno = Trenutno(
            c.optDouble("temperature_2m"), c.optDouble("apparent_temperature"),
            c.optInt("relative_humidity_2m"), c.optInt("weather_code"),
            c.optDouble("wind_speed_10m"), c.optInt("wind_direction_10m"),
            c.optInt("is_day", 1) == 1, c.optDouble("pressure_msl")
        )
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val h = o.getJSONObject("hourly")
        val t = h.getJSONArray("time")
        for (i in 0 until t.length()) {
            val ms = fmt.parse(t.getString(i))?.time ?: continue
            sati.add(
                Sat(
                    ms, h.getJSONArray("temperature_2m").optDouble(i),
                    h.getJSONArray("weather_code").optInt(i),
                    h.getJSONArray("precipitation_probability").optInt(i),
                    h.getJSONArray("is_day").optInt(i, 1) == 1
                )
            )
            satnoOsjecaj.add(h.getJSONArray("apparent_temperature").optDouble(i))
            satnoVlaznost.add(h.getJSONArray("relative_humidity_2m").optInt(i))
            satnoVjetar.add(h.getJSONArray("wind_speed_10m").optDouble(i))
            satnoSmjer.add(h.getJSONArray("wind_direction_10m").optInt(i))
            satnoPritisak.add(h.getJSONArray("pressure_msl").optDouble(i))
        }
        val d = o.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        for (i in 0 until dt.length()) {
            dani.add(
                Dan(
                    dt.getString(i), d.getJSONArray("weather_code").optInt(i),
                    d.getJSONArray("temperature_2m_max").optDouble(i),
                    d.getJSONArray("temperature_2m_min").optDouble(i),
                    d.getJSONArray("precipitation_probability_max").optInt(i)
                )
            )
        }
    }

    /** Indeks sata u koji pada `sad` (ili -1). */
    fun indeksSata(sad: Long): Int {
        for (i in sati.indices) if (sati[i].vrijeme > sad - 3_600_000L) return i
        return -1
    }

    fun trenutnoU(sad: Long): Trenutno {
        if (sad - preuzeto < 45 * 60_000L) return trenutno
        val i = indeksSata(sad)
        if (i < 0) return trenutno
        val s = sati[i]
        return Trenutno(
            s.temp, satnoOsjecaj[i], satnoVlaznost[i], s.kod,
            satnoVjetar[i], satnoSmjer[i], s.dan, satnoPritisak[i]
        )
    }

    /** Dnevni zapisi od danas pa nadalje. */
    fun daniOdDanas(sad: Long): List<Dan> {
        val danas = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(sad))
        return dani.filter { it.datum >= danas }
    }

    companion object {
        fun url(lat: Double, lon: Double): String =
            "http://api.open-meteo.com/v1/forecast?latitude=${String.format(Locale.US, "%.4f", lat)}" +
                "&longitude=${String.format(Locale.US, "%.4f", lon)}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code," +
                "wind_speed_10m,wind_direction_10m,is_day,pressure_msl" +
                "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code," +
                "precipitation_probability,is_day,wind_speed_10m,wind_direction_10m,pressure_msl" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=7"
    }
}

object Tekst {
    val daniPuni = arrayOf("nedjelja", "ponedjeljak", "utorak", "srijeda", "četvrtak", "petak", "subota")
    val mjeseci = arrayOf(
        "januar", "februar", "mart", "april", "maj", "jun",
        "jul", "avgust", "septembar", "oktobar", "novembar", "decembar"
    )

    fun opis(kod: Int): String = when (kod) {
        0 -> "Vedro"
        1 -> "Pretežno vedro"
        2 -> "Djelimično oblačno"
        3 -> "Oblačno"
        45, 48 -> "Magla"
        51, 53, 55 -> "Rosulja"
        56, 57 -> "Ledena rosulja"
        61 -> "Slaba kiša"
        63 -> "Kiša"
        65 -> "Jaka kiša"
        66, 67 -> "Ledena kiša"
        71 -> "Slab snijeg"
        73 -> "Snijeg"
        75 -> "Jak snijeg"
        77 -> "Susnježica"
        80, 81 -> "Pljuskovi"
        82 -> "Jaki pljuskovi"
        85, 86 -> "Snježni pljuskovi"
        95 -> "Grmljavina"
        96, 99 -> "Grmljavina sa gradom"
        else -> "—"
    }

    fun strana(deg: Int): String {
        val s = arrayOf("S", "SI", "I", "JI", "J", "JZ", "Z", "SZ")
        return s[(((deg % 360) + 360) % 360 + 22) / 45 % 8]
    }

    /** 1 dan, 2 dana, 5 dana, 21 dan … */
    fun dana(n: Int): String = if (n % 10 == 1 && n % 100 != 11) "$n dan" else "$n dana"

    fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    private val cir = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "đ", 'е' to "e", 'ж' to "ž",
        'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj", 'м' to "m", 'н' to "n",
        'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'ћ' to "ć", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "č", 'џ' to "dž", 'ш' to "š"
    )

    /** Ćirilica → latinica (geokoder vraća srpska imena na ćirilici). */
    fun latinica(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            val low = ch.lowercaseChar()
            val l = cir[low]
            if (l == null) sb.append(ch)
            else sb.append(if (ch.isUpperCase()) l.replaceFirstChar { it.uppercase() } else l)
        }
        return sb.toString()
    }
}

/** Praznici u Crnoj Gori (+ najvažniji vjerski). */
object Praznici {
    data class P(val mjesec: Int, val dan: Int, val ime: String) // mjesec 0..11

    private fun vaskrs(god: Int): Calendar {
        // pravoslavni Vaskrs: julijanski račun + 13 dana (važi 1900–2099)
        val a = god % 4; val b = god % 7; val c = god % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val mj = (d + e + 114) / 31
        val dan = (d + e + 114) % 31 + 1
        val cal = Calendar.getInstance()
        cal.clear(); cal.set(god, mj - 1, dan)
        cal.add(Calendar.DAY_OF_MONTH, 13)
        return cal
    }

    fun zaGodinu(god: Int): List<P> {
        val l = arrayListOf(
            P(0, 1, "Nova godina"), P(0, 2, "Nova godina"),
            P(0, 6, "Badnji dan"), P(0, 7, "Božić"), P(0, 14, "Pravoslavna Nova godina"),
            P(4, 1, "Praznik rada"), P(4, 2, "Praznik rada"),
            P(4, 21, "Dan nezavisnosti"), P(4, 22, "Dan nezavisnosti"),
            P(6, 13, "Dan državnosti"), P(6, 14, "Dan državnosti"),
            P(10, 13, "Njegošev dan"), P(11, 24, "Badnje veče (katolički)"), P(11, 25, "Božić (katolički)")
        )
        val v = vaskrs(god)
        fun dodaj(pomak: Int, ime: String) {
            val c = v.clone() as Calendar
            c.add(Calendar.DAY_OF_MONTH, pomak)
            l.add(P(c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), ime))
        }
        dodaj(-2, "Veliki petak"); dodaj(0, "Vaskrs"); dodaj(1, "Vaskršnji ponedjeljak")
        return l
    }

    fun jePraznik(god: Int, mj: Int, dan: Int) = zaGodinu(god).any { it.mjesec == mj && it.dan == dan }

    /** (ime, za koliko dana) – sljedeći praznik od danas (0 = danas). */
    fun sljedeci(danas: Calendar): Pair<String, Int> {
        val start = danas.clone() as Calendar
        start.set(Calendar.HOUR_OF_DAY, 12); start.set(Calendar.MINUTE, 0)
        for (i in 0..400) {
            val c = start.clone() as Calendar
            c.add(Calendar.DAY_OF_MONTH, i)
            val p = zaGodinu(c.get(Calendar.YEAR)).firstOrNull {
                it.mjesec == c.get(Calendar.MONTH) && it.dan == c.get(Calendar.DAY_OF_MONTH)
            }
            if (p != null) return p.ime to i
        }
        return "" to -1
    }
}

object Astro {
    /** Izlazak i zalazak sunca u lokalnim satima (decimalno), null za polarni dan/noć. */
    fun sunce(cal: Calendar, lat: Double, lon: Double): Pair<Double, Double>? {
        val n = cal.get(Calendar.DAY_OF_YEAR)
        val g = 2 * PI / 365.0 * (n - 1)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
            0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val la = Math.toRadians(lat)
        val x = cos(Math.toRadians(90.833)) / (cos(la) * cos(decl)) - tan(la) * tan(decl)
        if (x < -1 || x > 1) return null
        val ha = Math.toDegrees(acos(x))
        val tz = TimeZone.getDefault().getOffset(cal.timeInMillis) / 60000.0
        val izl = (720 - 4 * (lon + ha) - eq + tz) / 60.0
        val zal = (720 - 4 * (lon - ha) - eq + tz) / 60.0
        return izl to zal
    }

    /** Starost Mjeseca u danima (0 = mlađ). */
    fun starostMjeseca(ms: Long): Double {
        val jd = ms / 86_400_000.0 + 2440587.5
        val sin = 29.530588853
        var a = ((jd - 2451550.1) / sin) % 1.0
        if (a < 0) a += 1.0
        return a * sin
    }

    fun osvijetljenost(starost: Double) = (1 - cos(2 * PI * starost / 29.530588853)) / 2

    fun imeMijene(a: Double): String = when {
        a < 1.85 -> "Mlađ"
        a < 5.54 -> "Rastući srp"
        a < 9.23 -> "Prva četvrt"
        a < 12.92 -> "Rastući mjesec"
        a < 16.61 -> "Pun mjesec"
        a < 20.30 -> "Opadajući mjesec"
        a < 23.99 -> "Posljednja četvrt"
        a < 27.68 -> "Opadajući srp"
        else -> "Mlađ"
    }
}
