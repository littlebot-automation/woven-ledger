package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.NewPayment
import com.wovenledger.app.data.api.PaymentDto
import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.PaymentDao
import com.wovenledger.app.data.entities.Payment
import com.wovenledger.app.data.entities.PaymentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val dao: PaymentDao,
    private val api: WovenLedgerApiService
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

    /** Books the payment on the server and mirrors it into Room. */
    suspend fun createOnServer(payment: NewPayment): Payment = store(api.createPayment(payment))

    suspend fun updateOnServer(id: Long, payment: NewPayment): Payment =
        store(api.updatePayment(id.toInt(), payment))

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
        val ids = dtos.map { it.id.toLong() }
        if (ids.isEmpty()) dao.deleteAll() else dao.deleteMissing(ids)
    }

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
