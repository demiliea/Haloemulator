package com.brilliantlabs.halo_emulator_app.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class BrilliantBlePeripheral(
    private val context: Context,
    private val onTxWrite: (ByteArray) -> Unit,
) {
  companion object {
    private const val TAG = "BrilliantBle"
    val SERVICE_UUID: UUID = UUID.fromString("7a230001-5475-a6a4-654c-8431f6ad49c4")
    val TX_UUID: UUID = UUID.fromString("7a230002-5475-a6a4-654c-8431f6ad49c4")
    val RX_UUID: UUID = UUID.fromString("7a230003-5475-a6a4-654c-8431f6ad49c4")
    val AUDIO_TX_UUID: UUID = UUID.fromString("7a230005-5475-a6a4-654c-8431f6ad49c4")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
  }

  private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
  private val adapter: BluetoothAdapter? = bluetoothManager.adapter
  private var gattServer: BluetoothGattServer? = null
  private var rxCharacteristic: BluetoothGattCharacteristic? = null
  private var connectedDevice: BluetoothDevice? = null
  private var advertiser: BluetoothLeAdvertiser? = null
  var isAdvertising = false
    private set

  private val serverCallback = object : BluetoothGattServerCallback() {
    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
      connectedDevice = if (newState == BluetoothGatt.STATE_CONNECTED) device else null
      Log.i(TAG, "Connection state: $newState")
    }

    override fun onCharacteristicWriteRequest(
      device: BluetoothDevice,
      requestId: Int,
      characteristic: BluetoothGattCharacteristic,
      preparedWrite: Boolean,
      responseNeeded: Boolean,
      offset: Int,
      value: ByteArray,
    ) {
      if (characteristic.uuid == TX_UUID || characteristic.uuid == AUDIO_TX_UUID) {
        onTxWrite(value)
      }
      if (responseNeeded) {
        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
      }
    }

    override fun onDescriptorWriteRequest(
      device: BluetoothDevice,
      requestId: Int,
      descriptor: BluetoothGattDescriptor,
      preparedWrite: Boolean,
      responseNeeded: Boolean,
      offset: Int,
      value: ByteArray,
    ) {
      if (responseNeeded) {
        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
      }
    }
  }

  fun start(deviceName: String = "Halo Emulator"): Boolean {
    val bt = adapter ?: return false
    if (!bt.isEnabled) return false

    gattServer = bluetoothManager.openGattServer(context, serverCallback)
    val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

    val tx = BluetoothGattCharacteristic(
      TX_UUID,
      BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
      BluetoothGattCharacteristic.PERMISSION_WRITE,
    )
    val rx = BluetoothGattCharacteristic(
      RX_UUID,
      BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ,
      BluetoothGattCharacteristic.PERMISSION_READ,
    )
    val cccd = BluetoothGattDescriptor(
      CCCD_UUID,
      BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
    )
    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    rx.addDescriptor(cccd)

    val audioTx = BluetoothGattCharacteristic(
      AUDIO_TX_UUID,
      BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
      BluetoothGattCharacteristic.PERMISSION_WRITE,
    )

    service.addCharacteristic(tx)
    service.addCharacteristic(rx)
    service.addCharacteristic(audioTx)
    gattServer?.addService(service)
    rxCharacteristic = rx

    bt.name = deviceName
    advertiser = bt.bluetoothLeAdvertiser
    val settings = AdvertiseSettings.Builder()
      .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
      .setConnectable(true)
      .setTimeout(0)
      .build()
    val data = AdvertiseData.Builder()
      .setIncludeDeviceName(true)
      .addServiceUuid(ParcelUuid(SERVICE_UUID))
      .build()
    advertiser?.startAdvertising(settings, data, advertiseCallback)
    return true
  }

  fun stop() {
    advertiser?.stopAdvertising(advertiseCallback)
    isAdvertising = false
    gattServer?.close()
    gattServer = null
    connectedDevice = null
  }

  fun notifyRx(data: ByteArray) {
    val device = connectedDevice ?: return
    val rx = rxCharacteristic ?: return
    rx.value = data
    gattServer?.notifyCharacteristicChanged(device, rx, false)
  }

  private val advertiseCallback = object : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
      isAdvertising = true
      Log.i(TAG, "BLE advertising started")
    }

    override fun onStartFailure(errorCode: Int) {
      isAdvertising = false
      Log.e(TAG, "BLE advertising failed: $errorCode")
    }
  }
}
