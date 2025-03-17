import asyncio
import multiprocessing
import pandas as pd
from bleak import BleakClient, BleakScanner
from datetime import datetime

# MAC Addresses
DEVICE_1_MAC = "C0:22:19:03:01:CC"
DEVICE_2_MAC = "A0:9E:1A:E3:5A:41"

HR_UUID = "00002a37-0000-1000-8000-00805f9b34fb"

# Shared queue for synchronization
data_queue = multiprocessing.Queue()
csv_filename = "synchronized_hr_data.csv"  # CSV file name

def advertisement_callback(device, advertisement_data):
    """
    Processes BLE advertisement packets and filters based on DEVICE_1_MAC.
    Stores data in the queue for synchronization.
    """
    if device.address != DEVICE_1_MAC:
        return  

    current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    if advertisement_data.manufacturer_data:
        for manufacturer_id, raw_data in advertisement_data.manufacturer_data.items():
            if len(raw_data) >= 12:
                heart_rate = raw_data[3]  
                respiration_rate = raw_data[5]  
                temperature = int.from_bytes(raw_data[10:12], byteorder="little") / 100.0  
                spo2 = raw_data[-5] if raw_data[-5] != 151 else 0

                # Send scanned data to queue
                data_queue.put({
                    "timestamp": current_time,
                    "type": "scan",
                    "heart_rate": heart_rate,
                    "respiration_rate": respiration_rate,
                    "temperature": temperature,
                    "spo2": spo2
                })

def run_scan(queue):
    """
    Runs scanning as a separate process and stores data in the queue.
    """
    global data_queue
    data_queue = queue  # Share queue

    async def scan_ble():
        scanner = BleakScanner(detection_callback=advertisement_callback)
        await scanner.start()
        await asyncio.sleep(180)
        await scanner.stop()

    asyncio.run(scan_ble())

async def collect_hr_data(queue):
    """
    Connects to DEVICE_2_MAC and collects heart rate data.
    Stores HR data in the queue.
    """
    async with BleakClient(DEVICE_2_MAC) as client:
        if await client.is_connected():
            print(f"Connected to {DEVICE_2_MAC}")

            def hr_callback(sender, data):
                current_time = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                hr_value = data[1]

                # Send HR data to queue
                queue.put({
                    "timestamp": current_time,
                    "type": "hr",
                    "heart_rate": hr_value
                })

            await client.start_notify(HR_UUID, hr_callback)

            try:
                while True:
                    await asyncio.sleep(5)  
            except asyncio.CancelledError:
                await client.stop_notify(HR_UUID)

def synchronize_and_save(queue, csv_filename):
    """
    Checks queue and saves data to CSV only when both devices have matching timestamps.
    """
    scanned_data = None
    hr_data = None

    # Create DataFrame and CSV file header if it doesn't exist
    try:
        pd.read_csv(csv_filename)
    except FileNotFoundError:
        pd.DataFrame(columns=["timestamp", "HLTO_HR", "HLTO_Respiration", "HLTO_Temperature", "HLTO_SpO2", "Polar10_HR"]).to_csv(csv_filename, index=False)

    while True:
        try:
            data = queue.get(timeout=20)  # Wait for data (timeout avoids deadlock)

            if data["type"] == "scan":
                scanned_data = data
            elif data["type"] == "hr":
                hr_data = data

            # Save only when timestamps match
            if scanned_data and hr_data and scanned_data["timestamp"] == hr_data["timestamp"]:
                print("\n✅ Synchronized Data Saved:")
                print(f"[{scanned_data['timestamp']}]")
                print(f"  - HLTO: HR={scanned_data['heart_rate']} bpm | Respiration={scanned_data['respiration_rate']} bpm | Temperature={scanned_data['temperature']} °C | SpO₂={scanned_data['spo2']}%")
                print(f"  - Polar10: HR={hr_data['heart_rate']} bpm")

                # Append data to CSV file
                df = pd.DataFrame([{
                    "timestamp": scanned_data["timestamp"],
                    "HLTO_HR": scanned_data["heart_rate"],
                    "HLTO_Respiration": scanned_data["respiration_rate"],
                    "HLTO_Temperature": scanned_data["temperature"],
                    "HLTO_SpO2": scanned_data["spo2"],
                    "Polar10_HR": hr_data["heart_rate"]
                }])
                df.to_csv(csv_filename, mode="a", header=False, index=False)

                # Reset data after saving
                scanned_data = None
                hr_data = None

        except multiprocessing.queues.Empty:
            continue  # Keep checking

async def main():
    queue = multiprocessing.Queue()  # Create shared queue

    # Start scanning process
    scan_process = multiprocessing.Process(target=run_scan, args=(queue,))
    scan_process.start()

    # Start the synchronization and saving process
    save_process = multiprocessing.Process(target=synchronize_and_save, args=(queue, csv_filename))
    save_process.start()

    # Run HR data collection in main process
    await collect_hr_data(queue)

    scan_process.join()  # Wait for scanning to finish
    save_process.terminate()  # Stop saving process

if __name__ == "__main__":
    asyncio.run(main())
