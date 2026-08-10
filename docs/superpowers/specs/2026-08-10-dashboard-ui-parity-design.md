# Dashboard UI Parity — Design

**Date:** 2026-08-10
**Status:** Approved

## Problem

`templates/admin/dashboard.html.twig` is the only admin page that renders bare
`<table>`, `<th>`, `<td>` and `<h2>` tags with no classes. Every sibling page —
`reports.html.twig`, `ledger.html.twig` — uses the Bootstrap classes that ship
with EasyAdmin (`table datagrid`, `card`, `text-end`, `no-results`, `h5`). The
result is that the dashboard renders as unstyled browser-default tables inside
otherwise-styled admin chrome.

The data is correct. `ReportService::dashboardData()` returns every key the
template reads. This is a presentation defect only.

## Constraints

Per project convention, the admin runs on stock EasyAdmin. This change introduces
no theme, no layout override, no project stylesheet and no JavaScript. Every class
used below already ships with EasyAdmin's bundled Bootstrap and already appears in
`reports.html.twig`, so the two pages read as one system.

## Design

Single file changes: `templates/admin/dashboard.html.twig`. No controller,
service, entity or asset changes.

### 1. Summary figures become a KPI card grid

The seven-row label/value table is replaced by a card grid:

```twig
<div class="row g-3 mb-4">
  <div class="col-sm-6 col-xl-3">
    <div class="card"><div class="card-body">
      <div class="text-muted small">Total Sales</div>
      <div class="fs-4 fw-bold">{{ data.totalSales|inr }}</div>
    </div></div>
  </div>
  ...
</div>
```

This is the pattern the Staff tab of `reports.html.twig` already uses for its
two totals, extended to seven cards.

Semantic color follows the same precedent — applied only to figures that carry a
direction, so the color means something:

| Figure                  | Class          |
|-------------------------|----------------|
| Total Sales             | (neutral)      |
| Total Purchases         | (neutral)      |
| Cash In (Receipts)      | (neutral)      |
| Cash Out (Payments)     | (neutral)      |
| Outstanding Receivable  | `text-success` |
| Outstanding Payable     | `text-danger`  |
| Unpaid Wages            | `text-danger`  |

Sales, purchases and cash movement are volume, not health, so they stay neutral.

### 2. List sections adopt the datagrid treatment

Below the summary cards the page is three rows of paired boxes, each pair
naturally two of a kind:

| Row | Left | Right |
|-----|------|-------|
| 1 | Low Stock | Top Outstanding Parties |
| 2 | Recent Sales Invoices | Recent Purchase Bills |
| 3 | Recent Receipts | Recent Payments |

Every pair uses the identical shell — a `row g-3` of two `col-lg-6` cards, each
`card h-100` titled by its `card-header`, holding a
`card-body p-0 > table-responsive > table table datagrid mb-0`. No section keeps
an `h5` heading; the `card-header` carries every title.

Within the tables:

- `class="text-end"` on every amount and quantity column, in both `<th>` and
  `<td>`.
- `datagrid-empty` on the table and a `no-results` row when the section is
  empty.

The uniform shell is the point. Earlier drafts mixed full-width bare tables,
half-width bare tables and boxes on one page, which made the differences look
like meaning rather than accident.

### 3. Empty states move inside the table

The hand-written `{% if data.x is empty %}<p>…</p>{% else %}` wrappers are
replaced by the `{% for %}…{% else %}` idiom used everywhere else in the admin:

```twig
{% else %}
  <tr class="no-results"><td colspan="100">Every item is above its threshold.</td></tr>
{% endfor %}
```

Existing empty-state wording is preserved verbatim — "Every item is above its
threshold.", "No outstanding balances.", "No invoices yet.", "No bills yet." It
only moves from a stray unstyled paragraph into a styled table row.

### 4. Low-stock emphasis

In the Low Stock table, the on-hand quantity gets `text-danger fw-bold` when it
is at or below the row's threshold, matching how the stock report in
`reports.html.twig` flags the same condition.

### 5. Recent Receipts and Recent Payments

`dashboardData()` has always computed `recentReceipts` and `recentPayments`,
which the template never rendered — the queries ran on every dashboard load and
the results were discarded. Both are now rendered as two further sections in the
same datagrid treatment, closing that gap.

Unlike the four sections above them, these two are each boxed in a `card` whose
title lives in a `card-header`, with the data as an ordinary table inside. The
two boxes sit side by side, each taking half the width:

```twig
<div class="row g-3">
  <div class="col-lg-6">
    <div class="card h-100">
      <div class="card-header">Recent Receipts</div>
      <div class="card-body p-0">
        <div class="table-responsive">
          <table class="table datagrid mb-0">…</table>
        </div>
      </div>
    </div>
  </div>
  <div class="col-lg-6">…Recent Payments…</div>
</div>
```

The split is `col-lg-6`, not a plain `col-6`: five columns of voucher data at
half a phone's width is unreadable, so the boxes stack full-width below the `lg`
breakpoint and sit half-and-half above it.

`table-responsive` wraps each table because a five-column table in a half-width
box overflows at middling viewport widths. It confines the horizontal scroll to
the box instead of letting it push the whole page sideways.

`h-100` keeps the two boxes the same height when one holds fewer rows than the
other; without it the shorter box stops early and the row looks broken.

Columns are `No | Date | From/Paid To | Mode | Amount`, amount right-aligned.

Two classes carry weight here. `p-0` on the body removes the card's padding so
the table meets the box edges instead of floating inside it, and `mb-0` on the
table removes Bootstrap's default bottom margin, which would otherwise leave a
gap between the last row and the card border. Because these sections keep their
table, they also keep the `no-results` empty-state row rather than needing a
separate empty component.

Since the `card-header` carries the title, these two sections have no `h5`
heading above them, unlike sections 2–4.

`Mode` is surfaced because cash-versus-bank is the distinguishing field of a
voucher, and both entities carry it.

Every class used here is verified present in EasyAdmin's bundled
`public/bundles/easyadmin/app.*.css`. No stylesheet is added.

Payments may be made to either a `Party` or a `Staff` member. The template uses
the existing `Payment::getPayeeName()` accessor, which already collapses that
branch into a single "Paid To" value, rather than reproducing the conditional in
Twig. It returns an empty string when neither is set, so the template falls back
to an em dash via `?:`.

Amounts stay neutral, consistent with the Recent Sales and Recent Purchase
tables and with the reasoning behind the neutral Cash In / Cash Out cards: these
are volume, not health.

## Verification

- `bin/console lint:twig templates/admin/dashboard.html.twig` passes.
- The rendered `/admin` page shows the summary card grid, four styled tables and
  two boxed voucher tables, with no bare browser-default table remaining.
- Empty states render as `no-results` rows within their tables.
- No new file is added under `public/css` or `public/js`, and no
  `templates/bundles/EasyAdminBundle` override is created.
