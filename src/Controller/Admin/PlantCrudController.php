<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Plant;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Config\Filters;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextareaField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;
use EasyCorp\Bundle\EasyAdminBundle\Filter\TextFilter;

class PlantCrudController extends AbstractCrudController
{
    public static function getEntityFqcn(): string
    {
        return Plant::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Plant')
            ->setEntityLabelInPlural('Plants')
            ->setPageTitle(Crud::PAGE_INDEX, 'Plants')
            ->setHelp(Crud::PAGE_INDEX, 'Manufacturing units you operate')
            ->setDefaultSort(['name' => 'ASC'])
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

    /** Only two real columns exist on a plant, so both are offered. */
    public function configureFilters(Filters $filters): Filters
    {
        return $filters
            ->add(TextFilter::new('name', 'Plant Name'))
            ->add(TextFilter::new('address', 'Address'));
    }

    /**
     * Only two fields, so no fieldset earns its keep here — just a deliberate
     * half-width name over a full-width address, given as responsive classes so
     * the pair stacks cleanly on a phone.
     */
    public function configureFields(string $pageName): iterable
    {
        yield TextField::new('name', 'Plant Name')->setColumns('col-12 col-md-6');
        yield TextareaField::new('address', 'Address')->setColumns('col-12');
    }
}
