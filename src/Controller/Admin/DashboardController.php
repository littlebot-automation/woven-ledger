<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Repository\SettingsRepository;
use App\Service\NavigationProvider;
use App\Service\ReportService;
use App\Service\StockService;
use EasyCorp\Bundle\EasyAdminBundle\Config\Assets;
use EasyCorp\Bundle\EasyAdminBundle\Config\Crud;
use EasyCorp\Bundle\EasyAdminBundle\Config\Dashboard;
use EasyCorp\Bundle\EasyAdminBundle\Config\MenuItem;
use EasyCorp\Bundle\EasyAdminBundle\Controller\AbstractDashboardController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

class DashboardController extends AbstractDashboardController
{
    public function __construct(
        private readonly ReportService $reportService,
        private readonly StockService $stockService,
        private readonly SettingsRepository $settingsRepository,
        private readonly NavigationProvider $navigation,
    ) {
    }

    #[Route('/admin', name: 'admin')]
    public function index(): Response
    {
        [$title, $subtitle] = $this->navigation->getPageMeta('dashboard');

        return $this->render('admin/dashboard.html.twig', [
            'page_title' => $title,
            'page_subtitle' => $subtitle,
            'active_nav' => 'dashboard',
            'data' => $this->reportService->dashboardData(),
            'settings' => $this->settingsRepository->getSettings(),
        ]);
    }

    public function configureDashboard(): Dashboard
    {
        return Dashboard::new()
            ->setTitle('<span class="wl-brand-mark">▨</span> <span class="wl-brand-name">Woven Ledger</span>')
            ->renderContentMaximized()
            ->generateRelativeUrls();
    }

    public function configureAssets(): Assets
    {
        return Assets::new()
            ->addCssFile('css/woven.css')
            ->addJsFile('js/woven.js');
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

                $menuItem = MenuItem::linkToCrud($label, '', $item['entity']);

                if ('items' === $item['id'] && $lowStock > 0) {
                    $menuItem->setBadge((string) $lowStock, 'danger');
                }

                yield $menuItem;
            }
        }
    }
}
