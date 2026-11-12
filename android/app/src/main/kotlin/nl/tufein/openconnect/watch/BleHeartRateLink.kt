package nl.tufein.openconnect.watch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import nl.tufein.openconnect.core.watch.HeartRateMeasurement
import java.util.UUID

/** The permissions Bluetooth needs on this Android version; the screen asks for them before scanning. */
fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

fun hasBluetoothPermissions(context: Context): Boolean =
    bluetoothPermissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * Heart rate over Bluetooth Low Energy with the standard Heart Rate Service (0x180D), which Garmin watches
 * offer when "broadcast heart rate" is on, and which chest straps always offer.
 *
 * Like [UsbWatchLink] this needs a real phone and sensor to exercise; the parsing of what arrives is tested
 * in [HeartRateMeasurement.parse]. Permission checks happen in the screen before any of this is called, and
 * a missing permission still ends as an error message instead of a crash.
 */
@SuppressLint("MissingPermission")
class BleHeartRateLink(private val context: Context) : HeartRateLink {
    private companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val adapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override fun scan(onFound: (BleSensor) -> Unit, onError: (String) -> Unit): AutoCloseable {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            onError("Bluetooth is off or not available")
            return AutoCloseable { }
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = runCatching { result.device.name }.getOrNull() ?: result.scanRecord?.deviceName ?: result.device.address
                onFound(BleSensor(result.device.address, name))
            }

            override fun onScanFailed(errorCode: Int) = onError("Bluetooth scan failed (code $errorCode)")
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(filters, settings, callback)
        } catch (e: SecurityException) {
            onError("Bluetooth permission is missing")
            return AutoCloseable { }
        }
        return AutoCloseable { runCatching { scanner.stopScan(callback) } }
    }

    override fun connect(sensor: BleSensor, listener: HeartRateListener): AutoCloseable {
        val device: BluetoothDevice? = try {
            adapter?.getRemoteDevice(sensor.address)
        } catch (e: IllegalArgumentException) {
            null
        }
        if (device == null) {
            listener.onDisconnected("Bluetooth is not available")
            return AutoCloseable { }
        }
        var closedOnPurpose = false
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        gatt.close()
                        listener.onDisconnected(if (closedOnPurpose) null else "Connection lost (status $status)")
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val characteristic = gatt.getService(HEART_RATE_SERVICE)?.getCharacteristic(HEART_RATE_MEASUREMENT)
                if (characteristic == null) {
                    listener.onDisconnected("This device does not offer heart rate")
                    gatt.disconnect()
                    return
                }
                gatt.setCharacteristicNotification(characteristic, true)
                val config = characteristic.getDescriptor(CLIENT_CONFIG)
                if (config == null) {
                    listener.onDisconnected("This device cannot send heart rate notifications")
                    gatt.disconnect()
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(config, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    config.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(config)
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) listener.onConnected()
            }

            // Android 13 and newer deliver the value as an argument ...
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                HeartRateMeasurement.parse(value)?.let(listener::onMeasurement)
            }

            // ... older versions through the deprecated callback and the characteristic's own value.
            @Deprecated("Used on Android 12 and older")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    characteristic.value?.let { HeartRateMeasurement.parse(it)?.let(listener::onMeasurement) }
                }
            }
        }
        val gatt = try {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            listener.onDisconnected("Bluetooth permission is missing")
            return AutoCloseable { }
        }
        return AutoCloseable {
            closedOnPurpose = true
            runCatching { gatt.disconnect() }
        }
    }
}
