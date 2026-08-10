<?php

declare(strict_types=1);

namespace App\Service;

use App\Entity\Item;
use App\Repository\ItemStockRepository;
use App\Repository\PurchaseBillLineRepository;
use App\Repository\SalesInvoiceLineRepository;
use App\Repository\StockMovementRepository;

/**
 * Stock movement history for one item.
 *
 * There is no stock-movement ledger in this schema: ItemStock holds a *current*
 * quantity per plant and StockService mutates it in place. So the history is
 * reconstructed, from exactly three sources, chosen so that nothing is counted
 * twice:
 *
 *   in   — PurchaseBillLine (the rows DocumentPersister added stock for)
 *   out  — SalesInvoiceLine (the rows DocumentPersister removed stock for)
 *   ±    — StockMovement, which by construction holds manual adjustments only
 *
 * Document movements are never written to StockMovement (see that entity's note),
 * which is what keeps the three sources disjoint.
 *
 * The honesty problem: every manual adjustment made before StockMovement existed
 * moved stock and left no trace at all. For such items the three sources cannot
 * add up to the quantity on hand, and no amount of querying will recover the
 * difference. Rather than print a running balance that quietly disagrees with the
 * stock figure shown above it, this service computes the gap per plant and emits
 * it as an explicit opening/unaccounted row. The table therefore always closes on
 * the real on-hand quantity, and states plainly how much of it it cannot explain.
 */
class StockHistoryService
{
    /** Quantities are stored to three decimals, so anything smaller is rounding noise. */
    private const EPSILON = 0.0005;

    public function __construct(
        private readonly PurchaseBillLineRepository $purchaseLines,
        private readonly SalesInvoiceLineRepository $salesLines,
        private readonly StockMovementRepository $movements,
        private readonly ItemStockRepository $stocks,
    ) {
    }

    /**
     * The item's movement history with a running balance, oldest first.
     *
     * @return array{
     *     entries: array<int, array{date: \DateTimeImmutable|null, type: string, ref: string, kind: string, documentId: int|null, plant: string, qty: float, balance: float}>,
     *     current: float,
     *     unaccounted: float,
     *     closing: float,
     * }
     */
    public function getHistory(Item $item): array
    {
        $entries = [];

        foreach ($this->purchaseLines->findRowsForItem($item) as $row) {
            $entries[] = $this->documentEntry($row, 'Purchase Bill', 'purchase', 1.0);
        }

        foreach ($this->salesLines->findRowsForItem($item) as $row) {
            $entries[] = $this->documentEntry($row, 'Sales Invoice', 'sales', -1.0);
        }

        foreach ($this->movements->findRowsForItem($item) as $row) {
            $entries[] = [
                'date' => $row['movedAt'],
                'type' => 'Manual Adjustment',
                'ref' => '' === $row['reason'] ? '-' : $row['reason'],
                'kind' => 'manual',
                'documentId' => null,
                'plantId' => $row['plantId'],
                'plant' => $row['plantName'],
                'qty' => $row['qty'],
                'balance' => 0.0,
            ];
        }

        // Documents carry a date but no time, so same-day rows would otherwise sort
        // arbitrarily. Falling back to the kind and the document id keeps the order
        // stable between requests, which matters for a running balance.
        usort($entries, static function (array $a, array $b): int {
            return [$a['date']->format('Y-m-d H:i:s'), $a['kind'], $a['documentId'] ?? 0]
                <=> [$b['date']->format('Y-m-d H:i:s'), $b['kind'], $b['documentId'] ?? 0];
        });

        // Read once and reuse: this backs both the reconciling rows and the
        // closing figure, and findBy() would re-query for each.
        $stockRows = $this->stocks->findForItem($item);

        $opening = $this->openingEntries($stockRows, $entries);
        $entries = [...$opening, ...$entries];

        $running = 0.0;
        foreach ($entries as $i => $entry) {
            $running += $entry['qty'];
            $entries[$i]['balance'] = $running;
        }

        // Cast: array_sum() of an empty list is int 0, and these are quantities.
        $unaccounted = (float) array_sum(array_column($opening, 'qty'));

        return [
            'entries' => array_map(static function (array $entry): array {
                unset($entry['plantId']);

                return $entry;
            }, $entries),
            'current' => (float) array_sum(array_map(static fn ($stock): float => $stock->getQty(), $stockRows)),
            'unaccounted' => $unaccounted,
            // By construction this equals 'current'; it is returned so a caller (or
            // a test) can assert the reconciliation rather than trust it.
            'closing' => $running,
        ];
    }

