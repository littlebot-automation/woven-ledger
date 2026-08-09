package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.PartyDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.PartyDao
import com.wovenledger.app.data.entities.BalanceType
import com.wovenledger.app.data.entities.Party
import com.wovenledger.app.data.entities.PartyType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking

/**
 * The party sync carries the two figures a party is judged by, and they are not the
 * same figure: `opening_balance` is an unsigned amount plus a side of the ledger, and
 * `ledger_balance` is the live total, SIGNED, where negative means we owe them. Mixing
 * them up, or dropping the sign, turns a supplier into a debtor with a number beside
 * their name that still looks plausible.
 */
class PartyRepositoryTest {

    private val dao: PartyDao = mock()
    private val api: WovenLedgerApiService = mock()
    private val repository = PartyRepository(dao, api)

    // ---------------------------------------------------------------- sync mapping

    @Test
    fun `an opening balance we owe is stored as an amount plus the TO_PAY side`() = runTest {
        serverReturns(partyDto(openingBalance = 125_050L, openingBalanceType = "to_pay"))

        repository.syncFromApi()

        val stored = insertedParty()
        assertEquals(125_050L, stored.openingBalance)
        assertEquals(BalanceType.TO_PAY, stored.openingBalanceType)
    }

    @Test
    fun `an opening balance owed to us is stored on the TO_RECEIVE side`() = runTest {
        serverReturns(partyDto(openingBalance = 250_000L, openingBalanceType = "to_receive"))

        repository.syncFromApi()

        val stored = insertedParty()
        assertEquals(250_000L, stored.openingBalance)
        assertEquals(BalanceType.TO_RECEIVE, stored.openingBalanceType)
    }

    /**
     * Only the exact wire token "to_pay" means we owe. Anything else — an absent field
     * on an older server, a typo, a label the portal renames — must fall to
     * TO_RECEIVE rather than silently reverse the direction of the money.
     */
    @Test
    fun `an absent or unrecognised balance side falls back to TO_RECEIVE`() = runTest {
        serverReturns(
            partyDto(id = 1, openingBalanceType = null),
            partyDto(id = 2, openingBalanceType = "TO_PAY"),
            partyDto(id = 3, openingBalanceType = "payable"),
        )

        repository.syncFromApi()

        assertEquals(
            listOf(BalanceType.TO_RECEIVE, BalanceType.TO_RECEIVE, BalanceType.TO_RECEIVE),
            insertedParties().map { it.openingBalanceType },
        )
    }

    /**
     * The running total keeps its sign, unlike the opening balance: it is one signed
     * number, and `abs()` applied here would report every creditor as a debtor.
     */
    @Test
    fun `the live ledger balance keeps its sign`() = runTest {
        serverReturns(
            partyDto(id = 1, ledgerBalance = -125_050L),
            partyDto(id = 2, ledgerBalance = 250_000L),
            partyDto(id = 3, ledgerBalance = 0L),
        )

        repository.syncFromApi()

        assertEquals(listOf(-125_050L, 250_000L, 0L), insertedParties().map { it.ledgerBalance })
    }

    /** The opening figure and the running total are separate columns, not one value. */
    @Test
    fun `the opening balance and the ledger balance are kept apart`() = runTest {
        serverReturns(
            partyDto(
                openingBalance = 100_000L,
                openingBalanceType = "to_receive",
                ledgerBalance = -45_000L,
            )
        )

        repository.syncFromApi()

        val stored = insertedParty()
        assertEquals(100_000L, stored.openingBalance)
        assertEquals(-45_000L, stored.ledgerBalance)
    }

    @Test
    fun `the party type arrives lowercase from the API and is mapped to the enum`() = runTest {
        serverReturns(
            partyDto(id = 1, type = "customer"),
            partyDto(id = 2, type = "supplier"),
            partyDto(id = 3, type = "both"),
        )

        repository.syncFromApi()

        val stored = insertedParties()
        assertEquals(
            listOf(PartyType.CUSTOMER, PartyType.SUPPLIER, PartyType.BOTH),
            stored.map { it.type },
        )
        assertEquals(listOf(1L, 2L, 3L), stored.map { it.id })
    }

    /**
     * Address, phone and GST are all nullable on the wire. Address and phone are
     * non-null columns and must land as empty strings; GSTIN stays null, because an
     * empty GSTIN and an unregistered party are different things.
     */
    @Test
    fun `absent optional fields do not become the string null`() = runTest {
        serverReturns(partyDto(address = null, phone = null, gstNumber = null))

        repository.syncFromApi()

        val stored = insertedParty()
        assertEquals("", stored.address)
        assertEquals("", stored.phone)
        assertNull(stored.gstin)
    }

    /** A re-sync refreshes the existing row rather than inserting a duplicate. */
    @Test
    fun `a party already in the database is updated, not inserted again`() = runTest {
        whenever(api.getParties()).thenReturn(
            listOf(partyDto(id = 7, openingBalance = 500L, openingBalanceType = "to_pay"))
        )
        whenever(dao.getParty(7L)).thenReturn(flowOf(party(id = 7L)))

        repository.syncFromApi()

        val captor = argumentCaptor<Party>()
        verifyBlocking(dao) { update(captor.capture()) }
        assertEquals(500L, captor.firstValue.openingBalance)
        assertEquals(BalanceType.TO_PAY, captor.firstValue.openingBalanceType)
        verifyBlocking(dao, never()) { insert(any()) }
    }

