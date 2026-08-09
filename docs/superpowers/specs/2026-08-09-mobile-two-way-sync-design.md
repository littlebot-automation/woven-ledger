# Woven Ledger Mobile — Full UI + Two-Way Sync

**Date:** 2026-08-09
**Status:** Design, pending implementation

---

## Context

The Android app (`android/`) currently ships 22 placeholder screens. Every screen renders
the same centred "title / description / Back" stub from `ui/screens/Screens.kt:209`. The
data layer beneath them is real and working — Room with 13 repositories and thorough DAO
queries — and as of today a read-only JSON API exists at `/api/*` and syncs five entities
(parties, items, sales invoices, purchase bills, payments) from the Symfony portal.

Three gaps make the app unusable today:

1. **No screens.** Synced data lands in Room and is never rendered.
2. **Incomplete read API.** Invoice and bill **line items** are not exposed, so a document
   detail screen has nothing to show. Receipts, staff, staff work, plants and settings have
   no endpoints at all.
3. **No writes.** The portal is the only place records can be created.

The intended outcome is a real mobile client for the portal: every domain object viewable,
and the four transaction documents plus the two masters creatable from the phone, with the
server remaining the single source of truth.

`docs/SPEC.md` is the authoritative product spec (navigation grouping, business rules,
dashboard content, six reports, design tokens). This design implements it on Android; where
the two disagree, `docs/SPEC.md` wins.

---

## Decisions taken

| Decision | Choice | Consequence |
|---|---|---|
| Sync direction | Full two-way | Backend needs write endpoints |
| Offline writes | **Online-only** | Save posts and waits; no connectivity → error, nothing saved |
| Scope | Single spec, staged execution | Backend refactor lands before dependents |

Online-only writes mean there is **no outbox, no conflict resolution, and no client-side
document numbering**. Room is a read cache that makes browsing work offline; creating
requires connectivity. This is what keeps document numbers and stock provably correct.

---

## 1. Backend: extract the document write sequence

### The problem

The write sequence for documents lives inside the EasyAdmin controllers, not in a service.
From `src/Controller/Admin/SalesInvoiceCrudController.php:126`:

```
create:  normalise($doc)
         setNo(documentNumbers->consume(SALES))
         persist + flush
         salesWarnings($lines, $plant)          → soft flash warnings
         applySnapshot(snapshot($lines,$plant), -1)

update:  storedSnapshot = snapshot(...)          ← taken BEFORE the form binds
         normalise($doc); flush
         salesWarnings($lines, $plant, storedSnapshot)
         applySnapshot(storedSnapshot, +1)       ← undo stored effect at stored plant
         applySnapshot(snapshot(new), -1)        ← apply new effect

delete:  snapshot(...) → delete → applySnapshot(snapshot, +1)
```

`PurchaseBillCrudController` mirrors this with the signs flipped (`+1` on create, `-1`/`+1`
on update, `-1` on delete).

If the API re-implements this, the two copies will drift and plant stock will corrupt
silently. Stock bugs are near-invisible until inventory is physically counted.

### The change

Introduce `App\Service\DocumentPersister`, owning that sequence for sales invoices and
purchase bills, and numbering for receipts and payments. Both the EasyAdmin controllers and
the new API endpoints call it. This is a **refactor of existing behaviour** — no behaviour
change on the portal side, which is what makes it safely testable.

```php
final class DocumentPersister
{
    /** @return string[] soft stock warnings */
    public function createSalesInvoice(SalesInvoice $invoice): array;
    public function updateSalesInvoice(SalesInvoice $invoice, array $storedSnapshot): array;
    public function deleteSalesInvoice(SalesInvoice $invoice): void;
    // …purchase bill equivalents, and createReceipt/createPayment for numbering only
}
```

The sign difference between sales and purchase is the only asymmetry and is expressed once,
as a constructor-time or per-method constant — not duplicated per call site.

`DocumentNumberService::consume()` mutates `Settings` (`nextInvoiceNo` etc., see
`src/Service/DocumentNumberService.php:56`), so create must run inside a Doctrine
transaction; two concurrent creates would otherwise collide on the same number.

### Files

