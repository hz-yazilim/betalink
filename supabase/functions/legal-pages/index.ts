const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "content-type",
  "Content-Type": "text/html; charset=utf-8",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "strict-origin-when-cross-origin",
  "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; base-uri 'none'; form-action 'self' mailto:; frame-ancestors 'none'",
};

const shell = (title: string, body: string) => `<!doctype html>
<html lang="tr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title} | Betalink</title><meta name="robots" content="index,follow">
<style>
:root{color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#0a1020;color:#edf1ff;font:16px/1.7 system-ui,-apple-system,Segoe UI,sans-serif}
main{max-width:850px;margin:40px auto;padding:28px;background:#151e33;border:1px solid #2b3857;border-radius:20px}
h1{font-size:2rem;line-height:1.2;margin:0 0 8px}h2{font-size:1.2rem;margin:28px 0 8px;color:#aab9ff}
p,li{color:#d0d8ec}a{color:#9eafff;overflow-wrap:anywhere}.muted{color:#9ba8c2;font-size:.9rem}
.notice{padding:14px 16px;background:#202b45;border-radius:12px;margin:18px 0}
.button{display:inline-block;padding:12px 18px;background:#91a2ff;color:#10152b;border-radius:10px;text-decoration:none;font-weight:700}
footer{margin-top:28px;padding-top:18px;border-top:1px solid #34415e;color:#9ba8c2;font-size:.9rem}
@media(max-width:600px){main{margin:12px;padding:20px;border-radius:14px}h1{font-size:1.65rem}}
</style></head><body><main><div class="muted">HZ YAZILIM · BETALINK</div>${body}
<footer>İletişim: <a href="mailto:hz.yazilimm@gmail.com">hz.yazilimm@gmail.com</a><br>Son güncelleme: 6 Kasım 2026</footer></main></body></html>`;

