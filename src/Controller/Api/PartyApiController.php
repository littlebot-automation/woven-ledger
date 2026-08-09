<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Party;
use App\Repository\PartyRepository;
use App\Service\LedgerService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/parties')]
class PartyApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly PartyRepository $parties,
        private readonly LedgerService $ledger,
        private readonly EntityManagerInterface $em,
    ) {
    }

    #[Route('', name: 'api_parties', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(array_map(
            $this->payload(...),
            $this->parties->findBy([], ['name' => 'ASC'])
        ));
    }

    #[Route('/{id}', name: 'api_party', requirements: ['id' => '\d+'], methods: ['GET'])]
    public function show(int $id): JsonResponse
    {
        $party = $this->parties->find($id);

        return $party ? $this->json($this->payload($party)) : $this->notFound('Party');
    }

    /**
     * A party is a master record: no numbering, no stock, so it is persisted here
     * rather than through DocumentPersister.
     */
    #[Route('', name: 'api_party_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $body = $this->decodeBody($request);

        if (null === $body) {
            return $this->malformedBody();
        }

        $party = new Party();
        $errors = $this->bind($party, $body);

        if ([] !== $errors) {
            return $this->invalid($errors);
        }

        $this->em->persist($party);
        $this->em->flush();

        return $this->json($this->payload($party), JsonResponse::HTTP_CREATED);
    }

    #[Route('/{id}', name: 'api_party_update', requirements: ['id' => '\d+'], methods: ['PUT'])]
    public function update(int $id, Request $request): JsonResponse
    {
        $party = $this->parties->find($id);

        if (null === $party) {
            return $this->notFound('Party');
        }

        $body = $this->decodeBody($request);

        if (null === $body) {
            return $this->malformedBody();
        }

        $errors = $this->bind($party, $body);

        if ([] !== $errors) {
            return $this->invalid($errors);
        }

        $this->em->flush();

        return $this->json($this->payload($party));
    }

    /**
     * Fills the entity from the request, reporting per-field problems.
     *
     * Nothing is written until the caller sees an empty error map, so a rejected
     * update leaves the managed entity untouched apart from fields that were
     * themselves valid — and the caller does not flush in that case.
     *
     * @param array<string, mixed> $body
     *
     * @return array<string, string>
     */
    private function bind(Party $party, array $body): array
    {
        $errors = [];

        $name = trim((string) ($body['name'] ?? ''));
        if ('' === $name) {
            $errors['name'] = 'Party name is required';
        }

        // The entity's own allow-list decides what a type may be.
        $type = $this->parseChoice($body['type'] ?? null, 'type', Party::TYPES, $errors, 'customer');

        $openingBalance = $this->parseMoney($body['opening_balance'] ?? null, 'opening_balance', $errors) ?? 0.0;
        $openingType = $this->parseChoice(
            $body['opening_balance_type'] ?? null,
            'opening_balance_type',
            Party::OPENING_TYPES,
            $errors,
            'to_receive',
        );

        if ([] !== $errors) {
            return $errors;
        }

        $party->setName($name);
        $party->setType((string) $type);
        $party->setPhone($this->optionalText($body['phone'] ?? null));
        $party->setGstin($this->optionalText($body['gst_number'] ?? null));
        $party->setAddress($this->optionalText($body['address'] ?? null));
        $party->setOpeningBalance($openingBalance);
        $party->setOpeningBalanceType((string) $openingType);

        return [];
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(Party $party): array
    {
        $stamp = $this->stamp($party->getCreatedAt());

        return [
            'id' => $party->getId(),
            'name' => $party->getName(),
            'type' => $party->getType(),
            'address' => $party->getAddress(),
            'phone' => $party->getPhone(),
            // The portal has never captured party e-mail addresses.
            'email' => null,
            'gst_number' => $party->getGstin(),
            // The live balance is derived; the opening figure is what an edit form
            // rewrites, so both are exposed rather than only the total.
            'opening_balance' => $this->paise($party->getOpeningBalance()),
            'opening_balance_type' => $party->getOpeningBalanceType(),
            'ledger_balance' => $this->paise($this->ledger->getPartyBalance($party)),
            'created_at' => $stamp,
            'updated_at' => $stamp,
        ];
    }
}
