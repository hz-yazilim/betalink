# Betalink ürün ve rakip denetimi

Denetim tarihi: 8 Kasım 2026
Kapsam: GitHub `main`, canlı Supabase şeması/RLS/advisor sonuçları ve herkese açık rakip/Google Play dokümantasyonu.

## Ürün özeti

Betalink; Android uygulama geliştiricileri ile gerçek kapalı test katılımcılarını buluşturan, hem tester hem geliştirici rolünün aynı hesapta kullanılabildiği yerel bir topluluk ürünüdür.

## Mevcut güçlü taraflar

- Native Android / Jetpack Compose; WebView değil.
- E-posta ve Google ile oturum açma, güvenli oturum saklama.
- Cihaz modeli ve Android sürümüne göre kampanya uyumluluğu.
- Kampanya oluşturma, başvuru, geliştirici onayı, bekleme listesi ve yedek tester akışı.
- Play testine katılım beyanı, 12 tester eşiği ve 14 günlük kampanya takibi.
- Görev listesi, tester gönderimi, geliştirici incelemesi, değişiklik isteme ve yeniden gönderim.
- Puanlı geri bildirim, geliştirici yanıtı, kullanıcı şikâyeti ve engelleme.
- 15 onaylı tester üzerinden çalışan ayrı grup sayacı; grup lideri 15 tester sayısına dahil değil.
- Grup e-posta paylaşımında açık rıza; izin geri alınabiliyor.
- Grup günlük check-in altyapısı; bu denetimde eksik ekran bağlantısı tamamlandı.
- BP cüzdanı, sunucu tarafı bakiye kontrolü, işlem defteri ve ilk kayıt 10 BP bonusu.
- Uygulama içi bildirim kutusu, destek/şikâyet, admin paneli.
- Uygulama içi hesap silme ve harici hesap silme sayfası; gizlilik politikası.

## Rakiplerden görülen ürün desenleri

| Rakip / kategori | Öne çıkan yaklaşım | Betalink durumu |
| --- | --- | --- |
| AndroidBetaTesting | Karşılıklı tester topluluğu, doğrulanmış katılım/puan, özel test bağlantıları, ayrı test turları ve geri bildirim | Karşılıklı kampanya/grup mantığı ve BP var. Katılım puanı/itibar var; fakat Google Play opt-in doğrulaması harici sistemden otomatik alınmıyor. |
| TestCircle | 14 günlük turu birkaç ayrı geri bildirim aşamasına bölme ve test sonunda özet rapor | Görev ve geri bildirim akışı var; standartlaştırılmış aşama planı ve otomatik test özeti eksik. |
| Testers Community | Sabit tester paketleri ve günlük katılım disiplini | 15 kişilik grup ve günlük check-in temeli var; otomatik devamsızlık uyarısı/üyeyi değiştirme akışı geliştirilmeli. |
| BetaTesting / UserTesting / Centercode | Hedef kitleye göre tester bulma, video/oturum geri bildirimi ve daha gelişmiş araştırma raporları | Betalink'in ücretsiz karşılıklı topluluk modeli farklı. Video kayıt, panel üzerinden profesyonel tester bulma ve araştırma raporu mevcut değil; bunlar ilk sürüm için zorunlu değil. |

Kaynaklar: AndroidBetaTesting, TestCircle, Testers Community ve beta test platformları karşılaştırması (aşağıdaki bağlantılar).

## Bu turda uygulanan iyileştirmeler

1. **Grup günlük test bildirimi ekrana bağlandı.** Kullanıcı çalışan grup testinde günlük katılımını bildirebiliyor; aynı gün tekrar bildirim engelleniyor ve son bildirim tarihleri gösteriliyor.
2. **Grup araması eklendi.** Grup adı ve açıklamasında arama yapılabiliyor.
3. **Kampanya araması eklendi.** Uygulama adı, açıklama ve kategoriye göre keşif yapılabiliyor.
4. **Grup check-in RPC erişimi sıkılaştırıldı.** Anonim rolün çalıştırma izni kaldırıldı; yalnızca giriş yapmış kullanıcıya verildi. Fonksiyon, onaylı grup üyeliğini sunucu tarafında kontrol etmeye devam ediyor.
5. **Grup check-in sorgusu için eksik kullanıcı indeksi eklendi.**
6. **Google Play Android 16 hedef API şartına geçildi.** Proje compileSdk/targetSdk 36'ya yükseltildi; CI Android 36 platformu ve build-tools ile güncellendi.
7. **Kullanım koşulları ve topluluk kuralları eklendi.** Yeni kullanıcı, uygulama ekranlarına geçmeden önce koşulları okuyup onaylamak zorunda; kabul edilen sürüm profilde zaman damgasıyla saklanıyor. Koşullar profil ekranından tekrar açılabiliyor ve herkese açık web sayfası da yayınlandı.

## Hâlâ geliştirilmesi gereken ürün işleri

