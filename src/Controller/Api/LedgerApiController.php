<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Repository\PartyRepository;
use App\Service\LedgerService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;

/**
 * The party ledger on the wire — spec §4.4, read-only.
 *
 * The running account for one party: every sales invoice, purchase bill, receipt and
 * payment that touches them, oldest first, each carrying the balance as it stood after
 * that entry.
 *
 * The sign convention is {@see LedgerService}'s and is preserved end to end:
 *
 *   POSITIVE => they owe us (receivable)
 *   NEGATIVE => we owe them (payable)
 *
 * No arithmetic happens here. LedgerService owns the ordering, the running total and
 * the opening-balance row; this controller only converts rupees to integer paise and
 * renames the service's keys to the wire's snake_case. Re-deriving any of it would give
 * the phone a second, quietly divergent definition of what a party owes.
 *
 * The route sits under /api/parties/{id}/ledger while the rest of that prefix lives in
 * {@see PartyApiController}: attribute routing keys on the full path, and the sibling
 * /api/parties/{id} route requires a numeric id, so it cannot swallow this one.
 */
class LedgerApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly PartyRepository $parties,
        private readonly LedgerService $ledger,
    ) {
    }

    #[Route(
        '/api/parties/{id}/ledger',
        name: 'api_party_ledger',
        requirements: ['id' => '\d+'],
        methods: ['GET'],
    )]
    public function show(int $id): JsonResponse
    {
        $party = $this->parties->find($id);

        if (null === $party) {
            return $this->notFound('Party');
        }

        $entries = $this->ledger->getEntries($party);

        return $this->json([
            'party_id' => $party->getId(),
            'party_name' => $party->getName(),
            // The last row's running total, or zero for a party with no history —
            // the same figure the party payload calls ledger_balance.
            'closing_balance' => $this->paise($this->ledger->getBalance($party)),
            'entries' => array_map($this->entry(...), $entries),
        ]);
    }

    /**
     * One row of the running account.
     *
     * `amount` and `balance` keep the service's sign; the phone decides how to show
     * direction. `document_id` is null for the opening-balance row, which is a property
     * of the party rather than a document anything can open.
     *
     * @param array{date: string, type: string, ref: string, amount: float, balance: float, kind: string, id: int|null} $entry
     *
     * @return array<string, mixed>
     */
    private function entry(array $entry): array
    {
        return [
            'date' => $entry['date'],
            'type' => $entry['type'],
            'reference' => $entry['ref'],
            'amount' => $this->paise($entry['amount']),
            'balance' => $this->paise($entry['balance']),
            'kind' => $entry['kind'],
            'document_id' => $entry['id'],
        ];
    }
}
