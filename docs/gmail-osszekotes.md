# Gmail-fiók összekapcsolása

Az SMSFWD 0.3.2 Google-fiókos engedélykérést és Gmail API-küldést tartalmaz. A már bejelentkezett telefonos Google-fiók kiválasztható, de a Gmail alkalmazás belépése nem ad automatikus levélküldési engedélyt az SMSFWD-nek.

A regisztrációhoz és kipróbáláshoz kövesd a [Google Cloud beállítási útmutatót](google-cloud-beallitas.md). Ez tartalmazza a Gmail API, a tesztfelhasználó és az Android OAuth-kliens beállítását, a pontos csomagnevet és az aktuális aláíró tanúsítvány SHA-1 ujjlenyomatát.

A natív integráció készítése és a felhős build nem igazolja a valódi Google-engedélykérés sikerét. A Google Cloud regisztráció, telefonos fiókengedélyezés és valódi próba-e-mail még szükséges. Addig az SMTP + alkalmazásjelszó mód használható. Jelszót vagy tokent ne küldj chatben.
