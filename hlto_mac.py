import asyncio
import struct
import os
from datetime import datetime
import time
import csv

from bleak import BleakScanner, BleakClient

# Match the BLE name you saw: "HLTO - 01CC"
TARGET_NAME_KEYWORD = "HLTO"

# Output files
OUTPUT_DIR = "."
binary_file = None
csv_file = None
debug_file = None
csv_writer = None
frame_count = 0
output_filename = ""

# Latest values (updated by notifications)
latest_heart_rate = 0
latest_respiration_rate = 0
latest_temperature = 0.0
latest_spo2 = 255  # 255 = not yet read

# ─── DEBUG CONFIG ─────────────────────────────────────────────────────────────
DEBUG_RAW_DSP   = True   # Print every raw line from the vendor DSP stream
DEBUG_RAW_HR    = True   # Print raw bytes from HR characteristic
DEBUG_RAW_TEMP  = True   # Print raw bytes from Temperature characteristic
DEBUG_UNKNOWN   = True   # Print raw bytes from any other notifiable characteristic
DEBUG_TO_FILE   = True   # Mirror all debug output to a .log file
# ──────────────────────────────────────────────────────────────────────────────


def debug_log(msg: str):
    """Print debug message and optionally write to log file."""
    print(msg)
    if DEBUG_TO_FILE and debug_file:
        debug_file.write(msg + "\n")
        debug_file.flush()


def create_output_files():
    """Create binary, CSV, and debug log output files."""
    global binary_file, csv_file, debug_file, csv_writer, output_filename

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    # Binary file for ROS 2 sync
    output_filename = os.path.join(OUTPUT_DIR, f"hlto_{timestamp}.bin")
    binary_file = open(output_filename, "wb")
    binary_file.write(b"HLTODATA")          # 8-byte magic
    binary_file.write(struct.pack("<I", 1)) # version 1

    # CSV file for easy viewing
    csv_path = os.path.join(OUTPUT_DIR, f"hlto_{timestamp}.csv")
    csv_file = open(csv_path, "w", newline="", encoding="utf-8")
    csv_writer = csv.writer(csv_file)
    csv_writer.writerow([
        "timestamp", "unix_ms", "heart_rate",
        "respiration_rate", "temperature", "spo2", "rssi"
    ])

    # Debug log file
    if DEBUG_TO_FILE:
        log_path = os.path.join(OUTPUT_DIR, f"hlto_{timestamp}.log")
        debug_file = open(log_path, "w", encoding="utf-8")
        debug_log(f"[DEBUG] Session started at {timestamp}")
        print(f"📁 Debug log: {log_path}")

    print(f"📁 Binary: {output_filename}")
    print(f"📁 CSV:    {csv_path}")


def write_frame(rssi: int = -50):
    """Write current values to binary and CSV files."""
    global frame_count

    if binary_file is None:
        return

    frame_count += 1
    unix_time_ms = int(time.time() * 1000)
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

    spo2_val = 255 if latest_spo2 is None else latest_spo2

    # ── Binary (ROS 2 compatible) ──────────────────────────────────────────────
    binary_file.write(struct.pack("<I", frame_count))               # frame_index
    binary_file.write(struct.pack("<q", unix_time_ms))              # unix_time_ms
    binary_file.write(struct.pack("<B", latest_heart_rate))         # heart_rate
    binary_file.write(struct.pack("<B", latest_respiration_rate))   # respiration_rate
    binary_file.write(struct.pack("<f", latest_temperature))        # temperature
    binary_file.write(struct.pack("<B", spo2_val))                  # spo2
    binary_file.write(struct.pack("<b", rssi))                      # rssi

    # ── CSV ───────────────────────────────────────────────────────────────────
    if csv_writer:
        csv_writer.writerow([
            timestamp,
            unix_time_ms,
            latest_heart_rate,
            latest_respiration_rate,
            f"{latest_temperature:.2f}",
            latest_spo2 if latest_spo2 != 255 else "reading...",
            rssi,
        ])

    if frame_count % 10 == 0:
        binary_file.flush()
        csv_file.flush()


