<div align="center">
  <img src="app/src/main/res/drawable-nodpi/kino_banner.png" alt="Kino" width="480" />
  <h1>Kino</h1>
  <p><strong>Twoja biblioteka Emby. Wygodnie na dużym ekranie.</strong></p>
  <p>Fork NuvioTV dla Android TV i Google TV</p>
  <p>
    <a href="https://github.com/shymon999/Kino/releases"><img alt="Pobierz APK" src="https://img.shields.io/badge/Pobierz-APK-2ea44f?style=for-the-badge&amp;logo=android&amp;logoColor=white" /></a>
    <a href="LICENSE"><img alt="Licencja GPL-3.0" src="https://img.shields.io/badge/Licencja-GPL--3.0-blue?style=for-the-badge" /></a>
    <img alt="Android 7 i nowszy" src="https://img.shields.io/badge/Android-7.0%2B-3ddc84?style=for-the-badge&amp;logo=android&amp;logoColor=white" />
  </p>
  <p>
    <a href="#instalacja">Instalacja</a> ·
    <a href="#co-potrafi">Funkcje</a> ·
    <a href="#zrzuty-ekranu">Zrzuty ekranu</a> ·
    <a href="#ustawienia-kino">Ustawienia</a> ·
    <a href="docs/ZMIANY-KINO.md">Opis zmian</a> ·
    <a href="https://github.com/shymon999/Kino/issues">Zgłoś błąd</a>
  </p>
</div>

