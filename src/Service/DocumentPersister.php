<?php

declare(strict_types=1);

namespace App\Service;

use App\Entity\Payment;
use App\Entity\PurchaseBill;
use App\Entity\Receipt;
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
        // Reading, incrementing and committing the counter must be atomic, or two
        // simultaneous creates can be handed the same number — and `no` is unique.
        $this->em->wrapInTransaction(function () use ($invoice): void {
            $this->stockManager->normalise($invoice);
            $invoice->setNo($this->documentNumbers->consume(DocumentNumberService::SALES));
            $this->em->persist($invoice);
        });

        $warnings = $this->stockManager->salesWarnings($invoice->getLines(), $invoice->getPlant());
        $this->stockManager->applySnapshot($this->snapshotOf($invoice), self::SALES_APPLY);

        return $warnings;
    }

    /**
     * @param array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>} $storedSnapshot
     *                                                                                                   taken before the caller mutated the document
     *
     * @return string[]
     */
    public function updateSalesInvoice(SalesInvoice $invoice, array $storedSnapshot): array
    {
        $this->stockManager->normalise($invoice);
        $this->em->flush();

        $warnings = $this->stockManager->salesWarnings(
            $invoice->getLines(),
            $invoice->getPlant(),
            $storedSnapshot,
        );

        // Undo the stored effect at the stored plant, then apply the new one.
        $this->stockManager->applySnapshot($storedSnapshot, -self::SALES_APPLY);
        $this->stockManager->applySnapshot($this->snapshotOf($invoice), self::SALES_APPLY);

        return $warnings;
    }

    public function deleteSalesInvoice(SalesInvoice $invoice): void
    {
        // Freeze before removal: once the entity is gone its lines are unreadable.
        $snapshot = $this->snapshotOf($invoice);

        $this->em->remove($invoice);
        $this->em->flush();

        $this->stockManager->applySnapshot($snapshot, -self::SALES_APPLY);
    }

    public function createPurchaseBill(PurchaseBill $bill): void
    {
        $this->em->wrapInTransaction(function () use ($bill): void {
            $this->stockManager->normalise($bill);
            $bill->setNo($this->documentNumbers->consume(DocumentNumberService::PURCHASE));
            $this->em->persist($bill);
        });

        $this->stockManager->applySnapshot($this->snapshotOf($bill), self::PURCHASE_APPLY);
    }

    /**
     * @param array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>} $storedSnapshot
     */
    public function updatePurchaseBill(PurchaseBill $bill, array $storedSnapshot): void
    {
        $this->stockManager->normalise($bill);
        $this->em->flush();

        $this->stockManager->applySnapshot($storedSnapshot, -self::PURCHASE_APPLY);
        $this->stockManager->applySnapshot($this->snapshotOf($bill), self::PURCHASE_APPLY);
    }

    public function deletePurchaseBill(PurchaseBill $bill): void
    {
        $snapshot = $this->snapshotOf($bill);

        $this->em->remove($bill);
        $this->em->flush();

        $this->stockManager->applySnapshot($snapshot, -self::PURCHASE_APPLY);
    }

    public function createReceipt(Receipt $receipt): void
    {
        $this->em->wrapInTransaction(function () use ($receipt): void {
            $receipt->setNo($this->documentNumbers->consume(DocumentNumberService::RECEIPT));
            $this->em->persist($receipt);
        });
    }

    public function createPayment(Payment $payment): void
    {
        $this->em->wrapInTransaction(function () use ($payment): void {
            $payment->setNo($this->documentNumbers->consume(DocumentNumberService::PAYMENT));
            $this->em->persist($payment);
        });
    }
}
