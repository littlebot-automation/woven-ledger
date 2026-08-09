<?php

declare(strict_types=1);

namespace App\Service;

use App\Entity\PurchaseBill;
use App\Entity\SalesInvoice;
use Doctrine\ORM\EntityManagerInterface;

/**
 * The single owner of what happens when a document is written: numbering and
 * stock movement. Both the admin portal and the mobile API go through here, so
 * the two can never drift apart.
 *
 * Sales remove stock, purchases add it. That sign is the only asymmetry, and it
 * is stated once per document type rather than at each call site.
 */
class DocumentPersister
{
    private const SALES_APPLY = -1;
    private const PURCHASE_APPLY = 1;

    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly DocumentStockManager $stockManager,
        private readonly DocumentNumberService $documentNumbers,
    ) {
    }

    /**
     * @return array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>}
     */
    public function snapshotOf(SalesInvoice|PurchaseBill $document): array
    {
        return $this->stockManager->snapshot($document->getLines(), $document->getPlant());
    }

    /**
     * @return string[] soft stock warnings; never a reason to reject the sale
     */
    public function createSalesInvoice(SalesInvoice $invoice): array
    {
        $this->stockManager->normalise($invoice);
        $invoice->setNo($this->documentNumbers->consume(DocumentNumberService::SALES));

        $this->em->persist($invoice);
        $this->em->flush();

        $warnings = $this->stockManager->salesWarnings($invoice->getLines(), $invoice->getPlant());
        $this->stockManager->applySnapshot($this->snapshotOf($invoice), self::SALES_APPLY);

        return $warnings;
    }
}