    /**
     * @param array{qty: float, docId: int, docNo: string, docDate: \DateTimeImmutable, plantId: int|null, plantName: string|null} $row
     *
     * @return array{date: \DateTimeImmutable, type: string, ref: string, kind: string, documentId: int, plantId: int|null, plant: string, qty: float, balance: float}
     */
    private function documentEntry(array $row, string $type, string $kind, float $sign): array
    {
        return [
            'date' => $row['docDate'],
            'type' => $type,
            'ref' => $row['docNo'],
            'kind' => $kind,
            'documentId' => $row['docId'],
            'plantId' => $row['plantId'],
            'plant' => $row['plantName'] ?? '-',
            'qty' => $sign * $row['qty'],
            'balance' => 0.0,
        ];
    }

    /**
     * The balancing figure, per plant: what is on hand now minus everything the
     * three sources can explain.
     *
     * It is emitted per plant rather than as one lump so the table reconciles at
     * the level people actually count stock at, and so the plant column is never
     * blank. A plant is included only when its gap is real.
     *
     * @param array<int, \App\Entity\ItemStock>                $stockRows
     * @param array<int, array{plantId: int|null, qty: float}> $entries
     *
     * @return array<int, array{date: \DateTimeImmutable|null, type: string, ref: string, kind: string, documentId: null, plantId: int|null, plant: string, qty: float, balance: float}>
     */
    private function openingEntries(array $stockRows, array $entries): array
    {
        $explained = [];
        foreach ($entries as $entry) {
            $key = $entry['plantId'] ?? 0;
            $explained[$key] = ($explained[$key] ?? 0.0) + $entry['qty'];
        }

        $opening = [];

        foreach ($stockRows as $stock) {
            $plant = $stock->getPlant();
            if (null === $plant) {
                continue;
            }

            $plantId = (int) $plant->getId();
            $gap = $stock->getQty() - ($explained[$plantId] ?? 0.0);
            unset($explained[$plantId]);

            if (abs($gap) > self::EPSILON) {
                $opening[] = $this->openingEntry($plantId, $plant->getName(), $gap);
            }
        }

        // Plants that documents moved stock at but which carry no ItemStock row
        // today: on hand is zero there, so the whole explained movement is a gap.
        foreach ($explained as $plantId => $moved) {
            if (abs($moved) > self::EPSILON) {
                $opening[] = $this->openingEntry(
                    0 === $plantId ? null : $plantId,
                    $this->plantNameFor($entries, $plantId),
                    -$moved,
                );
            }
        }

        return $opening;
    }

    /**
     * @return array{date: null, type: string, ref: string, kind: string, documentId: null, plantId: int|null, plant: string, qty: float, balance: float}
     */
    private function openingEntry(?int $plantId, string $plantName, float $qty): array
    {
        return [
            // Deliberately undated: nothing records when the untracked adjustments
            // were made, and inventing a date would be the dishonest part.
            'date' => null,
            'type' => 'Opening / unaccounted',
            'ref' => 'Not recorded',
            'kind' => 'opening',
            'documentId' => null,
            'plantId' => $plantId,
            'plant' => $plantName,
            'qty' => $qty,
            'balance' => 0.0,
        ];
    }

    /** @param array<int, array{plantId: int|null, plant: string}> $entries */
    private function plantNameFor(array $entries, int $plantId): string
    {
        foreach ($entries as $entry) {
            if (($entry['plantId'] ?? 0) === $plantId) {
                return $entry['plant'];
            }
        }

        return '-';
    }
}
