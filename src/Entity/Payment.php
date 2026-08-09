<?php

declare(strict_types=1);

namespace App\Entity;

use App\Repository\PaymentRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Validator\Constraints as Assert;

/**
 * Money paid out. Polymorphic: either to a party (which raises what they owe us
 * in the ledger) or to a staff member as wages (which never touches a party ledger).
 */
#[ORM\Entity(repositoryClass: PaymentRepository::class)]
class Payment implements \Stringable
{
    public const TYPES = [
        'To Party' => 'party',
        'To Staff' => 'staff',
    ];

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

    #[ORM\Column(length: 20)]
    #[Assert\Choice(choices: ['party', 'staff'])]
    private string $type = 'party';

    #[ORM\ManyToOne(targetEntity: Party::class)]
    #[ORM\JoinColumn(nullable: true)]
    private ?Party $party = null;

    #[ORM\ManyToOne(targetEntity: Staff::class)]
    #[ORM\JoinColumn(nullable: true)]
    private ?Staff $staff = null;

    #[ORM\Column(type: 'decimal', precision: 14, scale: 2)]
    #[Assert\Positive(message: 'Enter a valid amount')]
    private string $amount = '0.00';

    #[ORM\Column(length: 30)]
    private string $mode = 'Cash';

    #[ORM\Column(type: 'text', nullable: true)]
    private ?string $notes = null;

    /**
     * The work entries this voucher settled — populated by a wage settlement.
     *
     * @var Collection<int, StaffWork>
     */
    #[ORM\OneToMany(mappedBy: 'paymentVoucher', targetEntity: StaffWork::class)]
    private Collection $staffWorks;

    public function __construct()
    {
        $this->staffWorks = new ArrayCollection();
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

    public function getType(): string
    {
        return $this->type;
    }

    public function setType(string $type): static
    {
        $this->type = $type;

        return $this;
    }

    public function isToParty(): bool
    {
        return 'party' === $this->type;
    }

    public function isToStaff(): bool
    {
        return 'staff' === $this->type;
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

    public function getStaff(): ?Staff
    {
        return $this->staff;
    }

    public function setStaff(?Staff $staff): static
    {
        $this->staff = $staff;

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

    /** @return Collection<int, StaffWork> */
    public function getStaffWorks(): Collection
    {
        return $this->staffWorks;
    }

    public function addStaffWork(StaffWork $work): static
    {
        if (!$this->staffWorks->contains($work)) {
            $this->staffWorks->add($work);
            $work->setPaymentVoucher($this);
        }

        return $this;
    }

    public function removeStaffWork(StaffWork $work): static
    {
        if ($this->staffWorks->removeElement($work) && $work->getPaymentVoucher() === $this) {
            $work->setPaymentVoucher(null);
        }

        return $this;
    }

    /** Single "Paid To" column for the index, whichever branch applies. */
    public function getPayeeName(): string
    {
        return $this->isToStaff()
            ? (string) $this->staff?->getName()
            : (string) $this->party?->getName();
    }

    public function __toString(): string
    {
        return $this->no;
    }
}
