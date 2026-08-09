<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\SalesInvoice;
use App\Repository\SettingsRepository;
use EasyCorp\Bundle\EasyAdminBundle\Attribute\AdminRoute;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;

class SalesPrintController extends AbstractController
{
    public function __construct(
        private readonly SettingsRepository $settingsRepository,
    ) {
    }

    #[AdminRoute(path: '/sales/{id}/print', name: 'sales_print', options: ['requirements' => ['id' => '\d+']])]
    public function print(SalesInvoice $invoice): Response
    {
        return $this->render('admin/sales_print.html.twig', [
            'invoice' => $invoice,
            'settings' => $this->settingsRepository->getSettings(),
        ]);
    }
}
