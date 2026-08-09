<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Party;
use App\Entity\Payment;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Payment>
 */
class PaymentRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Payment::class);
    }

    public function sumAmount(): float
    {
        return (float) $this->createQueryBuilder('p')
            ->select('COALESCE(SUM(p.amount), 0)')
            ->getQuery()
            ->getSingleScalarResult();
    }

    /** @return Payment[] */
    public function recent(int $limit = 10): array
    {
        return $this->createQueryBuilder('p')
            ->leftJoin('p.party', 'pa')->addSelect('pa')
            ->leftJoin('p.staff', 'st')->addSelect('st')
            ->orderBy('p.date', 'DESC')
            ->addOrderBy('p.id', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /**
     * Only party-directed payments belong in a party ledger — wage payments are
     * excluded by the type filter here, not by the caller.
     *
     * @return Payment[]
     */
    public function findPartyPayments(Party $party): array
    {
        return $this->createQueryBuilder('p')
            ->andWhere('p.party = :party')
            ->andWhere("p.type = 'party'")
            ->setParameter('party', $party)
            ->orderBy('p.date', 'ASC')
            ->getQuery()
            ->getResult();
    }
}
