<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\ReceiptRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

/**
 * Money received from a party. In the ledger this reduces what they owe us.
 */
#[ORM\Entity(repositoryClass: ReceiptRepository::class)]
class Receipt implements \Stringable
{
    public const MODES = [
        'Cash' => 'Cash',
        'Bank Transfer' => 'Bank Transfer',
        'UPI' => 'UPI',
        'Cheque' => 'Cheque',
    ];

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
    #[Assert\NotNull(message: 'Select a party')]
    private ?Party $party = null;

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    #[Assert\Positive(message: 'Enter a valid amount')]
    private string $amount = '0.00';

    #[ORM\Column(length: 30)]
    private string $mode = 'Cash';

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $notes = null;

    public function __construct()
    {
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

    public function getAmount(): float
    {
        return (float) $this->amount;
    }

    public function setAmount(string|float|int|null $v): static
    {
        $this->amount = number_format((float) ($v ?? 0), 2, '.', '');

        return $this;
    }

    public function getMode(): string
    {
        return $this->mode;
    }

    public function setMode(string $mode): static
    {
        $this->mode = $mode;

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

    public function __toString(): string
    {
        return $this->no;
    }
}
