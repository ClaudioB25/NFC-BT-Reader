# NFC Bluethoot Reader
A  simple cordova plugin to read Mifare RFID tags with an external NFC Bluethoot reader from Android.

I adjusted existing JAVA library to force android device to mantain bluethoot connection.
To avoid bluethoot generic connection lost errors, android device will try to connect each 2 minutes.

// TODO install e uso

To debug bluethoot nfc reader run:
adb logcat bizcode:D SystemWebViewClient:D *:S 

To get this plugin working correctly, after you install your app, you have to give Localization Access and keep Localization On due to BLE requirement.

Currently i support only [acr1255u-j1](https://www.acs.com.hk/en/products/403/acr1255u-j1-acs-secure-bluetooth%C2%AE-nfc-reader/).

Resources used [here](https://www.acs.com.hk/en/products/403/acr1255u-j1-acs-secure-bluetooth%C2%AE-nfc-reader/)
