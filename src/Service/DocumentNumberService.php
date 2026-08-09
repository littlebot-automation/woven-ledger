<?php

declare(strict_types=1);

namespace App\Service;

use App\Repository\SettingsRepository;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Document numbering — spec §4.1.
 *
 * Numbers are prefix + a zero-padded 4-digit counter (SI-0001). The counter is
 * consumed only when a brand-new document is saved; editing an existing document
 * must never burn a number.
 */
class DocumentNumberService
{
    public const SALES = 'sales';
    public const PURCHASE = 'purchase';
    public const RECEIPT = 'receipt';
    public const PAYMENT = 'payment';

    public function __construct(
        private readonly SettingsRepository $settingsRepository,
        private readonly EntityManagerInterface $em,
    ) {
    }

    /** The number a new document of this kind would receive, without consuming it. */
    public function peek(string $kind): string
    {
        $settings = $this->settingsRepository->getSettings();

        [$prefix, $counter] = match ($kind) {
            self::SALES => [$settings->getInvoicePrefix(), $settings->getNextInvoiceNo()],
            self::PURCHASE => [$settings->getPurchasePrefix(), $settings->getNextPurchaseNo()],
            self::RECEIPT => [$settings->getReceiptPrefix(), $settings->getNextReceiptNo()],
            self::PAYMENT => [$settings->getPaymentPrefix(), $settings->getNextPaymentNo()],
            default => throw new \InvalidArgumentException(\sprintf('Unknown document kind "%s".', $kind)),
        };

        return $prefix.str_pad((string) $counter, 4, '0', \STR_PAD_LEFT);
    }

    /**
     * Take the next number AND advance the counter. Call this exactly once, when
     * a new document is persisted.
     */
    public function consume(string $kind): string
    {
        $no = $this->peek($kind);
        $settings = $this->settingsRepository->getSettings();

        match ($kind) {
            self::SALES => $settings->setNextInvoiceNo($settings->getNextInvoiceNo() + 1),
            self::PURCHASE => $settings->setNextPurchaseNo($settings->getNextPurchaseNo() + 1),
            self::RECEIPT => $settings->setNextReceiptNo($settings->getNextReceiptNo() + 1),
            self::PAYMENT => $settings->setNextPaymentNo($settings->getNextPaymentNo() + 1),
            default => throw new \InvalidArgumentException(\sprintf('Unknown document kind "%s".', $kind)),
        };

        $this->em->flush();

        return $no;
    }
}
