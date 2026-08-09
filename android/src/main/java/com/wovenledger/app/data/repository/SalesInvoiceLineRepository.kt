package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.SalesInvoiceLineDao
import com.wovenledger.app.data.entities.SalesInvoiceLine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalesInvoiceLineRepository @Inject constructor(private val dao: SalesInvoiceLineDao) {

    suspend fun create(line: SalesInvoiceLine): Long = dao.insert(line)

    fun getByInvoice(invoiceId: Long): Flow<List<SalesInvoiceLine>> = dao.getLinesByInvoice(invoiceId)

    suspend fun delete(line: SalesInvoiceLine) = dao.delete(line)

    suspend fun deleteByInvoice(invoiceId: Long) {
        dao.getLinesByInvoice(invoiceId).collect { lines ->
            lines.forEach { line -> dao.delete(line) }
        }
    }

    fun hasEntriesFor(invoiceId: Long): Flow<List<SalesInvoiceLine>> = dao.getLinesByInvoice(invoiceId)
}
