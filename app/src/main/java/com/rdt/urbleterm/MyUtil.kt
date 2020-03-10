package com.rdt.urbleterm

import android.bluetooth.BluetoothDevice

class MyUtil {

    companion object {

        fun compare(a: BluetoothDevice, b: BluetoothDevice): Int {
            val a_valid: Boolean = a.name != null && a.name.isNotEmpty()
            val b_valid: Boolean = b.name != null && b.name.isNotEmpty()
            if (a_valid && b_valid) {
                val ret: Int = a.name.compareTo(b.name)
                if (ret != 0) {
                    return ret
                }
                return a.address.compareTo(b.address)
            }
            if (a_valid) return -1
            if (b_valid) return +1
            return a.address.compareTo(b.address)
        }

    }

}