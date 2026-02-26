# Bluetooth HID Contract

**Date**: 2025-02-25  
**Version**: 1.0.0  
**Standard**: Bluetooth HID 1.1.1 with FIDO2 Profile

## Protocol Overview

The Bluetooth HID implementation provides a Human Interface Device (HID) interface for FIDO2 authentication. This contract defines the exact Bluetooth HID behavior required for cross-platform compatibility.

## Bluetooth Profile Requirements

### HID Device Profile

**Profile Version**: HID 1.1.1  
**Transport**: Bluetooth Low Energy (BLE)  
**Role**: HID Device (peripheral)  
**Connection Type**: GATT-based HID Service

### Service Requirements

**Primary Service**: Human Interface Device Service  
**Service UUID**: `0x1812` (HID Service)  
**Service Characteristics**:

| UUID | Name | Properties | Description |
|------|------|-------------|-------------|
| 0x2A4A | HID Information | Read | HID version and country code |
| 0x2A4B | Report Map | Read | HID report descriptor |
| 0x2A4C | HID Control | Write | Control commands |
| 0x2A4D | Report | Read/Write/Notify | HID report data |
| 0x2A4E | Protocol Mode | Read/Write | Boot/Report protocol |

## HID Report Descriptor

### FIDO2 HID Descriptor

```c
// FIDO2 HID Report Descriptor (64 bytes)
const uint8_t fido2_hid_descriptor[] = {
    // Top-Level Collection
    0x05, 0x01,        // Usage Page (Generic Desktop)
    0x09, 0x06,        // Usage (Keyboard)
    0xA1, 0x01,        // Collection (Application)
    
    // Report ID
    0x85, 0x01,        // Report ID (1)
    
    // FIDO2 Usage Page
    0x05, 0xD0,        // Usage Page (FIDO Alliance)
    0x09, 0x01,        // Usage (U2F HID)
    
    // Logical and Physical Extents
    0x15, 0x00,        // Logical Minimum (0)
    0x26, 0xFF, 0x00,  // Logical Maximum (255)
    0x75, 0x08,        // Report Size (8 bits)
    0x95, 0x40,        // Report Count (64 bytes)
    
    // Input Report
    0x81, 0x02,        // Input (Data, Variable, Absolute)
    
    // End Collection
    0xC0                 // End Collection
};
```

### Report Structure

**Report ID**: 1 (all FIDO2 messages)  
**Maximum Size**: 64 bytes per report  
**Direction**: Input (device to host) and Output (host to device)

## Connection Management

### Device Discovery

**Advertising Packet**:
```c
struct ble_advertisement {
    uint8_t flags[3];              // BLE flags
    uint8_t local_name[...];        // Device name (optional)
    uint8_t service_uuids[16];      // HID Service UUID (0x1812)
    uint8_t manufacturer_data[...];  // Optional manufacturer data
    uint8_t service_data[...];       // Optional service data
};
```

**Advertising Parameters**:
- **Interval**: 100ms - 1s (adjustable for power)
- **Connectable**: Yes
- **Scannable**: Yes
- **Directed**: No (undirected advertising)

### Pairing Process

**Security Requirements**:
- **Pairing Method**: Just Works or Numeric Comparison
- **Encryption**: AES-128 (minimum)
- **Authentication**: MITM protection required
- **Bonding**: Persistent bonding preferred

**Pairing Flow**:
1. **Device Discovery**: Desktop scans for HID devices
2. **Connection Request**: Desktop initiates GATT connection
3. **Service Discovery**: Desktop discovers HID service characteristics
4. **Pairing**: Secure pairing with user confirmation
5. **Bonding**: Persistent pairing key storage
6. **Service Configuration**: Configure HID report characteristics

### Connection States

```kotlin
enum class ConnectionState {
    DISCONNECTED,      // No active connection
    CONNECTING,        // Connection in progress
    CONNECTED,         // Successfully connected
    PAIRING,          // Pairing in progress
    BONDED,           // Successfully paired
    ERROR              // Connection error
}
```

## Data Transfer Protocol

### Report Types

**Input Reports** (Device to Host):
- CTAP2 command responses
- Status information
- Error notifications

**Output Reports** (Host to Device):
- CTAP2 command requests
- Control commands
- Configuration data

**Feature Reports** (Bidirectional):
- Device information
- Configuration parameters
- Diagnostic data

### Message Framing

**CTAP2 over HID**:
```c
struct hid_ctap_frame {
    uint8_t channel_id[4];      // Channel identifier
    uint8_t command;             // CTAP2 command byte
    uint8_t length[2];          // Payload length (big-endian)
    uint8_t data[...];          // CTAP2 payload
    uint8_t sequence;            // Sequence number for fragmentation
};
```

**Channel Management**:
- **Initialization**: Via HID INIT command
- **Channel ID**: 32-bit random value
- **Timeout**: 30 seconds of inactivity
- **Fragmentation**: Support for messages > 64 bytes

### Flow Control

**Acknowledgment**:
- Implicit acknowledgment via next message
- Timeout detection for unacknowledged messages
- Retransmission for lost packets

**Window Size**:
- Maximum 1 outstanding message per channel
- Backpressure via HID protocol flow control

## Performance Requirements

### Latency Targets

**Connection Establishment**:
- **Discovery**: <5 seconds
- **Pairing**: <10 seconds
- **Service Configuration**: <2 seconds

