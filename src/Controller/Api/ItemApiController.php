<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Item;
use App\Repository\ItemRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/items')]
class ItemApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly ItemRepository $items,
    ) {
    }

    #[Route('', name: 'api_items', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(array_map(
            $this->payload(...),
            $this->items->findBy([], ['name' => 'ASC'])
        ));
    }

    #[Route('/{id}', name: 'api_item', requirements: ['id' => '\d+'], methods: ['GET'])]
    public function show(int $id): JsonResponse
    {
        $item = $this->items->find($id);

        return $item ? $this->json($this->payload($item)) : $this->notFound('Item');
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(Item $item): array
    {
        return [
            'id' => $item->getId(),
            'name' => $item->getName(),
            'type' => $item->getType(),
            'unit' => $item->getUnit(),
            'hsn_code' => $item->getHsn(),
            'default_rate' => $this->paise($item->getDefaultRate()),
            // No GST is modelled anywhere in the portal; kept for wire compatibility.
            'gst_rate' => 0.0,
            // Items carry no timestamp columns.
            'created_at' => null,
            'updated_at' => null,
        ];
    }
}
