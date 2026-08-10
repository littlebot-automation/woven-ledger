<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\Item;
use App\Repository\PlantRepository;
use App\Service\IndianNumberFormatter;
use App\Service\StockHistoryService;
use App\Service\StockService;
use EasyCorp\Bundle\EasyAdminBundle\Config\Action;
use EasyCorp\Bundle\EasyAdminBundle\Config\Actions;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Config\Filters;
use EasyCorp\Bundle\EasyAdminBundle\Config\KeyValueStore;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractCrudController;
use EasyCorp\Bundle\EasyAdminBundle\Field\ChoiceField;
use EasyCorp\Bundle\EasyAdminBundle\Field\FormField;
use EasyCorp\Bundle\EasyAdminBundle\Field\MoneyField;
use EasyCorp\Bundle\EasyAdminBundle\Field\NumberField;
use EasyCorp\Bundle\EasyAdminBundle\Field\TextField;
use EasyCorp\Bundle\EasyAdminBundle\Filter\ChoiceFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\NumericFilter;
use EasyCorp\Bundle\EasyAdminBundle\Filter\TextFilter;
use EasyCorp\Bundle\EasyAdminBundle\Router\AdminUrlGenerator;

class ItemCrudController extends AbstractCrudController
{
    public function __construct(
        private readonly StockService $stockService,
        private readonly PlantRepository $plantRepository,
        private readonly IndianNumberFormatter $numbers,
        private readonly StockHistoryService $stockHistory,
        private readonly AdminUrlGenerator $adminUrlGenerator,
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
            ->setSearchFields(['name', 'hsn'])
            // Clicking a row opens the read-only view rather than the form.
            // Enabling the detail action is not enough on its own: EasyAdmin's
            // default row action is the chain [EDIT, DETAIL], and EDIT is enabled
            // here, so it would keep winning. Naming DETAIL moves the link.
            ->setDefaultRowAction(Action::DETAIL)
            // Scoped to this controller, so every other entity keeps the stock
            // detail template. The override only appends the stock history; the
            // field list still comes from EasyAdmin's own template.
            ->overrideTemplate('crud/detail', 'admin/item_detail.html.twig');
    }

    /**
     * overrideTemplate() swaps the template but hands it no extra variables, so
     * the movement history has to be injected here.
     *
     * Guarded on PAGE_DETAIL: the index renders one row per item, and rebuilding
     * a history per row would turn the list into N sets of queries.
     */
    public function configureResponseParameters(KeyValueStore $responseParameters): KeyValueStore
    {
        if (Crud::PAGE_DETAIL !== $responseParameters->get('pageName')) {
            return $responseParameters;
        }

        $item = $responseParameters->get('entity')?->getInstance();

        if (!$item instanceof Item) {
            return $responseParameters;
        }

        $history = $this->stockHistory->getHistory($item);
        $history['entries'] = array_map($this->withDocumentUrl(...), $history['entries']);

        $responseParameters->set('stock_history', $history);

        // AdminUrlGenerator is a shared, stateful service and withDocumentUrl() has
        // just left it pointing at the last linked document. Aim it back at this
        // page so anything rendered afterwards that does not set every parameter
        // itself still inherits the context it would have inherited without us.
        $this->adminUrlGenerator
            ->setController(self::class)
            ->setAction(Action::DETAIL)
            ->setEntityId($item->getId());

        return $responseParameters;
    }

    /**
     * Link a history row to the document behind it. Manual adjustments and the
     * reconciling opening row have no document, so they get no link.
     *
     * @param array{kind: string, documentId: int|null} $entry
     *
     * @return array{kind: string, documentId: int|null, url: string|null}
     */
    private function withDocumentUrl(array $entry): array
    {
        $controller = match ($entry['kind']) {
            'purchase' => PurchaseBillCrudController::class,
            'sales' => SalesInvoiceCrudController::class,
            default => null,
        };

        $entry['url'] = (null === $controller || null === $entry['documentId'])
            ? null
            : $this->adminUrlGenerator
                ->setController($controller)
                ->setAction(Action::DETAIL)
                ->setEntityId($entry['documentId'])
                ->generateUrl();

        return $entry;
    }

    public function configureActions(Actions $actions): Actions
    {
        $adjust = Action::new('adjustStock', 'Adjust Stock', 'fa fa-wrench')
            ->linkToRoute('admin_item_adjust', static fn (Item $item): array => ['id' => $item->getId()]);

        return $actions
            ->add(Crud::PAGE_INDEX, $adjust)
            // DETAIL is not a default index action, so it is added for the row link.
            ->add(Crud::PAGE_INDEX, Action::DETAIL);
    }

