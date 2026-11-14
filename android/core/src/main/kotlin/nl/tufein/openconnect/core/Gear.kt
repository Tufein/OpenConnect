package nl.tufein.openconnect.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A pair of shoes. Its sport's activities count towards it from [since] (null: from the very first one) until a newer pair
 * of the same sport starts or until [until] (the day it was retired; null while it is in use). [startKm] is what it
 * had already done when it was added, and [retireKm] is when it should be replaced.
 */
data class Shoe(
    val id: String,
    val name: String,
    val sport: Sport,
    val since: LocalDate? = null,
    val until: LocalDate? = null,
    val startKm: Double = 0.0,
    val retireKm: Int = Gear.DEFAULT_RETIRE_KM,
)

/**
 * All the shoes, and the activities the person put on a particular pair (or on none) by hand. Everything else follows
 * the rule in [Gear.shoeFor], so the list stays small however many activities there are.
 */
data class GearBook(val shoes: List<Shoe> = emptyList(), val overrides: Map<String, String> = emptyMap()) {
    /** One line per shoe (`S|id|name|sport|since|until|startKm|retireKm`) or override (`O|activity|shoe`). */
    fun encode(): String = buildString {
        for (s in shoes) {
            append("S|").append(esc(s.id)).append('|').append(esc(s.name)).append('|').append(s.sport.key).append('|')
            append(s.since ?: "").append('|').append(s.until ?: "").append('|').append(s.startKm).append('|').append(s.retireKm).append('\n')
        }
        for ((activity, shoe) in overrides) append("O|").append(esc(activity)).append('|').append(esc(shoe)).append('\n')
    }

    companion object {
        private fun esc(s: String) = s.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A").replace("\r", "%0D")
        private fun unesc(s: String) = s.replace("%0D", "\r").replace("%0A", "\n").replace("%7C", "|").replace("%25", "%")

        private fun date(s: String): LocalDate? = if (s.isEmpty()) null else runCatching { LocalDate.parse(s) }.getOrNull()

        /** Reads what [encode] wrote. A line that makes no sense is skipped rather than failing the whole list. */
        fun decode(text: String): GearBook {
            val shoes = ArrayList<Shoe>()
            val overrides = LinkedHashMap<String, String>()
            for (line in text.lines()) {
                val p = line.split('|')
                when {
                    p.size == 8 && p[0] == "S" -> {
                        val retire = p[7].toIntOrNull() ?: continue
                        val start = p[6].toDoubleOrNull() ?: continue
                        if (p[1].isEmpty() || p[2].isEmpty()) continue
                        shoes += Shoe(unesc(p[1]), unesc(p[2]), Sport.fromKey(p[3]), date(p[4]), date(p[5]), start, retire)
                    }
                    p.size == 3 && p[0] == "O" -> overrides[unesc(p[1])] = unesc(p[2])
                }
            }
            return GearBook(shoes, overrides)
        }
    }
}

enum class ShoeStatus {
    /** Plenty of kilometres left. */
    FRESH,

    /** At 80 percent of its limit or more. */
    GETTING_OLD,

    /** At or past its limit: time for a new pair. */
    REPLACE,
}

/** What a pair has done. [km] includes [Shoe.startKm]; [last] is the day of the latest activity on it. */
data class ShoeUsage(val shoe: Shoe, val km: Double, val activities: Int, val last: LocalDate?, val status: ShoeStatus) {
    /** How much of its life is used, 1.0 at the limit (and more beyond it). */
    val fraction: Double get() = if (shoe.retireKm <= 0) 1.0 else km / shoe.retireKm
}

/** Shoe mileage: which activity went on which pair, how far each pair has gone and when it should be replaced. */
object Gear {
    const val DEFAULT_RETIRE_KM = 800
    const val MIN_RETIRE_KM = 100
    const val MAX_RETIRE_KM = 3000
    const val LIMIT_STEP_KM = 50
    const val MAX_NAME = 40
    const val MAX_SHOES = 40
    const val WARN_FRACTION = 0.8

    /** The value of an override that means "no shoes on this one". */
    const val NONE = "-"

    /** The sports that get shoes in the app. */
    val SPORTS = listOf(Sport.RUNNING, Sport.WALKING, Sport.HIKING, Sport.CYCLING)

    private fun day(a: Activity): LocalDate = a.startTime.atZone(ZoneOffset.UTC).toLocalDate()

    /** A name that fits one line of the stored text; blank stays blank. */
    fun cleanName(name: String): String = name.filter { !it.isISOControl() }.replace('|', ' ').trim().take(MAX_NAME).trim()

