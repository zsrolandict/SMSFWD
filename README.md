# SMSFWD

Magyar nyelvű, belső használatú SMS-továbbító alkalmazás. Világos, türkiz–sötétkék felület, szerkeszthető szabályok és átlátható előzmények.

## Több címzettel és szószűréssel bővített változat: 0.3

A közös React-felület böngészőben és Capacitorral Android-alkalmazásként fut. Az Android SMS-kapcsolat Java kóddal készült. A korábbi Kotlin/Compose terv helyett így a bemutató és a telefonos felület közös kódban készül.

- Szabály létrehozása, szerkesztése, kapcsolása és törlése.
- Feladó és kulcsszó szerinti szűrés, azonos célok összevonása.
- Helyi mentés, szüneteltetés, szimulált próbaüzenetek.
- Kereshető és törölhető eseményelőzmények.
- Reszponzív felület és mobilos navigáció.
- Android SMS-engedélykérés, bejövő SMS fogadása és saját SIM-es továbbítás.
- Kimenő szegmenslimit és részenkénti küldési visszajelzés.

A **böngészős nézet kizárólag szimuláció**: nem küld valódi üzenetet. A tesztküldés Androidon is szimuláció. A mintaszabályok nem végeznek valódi Android-küldést; saját szabály mentése vagy minta átszerkesztése szükséges.

## Böngészős futtatás

Node.js 22+ szükséges.

```sh
npm ci
npm run dev
```

Production build: `npm run build`. A `dist/` statikus webszerverrel kiszolgálható. A betűkészletek is a csomag részei, nincs futásidejű Google Fonts-függőség.

## Android APK

### Letöltés a GitHubról

Az első, a felhőkörnyezetben buildelt és aláírás-ellenőrzött debug APK közvetlenül is elérhető: [SMSFWD 0.3 APK](downloads/SMSFWD-0.3-debug.apk). A GitHub fájlnézetben a **Download raw file** gombbal tölthető le. Ez a bemutatóhoz biztosított egyszeri bináris; a további buildek a GitHub Actions artifactjaiban készülnek. A fiók konfigurálása és a fizikai telefonos próba továbbra is szükséges. Az APK ugyanazzal a helyi debug kulccsal készült, mint a közvetlenül letölthető 0.1-es változat, így arra frissítésként telepíthető. Az APK SHA-256 ellenőrzőösszege a `downloads/SHA256SUMS` fájlban található.

A `.github/workflows/android-apk.yml` a `main` ág feltöltése után APK-t buildel. A GitHub-tároló **Actions → Android APK letoltes** részében egy sikeres futás alján, az **Artifacts** szakaszban a **SMSFWD-Android** csomag tölthető le, GitHub-bejelentkezés után. A ZIP-ből az `app-debug.apk` fájlt kell kicsomagolni, majd Androidon telepíteni. Az artifact 14 napig marad meg; a munkafolyamat kézzel újraindítható. A `SMSFWD-bemutato` csomag külön tartalmazza a megnyitható bemutatót.

A CI debug aláírókulcs a futtatókörnyezethez tartozik és változhat: későbbi CI-build ezért nem feltétlenül telepíthető ugyanarra az alkalmazásra frissítésként. Ilyenkor eltávolítás szükséges lehet, ami törli a helyi szabályokat. Stabil frissítéshez megőrzött release kulcs kell.

Android 10+ (API 29), JDK 21, SDK 36 és Build Tools 35.0.0 szükséges. A pinelt Gradle-wrapper SHA-256 ellenőrzést használ. A jelen felhőkörnyezetben a teljes előkészítés:

```sh
bash scripts/setup-cloud.sh
```

Későbbi módosítás után:

```sh
npm run android:build
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`; a felhős build másolata `/workspace/artifacts/SMSFWD-debug.apk`. Ez fejlesztői kulccsal aláírt debug APK. Vállalati kiadáshoz külön megőrzött release aláírókulcs és frissítési folyamat szükséges.

Telefonon: telepítsd az APK-t, nyisd meg, engedélyezd az SMS-hozzáférést, majd hozz létre saját telefonszámos szabályt. A rendszerben legyen alapértelmezett SMS-SIM beállítva. Az aktív saját szabály illeszkedő valódi SMS esetén valódi, díjköteles küldést indíthat. A pilothoz ellenőrzött saját célszámot használj.

## Jelenlegi korlátok

- Az automatikus e-mailhez küldő SMTP-postafiókot kell beállítani. Gmailhez alkalmazásjelszó szükséges; Microsoft 365 OAuth-bejelentkezés még nincs. A mentés nem igazolja a belépést: valódi próba-e-maillel ellenőrizendő.
- A kimenő SIM a telefon alapértelmezett SMS-SIM-je; külön alkalmazásbeli SIM-választás még nincs.
- A tárolás szinkronizált SharedPreferences; az e-mail várólistát WorkManager feldolgozza internetkapcsolat esetén. Blokkolt e-mail a beállítás javítása után az Előzményekből kézzel újraküldhető. Bizonytalan SMTP-kimenetel nem ismétlődik automatikusan.
- Nincs régi SMS-import, MMS/RCS vagy automatikus szövegátfogalmazás.
- Legfeljebb 200 legutóbbi esemény marad meg; folyamatban lévő Android-küldési rekord tovább megőrződhet a visszajelzéshez.
- Bizonytalan/részleges SMS nem ismétlődik automatikusan. Teljes visszajelzés hiányában tíz perc után bizonytalan állapot jelenik meg az alkalmazás megnyitásakor.
- A napi limit eszközhelyi dátumot használ; időzónaváltás kezelése még nem a végleges terv szerinti.
- Gyártói háttérkorlátozás, force stop és újraindítás utáni első feloldás előtti működés fizikai eszközön ellenőrizendő.