Kino jest forkiem [NuvioTV](https://github.com/NuvioMedia/NuvioTV). Zachowuje jego interfejs i odtwarzacz, a rozwija obsługę bibliotek Emby: porządek na głównej, wyszukiwanie tolerujące literówki, lokalne kontynuowanie oglądania i ustawienia jakości.

W wersji uniwersalnej każdy podaje własny adres serwera i konto. Nie ma wbudowanego konta, hasła ani prywatnego klucza TMDB.

> [!NOTE]
> Pierwsze publiczne wydanie ma status **preview**. Testy kodu i weryfikacja APK przeszły pomyślnie, ale nowy kreator oraz ustawienia wersji uniwersalnej wymagają jeszcze prób na różnych fizycznych TV. Kino nie zawiera filmów ani dostępu do serwera.

## Instalacja

**[Pobierz Kino-uniwersalna.apk](https://github.com/shymon999/Kino/releases/download/kino-preview-2026.10.17/Kino-uniwersalna.apk)**

1. Pobierz APK z [wydań projektu](https://github.com/shymon999/Kino/releases) i przenieś go na telewizor, na przykład aplikacją do przesyłania plików w sieci lokalnej albo przez USB.
2. Otwórz plik na TV. Jeśli Android o to poprosi, zezwól użytemu menedżerowi plików na instalację z tego źródła.
3. Uruchom **Kino**, podaj własne dane Emby i wybierz biblioteki.

Jeden plik obsługuje ARM 32-bit, ARM 64-bit, x86 i x86_64. Nie trzeba wybierać bitowości. Kino instaluje się obok oryginalnego NuvioTV. Aktualizacje Kino instaluj na istniejącą aplikację, bez usuwania jej danych.

## Zrzuty ekranu

<p align="center">
  <img src="docs/images/filmy.png" alt="Zakładka Filmy z rzędami gatunków" width="820" />
</p>

| Seriale i oceny na plakatach | Filmografia aktora |
| --- | --- |
| ![Seriale](docs/images/seriale.png) | ![Filmografia](docs/images/filmografia.png) |

Zrzuty z wcześniejszych testów TV pokazują przeglądanie katalogów i filmografię. Nie przedstawiają nowego kreatora ani panelu Ustawienia Kino. Plakaty i zawartość zależą od podłączonego serwera; polskie plakaty z testowej konfiguracji nie są gwarancją polskich grafik na każdym serwerze.

## Co potrafi

| Funkcja | Jak działa |
| --- | --- |
| Główna, Filmy i Seriale | Osobne zakładki, kontynuowanie oglądania oraz listy gatunków z bibliotek serwera. |
| Gatunki | Normalizacja podobnych nazw i tłumaczenie zgodne z językiem aplikacji, także na TV z innym językiem systemu. |
| Własny układ | Wszystkie gatunki są domyślnie dostępne. Można ukrywać wybrane gatunki, pojedyncze rzędy i zmieniać ich kolejność. Anime i treści dziecięce są dostępne. |
| Wyszukiwanie tytułów | Oprócz zapytania do Emby aplikacja wykorzystuje indeks tytułów i dopasowanie tolerujące błędy. |
| Aktorzy i filmografie | Można wyszukać osobę albo wejść w obsadę i zobaczyć jej tytuły dostępne na podłączonym serwerze. |
| Historia lokalna | Postęp oglądania jest oddzielny dla instalacji. Dwa TV ze wspólnym kontem Emby mogą mieć własne kontynuowanie oglądania. |
| Jakość | Domyślnie bez limitu bitrate. Dostępne limity od 10 do 100 Mb/s i opcjonalna automatyczna adaptacja. |
| Plakaty i oceny | Plakaty oraz oceny pochodzą z Emby. Widoczność ocen na plakatach można zmienić. |
| Losowanie | Kolejność zmienia się między uruchomieniami, a podczas przeglądania stronicowanie zachowuje wspólny seed. |
| Pilot i głos | Interfejs jest przeznaczony do pilota. Dostępność rozpoznawania mowy zależy od TV i pilota; dopasowanie działa też na tekście wpisanym ręcznie. |

## Wyszukiwanie z literówkami

Przykłady sprawdzone na bibliotece testowej:

| Wpisane zapytanie | Przykład dopasowania |
| --- | --- |
| `szklanka pulapa` | Tytuły z serii „Szklana pułapka”. |
| `Ziemia gangsterow` | „Strefa gangsterów”. |
| `nold schwarezenegger` | Filmy z Arnoldem Schwarzeneggerem. |
| `Bruce Willis` | Dostępne na serwerze filmy z tym aktorem. |

Wyniki zależą od zawartości bibliotek i metadanych Emby. Mechanizm ogranicza fałszywe dopasowania, nie poprawia dowolnej błędnej nazwy. Pierwsze wyszukiwanie z błędami w dużej bibliotece może wymagać zbudowania indeksu; kolejne korzystają z pamięci podręcznej. Nowe pozycje nie wymagają aktualizacji APK, indeks odświeża się okresowo.

## Pierwsze uruchomienie

1. Podaj adres Emby, na przykład `https://emby.example.com`, nazwę użytkownika i hasło.
2. Wybierz biblioteki, które chcesz przeglądać.
3. Wybierz **Zacznij oglądać**.

Konto Nuvio nie jest wymagane do podłączenia Emby. Dane połączenia są przechowywane przez istniejący mechanizm poświadczeń aplikacji. Przy aktualizacji zachowywane są dotychczasowe połączenia i ustawienia.

## Ustawienia Kino

W ustawieniach aplikacji pierwsza sekcja **Ustawienia Kino** zbiera:

- **Maksymalny bitrate:** Bez limitu, 10, 15, 20, 25, 30, 40, 60, 80 lub 100 Mb/s. Zmiana jest odczytywana przy uruchamianiu tytułu. „Bez limitu” pomija ograniczenie bitrate w żądaniu do Emby.
- **Automatyczna jakość:** osobny, domyślnie wyłączony przełącznik.
- **Ukryj listy gatunków:** ukrywanie wspólne dla wybranych gatunków. Tytuły pozostają dostępne w wyszukiwaniu.
- **Widoczność i kolejność list:** osobne sterowanie poszczególnymi rzędami filmów i seriali.
- **Oceny przy plakatach:** włączenie albo wyłączenie oznaczeń ocen.
- **Serwery mediów:** konta, biblioteki i odświeżanie ich danych.

Pozostałe ustawienia NuvioTV, w tym język aplikacji, dźwięk, napisy i wygląd, pozostają dostępne w swoich sekcjach.

### Jakość i transkodowanie

Bez limitu aplikacja może odtwarzać materiał bezpośrednio, jeśli TV obsługuje jego format. Limit jest przekazywany do Emby; uprawnienia konta i ustawienia serwera nadal obowiązują. Nominalny bitrate nie jest pomiarem chwilowego transferu sieci.

**Automatyczna jakość jest funkcją eksperymentalną.** Korzysta z bufora i pomiarów pobierania. Przy odpowiedzi HLS z jednym wariantem zmiana wymaga nowej sesji transkodowania i może spowodować krótką przerwę. Wymagane są uprawnienia serwera do transkodowania. Nie ma gwarancji zachowania HDR ani natychmiastowego usunięcia zacięć przy nagłym spadku prędkości.

Oceny na kartach pochodzą z metadanych Emby. Sama wartość `CommunityRating` nie gwarantuje, że jej dostawcą jest IMDb. Uniwersalny APK używa plakatów serwera; prywatny klucz TMDB nie jest do niego dołączany.

## Telewizory i architektury

- Android TV / Google TV, minimum **Android 7, API 24**.
- Uniwersalny APK obejmuje **armeabi-v7a, arm64-v8a, x86 i x86_64**. Android wybiera właściwe biblioteki.
- Tizen i webOS wymagają innej aplikacji.

Obsługa architektury nie jest gwarancją działania każdego kodeka na każdym modelu TV. Pamięć urządzenia, dekodery, wersja Androida oraz możliwości transkodowania Emby wpływają na odtwarzanie. Nowa wersja uniwersalna wymaga jeszcze prób na różnych fizycznych odbiornikach.

## Budowanie

Projekt używa Gradle Wrapper. Lokalnie używany jest JDK 21 i Android SDK zgodny z konfiguracją projektu. Do budowy wersji uniwersalnej nie są potrzebne prywatne dane wariantu rodzinnego.

```powershell
git clone --branch emby-gatunki https://github.com/shymon999/Kino.git
cd Kino
```

Przykład lokalnego kandydata podpisanego kluczem diagnostycznym w PowerShell:

```powershell
$env:CI_USE_DEBUG_SIGNING = 'true'
.\gradlew.bat :app:assembleFullRelease --no-parallel --max-workers=1
```

Bez `-PembyAbi` Gradle przygotowuje paczki poszczególnych architektur oraz APK uniwersalny w `app/build/outputs/apk/full/release/`. Wariant przeznaczony do udostępniania powinien mieć własny, stały podpis; kolejne aktualizacje wymagają tego samego klucza.

Wariant `family` jest osobną konfiguracją prywatną z własnym identyfikatorem aplikacji. Nie jest wymagany do budowy `full`, a jego dane połączenia pozostają poza repozytorium. Publicznie udostępniaj wariant uniwersalny.

## Aktualizacje i testowanie

Na tym etapie aktualizacje Kino instaluje się ręcznie, z zachowaniem podpisu i danych aplikacji. Mechanizm pobierania aktualizacji oryginalnego Nuvio jest wyłączony dla pakietu forka.

Opis techniczny zmian: [Różnice względem NuvioTV](docs/ZMIANY-KINO.md). Zakres potwierdzeń i plan prób: [Testowanie](docs/TESTOWANIE-KINO.md).

## Pytania i odpowiedzi

<details>
<summary><strong>Czy potrzebuję konta Nuvio albo klucza TMDB?</strong></summary>

Do pierwszego startu wystarczą adres Emby i własne konto. Metadane i grafiki w publicznej paczce pochodzą z serwera. Konto Nuvio i prywatny klucz TMDB nie są wymagane do tego sposobu używania Kino.

</details>

<details>
<summary><strong>Czy dwa TV na jednym koncie mają wspólne kontynuowanie oglądania?</strong></summary>

Kino prowadzi postęp lokalnie dla każdej instalacji. Dwa telewizory mogą korzystać z jednego konta Emby i mieć oddzielną historię. Usunięcie danych aplikacji usuwa lokalny postęp.

</details>

<details>
<summary><strong>Czy anime i treści dla dzieci są usunięte?</strong></summary>

W publicznej wersji wszystkie gatunki są domyślnie dostępne. Wybierz te, których list nie chcesz oglądać, w Ustawienia Kino. Ukryte tytuły nadal można znaleźć w wyszukiwaniu.

</details>

<details>
<summary><strong>Czy jakość sama spada przy wolniejszym internecie?</strong></summary>

Samo ustawienie limitu nie włącza adaptacji. Służy do tego osobny, eksperymentalny przełącznik Automatyczna jakość. Działanie zależy od transkodowania na serwerze, a zmiana może przerwać obraz na chwilę. Domyślnie adaptacja jest wyłączona, a limit nie jest narzucony.

</details>

## Zgłaszanie błędów i rozwój

W [zgłoszeniu](https://github.com/shymon999/Kino/issues/new/choose) podaj wersję Kino, model TV, wersję Androida, typ połączenia i kroki odtworzenia błędu. Dla problemów z odtwarzaniem dodaj format wideo/audio i informację o transkodowaniu. Usuń z logów hasła, tokeny, adresy prywatnych serwerów i dane konta.

Zmiany specyficzne dla Emby są utrzymywane jako osobne commity na gałęzi `emby-gatunki`, aby ułatwić późniejsze pobieranie zmian z NuvioTV. Procesy automatyzacji odziedziczone po upstream nie są skonfigurowanym systemem wydań Kino. Publiczne APK są na razie przygotowywane i sprawdzane lokalnie.

## Projekt bazowy i podziękowania

Kino korzysta z kodu, interfejsu, odtwarzacza i bibliotek projektu [NuvioTV autorstwa NuvioMedia](https://github.com/NuvioMedia/NuvioTV). Informacje licencyjne pozostają w [LICENSE](LICENSE), odpowiednich plikach projektu i ekranie **Licencje i podziękowania** w aplikacji.

Kod jest udostępniany na licencji **GPL-3.0**, zgodnie z projektem bazowym. Kino jest niezależnym forkiem i nie jest oficjalną aplikacją NuvioMedia ani Emby. Grafiki filmów i seriali należą do ich właścicieli.
