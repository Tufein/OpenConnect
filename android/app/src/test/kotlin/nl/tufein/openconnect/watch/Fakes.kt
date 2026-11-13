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

    /** How many times the controller asked for a connection, retries included. */
    @Volatile var connectCalls = 0

    /** Every listener it was given, oldest first: a test can play a late answer of an old connection. */
    val listeners = java.util.concurrent.CopyOnWriteArrayList<HeartRateListener>()

    override fun scan(onFound: (BleSensor) -> Unit, onError: (String) -> Unit): AutoCloseable {
        this.onFound = onFound
        scanning = true
        return AutoCloseable { scanning = false }
    }

    override fun connect(sensor: BleSensor, listener: HeartRateListener): AutoCloseable {
        this.listener = listener
        listeners.add(listener)
        connectCalls++
        connected = sensor
        return AutoCloseable { connected = null }
    }
}

/** A phone with scriptable Garmin Bluetooth: the test decides what is found and what opening a watch does. */
class FakeGarminBle(
    var nearby: List<GarminBleWatch> = emptyList(),
    var findError: String? = null,
    var open: suspend (GarminBleWatch, (GarminStep) -> Unit) -> OpenedWatch,
) : GarminBleLink {
    @Volatile var searching = false
    val opened = java.util.concurrent.CopyOnWriteArrayList<GarminBleWatch>()

    override fun find(onFound: (GarminBleWatch) -> Unit, onError: (String) -> Unit): AutoCloseable {
        findError?.let {
            onError(it)
            return AutoCloseable { }
        }
        searching = true
        nearby.forEach(onFound)
        return AutoCloseable { searching = false }
    }

    override suspend fun open(watch: GarminBleWatch, onStep: (GarminStep) -> Unit): OpenedWatch {
        opened += watch
        return open.invoke(watch, onStep)
    }
}