def make_notification_handler(char_uuid: str, char_name: str = "", rssi_ref: list = None):
    """
    Create notification handler for BLE characteristics.

    rssi_ref: a mutable list [rssi_value] so the run() loop can keep it updated.
    """
    global latest_heart_rate, latest_respiration_rate, latest_temperature, latest_spo2

    CUSTOM_LOG_UUID = "40af0003-9479-43f6-ae95-c45fb2afb9d2"
    HR_UUID         = "00002a37-0000-1000-8000-00805f9b34fb"
    TEMP_UUID       = "00002a1c-0000-1000-8000-00805f9b34fb"

    if rssi_ref is None:
        rssi_ref = [-50]

    def handler(sender: int, data: bytearray):
        global latest_heart_rate, latest_respiration_rate, latest_temperature, latest_spo2

        ts   = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
        rssi = rssi_ref[0]

        # ── 1) Vendor DSP text stream ──────────────────────────────────────────
        if char_uuid == CUSTOM_LOG_UUID:
            try:
                text = data.decode("ascii", errors="replace")
            except Exception as e:
                debug_log(f"[{ts}][DSP] Decode error: {e} | raw: {data.hex()}")
                return

            if DEBUG_RAW_DSP:
                # Print the raw text so you can see exactly what the device sends
                debug_log(f"[{ts}][DSP RAW] >>> {repr(text)}")

            for line in text.splitlines():
                line = line.strip()
                if not line:
                    continue

                # ── SpO2 ──────────────────────────────────────────────────────
                # Device may use various prefixes — catch them all
                line_lower = line.lower()
                if "spo2" in line_lower or "sp02" in line_lower:
                    debug_log(f"[{ts}][DSP] SpO2 candidate line: {repr(line)}")
                    try:
                        # Try splitting on common delimiters: ':', '=', ' '
                        for delim in (":", "="):
                            if delim in line:
                                after = line.split(delim, 1)[1].strip()
                                val = float(after.split()[0])
                                if 0 <= val <= 100:
                                    latest_spo2 = int(val)
                                    print(f"[{ts}] ✅ SpO2: {latest_spo2}%")
                                    write_frame(rssi)
                                    break
                        else:
                            # No delimiter — try parsing the last token
                            val = float(line.split()[-1])
                            if 0 <= val <= 100:
                                latest_spo2 = int(val)
                                print(f"[{ts}] ✅ SpO2: {latest_spo2}%")
                                write_frame(rssi)
                    except Exception as e:
                        debug_log(f"[{ts}][DSP] SpO2 parse failed: {e} | line: {repr(line)}")

                # ── Respiration Rate ───────────────────────────────────────────
                elif "rr" in line_lower or "resp" in line_lower or "breath" in line_lower:
                    debug_log(f"[{ts}][DSP] RR candidate line: {repr(line)}")
                    try:
                        for delim in (":", "="):
                            if delim in line:
                                after = line.split(delim, 1)[1].strip()
                                val = int(float(after.split()[0]))
                                if 0 < val < 60:
                                    latest_respiration_rate = val
                                    print(f"[{ts}] ✅ RR: {latest_respiration_rate} br/min")
                                    write_frame(rssi)
                                    break
                        else:
                            val = int(float(line.split()[-1]))
                            if 0 < val < 60:
                                latest_respiration_rate = val
                                print(f"[{ts}] ✅ RR: {latest_respiration_rate} br/min")
                                write_frame(rssi)
                    except Exception as e:
                        debug_log(f"[{ts}][DSP] RR parse failed: {e} | line: {repr(line)}")

                # ── Log any other DSP line we don't yet handle ─────────────────
                else:
                    debug_log(f"[{ts}][DSP UNHANDLED] {repr(line)}")

        # ── 2) Standard Heart Rate (0x2A37) ────────────────────────────────────
        elif char_uuid == HR_UUID:
            if DEBUG_RAW_HR:
                debug_log(f"[{ts}][HR RAW] {data.hex()}  len={len(data)}")

            if len(data) >= 2:
                flags = data[0]
                if (flags & 0x01) and len(data) >= 3:
                    latest_heart_rate = int.from_bytes(data[1:3], byteorder="little")
                else:
                    latest_heart_rate = data[1]

                # RR intervals are sometimes embedded in the HR packet (bytes 3+)
                if len(data) >= 5 and (flags & 0x10):
                    rr_raw = int.from_bytes(data[3:5], byteorder="little")
                    rr_sec = rr_raw / 1024.0
                    debug_log(f"[{ts}][HR] Embedded RR interval: {rr_raw} ({rr_sec:.3f}s)")

                print(f"[{ts}] HR: {latest_heart_rate} bpm")
                write_frame(rssi)

        # ── 3) Standard Temperature (0x2A1C) ───────────────────────────────────
        elif char_uuid == TEMP_UUID:
            if DEBUG_RAW_TEMP:
                debug_log(f"[{ts}][TEMP RAW] {data.hex()}  len={len(data)}")

            if len(data) >= 5:
                flags    = data[0]
                mantissa = int.from_bytes(data[1:4], byteorder="little", signed=True)
                exponent = int.from_bytes(data[4:5], byteorder="little", signed=True)
                latest_temperature = mantissa * (10 ** exponent)
                unit = "°F" if (flags & 0x01) else "°C"
                print(f"[{ts}] Temp: {latest_temperature:.2f}{unit}")
                write_frame(rssi)
            else:
                debug_log(f"[{ts}][TEMP] Packet too short ({len(data)} bytes): {data.hex()}")

        # ── 4) Unknown characteristics ──────────────────────────────────────────
        else:
            if DEBUG_UNKNOWN:
                label = char_name or char_uuid
                try:
                    as_text = data.decode("ascii", errors="replace")
                except Exception:
                    as_text = ""
                debug_log(
                    f"[{ts}][UNKNOWN {label}] hex={data.hex()}  "
                    f"len={len(data)}  text={repr(as_text)}"
                )

    return handler


