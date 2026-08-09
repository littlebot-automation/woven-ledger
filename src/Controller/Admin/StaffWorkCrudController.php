<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Staff;
use App\Entity\StaffWork;
use App\Repository\StaffRepository;
use Doctrine\ORM\EntityManagerInterface;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\AssociationField;
use EasyCorp\Bundle\EasyAdminBundle\Field\BooleanField;
use EasyCorp\Bundle\EasyAdminBundle\Field\DateField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\NumberField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;

class StaffWorkCrudController extends AbstractCrudController
{
    public function __construct(
        private readonly StaffRepository $staffRepository,
    ) {
    }

    public static function getEntityFqcn(): string
    {
        return StaffWork::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Work Entry')
            ->setEntityLabelInPlural('Daily Staff Work')
            ->setPageTitle(Crud::PAGE_INDEX, 'Daily Staff Work')
            ->setHelp(Crud::PAGE_INDEX, 'Log work and settle staff wages')
            ->setDefaultSort(['date' => 'DESC', 'id' => 'DESC'])
            ->setSearchFields(['workType'])
            ->setDateFormat('dd MMM yyyy');
    }

    public function configureActions(Actions $actions): Actions
    {
        $settle = Action::new('settleWages', 'Settle Unpaid Wages', 'fa fa-hand-holding-dollar')
            ->linkToRoute('admin_staff_settle')
            ->createAsGlobalAction();

        return $actions->add(Crud::PAGE_INDEX, $settle);
    }

    public function configureFields(string $pageName): iterable
    {
        yield DateField::new('date', 'Date');

        yield AssociationField::new('staff', 'Staff Member')
            ->setFormTypeOption('attr', [
                'class' => 'wl-work-staff',
                'data-rates' => $this->rateMap(),
            ]);

        yield AssociationField::new('plant', 'Plant');

        yield TextField::new('workType', 'Work Type')
            ->setHelp('e.g. Stitching, Loading, Lamination');

        yield NumberField::new('qty', 'Qty')
            ->setNumDecimals(3)
            ->setFormTypeOption('attr', ['class' => 'wl-work-qty', 'step' => '0.001']);

        yield MoneyField::new('rate', 'Rate')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setFormTypeOption('attr', ['class' => 'wl-work-rate', 'step' => '0.01'])
            ->setHelp('Auto-filled from the staff member’s wage type — override if needed');

        yield MoneyField::new('amount', 'Amount')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setFormTypeOption('disabled', true)
            ->setRequired(false)
            ->addCssClass('mono');

        yield BooleanField::new('paid', 'Paid')
            ->renderAsSwitch(false)
            ->setFormTypeOption('disabled', true)
            ->setHelp('Set by wage settlement');

        yield AssociationField::new('paymentVoucher', 'Settled By')
            ->onlyOnDetail();
    }

    /** Amount is always derived server-side; a posted value is never trusted. */
    public function persistEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if ($entityInstance instanceof StaffWork) {
            $entityInstance->recalculateAmount();
        }

        parent::persistEntity($em, $entityInstance);
    }

    public function updateEntity(EntityManagerInterface $em, $entityInstance): void
    {
        if ($entityInstance instanceof StaffWork) {
            $entityInstance->recalculateAmount();
        }

        parent::updateEntity($em, $entityInstance);
    }

    /**
     * staffId => applicable rate, so the form can auto-fill without an endpoint.
     */
    private function rateMap(): string
    {
        $map = [];
        foreach ($this->staffRepository->findAllOrdered() as $staff) {
            \assert($staff instanceof Staff);
            $map[(string) $staff->getId()] = $staff->getEffectiveRate();
        }

        return json_encode($map, \JSON_THROW_ON_ERROR);
    }
}
