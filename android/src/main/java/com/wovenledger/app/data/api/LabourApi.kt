package com.wovenledger.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The labour vertical — staff, their daily work, and what they are owed.
 *
 * This lives on its own Retrofit interface rather than on [WovenLedgerApiService]
 * so the two can move independently.
 *
 * Money is integer paise on the wire, matching the Room entities.
 */
data class StaffDto(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("role")
    val role: String?,
    /** Null while a staff member is not assigned to a plant. */
    @SerializedName("plant_id")
    val plantId: Int?,
    @SerializedName("phone")
    val phone: String?,
    /** "daily" or "piece" — maps onto [com.wovenledger.app.data.entities.StaffWageType]. */
    @SerializedName("wage_type")
    val wageType: String,
    @SerializedName("daily_rate")
    val dailyRate: Long,
    @SerializedName("piece_rate")
    val pieceRate: Long,
    /** Whichever of the two rates the wage type selects. */
    @SerializedName("effective_rate")
    val effectiveRate: Long,
    /** Total of this member's unpaid work entries, in paise. */
    @SerializedName("unpaid_wages")
    val unpaidWages: Long
)

data class StaffWorkDto(
    @SerializedName("id")
    val id: Int,
    /** ISO 'yyyy-MM-dd'. */
    @SerializedName("date")
    val date: String,
    @SerializedName("staff_id")
    val staffId: Int,
    @SerializedName("plant_id")
    val plantId: Int?,
    @SerializedName("work_type")
    val workType: String,
    /** Days worked, or pieces produced — a count, not money. */
    @SerializedName("qty")
    val qty: Double,
    @SerializedName("rate")
    val rate: Long,
    /** qty × rate, in paise. Room derives this rather than storing it. */
    @SerializedName("amount")
    val amount: Long,
    @SerializedName("paid")
    val paid: Boolean,
    /** The wage payment that settled this entry, once one has. */
    @SerializedName("payment_voucher_id")
    val paymentVoucherId: Int?
)

/**
 * A new day's work, posted to the server.
 *
 * The amount is deliberately absent — the server derives it from qty × rate — and so
 * is `paid`, which only wage settlement may set.
 */
data class NewStaffWork(
    /** ISO 'yyyy-MM-dd'. */
    @SerializedName("date")
    val date: String,
    @SerializedName("staff_id")
    val staffId: Int,
    /** Null is a valid choice: work need not be tied to a plant. */
    @SerializedName("plant_id")
    val plantId: Int?,
    @SerializedName("work_type")
    val workType: String,
    @SerializedName("qty")
    val qty: Double,
    /** Paise. Null takes the staff member's own wage rate. */
    @SerializedName("rate")
    val rate: Long?
)

/** Body of a 422: field name to message, e.g. {"errors": {"qty": "Qty is required"}}. */
data class StaffWorkErrors(
    @SerializedName("errors")
    val errors: Map<String, String> = emptyMap()
)

interface LabourApiService {
    @GET("/api/staff")
    suspend fun getStaff(): List<StaffDto>

    @GET("/api/staff/{id}")
    suspend fun getStaffMember(@Path("id") id: Int): StaffDto

    @GET("/api/staff-work")
    suspend fun getStaffWork(): List<StaffWorkDto>

    @GET("/api/staff-work/{id}")
    suspend fun getStaffWorkEntry(@Path("id") id: Int): StaffWorkDto

    /** 201 with the created entry, or 422 with a per-field error map. */
    @POST("/api/staff-work")
    suspend fun createStaffWork(@Body entry: NewStaffWork): StaffWorkDto
}