    // --------------------------------------------------------------------- pruning

    /**
     * Upserting alone left parties deleted on the portal on the phone for good. The
     * server's list is the whole truth about which parties exist.
     */
    @Test
    fun `parties the server no longer has are removed`() = runTest {
        serverReturns(partyDto(id = 1), partyDto(id = 2))

        repository.syncFromApi()

        verifyBlocking(dao) { deleteMissing(listOf(1L, 2L)) }
    }

    /**
     * "Keep none of them" is not expressible as `NOT IN ()` in SQLite, so an empty
     * server list has to clear the table outright — and must not be mistaken for
     * "nothing to prune".
     */
    @Test
    fun `an empty server list clears the table rather than keeping everything`() = runTest {
        serverReturns()

        repository.syncFromApi()

        verifyBlocking(dao) { deleteAll() }
        verifyBlocking(dao, never()) { deleteMissing(any()) }
    }

    // ---------------------------------------------------------------------- search

    @Test
    fun `search matches name, phone and GSTIN, and ignores case`() = runTest {
        whenever(dao.getAllParties()).thenReturn(
            flowOf(
                listOf(
                    party(id = 1, name = "Sharma Poly Bags"),
                    party(id = 2, name = "Verma Traders", phone = "9876543210"),
                    party(id = 3, name = "Gupta Weaving", gstin = "27AAECS1234F1Z5"),
                )
            )
        )

        assertEquals(listOf(1L), repository.search("sharma").first().map { it.id })
        assertEquals(listOf(2L), repository.search("98765").first().map { it.id })
        assertEquals(listOf(3L), repository.search("27aaecs").first().map { it.id })
        assertTrue(repository.search("Kolkata").first().isEmpty())
    }

    /** A party with no GSTIN is the common case; searching must not blow up on it. */
    @Test
    fun `search tolerates parties without a GSTIN`() = runTest {
        whenever(dao.getAllParties()).thenReturn(
            flowOf(listOf(party(id = 1, name = "Sharma Poly Bags", gstin = null)))
        )

        assertTrue(repository.search("27AAECS").first().isEmpty())
        assertEquals(listOf(1L), repository.search("poly").first().map { it.id })
    }

    // ------------------------------------------------------------- hasTransactions

    /**
     * DISABLED — this documents a production bug, it is not a regression guard.
     *
     * `PartyRepository.hasTransactions` returns
     *     `party != null && (party.openingBalance > 0 || party.openingBalanceType != null)`
     * but `Party.openingBalanceType` is a non-nullable `BalanceType` with a default, so
     * `!= null` is always true and the whole expression collapses to "this party
     * exists". A brand-new party with a zero balance and no documents reports true.
     *
     * It also never consults `PartyDao.hasTransactions(id)`, the SQL query that actually
     * counts invoices, bills, receipts and payments for the party — that query exists
     * and is unused. Nothing calls the repository method today, so the damage is latent;
     * the first delete guard wired to it would refuse to delete anything.
     *
     * Not fixed here: production code is owned elsewhere in this change.
     */
    @Ignore("Bug: hasTransactions is always true for an existing party — see kdoc")
    @Test
    fun `a party with no balance and no documents has no transactions`() = runTest {
        whenever(dao.getParty(1L)).thenReturn(flowOf(party(id = 1, openingBalance = 0L)))

        assertFalse(repository.hasTransactions(1L))
    }

    @Test
    fun `a party that does not exist has no transactions`() = runTest {
        whenever(dao.getParty(99L)).thenReturn(flowOf(null))

        assertFalse(repository.hasTransactions(99L))
    }

    // ---------------------------------------------------------------------- helpers

    private suspend fun serverReturns(vararg parties: PartyDto) {
        whenever(api.getParties()).thenReturn(parties.toList())
        // Nothing cached yet, so every row takes the insert path.
        whenever(dao.getParty(any())).thenReturn(flowOf(null))
        wheneverBlocking { dao.insert(any()) }.thenReturn(1L)
    }

    private fun insertedParty(): Party = insertedParties().single()

    private fun insertedParties(): List<Party> {
        val captor = argumentCaptor<Party>()
        verifyBlocking(dao, atLeastOnce()) { insert(captor.capture()) }
        return captor.allValues
    }

    private fun partyDto(
        id: Int = 1,
        name: String = "Sharma Poly Bags",
        type: String = "customer",
        address: String? = "Kanpur",
        phone: String? = "9876543210",
        gstNumber: String? = "09AAACS1234F1Z1",
        openingBalance: Long = 0L,
        openingBalanceType: String? = "to_receive",
        ledgerBalance: Long = 0L,
    ) = PartyDto(
        id = id,
        name = name,
        type = type,
        address = address,
        phone = phone,
        email = null,
        gstNumber = gstNumber,
        openingBalance = openingBalance,
        openingBalanceType = openingBalanceType,
        ledgerBalance = ledgerBalance,
        createdAt = "2026-01-01 00:00:00",
        updatedAt = "2026-01-01 00:00:00",
    )

    private fun party(
        id: Long = 1L,
        name: String = "Sharma Poly Bags",
        phone: String = "",
        gstin: String? = null,
        openingBalance: Long = 0L,
    ) = Party(
        id = id,
        name = name,
        type = PartyType.CUSTOMER,
        phone = phone,
        gstin = gstin,
        openingBalance = openingBalance,
    )
}
