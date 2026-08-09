<?php

declare(strict_types=1);

namespace App\Service;

use App\Repository\ItemRepository;
use App\Repository\PaymentRepository;
use App\Repository\PlantRepository;
use App\Repository\PurchaseBillRepository;
use App\Repository\ReceiptRepository;
use App\Repository\SalesInvoiceRepository;
use App\Repository\StaffWorkRepository;

/**
 * Backs the dashboard (spec §5) and the six report tabs (spec §6).
 */
class ReportService
{
    public const TABS = [
        'sales' => 'Sales Summary',
        'purchase' => 'Purchase Summary',
        'stock' => 'Stock Report',
        'receivable' => 'Receivables',
        'payable' => 'Payables',
        'staff' => 'Staff Payments',
    ];

    public function __construct(
        private readonly LedgerService $ledgerService,
        private readonly StockService $stockService,
        private readonly SalesInvoiceRepository $salesInvoiceRepository,
        private readonly PurchaseBillRepository $purchaseBillRepository,
        private readonly ReceiptRepository $receiptRepository,
        private readonly PaymentRepository $paymentRepository,
        private readonly ItemRepository $itemRepository,
        private readonly PlantRepository $plantRepository,
        private readonly StaffWorkRepository $staffWorkRepository,
    ) {
    }

    /**
     * Dispatches one report tab. An unknown tab falls back to sales, matching the
     * original, whose chip row defaults to the first tab.
     */
    public function reportData(string $tab): array
    {
        return match ($tab) {
            'purchase' => $this->purchaseSummary(),
            'stock' => $this->stockReport(),
            'receivable' => $this->receivables(),
            'payable' => $this->payables(),
            'staff' => $this->staffPayments(),
            default => $this->salesSummary(),
        };
    }

    public function salesSummary(): array
    {
        $rows = $this->salesInvoiceRepository->findAllNewestFirst();

        return [
            'tab' => 'sales',
            'title' => self::TABS['sales'],
            'rows' => $rows,
            'total' => $this->salesInvoiceRepository->sumTotal(),
        ];
    }

    public function purchaseSummary(): array
    {
        $rows = $this->purchaseBillRepository->findAllNewestFirst();

        return [
            'tab' => 'purchase',
            'title' => self::TABS['purchase'],
            'rows' => $rows,
            'total' => $this->purchaseBillRepository->sumTotal(),
        ];
    }

    /**
     * Item rows with one quantity per plant plus a total, so the template can
     * render a column per plant without touching the service layer.
     */
    public function stockReport(): array
    {
        $plants = $this->plantRepository->findAllOrdered();
        $rows = [];

        foreach ($this->itemRepository->findAllWithStock() as $item) {
            $quantities = [];
            $total = 0.0;

            foreach ($plants as $plant) {
                $qty = $item->getStockFor($plant);
                $quantities[(int) $plant->getId()] = $qty;
                $total += $qty;
            }

            $rows[] = [
                'item' => $item,
                'quantities' => $quantities,
                'total' => $total,
                'threshold' => $this->stockService->thresholdFor($item),
            ];
        }

        return [
            'tab' => 'stock',
            'title' => self::TABS['stock'],
            'plants' => $plants,
            'rows' => $rows,
        ];
    }

    public function receivables(): array
    {
        $rows = $this->ledgerService->getReceivables();

        return [
            'tab' => 'receivable',
            'title' => 'Outstanding Receivables',
            'rows' => $rows,
            'total' => array_sum(array_column($rows, 'balance')),
        ];
    }

    public function payables(): array
    {
        $rows = $this->ledgerService->getPayables();

        return [
            'tab' => 'payable',
            'title' => 'Outstanding Payables',
            'rows' => $rows,
            'total' => array_sum(array_column($rows, 'balance')),
        ];
    }

    public function staffPayments(): array
    {
        return [
            'tab' => 'staff',
            'title' => self::TABS['staff'],
            'rows' => $this->staffWorkRepository->sumByStaff(),
            'totalPaid' => $this->staffWorkRepository->sumPaid(),
            'totalUnpaid' => $this->staffWorkRepository->sumUnpaid(),
        ];
    }

    /**
     * Everything the dashboard needs, in one pass.
     */
    public function dashboardData(): array
    {
        $receivables = $this->ledgerService->getReceivables();
        $payables = $this->ledgerService->getPayables();

        return [
            'totalSales' => $this->salesInvoiceRepository->sumTotal(),
            'totalPurchases' => $this->purchaseBillRepository->sumTotal(),
            'cashIn' => $this->receiptRepository->sumAmount(),
            'cashOut' => $this->paymentRepository->sumAmount(),
            'receivableTotal' => array_sum(array_column($receivables, 'balance')),
            'payableTotal' => array_sum(array_column($payables, 'balance')),
            'lowStockRows' => $this->stockService->getLowStockRows(),
            'topOutstanding' => $this->ledgerService->getTopOutstanding(5),
            'recentSales' => $this->salesInvoiceRepository->recent(5),
            'recentPurchases' => $this->purchaseBillRepository->recent(5),
            'recentReceipts' => $this->receiptRepository->recent(5),
            'recentPayments' => $this->paymentRepository->recent(5),
            'unpaidWages' => $this->staffWorkRepository->sumUnpaid(),
        ];
    }
}