- **New:** `src/Service/DocumentPersister.php`
- **Modified:** `SalesInvoiceCrudController`, `PurchaseBillCrudController`,
  `ReceiptCrudController`, `PaymentCrudController` — lifecycle hooks delegate to the service
  and keep only their `addFlash` presentation concern.

---

## 2. Backend: complete the API

Existing controllers in `src/Controller/Api/` already share `ApiPayloadTrait` (paise
conversion, `Y-m-d\TH:i:s` stamps, 404 shape). Extend that pattern.

### New read endpoints

| Endpoint | Notes |
|---|---|
| `GET /api/receipts`, `/{id}` | Same shape as payments |
| `GET /api/staff`, `/{id}` | Includes wage type and rates |
| `GET /api/staff-work` | Filterable `?paid=0` |
| `GET /api/plants` | Needed by every document form |
| `GET /api/settings` | Prefixes, next numbers, default wage, current plant |

### Line items

Lines are **nested in the document payload**, not separate endpoints — a document and its
lines are one transactional unit and must never be fetched or written apart:

```json
{ "id": 15, "document_number": "SI-0015", "party_id": 1, "plant_id": 1,
  "invoice_date": "2026-08-05", "total_amount": 9120000, "discount_amount": 0,
  "net_amount": 9120000,
  "lines": [ { "id": 41, "item_id": 7, "qty": 1200.0, "rate": 7600, "amount": 9120000 } ] }
```

This changes the existing `/api/sales-invoices` and `/api/purchase-bills` responses
additively; the Android DTOs gain a `lines` field.

### Write endpoints

`POST` and `PUT` on parties, sales invoices, purchase bills, receipts, payments and staff
work — the six objects the mobile screens create. Items are read-only on mobile: the item
master carries plant-wise stock and low-stock thresholds that only the portal edits, and no
create screen exists for it. Bodies mirror the read payloads, minus server-owned fields
(`id`, `document_number`, timestamps). Money arrives as **integer paise** and converts to decimal
rupees at the boundary, matching the read direction.

Responses:

- **201 / 200** — the full created/updated resource, including the server-assigned
  `document_number`, plus `"warnings": []` for soft stock shortfalls.
- **422** — `{"errors": {"party_id": "Party not found", "lines": "At least one line required"}}`
- **409** — number allocation collision, retryable.

Stock shortfalls are **warnings, never errors**. `salesWarnings()` is explicitly non-blocking
in the portal (`DocumentStockManager.php:90`) and the mobile client must not be stricter than
the portal.

### Validation

The entities carry `const` allow-lists (`Party::TYPES`, `Payment::MODES`, `Item::TYPES`).
Validation resolves relations, checks values against those lists, and rejects unknown enum
values — reusing the constants rather than restating them.

---

## 3. Android: architecture

MVVM. Every required dependency is already declared in `android/build.gradle.kts`:
`lifecycle-viewmodel-compose:2.8.2` (:99), `hilt-navigation-compose:1.2.0` (:110),
`material-icons-extended` (:92). Nothing new to add.

### State

One `@HiltViewModel` per screen exposing a single `StateFlow<UiState>`:

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<out T>(val data: T, val syncing: Boolean = false) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val message: String, val retry: () -> Unit) : UiState<Nothing>
}
```

`Empty` is distinct from `Content(emptyList())` because `docs/SPEC.md:179` calls for
"friendly empty states (icon + sentence) everywhere".

### Data flow

Room remains the read cache. Screens observe DAO `Flow`s, so browsing works offline. A write
posts to the server; on success the affected entity re-syncs, Room updates, and every
observing screen recomposes automatically. No manual UI refresh.

### Fixing `SyncManager`

`data/sync/SyncManager.kt:24` uses `GlobalScope.launch` and swallows every failure into
`printStackTrace()` — sync errors are currently invisible. It becomes a repository-scoped
operation returning a result and exposing `StateFlow<SyncState>`, so the UI can show
syncing/failed. Without this, an expired tunnel looks identical to an empty database.

### Fixing `App.kt`

`ui/App.kt:37-72` hardcodes `selected = false` on all four bottom-bar tabs and uses
`Icons.Filled.Home` for every one, so nothing ever highlights. Selection derives from the
current back-stack entry, with distinct icons per destination.

### File layout

`Screens.kt` splits into `ui/screens/<feature>/` — one file per feature area (`dashboard/`,
`parties/`, `items/`, `sales/`, `purchases/`, `receipts/`, `payments/`, `staff/`, `reports/`,
`settings/`), each with its screen composables and ViewModel. A single 22-screen file is
unworkable to edit or reason about.

Shared composables in `ui/components/`: `MoneyText` (paise → `₹` `en-IN`, 2 decimals),
`EmptyState`, `ErrorState`, `SyncBanner`, `DocumentCard`, `PartyPicker`, `ItemPicker`,
`DateField`, `ConfirmDialog`.

### Theme

`ui/theme/Color.kt` is stock Material teal (`#006A60`) and ignores the brand. It is replaced
with the `docs/SPEC.md:196` tokens: primary `#1D3557`, primary-dark `#142238`, accent
`#C97A2B`, background `#F5F2EA`, success `#2A9D4A`, danger `#C0392B`. Money and document
numbers render in a monospace face, per the spec's IBM Plex Mono intent.

