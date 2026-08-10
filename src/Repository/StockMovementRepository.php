<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Item;
use App\Entity\StockMovement;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<StockMovement>
 */
class StockMovementRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, StockMovement::class);
    }

    /**
     * Every recorded movement for one item, oldest first, as flat scalars.
     *
     * Scalar rows rather than entities: the history table needs the plant *name*
     * and nothing else off the association, so hydrating StockMovement objects
     * would only invite a lazy-load per row.
     *
     * @return array<int, array{qty: float, plantId: int, plantName: string, movedAt: \DateTimeImmutable, reason: string, source: string}>
     */
    public function findRowsForItem(Item $item): array
    {
        /** @var array<int, array{qty: string, plantId: int, plantName: string, movedAt: \DateTimeImmutable, reason: string, source: string}> $rows */
        $rows = $this->createQueryBuilder('m')
            ->select(
                'm.qtyDelta AS qty',
                'p.id AS plantId',
                'p.name AS plantName',
                'm.recordedAt AS movedAt',
                'm.reason AS reason',
                'm.source AS source',
            )
            ->join('m.plant', 'p')
            ->andWhere('m.item = :item')
            ->setParameter('item', $item)
            ->orderBy('m.recordedAt', 'ASC')
            ->addOrderBy('m.id', 'ASC')
            ->getQuery()
            ->getArrayResult();

        return array_map(static fn (array $row): array => [
            'qty' => (float) $row['qty'],
            'plantId' => (int) $row['plantId'],
            'plantName' => (string) $row['plantName'],
            'movedAt' => $row['movedAt'],
            'reason' => (string) $row['reason'],
            'source' => (string) $row['source'],
        ], $rows);
    }
}
