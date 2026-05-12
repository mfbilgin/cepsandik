# Cep Sandık: Bitirme Tezi Referans Dosyası

Bu dosya, **Cep Sandık** projesi için hazırlanan bitirme tezi taslağını doldurmanıza yardımcı olacak teknik detayları, mimari açıklamaları ve proje hedeflerini içermektedir.

---

## 1. ÖZET (Abstract)
**Cep Sandık**, dijital oylama süreçlerinde şeffaflık, güvenlik ve doğrulanabilirlik sorunlarını çözmeyi amaçlayan, **Uçtan Uca Doğrulanabilir (E2E-V)** bir mobil oylama sistemidir. Proje, Microsoft tarafından geliştirilen **ElectionGuard** protokolünü kullanarak oyların gizliliğini korurken, her seçmenin kendi oyunun sayıldığını kontrol edebilmesini (Bireysel Doğrulanabilirlik) ve herkesin seçim sonuçlarının doğruluğunu matematiksel olarak teyit edebilmesini (Evrensel Doğrulanabilirlik) sağlar. Sistem; React Native tabanlı bir mobil uygulama, Spring Boot mikroservis mimarisi, gRPC tabanlı bir kriptografi motoru ve asenkron çalışan bir "Bulletin Board" (İlan Panosu) katmanından oluşmaktadır.

---

## 2. GİRİŞ (Introduction)
Dijital dönüşüm süreciyle birlikte seçim sistemlerinin de dijitalleşmesi kaçınılmaz hale gelmiştir. Ancak, geleneksel dijital oylama sistemleri "kara kutu" (black-box) doğası nedeniyle seçmen güvenini tam olarak kazanamamaktadır. Cep Sandık projesi, gelişmiş kriptografik yöntemleri (ElGamal homomorfik şifreleme, Sıfır Bilgi Kanıtları) modern yazılım mimarileriyle birleştirerek, güven ihtiyacını bir otoriteye değil, matematiksel kanıtlara dayandıran bir platform sunar.

---

## 3. PROBLEM TANIMI VE AMAÇ (Problem Definition & Objectives)
*   **Problem:** Mevcut dijital oylama sistemlerinde veritabanına erişimi olan bir yöneticinin oyları değiştirebilmesi veya seçim sonuçlarını manipüle edebilmesi.
*   **Amaç:**
    *   Seçmen gizliliğini (anonymity) %100 korumak.
    *   Hatalı veya kötü niyetli sistem müdahalelerini tespit edebilecek bir denetim mekanizması kurmak.
    *   Mobil cihazlar üzerinden erişilebilir ve ölçeklenebilir bir yapı oluşturmak.
    *   Eşik Şifreleme (Threshold Encryption) ile seçim anahtarını birden fazla "Emanetçi" (Guardian) arasında bölüştürerek tek bir noktadan sızıntıyı önlemek.

---

## 4. SİSTEM MİMARİSİ (System Architecture)
Sistem dört ana katmandan oluşmaktadır:

1.  **İstemci Katmanı (Mobile App):** React Native ile geliştirilmiştir. Kullanıcı kimlik doğrulama ve oyların ilk aşama şifrelemesini (RSA-OAEP) yapar.
2.  **Mantıksal Katman (Election Service):** Java/Spring Boot mikroservisidir. Seçim yaşam döngüsünü (Taslak -> Aktif -> Bitti) yönetir.
3.  **Kriptografi Katmanı (Crypto Engine):** Python tabanlıdır ve gRPC üzerinden iletişim kurar. ElectionGuard SDK'sını kullanarak homomorfik şifreleme ve ZKP (Sıfır Bilgi Kanıtı) üretimini gerçekleştirir.
4.  **Denetim Katmanı (Bulletin Board):** RabbitMQ ve MongoDB kullanır. Tüm kriptografik kanıtları ve şifreli oyları asenkron olarak kamuya açık bir şekilde yayınlar.

---

## 5. KULLANILAN TEKNOLOJİLER (Technologies Used)
*   **Frontend:** React Native, TypeScript, Expo.
*   **Backend:** Java 17, Spring Boot, PostgreSQL.
*   **Crypto Engine:** Python 3.9, gRPC, Protobuf, ElectionGuard SDK.
*   **Altyapı:** Nginx (API Gateway), RabbitMQ (Message Broker), MongoDB (Audit Log).
*   **Kriptografi:** ElGamal (Homomorfik), RSA-OAEP (Transit Güvenliği), Chaum-Pedersen (ZKP), SHA-256 (Hash Zinciri).

---

## 6. UYGULAMA VE GELİŞTİRME (Implementation)
*   **Hibrit Şifreleme:** Mobil cihazlarda performans kaybını önlemek için oylar önce RSA/AES hibrit yöntemiyle sunucuya iletilir, ardından Kripto Motorunda ElectionGuard formatına dönüştürülür.
*   **Stateless Crypto Engine:** Kripto motoru hiçbir veriyi saklamaz (stateless). Bu sayede sisteme binlerce seçmen gelse dahi motor yatayda kolayca ölçeklenebilir.
*   **Asenkron Denetim:** Oylama işlemi sırasında sistemin yavaşlamaması için denetim verileri RabbitMQ üzerinden kuyruğa alınır ve arka planda İlan Panosuna işlenir.

---

## 7. GÜVENLİK ANALİZİ (Security Analysis)
*   **Mükemmel İleri Gizlilik:** Seçim anahtarları Emanetçiler arasında bölünmüştür (Quorum mekanizması).
*   **Manipülasyon Tespiti:** Veritabanındaki bir oy değiştirilirse, İlan Panosundaki Sıfır Bilgi Kanıtı (ZKP) ile uyumsuz hale gelir ve denetim scriptleri (verify_election.py) hatayı anında yakalar.
*   **Korunan Veri İletişimi:** Nginx Gateway ve JWT tabanlı kimlik doğrulama ile mikroservislerin güvenliği sağlanmıştır.

---

## 8. SONUÇ VE GELECEK ÇALIŞMALAR (Conclusion & Future Work)
Cep Sandık, akademik düzeydeki kriptografik protokollerin son kullanıcıya ulaşabileceği pratik bir mobil uygulama haline getirilmiştir. 
**Gelecek Çalışmalar:**
*   Baskı Altında Oylama (Coercion Resistance) mekanizmalarının güçlendirilmesi.
*   Blockchain tabanlı bir İlan Panosu entegrasyonu ile merkeziyetsiz denetimin artırılması.
*   Farklı seçim türleri (STV, Dereceli Oylama) için destek eklenmesi.

---

## 9. REFERANSLAR (Selected References)
1.  Microsoft ElectionGuard Specification v2.1.
2.  Benaloh, J. "Simple verifiable elections." (Homomorfik şifreleme temelleri).
3.  Chaum, D. "Untraceable electronic mail, return addresses, and digital pseudonyms."
4.  Spring Boot and Microservices Documentation.
5.  React Native: Building Native Mobile Apps with JavaScript.
