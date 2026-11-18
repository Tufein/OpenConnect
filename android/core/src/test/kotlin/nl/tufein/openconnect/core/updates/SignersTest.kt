package nl.tufein.openconnect.core.updates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignersTest {
    private val mine = "06b67b82ec65b533343130fea378165b15ef520bc01969f47ff440242dbdf3b7"
    private val other = "aa".repeat(32)

    @Test
    fun `the same signer is accepted`() = assertTrue(Signers.compatible(setOf(mine), setOf(mine)))

    @Test
    fun `another signer is refused`() = assertFalse(Signers.compatible(setOf(mine), setOf(other)))

    @Test
    fun `a rotated key whose history still holds the installed key is accepted`() =
        assertTrue(Signers.compatible(setOf(mine), setOf(other, mine)))

    @Test
    fun `a file whose signers cannot be read is refused`() = assertFalse(Signers.compatible(setOf(mine), emptySet()))

    @Test
    fun `an installed app whose signers cannot be read never matches`() = assertFalse(Signers.compatible(emptySet(), setOf(mine)))

    @Test
    fun `the digest is the lower-case sha-256 of the certificate bytes`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Signers.digest("abc".toByteArray()))
    }
}
