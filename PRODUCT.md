# Kino

Klient Android TV oparty na NuvioTV, przeznaczony do bibliotek Emby. Sterowanie pilotem,
czytelność z kanapy i proste uruchamianie tytułów mają pierwszeństwo przed rozbudową interfejsu.

## Platform

Android TV / Google TV, Kotlin i Jetpack Compose TV. Minimum Android 7 (API 24).
Wersja rodzinna zachowuje konfigurację prywatną. Uniwersalna wymaga własnego serwera i konta,
nie zawiera danych rodzinnych i umożliwia wybór jakości oraz widoczności gatunków.

## Interfejs

Rozwijamy istniejące ustawienia i ich komponenty: ciemne tło, obecne motywy, duży tekst,
czytelny fokus pilota, pionowe listy opcji i istniejące dialogi wyboru. Zachowujemy zieloną
ikonę Kino. Nie tworzymy nowego systemu wizualnego dla panelu konfiguracji.

## Pierwsze uruchomienie

Uniwersalna wersja prowadzi do połączenia z Emby i wyboru bibliotek. Konto Nuvio pozostaje
opcjonalną funkcją. Aktualizacja zachowuje istniejące połączenia i ustawienia użytkownika.

## Dane

Katalogi i metadane pochodzą z serwera. Historia oglądania jest lokalna dla instalacji.
Hasła nie trafiają do logów ani dokumentacji. Zmiany przygotowujemy lokalnie, bez publikacji
do czasu wyraźnej decyzji użytkownika.
