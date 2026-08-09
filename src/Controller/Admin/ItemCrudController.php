<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Item;
use App\Repository\PlantRepository;
use App\Service\StockService;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\ChoiceField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\NumberField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;

class ItemCrudController extends AbstractCrudController
{
    public function __construct(
        private readonly StockService $stockService,
        private readonly PlantRepository $plantRepository,
    ) {
    }

    public static function getEntityFqcn(): string
    {
        return Item::class;
    }

    public function configureCrud(Crud $crud): Crud
    {
        return $crud
            ->setEntityLabelInSingular('Item')
            ->setEntityLabelInPlural('Item & Inventory')
            ->setPageTitle(Crud::PAGE_INDEX, 'Item & Inventory')
            ->setHelp(Crud::PAGE_INDEX, 'Fabric rolls, finished bags and plant-wise stock')
            ->setDefaultSort(['name' => 'ASC'])
            ->setSearchFields(['name', 'hsn']);
    }

    public function configureActions(Actions $actions): Actions
    {
        $adjust = Action::new('adjustStock', 'Adjust Stock', 'fa fa-wrench')
            ->linkToRoute('admin_item_adjust', static fn (Item $item): array => ['id' => $item->getId()]);

        return $actions->add(Crud::PAGE_INDEX, $adjust);
    }

    public function configureFields(string $pageName): iterable
    {
        yield TextField::new('name', 'Item Name')
            ->setHelp('e.g. PP Woven Fabric Roll 120GSM');

        yield ChoiceField::new('type', 'Type')
            ->setChoices(Item::TYPES)
            ->renderAsBadges([
                'raw_material' => 'warning',
                'finished_good' => 'info',
            ]);

        yield TextField::new('unit', 'Unit')->setHelp('kg / pcs / roll');

        yield MoneyField::new('defaultRate', 'Default Rate')
            ->setCurrency('INR')
            ->setStoredAsCents(false)
            ->setNumDecimals(2);

        yield TextField::new('hsn', 'HSN Code')->hideOnIndex();

        yield NumberField::new('lowStockThreshold', 'Low Stock Threshold')
            ->setNumDecimals(3)
            ->hideOnIndex()
            ->setHelp('Blank = use the default from Business Settings');

        // Plant-wise quantities. EasyAdmin keys fields by property, so this is one
        // column listing every plant rather than a column per plant.
        // Use a virtual property name to avoid extraction issues.
        yield TextField::new('plantStocks', 'Plant-wise Stock')
            ->onlyOnIndex()
            ->setSortable(false)
            ->renderAsHtml()
            ->formatValue(function ($value, Item $item): string {
                $threshold = $this->stockService->thresholdFor($item);
                $parts = [];

                foreach ($this->plantRepository->findAllOrdered() as $plant) {
                    $qty = $item->getStockFor($plant);

                    $parts[] = \sprintf(
                        '<span class="wl-stock-cell"><small>%s</small> <span class="mono %s">%s</span></span>',
                        htmlspecialchars($plant->getName(), \ENT_QUOTES),
                        $qty <= $threshold ? 'wl-stock-low' : '',
                        $this->trimQty($qty),
                    );
                }

                // A plain space (not &nbsp;) so the chips can wrap onto more
                // lines — otherwise this column forces the whole grid off-screen.
                return implode(' ', $parts);
            });

        yield TextField::new('itemTotal', 'Total Stock')
            ->onlyOnIndex()
            ->setSortable(false)
            ->renderAsHtml()
            ->formatValue(fn ($value, Item $item): string => \sprintf(
                '<strong class="mono">%s %s</strong>',
                $this->trimQty($item->getTotalStock()),
                htmlspecialchars($item->getUnit(), \ENT_QUOTES),
            ));
    }

    private function trimQty(float $qty): string
    {
        return rtrim(rtrim(number_format($qty, 3, '.', ','), '0'), '.');
    }
}
