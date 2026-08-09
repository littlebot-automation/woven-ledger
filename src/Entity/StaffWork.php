<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\StaffWorkRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: StaffWorkRepository::class)]
class StaffWork
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(type: 'date_immutable')]
    private \DateTimeImmutable $date;

    #[ORM\ManyToOne(targetEntity: Staff::class)]
    #[ORM\JoinColumn(nullable: false)]
    private ?Staff $staff = null;

    #[ORM\ManyToOne(targetEntity: Plant::class)]
    #[ORM\JoinColumn(nullable: true, onDelete: 'SET NULL')]
    private ?Plant $plant = null;

    /** Free text, e.g. "Stitching", "Loading". */
    #[ORM\Column(length: 120)]
    #[Assert\NotBlank(message: 'Work type is required')]
    private string $workType = '';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 3)]
    private string $qty = '0.000';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $rate = '0.00';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $amount = '0.00';

    #[ORM\Column]
    private bool $paid = false;

    #[ORM\ManyToOne(targetEntity: Payment::class, inversedBy: 'staffWorks')]
    #[ORM\JoinColumn(nullable: true, onDelete: 'SET NULL')]
    private ?Payment $paymentVoucher = null;

    public function __construct()
    {
        $this->date = new \DateTimeImmutable('today');
    }

    public function getId(): ?int
    {
        return $this->id;
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

    public function getStaff(): ?Staff
    {
        return $this->staff;
    }

    public function setStaff(?Staff $staff): static
    {
        $this->staff = $staff;

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

    public function getWorkType(): string
    {
        return $this->workType;
    }

    public function setWorkType(string $workType): static
    {
        $this->workType = $workType;

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

    public function isPaid(): bool
    {
        return $this->paid;
    }

    public function setPaid(bool $paid): static
    {
        $this->paid = $paid;

        return $this;
    }

    public function getPaymentVoucher(): ?Payment
    {
        return $this->paymentVoucher;
    }

    public function setPaymentVoucher(?Payment $paymentVoucher): static
    {
        $this->paymentVoucher = $paymentVoucher;

        return $this;
    }
}
