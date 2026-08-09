# Woven Ledger — Portal Replica Spec

Reverse-engineered from `https://sales.littlebotautomation.com`, a single-file HTML/JS
app (1,825 lines) that keeps all state in browser `localStorage` under the key
`wovenledger_db_v1`.

This spec drives two replicas:
- **Web**: Symfony 7.3 + EasyAdmin 4 + Doctrine + SQLite (this repository).
- **Android**: Jetpack Compose + Room, offline-first (see `/home/shivansh/woven-ledger-android`).

---

## 1. Product summary

"Woven Ledger" is an ERP for a **PP woven bag manufacturing** business. It tracks
parties (customers/suppliers), items with **plant-wise stock**, staff and their daily
work/wages, and four transaction documents (sales invoices, purchase bills, receipt
vouchers, payment vouchers). It derives a **party ledger** and six reports.

Currency is INR (`₹`), formatted `en-IN` with 2 decimals. Dates display as `dd MMM yyyy`.

---

## 2. Navigation — preserve grouping, order, labels, icons

| Group | Route id | Label | Icon |
|---|---|---|---|
| Overview | `dashboard` | Dashboard | 📊 |
| Masters | `parties` | Customers & Suppliers | 👥 |
| Masters | `items` | Item & Inventory | 📦 |
| Masters | `staff` | Staff Master | 🪪 |
| Transactions | `sales` | Sales Invoices | 🧾 |
| Transactions | `purchases` | Purchase Bills | 🛒 |
| Transactions | `receipts` | Receipt Vouchers | 💵 |
| Transactions | `paymentsv` | Payment Vouchers | 💸 |
| Transactions | `staffwork` | Daily Staff Work | 👷 |
| Insights | `ledger` | Party Ledger | 📒 |
| Insights | `reports` | Business Reports | 📈 |
| System | `settings` | Business Settings | ⚙️ |

Page subtitles:

- dashboard: "Overview of your business today"
- parties: "Manage your party master records"
- items: "Fabric rolls, finished bags and plant-wise stock"
- staff: "Workers across your plants"
- sales: "Bill your buyers and auto-adjust stock"
- purchases: "Record fabric roll purchases from suppliers"
- receipts: "Money received from parties"
- paymentsv: "Money paid to parties or staff"
- staffwork: "Log work and settle staff wages"
- ledger: "Running account for any party"
- reports: "Sales, purchase, stock and outstanding summaries"
- settings: "Company details, plants, numbering & data"

---

## 3. Domain model

Money = 2 decimal places, quantity = 3 decimal places.

### 3.1 Plant
`name` (required), `address`. Seed row: "Main Plant".

### 3.2 Party
`name` **required**, `type` (`customer` | `supplier` | `both`, default `customer`),
`phone`, `gstin`, `address`, `openingBalance` (default 0),
`openingBalanceType` (`to_receive` = they owe us | `to_pay` = we owe them, default `to_receive`),
`createdAt` (defaults to today).

Delete guard: refuse if the party appears on any sales invoice, purchase bill, receipt
or payment — "Cannot delete: party has transactions."

### 3.3 Item
`name` **required** (e.g. "PP Woven Fabric Roll 120GSM"),
`type` (`raw_material` = fabric roll | `finished_good` = bag, default `raw_material`),
`unit` (default `pcs`; the form defaults to `kg`, blank falls back to `pcs`),
`defaultRate`, `hsn`, `lowStockThreshold` **nullable** — null means "use the global default".
Stock is per plant.

### 3.4 ItemStock
`item`, `plant`, `qty`. Unique on (item, plant).

### 3.5 Staff
`name` **required**, `role`, `plant`, `phone`,
`wageType` (`daily` | `piece`, default `daily`), `dailyRate`, `pieceRate`.

Delete guard: refuse if the staff member has any work entries.

### 3.6 SalesInvoice
`no` unique (`SI-0001`), `date`, `party`, `plant`, lines,
`subtotal`, `discount`, `total` where `total = max(0, subtotal - discount)`, `notes`.

### 3.7 SalesInvoiceLine
`item`, `qty`, `rate`, `amount = qty × rate`.

### 3.8 PurchaseBill / PurchaseBillLine
Identical shape; prefix `PB-`; party is the **supplier**.

### 3.9 Receipt
`no` unique (`RC-0001`), `date`, `party`, `amount` > 0,
`mode` (`Cash` | `Bank Transfer` | `UPI` | `Cheque`), `notes`.

### 3.10 Payment
`no` unique (`PY-0001`), `date`, `type` (`party` | `staff`),
`party` (nullable) **or** `staff` (nullable), `amount` > 0, `mode`, `notes`,
plus the set of staff work entries it settled.

### 3.11 StaffWork
`date`, `staff`, `plant`, `workType` (free text: "Stitching", "Loading"),
`qty`, `rate`, `amount = qty × rate`, `paid` (default false), `paymentVoucher` (nullable).

