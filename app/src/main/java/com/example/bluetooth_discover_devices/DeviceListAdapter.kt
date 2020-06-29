package com.example.bluetooth_discover_devices

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetooth_discover_devices.BleUtil.calculateDistance
import kotlinx.android.synthetic.main.item_bt.view.*

class DeviceListAdapter(
    var btList: List<BtDevice>
): RecyclerView.Adapter<DeviceListAdapter.ViewHolder>() {
    inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        fun bind(position: Int, bt: BtDevice) {
            itemView.pos.text = "${position + 1}. "
            var type = ""
            when(bt.device.type) {
                BluetoothDevice.DEVICE_TYPE_UNKNOWN -> type = "UNKNOWN"
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> type = "CLASSIC"
                BluetoothDevice.DEVICE_TYPE_LE -> type = "LE"
                BluetoothDevice.DEVICE_TYPE_DUAL -> type = "DUAL"
            }
            itemView.bt_name.text = "${bt.device.name}"
            itemView.bt_addr.text = "${bt.device.address}"
            itemView.bt_type.text = "$type"
            itemView.bt_rssi.text = "${bt.rssi}"
            itemView.bt_dis.text = "${calculateDistance(bt.rssi)}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bt, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return btList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position, btList[position])
    }
}