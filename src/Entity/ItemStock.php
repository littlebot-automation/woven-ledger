<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\ItemStockRepository;
use Doctrine\ORM\Mapping as ORM;

/**
 * Plant-wise stock level for one item. The original app kept this as a
 * `stock: {plantId: qty}` map hanging off each item.
 */
#[ORM\Entity(repositoryClass: ItemStockRepository::class)]
#[ORM\UniqueConstraint(name: 'uniq_item_plant', columns: ['item_id', 'plant_id'])]
class ItemStock
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Item::class, inversedBy: 'stocks')]
    #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
    private ?Item $item = null;

    #[ORM\ManyToOne(targetEntity: Plant::class)]
    #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
    private ?Plant $plant = null;

    #[ORM\Column(type: 'decimal', precision: 14, scale: 3)]
    private string $qty = '0.000';

    public function getId(): ?int
    {
        return $this->id;
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

    public function getPlant(): ?Plant
    {
        return $this->plant;
    }

    public function setPlant(?Plant $plant): static
    {
        $this->plant = $plant;

        return $this;
    }

    public function getQty(): float
    {
        return (float) $this->qty;
    }

    public function setQty(string|float|int|null $v): static
    {
        $this->qty = number_format((float) ($v ?? 0), 3, '.', '');

        return $this;
    }

    public function adjust(float $delta): static
    {
        return $this->setQty($this->getQty() + $delta);
    }
}
