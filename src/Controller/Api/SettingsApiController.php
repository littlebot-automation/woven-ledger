<?php

declare(strict_types=1);

namespace App\Controller\Api;

use App\Repository\SettingsRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\Routing\Attribute\Route;

#[Route('/api/settings')]
class SettingsApiController extends AbstractController
{
    use ApiPayloadTrait;

    public function __construct(
        private readonly SettingsRepository $settings,
    ) {
    }

    #[Route('', name: 'api_settings', methods: ['GET'])]
    public function show(): JsonResponse
    {
        $settings = $this->settings->getSettings();

        return $this->json([
            'company_name' => $settings->getCompanyName(),
            'address' => $settings->getAddress(),
            'gstin' => $settings->getGstin(),
            'phone' => $settings->getPhone(),
            'invoice_prefix' => $settings->getInvoicePrefix(),
            'purchase_prefix' => $settings->getPurchasePrefix(),
            'receipt_prefix' => $settings->getReceiptPrefix(),
            'payment_prefix' => $settings->getPaymentPrefix(),
            'next_invoice_no' => $settings->getNextInvoiceNo(),
            'next_purchase_no' => $settings->getNextPurchaseNo(),
            'next_receipt_no' => $settings->getNextReceiptNo(),
            'next_payment_no' => $settings->getNextPaymentNo(),
            'default_wage' => $this->paise($settings->getDefaultWage()),
            'current_plant_id' => $settings->getCurrentPlant()?->getId(),
        ]);
    }
}
