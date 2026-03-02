package com.chimali.core.bluetooth.util

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings

/**
 * Constants for Bluetooth HID FIDO2 Authenticator configuration.
 *
 * The HID descriptor and SDP settings are derived from the WIOsense/rauth-android
 * and octaviordz/wiokey-android reference implementations, ensuring compatibility
 * with the built-in FIDO HID drivers on Windows, macOS, and Linux.
 *
 * References:
 * - https://github.com/WIOsense/rauth-android
 * - https://github.com/octaviordz/wiokey-android
 * - FIDO HID Protocol Spec: https://fidoalliance.org/specs/fido-v2.0-ps-20190130/fido-client-to-authenticator-protocol-v2.0-ps-20190130.html#usb
 */
object BluetoothHidConstants {

    // --- HID Report Sizes ---

    /** Size of the HID payload available for framing. 
     * In Android's Bluetooth HID Profile, the MTU is capped at 64 bytes.
     * With 1 byte for HIDP header and 1 byte for Report ID, 62 bytes are available. */
    const val HID_REPORT_SIZE = 62

    /** The FIDO HID Report ID. FIDO spec requires it to be 0x00 for HID over BT, but
     * some hosts expect 0x01. We use 0x00 (no report ID) per the FIDO spec. */
    const val HID_REPORT_ID = 0x00.toByte()

    // --- FIDO HID Report Descriptor ---
    //
    // Usage Page: FIDO Alliance (0xF1D0)
    // Usage: U2FHID (0x01)
    // Input/Output: 64-byte data reports
    //
    // This descriptor makes the device visible as a "FIDO Security Key" to the host OS,
    // not a generic HID device. This is required for Windows/macOS to accept it as a FIDO key.
    val FIDO_HID_REPORT_DESCRIPTOR = byteArrayOf(
        0x06.toByte(), 0xD0.toByte(), 0xF1.toByte(), // Usage Page (FIDO Alliance: 0xF1D0)
        0x09.toByte(), 0x01.toByte(),                 // Usage (U2FHID: 0x01)
        0xA1.toByte(), 0x01.toByte(),                 // Collection (Application)
        0x09.toByte(), 0x20.toByte(),                 //   Usage (INPUT_REPORT: 0x20)
        0x15.toByte(), 0x00.toByte(),                 //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(),  //   Logical Maximum (255)
        0x75.toByte(), 0x08.toByte(),                 //   Report Size (8 bits)
        0x95.toByte(), HID_REPORT_SIZE.toByte(),      //   Report Count (64 bytes)
        0x81.toByte(), 0x02.toByte(),                 //   Input (Data, Absolute, Variable)
        0x09.toByte(), 0x21.toByte(),                 //   Usage (OUTPUT_REPORT: 0x21)
        0x15.toByte(), 0x00.toByte(),                 //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x00.toByte(),  //   Logical Maximum (255)
        0x75.toByte(), 0x08.toByte(),                 //   Report Size (8 bits)
        0x95.toByte(), HID_REPORT_SIZE.toByte(),      //   Report Count (64 bytes)
        0x91.toByte(), 0x02.toByte(),                 //   Output (Data, Absolute, Variable)
        0xC0.toByte()                                  // End Collection
    )

    // --- SDP Record (Service Discovery Protocol) ---
    //
    // These settings advertise the device on the Bluetooth Classic HID Profile.
    val SDP_RECORD = BluetoothHidDeviceAppSdpSettings(
        "Chimali Security Key",    // Name visible to host OS
        "Virtual FIDO2 Key",       // Description
        "Chimali",                 // Provider
        BluetoothHidDevice.SUBCLASS1_COMBO, // HID Subclass (generic HID)
        FIDO_HID_REPORT_DESCRIPTOR
    )

    // --- QoS Settings ---
    //
    // Optimized for low-latency interactive FIDO HID communication.
    // Values matched exactly to wiokey-android (Constants.java).
    val QOS_OUT = BluetoothHidDeviceAppQosSettings(
        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
        1000, // Token rate
        HID_REPORT_SIZE + 1, // Token bucket size (63)
        2000, // Peak bandwidth
        5000, // Latency (microseconds)
        BluetoothHidDeviceAppQosSettings.MAX // Delay variation
    )
}
