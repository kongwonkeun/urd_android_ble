package com.rdt.urbleterm

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.*
import android.util.Log
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.ListFragment
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class FragDevList : ListFragment() {

    private val TAG = FragDevList::class.java.simpleName
    private val SCAN_PERIOD = 10000L
    private var m_state: ScanState = ScanState.UNKNOWN
    private val m_handler: Handler = Handler()
    private var m_scan_callback: ScanCallback? = null
    private var m_menu: Menu? = null
    private var m_discovery_receiver: BroadcastReceiver? = null
    private val m_discovery_filter: IntentFilter = IntentFilter()
    private val m_adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private lateinit var m_scanner: BluetoothLeScanner
    private val m_dev_list: ArrayList<BluetoothDevice> = ArrayList()
    private var m_dev_list_adapter: ArrayAdapter<BluetoothDevice>? = null

    //
    // LIFECYCLE
    //
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        m_dev_list_adapter = object : ArrayAdapter<BluetoothDevice>(activity!!, 0, m_dev_list) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val dev: BluetoothDevice = m_dev_list.get(position)
                var view = convertView
                if (view == null) {
                    view = activity!!.layoutInflater.inflate(R.layout.frag_dev_list, parent, false)
                }
                val name = view!!.findViewById<TextView>(R.id.v_name)
                val address = view.findViewById<TextView>(R.id.v_address)
                name.text = dev.name
                address.text = dev.address
                return view
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        listAdapter = null
        val header: View = activity!!.layoutInflater.inflate(R.layout.frag_dev_list_header, null, false)
        listView.addHeaderView(header, null, false)
        setEmptyText("initializing...")
        listAdapter = m_dev_list_adapter

        m_scan_callback = object : ScanCallback() {
            override fun onBatchScanResults(results: MutableList<ScanResult>?) {}
            override fun onScanFailed(errorCode: Int) {}
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val dev: BluetoothDevice? = result?.device
                if (dev != null && !m_dev_list.contains(dev)) {
                    m_dev_list.add(dev)
                }
                m_dev_list.sortWith(Comparator { x: BluetoothDevice, y: BluetoothDevice ->
                    MyUtil.compare(x, y)
                })
                m_dev_list_adapter!!.notifyDataSetChanged()
            }
        }

        m_discovery_receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent!!.action.equals(BluetoothDevice.ACTION_FOUND)) {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device!!.type != BluetoothDevice.DEVICE_TYPE_CLASSIC && activity != null) {
                        activity!!.runOnUiThread {
                            update_scan(device)
                        }
                    }
                }
                if (intent.action.equals((BluetoothAdapter.ACTION_DISCOVERY_FINISHED))) {
                    m_state = ScanState.DISCOVERY_FINISHED
                    stop_scan()
                }
            }
        }
        m_discovery_filter.addAction(BluetoothDevice.ACTION_FOUND)
        m_discovery_filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }

    override fun onResume() {
        super.onResume()
        activity!!.registerReceiver(m_discovery_receiver, m_discovery_filter)
        if (!m_adapter.isEnabled) {
            setEmptyText("bluetooth is disabled")
            return
        }
        m_scanner = m_adapter.bluetoothLeScanner
        start_scan()
    }

    override fun onPause() {
        activity!!.unregisterReceiver(m_discovery_receiver)
        super.onPause()
    }

    override fun onDetach() {
        super.onDetach()
    }

    //
    // MENU
    //
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_dev_list, menu)
        m_menu = menu
        menu.findItem(R.id.v_setting).isEnabled = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.v_setting -> {
                val intent: Intent = Intent()
                intent.action = android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
                startActivity(intent)
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
        return true
    }

    //
    // PERMISSION
    //
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Handler(Looper.getMainLooper()).postDelayed(this::start_scan, 1)
        } else {
            val builder: AlertDialog.Builder = AlertDialog.Builder(activity)
            builder.setTitle(getText(R.string.location_denied_title))
            builder.setMessage(getText(R.string.location_denied_message))
            builder.setPositiveButton(android.R.string.ok, null)
            builder.show()
        }
    }

    //
    // LIST
    //
    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        stop_scan()
        val dev: BluetoothDevice = m_dev_list[position - 1]
        val arg = Bundle()
        arg.putString("device", dev.address)
        val f: Fragment = FragTerm()
        f.arguments = arg
        fragmentManager!!.beginTransaction().replace(R.id.v_frag, f, "term").addToBackStack(null).commit()
    }

    //
    // PRIVATE FUN
    //
    private fun start_scan() {
        if (m_state != ScanState.UNKNOWN) {
            return
        }
        m_state = ScanState.SCAN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activity!!.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                m_state = ScanState.UNKNOWN
                val builder: AlertDialog.Builder = AlertDialog.Builder(activity)
                builder.setTitle(R.string.location_permission_title)
                builder.setMessage(R.string.location_permission_message)
                builder.setPositiveButton(android.R.string.ok) {
                    _, _ -> requestPermissions(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 0)
                }
                builder.show()
                return
            }
            val lm: LocationManager = activity!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var ls = false
            try {
                ls = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (e: Exception) {}
            try {
                ls.or(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            } catch (e: Exception) {}
            if (!ls) {
                m_state = ScanState.DISCOVERY
            }
        }
        m_dev_list.clear()
        m_dev_list_adapter!!.notifyDataSetChanged()
        setEmptyText("scanning...")

        if (m_state == ScanState.SCAN) {
            m_handler.postDelayed(this::stop_scan, SCAN_PERIOD)
            DoAsyncTask {
                m_scanner.startScan(m_scan_callback)
            }.execute()
        } else {
            m_adapter.startDiscovery()
        }
    }

    private fun stop_scan() {
        if (m_state == ScanState.UNKNOWN) {
            return
        }
        when (m_state) {
            ScanState.SCAN -> {
                m_handler.removeCallbacks(this::stop_scan)
                m_scanner.stopScan(m_scan_callback)
            }
            ScanState.DISCOVERY -> m_adapter.cancelDiscovery()
            else -> {}
        }
        m_state = ScanState.UNKNOWN
    }

    private fun update_scan(dev: BluetoothDevice) {
        if (m_state == ScanState.UNKNOWN) {
            return
        }
        if (m_dev_list.indexOf(dev) < 0) {
            m_dev_list.add(dev)
            m_dev_list.sortWith(kotlin.Comparator { x: BluetoothDevice, y: BluetoothDevice ->
                MyUtil.compare(x, y)
            })
            m_dev_list_adapter!!.notifyDataSetChanged()
        }
    }

    //
    // INNER CLASS
    //
    enum class ScanState(val i: Int) {
        SCAN(1),
        DISCOVERY(2),
        DISCOVERY_FINISHED(3),
        UNKNOWN(4)
    }

    inner class DoAsyncTask(val handler: () -> Unit) : AsyncTask<Void, Void, Void>() {
        override fun doInBackground(vararg params: Void?): Void? {
            handler()
            return null
        }
    }

}

/* EOF */