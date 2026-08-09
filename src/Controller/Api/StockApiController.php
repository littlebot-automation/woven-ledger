<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Item;
use App\Entity\Plant;
use App\Repository\ItemRepository;
use App\Repository\PlantRepository;
use App\Service\StockService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;

/**
 * Plant-wise stock, read-only — the wire form of the Stock Report (spec §6.3).
 *
 * One row per item, carrying its per-plant breakdown and its total, so the phone
 * can render "Item / Type / a column per plant / Total" without a second call and
 * without knowing how stock is stored.
 *
 * Quantities are NOT money: they cross the wire as decimals with three places,
 * exactly as Doctrine stores them. The paise() helper is deliberately unused here —
 * multiplying a quantity by 100 would report 120 tonnes as 12,000.
 *
 * Every arithmetic and low-stock decision is delegated to {@see StockService}; this
 * controller only shapes what that service already knows.
 */
#[Route('/api/stock')]
class StockApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly ItemRepository $items,
        private readonly PlantRepository $plants,
        private readonly StockService $stock,
    ) {
    }

    #[Route('', name: 'api_stock', methods: ['GET'])]
    public function list(): JsonResponse
    {
        $plants = $this->plants->findAllOrdered();

        return $this->json(array_map(
            fn (Item $item): array => $this->payload($item, $plants),
            // Joins the stock rows in one query; already ordered by item name.
            $this->items->findAllWithStock()
        ));
    }

    #[Route('/{itemId}', name: 'api_stock_item', requirements: ['itemId' => '\d+'], methods: ['GET'])]
    public function show(int $itemId): JsonResponse
    {
        $item = $this->items->find($itemId);

        if (null === $item) {
            return $this->notFound('Item');
        }

        return $this->json($this->payload($item, $this->plants->findAllOrdered()));
    }

    /**
     * @param Plant[] $plants every plant, so an item absent from one still reports a
     *                        zero column rather than a hole the report has to guess at
     *
     * @return array<string, mixed>
     */
    private function payload(Item $item, array $plants): array
    {
        $quantities = $this->stock->qtyByPlant($item);
        $byPlant = [];
        $total = 0.0;

        foreach ($plants as $plant) {
            $qty = $quantities[(int) $plant->getId()] ?? 0.0;
            $total += $qty;

            $byPlant[] = [
                'plant_id' => $plant->getId(),
                'plant_name' => $plant->getName(),
                'qty' => $this->qty($qty),
            ];
        }

        return [
            'item_id' => $item->getId(),
            'item_name' => $item->getName(),
            'item_type' => $item->getType(),
            'unit' => $item->getUnit(),
            'total_qty' => $this->qty($total),
            // The item's own override. Null means "inherit the settings default",
            // which is the blank field on the portal form.
            'low_stock_threshold' => $this->qty($item->getLowStockThreshold()),
            'is_low' => $this->isLow($item, $plants),
            'by_plant' => $byPlant,
        ];
    }

    /**
     * Spec §4.3: low when the quantity *at a plant* is at or below the threshold, so
     * one short plant is enough to flag the item even when the total looks healthy.
     *
     * @param Plant[] $plants
     */
    private function isLow(Item $item, array $plants): bool
    {
        foreach ($plants as $plant) {
            if ($this->stock->isLow($item, $plant)) {
                return true;
            }
        }

        return false;
    }

    /** A quantity at the three decimal places ItemStock stores, never paise. */
    private function qty(?float $value): ?float
    {
        return null === $value ? null : round($value, 3);
    }
}