async def find_hlto(timeout: float = 10.0):
    """Scan for HLTO device."""
    print(f"🔍 Scanning for {timeout} seconds...")
    devices = await BleakScanner.discover(timeout=timeout)

    candidate = None
    for d in devices:
        name = d.name or ""
        if TARGET_NAME_KEYWORD.lower() in name.lower():
            candidate = d
            print(f"  Found: {d.name} ({d.address})")

    if candidate is None:
        print(f"❌ Could not find device with name containing: {TARGET_NAME_KEYWORD}")
        return None

    print(f"\n✅ Using device: {candidate.name} ({candidate.address})")
    return candidate


async def run():
    """Main function."""
    device = await find_hlto()
    if device is None:
        return

    create_output_files()

    # Mutable RSSI reference updated every second
    rssi_ref = [-50]

    print("\n📡 Connecting...")
    async with BleakClient(device) as client:
        print(f"✅ Connected: {client.is_connected}")

        # Get services
        if hasattr(client, "get_services"):
            services = await client.get_services()
        else:
            services = client.services

        if services is None:
            print("❌ Could not get GATT services")
            return

        # ── Print full GATT map for debugging ─────────────────────────────────
        print("\n📋 GATT Service/Characteristic Map:")
        for service in services:
            print(f"  Service: {service.uuid}  ({service.description if hasattr(service, 'description') else ''})")
            for char in service.characteristics:
                print(f"    └─ Char: {char.uuid}  props={char.properties}  "
                      f"({'desc: ' + char.description if hasattr(char, 'description') else ''})")

        # ── Subscribe to all notifiable characteristics ───────────────────────
        notifiable_chars = []
        for service in services:
            for char in service.characteristics:
                if "notify" in char.properties:
                    notifiable_chars.append(char)

        if not notifiable_chars:
            print("❌ No notifiable characteristics found")
            return

        print(f"\n📊 Subscribing to {len(notifiable_chars)} characteristics...")
        for char in notifiable_chars:
            char_name = getattr(char, "description", "") or char.uuid
            handler = make_notification_handler(char.uuid, char_name, rssi_ref)
            await client.start_notify(char.uuid, handler)
            print(f"  ✅ Subscribed: {char_name} ({char.uuid})")

        print("\n🎧 Listening for data... Press Ctrl+C to stop.\n")
        print("-" * 60)

        try:
            while True:
                await asyncio.sleep(1.0)

                # ── Live RSSI polling ──────────────────────────────────────────
                # Bleak doesn't expose RSSI during connection on all platforms,
                # but try anyway and fall back silently.
                try:
                    rssi = await client.get_rssi()
                    if rssi is not None:
                        rssi_ref[0] = rssi
                except Exception:
                    pass  # Not supported on all backends; that's fine

        except KeyboardInterrupt:
            print("\n\n🛑 Stopping...")
        finally:
            for char in notifiable_chars:
                try:
                    await client.stop_notify(char.uuid)
                except Exception:
                    pass

    cleanup()


def cleanup():
    """Close all output files."""
    global binary_file, csv_file, debug_file, frame_count, output_filename

    if binary_file:
        binary_file.flush()
        binary_file.close()
    if csv_file:
        csv_file.flush()
        csv_file.close()
    if debug_file:
        debug_file.flush()
        debug_file.close()

    print(f"\n✅ Saved {frame_count} frames to {output_filename}")


if __name__ == "__main__":
    try:
        asyncio.run(run())
    except KeyboardInterrupt:
        print("\n🛑 Stopped by user.")
    finally:
        cleanup()