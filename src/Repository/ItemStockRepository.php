<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Item;
use App\Entity\ItemStock;
use App\Entity\Plant;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<ItemStock>
 */
class ItemStockRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, ItemStock::class);
    }

    public function findOneFor(Item $item, Plant $plant): ?ItemStock
    {
        return $this->findOneBy(['item' => $item, 'plant' => $plant]);
    }

    /** @return ItemStock[] */
    public function findForItem(Item $item): array
    {
        return $this->findBy(['item' => $item]);
    }

    /**
     * Every stock row with its item and plant joined, for the stock report.
     *
     * @return ItemStock[]
     */
    public function findAllWithRelations(): array
    {
        return $this->createQueryBuilder('s')
            ->join('s.item', 'i')->addSelect('i')
            ->join('s.plant', 'p')->addSelect('p')
            ->orderBy('i.name', 'ASC')
            ->getQuery()
            ->getResult();
    }
}
