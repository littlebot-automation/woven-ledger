<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Repository\SettingsRepository;
use App\Service\NavigationProvider;
use App\Service\ReportService;
use App\Service\StockService;
use EasyCorp\Bundle\EasyAdminBundle\Attribute\AdminDashboard;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Config\Dashboard;
use EasyCorp\Bundle\EasyAdminBundle\Config\MenuItem;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractDashboardController;
use Symfony\Component\HttpFoundation\Response;

#[AdminDashboard(routePath: '/admin', routeName: 'admin')]
class DashboardController extends AbstractDashboardController
{
    public function __construct(
        private readonly ReportService $reportService,
        private readonly StockService $stockService,
        private readonly SettingsRepository $settingsRepository,
        private readonly NavigationProvider $navigation,
    ) {
    }

    public function index(): Response
    {
        [$title] = $this->navigation->getPageMeta('dashboard');

        return $this->render('admin/dashboard.html.twig', [
            'page_title' => $title,
            'data' => $this->reportService->dashboardData(),
            'settings' => $this->settingsRepository->getSettings(),
        ]);
    }

    public function configureDashboard(): Dashboard
    {
        return Dashboard::new()
            ->setTitle('Woven Ledger');
    }

    public function configureCrud(): Crud
    {
        return Crud::new()
            ->setDateFormat('dd MMM yyyy')
            ->setPaginatorPageSize(25)
            ->setDefaultSort(['id' => 'DESC']);
    }

    /**
     * Sidebar, reproducing spec §2 exactly. The emoji live in the label because
     * EasyAdmin's setIcon() expects a CSS icon class, not a character.
     */
    public function configureMenuItems(): iterable
    {
        $lowStock = $this->stockService->getLowStockCount();

        foreach ($this->navigation->getGroups() as $group) {
            yield MenuItem::section($group['group']);

            foreach ($group['items'] as $item) {
                $label = $item['icon'].'  '.$item['label'];

                if ('dashboard' === $item['id']) {
                    yield MenuItem::linkToDashboard($label);

                    continue;
                }

                if (isset($item['route'])) {
                    yield MenuItem::linkToRoute($label, '', $item['route']);

                    continue;
                }

                $menuItem = MenuItem::linkTo($item['controller'], $label);

                if ('items' === $item['id'] && $lowStock > 0) {
                    $menuItem->setBadge((string) $lowStock, 'danger');
                }

                yield $menuItem;
            }
        }
    }
}
