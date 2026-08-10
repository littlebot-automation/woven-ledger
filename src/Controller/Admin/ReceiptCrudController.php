<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Receipt;
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

class ReceiptCrudController extends AbstractCrudController
{
    public function __construct(
        private readonly DocumentPersister $documentPersister,
    ) {
    }

    public static function getEntityFqcn(): string
    {
        return Receipt::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Receipt Voucher')
            ->setEntityLabelInPlural('Receipt Vouchers')
            ->setPageTitle(Crud::PAGE_INDEX, 'Receipt Vouchers')
            ->setHelp(Crud::PAGE_INDEX, 'Money received from parties')
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
     * Collections are reviewed party-by-party and month-by-month, so those two
     * come first; mode matters when reconciling a bank statement against cash.
     */
    public function configureFilters(Filters $filters): Filters
    {
        return $filters
            ->add(EntityFilter::new('party', 'Received From'))
            ->add(DateTimeFilter::new('date', 'Date'))
            ->add(NumericFilter::new('amount', 'Amount'))
            ->add(ChoiceFilter::new('mode', 'Mode')->setChoices(Receipt::MODES))
            ->add(TextFilter::new('no', 'Voucher No'))
            ->add(TextFilter::new('notes', 'Notes'));
    }

    /**
     * Two rows and a note: the voucher's identity (number, date) above the
     * money itself (who, how much, how). Responsive classes rather than plain
     * counts, so the whole thing stacks on a phone.
     */
    public function configureFields(string $pageName): iterable
    {
        yield TextField::new('no', 'Voucher No')
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->setHelp('Assigned automatically on save')
            ->setColumns('col-12 col-md-6');

        yield DateField::new('date', 'Date')->setColumns('col-12 col-md-6');

        yield AssociationField::new('party', 'Received From')->autocomplete()
            ->setColumns('col-12 col-md-4');

        yield MoneyField::new('amount', 'Amount')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setColumns('col-12 col-md-4');

        yield ChoiceField::new('mode', 'Mode')->setChoices(Receipt::MODES)
            ->setColumns('col-12 col-md-4');

        yield TextareaField::new('notes', 'Notes')->hideOnIndex()->setColumns('col-12');
    }

    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof Receipt) {
            parent::persistEntity($em, $entityInstance);

            return;
        }

        $this->documentPersister->createReceipt($entityInstance);
    }
}
