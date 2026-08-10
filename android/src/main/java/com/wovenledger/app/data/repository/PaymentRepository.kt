package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.NewPayment
import com.wovenledger.app.data.api.PaymentDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.PaymentDao
import com.wovenledger.app.data.entities.Payment
import com.wovenledger.app.data.entities.PaymentType
import com.wovenledger.app.data.outbox.Outbox
import com.wovenledger.app.data.outbox.OutboxOperation
import com.wovenledger.app.data.outbox.OutboxTarget
import com.wovenledger.app.data.outbox.attemptOnline
import com.wovenledger.app.data.outbox.nextLocalId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val dao: PaymentDao,
    private val api: WovenLedgerApiService,
    private val outbox: Outbox
) {

    suspend fun create(payment: Payment): Long = dao.insert(payment)

    fun read(id: Long): Flow<Payment?> = dao.getPayment(id)

    suspend fun update(payment: Payment) = dao.update(payment)

    suspend fun delete(payment: Payment) = dao.delete(payment)

    fun getAll(): Flow<List<Payment>> = dao.getAllPayments()

    fun getByParty(partyId: Long): Flow<List<Payment>> = dao.getPaymentsByParty(partyId)

    fun getByStaff(staffId: Long): Flow<List<Payment>> = dao.getPaymentsByStaff(staffId)

    fun findForParty(partyId: Long): Flow<List<Payment>> = dao.getPaymentsByParty(partyId)

    fun findForStaff(staffId: Long): Flow<List<Payment>> = dao.getPaymentsByStaff(staffId)

    fun sumAmount(): Flow<Long> = dao.getAllPayments().map { payments ->
        payments.sumOf { it.amount }
    }

    fun sumAmountForParty(partyId: Long): Flow<Long> = dao.getPaymentsByParty(partyId).map { payments ->
        payments.sumOf { it.amount }
    }

    fun sumAmountForStaff(staffId: Long): Flow<Long> = dao.getPaymentsByStaff(staffId).map { payments ->
        payments.sumOf { it.amount }
    }

    fun findRecent(): Flow<List<Payment>> = dao.getAllPayments().map { it.take(10) }

    /**
     * Books the payment on the server and mirrors it into Room; queues it if the
     * phone cannot reach the server. `PY-0012` is the server's to issue, so a queued
     * voucher waits without a number.
     */
    suspend fun createOnServer(payment: NewPayment): SavedDocument<Payment> {
        val dto = attemptOnline { api.createPayment(payment) } ?: return queueCreate(payment)

        return SavedDocument(store(dto))
    }

    suspend fun updateOnServer(id: Long, payment: NewPayment): SavedDocument<Payment> {
        // A document that has not uploaded has no server id to PUT to: editing it
        // rewrites the write already queued for it instead of asking the server about
        // a record it has never seen.
        if (id < 0) {
            return queueUpdate(id, payment)
        }

        val dto = attemptOnline { api.updatePayment(id.toInt(), payment) }
            ?: return queueUpdate(id, payment)

        return SavedDocument(store(dto))
    }

    private suspend fun queueCreate(body: NewPayment): SavedDocument<Payment> {
        val localId = nextLocalId(dao.minId())
        val echo = echoOf(localId, body)

        dao.insert(echo)
        outbox.enqueue(OutboxTarget.PAYMENT, OutboxOperation.CREATE, localId, body)

        return SavedDocument(echo, queued = true)
    }

    private suspend fun queueUpdate(id: Long, body: NewPayment): SavedDocument<Payment> {
        val stored = dao.getPayment(id).first()
        val edited = echoOf(id, body).copy(no = stored?.no.orEmpty())

        dao.insert(edited)
        outbox.enqueue(OutboxTarget.PAYMENT, OutboxOperation.UPDATE, id, body)

        return SavedDocument(edited, queued = true)
    }

    /**
     * Removes a locally written voucher that is never going to be uploaded.
     *
     * Only the outbox calls this, when the user abandons a queued write.
     */
    suspend fun deleteLocal(localId: Long) = dao.deleteById(localId)

    /** Sends a queued create, then swaps the local echo for what the server stored. */
    suspend fun uploadCreate(localId: Long, body: NewPayment): List<String> {
        val dto = api.createPayment(body)

        dao.deleteById(localId)
        store(dto)

        return emptyList()
    }

    suspend fun uploadUpdate(id: Long, body: NewPayment): List<String> {
        store(api.updatePayment(id.toInt(), body))

        return emptyList()
    }

    /** Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it. */
    suspend fun syncFromApi() {
        val dtos = api.getPayments()

        dtos.forEach { dto ->
            val payment = toEntity(dto)

            if (dao.getPayment(payment.id).first() == null) {
                dao.insert(payment)
            } else {
                dao.update(payment)
            }
        }

        // Upserting alone left payments deleted on the portal on the phone for good.
        // Queued vouchers are kept: absent from the server's list means "not sent yet".
        val keep = dtos.map { it.id.toLong() } + dao.pendingIds()
        if (keep.isEmpty()) dao.deleteSynced() else dao.deleteMissing(keep)
    }

    /** What a queued voucher looks like in the app while it waits — no number yet. */
    private fun echoOf(id: Long, body: NewPayment) = Payment(
        id = id,
        no = "",
        partyId = body.partyId?.toLong(),
        staffId = body.staffId?.toLong(),
        amount = body.amount,
        date = LocalDate.parse(body.paymentDate),
        type = PaymentType.valueOf(body.paymentType.uppercase()),
        mode = paymentModeOf(body.mode),
        notes = body.notes.orEmpty(),
    )

    private suspend fun store(dto: PaymentDto): Payment = toEntity(dto).also { dao.insert(it) }

    private fun toEntity(dto: PaymentDto) = Payment(
        id = dto.id.toLong(),
        no = dto.documentNumber,
        // A voucher points at one side or the other, never both: wages carry a staff
        // member where a party payment carries a party.
        partyId = dto.partyId?.toLong(),
        staffId = dto.staffId?.toLong(),
        amount = dto.amount,
        date = java.time.LocalDate.parse(dto.paymentDate),
        type = PaymentType.valueOf(dto.paymentType.uppercase()),
        mode = paymentModeOf(dto.mode),
        notes = dto.notes ?: ""
    )
}
