package com.wovenledger.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette, taken from the design tokens in docs/SPEC.md §8 so the app reads
 * as the same product as the web portal.
 *
 * The portal is a warm-paper surface with a deep navy chrome and an amber accent —
 * deliberately not a stock Material palette.
 */

// Core brand
val WovenNavy = Color(0xFF1D3557)
val WovenNavyDark = Color(0xFF142238)
val WovenNavyTint = Color(0xFFE7ECF3)

val WovenAmber = Color(0xFFC97A2B)
val WovenAmberTint = Color(0xFFF7E7D4)

// Surfaces
val WovenPaper = Color(0xFFF5F2EA)
val WovenSurface = Color(0xFFFFFFFF)
val WovenBorder = Color(0xFFE3DDCE)
val WovenBorderSoft = Color(0xFFEDE8DC)

// Ink
val WovenInk = Color(0xFF20242B)
val WovenInkSoft = Color(0xFF565F6B)

// Status
val WovenSuccess = Color(0xFF2A9D4A)
val WovenSuccessTint = Color(0xFFE3F5E8)
val WovenDanger = Color(0xFFC0392B)
val WovenDangerTint = Color(0xFFFBE8E5)

// Dark-theme counterparts. The portal has no dark mode, so these are derived:
// the navy becomes the surface and the amber carries the accent, keeping the
// brand recognisable rather than inventing a second identity.
val WovenDarkBackground = Color(0xFF11161F)
val WovenDarkSurface = Color(0xFF1A2130)
val WovenDarkInk = Color(0xFFE8EAEE)
val WovenDarkInkSoft = Color(0xFFA7B0BE)
val WovenNavyLight = Color(0xFF8FB0DA)
val WovenAmberLight = Color(0xFFE9A868)
