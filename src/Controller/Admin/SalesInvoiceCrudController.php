<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\SalesInvoice;
use App\Form\SalesInvoiceLineType;
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

class SalesInvoiceCrudController extends AbstractCrudController
{
    /**
     * The document's stock footprint as stored, captured before the form binds.
     *
     * @var array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>}
     */
    private array $storedSnapshot = ['plantId' => null, 'lines' => []];

    public function __construct(
        private readonly DocumentPersister $documentPersister,
    ) {
    }

    public static function getEntityFqcn(): string
    {
        return SalesInvoice::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Sales Invoice')
            ->setEntityLabelInPlural('Sales Invoices')
            ->setPageTitle(Crud::PAGE_INDEX, 'Sales Invoices')
            ->setHelp(Crud::PAGE_INDEX, 'Bill your buyers and auto-adjust stock')
            ->setDefaultSort(['date' => 'DESC', 'id' => 'DESC'])
            ->setSearchFields(['no', 'notes'])
            ->setDateFormat('dd MMM yyyy')
            // Clicking a row opens the read-only view rather than the form.
            // Enabling the detail action is not enough on its own: EasyAdmin's
            // default row action is the chain [EDIT, DETAIL], and EDIT is enabled
            // here, so it would keep winning. Naming DETAIL moves the link.
            ->setDefaultRowAction(Action::DETAIL);
    }

    public function configureActions(Actions $actions): Actions
    {
        $print = Action::new('print', 'Print', 'fa fa-print')
            ->linkToRoute('admin_sales_print', static fn (SalesInvoice $i): array => ['id' => $i->getId()])
            ->setHtmlAttributes(['target' => '_blank']);

        return $actions
            ->add(Crud::PAGE_INDEX, $print)
            ->add(Crud::PAGE_DETAIL, $print)
            // DETAIL is not a default index action, so it is added for the row link.
            ->add(Crud::PAGE_INDEX, Action::DETAIL);
    }

    /**
     * "Which invoices did I raise for this buyer, and when" is the question this
     * list gets asked, so buyer and date lead; total follows for chasing big bills.
     *
     * Line items are not filterable here: lines live on SalesInvoiceLine, and
     * reaching into them (e.g. "invoices containing item X") needs a join that
     * only a custom filter could express.
     */
    public function configureFilters(Filters $filters): Filters
    {
        return $filters
            ->add(EntityFilter::new('party', 'Buyer'))
            ->add(DateTimeFilter::new('date', 'Date'))
            ->add(EntityFilter::new('plant', 'Plant'))
            ->add(NumericFilter::new('total', 'Total'))
            ->add(TextFilter::new('no', 'Invoice No'))
            ->add(NumericFilter::new('subtotal', 'Subtotal'))
            ->add(NumericFilter::new('discount', 'Discount'))
            ->add(TextFilter::new('notes', 'Notes'));
    }

    /**
     * Widths are declared rather than inherited from the field type, so the
     * header reads as one line of four facts instead of four ragged rows. The
     * classes are responsive on purpose: a bare column count would be N/12 at
     * every breakpoint and unreadable on a phone.
     */
    public function configureFields(string $pageName): iterable
    {
        // Who, when, from where — one row on a desktop, two pairs on a tablet,
        // stacked on a phone.
        yield FormField::addFieldset('Invoice');

        yield TextField::new('no', 'Invoice No')
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->setHelp('Assigned automatically on save')
            ->setColumns('col-12 col-md-6 col-xl-3');

        yield DateField::new('date', 'Date')->setColumns('col-12 col-md-6 col-xl-3');
        yield AssociationField::new('party', 'Buyer')->autocomplete()->setColumns('col-12 col-md-6 col-xl-3');
        yield AssociationField::new('plant', 'Plant')->setColumns('col-12 col-md-6 col-xl-3');

        yield FormField::addFieldset('Line Items')->onlyOnForms();

        // The line editor is a multi-column grid in its own right and is the
        // busiest control on the page: it always gets the full width.
        yield CollectionField::new('lines', 'Items')
            ->setEntryType(SalesInvoiceLineType::class)
            ->allowAdd()
            ->allowDelete()
            ->setEntryIsComplex()
            ->renderExpanded()
            ->onlyOnForms()
            ->setFormTypeOption('by_reference', false)
            ->setColumns('col-12');

        // Three money boxes that are read together, so they sit three-up.
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

    /**
     * Freeze the stored footprint before the submitted data overwrites it.
     */
    public function edit(AdminContext $context): KeyValueStore|Response
    {
        $invoice = $context->getEntity()->getInstance();

        if ($invoice instanceof SalesInvoice) {
            $this->storedSnapshot = $this->documentPersister->snapshotOf($invoice);
        }

        return parent::edit($context);
    }

    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof SalesInvoice) {
            parent::persistEntity($em, $entityInstance);

            return;
        }

        // Soft warnings only — the sale is already saved.
        foreach ($this->documentPersister->createSalesInvoice($entityInstance) as $warning) {
            $this->addFlash('warning', $warning);
        }
    }

    public function updateEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof SalesInvoice) {
            parent::updateEntity($em, $entityInstance);

            return;
        }

        foreach ($this->documentPersister->updateSalesInvoice($entityInstance, $this->storedSnapshot) as $warning) {
            $this->addFlash('warning', $warning);
        }
    }

    public function deleteEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof SalesInvoice) {
            parent::deleteEntity($em, $entityInstance);

            return;
        }

        $this->documentPersister->deleteSalesInvoice($entityInstance);
    }
}
