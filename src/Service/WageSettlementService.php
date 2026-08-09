<?php

declare(strict_types=1);

namespace App\Service;

use App\Entity\Payment;
use App\Entity\Staff;
use App\Repository\StaffWorkRepository;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Wage settlement — spec §4.5.
 *
 * Settling turns every unpaid work entry for one staff member into a single
 * payment voucher, and marks those entries paid against it.
 */
class WageSettlementService
{
    public function __construct(
        private readonly StaffWorkRepository $staffWorkRepository,
        private readonly DocumentNumberService $documentNumbers,
        private readonly EntityManagerInterface $em,
    ) {
    }

    /**
     * @return array{payment: Payment, total: float, count: int}|null null when there is nothing outstanding
     */
    public function settle(Staff $staff, \DateTimeImmutable $date, string $mode): ?array
    {
        $entries = $this->staffWorkRepository->findUnpaidFor($staff);

        if ([] === $entries) {
            return null;
        }

        $total = 0.0;
        foreach ($entries as $entry) {
            $total += $entry->getAmount();
        }

        $payment = new Payment();
        $payment->setNo($this->documentNumbers->consume(DocumentNumberService::PAYMENT));
        $payment->setDate($date);
        $payment->setType('staff');
        $payment->setStaff($staff);
        $payment->setParty(null);
        $payment->setAmount($total);
        $payment->setMode($mode);
        $payment->setNotes(\sprintf('Wage settlement — %d entries', \count($entries)));

        $this->em->persist($payment);

        foreach ($entries as $entry) {
            $entry->setPaid(true);
            $entry->setPaymentVoucher($payment);
        }

        $this->em->flush();

        return ['payment' => $payment, 'total' => $total, 'count' => \count($entries)];
    }

    /**
     * Staff with outstanding wages and what they are owed — drives the picker.
     *
     * @return array<int, array{staff: Staff, total: float, count: int}>
     */
    public function outstanding(): array
    {
        return $this->staffWorkRepository->findStaffWithUnpaid();
    }
}
