<?php

declare(strict_types=1);

namespace App\Service;

use App\Entity\Party;
use App\Repository\PartyRepository;
use App\Repository\PaymentRepository;
use App\Repository\PurchaseBillRepository;
use App\Repository\ReceiptRepository;
use App\Repository\SalesInvoiceRepository;

/**
 * Party ledger — spec §4.4.
 *
 * Sign convention, and the single most important rule in the app:
 *
 *   POSITIVE balance => they owe us (receivable)
 *   NEGATIVE balance => we owe them (payable)
 *
 * Sales invoices and payments we make to a party increase what they owe us;
 * purchase bills and receipts reduce it. Wage payments (type = staff) are not
 * party transactions at all and never appear here.
 */
class LedgerService
{
    public function __construct(
        private readonly PartyRepository $partyRepository,
        private readonly SalesInvoiceRepository $salesInvoiceRepository,
        private readonly PurchaseBillRepository $purchaseBillRepository,
        private readonly ReceiptRepository $receiptRepository,
        private readonly PaymentRepository $paymentRepository,
    ) {
    }

    /**
     * The party's running account, oldest entry first.
     *
     * @return array<int, array{date: string, type: string, ref: string, amount: float, balance: float, kind: string, id: int|null}>
     */
    public function getEntries(Party $party): array
    {
        $entries = [];

        $opening = $party->getSignedOpeningBalance();
        if (0.0 !== $opening) {
            $entries[] = [
                'date' => $party->getCreatedAt()->format('Y-m-d'),
                'type' => 'Opening Balance',
                'ref' => '-',
                'amount' => $opening,
                'balance' => 0.0,
                'kind' => 'opening',
                'id' => null,
            ];
        }

        foreach ($this->salesInvoiceRepository->findForParty($party) as $invoice) {
            $entries[] = [
                'date' => $invoice->getDate()->format('Y-m-d'),
                'type' => 'Sales Invoice',
                'ref' => $invoice->getNo(),
                'amount' => $invoice->getTotal(),
                'balance' => 0.0,
                'kind' => 'sales',
                'id' => $invoice->getId(),
            ];
        }

        foreach ($this->purchaseBillRepository->findForParty($party) as $bill) {
            $entries[] = [
                'date' => $bill->getDate()->format('Y-m-d'),
                'type' => 'Purchase Bill',
                'ref' => $bill->getNo(),
                'amount' => -$bill->getTotal(),
                'balance' => 0.0,
                'kind' => 'purchase',
                'id' => $bill->getId(),
            ];
        }

        foreach ($this->receiptRepository->findForParty($party) as $receipt) {
            $entries[] = [
                'date' => $receipt->getDate()->format('Y-m-d'),
                'type' => 'Receipt',
                'ref' => $receipt->getNo(),
                'amount' => -$receipt->getAmount(),
                'balance' => 0.0,
                'kind' => 'receipt',
                'id' => $receipt->getId(),
            ];
        }

        foreach ($this->paymentRepository->findPartyPayments($party) as $payment) {
            $entries[] = [
                'date' => $payment->getDate()->format('Y-m-d'),
                'type' => 'Payment',
                'ref' => $payment->getNo(),
                'amount' => $payment->getAmount(),
                'balance' => 0.0,
                'kind' => 'payment',
                'id' => $payment->getId(),
            ];
        }

        usort($entries, static fn (array $a, array $b): int => $a['date'] <=> $b['date']);

        $running = 0.0;
        foreach ($entries as $i => $entry) {
            $running += $entry['amount'];
            $entries[$i]['balance'] = $running;
        }

        return $entries;
    }

    /** Closing balance: positive means they owe us. */
    public function getBalance(Party $party): float
    {
        $entries = $this->getEntries($party);

        return $entries ? end($entries)['balance'] : 0.0;
    }

    /** Spec §4.4 names this accessor getPartyBalance; kept as an alias. */
    public function getPartyBalance(Party $party): float
    {
        return $this->getBalance($party);
    }

    /**
     * @return array<int, array{party: Party, balance: float}>
     */
    public function getAllBalances(): array
    {
        $out = [];
        foreach ($this->partyRepository->findBy([], ['name' => 'ASC']) as $party) {
            $out[] = ['party' => $party, 'balance' => $this->getBalance($party)];
        }

        return $out;
    }

    /**
     * Parties who owe us, largest first.
     *
     * @return array<int, array{party: Party, balance: float}>
     */
    public function getReceivables(): array
    {
        $rows = array_values(array_filter(
            $this->getAllBalances(),
            static fn (array $r): bool => $r['balance'] > 0.01,
        ));

        usort($rows, static fn (array $a, array $b): int => $b['balance'] <=> $a['balance']);

        return $rows;
    }

    /**
     * Parties we owe, largest first, returned as positive amounts.
     *
     * @return array<int, array{party: Party, balance: float}>
     */
    public function getPayables(): array
    {
        $rows = [];
        foreach ($this->getAllBalances() as $row) {
            if ($row['balance'] < -0.01) {
                $rows[] = ['party' => $row['party'], 'balance' => abs($row['balance'])];
            }
        }

        usort($rows, static fn (array $a, array $b): int => $b['balance'] <=> $a['balance']);

        return $rows;
    }

    /**
     * Biggest outstanding accounts either way, for the dashboard.
     *
     * @return array<int, array{party: Party, balance: float}>
     */
    public function getTopOutstanding(int $limit = 5): array
    {
        $rows = array_values(array_filter(
            $this->getAllBalances(),
            static fn (array $r): bool => abs($r['balance']) > 0.01,
        ));

        usort($rows, static fn (array $a, array $b): int => abs($b['balance']) <=> abs($a['balance']));

        return \array_slice($rows, 0, $limit);
    }
}