## Ellenőrzések

```sh
npm test
CHROMIUM_PATH=/usr/bin/chromium npm run test:browser
npm run android:build
```

Más gépen a böngészős teszthez `npx playwright install chromium`, és a `CHROMIUM_PATH` elhagyható. JavaScript szabálymotor-tesztek, böngészős folyamatok és Android JUnit szabályillesztési tesztek vannak. APK build és lint külön fut. A valódi SIM-küldés és kézbesítés fizikai telefonos pilotot igényel; a felhős build és szimuláció ezt nem igazolja.

## Dokumentumok

- [Termékterv és elfogadási feltételek](docs/termekterv.md)
- [Eredeti célarchitektúra](docs/architektura.md)
- [Fejlesztési sorrend, ellenőrzések és költségek](docs/fejlesztes.md)

A dokumentumokban szereplő végleges célok részben még nem valósultak meg; a fenti 0.3-es állapot az irányadó. A meglévő `/workspace/SMSFWD` izolált checkoutot használjuk, külön worktree nélkül.

## Gmail beállítása

1. Telepítsd a 0.3-es APK-t a meglévő alkalmazás fölé. Ne távolítsd el a korábbi alkalmazást; a szabályok így megmaradnak.
2. A küldő Google-fiókban legyen kétlépcsős azonosítás. Ha a fiók engedi, hozz létre alkalmazásjelszót: https://myaccount.google.com/apppasswords . Jelszót ne küldj chatben, és ne használd a normál Google-jelszót. Egyes felügyelt fiókokban az alkalmazásjelszó nem érhető el.
3. Az alkalmazás Beállítások → Automatikus e-mail → Postafiók beállítása részében add meg a küldő Gmail-címet, a `smtp.gmail.com` kiszolgálót, az 587-es STARTTLS portot és az alkalmazásjelszót.
4. A valódi próba-e-mail címzettjének add meg a saját célcímedet. Kapcsold be a továbbítást, küldj próbát, ellenőrizd az Előzményeket és a címzett postaládáját/spammappáját.
5. Küldj új valódi SMS-t, vagy a korábbi beállítás miatt blokkolt e-mail mellett válaszd az E-mail újraküldése gombot. A mentés nem küldi ki automatikusan a régi blokkolt üzeneteket.

A Beállítások → Szabály próba továbbra is szimuláció. A postafiók beállításainál található próba-e-mail valódi küldés. Az „elküldve” SMTP-elfogadást jelent, nem garantálja a postaládába érkezést. E-mail közvetlenül a telefonról, ellenőrzött TLS-kapcsolattal megy ki; nem kell külön szerver. A jelszó csak kifejezett megjelenítési kérésre kerülhet a telefon felületére, a böngésző tárhelyére nem, nem része az APK-nak, és nem kerül a GitHubra.

## Újdonságok a 0.3-ban

- A szem ikon megmutatja/elrejti a jelenleg beírt alkalmazásjelszót. Üres mezőnél Androidon kifejezetten a szemre kattintva a mentett jelszó is lekérhető; képernyőzár esetén a telefon feloldási ellenőrzést kér. Automatikusan nem töltjük vissza.
- A főoldal működési áttekintés; a nagy próbablokk és nyitó marketingfelület megszűnt. Szabályszimuláció: Beállítások → Szabály próba.
- Egy szabály több címzettet kezel: soronként egy nemzetközi telefonszám vagy e-mail cím. Az azonos csatornán ismétlődő címzett egy bejövő eseményre egyszer kap üzenetet.
- Több szót/kifejezést lehet megadni, soronként egyet; bármelyik vagy mindegyik illeszkedése választható. Az új szűrés teljes szavakat/kifejezéseket keres, a kis-/nagybetűt és eltérő szóközöket egységesíti, az ékezeteket megőrzi. A korábbi egyetlen kulcsszós szabályok szerkesztésig a korábbi részszöveg-illesztéssel működnek.
- „Jelszavas SMS-ek mintája”: `egyszer használatos jelszava` és `InfoCert`, bármelyik illeszkedéssel. Az üzenetben lévő változó számot nem kell beírni. A gomb csak a szűrőt tölti ki; címzettet nem állít be, és önmagában nem indít küldést.
- Egy szabály csatornája SMS vagy e-mail; ha egyszerre mindkettőre kell küldeni, két szabályt hozz létre azonos szövegfeltételekkel.

A Gmail-fiók egyszerű összekapcsolásához szükséges Google-regisztráció részletei: [Gmail-összekapcsolás](docs/gmail-osszekotes.md). Ez az integráció még nincs kész; a meglévő Gmail-bejelentkezés önmagában nem helyettesíti az SMSFWD számára szükséges engedélyt.
