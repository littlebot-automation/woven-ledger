<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Staff;
use App\Repository\StaffWorkRepository;
use App\Service\IndianNumberFormatter;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Config\Filters;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\AssociationField;
use EasyCorp\Bundle\EasyAdminBundle\Field\ChoiceField;
use EasyCorp\Bundle\EasyAdminBundle\Field\FormField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;
use EasyCorp\Bundle\EasyAdminBundle\Filter\ChoiceFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\EntityFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\NumericFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\TextFilter;

class StaffCrudController extends AbstractCrudController
{
    public function __construct(
        private readonly StaffWorkRepository $staffWorkRepository,
        private readonly IndianNumberFormatter $numbers,
    ) {
    }

    public static function getEntityFqcn(): string
    {
        return Staff::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Staff Member')
            ->setEntityLabelInPlural('Staff Master')
            ->setPageTitle(Crud::PAGE_INDEX, 'Staff Master')
            ->setHelp(Crud::PAGE_INDEX, 'Workers across your plants')
            ->setDefaultSort(['name' => 'ASC'])
            ->setSearchFields(['name', 'role', 'phone'])
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
     * Staff are pulled up per plant and per wage scheme, so those lead.
     *
     * The index's "Rate" column is skipped: it switches between dailyRate and
     * pieceRate in PHP, so the two real columns are offered separately instead.
     */
    public function configureFilters(Filters $filters): Filters
    {
        return $filters
            ->add(EntityFilter::new('plant', 'Plant'))
            ->add(ChoiceFilter::new('wageType', 'Wage Type')->setChoices(Staff::WAGE_TYPES))
            ->add(TextFilter::new('name', 'Name'))
            ->add(TextFilter::new('role', 'Role'))
            ->add(TextFilter::new('phone', 'Phone'))
            ->add(NumericFilter::new('dailyRate', 'Daily Rate'))
            ->add(NumericFilter::new('pieceRate', 'Piece Rate'));
    }

    /**
     * Widths are set explicitly instead of leaning on EasyAdmin's per-type
     * defaults, which are ragged enough that paired fields wrap apart. They are
     * responsive class strings, not bare column counts, so the form collapses to
     * one column on a phone rather than staying N/12 wide at every breakpoint.
     */
    public function configureFields(string $pageName): iterable
    {
        // Who the worker is and where they report: two even rows.
        yield FormField::addFieldset('Staff');

        yield TextField::new('name', 'Name')->setColumns('col-12 col-md-6');
        yield TextField::new('role', 'Role')->setColumns('col-12 col-md-6');
        yield AssociationField::new('plant', 'Plant')->setColumns('col-12 col-md-6');
        yield TextField::new('phone', 'Phone')->hideOnIndex()->setColumns('col-12 col-md-6');

        // How they are paid. The scheme and both rates sit three-up, because
        // picking a wage type is only meaningful next to the rate it selects.
        yield FormField::addFieldset('Wages');

        yield ChoiceField::new('wageType', 'Wage Type')
            ->setChoices(Staff::WAGE_TYPES)
            ->renderAsBadges([
                'daily' => 'primary',
                'piece' => 'success',
            ])
            ->setColumns('col-12 col-md-4');

        yield MoneyField::new('dailyRate', 'Daily Rate')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->hideOnIndex()
            ->setColumns('col-6 col-md-4');

        yield MoneyField::new('pieceRate', 'Piece Rate')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->hideOnIndex()
            ->setColumns('col-6 col-md-4');

        // Only the rate that actually applies is meaningful when reading the
        // record, so it shows on the index and the detail page but never on a
        // form. Use a non-colliding property name — 'effectiveRate' would match
        // the real getter, causing PropertyAccessor to retrieve a float and fail
        // validation.
        yield TextField::new('staffRate', 'Rate')
            ->hideOnForm()
            ->setSortable(false)
            ->renderAsHtml()
            ->formatValue(fn ($value, Staff $staff): string => \sprintf(
                '<span class="mono">%s</span> <small>/ %s</small>',
                $this->numbers->inr($staff->getEffectiveRate()),
                'daily' === $staff->getWageType() ? 'day' : 'unit',
            ));
    }

    public function deleteEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if ($entityInstance instanceof Staff && $this->staffWorkRepository->hasEntriesFor($entityInstance)) {
            $this->addFlash('danger', 'Cannot delete: staff has recorded work entries.');

            return;
        }

        parent::deleteEntity($em, $entityInstance);
    }
}
