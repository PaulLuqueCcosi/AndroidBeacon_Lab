package com.erns.androidbeacon.beaconTransmiter

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
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

        // Método para obtener una instancia única de DefaultBeaconTransmitter
        fun getInstance(context: Context, config: BeaconConfig): DefaultBeaconTransmitter =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DefaultBeaconTransmitter(context, config).also { INSTANCE = it }
            }
    }

    // Adaptador Bluetooth del dispositivo
    private val bluetoothAdapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    // Variable para rastrear si se está transmitiendo publicidad
    private var isAdvertising = false

    override fun startAdvertising() {
        // Verificar si el Bluetooth LE es compatible y si se otorgaron permisos
        if (!isBluetoothLeSupported() || !hasBluetoothPermission()) {
            Log.e(TAG, "Bluetooth LE not supported or permission denied.")
            return
        }

        // Verificar el permiso BLUETOOTH_ADVERTISE
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE permission denied!")
            return
        }

        // Construir los datos y ajustes de la publicidad
        val data = buildAdvertiseData(config)
        val settings = buildAdvertiseSettings()

        // Obtener el BluetoothLeAdvertiser
        val bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

        // Verificar si el BluetoothLeAdvertiser no es nulo
        if (bluetoothLeAdvertiser != null) {
            // Detener cualquier publicidad en curso
            bluetoothLeAdvertiser.stopAdvertising(advertisingCallback)

            // Comenzar una nueva publicidad
            bluetoothLeAdvertiser.startAdvertising(settings, data, advertisingCallback)

            // Marcar el estado de la publicidad como activa
            isAdvertising = true
        } else {
            Log.e(TAG, "Bluetooth LE Advertiser is null.")
        }
    }

    override fun stopAdvertising() {
        // Verificar el permiso BLUETOOTH_ADVERTISE
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE permission denied!")
            return
        }

        // Detener la publicidad si está en curso
        if (isAdvertising) {
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertisingCallback)
            isAdvertising = false
        }
    }

    // Verificar si el Bluetooth LE es compatible en el dispositivo
    private fun isBluetoothLeSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    // Verificar si se otorgó el permiso para publicitar Bluetooth
    private fun hasBluetoothPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Construir los datos de la publicidad
    private fun buildAdvertiseData(config: BeaconConfig): AdvertiseData {
        val dataBuilder = AdvertiseData.Builder()
        val manufacturerData = ByteBuffer.allocate(23)

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

        // Agregar el nombre del dispositivo si está configurado en la configuración
        config.name?.let {
            dataBuilder.setIncludeDeviceName(true)
            bluetoothAdapter?.name = it
        }

        return dataBuilder.build()
    }

    // Construir los ajustes para la publicidad
    private fun buildAdvertiseSettings(): AdvertiseSettings {
        return AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()
    }

    // Callback para manejar eventos de inicio y detención de publicidad
    private val advertisingCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising successfully started")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Advertising failed, errorCode: $errorCode")

            when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> Log.e(TAG, "ADVERTISE_FAILED_ALREADY_STARTED")
                ADVERTISE_FAILED_DATA_TOO_LARGE -> Log.e(TAG, "ADVERTISE_FAILED_DATA_TOO_LARGE")
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> Log.e(TAG, "ADVERTISE_FAILED_FEATURE_UNSUPPORTED")
                ADVERTISE_FAILED_INTERNAL_ERROR -> Log.e(TAG, "ADVERTISE_FAILED_INTERNAL_ERROR")
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> Log.e(TAG, "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS")
                else -> Log.e(TAG, "Unhandled error: $errorCode")
            }
        }
    }
}
