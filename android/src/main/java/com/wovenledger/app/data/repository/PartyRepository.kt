package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.NewParty
import com.wovenledger.app.data.api.PartyDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.PartyDao
import com.wovenledger.app.data.entities.BalanceType
import com.wovenledger.app.data.entities.Party
import com.wovenledger.app.data.entities.PartyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartyRepository @Inject constructor(
    private val dao: PartyDao,
    private val api: WovenLedgerApiService
) {

    suspend fun create(party: Party): Long = dao.insert(party)

    fun read(id: Long): Flow<Party?> = dao.getParty(id)

    suspend fun update(party: Party) = dao.update(party)

    suspend fun delete(party: Party) = dao.delete(party)

    fun getAll(): Flow<List<Party>> = dao.getAllParties()

    fun getCustomers(): Flow<List<Party>> = dao.getCustomers()

    fun getSuppliers(): Flow<List<Party>> = dao.getSuppliers()

    fun search(q: String): Flow<List<Party>> = dao.getAllParties().map { parties ->
        parties.filter { party ->
            party.name.contains(q, ignoreCase = true) ||
            party.phone.contains(q, ignoreCase = true) ||
            party.gstin?.contains(q, ignoreCase = true) ?: false
        }
    }

    /**
     * Whether any document points at this party — the question worth asking before
     * offering to delete one.
     *
     * This used to test `openingBalanceType != null` on a non-nullable field, so it
     * answered true for every party that existed. The DAO query counts the invoices,
     * bills, receipts and payments that actually reference it.
     */
    suspend fun hasTransactions(partyId: Long): Boolean =
        dao.hasTransactions(partyId.toInt()) > 0

    fun findRecent(): Flow<List<Party>> = dao.getAllParties()

    /**
     * Registers a party on the server and mirrors it into Room.
     *
     * Writes are online-only by design: this suspends until the server answers and
     * throws — including [retrofit2.HttpException] on a 422 — rather than queueing.
     */
    suspend fun createOnServer(party: NewParty): Party = store(api.createParty(party))

    suspend fun updateOnServer(id: Long, party: NewParty): Party =
        store(api.updateParty(id.toInt(), party))

    /** Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it. */
    suspend fun syncFromApi() {
        val dtos = api.getParties()

        dtos.forEach { dto ->
            val party = toEntity(dto)
            if (dao.getParty(party.id).first() == null) {
                dao.insert(party)
            } else {
                dao.update(party)
            }
        }

        // Sync was upsert-only, so a party deleted on the portal used to linger on
        // the phone for good. The server's list is the whole truth about what exists.
        val ids = dtos.map { it.id.toLong() }
        if (ids.isEmpty()) dao.deleteAll() else dao.deleteMissing(ids)
    }

    private suspend fun store(dto: PartyDto): Party = toEntity(dto).also { dao.insert(it) }

    private fun toEntity(dto: PartyDto) = Party(
        id = dto.id.toLong(),
        name = dto.name,
        type = PartyType.valueOf(dto.type.uppercase()),
        address = dto.address ?: "",
        phone = dto.phone ?: "",
        gstin = dto.gstNumber,
        // Two different figures: the opening one an edit form rewrites, and the
        // running total the portal derives and the lists display.
        openingBalance = dto.openingBalance,
        openingBalanceType = if (dto.openingBalanceType == "to_pay") {
            BalanceType.TO_PAY
        } else {
            BalanceType.TO_RECEIVE
        },
        ledgerBalance = dto.ledgerBalance
    )
}
