<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\SalesInvoice;
use App\Form\SalesInvoiceLineType;
use App\Service\DocumentNumberService;
use App\Service\DocumentStockManager;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Context\AdminContext;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\AssociationField;
use EasyCorp\Bundle\EasyAdminBundle\Field\CollectionField;
use EasyCorp\Bundle\EasyAdminBundle\Field\DateField;
use EasyCorp\Bundle\EasyAdminBundle\Field\FormField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextareaField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;

class SalesInvoiceCrudController extends AbstractCrudController
{
    /**
     * The document's stock footprint as stored, captured before the form binds.
     *
     * @var array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>}
     */
    private array $storedSnapshot = ['plantId' => null, 'lines' => []];

    public function __construct(
        private readonly DocumentStockManager $stockManager,
        private readonly DocumentNumberService $documentNumbers,
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
            ->setDateFormat('dd MMM yyyy');
    }

    public function configureActions(Actions $actions): Actions
    {
        $print = Action::new('print', 'Print', 'fa fa-print')
            ->linkToRoute('admin_sales_print', static fn (SalesInvoice $i): array => ['id' => $i->getId()])
            ->setHtmlAttributes(['target' => '_blank']);

        return $actions
            ->add(Crud::PAGE_INDEX, $print)
            ->add(Crud::PAGE_DETAIL, $print);
    }

    public function configureFields(string $pageName): iterable
    {
        yield FormField::addPanel('Invoice');

        yield TextField::new('no', 'Invoice No')
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->addCssClass('mono')
            ->setHelp('Assigned automatically on save');

        yield DateField::new('date', 'Date');
        yield AssociationField::new('party', 'Buyer')->autocomplete();
        yield AssociationField::new('plant', 'Plant');

        yield FormField::addPanel('Line Items')->onlyOnForms();

        yield CollectionField::new('lines', 'Items')
            ->setEntryType(SalesInvoiceLineType::class)
            ->allowAdd()
            ->allowDelete()
            ->setEntryIsComplex()
            ->renderExpanded()
            ->onlyOnForms()
            ->setFormTypeOption('by_reference', false)
            ->addCssClass('wl-line-items');

        yield FormField::addPanel('Totals')->onlyOnForms();

        yield MoneyField::new('subtotal', 'Subtotal')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->hideOnIndex();

        yield MoneyField::new('discount', 'Discount')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->hideOnIndex();

        yield MoneyField::new('total', 'Total')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->addCssClass('mono');

        yield TextareaField::new('notes', 'Notes')->hideOnIndex();
    }

    /**
     * Freeze the stored footprint before the submitted data overwrites it.
     */
    public function edit(AdminContext $context)
    {
        $invoice = $context->getEntity()->getInstance();

        if ($invoice instanceof SalesInvoice) {
            $this->storedSnapshot = $this->stockManager->snapshot($invoice->getLines(), $invoice->getPlant());
        }

        return parent::edit($context);
    }

    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof SalesInvoice) {
            parent::persistEntity($em, $entityInstance);

            return;
        }

        $this->stockManager->normalise($entityInstance);
        $entityInstance->setNo($this->documentNumbers->consume(DocumentNumberService::SALES));

        parent::persistEntity($em, $entityInstance);

        // Soft warnings only — the sale is already saved.
        foreach ($this->stockManager->salesWarnings($entityInstance->getLines(), $entityInstance->getPlant()) as $warning) {
            $this->addFlash('warning', $warning);
        }

        $this->stockManager->applySnapshot(
            $this->stockManager->snapshot($entityInstance->getLines(), $entityInstance->getPlant()),
            -1,
        );
    }

    public function updateEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof SalesInvoice) {
            parent::updateEntity($em, $entityInstance);

            return;
        }

        $this->stockManager->normalise($entityInstance);

        parent::updateEntity($em, $entityInstance);

        foreach ($this->stockManager->salesWarnings(
            $entityInstance->getLines(),
            $entityInstance->getPlant(),
            $this->storedSnapshot,
        ) as $warning) {
            $this->addFlash('warning', $warning);
        }

        // Undo the stored effect at the stored plant, then apply the new one.
        $this->stockManager->applySnapshot($this->storedSnapshot, +1);
        $this->stockManager->applySnapshot(
            $this->stockManager->snapshot($entityInstance->getLines(), $entityInstance->getPlant()),
            -1,
        );
    }

    public function deleteEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if ($entityInstance instanceof SalesInvoice) {
            $snapshot = $this->stockManager->snapshot($entityInstance->getLines(), $entityInstance->getPlant());
            parent::deleteEntity($em, $entityInstance);
            $this->stockManager->applySnapshot($snapshot, +1);

            return;
        }

        parent::deleteEntity($em, $entityInstance);
    }
}
