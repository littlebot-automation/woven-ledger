<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\PurchaseBill;
use App\Form\PurchaseBillLineType;
use App\Service\DocumentNumberService;
use App\Service\DocumentStockManager;
use Doctrine\ORM\EntityManagerInterface;
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

/**
 * Mirror of {@see SalesInvoiceCrudController}, with the stock signs inverted and
 * no availability warning — receiving goods can never be short.
 */
class PurchaseBillCrudController extends AbstractCrudController
{
    /** @var array{plantId: int|null, lines: array<int, array{itemId: int, qty: float}>} */
    private array $storedSnapshot = ['plantId' => null, 'lines' => []];

    public function __construct(
        private readonly DocumentStockManager $stockManager,
        private readonly DocumentNumberService $documentNumbers,
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
            ->setDateFormat('dd MMM yyyy');
    }

    public function configureFields(string $pageName): iterable
    {
        yield FormField::addPanel('Bill');

        yield TextField::new('no', 'Bill No')
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->addCssClass('mono')
            ->setHelp('Assigned automatically on save');

        yield DateField::new('date', 'Date');
        yield AssociationField::new('party', 'Supplier')->autocomplete();
        yield AssociationField::new('plant', 'Plant');

        yield FormField::addPanel('Line Items')->onlyOnForms();

        yield CollectionField::new('lines', 'Items')
            ->setEntryType(PurchaseBillLineType::class)
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

    public function edit(AdminContext $context)
    {
        $bill = $context->getEntity()->getInstance();

        if ($bill instanceof PurchaseBill) {
            $this->storedSnapshot = $this->stockManager->snapshot($bill->getLines(), $bill->getPlant());
        }

        return parent::edit($context);
    }

    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof PurchaseBill) {
            parent::persistEntity($em, $entityInstance);

            return;
        }

        $this->stockManager->normalise($entityInstance);
        $entityInstance->setNo($this->documentNumbers->consume(DocumentNumberService::PURCHASE));

        parent::persistEntity($em, $entityInstance);

        $this->stockManager->applySnapshot(
            $this->stockManager->snapshot($entityInstance->getLines(), $entityInstance->getPlant()),
            +1,
        );
    }

    public function updateEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if (!$entityInstance instanceof PurchaseBill) {
            parent::updateEntity($em, $entityInstance);

            return;
        }

        $this->stockManager->normalise($entityInstance);

        parent::updateEntity($em, $entityInstance);

        $this->stockManager->applySnapshot($this->storedSnapshot, -1);
        $this->stockManager->applySnapshot(
            $this->stockManager->snapshot($entityInstance->getLines(), $entityInstance->getPlant()),
            +1,
        );
    }

    public function deleteEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if ($entityInstance instanceof PurchaseBill) {
            $snapshot = $this->stockManager->snapshot($entityInstance->getLines(), $entityInstance->getPlant());
            parent::deleteEntity($em, $entityInstance);
            $this->stockManager->applySnapshot($snapshot, -1);

            return;
        }

        parent::deleteEntity($em, $entityInstance);
    }
}
