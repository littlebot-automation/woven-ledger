<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Settings;
use App\Repository\SettingsRepository;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Config\KeyValueStore;
use EasyCorp\Bundle\EasyAdminBundle\Context\AdminContext;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\AssociationField;
use EasyCorp\Bundle\EasyAdminBundle\Field\FormField;
use EasyCorp\Bundle\EasyAdminBundle\Field\IntegerField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\NumberField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextareaField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;
use EasyCorp\Bundle\EasyAdminBundle\Router\AdminUrlGenerator;
use Symfony\Component\HttpFoundation\Response;

/**
 * The single configuration row. There is nothing to list and nothing to create,
 * so the index redirects straight into editing it.
 */
class SettingsCrudController extends AbstractCrudController
{
    public function __construct(
        private readonly SettingsRepository $settingsRepository,
        private readonly AdminUrlGenerator $adminUrlGenerator,
    ) {
    }

    public static function getEntityFqcn(): string
    {
        return Settings::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Business Settings')
            ->setEntityLabelInPlural('Business Settings')
            ->setPageTitle(Crud::PAGE_EDIT, 'Business Settings')
            ->setHelp(Crud::PAGE_EDIT, 'Company details, plants, numbering & data');
    }

    public function configureActions(Actions $actions): Actions
    {
        return $actions
            ->disable(Action::NEW, Action::DELETE, Action::BATCH_DELETE);
    }

    public function index(AdminContext $context): KeyValueStore|Response
    {
        $settings = $this->settingsRepository->getSettings();

        return $this->redirect(
            $this->adminUrlGenerator
                ->setController(self::class)
                ->setAction(Action::EDIT)
                ->setEntityId($settings->getId())
                ->generateUrl()
        );
    }

    /**
     * Widths are given as responsive Bootstrap classes rather than plain column
     * counts, so the form folds down to a single column on a phone instead of
     * squeezing every control into an unreadable sliver.
     */
    public function configureFields(string $pageName): iterable
    {
        // The identity of the business: name and GSTIN belong together, and the
        // address is a textarea, so it gets the whole row to itself.
        yield FormField::addFieldset('Company');
        yield TextField::new('companyName', 'Company Name')->setColumns('col-12 col-md-6');
        yield TextField::new('gstin', 'GSTIN')->setColumns('col-12 col-md-6');
        yield TextField::new('phone', 'Phone')->setColumns('col-12 col-md-6');
        yield TextareaField::new('address', 'Address')->setColumns('col-12');

        // A prefix and its next number are one setting in two boxes, so they sit
        // next to each other: one pair per row when narrow, two pairs when wide.
        yield FormField::addFieldset('Document Numbering');
        yield TextField::new('invoicePrefix', 'Sales Invoice Prefix')->setColumns('col-6 col-lg-3');
        yield IntegerField::new('nextInvoiceNo', 'Next Invoice No')->setColumns('col-6 col-lg-3');
        yield TextField::new('purchasePrefix', 'Purchase Bill Prefix')->setColumns('col-6 col-lg-3');
        yield IntegerField::new('nextPurchaseNo', 'Next Bill No')->setColumns('col-6 col-lg-3');
        yield TextField::new('receiptPrefix', 'Receipt Prefix')->setColumns('col-6 col-lg-3');
        yield IntegerField::new('nextReceiptNo', 'Next Receipt No')->setColumns('col-6 col-lg-3');
        yield TextField::new('paymentPrefix', 'Payment Prefix')->setColumns('col-6 col-lg-3');
        yield IntegerField::new('nextPaymentNo', 'Next Payment No')->setColumns('col-6 col-lg-3');

        // Three unrelated but equally small defaults, so they share one row.
        yield FormField::addFieldset('Defaults');
        yield NumberField::new('lowStockDefault', 'Low Stock Default')
            ->setNumDecimals(3)
            ->setHelp('Used for any item without its own threshold')
            ->setColumns('col-12 col-md-4');
        yield MoneyField::new('defaultWage', 'Default Wage')
            ->setCurrency('INR')->setStoredAsCents(false)->setNumDecimals(2)
            ->setColumns('col-12 col-md-4');
        yield AssociationField::new('currentPlant', 'Active Plant')
            ->setHelp('Shown in the top bar and used as the default on new documents')
            ->setColumns('col-12 col-md-4');
    }
}
