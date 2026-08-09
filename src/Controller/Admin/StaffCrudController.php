<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Staff;
use App\Repository\StaffWorkRepository;
use App\Service\IndianNumberFormatter;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\AssociationField;
use EasyCorp\Bundle\EasyAdminBundle\Field\ChoiceField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;

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
            ->setSearchFields(['name', 'role', 'phone']);
    }

    public function configureFields(string $pageName): iterable
    {
        yield TextField::new('name', 'Name');
        yield TextField::new('role', 'Role');
        yield AssociationField::new('plant', 'Plant');
        yield TextField::new('phone', 'Phone')->hideOnIndex();

        yield ChoiceField::new('wageType', 'Wage Type')
            ->setChoices(Staff::WAGE_TYPES)
            ->renderAsBadges([
                'daily' => 'primary',
                'piece' => 'success',
            ]);

        yield MoneyField::new('dailyRate', 'Daily Rate')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->hideOnIndex();

        yield MoneyField::new('pieceRate', 'Piece Rate')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->hideOnIndex();

        // On the index only the rate that actually applies is meaningful.
        // Use a non-colliding property name — 'effectiveRate' would match the real
        // getter, causing PropertyAccessor to retrieve a float and fail validation.
        yield TextField::new('staffRate', 'Rate')
            ->onlyOnIndex()
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
