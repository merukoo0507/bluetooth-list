package com.example.bluetooth_discover_devices

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseData
import android.os.ParcelUuid
import timber.log.Timber
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.pow


object BleUtil {
    private val TAG = BleUtil::class.java.simpleName
    fun parseAdertisedData(advertisedData: ByteArray?): BleAdvertisedData {
        val uuids: MutableList<UUID> = ArrayList<UUID>()
        var name: String? = null
        if (advertisedData == null) {
            return BleAdvertisedData(uuids, name)
        }
        val buffer: ByteBuffer = ByteBuffer.wrap(advertisedData).order(ByteOrder.LITTLE_ENDIAN)
        while (buffer.remaining() > 2) {
            var length: Int = buffer.get().toInt()
            if (length == 0) break
            when (buffer.get()) {
                0x02.toByte() , 0x03.toByte() -> while (length >= 2) {
                    uuids.add(
                        UUID.fromString(
                            java.lang.String.format(
                                "%08x-0000-1000-8000-00805f9b34fb", buffer.short
                            )
                        )
                    )
                    length -= 2
                }
                0x06.toByte(), 0x07.toByte() -> while (length >= 16) {
                    val lsb: Long = buffer.long
                    val msb: Long = buffer.long
                    uuids.add(UUID(msb, lsb))
                    length -= 16
                }
                0x09.toByte() -> {
                    val nameBytes = ByteArray(length - 1)
                    buffer.get(nameBytes)
                    try {
                        name = nameBytes.toString(Charset.forName("utf-8"))
                    } catch (e: UnsupportedEncodingException) {
                        e.printStackTrace()
                    }
                }
                else -> buffer.position(buffer.position() + length - 1)
            }
        }
        return BleAdvertisedData(uuids, name)
    }


    const val OVERHEAD_BYTES_PER_FIELD = 2
    // Flags field will be set by system.
    const val FLAGS_FIELD_BYTES = 3
    const val MANUFACTURER_SPECIFIC_DATA_LENGTH = 2
    const val UUID_BYTES_16_BIT = 2
    const val UUID_BYTES_32_BIT = 4
    const val UUID_BYTES_128_BIT = 16
    val BASE_UUID = ParcelUuid.fromString("00000000-0000-1000-8000-00805F9B34FB")

    // Compute the size of advertisement data or scan resp
    fun totalBytes(data: AdvertiseData?, isFlagsIncluded: Boolean, bluetoothAdapter: BluetoothAdapter): Int {
        if (data == null) return 0
        // Flags field is omitted if the advertising is not connectable.
        var size = if (isFlagsIncluded) FLAGS_FIELD_BYTES else 0
        if (data.serviceUuids != null) {
            var num16BitUuids = 0
            var num32BitUuids = 0
            var num128BitUuids = 0
            for (uuid in data.serviceUuids) {
                if (is16BitUuid(uuid)) {
                    ++num16BitUuids
                } else if (is32BitUuid(uuid)) {
                    ++num32BitUuids
                } else {
                    ++num128BitUuids
                }
            }
            // 16 bit service uuids are grouped into one field when doing advertising.
            if (num16BitUuids != 0) {
                size += OVERHEAD_BYTES_PER_FIELD + num16BitUuids * UUID_BYTES_16_BIT
            }
            // 32 bit service uuids are grouped into one field when doing advertising.
            if (num32BitUuids != 0) {
                size += OVERHEAD_BYTES_PER_FIELD + num32BitUuids * UUID_BYTES_32_BIT
            }
            // 128 bit service uuids are grouped into one field when doing advertising.
            if (num128BitUuids != 0) {
                size += (OVERHEAD_BYTES_PER_FIELD
                        + num128BitUuids * UUID_BYTES_128_BIT)
            }
        }
        for (uuid in data.serviceData.keys) {
            val uuidLen: Int = uuidToBytes(uuid).size
            size += (OVERHEAD_BYTES_PER_FIELD + uuidLen
                    + byteLength(data.serviceData[uuid]))
        }
        for (i in 0 until data.manufacturerSpecificData.size()) {
            size += (OVERHEAD_BYTES_PER_FIELD + MANUFACTURER_SPECIFIC_DATA_LENGTH
                    + byteLength(data.manufacturerSpecificData.valueAt(i)))
        }
        if (data.includeTxPowerLevel) {
            size += OVERHEAD_BYTES_PER_FIELD + 1 // tx power level value is one byte.
        }
        Timber.d("n:${bluetoothAdapter.name}, l: ${bluetoothAdapter.name.length}")
        if (data.includeDeviceName && bluetoothAdapter.getName() != null) {
            size += OVERHEAD_BYTES_PER_FIELD + bluetoothAdapter.getName().length
        }
        return size
    }

