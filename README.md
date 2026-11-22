# StempelKam

Kamera timestamp untuk Android: setiap foto otomatis diberi watermark berisi waktu, tanggal, hari, alamat, koordinat GPS, ketinggian, dan catatan.

## Fitur
- Pratinjau watermark langsung di layar kamera (jam berjalan tiap detik)
- 3 gaya watermark: **Klasik**, **Kartu** (laporan lapangan), **Minimal**
- Alamat otomatis dari GPS (Geocoder, perlu internet), plus koordinat dan ketinggian
- Catatan / nama proyek, dengan pilihan tampil atau sembunyi untuk tiap informasi
- Flash (Mati/Nyala/Auto), kamera depan/belakang, suara shutter
- Foto tersimpan di Galeri › Pictures/StempelKam, dengan EXIF GPS & waktu
- Pengaturan tersimpan otomatis

## Build APK lewat GitHub (tanpa Android Studio)
1. Buat repo baru di GitHub, lalu upload seluruh isi folder ini (termasuk folder `.github`).
2. Buka tab **Actions**; workflow "Build APK" berjalan otomatis (±5 menit).
3. Setelah centang hijau, buka run tersebut lalu unduh artifact **StempelKam-apk**. Ekstrak zip-nya untuk mendapatkan `app-debug.apk`.
4. Instal di HP (izinkan "Instal dari sumber tidak dikenal").

## Build dengan Android Studio
Buka folder ini lalu tekan Run. Butuh JDK 17 dan Android SDK 34.

Min. Android 7.0 (API 24).
