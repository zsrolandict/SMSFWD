# Gmail fiók-összekapcsolás: fennmaradó előfeltétel

A már bejelentkezett Android Gmail-fiók használható Google fiókválasztáshoz, de a Gmail alkalmazás belépése nem ad automatikus levélküldési engedélyt az SMSFWD-nek. Az SMSFWD-ben jelenleg nincs saját felhasználói fiók vagy Google OAuth-bejelentkezés; a továbbítás kapcsolója nem bejelentkezés. A 0.3 SMTP-küldése a telefonon megadott postafiókkal működik. Nem került bele működést színlelő „Összekapcsolás” gomb.

A jelszó nélküli összekapcsolás folytatásához szükséges egy felhasználó által kezelt Google Cloud projekt és alkalmazásregisztráció:

1. Gmail API engedélyezése a Google Cloud projektben.
2. Google Auth Platform alkalmazás és hozzájárulási képernyő létrehozása; tesztmódban a használt fiókok felvétele tesztfelhasználónak. Személyes Gmailhez nem használható csak egy Workspace-szervezetre korlátozott belső alkalmazás.
3. Android OAuth-kliens regisztrálása az alkalmazás csomagnevével és a használt APK aláíró tanúsítványának SHA-1 ujjlenyomatával. A későbbi release vagy eltérő CI-kulcs külön regisztrációt igényelhet.
4. Ezután megvalósítható az Android Google Identity AuthorizationClient alapú engedélykérés a kizárólag küldésre jogosító `https://www.googleapis.com/auth/gmail.send` scope-ra, majd Gmail API-küldés. A felhasználó a Google saját fiókválasztó és engedélyező felületét látja.
5. A rövid életű hozzáférés megújítását, visszavont engedélyt és a háttérben szükséges új bejelentkezést kezelni kell. Nem elég egyszer a Gmail alkalmazásba belépni. Éles kiadás előtt a projekt tényleges Google-követelményeit ellenőrizni kell.

A jelen közvetlen pilothoz:

- Csomagnév: `hu.smsfwd.app`
- SHA-1: `03:1C:0A:49:F6:38:03:60:D7:F4:BA:D3:FD:0C:6E:66:6C:CB:68:A6`

Ez nyilvános tanúsítvány-ujjlenyomat, nem aláírókulcs vagy jelszó. A Google-fiók jelszavát, privát kulcsot vagy kliens-titkot nem kell chatben megadni, és Android APK-ba sem szabad közös kliens-titkot beégetni. A regisztráció és a Google engedélyezése nélkül a fiók-összekapcsolás nem tesztelhető vagy tekinthető késznek.

Hivatalos leírások:

- https://developers.google.com/identity/authorization/android
- https://developers.google.com/gmail/api/auth/scopes
- https://developers.google.com/gmail/api/guides/sending
