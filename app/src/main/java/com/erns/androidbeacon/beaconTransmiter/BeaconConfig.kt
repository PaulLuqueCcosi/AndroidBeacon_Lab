package com.erns.androidbeacon.beaconTransmiter

data class BeaconConfig(
    val uuid: String,
    val major: Int,
    val minor: Int,
    val txPower: Int
)

class BeaconConfigBuilder {
    private var uuid: String = "00000000-0000-0000-0000-000000000000"
    private var major: Int = 0
    private var minor: Int = 0
    private var txPower: Int = -59

    fun setUuid(uuid: String) = apply { this.uuid = uuid }
    fun setMajor(major: Int) = apply { this.major = major }
    fun setMinor(minor: Int) = apply { this.minor = minor }
    fun setTxPower(txPower: Int) = apply { this.txPower = txPower }

    fun build() = BeaconConfig(uuid, major, minor, txPower)
}
