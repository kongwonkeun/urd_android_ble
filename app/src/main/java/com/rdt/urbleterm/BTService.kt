package com.rdt.urbleterm

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.*

class BTService : Service(), BTListener {

    private val TAG = BTService::class.java.simpleName;
    private val m_binder: IBinder = MyBinder()
    private val m_handler: Handler = Handler(Looper.getMainLooper())
    private val m_q1: Queue<QItem> = LinkedList()
    private val m_q2: Queue<QItem> = LinkedList()
    private var m_listener: BTListener? = null
    private var m_connected: Boolean = false

    //
    // LIFECYCLE
    //
    override fun onBind(intent: Intent?): IBinder? {
        return m_binder
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    //
    // IMPLEMENT BTListener
    //
    override fun on_connect() {
        if (m_connected) {
            synchronized(this) {
                if (m_listener != null) {
                    m_handler.post {
                        if (m_listener != null) {
                            m_listener!!.on_connect()
                        } else {
                            m_q1.add(QItem(QType.CONNECT, null, null))
                        }
                    }
                } else {
                    m_q2.add(QItem(QType.CONNECT, null, null))
                }
            }
        }
    }

    override fun on_connect_err(e: Exception) {
        if (m_connected) {
            synchronized(this) {
                if (m_listener != null) {
                    m_handler.post {
                        if (m_listener != null) {
                            m_listener!!.on_connect_err(e)
                        } else {
                            m_q1.add(QItem(QType.CONNECT_ERR, null, e))
                            disconnect()
                        }
                    }
                } else {
                    m_q2.add(QItem(QType.CONNECT_ERR, null, e))
                    disconnect()
                }
            }
        }
    }

    override fun on_io_err(e: Exception) {
        if (m_connected) {
            synchronized(this) {
                if (m_listener != null) {
                    m_handler.post {
                        if (m_listener != null) {
                            m_listener!!.on_io_err(e)
                        } else {
                            m_q1.add(QItem(QType.IO_ERR, null, e))
                            disconnect()
                        }
                    }
                } else {
                    m_q2.add(QItem(QType.IO_ERR, null, e))
                    disconnect()
                }
            }
        }
    }

    override fun on_recv(data: ByteArray) {
        if (m_connected) {
            synchronized(this) {
                if (m_listener != null) {
                    m_handler.post {
                        if (m_listener != null) {
                            m_listener!!.on_recv(data)
                        } else {
                            m_q1.add(QItem(QType.RECV, data, null))
                        }
                    }
                } else {
                    m_q2.add(QItem(QType.RECV, data, null))
                }
            }
        }
    }

    //
    // PUBLIC FUN
    //
    fun attach(listener: BTListener) {
        if (Looper.getMainLooper().thread != Thread.currentThread()) {
            throw IllegalArgumentException("not in main thread")
        }
        if (m_connected) {
            synchronized(this) {
                this.m_listener = listener
            }
        }
        for (item in m_q1) {
            when (item.type) {
                QType.CONNECT -> listener.on_connect()
                QType.CONNECT_ERR -> listener.on_connect_err(item.err!!)
                QType.IO_ERR -> on_io_err(item.err!!)
                QType.RECV -> on_recv(item.data!!)
                else -> {}
            }
        }
        for (item in m_q2) {
            when (item.type) {
                QType.CONNECT -> listener.on_connect()
                QType.CONNECT_ERR -> listener.on_connect_err(item.err!!)
                QType.IO_ERR -> on_io_err(item.err!!)
                QType.RECV -> on_recv(item.data!!)
                else -> {}
            }
        }
    }

    fun connect(listener: BTListener) {
        m_listener = listener
        m_connected = true
    }

    fun detach() {
        m_listener = null
    }

    fun disconnect() {
        m_listener = null
        m_connected = false
    }

    //
    // INNER CLASS
    //
    inner class MyBinder : Binder() {
        fun getService(): BTService {
            return this@BTService
        }
    }

    enum class QType(val i: Int) {
        CONNECT(1),
        CONNECT_ERR(2),
        IO_ERR(3),
        RECV(4),
        UNKNOWN(5)
    }

    inner class QItem(var type: QType, var data: ByteArray?, var err: Exception?) {
    }

}

/* EOF */