<?php

declare(strict_types=1);

namespace App\Tests\Controller\Admin;

use App\Controller\Admin\PaymentCrudController;
use App\Controller\Admin\PurchaseBillCrudController;
use App\Controller\Admin\ReceiptCrudController;
use App\Controller\Admin\SalesInvoiceCrudController;
use App\Entity\Payment;
use App\Entity\PurchaseBill;
use App\Entity\PurchaseBillLine;
use App\Entity\Receipt;
use App\Entity\SalesInvoice;
use App\Entity\SalesInvoiceLine;
use App\Service\DocumentPersister;
use App\Tests\DomainTestCase;

/**
 * The automated stand-in for the plan's "regression-test the portal by hand" steps.
 *
 * These drive the EasyAdmin lifecycle hooks themselves — persistEntity(),
 * updateEntity(), deleteEntity() — rather than the service underneath them, so they
 * prove the refactored controllers still produce the numbering and stock results
 * that Tasks 3-7 pinned down.
 *
 * Two things a browser would do for us have to be done by hand here:
 *  - Quantities stay within stock, because a shortfall reaches addFlash(), which
 *    needs a live request and session the kernel has no reason to provide.
 *  - The stored snapshot is seeded through reflection. In the portal that
 *    assignment happens inside edit(), which cannot be called without a fully
 *    built AdminContext; the value seeded here is the identical expression the
 *    hook uses, $documentPersister->snapshotOf($document).
 */
class PortalRegressionTest extends DomainTestCase
{
    private function persister(): DocumentPersister
    {
        return $this->service(DocumentPersister::class);
    }

    /**
     * Stand in for the edit() hook, which freezes the stored footprint before the
     * submitted form data overwrites the entity.
     */
    private function freezeStoredSnapshot(object $controller, SalesInvoice|PurchaseBill $document): void
    {
        $property = new \ReflectionProperty($controller, 'storedSnapshot');
        $property->setValue($controller, $this->persister()->snapshotOf($document));
    }

    private function salesInvoiceFor(float $qty, float $rate = 76.0): SalesInvoice
    {
        $line = (new SalesInvoiceLine())
            ->setItem($this->fabric)
            ->setQty($qty)
            ->setRate($rate);

        return (new SalesInvoice())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setParty($this->customer)
            ->setPlant($this->plantA)
            ->addLine($line);
    }

    public function testTheSalesInvoiceLifecycleThroughTheControllerIsUnchanged(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $controller = $this->service(SalesInvoiceCrudController::class);

        // 1. Create for a known quantity: the number is assigned and stock drops by
        //    exactly that quantity.
        $invoice = $this->salesInvoiceFor(100.0);
        $controller->persistEntity($this->em, $invoice);

        $this->assertSame('SI-0001', $invoice->getNo());
        $this->assertSame(400.0, $this->stockOf($this->fabric, $this->plantA));

        // 2. Edit down: stock reflects the new quantity, not a double application.
        $this->freezeStoredSnapshot($controller, $invoice);
        $invoice->getLines()->first()->setQty(60.0);
        $controller->updateEntity($this->em, $invoice);

        $this->assertSame(440.0, $this->stockOf($this->fabric, $this->plantA));
        $this->assertSame('SI-0001', $invoice->getNo(), 'Editing must not burn a number.');

        // 3. Delete: stock returns to its starting value.
        $controller->deleteEntity($this->em, $invoice);

        $this->assertSame(500.0, $this->stockOf($this->fabric, $this->plantA));
    }

    private function purchaseBillFor(float $qty, float $rate = 70.0): PurchaseBill
    {
        $line = (new PurchaseBillLine())
            ->setItem($this->fabric)
            ->setQty($qty)
            ->setRate($rate);

        return (new PurchaseBill())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setParty($this->supplier)
            ->setPlant($this->plantA)
            ->addLine($line);
    }

    public function testThePurchaseBillLifecycleThroughTheControllerIsUnchanged(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $controller = $this->service(PurchaseBillCrudController::class);

        // Create: stock rises by the received quantity.
        $bill = $this->purchaseBillFor(100.0);
        $controller->persistEntity($this->em, $bill);

        $this->assertSame('PB-0001', $bill->getNo());
        $this->assertSame(600.0, $this->stockOf($this->fabric, $this->plantA));

        // Edit down: the difference is removed, not the whole bill.
        $this->freezeStoredSnapshot($controller, $bill);
        $bill->getLines()->first()->setQty(60.0);
        $controller->updateEntity($this->em, $bill);

        $this->assertSame(560.0, $this->stockOf($this->fabric, $this->plantA));
        $this->assertSame('PB-0001', $bill->getNo(), 'Editing must not burn a number.');

        // Delete: stock returns to its starting value.
        $controller->deleteEntity($this->em, $bill);

        $this->assertSame(500.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testReceiptAndPaymentControllersStillNumberSequentially(): void
    {
        $receipts = $this->service(ReceiptCrudController::class);
        $payments = $this->service(PaymentCrudController::class);

        $firstReceipt = (new Receipt())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setParty($this->customer)
            ->setAmount(2500.00)
            ->setMode('UPI');
        $receipts->persistEntity($this->em, $firstReceipt);

        $secondReceipt = (new Receipt())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setParty($this->customer)
            ->setAmount(1500.00)
            ->setMode('Cash');
        $receipts->persistEntity($this->em, $secondReceipt);

        $payment = (new Payment())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setType('party')
            ->setParty($this->supplier)
            ->setAmount(5000.00)
            ->setMode('Cash');
        $payments->persistEntity($this->em, $payment);

        $this->assertSame('RC-0001', $firstReceipt->getNo());
        $this->assertSame('RC-0002', $secondReceipt->getNo());
        $this->assertSame('PY-0001', $payment->getNo(), 'Payment numbering is independent of receipts.');
    }
}
