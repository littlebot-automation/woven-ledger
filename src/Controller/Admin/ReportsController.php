<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Repository\SettingsRepository;
use App\Service\NavigationProvider;
use App\Service\ReportService;
use EasyCorp\Bundle\EasyAdminBundle\Attribute\AdminRoute;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;

class ReportsController extends AbstractController
{
    public function __construct(
        private readonly ReportService $reportService,
        private readonly SettingsRepository $settingsRepository,
        private readonly NavigationProvider $navigation,
    ) {
    }

    #[AdminRoute(path: '/reports', name: 'reports')]
    public function index(Request $request): Response
    {
        $tab = (string) $request->query->get('tab', 'sales');

        if (!\array_key_exists($tab, ReportService::TABS)) {
            $tab = 'sales';
        }

        [$title] = $this->navigation->getPageMeta('reports');

        return $this->render('admin/reports.html.twig', [
            'page_title' => $title,
            'tabs' => ReportService::TABS,
            'tab' => $tab,
            'data' => $this->reportService->reportData($tab),
            'settings' => $this->settingsRepository->getSettings(),
        ]);
    }
}
