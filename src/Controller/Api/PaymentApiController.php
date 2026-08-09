<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Payment;
use App\Repository\PartyRepository;
use App\Repository\PaymentRepository;
use App\Repository\StaffRepository;
use App\Service\DocumentPersister;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/payments')]
class PaymentApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly PaymentRepository $payments,
        private readonly PartyRepository $parties,
        private readonly StaffRepository $staff,
        private readonly DocumentPersister $persister,
        private readonly EntityManagerInterface $em,
    ) {
    }

    #[Route('', name: 'api_payments', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(array_map(
            $this->payload(...),
            $this->payments->findBy([], ['date' => 'DESC', 'id' => 'DESC'])
        ));
    }

    #[Route('/{id}', name: 'api_payment', requirements: ['id' => '\d+'], methods: ['GET'])]
    public function show(int $id): JsonResponse
    {
        $payment = $this->payments->find($id);

        return $payment ? $this->json($this->payload($payment)) : $this->notFound('Payment');
    }

    /**
     * Money out. No stock moves, but a number is consumed, so creation goes
     * through the persister.
     */
    #[Route('', name: 'api_payment_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $body = $this->decodeBody($request);

        if (null === $body) {
            return $this->malformedBody();
        }

        $payment = new Payment();
        $errors = $this->bind($payment, $body);

        if ([] !== $errors) {
            return $this->invalid($errors);
        }

        $this->persister->createPayment($payment);

        return $this->json(
            $this->payload($payment) + ['warnings' => []],
            JsonResponse::HTTP_CREATED,
        );
    }

    #[Route('/{id}', name: 'api_payment_update', requirements: ['id' => '\d+'], methods: ['PUT'])]
    public function update(int $id, Request $request): JsonResponse
    {
        $payment = $this->payments->find($id);

        if (null === $payment) {
            return $this->notFound('Payment');
        }

        $body = $this->decodeBody($request);

        if (null === $body) {
            return $this->malformedBody();
        }

        $errors = $this->bind($payment, $body);

        if ([] !== $errors) {
            return $this->invalid($errors);
        }

        $this->em->flush();

        return $this->json($this->payload($payment) + ['warnings' => []]);
    }

    /**
     * A payment is polymorphic: it is made either to a party or to a staff member
     * as wages, and the branch not taken is cleared so a voucher switched from one
     * to the other cannot keep pointing at both.
     *
     * @param array<string, mixed> $body
     *
     * @return array<string, string>
     */
    private function bind(Payment $payment, array $body): array
    {
        $errors = [];

        $date = $this->parseDate($body['payment_date'] ?? null, 'payment_date', $errors);
        $amount = $this->parseMoney($body['amount'] ?? null, 'amount', $errors, required: true, positive: true);
        $mode = $this->parseChoice($body['mode'] ?? null, 'mode', Payment::MODES, $errors, 'Cash');
        $type = $this->parseChoice($body['payment_type'] ?? null, 'payment_type', Payment::TYPES, $errors, 'party');

        $party = null;
        $staff = null;

        if ('staff' === $type) {
            $staffId = $body['staff_id'] ?? null;
            if (null === $staffId || '' === $staffId) {
                $errors['staff_id'] = 'Staff member is required for a wage payment';
            } else {
                $staff = $this->staff->find((int) $staffId);
                if (null === $staff) {
                    $errors['staff_id'] = 'Staff member not found';
                }
            }
        } elseif ('party' === $type) {
            $partyId = $body['party_id'] ?? null;
            if (null === $partyId || '' === $partyId) {
                $errors['party_id'] = 'Party is required';
            } else {
                $party = $this->parties->find((int) $partyId);
                if (null === $party) {
                    $errors['party_id'] = 'Party not found';
                }
            }
        }

        if ([] !== $errors) {
            return $errors;
        }

        $payment->setDate($date);
        $payment->setType((string) $type);
        $payment->setParty($party);
        $payment->setStaff($staff);
        $payment->setAmount($amount);
        $payment->setMode((string) $mode);
        $payment->setNotes($this->optionalText($body['notes'] ?? null));

        return [];
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(Payment $payment): array
    {
        $stamp = $this->stamp($payment->getDate());

        return [
            'id' => $payment->getId(),
            'document_number' => $payment->getNo(),
            // Wage payments carry a staff member instead of a party.
            'party_id' => $payment->getParty()?->getId(),
            'staff_id' => $payment->getStaff()?->getId(),
            'amount' => $this->paise($payment->getAmount()),
            'payment_date' => $payment->getDate()->format('Y-m-d'),
            'payment_type' => $payment->getType(),
            'reference_number' => null,
            'mode' => $payment->getMode(),
            'notes' => $payment->getNotes(),
            'created_at' => $stamp,
            'updated_at' => $stamp,
        ];
    }
}
