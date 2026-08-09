<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\ItemRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

#[ORM\Entity(repositoryClass: ItemRepository::class)]
class Item implements \Stringable
{
    public const TYPES = [
        'Raw Material (fabric roll)' => 'raw_material',
        'Finished Good (bag)' => 'finished_good',
    ];

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 180)]
    #[Assert\NotBlank(message: 'Item name is required')]
    private string $name = '';

    #[ORM\Column(length: 20)]
    #[Assert\Choice(choices: ['raw_material', 'finished_good'])]
    private string $type = 'raw_material';

    #[ORM\Column(length: 20)]
    private string $unit = 'pcs';

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    private string $defaultRate = '0.00';

    #[ORM\Column(length: 20, nullable: true)]
    private ?string $hsn = null;

    /**
     * Null means "inherit Settings::lowStockDefault" — the original app's blank field.
     */
    #[ORM\Column(type: 'decimal', precision: 14, scale: 3, nullable: true)]
    private ?string $lowStockThreshold = null;

    /** @var Collection<int, ItemStock> */
    #[ORM\OneToMany(mappedBy: 'item', targetEntity: ItemStock::class, cascade: ['persist', 'remove'], orphanRemoval: true)]
    private Collection $stocks;

    public function __construct()
    {
        $this->stocks = new ArrayCollection();
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
        return 'raw_material' === $this->type ? 'Raw Material' : 'Finished Good';
    }

    public function isRawMaterial(): bool
    {
        return 'raw_material' === $this->type;
    }

    public function getUnit(): string
    {
        return $this->unit;
    }

    public function setUnit(?string $unit): static
    {
        $unit = trim((string) $unit);
        $this->unit = '' === $unit ? 'pcs' : $unit;

        return $this;
    }

    public function getDefaultRate(): float
    {
        return (float) $this->defaultRate;
    }

    public function setDefaultRate(string|float|int|null $v): static
    {
        $this->defaultRate = number_format((float) ($v ?? 0), 2, '.', '');

        return $this;
    }

    public function getHsn(): ?string
    {
        return $this->hsn;
    }

    public function setHsn(?string $hsn): static
    {
        $this->hsn = $hsn;

        return $this;
    }

    public function getLowStockThreshold(): ?float
    {
        return null === $this->lowStockThreshold ? null : (float) $this->lowStockThreshold;
    }

    public function setLowStockThreshold(string|float|int|null $v): static
    {
        $this->lowStockThreshold = (null === $v || '' === $v)
            ? null
            : number_format((float) $v, 3, '.', '');

        return $this;
    }

    /** @return Collection<int, ItemStock> */
    public function getStocks(): Collection
    {
        return $this->stocks;
    }

    public function addStock(ItemStock $stock): static
    {
        if (!$this->stocks->contains($stock)) {
            $this->stocks->add($stock);
            $stock->setItem($this);
        }

        return $this;
    }

    public function removeStock(ItemStock $stock): static
    {
        if ($this->stocks->removeElement($stock) && $stock->getItem() === $this) {
            $stock->setItem(null);
        }

        return $this;
    }

    public function getStockFor(?Plant $plant): float
    {
        if (null === $plant) {
            return 0.0;
        }

        foreach ($this->stocks as $stock) {
            if ($stock->getPlant()?->getId() === $plant->getId()) {
                return $stock->getQty();
            }
        }

        return 0.0;
    }

    public function getTotalStock(): float
    {
        $total = 0.0;
        foreach ($this->stocks as $stock) {
            $total += $stock->getQty();
        }

        return $total;
    }

    public function __toString(): string
    {
        return $this->name;
    }
}
