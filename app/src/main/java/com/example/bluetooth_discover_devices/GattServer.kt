package com.example.bluetooth_discover_devices

import android.bluetooth.*
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import timber.log.Timber
import java.util.*

class GattServer(val context: Context) {
    companion object {
        /* Current Time Service UUID */
        var time_service = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
        /* Mandatory Current Time Information Characteristic */
        var current_time = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    }

    var bluetoothManager: BluetoothManager
    lateinit var bluetoothGattServer: BluetoothGattServer

    init {
        bluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
    }

    fun createGattServer() {
        // create a service
        var service = BluetoothGattService(time_service, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        // create a characteristic
        var characteristic = BluetoothGattCharacteristic(current_time,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ)
        service.addCharacteristic(characteristic)
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallBack)
        bluetoothGattServer.addService(service)
    }

    val gattServerCallBack = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Timber.d("BluetoothDevice CONNECTED: ${device?.name}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Timber.d("BluetoothDevice DISCONNECTED: ${device?.name}")
            }
            super.onConnectionStateChange(device, status, newState)
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, "response".toByteArray())
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
        }
    }

    fun destroy() {
        bluetoothGattServer.close()
    }
}