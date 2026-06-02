# EarlyNotify

Huawei Watch GT6 (ve Huawei Health'e veri gönderen diğer saatler) için **anlık yüksek nabız
alarmı** veren küçük bir Android uygulaması.

Huawei Health'in yerleşik uyarısı yalnızca nabız **100 üstünde 5-10 dakika** kaldığında
bildirim atar ve eşik 100'ün altına indirilemez. EarlyNotify, **kendi belirlediğiniz eşikte
(örn. 80 bpm)** ve **kendi seçtiğiniz kurala göre** anlık olarak alarm üretir.

## Özellikler

- Kullanıcı tanımlı eşik (varsayılan **80 bpm**)
- Üç tetik kuralı — uygulamadan seçilir:
  - **Anında** — eşiği aşan ilk ölçümde alarm
  - **N ardışık** — son N ölçümün hepsi eşik üstündeyse
  - **Süre boyunca** — eşik üstünde kesintisiz X saniye kalınca
- Tekrar susturma (cooldown), bildirim kontrol sıklığı, alarm tipi (sadece bildirim /
  titreşim / ses + titreşim) ayarlanabilir
- Arka planda çalışan foreground servis + cihaz yeniden başlatıldığında otomatik devam
- **İki veri kaynağı:**
  - **Health Connect** — Huawei Health'in senkronladığı nabız kayıtlarını okur (kurulumu kolay,
    senkron birkaç dakika gecikebilir)
  - **Erişilebilirlik (Accessibility)** — Huawei Health ekranındaki anlık nabzı okur (en hızlı,
    ekranın açık olmasını gerektirir)
- Tüm veriler yalnızca cihazda işlenir; hiçbir yere gönderilmez.

> **Gecikme hakkında dürüst not:** Saniye-altı "tam anlık" uyarı yalnızca Huawei'nin resmi
> Health Kit SDK'sı ile garanti edilir; o ise Huawei Developer hesabı + imza kaydı gerektirir
> ve herkese açık derleme ile uyumsuzdur. Pratikte en düşük gecikmeyi **Accessibility kaynağı**
> verir.

## Kurulum

### 1. APK'yı edin
- **Hazır APK:** Depodaki **Actions** sekmesi → en son başarılı build → `earlynotify-debug-apk`
  artifact'ını indir. (Veya bir `v*` etiketi push edilmişse **Releases** bölümünden.)
- APK'yı telefona kopyalayıp aç; "bilinmeyen kaynaklardan yükleme"ye izin ver.

### 2. İzinleri ver (uygulama içindeki "Kurulum & İzinler" kartı yönlendirir)
1. **Bildirim izni**
2. **Health Connect** — yüklü değilse uygulama Play Store'a yönlendirir; sonra "nabız okuma"
   iznini ver. Ardından **Huawei Health → Ayarlar → Health Connect** bağlantısını etkinleştir.
3. **Erişilebilirlik** — Ayarlar'da "EarlyNotify — Huawei Health nabız okuyucu" servisini aç
   (en hızlı uyarı için Huawei Health'in nabız ekranı açıkken çalışır).
4. **Pil optimizasyonu muafiyeti** — servisin arka planda öldürülmemesi için.

### 3. Ayarla ve başlat
Eşiği ve kuralı seç, **"İzleme aktif"** anahtarını aç. "Test alarmı çal" ile alarmı doğrula.

## Geliştirme

```bash
./gradlew testDebugUnitTest   # ThresholdEvaluator birim testleri
./gradlew assembleDebug       # APK: app/build/outputs/apk/debug/app-debug.apk
```

Her `main` push'unda GitHub Actions otomatik test + APK build yapar. Bir sürüm yayınlamak için
`vX.Y.Z` etiketi push et; APK otomatik olarak Release'e eklenir.

## Mimari

```
GT6 ──BT──> Huawei Health ──┬─> Health Connect ──poll──> HealthConnectSource ─┐
                            └─> ekran UI ──Accessibility──> HuaweiHealth...Service ─┤
                                                                                    ▼
                                                                          MonitorService
                                                                       (kayan ölçüm penceresi)
                                                                                    ▼
                                                                        ThresholdEvaluator (saf kural)
                                                                                    ▼
                                                                      AlarmNotifier (bildirim/ses/titreşim)
```

- `logic/ThresholdEvaluator.kt` — saf, test edilebilir kural mantığı
- `data/` — ayarlar (DataStore), veri kaynakları, süreç-içi veri yolu
- `service/MonitorService.kt` — kaynakları birleştiren foreground servis
- `alarm/AlarmNotifier.kt` — bildirim kanalları ve alarm
- `ui/` — Jetpack Compose ekranları

## Lisans

Kişisel kullanım için. Sağlık amaçlı tıbbi bir cihaz değildir; acil durumlar için güvenmeyin.