---

## 4. Android: screens

Navigation follows the `docs/SPEC.md:24` grouping. The bottom bar carries the four primary
destinations; the rest live behind **More**.

**Dashboard** — total sales, total purchases, cash in, cash out; outstanding receivables and
payables; low-stock list; top outstanding parties; recent transactions.

**Lists** (search where the spec calls for it — parties, items, sales, purchases; party type
filter): parties, items, sales invoices, purchase bills, receipts, payments, staff, staff work.

**Details**: party (with its running ledger), item (plant-wise stock), sales invoice and
purchase bill (with line items), receipt, payment, staff (work history, paid vs unpaid).

**Create forms**: sales invoice, purchase bill, receipt, payment and staff work — the five
that already have routes in `ui/navigation/NavigationRoutes.kt` — plus **party**, which needs
a new route (`party_create`), since invoicing a new customer otherwise forces a trip to the
portal. Each form posts online, disables submit while in flight, surfaces `422` errors per
field, and shows returned stock warnings as a non-blocking banner after save.

**Reports** — the six tabs in spec order: Sales Summary, Purchase Summary, Stock Report,
Receivables, Payables, Staff Payments.

**Settings** — company details, prefixes, next numbers, default wage, current plant.

---

## 5. Out of scope

Print views, JSON backup export/import, and the plant switcher in the top bar
(`docs/SPEC.md:189`) are portal features not carried to mobile in this pass. Deletion from
mobile is also excluded — `DocumentPersister` grows delete support for the portal's benefit,
but no mobile screen calls it.

---

## 6. Verification

**Backend.** PHPUnit against `DocumentPersister`, asserting resulting `ItemStock` rows:

- Create a sales invoice for 100 units → stock drops by exactly 100.
- Edit it to 60 units → stock reflects 60, not 40 and not 160. This is the case the
  snapshot mechanism exists for and the one most likely to regress.
- Move an invoice to a different plant → original plant is made whole, new plant is debited.
- Delete → stock returns to its starting value.
- Purchase bills: the same four, with signs inverted.
- Two concurrent creates do not receive the same document number.

Portal regression: create and edit an invoice through EasyAdmin after the refactor and
confirm stock and numbering are unchanged.

**API.** `curl` each new endpoint for 200 and correct shape; POST a sales invoice and confirm
the response carries a server-assigned `document_number` and that `GET /api/items` shows the
reduced stock. POST an invalid body and confirm `422` with a per-field map.

**Android.** Unit tests for repositories against a fake API service, and for ViewModel state
transitions (Loading → Content, Loading → Error, retry). Then run the real thing on the
`littlebot_test` emulator against `https://invoice.littlebotautomation.com`:

```
ANDROID_SDK_ROOT=/home/shivansh/android-sdk \
  /home/shivansh/android-sdk/emulator/emulator -avd littlebot_test &
cd android && ./gradlew assembleDebug
adb -s emulator-5554 install -r build/outputs/apk/debug/WovenLedger-debug.apk
adb -s emulator-5554 logcat | grep -E "okhttp|FATAL"
```

End-to-end acceptance: create a sales invoice on the phone, then confirm it appears in the
Symfony portal with the correct number, totals, and stock effect.
