package com.hzyazilim.betalink.ui.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Dialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

enum class LegalDocument { TERMS, PRIVACY, ACCOUNT_DELETION }

@Composable
fun LegalDocumentsDialog(initialDocument: LegalDocument, onDismiss: () -> Unit) {
    var selected by remember(initialDocument) { mutableStateOf(initialDocument) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF0B1225)
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BETALINK · YASAL MERKEZ", color = Color(0xFF2EE6A8), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Bilgilendirme ve hakların", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDismiss) { Text("Kapat", color = Color(0xFFB8C3DD)) }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LegalDocument.values().forEach { item ->
                        val active = selected == item
                        Button(
                            onClick = { selected = item },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) Color(0xFF2EE6A8) else Color(0xFF18233D),
                                contentColor = if (active) Color(0xFF07101D) else Color(0xFFDCE4F8)
                            ),
                            shape = RoundedCornerShape(13.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(when (item) {
                                LegalDocument.TERMS -> "Kullanım koşulları"
                                LegalDocument.PRIVACY -> "Gizlilik · KVKK"
                                LegalDocument.ACCOUNT_DELETION -> "Hesap silme"
                            }, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .background(Color(0xFF111B32), RoundedCornerShape(18.dp))
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    val title = when (selected) {
                        LegalDocument.TERMS -> "Kullanım Koşulları ve Topluluk Kuralları"
                        LegalDocument.PRIVACY -> "Gizlilik Politikası ve KVKK Aydınlatması"
                        LegalDocument.ACCOUNT_DELETION -> "Hesap ve Veri Silme"
                    }
                    Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(when (selected) {
                        LegalDocument.TERMS -> "Sürüm: 8 Kasım 2026"
                        LegalDocument.PRIVACY -> "Son güncelleme: 10 Kasım 2026"
                        LegalDocument.ACCOUNT_DELETION -> "Son güncelleme: 6 Kasım 2026"
                    }, color = Color(0xFF9AA8C4), fontSize = 10.sp)
                    val body = when (selected) {
                        LegalDocument.TERMS -> TERMS_TEXT
                        LegalDocument.PRIVACY -> PRIVACY_TEXT
                        LegalDocument.ACCOUNT_DELETION -> DELETION_TEXT
                    }
                    body.trimIndent().lines().filter { it.isNotBlank() }.forEach { line ->
                        when {
                            line.startsWith("# ") -> Text(line.removePrefix("# "), color = Color(0xFF2EE6A8), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 7.dp))
                            line.startsWith("• ") -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", color = Color(0xFF2EE6A8), fontWeight = FontWeight.Bold)
                                Text(line.removePrefix("• "), color = Color(0xFFD5DDF0), fontSize = 12.sp, lineHeight = 18.sp)
                            }
                            line.startsWith("NOT: ") -> Text(line.removePrefix("NOT: "), color = Color(0xFF9AA8C4), fontSize = 10.sp, lineHeight = 15.sp)
                            else -> Text(line, color = Color(0xFFD5DDF0), fontSize = 12.sp, lineHeight = 18.sp)
                        }
                    }
                    Text("Hizmet sağlayıcı: Hz Yazılım · İletişim: hz.yazilimm@gmail.com", color = Color(0xFF8795B3), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Text("Bu belgeleri uygulamadan ayrılmadan okuyabilirsin.", color = Color(0xFF8795B3), fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

private const val TERMS_TEXT = """
Betalink; uygulama geliştiricileri ile uygulamalarını gerçek koşullarda test eden katılımcıları buluşturan bir topluluk platformudur. Hesap oluşturarak veya hizmeti kullanmaya devam ederek bu koşulları kabul etmiş olursun.

# 1. Doğru ve dürüst kullanım
• Hesabını yalnızca kendin kullan; başkasının kimliğine bürünme.
• Test başvurularında cihazın ve uygunluğun hakkında doğru bilgi ver.
• Katıldığını beyan ettiğin Google Play testine gerçekten katıl; uygulamayı makul ölçüde kullan ve dürüst geri bildirim sun.
• Google Play Console katılımını Betalink'in otomatik doğruladığını varsayma. Uygulama içindeki katılım ve günlük check-in kayıtları kullanıcı beyanıdır.

# 2. Yasaklanan içerik ve davranışlar
• Tehdit, taciz, zorbalık, nefret söylemi, hedef gösterme veya kişisel saldırı.
• Hukuka aykırı, yanıltıcı, dolandırıcılık amaçlı veya başkasının haklarını ihlal eden içerik.
• Spam, sahte test başvurusu, sahte check-in, yanıltıcı puan/ödül elde etme veya sistemi manipüle etme.
• Başka kullanıcıların kişisel verilerini izinsiz paylaşma.
• Google şifresi, doğrulama kodu veya Play Console giriş bilgisi isteme/paylaşma.
• Zararlı bağlantı, kötü amaçlı yazılım veya uygulama güvenliğini tehlikeye atan içerik.
• BetaPuan'ı nakit, gerçek para veya platform dışı bir ödeme aracı gibi sunma.

# 3. Kampanyalar ve tester grupları
• Kampanya ve gruplar gerçek test ihtiyacı için kullanılmalıdır.
• Geliştiriciler test kapsamını, görevleri ve beklentileri açıkça belirtmelidir.
• Tester'lar görevleri ve günlük bildirimleri yalnızca gerçekten yaptıklarında tamamlandı olarak işaretlemelidir.
• Grup e-posta paylaşımı isteğe bağlıdır. Yalnızca açıkça izin veren üyelerin test e-postaları ilgili grubun onaylı üyeleriyle paylaşılır.
• Betalink, Google Play'de yayın onayı veya üretim erişimi garantisi vermez.

# 4. BetaPuan
BetaPuan uygulama içi sanal puandır; nakde çevrilemez, devredilemez ve gerçek para değeri yoktur. Kazanım ve harcama koşulları uygulamada gösterilir. Hileli veya kötüye kullanım amaçlı işlemler incelenebilir ve kurallara göre geri alınabilir.

# 5. Şikâyet, engelleme ve moderasyon
Kullanıcılar uygunsuz içerik veya davranışı uygulama içinden bildirebilir ve diğer kullanıcıları engelleyebilir. Hz Yazılım bildirimleri inceleyebilir; içeriği kaldırabilir, üyeliği/grubu sonlandırabilir veya hesabı geçici ya da kalıcı olarak kısıtlayabilir. Acil güvenlik veya hukuki durumlarda gerekli işlemler yapılabilir.

# 6. Fikri mülkiyet ve içerik sorumluluğu
Paylaştığın içeriğe ilişkin hak ve izinlere sahip olmalısın. Başkasına ait marka, yazılım, görsel veya metinleri izinsiz kullanma. Paylaştığın içerik için sorumluluk sana aittir; Betalink hizmeti işletmek ve moderasyon yapmak amacıyla içeriği platform içinde barındırabilir ve gösterebilir.

# 7. Hizmetin sınırları
Betalink, üçüncü taraf uygulamaların, Google Play test listelerinin veya geliştirici hesaplarının sahibi değildir. Üçüncü taraf hizmetlerinin kullanılabilirliği ve kuralları ilgili sağlayıcıya bağlıdır. Bakım, güvenlik veya kötüye kullanım durumlarında özellikler geçici olarak sınırlandırılabilir.

# 8. Hesap ve veri silme
Hesabını uygulama içinden Profil bölümünde silebilirsin. Kişisel verilerinin işlenmesi hakkında bu ekrandaki Gizlilik · KVKK bölümünü inceleyebilirsin.

# 9. Koşulların güncellenmesi
Önemli değişikliklerde yeni sürüm gösterilir ve gerektiğinde yeniden onay istenir. Geçerli koşul sürümü uygulamada kayıt altına alınır.

# 10. İletişim
Kullanım koşulları, güvenlik veya moderasyonla ilgili sorular için hz.yazilimm@gmail.com adresine ulaşabilirsin.

NOT: Bu metin ürün ve topluluk kurallarını tanımlar; yayına çıkmadan önce Türkiye mevzuatı ve işletme modeline göre hukuk danışmanı tarafından gözden geçirilmelidir.
"""

private const val PRIVACY_TEXT = """
Bu politika, Betalink Android uygulaması ve ilgili hizmetleri için geçerlidir. Hizmeti sunan Hz Yazılım'dır. Betalink, uygulama geliştiricileri ile gerçek kapalı test katılımcılarını buluşturan bir topluluk platformudur.

# 1. İşlenen veriler
• Hesap ve kimlik: kayıt e-posta adresi, kimlik doğrulama kayıtları, görünen ad, hesap oluşturma bilgileri ve kabul edilen kullanım koşullarının sürüm/zaman damgası.
• Profil ve cihaz: kullanıcının eklediği profil fotoğrafı bağlantısı, ülke, cihaz modeli, Android sürümü, Google Play ülkesi ve isteğe bağlı biyografi/uygunluk bilgisi.
• Kampanya ve grup etkinliği: oluşturulan kampanyalar, başvurular, grup üyelikleri, test katılım durumları, günlük onaylar, görev gönderimleri ve zaman damgaları.
• İletişim ve güvenlik: uygulama içi geri bildirimler, geliştirici yanıtları, destek talepleri, şikâyetler, engelleme kayıtları, bildirimlerin okunma durumu ve kötüye kullanım incelemesi için gerekli kayıtlar.
• BetaPuan: sanal puan bakiyesi ve puan hareketleri. BetaPuan nakde çevrilemez; gerçek para veya ödeme bilgisi değildir.
• Kişiler ve grup davetleri: görünen adlar, davet tercihleri, davet eden/alıcı hesap bilgileri, davet notu, durumu ve zaman damgaları. Profil iletişim e-postası varsayılan olarak gizlidir; yalnızca kullanıcı halka açık gösterme iznini açarsa diğer oturum açmış kullanıcılara gösterilir. Hesap giriş e-postası halka açık dizinde gösterilmez.
• Yönetici erişimi: hesap e-posta adresleri yalnızca doğrulanmış platform yöneticisinin korunan yönetim panelinde hesap/destek yönetimi amacıyla görüntülenebilir.
• Google Play test e-postası: yalnızca bir test grubunda açıkça paylaşmayı seçersen, Google Play kapalı testinde kullandığın e-posta adresi o grubun onaylı üyelerine gösterilebilir.

# 2. Kullanım amaçları
Veriler; hesap ve oturum yönetimi, hesap güvenliği, kampanya/test/grup süreçleri, cihaz uyumluluğu eşleştirmesi, görev ve geri bildirim akışı, destek talepleri, kötüye kullanım incelemesi, BetaPuan işlemleri ve yasal yükümlülükler için kullanılır.

# 3. Paylaşım ve hizmet sağlayıcılar
Betalink kişisel verileri satmaz ve reklam hedeflemesi için kullanmaz. Veriler, hizmetin çalışması için kullanılan Supabase altyapı sağlayıcısı (kimlik doğrulama, veritabanı ve sunucu fonksiyonları) tarafından işlenebilir.
Kişiler dizininde görünen adlar oturum açmış Betalink kullanıcılarına gösterilir. Grup davetleri yalnızca davet eden ve davet edilen hesaplar tarafından görüntülenebilir. Profil iletişim e-postası varsayılan olarak gizlidir; görünürlüğünü sen kontrol edersin. Hesap giriş e-posta adresleri diğer kullanıcılara gösterilmez; yalnızca doğrulanmış platform yöneticisi tarafından yönetim panelinde görülebilir. Kampanya bilgileri ve geri bildirimler ilgili kampanya taraflarına, test organizasyonunun gerektirdiği ölçüde gösterilir. Google Play test e-posta adresin yalnızca açık rızan varsa ilgili grubun onaylı üyelerine gösterilir.
Betalink Google Play Console hesabına erişmez; Google şifreni, Play Console giriş bilgilerini veya Google hesabı doğrulama kodlarını istemez. Google Play bağlantılarına geçtiğinde Google'ın kendi hizmet ve gizlilik koşulları geçerlidir. Betalink şu anda reklam göstermez.

# 4. İzinler ve cihaz erişimi
Android internet bağlantısı sunucu hizmetleri için kullanılır. Android 13 ve üzeri sürümlerde bildirim izni isteğe bağlı olarak talep edilir. Betalink'in mevcut sürümü konum, rehber, SMS, arama kayıtları, mikrofon veya kamera verisi toplamaz.

# 5. Hukuki sebepler
Veriler; hesabın ve hizmet sözleşmesinin kurulması/ifası, hukuki yükümlülüklerin yerine getirilmesi, bir hakkın tesisi-kullanılması-korunması ve temel haklarına zarar vermemek kaydıyla meşru menfaat kapsamında işlenebilir. Grup içinde Google Play test e-postası paylaşımı gibi isteğe bağlı işlemler açık rızana dayanır; rızanı uygulamadaki ilgili paylaşım ayarından geri alabilirsin.

# 6. Saklama ve silme
Hesap verileri hesabın aktif olduğu süre boyunca hizmeti sunmak için saklanır. Hesabını uygulama içinden Profil bölümünden silebilirsin. Hesap silindiğinde hesabına bağlı profil, kampanya/grup üyelikleri, görevler, geri bildirimler, bildirimler, grup davetleri, destek kayıtları ve BetaPuan kayıtları ilgili ilişkisel kayıtlarla birlikte silinir. Başka kullanıcıların kayıtlarında sana işaret eden bazı bağlantılar, kayıt bütünlüğü ve güvenlik amacıyla kimlikten arındırılmış biçimde kalabilir. Kanunen saklanması zorunlu kayıtlar yalnızca gerekli süre boyunca tutulur.
Harici silme talepleri kimlik doğrulaması gerekebileceğinden en kısa sürede ve KVKK kapsamındaki başvurular için en geç 30 gün içinde yanıtlanır. Hesap şifreni veya doğrulama kodunu e-posta ile gönderme.

# 7. Güvenlik
Aktarım sırasında şifreli bağlantılar, veritabanı erişim politikaları ve sunucu tarafında yetki kontrolü gibi makul teknik/idari önlemler kullanılır. Hiçbir çevrimiçi sistem için mutlak güvenlik garantisi verilemez.

# 8. Hakların ve iletişim
KVKK kapsamındaki bilgi alma, düzeltme, silme/yok etme ve diğer yasal hakların için hz.yazilimm@gmail.com adresine başvurabilirsin. Başvurunda Betalink hesabındaki e-posta adresini belirt. Talebin niteliğine göre kimlik doğrulaması istenebilir.

NOT: Bu metin Betalink'in mevcut işlevlerine göre hazırlanmıştır. Yeni veri toplama, analiz, reklam veya üçüncü taraf SDK'ları eklenirse politika ve Play Console Veri Güvenliği beyanı güncellenmelidir.
"""

private const val DELETION_TEXT = """
Betalink hesabını ve hesabınla ilişkili kişisel verileri uygulama içinden silebilirsin. Bu işlem kalıcıdır.

# 1. Uygulama içinden silme
• Betalink uygulamasında hesabına giriş yap.
• Profil ekranını aç.
• Hesabımı ve verilerimi sil seçeneğine dokun.
• Kalıcı silme onayını ver.

# 2. Silinen veriler
Hesabın silindiğinde profil ve cihaz profili, kampanya/grup üyelikleri, test katılım ve günlük onay kayıtları, görev gönderimleri, geri bildirimler, bildirimler, destek talepleri, engelleme/şikâyet kayıtların ve BetaPuan bakiyesi/hareketleri hesabınla ilişkilendirilen veritabanı kayıtlarıyla birlikte silinir.
Başka kullanıcıların kayıt bütünlüğünü veya yasal yükümlülükleri korumak için tutulması gereken bazı kayıtlar, mümkün olduğunda hesabınla ilişkilendirilemeyecek şekilde sınırlı tutulabilir.

# 3. Harici silme talebi
Uygulamaya erişemiyorsan Betalink'e kayıt olurken kullandığın e-posta adresinden hz.yazilimm@gmail.com adresine hesap ve veri silme talebi gönderebilirsin. Mesajda hesabınla eşleşen kayıt e-posta adresini belirtmen yeterlidir. Şifre veya doğrulama kodu gönderme.
Talebin alınmasının ardından hesabın sana ait olduğu doğrulanır. Silme talebi en kısa sürede, KVKK kapsamındaki başvurular için en geç 30 gün içinde sonuçlandırılır. İşlem tamamlandığında talep e-postana yanıt verilir.

# 4. Önemli bilgi
Yalnızca uygulamayı telefondan kaldırmak hesabını silmez. Hesap silme işlemini uygulama içindeki Profil bölümünden başlatmalısın.

# 5. Yardım
Hesap silme veya kişisel verilerle ilgili sorular için hz.yazilimm@gmail.com adresine ulaşabilirsin.
"""
