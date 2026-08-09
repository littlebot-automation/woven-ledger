<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Plant;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextareaField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;

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
            ->setDefaultSort(['name' => 'ASC']);
    }

    public function configureFields(string $pageName): iterable
    {
        yield TextField::new('name', 'Plant Name');
        yield TextareaField::new('address', 'Address');
    }
}
