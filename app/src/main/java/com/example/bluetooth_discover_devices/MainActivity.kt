package com.example.bluetooth_discover_devices

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.lang.reflect.Method
import java.math.BigInteger
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    //Bluetooth
    lateinit var bluetoothAdapter: BluetoothAdapter
    lateinit var deviceListAdapter: DeviceListAdapter
    var advertiser: BluetoothLeAdvertiser? = null
    var gattServer: GattServer? = null

    //Data
    var enableBt = MutableLiveData(false)
    var scanDevices = ArrayList<BtDevice>()
    var btDevices = ArrayList<BtDevice>()
    var btNames = mutableListOf<String>()

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Timber.plant(Timber.DebugTree())
        checkBTPermissions()

        //利用getPackageManager().hasSystemFeature()檢查手機是否支援BLE設備，否則利用finish()關閉程式
        if(!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            Toast.makeText(baseContext, "no sup bluetooth le",Toast.LENGTH_SHORT).show()
            finish()
        }
        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter

        //如果裝置版本<=android6，將16bit的deviceId轉64bit，當作藍芽名稱
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            var bytes = BigInteger("5ae9afe975413f4006334a16",16).toByteArray()
            var encode = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Timber.d("bytes(Base64) -> $encode")
            bluetoothAdapter.name = encode
        }
        Timber.d("self: ${bluetoothAdapter.name}")

        deviceListAdapter = DeviceListAdapter(btDevices)
        recyclerView.adapter = deviceListAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        enableBt.value = bluetoothAdapter.isEnabled
        enableBt.observe(this, androidx.lifecycle.Observer {
            if (it) {
                bt_status.text = "Bluetooth: on"
            } else {
                bt_status.text = "Bluetooth: off"
            }
            start_scan_btn.isClickable = it
            start_discover_btn.isClickable = it
            advertise_btn.isClickable = it
            discover_btn.isClickable = it
        })

        //打開/關閉藍芽
        turn_btn.setOnClickListener {
            enableBt.value = !bluetoothAdapter.isEnabled
            if (bluetoothAdapter.isEnabled) {
                bluetoothAdapter.disable()
            } else {
                bluetoothAdapter.enable()
            }
        }

        //開始Scan藍芽裝置
        start_scan_btn.setOnClickListener {
            scanDevices.clear()
            btNames.clear()
            deviceListAdapter = DeviceListAdapter(scanDevices)
            recyclerView.adapter = deviceListAdapter
            scan()
        }

        //藍芽開始搜尋附近裝置，需註冊receiver
        start_discover_btn.setOnClickListener {
            if (bluetoothAdapter.isEnabled) {
                btDevices.clear()
                btNames.clear()
                deviceListAdapter = DeviceListAdapter(btDevices)
                recyclerView.adapter = deviceListAdapter
                bluetoothAdapter.startDiscovery()
                bt_status.text = "Start discovery."
            }
        }

        //廣告藍芽資訊
        advertise_btn.setOnClickListener {
            Timber.d("Is Support MultipleAdvertise: ${bluetoothAdapter.isMultipleAdvertisementSupported}")
            if(bluetoothAdapter.isMultipleAdvertisementSupported) {
                advertise()
            }
        }

        //藍芽裝置開始被搜尋
        discover_btn.setOnClickListener {
            val method: Method
            try {
                method = bluetoothAdapter.javaClass.getMethod(
                    "setScanMode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                method.invoke(
                    bluetoothAdapter,
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,
                    120
                )
                Timber.d("invoke method invoke successfully")
            } catch (e: Exception) {
                Timber.d("discoverable exception: ${e.printStackTrace()}")
                val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                    putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                }
                discoverableIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                this.applicationContext.startActivity(discoverableIntent)
            }
        }

        var intent = IntentFilter(ACTION_FOUND)
        registerReceiver(receiver, intent)
        intent = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        registerReceiver(receiver, intent)
        intent = IntentFilter(ACTION_UUID)
        registerReceiver(receiver, intent)

