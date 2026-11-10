# Betalink 0.4.2

## Profil ve Kişiler
- Kullanıcılar görünen adlarını ve kısa biyografilerini düzenleyebilir.
- İsteğe bağlı profil görseli bağlantısı eklenebilir.
- Kullanıcılar hesap giriş e-postasından ayrı bir iletişim Gmail'i girebilir.
- İletişim e-postası varsayılan olarak gizlidir; kullanıcı açıkça izin verirse Kişiler listesinde görünür.
- Hesap giriş e-postası halka açık dizine hiçbir zaman eklenmez.
- Kişiler listesinde herkese açık iletişim e-postası ve biyografi görüntülenebilir.

## Güvenlik
- İletişim e-postası ayrı, RLS korumalı tabloda tutulur.
- Dizin e-posta RPC'si yalnızca görünürlüğü açık ve boş olmayan iletişim adreslerini döndürür.
- Hesap giriş e-postaları yönetici paneli dışındaki kullanıcı dizinine dahil edilmez.
- Gizlilik politikası bu ayrımı açıklayacak şekilde güncellenmiştir.

## Sürüm
- Android versionName: 0.4.2
- Android versionCode: 7
