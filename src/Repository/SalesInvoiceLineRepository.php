<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Item;
use App\Entity\SalesInvoiceLine;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<SalesInvoiceLine>
 */
class SalesInvoiceLineRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, SalesInvoiceLine::class);
    }

    /**
     * Every sale of one item, oldest invoice first — the outbound half of the
     * item's stock history. Quantities come back positive; the caller applies
     * the sign, exactly as DocumentPersister does.
     *
     * @return array<int, array{qty: float, docId: int, docNo: string, docDate: \DateTimeImmutable, plantId: int|null, plantName: string|null}>
     */
    public function findRowsForItem(Item $item): array
    {
        /** @var array<int, array{qty: string, docId: int, docNo: string, docDate: \DateTimeImmutable, plantId: int|null, plantName: string|null}> $rows */
        $rows = $this->createQueryBuilder('l')
            ->select(
                'l.qty AS qty',
                'i.id AS docId',
                'i.no AS docNo',
                'i.date AS docDate',
                'p.id AS plantId',
                'p.name AS plantName',
            )
            ->join('l.invoice', 'i')
            ->leftJoin('i.plant', 'p')
            ->andWhere('l.item = :item')
            ->setParameter('item', $item)
            ->orderBy('i.date', 'ASC')
            ->addOrderBy('i.id', 'ASC')
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
