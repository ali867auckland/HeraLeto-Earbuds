# WBD101_HLTO_Demo_Android

Well Being Digital Limited  
January 2024  

## Summary  

This demo Android app is written in Java, it's functions are to:

1) Perform Bluetooth Low Energy (BLE) scans to look for nearby Hera Leto devices.  
2) Connect to a device and subscribe to the relevant Bluetooth services (Heart Rate Serivce, Temperature Service).
3) Extract information from the advertising data packets and feed the device data into our C API to extract HRV features.
4) Save the raw data as well as API output variables to a local file in CSV format.
5) Maintain the BLE device connection in the event of earphone disconnection.

The Java files for the demp app are located in the following directory:

> WBD101_HLTO_Demo_Android/app/src/main/java/...

and the main file containing the BLE data processing logic is located at:

> WBD101_HLTO_Demo_Android/app/src/main/java/com/wbd101/hlt010/BleOperations.java