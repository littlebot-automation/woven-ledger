package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.PartyDao
import com.wovenledger.app.data.entities.Party
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartyRepository @Inject constructor(private val dao: PartyDao) {

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

    suspend fun hasTransactions(partyId: Long): Boolean {
        val party = dao.getParty(partyId).first()
        return party != null && (
            party.openingBalance > 0 ||
            party.openingBalanceType != null
        )
    }

    fun findRecent(): Flow<List<Party>> = dao.getAllParties()
}
