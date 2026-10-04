package dev.bgcheck.network.domain.model

enum class InternetAdvice(val label: String) {
    KEEP_ALLOWED("Keep internet allowed"),
    ALLOW_IF_USED("Allow if you use online features"),
    BLOCK_OPTIONAL("Consider blocking internet"),
    NO_DIRECT_ACCESS("No direct internet permission"),
    REVIEW("Review before blocking"),
    UNINSTALLED("No rule needed while uninstalled"),
}

data class InternetRecommendation(
    val advice: InternetAdvice,
    val reason: String,
    val consequence: String,
    val notes: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val afterInstall: InternetRecommendation? = null,
) {
    fun detailText(): String = (listOf(advice.label, reason, consequence) + notes +
        listOfNotNull(afterInstall?.let { "If reinstalled: ${it.advice.label}. ${it.reason} ${it.consequence}" }))
        .filter { it.isNotBlank() }.joinToString("\n\n")
}

object InternetRecommendations {
    const val GUIDANCE_NOTE = "Guidance only; opening this screen does not change firewall rules. A recommendation is not the current firewall status."
    private const val PERMISSION_SOURCE = "https://developer.android.com/reference/android/Manifest.permission#INTERNET"
    private const val UID_SOURCE = "https://developer.android.com/guide/topics/manifest/manifest-element#uid"

    // Exact IDs only: a label, prefix or uninstall-safety rating cannot establish network needs.
    private val guide = InternetRecommendationGuide.entries

    fun forPackage(
        packageName: String,
        system: Boolean,
        installed: Boolean = true,
        uid: Int = -1,
        hasInternetPermission: Boolean? = null,
        sharedUid: Boolean = false,
        critical: Boolean = false,
        enabled: Boolean = true,
    ): InternetRecommendation {
        val baseline = guide[packageName] ?: if (system) InternetRecommendation(
            InternetAdvice.KEEP_ALLOWED,
            "This system package has no specific network recommendation in the guide.",
            "Keep its current access until its role is understood; blocking may interrupt device features or other apps.",
        ) else InternetRecommendation(
            InternetAdvice.REVIEW,
            "This app has no specific network recommendation in the guide.",
            "Check whether you need its sign-in, notifications, sync, downloads or other online features before blocking.",
        )
        if (!installed) return InternetRecommendation(
            InternetAdvice.UNINSTALLED,
            "This package is not installed for this Android user, so there is no running app here to block.",
            "A retained record is not a live firewall identity. It may still be installed in another profile, which needs its own review.",
            notes = listOf("After reinstalling, refresh the inventory and check its new permissions and shared UID before setting a rule."),
            afterInstall = baseline,
            sourceUrl = baseline.sourceUrl,
        )
        val recommendation = when {
            uid >= 0 && uid % 100_000 < 10_000 -> InternetRecommendation(
                InternetAdvice.KEEP_ALLOWED, "This package uses a core Android UID.",
                "Blocking that identity can disrupt connectivity or multiple system services. Some firewall backends cannot block it.",
                sourceUrl = UID_SOURCE,
            )
            sharedUid -> InternetRecommendation(
                InternetAdvice.KEEP_ALLOWED, "This package shares its Android UID with other installed packages.",
                "A firewall rule can affect the whole group. Review every package using this UID before blocking any member.",
                sourceUrl = UID_SOURCE,
            )
            critical -> InternetRecommendation(
                InternetAdvice.KEEP_ALLOWED, "The firewall identifies this as a protected system or VPN package.",
                "Blocking it may interrupt essential services or the connection carrying other apps' traffic.",
            )
            hasInternetPermission == false -> InternetRecommendation(
                InternetAdvice.NO_DIRECT_ACCESS, "Android reports no INTERNET permission for this installed package.",
                "A direct internet block is usually unnecessary. It may still ask another app or system service to perform online work.",
                sourceUrl = PERMISSION_SOURCE,
            )
            else -> baseline
        }
        return recommendation.copy(notes = buildList {
            if (hasInternetPermission == null) add("Internet permission could not be verified; do not assume this app is offline.")
            if (!enabled) add("Currently disabled. This advice applies if the package is enabled again.")
            if (uid >= 0) add("Android user ${uid / 100_000} · UID $uid. Rules apply to that Android identity; review shared packages and the selected firewall backend.")
        })
    }
}
