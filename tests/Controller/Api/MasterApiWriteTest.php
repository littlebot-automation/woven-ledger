<?php

declare(strict_types=1);

namespace App\Tests\Controller\Api;

use App\Tests\ApiTestCase;

/**
 * Parties, receipts, payments and staff work: the writes that carry no stock.
 *
 * What they must still get right is numbering (consumed once, on create, never on
 * edit), the paise boundary, and rejecting an unknown enum value using the
 * entity's own allow-list rather than a restated copy of it.
 */
class MasterApiWriteTest extends ApiTestCase
{
    // ------------------------------------------------------------ Parties

    public function testCreatingAndEditingAParty(): void
    {
        $created = $this->send('POST', '/api/parties', [
            'name' => 'Bharat Polybags',
            'type' => 'both',
            'phone' => '9876543210',
            'gst_number' => '24AAACB1234C1ZZ',
            // ₹1,250.00 opening, owed to us
            'opening_balance' => 125000,
            'opening_balance_type' => 'to_receive',
        ]);

        $this->assertSame(201, $this->statusCode());
        $this->assertSame('Bharat Polybags', $created['name']);
        $this->assertSame(125000, $created['opening_balance']);
        $this->assertSame(125000, $created['ledger_balance']);

        $updated = $this->send('PUT', '/api/parties/'.$created['id'], [
            'name' => 'Bharat Polybags Pvt Ltd',
            'type' => 'supplier',
            'opening_balance' => 0,
        ]);

        $this->assertSame(200, $this->statusCode());
        $this->assertSame('Bharat Polybags Pvt Ltd', $updated['name']);
        $this->assertSame('supplier', $updated['type']);
        $this->assertSame(0, $updated['opening_balance']);
    }

    public function testAPartyNeedsANameAndAKnownType(): void
    {
        $response = $this->send('POST', '/api/parties', ['name' => '  ', 'type' => 'wholesaler']);

        $this->assertSame(422, $this->statusCode());
        $this->assertArrayHasKey('name', $response['errors']);
        // The message is built from Party::TYPES, so it lists what is really allowed.
        $this->assertStringContainsString('customer', $response['errors']['type']);
    }

    // ------------------------------------------------------------ Receipts

    public function testCreatingAndEditingAReceipt(): void
    {
        $created = $this->send('POST', '/api/receipts', [
            'party_id' => $this->customer->getId(),
            'amount' => 500000,
            'receipt_date' => '2026-08-09',
            'mode' => 'UPI',
        ]);

        $this->assertSame(201, $this->statusCode());
        $this->assertSame('RC-0001', $created['document_number']);
        $this->assertSame(500000, $created['amount']);

        $updated = $this->send('PUT', '/api/receipts/'.$created['id'], [
            'party_id' => $this->customer->getId(),
            'amount' => 450000,
            'mode' => 'Cash',
        ]);

        $this->assertSame(200, $this->statusCode());
        $this->assertSame('RC-0001', $updated['document_number'], 'An edit must not burn a number.');
        $this->assertSame(450000, $updated['amount']);
    }

    public function testAReceiptNeedsAPositiveAmountAndAKnownMode(): void
    {
        $response = $this->send('POST', '/api/receipts', [
            'party_id' => $this->customer->getId(),
            'amount' => 0,
            'mode' => 'Barter',
        ]);

        $this->assertSame(422, $this->statusCode());
        $this->assertArrayHasKey('amount', $response['errors']);
        $this->assertArrayHasKey('mode', $response['errors']);
    }

    // ------------------------------------------------------------ Payments

    public function testCreatingAPaymentToAParty(): void
    {
        $created = $this->send('POST', '/api/payments', [
            'party_id' => $this->supplier->getId(),
            'amount' => 250000,
            'payment_date' => '2026-08-09',
            'payment_type' => 'party',
            'mode' => 'Bank Transfer',
        ]);

        $this->assertSame(201, $this->statusCode());
        $this->assertSame('PY-0001', $created['document_number']);
        $this->assertSame($this->supplier->getId(), $created['party_id']);
        $this->assertNull($created['staff_id']);
    }

    public function testAWagePaymentNeedsAStaffMemberRatherThanAParty(): void
    {
        $rejected = $this->send('POST', '/api/payments', [
            'amount' => 250000,
            'payment_type' => 'staff',
        ]);

        $this->assertSame(422, $this->statusCode());
        $this->assertArrayHasKey('staff_id', $rejected['errors']);

        $created = $this->send('POST', '/api/payments', [
            'amount' => 250000,
            'payment_type' => 'staff',
            'staff_id' => $this->weaver->getId(),
        ]);

        $this->assertSame(201, $this->statusCode());
        $this->assertSame($this->weaver->getId(), $created['staff_id']);
        $this->assertNull($created['party_id']);
    }

    public function testSwitchingAPaymentFromAPartyToStaffClearsTheOtherSide(): void
    {
        $created = $this->send('POST', '/api/payments', [
            'party_id' => $this->supplier->getId(),
            'amount' => 100000,
            'payment_type' => 'party',
        ]);

        $updated = $this->send('PUT', '/api/payments/'.$created['id'], [
            'amount' => 100000,
            'payment_type' => 'staff',
            'staff_id' => $this->weaver->getId(),
        ]);

        $this->assertSame(200, $this->statusCode());
        $this->assertNull($updated['party_id'], 'A voucher must not point at both sides at once.');
        $this->assertSame($this->weaver->getId(), $updated['staff_id']);
    }

    // ------------------------------------------------------------ Staff work

    public function testEditingAWorkEntryRecomputesItsAmount(): void
    {
        $created = $this->send('POST', '/api/staff-work', [
            'staff_id' => $this->weaver->getId(),
            'plant_id' => $this->plantA->getId(),
            'work_type' => 'Stitching',
            'qty' => 2.0,
            'rate' => 50000,
        ]);

        $this->assertSame(201, $this->statusCode());
        $this->assertSame(100000, $created['amount']);

        $updated = $this->send('PUT', '/api/staff-work/'.$created['id'], [
            'staff_id' => $this->weaver->getId(),
            'work_type' => 'Loading',
            'qty' => 3.0,
            'rate' => 50000,
        ]);

        $this->assertSame(200, $this->statusCode());
        $this->assertSame('Loading', $updated['work_type']);
        $this->assertSame(150000, $updated['amount']);
        $this->assertNull($updated['plant_id'], 'Omitting the plant clears it, as "None" does on the portal.');
    }

    public function testEditingAWorkEntryWithoutAQtyIsRejected(): void
    {
        $created = $this->send('POST', '/api/staff-work', [
            'staff_id' => $this->weaver->getId(),
            'work_type' => 'Stitching',
            'qty' => 2.0,
        ]);

        $response = $this->send('PUT', '/api/staff-work/'.$created['id'], [
            'staff_id' => $this->weaver->getId(),
            'work_type' => 'Stitching',
        ]);

        $this->assertSame(422, $this->statusCode());
        $this->assertArrayHasKey('qty', $response['errors']);
    }

    // ------------------------------------------------------------ Plants

    public function testPlantsAreListedForTheDocumentForms(): void
    {
        $plants = $this->send('GET', '/api/plants');

        $this->assertSame(200, $this->statusCode());
        $this->assertSame(['Plant A', 'Plant B'], array_column($plants, 'name'));
    }
}
