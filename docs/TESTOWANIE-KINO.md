# Testowanie Kino

Ten dokument oddziela testy kodu od prób na fizycznych telewizorach. Kompilacja i poprawny test JVM nie potwierdzają płynności obrazu, dźwięku ani obsługi każdego pilota.

## Regresja zmian uniwersalnych

17.10.2026 wybrana regresja zakończyła się powodzeniem: full 141 testów w 20 zestawach, family 140 testów w 19 zestawach. W obu wariantach nie było błędów ani pominięć. Zestawy w znacznej części się pokrywają, więc nie jest to 281 różnych scenariuszy.

Zestawy testów obejmują:

- domyślny brak limitu w wariancie uniwersalnym oraz domyślne 40 Mb/s w rodzinnym;
- zapis ustawień, ukrywanie gatunków i zachowanie tytułów w wyszukiwaniu;
- pełne gatunki Emby bez prywatnego wykluczania anime, dzieci i sportu;
- pomijanie MaxStreamingBitrate w JSON oraz wysyłanie jawnie wybranego limitu;
- zachowanie limitu przy zmianie ścieżki i przejściu do transkodowania;
- pułap automatycznej adaptacji;
- świeży kreator Emby, migrację ustawień, ponowienie po błędzie i brak możliwości ukończenia bez wybranej biblioteki;
- katalog ustawień, wyszukiwanie i wyłączenie updatera oryginalnego Nuvio w forku.

Testy używają atrap i kontrolowanych odpowiedzi HTTP. Nie są próbą transkodowania na dowolnym realnym serwerze. Wybrane zestawy regresji nie oznaczają uruchomienia wszystkich testów upstream.

## Regresja ustawień plakatów

Zapis, usunięcie i ponowne włączenie własnego klucza, błąd zapisu, walidacja API v3, plakat serialu, odpowiedź HTTP 401, powrót do grafiki Emby i spóźnione odpowiedzi po wyłączeniu TMDB są objęte testami kodu. 17.10.2026 regresja full obejmująca te przypadki oraz katalogi, wyszukiwanie i odtwarzanie zakończyła się wynikiem 155 testów / 22 zestawy, bez błędów i pominięć.

## APK i architektury

Minimalny system wynika z minSdk 24: Android 7. Uniwersalny APK ma zawierać armeabi-v7a, arm64-v8a, x86 i x86_64. Inwentaryzacja bibliotek i zawartości ZIP potwierdza obecność ABI, ale nie uruchomienie na czterech fizycznych urządzeniach.

Przed udostępnieniem kandydata należy sprawdzić podpis, identyfikator aplikacji, etykietę Kino, minimalny SDK, biblioteki natywne i brak prywatnych wartości wariantu rodzinnego. Przed publikacją kodu potrzebny jest także przegląd historii repo i ustawień CI.

## Próby na TV przed szerszą dystrybucją

1. Na świeżej instalacji przejść pilotem od logowania Emby do wyboru bibliotek i ekranu głównego. Sprawdzić błędne hasło, pustą bibliotekę, Wstecz i ponowienie.
2. Na aktualizacji zachować istniejące konto, biblioteki, układ i historię lokalną. Zweryfikować, że konto Nuvio nie jest wymagane.
3. Sprawdzić Filmy i Seriale, wszystkie gatunki, ukrycie i przywrócenie anime/dzieci, kolejność rzędów oraz zachowanie po restarcie. Powtórzyć z polskim językiem aplikacji na angielskim systemie TV.
4. Wybrać Bez limitu, 20, 40 i 80 Mb/s. Rozpocząć nowy tytuł, zmienić audio i napisy, przewinąć oraz wrócić do kontynuowania oglądania. Limit powinien być respektowany w negocjacji z serwerem, o ile serwer na to pozwala.
5. Włączyć eksperymentalną adaptację i ograniczyć przepustowość w kontrolowanym środowisku. Sprawdzić pozycję, zmianę jakości, dźwięk i zachowanie HDR. Nie wymuszać tej opcji wszystkim użytkownikom bez prób.
6. Uruchomić dwa urządzenia na tym samym koncie. Sprawdzić niezależność postępu i zatrzymywanie wyłącznie własnej sesji transkodowania.
7. Sprawdzić wpisywanie i wyszukiwanie głosowe, literówki tytułów i nazwisk, filmografię oraz odświeżenie po dodaniu tytułu na serwerze.
8. Odtworzyć różne formaty na ARM32 i ARM64, w tym starszym Android TV. Obecność biblioteki AV1/HEVC w APK nie gwarantuje dekodera sprzętowego TV.

Dotychczasowe próby rodzinnego wariantu na TV nie zastępują prób nowego kreatora i nowych ustawień wersji uniwersalnej. Nie potwierdzono tej wersji na wszystkich modelach Android TV. Tizen i webOS nie uruchamiają tego APK.
