<?php

declare(strict_types=1);

namespace App\Tests\Controller\Api;

use App\Tests\ApiTestCase;

/**
 * The write path the phone uses for a sale.
 *
 * The case these exist for is the edit: stock on hand already reflects the
 * invoice as it was stored, so changing a quantity must undo the old effect
 * before applying the new one. Getting that wrong is invisible until someone
 * counts the warehouse.
 */
class SalesInvoiceApiTest extends ApiTestCase
{
    /**
     * @param array<string, mixed> $overrides
     *
     * @return array<string, mixed>
     */
    private function invoiceBody(float $qty, array $overrides = []): array
    {
        $defaults = [
            'party_id' => $this->customer->getId(),
            'plant_id' => $this->plantA->getId(),
            'invoice_date' => '2026-08-09',
            // 7600 paise = ₹76.00
            'lines' => [['item_id' => $this->fabric->getId(), 'qty' => $qty, 'rate' => 7600]],
        ];

        // Right-hand keys win with array_merge; the `+` operator would keep the
        // defaults and silently ignore every override.
        return array_merge($defaults, $overrides);
    }

    public function testCreatingAnInvoiceNumbersItAndRemovesStock(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $created = $this->send('POST', '/api/sales-invoices', $this->invoiceBody(100.0));

        $this->assertSame(201, $this->statusCode());
        $this->assertSame('SI-0001', $created['document_number']);
        $this->assertSame(760000, $created['net_amount'], 'Money leaves as integer paise.');
        $this->assertSame([], $created['warnings']);
        $this->assertCount(1, $created['lines']);
        $this->assertSame(7600, $created['lines'][0]['rate']);
        $this->assertSame(400.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testEditingAQuantityLeavesStockCorrect(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $created = $this->send('POST', '/api/sales-invoices', $this->invoiceBody(100.0));
        $this->assertSame(400.0, $this->stockOf($this->fabric, $this->plantA));

        $updated = $this->send('PUT', '/api/sales-invoices/'.$created['id'], $this->invoiceBody(60.0));

        $this->assertSame(200, $this->statusCode());
        $this->assertSame('SI-0001', $updated['document_number'], 'An edit must not burn a number.');
        // 500 - 60. Not 440 - 60 (never reversed) and not 400 + 100 (double undone).
        $this->assertSame(440.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testMovingAnInvoiceToAnotherPlantMakesTheOriginalWhole(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);
        $this->seedStock($this->fabric, $this->plantB, 200.0);

        $created = $this->send('POST', '/api/sales-invoices', $this->invoiceBody(100.0));

        $this->send('PUT', '/api/sales-invoices/'.$created['id'], $this->invoiceBody(
            100.0,
            ['plant_id' => $this->plantB->getId()],
        ));

        $this->assertSame(200, $this->statusCode());
        $this->assertSame(500.0, $this->stockOf($this->fabric, $this->plantA));
        $this->assertSame(100.0, $this->stockOf($this->fabric, $this->plantB));
    }

    public function testRemovingALineReturnsItsGoods(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $created = $this->send('POST', '/api/sales-invoices', [
            'party_id' => $this->customer->getId(),
            'plant_id' => $this->plantA->getId(),
            'lines' => [
                ['item_id' => $this->fabric->getId(), 'qty' => 100.0, 'rate' => 7600],
                ['item_id' => $this->fabric->getId(), 'qty' => 40.0, 'rate' => 7600],
            ],
        ]);

        $this->assertSame(360.0, $this->stockOf($this->fabric, $this->plantA));

        $updated = $this->send('PUT', '/api/sales-invoices/'.$created['id'], $this->invoiceBody(100.0));

        $this->assertCount(1, $updated['lines'], 'The document is replaced, not merged.');
        $this->assertSame(400.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testSellingMoreThanOnHandWarnsButStillSaves(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 10.0);

        $created = $this->send('POST', '/api/sales-invoices', $this->invoiceBody(100.0));

        $this->assertSame(201, $this->statusCode(), 'A shortfall is never a reason to reject a sale.');
        $this->assertNotEmpty($created['warnings']);
        $this->assertSame(-90.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testAnInvoiceWithoutABuyerOrLinesIsRejectedPerField(): void
    {
        $response = $this->send('POST', '/api/sales-invoices', ['plant_id' => $this->plantA->getId()]);

        $this->assertSame(422, $this->statusCode());
        $this->assertArrayHasKey('party_id', $response['errors']);
        $this->assertArrayHasKey('lines', $response['errors']);
    }

    public function testAnUnknownItemOnALineIsRejected(): void
    {
        $response = $this->send('POST', '/api/sales-invoices', $this->invoiceBody(1.0, [
            'lines' => [['item_id' => 9999, 'qty' => 1.0, 'rate' => 100]],
        ]));

        $this->assertSame(422, $this->statusCode());
        $this->assertStringContainsString('Line 1', $response['errors']['lines']);
    }

    public function testTheDiscountArrivesAndLeavesAsPaise(): void
    {
        $created = $this->send('POST', '/api/sales-invoices', $this->invoiceBody(10.0, [
            // ₹100.00 off ₹760.00
            'discount_amount' => 10000,
        ]));

        $this->assertSame(76000, $created['total_amount']);
        $this->assertSame(10000, $created['discount_amount']);
        $this->assertSame(66000, $created['net_amount']);
    }

    public function testTheReadEndpointsCarryTheLines(): void
    {
        $created = $this->send('POST', '/api/sales-invoices', $this->invoiceBody(12.0));

        $fetched = $this->send('GET', '/api/sales-invoices/'.$created['id']);
        $this->assertSame(200, $this->statusCode());
        // JSON has no float/int distinction for a whole number, so compare loosely.
        $this->assertEquals(12.0, $fetched['lines'][0]['qty']);
        $this->assertSame($this->plantA->getId(), $fetched['plant_id']);

        $listed = $this->send('GET', '/api/sales-invoices');
        $this->assertCount(1, $listed[0]['lines']);
    }

    public function testEditingAnInvoiceThatIsNotThereIs404(): void
    {
        $this->send('PUT', '/api/sales-invoices/9999', $this->invoiceBody(1.0));

        $this->assertSame(404, $this->statusCode());
    }
}
