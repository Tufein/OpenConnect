// OpenConnect, SPDX-License-Identifier: AGPL-3.0-or-later
package nl.tufein.openconnect.watch

import android.util.SparseArray
import nl.tufein.openconnect.core.watch.GarminDevices

/**
 * One result of the Bluetooth scan, or one device Android has paired, as an entry for the list of Garmin devices to sync;
 * null when it is not a Garmin device worth offering. Which Garmin it is, all families, comes from [GarminDevices]: the
 * product number in Garmin's manufacturer data first, then the name. Heart rate straps, lights and scales are Garmin's
 * too but there is nothing to sync from them, so they stay out of the list.
 *
 * A name the watch advertised and that we recognise is shown as it is ("fenix 8 - 47mm", the size can matter to the owner of
 * two); a watch with no name, or one that someone renamed, is shown under the name of its model.
 */
internal fun garminCandidate(address: String, name: String?, manufacturerData: SparseArray<ByteArray>?, paired: Boolean): GarminBleWatch? {
    val device = GarminDevices.fromAdvertisement(name, manufacturerData.toMap()) ?: return null
    if (!device.offerForSync) return null
    val advertised = name?.trim()?.takeIf { it.isNotEmpty() && GarminDevices.fromName(it) != null }
    return GarminBleWatch(address, advertised ?: device.displayName, paired, device.bleSync)
}

private fun SparseArray<ByteArray>?.toMap(): Map<Int, ByteArray> {
    if (this == null) return emptyMap()
    val map = HashMap<Int, ByteArray>(size())
    for (i in 0 until size()) map[keyAt(i)] = valueAt(i)
    return map
}
