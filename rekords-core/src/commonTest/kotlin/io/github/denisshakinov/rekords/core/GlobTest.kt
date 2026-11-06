package io.github.denisshakinov.rekords.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What SQLite's `GLOB` answers for each pattern, which [matchesGlob] has to answer the same. */
class GlobTest {

    @Test
    fun text_matches_itself_alone_and_case_counts() {
        assertTrue("Doom".matchesGlob("Doom"))
        assertTrue("".matchesGlob(""))
        assertFalse("Doom".matchesGlob("doom"))
        assertFalse("Doom 2".matchesGlob("Doom"))
        assertFalse("Doom".matchesGlob("Doom 2"))
    }

    @Test
    fun asterisk_matches_any_run_of_characters() {
        assertTrue("".matchesGlob("*"))
        assertTrue("Doom".matchesGlob("*"))
        assertTrue("Doom".matchesGlob("D*"))
        assertTrue("Doom".matchesGlob("Doom*"))
        assertTrue("Silent Hill 2".matchesGlob("*Hill*"))
        assertTrue("abcabc".matchesGlob("*abc"))
        assertTrue("aXbXc".matchesGlob("a*b*c"))
        assertTrue("Doom".matchesGlob("**m"))
        assertFalse("Doom".matchesGlob("*x*"))
        assertFalse("abcab".matchesGlob("*abc"))
    }

    @Test
    fun question_mark_matches_exactly_one_character() {
        assertTrue("Doom 2".matchesGlob("Doom ?"))
        assertTrue("Doom".matchesGlob("D??m"))
        assertTrue("Doom".matchesGlob("*?"))
        assertFalse("Doom".matchesGlob("Doom?"))
        assertFalse("".matchesGlob("?"))
        assertFalse("".matchesGlob("*?"))
    }

    @Test
    fun set_matches_one_character_listed_or_within_a_range() {
        assertTrue("b".matchesGlob("[abc]"))
        assertFalse("d".matchesGlob("[abc]"))
        assertTrue("7".matchesGlob("[0-9]"))
        assertTrue("q".matchesGlob("[a-fq]"))
        assertFalse("g".matchesGlob("[a-fq]"))
        assertFalse("ab".matchesGlob("[ab]"))
        assertFalse("".matchesGlob("[ab]"))
        // A range the wrong way round holds nothing.
        assertFalse("m".matchesGlob("[z-a]"))
    }

    @Test
    fun caret_inverts_a_set() {
        assertTrue("d".matchesGlob("[^abc]"))
        assertFalse("a".matchesGlob("[^abc]"))
        assertFalse("".matchesGlob("[^abc]"))
        // Anywhere but first it is a character of the set.
        assertTrue("^".matchesGlob("[a^]"))
    }

    @Test
    fun bracket_first_and_dash_at_either_end_are_characters_of_the_set() {
        assertTrue("]".matchesGlob("[]a]"))
        assertTrue("a".matchesGlob("[]a]"))
        assertFalse("]".matchesGlob("[^]a]"))
        assertTrue("b".matchesGlob("[^]a]"))
        assertTrue("-".matchesGlob("[-a]"))
        assertTrue("-".matchesGlob("[a-]"))
        assertFalse("b".matchesGlob("[a-]"))
        // A `]` leading the set does not begin a range.
        assertTrue("-".matchesGlob("[]-a]"))
        assertFalse("^".matchesGlob("[]-a]"))
        // Nor does a range end another one.
        assertTrue("-".matchesGlob("[a-c-e]"))
        assertFalse("d".matchesGlob("[a-c-e]"))
    }

    @Test
    fun wildcards_stand_for_themselves_within_a_set() {
        assertTrue("*".matchesGlob("[*]"))
        assertFalse("a".matchesGlob("[*]"))
        assertTrue("?".matchesGlob("[?]"))
        assertTrue("[".matchesGlob("[[]"))
        assertTrue("a*b".matchesGlob("a[*]b"))
    }

    @Test
    fun set_left_open_matches_nothing() {
        assertFalse("[".matchesGlob("["))
        assertFalse("a".matchesGlob("[a"))
        assertFalse("]".matchesGlob("[]"))
        assertFalse("".matchesGlob("*["))
        // A `]` outside a set is a character like any other.
        assertTrue("]".matchesGlob("]"))
    }

    @Test
    fun character_beyond_the_basic_multilingual_plane_is_one_character() {
        val gamepad = "🎮"
        assertTrue(gamepad.matchesGlob("?"))
        assertFalse(gamepad.matchesGlob("??"))
        assertTrue("Doom $gamepad".matchesGlob("Doom ?"))
        assertTrue(gamepad.matchesGlob("[$gamepad]"))
        assertTrue(gamepad.matchesGlob("[🌀-🏿]"))
    }
}
