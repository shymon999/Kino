<div align="center">
  <img src="app/src/main/res/drawable-nodpi/kino_banner.png" alt="Kino" width="480" />
</div>

# Kino

**Filmy i seriale z Emby na Android TV, z listami gatunków i prostą obsługą pilotem.**

Kino jest forkiem [NuvioTV](https://github.com/NuvioMedia/NuvioTV). Zachowuje jego interfejs i odtwarzacz, a rozwija obsługę bibliotek Emby: porządek na głównej, wyszukiwanie tolerujące literówki, lokalne kontynuowanie oglądania i ustawienia jakości.

W wersji uniwersalnej każdy podaje własny adres serwera i konto. Nie ma wbudowanego konta, hasła ani prywatnego klucza TMDB.

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

## Projekt bazowy i podziękowania

Kino korzysta z kodu, interfejsu, odtwarzacza i bibliotek projektu [NuvioTV autorstwa NuvioMedia](https://github.com/NuvioMedia/NuvioTV). Informacje licencyjne pozostają w [LICENSE](LICENSE), odpowiednich plikach projektu i ekranie **Licencje i podziękowania** w aplikacji.