    /**
     * Raw material vs finished good is the split the list is usually read by.
     *
     * Unit gets a TextFilter rather than a ChoiceFilter: setUnit() accepts any
     * free text (it only defaults blanks to 'pcs'), so there is no allowed set to
     * populate a dropdown from without inventing one.
     *
     * The stock columns are absent on purpose — "Plant-wise Stock" and "Total
     * Stock" are summed from the ItemStock collection at render time and are not
     * columns on item, so they cannot back a WHERE clause.
     */
    public function configureFilters(Filters $filters): Filters
    {
        return $filters
            ->add(ChoiceFilter::new('type', 'Type')->setChoices(Item::TYPES))
            ->add(TextFilter::new('name', 'Item Name'))
            ->add(TextFilter::new('unit', 'Unit'))
            ->add(NumericFilter::new('defaultRate', 'Default Rate'))
            ->add(TextFilter::new('hsn', 'HSN Code'))
            ->add(NumericFilter::new('lowStockThreshold', 'Low Stock Threshold'));
    }

    /**
     * Widths are set explicitly instead of leaning on EasyAdmin's per-type
     * defaults, which are ragged enough that paired fields wrap apart. They are
     * responsive class strings, not bare column counts, so the form collapses to
     * one column on a phone rather than staying N/12 wide at every breakpoint.
     */
    public function configureFields(string $pageName): iterable
    {
        // What the item *is*: the descriptive row, then the two short codes that
        // classify it. 'hsn' is yielded before 'defaultRate' purely to land it
        // beside 'unit'; it is hidden on the index, so the list order is untouched.
        yield FormField::addFieldset('Item');

        yield TextField::new('name', 'Item Name')
            ->setHelp('e.g. PP Woven Fabric Roll 120GSM')
            ->setColumns('col-12 col-md-6');

        yield ChoiceField::new('type', 'Type')
            ->setChoices(Item::TYPES)
            ->renderAsBadges([
                'raw_material' => 'warning',
                'finished_good' => 'info',
            ])
            ->setColumns('col-12 col-md-6');

        yield TextField::new('unit', 'Unit')
            ->setHelp('kg / pcs / roll')
            ->setColumns('col-6 col-md-4');

        yield TextField::new('hsn', 'HSN Code')
            ->hideOnIndex()
            ->setColumns('col-6 col-md-4');

        // The two numbers, split off into their own fieldset so the row break
        // is structural rather than an accident of column arithmetic.
        yield FormField::addFieldset('Rate & Stock');

        yield MoneyField::new('defaultRate', 'Default Rate')
            ->setCurrency('INR')
            ->setStoredAsCents(false)
            ->setNumDecimals(2)
            ->setColumns('col-6 col-md-4');

        yield NumberField::new('lowStockThreshold', 'Low Stock Threshold')
            ->setNumDecimals(3)
            ->hideOnIndex()
            ->setHelp('Blank = use the default from Business Settings')
            ->setColumns('col-6 col-md-4');

        // Plant-wise quantities. EasyAdmin keys fields by property, so this is one
        // column listing every plant rather than a column per plant.
        // Use a virtual property name to avoid extraction issues.
        yield TextField::new('plantStocks', 'Plant-wise Stock')
            // Index and detail, never a form: the chips are built here from the
            // ItemStock collection and there is nothing to write back.
            ->hideOnForm()
            ->setSortable(false)
            ->renderAsHtml()
            ->formatValue(function ($value, Item $item): string {
                $threshold = $this->stockService->thresholdFor($item);
                $parts = [];

                foreach ($this->plantRepository->findAllOrdered() as $plant) {
                    $qty = $item->getStockFor($plant);

                    $parts[] = \sprintf(
                        '<span><small>%s</small> %s%s</span>',
                        htmlspecialchars($plant->getName(), \ENT_QUOTES),
                        $this->numbers->qty($qty),
                        $qty <= $threshold ? ' <small>(low)</small>' : '',
                    );
                }

                // A plain space (not &nbsp;) so the chips can wrap onto more
                // lines — otherwise this column forces the whole grid off-screen.
                return implode(' ', $parts);
            });

        yield TextField::new('itemTotal', 'Total Stock')
            // Same reasoning as 'plantStocks' above.
            ->hideOnForm()
            ->setSortable(false)
            ->renderAsHtml()
            ->formatValue(fn ($value, Item $item): string => \sprintf(
                '<strong class="mono">%s %s</strong>',
                $this->numbers->qty($item->getTotalStock()),
                htmlspecialchars($item->getUnit(), \ENT_QUOTES),
            ));
    }
}
