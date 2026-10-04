package dev.bgcheck.app.data

import dev.bgcheck.network.domain.model.InternetRecommendation
import dev.bgcheck.network.domain.model.InternetRecommendations

val AppEntry.internetRecommendation: InternetRecommendation
    get() = InternetRecommendations.forPackage(
        packageName = packageName, system = system, installed = installed, uid = uid,
        hasInternetPermission = hasInternetPermission,
        sharedUid = associations.any { it.kind == AssociationKind.SHARED_UID },
        enabled = enabled,
    )