**Data Transfer**:
- **Report Latency**: <50ms (device to host)
- **Command Response**: <200ms (end-to-end)
- **Throughput**: >1 KB/s sustained

### Power Management

**Advertising Power**:
- **TX Power**: Adjustable based on range requirements
- **Duty Cycle**: Optimized for battery life
- **Sleep Mode**: Automatic inactivity timeout

**Connection Power**:
- **Connection Interval**: 30ms - 400ms (adaptive)
- **Slave Latency**: 0-9 (configurable)
- **Supervision Timeout**: 6 seconds

## Error Handling

### Connection Errors

| Error Code | Name | Description | Recovery |
|------------|------|-------------|-----------|
| 0x01 | CONNECTION_FAILED | Connection establishment failed | Retry with exponential backoff |
| 0x02 | AUTHENTICATION_FAILED | Pairing authentication failed | Re-pair with user confirmation |
| 0x03 | INSUFFICIENT_ENCRYPTION | Insufficient encryption level | Upgrade security requirements |
| 0x04 | TIMEOUT | Connection timeout | Retry connection |
| 0x05 | DEVICE_NOT_FOUND | Device not found during discovery | Restart discovery |
| 0x06 | MAX_CONNECTIONS | Maximum connections reached | Disconnect unused connections |

### Protocol Errors

| Error Code | Name | Description | Recovery |
|------------|------|-------------|-----------|
| 0x10 | INVALID_REPORT | Invalid HID report format | Ignore report, send error |
| 0x11 | REPORT_TOO_LARGE | Report exceeds maximum size | Send error response |
| 0x12 | CHANNEL_NOT_FOUND | Invalid channel ID | Send error response |
| 0x13 | COMMAND_NOT_SUPPORTED | Unsupported CTAP2 command | Send error response |
| 0x14 | CHECKSUM_ERROR | Checksum validation failed | Request retransmission |

## Security Considerations

### Pairing Security

**MITM Protection**:
- Numeric comparison pairing when available
- Out-of-band verification for high-security scenarios
- Certificate validation for authenticated pairing

**Key Management**:
- Secure storage of pairing keys in Android KeyStore
- Periodic key rotation for long-term pairings
- Secure deletion of revoked pairings

### Data Protection

**Encryption Requirements**:
- All data encrypted over BLE link
- Perfect forward secrecy via ECDH key exchange
- Replay attack prevention via sequence numbers

**Authentication Requirements**:
- User presence verification for sensitive operations
- Biometric authentication for high-value credentials
- Rate limiting for authentication attempts

## Platform Compatibility

### Windows Support

**Requirements**:
- Windows 10 version 1903 or later
- Bluetooth LE support
- Native HID driver support

**Known Issues**:
- Some Windows versions require manual driver installation
- Connection stability issues on certain hardware
- Workaround: Use Windows Bluetooth troubleshooting tools

### macOS Support

**Requirements**:
- macOS 12.0 (Monterey) or later
- Bluetooth 4.0+ support
- Native HID framework support

**Known Issues**:
- Keychain access prompts for first-time use
- Automatic reconnection behavior varies by version
- Workaround: Use System Preferences for Bluetooth management

### Linux Support

**Requirements**:
- BlueZ 5.0 or later
- Linux kernel 4.19 or later
- udev rules for HID device access

**Known Issues**:
- Distribution-specific configuration required
- Some desktop environments don't auto-connect
- Workaround: Manual udev rule configuration

## Testing Requirements

### Conformance Testing

**Bluetooth SIG Tests**:
- HID Profile Test Suite
- GATT Interoperability Tests
- Security and Privacy Tests

**FIDO2 Tests**:
- FIDO2 Alliance Conformance Tools
- Cross-platform authentication tests
- Performance benchmarking

### Integration Testing

**Test Devices**:
- Windows 10/11 laptops with Bluetooth 4.0+
- macOS 12+ devices with Bluetooth LE
- Linux distributions (Ubuntu 22.04+, Fedora 36+)

**Test Scenarios**:
- Successful pairing and connection
- Authentication flows with different browsers
- Error handling and recovery
- Multi-device management

## Implementation Notes

### Android Integration

**BluetoothHidDevice API**:
- Minimum SDK: API 28 (Android 9.0)
- Required permissions: BLUETOOTH_PRIVILEGED
- Thread safety: All operations must be thread-safe
- Lifecycle management: Proper cleanup in onDestroy()

**Power Optimization**:
- Adaptive advertising intervals
- Connection parameter optimization
- Background processing limitations
- Battery usage monitoring

### Performance Optimization

**Connection Management**:
- Connection pooling for multiple devices
- Automatic reconnection with exponential backoff
- Keep-alive mechanisms for long-lived connections
- Graceful degradation for poor signal conditions

**Data Processing**:
- Efficient CBOR encoding/decoding
- Minimal memory allocations in hot paths
- Asynchronous processing for UI responsiveness
- Proper buffer management for HID reports

### Debugging Support

**Logging**:
- Structured logging with correlation IDs
- Performance metrics collection
- Error categorization and reporting
- Privacy-preserving audit logs

**Diagnostics**:
- Connection state monitoring
- Bluetooth signal strength tracking
- Protocol compliance validation
- Cross-platform compatibility verification
