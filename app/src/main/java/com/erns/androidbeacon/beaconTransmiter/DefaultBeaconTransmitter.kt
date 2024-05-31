package com.erns.androidbeacon.beaconTransmiter

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import com.erns.androidbeacon.beaconTransmiter.tools.BleTools
import java.nio.ByteBuffer

class DefaultBeaconTransmitter private constructor(
    private val context: Context,
    private val config: BeaconConfig
) : BeaconTransmitter {

    companion object {
        private const val TAG = "BeaconTransmitter"
        private const val MANUFACTURER_ID = 76

        @Volatile
        private var INSTANCE: DefaultBeaconTransmitter? = null

        fun getInstance(context: Context, config: BeaconConfig): DefaultBeaconTransmitter =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DefaultBeaconTransmitter(context, config).also { INSTANCE = it }
            }
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var isAdvertising = false

    override fun startAdvertising() {
        if (!isBluetoothLeSupported() || !hasBluetoothPermission()) {
            Log.e(TAG, "Bluetooth LE not supported or permission denied.")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_CONNECT denied!")
            return
        }

        val data = buildAdvertiseData(config)
        val settings = buildAdvertiseSettings()

        bluetoothAdapter?.bluetoothLeAdvertiser?.apply {
            stopAdvertising(advertisingCallback)
            startAdvertising(settings, data, advertisingCallback)
            isAdvertising = true
        }
    }

    override fun stopAdvertising() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_CONNECT denied!")
            return
        }

        if (isAdvertising) {
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertisingCallback)
            isAdvertising = false
        }
    }

    private fun isBluetoothLeSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    private fun hasBluetoothPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildAdvertiseData(config: BeaconConfig): AdvertiseData {
        val dataBuilder = AdvertiseData.Builder()
        val manufacturerData = ByteBuffer.allocate(23)

//        val serviceUuid = ParcelUuid.fromString(config.uuid)
        val uuidBytes: ByteArray = BleTools.getIdAsByte(config.uuid)

        manufacturerData.put(0, 0x02.toByte()) // Beacon Identifier
        manufacturerData.put(1, 0x15.toByte()) // Beacon Identifier
        for (i in 2..17) {
            manufacturerData.put(i, uuidBytes[i - 2]) // UUID
        }
        manufacturerData.put(18, (config.major shr 8).toByte()) // Major
        manufacturerData.put(19, config.major.toByte())
        manufacturerData.put(20, (config.minor shr 8).toByte()) // Minor
        manufacturerData.put(21, config.minor.toByte())
        manufacturerData.put(22, config.txPower.toByte()) // TxPower

        dataBuilder.addManufacturerData(MANUFACTURER_ID, manufacturerData.array())

        // Add the name if it's set
        config.name?.let {
            dataBuilder.setIncludeDeviceName(true)
            bluetoothAdapter?.name = it
        }

        return dataBuilder.build()
    }

    private fun buildAdvertiseSettings(): AdvertiseSettings {
        return AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()
    }

    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising successfully started")
        }


        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)

            Log.d(TAG, "Advertising failed, errorCode: $errorCode")

            when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> Log.d(TAG, "ADVERTISE_FAILED_ALREADY_STARTED")
                ADVERTISE_FAILED_DATA_TOO_LARGE -> Log.d(TAG, "ADVERTISE_FAILED_DATA_TOO_LARGE")
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> Log.d(
                    TAG,
                    "ADVERTISE_FAILED_FEATURE_UNSUPPORTED"
                )

                ADVERTISE_FAILED_INTERNAL_ERROR -> Log.d(TAG, "ADVERTISE_FAILED_INTERNAL_ERROR")
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> Log.d(
                    TAG,
                    "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS"
                )

                else -> Log.d(TAG, "Unhandled error: $errorCode")
            }
//            super.onStartFailure(errorCode)
//            Log.e(TAG, "Advertising failed, errorCode: $errorCode")
        }
    }
}
