package com.rdt.urbleterm

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.frag_term.*

class FragTerm : Fragment(), ServiceConnection, BTListener {

    private val TAG = FragTerm::class.java.simpleName
    private val NL = "\n"
    private val CRNL = "\r\n"
    private var m_sock: BTSock? = null
    private var m_svc: BTService? = null
    private var m_connected: ConnectedType = ConnectedType.CONN_FALSE
    private var m_first_start: Boolean = true;
    private var m_addr: String = ""

    //
    // LIFECYCLE
    //
    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity!!.bindService(Intent(activity!!, BTService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        m_addr = arguments!!.getString("device").toString()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.frag_term, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        v_window.movementMethod = ScrollingMovementMethod()
        v_send.setOnClickListener {
            send(v_message.text.toString())
        }
    }

    override fun onStart() {
        super.onStart()
        if (m_svc != null) {
            m_svc!!.attach(this)
        } else {
            activity!!.startService(Intent(activity!!, BTService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (m_first_start && m_svc != null) {
            m_first_start = false
            activity!!.runOnUiThread(this::connect)
        }
    }

    override fun onStop() {
        if (m_svc != null && !activity!!.isChangingConfigurations) {
            m_svc!!.detach()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (m_connected != ConnectedType.CONN_FALSE) {
            disconnect()
        }
        activity!!.stopService(Intent(activity, BTService::class.java))
        super.onDestroy()
    }

    override fun onDetach() {
        try {
            activity!!.unbindService(this)
        } catch (e: Exception) {
        }
        super.onDetach()
    }

    //
    // IMPLEMENT ServiceConnection
    //
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        m_svc = (service as BTService.MyBinder).getService()
        if (m_first_start && isResumed) {
            m_first_start = false
            activity!!.runOnUiThread(this::connect)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        m_svc = null
    }

    //
    // IMPLEMENT BTListener
    //
    override fun on_connect() {
        status("connected")
        m_connected = ConnectedType.CONN_TRUE
    }

    override fun on_connect_err(e: Exception) {
        status("connection failed: " + e.message)
        disconnect()
    }

    override fun on_io_err(e: Exception) {
        status("connection lost: " + e.message)
        disconnect()
    }

    override fun on_recv(data: ByteArray) {
        receive(data)
    }

    //
    // PRIVATE FUN
    //
    private fun connect() {
        try {
            val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val dev: BluetoothDevice = adapter.getRemoteDevice(m_addr)
            status("connecting...")
            m_connected = ConnectedType.CONN_PENDING
            m_sock = BTSock()
            m_svc!!.connect(this)
            m_sock!!.connect(context!!, m_svc!!, dev)
        } catch (e: Exception) {
            on_connect_err(e)
        }
    }

    private fun disconnect() {
        m_connected = ConnectedType.CONN_FALSE
        if (m_svc != null) {
            m_svc!!.disconnect()
        }
        if (m_sock != null) {
            m_sock!!.disconnect()
            m_sock = null
        }
    }

    private fun receive(data: ByteArray) {
        status(data.toString(Charsets.UTF_8))
    }

    private fun send(str: String) {
        if (m_connected != ConnectedType.CONN_TRUE) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_LONG).show()
            return
        }
        try {
            status(str)
            val data: ByteArray = (str + CRNL).toByteArray(Charsets.UTF_8)
            m_sock!!.send(data)
        } catch (e: Exception) {
            on_io_err(e)
        }
    }

    private fun status(str: String) {
        v_window.append(str + '\n')
        val scroll: Int = v_window.layout.getLineTop(v_window.lineCount) - v_window.height
        if (scroll > 10000) {
            v_window.text = NL
        } else if (scroll > 0) {
            v_window.scrollTo(0, scroll)
        } else {
            v_window.scrollTo(0, 0)
        }
    }

    //
    // INNER CLASS
    //
    enum class ConnectedType(val i: Int) {
        CONN_FALSE(1),
        CONN_PENDING(2),
        CONN_TRUE(3),
        CONN_UNKNOWN(4)
    }

}

/* EOF */