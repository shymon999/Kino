# Różnice Kino względem NuvioTV

Kino rozwija klienta Emby na bazie NuvioTV. Zachowuje Compose, obsługę pilota i odtwarzacz projektu bazowego. Nie jest oficjalną aplikacją NuvioMedia ani Emby.

## Biblioteki i strona główna

Zakładki Główna, Filmy i Seriale prezentują biblioteki podłączonego serwera. Rzędy gatunków powstają z metadanych Emby. Nazwy są normalizowane, między innymi dla wariantów Sci-Fi i nazw polskich/angielskich, a etykiety korzystają z języka aplikacji. W zakładce Filmy nagłówek kategorii nie powtarza słowa Filmy.

Uniwersalny wariant korzysta z pełnych gatunków serwera. Nie wymaga minimalnej liczby tytułów w gatunku i nie stosuje prywatnego wykluczania anime, dzieci ani sportu. Użytkownik może ukrywać gatunki oraz sterować widocznością i kolejnością poszczególnych rzędów. Ukrycie rzędu nie usuwa tytułów z wyszukiwania. Nowe pozycje są pobierane z serwera przy odświeżaniu danych, bez przebudowy APK.

Losowanie katalogów wykorzystuje seed sesji, aby kolejne strony przeglądanej listy zachowywały spójne zapytanie. Metadane i oceny pochodzą z Emby. Plakaty domyślnie pochodzą z Emby; opcjonalnie użytkownik może włączyć polskie plakaty TMDB i podać własny klucz API v3. Ocena CommunityRating nie oznacza automatycznie oceny IMDb.

## Wyszukiwanie

Oprócz standardowego wyszukiwania Emby klient wykorzystuje pamiętany indeks tytułów, normalizację znaków i ocenę podobieństwa. Obsługuje część literówek, pominiętych słów i niedokładnych nazw. Wynik rozpoznawania mowy trafia do tego samego mechanizmu co wpisany tekst. Rozpoznawanie głosu zależy od usług i pilota telewizora.

Wyszukiwanie osób i filmografie wykorzystują metadane obsady. Wyniki dotyczą tytułów dostępnych na serwerze. Progi dopasowania ograniczają przypadkowe trafienia, więc nie każda błędna nazwa da wynik. Indeks ma pamięć podręczną i okres odświeżania; pierwsze zapytanie do dużej biblioteki może potrwać dłużej.

## Historia oglądania

Postęp serwera zapisywany jest lokalnie dla instalacji Kino. Kontynuowanie oglądania nie korzysta ze wspólnej historii konta Emby. Sesje i pozycje nie synchronizują się pomiędzy telewizorami. Usunięcie danych aplikacji usuwa tę lokalną historię.

Zakończenie własnej sesji transkodowania jest oddzielone od raportowania wspólnej historii konta. Klient zatrzymuje własną sesję, zamiast kończyć odtwarzanie pozostałych urządzeń użytkownika.

## Ustawienia jakości

W uniwersalnym wariancie domyślny limit wynosi Bez limitu. Użytkownik może wybrać 10, 15, 20, 25, 30, 40, 60, 80 albo 100 Mb/s. Preferencja jest odczytywana przy uruchamianiu tytułu i zachowywana przy zmianach ścieżki lub awaryjnym przejściu do transkodowania.

Brak limitu reprezentuje null i pominięcie MaxStreamingBitrate w żądaniu oraz profilu urządzenia. Jest to zgodne z nullable polem w modelach Emby: [PlaybackInfoRequest](https://dev.emby.media/reference/pluginapi/MediaBrowser.Model.MediaInfo.PlaybackInfoRequest.html) i [DeviceProfile](https://dev.emby.media/reference/pluginapi/MediaBrowser.Model.Dlna.DeviceProfile.html). Limity konta i serwera nadal obowiązują.

Eksperymentalna automatyczna jakość jest osobnym przełącznikiem, domyślnie wyłączonym. Polityka uwzględnia bufor i pomiary transferu oraz respektuje wybrany pułap. Dla HLS z jednym wariantem zmiana może wymagać ponownego transkodowania z zachowaniem pozycji, co oznacza krótką przerwę i możliwą zmianę HDR. Nie zastępuje to uprawnień i wydajności transkodowania serwera.

## Pierwszy start i tożsamość aplikacji

Wariant uniwersalny zaczyna od logowania do Emby i wyboru bibliotek. Konto Nuvio oraz QR nie są wymaganym etapem tego kreatora. Istniejące połączenia i ustawienia wyglądu są zachowywane przy migracji. Przy świeżym starcie wybierany jest prosty układ, odtwarzanie pierwszego źródła serwera i wyłączone zwiastuny.

Sekcja Ustawienia Kino skupia opcje forka. Ekran O Kino wskazuje projekt bazowy i prowadzi do licencji. Pakiet com.nuvio.tv.emby, nazwa Kino, własna grafika i podpis pozwalają instalować aplikację obok oryginału. Pobieranie aktualizacji oryginalnego Nuvio jest wyłączone, ponieważ jego APK nie aktualizuje pakietu forka.

## Warianty i prywatność paczek

Wariant full jest uniwersalny i nie zawiera prywatnego konta Emby ani prywatnego klucza TMDB. Własny klucz podany w ustawieniach plakatów jest przechowywany lokalnie w ProtectedProfilePreferences z szyfrowaniem Android Keystore. Można go usunąć lub wyłączyć TMDB bez usuwania klucza. Zmiana źródła odświeża istniejące rzędy i przywraca plakat Emby. Wariant family jest odrębną prywatną dystrybucją, z innym identyfikatorem i konfiguracją poza repo. Jej domyślny limit 40 Mb/s i prywatny dobór kategorii nie są ustawieniami wersji uniwersalnej.

Konfiguracja użytkownika nie jest przenoszona przez wysłanie samego APK. Każda instalacja uniwersalna wymaga własnego logowania. Pliki podpisu, local.properties i prywatne dane wdrożenia nie służą do publikacji.

## Najważniejsze miejsca w kodzie

| Obszar | Pliki |
| --- | --- |
| Ustawienia instalacji | KinoPreferencesDataStore.kt |
| Katalogi i gatunki | ServerCatalog.kt, EmbyProvider.kt |
| Logowanie i biblioteki | UniversalEmbySetupScreen.kt, ServerRepository.kt |
| Jakość i sesje | ServerPlayback.kt, ServerBitratePolicy.kt, PlayerRuntimeControllerServer.kt |
| Interfejs ustawień | KinoSettingsContent.kt, KinoAboutSettingsContent.kt |
| Warianty i ABI | app/build.gradle.kts |

Stan potwierdzeń i ograniczenia opisuje [Testowanie](TESTOWANIE-KINO.md).
