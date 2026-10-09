# Gmail összekapcsolása: Google Cloud beállítás

Az SMSFWD 0.3.2 egygombos Gmail-kapcsolatához az alkalmazást előbb regisztrálni kell a Google-nél. A telefonon meglévő Google-fiók ezután kiválasztható, de a Gmail alkalmazásba való korábbi belépés nem helyettesíti a küldési engedélyt.

## 1. Projekt létrehozása

1. Nyisd meg: https://console.cloud.google.com/
2. Jelentkezz be a projektet kezelő Google-fiókkal.
3. A felső projektválasztóban válaszd az **Új projekt / New project** lehetőséget.
4. Adj neki például **SMSFWD** nevet, hozd létre, majd válaszd ki.
5. Az **APIs & Services → Library** részben keresd meg a **Gmail API** szolgáltatást, és engedélyezd.

## 2. Hozzájárulási képernyő

1. Nyisd meg a **Google Auth Platform** részt. A menüpontok neve a Google felületének nyelvétől és aktuális változatától függhet.
2. A **Branding** részben add meg az SMSFWD alkalmazásnevet, a támogatási címet és a fejlesztői kapcsolattartó címét.
3. Saját, személyes Gmail-fiókhoz az **Audience** legyen **External**, a kezdeti használat **Testing** állapotban történjen.
4. A **Test users** listához add hozzá azt a Gmail-fiókot, amelyről az SMS-eket továbbítani szeretnéd. A projekt tulajdonosának fiókja nem feltétlenül azonos a küldő fiókkal.
5. A **Data Access** részben engedélyezd a `https://www.googleapis.com/auth/gmail.send` jogosultságot. Az app kizárólag Gmail-küldési jogosultságot kér; nem kér levélolvasási engedélyt.

A tesztfelhasználós beállítás saját kipróbálásra szolgál. Másoknak szánt nyilvános kiadás előtt a Google alkalmazásellenőrzési és közzétételi követelményeit külön teljesíteni kell. A Google a tesztmód engedélyeit korlátozhatja vagy lejárathatja; ilyenkor az appban újra kell engedélyezni a kapcsolatot.

## 3. Android OAuth-kliens

A **Clients → Create client** részben válassz **Android** alkalmazást:

- Név: például **SMSFWD 0.3.2 pilot**.
- Csomagnév: `hu.smsfwd.app`.
- Tanúsítvány SHA-1: `0E:C7:9C:E7:CD:7D:45:F7:D8:02:A8:F4:1F:30:58:3E:CD:5E:74:D0`.

Ez a 0.3.1 új felhőbuildjének aláíró tanúsítványa. A 0.3.2 ugyanazzal a kulccsal készül, ezért a már telepített 0.3.1 fölé frissíthető. A régi, közvetlen 0.3-as APK másik kulcsot használt. Release kulcs vagy későbbi eltérő aláírás esetén külön Android-kliens regisztráció szükséges.

A SHA-1 nyilvános tanúsítványadat; nem titkos kulcs és nem jelszó. Ehhez a natív engedélykéréshez nem kell közös kliens-titkot beírni az appba vagy chatbe.

## 4. Kipróbálás a telefonon

1. Frissíts az új APK-ra a telepített 0.3.1 fölé; ne távolítsd el előtte az appot.
2. Nyisd meg a **Beállítások → Automatikus e-mail** részt.
3. Koppints a **Gmail összekapcsolása** gombra.
4. Válaszd ki a tesztfelhasználóként felvett fiókot, és engedélyezd a küldést a Google saját felületén.
5. Küldj valódi próba-e-mailt egy saját címre. Ellenőrizd az Előzményeket és a postaládát/spam mappát.
6. Az SMS-ből történő továbbításhoz külön engedélyezd az **SMS fogadását**, készíts e-mailes szabályt, kapcsold be a továbbítást, majd küldj egy új, illeszkedő SMS-t.

Az SMS-küldési engedély csak SMS-címzetthez szükséges. A postafiók próba-e-mailje SMS-engedély és bekapcsolt automatikus továbbítás nélkül is indítható.

## Ha nem sikerül

- Ellenőrizd, hogy a projektben valóban engedélyezett-e a Gmail API.
- Ellenőrizd a tesztfelhasználót, csomagnevet és a telepített APK SHA-1 ujjlenyomatát.
- A telefonon működő Google Play-szolgáltatások és internetkapcsolat szükséges.
- Ha a Google új engedélyezést kér, nyisd meg az appot, és kapcsolódj újra. A háttérben az SMSFWD nem nyit engedélyező ablakot.
- A Google-regisztráció elvégzéséig a meglévő SMTP + alkalmazásjelszó módszer használható.

Jelszót, hozzáférési tokent, ügyféladatot vagy SMS-tartalmat ne küldj hibajelentésben. A Debug nézet csak eszköz- és engedélyállapotokat mutat.

Hivatalos útmutatók:

- https://developers.google.com/identity/authorization/android
- https://developers.google.com/gmail/api/auth/scopes
- https://developers.google.com/gmail/api/guides/sending
