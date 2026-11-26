package com.kenai.android

import com.kenai.android.core.ChatConversationOption
import com.kenai.android.core.ChatMessage
import com.kenai.android.core.ChatRole
import com.kenai.android.core.ChatSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSearchTest {
    private val a = ChatConversationOption("a", "Recetas de Pasta", 10)
    private val b = ChatConversationOption("b", "Kotlin coroutines", 30)
    private val c = ChatConversationOption("c", "", 20)
    private val items = listOf(a, b, c)
    private val held = mapOf(
        "a" to listOf(ChatMessage("1", ChatRole.USER, "how long to boil spaghetti?")),
        "c" to listOf(ChatMessage("2", ChatRole.ASSISTANT, "Use a SUPERVISOR scope")),
    )

    @Test fun aBlankQueryReturnsEverythingAsItWas() {
        assertEquals(items, ChatSearch.filter(items, held, ""))
        assertEquals(items, ChatSearch.filter(items, held, "   "))
    }

    @Test fun titlesMatchIgnoringCaseAndAccents() {
        assertEquals(listOf(a), ChatSearch.filter(items, held, "recetas"))
        assertEquals(listOf(b), ChatSearch.filter(items, held, "KOTLIN"))
        assertEquals(listOf(a), ChatSearch.filter(items, held, "pasta"))
        assertEquals(listOf(ChatConversationOption("x", "Canción nueva")), ChatSearch.filter(listOf(ChatConversationOption("x", "Canción nueva")), emptyMap(), "cancion"))
    }

    @Test fun theTextOfCachedMessagesMatchesToo() {
        assertEquals(listOf(a), ChatSearch.filter(items, held, "spaghetti"))
        assertEquals(listOf(c), ChatSearch.filter(items, held, "supervisor"))
    }

    @Test fun everyWordMustMatchSomewhereInTheSameConversationAndOrderIsKept() {
        assertEquals(listOf(a), ChatSearch.filter(items, held, "pasta boil"))
        assertEquals(emptyList<ChatConversationOption>(), ChatSearch.filter(items, held, "pasta coroutines"))
        assertEquals(items, ChatSearch.filter(items, held, "s"))
    }

    @Test fun noMatchGivesAnEmptyListAndNothingIsSentAnywhere() {
        assertEquals(emptyList<ChatConversationOption>(), ChatSearch.filter(items, held, "zzz"))
        assertEquals(emptyList<ChatConversationOption>(), ChatSearch.filter(emptyList(), emptyMap(), "a"))
    }


    @Test fun anIndexBuiltOnceAnswersLikeTheDirectFilter() {
        val index = ChatSearch.index(items, held)
        for (q in listOf("", "recetas", "spaghetti", "pasta boil", "pasta coroutines", "zzz", "SUPERVISOR", "cancion")) {
            assertEquals(q, ChatSearch.filter(items, held, q), index.filter(q))
        }
    }
}
