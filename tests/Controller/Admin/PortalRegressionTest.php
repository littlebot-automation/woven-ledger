<?php

declare(strict_types=1);

namespace App\Tests\Controller\Admin;

use App\Controller\Admin\SalesInvoiceCrudController;
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
    private function freezeStoredSnapshot(object $controller, SalesInvoice|\App\Entity\PurchaseBill $document): void
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
}
