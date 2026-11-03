package de.screentime.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class TrackedApp(
    val name: String,
    val procs: List<String>,
    val contains: String,
    val game: Boolean,
)

data class Status(
    val child: String,
    val live: Boolean,
    val weekday: Int,
    val limitToday: Int,
    val spentToday: Int,
    val leftToday: Int,
    /** Wochentag (1 = Mo … 7 = So) → Sekunden; fehlender Tag = gesperrt */
    val limits: Map<Int, Int>,
    val hours: Map<Int, List<Int>>,
    val lockout: String,
    val ptEnabled: Boolean,
    val ptLimits: Map<Int, Int>,
    val ptSpent: Int,
    val ptLeft: Int,
    val usageToday: Map<String, Int>,
    val usageWeek: Map<String, Int>,
    val apps: List<TrackedApp>,
    /** PC gesperrt (Kinderkonto deaktiviert, Bildschirm gesperrt) */
    val locked: Boolean,
)

data class DayLimit(val day: Int, val allowed: Boolean, val secs: Int)

data class LimitsEdit(
    val days: List<DayLimit>,
    val fromHour: Int,
    val toHour: Int,
    val lockout: String,
    val ptEnabled: Boolean,
    val ptSecs: Int,
) {
    companion object {
        fun from(s: Status): LimitsEdit {
            val days = (1..7).map { d ->
                DayLimit(d, s.limits.containsKey(d), s.limits[d] ?: (105 * 60))
            }
            val hours = (1..7).mapNotNull { s.hours[it] }.firstOrNull { it.isNotEmpty() }.orEmpty()
            return LimitsEdit(
                days = days,
                fromHour = hours.minOrNull() ?: 0,
                toHour = (hours.maxOrNull() ?: 23) + 1,
                lockout = s.lockout,
                ptEnabled = s.ptEnabled,
                ptSecs = s.ptLimits.values.firstOrNull() ?: 3600,
            )
        }
    }
}

/** Spricht per SSH mit screentime-ctl auf dem PC. Alle Funktionen blockieren (im IO-Thread aufrufen). */
class Api(private val store: Store) {

    private fun call(args: String): JSONObject {
        val c = store.conn()
        if (!c.complete) throw ApiException("Bitte zuerst unter „Einstellungen“ die Verbindung zum PC eintragen.")
        val r = Ssh.exec(c, "sudo -n /usr/local/sbin/screentime-ctl $args")
        if (c.hostKey.isBlank() && r.hostKey != null) store.hostKey = r.hostKey

        val line = r.out.lines().lastOrNull { it.trim().startsWith("{") }?.trim()
        if (line == null) {
            val err = r.err.lowercase()
            throw ApiException(
                when {
                    "password is required" in err || "passwort" in err || "sudoers" in err ->
                        "Keine Berechtigung auf dem PC. Bitte das Installationsskript ausführen."
                    "not found" in err || "nicht gefunden" in err || r.exit == 127 ->
                        "Screentime ist auf dem PC noch nicht installiert. Bitte das Installationsskript ausführen."
                    else -> "Unerwartete Antwort vom PC: ${r.err.take(200)}"
                }
            )
        }
        val j = JSONObject(line)
        if (!j.optBoolean("ok")) {
            val err = j.optString("error", "Unbekannter Fehler auf dem PC")
            if (err == "Unbekannter Befehl") throw ApiException(
                "Diese Funktion kennt der PC noch nicht. Bitte das Installationsskript auf dem PC erneut ausführen."
            )
            throw ApiException(err)
        }
        return j
    }

    fun ping(): String = call("ping").optString("child")

    fun status(): Status = parseStatus(call("status"))

    /** op: '+', '-' oder '=' */
    fun changeTime(op: Char, secs: Int, playtime: Boolean = false) {
        require(op == '+' || op == '-' || op == '=')
        val cmd = if (playtime) "playbonus" else "bonus"
        call("$cmd $op ${secs.coerceIn(0, 86_400)}")
    }

    fun saveLimits(e: LimitsEdit) {
        val days = JSONObject()
        e.days.filter { it.allowed }.forEach { days.put(it.day.toString(), it.secs) }
        val j = JSONObject()
            .put("days", days)
            .put("hours", JSONObject().put("from", e.fromHour).put("to", e.toHour))
            .put("lockout", e.lockout)
            .put("playtime", JSONObject().put("enabled", e.ptEnabled).put("seconds", e.ptSecs))
        call("limits ${b64(j.toString())}")
    }

    fun saveApps(apps: List<TrackedApp>) {
        val arr = JSONArray()
        apps.forEach { a ->
            arr.put(
                JSONObject()
                    .put("name", a.name)
                    .put("procs", JSONArray(a.procs))
                    .put("contains", a.contains)
                    .put("game", a.game)
            )
        }
        call("apps ${b64(arr.toString())}")
    }

    /** PC sofort sperren: Bildschirm sperren und Anmelden verhindern, bis [unlock] aufgerufen wird. */
    fun lock() {
        call("lock")
    }

    fun unlock() {
        call("unlock")
    }

    /** Zeigt [text] mit Ton auf dem Bildschirm des Kindes. */
    fun callChild(text: String) {
        call("call ${b64(text.take(200))}")
    }

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    companion object {
        private fun intMap(o: JSONObject?): Map<Int, Int> {
            if (o == null) return emptyMap()
            return o.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to o.optInt(k) } }.toMap()
        }

        private fun strMap(o: JSONObject?): Map<String, Int> {
            if (o == null) return emptyMap()
            return o.keys().asSequence().associateWith { o.optInt(it) }
        }

        fun parseStatus(j: JSONObject): Status {
            val today = j.optJSONObject("today") ?: JSONObject()
            val hoursObj = j.optJSONObject("hours") ?: JSONObject()
            val hours = hoursObj.keys().asSequence().mapNotNull { k ->
                val arr = hoursObj.optJSONArray(k) ?: return@mapNotNull null
                k.toIntOrNull()?.let { d -> d to (0 until arr.length()).map { arr.optInt(it) } }
            }.toMap()
            val pt = j.optJSONObject("playtime") ?: JSONObject()
            val usage = j.optJSONObject("usage") ?: JSONObject()
            val appsArr = j.optJSONArray("apps") ?: JSONArray()
            val apps = (0 until appsArr.length()).mapNotNull { i ->
                val a = appsArr.optJSONObject(i) ?: return@mapNotNull null
                val p = a.optJSONArray("procs") ?: JSONArray()
                TrackedApp(
                    name = a.optString("name"),
                    procs = (0 until p.length()).map { p.optString(it) },
                    contains = a.optString("contains"),
                    game = a.optBoolean("game"),
                )
            }
            return Status(
                child = j.optString("child"),
                live = j.optBoolean("live"),
                weekday = j.optInt("weekday", 1),
                limitToday = today.optInt("limit"),
                spentToday = today.optInt("spent"),
                leftToday = today.optInt("left"),
                limits = intMap(j.optJSONObject("limits")),
                hours = hours,
                lockout = j.optString("lockout", "terminate"),
                ptEnabled = pt.optBoolean("enabled"),
                ptLimits = intMap(pt.optJSONObject("limits")),
                ptSpent = pt.optInt("spent"),
                ptLeft = pt.optInt("left"),
                usageToday = strMap(usage.optJSONObject("today")),
                usageWeek = strMap(usage.optJSONObject("week")),
                apps = apps,
                locked = j.optBoolean("locked"),
            )
        }
    }
}
