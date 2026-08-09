<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\SalesInvoiceLineRepository;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: SalesInvoiceLineRepository::class)]
class SalesInvoiceLine
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: SalesInvoice::class, inversedBy: 'lines')]
    #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
    private ?SalesInvoice $invoice = null;

    #[ORM\ManyToOne(targetEntity: Item::class)]
    #[ORM\JoinColumn(nullable: true)]
    private ?Item $item = null;

    #[ORM\Column(type: 'decimal', precision: 14, scale: 3)]
    private string $qty = '0.000';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $rate = '0.00';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $amount = '0.00';

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getInvoice(): ?SalesInvoice
    {
        return $this->invoice;
    }

    public function setInvoice(?SalesInvoice $invoice): static
    {
        $this->invoice = $invoice;

        return $this;
    }

    public function getItem(): ?Item
    {
        return $this->item;
    }

    public function setItem(?Item $item): static
    {
        $this->item = $item;

        return $this;
    }

    public function getQty(): float
    {
        return (float) $this->qty;
    }

    public function setQty(string|float|int|null $v): static
    {
        $this->qty = number_format((float) ($v ?? 0), 3, '.', '');
        $this->recalculateAmount();

        return $this;
    }

    public function getRate(): float
    {
        return (float) $this->rate;
    }

    public function setRate(string|float|int|null $v): static
    {
        $this->rate = number_format((float) ($v ?? 0), 2, '.', '');
        $this->recalculateAmount();

        return $this;
    }

    public function getAmount(): float
    {
        return (float) $this->amount;
    }

    public function setAmount(string|float|int|null $v): static
    {
        $this->amount = number_format((float) ($v ?? 0), 2, '.', '');

        return $this;
    }

    public function recalculateAmount(): void
    {
        $this->amount = number_format($this->getQty() * $this->getRate(), 2, '.', '');
    }

    /** A line only counts if it names an item and carries a positive quantity. */
    public function isValid(): bool
    {
        return null !== $this->item && $this->getQty() > 0;
    }
}
