<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Party;
use App\Entity\SalesInvoice;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<SalesInvoice>
 */
class SalesInvoiceRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, SalesInvoice::class);
    }

    public function sumTotal(): float
    {
        return (float) $this->createQueryBuilder('s')
            ->select('COALESCE(SUM(s.total), 0)')
            ->getQuery()
            ->getSingleScalarResult();
    }

    /** @return SalesInvoice[] */
    public function recent(int $limit = 10): array
    {
        return $this->createQueryBuilder('s')
            ->leftJoin('s.party', 'p')->addSelect('p')
            ->leftJoin('s.plant', 'pl')->addSelect('pl')
            ->orderBy('s.date', 'DESC')
            ->addOrderBy('s.id', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /** @return SalesInvoice[] */
    public function findAllNewestFirst(): array
    {
        return $this->createQueryBuilder('s')
            ->leftJoin('s.party', 'p')->addSelect('p')
            ->leftJoin('s.plant', 'pl')->addSelect('pl')
            ->orderBy('s.date', 'DESC')
            ->addOrderBy('s.id', 'DESC')
            ->getQuery()
            ->getResult();
    }

    /** @return SalesInvoice[] */
    public function findForParty(Party $party): array
    {
        return $this->createQueryBuilder('s')
            ->andWhere('s.party = :party')
            ->setParameter('party', $party)
            ->orderBy('s.date', 'ASC')
            ->getQuery()
            ->getResult();
    }
}
