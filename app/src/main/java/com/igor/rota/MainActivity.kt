package com.igor.rota

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private val orange = Color.parseColor("#FC4C02")
    private val grey = Color.parseColor("#F0F0F0")
    private lateinit var tvTime: TextView
    private lateinit var tvDist: TextView
    private lateinit var tvAvg: TextView
    private lateinit var tvMax: TextView
    private lateinit var tvMsg: TextView
    private lateinit var route: RouteView
    private lateinit var btns: LinearLayout
    private lateinit var rec: LinearLayout
    private lateinit var histScroll: ScrollView
    private lateinit var histList: LinearLayout
    private lateinit var segScroll: ScrollView
    private lateinit var segList: LinearLayout
    private val tabBtns = ArrayList<Button>()
    private var shown: St? = null
    private var pendingGpx: String? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun f(v: Double, d: Int = 1) = String.format(Locale("pt", "BR"), "%.${d}f", v)
    private fun hms(ms: Long): String { val s = ms / 1000; return String.format("%02d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60) }
    private fun tempo(ms: Long): String { val s = ms / 1000; return if (s >= 3600) hms(ms) else String.format("%d:%02d", s / 60, s % 60) }
    private fun prefs() = getSharedPreferences("rota", Context.MODE_PRIVATE)
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun tv(size: Float, bold: Boolean = false, color: Int = Color.DKGRAY) = TextView(this).apply {
        textSize = size; setTextColor(color); gravity = Gravity.CENTER
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun btn(txt: String, bg: Int, onClick: () -> Unit) = Button(this).apply {
        text = txt; setTextColor(Color.WHITE); setBackgroundColor(bg); setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(grey); setPadding(dp(12), dp(12), dp(12), dp(12))
    }
    private fun cardParams() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        buildUi()
        if (!hasPerm()) perms()
        h.post(ticker)
    }

    override fun onDestroy() { h.removeCallbacks(ticker); super.onDestroy() }

    private val ticker = object : Runnable {
        override fun run() { refresh(); h.postDelayed(this, 500) }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(36), dp(16), dp(16))
        }
        root.addView(tv(20f, true, orange).apply { text = "● Rota"; gravity = Gravity.START })
        val tabs = LinearLayout(this)
        listOf("Gravar", "Atividades", "Segmentos").forEachIndexed { i, t ->
            val b = btn(t, if (i == 0) orange else Color.GRAY) { showTab(i) }
            b.textSize = 12f; tabBtns.add(b); tabs.addView(b)
        }
        root.addView(tabs)

        rec = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        route = RouteView(this)
        rec.addView(route, LinearLayout.LayoutParams(MATCH_PARENT, dp(260)))
        tvTime = tv(52f, true, Color.BLACK); rec.addView(tvTime)
        rec.addView(tv(12f).apply { text = "tempo em movimento" })
        val stats = LinearLayout(this).apply { setPadding(0, dp(12), 0, dp(12)) }
        fun stat(label: String): TextView {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(grey); setPadding(0, dp(8), 0, dp(8)) }
            val v = tv(22f, true, Color.BLACK)
            col.addView(v); col.addView(tv(11f).apply { text = label })
            stats.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            return v
        }
        tvDist = stat("km"); tvAvg = stat("média km/h"); tvMax = stat("máx km/h")
        rec.addView(stats)
        btns = LinearLayout(this); rec.addView(btns)
        tvMsg = tv(13f); rec.addView(tvMsg)
        root.addView(rec)

        histList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        histScroll = ScrollView(this).apply { visibility = View.GONE; addView(histList) }
        root.addView(histScroll)
        segList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        segScroll = ScrollView(this).apply { visibility = View.GONE; addView(segList) }
        root.addView(segScroll)
        setContentView(root)
    }

    private fun showTab(i: Int) {
        rec.visibility = if (i == 0) View.VISIBLE else View.GONE
        histScroll.visibility = if (i == 1) View.VISIBLE else View.GONE
        segScroll.visibility = if (i == 2) View.VISIBLE else View.GONE
        tabBtns.forEachIndexed { k, b -> b.setBackgroundColor(if (k == i) orange else Color.GRAY) }
        if (i == 1) renderHist() else if (i == 2) renderSegs()
    }

    private fun refresh() {
        val ms = Tracker.elapsed()
        tvTime.text = hms(ms)
        tvDist.text = f(Tracker.dist / 1000, 2)
        tvAvg.text = f(if (ms > 0) Tracker.dist / (ms / 1000.0) * 3.6 else 0.0)
        tvMax.text = f(Tracker.maxKmh)
        val p = Tracker.points()
        route.pts = p; route.invalidate()
        if (Tracker.st == St.RUNNING && p.isEmpty()) tvMsg.text = "Aguardando sinal de GPS…"
        else if (tvMsg.text == "Aguardando sinal de GPS…") tvMsg.text = ""
        if (shown != Tracker.st) { shown = Tracker.st; renderButtons() }
    }

    private fun renderButtons() {
        btns.removeAllViews()
        val red = Color.parseColor("#C62828")
        when (Tracker.st) {
            St.IDLE -> btns.addView(btn("Iniciar", orange) { start() })
            St.RUNNING -> {
                btns.addView(btn("Pausar", Color.DKGRAY) { Tracker.pause() })
                btns.addView(btn("Finalizar", red) { finalizar() })
            }
            St.PAUSED -> {
                btns.addView(btn("Retomar", orange) { start() })
                btns.addView(btn("Finalizar", red) { finalizar() })
            }
        }
    }

    private fun hasPerm() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun perms() {
        val l = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(l.toTypedArray(), 1)
    }

    private fun start() {
        if (!hasPerm()) { perms(); tvMsg.text = "Permita a localização e toque em Iniciar."; return }
        val was = Tracker.st
        if (was == St.IDLE) Tracker.reset()
        Tracker.resume()
        tvMsg.text = ""
        if (was == St.IDLE) startForegroundService(Intent(this, TrackingService::class.java).setAction("START"))
    }

    private fun finalizar() {
        Tracker.finish()
        val p = Tracker.points()
        if (p.size > 1 && Tracker.dist > 0) {
            saveAct(p, Tracker.dist, Tracker.elapsed(), Tracker.maxKmh)
            tvMsg.text = "Atividade salva em \"Atividades\"."
        } else tvMsg.text = "Atividade muito curta, não foi salva."
        startService(Intent(this, TrackingService::class.java).setAction("STOP"))
    }

    // ---------- Atividades ----------
    private fun acts() = JSONArray(prefs().getString("acts", "[]"))

    private fun parsePts(s: String) = s.split(";").filter { it.isNotEmpty() }.map { q ->
        val x = q.split(","); Pt(x[0].toDouble(), x[1].toDouble(), x.getOrNull(2)?.toLongOrNull() ?: 0L)
    }

    private fun saveAct(pts: List<Pt>, dist: Double, ms: Long, mx: Double) {
        val step = Math.ceil(pts.size / 3000.0).toInt().coerceAtLeast(1)
        val sb = StringBuilder()
        pts.forEachIndexed { i, p -> if (i % step == 0 || i == pts.lastIndex) sb.append(p.lat).append(',').append(p.lon).append(',').append(p.t).append(';') }
        val o = JSONObject().put("id", System.currentTimeMillis()).put("dist", dist).put("ms", ms).put("max", mx).put("pts", sb.toString())
        val old = acts(); val n = JSONArray().put(o)
        for (i in 0 until old.length()) n.put(old.get(i))
        prefs().edit().putString("acts", n.toString()).apply()
    }

    private fun renderHist() {
        histList.removeAllViews()
        val a = acts()
        if (a.length() == 0) { histList.addView(tv(14f).apply { text = "Nenhuma atividade ainda."; setPadding(0, dp(24), 0, 0) }); return }
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val ms = o.getLong("ms"); val d = o.getDouble("dist")
            val avg = if (ms > 0) d / (ms / 1000.0) * 3.6 else 0.0
            val card = card()
            card.addView(tv(15f, true, Color.BLACK).apply { text = df.format(Date(o.getLong("id"))); gravity = Gravity.START })
            card.addView(tv(13f).apply { text = "${f(d / 1000, 2)} km · ${hms(ms)} · méd ${f(avg)} km/h · máx ${f(o.getDouble("max"))} km/h"; gravity = Gravity.START })
            val rv = RouteView(this)
            rv.pts = parsePts(o.getString("pts"))
            card.addView(rv, LinearLayout.LayoutParams(MATCH_PARENT, dp(150)).apply { topMargin = dp(8) })
            val id = o.getLong("id")
            card.addView(Button(this).apply { text = "Criar segmento"; setOnClickListener { criarSegmento(rv.pts) } })
            card.addView(Button(this).apply { text = "Exportar GPX"; setOnClickListener { exportGpx(rv.pts, id) } })
            card.addView(Button(this).apply { text = "Excluir"; setOnClickListener { delAct(id) } })
            histList.addView(card, cardParams())
        }
    }

    private fun delAct(id: Long) {
        val old = acts(); val n = JSONArray()
        for (i in 0 until old.length()) if (old.getJSONObject(i).getLong("id") != id) n.put(old.get(i))
        prefs().edit().putString("acts", n.toString()).apply()
        renderHist()
    }

    private fun exportGpx(pts: List<Pt>, id: Long) {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Rota\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        sb.append("<trk><name>Rota ${iso.format(Date(id))}</name><trkseg>\n")
        for (p in pts) {
            sb.append("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\">")
            if (p.t > 0) sb.append("<time>${iso.format(Date(p.t))}</time>")
            sb.append("</trkpt>\n")
        }
        sb.append("</trkseg></trk></gpx>")
        pendingGpx = sb.toString()
        val name = "rota-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(id)) + ".gpx"
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, name), 2)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (req == 2 && res == RESULT_OK && uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(pendingGpx.orEmpty().toByteArray()) }
                toast("GPX salvo.")
            } catch (e: Exception) { toast("Erro ao salvar o GPX.") }
        }
    }

    // ---------- Segmentos ----------
    private fun segsArr() = JSONArray(prefs().getString("segs", "[]"))

    private fun loadSegs(): List<Seg> {
        val a = segsArr()
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Seg(o.getLong("id"), o.getString("name"), parsePts(o.getString("pts")), o.getDouble("len"))
        }
    }

    private fun saveSeg(name: String, pts: List<Pt>) {
        val sb = StringBuilder(); pts.forEach { sb.append(it.lat).append(',').append(it.lon).append(';') }
        val o = JSONObject().put("id", System.currentTimeMillis()).put("name", name).put("len", Geo.length(pts)).put("pts", sb.toString())
        val old = segsArr(); val n = JSONArray().put(o)
        for (i in 0 until old.length()) n.put(old.get(i))
        prefs().edit().putString("segs", n.toString()).apply()
    }

    private fun delSeg(id: Long) {
        val old = segsArr(); val n = JSONArray()
        for (i in 0 until old.length()) if (old.getJSONObject(i).getLong("id") != id) n.put(old.get(i))
        prefs().edit().putString("segs", n.toString()).apply()
    }

    private fun criarSegmento(pts: List<Pt>) {
        if (pts.size < 10) { toast("Atividade curta demais."); return }
        val n = pts.size
        var ini = 0; var fim = n - 1
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        val rv = RouteView(this).apply { this.pts = pts; hlFrom = 0; hlTo = n - 1 }
        box.addView(rv, LinearLayout.LayoutParams(MATCH_PARENT, dp(220)))
        val info = tv(13f); box.addView(info)
        fun upd() {
            rv.hlFrom = ini; rv.hlTo = fim; rv.invalidate()
            val len = if (fim > ini) Geo.length(pts.subList(ini, fim + 1)) else 0.0
            info.text = "Trecho selecionado: ${f(len / 1000, 2)} km"
        }
        fun seek(label: String, init: Int, onChange: (Int) -> Unit) {
            box.addView(tv(12f).apply { text = label })
            box.addView(SeekBar(this).apply {
                max = n - 1; progress = init
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) { onChange(p); upd() }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
        }
        seek("Início", 0) { ini = it }
        seek("Fim", n - 1) { fim = it }
        val name = EditText(this).apply { hint = "Nome do segmento" }
        box.addView(name)
        upd()
        AlertDialog.Builder(this).setTitle("Criar segmento")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Salvar") { _, _ ->
                val sub = if (fim > ini) pts.subList(ini, fim + 1) else emptyList()
                if (Geo.length(sub) < 200) toast("O segmento precisa ter pelo menos 200 m.")
                else { saveSeg(name.text.toString().ifBlank { "Segmento" }, sub); toast("Segmento criado. Veja na aba Segmentos.") }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun renderSegs() {
        segList.removeAllViews()
        val segs = loadSegs()
        if (segs.isEmpty()) {
            segList.addView(tv(14f).apply { text = "Nenhum segmento ainda.\nEm Atividades, toque em \"Criar segmento\"."; setPadding(0, dp(24), 0, 0) })
            return
        }
        segList.addView(tv(14f).apply { text = "Calculando passagens…"; setPadding(0, dp(24), 0, 0) })
        Thread {
            try {
                val a = acts()
                val recs = (0 until a.length()).map { val o = a.getJSONObject(it); Pair(o.getLong("id"), parsePts(o.getString("pts"))) }
                val res = segs.map { s ->
                    recs.flatMap { r -> Matcher.find(r.second, s).map { Triple(r.first, it.first, it.second) } }.sortedBy { it.third }
                }
                runOnUiThread { drawSegs(segs, res) }
            } catch (e: Exception) {
                runOnUiThread { segList.removeAllViews(); segList.addView(tv(14f).apply { text = "Erro ao calcular os segmentos." }) }
            }
        }.start()
    }

    private fun drawSegs(segs: List<Seg>, res: List<List<Triple<Long, Long, Long>>>) {
        segList.removeAllViews()
        val df = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        segs.forEachIndexed { i, s ->
            val r = res[i]
            val card = card()
            card.addView(tv(16f, true, Color.BLACK).apply { text = s.name; gravity = Gravity.START })
            card.addView(tv(13f).apply { text = "${f(s.len / 1000, 2)} km · ${r.size} passagens"; gravity = Gravity.START })
            if (r.isEmpty()) card.addView(tv(13f).apply { text = "Nenhuma passagem com horário registrado."; gravity = Gravity.START })
            r.take(10).forEachIndexed { k, p ->
                val kmh = s.len / (p.third / 1000.0) * 3.6
                val rank = if (k == 0) "🏆" else "${k + 1}º"
                card.addView(tv(14f, k == 0, Color.BLACK).apply {
                    text = "$rank  ${tempo(p.third)} · ${f(kmh)} km/h · ${df.format(Date(p.second))}"; gravity = Gravity.START
                })
            }
            card.addView(Button(this).apply { text = "Excluir segmento"; setOnClickListener { delSeg(s.id); renderSegs() } })
            segList.addView(card, cardParams())
        }
    }
}
