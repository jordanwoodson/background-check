package dev.bgcheck.app.data

enum class DescriptionSource(val label: String) {
    GUIDE("Built-in package guide"), APP("Provided by the app"),
    CATEGORY("Android app category"), UNKNOWN("Purpose unavailable")
}

data class PackageDescription(
    val text: String,
    val source: DescriptionSource,
    val citations: List<DescriptionCitation> = emptyList(),
    val note: String? = null,
)

object PackageDescriptions {
    // Match exact IDs only. A vendor prefix or a familiar app label does not establish purpose.
    // Sources and maintenance notes: docs/package-descriptions.md.
    private val guide = buildMap {
        fun describe(text: String, vararg packages: String) = packages.forEach { put(it, text) }
        describe("Provides Android's core framework and shared system resources.", "android")
        describe("Displays notifications, Quick Settings, the status bar and system navigation.", "com.android.systemui")
        describe("Lets you configure the phone, connected devices, apps and Android preferences.", "com.android.settings")
        describe("Stores Android system preferences used by Settings and other components.", "com.android.providers.settings")
        describe("Stores contacts and call history for apps with permission to access them.", "com.android.providers.contacts")
        describe("Stores calendars and events for apps with calendar access.", "com.android.providers.calendar")
        describe("Stores SMS, MMS and mobile-network configuration data.", "com.android.providers.telephony")
        describe("Indexes photos, videos and audio so apps can find and access media.", "com.android.providers.media", "com.android.providers.media.module", "com.google.android.providers.media.module")
        describe("Manages file downloads requested by apps and keeps download records.", "com.android.providers.downloads")
        describe("Displays files downloaded through Android's download manager.", "com.android.providers.downloads.ui")
        describe("Provides the system file picker for opening and saving documents.", "com.android.documentsui", "com.google.android.documentsui")
        describe("Exposes shared device storage to Android's file picker.", "com.android.externalstorage")
        describe("Handles app permission prompts and permission management.", "com.android.permissioncontroller", "com.google.android.permissioncontroller")
        describe("Handles installation and removal of Android app packages.", "com.android.packageinstaller", "com.google.android.packageinstaller")
        describe("Manages cellular phone services and SIM-related functions.", "com.android.phone")
        describe("Coordinates phone calls between calling apps and Android.", "com.android.server.telecom")
        describe("Displays the dial pad, recent calls and calling controls.", "com.android.dialer", "com.google.android.dialer")
        describe("Lets you view and edit your address book.", "com.android.contacts", "com.google.android.contacts")
        describe("Sends and receives text messages and displays conversations.", "com.android.messaging", "com.google.android.apps.messaging")
        describe("Shows services and menus supplied by your SIM card or carrier.", "com.android.stk", "com.android.stk2")
        describe("Supplies carrier-specific settings for mobile-network features.", "com.android.carrierconfig")
        describe("Displays emergency alerts broadcast by mobile networks.", "com.android.cellbroadcastreceiver", "com.google.android.cellbroadcastreceiver")
        describe("Receives and processes cell-broadcast messages, including emergency alerts.", "com.android.cellbroadcastservice", "com.google.android.cellbroadcastservice")
        describe("Supports Bluetooth connections to headphones, cars and other devices.", "com.android.bluetooth")
        describe("Handles NFC communication with nearby tags, readers and devices.", "com.android.nfc")
        describe("Checks network connectivity and supports Android's network connections.", "com.android.networkstack", "com.google.android.networkstack")
        describe("Defines the system permissions required by Android's network stack.", "com.android.networkstack.permissionconfig", "com.google.android.networkstack.permissionconfig")
        describe("Displays sign-in pages for Wi-Fi networks that require a login.", "com.android.captiveportallogin", "com.google.android.captiveportallogin")
        describe("Provides Android's VPN connection confirmation and status screens.", "com.android.vpndialogs")
        describe("Manages print jobs sent from apps to printers or print services.", "com.android.printspooler")
        describe("Finds compatible network printers and sends documents to them.", "com.android.bips")
        describe("Stores and manages security certificates and credentials used by apps.", "com.android.keychain")
        describe("Provides Android's command-line shell used by ADB and device tools.", "com.android.shell")
        describe("Shows the home screen, app drawer and app-launching controls.", "com.android.launcher3", "com.google.android.apps.nexuslauncher", "com.motorola.launcher3")
        describe("Provides an on-screen keyboard for entering text.", "com.android.inputmethod.latin", "com.google.android.inputmethod.latin")
        describe("Shows alarms, timers, a stopwatch and clocks.", "com.android.deskclock", "com.google.android.deskclock")
        describe("Performs calculations with a calculator interface.", "com.android.calculator2", "com.google.android.calculator")
        describe("Displays calendars and lets you manage events.", "com.android.calendar", "com.google.android.calendar")
        describe("Takes photos and records videos using the device cameras.", "com.android.camera2", "com.google.android.GoogleCamera", "com.motorola.camera2", "com.motorola.camera3")
        describe("Lets you choose the wallpaper for your home and lock screens.", "com.android.wallpaperpicker", "com.android.wallpaperpicker2", "com.google.android.apps.wallpaper")
        describe("Completes Android device provisioning and records that initial setup is finished.", "com.android.provision")
        describe("Guides you through initial device setup and account configuration.", "com.google.android.setupwizard")
        describe("Sets up managed work profiles and organization-managed devices.", "com.android.managedprovisioning")
        describe("Provides emergency information and emergency assistance features.", "com.android.emergency")
        describe("Provides web-page rendering inside apps that use Android WebView.", "com.android.webview", "com.google.android.webview")
        describe("Supplies shared Google services such as account sign-in, location and push notifications.", "com.google.android.gms")
        describe("Finds, installs and updates apps through Google Play.", "com.android.vending")
        describe("Browses websites and manages browser tabs and bookmarks.", "com.android.chrome", "org.mozilla.firefox", "com.brave.browser")
        describe("Searches the web and provides Google's search and voice features.", "com.google.android.googlequicksearchbox")
        describe("Sends, receives and organizes email in Gmail.", "com.google.android.gm")
        describe("Shows maps, nearby places, directions and navigation.", "com.google.android.apps.maps")
        describe("Displays, organizes and backs up photos and videos.", "com.google.android.apps.photos")
        describe("Stores, syncs and shares files through Google Drive.", "com.google.android.apps.docs")
        describe("Plays and shares videos from YouTube.", "com.google.android.youtube")
        describe("Streams music and manages a music library in YouTube Music.", "com.google.android.apps.youtube.music")
        describe("Browses and manages files stored on the device.", "com.google.android.apps.nbu.files")
        describe("Creates notes, lists and reminders in Google Keep.", "com.google.android.keep")
        describe("Provides screen reading and other accessibility tools.", "com.google.android.marvin.talkback")
        describe("Provides speech recognition and text-to-speech capabilities for apps.", "com.google.android.tts")
        describe("Shows app usage and provides focus and screen-time controls.", "com.google.android.apps.wellbeing")
        describe("Manages and shares health and fitness data between authorized apps.", "com.google.android.apps.healthdata", "com.google.android.healthconnect.controller", "com.android.healthconnect.controller")
        describe("Provides Motorola customization, gestures and device feature settings.", "com.motorola.moto")
        describe("Enables Motorola gesture shortcuts, such as motions for the flashlight or camera.", "com.motorola.actions")
        describe("Groups Motorola security and privacy controls, including network protection and secure folders.", "com.motorola.securityhub")
        describe("Checks app activity, battery use and background settings on this device.", "dev.bgcheck.app")
        describe("Lets authorized apps use Android system APIs through an ADB or root service.", "moe.shizuku.privileged.api")
        describe("Connects this device to your private Tailscale network.", "com.tailscale.ipn")
        describe("Runs a terminal and Linux command-line tools on Android.", "com.termux")
        describe("Provides remote access to this Android device.", "sh.haven.app")
        describe("Checks for app updates and installs them directly from supported release sources.", "dev.imranr.obtainium")
        describe("Finds, installs and updates free and open-source Android apps.", "org.fdroid.fdroid")
        describe("Provides conversations with ChatGPT for questions, writing and other tasks.", "com.openai.chatgpt")
        describe("Sends encrypted messages and supports voice and video calls in Signal.", "org.thoughtcrime.securesms")
        describe("Sends messages and supports voice and video calls in WhatsApp.", "com.whatsapp")
        describe("Sends messages and supports groups, channels and calls in Telegram.", "org.telegram.messenger")
        describe("Stores and fills passwords and other saved credentials.", "com.x8bit.bitwarden")
    }

