<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\PartyRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: PartyRepository::class)]
class Party implements \Stringable
{
    public const TYPES = [
        'Customer' => 'customer',
        'Supplier' => 'supplier',
        'Both' => 'both',
    ];

    public const OPENING_TYPES = [
        'To Receive (they owe us)' => 'to_receive',
        'To Pay (we owe them)' => 'to_pay',
    ];

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 180)]
    #[Assert\NotBlank(message: 'Party name is required')]
    private string $name = '';

    #[ORM\Column(length: 20)]
    #[Assert\Choice(choices: ['customer', 'supplier', 'both'])]
    private string $type = 'customer';

    #[ORM\Column(length: 40, nullable: true)]
    private ?string $phone = null;

    #[ORM\Column(length: 20, nullable: true)]
    private ?string $gstin = null;

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $address = null;

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $openingBalance = '0.00';

    #[ORM\Column(length: 20)]
    #[Assert\Choice(choices: ['to_receive', 'to_pay'])]
    private string $openingBalanceType = 'to_receive';

    #[ORM\Column(type: 'date_immutable')]
    private \DateTimeImmutable $createdAt;

    public function __construct()
    {
        $this->createdAt = new \DateTimeImmutable('today');
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getName(): string
    {
        return $this->name;
    }

    public function setName(string $name): static
    {
        $this->name = $name;

        return $this;
    }

    public function getType(): string
    {
        return $this->type;
    }

    public function setType(string $type): static
    {
        $this->type = $type;

        return $this;
    }

    public function getTypeLabel(): string
    {
        return (string) array_search($this->type, self::TYPES, true);
    }

    public function isCustomer(): bool
    {
        return \in_array($this->type, ['customer', 'both'], true);
    }

    public function isSupplier(): bool
    {
        return \in_array($this->type, ['supplier', 'both'], true);
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

    public function getAddress(): ?string
    {
        return $this->address;
    }

    public function setAddress(?string $address): static
    {
        $this->address = $address;

        return $this;
    }

    public function getOpeningBalance(): float
    {
        return (float) $this->openingBalance;
    }

    public function setOpeningBalance(string|float|int|null $v): static
    {
        $this->openingBalance = number_format((float) ($v ?? 0), 2, '.', '');

        return $this;
    }

    public function getOpeningBalanceType(): string
    {
        return $this->openingBalanceType;
    }

    public function setOpeningBalanceType(string $v): static
    {
        $this->openingBalanceType = $v;

        return $this;
    }

    /**
     * Opening balance as a signed ledger amount: positive means they owe us.
     */
    public function getSignedOpeningBalance(): float
    {
        $abs = abs($this->getOpeningBalance());

        return 'to_pay' === $this->openingBalanceType ? -$abs : $abs;
    }

    public function getCreatedAt(): \DateTimeImmutable
    {
        return $this->createdAt;
    }

    public function setCreatedAt(\DateTimeImmutable $createdAt): static
    {
        $this->createdAt = $createdAt;

        return $this;
    }

    /**
     * Transient: the live balance is computed by LedgerService, never stored.
     * The getter exists only so EasyAdmin can read it as a virtual field —
     * PartyCrudController renders the real value via formatValue().
     */
    public function getLedgerBalance(): ?float
    {
        return null;
    }

    public function __toString(): string
    {
        return $this->name;
    }
}