const privacy = `
<h1>Gizlilik Politikası</h1><p class="muted">Betalink Android uygulaması ve ilgili hizmetleri için geçerlidir.</p>
<p>Bu politika, Betalink hizmetini sunan Hz Yazılım'ın kişisel verileri nasıl işlediğini açıklar. Betalink; uygulama geliştiricileri ile gerçek kapalı test katılımcılarını buluşturan bir topluluk platformudur.</p>
<h2>1. İşlenen veriler</h2>
<ul>
<li><b>Hesap ve kimlik:</b> kayıt e-posta adresi, kimlik doğrulama kayıtları, görünen ad ve hesap oluşturma bilgileri.</li>
<li><b>Profil ve cihaz:</b> kullanıcının eklediği profil fotoğrafı bağlantısı, ülke, cihaz modeli, Android sürümü, Google Play ülkesi ve isteğe bağlı biyografi/uygunluk bilgisi.</li>
<li><b>Kampanya ve grup etkinliği:</b> oluşturulan kampanyalar, başvurular, grup üyelikleri, test katılım durumları, kullanıcının girdiği günlük onaylar, görev gönderimleri ve zaman damgaları.</li>
<li><b>İletişim ve güvenlik:</b> uygulama içi geri bildirimler, geliştirici yanıtları, destek talepleri, şikâyetler, engelleme kayıtları, bildirimlerin okunma durumu ve kötüye kullanım incelemesi için gerekli kayıtlar.</li>
<li><b>BetaPuan:</b> sanal puan bakiyesi ve puan hareketleri. BetaPuan nakde çevrilemez ve gerçek para/ödeme bilgisi değildir.</li>
<li><b>Google Play test e-postası:</b> yalnızca bir test grubunda açıkça paylaşmayı seçersen, Google Play kapalı testinde kullandığın e-posta adresi. Bu adres onaylı grup üyelerine gösterilebilir. Paylaşım iznini kapatırsan adres grup görünümünden kaldırılır.</li>
</ul>
<h2>2. Verileri hangi amaçlarla kullanırız?</h2>
<ul><li>Hesap açma, oturum açma ve hesap güvenliğini sağlama.</li><li>Kampanya, tester, grup, görev ve 14 günlük test süreçlerini yürütme.</li><li>Katılımcıları cihaz/Android uyumluluğuna göre eşleştirme ve test geri bildirimlerini ilgili geliştiriciye iletme.</li><li>Destek taleplerini yanıtlama, kötüye kullanım/şikâyetleri inceleme ve platform güvenliğini koruma.</li><li>BetaPuan bakiyesini ve kurallara bağlı işlemleri hesaplama.</li><li>Yasal yükümlülükleri yerine getirme ve haklarımızı kullanma/koruma.</li></ul>
<h2>3. Paylaşım ve hizmet sağlayıcılar</h2>
<p>Betalink kişisel verileri satmaz ve reklam hedeflemesi için kullanmaz. Veriler, hizmetin çalışması için kullanılan Supabase altyapı sağlayıcısı (kimlik doğrulama, veritabanı ve sunucu fonksiyonları) tarafından işlenebilir.</p>
<p>Kampanya bilgileri, başvurunun ve test organizasyonunun gerektirdiği ölçüde ilgili kampanya geliştiricisine; görev ve geri bildirimler ilgili kampanya taraflarına gösterilir. Google Play test e-posta adresin yalnızca ilgili grupta paylaşım için açık rıza verdiğinde, o grubun onaylı üyelerine gösterilir.</p>
<p>Betalink Google Play Console hesabına erişmez; Google şifreni, Play Console giriş bilgilerini veya Google hesabı doğrulama kodlarını istemez. Google Play bağlantılarına geçtiğinde Google'ın kendi hizmet ve gizlilik koşulları geçerlidir. Betalink şu anda reklam göstermez ve uzaktan anlık bildirim (FCM) altyapısı etkin değildir.</p>
<h2>4. İzinler ve cihaz erişimi</h2>
<p>Android internet bağlantısı uygulamanın sunucu hizmetleri için kullanılır. Android 13 ve üzeri sürümlerde bildirim izni isteğe bağlı olarak talep edilir. Betalink'in mevcut sürümü konum, rehber, SMS, arama kayıtları, mikrofon veya kamera verisi toplamaz.</p>
<h2>5. Hukuki sebepler</h2>
<p>Veriler; hesabın ve hizmet sözleşmesinin kurulması/ifası, hukuki yükümlülüklerin yerine getirilmesi, bir hakkın tesisi-kullanılması-korunması ve temel haklarına zarar vermemek kaydıyla meşru menfaat kapsamında işlenebilir. Grup içinde Google Play test e-postası paylaşımı gibi isteğe bağlı işlemler açık rızana dayanır; rızanı uygulamadaki ilgili paylaşım ayarından geri alabilirsin.</p>
<h2>6. Saklama ve silme</h2>
<p>Hesap verileri hesabın aktif olduğu süre boyunca hizmeti sunmak için saklanır. Uygulama içinden hesabını silebilir veya uygulamayı kullanamıyorsan <a href="https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/account-deletion">hesap silme sayfasından</a> talep gönderebilirsin. Hesap silindiğinde, hesabına bağlı profil, kampanya/grup üyelikleri, görevler, geri bildirimler, bildirimler, destek kayıtları ve BetaPuan kayıtları ilgili ilişkisel kayıtlarla birlikte silinir. Başka kullanıcıların kayıtlarında sana işaret eden bazı bağlantılar, kayıt bütünlüğü ve güvenlik amacıyla kimlikten arındırılmış biçimde kalabilir. Kanunen saklanması zorunlu kayıtlar yalnızca gerekli süre boyunca tutulur.</p>
<p>Harici silme talepleri kimlik doğrulaması gerekebileceğinden en kısa sürede ve KVKK kapsamındaki başvurular için en geç 30 gün içinde yanıtlanır. Hesap şifreni veya doğrulama kodunu e-posta ile gönderme.</p>
<h2>7. Güvenlik</h2>
<p>Aktarım sırasında şifreli bağlantılar, veritabanı erişim politikaları ve sunucu tarafında yetki kontrolü gibi makul teknik/idari önlemler kullanılır. Hiçbir çevrimiçi sistem için mutlak güvenlik garantisi verilemez.</p>
<h2>8. Hakların ve iletişim</h2>
<p>KVKK kapsamındaki bilgi alma, düzeltme, silme/yok etme ve diğer yasal hakların için <a href="mailto:hz.yazilimm@gmail.com?subject=Betalink%20KVKK%20Basvurusu">hz.yazilimm@gmail.com</a> adresine başvurabilirsin. Başvurunda Betalink hesabındaki e-posta adresini belirt. Talebin niteliğine göre kimlik doğrulaması istenebilir.</p>
<div class="notice">Bu metin Betalink'in mevcut işlevlerine göre hazırlanmıştır. Yeni veri toplama, analiz, reklam veya üçüncü taraf SDK'ları eklenirse politika ve Play Console Veri Güvenliği beyanı güncellenmelidir.</div>
`;

