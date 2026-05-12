# Cep Sandık: Technical Challenges and Architectural Solutions

This document outlines the significant technical hurdles encountered during the development of the **Cep Sandık** project and the rationale behind the architectural decisions made to resolve them.

---

## 1. Architectural Challenges

### 1.1 Why gRPC for the Crypto Engine?
**Problem:** The core cryptographic logic (ElectionGuard) is implemented in Python, while the business logic and user management are in Java (Spring Boot). A communication bridge was needed that is both fast and robust.

**Solution:** **gRPC** was chosen over REST for several reasons:
- **Strong Typing:** Protobuf files ensure that hem Java hem de Python servislerinin aynı şemaya sadık kalmasını sağlar. Bu sayede, gevşek yapılı JSON verilerinde sıkça görülen serileştirme hataları (serialization errors) önlenir.
- **Performans:** gRPC'nin ikili (binary) serileştirme yapısı ve HTTP/2 üzerinden çoklamalı (multiplexed) iletişimi, standart REST'e göre çok daha düşük gecikme süresi sağlar. Bu, büyük kriptografik manifestlerin iletimi için kritiktir.
- **Servis İzolasyonu:** Kripto motorunun sadece matematiksel işlemlere odaklanan bir "kara kutu" olarak kalmasını sağlarken, Java tarafının veritabanı ve iş kurallarını yönetmesine imkan tanır.

### 1.2 Stateless vs. Stateful Crypto Processing
**Problem:** Kriptografik durumlar (ElectionGuard Context, Manifestler) büyük ve karmaşıktır. Bu durumu Python servisinde saklamak, yatay ölçeklendirme için bir engel oluşturur ve veritabanı senkronizasyonu gerektirir.

**Solution:** **Stateless (Durumsuz) Crypto Engine.**
Her gRPC isteği (örneğin `EncryptBallot`), tüm gerekli bağlamı (JSON manifest ve context) payload içinde taşır. Python servisi isteği işler ve sonucunu döner, hiçbir veriyi kendi belleğinde veya veritabanında saklamaz. Bu sayede Crypto Engine podlarını bir yük dengeleyici arkasında sonsuz sayıda ölçeklendirebiliriz.

### 1.3 Asynchronous Audit Trail (Asenkron Denetim İzi)
**Problem:** Her oyu Bulletin Board servisine (MongoDB) gerçek zamanlı yazmak, ana "Oy Verme" akışını yavaşlatabilir ve kullanıcı deneyimini olumsuz etkileyebilir.

**Solution:** **Mesaj Odaklı Denetim (RabbitMQ).**
`ElectionService` oyu ana veritabanına kaydeder ve kullanıcıya anında başarı yanıtı döner. Aynı anda bu işlemi bir RabbitMQ event'i olarak yayınlar. `BulletinBoardService` bu mesajları kendi hızıyla tüketerek kamuya açık defteri günceller, böylece ana akışın performansı korunur.

---

## 2. Altyapı ve İletişim Zorlukları

### 2.1 API Gateway 502/504 Hataları
**Problem:** İlk testlerde, büyük ElectionGuard manifestleri veya büyük seçim sonuçları Nginx'in "Bad Gateway" veya "Gateway Timeout" hataları vermesine neden oldu.

**Solution:** **Nginx Tampon (Buffer) ve Zaman Aşımı Optimizasyonu.**
Nginx konfigürasyonundaki `proxy_buffer_size`, `proxy_buffers` ve `proxy_read_timeout` değerleri artırıldı. Böylece büyük kriptografik JSON paketlerinin kesilmeden iletilmesi sağlandı.

### 2.2 Mikroservisler Arası İzlenebilirlik
**Problem:** 4-5 farklı mikroservis arasında bir hata oluştuğunda bunları birbirine bağlamak ve hatanın kaynağını bulmak zordu.

**Solution:** **Merkezi Correlation ID (İlişkilendirme Kimliği).**
API Gateway, her gelen istek için benzersiz bir `X-Request-Id` üretir. Bu ID, tüm servisler arasında header'lar aracılığıyla taşınır ve her log satırına eklenir. Bu sayede, tek bir oy verme işleminin tüm servislerdeki ayak izini tek bir ID ile takip edebiliyoruz.

---

## 3. Kriptografik Zorluklar

### 3.1 ElectionGuard SDK Kısıtlamaları
**Problem:** Standart `electionguard-python` SDK'sı (v1.4.0), seçim başlatmayı kolaylaştıran bazı üst seviye yardımcı modülleri (örneğin `election_builder`) içermiyordu.

**Solution:** **Manuel Bağlam (Context) İnşası.**
`crypto_servicer.py` içinde, SDK'nın eksik olduğu kısımları atlamak için `manifest_hash`, `commitment_hash` ve `extended_base_hash` zincirlerini manuel olarak hesaplayan bir mantık uyguladık. Bu, ElectionGuard spesifikasyonuyla %100 uyumlu kalırken SDK limitlerini aşmamızı sağladı.

### 3.2 Global Zaman Senkronizasyonu
**Problem:** Dağıtık bir sistemde servislerin saatleri arasındaki ufak farklar, seçimin başlama/bitiş zamanlarında veya token srelerinde hatalara neden olabilir.

**Solution:** **Global UTC Zorunluluğu.**
Tüm mikroservislerin merkezi bir `UtcClock` kullanması sağlandı ve uygulama girişlerinde varsayılan TimeZone Java tarafında UTC olarak sabitlendi.

---

## 4. Mobil (React Native) Zorluklar

### 4.1 Tema Tutarlılığı
**Problem:** Uygulama büyüdükçe farklı ekranlarda farklı renk tonları (gri, slate vb.) kullanılmaya başlandı, bu da görsel bir karmaşa yarattı.

**Solution:** **Tema Altyapısı ve Yardımcı Araçlar.**
Tüm renk değerleri merkezi bir `theme.ts` dosyasına taşındı ve tüm bileşenlerin (bottom sheet, picker vb.) bu temaya sadık kalmasını sağlayan merkezi yardımcı fonksiyonlar (util) oluşturuldu.

### 4.2 Mobil Cihazda RSA Performansı
**Problem:** Düşük donanımlı mobil cihazlarda büyük verileri RSA ile şifrelemek UI thread'ini dondurabiliyordu.

**Solution:** **Hibrit Şifreleme Optimizasyonu.**
Uygulama, payload'un tamamını RSA ile şifrelemek yerine; rastgele bir AES anahtarı üretip sadece anahtarı RSA ile, asıl veriyi ise çok daha hızlı olan AES-GCM ile şifreler. Bu, işlem süresini dramatik şekilde düşürdü.
