<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\PurchaseBill;
use App\Form\PurchaseBillLineType;
use App\Service\DocumentPersister;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Config\Filters;
use EasyCorp\Bundle\EasyAdminBundle\Config\KeyValueStore;
use EasyCorp\Bundle\EasyAdminBundle\Context\AdminContext;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\AssociationField;
use EasyCorp\Bundle\EasyAdminBundle\Field\CollectionField;
use EasyCorp\Bundle\EasyAdminBundle\Field\DateField;
use EasyCorp\Bundle\EasyAdminBundle\Field\FormField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextareaField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;
use EasyCorp\Bundle\EasyAdminBundle\Filter\DateTimeFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\EntityFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\NumericFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\TextFilter;
use Symfony\Component\HttpFoundation\Response;

/**
 * Mirror of {@see SalesInvoiceCrudController}, with the stock signs inverted and
 * no availability warning — receiving goods can never be short.
 */
class PurchaseBillCrudController extends AbstractCrudController
{
    /** @var array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>} */
    private array $storedSnapshot = ['plantId' => null, 'lines' => []];

    public function __construct(
        private readonly DocumentPersister $documentPersister,
    ) {
    }

    public static function getEntityFqcn(): string
    {
        return PurchaseBill::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Purchase Bill')
            ->setEntityLabelInPlural('Purchase Bills')
            ->setPageTitle(Crud::PAGE_INDEX, 'Purchase Bills')
            ->setHelp(Crud::PAGE_INDEX, 'Record fabric roll purchases from suppliers')
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

    /** Mirror of the sales invoice filters, with the party read as the supplier. */
    public function configureFilters(Filters $filters): Filters
    {
        return $filters
            ->add(EntityFilter::new('party', 'Supplier'))
            ->add(DateTimeFilter::new('date', 'Date'))
            ->add(EntityFilter::new('plant', 'Plant'))
            ->add(NumericFilter::new('total', 'Total'))
            ->add(TextFilter::new('no', 'Bill No'))
            ->add(NumericFilter::new('subtotal', 'Subtotal'))
            ->add(NumericFilter::new('discount', 'Discount'))
            ->add(TextFilter::new('notes', 'Notes'));
    }

    /** Widths mirror the sales invoice form, for the same reasons. */
    public function configureFields(string $pageName): iterable
    {
        yield FormField::addFieldset('Bill');

        yield TextField::new('no', 'Bill No')
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->setHelp('Assigned automatically on save')
            ->setColumns('col-12 col-md-6 col-xl-3');

        yield DateField::new('date', 'Date')->setColumns('col-12 col-md-6 col-xl-3');
        yield AssociationField::new('party', 'Supplier')->autocomplete()->setColumns('col-12 col-md-6 col-xl-3');
        yield AssociationField::new('plant', 'Plant')->setColumns('col-12 col-md-6 col-xl-3');

        yield FormField::addFieldset('Line Items')->onlyOnForms();

        // Full width, always — the line editor is a grid inside a grid.
        yield CollectionField::new('lines', 'Items')
            ->setEntryType(PurchaseBillLineType::class)
            ->allowAdd()
            ->allowDelete()
            ->setEntryIsComplex()
            ->renderExpanded()
            ->onlyOnForms()
            ->setFormTypeOption('by_reference', false)
            ->setColumns('col-12');

        yield FormField::addFieldset('Totals')->onlyOnForms();

        yield MoneyField::new('subtotal', 'Subtotal')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->hideOnIndex()
            ->setColumns('col-12 col-md-4');

        yield MoneyField::new('discount', 'Discount')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->hideOnIndex()
            ->setColumns('col-12 col-md-4');

        yield MoneyField::new('total', 'Total')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->setColumns('col-12 col-md-4');

        yield TextareaField::new('notes', 'Notes')->hideOnIndex()->setColumns('col-12');
    }

    public function edit(AdminContext $context): KeyValueStore|Response
    {
        $bill = $context->getEntity()->getInstance();

        if ($bill instanceof PurchaseBill) {
            $this->storedSnapshot = $this->documentPersister->snapshotOf($bill);
        }

        return parent::edit($context);
    }

    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof PurchaseBill) {
            parent::persistEntity($em, $entityInstance);

            return;
        }

        $this->documentPersister->createPurchaseBill($entityInstance);
    }

    public function updateEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof PurchaseBill) {
            parent::updateEntity($em, $entityInstance);

            return;
        }

        $this->documentPersister->updatePurchaseBill($entityInstance, $this->storedSnapshot);
    }

    public function deleteEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof PurchaseBill) {
            parent::deleteEntity($em, $entityInstance);

            return;
        }

        $this->documentPersister->deletePurchaseBill($entityInstance);
    }
}
