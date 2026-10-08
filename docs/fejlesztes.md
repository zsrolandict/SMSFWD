# Fejlesztési terv és ellenőrzések

> Az első futtatható 0.1-es verzió képességei, parancsai és korlátai a README-ben találhatók. A következő táblázat az eredeti célterv; az e-mail-integráció, a tartós újrapróbálási várólista és a fizikai készülékes pilot még nincs kész. A korábbi üres checkout állapot a tervezéskor készült felmérés, nem a jelenlegi tárolóállapot.

## Megvalósítási sorrend

| Lépés | Eredmény | Elkészülés feltétele |
|---|---|---|
| 1. Projekt és környezet | Kotlin/Compose projekt, rögzített Gradle-wrapper és függőségek | Debug APK build, futó egységteszt, lint; kipróbált környezetlépések mentése |
| 2. Szabálymotor | Feladó-, szöveg- és SIM-szűrés, célnormalizálás | Pozitív és negatív esetek, több szabály és azonos cél tesztje |
| 3. Helyi tárolás | Room, célpillanatképek, állapotátmenetek, foglalás | Tranzakciós és folyamat-helyreállítási tesztek |
| 4. Mobilfelület | Beállítási segítség, áttekintés, szabályok, előzmények | UI-tesztek mentésre, hibákra és szünetre; szimulált adat egyértelmű fejlesztői jelöléssel |
| 5. Valódi SMS | Bejövő események és saját SIM-es küldés | Fizikai telefonos fogadás és továbbítás; hosszú SMS, limit, dual SIM |
| 6. Valódi e-mail | Kiválasztott céges integráció | Külső tesztpostafiókban igazolt érkezés, offline újrapróbálás és hitelesítési hiba |
| 7. Belső pilot | Aláírt APK, telepítési és használati leírás | Valós eszközökön háttérteszt és frissítés adatvesztés nélkül |

A 2–5. lépés a levelezésre vonatkozó válasz nélkül is fejleszthető. Tesztadapter nem számít működő e-mail-integrációnak. Az éles aláírókulcsot nem találjuk ki és nem tesszük a tárolóba; külön megőrzendő. A debug APK fejlesztéshez használható, nem helyettesíti a belső kiadás folyamatát.

## Ellenőrzési terv

- Domain: pontos és betűs feladó, nem illeszkedés, kis-/nagybetű, ékezet, több cél, több szabály, nem egyértelmű telefonszám.
- Adatbázis: egy esemény többszöri feldolgozása, két külön azonos szövegű esemény, atomikus célképzés és keretfoglalás, egymással versenyző workerek, megszakadt ütemezés.
- Küldés: hálózat nélkül várakozó e-mail, ismert hiba és ismeretlen kimenetel elkülönítése, részleges SMS, callback időtúllépés, kézi újraküldés és napi limit.
- Felület: engedély megtagadása, hibás cím, beállítatlan e-mail, nincs kiválasztott SIM, szüneteltetés, célmódosítás várakozó feladat mellett.
- Készülék: lezárt képernyő és Doze, újraindítás/feloldás, force stop, térerőhiány, SIM-csere, dual SIM, hosszú ékezetes SMS, tényleges e-mail-érkezés.

Emulátoron szimulálható a fogadás és ellenőrizhető a felület. Valódi SMS-küldést, tarifát és gyártói háttérműködést csak fizikai telefon igazol. A teszteredményekben külön jelöljük a sikeres, hibás, kihagyott és el nem végzett ellenőrzéseket.

## Fenntartás és becslés

A következő számok tájékoztató tervezési becslések, nem aktuális szolgáltatói ajánlatok. Meglévő telefon, SIM és levelezési előfizetés mellett a közvetlen küldés infrastruktúra-többlete akár 0 Ft/hó. Külön kis átjáró hozzávetőleg 2–6 ezer Ft/hó infrastruktúrával tervezhető; levelezési díj, domain, mentés és üzemeltetői munka szükség szerint ezen felül.

SMS-költség = az összes célra ténylegesen elküldött díjazott szegmens × egységdíj. Például 1000 egy szegmenses küldés × 25 Ft = 25 000 Ft. Több címzett, fejléc, hosszú szöveg és Unicode további szegmenseket okozhat. A konkrét díjat és az automatizált használat feltételeit a SIM csomagja dönti el. Az e-mail ezért célszerű alapcsatorna.

Az első pilot becslése 7–12 fejlesztői munkanap egyszerű levelezési integrációval és korlátozott készülékkörrel. Ez nem határidővállalás; a hitelesítési követelmények és eszközhibák növelhetik. Stabil kis telepítésnél havi 1–3 munkaóra ellenőrzési/frissítési tartalék, platformváltozáskor külön fejlesztési munka tervezendő. A fejlesztői és karbantartási munka pénzbeli díját ez a dokumentum nem határozza meg.

## Nyitott információk

1. Céges levelezés szolgáltatója és a támogatott küldési mód; nem kérünk jelszót chatben.
2. Telefonmodellek, Android-verziók és darabszám a készülékes pilot előtt.
3. SMS-forgalom, célok száma, SIM-egységdíj és szükséges napi limit a valódi automatikus küldés előtt.
4. Elfogadható késleltetés: a háttérfeladatok nem garantálnak azonnali továbbítást.

A telefonok adatai nélkül Android 10+ és kis csapat a tervezési alap, nem igazolt készülékkompatibilitás. A levelezési adaptert a szolgáltató ismeretében választjuk ki. Ezek nem akadályozzák a projekt, szabálymotor és felület előkészítését.

## Környezet és jelenlegi bizonyíték

A `/workspace/SMSFWD` checkout kezdetben üres volt, a GitHub read-only hozzáférés működött, Java 21 elérhető. A jelen módosítás dokumentáció: alkalmazást nem buildeltünk és nem teszteltünk, Android SDK-t és buildverziókat még nem rögzítettünk. A környezet ezért nem tekinthető kész Android-fejlesztői környezetnek.

A projekt létrehozásakor a meglévő izolált checkoutot használjuk, külön worktree nélkül. A pinelt eszközökkel a buildet és érdemi teszteket ténylegesen futtatjuk, majd a bevált telepítési és indítási lépéseket a felhőkörnyezet konfigurációs tervezetébe mentjük. A mentés nem publikálás; az új felhős feladatban történő visszaállítást külön kell igazolni.
