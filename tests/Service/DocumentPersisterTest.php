<?php

declare(strict_types=1);

namespace App\Tests\Service;

use App\Entity\SalesInvoice;
use App\Entity\SalesInvoiceLine;
use App\Service\DocumentPersister;
use App\Tests\DomainTestCase;

class DocumentPersisterTest extends DomainTestCase
{
    private function persister(): DocumentPersister
    {
        return $this->service(DocumentPersister::class);
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

    public function testCreatingASalesInvoiceRemovesStockAndAssignsANumber(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $invoice = $this->salesInvoiceFor(100.0);
        $this->persister()->createSalesInvoice($invoice);

        $this->assertSame('SI-0001', $invoice->getNo());
        $this->assertSame(400.0, $this->stockOf($this->fabric, $this->plantA));
        $this->assertEqualsWithDelta(7600.0, $invoice->getTotal(), 0.001);
    }

    public function testSellingMoreThanOnHandStillSavesButWarns(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 10.0);

        $invoice = $this->salesInvoiceFor(100.0);
        $warnings = $this->persister()->createSalesInvoice($invoice);

        $this->assertNotEmpty($warnings, 'A shortfall must warn.');
        $this->assertSame('SI-0001', $invoice->getNo(), 'The sale must still be saved.');
        $this->assertSame(-90.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testReducingAnInvoiceQuantityReturnsTheDifferenceToStock(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $invoice = $this->salesInvoiceFor(100.0);
        $this->persister()->createSalesInvoice($invoice);
        $this->assertSame(400.0, $this->stockOf($this->fabric, $this->plantA));

        // The caller freezes the stored footprint BEFORE mutating the entity.
        $stored = $this->persister()->snapshotOf($invoice);

        $invoice->getLines()->first()->setQty(60.0);
        $this->persister()->updateSalesInvoice($invoice, $stored);

        // 500 - 60. Not 440 (double-applied) and not 340 (never reversed).
        $this->assertSame(440.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testMovingAnInvoiceToAnotherPlantMakesTheOriginalWhole(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);
        $this->seedStock($this->fabric, $this->plantB, 200.0);

        $invoice = $this->salesInvoiceFor(100.0);
        $this->persister()->createSalesInvoice($invoice);

        $stored = $this->persister()->snapshotOf($invoice);
        $invoice->setPlant($this->plantB);
        $this->persister()->updateSalesInvoice($invoice, $stored);

        $this->assertSame(500.0, $this->stockOf($this->fabric, $this->plantA), 'Original plant restored.');
        $this->assertSame(100.0, $this->stockOf($this->fabric, $this->plantB), 'New plant debited.');
    }

    public function testUpdatingDoesNotConsumeASecondDocumentNumber(): void
    {
        $invoice = $this->salesInvoiceFor(10.0);
        $this->persister()->createSalesInvoice($invoice);

        $stored = $this->persister()->snapshotOf($invoice);
        $invoice->getLines()->first()->setQty(20.0);
        $this->persister()->updateSalesInvoice($invoice, $stored);

        $this->assertSame('SI-0001', $invoice->getNo());
    }
}