### Öncelik 1 — güven ve test kalitesi
- Günlük check-in'i gerçek kullanım kanıtı gibi göstermemek: bu kayıt tester beyanıdır. Google Play Console katılımı ve uygulama kullanımı otomatik doğrulanmıyor.
- Kampanya ve grup için son 7/14 gün katılım görünümü, devamsızlık uyarısı ve yedek tester çağırma akışı.
- Tester güvenilirlik puanını açıklayan profil: tamamlanan test, zamanında check-in, onaylanan görev, bırakılan test gibi ayrı metrikler.
- Test sonunda standart rapor: bulunan hatalar, cihaz/Android dağılımı, görev tamamlama, tester geri bildirimleri ve geliştirici yanıtları.

### Öncelik 2 — grup deneyimi
- Grup kartlarında doluluk/üye sayısı, kalan tester, sayaç durumu ve son aktivite özeti.
- Grup sahibinin onaylı üyeyi çıkarabilmesi ve yedek üyeyi çağırabilmesi.
- Üye listesinde UUID yerine izin verilen görünen ad ve katkı özeti.
- Grup daveti/hatırlatması için uygulama içi bildirim; FCM kurulana kadar uygulama kapalıyken push gönderilemeyeceği açıkça belirtilmeli.

### Öncelik 3 — geliştirici deneyimi
- Kampanya oluşturma sırasında daha net test brief'i: test edilecek özellikler, zorunlu görevler, bilinen sorunlar, desteklenen cihazlar ve geri bildirim beklentisi.
- Geliştirici panelinde başvuru/aktif/yedek/tamamlanan tester sayıları ve tek ekranda test ilerlemesi.
- Hata bildirimi için tür, önem derecesi, tekrar adımları ve beklenen/gerçekleşen sonuç alanları.
- Test sonunda dışa aktarılabilir özet rapor.

### Öncelik 4 — teknik kalite
- FCM push teslimatı henüz yapılandırılmadı.
- Supabase Auth'ta sızdırılmış parola koruması kapalı görünüyor; Auth ayarlarından açılmalı.
- Gerçek cihazlarda Android 16 edge-to-edge, küçük ekran, klavye, geri tuşu, çevrimdışı/ağ hatası ve hesap silme senaryoları test edilmeli.
- Admin, destek ve grup yönetimi işlemlerine ait hata/başarı durumları gerçek hesaplarla uçtan uca test edilmeli.

## Google Play yayın kontrol listesi

- [x] Uygulama içi hesap silme akışı var.
- [x] Harici hesap silme URL'si ve gizlilik politikası sayfası var.
- [x] Uygulama içinde gizlilik politikasına erişim var.
- [x] Şikâyet/raporlama ve kullanıcı engelleme akışları var.
- [x] Play testine katılımın uygulama içi beyan olduğu, Console doğrulaması olmadığı açıklanıyor.
- [x] Android 16 / API 36 hedef SDK'ya geçildi.
- [ ] Play Console Veri Güvenliği ve hesap silme beyanları, gerçek SDK/veri akışına göre doldurulmalı.
- [x] UGC/topluluk kuralları uygulama içinde zorunlu olarak onaylatılıyor; profil ekranından yeniden açılabiliyor.
- [ ] Moderasyon, raporlama ve engelleme akışı gerçek hesaplarla uçtan uca test edilmeli.
- [ ] İçerik derecelendirmesi, hedef kitle, reklam beyanı, uygulama erişim bilgileri ve gizlilik URL'si Play Console'da tamamlanmalı.
- [ ] Mağaza simgesi, telefon ekran görüntüleri, kısa/tam açıklama ve gerekiyorsa özellik grafiği tamamlanmalı.
- [ ] İmzalı release AAB oluşturulmalı; debug APK Play Store yüklemesi değildir.
- [ ] Yeni kişisel geliştirici hesabı kapsamındaysa en az 12 tester, aynı kapalı testte kesintisiz 14 gün opt-in kalmalı; sonrasında production access başvurusu yapılmalı.
- [ ] Play Console pre-launch raporu ve gerçek Android cihaz testleri tamamlanmalı.
- [ ] Play incelemesi için çalışan, kısıtlı yetkili bir test hesabı ve inceleme talimatı sağlanmalı.

## Önemli doğruluk notları

- Betalink'in kampanya takip sayacı ve günlük check-in kayıtları Google Play Console verisi değildir. Kullanıcı beyanını ve uygulama içi süreçleri kaydeder.
- 15 kişilik grup eşiği Betalink'in kendi grup kuralıdır; Google'ın yeni kişisel geliştirici hesapları için istediği minimum 12 tester şartından ayrıdır.
- Bu denetim kod, canlı veritabanı şeması, Supabase advisor çıktıları ve kamuya açık dokümanları kapsar. Gerçek cihazda test, Play Console içeriği ve imzalı AAB doğrulaması yerine geçmez.

## Referanslar

- https://androidbetatesting.com/
- https://play.google.com/store/apps/details?id=com.greenant.testcircle
- https://www.testerscommunity.com/blog/best-google-play-testing-communities
- https://www.koji.so/visuals/beta-testing-platforms-best-use-table
- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/13327111
- https://support.google.com/googleplay/android-developer/answer/11926878
- https://support.google.com/googleplay/android-developer/answer/9876937
