package com.wovenledger.app.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * The party ledger — spec §4.4.
 *
 * A running account for one party: every sales invoice, purchase bill, receipt and
 * payment that touches them, oldest first, each carrying the balance as it stood after
 * that entry.
 *
 * The ledger is *derived*, so it lives on its own Retrofit interface rather than on
 * [WovenLedgerApiService] and is never cached in Room. A stored copy would go stale the
 * moment any document behind it changed, and a wrong balance is worse than a slow one.
 *
 * Signs are the server's and mean the same thing everywhere in this app:
 *
 *   POSITIVE => they owe us (receivable)
 *   NEGATIVE => we owe them (payable)
 *
 * Money is integer paise on the wire, matching the Room entities.
 */
data class PartyLedgerDto(
    @SerializedName("party_id")
    val partyId: Long,
    @SerializedName("party_name")
    val partyName: String,
    /** Signed paise: the balance after the last entry, or zero for a party with no history. */
    @SerializedName("closing_balance")
    val closingBalance: Long,
    /** Oldest first — the order is the server's and must not be re-sorted. */
    @SerializedName("entries")
    val entries: List<LedgerEntryDto> = emptyList()
)

data class LedgerEntryDto(
    /** ISO 'yyyy-MM-dd'. */
    @SerializedName("date")
    val date: String,
    /** Human label, e.g. "Sales Invoice", "Receipt", "Opening Balance". */
    @SerializedName("type")
    val type: String,
    /** The document number, or "-" for the opening balance. */
    @SerializedName("reference")
    val reference: String,
    /** Signed paise: what this entry did to the balance. */
    @SerializedName("amount")
    val amount: Long,
    /** Signed paise: the running balance after this entry. */
    @SerializedName("balance")
    val balance: Long,
    /** Machine tag: "opening", "sales", "purchase", "receipt" or "payment". */
    @SerializedName("kind")
    val kind: String,
    /** The document behind the entry; null for the opening balance, which has none. */
    @SerializedName("document_id")
    val documentId: Long?
)

interface LedgerApiService {
    /** 200 with the running account, or 404 when no such party exists. */
    @GET("/api/parties/{id}/ledger")
    suspend fun getPartyLedger(@Path("id") partyId: Long): PartyLedgerDto
}