    private val categories = mapOf(
        0 to "games", 1 to "music and audio", 2 to "video", 3 to "photos and images",
        4 to "social communication", 5 to "news", 6 to "maps and navigation",
        7 to "productivity", 8 to "accessibility",
    )
    private val whitespace = Regex("\\s+")

    fun describe(packageName: String, appDescription: String? = null, category: Int = -1): PackageDescription {
        val audited = AuditedPackageGuide.packages[packageName]
        (audited?.description ?: guide[packageName])?.let {
            return PackageDescription(it, DescriptionSource.GUIDE, audited?.sources.orEmpty(), audited?.note)
        }
        appDescription?.replace(whitespace, " ")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return PackageDescription(it, DescriptionSource.APP)
        }
        categories[category]?.let {
            return PackageDescription("Android lists this app under $it. Its exact purpose is not documented here.", DescriptionSource.CATEGORY)
        }
        return PackageDescription("No description is available for this package. Its purpose has not been identified.", DescriptionSource.UNKNOWN)
    }

    fun label(packageName: String, appLabel: String): String =
        if (appLabel.isBlank() || appLabel == packageName) AuditedPackageGuide.packages[packageName]?.label ?: packageName else appLabel

    fun associations(packageName: String): List<PackageAssociation> =
        AuditedPackageGuide.packages[packageName]?.associations.orEmpty()
}

enum class PackageType(val label: String) {
    USER("Your apps"), ALL("All apps"), SYSTEM("System"), UNINSTALLED("Uninstalled")
}

fun matchesPackage(app: AppEntry, type: PackageType, query: String): Boolean {
    val matchesType = when (type) {
        PackageType.USER -> app.installed && !app.system
        PackageType.SYSTEM -> app.installed && app.system
        PackageType.UNINSTALLED -> !app.installed
        PackageType.ALL -> true
    }
    val needle = query.trim()
    return matchesType && (needle.isEmpty() || app.label.contains(needle, true) ||
        app.packageName.contains(needle, true) || app.description.text.contains(needle, true))
}
