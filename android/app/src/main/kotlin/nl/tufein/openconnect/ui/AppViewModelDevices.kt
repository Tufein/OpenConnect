package nl.tufein.openconnect.ui

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import nl.tufein.openconnect.R
import nl.tufein.openconnect.core.reports.AiVendor
import nl.tufein.openconnect.watch.BleSensor
import nl.tufein.openconnect.watch.GarminBleWatch
import nl.tufein.openconnect.watch.WatchInfo

// What the screens ask of the watch, Health Connect, notification forwarding, the Garmin account, the Oura Ring and the
// AI keys. Each is a thin call into the controller in AppContainer; they moved here from AppViewModel.kt in v1.14.0
// as extension functions, so the screens call them exactly as before and nothing in them changed.

fun AppViewModel.saveAiKey(vendor: AiVendor, pasted: String) = container.ai.saveKey(vendor, pasted)

fun AppViewModel.checkAiKey(vendor: AiVendor) = container.ai.checkKey(vendor)

fun AppViewModel.forgetAiKey(vendor: AiVendor) = container.ai.forgetKey(vendor)

fun AppViewModel.setAiModel(vendor: AiVendor, model: String) = container.ai.setModel(vendor, model)

fun AppViewModel.setAiChoice(choice: String) = container.ai.setChoice(choice)

fun AppViewModel.setAiLanguage(language: String) = container.ai.setLanguage(language)

fun AppViewModel.askCoach(question: String) = container.coach.ask(question)

fun AppViewModel.clearCoach() = container.coach.clear()

fun AppViewModel.refreshWatches() = container.watch.refreshUsb()

fun AppViewModel.readWatch(info: WatchInfo) = container.watch.syncUsb(info)

/** [tree] is the address the folder picker returned. */
fun AppViewModel.readFolder(tree: Uri) = container.watch.syncFolder(tree.toString())

fun AppViewModel.scanSensors() = container.watch.startScan()

fun AppViewModel.connectSensor(sensor: BleSensor) = container.watch.connect(sensor)

/** Turns the automatic live-heart-rate start on or off; the switch is on the Watch screen. */
fun AppViewModel.setAutoLive(on: Boolean) = container.watch.setAutoLive(on)

fun AppViewModel.stopLive() = container.watch.stopLive()

fun AppViewModel.findGarmin() = container.watch.findGarmin()

fun AppViewModel.stopFindGarmin() = container.watch.stopFindGarmin()

fun AppViewModel.syncGarmin(watch: GarminBleWatch) = container.watch.syncGarmin(watch)

fun AppViewModel.forgetGarmin() = container.watch.forgetGarmin()

fun AppViewModel.refreshHealth() = container.health.refresh()

fun AppViewModel.exportHealth() = container.health.exportNow()

fun AppViewModel.importHealth() = container.health.importNow()

fun AppViewModel.setHealthAuto(on: Boolean) = container.health.setAuto(on)

fun AppViewModel.setHealthAutoImport(on: Boolean) = container.health.setAutoImport(on)

fun AppViewModel.setGarminArchive(on: Boolean) = container.watch.setGarminArchive(on)

fun AppViewModel.setGarminBackground(on: Boolean) = container.watch.setGarminBackground(on)

/** Where forwarding stands, the person's choices and what was sent; lives in [AppContainer.forwarding]. */
val AppViewModel.forward: StateFlow<nl.tufein.openconnect.notify.ForwardUi> get() = container.forwarding.state

/** What forwarding did, line by line, for the "What is happening?" part of the card. */
val AppViewModel.forwardLog: StateFlow<List<nl.tufein.openconnect.notify.ForwardTrail.Line>> get() = container.forwardTrail.flow

/** Looks at what is still needed (access, a watch, Bluetooth) and connects when it is all there. */
fun AppViewModel.refreshForward() = container.forwarding.refresh()

fun AppViewModel.setForward(on: Boolean) = container.forwarding.setEnabled(on)

fun AppViewModel.forwardReconnect() = container.forwarding.reconnect()

fun AppViewModel.setForwardHide(on: Boolean) = container.forwarding.setHideContent(on)

fun AppViewModel.setForwardScreenOff(on: Boolean) = container.forwarding.setOnlyWhenScreenOff(on)

fun AppViewModel.setForwardApp(packageName: String, on: Boolean) = container.forwarding.setApp(packageName, on)

/** Sends a test notification to the watch and says what happened. */
fun AppViewModel.testForward() {
    val result = container.forwarding.sendTest(text(R.string.forward_test_title), text(R.string.forward_test_body))
    tell(
        text(
            when (result) {
                nl.tufein.openconnect.notify.TestResult.SENT -> R.string.forward_test_sent
                nl.tufein.openconnect.notify.TestResult.NOT_CONNECTED -> R.string.forward_test_not_connected
                nl.tufein.openconnect.notify.TestResult.WATCH_OFF -> R.string.forward_test_watch_off
                nl.tufein.openconnect.notify.TestResult.WATCH_NOT_ASKING -> R.string.forward_test_not_asking
            },
        ),
    )
}

fun AppViewModel.saveGarminKey(pasted: String) = container.garmin.saveKey(pasted)

fun AppViewModel.forgetGarminKey() = container.garmin.forgetKey()

fun AppViewModel.setGarminDays(days: Int) = container.garmin.setDays(days)

fun AppViewModel.setGarminAutoSync(on: Boolean) = container.garmin.setAutoSync(on)

fun AppViewModel.syncGarmin() = container.garmin.sync()

val AppViewModel.oura: StateFlow<nl.tufein.openconnect.oura.OuraUi> get() = container.oura.state

fun AppViewModel.saveOuraClient(id: String, secret: String, redirect: String) = container.oura.saveClient(id, secret, redirect)

fun AppViewModel.ouraLoginUrl(): String? = container.oura.loginUrl()

fun AppViewModel.finishOuraLogin(redirect: String) = container.oura.finishLogin(redirect)

fun AppViewModel.syncOura() = container.oura.sync()

fun AppViewModel.disconnectOura() = container.oura.disconnect()

fun AppViewModel.forgetOura() = container.oura.forget()

fun AppViewModel.cancelGarmin() = container.garmin.cancel()
