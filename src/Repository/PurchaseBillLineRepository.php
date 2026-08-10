<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Item;
use App\Entity\PurchaseBillLine;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<PurchaseBillLine>
 */
class PurchaseBillLineRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, PurchaseBillLine::class);
    }

    /**
     * Every purchase of one item, oldest bill first — the inbound half of the
     * item's stock history.
     *
     * One query, flat scalars: the history table wants the bill's date, number
     * and plant name, so joining and projecting them here avoids a lazy-load of
     * PurchaseBill (and then Plant) for every line.
     *
     * @return array<int, array{qty: float, docId: int, docNo: string, docDate: \DateTimeImmutable, plantId: int|null, plantName: string|null}>
     */
    public function findRowsForItem(Item $item): array
    {
        /** @var array<int, array{qty: string, docId: int, docNo: string, docDate: \DateTimeImmutable, plantId: int|null, plantName: string|null}> $rows */
        $rows = $this->createQueryBuilder('l')
            ->select(
                'l.qty AS qty',
                'b.id AS docId',
                'b.no AS docNo',
                'b.date AS docDate',
                'p.id AS plantId',
                'p.name AS plantName',
            )
            ->join('l.bill', 'b')
            // Left join: a bill's plant column is nullable, and a line on a
            // plantless bill still happened even if it cannot be attributed.
            ->leftJoin('b.plant', 'p')
            ->andWhere('l.item = :item')
            ->setParameter('item', $item)
            ->orderBy('b.date', 'ASC')
            ->addOrderBy('b.id', 'ASC')
            ->getQuery()
            ->getArrayResult();

        return array_map(static fn (array $row): array => [
            'qty' => (float) $row['qty'],
            'docId' => (int) $row['docId'],
            'docNo' => (string) $row['docNo'],
            'docDate' => $row['docDate'],
            'plantId' => null === $row['plantId'] ? null : (int) $row['plantId'],
            'plantName' => $row['plantName'],
        ], $rows);
    }
}
