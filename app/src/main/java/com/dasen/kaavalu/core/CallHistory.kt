package com.dasen.kaavalu.core

/**
 * Every call from an unknown number, newest first, kept on the phone.
 *
 * The first version kept only calls that reached Watch, and only the latest one. A family
 * member testing it — deleting a friend's number and having him call — saw a 15-point call
 * correctly detected and then nothing to look at, which reads as "it did not work". A quiet
 * call is still worth seeing: it is the proof the app is listening, and the stranger who
 * called twice this week is exactly the pattern a family wants to notice.
 *
 * Pure list operations, so the storage rules are tested without a phone.
 */
object CallHistory {

    /** Enough to cover a bad week, small enough to stay a list a person can read. */
    const val CAP = 20

    private const val SEPARATOR = "=="

    /**
     * Insert or update [r]. A call is identified by when it started: the live session
     * rewrites its own entry as each new signal lands, rather than adding a new one.
     */
    fun upsert(list: List<SessionRecord>, r: SessionRecord, cap: Int = CAP): List<SessionRecord> =
        (listOf(r) + list.filter { it.startedAt != r.startedAt })
            .sortedByDescending { it.startedAt }
            .take(cap)

    /** "I know this person": the caller was never a stranger, so the call leaves no trail. */
    fun remove(list: List<SessionRecord>, startedAt: Long): List<SessionRecord> =
        list.filter { it.startedAt != startedAt }

    fun encode(list: List<SessionRecord>): String =
        list.joinToString("$SEPARATOR\n") { it.encode() }

    /** Entries that cannot be read are dropped one by one, never the whole history. */
    fun decode(raw: String?): List<SessionRecord> =
        raw.orEmpty()
            .split("$SEPARATOR\n")
            .mapNotNull { SessionRecord.decode(it) }
}

/** The highest tier a recorded call reached. */
val SessionRecord.peak: Tier
    get() = escalations.maxOfOrNull { it.tier } ?: Tier.CALM
