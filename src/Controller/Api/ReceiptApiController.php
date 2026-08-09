<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Receipt;
use App\Repository\ReceiptRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/receipts')]
class ReceiptApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly ReceiptRepository $receipts,
    ) {
    }

    #[Route('', name: 'api_receipts', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(array_map(
            $this->payload(...),
            $this->receipts->findBy([], ['date' => 'DESC', 'id' => 'DESC'])
        ));
    }

    #[Route('/{id}', name: 'api_receipt', requirements: ['id' => '\d+'], methods: ['GET'])]
    public function show(int $id): JsonResponse
    {
        $receipt = $this->receipts->find($id);

        return $receipt ? $this->json($this->payload($receipt)) : $this->notFound('Receipt');
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(Receipt $receipt): array
    {
        $stamp = $this->stamp($receipt->getDate());

        return [
            'id' => $receipt->getId(),
            'document_number' => $receipt->getNo(),
            'party_id' => $receipt->getParty()?->getId(),
            'amount' => $this->paise($receipt->getAmount()),
            'receipt_date' => $receipt->getDate()->format('Y-m-d'),
            'mode' => $receipt->getMode(),
            'notes' => $receipt->getNotes(),
            'created_at' => $stamp,
            'updated_at' => $stamp,
        ];
    }
}
