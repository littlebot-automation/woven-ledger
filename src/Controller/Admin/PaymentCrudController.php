<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Payment;
use App\Service\DocumentNumberService;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\AssociationField;
use EasyCorp\Bundle\EasyAdminBundle\Field\ChoiceField;
use EasyCorp\Bundle\EasyAdminBundle\Field\DateField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextareaField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;

class PaymentCrudController extends AbstractCrudController
{
    public function __construct(
        private readonly DocumentNumberService $documentNumbers,
    ) {
    }

    public static function getEntityFqcn(): string
    {
        return Payment::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Payment Voucher')
            ->setEntityLabelInPlural('Payment Vouchers')
            ->setPageTitle(Crud::PAGE_INDEX, 'Payment Vouchers')
            ->setHelp(Crud::PAGE_INDEX, 'Money paid to parties or staff')
            ->setDefaultSort(['date' => 'DESC', 'id' => 'DESC'])
            ->setSearchFields(['no', 'notes'])
            ->setDateFormat('dd MMM yyyy');
    }

    public function configureFields(string $pageName): iterable
    {
        yield TextField::new('no', 'Voucher No')
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->addCssClass('mono')
            ->setHelp('Assigned automatically on save');

        yield DateField::new('date', 'Date');

        yield ChoiceField::new('type', 'Paid To')
            ->setChoices(Payment::TYPES)
            ->renderAsBadges(['party' => 'primary', 'staff' => 'success'])
            ->setHelp('Choose whether this voucher pays a party or a staff member');

        yield AssociationField::new('party', 'Party')
            ->autocomplete()
            ->hideOnIndex()
            ->setRequired(false);

        yield AssociationField::new('staff', 'Staff Member')
            ->autocomplete()
            ->hideOnIndex()
            ->setRequired(false);

        yield TextField::new('payeeName', 'Payee')
            ->onlyOnIndex()
            ->setSortable(false)
            ->formatValue(static fn ($v, Payment $p): string => $p->getPayeeName());

        yield MoneyField::new('amount', 'Amount')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->addCssClass('mono');

        yield ChoiceField::new('mode', 'Mode')->setChoices(Payment::MODES);
        yield TextareaField::new('notes', 'Notes')->hideOnIndex();
    }

    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if ($entityInstance instanceof Payment) {
            if (!$this->normalisePayee($entityInstance)) {
                return;
            }

            $entityInstance->setNo($this->documentNumbers->consume(DocumentNumberService::PAYMENT));
        }

        parent::persistEntity($em, $entityInstance);
    }

    public function updateEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if ($entityInstance instanceof Payment && !$this->normalisePayee($entityInstance)) {
            return;
        }

        parent::updateEntity($em, $entityInstance);
    }

    /**
     * Exactly one payee branch may be populated. Returns false (and flashes) when
     * the required one is missing, so the caller aborts the save.
     */
    private function normalisePayee(Payment $payment): bool
    {
        if ($payment->isToStaff()) {
            if (null === $payment->getStaff()) {
                $this->addFlash('danger', 'Select a staff member for this payment.');

                return false;
            }

            $payment->setParty(null);

            return true;
        }

        if (null === $payment->getParty()) {
            $this->addFlash('danger', 'Select a party for this payment.');

            return false;
        }

        $payment->setStaff(null);

        return true;
    }
}
