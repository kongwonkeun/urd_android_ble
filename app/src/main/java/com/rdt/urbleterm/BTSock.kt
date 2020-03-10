package com.rdt.urbleterm

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import java.io.IOException
import kotlin.collections.ArrayList

class BTSock : BluetoothGattCallback() {

    private val TAG = BTSock::class.java.simpleName
    private var MTU = 23
    private val MTU_MAX = 512
    private var m_mtu: Int = MTU - 3
    private var m_ctx: Context? = null
    private val m_pairing_filter: IntentFilter = IntentFilter()
    private var m_pairing_receiver: BroadcastReceiver
    private var m_disconnecting_receiver: BroadcastReceiver
    private var m_listener: BTListener? = null
    private var m_delegation: Delegation? = null
    private var m_dev: BluetoothDevice? = null
    private var m_gatt: BluetoothGatt? = null
    private var m_rchar: BluetoothGattCharacteristic? = null
    private var m_wchar: BluetoothGattCharacteristic? = null

    private var m_connected: Boolean = false
    private var m_canceled: Boolean = false
    private var m_write_pending: Boolean = false
    private val m_write_buffer: ArrayList<ByteArray> = ArrayList()

    init {
        m_pairing_filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        m_pairing_filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)

        m_pairing_receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val dev: BluetoothDevice? = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (dev == null || dev != m_dev) {
                    return
                }
                when (intent.action) {
                    BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                        on_connect_err(IOException(m_ctx!!.getString(R.string.pairing_request)))
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {}
                    else -> {}
                }
            }
        }

        m_disconnecting_receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (m_listener != null) {
                    m_listener!!.on_io_err(Exception("disconnect"))
                }
                disconnect()
            }
        }
    }

    //
    // GATT CALLBACK
    //
    override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
        if (m_canceled) {
            return
        }
        m_delegation!!.on_char_changed(gatt!!, characteristic!!)
        if (m_canceled) {
            return
        }
        if (characteristic == m_rchar) {
            val data: ByteArray = m_rchar!!.value
            on_recv(data)
        }
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        if (m_canceled || !m_connected || m_wchar == null) {
            return
        }
        if (status != BluetoothGatt.GATT_SUCCESS) {
            on_io_err(IOException("write failed"))
            return
        }
        m_delegation!!.on_char_write(gatt!!, characteristic!!, status)
        if (m_canceled) {
            return
        }
        if (characteristic == m_wchar) {
            var data: ByteArray? = null
            synchronized(m_write_buffer) {
                if (!m_write_buffer.isEmpty() && m_delegation!!.can_write()) {
                    m_write_pending = true
                    data = m_write_buffer.removeAt(0)
                } else {
                    m_write_pending = false
                    data = null
                }
            }
            if (data != null) {
                m_wchar!!.value = data
                if (!gatt.writeCharacteristic((m_wchar))) {
                    on_io_err(IOException("write failed"))
                }
            }
        }
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (!m_gatt!!.discoverServices()) {
                on_connect_err(IOException("discover service failed"))
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (m_connected) {
                    on_io_err(IOException("gatt status $status"))
                } else {
                    on_connect_err(IOException("gatt status $status"))
                }
            }
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
        m_delegation!!.on_desc_write(gatt!!, descriptor!!, status)
        if (m_canceled) {
            return
        }
        if (descriptor.characteristic == m_rchar) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                on_connect_err(IOException("write descriptor failed"))
            } else {
                on_connect()
                m_connected = true
            }
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            m_mtu = mtu - 3
        }
        connect_char_3rd(gatt!!)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (m_canceled) {
            return
        }
        connect_char_1st(gatt!!)
    }

    //
    // PUBLIC FUN
    //
    @Throws(IOException::class)
    fun connect(ctx: Context, listener: BTListener, dev: BluetoothDevice) {
        if (m_connected || m_gatt != null) {
            throw IOException("already connected")
        }
        m_canceled = false
        m_ctx = ctx
        m_listener = listener
        m_dev = dev
        m_ctx!!.registerReceiver(m_disconnecting_receiver, IntentFilter(MyConfig.MY_ACTION_DISCONNECT))
        m_ctx!!.registerReceiver(m_pairing_receiver, m_pairing_filter)
        if (Build.VERSION.SDK_INT < 23) {
            m_gatt = m_dev!!.connectGatt(m_ctx, false, this)
        } else {
            m_gatt = m_dev!!.connectGatt(m_ctx, false, this, BluetoothDevice.TRANSPORT_LE)
        }
        if (m_gatt == null) {
            throw IOException("gatt connection failed")
        }
    }

    fun disconnect() {
        m_listener = null
        m_dev = null
        synchronized(m_write_buffer) {
            m_write_pending = false
            m_write_buffer.clear()
        }
        m_wchar = null
        m_rchar = null
        if (m_delegation != null) {
            m_delegation!!.disconnect()
        }
        if (m_gatt != null) {
            m_gatt!!.disconnect()
            try {
                m_gatt!!.close()
            } catch (e: Exception) {}
            m_gatt = null
            m_connected = false
        }
        m_ctx!!.unregisterReceiver(m_pairing_receiver)
        m_ctx!!.unregisterReceiver(m_disconnecting_receiver)
    }

    @Throws(IOException::class)
    fun send(data: ByteArray) {
        if (m_canceled || !m_connected || m_wchar == null) {
            throw IOException("not connected")
        }
        var buff: ByteArray?
        synchronized(m_write_buffer) {
            buff = if (data.size <= m_mtu) {
                data
            } else {
                data.copyOfRange(0, m_mtu)
            }
            if (!m_write_pending && m_write_buffer.isEmpty() && m_delegation!!.can_write()) {
                m_write_pending = true
            } else {
                m_write_buffer.add(buff!!)
                buff = null
            }
            if (data.size > m_mtu) {
                for (i in 1..(data.size + m_mtu)/m_mtu) {
                    val from: Int = i * m_mtu
                    val to: Int = Math.min(from + m_mtu, data.size)
                    m_write_buffer.add(data.copyOfRange(from, to))
                }
            }
        }
        if (buff != null) {
            m_wchar!!.value = buff
            if (!m_gatt!!.writeCharacteristic(m_wchar)) {
                on_io_err(IOException("write failed"))
            }
        }
    }

    //
    // PRIVATE FUN
    //
    private fun connect_char_1st(gatt: BluetoothGatt) {
        var sync = true
        m_write_pending = false
        for (svc in gatt.services) {
            when (svc.uuid) {
                MyConfig.BLE_MY_SERVICE -> m_delegation = MyDelegation()
                else -> {}
            }
            if (m_delegation != null) {
                sync = m_delegation!!.connect_char(svc)
                break
            }
        }
        if (m_canceled) {
            return
        }
        if (m_delegation == null || m_rchar == null || m_wchar == null) {
            for (svc in gatt.services) {
                for (cha in svc.characteristics) {
                }
            }
            on_connect_err(IOException("no profile found"))
            return
        }
        if (sync) {
            connect_char_2nd(gatt)
        }
    }

    private fun connect_char_2nd(gatt: BluetoothGatt) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!gatt.requestMtu(MTU_MAX)) {
                on_connect_err(IOException("request mtu failed"))
            }
        } else {
            connect_char_3rd(gatt)
        }
    }

    private fun connect_char_3rd(gatt: BluetoothGatt) {
        var prop: Int = m_wchar!!.properties
        if (prop.and(BluetoothGattCharacteristic.PROPERTY_WRITE + BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
            on_connect_err(IOException("write characteristic not writable"))
            return
        }
        if (!gatt.setCharacteristicNotification(m_rchar, true)) {
            on_connect_err(IOException("no notification for read characteristic"))
            return
        }
        val desc: BluetoothGattDescriptor? = m_rchar!!.getDescriptor(MyConfig.BLE_CCCD)
        if (desc == null) {
            on_connect_err(IOException("no cccd descriptor for read characteristic"))
            return
        }
        prop = m_rchar!!.properties
        if (prop.and(BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            desc.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else if (prop.and(BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            on_connect_err(IOException("no indication or notification descriptor for read characteristic"))
            return
        }
        if (!gatt.writeDescriptor(desc)) {
            on_connect_err(IOException("cccd descriptor not writable"))
        }
    }

    //
    // CALLBACK
    //
    private fun on_connect() {
        if (m_listener != null) {
            m_listener!!.on_connect()
        }
    }

    private fun on_connect_err(e: Exception) {
        if (m_listener != null) {
            m_listener!!.on_connect_err(e)
        }
    }

    private fun on_recv(data: ByteArray) {
        if (m_listener != null) {
            m_listener!!.on_recv(data)
        }
    }

    private fun on_io_err(e: Exception) {
        if (m_listener != null) {
            m_listener!!.on_io_err(e)
        }
    }

    //
    // INNER CLASS
    //
    open inner class Delegation {
        open fun connect_char(svc: BluetoothGattService): Boolean { return true }
        open fun can_write(): Boolean { return true }
        open fun disconnect() { /*nop*/ }
        open fun on_char_changed(g: BluetoothGatt, c: BluetoothGattCharacteristic) { /*nop*/ }
        open fun on_char_write(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) { /*nop*/ }
        open fun on_desc_write(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { /*nop*/ }
    }

    inner class MyDelegation : Delegation() {
        override fun connect_char(svc: BluetoothGattService): Boolean {
            m_rchar = svc.getCharacteristic(MyConfig.BLE_MY_CHAR)
            m_wchar = svc.getCharacteristic(MyConfig.BLE_MY_CHAR)
            return true
        }
    }

    inner class HM10Delegation : Delegation() {
        override fun connect_char(svc: BluetoothGattService): Boolean {
            m_rchar = svc.getCharacteristic(MyConfig.BLE_HM10_CHAR)
            m_wchar = svc.getCharacteristic(MyConfig.BLE_HM10_CHAR)
            return true
        }
    }

}

/* EOF */