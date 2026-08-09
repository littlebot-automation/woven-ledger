<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\StaffWork;
use App\Repository\PlantRepository;
use App\Repository\StaffRepository;
use App\Repository\StaffWorkRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/staff-work')]
class StaffWorkApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly StaffWorkRepository $staffWork,
        private readonly StaffRepository $staff,
        private readonly PlantRepository $plants,
        private readonly EntityManagerInterface $em,
    ) {
    }

    #[Route('', name: 'api_staff_work_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        return $this->json(array_map(
            $this->payload(...),
            $this->staffWork->findBy([], ['date' => 'DESC', 'id' => 'DESC'])
        ));
    }

    #[Route('/{id}', name: 'api_staff_work', requirements: ['id' => '\d+'], methods: ['GET'])]
    public function show(int $id): JsonResponse
    {
        $entry = $this->staffWork->find($id);

        return $entry ? $this->json($this->payload($entry)) : $this->notFound('Staff work');
    }

    /**
     * Records a day's work. The app posts this online and waits for the answer —
     * there is no offline queue — so the response is the created resource.
     *
     * `paid` is deliberately not accepted: only wage settlement may set it.
     */
    #[Route('', name: 'api_staff_work_create', methods: ['POST'])]
    public function create(Request $request): JsonResponse
    {
        $body = json_decode($request->getContent(), true);

        if (!\is_array($body)) {
            return $this->json(['errors' => ['body' => 'Expected a JSON object']], JsonResponse::HTTP_UNPROCESSABLE_ENTITY);
        }

        $errors = [];

        $date = null;
        $rawDate = $body['date'] ?? null;
        if (null === $rawDate || '' === $rawDate) {
            $date = new \DateTimeImmutable('today');
        } else {
            $parsed = \DateTimeImmutable::createFromFormat('!Y-m-d', (string) $rawDate);
            if (false === $parsed) {
                $errors['date'] = 'Date must be formatted YYYY-MM-DD';
            } else {
                $date = $parsed;
            }
        }

        $staff = null;
        $staffId = $body['staff_id'] ?? null;
        if (null === $staffId || '' === $staffId) {
            $errors['staff_id'] = 'Staff member is required';
        } else {
            $staff = $this->staff->find((int) $staffId);
            if (null === $staff) {
                $errors['staff_id'] = 'Staff member not found';
            }
        }

        // The plant is optional — "None" is a valid choice on the portal form too.
        $plant = null;
        $plantId = $body['plant_id'] ?? null;
        if (null !== $plantId && '' !== $plantId) {
            $plant = $this->plants->find((int) $plantId);
            if (null === $plant) {
                $errors['plant_id'] = 'Plant not found';
            }
        }

        $workType = trim((string) ($body['work_type'] ?? ''));
        if ('' === $workType) {
            $errors['work_type'] = 'Work type is required';
        }

        $qty = 0.0;
        $rawQty = $body['qty'] ?? null;
        if (null === $rawQty || '' === $rawQty || !is_numeric($rawQty)) {
            $errors['qty'] = 'Qty is required';
        } elseif ((float) $rawQty <= 0) {
            $errors['qty'] = 'Qty must be greater than zero';
        } else {
            $qty = (float) $rawQty;
        }

        // Rate arrives as integer paise, or is omitted to take the staff member's
        // own wage rate — which the entity already picks between daily and piece.
        $rate = null;
        $rawRate = $body['rate'] ?? null;
        if (null === $rawRate || '' === $rawRate) {
            $rate = $staff?->getEffectiveRate();
        } elseif (!is_numeric($rawRate)) {
            $errors['rate'] = 'Rate must be an amount in paise';
        } elseif ((int) $rawRate < 0) {
            $errors['rate'] = 'Rate cannot be negative';
        } else {
            $rate = (int) $rawRate / 100;
        }

        if ([] !== $errors) {
            return $this->json(['errors' => $errors], JsonResponse::HTTP_UNPROCESSABLE_ENTITY);
        }

        $entry = new StaffWork();
        $entry->setDate($date);
        $entry->setStaff($staff);
        $entry->setPlant($plant);
        $entry->setWorkType($workType);
        $entry->setRate($rate);
        // setQty recalculates the amount, so it goes last and the client never
        // sends an amount of its own.
        $entry->setQty($qty);

        $this->em->persist($entry);
        $this->em->flush();

        return $this->json($this->payload($entry), JsonResponse::HTTP_CREATED);
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(StaffWork $entry): array
    {
        return [
            'id' => $entry->getId(),
            'date' => $entry->getDate()->format('Y-m-d'),
            'staff_id' => $entry->getStaff()?->getId(),
            'plant_id' => $entry->getPlant()?->getId(),
            'work_type' => $entry->getWorkType(),
            // Qty is a count of days or of pieces, so it stays a decimal rather
            // than being scaled like money.
            'qty' => $entry->getQty(),
            'rate' => $this->paise($entry->getRate()),
            'amount' => $this->paise($entry->getAmount()),
            'paid' => $entry->isPaid(),
            'payment_voucher_id' => $entry->getPaymentVoucher()?->getId(),
        ];
    }
}
