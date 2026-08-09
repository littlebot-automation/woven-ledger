/*
 * Woven Ledger — form behaviour ported from the original portal.
 *
 *  1. Picking a staff member auto-fills the wage rate and previews the amount.
 *  2. Picking an item on an invoice line fills its default rate, and line
 *     amounts plus the grand total recompute live.
 *
 * Everything degrades gracefully: the server recomputes all of it on save, so
 * this is convenience only.
 */
(function () {
  'use strict';

  function num(el) {
    if (!el) return 0;
    var v = parseFloat(el.value);
    return isNaN(v) ? 0 : v;
  }

  function inr(value) {
    var negative = value < 0;
    value = Math.abs(value);
    var whole = Math.floor(value).toString();
    var decimals = value.toFixed(2).split('.')[1];

    if (whole.length > 3) {
      var last3 = whole.slice(-3);
      var rest = whole.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ',');
      whole = rest + ',' + last3;
    }
    return (negative ? '-' : '') + '₹' + whole + '.' + decimals;
  }

  /* ---------------------------------------------- staff work rate auto-fill */
  function initStaffWork() {
    var staffSelect = document.querySelector('.wl-work-staff');
    if (!staffSelect) return;

    var rates = {};
    try {
      rates = JSON.parse(staffSelect.getAttribute('data-rates') || '{}');
    } catch (e) {
      return;
    }

    var rateInput = document.querySelector('.wl-work-rate');
    var qtyInput = document.querySelector('.wl-work-qty');
    if (!rateInput) return;

    function fillRate() {
      var rate = rates[staffSelect.value];
      // Only overwrite an untouched rate, so a manual override survives.
      if (rate !== undefined && !rateInput.dataset.touched) {
        rateInput.value = rate;
      }
      preview();
    }

    function preview() {
      var target = document.getElementById('wl-amount-preview');
      if (target) target.textContent = inr(num(qtyInput) * num(rateInput));
    }

    rateInput.addEventListener('input', function () {
      rateInput.dataset.touched = '1';
      preview();
    });
    if (qtyInput) qtyInput.addEventListener('input', preview);
    staffSelect.addEventListener('change', fillRate);

    preview();
  }

  /* ------------------------------------------------- invoice line behaviour */
  function lineRows() {
    var seen = [];
    document.querySelectorAll('.wl-line-items .wl-li-item').forEach(function (itemSelect) {
      var row = itemSelect.closest('.field-collection-item') || itemSelect.parentElement.parentElement;
      if (row && seen.indexOf(row) === -1) seen.push(row);
    });
    return seen;
  }

  function recalcDocument() {
    var subtotal = 0;

    lineRows().forEach(function (row) {
      var qty = num(row.querySelector('.wl-li-qty'));
      var rate = num(row.querySelector('.wl-li-rate'));
      var amount = qty * rate;
      subtotal += amount;

      var cell = row.querySelector('.wl-li-amount');
      if (!cell) {
        cell = document.createElement('div');
        cell.className = 'wl-li-amount mono';
        row.appendChild(cell);
      }
      cell.textContent = inr(amount);
    });

    var discountInput = document.querySelector('[id$="_discount"]');
    var totalInput = document.querySelector('[id$="_total"]');
    var subtotalInput = document.querySelector('[id$="_subtotal"]');

    var total = Math.max(0, subtotal - num(discountInput));
    if (subtotalInput) subtotalInput.value = subtotal.toFixed(2);
    if (totalInput) totalInput.value = total.toFixed(2);
  }

  function initLineItems() {
    var wrap = document.querySelector('.wl-line-items');
    if (!wrap) return;

    wrap.addEventListener('change', function (e) {
      if (e.target.classList.contains('wl-li-item')) {
        var row = e.target.closest('.field-collection-item') || e.target.parentElement.parentElement;
        var rateInput = row && row.querySelector('.wl-li-rate');
        var option = e.target.options[e.target.selectedIndex];
        var rate = option && option.getAttribute('data-rate');

        // Only fill a blank rate — never clobber what the user typed.
        if (rateInput && rate && !num(rateInput)) rateInput.value = rate;
      }
      recalcDocument();
    });

    wrap.addEventListener('input', recalcDocument);

    var discountInput = document.querySelector('[id$="_discount"]');
    if (discountInput) discountInput.addEventListener('input', recalcDocument);

    // EasyAdmin injects new collection rows dynamically.
    new MutationObserver(recalcDocument).observe(wrap, { childList: true, subtree: true });

    recalcDocument();
  }

  /* ------------------------------------------------ datagrid action menus */
  /*
   * The datagrid scrolls horizontally (see .content-body in woven.css), which
   * makes it a clipping context. Bootstrap positions these menus with Popper's
   * default "absolute" strategy, so they would be cut off by that scroll box.
   * "fixed" positions them against the viewport instead, escaping the clip.
   *
   * Bootstrap reads this attribute when it constructs the Dropdown, which it
   * does lazily on first click — so setting it at DOMContentLoaded is in time.
   */
  function initDatagridMenus() {
    document
      .querySelectorAll('.datagrid [data-bs-toggle="dropdown"]')
      .forEach(function (toggle) {
        toggle.setAttribute('data-bs-strategy', 'fixed');
      });
  }

  document.addEventListener('DOMContentLoaded', function () {
    initStaffWork();
    initLineItems();
    initDatagridMenus();
  });
})();
