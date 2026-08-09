package com.wovenledger.app.data.repository

import com.wovenledger.app.data.entities.PaymentMode

/**
 * The portal spells payment modes as display labels ("Bank Transfer"); Room stores
 * them as enum constants (BANK_TRANSFER). Both directions of that translation live
 * here so receipts and payments cannot drift apart on it — and so a mode the server
 * adds later degrades to Cash rather than crashing a sync mid-list.
 */
fun paymentModeOf(wire: String?): PaymentMode = when (wire?.uppercase()?.replace(' ', '_')) {
    PaymentMode.BANK_TRANSFER.name -> PaymentMode.BANK_TRANSFER
    PaymentMode.UPI.name -> PaymentMode.UPI
    PaymentMode.CHEQUE.name -> PaymentMode.CHEQUE
    else -> PaymentMode.CASH
}

/** The label the server's `Receipt::MODES` / `Payment::MODES` allow-lists accept. */
fun PaymentMode.toWire(): String = when (this) {
    PaymentMode.CASH -> "Cash"
    PaymentMode.BANK_TRANSFER -> "Bank Transfer"
    PaymentMode.UPI -> "UPI"
    PaymentMode.CHEQUE -> "Cheque"
}
