package com.erns.androidbeacon.beaconTransmiter.tools

object BleTools {

    fun getIdAsByte(value: String): ByteArray {
        val uuid = ByteArray(16)
        for (i in uuid.indices) {
            val index = i * 2
            val `val` = value.substring(index, index + 2).toInt(16)
            uuid[i] = `val`.toByte()
        }
        return uuid
    }

}