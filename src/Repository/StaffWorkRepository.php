<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Staff;
use App\Entity\StaffWork;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<StaffWork>
 */
class StaffWorkRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, StaffWork::class);
    }

    /** @return StaffWork[] */
    public function findUnpaidFor(Staff $staff): array
    {
        return $this->createQueryBuilder('w')
            ->andWhere('w.staff = :staff')
            ->andWhere('w.paid = false')
            ->setParameter('staff', $staff)
            ->orderBy('w.date', 'ASC')
            ->getQuery()
            ->getResult();
    }

    public function hasEntriesFor(Staff $staff): bool
    {
        return (int) $this->createQueryBuilder('w')
            ->select('COUNT(w.id)')
            ->andWhere('w.staff = :staff')
            ->setParameter('staff', $staff)
            ->getQuery()
            ->getSingleScalarResult() > 0;
    }

    public function sumUnpaid(): float
    {
        return (float) $this->createQueryBuilder('w')
            ->select('COALESCE(SUM(w.amount), 0)')
            ->andWhere('w.paid = false')
            ->getQuery()
            ->getSingleScalarResult();
    }

    public function sumPaid(): float
    {
        return (float) $this->createQueryBuilder('w')
            ->select('COALESCE(SUM(w.amount), 0)')
            ->andWhere('w.paid = true')
            ->getQuery()
            ->getSingleScalarResult();
    }

    /**
     * Staff who currently have unpaid work, with their unpaid totals — drives
     * the settlement picker.
     *
     * @return array<int, array{staff: Staff, total: float, count: int}>
     */
    public function findStaffWithUnpaid(): array
    {
        // Doctrine cannot select a joined entity without the root alias, so the
        // aggregate is scalar and the Staff entities are hydrated afterwards.
        $rows = $this->createQueryBuilder('w')
            ->select('IDENTITY(w.staff) AS staffId', 'SUM(w.amount) AS total', 'COUNT(w.id) AS cnt')
            ->andWhere('w.paid = false')
            ->groupBy('w.staff')
            ->getQuery()
            ->getResult();

        return $this->hydrateStaffRows($rows, static fn (array $r): array => [
            'total' => (float) $r['total'],
            'count' => (int) $r['cnt'],
        ]);
    }

    /**
     * Paid vs unpaid totals per staff member, for the Staff Payments report.
     *
     * @return array<int, array{staff: Staff, paid: float, unpaid: float}>
     */
    public function sumByStaff(): array
    {
        $rows = $this->createQueryBuilder('w')
            ->select(
                'IDENTITY(w.staff) AS staffId',
                'COALESCE(SUM(CASE WHEN w.paid = true THEN w.amount ELSE 0 END), 0) AS paidSum',
                'COALESCE(SUM(CASE WHEN w.paid = false THEN w.amount ELSE 0 END), 0) AS unpaidSum'
            )
            ->groupBy('w.staff')
            ->getQuery()
            ->getResult();

        return $this->hydrateStaffRows($rows, static fn (array $r): array => [
            'paid' => (float) $r['paidSum'],
            'unpaid' => (float) $r['unpaidSum'],
        ]);
    }

    /**
     * Turn scalar aggregate rows keyed by staff id into rows carrying the Staff
     * entity, ordered by name.
     *
     * @param array<int, array<string, mixed>>            $rows
     * @param callable(array<string, mixed>): array<mixed> $extra
     *
     * @return array<int, array<string, mixed>>
     */
    private function hydrateStaffRows(array $rows, callable $extra): array
    {
        $ids = array_values(array_filter(array_column($rows, 'staffId')));

        if ([] === $ids) {
            return [];
        }

        $staff = [];
        foreach ($this->getEntityManager()->getRepository(Staff::class)->findBy(['id' => $ids]) as $member) {
            $staff[(int) $member->getId()] = $member;
        }

        $out = [];
        foreach ($rows as $row) {
            $member = $staff[(int) $row['staffId']] ?? null;
            if (null === $member) {
                continue;
            }

            $out[] = ['staff' => $member] + $extra($row);
        }

        usort($out, static fn (array $a, array $b): int => strcmp($a['staff']->getName(), $b['staff']->getName()));

        return $out;
    }

    /** @return StaffWork[] */
    public function recent(int $limit = 10): array
    {
        return $this->createQueryBuilder('w')
            ->orderBy('w.date', 'DESC')
            ->addOrderBy('w.id', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }
}
