package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.PaymentDao
import com.wovenledger.app.data.entities.Payment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(private val dao: PaymentDao) {

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
}
