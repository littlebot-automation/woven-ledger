<?php

declare(strict_types=1);

namespace App\Repository;

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
}
