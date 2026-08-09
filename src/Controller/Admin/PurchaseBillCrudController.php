<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\PurchaseBill;
use App\Form\PurchaseBillLineType;
use App\Service\DocumentPersister;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
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
            ->setDateFormat('dd MMM yyyy');
    }

    public function configureFields(string $pageName): iterable
    {
        yield FormField::addFieldset('Bill');

        yield TextField::new('no', 'Bill No')
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->setHelp('Assigned automatically on save');

        yield DateField::new('date', 'Date');
        yield AssociationField::new('party', 'Supplier')->autocomplete();
        yield AssociationField::new('plant', 'Plant');

        yield FormField::addFieldset('Line Items')->onlyOnForms();

        yield CollectionField::new('lines', 'Items')
            ->setEntryType(PurchaseBillLineType::class)
            ->allowAdd()
            ->allowDelete()
            ->setEntryIsComplex()
            ->renderExpanded()
            ->onlyOnForms()
            ->setFormTypeOption('by_reference', false);

        yield FormField::addFieldset('Totals')->onlyOnForms();

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
            ->setRequired(false);

        yield TextareaField::new('notes', 'Notes')->hideOnIndex();
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
