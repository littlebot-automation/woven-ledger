<?php

declare(strict_types=1);

namespace App\Service;

use App\Entity\Item;
use App\Entity\Plant;
use App\Repository\ItemRepository;
use App\Repository\PlantRepository;

/**
 * Stock effects of sales invoices and purchase bills — spec §4.2.
 *
 * Editing a document is the subtle case. The stock already on hand reflects the
 * document *as it was stored*, so we must undo that exact effect — at the plant it
 * was stored against, using the quantities it was stored with — before applying the
 * new lines. By the time the form has been submitted the entity in memory holds only
 * the new values, so the caller takes a {@see snapshot()} before binding the form and
 * hands it back here.
 */
class DocumentStockManager
{
    public function __construct(
        private readonly StockService $stockService,
        private readonly ItemRepository $itemRepository,
        private readonly PlantRepository $plantRepository,
    ) {
    }

    /**
     * Freeze a document's current stock footprint as plain scalars, so later
     * mutation of the entity graph cannot corrupt it.
     *
     * @param iterable<object> $lines
     *
     * @return array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>}
     */
    public function snapshot(iterable $lines, ?Plant $plant): array
    {
        $frozen = [];

        foreach ($lines as $line) {
            $item = $line->getItem();
            if (!$item instanceof Item || null === $item->getId()) {
                continue;
            }

            $qty = (float) $line->getQty();
            if (0.0 === $qty) {
                continue;
            }

            $frozen[] = ['itemId' => (int) $item->getId(), 'qty' => $qty];
        }

        return [
            'plantId' => null === $plant ? null : (int) $plant->getId(),
            'lines' => $frozen,
        ];
    }

    /**
     * Replay a snapshot against stock.
     *
     * @param array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>} $snapshot
     * @param int                                                                        $sign     +1 adds to stock, -1 removes
     */
    public function applySnapshot(array $snapshot, int $sign): void
    {
        $plantId = $snapshot['plantId'] ?? null;
        if (null === $plantId) {
            return;
        }

        $plant = $this->plantRepository->find($plantId);
        if (!$plant instanceof Plant) {
            return;
        }

        foreach ($snapshot['lines'] as $line) {
            $item = $this->itemRepository->find($line['itemId']);
            if ($item instanceof Item) {
                $this->stockService->adjust($item, $plant, $sign * $line['qty']);
            }
        }
    }

    /**
     * Collect soft availability warnings for a sale, without blocking it.
     *
     * @param iterable<object>                                                           $lines
     * @param array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>} $previous the stored footprint when editing, so a line's own booked quantity is not counted against it
     *
     * @return string[]
     */
    public function salesWarnings(iterable $lines, ?Plant $plant, array $previous = ['plantId' => null, 'lines' => []]): array
    {
        if (null === $plant) {
            return [];
        }

        $booked = [];
        if (($previous['plantId'] ?? null) === (int) $plant->getId()) {
            foreach ($previous['lines'] as $line) {
                $booked[$line['itemId']] = ($booked[$line['itemId']] ?? 0.0) + $line['qty'];
            }
        }

        $warnings = [];
        foreach ($lines as $line) {
            $item = $line->getItem();
            if (!$item instanceof Item) {
                continue;
            }

            $warning = $this->stockService->checkAvailability(
                $item,
                $plant,
                (float) $line->getQty(),
                $booked[(int) $item->getId()] ?? 0.0,
            );

            if (null !== $warning) {
                $warnings[] = $warning;
            }
        }

        return $warnings;
    }

    /**
     * Drop empty rows, recompute line amounts, then the document totals.
     *
     * @param \App\Entity\SalesInvoice|\App\Entity\PurchaseBill $document
     */
    public function normalise(object $document): void
    {
        foreach ($document->getLines()->toArray() as $line) {
            if (!$line->isValid()) {
                $document->removeLine($line);

                continue;
            }

            $line->recalculateAmount();
        }

        $document->recalculateTotals();
    }
}
