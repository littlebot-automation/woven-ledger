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
}
