<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Receipt;
use App\Repository\PartyRepository;
use App\Repository\ReceiptRepository;
use App\Service\DocumentPersister;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/receipts')]
class ReceiptApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly ReceiptRepository $receipts,
        private readonly PartyRepository $parties,
        private readonly DocumentPersister $persister,
        private readonly EntityManagerInterface $em,
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
     * Money in. A receipt moves no stock, but it does take a document number, so
     * creation still goes through the persister — the counter is only ever read
     * and advanced in one place.
     */
    #[Route('', name: 'api_receipt_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $body = $this->decodeBody($request);

        if (null === $body) {
            return $this->malformedBody();
        }

        $receipt = new Receipt();
        $errors = $this->bind($receipt, $body);

        if ([] !== $errors) {
            return $this->invalid($errors);
        }

        $this->persister->createReceipt($receipt);

        return $this->json(
            $this->payload($receipt) + ['warnings' => []],
            JsonResponse::HTTP_CREATED,
        );
    }

    /**
     * Editing never re-numbers: the number was consumed when the receipt was
     * created and belongs to it for good.
     */
    #[Route('/{id}', name: 'api_receipt_update', requirements: ['id' => '\d+'], methods: ['PUT'])]
    public function update(int $id, Request $request): JsonResponse
    {
        $receipt = $this->receipts->find($id);

        if (null === $receipt) {
            return $this->notFound('Receipt');
        }

        $body = $this->decodeBody($request);

        if (null === $body) {
            return $this->malformedBody();
        }

        $errors = $this->bind($receipt, $body);

        if ([] !== $errors) {
            return $this->invalid($errors);
        }

        $this->em->flush();

        return $this->json($this->payload($receipt) + ['warnings' => []]);
    }

    /**
     * @param array<string, mixed> $body
     *
     * @return array<string, string>
     */
    private function bind(Receipt $receipt, array $body): array
    {
        $errors = [];

        $date = $this->parseDate($body['receipt_date'] ?? null, 'receipt_date', $errors);
        $amount = $this->parseMoney($body['amount'] ?? null, 'amount', $errors, required: true, positive: true);
        $mode = $this->parseChoice($body['mode'] ?? null, 'mode', Receipt::MODES, $errors, 'Cash');

        $party = null;
        $partyId = $body['party_id'] ?? null;
        if (null === $partyId || '' === $partyId) {
            $errors['party_id'] = 'Party is required';
        } else {
            $party = $this->parties->find((int) $partyId);
            if (null === $party) {
                $errors['party_id'] = 'Party not found';
            }
        }

        if ([] !== $errors) {
            return $errors;
        }

        $receipt->setDate($date);
        $receipt->setParty($party);
        $receipt->setAmount($amount);
        $receipt->setMode((string) $mode);
        $receipt->setNotes($this->optionalText($body['notes'] ?? null));

        return [];
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
