<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Entity\Staff;
use App\Repository\StaffRepository;
use App\Repository\StaffWorkRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/staff')]
class StaffApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly StaffRepository $staff,
        private readonly StaffWorkRepository $staffWork,
    ) {
    }

    #[Route('', name: 'api_staff_list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        // One aggregate for the whole list — a per-row unpaid query would be N+1.
        $unpaid = $this->unpaidByStaff();

        return $this->json(array_map(
            fn (Staff $member): array => $this->payload($member, $unpaid[(int) $member->getId()] ?? 0.0),
            $this->staff->findAllOrdered()
        ));
    }

    #[Route('/{id}', name: 'api_staff', requirements: ['id' => '\d+'], methods: ['GET'])]
    public function show(int $id): JsonResponse
    {
        $member = $this->staff->find($id);

        if (null === $member) {
            return $this->notFound('Staff');
        }

        $unpaid = 0.0;
        foreach ($this->staffWork->findUnpaidFor($member) as $entry) {
            $unpaid += $entry->getAmount();
        }

        return $this->json($this->payload($member, $unpaid));
    }

    /**
     * Unpaid wage totals keyed by staff id.
     *
     * @return array<int, float>
     */
    private function unpaidByStaff(): array
    {
        $totals = [];
        foreach ($this->staffWork->findStaffWithUnpaid() as $row) {
            $totals[(int) $row['staff']->getId()] = (float) $row['total'];
        }

        return $totals;
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(Staff $member, float $unpaidWages): array
    {
        return [
            'id' => $member->getId(),
            'name' => $member->getName(),
            'role' => $member->getRole(),
            'plant_id' => $member->getPlant()?->getId(),
            'phone' => $member->getPhone(),
            // 'daily' | 'piece' — the Android enum is StaffWageType.DAILY/PIECE.
            'wage_type' => $member->getWageType(),
            'daily_rate' => $this->paise($member->getDailyRate()),
            'piece_rate' => $this->paise($member->getPieceRate()),
            'effective_rate' => $this->paise($member->getEffectiveRate()),
            'unpaid_wages' => $this->paise($unpaidWages),
        ];
    }
}
