package com.wovenledger.app.data.repository

import com.wovenledger.app.data.api.WovenLedgerApiService
import com.wovenledger.app.data.dao.PaymentDao
import com.wovenledger.app.data.entities.Payment
import com.wovenledger.app.data.entities.PaymentMode
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

    /** Throws on failure so [com.wovenledger.app.data.sync.SyncManager] can report it. */
    suspend fun syncFromApi() {
        val apiPayments = api.getPayments()
        apiPayments.forEach { dto ->
            val payment = Payment(
                id = dto.id.toLong(),
                no = dto.documentNumber,
                partyId = dto.partyId?.toLong(),
                staffId = null,
                amount = dto.amount,
                date = java.time.LocalDate.parse(dto.paymentDate),
                type = PaymentType.valueOf(dto.paymentType.uppercase()),
                // API sends display labels such as "Bank Transfer".
                mode = dto.mode
                    ?.let { PaymentMode.valueOf(it.uppercase().replace(' ', '_')) }
                    ?: PaymentMode.CASH,
                notes = dto.notes ?: ""
            )
            val existing = dao.getPayment(payment.id).first()
            if (existing == null) {
                dao.insert(payment)
            } else {
                dao.update(payment)
            }
        }
    }
}
