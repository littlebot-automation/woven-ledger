<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\PurchaseBillRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

/**
 * Mirror of {@see SalesInvoice}: same shape, opposite stock direction, and the
 * party is the supplier rather than the buyer.
 */
#[ORM\Entity(repositoryClass: PurchaseBillRepository::class)]
class PurchaseBill implements \Stringable
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 30, unique: true)]
    private string $no = '';

    #[ORM\Column(type: 'date_immutable')]
    private \DateTimeImmutable $date;

    #[ORM\ManyToOne(targetEntity: Party::class)]
    #[ORM\JoinColumn(nullable: false)]
    #[Assert\NotNull(message: 'Select a supplier')]
    private ?Party $party = null;

    #[ORM\ManyToOne(targetEntity: Plant::class)]
    #[ORM\JoinColumn(nullable: false)]
    private ?Plant $plant = null;

    /** @var Collection<int, PurchaseBillLine> */
    #[ORM\OneToMany(mappedBy: 'bill', targetEntity: PurchaseBillLine::class, cascade: ['persist', 'remove'], orphanRemoval: true)]
    private Collection $lines;

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $subtotal = '0.00';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $discount = '0.00';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $total = '0.00';

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $notes = null;

    public function __construct()
    {
        $this->lines = new ArrayCollection();
        $this->date = new \DateTimeImmutable('today');
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getNo(): string
    {
        return $this->no;
    }

    public function setNo(string $no): static
    {
        $this->no = $no;

        return $this;
    }

    public function getDate(): \DateTimeImmutable
    {
        return $this->date;
    }

    public function setDate(\DateTimeImmutable $date): static
    {
        $this->date = $date;

        return $this;
    }

    public function getParty(): ?Party
    {
        return $this->party;
    }

    public function setParty(?Party $party): static
    {
        $this->party = $party;

        return $this;
    }

    public function getPlant(): ?Plant
    {
        return $this->plant;
    }

    public function setPlant(?Plant $plant): static
    {
        $this->plant = $plant;

        return $this;
    }

    /** @return Collection<int, PurchaseBillLine> */
    public function getLines(): Collection
    {
        return $this->lines;
    }

    public function addLine(PurchaseBillLine $line): static
    {
        if (!$this->lines->contains($line)) {
            $this->lines->add($line);
            $line->setBill($this);
        }

        return $this;
    }

    public function removeLine(PurchaseBillLine $line): static
    {
        if ($this->lines->removeElement($line) && $line->getBill() === $this) {
            $line->setBill(null);
        }

        return $this;
    }

    public function getSubtotal(): float
    {
        return (float) $this->subtotal;
    }

    public function setSubtotal(string|float|int|null $v): static
    {
        $this->subtotal = number_format((float) ($v ?? 0), 2, '.', '');

        return $this;
    }

    public function getDiscount(): float
    {
        return (float) $this->discount;
    }

    public function setDiscount(string|float|int|null $v): static
    {
        $this->discount = number_format((float) ($v ?? 0), 2, '.', '');

        return $this;
    }

    public function getTotal(): float
    {
        return (float) $this->total;
    }

    public function setTotal(string|float|int|null $v): static
    {
        $this->total = number_format((float) ($v ?? 0), 2, '.', '');

        return $this;
    }

    public function getNotes(): ?string
    {
        return $this->notes;
    }

    public function setNotes(?string $notes): static
    {
        $this->notes = $notes;

        return $this;
    }

    public function recalculateTotals(): void
    {
        $subtotal = 0.0;
        foreach ($this->lines as $line) {
            $line->recalculateAmount();
            $subtotal += $line->getAmount();
        }

        $this->setSubtotal($subtotal);
        $this->setTotal(max(0.0, $subtotal - $this->getDiscount()));
    }

    public function __toString(): string
    {
        return $this->no;
    }
}
