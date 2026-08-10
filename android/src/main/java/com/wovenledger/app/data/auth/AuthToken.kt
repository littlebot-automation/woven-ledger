package com.wovenledger.app.data.auth

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * The JWT the server issued, kept so a restart does not mean signing in again.
 *
 * One fixed row, exactly as `settings` does it — there is one server and one login, so
 * a second row would only ever be a bug. The password is deliberately absent: the token
 * expires in thirty days and the user signs in again, whereas a stored password would
 * be a credential sitting on the phone forever with nothing to expire it.
 */
@Entity(tableName = "auth_token")
data class AuthToken(
    @PrimaryKey
    val id: Int = 1,
    val token: String,
    /** Epoch millis, for nothing but diagnostics — the server decides what is expired. */
    @ColumnInfo(name = "issued_at")
    val issuedAt: Long,
)

@Dao
interface AuthTokenDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(token: AuthToken)

    @Query("SELECT * FROM auth_token WHERE id = 1")
    suspend fun find(): AuthToken?

    @Query("DELETE FROM auth_token")
    suspend fun clear()
}