    fun byteLength(array: ByteArray?): Int {
        return array?.size ?: 0
    }

    fun uuidToBytes(uuid: ParcelUuid?): ByteArray {
        requireNotNull(uuid) { "uuid cannot be null" }
        if (is16BitUuid(uuid)) {
            val uuidBytes = ByteArray(UUID_BYTES_16_BIT)
            val uuidVal: Int = getServiceIdentifierFromParcelUuid(uuid)
            uuidBytes[0] = (uuidVal and 0xFF).toByte()
            uuidBytes[1] = (uuidVal and 0xFF00 shr 8).toByte()
            return uuidBytes
        }
        if (is32BitUuid(uuid)) {
            val uuidBytes = ByteArray(UUID_BYTES_32_BIT)
            val uuidVal: Int = getServiceIdentifierFromParcelUuid(uuid)
            uuidBytes[0] = (uuidVal and 0xFF).toByte()
            uuidBytes[1] = (uuidVal and 0xFF00 shr 8).toByte()
            uuidBytes[2] = (uuidVal and 0xFF0000 shr 16).toByte()
            uuidBytes[3] = (uuidVal and -0x1000000 shr 24).toByte()
            return uuidBytes
        }
        // Construct a 128 bit UUID.
        val msb = uuid.uuid.mostSignificantBits
        val lsb = uuid.uuid.leastSignificantBits
        val uuidBytes = ByteArray(UUID_BYTES_128_BIT)
        val buf =
            ByteBuffer.wrap(uuidBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.putLong(8, msb)
        buf.putLong(0, lsb)
        return uuidBytes
    }

    private fun getServiceIdentifierFromParcelUuid(parcelUuid: ParcelUuid): Int {
        val uuid = parcelUuid.uuid
        val value = uuid.mostSignificantBits and -0x100000000L ushr 32
        return value.toInt()
    }

    fun is16BitUuid(parcelUuid: ParcelUuid): Boolean {
        val uuid = parcelUuid.uuid
        return if (uuid.leastSignificantBits !== BASE_UUID.uuid.leastSignificantBits) {
            false
        } else {
            uuid.mostSignificantBits and -0xffff00000001L === 0x1000L
        }
    }

    fun is32BitUuid(parcelUuid: ParcelUuid): Boolean {
        val uuid = parcelUuid.uuid
        if (uuid.leastSignificantBits !== BASE_UUID.uuid.leastSignificantBits) {
            return false
        }
        return if (is16BitUuid(parcelUuid)) {
            false
        } else uuid.mostSignificantBits and 0xFFFFFFFFL === 0x1000L
    }

    fun calculateDistance(rssi: Int): Double {
        var txPower = -60 //hard coded power value. Usually ranges between -59 to -65

        if (rssi == 0) {
            return -1.0 // if we cannot determine distance, return -1.
        }
        var ratio = rssi * 1.0 / txPower

        return if (ratio < 1.0) {
            ratio.pow(10)
        } else {
            (0.89976) * ratio.pow(7.7095) + 0.111
        }
    }
}

class BleAdvertisedData(
    uuids: List<UUID>,
    name: String?
) {
    private val mUuids: List<UUID> = uuids
    val name: String? = name
    val uuids: List<Any>
        get() = mUuids
}

data class BtDevice(
    var device: BluetoothDevice,
    var uuid: MutableList<ParcelUuid>?,
    var rssi: Int
)