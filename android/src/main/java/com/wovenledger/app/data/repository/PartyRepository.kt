package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.NewParty
import com.wovenledger.app.data.api.PartyDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.PartyDao
import com.wovenledger.app.data.entities.BalanceType
import com.wovenledger.app.data.entities.Party
import com.wovenledger.app.data.entities.PartyType
import com.wovenledger.app.data.outbox.Outbox
import com.wovenledger.app.data.outbox.OutboxOperation
import com.wovenledger.app.data.outbox.OutboxTarget
import com.wovenledger.app.data.outbox.attemptOnline
import com.wovenledger.app.data.outbox.nextLocalId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartyRepository @Inject constructor(
    private val dao: PartyDao,
    private val api: WovenLedgerApiService,
    private val outbox: Outbox
) {

    suspend fun create(party: Party): Long = dao.insert(party)

    fun read(id: Long): Flow<Party?> = dao.getParty(id)

    suspend fun update(party: Party) = dao.update(party)

    suspend fun delete(party: Party) = dao.delete(party)

    fun getAll(): Flow<List<Party>> = dao.getAllParties()

    /**
     * The parties a document may be raised against.
     *
     * A party added with no signal exists only under a negative local id, and that id
     * means nothing to the server: an invoice carrying it as `party_id` would be
     * rejected on upload and stay rejected. So a pending party shows in the parties
     * list — marked as pending — but is not offered on a document form until it has
     * uploaded and has a real id.
     */
    fun getPostable(): Flow<List<Party>> = dao.getAllParties().map { parties ->
        parties.filter { it.id >= 0 }
    }

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
     * Registers a party on the server and mirrors it into Room; queues it if the
     * phone cannot reach the server.
     *
     * The server is asked first, so a 422 is shown while the form is still open.
     * Only a connectivity failure is queued.
     */
    suspend fun createOnServer(party: NewParty): SavedDocument<Party> {
        val dto = attemptOnline { api.createParty(party) } ?: return queueCreate(party)

        return SavedDocument(store(dto))
    }

    suspend fun updateOnServer(id: Long, party: NewParty): SavedDocument<Party> {
        // A document that has not uploaded has no server id to PUT to: editing it
        // rewrites the write already queued for it instead of asking the server about
        // a record it has never seen.
        if (id < 0) {
            return queueUpdate(id, party)
        }

        val dto = attemptOnline { api.updateParty(id.toInt(), party) }
            ?: return queueUpdate(id, party)

        return SavedDocument(store(dto))
    }

    /**
     * Writes the party locally under a negative id and queues it.
     *
     * A queued party is deliberately not offered on the document forms: an invoice
     * would carry its negative id as `party_id`, which means nothing to the server.
     * It becomes selectable the moment it uploads and has a real id.
     */
    private suspend fun queueCreate(body: NewParty): SavedDocument<Party> {
        val localId = nextLocalId(dao.minId())
        val echo = echoOf(localId, body)

        dao.insert(echo)
        outbox.enqueue(OutboxTarget.PARTY, OutboxOperation.CREATE, localId, body)

        return SavedDocument(echo, queued = true)
    }

    private suspend fun queueUpdate(id: Long, body: NewParty): SavedDocument<Party> {
        // The ledger balance is the portal's running total, derived from every
        // document on the account, so an edit here must not overwrite it with a guess.
        val stored = dao.getParty(id).first()
        val edited = echoOf(id, body).copy(ledgerBalance = stored?.ledgerBalance ?: 0L)

        dao.insert(edited)
        outbox.enqueue(OutboxTarget.PARTY, OutboxOperation.UPDATE, id, body)

        return SavedDocument(edited, queued = true)
    }

    /**
     * Removes a locally written party that is never going to be uploaded.
     *
     * Only the outbox calls this, when the user abandons a queued write.
     */
    suspend fun deleteLocal(localId: Long) = dao.deleteById(localId)

    /** Sends a queued create, then swaps the local echo for what the server stored. */
    suspend fun uploadCreate(localId: Long, body: NewParty): List<String> {
        val dto = api.createParty(body)

        dao.deleteById(localId)
        store(dto)

        return emptyList()
    }

    suspend fun uploadUpdate(id: Long, body: NewParty): List<String> {
        store(api.updateParty(id.toInt(), body))

        return emptyList()
    }

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
        // the phone for good. The server's list is the whole truth about what exists
        // there — but it says nothing about parties added here that have not uploaded
        // yet, so those are kept rather than pruned.
        val keep = dtos.map { it.id.toLong() } + dao.pendingIds()
        if (keep.isEmpty()) dao.deleteSynced() else dao.deleteMissing(keep)
    }

    /** What a queued party looks like in the app while it waits. */
    private fun echoOf(id: Long, body: NewParty) = Party(
        id = id,
        name = body.name,
        type = PartyType.valueOf(body.type.uppercase()),
        phone = body.phone.orEmpty(),
        gstin = body.gstNumber,
        address = body.address.orEmpty(),
        openingBalance = body.openingBalance,
        openingBalanceType = if (body.openingBalanceType == "to_pay") {
            BalanceType.TO_PAY
        } else {
            BalanceType.TO_RECEIVE
        },
        // The portal derives this from the documents on the account; a party nobody
        // has traded with yet is at zero either way.
        ledgerBalance = 0L,
    )

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
