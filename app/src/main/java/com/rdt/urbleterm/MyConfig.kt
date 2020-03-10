package com.rdt.urbleterm

import java.util.*

class MyConfig {

    companion object {
        val BLE_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val BLE_HM10_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val BLE_HM10_CHAR = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val BLE_MY_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val BLE_MY_CHAR = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        var MY_ACTION_DISCONNECT: String = ""
    }

}

/* EOF */