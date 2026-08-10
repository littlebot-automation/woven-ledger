<?php

declare(strict_types=1);

namespace App\Tests\Service;

use App\Entity\PurchaseBill;
use App\Entity\PurchaseBillLine;
use App\Entity\SalesInvoice;
use App\Entity\SalesInvoiceLine;
use App\Entity\StockMovement;
use App\Service\DocumentPersister;
use App\Service\StockHistoryService;
use App\Service\StockService;
use App\Tests\DomainTestCase;

/**
 * The history is reconstructed, not stored, so these tests pin down the two things
 * that can go wrong with a reconstruction: a source counted twice, and a source
 * missing altogether. The last of them is the important one — it asserts that the
 * table still closes on the real quantity even when part of it is unexplainable.
 */
class StockHistoryServiceTest extends DomainTestCase
{
    private function history(): StockHistoryService
    {
        return $this->service(StockHistoryService::class);
    }

    private function persister(): DocumentPersister
    {
        return $this->service(DocumentPersister::class);
    }

    private function stock(): StockService
    {
        return $this->service(StockService::class);
    }

    private function buyFabric(float $qty, string $date = '2026-08-01'): PurchaseBill
    {
        $bill = (new PurchaseBill())
            ->setDate(new \DateTimeImmutable($date))
            ->setParty($this->supplier)
            ->setPlant($this->plantA)
            ->addLine((new PurchaseBillLine())->setItem($this->fabric)->setQty($qty)->setRate(70.0));

        $this->persister()->createPurchaseBill($bill);

        return $bill;
    }

    private function sellFabric(float $qty, string $date = '2026-08-05'): SalesInvoice
    {
        $invoice = (new SalesInvoice())
            ->setDate(new \DateTimeImmutable($date))
            ->setParty($this->customer)
            ->setPlant($this->plantA)
            ->addLine((new SalesInvoiceLine())->setItem($this->fabric)->setQty($qty)->setRate(76.0));

        $this->persister()->createSalesInvoice($invoice);

        return $invoice;
    }

    public function testAnItemThatHasNeverMovedHasAnEmptyHistory(): void
    {
        $history = $this->history()->getHistory($this->fabric);

        $this->assertSame([], $history['entries']);
        $this->assertSame(0.0, $history['closing']);
        $this->assertSame(0.0, $history['unaccounted']);
    }

    public function testAPurchaseBillLineIsReadBackAsStockIn(): void
    {
        $bill = $this->buyFabric(120.0);

        $history = $this->history()->getHistory($this->fabric);

        $this->assertCount(1, $history['entries']);
        $entry = $history['entries'][0];

        $this->assertSame('Purchase Bill', $entry['type']);
        $this->assertSame('purchase', $entry['kind']);
        $this->assertSame($bill->getNo(), $entry['ref']);
        $this->assertSame($bill->getId(), $entry['documentId']);
        $this->assertSame('Plant A', $entry['plant']);
        $this->assertSame(120.0, $entry['qty']);
        $this->assertSame(120.0, $entry['balance']);

        // Nothing is missing, so no reconciling row is needed.
        $this->assertSame(0.0, $history['unaccounted']);
        $this->assertSame(120.0, $history['closing']);
    }

    public function testASalesInvoiceLineIsReadBackAsStockOut(): void
    {
        $this->buyFabric(120.0);
        $invoice = $this->sellFabric(45.0);

        $history = $this->history()->getHistory($this->fabric);

        $this->assertCount(2, $history['entries']);
        $entry = $history['entries'][1];

        $this->assertSame('Sales Invoice', $entry['type']);
        $this->assertSame('sales', $entry['kind']);
        $this->assertSame($invoice->getNo(), $entry['ref']);
        $this->assertSame(-45.0, $entry['qty']);

        // Oldest first, and the balance runs down the column.
        $this->assertSame([120.0, 75.0], array_column($history['entries'], 'balance'));
        $this->assertSame(75.0, $history['closing']);
        $this->assertSame(0.0, $history['unaccounted']);
    }