    private fun newId(now: Instant, taken: Set<String>): String {
        var n = now.epochSecond
        while ("s$n" in taken) n++
        return "s$n"
    }

    /**
     * Adds a pair that is the default for [sport] from [today] on, or, with [countEarlier], for all its earlier activities too
     * (when no other pair of that sport has them). A blank name or too many pairs changes nothing.
     */
    fun add(book: GearBook, name: String, sport: Sport, today: LocalDate, countEarlier: Boolean, now: Instant): GearBook {
        val clean = cleanName(name)
        if (clean.isEmpty() || book.shoes.size >= MAX_SHOES) return book
        val shoe = Shoe(newId(now, book.shoes.map { it.id }.toSet()), clean, sport, since = if (countEarlier) null else today)
        return book.copy(shoes = book.shoes + shoe)
    }

    private fun change(book: GearBook, id: String, f: (Shoe) -> Shoe) =
        book.copy(shoes = book.shoes.map { if (it.id == id) f(it) else it })

    /** Takes a pair out of use from [today]: its past stays with it, new activities go elsewhere. */
    fun retire(book: GearBook, id: String, today: LocalDate) = change(book, id) { it.copy(until = today) }

    fun resume(book: GearBook, id: String) = change(book, id) { it.copy(until = null) }

    /** Moves the replace-after limit by [kmDelta], kept between [MIN_RETIRE_KM] and [MAX_RETIRE_KM]. */
    fun moveLimit(book: GearBook, id: String, kmDelta: Int) =
        change(book, id) { it.copy(retireKm = (it.retireKm + kmDelta).coerceIn(MIN_RETIRE_KM, MAX_RETIRE_KM)) }

    /** Deletes a pair and the hand-made choices that pointed at it. */
    fun remove(book: GearBook, id: String) =
        GearBook(book.shoes.filter { it.id != id }, book.overrides.filterValues { it != id })

    /** Puts an activity on [shoeId], on no shoes ([NONE]), or, with null, back to what the rule says. */
    fun assign(book: GearBook, activityId: String, shoeId: String?): GearBook =
        book.copy(overrides = if (shoeId == null) book.overrides - activityId else book.overrides + (activityId to shoeId))

    /** The pair an activity went on: the hand-made choice, otherwise the newest pair of its sport that was in use that day. */
    fun shoeFor(book: GearBook, activity: Activity): Shoe? {
        val chosen = book.overrides[activity.id]
        if (chosen == NONE) return null
        book.shoes.firstOrNull { it.id == chosen }?.let { return it }
        val d = day(activity)
        return book.shoes
            .filter { it.sport == activity.sport && (it.since == null || !d.isBefore(it.since)) && (it.until == null || !d.isAfter(it.until)) }
            .sortedBy { it.since ?: LocalDate.MIN }
            .lastOrNull()
    }

    fun status(km: Double, retireKm: Int): ShoeStatus = when {
        km >= retireKm -> ShoeStatus.REPLACE
        km >= retireKm * WARN_FRACTION -> ShoeStatus.GETTING_OLD
        else -> ShoeStatus.FRESH
    }

    /** What every pair has done, in the order they were added. */
    fun usage(book: GearBook, activities: List<Activity>): List<ShoeUsage> {
        val meters = HashMap<String, Double>()
        val counts = HashMap<String, Int>()
        val last = HashMap<String, LocalDate>()
        for (a in activities) {
            val shoe = shoeFor(book, a) ?: continue
            meters[shoe.id] = (meters[shoe.id] ?: 0.0) + a.distanceM
            counts[shoe.id] = (counts[shoe.id] ?: 0) + 1
            val d = day(a)
            if (last[shoe.id]?.isBefore(d) != false) last[shoe.id] = d
        }
        return book.shoes.map { s ->
            val km = s.startKm + (meters[s.id] ?: 0.0) / 1000.0
            ShoeUsage(s, km, counts[s.id] ?: 0, last[s.id], status(km, s.retireKm))
        }
    }

    /** Pairs still in use that have reached their limit, which is what a notification is sent about. */
    fun due(usage: List<ShoeUsage>): List<ShoeUsage> = usage.filter { it.shoe.until == null && it.status == ShoeStatus.REPLACE }

    /** The pairs an activity can be put on by hand: those of its sport. */
    fun choices(book: GearBook, activity: Activity): List<Shoe> = book.shoes.filter { it.sport == activity.sport }
}
