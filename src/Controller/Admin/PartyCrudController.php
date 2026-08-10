<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Party;
use App\Repository\PartyRepository;
use App\Service\IndianNumberFormatter;
use App\Service\LedgerService;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Config\Filters;
use EasyCorp\Bundle\EasyAdminBundle\Config\KeyValueStore;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\ChoiceField;
use EasyCorp\Bundle\EasyAdminBundle\Field\DateField;
use EasyCorp\Bundle\EasyAdminBundle\Field\FormField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextareaField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;
use EasyCorp\Bundle\EasyAdminBundle\Filter\ChoiceFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\DateTimeFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\NumericFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\TextFilter;

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
            // The party's own name, not "Party (#8)". The view page is now where a
            // row click lands, so its title is what tells you who you are looking at.
            ->setPageTitle(Crud::PAGE_DETAIL, static fn (Party $party): string => $party->getName())
            ->setHelp(Crud::PAGE_INDEX, 'Manage your party master records')
            ->setDefaultSort(['name' => 'ASC'])
            ->setSearchFields(['name', 'phone', 'gstin'])
            // Clicking a row opens the read-only view rather than the form.
            // Enabling the detail action is not enough on its own: EasyAdmin's
            // default row action is the chain [EDIT, DETAIL], and EDIT is enabled
            // here, so it would keep winning. Naming DETAIL moves the link.
            ->setDefaultRowAction(Action::DETAIL)
            // Scoped to this controller, so every other entity keeps the stock
            // detail template. The override only appends the ledger table; the
            // field list still comes from EasyAdmin's own template.
            ->overrideTemplate('crud/detail', 'admin/party_detail.html.twig');
    }

    /**
     * overrideTemplate() swaps the template but hands it no extra variables, so
     * the ledger has to be injected here.
     *
     * Guarded on PAGE_DETAIL: the index renders one row per party, and building
     * a ledger per row would turn the list into N sets of queries.
     */
    public function configureResponseParameters(KeyValueStore $responseParameters): KeyValueStore
    {
        if (Crud::PAGE_DETAIL !== $responseParameters->get('pageName')) {
            return $responseParameters;
        }

        $party = $responseParameters->get('entity')?->getInstance();

        if ($party instanceof Party) {
            $responseParameters->set('ledger_entries', $this->ledgerService->getEntries($party));
        }

        return $responseParameters;
    }

    /** DETAIL is not a default index action, so it is added for the row link. */
    public function configureActions(Actions $actions): Actions
    {
        return $actions->add(Crud::PAGE_INDEX, Action::DETAIL);
    }

    /**
     * Customer/supplier is the first thing anyone narrows by, so it leads.
     *
     * The index's "Balance" column is deliberately absent here: it is computed by
     * LedgerService on every render and has no Doctrine column behind it, so a
     * filter could not build a WHERE clause for it.
     */
    public function configureFilters(Filters $filters): Filters
    {
        return $filters
            ->add(ChoiceFilter::new('type', 'Type')->setChoices(Party::TYPES))
            ->add(TextFilter::new('name', 'Party Name'))
            ->add(DateTimeFilter::new('createdAt', 'Since'))
            ->add(NumericFilter::new('openingBalance', 'Opening Balance'))
            ->add(ChoiceFilter::new('openingBalanceType', 'Opening Balance Type')->setChoices(Party::OPENING_TYPES))
            ->add(TextFilter::new('phone', 'Phone'))
            ->add(TextFilter::new('gstin', 'GSTIN'))
            ->add(TextFilter::new('address', 'Address'));
    }

    /**
     * Widths are set explicitly instead of leaning on EasyAdmin's per-type
     * defaults, which are ragged enough that paired fields wrap apart. They are
     * responsive class strings, not bare column counts, so the form collapses to
     * one column on a phone rather than staying N/12 wide at every breakpoint.
     */
    public function configureFields(string $pageName): iterable
    {
        // Who they are: identity on the first row, tax/contact on the second,
        // and the address gets its own row because a textarea needs the width.
        yield FormField::addFieldset('Party');

        yield TextField::new('name', 'Party Name')->setColumns('col-12 col-md-6');

        yield ChoiceField::new('type', 'Type')
            ->setChoices(Party::TYPES)
            ->renderAsBadges([
                'customer' => 'primary',
                'supplier' => 'warning',
                'both' => 'info',
            ])
            ->setColumns('col-12 col-md-6');

        // A party with no GSTIN is ordinary, not an error, so the view page shows a
        // dash rather than EasyAdmin's "Null" badge. Display only — the form still
        // gets a genuinely empty field, and these are hidden from the index anyway.
        yield TextField::new('phone', 'Phone')->hideOnIndex()
            ->formatValue(self::orDash(...))->setColumns('col-12 col-md-6');
        yield TextField::new('gstin', 'GSTIN')->hideOnIndex()
            ->formatValue(self::orDash(...))->setColumns('col-12 col-md-6');
        yield TextareaField::new('address', 'Address')->hideOnIndex()
            ->formatValue(self::orDash(...))->setColumns('col-12');

        // The amount and the side it falls on are one number in two boxes, so
        // they always share a row — reading either alone tells you nothing.
        yield FormField::addFieldset('Opening Balance')->onlyOnForms();

        yield MoneyField::new('openingBalance', 'Opening Balance')
            ->setCurrency('INR')
            ->setStoredAsCents(false)
            ->setNumDecimals(2)
            ->hideOnIndex()
            ->setColumns('col-6 col-md-4');

        yield ChoiceField::new('openingBalanceType', 'Opening Balance Type')
            ->setChoices(Party::OPENING_TYPES)
            ->hideOnIndex()
            ->setColumns('col-6 col-md-4');

        yield DateField::new('createdAt', 'Since')->hideOnForm();

        // Live ledger balance: positive means they owe us.
        // Use a virtual property name to avoid EasyAdmin trying to extract 'id'.
        yield TextField::new('ledgerBalance', 'Balance')
            // Index and detail, never a form: getLedgerBalance() is a transient
            // read-only getter with no setter behind it.
            ->hideOnForm()
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

    /** An em dash reads as "nothing here"; EasyAdmin's "Null" badge reads as a fault. */
    private static function orDash(?string $value): string
    {
        return null === $value || '' === trim($value) ? '—' : $value;
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
