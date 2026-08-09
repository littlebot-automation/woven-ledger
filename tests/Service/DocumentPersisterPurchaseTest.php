<?php

declare(strict_types=1);

namespace App\Tests\Service;

use App\Entity\PurchaseBill;
use App\Entity\PurchaseBillLine;
use App\Service\DocumentPersister;
use App\Tests\DomainTestCase;

class DocumentPersisterPurchaseTest extends DomainTestCase
{
    private function persister(): DocumentPersister
    {
        return $this->service(DocumentPersister::class);
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

    public function testCreatingAPurchaseBillAddsStockAndAssignsANumber(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $bill = $this->purchaseBillFor(100.0);
        $this->persister()->createPurchaseBill($bill);

        $this->assertSame('PB-0001', $bill->getNo());
        $this->assertSame(600.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testReducingAPurchaseQuantityRemovesTheDifference(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $bill = $this->purchaseBillFor(100.0);
        $this->persister()->createPurchaseBill($bill);
        $this->assertSame(600.0, $this->stockOf($this->fabric, $this->plantA));

        $stored = $this->persister()->snapshotOf($bill);
        $bill->getLines()->first()->setQty(60.0);
        $this->persister()->updatePurchaseBill($bill, $stored);

        $this->assertSame(560.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testDeletingAPurchaseBillRemovesItsStock(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $bill = $this->purchaseBillFor(100.0);
        $this->persister()->createPurchaseBill($bill);

        $this->persister()->deletePurchaseBill($bill);

        $this->assertSame(500.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testSalesAndPurchaseNumbersAdvanceIndependently(): void
    {
        $firstBill = $this->purchaseBillFor(10.0);
        $this->persister()->createPurchaseBill($firstBill);

        $secondBill = $this->purchaseBillFor(10.0);
        $this->persister()->createPurchaseBill($secondBill);

        $this->assertSame('PB-0001', $firstBill->getNo());
        $this->assertSame('PB-0002', $secondBill->getNo());
    }
}
