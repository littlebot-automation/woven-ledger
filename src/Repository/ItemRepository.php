<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Item;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Item>
 */
class ItemRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Item::class);
    }

    /** @return Item[] */
    public function search(?string $q = null): array
    {
        $qb = $this->createQueryBuilder('i')->orderBy('i.name', 'ASC');

        if (null !== $q && '' !== trim($q)) {
            $qb->andWhere('LOWER(i.name) LIKE :q OR LOWER(i.hsn) LIKE :q')
                ->setParameter('q', '%'.mb_strtolower(trim($q)).'%');
        }

        return $qb->getQuery()->getResult();
    }

    /**
     * Items with their stock rows eagerly joined — avoids an N+1 when the index
     * renders one quantity column per plant.
     *
     * @return Item[]
     */
    public function findAllWithStock(): array
    {
        return $this->createQueryBuilder('i')
            ->leftJoin('i.stocks', 's')->addSelect('s')
            ->leftJoin('s.plant', 'p')->addSelect('p')
            ->orderBy('i.name', 'ASC')
            ->getQuery()
            ->getResult();
    }
}
