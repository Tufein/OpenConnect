package nl.tufein.openconnect.watch

/** A phone with a scriptable USB port. */
class FakeUsb(var watches: List<WatchInfo> = emptyList(), val open: suspend (WatchInfo) -> OpenedWatch) : WatchLink {
    override fun attached() = watches

    override suspend fun open(info: WatchInfo) = open.invoke(info)
}

/** A phone with a scriptable Bluetooth radio: the test plays the sensor. */
class FakeBle : HeartRateLink {
    @Volatile var onFound: ((BleSensor) -> Unit)? = null

    @Volatile var listener: HeartRateListener? = null

    @Volatile var scanning = false

    @Volatile var connected: BleSensor? = null

    override fun scan(onFound: (BleSensor) -> Unit, onError: (String) -> Unit): AutoCloseable {
        this.onFound = onFound
        scanning = true
        return AutoCloseable { scanning = false }
    }

    override fun connect(sensor: BleSensor, listener: HeartRateListener): AutoCloseable {
        this.listener = listener
        connected = sensor
        return AutoCloseable { connected = null }
    }
}
