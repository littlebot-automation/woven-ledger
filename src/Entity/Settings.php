<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\SettingsRepository;
use Doctrine\ORM\Mapping as ORM;

/**
 * Single-row configuration (id = 1), mirroring the `settings` object the original
 * app kept in localStorage.
 */
#[ORM\Entity(repositoryClass: SettingsRepository::class)]
class Settings
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 180)]
    private string $companyName = 'Your PP Woven Bags Co.';

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $address = null;

    #[ORM\Column(length: 40, nullable: true)]
    private ?string $phone = null;

    #[ORM\Column(length: 20, nullable: true)]
    private ?string $gstin = null;

    #[ORM\Column(length: 10)]
    private string $invoicePrefix = 'SI-';

    #[ORM\Column(length: 10)]
    private string $purchasePrefix = 'PB-';

    #[ORM\Column(length: 10)]
    private string $receiptPrefix = 'RC-';

    #[ORM\Column(length: 10)]
    private string $paymentPrefix = 'PY-';

    #[ORM\Column]
    private int $nextInvoiceNo = 1;

    #[ORM\Column]
    private int $nextPurchaseNo = 1;

    #[ORM\Column]
    private int $nextReceiptNo = 1;

    #[ORM\Column]
    private int $nextPaymentNo = 1;

    #[ORM\Column(type: 'decimal', precision: 14, scale: 3)]
    private string $lowStockDefault = '50.000';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $defaultWage = '500.00';

    #[ORM\ManyToOne(targetEntity: Plant::class)]
    #[ORM\JoinColumn(nullable: true, onDelete: 'SET NULL')]
    private ?Plant $currentPlant = null;

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getCompanyName(): string
    {
        return $this->companyName;
    }

    public function setCompanyName(string $companyName): static
    {
        $this->companyName = $companyName;

        return $this;
    }

    public function getAddress(): ?string
    {
        return $this->address;
    }

    public function setAddress(?string $address): static
    {
        $this->address = $address;

        return $this;
    }

    public function getPhone(): ?string
    {
        return $this->phone;
    }

    public function setPhone(?string $phone): static
    {
        $this->phone = $phone;

        return $this;
    }

    public function getGstin(): ?string
    {
        return $this->gstin;
    }

    public function setGstin(?string $gstin): static
    {
        $this->gstin = $gstin;

        return $this;
    }

    public function getInvoicePrefix(): string
    {
        return $this->invoicePrefix;
    }

    public function setInvoicePrefix(string $v): static
    {
        $this->invoicePrefix = $v;

        return $this;
    }

    public function getPurchasePrefix(): string
    {
        return $this->purchasePrefix;
    }

    public function setPurchasePrefix(string $v): static
    {
        $this->purchasePrefix = $v;

        return $this;
    }

    public function getReceiptPrefix(): string
    {
        return $this->receiptPrefix;
    }

    public function setReceiptPrefix(string $v): static
    {
        $this->receiptPrefix = $v;

        return $this;
    }

    public function getPaymentPrefix(): string
    {
        return $this->paymentPrefix;
    }

    public function setPaymentPrefix(string $v): static
    {
        $this->paymentPrefix = $v;

        return $this;
    }

    public function getNextInvoiceNo(): int
    {
        return $this->nextInvoiceNo;
    }

    public function setNextInvoiceNo(int $v): static
    {
        $this->nextInvoiceNo = $v;

        return $this;
    }

    public function getNextPurchaseNo(): int
    {
        return $this->nextPurchaseNo;
    }

    public function setNextPurchaseNo(int $v): static
    {
        $this->nextPurchaseNo = $v;

        return $this;
    }

    public function getNextReceiptNo(): int
    {
        return $this->nextReceiptNo;
    }

    public function setNextReceiptNo(int $v): static
    {
        $this->nextReceiptNo = $v;

        return $this;
    }

    public function getNextPaymentNo(): int
    {
        return $this->nextPaymentNo;
    }

    public function setNextPaymentNo(int $v): static
    {
        $this->nextPaymentNo = $v;

        return $this;
    }

    public function getLowStockDefault(): float
    {
        return (float) $this->lowStockDefault;
    }

    public function setLowStockDefault(string|float|int|null $v): static
    {
        $this->lowStockDefault = number_format((float) ($v ?? 0), 3, '.', '');

        return $this;
    }

    public function getDefaultWage(): float
    {
        return (float) $this->defaultWage;
    }

    public function setDefaultWage(string|float|int|null $v): static
    {
        $this->defaultWage = number_format((float) ($v ?? 0), 2, '.', '');

        return $this;
    }

    public function getCurrentPlant(): ?Plant
    {
        return $this->currentPlant;
    }

    public function setCurrentPlant(?Plant $currentPlant): static
    {
        $this->currentPlant = $currentPlant;

        return $this;
    }
}
