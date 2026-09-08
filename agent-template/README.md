# AndroLink Agent
Tahap 1: agen Android untuk identitas perangkat, pairing, session dan heartbeat.

Server default: https://androlink.xo.je/api/

Catatan: kode pairing pada UI disiapkan, tetapi endpoint `pair_device.php` pada backend lama menerima `device_id` dan `device_token`. Integrasi kode pairing harus disatukan di backend final agar kode benar-benar divalidasi.

Buka folder ini di Android Studio, sync Gradle, lalu build APK.
