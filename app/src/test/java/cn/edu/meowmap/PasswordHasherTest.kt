package cn.edu.meowmap

import cn.edu.meowmap.data.PasswordHasher
import org.junit.Assert.*
import org.junit.Test

class PasswordHasherTest {
    @Test fun acceptsCorrectPasswordAndRejectsIncorrectOne() {
        val salt = PasswordHasher.salt()
        val hash = PasswordHasher.hash("campus-cat-123", salt)
        assertNotEquals("campus-cat-123", hash)
        assertTrue(PasswordHasher.verify("campus-cat-123", salt, hash))
        assertFalse(PasswordHasher.verify("campus-cat-124", salt, hash))
    }
    @Test fun identicalPasswordsHaveDistinctSaltedHashes() {
        val first = PasswordHasher.salt()
        val second = PasswordHasher.salt()
        assertNotEquals(first, second)
        assertNotEquals(PasswordHasher.hash("campus-cat-123", first), PasswordHasher.hash("campus-cat-123", second))
    }
    @Test fun demonstrationAccountCannotAuthenticate() {
        assertFalse(PasswordHasher.verify("anything", "", ""))
    }
}
