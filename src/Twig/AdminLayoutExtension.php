<?php

declare(strict_types=1);

namespace App\Twig;

use App\Repository\SettingsRepository;
use App\Service\NavigationProvider;
use Symfony\Bundle\SecurityBundle\Security;
use Symfony\Component\HttpFoundation\RequestStack;
use Twig\Extension\AbstractExtension;
use Twig\Extension\GlobalsInterface;

class AdminLayoutExtension extends AbstractExtension implements GlobalsInterface
{
    public function __construct(
        private readonly SettingsRepository $settingsRepository,
        private readonly NavigationProvider $navigation,
        private readonly RequestStack $requestStack,
        private readonly Security $security,
    ) {
    }

    public function getGlobals(): array
    {
        $request = $this->requestStack->getCurrentRequest();
        if (null === $request || !str_starts_with($request->getPathInfo(), '/admin')) {
            return [];
        }

        $settings = $this->settingsRepository->getSettings();
        [$title] = $this->navigation->getPageMeta($this->getActiveNav($request));

        return [
            'settings' => $settings,
            'page_title' => $title,
        ];
    }

    private function getActiveNav(\Symfony\Component\HttpFoundation\Request $request): string
    {
        // Try to get from query parameter
        if ($controller = $request->query->get('crudControllerFqcn')) {
            return $this->mapControllerToNav($controller);
        }

        // Try to get from route attributes
        if ($controller = $request->attributes->get('crudControllerFqcn')) {
            return $this->mapControllerToNav($controller);
        }

        // Try to get from current route
        $routeName = $request->attributes->get('_route');
        return match ($routeName) {
            'admin' => 'dashboard',
            'admin_ledger' => 'ledger',
            'admin_reports' => 'reports',
            'admin_backup' => 'backup',
            default => 'dashboard',
        };
    }

    private function mapControllerToNav(string $controllerClass): string
    {
        return match (true) {
            str_contains($controllerClass, 'ItemCrudController') => 'items',
            str_contains($controllerClass, 'PartyCrudController') => 'parties',
            str_contains($controllerClass, 'StaffCrudController') => 'staff',
            str_contains($controllerClass, 'SalesInvoiceCrudController') => 'sales',
            str_contains($controllerClass, 'PurchaseBillCrudController') => 'purchases',
            str_contains($controllerClass, 'ReceiptCrudController') => 'receipts',
            str_contains($controllerClass, 'PaymentCrudController') => 'paymentsv',
            str_contains($controllerClass, 'StaffWorkCrudController') => 'staffwork',
            str_contains($controllerClass, 'PlantCrudController') => 'plants',
            str_contains($controllerClass, 'SettingsCrudController') => 'settings',
            default => 'dashboard',
        };
    }
}
