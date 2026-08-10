<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Payment;
use App\Service\DocumentPersister;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Config\Filters;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\AssociationField;
use EasyCorp\Bundle\EasyAdminBundle\Field\ChoiceField;
use EasyCorp\Bundle\EasyAdminBundle\Field\DateField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextareaField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;
use EasyCorp\Bundle\EasyAdminBundle\Filter\ChoiceFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\DateTimeFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\EntityFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\NumericFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\TextFilter;

class PaymentCrudController extends AbstractCrudController
{
    public function __construct(
        private readonly DocumentPersister $documentPersister,
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
            ->setDateFormat('dd MMM yyyy')
            // Clicking a row opens the read-only view rather than the form.
            // Enabling the detail action is not enough on its own: EasyAdmin's
            // default row action is the chain [EDIT, DETAIL], and EDIT is enabled
            // here, so it would keep winning. Naming DETAIL moves the link.
            ->setDefaultRowAction(Action::DETAIL);
    }

    /** DETAIL is not a default index action, so it is added for the row link. */
    public function configureActions(Actions $actions): Actions
    {
        return $actions->add(Crud::PAGE_INDEX, Action::DETAIL);
    }

    /**
     * The voucher is polymorphic, so the payee needs two filters rather than one:
     * "Paid To" picks the branch, then Party or Staff narrows within it. The
     * index's single "Payee" column cannot be filtered — getPayeeName() picks a
     * branch in PHP and is not a stored column.
     */
    public function configureFilters(Filters $filters): Filters
    {
        return $filters
            ->add(ChoiceFilter::new('type', 'Paid To')->setChoices(Payment::TYPES))
            ->add(EntityFilter::new('party', 'Party'))
            ->add(EntityFilter::new('staff', 'Staff Member'))
            ->add(DateTimeFilter::new('date', 'Date'))
            ->add(NumericFilter::new('amount', 'Amount'))
            ->add(ChoiceFilter::new('mode', 'Mode')->setChoices(Payment::MODES))
            ->add(TextFilter::new('no', 'Voucher No'))
            ->add(TextFilter::new('notes', 'Notes'));
    }

    /**
     * Three rows: the voucher's identity, then the payee question in full (the
     * branch selector next to both branches, since only one of them will be
     * filled in), then the money. Widths only — which fields exist and how the
     * payee is resolved is untouched.
     */
    public function configureFields(string $pageName): iterable
    {
        yield TextField::new('no', 'Voucher No')
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->setHelp('Assigned automatically on save')
            ->setColumns('col-12 col-md-6');

        yield DateField::new('date', 'Date')->setColumns('col-12 col-md-6');

        yield ChoiceField::new('type', 'Paid To')
            ->setChoices(Payment::TYPES)
            ->renderAsBadges(['party' => 'primary', 'staff' => 'success'])
            ->setHelp('Choose whether this voucher pays a party or a staff member')
            ->setColumns('col-12 col-md-4');

        yield AssociationField::new('party', 'Party')
            ->autocomplete()
            ->hideOnIndex()
            ->setRequired(false)
            ->setColumns('col-12 col-md-4');

        yield AssociationField::new('staff', 'Staff Member')
            ->autocomplete()
            ->hideOnIndex()
            ->setRequired(false)
            ->setColumns('col-12 col-md-4');

        // Index and detail, never a form: getPayeeName() resolves the party-or-staff
        // branch in PHP and has no setter, so it must stay out of the form grid.
        yield TextField::new('payeeName', 'Payee')
            ->hideOnForm()
            ->setSortable(false)
            ->formatValue(static fn ($v, Payment $p): string => $p->getPayeeName());

        yield MoneyField::new('amount', 'Amount')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setColumns('col-12 col-md-6');

        yield ChoiceField::new('mode', 'Mode')->setChoices(Payment::MODES)
            ->setColumns('col-12 col-md-6');

        yield TextareaField::new('notes', 'Notes')->hideOnIndex()->setColumns('col-12');
    }

    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof Payment) {
            parent::persistEntity($em, $entityInstance);

            return;
        }

        // An unresolved payee aborts the save, exactly as before.
        if (!$this->normalisePayee($entityInstance)) {
            return;
        }

        $this->documentPersister->createPayment($entityInstance);
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
