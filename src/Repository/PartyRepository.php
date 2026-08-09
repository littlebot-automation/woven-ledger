<?php

declare(strict_types=1);

namespace App\Repository;

use App\Entity\Party;
use App\Entity\Payment;
use App\Entity\PurchaseBill;
use App\Entity\Receipt;
use App\Entity\SalesInvoice;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Party>
 */
class PartyRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Party::class);
    }

    /**
     * Name/phone/GSTIN search plus a type filter where "customer" and "supplier"
     * both also match parties typed as "both".
     *
     * @return Party[]
     */
    public function search(?string $q = null, ?string $type = null): array
    {
        $qb = $this->createQueryBuilder('p')->orderBy('p.name', 'ASC');

        if (null !== $q && '' !== trim($q)) {
            $qb->andWhere('LOWER(p.name) LIKE :q OR LOWER(p.phone) LIKE :q OR LOWER(p.gstin) LIKE :q')
                ->setParameter('q', '%'.mb_strtolower(trim($q)).'%');
        }

        if (null !== $type && '' !== $type && 'all' !== $type) {
            $qb->andWhere('p.type = :type OR p.type = :both')
                ->setParameter('type', $type)
                ->setParameter('both', 'both');
        }

        return $qb->getQuery()->getResult();
    }

    /**
     * Ids of every party referenced by at least one transaction — backs the
     * "Cannot delete: party has transactions." guard in one round trip per type.
     *
     * @return int[]
     */
    public function findIdsWithTransactions(): array
    {
        $em = $this->getEntityManager();
        $ids = [];

        foreach ([SalesInvoice::class, PurchaseBill::class, Receipt::class] as $class) {
            foreach ($em->createQuery(
                \sprintf('SELECT DISTINCT IDENTITY(d.party) AS pid FROM %s d', $class)
            )->getScalarResult() as $row) {
                if (null !== $row['pid']) {
                    $ids[(int) $row['pid']] = true;
                }
            }
        }

        foreach ($em->createQuery(
            \sprintf("SELECT DISTINCT IDENTITY(d.party) AS pid FROM %s d WHERE d.type = 'party'", Payment::class)
        )->getScalarResult() as $row) {
            if (null !== $row['pid']) {
                $ids[(int) $row['pid']] = true;
            }
        }

        return array_keys($ids);
    }

    public function hasTransactions(Party $party): bool
    {
        return \in_array($party->getId(), $this->findIdsWithTransactions(), true);
    }
}
