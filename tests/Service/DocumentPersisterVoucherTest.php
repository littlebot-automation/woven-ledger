<?php

declare(strict_types=1);

namespace App\Tests\Service;

use App\Entity\Payment;
use App\Entity\Receipt;
use App\Service\DocumentPersister;
use App\Tests\DomainTestCase;

class DocumentPersisterVoucherTest extends DomainTestCase
{
    private function persister(): DocumentPersister
    {
        return $this->service(DocumentPersister::class);
    }

    public function testCreatingAPaymentAssignsAPaymentNumber(): void
    {
        $payment = (new Payment())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setType('party')
            ->setParty($this->supplier)
            ->setAmount(5000.00)
            ->setMode('Cash');

        $this->persister()->createPayment($payment);

        $this->assertSame('PY-0001', $payment->getNo());
    }

    public function testCreatingAReceiptAssignsAReceiptNumber(): void
    {
        $receipt = (new Receipt())
            ->setDate(new \DateTimeImmutable('2026-08-09'))
            ->setParty($this->customer)
            ->setAmount(2500.00)
            ->setMode('UPI');

        $this->persister()->createReceipt($receipt);

        $this->assertSame('RC-0001', $receipt->getNo());
    }
}
