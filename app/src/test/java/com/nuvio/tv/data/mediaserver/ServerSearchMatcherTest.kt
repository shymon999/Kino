package com.nuvio.tv.data.mediaserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSearchMatcherTest {
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
