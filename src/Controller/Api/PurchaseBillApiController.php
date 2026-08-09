<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\PurchaseBill;
use App\Entity\PurchaseBillLine;
use App\Repository\ItemRepository;
use App\Repository\PartyRepository;
use App\Repository\PlantRepository;
use App\Repository\PurchaseBillRepository;
use App\Repository\SettingsRepository;
use App\Service\DocumentPersister;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/purchase-bills')]
class PurchaseBillApiController extends AbstractController
{
    use ApiPayloadTrait;
    use DocumentWriteTrait;

    public function __construct(
        private readonly PurchaseBillRepository $purchaseBills,
        private readonly PartyRepository $parties,
        private readonly PlantRepository $plants,
        private readonly ItemRepository $items,
        private readonly SettingsRepository $settings,
        private readonly DocumentPersister $persister,
    ) {
    }

    #[Route('', name: 'api_purchase_bills', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(array_map(
            $this->payload(...),
            $this->purchaseBills->findBy([], ['date' => 'DESC', 'id' => 'DESC'])
        ));
    }

    #[Route('/{id}', name: 'api_purchase_bill', requirements: ['id' => '\d+'], methods: ['GET'])]
    public function show(int $id): JsonResponse
    {
        $bill = $this->purchaseBills->find($id);

        return $bill ? $this->json($this->payload($bill)) : $this->notFound('Purchase bill');
    }

    /**
     * Books a bill and adds its goods to stock — the sales invoice with the sign
     * flipped, which is why both go through the same persister.
     */
    #[Route('', name: 'api_purchase_bill_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $body = $this->decodeBody($request);

        if (null === $body) {
            return $this->malformedBody();
        }

        $read = $this->readDocumentBody($body, 'bill_date', 'Supplier');

        if ([] !== $read['errors']) {
            return $this->invalid($read['errors']);
        }

        $bill = new PurchaseBill();
        $this->apply($bill, $read);

        $this->persister->createPurchaseBill($bill);

        // A purchase adds stock, so it can never fall short; the key is present
        // anyway so the client has one response shape to read.
        return $this->json(
            $this->payload($bill) + ['warnings' => []],
            JsonResponse::HTTP_CREATED,
        );
    }

    /**
     * Rewrites a bill. The stored footprint is frozen before the entity changes,
     * so the goods this bill originally added are removed from the plant it
     * originally landed at before the new quantities are applied.
     */
    #[Route('/{id}', name: 'api_purchase_bill_update', requirements: ['id' => '\d+'], methods: ['PUT'])]
    public function update(int $id, Request $request): JsonResponse
    {
        $bill = $this->purchaseBills->find($id);

        if (null === $bill) {
            return $this->notFound('Purchase bill');
        }

        $body = $this->decodeBody($request);

        if (null === $body) {
            return $this->malformedBody();
        }

        $read = $this->readDocumentBody($body, 'bill_date', 'Supplier');

        if ([] !== $read['errors']) {
            return $this->invalid($read['errors']);
        }

        $storedSnapshot = $this->persister->snapshotOf($bill);

        $this->apply($bill, $read);

        $this->persister->updatePurchaseBill($bill, $storedSnapshot);

        return $this->json($this->payload($bill) + ['warnings' => []]);
    }

    /**
     * @param array{party: mixed, plant: mixed, date: mixed, discount: float, notes: string|null, lines: array<int, array{item: mixed, qty: float, rate: float}>} $read
     */
    private function apply(PurchaseBill $bill, array $read): void
    {
        $bill->setParty($read['party']);
        $bill->setPlant($read['plant']);
        $bill->setDate($read['date']);
        $bill->setDiscount($read['discount']);
        $bill->setNotes($read['notes']);

        foreach ($bill->getLines()->toArray() as $existing) {
            $bill->removeLine($existing);
        }

        foreach ($read['lines'] as $line) {
            $bill->addLine(
                (new PurchaseBillLine())
                    ->setItem($line['item'])
                    ->setQty($line['qty'])
                    ->setRate($line['rate'])
            );
        }

        $bill->recalculateTotals();
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(PurchaseBill $bill): array
    {
        $stamp = $this->stamp($bill->getDate());

        return [
            'id' => $bill->getId(),
            'document_number' => $bill->getNo(),
            'party_id' => $bill->getParty()?->getId() ?? 0,
            'plant_id' => $bill->getPlant()?->getId(),
            'bill_date' => $bill->getDate()->format('Y-m-d'),
            'due_date' => null,
            'total_amount' => $this->paise($bill->getSubtotal()),
            'discount_amount' => $this->paise($bill->getDiscount()),
            // No GST is modelled anywhere in the portal; kept for wire compatibility.
            'gst_amount' => 0,
            'net_amount' => $this->paise($bill->getTotal()),
            'status' => 'received',
            'notes' => $bill->getNotes(),
            'lines' => $this->linePayloads($bill->getLines()),
            'created_at' => $stamp,
            'updated_at' => $stamp,
        ];
    }
}