    public function testAManualAdjustmentIsRecordedAndAppearsInTheHistory(): void
    {
        $this->buyFabric(100.0);
        $this->stock()->adjustManually($this->fabric, $this->plantA, -6.0, 'Wastage');

        $rows = $this->em->getRepository(StockMovement::class)->findAll();
        $this->assertCount(1, $rows, 'the manual path must leave exactly one audit row');
        $this->assertSame(-6.0, $rows[0]->getQtyDelta());
        $this->assertSame('Wastage', $rows[0]->getReason());
        $this->assertSame(StockMovement::SOURCE_MANUAL, $rows[0]->getSource());

        $history = $this->history()->getHistory($this->fabric);

        $this->assertCount(2, $history['entries']);
        $entry = $history['entries'][1];

        $this->assertSame('Manual Adjustment', $entry['type']);
        $this->assertSame('manual', $entry['kind']);
        $this->assertSame('Wastage', $entry['ref']);
        $this->assertNull($entry['documentId']);
        $this->assertSame(-6.0, $entry['qty']);

        // Recorded, therefore explained: the audited adjustment closes the gap it opens.
        $this->assertSame(0.0, $history['unaccounted']);
        $this->assertSame(94.0, $history['closing']);
        $this->assertSame(94.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testAnAbsoluteManualSetIsRecordedAsTheDifferenceItMade(): void
    {
        $this->buyFabric(100.0);
        $this->stock()->setQtyManually($this->fabric, $this->plantA, 80.0);

        $entries = $this->history()->getHistory($this->fabric)['entries'];

        $this->assertSame(-20.0, $entries[1]['qty']);
        $this->assertSame('Set to 80 (was 100)', $entries[1]['ref']);
    }

    public function testDocumentsDoNotAlsoWriteMovementRows(): void
    {
        $this->buyFabric(100.0);
        $this->sellFabric(30.0);

        // If DocumentStockManager's calls were audited too, every document would be
        // counted once from its lines and once from a StockMovement row.
        $this->assertCount(0, $this->em->getRepository(StockMovement::class)->findAll());
        $this->assertSame(70.0, $this->history()->getHistory($this->fabric)['closing']);
    }

    public function testStockMovedWithoutAnyRecordSurfacesAsAnOpeningGap(): void
    {
        // seedStock() goes through the unaudited setQty() — exactly what every
        // manual adjustment did before StockMovement existed.
        $this->seedStock($this->fabric, $this->plantA, 500.0);
        $this->sellFabric(40.0);

        $history = $this->history()->getHistory($this->fabric);

        $this->assertSame(500.0, $history['unaccounted']);

        $opening = $history['entries'][0];
        $this->assertSame('opening', $opening['kind']);
        $this->assertSame('Opening / unaccounted', $opening['type']);
        $this->assertNull($opening['date'], 'nothing records when the untracked change happened');
        $this->assertSame('Plant A', $opening['plant']);
        $this->assertSame(500.0, $opening['qty']);

        // The point of the reconciling row: the table adds up to the real figure.
        $this->assertSame(460.0, $history['closing']);
        $this->assertSame($history['current'], $history['closing']);
        $this->assertSame(460.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testTheGapIsReconciledPerPlant(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);
        $this->seedStock($this->fabric, $this->plantB, 200.0);
        $this->sellFabric(40.0);

        $history = $this->history()->getHistory($this->fabric);

        $opening = array_values(array_filter(
            $history['entries'],
            static fn (array $e): bool => 'opening' === $e['kind'],
        ));

        $this->assertCount(2, $opening, 'one reconciling row per plant that needs one');
        $this->assertSame(
            ['Plant A' => 500.0, 'Plant B' => 200.0],
            array_combine(array_column($opening, 'plant'), array_column($opening, 'qty')),
        );

        $this->assertSame(660.0, $history['closing']);
        $this->assertSame($history['current'], $history['closing']);
    }
}
