package co.chinho.readabilityreader.data.remote

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyHasher @Inject constructor() {
    fun computeApiKey(username: String, password: String): String {
        return md5("$username:$password")
    }

    fun computePasswordOnlyHash(password: String): String {
        return md5(password)
    }

    private fun md5(input: String): String {
        val md5 = MessageDigest.getInstance("MD5")
        val bytes = md5.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