const terms = `
<h1>Betalink Kullanım Koşulları ve Topluluk Kuralları</h1>
<p class="muted">Sürüm: 8 Kasım 2026 · Hizmet sağlayıcı: Hz Yazılım</p>
<p>Betalink; uygulama geliştiricileri ile uygulamalarını gerçek koşullarda test eden katılımcıları buluşturan bir topluluk platformudur. Kullanıcılar kampanya, grup, görev, açıklama ve geri bildirim oluşturmadan önce bu koşulları kabul etmelidir.</p>
<h2>1. Dürüst kullanım</h2>
<ul><li>Hesabını yalnızca kendin kullan ve başkasının kimliğine bürünme.</li><li>Cihazın, uygunluğun ve test katılımın hakkında doğru bilgi ver.</li><li>Katıldığını beyan ettiğin Google Play testine gerçekten katıl; uygulamayı kullan ve dürüst geri bildirim sun.</li><li>Uygulama içi katılım ve günlük check-in kayıtlarının kullanıcı beyanı olduğunu, Google Play Console tarafından doğrulanmadığını bil.</li></ul>
<h2>2. Yasaklanan içerik ve davranışlar</h2>
<ul><li>Tehdit, taciz, zorbalık, nefret söylemi, hedef gösterme veya kişisel saldırı.</li><li>Hukuka aykırı, dolandırıcılık amaçlı veya başkasının haklarını ihlal eden içerik.</li><li>Spam, sahte test başvurusu, sahte check-in, yanıltıcı puan/ödül elde etme veya sistemi manipüle etme.</li><li>Başka kullanıcıların kişisel verilerini izinsiz paylaşma.</li><li>Google şifresi, doğrulama kodu veya Play Console giriş bilgisi isteme/paylaşma.</li><li>Zararlı bağlantı veya kötü amaçlı yazılım paylaşma.</li><li>BetaPuan'ı nakit veya gerçek para gibi sunma.</li></ul>
<h2>3. Kampanyalar ve tester grupları</h2>
<p>Kampanyalar ve gruplar gerçek test ihtiyacı için kullanılmalıdır. Geliştiriciler test kapsamını ve görevleri açıkça belirtmeli; tester'lar yalnızca gerçekten yaptıkları görevleri ve günlük bildirimleri tamamlandı olarak işaretlemelidir. Grup e-posta paylaşımı isteğe bağlıdır. Betalink, Google Play'de yayın onayı veya production erişimi garantisi vermez.</p>
<h2>4. BetaPuan</h2>
<p>BetaPuan sanal, devredilemez ve nakde çevrilemez uygulama içi puandır. Hileli veya kötüye kullanım amaçlı işlemler incelenebilir ve kurallara göre geri alınabilir.</p>
<h2>5. Şikâyet ve moderasyon</h2>
<p>Kullanıcılar uygunsuz içerik veya davranışı uygulama içinden bildirebilir ve diğer kullanıcıları engelleyebilir. Hz Yazılım bildirimleri inceleyebilir; içeriği kaldırabilir, grup üyeliğini sonlandırabilir veya hesabı geçici/kalıcı olarak kısıtlayabilir.</p>
<h2>6. İçerik sorumluluğu</h2>
<p>Paylaştığın içerik için gerekli hak ve izinlere sahip olmalısın. Başkasına ait marka, yazılım, görsel veya metinleri izinsiz kullanma. Paylaştığın içerikten sen sorumlusun; Betalink içeriği hizmeti işletmek ve moderasyon yapmak amacıyla platform içinde barındırabilir ve gösterebilir.</p>
<h2>7. Üçüncü taraf hizmetler</h2>
<p>Betalink, Google Play Console veya üçüncü taraf uygulamaların sahibi değildir. Üçüncü taraf hizmetlerinin kullanılabilirliği ve kuralları ilgili sağlayıcıya bağlıdır. Betalink bakım, güvenlik veya kötüye kullanım durumlarında özellikleri geçici olarak sınırlandırabilir.</p>
<h2>8. Hesap ve gizlilik</h2>
<p>Hesabını uygulama içinden Profil bölümünde silebilir veya <a href="https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/account-deletion">harici hesap silme sayfasından</a> talep gönderebilirsin. Kişisel verilerin için <a href="https://lwloymjcnmzobjmgvlnp.supabase.co/functions/v1/legal-pages/privacy">Gizlilik Politikası</a> geçerlidir.</p>
<h2>9. Güncellemeler ve iletişim</h2>
<p>Önemli koşul değişikliklerinde yeni sürüm gösterilir ve yeniden onay istenir. Sorular için <a href="mailto:hz.yazilimm@gmail.com">hz.yazilimm@gmail.com</a> adresine ulaşabilirsin.</p>
<div class="notice">Bu metin ürün ve topluluk kurallarını tanımlar. Yayın öncesinde Türkiye mevzuatı ve işletme modeline göre hukuk danışmanı tarafından gözden geçirilmelidir.</div>
`;

