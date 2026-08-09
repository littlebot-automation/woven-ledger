<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Party;
use App\Entity\PurchaseBill;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<PurchaseBill>
 */
class PurchaseBillRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, PurchaseBill::class);
    }

    public function sumTotal(): float
    {
        return (float) $this->createQueryBuilder('b')
            ->select('COALESCE(SUM(b.total), 0)')
            ->getQuery()
            ->getSingleScalarResult();
    }

    /** @return PurchaseBill[] */
    public function recent(int $limit = 10): array
    {
        return $this->createQueryBuilder('b')
            ->leftJoin('b.party', 'p')->addSelect('p')
            ->leftJoin('b.plant', 'pl')->addSelect('pl')
            ->orderBy('b.date', 'DESC')
            ->addOrderBy('b.id', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /** @return PurchaseBill[] */
    public function findAllNewestFirst(): array
    {
        return $this->createQueryBuilder('b')
            ->leftJoin('b.party', 'p')->addSelect('p')
            ->leftJoin('b.plant', 'pl')->addSelect('pl')
            ->orderBy('b.date', 'DESC')
            ->addOrderBy('b.id', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /** @return PurchaseBill[] */
    public function findForParty(Party $party): array
    {
        return $this->createQueryBuilder('b')
            ->andWhere('b.party = :party')
            ->setParameter('party', $party)
            ->orderBy('b.date', 'ASC')
            ->getQuery()
            ->getResult();
    }
}
