package com.jovan.vrijeme

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

class MainActivity : Activity() {

    private lateinit var prikaz: Prikaz
    private lateinit var prefs: SharedPreferences
    private val h = Handler(Looper.getMainLooper())
    private var aktivan = false

    private val preuzmi = Runnable { osvjeziVrijeme() }
    private val minutni = object : Runnable {
        override fun run() {
            svjetlina()
            sakrijSistem()
            h.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        prefs = getSharedPreferences("vrijeme", MODE_PRIVATE)
        prikaz = Prikaz(this)
        setContentView(prikaz)
        ucitajPodesavanja()
        // keširana prognoza da ekran nije prazan dok mreža ne proradi
        prefs.getString("json", null)?.let { j ->
            try { prikaz.vrijeme = Vrijeme(j, prefs.getLong("preuzeto", 0L)) } catch (_: Exception) {}
        }
        prikaz.setOnClickListener { prikaz.sljedecaKartica() }
        prikaz.setOnLongClickListener { podesavanja(); true }
    }

    private fun ucitajPodesavanja() {
        prikaz.grad = prefs.getString("grad", "Berane") ?: "Berane"
        prikaz.lat = prefs.getFloat("lat", 42.8433f).toDouble()
        prikaz.lon = prefs.getFloat("lon", 19.8728f).toDouble()
        prikaz.sekunde = prefs.getBoolean("sekunde", true)
        requestedOrientation = when (prefs.getInt("orijentacija", 0)) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    override fun onResume() {
        super.onResume()
        aktivan = true
        sakrijSistem()
        h.removeCallbacks(minutni); h.post(minutni)
        h.removeCallbacks(preuzmi)
        val staro = System.currentTimeMillis() - prefs.getLong("preuzeto", 0L)
        h.postDelayed(preuzmi, if (staro > 10 * 60_000L) 1500L else 15 * 60_000L - staro)
    }

    override fun onPause() {
        super.onPause()
        aktivan = false
        h.removeCallbacks(minutni)
        h.removeCallbacks(preuzmi)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) sakrijSistem()
    }

    @Suppress("DEPRECATION")
    private fun sakrijSistem() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    /** Noću (23–6 h) se ekran zatamni da ne bliješti po sobi. */
    private fun svjetlina() {
        val sat = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val nocu = prefs.getBoolean("zatamni", true) && (sat >= 23 || sat < 6)
        val lp = window.attributes
        val cilj = if (nocu) 0.04f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (lp.screenBrightness != cilj) {
            lp.screenBrightness = cilj
            window.attributes = lp
        }
    }

    private fun osvjeziVrijeme() {
        val lat = prikaz.lat; val lon = prikaz.lon
        Thread {
            var ok = false
            try {
                val json = preuzmiTekst(Vrijeme.url(lat, lon))
                val sad = System.currentTimeMillis()
                val v = Vrijeme(json, sad)
                prefs.edit().putString("json", json).putLong("preuzeto", sad).apply()
                runOnUiThread { prikaz.vrijeme = v }
                ok = true
            } catch (e: Exception) {
                android.util.Log.w("Vrijeme", "prognoza nije preuzeta", e)
            }
            runOnUiThread {
                if (aktivan) {
                    h.removeCallbacks(preuzmi)
                    h.postDelayed(preuzmi, if (ok) 15 * 60_000L else 2 * 60_000L)
                }
            }
        }.start()
    }

    private fun preuzmiTekst(adresa: String): String {
        val c = URL(adresa).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        // Open-Meteo odbija podrazumijevani „Dalvik/…" User-Agent (HTTP 403)
        c.setRequestProperty("User-Agent", "VrijemeSat/1.0 (Android tablet sat)")
        try {
            if (c.responseCode != 200) {
                val tijelo = try { c.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
                throw RuntimeException("HTTP ${c.responseCode} $adresa :: ${tijelo?.take(300)}")
            }
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { c.disconnect() }
    }

    // ───────────────────────── podešavanja (dugi pritisak) ─────────────────────────

    private fun podesavanja() {
        val orNazivi = arrayOf("položeno", "uspravno", "automatski")
        val stavke = arrayOf(
            "Grad: ${prikaz.grad}  —  promijeni",
            "Položaj ekrana: ${orNazivi[prefs.getInt("orijentacija", 0)]}",
            "Noćno zatamnjenje (23–6 h): " + if (prefs.getBoolean("zatamni", true)) "uključeno" else "isključeno",
            "Sekunde: " + if (prikaz.sekunde) "prikazane" else "sakrivene",
            "Osvježi prognozu sada",
            "Zatvori aplikaciju"
        )
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Podešavanja")
            .setItems(stavke) { _, i ->
                when (i) {
                    0 -> traziGrad()
                    1 -> {
                        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Položaj ekrana")
                            .setSingleChoiceItems(orNazivi, prefs.getInt("orijentacija", 0)) { d, j ->
                                prefs.edit().putInt("orijentacija", j).apply()
                                ucitajPodesavanja(); d.dismiss()
                            }.show()
                    }
                    2 -> { prefs.edit().putBoolean("zatamni", !prefs.getBoolean("zatamni", true)).apply(); svjetlina() }
                    3 -> { prefs.edit().putBoolean("sekunde", !prikaz.sekunde).apply(); ucitajPodesavanja() }
                    4 -> { Toast.makeText(this, "Preuzimam prognozu…", Toast.LENGTH_SHORT).show(); osvjeziVrijeme() }
                    5 -> finish()
                }
            }
            .setOnDismissListener { sakrijSistem() }
            .show()
    }

    private fun traziGrad() {
        val unos = EditText(this).apply {
            hint = "npr. Berane, Podgorica, Kolašin"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine()
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Koji grad?")
            .setView(unos)
            .setPositiveButton("Traži") { _, _ ->
                val upit = unos.text.toString().trim()
                if (upit.isNotEmpty()) pretrazi(upit)
            }
            .setNegativeButton("Otkaži", null)
            .show()
    }

    private fun pretrazi(upit: String) {
        Thread {
            try {
                val url = "http://geocoding-api.open-meteo.com/v1/search?count=8&language=sr&name=" +
                    URLEncoder.encode(upit, "UTF-8")
                val arr = JSONObject(preuzmiTekst(url)).optJSONArray("results")
                val imena = ArrayList<String>(); val kratko = ArrayList<String>()
                val lat = ArrayList<Double>(); val lon = ArrayList<Double>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ime = Tekst.latinica(o.optString("name"))
                    val dio = listOf(o.optString("admin1"), o.optString("country"))
                        .filter { it.isNotEmpty() }.joinToString(", ") { Tekst.latinica(it) }
                    imena.add(if (dio.isEmpty()) ime else "$ime — $dio")
                    kratko.add(ime); lat.add(o.optDouble("latitude")); lon.add(o.optDouble("longitude"))
                }
                runOnUiThread {
                    if (imena.isEmpty()) {
                        Toast.makeText(this, "Nisam našao „$upit”", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Izaberi mjesto")
                        .setItems(imena.toTypedArray()) { _, i ->
                            prefs.edit().putString("grad", kratko[i])
                                .putFloat("lat", lat[i].toFloat()).putFloat("lon", lon[i].toFloat())
                                .remove("json").putLong("preuzeto", 0L).apply()
                            prikaz.vrijeme = null
                            ucitajPodesavanja()
                            osvjeziVrijeme()
                        }
                        .setOnDismissListener { sakrijSistem() }
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Nema veze sa internetom", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}

/** Kad se tablet upali, odmah otvori sat. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(i)
    }
}
