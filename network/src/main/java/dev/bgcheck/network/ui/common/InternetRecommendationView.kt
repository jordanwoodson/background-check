package dev.bgcheck.network.ui.common

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.widget.TextView
import dev.bgcheck.network.domain.model.InternetRecommendation
import dev.bgcheck.network.domain.model.InternetRecommendations
import dev.bgcheck.network.domain.model.NetworkPackage
import dev.bgcheck.network.domain.model.Package
import dev.bgcheck.network.domain.model.PackageType

/** All package and firewall sheets use the same advisory engine as the main Apps list. */
object InternetRecommendationView {
    fun bind(view: TextView, pkg: NetworkPackage) {
        render(view, InternetRecommendations.forPackage(
            packageName = pkg.packageName, system = pkg.type == PackageType.SYSTEM, uid = pkg.uid,
            hasInternetPermission = pkg.hasInternetPermission.takeIf { pkg.permissionsKnown },
            sharedUid = sharedUid(view, pkg.uid), critical = pkg.isSystemCritical || pkg.isVpnApp,
            enabled = pkg.isEnabled,
        ))
    }

    fun bind(view: TextView, pkg: Package) {
        val installed = pkg.isInstalled
        render(view, InternetRecommendations.forPackage(
            packageName = pkg.packageName, system = pkg.type == PackageType.SYSTEM, uid = pkg.uid,
            installed = installed,
            hasInternetPermission = pkg.permissions.contains("android.permission.INTERNET").takeIf { pkg.permissionsKnown },
            sharedUid = installed && sharedUid(view, pkg.uid), enabled = pkg.isEnabled,
        ))
    }

    private fun sharedUid(view: TextView, uid: Int): Boolean = uid >= 0 &&
        (runCatching { view.context.packageManager.getPackagesForUid(uid)?.size }.getOrNull() ?: 0) > 1

    private fun render(view: TextView, recommendation: InternetRecommendation) {
        var expanded = false
        fun update() {
            val body = if (expanded) recommendation.detailText() else
                "${recommendation.advice.label}\n\n${recommendation.reason}"
            val text = SpannableStringBuilder("Internet firewall recommendation\n\n$body")
            if (expanded) {
                text.append("\n\n${InternetRecommendations.GUIDANCE_NOTE}")
                recommendation.sourceUrl?.let { url ->
                    text.append("\n\n")
                    val start = text.length
                    text.append("Recommendation source")
                    text.setSpan(URLSpan(url), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            text.append(if (expanded) "\n\nHide recommendation details" else "\n\nShow effects and details")
            view.text = text
            view.stateDescription = if (expanded) "Expanded" else "Collapsed"
        }
        view.movementMethod = LinkMovementMethod.getInstance()
        view.isFocusable = true
        view.setOnClickListener { expanded = !expanded; update() }
        update()
    }
}
