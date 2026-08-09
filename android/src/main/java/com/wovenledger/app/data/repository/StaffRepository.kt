package com.wovenledger.app.data.repository

import com.wovenledger.app.data.dao.StaffDao
import com.wovenledger.app.data.entities.Staff
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaffRepository @Inject constructor(private val dao: StaffDao) {

    suspend fun create(staff: Staff): Long = dao.insert(staff)

    fun read(id: Long): Flow<Staff?> = dao.getStaff(id)

    suspend fun update(staff: Staff) = dao.update(staff)

    suspend fun delete(staff: Staff) = dao.delete(staff)

    fun getAll(): Flow<List<Staff>> = dao.getAllStaff()

    fun getByPlant(plantId: Long): Flow<List<Staff>> = dao.getStaffByPlant(plantId)

    fun findForPlant(plantId: Long): Flow<List<Staff>> = dao.getStaffByPlant(plantId)

    fun findRecent(): Flow<List<Staff>> = dao.getAllStaff()
}
