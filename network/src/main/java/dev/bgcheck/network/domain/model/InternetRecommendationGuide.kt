package dev.bgcheck.network.domain.model

/** Editorial advice based on the documented roles in docs/package-descriptions.md, not traffic analysis. */
internal object InternetRecommendationGuide {
    val entries: Map<String, InternetRecommendation> = buildMap {
        fun group(advice: InternetAdvice, reason: String, consequence: String, source: String, vararg ids: String) {
            ids.forEach { id ->
                check(!containsKey(id)) { "Duplicate internet recommendation: $id" }
                put(id, InternetRecommendation(advice, reason, consequence, sourceUrl = source))
            }
        }
        group(InternetAdvice.KEEP_ALLOWED,
            "This component supports Android connectivity and network sign-in.",
            "Blocking can break network validation, Wi-Fi sign-in or connectivity used by other apps.",
            "https://source.android.com/docs/core/ota/modular-system/networking",
            "com.android.networkstack", "com.google.android.networkstack",
            "com.android.captiveportallogin", "com.google.android.captiveportallogin",
            "com.android.networkstack.tethering", "com.google.android.networkstack.tethering",
            "com.android.pacprocessor", "com.android.proxyhandler")
        group(InternetAdvice.KEEP_ALLOWED,
            "Google Play services supplies shared sign-in, push notifications and other services.",
            "Blocking can delay notifications and break sign-in or features in many other apps.",
            "https://firebase.google.com/docs/cloud-messaging/network-configuration",
            "com.google.android.gms", "com.google.android.gsf")
        group(InternetAdvice.KEEP_ALLOWED,
            "MicroG provides account and notification services for compatible apps.",
            "Blocking can break sign-in and notifications in apps that depend on this service.",
            "https://github.com/ReVanced/GmsCore",
            "app.revanced.android.gms", "app.morphe.android.gms")
        group(InternetAdvice.KEEP_ALLOWED,
            "This component supports calling, messaging or carrier provisioning.",
            "Blocking can interrupt MMS, Wi-Fi calling, visual voicemail, activation or carrier services; behavior varies by carrier.",
            "https://source.android.com/docs/core/connect",
            "com.android.phone", "com.android.server.telecom", "com.android.mms.service",
            "com.android.imsserviceentitlement", "com.google.android.ims", "com.google.android.euicc",
            "com.motorola.attvowifi", "com.motorola.entitlement", "com.motorola.carrierconfig",
            "com.motorola.omadm.service", "com.motorola.rcsConfigService", "com.motorola.visualvoicemail")
        group(InternetAdvice.KEEP_ALLOWED,
            "This service retrieves system updates or security configuration.",
            "Blocking can prevent update checks and downloads. Keep access available for device maintenance.",
            "https://support.google.com/android/answer/7680439",
            "com.motorola.ccc.ota", "com.google.android.configupdater")
        group(InternetAdvice.KEEP_ALLOWED,
            "Android's Download Manager performs downloads on behalf of other apps.",
            "Blocking can stop downloads requested by browsers, stores and other apps.",
            "https://developer.android.com/reference/android/app/DownloadManager",
            "com.android.providers.downloads")
        group(InternetAdvice.KEEP_ALLOWED,
            "This VPN or remote-access tool carries connections or keeps the device reachable.",
            "Blocking can disconnect remote access or traffic routed through it. Review its own connection settings first.",
            "https://developer.android.com/develop/connectivity/vpn",
            "com.tailscale.ipn", "sh.haven.app", "com.termux", "com.p5sys.jump.android",
            "com.wireguard.android", "ch.protonvpn.android", "com.privateinternetaccess.android",
            "com.adguard.android")
        group(InternetAdvice.ALLOW_IF_USED,
            "This app checks release sources and downloads app updates.",
            "Blocking stops update checks and installation downloads, including automatic updates you rely on.",
            "https://github.com/ImranR98/Obtainium",
            "dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")
        group(InternetAdvice.ALLOW_IF_USED,
            "This store uses the network to find, install and update apps.",
            "Blocking prevents store browsing and updates; installed apps may also lose licensing or purchase checks.",
            "https://support.google.com/googleplay/answer/113412",
            "com.android.vending", "com.aurora.store", "org.fdroid.fdroid", "app.morphe.manager")
        group(InternetAdvice.ALLOW_IF_USED,
            "This app uses online services for messages, email or calls.",
            "Blocking stops online messages, attachments, sync and internet calls. Carrier SMS and offline transports can behave differently. Keep access if you need timely communication.",
            "https://developer.android.com/develop/connectivity/network-ops/connecting",
            "org.thoughtcrime.securesms", "com.whatsapp", "org.telegram.messenger", "com.discord",
            "com.google.android.gm", "ch.protonmail.android", "net.thunderbird.android",
            "com.google.android.apps.messaging", "com.google.android.apps.dynamite",
            "com.google.android.apps.tachyon", "com.facebook.orca", "com.x.chat", "org.briarproject.briar.android")
        group(InternetAdvice.ALLOW_IF_USED,
            "Browsing websites requires network access.",
            "Blocking prevents pages and downloads from loading. Locally saved content may still be available.",
            "https://developer.android.com/develop/connectivity/network-ops/connecting",
            "com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "org.torproject.torbrowser")
        group(InternetAdvice.ALLOW_IF_USED,
            "This app retrieves online content or connects to a hosted service.",
            "Blocking stops online requests, feeds, streaming and uploads. Only previously downloaded or local content may work.",
            "https://developer.android.com/develop/connectivity/network-ops/connecting",
            "com.openai.chatgpt", "com.anthropic.claude", "ai.perplexity.app.android", "ai.x.grok",
            "ai.mistral.chat", "com.deepseek.chat", "com.google.android.apps.bard",
            "com.google.android.youtube", "com.google.android.apps.youtube.music",
            "app.morphe.android.youtube", "app.morphe.android.apps.youtube.music",
            "org.schabi.newpipe", "InfinityLoop1309.NewPipeEnhanced", "org.wisso.newpipematerial",
            "com.spotify.music", "com.soundcloud.android", "tv.twitch.android.app",
            "com.reddit.frontpage", "com.twitter.android", "xyz.blueskyweb.app", "com.facebook.katana",
            "com.instagram.android", "com.snapchat.android", "com.zhiliaoapp.musically",
            "com.github.android", "com.amazon.mShop.android.shopping", "com.rumble.battles",
            "de.danoeh.antennapod", "au.com.shiftyjelly.pocketcasts", "org.y20k.transistor")
        group(InternetAdvice.ALLOW_IF_USED,
            "This app supports online backup, synchronization or access to remote files.",
            "Blocking stops transfers and sync. Keep access if other devices or cloud backups need to stay up to date.",
            "https://developer.android.com/develop/connectivity/network-ops/connecting",
            "com.google.android.apps.docs", "com.google.android.apps.photos", "com.google.android.keep",
            "com.google.android.calendar", "com.google.android.apps.tasks", "md.obsidian",
            "com.github.catfriend1.syncthingfork", "com.x8bit.bitwarden", "com.vcast.mediamanager")
        group(InternetAdvice.ALLOW_IF_USED,
            "Online maps, weather or location searches need network access.",
            "Blocking stops live information and map downloads. Download offline maps first where supported.",
            "https://support.google.com/maps/answer/6291838",
            "com.google.android.apps.maps", "app.organicmaps", "app.comaps.fdroid", "net.osmand",
            "org.breezyweather", "com.weawow", "com.motorola.timeweatherwidget", "com.basevelocity.radarscope")
        group(InternetAdvice.ALLOW_IF_USED,
            "This tool accesses network hosts or transfers files.",
            "Blocking prevents remote sessions and transfers, including local-network connections depending on the backend.",
            "https://developer.android.com/develop/connectivity/network-ops/connecting",
            "com.server.auditor.ssh.client", "org.proninyaroslav.libretorrent", "com.delphicoder.flud.paid",
            "dev.bartuzen.qbitcontroller", "dev.acamol.qbit", "com.deniscerri.ytdl",
            "com.audiobookshelf.app", "org.videolan.vlc", "pl.solidexplorer2", "me.zhanghai.android.files")
        group(InternetAdvice.BLOCK_OPTIONAL,
            "The package's main function is a local calculator, clock or editing tool.",
            "Consider blocking if you only use local features. Online extras, downloads, sharing or sync may stop if the app offers them.",
            "https://developer.android.com/reference/android/Manifest.permission#INTERNET",
            "com.google.android.calculator", "com.android.calculator2", "com.google.android.deskclock", "com.android.deskclock",
            "com.niksoftware.snapseed", "net.gsantner.markor", "org.woheller69.exifedit", "si.jakobkreft.exifremove",
            "com.openrecorder.app", "org.oxycblt.auxio", "ru.tech.imageresizershrinker")
        group(InternetAdvice.ALLOW_IF_USED,
            "This keyboard can offer optional online voice, language or synchronization features.",
            "Consider blocking for local typing only; cloud dictation, downloaded languages and synchronization may stop.",
            "https://support.google.com/gboard/",
            "com.google.android.inputmethod.latin", "com.touchtype.swiftkey", "org.futo.inputmethod.latin",
            "org.futo.voiceinput", "helium314.keyboard", "com.android.inputmethod.latin")
        group(InternetAdvice.BLOCK_OPTIONAL,
            "This component provides optional feedback or diagnostic reporting.",
            "Consider blocking if you do not use reporting. Feedback uploads and remote troubleshooting may stop; this does not disable other apps' telemetry.",
            "https://help.motorola.com/hc/apps/",
            "com.motorola.help", "com.motorola.help.extlog", "com.motorola.bug2go",
            "com.motorola.wifi.motowifimetrics", "com.att.iqi", "com.tmobile.echolocate")
    }
}
