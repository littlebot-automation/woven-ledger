<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\StaffRepository;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: StaffRepository::class)]
class Staff implements \Stringable
{
    public const WAGE_TYPES = [
        'Daily Rate' => 'daily',
        'Piece Rate (per bag/unit)' => 'piece',
    ];

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 180)]
    #[Assert\NotBlank(message: 'Name is required')]
    private string $name = '';

    #[ORM\Column(length: 120, nullable: true)]
    private ?string $role = null;

    #[ORM\ManyToOne(targetEntity: Plant::class)]
    #[ORM\JoinColumn(nullable: true, onDelete: 'SET NULL')]
    private ?Plant $plant = null;

    #[ORM\Column(length: 40, nullable: true)]
    private ?string $phone = null;

    #[ORM\Column(length: 20)]
    #[Assert\Choice(choices: ['daily', 'piece'])]
    private string $wageType = 'daily';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $dailyRate = '0.00';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $pieceRate = '0.00';

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

    public function getRole(): ?string
    {
        return $this->role;
    }

    public function setRole(?string $role): static
    {
        $this->role = $role;

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

    public function getPhone(): ?string
    {
        return $this->phone;
    }

    public function setPhone(?string $phone): static
    {
        $this->phone = $phone;

        return $this;
    }

    public function getWageType(): string
    {
        return $this->wageType;
    }

    public function setWageType(string $wageType): static
    {
        $this->wageType = $wageType;

        return $this;
    }

    public function getWageTypeLabel(): string
    {
        return 'daily' === $this->wageType ? 'Daily' : 'Piece';
    }

    public function getDailyRate(): float
    {
        return (float) $this->dailyRate;
    }

    public function setDailyRate(string|float|int|null $v): static
    {
        $this->dailyRate = number_format((float) ($v ?? 0), 2, '.', '');

        return $this;
    }

    public function getPieceRate(): float
    {
        return (float) $this->pieceRate;
    }

    public function setPieceRate(string|float|int|null $v): static
    {
        $this->pieceRate = number_format((float) ($v ?? 0), 2, '.', '');

        return $this;
    }

    /**
     * The rate that applies given the wage type — this is what the work-entry
     * form auto-fills when a staff member is picked.
     */
    public function getEffectiveRate(): float
    {
        return 'daily' === $this->wageType ? $this->getDailyRate() : $this->getPieceRate();
    }

    /**
     * Transient: the admin list renders getEffectiveRate() under this name.
     * The getter exists only so EasyAdmin can read it as a virtual field.
     */
    public function getStaffRate(): ?float
    {
        return null;
    }

    public function __toString(): string
    {
        return $this->name;
    }
}
