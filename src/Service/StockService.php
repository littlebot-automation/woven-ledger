<?php

declare(strict_types=1);

namespace App\Service;

use App\Entity\Item;
use App\Entity\ItemStock;
use App\Entity\Plant;
use App\Entity\StockMovement;
use App\Repository\ItemStockRepository;
use App\Repository\PlantRepository;
use App\Repository\SettingsRepository;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Plant-wise stock movement — spec §4.2 and §4.3.
 *
 * Stock is deliberately allowed to go negative: the original app warns on a short
 * sale but never blocks it, because the physical goods may already have shipped.
 */
class StockService
{
    public function __construct(
        private readonly ItemStockRepository $stockRepository,
        private readonly PlantRepository $plantRepository,
        private readonly SettingsRepository $settingsRepository,
        private readonly EntityManagerInterface $em,
        private readonly IndianNumberFormatter $numbers,
    ) {
    }

    public function getQty(Item $item, Plant $plant): float
    {
        return $this->stockRepository->findOneFor($item, $plant)?->getQty() ?? 0.0;
    }

    /** Absolute set — used for opening stock and physical-count corrections. */
    public function setQty(Item $item, Plant $plant, float $qty): void
    {
        $this->row($item, $plant)->setQty($qty);
        $this->em->flush();
    }

    /** Signed delta — used for wastage and every document movement. */
    public function adjust(Item $item, Plant $plant, float $delta): void
    {
        if (0.0 === $delta) {
            return;
        }

        $row = $this->row($item, $plant);
        $row->setQty($row->getQty() + $delta);
        $this->em->flush();
    }

    /**
     * Absolute set, audited — the manual "physical count / opening stock" path.
     *
     * The audit row is written here rather than inside setQty()/adjust() on
     * purpose. Those two are also what DocumentStockManager::applySnapshot() calls
     * for every sales invoice and purchase bill, so recording there would put a
     * StockMovement row beside every document line — and since StockHistoryService
     * already reconstructs document movement from the lines themselves, every bill
     * and invoice would be counted twice. These wrappers sit one level up, on the
     * only path no document takes: {@see \App\Controller\Admin\ItemStockController}.
     */
    public function setQtyManually(Item $item, Plant $plant, float $qty, string $reason = ''): void
    {
        $before = $this->getQty($item, $plant);
        $this->setQty($item, $plant, $qty);

        $this->recordMovement($item, $plant, $qty - $before, $reason ?: \sprintf(
            'Set to %s (was %s)',
            $this->numbers->qty($qty),
            $this->numbers->qty($before),
        ));
    }

    /** Signed delta, audited. See {@see setQtyManually()} for why the seam is here. */
    public function adjustManually(Item $item, Plant $plant, float $delta, string $reason = ''): void
    {
        $this->adjust($item, $plant, $delta);

        $this->recordMovement($item, $plant, $delta, $reason ?: 'Manual adjustment');
    }

    /**
     * Apply (or reverse) the lines of a sales invoice.
     *
     * @param iterable<object> $lines each exposing getItem() and getQty()
     * @param int              $sign  -1 to apply a sale, +1 to reverse one
     */
    public function applySalesLines(iterable $lines, ?Plant $plant, int $sign): void
    {
        $this->applyLines($lines, $plant, $sign);
    }

    /**
     * Apply (or reverse) the lines of a purchase bill.
     *
     * @param iterable<object> $lines each exposing getItem() and getQty()
     * @param int              $sign  +1 to apply a purchase, -1 to reverse one
     */
    public function applyPurchaseLines(iterable $lines, ?Plant $plant, int $sign): void
    {
        $this->applyLines($lines, $plant, $sign);
    }

    public function thresholdFor(Item $item): float
    {
        return $item->getLowStockThreshold()
            ?? $this->settingsRepository->getSettings()->getLowStockDefault();
    }

    public function isLow(Item $item, Plant $plant): bool
    {
        return $this->getQty($item, $plant) <= $this->thresholdFor($item);
    }

    /**
     * Every (item, plant) pair currently at or below its threshold.
     *
     * @return array<int, array{item: Item, plant: Plant, qty: float, threshold: float}>
     */
    public function getLowStockRows(): array
    {
        $rows = [];

        foreach ($this->stockRepository->findAllWithRelations() as $stock) {
            $item = $stock->getItem();
            $plant = $stock->getPlant();

            if (null === $item || null === $plant) {
                continue;
            }

            $threshold = $this->thresholdFor($item);
            if ($stock->getQty() <= $threshold) {
                $rows[] = [
                    'item' => $item,
                    'plant' => $plant,
                    'qty' => $stock->getQty(),
                    'threshold' => $threshold,
                ];
            }
        }

        return $rows;
    }

    public function getLowStockCount(): int
    {
        return \count($this->getLowStockRows());
    }

    /**
     * A soft availability warning, or null when there is enough on hand.
     *
     * This is advisory only. Callers must show it and still save.
     */
    public function checkAvailability(Item $item, Plant $plant, float $qty, float $alreadyBooked = 0.0): ?string
    {
        $available = $this->getQty($item, $plant) + $alreadyBooked;

        if ($qty <= $available) {
            return null;
        }

        return \sprintf(
            'Stock warning: only %s %s of "%s" available at %s.',
            $this->numbers->qty($available),
            $item->getUnit(),
            $item->getName(),
            $plant->getName(),
        );
    }

    /**
     * Per-plant quantities for one item, keyed by plant id — backs the dynamic
     * plant columns on the item index and the stock report.
     *
     * @return array<int, float>
     */
    public function qtyByPlant(Item $item): array
    {
        $out = [];
        foreach ($this->plantRepository->findAllOrdered() as $plant) {
            $out[(int) $plant->getId()] = $this->getQty($item, $plant);
        }

        return $out;
    }

    /**
     * A no-op movement is not worth an audit row — an operator re-saving the form
     * with the quantity already on hand has not moved anything.
     */
    private function recordMovement(Item $item, Plant $plant, float $delta, string $reason): void
    {
        if (abs($delta) < 0.0005) {
            return;
        }

        $movement = (new StockMovement())
            ->setItem($item)
            ->setPlant($plant)
            ->setQtyDelta($delta)
            ->setSource(StockMovement::SOURCE_MANUAL)
            ->setReason($reason);

        $this->em->persist($movement);
        $this->em->flush();
    }

    /** @param iterable<object> $lines */
    private function applyLines(iterable $lines, ?Plant $plant, int $sign): void
    {
        if (null === $plant) {
            return;
        }

        foreach ($lines as $line) {
            $item = $line->getItem();
            if (!$item instanceof Item) {
                continue;
            }

            $qty = (float) $line->getQty();
            if (0.0 === $qty) {
                continue;
            }

            $row = $this->row($item, $plant);
            $row->setQty($row->getQty() + ($sign * $qty));
        }

        $this->em->flush();
    }

    /**
     * The stock row for this item/plant, created on demand.
     *
     * Registering it on the item's collection too keeps getStockFor()/getTotalStock()
     * accurate within the same request.
     */
    private function row(Item $item, Plant $plant): ItemStock
    {
        $row = $this->stockRepository->findOneFor($item, $plant);

        if (!$row instanceof ItemStock) {
            $row = new ItemStock();
            $row->setItem($item);
            $row->setPlant($plant);
            $item->addStock($row);
            $this->em->persist($row);
        }

        return $row;
    }
}
