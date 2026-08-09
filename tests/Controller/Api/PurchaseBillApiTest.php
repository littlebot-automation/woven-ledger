<?php

declare(strict_types=1);

namespace App\Tests\Controller\Api;

use App\Tests\ApiTestCase;

/**
 * The sales invoice tests with the stock sign inverted: a bill brings goods in.
 */
class PurchaseBillApiTest extends ApiTestCase
{
    /**
     * @param array<string, mixed> $overrides
     *
     * @return array<string, mixed>
     */
    private function billBody(float $qty, array $overrides = []): array
    {
        $defaults = [
            'party_id' => $this->supplier->getId(),
            'plant_id' => $this->plantA->getId(),
            'bill_date' => '2026-08-09',
            'lines' => [['item_id' => $this->fabric->getId(), 'qty' => $qty, 'rate' => 7000]],
        ];

        // Right-hand keys win with array_merge; the `+` operator would keep the
        // defaults and silently ignore every override.
        return array_merge($defaults, $overrides);
    }

    public function testCreatingABillNumbersItAndAddsStock(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $created = $this->send('POST', '/api/purchase-bills', $this->billBody(100.0));

        $this->assertSame(201, $this->statusCode());
        $this->assertSame('PB-0001', $created['document_number']);
        $this->assertSame(700000, $created['net_amount']);
        $this->assertSame(600.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testEditingAQuantityLeavesStockCorrect(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);

        $created = $this->send('POST', '/api/purchase-bills', $this->billBody(100.0));
        $this->assertSame(600.0, $this->stockOf($this->fabric, $this->plantA));

        $this->send('PUT', '/api/purchase-bills/'.$created['id'], $this->billBody(60.0));

        $this->assertSame(200, $this->statusCode());
        // 500 + 60, with the original 100 taken back out first.
        $this->assertSame(560.0, $this->stockOf($this->fabric, $this->plantA));
    }

    public function testMovingABillToAnotherPlantMovesTheGoodsWithIt(): void
    {
        $this->seedStock($this->fabric, $this->plantA, 500.0);
        $this->seedStock($this->fabric, $this->plantB, 200.0);

        $created = $this->send('POST', '/api/purchase-bills', $this->billBody(100.0));

        $this->send('PUT', '/api/purchase-bills/'.$created['id'], $this->billBody(
            100.0,
            ['plant_id' => $this->plantB->getId()],
        ));

        $this->assertSame(500.0, $this->stockOf($this->fabric, $this->plantA));
        $this->assertSame(300.0, $this->stockOf($this->fabric, $this->plantB));
    }

    public function testABillWithoutASupplierIsRejectedPerField(): void
    {
        $response = $this->send('POST', '/api/purchase-bills', $this->billBody(1.0, ['party_id' => null]));

        $this->assertSame(422, $this->statusCode());
        $this->assertArrayHasKey('party_id', $response['errors']);
    }

    public function testTheReadEndpointCarriesTheLines(): void
    {
        $created = $this->send('POST', '/api/purchase-bills', $this->billBody(8.0));

        $fetched = $this->send('GET', '/api/purchase-bills/'.$created['id']);

        // JSON has no float/int distinction for a whole number, so compare loosely.
        $this->assertEquals(8.0, $fetched['lines'][0]['qty']);
        $this->assertSame(7000, $fetched['lines'][0]['rate']);
    }
}
