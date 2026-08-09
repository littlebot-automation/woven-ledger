<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Receipt;
use App\Service\DocumentPersister;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\AssociationField;
use EasyCorp\Bundle\EasyAdminBundle\Field\ChoiceField;
use EasyCorp\Bundle\EasyAdminBundle\Field\DateField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextareaField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;

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
            ->setDateFormat('dd MMM yyyy');
    }

    public function configureFields(string $pageName): iterable
    {
        yield TextField::new('no', 'Voucher No')
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->setHelp('Assigned automatically on save');

        yield DateField::new('date', 'Date');
        yield AssociationField::new('party', 'Received From')->autocomplete();

        yield MoneyField::new('amount', 'Amount')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2);

        yield ChoiceField::new('mode', 'Mode')->setChoices(Receipt::MODES);
        yield TextareaField::new('notes', 'Notes')->hideOnIndex();
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
