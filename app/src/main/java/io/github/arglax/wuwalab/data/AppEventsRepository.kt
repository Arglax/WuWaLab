package io.github.arglax.wuwalab.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A local, offline "app event" - a recurring in-app boost window driven purely
 * by the device's own calendar. There is no server call, no cached JSON and no
 * stored flag that can drift out of date: if it is Wednesday where the player
 * is, Midweek Jump is live; if it is Saturday or Sunday, Weekend Rush is.
 *
 * [multiplier] stacks on TOP of the Argstrite Bonus % and on top of a title
 * multiplier (see WuwaRepository.applyBonus) - it is not folded into either.
 */
enum class LocalAppEvent(
    val id: String,
    val title: String,
    val tagline: String,
    val multiplier: Float
) {
    MIDWEEK_JUMP(
        id = "midweek_jump",
        title = "Midweek Jump",
        tagline = "Every Wednesday - all Argstrite earnings x2.",
        multiplier = 2f
    ),
    WEEKEND_RUSH(
        id = "weekend_rush",
        title = "Weekend Rush",
        tagline = "Every Saturday and Sunday - all Argstrite earnings x3.",
        multiplier = 3f
    )
}

object AppEventCalendar {

    fun eventFor(dayOfWeek: DayOfWeek): LocalAppEvent? = when (dayOfWeek) {
        DayOfWeek.WEDNESDAY -> LocalAppEvent.MIDWEEK_JUMP
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> LocalAppEvent.WEEKEND_RUSH
        else -> null
    }

    /** Whichever event is live right now on the device's local calendar, or null. */
    fun activeEvent(
        nowEpochMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): LocalAppEvent? = eventFor(Instant.ofEpochMilli(nowEpochMs).atZone(zone).dayOfWeek)

    /** 1f when nothing is running, so this is always safe to multiply by. */
    fun multiplier(
        nowEpochMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Float = activeEvent(nowEpochMs, zone)?.multiplier ?: 1f

    /**
     * Milliseconds until the active event's window closes - 0 when nothing is
     * live. Weekend Rush spans two days, so this walks forward while the same
     * event still holds rather than assuming a single day.
     */
    fun millisUntilEnd(
        nowEpochMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val active = activeEvent(nowEpochMs, zone) ?: return 0L
        var date: LocalDate = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        while (eventFor(date.plusDays(1).dayOfWeek) == active) {
            date = date.plusDays(1)
        }
        val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return (endMs - nowEpochMs).coerceAtLeast(0L)
    }

    /** The next event to go live and how long until it does - never null, the week always comes back around. */
    fun nextEvent(
        nowEpochMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Pair<LocalAppEvent, Long>? {
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        for (offset in 1..7) {
            val date = today.plusDays(offset.toLong())
            val event = eventFor(date.dayOfWeek) ?: continue
            val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            return event to (startMs - nowEpochMs).coerceAtLeast(0L)
        }
        return null
    }
}
