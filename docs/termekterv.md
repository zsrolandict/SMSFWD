# Termékterv

## Cél és első verzió

A kolléga önállóan, néhány lépésben beállíthassa, mely bejövő SMS milyen címre menjen. A telefon végezze a szűrést és a továbbítást. Alapértelmezésként nem kell külön szerver; az e-mail-integráció kiválasztása még nyitott.

Induló tervezési feltételezés: kis csapat, telefononként önálló beállítások, Android 10 vagy újabb. A minimumverzió a tényleges készülékek alapján változhat. Nem szükséges AI vagy fizetős SMS API a szöveg továbbításához. A szöveget nem fogalmazzuk át automatikusan: az eredeti üzenet és egy érthető fejléc kerül a célhoz.

## Képernyők

### Beállítási segítség

1. Rövid magyarázat a fogadásról és a kimenő SMS díjáról.
2. SMS-fogadási engedély kérése. A küldési engedély csak SMS-cél beállításakor szükséges.
3. Első cél és szabály létrehozása. A „bármely feladó” külön tudatos választás.
4. Próbaüzenet előnézete, SMS esetén becsült szegmensszám, majd kézi küldés.
5. Állapotellenőrzés és készülékfüggő akkumulátor-beállítási segítség, ha szükséges.

Engedély megtagadásakor a beállítási felület továbbra is használható; a nem elérhető funkció okát szöveg mutatja. Tesztküldés valódi üzenetet küldhet és díjat okozhat, ezért csak a felhasználó kifejezett gombnyomására történik.

### Áttekintés

- „Továbbítás bekapcsolva” vagy „Továbbítás szünetel”.
- Aktív szabályok, várakozó küldések és hibák száma.
- Legutóbbi sikeres küldés ideje.
- „Hiányzik az SMS-engedély”, „Nincs internet” vagy „E-mail-fiók nincs beállítva” állapotjelzés.
- Globális kapcsoló és gyors hozzáférés új szabályhoz.

A szüneteltetés a még el nem kezdett küldéseket is megállítja. Már átadott SMS vagy e-mail nem hívható vissza. Szünet alatt az új SMS-ekhez nem készül továbbítási feladat, és visszakapcsoláskor sem történik automatikus visszamenőleges továbbítás. A korábban várakozó feladatok visszakapcsolás után folytatódnak.

### Szabályok és szabályszerkesztő

Mezők: szabály neve, bekapcsolás, bármely feladó vagy pontos feladó, opcionális szövegrészlet, fogadó SIM vagy bármely SIM, egy vagy több cél.

A megadott feltételek ÉS kapcsolatban állnak. A szövegszűrő kis-/nagybetűtől független, az ékezeteket megőrzi; reguláris kifejezés nem része az első változatnak. Telefonszámot országkóddal tárolunk, a betűs SMS-feladót külön szöveges azonosítóként kezeljük. Bizonytalan számot nem illesztünk pusztán az utolsó néhány számjegy alapján.

Több illeszkedő szabály mind lefut, de ugyanaz az üzenet ugyanarra a normalizált célra azonos csatornán csak egy küldési feladatot hoz létre. Cél lehet e-mail cím vagy nemzetközi formátumú telefonszám. E-mail helyi részét nem alakítjuk automatikusan kisbetűssé; domainje normalizálható.

### Előzmények

Üzenetenként feladó, érkezési idő és rövid szövegrészlet; célonként állapot és érthető hiba. Szűrés várakozó, elküldött és problémás küldésekre. Az „elküldve” a küldő szolgáltatás elfogadását jelenti; külön „kézbesítve” csak támogatott SMS-visszajelzés esetén jelenik meg.

A kézi újrapróbálás bizonytalan vagy részleges küldésnél előbb jelzi a duplikáció és az újabb díj lehetőségét. A küldés idején rögzített cél utólagos szerkesztéssel nem változik meg; várakozó feladat külön törölhető.

### Beállítások

E-mail-fiók vagy átjáró, kimenő SIM, napi SMS-szegmenslimit, előzménymegőrzés és próbaküldés. A limit beállítása nélkül az automatikus SMS-küldés nem indul el. A „korlátlan” nem alapérték. A díj nem állapítható meg a telefonból, ezért a felület szegmensszámot mutat, nem állít biztos forintösszeget.

## Üzenetformátum

E-mail tárgya: `SMS érkezett: +36301234567`. Törzse: eredeti feladó, érkezési idő időzónával, eredeti szöveg. Egyszerű szöveges tartalom; HTML-üzenetet nem generálunk az SMS-ből.

SMS: rövid, egyértelmű fejléc az eredeti feladóval, majd a teljes eredeti szöveg. Nem csonkítjuk és nem távolítjuk el automatikusan az ékezeteket. A továbbított SMS a küldő telefon SIM-számáról érkezik, nem az eredeti feladótól. A fejléc is része a díjazott szövegnek.

## Elfogadási feltételek

- Egy illeszkedő SMS a megfelelő célra kerül; nem illeszkedőből nem lesz feladat.
- Két azonos célt megadó szabály nem okoz két küldést ugyanarra az eseményre.
- Két külön, azonos szövegű SMS nem vész el tartalomalapú deduplikáció miatt.
- A többrészes bejövő SMS egy teljes üzenetként jelenik meg.
- A várólista újraindítás után megmarad, a megszakadt küldés kezelése nem okoz vak ismétlést.
- Az e-mail hálózat-visszatérés után újrapróbálódik; SMS nem igényel internetet.
- Hiányzó engedély, SIM, hitelesítés és limit érthető, külön állapotot ad.
- A napi limit több egyidejű feladat esetén sem léphető túl automatikus küldéssel.
- Kényszerleállítás után a felület jelzi a korlátot; ez nem kezelhető feltétel nélküli háttérgaranciával.

## Későbbi funkciók

Központi flottakezelés, távoli szabályszerkesztés, régi SMS-ek importálása, MMS/RCS, iPhone, automatikus szövegátfogalmazás és nyilvános Play-terjesztés nem része az első változatnak. A bejövő SMS-ek körét az Android rendszer által az alkalmazásnak átadott események adják; minden speciális üzenettípus átvételére nem teszünk ígéretet.
