<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Entity\SalesInvoice;
use App\Repository\SettingsRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

class SalesPrintController extends AbstractController
{
    public function __construct(
        private readonly SettingsRepository $settingsRepository,
    ) {
    }

    #[Route('/admin/sales/{id}/print', name: 'admin_sales_print', requirements: ['id' => '\d+'])]
    public function print(SalesInvoice $invoice): Response
    {
        return $this->render('admin/sales_print.html.twig', [
            'invoice' => $invoice,
            'settings' => $this->settingsRepository->getSettings(),
        ]);
    }
}
