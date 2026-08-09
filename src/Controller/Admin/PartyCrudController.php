<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Party;
use App\Repository\PartyRepository;
use App\Service\IndianNumberFormatter;
use App\Service\LedgerService;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\ChoiceField;
use EasyCorp\Bundle\EasyAdminBundle\Field\DateField;
use EasyCorp\Bundle\EasyAdminBundle\Field\FormField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextareaField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;

class PartyCrudController extends AbstractCrudController
{
    public function __construct(
        private readonly LedgerService $ledgerService,
        private readonly PartyRepository $partyRepository,
        private readonly IndianNumberFormatter $numbers,
    ) {
    }

    public static function getEntityFqcn(): string
    {
        return Party::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Party')
            ->setEntityLabelInPlural('Customers & Suppliers')
            ->setPageTitle(Crud::PAGE_INDEX, 'Customers & Suppliers')
            ->setHelp(Crud::PAGE_INDEX, 'Manage your party master records')
            ->setDefaultSort(['name' => 'ASC'])
            ->setSearchFields(['name', 'phone', 'gstin']);
    }

    public function configureFields(string $pageName): iterable
    {
        yield FormField::addFieldset('Party');

        yield TextField::new('name', 'Party Name');

        yield ChoiceField::new('type', 'Type')
            ->setChoices(Party::TYPES)
            ->renderAsBadges([
                'customer' => 'primary',
                'supplier' => 'warning',
                'both' => 'info',
            ]);

        yield TextField::new('phone', 'Phone')->hideOnIndex();
        yield TextField::new('gstin', 'GSTIN')->hideOnIndex();
        yield TextareaField::new('address', 'Address')->hideOnIndex();

        yield FormField::addFieldset('Opening Balance')->onlyOnForms();

        yield MoneyField::new('openingBalance', 'Opening Balance')
            ->setCurrency('INR')
            ->setStoredAsCents(false)
            ->setNumDecimals(2)
            ->hideOnIndex();

        yield ChoiceField::new('openingBalanceType', 'Opening Balance Type')
            ->setChoices(Party::OPENING_TYPES)
            ->hideOnIndex();

        yield DateField::new('createdAt', 'Since')->hideOnForm();

        // Live ledger balance: positive means they owe us.
        // Use a virtual property name to avoid EasyAdmin trying to extract 'id'.
        yield TextField::new('ledgerBalance', 'Balance')
            ->onlyOnIndex()
            ->setSortable(false)
            ->renderAsHtml()
            ->setFormTypeOption('mapped', false)
            ->formatValue(function ($value, Party $party): string {
                $balance = $this->ledgerService->getBalance($party);

                if (abs($balance) < 0.01) {
                    return '—';
                }

                $suffix = $balance > 0 ? 'to receive' : 'to pay';

                return \sprintf(
                    '%s <small>%s</small>',
                    $this->numbers->inr(abs($balance)),
                    $suffix,
                );
            });
    }

    /**
     * A party carrying history is never deleted — the original app refuses and
     * suggests keeping the record instead.
     */
    public function deleteEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if ($entityInstance instanceof Party && $this->partyRepository->hasTransactions($entityInstance)) {
            $this->addFlash('danger', 'Cannot delete: party has transactions. Consider keeping the record.');

            return;
        }

        parent::deleteEntity($em, $entityInstance);
    }
}