//        gattServer = GattServer(this)
//        gattServer!!.createGattServer()
    }

    private fun scan() {
        //先不設定scan藍芽的filter
//        var builder = ScanFilter.Builder()
//        builder.setServiceUuid(
//            ParcelUuid(UUID.fromString("A5ae9afe-9754-13f4-0063-34aff514E415")),
//            ParcelUuid(UUID.fromString("00000000-0000-0000-0000-00000FFFFFFF"))
//        )
//        var filter = Vector<ScanFilter>()
//        filter.add(builder.build())

        var builderScanSettings = ScanSettings.Builder()
        builderScanSettings.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        builderScanSettings.setReportDelay(0)

        var bluetoothScanner = bluetoothAdapter.bluetoothLeScanner
        bluetoothScanner.startScan(null, builderScanSettings.build(), object: ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                Timber.d("error: $errorCode")
            }

            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result!!.device.name!=null && result!!.device.name.isNotEmpty() && !btNames.contains("${result!!.device.name}")) {
                    Timber.d("onScanResult: name:${result!!.device.name}, " +
                            "uuid:${result!!.scanRecord!!.serviceUuids}, " +
                            "addr:${result!!.device.address}, " +
                            "rssi:${result!!.rssi}," +
                            "type:${result!!.device.type}")
                    scanDevices.add(BtDevice(result!!.device, result!!.scanRecord!!.serviceUuids, result!!.rssi))
                    deviceListAdapter = DeviceListAdapter(scanDevices)
                    recyclerView.adapter = deviceListAdapter
                    btNames.add("${result!!.device.name}")
                }
            }
        })
    }

    private fun advertise() {
        advertiser =
            bluetoothAdapter.bluetoothLeAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        //Qnap的ASCII = 514e4150
        val pUuid = ParcelUuid(UUID.fromString("514e4150-0000-1000-8000-00805f9b34fb"))
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(pUuid)
            .build()

        //計算AdvertiseData的bytes，藍芽5才支援isLeExtendedAdvertisingSupported()，可攜帶更多資訊
        Timber.d("total: ${BleUtil.totalBytes(data, false, bluetoothAdapter)}")
        val advertisingCallback: AdvertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Timber.d("LE Advertise success.")
            }

            override fun onStartFailure(errorCode: Int) {
                Timber.d("Advertising onStartFailure: $errorCode")
                super.onStartFailure(errorCode)
            }
        }
        Timber.d("start advertising")
        advertiser!!.startAdvertising(settings, data, advertisingCallback)
    }

    private val receiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            var action = intent!!.action
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                var device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
                var rssi = intent.getShortExtra(EXTRA_RSSI, Short.MIN_VALUE)
                for (bt in btDevices) {
                    if (bt.device.name == device.name
                        && bt.device.address.toString() == device.address.toString()) {
                        return
                    }
                }
                Timber.d("onReceive: ACTION_FOUND, ${device.name} - ${device.address} - $rssi - ${device.type}")
                btDevices.add(BtDevice(device, null, rssi.toInt()))
                deviceListAdapter = DeviceListAdapter(btDevices)
                recyclerView.adapter = deviceListAdapter

                if (device.name == "Afobot-test" || device.address == "22:22:6A:B9:27:DC") {
                    bt_status.text = "Find ${device.name}, ${device.address}"
                }
            }
            // When discovery cycle finished
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                Timber.d("size: ${btDevices.size}")
                Timber.d("onReceive: ACTION_DISCOVERY_FINISHED, ${btDevices.toList()}")
                for (bt in btDevices) {
                    if (bt.device.name != null
                        && bt.device.name!!.contains("Afobot", ignoreCase = true)) {
                        bt_status.text = "fetchUuid ${bt.device.name}: "
                        bt.device.fetchUuidsWithSdp()
                        btNames.add(bt.device.name!!)
                        break
                    }
                }
            }
            if (ACTION_UUID == action) {
                var device = intent.getParcelableExtra<BluetoothDevice>(EXTRA_DEVICE)
                var uuidExtra = intent.getParcelableArrayExtra(EXTRA_UUID)

                if (uuidExtra.isNotEmpty()) {
                    Timber.d("scanResult: name: ${device.name}, uuid: ${uuidExtra[0]}")
                    bt_status.text = "${bt_status.text}\nuuid: ${uuidExtra[0]}"
                }
                for (bt in btDevices) {
                    if (bt.device.name != null
                        && bt.device.name!!.contains("Afobot", ignoreCase = true)
                        && !btNames.contains(bt.device.name!!)) {
                        bt_status.text = "${bt_status.text}\nfetchUuid ${bt.device.name}: "
                        bt.device.fetchUuidsWithSdp()
                        btNames.add(bt.device.name!!)
                        break
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun checkBTPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION)
                ,1001)
        } else {
            Timber.d("checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,  permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Timber.d("PERMISSION_GRANTED")
                    //Android 8以上需要開GPS
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                        var locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            var intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            startActivity(intent)
                        }
                    }
                } else {
                    Timber.d("PERMISSION_DENIED")
                }
                return
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
//        gattServer?.destroy()
        super.onDestroy()
    }
}
