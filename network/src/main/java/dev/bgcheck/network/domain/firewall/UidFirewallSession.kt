package dev.bgcheck.network.domain.firewall

/** The individual UID operations available on Android's OEM deny chain. */
interface UidFirewallPort {
    fun getRule(uid: Int): Int
    fun setRule(uid: Int, rule: Int)
}

/** Journal before changing Android state; restore only rules this session actually changed. */
class UidFirewallSession(
    private val port: UidFirewallPort,
    private val load: () -> Map<Int, Int>,
    private val save: (Map<Int, Int>) -> Unit,
) {
    fun apply(deniedUids: Set<Int>) {
        val journal = load().toMutableMap()
        for (uid in deniedUids) {
            val previous = port.getRule(uid)
            if (previous == 2) continue
            if (uid !in journal) {
                journal[uid] = previous
                save(journal.toMap())
            }
            port.setRule(uid, 2)
            check(port.getRule(uid) == 2) { "Android did not deny UID $uid" }
        }
        restore(journal.keys - deniedUids)
    }

    fun restore() = restore(load().keys)

    private fun restore(uids: Set<Int>) {
        for (uid in uids) {
            val journal = load().toMutableMap()
            val previous = journal[uid] ?: continue
            // Another controller may have already changed it; preserve its current choice.
            if (port.getRule(uid) == 2) {
                port.setRule(uid, previous)
                check(port.getRule(uid) == previous) { "Android did not restore UID $uid" }
            }
            journal.remove(uid)
            save(journal)
        }
    }
}
