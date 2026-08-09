<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Party;
use App\Entity\Receipt;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Receipt>
 */
class ReceiptRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Receipt::class);
    }

    public function sumAmount(): float
    {
        return (float) $this->createQueryBuilder('r')
            ->select('COALESCE(SUM(r.amount), 0)')
            ->getQuery()
            ->getSingleScalarResult();
    }

    /** @return Receipt[] */
    public function recent(int $limit = 10): array
    {
        return $this->createQueryBuilder('r')
            ->leftJoin('r.party', 'p')->addSelect('p')
            ->orderBy('r.date', 'DESC')
            ->addOrderBy('r.id', 'DESC')
            ->setMaxResults($limit)
            ->getQuery()
            ->getResult();
    }

    /** @return Receipt[] */
    public function findForParty(Party $party): array
    {
        return $this->createQueryBuilder('r')
            ->andWhere('r.party = :party')
            ->setParameter('party', $party)
            ->orderBy('r.date', 'ASC')
            ->getQuery()
            ->getResult();
    }
}
