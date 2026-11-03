package cn.edu.meowmap.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Local prototype accounts. Remote credentials will be handled by the HTTPS API. */
object PasswordHasher {
    fun salt(): String = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), 120_000, 256)
        return try {
            Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
        } finally { spec.clearPassword() }
    }
    fun verify(password: String, salt: String, expected: String): Boolean =
        expected.isNotEmpty() && MessageDigest.isEqual(hash(password, salt).toByteArray(), expected.toByteArray())
}
