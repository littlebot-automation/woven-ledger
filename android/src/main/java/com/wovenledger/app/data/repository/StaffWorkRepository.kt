package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.StaffWorkDao
import com.wovenledger.app.data.entities.StaffWork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaffWorkRepository @Inject constructor(private val dao: StaffWorkDao) {

    suspend fun create(work: StaffWork): Long = dao.insert(work)

    fun read(id: Long): Flow<StaffWork?> = dao.getWork(id)

    suspend fun update(work: StaffWork) = dao.update(work)

    suspend fun delete(work: StaffWork) = dao.delete(work)

    fun getByStaff(staffId: Long): Flow<List<StaffWork>> = dao.getWorkByStaff(staffId)

    fun getUnpaidWork(): Flow<List<StaffWork>> = dao.getUnpaidWork()

    fun findUnpaidFor(staffId: Long): Flow<List<StaffWork>> = dao.getWorkByStaff(staffId).map { works ->
        works.filter { !it.paid }
    }

    fun sumAmountForStaff(staffId: Long): Flow<Long> = dao.getWorkByStaff(staffId).map { works ->
        works.sumOf { it.getAmount() }
    }

    fun sumUnpaidAmount(): Flow<Long> = dao.getUnpaidWork().map { works ->
        works.sumOf { it.getAmount() }
    }

    fun findRecent(): Flow<List<StaffWork>> = dao.getUnpaidWork()
}
