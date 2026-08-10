<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\StockMovementRepository;
use Doctrine\ORM\Mapping as ORM;

/**
 * An audit row for stock movement that no document can account for.
 *
 * Document movements are deliberately *not* stored here. A purchase bill line and
 * a sales invoice line already are the record of what moved, and
 * {@see \App\Service\StockHistoryService} reconstructs the history from them; a row
 * here for the same movement would count every bill and invoice twice.
 *
 * What this table exists for is the manual path — opening stock, physical-count
 * corrections and wastage entered through /admin/item/{id}/adjust. Those used to
 * mutate ItemStock in place and leave nothing behind at all, which is why an item's
 * derived history cannot be made to add up for anything adjusted before this table
 * existed. From now on it can.
 */
#[ORM\Entity(repositoryClass: StockMovementRepository::class)]
#[ORM\Index(name: 'idx_stock_movement_item', columns: ['item_id'])]
class StockMovement
{
    /**
     * The only source that writes rows today. It is stored rather than assumed so
     * that a future source (a production entry, say) stays distinguishable without
     * a migration, and so the history table can label rows honestly.
     */
    public const SOURCE_MANUAL = 'manual';

    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Item::class)]
    #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
    private ?Item $item = null;

    #[ORM\ManyToOne(targetEntity: Plant::class)]
    #[ORM\JoinColumn(nullable: false, onDelete: 'CASCADE')]
    private ?Plant $plant = null;

    /** Signed: positive is stock in, negative is stock out. Same scale as ItemStock::$qty. */
    #[ORM\Column(type: 'decimal', precision: 14, scale: 3)]
    private string $qtyDelta = '0.000';

    #[ORM\Column(length: 20)]
    private string $source = self::SOURCE_MANUAL;

    /** A short human note — "Set to 640 (was 500)", "Wastage". */
    #[ORM\Column(length: 180)]
    private string $reason = '';

    #[ORM\Column(type: 'datetime_immutable')]
    private \DateTimeImmutable $recordedAt;

    public function __construct()
    {
        $this->recordedAt = new \DateTimeImmutable();
    }

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

    public function getQtyDelta(): float
    {
        return (float) $this->qtyDelta;
    }

    public function setQtyDelta(string|float|int|null $v): static
    {
        $this->qtyDelta = number_format((float) ($v ?? 0), 3, '.', '');

        return $this;
    }

    public function getSource(): string
    {
        return $this->source;
    }

    public function setSource(string $source): static
    {
        $this->source = $source;

        return $this;
    }

    public function getReason(): string
    {
        return $this->reason;
    }

    public function setReason(?string $reason): static
    {
        // Truncated rather than rejected: a reason is a note, never a reason to
        // lose the audit row it is attached to.
        $this->reason = mb_substr(trim((string) $reason), 0, 180);

        return $this;
    }

    public function getRecordedAt(): \DateTimeImmutable
    {
        return $this->recordedAt;
    }

    public function setRecordedAt(\DateTimeImmutable $recordedAt): static
    {
        $this->recordedAt = $recordedAt;

        return $this;
    }
}
