# HeraLeto-Earbuds
Linux Python Code for connection with Hera-Leto Temperature and Oxygen Earbuds, for more info 
https://www.heraleto.com

# HLTO_Readings
Tested and ran on Raspberry Pi 400. Should work for any Raspberry device or Linux device running Python.
Ensure Bluetooth is working on device and simply switch on the Earbuds and wait for the readings

# HLTO_Comparison_Polar10
Also tested and ran on Raspberry Pi 400. Should work for any Raspberry device or Linux device running Python.
https://www.polar.com/nz-en/sensors/h10-heart-rate-sensor?srsltid=AfmBOoo6TAi25C2elllqJCzcLfU5cah0Q8f-iBLRsnMdSuPcNoUc0RIe
Have bluetooth enabled on device and have both the earbuds and cheststrap switched on.
Device will read the data and output data that was read at the exact same time for better comparisons, will create a csv file where all the data is stored according to the time read from the corrosponding devices.

# Android_java
Ran on windows for Android Studio, Builds an app for Android devices that connects to the Hera Leto earbuds and extracts all the data provided, to send through RFCOMM to a Linux System (Tested with Raspberry Pi 400 but should work with any other device).
