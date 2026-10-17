package com.nuvio.tv.data.mediaserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSearchMatcherTest {
    @Test fun familyFastMatcherPreservesRankingForTitlesPeopleAndUnicode() {
        val queries = listOf("nold schwarezenegger", "szklanka pulapa", "Bruce Willis", "Bruec Wills",
            "Ziemia gangsterów", "Harry Poter", "Nowy świat", "Czlowiek z wysokiego zamku", "nold", "abc",
            "pokaz filmy z Bruce Willis", "Strefa gangsterów", "schwrazeneggre", "żółć śnieg", "new world order")
        val names = queries + listOf("Arnold Schwarzenegger", "Arnold Vosloo", "Szklana pułapka", "Szklany klosz",
            "Brice Williams", "Bruce Wszechmogący", "Strefa gangsterów", "Ziemia obiecana", "Człowiek z wysokiego zamku",
            "Harry Potter", "Schwarzenegger", "ŻÓŁĆ ŚNIEG", "New World", "New Order", "", "李小龍")
        for (query in queries) {
            val original = ServerSearchMatcher(query)
            val optimized = ServerSearchMatcher(query, fast = true)
            repeat(2) {
                for (name in names) for (person in listOf(false, true)) {
                    assertEquals("$query / $name / person=$person", original.score(name, person), optimized.score(name, person), 0.0)
                }
            }
        }
    }
    @Test fun recognizesClippedFirstNameOnlyWhenTheSurnameAlsoMatches() {
        val matcher = ServerSearchMatcher("nold schwarezenegger")
        assertTrue(matcher.score("Arnold Schwarzenegger", person = true) >= 0.7)
        assertEquals(0.0, matcher.score("Arnold Schwarzenegger"), 0.0)
        assertEquals(0.0, matcher.score("Arnold Vosloo", person = true), 0.0)
        assertEquals(0.0, ServerSearchMatcher("nold").score("Arnold", person = true), 0.0)
        assertEquals(0.0, ServerSearchMatcher("Bruce Willis").score("Brice Williams", person = true), 0.0)
    }

    @Test fun boundedDistancePreservesTwoEditsAndAdjacentTranspositions() {
        assertTrue(serverSearchScore("schwarezenegger", "Schwarzenegger") > 0)
        assertTrue(serverSearchScore("schwrazeneggre", "Schwarzenegger") > 0)
        assertEquals(0.0, serverSearchScore("schwejzenegker", "Schwarzenegger"), 0.0)
    }
    @Test fun recognizesDictationMistakesEvenWithoutAnExactWord() {
        assertTrue(serverSearchScore("szklanka pulapa", "Szklana pułapka") > 0)
        assertTrue(serverSearchScore("pokaz filmy z Bruce Willis", "Bruce Willis") > 0)
        assertTrue(serverSearchScore("Bruec Wills", "Bruce Willis") > 0)
        assertEquals(0.0, serverSearchScore("Bruce Willis", "Bruce Wszechmogący"), 0.0)
        assertEquals(0.0, serverSearchScore("szklanka pulapa", "Szklany klosz"), 0.0)
    }
    @Test fun recognizesMisrememberedTitleWithDistinctiveSharedWord() {
        assertTrue(serverSearchScore("Ziemia gangsterów", "Strefa gangsterów") > 0)
        assertEquals(0.0, serverSearchScore("Ziemia gangsterów", "Ziemia obiecana"), 0.0)
    }

    @Test fun toleratesAccentsAndSingleLetterMistakes() {
        assertTrue(serverSearchScore("Harry Poter", "Harry Potter") > 0)
        assertEquals(1.0, serverSearchScore("Czlowiek z wysokiego zamku", "Człowiek z wysokiego zamku"), 0.0)
        assertTrue(serverSearchScore("Harry Potter", "Harry Potter") > serverSearchScore("Harry Poter", "Harry Potter"))
    }

    @Test fun ignoresCommandsButDoesNotMatchUnrelatedTitlesOnShortWords() {
        assertEquals(listOf("gangsterów", "Ziemia"), fallbackServerSearchTerms("Pokaż serial Ziemia gangsterów"))
        assertTrue(fallbackServerSearchTerms("Gangsterzy").isEmpty())
        assertEquals(0.0, serverSearchScore("Nowy świat", "Nowy dzień"), 0.0)
        assertEquals(0.0, serverSearchScore("Strefa gangsterów", "Gra o tron"), 0.0)
    }
}