const deletion = `
<h1>Betalink Hesap ve Veri Silme</h1>
<p>Bu sayfa, Betalink hesabını ve ilişkili kişisel verilerini silmek için Google Play'in istediği harici başvuru yoludur. Uygulama içinden silme seçeneği de Profil ekranında bulunur.</p>
<h2>Hesap silme talebi gönder</h2>
<p>Betalink'e kayıt olurken kullandığın e-posta adresinden aşağıdaki bağlantıya dokunarak talep e-postası oluştur. Mesajda yalnızca hesabınla eşleşen kayıt e-posta adresini belirtmen yeterlidir. Şifre veya doğrulama kodu gönderme.</p>
<p><a class="button" href="mailto:hz.yazilimm@gmail.com?subject=Betalink%20Hesap%20ve%20Veri%20Silme%20Talebi&body=Merhaba%2C%0A%0ABetalink%20hesabimin%20ve%20hesabimla%20iliskili%20kisisel%20verilerimin%20silinmesini%20talep%20ediyorum.%0A%0ABetalink%20kayit%20e-posta%20adresim%3A%20%5BBURAYA%20YAZIN%5D%0A%0A">Hesabımı silmek için e-posta gönder</a></p>
<div class="notice"><b>İşlem süresi:</b> Talebin alınmasının ardından hesabın sana ait olduğu doğrulanır. Silme talebi en kısa sürede, KVKK kapsamındaki başvurular için en geç 30 gün içinde sonuçlandırılır. İşlem tamamlandığında talep e-postana yanıt verilir.</div>
<h2>Uygulama içinden silme</h2>
<ol><li>Betalink uygulamasında hesabına giriş yap.</li><li><b>Profil</b> ekranını aç.</li><li><b>Hesabımı ve verilerimi sil</b> seçeneğine dokun.</li><li>Kalıcı silme onayını ver.</li></ol>
<h2>Silinen veriler</h2>
<p>Hesabın silindiğinde profil ve cihaz profili, kampanya/grup üyelikleri, test katılım ve günlük onay kayıtları, görev gönderimleri, geri bildirimler, bildirimler, destek talepleri, engelleme/şikâyet kayıtların ve BetaPuan bakiyesi/hareketleri hesabınla ilişkilendirilen veritabanı kayıtlarıyla birlikte silinir. Başka kullanıcıların kayıt bütünlüğünü veya yasal yükümlülükleri korumak için tutulması gereken bazı kayıtlar, mümkün olduğunda hesabınla ilişkilendirilemeyecek şekilde sınırlı tutulabilir.</p>
<p>Yalnızca uygulamayı telefondan kaldırmak hesabını silmez. Harici başvuru için e-posta göndermen gerekir.</p>
<h2>Yardım</h2><p>Hesap silme veya kişisel verilerle ilgili sorular: <a href="mailto:hz.yazilimm@gmail.com">hz.yazilimm@gmail.com</a></p>
`;

Deno.serve((req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "GET") return new Response("Method not allowed", { status: 405, headers: corsHeaders });
  const path = new URL(req.url).pathname.replace(/\/+$/, "");
  if (path.endsWith("/terms")) {
    return new Response(shell("Kullanım Koşulları", terms), { status: 200, headers: corsHeaders });
  }
  if (path.endsWith("/account-deletion")) {
    return new Response(shell("Hesap ve Veri Silme", deletion), { status: 200, headers: corsHeaders });
  }
  if (path.endsWith("/privacy") || path.endsWith("/privacy-policy") || path.endsWith("/legal-pages")) {
    return new Response(shell("Gizlilik Politikası", privacy), { status: 200, headers: corsHeaders });
  }
  return new Response(shell("Gizlilik Politikası", privacy), { status: 200, headers: corsHeaders });
});