### 3.12 Settings (single row)
`companyName` ("Your PP Woven Bags Co."), `address`, `phone`, `gstin`,
`invoicePrefix` `SI-`, `purchasePrefix` `PB-`, `receiptPrefix` `RC-`, `paymentPrefix` `PY-`,
`nextInvoiceNo` / `nextPurchaseNo` / `nextReceiptNo` / `nextPaymentNo` (default 1),
`lowStockDefault` (50), `defaultWage` (500), `currentPlant`.

---

## 4. Business rules — reproduce exactly

### 4.1 Document numbering
```
sales    = invoicePrefix  + pad(nextInvoiceNo, 4)     ->  SI-0001
purchase = purchasePrefix + pad(nextPurchaseNo, 4)    ->  PB-0001
receipt  = receiptPrefix  + pad(nextReceiptNo, 4)     ->  RC-0001
payment  = paymentPrefix  + pad(nextPaymentNo, 4)     ->  PY-0001
```
The counter increments **only after a new document saves**, never on edit.

### 4.2 Stock movement
- **Sales created**: each line `stock[item][plant] -= qty`.
- **Sales edited**: **reverse** the old lines (`+= old qty` at the *old* plant) first,
  then apply the new lines at the new plant.
- **Sales deleted**: restore (`+= qty`).
- **Purchase** is the mirror: create `+=`, edit reverses with `-=` at the old plant then
  `+=`, delete `-=`.
- Stock may go negative. On sales, if `qty > available`, show a **soft warning**
  ("Stock warning: only X UNIT of ITEM available at PLANT") and let the user proceed.
  Never a hard block.
- Manual **stock adjustment** per item: choose a plant, then either set an absolute
  quantity or apply a signed delta (opening stock, physical count, wastage).

### 4.3 Low stock
`threshold = item.lowStockThreshold ?? settings.lowStockDefault`.
Low when qty at a plant is `<= threshold`. Low rows render in the danger colour;
the nav shows a count badge on "Item & Inventory".

### 4.4 Party ledger
Signed running balance, **positive = they owe us**, **negative = we owe them**:

| Entry | Sign |
|---|---|
| Opening balance, `to_receive` | `+abs(openingBalance)` |
| Opening balance, `to_pay` | `-abs(openingBalance)` |
| Sales invoice | `+total` |
| Purchase bill | `-total` |
| Receipt | `-amount` |
| Payment to party | `+amount` |

Sort ascending by date, accumulate the running balance in that order. The closing
balance is the last entry's balance (0 if none). Staff payments never appear here.

### 4.5 Staff wages
- Logging work: picking a staff member **auto-fills** the rate from `dailyRate` or
  `pieceRate` per their `wageType`; `amount = qty × rate` previews live.
- **Settle unpaid wages**: pick a staff member with unpaid entries → sum their unpaid
  amounts → create ONE payment with `type=staff`, `amount=total`, notes
  `"Wage settlement — N entries"` → mark every one of those entries paid and link them
  to that voucher.

---

## 5. Dashboard
Total sales, total purchases, cash in (receipts), cash out (payments); outstanding
receivables (sum of positive balances) and payables (sum of |negative|); low-stock list;
top outstanding parties by absolute balance; recent transactions.
Friendly empty states (icon + sentence) everywhere.

## 6. Reports — six tabs in this order
1. **Sales Summary** — grand total + Invoice / Date / Party / Plant / Amount, newest first
2. **Purchase Summary** — same, Supplier column
3. **Stock Report** — Item / Type / one column per plant / Total, with units
4. **Receivables** — balances `> 0.01`, sorted desc, with grand total
5. **Payables** — balances `< -0.01` shown absolute, sorted desc, with grand total
6. **Staff Payments** — Total Paid / Total Unpaid tiles + per-staff paid vs unpaid

## 7. Other features
Party ledger print view; sales invoice print view; plant switcher in the top bar;
JSON backup export/import; search on parties, items, sales, purchases; party type
filter; toasts on save/delete; confirmation before destructive actions.

---

## 8. Design tokens

```
--bg:#F5F2EA        --surface:#FFFFFF     --ink:#20242B      --ink-soft:#565F6B
--primary:#1D3557   --primary-dark:#142238 --primary-tint:#E7ECF3
--accent:#C97A2B    --accent-tint:#F7E7D4
--success:#2A9D4A   --success-tint:#E3F5E8
--danger:#C0392B    --danger-tint:#FBE8E5
--border:#E3DDCE    --border-soft:#EDE8DC
--radius:10px       --sidebar-w:250px
```

Fonts: **Sora** (headings), **Inter** (body), **IBM Plex Mono** (money and document
numbers). Sidebar is `--primary-dark` with light text; the active nav item is `--accent`.
The brand signature is a "weave" texture — 45° and -45° repeating linear gradients — used
on the logo mark and sidebar head.
